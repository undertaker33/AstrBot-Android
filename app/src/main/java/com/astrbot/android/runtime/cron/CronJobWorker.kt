package com.astrbot.android.runtime.cron

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.astrbot.android.data.CronJobRepository
import com.astrbot.android.model.CronJob
import com.astrbot.android.runtime.RuntimeLogRepository
import org.json.JSONObject

/**
 * WorkManager Worker that fires when a scheduled cron job is due.
 *
 * Delegates the actual LLM agent invocation to [CronJobExecutionBridge],
 * which is wired by the app's runtime layer when the application starts.
 */
class CronJobWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val jobId = inputData.getString("jobId") ?: return Result.failure()
        RuntimeLogRepository.append("CronJobWorker: firing for jobId=$jobId")

        val job = CronJobRepository.getByJobId(jobId)
        if (job == null) {
            RuntimeLogRepository.append("CronJobWorker: job $jobId not found in DB, skipping")
            return Result.failure()
        }
        if (!job.enabled) {
            RuntimeLogRepository.append("CronJobWorker: job $jobId is disabled, skipping")
            return Result.success()
        }

        CronJobRepository.updateStatus(jobId, status = "running", lastRunAt = System.currentTimeMillis())

        return try {
            executeCronJob(job)
            CronJobRepository.updateStatus(jobId, status = "completed")

            if (job.runOnce) {
                CronJobRepository.delete(jobId)
                RuntimeLogRepository.append("CronJobWorker: run_once job $jobId completed and deleted")
            } else {
                // Reschedule next occurrence
                val nextFire = CronExpressionParser.nextFireTime(
                    job.cronExpression, System.currentTimeMillis(), job.timezone,
                )
                CronJobRepository.update(job.copy(nextRunTime = nextFire, status = "scheduled"))
                CronJobScheduler.scheduleJob(applicationContext, job.copy(nextRunTime = nextFire))
                RuntimeLogRepository.append("CronJobWorker: recurring job $jobId rescheduled")
            }
            Result.success()
        } catch (e: Exception) {
            RuntimeLogRepository.append("CronJobWorker: job $jobId failed: ${e.message}")
            CronJobRepository.updateStatus(jobId, status = "failed", lastError = e.message ?: "unknown")
            Result.retry()
        }
    }

    private suspend fun executeCronJob(job: CronJob) {
        val bridge = CronJobExecutionBridge.instance
            ?: throw IllegalStateException(
                "No CronJobExecutionBridge registered. " +
                    "Ensure the bridge is initialized at app startup before cron jobs can fire.",
            )

        val payload = runCatching { JSONObject(job.payloadJson) }.getOrDefault(JSONObject())
        val cronContext = CronJobExecutionContext(
            jobId = job.jobId,
            name = job.name,
            description = job.description,
            jobType = job.jobType,
            note = payload.optString("note", job.description),
            sessionId = payload.optString("session", ""),
            runOnce = job.runOnce,
            runAt = payload.optString("run_at", ""),
        )

        bridge.execute(cronContext)
    }
}

/**
 * Context object passed to the execution bridge when a cron job fires.
 */
data class CronJobExecutionContext(
    val jobId: String,
    val name: String,
    val description: String,
    val jobType: String,
    val note: String,
    val sessionId: String,
    val runOnce: Boolean,
    val runAt: String,
)

/**
 * Bridge interface for the actual LLM agent invocation.
 *
 * This is registered by the app's runtime layer so that the Worker (which has
 * limited access to the full dependency graph) can delegate the heavy lifting.
 */
fun interface CronJobExecutionBridge {
    suspend fun execute(context: CronJobExecutionContext)

    companion object {
        @Volatile
        var instance: CronJobExecutionBridge? = null
    }
}
