package com.astrbot.android.feature.plugin.runtime.toolsource

import com.astrbot.android.feature.cron.domain.CronJobRepositoryPort
import com.astrbot.android.feature.cron.domain.CronJobRunNowPort
import com.astrbot.android.feature.cron.domain.CronSchedulerPort
import com.astrbot.android.model.CronJob
import com.astrbot.android.model.CronJobExecutionRecord
import com.astrbot.android.core.common.logging.AppLogger
import com.astrbot.android.core.runtime.context.RuntimePlatform
import com.astrbot.android.feature.cron.runtime.CronExpressionParser
import com.astrbot.android.feature.cron.runtime.CronJobScheduler
import com.astrbot.android.feature.cron.runtime.ActiveCapabilityPromptStrings
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider
import org.json.JSONArray
import org.json.JSONObject

data class ActiveCapabilityCreateTaskRequest(
    val payload: Map<String, Any?>,
    val metadata: Map<String, Any?>?,
    val toolSourceContext: ToolSourceContext?,
    val targetContext: ActiveCapabilityTargetContext? = null,
)

sealed interface ActiveCapabilityTaskCreation {
    data class Created(val job: CronJob) : ActiveCapabilityTaskCreation
    data class Failed(val error: ActiveCapabilityStructuredError) : ActiveCapabilityTaskCreation
}

data class ActiveCapabilityStructuredError(
    val code: String,
    val message: String,
    val missingFields: List<String> = emptyList(),
    val retryable: Boolean = false,
)

