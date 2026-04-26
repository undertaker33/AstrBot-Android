package com.astrbot.android.feature.cron.runtime

import com.astrbot.android.feature.cron.domain.ActiveCapabilityTaskPort
import com.astrbot.android.feature.cron.domain.CronTaskCreateRequest
import com.astrbot.android.feature.cron.domain.CronTaskCreateResult
import com.astrbot.android.feature.plugin.runtime.toolsource.ActiveCapabilityNaturalLanguageParser
import java.time.OffsetDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ScheduledTaskIntentGuardTest {
    @Test
    fun creates_fallback_task_for_explicit_minute_of_hour_reminder_after_llm_skips_tool() = runBlocking {
        val port = RecordingTaskPort()
        val guard = ScheduledTaskIntentGuard(
            taskPort = port,
            naturalLanguageParser = ActiveCapabilityNaturalLanguageParser(),
            promptStrings = TestActiveCapabilityPromptStrings,
            clock = { OffsetDateTime.parse("2026-04-26T18:49:23+08:00").toInstant().toEpochMilli() },
        )

        val result = guard.tryCreateFallback(
            text = "\u0035\u0030\u5206\u63d0\u9192\u559d\u6c34",
            context = ScheduledTaskIntentGuardContext(
                proactiveEnabled = true,
                platform = "qq_onebot",
                conversationId = "friend:934457024",
                botId = "bot-1",
                configProfileId = "config-1",
                personaId = "persona-1",
                providerId = "deepseek-chat",
            ),
        )

        assertNotNull(result)
        assertEquals("job-1", (result as ScheduledTaskIntentGuardResult.Created).jobId)
        assertEquals(1, port.requests.size)
        val request = port.requests.single()
        assertEquals("qq_onebot", request.targetPlatform)
        assertEquals("friend:934457024", request.targetConversationId)
        assertEquals("bot-1", request.targetBotId)
        assertEquals("config-1", request.targetConfigProfileId)
        assertEquals("persona-1", request.targetPersonaId)
        assertEquals("deepseek-chat", request.targetProviderId)
        assertEquals("host_intent_guard", request.targetOrigin)
        assertEquals(true, request.payload["run_once"])
        assertEquals("\u63d0\u9192\u7528\u6237\u559d\u6c34", request.payload["note"])
        assertEquals("2026-04-26T18:50", request.payload["run_at"].toString().take(16))
    }

    @Test
    fun ignores_repeating_natural_language_without_parseable_time() = runBlocking {
        val port = RecordingTaskPort()
        val guard = ScheduledTaskIntentGuard(
            taskPort = port,
            naturalLanguageParser = ActiveCapabilityNaturalLanguageParser(),
            promptStrings = TestActiveCapabilityPromptStrings,
            clock = { OffsetDateTime.parse("2026-04-26T18:49:23+08:00").toInstant().toEpochMilli() },
        )

        val result = guard.tryCreateFallback(
            text = "\u6bcf\u5929\u63d0\u9192\u6211\u559d\u6c34",
            context = ScheduledTaskIntentGuardContext(
                proactiveEnabled = true,
                platform = "qq_onebot",
                conversationId = "friend:934457024",
                botId = "bot-1",
                configProfileId = "config-1",
                personaId = "persona-1",
                providerId = "deepseek-chat",
            ),
        )

        assertNull(result)
        assertEquals(0, port.requests.size)
    }

    @Test
    fun creates_daily_repeating_fallback_for_past_time_without_rejecting_as_one_time() = runBlocking {
        val port = RecordingTaskPort()
        val guard = ScheduledTaskIntentGuard(
            taskPort = port,
            naturalLanguageParser = ActiveCapabilityNaturalLanguageParser(),
            promptStrings = TestActiveCapabilityPromptStrings,
            clock = { OffsetDateTime.parse("2026-04-26T18:49:23+08:00").toInstant().toEpochMilli() },
        )

        val result = guard.tryCreateFallback(
            text = "\u6bcf\u5929\u65e9\u4e0a\u0038\u70b9\u63d0\u9192\u6211\u559d\u6c34",
            context = ScheduledTaskIntentGuardContext(
                proactiveEnabled = true,
                platform = "qq_onebot",
                conversationId = "friend:934457024",
                botId = "bot-1",
                configProfileId = "config-1",
                personaId = "persona-1",
                providerId = "deepseek-chat",
            ),
        )

        assertNotNull(result)
        assertEquals("job-1", (result as ScheduledTaskIntentGuardResult.Created).jobId)
        assertEquals(1, port.requests.size)
        val request = port.requests.single()
        assertEquals(false, request.payload["run_once"])
        assertEquals("0 8 * * *", request.payload["cron_expression"])
        assertEquals("\u63d0\u9192\u7528\u6237\u559d\u6c34", request.payload["note"])
        assertEquals("host_intent_guard", request.targetOrigin)
    }

    @Test
    fun creates_weekly_and_monthly_repeating_fallbacks() = runBlocking {
        val port = RecordingTaskPort()
        val guard = ScheduledTaskIntentGuard(
            taskPort = port,
            naturalLanguageParser = ActiveCapabilityNaturalLanguageParser(),
            promptStrings = TestActiveCapabilityPromptStrings,
            clock = { OffsetDateTime.parse("2026-04-26T18:49:23+08:00").toInstant().toEpochMilli() },
        )
        val context = ScheduledTaskIntentGuardContext(
            proactiveEnabled = true,
            platform = "qq_onebot",
            conversationId = "friend:934457024",
            botId = "bot-1",
            configProfileId = "config-1",
            personaId = "persona-1",
            providerId = "deepseek-chat",
        )

        guard.tryCreateFallback("\u6bcf\u5468\u4e00\u4e0b\u5348\u0033\u70b9\u63d0\u9192\u6211\u5f00\u4f1a", context)
        guard.tryCreateFallback("\u6bcf\u6708\u0031\u53f7\u4e0a\u5348\u0039\u70b9\u63d0\u9192\u6211\u4ea4\u62a5\u544a", context)

        assertEquals("0 15 * * 1", port.requests[0].payload["cron_expression"])
        assertEquals(false, port.requests[0].payload["run_once"])
        assertEquals("0 9 1 * *", port.requests[1].payload["cron_expression"])
        assertEquals(false, port.requests[1].payload["run_once"])
    }

    @Test
    fun creates_weekly_repeating_fallback_from_weekday_without_every_prefix() = runBlocking {
        val port = RecordingTaskPort()
        val guard = ScheduledTaskIntentGuard(
            taskPort = port,
            naturalLanguageParser = ActiveCapabilityNaturalLanguageParser(),
            promptStrings = TestActiveCapabilityPromptStrings,
            clock = { OffsetDateTime.parse("2026-04-26T18:49:23+08:00").toInstant().toEpochMilli() },
        )

        val result = guard.tryCreateFallback(
            text = "\u5468\u4e09\u4e0b\u5348\u4e09\u70b9\u63d0\u9192\u6211\u5f00\u4f1a",
            context = ScheduledTaskIntentGuardContext(
                proactiveEnabled = true,
                platform = "qq_onebot",
                conversationId = "friend:934457024",
                botId = "bot-1",
                configProfileId = "config-1",
                personaId = "persona-1",
                providerId = "deepseek-chat",
            ),
        )

        assertNotNull(result)
        assertEquals("0 15 * * 3", port.requests.single().payload["cron_expression"])
        assertEquals(false, port.requests.single().payload["run_once"])
    }

    @Test
    fun creates_daily_repeating_fallback_for_current_time_phrase() = runBlocking {
        val port = RecordingTaskPort()
        val guard = ScheduledTaskIntentGuard(
            taskPort = port,
            naturalLanguageParser = ActiveCapabilityNaturalLanguageParser(),
            promptStrings = TestActiveCapabilityPromptStrings,
            clock = { OffsetDateTime.parse("2026-04-26T18:49:23+08:00").toInstant().toEpochMilli() },
        )

        val result = guard.tryCreateFallback(
            text = "\u6bcf\u5929\u8fd9\u4e2a\u65f6\u95f4\u63d0\u9192\u6211\u559d\u6c34",
            context = ScheduledTaskIntentGuardContext(
                proactiveEnabled = true,
                platform = "qq_onebot",
                conversationId = "friend:934457024",
                botId = "bot-1",
                configProfileId = "config-1",
                personaId = "persona-1",
                providerId = "deepseek-chat",
            ),
        )

        assertNotNull(result)
        assertEquals("49 18 * * *", port.requests.single().payload["cron_expression"])
        assertEquals(false, port.requests.single().payload["run_once"])
    }

    @Test
    fun ignores_messages_without_reminder_intent_or_without_parseable_time() = runBlocking {
        val port = RecordingTaskPort()
        val guard = ScheduledTaskIntentGuard(
            taskPort = port,
            naturalLanguageParser = ActiveCapabilityNaturalLanguageParser(),
            promptStrings = TestActiveCapabilityPromptStrings,
            clock = { OffsetDateTime.parse("2026-04-26T18:49:23+08:00").toInstant().toEpochMilli() },
        )
        val context = ScheduledTaskIntentGuardContext(
            proactiveEnabled = true,
            platform = "qq_onebot",
            conversationId = "friend:934457024",
            botId = "bot-1",
            configProfileId = "config-1",
            personaId = "",
            providerId = "deepseek-chat",
        )

        assertNull(guard.tryCreateFallback("\u559d\u6c34\u5bf9\u8eab\u4f53\u597d\u5417", context))
        assertNull(guard.tryCreateFallback("\u63d0\u9192\u6211\u4e00\u4e0b", context))
        assertEquals(0, port.requests.size)
    }
}

