package com.astrbot.android.feature.cron.domain

import com.astrbot.android.feature.cron.domain.model.CronJob
import com.astrbot.android.feature.cron.domain.model.CronJobExecutionRecord
import kotlinx.coroutines.flow.StateFlow

interface CronJobRepositoryPort {
    val jobs: StateFlow<List<CronJob>>
    suspend fun create(job: CronJob): CronJob
    suspend fun update(job: CronJob): CronJob
    suspend fun delete(jobId: String)
    suspend fun getByJobId(jobId: String): CronJob?
    suspend fun listAll(): List<CronJob>
    suspend fun listEnabled(): List<CronJob>
    suspend fun updateStatus(jobId: String, status: String, lastRunAt: Long? = null, lastError: String? = null)
    suspend fun recordExecutionStarted(record: CronJobExecutionRecord): CronJobExecutionRecord
    suspend fun updateExecutionRecord(record: CronJobExecutionRecord): CronJobExecutionRecord
    suspend fun listRecentExecutionRecords(jobId: String, limit: Int = 5): List<CronJobExecutionRecord>
    suspend fun latestExecutionRecord(jobId: String): CronJobExecutionRecord?
}