class ActiveCapabilityRuntimeFacade(
    private val repository: CronJobRepositoryPort,
    private val scheduler: CronSchedulerPort,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
    private val naturalLanguageParser: ActiveCapabilityNaturalLanguageParser = ActiveCapabilityNaturalLanguageParser(),
    private val promptStrings: ActiveCapabilityPromptStrings,
    private val runNowPort: CronJobRunNowPort? = null,
    private val runNowPortProvider: () -> CronJobRunNowPort? = { runNowPort },
) {
    @Inject
    constructor(
        repository: CronJobRepositoryPort,
        scheduler: CronSchedulerPort,
        naturalLanguageParser: ActiveCapabilityNaturalLanguageParser,
        promptStrings: ActiveCapabilityPromptStrings,
        runNowPortProvider: Provider<CronJobRunNowPort>,
    ) : this(
        repository = repository,
        scheduler = scheduler,
        clock = { System.currentTimeMillis() },
        idGenerator = { UUID.randomUUID().toString() },
        naturalLanguageParser = naturalLanguageParser,
        promptStrings = promptStrings,
        runNowPortProvider = { runNowPortProvider.get() },
    )

    suspend fun createFutureTask(request: ActiveCapabilityCreateTaskRequest): ActiveCapabilityTaskCreation {
        val now = clock()
        val name = request.payload.stringValue("name").ifBlank { promptStrings.defaultTaskName }
        val note = request.payload.stringValue("note")
        val hostRawText = request.metadata.hostRawText()
        val payloadSummary = request.payload.toCompactLogString()
        if (note.isBlank()) {
            val failure = ActiveCapabilityTaskCreation.Failed(
                ActiveCapabilityStructuredError(
                    code = "missing_note",
                    message = promptStrings.missingNoteMessage,
                    missingFields = listOf("note"),
                    retryable = false,
                ),
            )
            logCreateFailure(failure.error, payloadSummary, hostRawText)
            return failure
        }
        val timezone = request.payload.stringValue("timezone").ifBlank { ZoneId.systemDefault().id }
        val schedule = resolveSchedule(
            request = request,
            now = now,
            timezone = timezone,
        )
        if (schedule == null) {
            val failure = ActiveCapabilityTaskCreation.Failed(
                ActiveCapabilityStructuredError(
                    code = "invalid_schedule",
                    message = promptStrings.invalidScheduleMessage,
                    retryable = false,
                ),
            )
            logCreateFailure(failure.error, payloadSummary, hostRawText)
            return failure
        }
        val adjustedSchedule = resolvePastSchedulePolicy(
            schedule = schedule,
            now = now,
            timezone = timezone,
            allowPastImmediate = request.payload.booleanValue("allow_past_immediate") ?: false,
        )
        if (adjustedSchedule == null) {
            val failure = ActiveCapabilityTaskCreation.Failed(
                ActiveCapabilityStructuredError(
                    code = "past_schedule",
                    message = promptStrings.pastScheduleMessage,
                    retryable = false,
                ),
            )
            logCreateFailure(failure.error, payloadSummary, hostRawText)
            return failure
        }
        val cronExpression = adjustedSchedule.cronExpression
        val runAt = adjustedSchedule.runAt
        val runOnce = when {
            cronExpression.isNotBlank() -> false
            runAt.isNotBlank() -> true
            else -> request.payload.booleanValue("run_once") ?: false
        }
        val enabled = request.payload.booleanValue("enabled") ?: true
        val target = request.targetContext ?: ActiveCapabilityTargetContext.resolve(request)
        val missingFields = target.missingRequiredFields()

        if (missingFields.isNotEmpty()) {
            val failure = ActiveCapabilityTaskCreation.Failed(
                ActiveCapabilityStructuredError(
                    code = "missing_context",
                    message = promptStrings.missingContextMessage(missingFields.joinToString()),
                    missingFields = missingFields,
                    retryable = false,
                ),
            )
            logCreateFailure(failure.error, payloadSummary, hostRawText)
            return failure
        }

        val jobId = idGenerator()
        val payloadJson = buildPayloadJson(
            note = note,
            runAt = runAt,
            timezone = timezone,
            enabled = enabled,
            target = target,
        )
        val job = CronJob(
            jobId = jobId,
            name = name,
            description = note,
            jobType = "active_agent",
            cronExpression = if (runAt.isNotBlank()) "" else cronExpression,
            timezone = timezone,
            payloadJson = payloadJson,
            enabled = enabled,
            runOnce = runOnce,
            platform = target.platform,
            conversationId = target.conversationId,
            botId = target.botId,
            configProfileId = target.configProfileId,
            personaId = target.personaId,
            providerId = target.providerId,
            origin = target.origin,
            status = if (enabled) "scheduled" else "paused",
            nextRunTime = adjustedSchedule.nextRunTime,
            createdAt = now,
            updatedAt = now,
        )
        val created = repository.create(job)
        if (created.enabled) scheduler.schedule(created)
        AppLogger.append(
            "ActiveCapability: create_future_task success jobId=${created.jobId} " +
                "nextRun=${created.nextRunTime.toIsoStringOrBlank()} source=${adjustedSchedule.source} " +
                "payload=$payloadSummary rawText=${hostRawText.toQuotedLogValue()}",
        )
        return ActiveCapabilityTaskCreation.Created(created)
    }

    suspend fun deleteFutureTask(jobId: String): JSONObject {
        if (jobId.isBlank()) {
            return structuredErrorJson(
                ActiveCapabilityStructuredError(
                    code = "missing_job_id",
                    message = promptStrings.deleteMissingJobIdMessage,
                    missingFields = listOf("job_id"),
                ),
            )
        }
        val existing = repository.getByJobId(jobId)
            ?: return structuredErrorJson(
                ActiveCapabilityStructuredError(
                    code = "not_found",
                    message = promptStrings.taskNotFoundMessage(jobId),
                ),
            )
        repository.delete(jobId)
        scheduler.cancel(jobId)
        return JSONObject().apply {
            put("success", true)
            put("deleted_job_id", jobId)
            put("deleted_name", existing.name)
        }
    }

    suspend fun listFutureTasks(): JSONObject {
        val arr = JSONArray()
        repository.listAll().forEach { job ->
            val latest = repository.latestExecutionRecord(job.jobId)
            arr.put(JSONObject().apply {
                put("job_id", job.jobId)
                put("name", job.name)
                put("description", job.description)
                put("job_type", job.jobType)
                put("cron_expression", job.cronExpression)
                put("timezone", job.timezone)
                put("enabled", job.enabled)
                put("run_once", job.runOnce)
                put("platform", job.platform)
                put("conversation_id", job.conversationId)
                put("bot_id", job.botId)
                put("config_profile_id", job.configProfileId)
                put("persona_id", job.personaId)
                put("provider_id", job.providerId)
                put("origin", job.origin)
                put("status", job.status)
                put("next_run_time", job.nextRunTime.toIsoStringOrBlank())
                put("last_run_at", job.lastRunAt.toIsoStringOrBlank())
                put("execution_summary", latest?.toJson() ?: JSONObject())
                put("last_error", latest?.takeIf { it.errorMessage.isNotBlank() }?.errorMessage ?: job.lastError)
            })
        }
        return JSONObject().apply {
            put("count", arr.length())
            put("tasks", arr)
        }
    }

    suspend fun updateFutureTask(payload: Map<String, Any?>): JSONObject {
        val jobId = payload.stringValue("job_id")
        if (jobId.isBlank()) {
            return structuredErrorJson(
                ActiveCapabilityStructuredError(
                    code = "missing_job_id",
                    message = promptStrings.updateMissingJobIdMessage,
                    missingFields = listOf("job_id"),
                ),
            )
        }
        val existing = repository.getByJobId(jobId)
            ?: return structuredErrorJson(
                ActiveCapabilityStructuredError(
                    code = "not_found",
                    message = promptStrings.taskNotFoundMessage(jobId),
                ),
            )

        val now = clock()
        val updated = repository.update(existing.applyConservativeUpdates(payload, now))
        if (updated.enabled) {
            scheduler.schedule(updated)
        } else {
            scheduler.cancel(updated.jobId)
        }
        return updated.toManagementJson()
    }

    suspend fun pauseFutureTask(jobId: String): JSONObject {
        if (jobId.isBlank()) {
            return structuredErrorJson(
                ActiveCapabilityStructuredError(
                    code = "missing_job_id",
                    message = promptStrings.pauseMissingJobIdMessage,
                    missingFields = listOf("job_id"),
                ),
            )
        }
        val existing = repository.getByJobId(jobId)
            ?: return structuredErrorJson(
                ActiveCapabilityStructuredError(
                    code = "not_found",
                    message = promptStrings.taskNotFoundMessage(jobId),
                ),
            )
        val updated = repository.update(
            existing.copy(
                enabled = false,
                status = "paused",
                updatedAt = clock(),
            ),
        )
        scheduler.cancel(jobId)
        return updated.toManagementJson()
    }

    suspend fun resumeFutureTask(jobId: String): JSONObject {
        if (jobId.isBlank()) {
            return structuredErrorJson(
                ActiveCapabilityStructuredError(
                    code = "missing_job_id",
                    message = promptStrings.resumeMissingJobIdMessage,
                    missingFields = listOf("job_id"),
                ),
            )
        }
        val existing = repository.getByJobId(jobId)
            ?: return structuredErrorJson(
                ActiveCapabilityStructuredError(
                    code = "not_found",
                    message = promptStrings.taskNotFoundMessage(jobId),
                ),
            )
        val updated = repository.update(
            existing.copy(
                enabled = true,
                status = "scheduled",
                updatedAt = clock(),
            ),
        )
        scheduler.schedule(updated)
        return updated.toManagementJson()
    }

    suspend fun listFutureTaskRuns(jobId: String, limit: Int = 5): JSONObject {
        if (jobId.isBlank()) {
            return structuredErrorJson(
                ActiveCapabilityStructuredError(
                    code = "missing_job_id",
                    message = promptStrings.listRunsMissingJobIdMessage,
                    missingFields = listOf("job_id"),
                ),
            )
        }
        val normalizedLimit = limit.coerceIn(1, 50)
        val runs = repository.listRecentExecutionRecords(jobId, normalizedLimit)
        return JSONObject().apply {
            put("success", true)
            put("job_id", jobId)
            put("count", runs.size)
            put("runs", JSONArray().apply {
                runs.forEach { record -> put(record.toJson()) }
            })
        }
    }

    suspend fun runFutureTaskNow(jobId: String): JSONObject {
        if (jobId.isBlank()) {
            return structuredErrorJson(
                ActiveCapabilityStructuredError(
                    code = "missing_job_id",
                    message = promptStrings.runNowMissingJobIdMessage,
                    missingFields = listOf("job_id"),
                ),
            )
        }
        val result = runNowPortProvider()?.runNow(jobId)
            ?: return structuredErrorJson(
                ActiveCapabilityStructuredError(
                    code = "run_now_unavailable",
                    message = promptStrings.runNowUnavailableMessage,
                    retryable = false,
                ),
            )
        return JSONObject().apply {
            put("success", result.success)
            put("job_id", jobId)
            put("status", result.status)
            put("message", result.message)
            if (result.errorCode.isNotBlank()) put("error_code", result.errorCode)
        }
    }

    fun creationToJson(result: ActiveCapabilityTaskCreation): JSONObject {
        return when (result) {
            is ActiveCapabilityTaskCreation.Created -> result.job.toCreatedJson()
            is ActiveCapabilityTaskCreation.Failed -> structuredErrorJson(result.error)
        }
    }

    private fun buildPayloadJson(
        note: String,
        runAt: String,
        timezone: String,
        enabled: Boolean,
        target: ActiveCapabilityTargetContext,
    ): String {
        val targetJson = target.toJson()
        return JSONObject().apply {
            put("note", note)
            if (runAt.isNotBlank()) put("run_at", runAt)
            put("timezone", timezone)
            put("enabled", enabled)
            put("session", target.conversationId)
            put("target", targetJson)
            put("platform", target.platform)
            put("conversation_id", target.conversationId)
            put("bot_id", target.botId)
            put("config_profile_id", target.configProfileId)
            put("persona_id", target.personaId)
            put("provider_id", target.providerId)
            put("origin", target.origin)
        }.toString()
    }

    private fun CronJob.toCreatedJson(): JSONObject {
        return JSONObject().apply {
            put("success", true)
            put("job_id", jobId)
            put("name", name)
            put("next_run_time", nextRunTime.toIsoStringOrBlank())
            put("run_once", runOnce)
            put("timezone", timezone)
            put("enabled", enabled)
            put("platform", platform)
            put("conversation_id", conversationId)
            put("bot_id", botId)
            put("config_profile_id", configProfileId)
            put("persona_id", personaId)
            put("provider_id", providerId)
            put("origin", origin)
        }
    }

    private fun CronJob.toManagementJson(): JSONObject {
        return JSONObject().apply {
            put("success", true)
            put("job_id", jobId)
            put("name", name)
            put("enabled", enabled)
            put("status", status)
            put("next_run_time", nextRunTime.toIsoStringOrBlank())
            put("updated_at", updatedAt.toIsoStringOrBlank())
        }
    }

    private fun CronJob.applyConservativeUpdates(payload: Map<String, Any?>, now: Long): CronJob {
        val requestedStatus = payload.stringValue("status")
        val requestedEnabled = payload.booleanValue("enabled")
        val effectiveEnabled = when {
            requestedEnabled != null -> requestedEnabled
            requestedStatus.equals("paused", ignoreCase = true) -> false
            requestedStatus.equals("scheduled", ignoreCase = true) -> true
            requestedStatus.equals("enabled", ignoreCase = true) -> true
            else -> enabled
        }
        val effectiveStatus = when {
            requestedStatus.isNotBlank() -> normalizeTaskStatus(requestedStatus, effectiveEnabled)
            !effectiveEnabled -> "paused"
            status == "paused" && effectiveEnabled -> "scheduled"
            else -> status
        }
        val updatedRunAt = payload.stringValue("run_at")
        val updatedCron = payload.stringValue("cron_expression")
        val updatedTimezone = payload.stringValue("timezone")
        val updatedNote = payload.stringValue("note")
        val updatedPayload = updatePayloadJson(
            source = payloadJson,
            note = updatedNote,
            runAt = updatedRunAt,
            cronExpression = updatedCron,
            timezone = updatedTimezone,
            enabled = payload.containsKey("enabled") || requestedStatus.isNotBlank(),
            enabledValue = effectiveEnabled,
        )
        val effectiveTimezone = updatedTimezone.ifBlank { timezone }
        val nextRun = when {
            updatedRunAt.isNotBlank() -> parseRunAtCandidate(updatedRunAt, now, effectiveTimezone)?.nextRunTime
            updatedCron.isNotBlank() -> CronExpressionParser.nextFireTime(updatedCron, now, effectiveTimezone)
            else -> nextRunTime
        }
        return copy(
            name = payload.stringValue("name").ifBlank { name },
            description = updatedNote.ifBlank { description },
            enabled = effectiveEnabled,
            status = effectiveStatus,
            cronExpression = updatedCron.ifBlank {
                if (updatedRunAt.isNotBlank()) "" else cronExpression
            },
            timezone = effectiveTimezone,
            payloadJson = updatedPayload,
            runOnce = when {
                updatedRunAt.isNotBlank() -> true
                updatedCron.isNotBlank() -> false
                else -> runOnce
            },
            nextRunTime = nextRun ?: nextRunTime,
            updatedAt = now,
        )
    }

    private fun CronJobExecutionRecord.toJson(): JSONObject {
        return JSONObject().apply {
            put("execution_id", executionId)
            put("status", status)
            put("started_at", startedAt.toIsoStringOrBlank())
            put("completed_at", completedAt.toIsoStringOrBlank())
            put("duration_ms", durationMs)
            put("attempt", attempt)
            put("trigger", trigger)
            put("error_code", errorCode)
            put("error_message", errorMessage)
            put("delivery_summary", deliverySummary)
        }
    }

    private fun structuredErrorJson(error: ActiveCapabilityStructuredError): JSONObject {
        return JSONObject().apply {
            put("success", false)
            put("error_code", error.code)
            put("message", error.message)
            put("retryable", error.retryable)
            put("missing_fields", JSONArray(error.missingFields))
        }
    }

    private fun resolveSchedule(
        request: ActiveCapabilityCreateTaskRequest,
        now: Long,
        timezone: String,
    ): ResolvedSchedule? {
        val explicitCron = request.payload.stringValue("cron_expression")
        if (explicitCron.isNotBlank()) {
            return ResolvedSchedule(
                cronExpression = explicitCron,
                runAt = "",
                nextRunTime = CronExpressionParser.nextFireTime(explicitCron, now, timezone),
                source = "cron_expression",
            )
        }

        val explicitRunAt = request.payload.stringValue("run_at")
        if (explicitRunAt.isNotBlank()) {
            parseRunAtCandidate(explicitRunAt, now, timezone)?.let { parsed ->
                return parsed.copy(source = "run_at")
            }
        }

        val inferredTextCandidates = listOfNotNull(
            request.metadata.hostRawText(),
            request.payload.stringValue("schedule").takeIf { it.isNotBlank() },
            request.payload.stringValue("when").takeIf { it.isNotBlank() },
            request.payload.stringValue("note").takeIf { it.isNotBlank() },
            request.payload.stringValue("name").takeIf { it.isNotBlank() },
        )
        for (candidate in inferredTextCandidates) {
            inferRunAtFromNaturalLanguage(candidate, now, timezone)?.let { inferred ->
                return inferred.copy(source = "natural_language")
            }
        }

        return null
    }

    private fun parseRunAtCandidate(
        candidate: String,
        now: Long,
        timezone: String,
    ): ResolvedSchedule? {
        return runCatching {
            val nextRunTime = OffsetDateTime.parse(candidate).toInstant().toEpochMilli()
            ResolvedSchedule(
                cronExpression = "",
                runAt = candidate,
                nextRunTime = nextRunTime,
                source = "run_at",
            )
        }.getOrNull() ?: inferRunAtFromNaturalLanguage(candidate, now, timezone)
    }

    private fun inferRunAtFromNaturalLanguage(
        text: String,
        now: Long,
        timezone: String,
    ): ResolvedSchedule? {
        return naturalLanguageParser.inferRunAt(text, now, timezone)?.toResolvedSchedule()
    }

    private fun resolvePastSchedulePolicy(
        schedule: ResolvedSchedule,
        now: Long,
        timezone: String,
        allowPastImmediate: Boolean,
    ): ResolvedSchedule? {
        if (schedule.nextRunTime >= now) return schedule
        if (!allowPastImmediate) return null
        val immediateRunAt = Instant.ofEpochMilli(now)
            .atZone(ZoneId.of(timezone))
            .toOffsetDateTime()
            .toString()
        return schedule.copy(
            cronExpression = "",
            runAt = immediateRunAt,
            nextRunTime = now,
            source = "${schedule.source}:past_immediate",
        )
    }

    private fun logCreateFailure(
        error: ActiveCapabilityStructuredError,
        payloadSummary: String,
        rawText: String?,
    ) {
        AppLogger.append(
            "ActiveCapability: create_future_task failed code=${error.code} message=${error.message} " +
                "missing=${error.missingFields.joinToString(prefix = "[", postfix = "]")} " +
                "payload=$payloadSummary rawText=${rawText.toQuotedLogValue()}",
        )
    }
}

