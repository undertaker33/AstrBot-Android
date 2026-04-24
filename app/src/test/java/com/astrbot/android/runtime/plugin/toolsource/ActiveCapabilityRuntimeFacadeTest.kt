package com.astrbot.android.feature.plugin.runtime.toolsource

import com.astrbot.android.feature.cron.domain.CronJobRepositoryPort
import com.astrbot.android.feature.cron.domain.CronJobRunNowPort
import com.astrbot.android.feature.cron.domain.CronJobRunNowResult
import com.astrbot.android.feature.cron.domain.CronSchedulerPort
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.CronJob
import com.astrbot.android.model.CronJobExecutionRecord
import com.astrbot.android.core.common.logging.RuntimeLogRepository
import com.astrbot.android.core.runtime.context.RuntimePlatform
import com.astrbot.android.feature.plugin.runtime.toolsource.ToolSourceContext
import java.time.OffsetDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveCapabilityRuntimeFacadeTest {
    @Test
    fun create_future_task_schema_prioritizes_note_and_keeps_name_optional() {
        val schema = ActiveCapabilityToolSchemas.createFutureTaskSchema()
        val properties = schema["properties"] as Map<*, *>
        val required = schema["required"] as List<*>

        listOf(
            "run_once",
            "name",
            "note",
            "cron_expression",
            "run_at",
            "session",
            "timezone",
            "enabled",
            "platform",
            "conversation_id",
            "bot_id",
            "config_profile_id",
            "persona_id",
            "provider_id",
            "origin",
        ).forEach { key ->
            assertTrue("Expected create_future_task schema to include $key", properties.containsKey(key))
        }
        assertEquals(listOf("note"), required)
        assertFalse(required.contains("name"))
    }

    @Test
    fun create_future_task_auto_fills_target_context_from_runtime_metadata_and_session_alias() = runBlocking {
        val repository = InMemoryActiveCapabilityTaskRepository()
        val scheduler = RecordingActiveCapabilityScheduler()
        val facade = ActiveCapabilityRuntimeFacade(
            repository = repository,
            scheduler = scheduler,
            clock = { 1_000L },
            idGenerator = { "job-1" },
        )

        val result = facade.createFutureTask(
            ActiveCapabilityCreateTaskRequest(
                payload = mapOf(
                    "note" to "Summarize overnight messages",
                    "run_at" to "2026-04-18T09:30:00+08:00",
                    "session" to "session-1",
                    "timezone" to "Asia/Shanghai",
                    "enabled" to true,
                ),
                metadata = mapOf(
                    "__host" to mapOf(
                        "platformAdapterType" to RuntimePlatform.APP_CHAT.wireValue,
                        "conversationId" to "conversation-1",
                        "eventExtras" to mapOf(
                            "botId" to "bot-1",
                            "configProfileId" to "cfg-1",
                            "personaId" to "persona-1",
                            "providerId" to "provider-1",
                        ),
                    ),
                ),
                toolSourceContext = ToolSourceContext.fromConfigProfile(
                    ConfigProfile(id = "cfg-1", proactiveEnabled = true),
                    platform = RuntimePlatform.APP_CHAT,
                    conversationId = "conversation-1",
                ),
            ),
        )

        assertTrue(result is ActiveCapabilityTaskCreation.Created)
        val created = result as ActiveCapabilityTaskCreation.Created
        assertEquals("job-1", created.job.jobId)
        assertEquals(RuntimePlatform.APP_CHAT.wireValue, created.job.platform)
        assertEquals("session-1", created.job.conversationId)
        assertEquals("bot-1", created.job.botId)
        assertEquals("cfg-1", created.job.configProfileId)
        assertEquals("persona-1", created.job.personaId)
        assertEquals("provider-1", created.job.providerId)
        assertEquals("tool", created.job.origin)
        assertEquals("Unnamed Task", created.job.name)
        assertEquals("Asia/Shanghai", created.job.timezone)
        assertTrue(created.job.enabled)
        assertEquals(created.job, repository.created.single())
        assertEquals(created.job, scheduler.scheduled.single())

        val payload = JSONObject(created.job.payloadJson)
        val target = payload.getJSONObject("target")
        assertEquals(RuntimePlatform.APP_CHAT.wireValue, target.getString("platform"))
        assertEquals("session-1", target.getString("conversation_id"))
        assertEquals("bot-1", target.getString("bot_id"))
        assertEquals("cfg-1", target.getString("config_profile_id"))
        assertEquals("persona-1", target.getString("persona_id"))
        assertEquals("provider-1", target.getString("provider_id"))
        assertEquals("tool", target.getString("origin"))
    }

    @Test
    fun create_future_task_returns_structured_error_for_missing_target_context() = runBlocking {
        val facade = ActiveCapabilityRuntimeFacade(
            repository = InMemoryActiveCapabilityTaskRepository(),
            scheduler = RecordingActiveCapabilityScheduler(),
            clock = { 1_000L },
            idGenerator = { "job-1" },
        )

        val result = facade.createFutureTask(
            ActiveCapabilityCreateTaskRequest(
                payload = mapOf(
                    "name" to "Incomplete task",
                    "note" to "Summarize overnight messages",
                    "run_at" to "2026-04-18T09:30:00+08:00",
                    "platform" to RuntimePlatform.APP_CHAT.wireValue,
                    "conversation_id" to "conversation-1",
                ),
                metadata = emptyMap(),
                toolSourceContext = ToolSourceContext.fromConfigProfile(
                    ConfigProfile(id = "cfg-1", proactiveEnabled = true),
                    platform = RuntimePlatform.APP_CHAT,
                    conversationId = "conversation-1",
                ),
            ),
        )

        assertTrue(result is ActiveCapabilityTaskCreation.Failed)
        val failed = result as ActiveCapabilityTaskCreation.Failed
        assertEquals("missing_context", failed.error.code)
        assertTrue(failed.error.missingFields.contains("bot_id"))
        assertTrue(failed.error.missingFields.contains("provider_id"))
        assertFalse(failed.error.retryable)
    }

    @Test
    fun create_future_task_requires_note_even_when_name_is_present() = runBlocking {
        val facade = ActiveCapabilityRuntimeFacade(
            repository = InMemoryActiveCapabilityTaskRepository(),
            scheduler = RecordingActiveCapabilityScheduler(),
            clock = { 1_000L },
            idGenerator = { "job-1" },
        )

        val result = facade.createFutureTask(
            ActiveCapabilityCreateTaskRequest(
                payload = mapOf(
                    "name" to "Morning check",
                    "run_at" to "2026-04-18T09:30:00+08:00",
                ),
                metadata = null,
                toolSourceContext = null,
                targetContext = null,
            ),
        )

        assertTrue(result is ActiveCapabilityTaskCreation.Failed)
        val failed = result as ActiveCapabilityTaskCreation.Failed
        assertEquals("missing_note", failed.error.code)
        assertTrue(failed.error.missingFields.contains("note"))
    }

    @Test
    fun create_future_task_infers_run_at_from_host_raw_text_when_schedule_is_missing() = runBlocking {
        val repository = InMemoryActiveCapabilityTaskRepository()
        val scheduler = RecordingActiveCapabilityScheduler()
        val now = OffsetDateTime.parse("2026-04-18T01:39:49+08:00").toInstant().toEpochMilli()
        val facade = ActiveCapabilityRuntimeFacade(
            repository = repository,
            scheduler = scheduler,
            clock = { now },
            idGenerator = { "job-relative" },
        )

        val result = facade.createFutureTask(
            ActiveCapabilityCreateTaskRequest(
                payload = mapOf(
                    "note" to "提醒我睡觉",
                    "timezone" to "Asia/Shanghai",
                ),
                metadata = mapOf(
                    "__host" to mapOf(
                        "platformAdapterType" to RuntimePlatform.QQ_ONEBOT.wireValue,
                        "conversationId" to "conversation-1",
                        "rawText" to "五分钟后提醒我睡觉",
                        "eventExtras" to mapOf(
                            "botId" to "bot-1",
                            "configProfileId" to "cfg-1",
                            "personaId" to "persona-1",
                            "providerId" to "provider-1",
                        ),
                    ),
                ),
                toolSourceContext = ToolSourceContext.fromConfigProfile(
                    ConfigProfile(id = "cfg-1", proactiveEnabled = true),
                    platform = RuntimePlatform.QQ_ONEBOT,
                    conversationId = "conversation-1",
                ),
            ),
        )

        assertTrue(result is ActiveCapabilityTaskCreation.Created)
        val created = result as ActiveCapabilityTaskCreation.Created
        assertEquals(now + 5 * 60 * 1000L, created.job.nextRunTime)
        assertTrue(created.job.runOnce)
        assertEquals(created.job, repository.created.single())
        assertEquals(created.job, scheduler.scheduled.single())
        val payload = JSONObject(created.job.payloadJson)
        assertTrue(payload.getString("run_at").isNotBlank())
    }

    @Test
    fun create_future_task_infers_common_chinese_daypart_schedules() = runBlocking {
        val now = OffsetDateTime.parse("2026-04-18T10:00:00+08:00").toInstant().toEpochMilli()
        val cases = listOf(
            "明天早上提醒我开会" to "2026-04-19T09:00:00+08:00",
            "明天中午提醒我吃饭" to "2026-04-19T12:00:00+08:00",
            "明天下午提醒我提交报告" to "2026-04-19T15:00:00+08:00",
            "明天晚上提醒我复盘" to "2026-04-19T20:00:00+08:00",
            "今晚提醒我喝水" to "2026-04-18T20:00:00+08:00",
            "后天提醒我检查任务" to "2026-04-20T09:00:00+08:00",
            "下午三点提醒我开会" to "2026-04-18T15:00:00+08:00",
        )

        cases.forEachIndexed { index, (rawText, expectedTime) ->
            val result = createTaskFromRawText(rawText = rawText, now = now, jobId = "job-daypart-$index")

            assertCreatedAt(result, expectedTime)
        }
    }

    @Test
    fun create_future_task_infers_half_hour_and_one_and_half_hour_delays() = runBlocking {
        val now = OffsetDateTime.parse("2026-04-18T10:00:00+08:00").toInstant().toEpochMilli()

        assertCreatedAt(
            createTaskFromRawText(rawText = "半小时后提醒我喝水", now = now, jobId = "job-half"),
            "2026-04-18T10:30:00+08:00",
        )
        assertCreatedAt(
            createTaskFromRawText(rawText = "一个半小时后提醒我出门", now = now, jobId = "job-one-half"),
            "2026-04-18T11:30:00+08:00",
        )
    }

    @Test
    fun create_future_task_rejects_past_schedule_by_default() = runBlocking {
        val now = OffsetDateTime.parse("2026-04-18T16:00:00+08:00").toInstant().toEpochMilli()

        val result = createTaskFromRawText(rawText = "下午三点提醒我喝水", now = now, jobId = "job-past")

        assertTrue(result is ActiveCapabilityTaskCreation.Failed)
        val failed = result as ActiveCapabilityTaskCreation.Failed
        assertEquals("past_schedule", failed.error.code)
        assertFalse(failed.error.retryable)
    }

    @Test
    fun create_future_task_allows_past_schedule_as_immediate_when_explicitly_requested() = runBlocking {
        val now = OffsetDateTime.parse("2026-04-18T16:00:00+08:00").toInstant().toEpochMilli()
        val repository = InMemoryActiveCapabilityTaskRepository()
        val scheduler = RecordingActiveCapabilityScheduler()
        val facade = ActiveCapabilityRuntimeFacade(
            repository = repository,
            scheduler = scheduler,
            clock = { now },
            idGenerator = { "job-immediate" },
        )

        val result = facade.createFutureTask(
            ActiveCapabilityCreateTaskRequest(
                payload = targetPayload() + mapOf(
                    "note" to "提醒我喝水",
                    "run_at" to "2026-04-18T15:00:00+08:00",
                    "timezone" to "Asia/Shanghai",
                    "allow_past_immediate" to true,
                ),
                metadata = null,
                toolSourceContext = null,
            ),
        )

        assertTrue(result is ActiveCapabilityTaskCreation.Created)
        val created = result as ActiveCapabilityTaskCreation.Created
        assertEquals(now, created.job.nextRunTime)
        assertEquals(created.job, scheduler.scheduled.single())
    }

    @Test
    fun create_future_task_normalizes_legacy_onebot_platform_to_runtime_platform() = runBlocking {
        val repository = InMemoryActiveCapabilityTaskRepository()
        val scheduler = RecordingActiveCapabilityScheduler()
        val facade = ActiveCapabilityRuntimeFacade(
            repository = repository,
            scheduler = scheduler,
            clock = { 1_000L },
            idGenerator = { "job-onebot" },
        )

        val result = facade.createFutureTask(
            ActiveCapabilityCreateTaskRequest(
                payload = mapOf(
                    "note" to "提醒喝水",
                    "run_at" to "2026-04-18T09:30:00+08:00",
                ),
                metadata = mapOf(
                    "__host" to mapOf(
                        "platformAdapterType" to "onebot",
                        "conversationId" to "friend:123",
                        "eventExtras" to mapOf(
                            "botId" to "bot-1",
                            "configProfileId" to "cfg-1",
                            "providerId" to "provider-1",
                        ),
                    ),
                ),
                toolSourceContext = ToolSourceContext.fromConfigProfile(
                    ConfigProfile(id = "cfg-1", proactiveEnabled = true),
                    platform = RuntimePlatform.QQ_ONEBOT,
                    conversationId = "friend:123",
                ),
            ),
        )

        assertTrue(result is ActiveCapabilityTaskCreation.Created)
        val created = result as ActiveCapabilityTaskCreation.Created
        assertEquals(RuntimePlatform.QQ_ONEBOT.wireValue, created.job.platform)
        val payload = JSONObject(created.job.payloadJson)
        assertEquals(RuntimePlatform.QQ_ONEBOT.wireValue, payload.getString("platform"))
        assertEquals(RuntimePlatform.QQ_ONEBOT.wireValue, payload.getJSONObject("target").getString("platform"))
    }

    @Test
    fun create_future_task_logs_structured_failure_reason_when_schedule_is_missing() = runBlocking {
        RuntimeLogRepository.clear()
        val facade = ActiveCapabilityRuntimeFacade(
            repository = InMemoryActiveCapabilityTaskRepository(),
            scheduler = RecordingActiveCapabilityScheduler(),
            clock = { 1_000L },
            idGenerator = { "job-1" },
        )

        val result = facade.createFutureTask(
            ActiveCapabilityCreateTaskRequest(
                payload = mapOf(
                    "note" to "提醒我睡觉",
                    "platform" to RuntimePlatform.APP_CHAT.wireValue,
                    "conversation_id" to "conversation-1",
                    "bot_id" to "bot-1",
                    "config_profile_id" to "cfg-1",
                    "provider_id" to "provider-1",
                ),
                metadata = null,
                toolSourceContext = null,
                targetContext = null,
            ),
        )

        assertTrue(result is ActiveCapabilityTaskCreation.Failed)
        val failed = result as ActiveCapabilityTaskCreation.Failed
        assertEquals("invalid_schedule", failed.error.code)
        assertTrue(
            RuntimeLogRepository.logs.value.any { entry ->
                entry.contains("ActiveCapability: create_future_task failed") &&
                    entry.contains("invalid_schedule")
            },
        )
    }

    private suspend fun createTaskFromRawText(
        rawText: String,
        now: Long,
        jobId: String,
    ): ActiveCapabilityTaskCreation {
        val facade = ActiveCapabilityRuntimeFacade(
            repository = InMemoryActiveCapabilityTaskRepository(),
            scheduler = RecordingActiveCapabilityScheduler(),
            clock = { now },
            idGenerator = { jobId },
        )
        return facade.createFutureTask(
            ActiveCapabilityCreateTaskRequest(
                payload = targetPayload() + mapOf(
                    "note" to "提醒事项",
                    "timezone" to "Asia/Shanghai",
                ),
                metadata = mapOf("__host" to mapOf("rawText" to rawText)),
                toolSourceContext = null,
            ),
        )
    }

    @Test
    fun pause_and_resume_future_task_updates_repository_and_scheduler() = runBlocking {
        val initialJob = CronJob(jobId = "job-1", name = "Water", enabled = true, status = "scheduled")
        val repository = InMemoryActiveCapabilityTaskRepository(initialJobs = listOf(initialJob))
        val scheduler = RecordingActiveCapabilityScheduler()
        val facade = ActiveCapabilityRuntimeFacade(
            repository = repository,
            scheduler = scheduler,
            clock = { 10_000L },
        )

        val paused = facade.pauseFutureTask("job-1")

        assertTrue(paused.getBoolean("success"))
        assertEquals("job-1", paused.getString("job_id"))
        assertEquals(false, paused.getBoolean("enabled"))
        assertEquals("paused", paused.getString("status"))
        assertEquals(listOf("job-1"), scheduler.cancelled)

        val resumed = facade.resumeFutureTask("job-1")

        assertTrue(resumed.getBoolean("success"))
        assertEquals("job-1", resumed.getString("job_id"))
        assertEquals(true, resumed.getBoolean("enabled"))
        assertEquals("scheduled", resumed.getString("status"))
        assertEquals(listOf("job-1"), scheduler.scheduled.map { it.jobId })
    }

    @Test
    fun list_future_task_runs_returns_recent_execution_records() = runBlocking {
        val records = listOf(
            CronJobExecutionRecord(
                executionId = "run-1",
                jobId = "job-1",
                status = "FAILED",
                errorCode = "delivery_failed",
                errorMessage = "QQ offline",
            ),
            CronJobExecutionRecord(
                executionId = "run-2",
                jobId = "job-1",
                status = "SUCCEEDED",
                deliverySummary = """{"delivered_message_count":1}""",
            ),
        )
        val facade = ActiveCapabilityRuntimeFacade(
            repository = InMemoryActiveCapabilityTaskRepository(executionRecords = records),
            scheduler = RecordingActiveCapabilityScheduler(),
        )

        val listed = facade.listFutureTaskRuns(jobId = "job-1", limit = 2)

        assertTrue(listed.getBoolean("success"))
        assertEquals("job-1", listed.getString("job_id"))
        assertEquals(2, listed.getInt("count"))
        assertEquals("run-1", listed.getJSONArray("runs").getJSONObject(0).getString("execution_id"))
        assertEquals("run-2", listed.getJSONArray("runs").getJSONObject(1).getString("execution_id"))
    }

    @Test
    fun update_future_task_updates_conservative_fields_and_reschedules_enabled_job() = runBlocking {
        val initialJob = CronJob(
            jobId = "job-1",
            name = "Old name",
            description = "Old note",
            cronExpression = "0 9 * * *",
            timezone = "Asia/Shanghai",
            payloadJson = JSONObject()
                .put("note", "Old note")
                .put("timezone", "Asia/Shanghai")
                .toString(),
            enabled = true,
            status = "scheduled",
            nextRunTime = 1L,
        )
        val repository = InMemoryActiveCapabilityTaskRepository(initialJobs = listOf(initialJob))
        val scheduler = RecordingActiveCapabilityScheduler()
        val facade = ActiveCapabilityRuntimeFacade(
            repository = repository,
            scheduler = scheduler,
            clock = { 10_000L },
        )

        val updated = facade.updateFutureTask(
            mapOf(
                "job_id" to "job-1",
                "name" to "Updated name",
                "note" to "Updated note",
                "cron_expression" to "0 10 * * *",
                "timezone" to "UTC",
            ),
        )

        assertTrue(updated.getBoolean("success"))
        assertEquals("job-1", updated.getString("job_id"))
        assertEquals("Updated name", updated.getString("name"))
        assertEquals("scheduled", updated.getString("status"))
        val stored = repository.created.single()
        assertEquals("Updated name", stored.name)
        assertEquals("Updated note", stored.description)
        assertEquals("0 10 * * *", stored.cronExpression)
        assertEquals("UTC", stored.timezone)
        assertTrue(stored.enabled)
        assertEquals(stored, scheduler.scheduled.single())
        assertTrue(scheduler.cancelled.isEmpty())
        val payload = JSONObject(stored.payloadJson)
        assertEquals("Updated note", payload.getString("note"))
        assertEquals("UTC", payload.getString("timezone"))
    }

    @Test
    fun update_future_task_can_pause_with_status_without_breaking_pause_resume_semantics() = runBlocking {
        val initialJob = CronJob(jobId = "job-1", enabled = true, status = "scheduled")
        val repository = InMemoryActiveCapabilityTaskRepository(initialJobs = listOf(initialJob))
        val scheduler = RecordingActiveCapabilityScheduler()
        val facade = ActiveCapabilityRuntimeFacade(
            repository = repository,
            scheduler = scheduler,
            clock = { 10_000L },
        )

        val updated = facade.updateFutureTask(
            mapOf(
                "job_id" to "job-1",
                "status" to "paused",
            ),
        )

        assertTrue(updated.getBoolean("success"))
        assertEquals(false, updated.getBoolean("enabled"))
        assertEquals("paused", updated.getString("status"))
        assertEquals(listOf("job-1"), scheduler.cancelled)
        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test
    fun run_future_task_now_delegates_to_injected_runner_and_returns_structured_json() = runBlocking {
        val runner = RecordingRunNowPort(
            CronJobRunNowResult(
                success = true,
                status = "succeeded",
                message = "Run completed.",
            ),
        )
        val facade = ActiveCapabilityRuntimeFacade(
            repository = InMemoryActiveCapabilityTaskRepository(),
            scheduler = RecordingActiveCapabilityScheduler(),
            runNowPort = runner,
        )

        val result = facade.runFutureTaskNow("job-1")

        assertTrue(result.getBoolean("success"))
        assertEquals("job-1", result.getString("job_id"))
        assertEquals("succeeded", result.getString("status"))
        assertEquals("Run completed.", result.getString("message"))
        assertEquals(listOf("job-1"), runner.requestedJobIds)
    }

    @Test
    fun run_future_task_now_returns_structured_error_when_job_id_is_missing() = runBlocking {
        val facade = ActiveCapabilityRuntimeFacade(
            repository = InMemoryActiveCapabilityTaskRepository(),
            scheduler = RecordingActiveCapabilityScheduler(),
        )

        val result = facade.runFutureTaskNow("")

        assertEquals(false, result.getBoolean("success"))
        assertEquals("missing_job_id", result.getString("error_code"))
    }

    private fun assertCreatedAt(
        result: ActiveCapabilityTaskCreation,
        expectedOffsetDateTime: String,
    ) {
        assertTrue(result is ActiveCapabilityTaskCreation.Created)
        val created = result as ActiveCapabilityTaskCreation.Created
        assertEquals(
            OffsetDateTime.parse(expectedOffsetDateTime).toInstant().toEpochMilli(),
            created.job.nextRunTime,
        )
    }

    private fun targetPayload(): Map<String, Any?> {
        return mapOf(
            "platform" to RuntimePlatform.APP_CHAT.wireValue,
            "conversation_id" to "conversation-1",
            "bot_id" to "bot-1",
            "config_profile_id" to "cfg-1",
            "provider_id" to "provider-1",
        )
    }
}

private class InMemoryActiveCapabilityTaskRepository(
    initialJobs: List<CronJob> = emptyList(),
    private val executionRecords: List<CronJobExecutionRecord> = emptyList(),
) : CronJobRepositoryPort {
    val created = initialJobs.toMutableList()
    val updated = mutableListOf<CronJob>()
    override val jobs: StateFlow<List<CronJob>> = MutableStateFlow(initialJobs)

    override suspend fun create(job: CronJob): CronJob {
        created += job
        return job
    }

    override suspend fun update(job: CronJob): CronJob {
        updated += job
        val index = created.indexOfFirst { it.jobId == job.jobId }
        if (index >= 0) {
            created[index] = job
        } else {
            created += job
        }
        return job
    }

    override suspend fun delete(jobId: String) = Unit

    override suspend fun getByJobId(jobId: String): CronJob? {
        return created.firstOrNull { it.jobId == jobId }
    }

    override suspend fun listAll(): List<CronJob> = created

    override suspend fun listEnabled(): List<CronJob> = created.filter(CronJob::enabled)

    override suspend fun updateStatus(jobId: String, status: String, lastRunAt: Long?, lastError: String?) = Unit

    override suspend fun recordExecutionStarted(record: CronJobExecutionRecord): CronJobExecutionRecord = record

    override suspend fun updateExecutionRecord(record: CronJobExecutionRecord): CronJobExecutionRecord = record

    override suspend fun listRecentExecutionRecords(jobId: String, limit: Int): List<CronJobExecutionRecord> {
        return executionRecords.filter { it.jobId == jobId }.takeLast(limit)
    }

    override suspend fun latestExecutionRecord(jobId: String): CronJobExecutionRecord? = null
}

private class RecordingActiveCapabilityScheduler : CronSchedulerPort {
    val scheduled = mutableListOf<CronJob>()
    val cancelled = mutableListOf<String>()

    override fun schedule(job: CronJob) {
        scheduled += job
    }

    override fun cancel(jobId: String) {
        cancelled += jobId
    }

    override fun cancelAll() = Unit
}

private class RecordingRunNowPort(
    private val result: CronJobRunNowResult,
) : CronJobRunNowPort {
    val requestedJobIds = mutableListOf<String>()

    override suspend fun runNow(jobId: String): CronJobRunNowResult {
        requestedJobIds += jobId
        return result
    }
}
