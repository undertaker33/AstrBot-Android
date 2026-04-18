package com.astrbot.android.feature.cron.runtime

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.astrbot.android.feature.cron.data.FeatureCronJobRepository
import com.astrbot.android.model.CronJob
import com.astrbot.android.core.common.logging.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Manages scheduling of cron jobs via Android WorkManager.
 *
 * For `run_once` jobs, schedules a OneTimeWorkRequest with initial delay.
 * For recurring cron jobs, schedules the next occurrence as a OneTimeWorkRequest
 * (re-enqueued after each execution by CronJobWorker).
 */
object CronJobScheduler {

    private const val WORK_TAG = "cron_job"

    fun initialize(context: Context) {
        // Reschedule all enabled jobs on app start.
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            val enabledJobs = FeatureCronJobRepository.listEnabled()
            AppLogger.append("CronJobScheduler: re-scheduling ${enabledJobs.size} enabled jobs")
            enabledJobs.forEach { job -> scheduleJob(context, job) }
        }
    }

    fun scheduleJob(context: Context, job: CronJob) {
        if (!job.enabled) {
            cancelJob(context, job.jobId)
            return
        }
        val delayMs = computeDelayMs(job)
        if (delayMs < 0) {
            AppLogger.append("CronJobScheduler: skipping job ${job.jobId} 鈥?invalid delay")
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val workRequest = OneTimeWorkRequestBuilder<CronJobWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .setInputData(workDataOf("jobId" to job.jobId))
            .addTag(WORK_TAG)
            .addTag("cron_job_${job.jobId}")
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "cron_job_${job.jobId}",
            ExistingWorkPolicy.REPLACE,
            workRequest,
        )
        AppLogger.append("CronJobScheduler: scheduled job ${job.jobId} '${job.name}' with delay ${delayMs}ms")
    }

    fun cancelJob(context: Context, jobId: String) {
        WorkManager.getInstance(context).cancelUniqueWork("cron_job_$jobId")
        AppLogger.append("CronJobScheduler: cancelled job $jobId")
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
    }

    /**
     * Compute delay in milliseconds from now until the job's next run.
     *
     * For `run_once` jobs: parse the `run_at` field in payload (ISO 8601) or `nextRunTime`.
     * For recurring cron jobs: parse cron expression to find next occurrence.
     */
    internal fun computeDelayMs(job: CronJob): Long {
        val now = System.currentTimeMillis()

        // If nextRunTime is already set and in the future, use it.
        if (job.nextRunTime > now) {
            return job.nextRunTime - now
        }

        // For run_once jobs, try parsing run_at from payload.
        if (job.runOnce) {
            val runAt = extractRunAt(job.payloadJson)
            return if (runAt > now) runAt - now else 0L
        }

        // Parse cron expression to compute next fire time.
        val nextFire = CronExpressionParser.nextFireTime(job.cronExpression, now, job.timezone)
        return if (nextFire > now) nextFire - now else 60_000L // fallback 1 min
    }

    private fun extractRunAt(payloadJson: String): Long {
        return runCatching {
            val obj = org.json.JSONObject(payloadJson)
            val runAtStr = obj.optString("run_at", "")
            if (runAtStr.isBlank()) return 0L
            java.time.OffsetDateTime.parse(runAtStr).toInstant().toEpochMilli()
        }.getOrDefault(0L)
    }
}