private data class ResolvedSchedule(
    val cronExpression: String,
    val runAt: String,
    val nextRunTime: Long,
    val source: String,
)

private fun java.time.ZonedDateTime.toResolvedSchedule(): ResolvedSchedule {
    return ResolvedSchedule(
        cronExpression = "",
        runAt = toOffsetDateTime().toString(),
        nextRunTime = toInstant().toEpochMilli(),
        source = "natural_language",
    )
}

object ActiveCapabilityToolSchemas {
    fun createFutureTaskSchema(strings: ActiveCapabilityPromptStrings? = null): Map<String, Any?> {
        return mapOf(
            "type" to "object",
            "properties" to mapOf(
                "run_once" to mapOf("type" to "boolean", "description" to strings?.schemaCreateRunOnceDescription.orEmpty()),
                "name" to mapOf("type" to "string", "description" to strings?.schemaCreateNameDescription.orEmpty()),
                "note" to mapOf("type" to "string", "description" to strings?.schemaCreateNoteDescription.orEmpty()),
                "cron_expression" to mapOf("type" to "string", "description" to strings?.schemaCreateCronExpressionDescription.orEmpty()),
                "run_at" to mapOf("type" to "string", "description" to strings?.schemaCreateRunAtDescription.orEmpty()),
                "session" to mapOf("type" to "string", "description" to strings?.schemaCreateSessionDescription.orEmpty()),
                "timezone" to mapOf("type" to "string", "description" to strings?.schemaCreateTimezoneDescription.orEmpty()),
                "enabled" to mapOf("type" to "boolean", "description" to strings?.schemaCreateEnabledDescription.orEmpty()),
                "allow_past_immediate" to mapOf("type" to "boolean", "description" to strings?.schemaCreateAllowPastImmediateDescription.orEmpty()),
                "platform" to mapOf("type" to "string", "description" to strings?.schemaCreatePlatformDescription.orEmpty()),
                "conversation_id" to mapOf("type" to "string", "description" to strings?.schemaCreateConversationIdDescription.orEmpty()),
                "bot_id" to mapOf("type" to "string", "description" to strings?.schemaCreateBotIdDescription.orEmpty()),
                "config_profile_id" to mapOf("type" to "string", "description" to strings?.schemaCreateConfigProfileIdDescription.orEmpty()),
                "persona_id" to mapOf("type" to "string", "description" to strings?.schemaCreatePersonaIdDescription.orEmpty()),
                "provider_id" to mapOf("type" to "string", "description" to strings?.schemaCreateProviderIdDescription.orEmpty()),
                "origin" to mapOf("type" to "string", "description" to strings?.schemaCreateOriginDescription.orEmpty()),
            ),
            "required" to listOf("note"),
        )
    }
}

