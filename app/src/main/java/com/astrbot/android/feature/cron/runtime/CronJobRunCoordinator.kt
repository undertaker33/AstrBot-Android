package com.astrbot.android.feature.cron.runtime

import com.astrbot.android.feature.cron.domain.CronJobRepositoryPort
import com.astrbot.android.model.CronJob
import com.astrbot.android.model.CronJobExecutionRecord
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

fun interface ScheduledTaskExecutor {
    suspend fun execute(context: CronJobExecutionContext): CronJobDeliverySummary
}

interface CronRescheduler {
    fun schedule(job: CronJob)
}

class WorkManagerCronRescheduler(
    private val applicationContext: android.content.Context,
) : CronRescheduler {
    override fun schedule(job: CronJob) {
        CronJobScheduler.scheduleJob(applicationContext, job)
    }
}

enum class CronJobRunOutcome {
    Succeeded,
    Retry,
    Failed,
    Skipped,
}

data class CronJobDeliverySummary(
    val platform: String,
    val conversationId: String,
    val deliveredMessageCount: Int,
    val receiptIds: List<String> = emptyList(),
    val textPreview: String = "",
) {
    fun toJsonString(): String {
        return JSONObject().apply {
            put("platform", platform)
            put("conversation_id", conversationId)
            put("delivered_message_count", deliveredMessageCount)
            put("receipt_ids", JSONArray(receiptIds))
            put("text_preview", textPreview)
        }.toString()
    }
}

