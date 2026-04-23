package com.astrbot.android.feature.plugin.runtime.toolsource

import com.astrbot.android.feature.cron.domain.CronJobRepositoryPort
import com.astrbot.android.feature.cron.domain.CronSchedulerPort
import com.astrbot.android.model.CronJob
import com.astrbot.android.model.CronJobExecutionRecord
import com.astrbot.android.core.common.logging.AppLogger
import com.astrbot.android.core.runtime.context.RuntimePlatform
import com.astrbot.android.feature.cron.runtime.CronExpressionParser
import com.astrbot.android.feature.cron.runtime.CronJobScheduler
import java.time.Instant
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import javax.inject.Inject
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
) {
    @Inject
    constructor(
        repository: CronJobRepositoryPort,
        scheduler: CronSchedulerPort,
    ) : this(
        repository = repository,
        scheduler = scheduler,
        clock = { System.currentTimeMillis() },
        idGenerator = { UUID.randomUUID().toString() },
    )

    suspend fun createFutureTask(request: ActiveCapabilityCreateTaskRequest): ActiveCapabilityTaskCreation {
        val now = clock()
        val name = request.payload.stringValue("name").ifBlank { "Unnamed Task" }
        val note = request.payload.stringValue("note")
        val hostRawText = request.metadata.hostRawText()
        val payloadSummary = request.payload.toCompactLogString()
        if (note.isBlank()) {
            val failure = ActiveCapabilityTaskCreation.Failed(
                ActiveCapabilityStructuredError(
                    code = "missing_note",
                    message = "'note' is required for a future task and should describe the reminder or future action.",
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
                    message = "Either 'cron_expression' or 'run_at' must be provided. Relative time expressions such as '5 minutes later' are only supported when they can be inferred from the current request.",
                    retryable = false,
                ),
            )
            logCreateFailure(failure.error, payloadSummary, hostRawText)
            return failure
        }
        val cronExpression = schedule.cronExpression
        val runAt = schedule.runAt
        val runOnce = request.payload.booleanValue("run_once") ?: runAt.isNotBlank()
        val enabled = request.payload.booleanValue("enabled") ?: true
        val target = request.targetContext ?: ActiveCapabilityTargetContext.resolve(request)
        val missingFields = target.missingRequiredFields()

        if (missingFields.isNotEmpty()) {
            val failure = ActiveCapabilityTaskCreation.Failed(
                ActiveCapabilityStructuredError(
                    code = "missing_context",
                    message = "Scheduled task target is incomplete: ${missingFields.joinToString()}",
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
            nextRunTime = schedule.nextRunTime,
            createdAt = now,
            updatedAt = now,
        )
        val created = repository.create(job)
        if (created.enabled) scheduler.schedule(created)
        AppLogger.append(
            "ActiveCapability: create_future_task success jobId=${created.jobId} " +
                "nextRun=${created.nextRunTime.toIsoStringOrBlank()} source=${schedule.source} " +
                "payload=$payloadSummary rawText=${hostRawText.toQuotedLogValue()}",
        )
        return ActiveCapabilityTaskCreation.Created(created)
    }

    suspend fun deleteFutureTask(jobId: String): JSONObject {
        if (jobId.isBlank()) {
            return structuredErrorJson(
                ActiveCapabilityStructuredError(
                    code = "missing_job_id",
                    message = "'job_id' is required to delete a task.",
                    missingFields = listOf("job_id"),
                ),
            )
        }
        val existing = repository.getByJobId(jobId)
            ?: return structuredErrorJson(
                ActiveCapabilityStructuredError(
                    code = "not_found",
                    message = "Task with job_id '$jobId' not found.",
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
        val normalized = text.trim()
        if (normalized.isBlank()) return null
        val zone = ZoneId.of(timezone)
        val nowAtZone = Instant.ofEpochMilli(now).atZone(zone)

        relativeDelayMatchers.firstNotNullOfOrNull { matcher ->
            matcher(normalized, nowAtZone)
        }?.let { inferredAt ->
            return inferredAt.toResolvedSchedule()
        }

        inferTomorrowSchedule(normalized, nowAtZone)?.let { inferredAt ->
            return inferredAt.toResolvedSchedule()
        }

        return null
    }

    private fun inferTomorrowSchedule(
        text: String,
        nowAtZone: ZonedDateTime,
    ): ZonedDateTime? {
        val mentionsTomorrow = listOf("鏄庡ぉ", "鏄庢棭", "鏄庢櫄", "tomorrow").any { token ->
            text.contains(token, ignoreCase = true)
        }
        if (!mentionsTomorrow) return null
        val targetTime = when {
            text.contains("鏄庢櫄") || text.contains("鏅氫笂") || text.contains("浠婃櫄") || text.contains("evening", ignoreCase = true) -> LocalTime.of(20, 0)
            text.contains("涓崍") || text.contains("noon", ignoreCase = true) -> LocalTime.of(12, 0)
            text.contains("涓嬪崍") || text.contains("afternoon", ignoreCase = true) -> LocalTime.of(15, 0)
            text.contains("鏃╀笂") || text.contains("涓婂崍") || text.contains("鏄庢棭") || text.contains("morning", ignoreCase = true) -> LocalTime.of(9, 0)
            else -> LocalTime.of(9, 0)
        }
        return nowAtZone.plusDays(1).with(targetTime)
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

private val relativeDelayMatchers: List<(String, ZonedDateTime) -> ZonedDateTime?> = listOf(
    { text, now ->
        Regex("([零一二两俩三四五六七八九十百\\d]+)\\s*(分钟|min|mins|minute|minutes)后?", RegexOption.IGNORE_CASE)
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.toFlexibleLong()
            ?.let(now::plusMinutes)
    },
    { text, now ->
        Regex("([零一二两俩三四五六七八九十百\\d]+)\\s*(小时|hr|hrs|hour|hours)后?", RegexOption.IGNORE_CASE)
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.toFlexibleLong()
            ?.let(now::plusHours)
    },
    { text, now ->
        Regex("([零一二两俩三四五六七八九十百\\d]+)\\s*(天|day|days)后?", RegexOption.IGNORE_CASE)
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.toFlexibleLong()
            ?.let(now::plusDays)
    },
    { text, now ->
        Regex("in\\s+(\\d+)\\s*(min|mins|minute|minutes)", RegexOption.IGNORE_CASE)
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()
            ?.let(now::plusMinutes)
    },
    { text, now ->
        Regex("in\\s+(\\d+)\\s*(hr|hrs|hour|hours)", RegexOption.IGNORE_CASE)
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()
            ?.let(now::plusHours)
    },
)

private fun ZonedDateTime.toResolvedSchedule(): ResolvedSchedule {
    return ResolvedSchedule(
        cronExpression = "",
        runAt = toOffsetDateTime().toString(),
        nextRunTime = toInstant().toEpochMilli(),
        source = "natural_language",
    )
}

object ActiveCapabilityToolSchemas {
    fun createFutureTaskSchema(): Map<String, Any?> {
        return mapOf(
            "type" to "object",
            "properties" to mapOf(
                "run_once" to mapOf("type" to "boolean", "description" to "Run the task only once."),
                "name" to mapOf("type" to "string", "description" to "Optional short title for the task."),
                "note" to mapOf("type" to "string", "description" to "Core instruction for the reminder, timed follow-up, or future action."),
                "cron_expression" to mapOf("type" to "string", "description" to "Cron expression for recurring tasks."),
                "run_at" to mapOf("type" to "string", "description" to "ISO 8601 datetime for one-time execution."),
                "session" to mapOf("type" to "string", "description" to "Backward-compatible conversation/session id; usually auto-filled from the current runtime context."),
                "timezone" to mapOf("type" to "string", "description" to "IANA timezone, for example Asia/Shanghai."),
                "enabled" to mapOf("type" to "boolean", "description" to "Whether the task should be scheduled immediately."),
                "platform" to mapOf("type" to "string", "description" to "Runtime platform; usually auto-filled from the current runtime context."),
                "conversation_id" to mapOf("type" to "string", "description" to "Target conversation id; usually auto-filled from the current runtime context."),
                "bot_id" to mapOf("type" to "string", "description" to "Target bot id; usually auto-filled from the current runtime context."),
                "config_profile_id" to mapOf("type" to "string", "description" to "Target config profile id; usually auto-filled from the current runtime context."),
                "persona_id" to mapOf("type" to "string", "description" to "Optional persona id override; usually auto-filled from the current runtime context."),
                "provider_id" to mapOf("type" to "string", "description" to "Target provider id; usually auto-filled from the current runtime context."),
                "origin" to mapOf("type" to "string", "description" to "Creator origin, for example tool, api, or ui."),
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

private fun String.toFlexibleLong(): Long? {
    return toLongOrNull() ?: toSimpleChineseNumber()
}

private fun String.toSimpleChineseNumber(): Long? {
    val normalized = trim()
    if (normalized.isEmpty()) return null
    val digitMap = mapOf(
        '零' to 0,
        '一' to 1,
        '二' to 2,
        '两' to 2,
        '俩' to 2,
        '三' to 3,
        '四' to 4,
        '五' to 5,
        '六' to 6,
        '七' to 7,
        '八' to 8,
        '九' to 9,
    )
    if (normalized == "十") return 10L
    if ('十' in normalized) {
        val parts = normalized.split('十', limit = 2)
        val tens = parts[0].takeIf { it.isNotEmpty() }?.singleOrNull()?.let(digitMap::get) ?: 1
        val ones = parts.getOrNull(1)?.takeIf { it.isNotEmpty() }?.singleOrNull()?.let(digitMap::get) ?: 0
        return (tens * 10 + ones).toLong()
    }
    return normalized.singleOrNull()?.let(digitMap::get)?.toLong()
}

private fun Long.toIsoStringOrBlank(): String {
    return if (this > 0L) java.time.Instant.ofEpochMilli(this).toString() else ""
}