data class ActiveCapabilityTargetContext(
    val platform: String,
    val conversationId: String,
    val botId: String,
    val configProfileId: String,
    val personaId: String,
    val providerId: String,
    val origin: String,
) {
    fun missingRequiredFields(): List<String> {
        return buildList {
            if (platform.isBlank()) add("platform")
            if (conversationId.isBlank()) add("conversation_id")
            if (botId.isBlank()) add("bot_id")
            if (configProfileId.isBlank()) add("config_profile_id")
            if (providerId.isBlank()) add("provider_id")
        }
    }

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("platform", platform)
            put("conversation_id", conversationId)
            put("bot_id", botId)
            put("config_profile_id", configProfileId)
            put("persona_id", personaId)
            put("provider_id", providerId)
            put("origin", origin)
        }
    }

    companion object {
        fun resolve(request: ActiveCapabilityCreateTaskRequest): ActiveCapabilityTargetContext {
            val host = request.metadata?.mapValue("__host")
            val eventExtras = host?.mapValue("eventExtras")
            val toolSourceContext = request.toolSourceContext
            return ActiveCapabilityTargetContext(
                platform = normalizePlatformValue(
                    request.payload.stringValue("platform")
                        .ifBlank { host.stringValue("platformAdapterType") }
                        .ifBlank { toolSourceContext?.platform?.wireValue.orEmpty() },
                ),
                conversationId = request.payload.stringValue("conversation_id")
                    .ifBlank { request.payload.stringValue("session") }
                    .ifBlank { host.stringValue("conversationId") }
                    .ifBlank { toolSourceContext?.conversationId.orEmpty() },
                botId = request.payload.stringValue("bot_id")
                    .ifBlank { eventExtras.stringValue("botId") },
                configProfileId = request.payload.stringValue("config_profile_id")
                    .ifBlank { eventExtras.stringValue("configProfileId") }
                    .ifBlank { toolSourceContext?.configProfileId.orEmpty() },
                personaId = request.payload.stringValue("persona_id")
                    .ifBlank { eventExtras.stringValue("personaId") },
                providerId = request.payload.stringValue("provider_id")
                    .ifBlank { eventExtras.stringValue("providerId") },
                origin = request.payload.stringValue("origin").ifBlank { "tool" },
            )
        }
    }
}

