package com.astrbot.android.feature.cron.runtime

import com.astrbot.android.feature.cron.domain.CronJobRunNowPort
import com.astrbot.android.feature.cron.domain.CronJobRunNowResult
import javax.inject.Inject

class CoordinatorCronJobRunNowPort @Inject constructor(
    private val coordinator: CronJobRunCoordinator,
) : CronJobRunNowPort {
    override suspend fun runNow(jobId: String): CronJobRunNowResult {
        return when (
            coordinator.runDueJob(
                jobId = jobId,
                attempt = 1,
                trigger = "run_now",
            )
        ) {
            CronJobRunOutcome.Succeeded -> CronJobRunNowResult(
                success = true,
                status = "succeeded",
                message = "Scheduled task ran now.",
            )
            CronJobRunOutcome.Skipped -> CronJobRunNowResult(
                success = true,
                status = "skipped",
                message = "Scheduled task is disabled and was skipped.",
            )
            CronJobRunOutcome.Retry -> CronJobRunNowResult(
                success = false,
                status = "retry",
                message = "Scheduled task failed with a retryable error.",
                errorCode = "retryable_failure",
            )
            CronJobRunOutcome.Failed -> CronJobRunNowResult(
                success = false,
                status = "failed",
                message = "Scheduled task failed.",
                errorCode = "execution_failed",
            )
        }
    }
}