open class CronJobExecutionFailure(
    val code: String,
    val retryable: Boolean,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class MissingScheduledTaskContextException(
    missingFields: List<String>,
    jobId: String,
) : CronJobExecutionFailure(
    code = "missing_context",
    retryable = false,
    message = "Scheduled task missing context for job=$jobId: ${missingFields.joinToString()}",
)

class CronJobRunCoordinator(
    private val repository: CronJobRepositoryPort,
    private val executor: ScheduledTaskExecutor,
    private val scheduler: CronRescheduler,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val nextFireTime: (String, Long, String) -> Long = CronExpressionParser::nextFireTime,
    private val executionIdGenerator: () -> String = { UUID.randomUUID().toString() },
) {
    private val failureFuseThreshold = 3

    suspend fun runDueJob(
        jobId: String,
        attempt: Int,
        trigger: String,
    ): CronJobRunOutcome {
        val job = repository.getByJobId(jobId) ?: return CronJobRunOutcome.Failed
        if (!job.enabled) return CronJobRunOutcome.Skipped

        val startedAt = clock()
        val executionId = executionIdGenerator()
        val runningRecord = CronJobExecutionRecord(
            executionId = executionId,
            jobId = jobId,
            status = "RUNNING",
            startedAt = startedAt,
            attempt = attempt,
            trigger = trigger,
        )
        repository.recordExecutionStarted(runningRecord)
        repository.update(job.copy(status = "running", lastRunAt = startedAt))

        return try {
            val context = job.toExecutionContext()
            val missing = context.missingRequiredFields()
            if (missing.isNotEmpty()) throw MissingScheduledTaskContextException(missing, job.jobId)

            val summary = executor.execute(context)
            if (summary.deliveredMessageCount <= 0) {
                throw CronJobExecutionFailure(
                    code = "empty_delivery",
                    retryable = false,
                    message = "Scheduled task completed without delivering a user-facing message for job=${job.jobId}",
                )
            }
            val completedAt = clock()
            repository.updateExecutionRecord(
                runningRecord.copy(
                    status = "SUCCEEDED",
                    completedAt = completedAt,
                    durationMs = completedAt - startedAt,
                    deliverySummary = summary.toJsonString(),
                ),
            )
            completeJob(job, startedAt, completedAt)
            CronJobRunOutcome.Succeeded
        } catch (error: Throwable) {
            val completedAt = clock()
            val failure = error.toExecutionFailure()
            val failedRecord = repository.updateExecutionRecord(
                runningRecord.copy(
                    status = "FAILED",
                    completedAt = completedAt,
                    durationMs = completedAt - startedAt,
                    errorCode = failure.code,
                    errorMessage = failure.message.orEmpty(),
                ),
            )
            val consecutiveFailures = consecutiveFailureCount(job.jobId, failedRecord)
            val fused = consecutiveFailures >= failureFuseThreshold
            repository.update(
                job.copy(
                    enabled = if (fused) false else job.enabled,
                    status = if (fused) "unhealthy" else "failed",
                    lastRunAt = startedAt,
                    lastError = failure.message.orEmpty(),
                    updatedAt = completedAt,
                ),
            )
            if (!fused && failure.retryable) CronJobRunOutcome.Retry else CronJobRunOutcome.Failed
        }
    }

    private suspend fun completeJob(job: CronJob, startedAt: Long, completedAt: Long) {
        if (job.runOnce) {
            repository.delete(job.jobId)
            return
        }
        val nextFire = nextFireTime(job.cronExpression, completedAt, job.timezone)
        val updated = job.copy(
            status = "scheduled",
            lastRunAt = startedAt,
            nextRunTime = nextFire,
            lastError = "",
            updatedAt = completedAt,
        )
        repository.update(updated)
        scheduler.schedule(updated)
    }

    private fun Throwable.toExecutionFailure(): CronJobExecutionFailure {
        return when (this) {
            is CronJobExecutionFailure -> this
            else -> CronJobExecutionFailure(
                code = this::class.simpleName?.ifBlank { null } ?: "execution_failed",
                retryable = true,
                message = message ?: "Scheduled task execution failed",
                cause = this,
            )
        }
    }

    private suspend fun consecutiveFailureCount(
        jobId: String,
        currentRecord: CronJobExecutionRecord,
    ): Int {
        val recentRecords = (repository.listRecentExecutionRecords(jobId, failureFuseThreshold) + currentRecord)
            .distinctBy(CronJobExecutionRecord::executionId)
            .sortedByDescending(CronJobExecutionRecord::startedAt)
        return recentRecords.takeWhile { record -> record.status == "FAILED" }.size
    }
}

private fun CronJob.toExecutionContext(): CronJobExecutionContext {
    val payload = runCatching { JSONObject(payloadJson) }.getOrDefault(JSONObject())
    val target = payload.optJSONObject("target")
    return CronJobExecutionContext(
        jobId = jobId,
        name = name,
        description = description,
        jobType = jobType,
        note = payload.optString("note", description),
        sessionId = payload.optString("session", ""),
        platform = platform.ifBlank { target.optPayloadString(payload, "platform") },
        conversationId = conversationId.ifBlank {
            target.optPayloadString(payload, "conversation_id").ifBlank { payload.optString("session", "") }
        },
        botId = botId.ifBlank { target.optPayloadString(payload, "bot_id") },
        configProfileId = configProfileId.ifBlank { target.optPayloadString(payload, "config_profile_id") },
        personaId = personaId.ifBlank { target.optPayloadString(payload, "persona_id") },
        providerId = providerId.ifBlank { target.optPayloadString(payload, "provider_id") },
        origin = origin.ifBlank { target.optPayloadString(payload, "origin") },
        runOnce = runOnce,
        runAt = payload.optString("run_at", ""),
    )
}

private fun JSONObject?.optPayloadString(root: JSONObject, key: String): String {
    return this?.optString(key, "")?.takeIf { it.isNotBlank() } ?: root.optString(key, "")
}

private fun CronJobExecutionContext.missingRequiredFields(): List<String> {
    return buildList {
        if (platform.isBlank()) add("platform")
        if (conversationId.isBlank() && sessionId.isBlank()) add("conversation_id")
        if (botId.isBlank()) add("bot_id")
        if (configProfileId.isBlank()) add("config_profile_id")
        if (providerId.isBlank()) add("provider_id")
    }
}

