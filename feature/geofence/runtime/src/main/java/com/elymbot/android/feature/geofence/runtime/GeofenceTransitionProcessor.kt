package com.elymbot.android.feature.geofence.runtime

import com.elymbot.android.core.common.logging.RuntimeLogger
import com.elymbot.android.feature.geofence.domain.GeofenceRuleRepositoryPort
import com.elymbot.android.feature.geofence.domain.model.ConfigGeofenceBinding
import com.elymbot.android.feature.geofence.domain.model.GeofenceExecutionFailureCodes
import com.elymbot.android.feature.geofence.domain.model.GeofenceExecutionRecord
import com.elymbot.android.feature.geofence.domain.model.GeofenceRegion
import com.elymbot.android.feature.geofence.domain.model.GeofenceRule
import com.elymbot.android.feature.geofence.domain.model.GeofenceRuleStatus
import com.elymbot.android.feature.geofence.domain.model.GeofenceTransition
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceActionExecutionContext
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceActionExecutionResult
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceActionExecutorPort
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class GeofenceTransitionProcessingSummary(
    val completedCount: Int = 0,
    val throttledCount: Int = 0,
    val failedCount: Int = 0,
)

class GeofenceTransitionProcessor @Inject constructor(
    private val repository: GeofenceRuleRepositoryPort,
    private val actionExecutor: GeofenceActionExecutorPort,
    private val runtimeLogger: RuntimeLogger,
) {
    private var clock: () -> Long = System::currentTimeMillis
    private var executionIdFactory: () -> String = { UUID.randomUUID().toString() }

    internal constructor(
        repository: GeofenceRuleRepositoryPort,
        clock: () -> Long,
        executionIdFactory: () -> String,
        actionExecutor: GeofenceActionExecutorPort = NoOpGeofenceActionExecutor,
        runtimeLogger: RuntimeLogger = RuntimeLogger.noop(),
    ) : this(repository = repository, actionExecutor = actionExecutor, runtimeLogger = runtimeLogger) {
        this.clock = clock
        this.executionIdFactory = executionIdFactory
    }

    suspend fun processTransition(
        transition: GeofenceTransition,
        geofenceRequestIds: List<String>,
        occurredAtMillis: Long,
    ): GeofenceTransitionProcessingSummary = withContext(Dispatchers.IO) {
        var completed = 0
        var throttled = 0
        var failed = 0
        geofenceRequestIds.distinct().forEach { requestId ->
            val result = processOne(transition, requestId, occurredAtMillis)
            when (result) {
                ProcessingResult.COMPLETED -> completed += 1
                ProcessingResult.THROTTLED -> throttled += 1
                ProcessingResult.FAILED -> failed += 1
            }
        }
        GeofenceTransitionProcessingSummary(
            completedCount = completed,
            throttledCount = throttled,
            failedCount = failed,
        )
    }

    private suspend fun processOne(
        transition: GeofenceTransition,
        requestId: String,
        occurredAtMillis: Long,
    ): ProcessingResult {
        val decoded = GeofenceRequestIdCodec.decode(requestId)
        if (decoded == null) {
            runtimeLogger.append(
                "audit=geofence_transition reason=${GeofenceExecutionFailureCodes.INVALID_REQUEST_ID} " +
                    "transition=${transition.persistedValue} requestId=$requestId",
            )
            return ProcessingResult.FAILED
        }
        val rule = repository.getRule(decoded.ruleId)
        if (rule == null) {
            recordFailedAudit(
                decoded = decoded,
                transition = transition,
                requestId = requestId,
                occurredAtMillis = occurredAtMillis,
                configId = AUDIT_CONFIG_ID,
                errorCode = GeofenceExecutionFailureCodes.MISSING_RULE,
                errorMessage = "geofence rule is missing",
            )
            return ProcessingResult.FAILED
        }
        val ruleBindings = repository.listAllConfigBindings()
            .filter { candidate -> candidate.ruleId == rule.ruleId }
        val region = rule.regions.firstOrNull { it.regionId == decoded.regionId }
        if (region == null) {
            recordFailedAudit(
                decoded = decoded,
                transition = transition,
                requestId = requestId,
                occurredAtMillis = occurredAtMillis,
                configId = ruleBindings.auditConfigId(),
                errorCode = GeofenceExecutionFailureCodes.MISSING_REGION,
                errorMessage = "geofence region is missing",
            )
            return ProcessingResult.FAILED
        }
        val binding = ruleBindings
            .filter(ConfigGeofenceBinding::enabled)
            .sortedWith(compareBy<ConfigGeofenceBinding> { it.sortIndex }.thenBy { it.configId })
            .firstOrNull()
        if (binding == null) {
            recordFailedAudit(
                decoded = decoded,
                transition = transition,
                requestId = requestId,
                occurredAtMillis = occurredAtMillis,
                configId = ruleBindings.auditConfigId(),
                errorCode = GeofenceExecutionFailureCodes.MISSING_BINDING,
                errorMessage = "enabled geofence binding is missing",
            )
            return ProcessingResult.FAILED
        }
        val startedAt = clock()
        val previousRecord = repository.latestExecutionRecord(rule.ruleId)
        val executionId = executionIdFactory()
        val startRecord = geofenceExecutionRecord(
            executionId = executionId,
            rule = rule,
            region = region,
            binding = binding,
            transition = transition,
            startedAt = startedAt,
            status = STATUS_STARTED,
            occurredAtMillis = occurredAtMillis,
        )
        repository.recordExecution(startRecord)

        val completedAt = clock()
        val preflightFailure = when {
            !rule.enabled || rule.status == GeofenceRuleStatus.PAUSED ->
                startRecord.complete(completedAt, STATUS_FAILED, "rule_disabled")
            !rule.allows(transition) ->
                startRecord.complete(completedAt, STATUS_FAILED, "transition_disabled")
            previousRecord != null &&
                rule.minimumTriggerIntervalMillis > 0L &&
                startedAt - previousRecord.startedAt < rule.minimumTriggerIntervalMillis ->
                startRecord.complete(completedAt, STATUS_THROTTLED, "minimum_interval")
            else -> null
        }
        val completion = preflightFailure ?: run {
            val actionResult = actionExecutor.execute(
                GeofenceActionExecutionContext(
                    rule = rule,
                    region = region,
                    binding = binding,
                    transition = transition,
                    occurredAtMillis = occurredAtMillis,
                ),
            )
            startRecord.complete(
                completedAt = clock(),
                status = if (actionResult.success) STATUS_COMPLETE else STATUS_FAILED,
                errorCode = actionResult.errorCode,
                errorMessage = actionResult.errorMessage,
                deliverySummary = actionResult.deliverySummary,
            )
        }
        repository.recordExecution(completion)
        if (completion.status == STATUS_COMPLETE) {
            repository.getRule(rule.ruleId)?.let { latestRule ->
                repository.updateRule(latestRule.copy(lastTriggeredAt = occurredAtMillis))
            }
            return ProcessingResult.COMPLETED
        }
        return if (completion.status == STATUS_THROTTLED) {
            ProcessingResult.THROTTLED
        } else {
            ProcessingResult.FAILED
        }
    }

    private suspend fun recordFailedAudit(
        decoded: DecodedGeofenceRequestId,
        transition: GeofenceTransition,
        requestId: String,
        occurredAtMillis: Long,
        configId: String,
        errorCode: String,
        errorMessage: String,
    ) {
        val failedAt = clock()
        val record = GeofenceExecutionRecord(
            executionId = executionIdFactory(),
            ruleId = decoded.ruleId,
            regionId = decoded.regionId,
            configId = configId,
            transition = transition,
            startedAt = failedAt,
            completedAt = failedAt,
            status = STATUS_FAILED,
            errorCode = errorCode,
            errorMessage = errorMessage,
            deliverySummary = "geofence transition ignored: $errorCode",
            locationSnapshotJson = buildFlatJson(
                "regionId" to decoded.regionId,
            ),
            triggerPayloadJson = buildFlatJson(
                "ruleId" to decoded.ruleId,
                "regionId" to decoded.regionId,
                "transition" to transition.persistedValue,
                "occurredAt" to occurredAtMillis.toString(),
                "requestId" to requestId,
                "audit" to "stale_trigger",
            ),
        )
        val recordResult = runCatching {
            repository.recordExecution(record)
        }
        recordResult.onFailure { error ->
            runtimeLogger.append(
                "audit=geofence_transition reason=$errorCode record=failed " +
                    "ruleId=${decoded.ruleId} regionId=${decoded.regionId} error=${error.javaClass.simpleName}",
            )
        }
        if (recordResult.isSuccess) {
            runtimeLogger.append(
                "audit=geofence_transition reason=$errorCode record=written " +
                    "ruleId=${decoded.ruleId} regionId=${decoded.regionId}",
            )
        }
    }

    private fun List<ConfigGeofenceBinding>.auditConfigId(): String =
        sortedWith(compareBy<ConfigGeofenceBinding> { it.sortIndex }.thenBy { it.configId })
            .firstOrNull()
            ?.configId
            ?: AUDIT_CONFIG_ID

    private fun GeofenceRule.allows(transition: GeofenceTransition): Boolean =
        when (transition) {
            GeofenceTransition.ENTER -> triggerEnter
            GeofenceTransition.EXIT -> triggerExit
            GeofenceTransition.DWELL -> triggerDwell
        }

    private fun GeofenceExecutionRecord.complete(
        completedAt: Long,
        status: String,
        errorCode: String,
        errorMessage: String = "",
        deliverySummary: String = "",
    ): GeofenceExecutionRecord =
        copy(
            completedAt = completedAt,
            status = status,
            errorCode = errorCode,
            errorMessage = errorMessage,
            deliverySummary = if (status == STATUS_COMPLETE) {
                deliverySummary.ifBlank { "geofence action completed" }
            } else {
                deliverySummary.ifBlank { this.deliverySummary }
            },
        )

    private enum class ProcessingResult {
        COMPLETED,
        THROTTLED,
        FAILED,
    }

    private companion object {
        const val STATUS_STARTED = "started"
        const val STATUS_COMPLETE = "complete"
        const val STATUS_THROTTLED = "throttled"
        const val STATUS_FAILED = "failed"
        const val AUDIT_CONFIG_ID = "geofence-audit"
    }
}

