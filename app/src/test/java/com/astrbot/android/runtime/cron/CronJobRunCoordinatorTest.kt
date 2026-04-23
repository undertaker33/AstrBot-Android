package com.astrbot.android.feature.cron.runtime

import com.astrbot.android.feature.cron.domain.CronJobRepositoryPort
import com.astrbot.android.model.CronJob
import com.astrbot.android.model.CronJobExecutionRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CronJobRunCoordinatorTest {
    @Test
    fun successful_run_records_delivery_summary_and_deletes_run_once_job() = runBlocking {
        val repository = InMemoryCronJobRunRepository(
            CronJob(
                jobId = "job-1",
                name = "One shot",
                description = "Run once",
                payloadJson = """{"target":{"platform":"app","conversation_id":"chat-1","bot_id":"bot-1","config_profile_id":"cfg-1","provider_id":"provider-1"},"note":"hello"}""",
                enabled = true,
                runOnce = true,
                nextRunTime = 1_000L,
            ),
        )
        val scheduler = RecordingCronRescheduler()
        val coordinator = CronJobRunCoordinator(
            repository = repository,
            executor = ScheduledTaskExecutor { context ->
                assertEquals("job-1", context.jobId)
                CronJobDeliverySummary(
                    platform = context.platform,
                    conversationId = context.conversationId,
                    deliveredMessageCount = 1,
                    receiptIds = listOf("receipt-1"),
                    textPreview = "delivered",
                )
            },
            scheduler = scheduler,
            clock = SequenceClock(1_000L, 1_150L),
            nextFireTime = { _, _, _ -> 0L },
            executionIdGenerator = { "exec-1" },
        )

        val outcome = coordinator.runDueJob(jobId = "job-1", attempt = 1, trigger = "work_manager")

        assertEquals(CronJobRunOutcome.Succeeded, outcome)
        assertTrue(repository.deletedJobIds.contains("job-1"))
        assertTrue(scheduler.scheduled.isEmpty())
        val record = repository.records.single()
        assertEquals("exec-1", record.executionId)
        assertEquals("job-1", record.jobId)
        assertEquals("SUCCEEDED", record.status)
        assertEquals(1, record.attempt)
        assertEquals("work_manager", record.trigger)
        assertEquals(150L, record.durationMs)
        assertEquals("", record.errorCode)
        assertTrue(record.deliverySummary.contains("receipt-1"))
        assertTrue(record.completedAt > 0L)
    }

    @Test
    fun failed_run_records_error_and_requests_retry_when_retryable() = runBlocking {
        val repository = InMemoryCronJobRunRepository(
            CronJob(
                jobId = "job-1",
                name = "Retry me",
                description = "Retry",
                payloadJson = """{"target":{"platform":"app","conversation_id":"chat-1","bot_id":"bot-1","config_profile_id":"cfg-1","provider_id":"provider-1"},"note":"hello"}""",
                enabled = true,
                runOnce = false,
                nextRunTime = 1_000L,
            ),
        )
        val coordinator = CronJobRunCoordinator(
            repository = repository,
            executor = ScheduledTaskExecutor {
                throw CronJobExecutionFailure(
                    code = "provider_unavailable",
                    retryable = true,
                    message = "Provider unavailable",
                )
            },
            scheduler = RecordingCronRescheduler(),
            clock = SequenceClock(2_000L, 2_250L),
            nextFireTime = { _, _, _ -> 0L },
            executionIdGenerator = { "exec-2" },
        )

        val outcome = coordinator.runDueJob(jobId = "job-1", attempt = 3, trigger = "work_manager")

        assertEquals(CronJobRunOutcome.Retry, outcome)
        val record = repository.records.single()
        assertEquals("FAILED", record.status)
        assertEquals(3, record.attempt)
        assertEquals("provider_unavailable", record.errorCode)
        assertEquals("Provider unavailable", record.errorMessage)
        assertEquals(250L, record.durationMs)
        assertNotNull(repository.updatedJobs.singleOrNull { it.status == "failed" })
    }

    @Test
    fun run_once_zero_delivery_is_recorded_as_failure_and_not_deleted() = runBlocking {
        val repository = InMemoryCronJobRunRepository(
            CronJob(
                jobId = "job-suppressed",
                name = "Suppressed",
                description = "Should notify",
                payloadJson = """{"target":{"platform":"qq","conversation_id":"friend:123","bot_id":"bot-1","config_profile_id":"cfg-1","provider_id":"provider-1"},"note":"提醒用户喝水"}""",
                enabled = true,
                runOnce = true,
                nextRunTime = 1_000L,
            ),
        )
        val scheduler = RecordingCronRescheduler()
        val coordinator = CronJobRunCoordinator(
            repository = repository,
            executor = ScheduledTaskExecutor {
                CronJobDeliverySummary(
                    platform = "qq",
                    conversationId = "friend:123",
                    deliveredMessageCount = 0,
                    textPreview = "suppressed",
                )
            },
            scheduler = scheduler,
            clock = SequenceClock(4_000L, 4_050L),
            nextFireTime = { _, _, _ -> 0L },
            executionIdGenerator = { "exec-4" },
        )

        val outcome = coordinator.runDueJob(jobId = "job-suppressed", attempt = 1, trigger = "work_manager")

        assertEquals(CronJobRunOutcome.Failed, outcome)
        assertTrue(repository.deletedJobIds.isEmpty())
        assertTrue(scheduler.scheduled.isEmpty())
        val record = repository.records.single()
        assertEquals("FAILED", record.status)
        assertEquals("empty_delivery", record.errorCode)
        assertTrue(record.errorMessage.contains("without delivering"))
        assertNotNull(repository.updatedJobs.singleOrNull { it.status == "failed" })
    }

    @Test
    fun recurring_success_updates_next_run_time_records_summary_and_reschedules() = runBlocking {
        val repository = InMemoryCronJobRunRepository(
            CronJob(
                jobId = "job-recurring",
                name = "Recurring",
                description = "Every day",
                cronExpression = "30 9 * * *",
                timezone = "Asia/Shanghai",
                payloadJson = """{"target":{"platform":"app","conversation_id":"chat-1","bot_id":"bot-1","config_profile_id":"cfg-1","provider_id":"provider-1"},"note":"hello"}""",
                enabled = true,
                runOnce = false,
                nextRunTime = 1_000L,
            ),
        )
        val scheduler = RecordingCronRescheduler()
        val coordinator = CronJobRunCoordinator(
            repository = repository,
            executor = ScheduledTaskExecutor {
                CronJobDeliverySummary(
                    platform = "app",
                    conversationId = "chat-1",
                    deliveredMessageCount = 1,
                    receiptIds = emptyList(),
                    textPreview = "ok",
                )
            },
            scheduler = scheduler,
            clock = SequenceClock(3_000L, 3_120L),
            nextFireTime = { expression, fromMillis, timezone ->
                assertEquals("30 9 * * *", expression)
                assertEquals(3_120L, fromMillis)
                assertEquals("Asia/Shanghai", timezone)
                99_000L
            },
            executionIdGenerator = { "exec-3" },
        )

        val outcome = coordinator.runDueJob(jobId = "job-recurring", attempt = 1, trigger = "work_manager")

        assertEquals(CronJobRunOutcome.Succeeded, outcome)
        val updated = repository.updatedJobs.last()
        assertEquals("scheduled", updated.status)
        assertEquals(99_000L, updated.nextRunTime)
        assertEquals(3_000L, updated.lastRunAt)
        assertEquals(updated, scheduler.scheduled.single())
        val record = repository.records.single()
        assertEquals("SUCCEEDED", record.status)
        assertTrue(record.deliverySummary.contains("chat-1"))
    }
}

