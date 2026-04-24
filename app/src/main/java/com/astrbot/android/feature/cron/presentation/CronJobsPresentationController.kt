package com.astrbot.android.feature.cron.presentation

import com.astrbot.android.feature.cron.domain.ActiveCapabilityTaskPort
import com.astrbot.android.feature.cron.domain.CronJobUseCases
import com.astrbot.android.feature.cron.domain.CronTaskCreateRequest
import com.astrbot.android.feature.cron.domain.CronTaskCreateResult
import com.astrbot.android.model.CronJob
import com.astrbot.android.model.CronJobExecutionRecord
import javax.inject.Inject

class CronJobsPresentationController @Inject constructor(
    private val useCases: CronJobUseCases,
    private val taskPort: ActiveCapabilityTaskPort,
) {
    suspend fun toggleEnabled(job: CronJob): CronJob =
        useCases.toggleEnabled(job)

    suspend fun pauseJob(jobId: String): CronJob? =
        useCases.pauseJob(jobId)

    suspend fun resumeJob(jobId: String): CronJob? =
        useCases.resumeJob(jobId)

    suspend fun updateJob(job: CronJob): CronJob =
        useCases.updateJob(job)

    suspend fun deleteJob(jobId: String) =
        useCases.deleteJob(jobId)

    suspend fun listRuns(
        jobId: String,
        limit: Int = 5,
    ): List<CronJobExecutionRecord> =
        useCases.listRuns(jobId, limit)

    suspend fun createFutureTask(request: CronTaskCreateRequest): CronTaskCreateResult =
        taskPort.createFutureTask(request)

    suspend fun findById(jobId: String): CronJob? =
        useCases.findById(jobId)
}