private class RecordingTaskPort : ActiveCapabilityTaskPort {
    val requests = mutableListOf<CronTaskCreateRequest>()

    override suspend fun createFutureTask(request: CronTaskCreateRequest): CronTaskCreateResult {
        requests += request
        return CronTaskCreateResult.Created("job-${requests.size}")
    }
}

internal object TestActiveCapabilityPromptStrings : ActiveCapabilityPromptStrings {
    override val activeCapabilityHiddenDuringScheduledTask = "hidden"
    override val proactiveCapabilityDisabled = "disabled"
    override val createFutureTaskDisplayName = "Schedule Future Task"
    override val createFutureTaskDescription = "create task"
    override val deleteFutureTaskDisplayName = "Cancel Future Task"
    override val deleteFutureTaskDescription = "delete task"
    override val listFutureTasksDisplayName = "List Future Tasks"
    override val listFutureTasksDescription = "list tasks"
    override val pauseFutureTaskDisplayName = "Pause Future Task"
    override val pauseFutureTaskDescription = "pause task"
    override val resumeFutureTaskDisplayName = "Resume Future Task"
    override val resumeFutureTaskDescription = "resume task"
    override val listFutureTaskRunsDisplayName = "List Future Task Runs"
    override val listFutureTaskRunsDescription = "list runs"
    override val updateFutureTaskDisplayName = "Update Future Task"
    override val updateFutureTaskDescription = "update task"
    override val runFutureTaskNowDisplayName = "Run Future Task Now"
    override val runFutureTaskNowDescription = "run now"
    override val schemaJobIdCancelDescription = "job id to cancel"
    override val schemaJobIdDescription = "job id"
    override val schemaRunsLimitDescription = "limit"
    override val schemaUpdatedShortTitleDescription = "title"
    override val schemaUpdatedTaskInstructionDescription = "instruction"
    override val schemaTaskEnabledDescription = "enabled"
    override val schemaUpdatedTaskStatusDescription = "status"
    override val schemaUpdatedRunAtDescription = "run at"
    override val schemaUpdatedCronExpressionDescription = "cron"
    override val schemaUpdatedTimezoneDescription = "timezone"
    override val schemaCreateRunOnceDescription = "run once"
    override val schemaCreateNameDescription = "name"
    override val schemaCreateNoteDescription = "note"
    override val schemaCreateCronExpressionDescription = "cron"
    override val schemaCreateRunAtDescription = "run at"
    override val schemaCreateSessionDescription = "session"
    override val schemaCreateTimezoneDescription = "timezone"
    override val schemaCreateEnabledDescription = "enabled"
    override val schemaCreateAllowPastImmediateDescription = "allow past"
    override val schemaCreatePlatformDescription = "platform"
    override val schemaCreateConversationIdDescription = "conversation"
    override val schemaCreateBotIdDescription = "bot"
    override val schemaCreateConfigProfileIdDescription = "config"
    override val schemaCreatePersonaIdDescription = "persona"
    override val schemaCreateProviderIdDescription = "provider"
    override val schemaCreateOriginDescription = "origin"
    override val defaultTaskName = "Unnamed Task"
    override val missingNoteMessage = "note missing"
    override val invalidScheduleMessage = "invalid schedule"
    override val pastScheduleMessage = "past schedule"
    override val deleteMissingJobIdMessage = "delete missing job"
    override val updateMissingJobIdMessage = "update missing job"
    override val pauseMissingJobIdMessage = "pause missing job"
    override val resumeMissingJobIdMessage = "resume missing job"
    override val listRunsMissingJobIdMessage = "list runs missing job"
    override val runNowMissingJobIdMessage = "run now missing job"
    override val runNowUnavailableMessage = "run now unavailable"
    override val guardCreatedReply = "\u597d\uff0c\u5df2\u8bbe\u7f6e\u63d0\u9192\u3002"
    override val guardPastScheduleReply = "\u8fd9\u4e2a\u65f6\u95f4\u5df2\u7ecf\u8fc7\u53bb\u4e86\uff0c\u6211\u6ca1\u6709\u521b\u5efa\u63d0\u9192\u3002"
    override val guardReminderNotePrefix = "\u63d0\u9192\u7528\u6237"

    override fun activeCapabilityToolError(message: String): String = "Error: $message"
    override fun missingContextMessage(fields: String): String = "missing context: $fields"
    override fun taskNotFoundMessage(jobId: String): String = "task $jobId not found"
    override fun guardFailedReply(message: String): String = "\u63d0\u9192\u6ca1\u6709\u521b\u5efa\u6210\u529f\uff1a$message"
    override fun fallbackCreatedInstruction(jobId: String): String =
        "host_scheduled_task_fallback: created\njob_id: $jobId\nTell the user naturally that the reminder was created. Do not call create_future_task again."

    override fun fallbackFailedInstruction(code: String, replyText: String): String =
        "host_scheduled_task_fallback: failed\nerror_code: $code\nmessage: $replyText\nTell the user naturally that the reminder was not created. Do not call create_future_task again."
}
