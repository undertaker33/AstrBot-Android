package com.astrbot.android.feature.cron.runtime

import com.astrbot.android.feature.cron.domain.CronJobRepositoryPort
import com.astrbot.android.feature.cron.domain.CronSchedulerPort
import com.astrbot.android.model.CronJob
import com.astrbot.android.model.CronJobExecutionRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CronJobReconcilerTest {
    @Test
    fun reconcileNow_cancels_existing_work_and_schedules_enabled_jobs_from_database() = runTest {
        val enabled = listOf(
            CronJob(jobId = "job-1", enabled = true),
            CronJob(jobId = "job-2", enabled = true),
        )
        val repository = FakeCronJobReconcileRepository(enabledJobs = enabled)
        val scheduler = RecordingCronSchedulerPort()
        val reconciler = CronJobReconciler(
            repository = repository,
            scheduler = scheduler,
            ioDispatcher = Dispatchers.Unconfined,
        )

        val summary = reconciler.reconcileNow()

        assertEquals(1, scheduler.cancelAllCount)
        assertEquals(listOf("job-1", "job-2"), scheduler.scheduledJobs.map { it.jobId })
        assertEquals(2, summary.scheduledCount)
        assertEquals(1, repository.listEnabledCallCount)
    }
}

private class FakeCronJobReconcileRepository(
    private val enabledJobs: List<CronJob>,
) : CronJobRepositoryPort {
    override val jobs: StateFlow<List<CronJob>> = MutableStateFlow(enabledJobs)
    var listEnabledCallCount = 0

    override suspend fun create(job: CronJob): CronJob = job

    override suspend fun getByJobId(jobId: String): CronJob? = null

    override suspend fun update(job: CronJob): CronJob = job

    override suspend fun delete(jobId: String) = Unit

    override suspend fun listAll(): List<CronJob> = enabledJobs

    override suspend fun listEnabled(): List<CronJob> {
        listEnabledCallCount += 1
        return enabledJobs
    }

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

private class RecordingCronSchedulerPort : CronSchedulerPort {
    val scheduledJobs = mutableListOf<CronJob>()
    var cancelAllCount = 0

    override fun schedule(job: CronJob) {
        scheduledJobs += job
    }

    override fun cancel(jobId: String) = Unit

    override fun cancelAll() {
        cancelAllCount += 1
    }
}
