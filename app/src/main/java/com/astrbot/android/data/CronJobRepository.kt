package com.astrbot.android.data

import android.content.Context
import com.astrbot.android.data.db.AstrBotDatabase
import com.astrbot.android.data.db.CronJobEntity
import com.astrbot.android.data.db.cron.CronJobDao
import com.astrbot.android.model.CronJob
import com.astrbot.android.runtime.RuntimeLogRepository
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object CronJobRepository {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initialized = AtomicBoolean(false)
    private var dao: CronJobDao? = null

    private val _jobs = MutableStateFlow<List<CronJob>>(emptyList())
    val jobs: StateFlow<List<CronJob>> = _jobs.asStateFlow()

    fun initialize(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        dao = AstrBotDatabase.get(context).cronJobDao()
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
        RuntimeLogRepository.append("CronJob created: jobId=${job.jobId} name=${job.name}")
        return job
    }

    suspend fun update(job: CronJob): CronJob {
        val updated = job.copy(updatedAt = System.currentTimeMillis())
        requireDao().upsert(updated.toEntity())
        return updated
    }

    suspend fun delete(jobId: String) {
        requireDao().deleteByJobId(jobId)
        RuntimeLogRepository.append("CronJob deleted: jobId=$jobId")
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

    private fun requireDao(): CronJobDao {
        return requireNotNull(dao) { "CronJobRepository not initialized. Call initialize(context) first." }
    }
}

private fun CronJobEntity.toDomain(): CronJob {
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
        status = status,
        lastRunAt = lastRunAt,
        nextRunTime = nextRunTime,
        lastError = lastError,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

private fun CronJob.toEntity(): CronJobEntity {
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
        status = status,
        lastRunAt = lastRunAt,
        nextRunTime = nextRunTime,
        lastError = lastError,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
