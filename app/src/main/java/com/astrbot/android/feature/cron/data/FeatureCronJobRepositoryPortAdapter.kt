package com.astrbot.android.feature.cron.data

import com.astrbot.android.feature.cron.domain.CronJobRepositoryPort
import com.astrbot.android.model.CronJob
import com.astrbot.android.model.CronJobExecutionRecord
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow

@Singleton
class FeatureCronJobRepositoryPortAdapter @Inject constructor(
    private val repository: FeatureCronJobRepositoryStore,
) : CronJobRepositoryPort {

    override val jobs: StateFlow<List<CronJob>>
        get() = repository.jobs

    override suspend fun create(job: CronJob): CronJob =
        repository.create(job)

    override suspend fun update(job: CronJob): CronJob =
        repository.update(job)

    override suspend fun delete(jobId: String) =
        repository.delete(jobId)

    override suspend fun getByJobId(jobId: String): CronJob? =
        repository.getByJobId(jobId)

    override suspend fun listAll(): List<CronJob> =
        repository.listAll()

    override suspend fun listEnabled(): List<CronJob> =
        repository.listEnabled()

    override suspend fun updateStatus(jobId: String, status: String, lastRunAt: Long?, lastError: String?) =
        repository.updateStatus(jobId, status, lastRunAt, lastError)

    override suspend fun recordExecutionStarted(record: CronJobExecutionRecord): CronJobExecutionRecord =
        repository.recordExecutionStarted(record)

    override suspend fun updateExecutionRecord(record: CronJobExecutionRecord): CronJobExecutionRecord =
        repository.updateExecutionRecord(record)

    override suspend fun listRecentExecutionRecords(jobId: String, limit: Int): List<CronJobExecutionRecord> =
        repository.listRecentExecutionRecords(jobId, limit)

    override suspend fun latestExecutionRecord(jobId: String): CronJobExecutionRecord? =
        repository.latestExecutionRecord(jobId)
}
