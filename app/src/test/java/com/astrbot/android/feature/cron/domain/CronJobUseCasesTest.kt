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
    fun listJobs_delegates_to_repository_listAll() = runTest {
        val jobs = listOf(CronJob(jobId = "job-1"), CronJob(jobId = "job-2"))
        val repository = FakeCronJobRepositoryPort(jobsSnapshot = jobs)
        val scheduler = FakeCronSchedulerPort()
        val useCases = CronJobUseCases(repository, scheduler)

        val listed = useCases.listJobs()

        assertEquals(jobs, listed)
        assertEquals(1, repository.listAllCallCount)
    }

    @Test
    fun pauseJob_disables_job_updates_repository_and_cancels_scheduler() = runTest {
        val repository = FakeCronJobRepositoryPort(
            jobById = CronJob(jobId = "job-1", enabled = true, status = "scheduled", updatedAt = 1L),
        )
        val scheduler = FakeCronSchedulerPort()
        val useCases = CronJobUseCases(repository, scheduler)

        val paused = useCases.pauseJob("job-1")

        assertEquals(false, paused?.enabled)
        assertEquals("paused", paused?.status)
        assertEquals(listOf("job-1"), repository.requestedJobIds)
        assertEquals(listOf("job-1"), repository.updatedJobs.map { it.jobId })
        assertEquals(listOf("job-1"), scheduler.cancelledJobIds)
        assertTrue(scheduler.scheduledJobs.isEmpty())
        assertTrue(repository.updatedJobs.single().updatedAt >= 1L)
    }

    @Test
    fun resumeJob_enables_job_updates_repository_and_schedules_job() = runTest {
        val repository = FakeCronJobRepositoryPort(
            jobById = CronJob(jobId = "job-1", enabled = false, status = "unhealthy"),
        )
        val scheduler = FakeCronSchedulerPort()
        val useCases = CronJobUseCases(repository, scheduler)

        val resumed = useCases.resumeJob("job-1")

        assertEquals(true, resumed?.enabled)
        assertEquals("scheduled", resumed?.status)
        assertEquals(listOf("job-1"), repository.requestedJobIds)
        assertEquals(listOf("job-1"), repository.updatedJobs.map { it.jobId })
        assertEquals(listOf("job-1"), scheduler.scheduledJobs.map { it.jobId })
        assertTrue(scheduler.cancelledJobIds.isEmpty())
    }

    @Test
    fun listRuns_delegates_to_repository_listRecentExecutionRecords() = runTest {
        val records = listOf(
            CronJobExecutionRecord(executionId = "run-1", jobId = "job-1"),
            CronJobExecutionRecord(executionId = "run-2", jobId = "job-1"),
        )
        val repository = FakeCronJobRepositoryPort(executionRecords = records)
        val scheduler = FakeCronSchedulerPort()
        val useCases = CronJobUseCases(repository, scheduler)

        val listed = useCases.listRuns("job-1", limit = 2)

        assertEquals(records, listed)
        assertEquals(listOf("job-1" to 2), repository.listRecentExecutionRecordRequests)
    }

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

    @Test
    fun updateJobById_updates_existing_job_and_reschedules_when_enabled() = runTest {
        val repository = FakeCronJobRepositoryPort(
            jobById = CronJob(
                jobId = "job-1",
                name = "Old",
                description = "Old note",
                enabled = true,
                updatedAt = 1L,
            ),
        )
        val scheduler = FakeCronSchedulerPort()
        val useCases = CronJobUseCases(repository, scheduler)

        val updated = useCases.updateJobById("job-1") { job ->
            job.copy(name = "New", description = "New note")
        }

        assertEquals("New", updated?.name)
        assertEquals("New note", updated?.description)
        assertEquals(listOf("job-1"), repository.requestedJobIds)
        assertEquals(listOf("job-1"), repository.updatedJobs.map { it.jobId })
        assertEquals(listOf("job-1"), scheduler.scheduledJobs.map { it.jobId })
        assertTrue(scheduler.cancelledJobIds.isEmpty())
        assertTrue(repository.updatedJobs.single().updatedAt >= 1L)
    }

    @Test
    fun updateJobById_cancels_scheduler_when_transform_disables_job() = runTest {
        val repository = FakeCronJobRepositoryPort(
            jobById = CronJob(jobId = "job-1", enabled = true),
        )
        val scheduler = FakeCronSchedulerPort()
        val useCases = CronJobUseCases(repository, scheduler)

        val updated = useCases.updateJobById("job-1") { job ->
            job.copy(enabled = false, status = "paused")
        }

        assertEquals(false, updated?.enabled)
        assertEquals("paused", updated?.status)
        assertEquals(listOf("job-1"), scheduler.cancelledJobIds)
        assertTrue(scheduler.scheduledJobs.isEmpty())
    }

    @Test
    fun updateJobById_returns_null_when_job_is_missing() = runTest {
        val repository = FakeCronJobRepositoryPort(jobById = null)
        val scheduler = FakeCronSchedulerPort()
        val useCases = CronJobUseCases(repository, scheduler)

        val updated = useCases.updateJobById("missing") { it.copy(name = "Nope") }

        assertEquals(null, updated)
        assertTrue(repository.updatedJobs.isEmpty())
        assertTrue(scheduler.scheduledJobs.isEmpty())
        assertTrue(scheduler.cancelledJobIds.isEmpty())
    }

    @Test
    fun runJobNow_delegates_to_injected_port_without_touching_scheduler() = runTest {
        val repository = FakeCronJobRepositoryPort()
        val scheduler = FakeCronSchedulerPort()
        val runner = FakeCronJobRunNowPort(
            result = CronJobRunNowResult(
                success = true,
                status = "succeeded",
                message = "Run completed.",
            ),
        )
        val useCases = CronJobUseCases(repository, scheduler, runner)

        val result = useCases.runJobNow("job-1")

        assertTrue(result.success)
        assertEquals("succeeded", result.status)
        assertEquals(listOf("job-1"), runner.requestedJobIds)
        assertTrue(scheduler.scheduledJobs.isEmpty())
        assertTrue(scheduler.cancelledJobIds.isEmpty())
    }

    @Test
    fun runJobNow_reports_unavailable_when_no_runner_is_injected() = runTest {
        val useCases = CronJobUseCases(
            repository = FakeCronJobRepositoryPort(),
            scheduler = FakeCronSchedulerPort(),
        )

        val result = useCases.runJobNow("job-1")

        assertEquals(false, result.success)
        assertEquals("run_now_unavailable", result.errorCode)
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

        override fun cancelAll() = Unit
    }

    private class FakeCronJobRepositoryPort(
        private val jobsSnapshot: List<CronJob> = emptyList(),
        private val jobById: CronJob? = null,
        private val executionRecords: List<CronJobExecutionRecord> = emptyList(),
    ) : CronJobRepositoryPort {
        override val jobs: StateFlow<List<CronJob>> = MutableStateFlow(emptyList())
        val requestedJobIds = mutableListOf<String>()
        val updatedJobs = mutableListOf<CronJob>()
        val deletedJobIds = mutableListOf<String>()
        val listRecentExecutionRecordRequests = mutableListOf<Pair<String, Int>>()
        var listAllCallCount = 0

        override suspend fun create(job: CronJob): CronJob = job

        override suspend fun update(job: CronJob): CronJob {
            updatedJobs += job
            return job
        }

        override suspend fun delete(jobId: String) {
            deletedJobIds += jobId
        }

        override suspend fun getByJobId(jobId: String): CronJob? {
            requestedJobIds += jobId
            return jobById
        }

        override suspend fun listAll(): List<CronJob> {
            listAllCallCount += 1
            return jobsSnapshot
        }

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
        ): List<CronJobExecutionRecord> {
            listRecentExecutionRecordRequests += jobId to limit
            return executionRecords
        }

        override suspend fun latestExecutionRecord(jobId: String): CronJobExecutionRecord? = null
    }

    private class FakeCronJobRunNowPort(
        private val result: CronJobRunNowResult,
    ) : CronJobRunNowPort {
        val requestedJobIds = mutableListOf<String>()

        override suspend fun runNow(jobId: String): CronJobRunNowResult {
            requestedJobIds += jobId
            return result
        }
    }
}