private object NoOpGeofenceActionExecutor : GeofenceActionExecutorPort {
    override suspend fun execute(context: GeofenceActionExecutionContext): GeofenceActionExecutionResult =
        GeofenceActionExecutionResult.success("geofence action completed")
}

internal fun geofenceExecutionRecord(
    executionId: String,
    rule: GeofenceRule = GeofenceRule(ruleId = "rule-1", name = "Rule", triggerEnter = true, actionPrompt = "Prompt"),
    region: GeofenceRegion = GeofenceRegion(
        regionId = "region-1",
        ruleId = rule.ruleId,
        label = "Region",
        latitude = 31.2304,
        longitude = 121.4737,
        radiusMeters = 100f,
    ),
    binding: ConfigGeofenceBinding = ConfigGeofenceBinding(configId = "config-1", ruleId = rule.ruleId, enabled = true),
    transition: GeofenceTransition = GeofenceTransition.ENTER,
    startedAt: Long,
    completedAt: Long = 0L,
    status: String = "started",
    occurredAtMillis: Long = startedAt,
): GeofenceExecutionRecord =
    GeofenceExecutionRecord(
        executionId = executionId,
        ruleId = rule.ruleId,
        regionId = region.regionId,
        configId = binding.configId,
        transition = transition,
        startedAt = startedAt,
        completedAt = completedAt,
        status = status,
        locationSnapshotJson = buildFlatJson(
            "regionId" to region.regionId,
            "regionLabel" to region.label,
            "latitude" to region.latitude.toString(),
            "longitude" to region.longitude.toString(),
            "radiusMeters" to region.radiusMeters.toString(),
        ),
        triggerPayloadJson = buildFlatJson(
            "ruleId" to rule.ruleId,
            "ruleName" to rule.name,
            "regionId" to region.regionId,
            "regionLabel" to region.label,
            "transition" to transition.persistedValue,
            "latitude" to region.latitude.toString(),
            "longitude" to region.longitude.toString(),
            "radiusMeters" to region.radiusMeters.toString(),
            "occurredAt" to occurredAtMillis.toString(),
            "configId" to binding.configId,
        ),
    )

private fun buildFlatJson(vararg entries: Pair<String, String>): String =
    entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
        """"${key.escapeJson()}":"${value.escapeJson()}""""
    }

private fun String.escapeJson(): String =
    replace("\\", "\\\\").replace("\"", "\\\"")
