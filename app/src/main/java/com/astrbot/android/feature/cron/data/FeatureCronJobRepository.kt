package com.astrbot.android.feature.cron.data

import kotlinx.coroutines.flow.collect

import android.content.Context
import com.astrbot.android.data.db.AstrBotDatabase
import com.astrbot.android.data.db.CronJobEntity
import com.astrbot.android.data.db.CronJobExecutionRecordEntity
import com.astrbot.android.data.db.cron.CronJobDao
import com.astrbot.android.data.db.cron.CronJobExecutionRecordDao
import com.astrbot.android.model.CronJob
import com.astrbot.android.model.CronJobExecutionRecord
import com.astrbot.android.core.common.logging.AppLogger
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

@Deprecated("Use CronJobRepositoryPort from feature/cron/domain. Direct access will be removed.")
object FeatureCronJobRepository {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initialized = AtomicBoolean(false)
    private var dao: CronJobDao? = null
    private var executionRecordDao: CronJobExecutionRecordDao? = null

    private val _jobs = MutableStateFlow<List<CronJob>>(emptyList())
    val jobs: StateFlow<List<CronJob>> = _jobs.asStateFlow()

    fun initialize(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        val database = AstrBotDatabase.get(context)
        dao = database.cronJobDao()
        executionRecordDao = database.cronJobExecutionRecordDao()
        repositoryScope.launch {
            dao!!.observeAll().collect { entities ->
                _jobs.value = entities.map { it.toDomain() }
            }
        }
    }

    suspend fun create(job: CronJob): CronJob {
        val now = System.currentTimeMillis()
        val entity = job.copy(
            createdAt = if (job.createdAt == 0L) now else job.createdAt,
            updatedAt = now,
        ).toEntity()
        requireDao().upsert(entity)
        AppLogger.append("CronJob created: jobId=${job.jobId} name=${job.name}")
        return job
    }

    suspend fun update(job: CronJob): CronJob {
        val updated = job.copy(updatedAt = System.currentTimeMillis())
        requireDao().upsert(updated.toEntity())
        return updated
    }

    suspend fun delete(jobId: String) {
        requireDao().deleteByJobId(jobId)
        AppLogger.append("CronJob deleted: jobId=$jobId")
    }

    suspend fun getByJobId(jobId: String): CronJob? {
        return requireDao().getByJobId(jobId)?.toDomain()
    }

    suspend fun listAll(): List<CronJob> {
        return requireDao().listAll().map { it.toDomain() }
    }

    suspend fun listEnabled(): List<CronJob> {
        return requireDao().listEnabled().map { it.toDomain() }
    }

