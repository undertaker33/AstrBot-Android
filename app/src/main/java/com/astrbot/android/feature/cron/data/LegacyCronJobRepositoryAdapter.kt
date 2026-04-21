package com.astrbot.android.feature.cron.data

import com.astrbot.android.feature.cron.domain.CronJobRepositoryPort
import com.astrbot.android.model.CronJob
import com.astrbot.android.model.CronJobExecutionRecord
import kotlinx.coroutines.flow.StateFlow

@Suppress("DEPRECATION")
/**
 * Compat-only adapter for targeted tests and transitional callers.
 * Production mainline should use a Hilt-owned cron job repository port.
 */
@Deprecated(
    "Compat-only seam. Production mainline should use a Hilt-owned CronJobRepositoryPort.",
    level = DeprecationLevel.WARNING,
)
class LegacyCronJobRepositoryAdapter : CronJobRepositoryPort {

    override val jobs: StateFlow<List<CronJob>>
        get() = FeatureCronJobRepository.jobs

    override suspend fun create(job: CronJob): CronJob =
        FeatureCronJobRepository.create(job)

    override suspend fun update(job: CronJob): CronJob =
        FeatureCronJobRepository.update(job)

    override suspend fun delete(jobId: String) =
        FeatureCronJobRepository.delete(jobId)

    override suspend fun getByJobId(jobId: String): CronJob? =
        FeatureCronJobRepository.getByJobId(jobId)

    override suspend fun listAll(): List<CronJob> =
        FeatureCronJobRepository.listAll()

    override suspend fun listEnabled(): List<CronJob> =
        FeatureCronJobRepository.listEnabled()

    override suspend fun updateStatus(jobId: String, status: String, lastRunAt: Long?, lastError: String?) =
        FeatureCronJobRepository.updateStatus(jobId, status, lastRunAt, lastError)

    override suspend fun recordExecutionStarted(record: CronJobExecutionRecord): CronJobExecutionRecord =
        FeatureCronJobRepository.recordExecutionStarted(record)

    override suspend fun updateExecutionRecord(record: CronJobExecutionRecord): CronJobExecutionRecord =
        FeatureCronJobRepository.updateExecutionRecord(record)

    override suspend fun listRecentExecutionRecords(jobId: String, limit: Int): List<CronJobExecutionRecord> =
        FeatureCronJobRepository.listRecentExecutionRecords(jobId, limit)

    override suspend fun latestExecutionRecord(jobId: String): CronJobExecutionRecord? =
        FeatureCronJobRepository.latestExecutionRecord(jobId)
}


