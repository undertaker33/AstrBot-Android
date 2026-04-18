package com.astrbot.android.feature.cron.domain

import com.astrbot.android.model.CronJob

class CronJobUseCases(
    private val repository: CronJobRepositoryPort,
    private val scheduler: CronSchedulerPort,
) {
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

    suspend fun deleteJob(jobId: String) {
        repository.delete(jobId)
        scheduler.cancel(jobId)
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
}