    suspend fun updateStatus(jobId: String, status: String, lastRunAt: Long? = null, lastError: String? = null) {
        val existing = requireDao().getByJobId(jobId) ?: return
        requireDao().upsert(
            existing.copy(
                status = status,
                lastRunAt = lastRunAt ?: existing.lastRunAt,
                lastError = lastError ?: existing.lastError,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun recordExecutionStarted(record: CronJobExecutionRecord): CronJobExecutionRecord {
        requireExecutionRecordDao().upsert(record.toEntity())
        return record
    }

    suspend fun updateExecutionRecord(record: CronJobExecutionRecord): CronJobExecutionRecord {
        requireExecutionRecordDao().upsert(record.toEntity())
        return record
    }

    suspend fun listRecentExecutionRecords(jobId: String, limit: Int = 5): List<CronJobExecutionRecord> {
        return requireExecutionRecordDao().listRecentForJob(jobId, limit.coerceAtLeast(1)).map { it.toDomain() }
    }

    suspend fun latestExecutionRecord(jobId: String): CronJobExecutionRecord? {
        return requireExecutionRecordDao().latestForJob(jobId)?.toDomain()
    }

    private fun requireDao(): CronJobDao {
        return requireNotNull(dao) { "CronJobRepository not initialized. Call initialize(context) first." }
    }

    private fun requireExecutionRecordDao(): CronJobExecutionRecordDao {
        return requireNotNull(executionRecordDao) {
            "CronJobRepository execution records not initialized. Call initialize(context) first."
        }
    }
}

private fun CronJobEntity.toDomain(): CronJob {
    val legacyTarget = CronJobPersistedTarget.fromPayload(payloadJson)
    return CronJob(
        jobId = jobId,
        name = name,
        description = description,
        jobType = jobType,
        cronExpression = cronExpression,
        timezone = timezone,
        payloadJson = payloadJson,
        enabled = enabled,
        runOnce = runOnce,
        platform = platform.ifBlank { legacyTarget.platform },
        conversationId = conversationId.ifBlank { legacyTarget.conversationId },
        botId = botId.ifBlank { legacyTarget.botId },
        configProfileId = configProfileId.ifBlank { legacyTarget.configProfileId },
        personaId = personaId.ifBlank { legacyTarget.personaId },
        providerId = providerId.ifBlank { legacyTarget.providerId },
        origin = origin.ifBlank { legacyTarget.origin },
        status = status,
        lastRunAt = lastRunAt,
        nextRunTime = nextRunTime,
        lastError = lastError,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

private fun CronJob.toEntity(): CronJobEntity {
    val legacyTarget = CronJobPersistedTarget.fromPayload(payloadJson)
    return CronJobEntity(
        jobId = jobId,
        name = name,
        description = description,
        jobType = jobType,
        cronExpression = cronExpression,
        timezone = timezone,
        payloadJson = payloadJson,
        enabled = enabled,
        runOnce = runOnce,
        platform = platform.ifBlank { legacyTarget.platform },
        conversationId = conversationId.ifBlank { legacyTarget.conversationId },
        botId = botId.ifBlank { legacyTarget.botId },
        configProfileId = configProfileId.ifBlank { legacyTarget.configProfileId },
        personaId = personaId.ifBlank { legacyTarget.personaId },
        providerId = providerId.ifBlank { legacyTarget.providerId },
        origin = origin.ifBlank { legacyTarget.origin },
        status = status,
        lastRunAt = lastRunAt,
        nextRunTime = nextRunTime,
        lastError = lastError,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

private fun CronJobExecutionRecordEntity.toDomain(): CronJobExecutionRecord {
    return CronJobExecutionRecord(
        executionId = executionId,
        jobId = jobId,
        status = status,
        startedAt = startedAt,
        completedAt = completedAt,
        durationMs = durationMs,
        attempt = attempt,
        trigger = trigger,
        errorCode = errorCode,
        errorMessage = errorMessage,
        deliverySummary = deliverySummary,
    )
}

private fun CronJobExecutionRecord.toEntity(): CronJobExecutionRecordEntity {
    return CronJobExecutionRecordEntity(
        executionId = executionId,
        jobId = jobId,
        status = status,
        startedAt = startedAt,
        completedAt = completedAt,
        durationMs = durationMs,
        attempt = attempt,
        trigger = trigger,
        errorCode = errorCode,
        errorMessage = errorMessage,
        deliverySummary = deliverySummary,
    )
}

private data class CronJobPersistedTarget(
    val platform: String = "",
    val conversationId: String = "",
    val botId: String = "",
    val configProfileId: String = "",
    val personaId: String = "",
    val providerId: String = "",
    val origin: String = "",
) {
    companion object {
        fun fromPayload(payloadJson: String): CronJobPersistedTarget {
            return runCatching {
                val payload = JSONObject(payloadJson)
                val target = payload.optJSONObject("target")
                CronJobPersistedTarget(
                    platform = target.optTargetString(payload, "platform"),
                    conversationId = target.optTargetString(payload, "conversation_id")
                        .ifBlank { payload.optString("session", "") },
                    botId = target.optTargetString(payload, "bot_id"),
                    configProfileId = target.optTargetString(payload, "config_profile_id"),
                    personaId = target.optTargetString(payload, "persona_id"),
                    providerId = target.optTargetString(payload, "provider_id"),
                    origin = target.optTargetString(payload, "origin"),
                )
            }.getOrDefault(CronJobPersistedTarget())
        }

        private fun JSONObject?.optTargetString(root: JSONObject, key: String): String {
            return this?.optString(key, "")?.takeIf { it.isNotBlank() }
                ?: root.optString(key, "")
        }
    }
}

