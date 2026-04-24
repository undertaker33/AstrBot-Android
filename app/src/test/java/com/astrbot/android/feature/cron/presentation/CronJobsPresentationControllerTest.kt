package com.astrbot.android.feature.cron.presentation

import com.astrbot.android.feature.cron.domain.ActiveCapabilityTaskPort
import com.astrbot.android.feature.cron.domain.CronJobRepositoryPort
import com.astrbot.android.feature.cron.domain.CronJobUseCases
import com.astrbot.android.feature.cron.domain.CronSchedulerPort
import com.astrbot.android.feature.cron.domain.CronTaskCreateRequest
import com.astrbot.android.feature.cron.domain.CronTaskCreateResult
import com.astrbot.android.model.CronJob
import com.astrbot.android.model.CronJobExecutionRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CronJobsPresentationControllerTest {

    @Test
    fun pause_and_resume_delegate_to_cron_use_cases() = runTest {
        val repository = FakeCronJobRepositoryPort(
            jobsById = mapOf("job-1" to CronJob(jobId = "job-1", enabled = true)),
        )
        val scheduler = FakeCronSchedulerPort()
        val controller = controller(repository, scheduler)

        val paused = controller.pauseJob("job-1")
        val resumed = controller.resumeJob("job-1")

        assertEquals(false, paused?.enabled)
        assertEquals(true, resumed?.enabled)
        assertEquals(listOf("job-1"), scheduler.cancelledJobIds)
        assertEquals(listOf("job-1"), scheduler.scheduledJobs.map { it.jobId })
        assertEquals(listOf(false, true), repository.updatedJobs.map { it.enabled })
    }

    @Test
    fun listRuns_delegates_to_cron_use_cases_with_limit() = runTest {
        val records = listOf(
            CronJobExecutionRecord(executionId = "run-1", jobId = "job-1"),
            CronJobExecutionRecord(executionId = "run-2", jobId = "job-1"),
        )
        val repository = FakeCronJobRepositoryPort(executionRecords = records)
        val controller = controller(repository)

        val listed = controller.listRuns("job-1", limit = 2)

        assertEquals(records, listed)
        assertEquals(listOf("job-1" to 2), repository.listRunRequests)
    }

    private fun controller(
        repository: FakeCronJobRepositoryPort,
        scheduler: FakeCronSchedulerPort = FakeCronSchedulerPort(),
    ): CronJobsPresentationController {
        return CronJobsPresentationController(
            useCases = CronJobUseCases(repository, scheduler),
            taskPort = FakeActiveCapabilityTaskPort(),
        )
    }

    private class FakeActiveCapabilityTaskPort : ActiveCapabilityTaskPort {
        override suspend fun createFutureTask(request: CronTaskCreateRequest): CronTaskCreateResult {
            return CronTaskCreateResult.Created("job-1")
        }
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
        private val jobsById: Map<String, CronJob> = emptyMap(),
        private val executionRecords: List<CronJobExecutionRecord> = emptyList(),
    ) : CronJobRepositoryPort {
        override val jobs: StateFlow<List<CronJob>> = MutableStateFlow(jobsById.values.toList())
        val updatedJobs = mutableListOf<CronJob>()
        val listRunRequests = mutableListOf<Pair<String, Int>>()

        override suspend fun create(job: CronJob): CronJob = job

        override suspend fun update(job: CronJob): CronJob {
            updatedJobs += job
            return job
        }

        override suspend fun delete(jobId: String) = Unit

        override suspend fun getByJobId(jobId: String): CronJob? =
            updatedJobs.lastOrNull { it.jobId == jobId } ?: jobsById[jobId]

        override suspend fun listAll(): List<CronJob> = jobs.value

        override suspend fun listEnabled(): List<CronJob> = jobs.value.filter { it.enabled }

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
            listRunRequests += jobId to limit
            return executionRecords
        }

        override suspend fun latestExecutionRecord(jobId: String): CronJobExecutionRecord? = null
    }
}
