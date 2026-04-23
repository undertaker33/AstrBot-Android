package com.astrbot.android.feature.plugin.runtime.toolsource

import com.astrbot.android.feature.cron.domain.CronJobRepositoryPort
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
}

private class InMemoryActiveCapabilityTaskRepository : CronJobRepositoryPort {
    val created = mutableListOf<CronJob>()
    override val jobs: StateFlow<List<CronJob>> = MutableStateFlow(emptyList())

    override suspend fun create(job: CronJob): CronJob {
        created += job
        return job
    }

    override suspend fun update(job: CronJob): CronJob = job

    override suspend fun delete(jobId: String) = Unit

    override suspend fun getByJobId(jobId: String): CronJob? {
        return created.firstOrNull { it.jobId == jobId }
    }

    override suspend fun listAll(): List<CronJob> = created

    override suspend fun listEnabled(): List<CronJob> = created.filter(CronJob::enabled)

    override suspend fun updateStatus(jobId: String, status: String, lastRunAt: Long?, lastError: String?) = Unit

    override suspend fun recordExecutionStarted(record: CronJobExecutionRecord): CronJobExecutionRecord = record

    override suspend fun updateExecutionRecord(record: CronJobExecutionRecord): CronJobExecutionRecord = record

    override suspend fun listRecentExecutionRecords(jobId: String, limit: Int): List<CronJobExecutionRecord> = emptyList()

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
}