private fun normalizePlatformValue(value: String): String {
    return when (value.trim().lowercase()) {
        "qq",
        "onebot",
        RuntimePlatform.QQ_ONEBOT.wireValue,
        -> RuntimePlatform.QQ_ONEBOT.wireValue
        RuntimePlatform.APP_CHAT.wireValue -> RuntimePlatform.APP_CHAT.wireValue
        else -> value.trim()
    }
}

private fun Map<*, *>?.mapValue(key: String): Map<String, Any?>? {
    @Suppress("UNCHECKED_CAST")
    return this?.get(key) as? Map<String, Any?>
}

private fun Map<String, Any?>?.hostRawText(): String? {
    val host = this.mapValue("__host") ?: return null
    return host.stringValue("rawText").ifBlank { host.stringValue("workingText") }.ifBlank { null }
}

private fun Map<*, *>?.stringValue(key: String): String {
    return (this?.get(key) as? String)?.trim().orEmpty()
}

private fun Map<String, Any?>.booleanValue(key: String): Boolean? {
    return this[key] as? Boolean
}

private fun Map<String, Any?>.toCompactLogString(): String {
    return entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
        "$key=${value.toCompactLogValue()}"
    }
}

private fun Any?.toCompactLogValue(): String {
    return when (this) {
        null -> "null"
        is String -> this.replace('\n', ' ').take(120).let { "\"$it\"" }
        else -> toString().replace('\n', ' ').take(120)
    }
}

