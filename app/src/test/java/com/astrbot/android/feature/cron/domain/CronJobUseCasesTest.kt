package com.astrbot.android.feature.cron.domain

import com.astrbot.android.model.CronJob
import com.astrbot.android.model.CronJobExecutionRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CronJobUseCasesTest {

    @Test
    fun toggleEnabled_updates_repository_and_schedules_enabled_job() = runTest {
        val repository = FakeCronJobRepositoryPort()
        val scheduler = FakeCronSchedulerPort()
        val useCases = CronJobUseCases(repository, scheduler)
        val job = CronJob(jobId = "job-1", enabled = false)

        val updated = useCases.toggleEnabled(job)

        assertTrue(updated.enabled)
        assertEquals(listOf("job-1"), repository.updatedJobs.map { it.jobId })
        assertEquals(listOf("job-1"), scheduler.scheduledJobs.map { it.jobId })
        assertTrue(scheduler.cancelledJobIds.isEmpty())
    }

    @Test
    fun deleteJob_cancels_scheduler_after_repository_delete() = runTest {
        val repository = FakeCronJobRepositoryPort()
        val scheduler = FakeCronSchedulerPort()
        val useCases = CronJobUseCases(repository, scheduler)

        useCases.deleteJob("job-2")

        assertEquals(listOf("job-2"), repository.deletedJobIds)
        assertEquals(listOf("job-2"), scheduler.cancelledJobIds)
    }

    private class FakeCronSchedulerPort : CronSchedulerPort {
        val scheduledJobs = mutableListOf<CronJob>()
        val cancelledJobIds = mutableListOf<String>()

        override fun schedule(job: CronJob) {
            scheduledJobs += job
        }

        override fun cancel(jobId: String) {
            cancelledJobIds += jobId
        }
    }

    private class FakeCronJobRepositoryPort : CronJobRepositoryPort {
        override val jobs: StateFlow<List<CronJob>> = MutableStateFlow(emptyList())
        val updatedJobs = mutableListOf<CronJob>()
        val deletedJobIds = mutableListOf<String>()

        override suspend fun create(job: CronJob): CronJob = job

        override suspend fun update(job: CronJob): CronJob {
            updatedJobs += job
            return job
        }

        override suspend fun delete(jobId: String) {
            deletedJobIds += jobId
        }

        override suspend fun getByJobId(jobId: String): CronJob? = null

        override suspend fun listAll(): List<CronJob> = emptyList()

        override suspend fun listEnabled(): List<CronJob> = emptyList()

        override suspend fun updateStatus(
            jobId: String,
            status: String,
            lastRunAt: Long?,
            lastError: String?,
        ) = Unit

        override suspend fun recordExecutionStarted(record: CronJobExecutionRecord): CronJobExecutionRecord = record

        override suspend fun updateExecutionRecord(record: CronJobExecutionRecord): CronJobExecutionRecord = record

        override suspend fun listRecentExecutionRecords(
            jobId: String,
            limit: Int,
        ): List<CronJobExecutionRecord> = emptyList()

        override suspend fun latestExecutionRecord(jobId: String): CronJobExecutionRecord? = null
    }
}
