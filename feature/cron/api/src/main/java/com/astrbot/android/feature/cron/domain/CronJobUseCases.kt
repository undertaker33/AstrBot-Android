package com.astrbot.android.feature.cron.domain

import com.astrbot.android.feature.cron.domain.model.CronJob
import com.astrbot.android.feature.cron.domain.model.CronJobExecutionRecord
import javax.inject.Inject

class CronJobUseCases(
    private val repository: CronJobRepositoryPort,
    private val scheduler: CronSchedulerPort,
    private val runNowPort: CronJobRunNowPort? = null,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    @Inject
    constructor(
        repository: CronJobRepositoryPort,
        scheduler: CronSchedulerPort,
        runNowPort: CronJobRunNowPort,
    ) : this(
        repository = repository,
        scheduler = scheduler,
        runNowPort = runNowPort,
        clock = { System.currentTimeMillis() },
    )

    suspend fun listJobs(): List<CronJob> {
        return repository.listAll()
    }

    suspend fun createJob(job: CronJob): CronJob {
        val created = repository.create(job)
        if (created.enabled) {
            scheduler.schedule(created)
        }
        return created
    }

    suspend fun updateJob(job: CronJob): CronJob {
        val updated = repository.update(job)
        if (updated.enabled) {
            scheduler.schedule(updated)
        } else {
            scheduler.cancel(updated.jobId)
        }
        return updated
    }

    suspend fun updateJobById(
        jobId: String,
        transform: (CronJob) -> CronJob,
    ): CronJob? {
        val existing = repository.getByJobId(jobId) ?: return null
        val candidate = transform(existing)
        val updated = updateJob(
            candidate.copy(
                jobId = existing.jobId,
                createdAt = existing.createdAt,
                updatedAt = clock(),
            ),
        )
        return updated
    }

    suspend fun runJobNow(jobId: String): CronJobRunNowResult {
        if (jobId.isBlank()) {
            return CronJobRunNowResult(
                success = false,
                status = "failed",
                errorCode = "missing_job_id",
                message = "missing_job_id",
            )
        }
        return runNowPort?.runNow(jobId)
            ?: CronJobRunNowResult(
                success = false,
                status = "unavailable",
                errorCode = "run_now_unavailable",
                message = "run_now_unavailable",
            )
    }

    suspend fun deleteJob(jobId: String) {
        repository.delete(jobId)
        scheduler.cancel(jobId)
    }

    suspend fun pauseJob(jobId: String): CronJob? {
        val job = repository.getByJobId(jobId) ?: return null
        return updateJob(
            job.copy(
                enabled = false,
                status = "paused",
                updatedAt = clock(),
            ),
        )
    }

    suspend fun resumeJob(jobId: String): CronJob? {
        val job = repository.getByJobId(jobId) ?: return null
        return updateJob(
            job.copy(
                enabled = true,
                status = "scheduled",
                updatedAt = clock(),
            ),
        )
    }

    suspend fun toggleEnabled(job: CronJob): CronJob {
        val toggled = job.copy(enabled = !job.enabled, updatedAt = System.currentTimeMillis())
        val updated = repository.update(toggled)
        if (updated.enabled) {
            scheduler.schedule(updated)
        } else {
            scheduler.cancel(updated.jobId)
        }
        return updated
    }

    suspend fun findById(jobId: String): CronJob? {
        return repository.getByJobId(jobId)
    }

    suspend fun listRuns(jobId: String, limit: Int): List<CronJobExecutionRecord> {
        return repository.listRecentExecutionRecords(jobId, limit)
    }
}