private fun String?.toQuotedLogValue(): String {
    return this?.replace('\n', ' ')?.take(160)?.let { "\"$it\"" } ?: "null"
}

private fun Long.toIsoStringOrBlank(): String {
    return if (this > 0L) java.time.Instant.ofEpochMilli(this).toString() else ""
}

private fun normalizeTaskStatus(status: String, enabled: Boolean): String {
    return when (status.trim().lowercase()) {
        "pause",
        "paused",
        -> "paused"
        "resume",
        "resumed",
        "enable",
        "enabled",
        "scheduled",
        -> "scheduled"
        else -> status.trim().ifBlank { if (enabled) "scheduled" else "paused" }
    }
}

private fun updatePayloadJson(
    source: String,
    note: String,
    runAt: String,
    cronExpression: String,
    timezone: String,
    enabled: Boolean,
    enabledValue: Boolean,
): String {
    val json = runCatching { JSONObject(source) }.getOrDefault(JSONObject())
    if (note.isNotBlank()) json.put("note", note)
    if (runAt.isNotBlank()) json.put("run_at", runAt)
    if (cronExpression.isNotBlank()) json.put("cron_expression", cronExpression)
    if (timezone.isNotBlank()) json.put("timezone", timezone)
    if (enabled) json.put("enabled", enabledValue)
    return json.toString()
}

