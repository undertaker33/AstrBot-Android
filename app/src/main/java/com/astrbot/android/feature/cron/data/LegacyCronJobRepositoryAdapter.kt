package com.astrbot.android.feature.cron.data

import com.astrbot.android.data.CronJobRepository
import com.astrbot.android.feature.cron.domain.CronJobRepositoryPort
import com.astrbot.android.model.CronJob
import com.astrbot.android.model.CronJobExecutionRecord
import kotlinx.coroutines.flow.StateFlow

class LegacyCronJobRepositoryAdapter : CronJobRepositoryPort {

    override val jobs: StateFlow<List<CronJob>>
        get() = CronJobRepository.jobs

    override suspend fun create(job: CronJob): CronJob =
        CronJobRepository.create(job)

    override suspend fun update(job: CronJob): CronJob =
        CronJobRepository.update(job)

    override suspend fun delete(jobId: String) =
        CronJobRepository.delete(jobId)

    override suspend fun getByJobId(jobId: String): CronJob? =
        CronJobRepository.getByJobId(jobId)

    override suspend fun listAll(): List<CronJob> =
        CronJobRepository.listAll()

    override suspend fun listEnabled(): List<CronJob> =
        CronJobRepository.listEnabled()

    override suspend fun updateStatus(jobId: String, status: String, lastRunAt: Long?, lastError: String?) =
        CronJobRepository.updateStatus(jobId, status, lastRunAt, lastError)

    override suspend fun recordExecutionStarted(record: CronJobExecutionRecord): CronJobExecutionRecord =
        CronJobRepository.recordExecutionStarted(record)

    override suspend fun updateExecutionRecord(record: CronJobExecutionRecord): CronJobExecutionRecord =
        CronJobRepository.updateExecutionRecord(record)

    override suspend fun listRecentExecutionRecords(jobId: String, limit: Int): List<CronJobExecutionRecord> =
        CronJobRepository.listRecentExecutionRecords(jobId, limit)

    override suspend fun latestExecutionRecord(jobId: String): CronJobExecutionRecord? =
        CronJobRepository.latestExecutionRecord(jobId)
}
