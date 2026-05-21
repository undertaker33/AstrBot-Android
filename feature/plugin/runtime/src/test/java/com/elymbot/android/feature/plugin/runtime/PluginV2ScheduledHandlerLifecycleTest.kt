package com.elymbot.android.feature.plugin.runtime

import com.elymbot.android.feature.cron.domain.CronJobRepositoryPort
import com.elymbot.android.feature.cron.domain.CronSchedulerPort
import com.elymbot.android.feature.cron.domain.model.CronJob
import com.elymbot.android.feature.cron.domain.model.CronJobExecutionRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginV2ScheduledHandlerLifecycleTest {

    @Test
    fun reconcile_creates_plugin_v2_schedule_job_and_schedules_it() = runTest {
        val repository = RecordingCronRepository()
        val scheduler = RecordingCronScheduler()
        val lifecycle = PluginV2ScheduledHandlerLifecycle(
            repository = repository,
            scheduler = scheduler,
            clock = { 10_000L },
            nextFireTime = { _, _, _ -> 20_000L },
        )

        lifecycle.reconcile(
            pluginId = "plugin.schedule",
            pluginVersion = "1.0.0",
            schedules = listOf(scheduleDescriptor()),
        )

        val job = repository.created.single()
        assertEquals(PluginV2ScheduledHandlerLifecycle.PLUGIN_V2_SCHEDULE_JOB_TYPE, job.jobType)
        assertEquals("plugin.schedule", job.pluginSchedulePayload().pluginId)
        assertEquals("daily-summary", job.pluginSchedulePayload().handlerKey)
        assertEquals("conversation-1", job.conversationId)
        assertEquals(20_000L, job.nextRunTime)
        assertEquals(job.jobId, scheduler.scheduled.single().jobId)
    }

    @Test
    fun reconcile_upgrade_updates_existing_schedule_by_handler_key() = runTest {
        val oldJob = CronJob(
            jobId = "plugin-v2-schedule:plugin.schedule:daily-summary",
            jobType = PluginV2ScheduledHandlerLifecycle.PLUGIN_V2_SCHEDULE_JOB_TYPE,
            cronExpression = "0 8 * * *",
            payloadJson = PluginV2SchedulePayload(
                pluginId = "plugin.schedule",
                pluginVersion = "1.0.0",
                handlerKey = "daily-summary",
                conversationId = "conversation-1",
                triggerSource = PluginV2ScheduledHandlerLifecycle.TRIGGER_SOURCE,
            ).toJsonString(),
            enabled = true,
            conversationId = "conversation-1",
        )
        val repository = RecordingCronRepository(initialJobs = listOf(oldJob))
        val scheduler = RecordingCronScheduler()

        PluginV2ScheduledHandlerLifecycle(
            repository = repository,
            scheduler = scheduler,
            clock = { 10_000L },
            nextFireTime = { _, _, _ -> 30_000L },
        ).reconcile(
            pluginId = "plugin.schedule",
            pluginVersion = "2.0.0",
            schedules = listOf(scheduleDescriptor(cron = "0 9 * * *")),
        )

        assertEquals(0, repository.created.size)
        val updated = repository.updated.single()
        assertEquals(oldJob.jobId, updated.jobId)
        assertEquals("0 9 * * *", updated.cronExpression)
        assertEquals("2.0.0", updated.pluginSchedulePayload().pluginVersion)
        assertEquals(30_000L, updated.nextRunTime)
        assertEquals(updated.jobId, scheduler.scheduled.single().jobId)
    }

    @Test
    fun pause_and_delete_scope_only_plugin_v2_schedule_jobs_for_plugin() = runTest {
        val pluginJob = CronJob(
            jobId = "plugin-v2-schedule:plugin.schedule:daily-summary",
            jobType = PluginV2ScheduledHandlerLifecycle.PLUGIN_V2_SCHEDULE_JOB_TYPE,
            payloadJson = PluginV2SchedulePayload(
                pluginId = "plugin.schedule",
                pluginVersion = "1.0.0",
                handlerKey = "daily-summary",
                conversationId = "conversation-1",
                triggerSource = PluginV2ScheduledHandlerLifecycle.TRIGGER_SOURCE,
            ).toJsonString(),
            enabled = true,
        )
        val otherJob = CronJob(
            jobId = "active-agent",
            jobType = "active_agent",
            payloadJson = "{}",
            enabled = true,
        )
        val repository = RecordingCronRepository(initialJobs = listOf(pluginJob, otherJob))
        val scheduler = RecordingCronScheduler()
        val lifecycle = PluginV2ScheduledHandlerLifecycle(repository, scheduler)

        lifecycle.pausePlugin("plugin.schedule")
        lifecycle.deletePlugin("plugin.schedule")

        assertFalse(repository.updated.single().enabled)
        assertEquals(pluginJob.jobId, scheduler.cancelled.single())
        assertEquals(listOf(pluginJob.jobId), repository.deleted)
        assertTrue(repository.jobsSnapshot.any { it.jobId == otherJob.jobId })
    }

    private fun scheduleDescriptor(
        cron: String = "0 9 * * *",
    ): PluginV2CompiledScheduledHandler {
        return PluginV2CompiledScheduledHandler(
            pluginId = "plugin.schedule",
            registrationKind = "schedule",
            registrationKey = "daily-summary",
            normalizedRegistrationKey = "plugin.schedule/schedule/daily-summary",
            handlerId = "hdl::plugin.schedule::schedule::daily-summary",
            callbackToken = PluginV2CallbackToken("cb::schedule::1"),
            priority = 0,
            filterAttachments = emptyList(),
            metadata = BootstrapRegistrationMetadata(),
            sourceOrder = 0,
            handlerKey = "daily-summary",
            cron = cron,
            runAtEpochMillis = null,
            conversationId = "conversation-1",
        )
    }

    private class RecordingCronRepository(
        initialJobs: List<CronJob> = emptyList(),
    ) : CronJobRepositoryPort {
        private val state = MutableStateFlow(initialJobs.toMutableList())
        val created = mutableListOf<CronJob>()
        val updated = mutableListOf<CronJob>()
        val deleted = mutableListOf<String>()
        val jobsSnapshot: List<CronJob>
            get() = state.value.toList()
        override val jobs: StateFlow<List<CronJob>> = state

        override suspend fun create(job: CronJob): CronJob {
            created += job
            state.value = (state.value + job).toMutableList()
            return job
        }

        override suspend fun update(job: CronJob): CronJob {
            updated += job
            state.value = state.value.map { current -> if (current.jobId == job.jobId) job else current }.toMutableList()
            return job
        }

        override suspend fun delete(jobId: String) {
            deleted += jobId
            state.value = state.value.filterNot { it.jobId == jobId }.toMutableList()
        }

        override suspend fun getByJobId(jobId: String): CronJob? = state.value.firstOrNull { it.jobId == jobId }
        override suspend fun listAll(): List<CronJob> = state.value
        override suspend fun listEnabled(): List<CronJob> = state.value.filter(CronJob::enabled)
        override suspend fun updateStatus(jobId: String, status: String, lastRunAt: Long?, lastError: String?) = Unit
        override suspend fun recordExecutionStarted(record: CronJobExecutionRecord): CronJobExecutionRecord = record
        override suspend fun updateExecutionRecord(record: CronJobExecutionRecord): CronJobExecutionRecord = record
        override suspend fun listRecentExecutionRecords(jobId: String, limit: Int): List<CronJobExecutionRecord> = emptyList()
        override suspend fun latestExecutionRecord(jobId: String): CronJobExecutionRecord? = null
    }

    private class RecordingCronScheduler : CronSchedulerPort {
        val scheduled = mutableListOf<CronJob>()
        val cancelled = mutableListOf<String>()
        var cancelAllCount = 0

        override fun schedule(job: CronJob) {
            scheduled += job
        }

        override fun cancel(jobId: String) {
            cancelled += jobId
        }

        override fun cancelAll() {
            cancelAllCount++
        }
    }
}
