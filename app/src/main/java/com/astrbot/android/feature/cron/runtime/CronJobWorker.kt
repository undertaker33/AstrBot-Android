package com.astrbot.android.feature.cron.runtime

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.astrbot.android.core.common.logging.AppLogger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * WorkManager worker for scheduled cron jobs.
 *
 * The worker translates WorkManager details into execution metadata and delegates
 * persistence, retry classification, and recurring reschedule to [CronJobRunCoordinator].
 */
@HiltWorker
class CronJobWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val coordinator: CronJobRunCoordinator,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val jobId = inputData.getString("jobId")
        if (jobId.isNullOrBlank()) {
            AppLogger.append("CronJobWorker: missing jobId input")
            return Result.failure()
        }

        AppLogger.append("CronJobWorker: firing for jobId=$jobId attempt=${runAttemptCount + 1}")
        return when (
            coordinator.runDueJob(
                jobId = jobId,
                attempt = runAttemptCount + 1,
                trigger = "work_manager",
            )
        ) {
            CronJobRunOutcome.Succeeded -> Result.success()
            CronJobRunOutcome.Skipped -> Result.success()
            CronJobRunOutcome.Retry -> Result.retry()
            CronJobRunOutcome.Failed -> Result.failure()
        }
    }
}

data class CronJobExecutionContext(
    val jobId: String,
    val name: String,
    val description: String,
    val jobType: String,
    val note: String,
    val sessionId: String,
    val platform: String,
    val conversationId: String,
    val botId: String,
    val configProfileId: String,
    val personaId: String,
    val providerId: String,
    val origin: String,
    val runOnce: Boolean,
    val runAt: String,
)

fun interface CronJobExecutionBridge {
    suspend fun execute(context: CronJobExecutionContext): CronJobDeliverySummary

    companion object {
        @Volatile
        var instance: CronJobExecutionBridge? = null
    }
}