private class SequenceClock(vararg values: Long) : () -> Long {
    private val queue = ArrayDeque(values.toList())

    override fun invoke(): Long {
        return if (queue.isEmpty()) {
            0L
        } else {
            queue.removeFirst()
        }
    }
}

private class InMemoryCronJobRunRepository(
    initialJob: CronJob,
) : CronJobRepositoryPort {
    private val jobsById = linkedMapOf(initialJob.jobId to initialJob)
    private val jobsState = MutableStateFlow(listOf(initialJob))
    override val jobs: StateFlow<List<CronJob>> = jobsState
    val records = mutableListOf<CronJobExecutionRecord>()
    val updatedJobs = mutableListOf<CronJob>()
    val deletedJobIds = mutableListOf<String>()

    override suspend fun create(job: CronJob): CronJob {
        jobsById[job.jobId] = job
        jobsState.value = jobsById.values.toList()
        return job
    }

    override suspend fun getByJobId(jobId: String): CronJob? = jobsById[jobId]

    override suspend fun update(job: CronJob): CronJob {
        jobsById[job.jobId] = job
        jobsState.value = jobsById.values.toList()
        updatedJobs += job
        return job
    }

    override suspend fun delete(jobId: String) {
        jobsById.remove(jobId)
        jobsState.value = jobsById.values.toList()
        deletedJobIds += jobId
    }

    override suspend fun listAll(): List<CronJob> = jobsById.values.toList()

    override suspend fun listEnabled(): List<CronJob> = jobsById.values.filter(CronJob::enabled)

    override suspend fun updateStatus(jobId: String, status: String, lastRunAt: Long?, lastError: String?) = Unit

    override suspend fun recordExecutionStarted(record: CronJobExecutionRecord): CronJobExecutionRecord {
        records += record
        return record
    }

    override suspend fun updateExecutionRecord(record: CronJobExecutionRecord): CronJobExecutionRecord {
        val index = records.indexOfFirst { it.executionId == record.executionId }
        if (index >= 0) {
            records[index] = record
        } else {
            records += record
        }
        return record
    }

    override suspend fun listRecentExecutionRecords(jobId: String, limit: Int): List<CronJobExecutionRecord> {
        return records.filter { it.jobId == jobId }.takeLast(limit)
    }

    override suspend fun latestExecutionRecord(jobId: String): CronJobExecutionRecord? {
        return records.lastOrNull { it.jobId == jobId }
    }
}

private class RecordingCronRescheduler : CronRescheduler {
    val scheduled = mutableListOf<CronJob>()

    override fun schedule(job: CronJob) {
        scheduled += job
    }
}
