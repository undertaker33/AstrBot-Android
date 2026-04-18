package com.astrbot.android.feature.cron.presentation

import com.astrbot.android.feature.cron.domain.ActiveCapabilityTaskPort
import com.astrbot.android.feature.cron.domain.CronJobUseCases
import com.astrbot.android.feature.cron.domain.CronTaskCreateRequest
import com.astrbot.android.feature.cron.domain.CronTaskCreateResult
import com.astrbot.android.model.CronJob

class CronJobsPresentationController(
    private val useCases: CronJobUseCases,
    private val taskPort: ActiveCapabilityTaskPort,
) {
    suspend fun toggleEnabled(job: CronJob): CronJob =
        useCases.toggleEnabled(job)

    suspend fun deleteJob(jobId: String) =
        useCases.deleteJob(jobId)

    suspend fun createFutureTask(request: CronTaskCreateRequest): CronTaskCreateResult =
        taskPort.createFutureTask(request)

    suspend fun findById(jobId: String): CronJob? =
        useCases.findById(jobId)
}
