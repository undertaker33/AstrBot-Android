package com.astrbot.android.ui.settings

import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.CronJob
import com.astrbot.android.data.ConversationRepository
import com.astrbot.android.core.runtime.context.RuntimePlatform
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertFalse as junitAssertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CronJobEditorDraftTest {

    @Test
    fun `editor draft requires name schedule and bot selection before submit`() {
        val incomplete = CronJobEditorDraft()

        assertFalse(incomplete.canSubmit())
        assertTrue(incomplete.missingFields().containsAll(listOf("name", "schedule", "bot_id")))
        junitAssertFalse(incomplete.missingFields().contains("config_profile_id"))
        junitAssertFalse(incomplete.missingFields().contains("persona_id"))
        junitAssertFalse(incomplete.missingFields().contains("provider_id"))

        val ready = incomplete.copy(
            name = "Morning summary",
            cronExpression = "0 9 * * *",
            conversationId = ConversationRepository.DEFAULT_SESSION_ID,
            selectedBotId = "bot-1",
        )

        assertTrue(ready.canSubmit())
        assertTrue(ready.missingFields().isEmpty())
    }

    @Test
    fun `editor draft derives target context from selected bot and dialog inputs`() {
        val selectedBot = BotProfile(
            id = "bot-qq",
            platformName = "QQ",
            displayName = "Primary Bot",
            configProfileId = "config-qq",
            defaultPersonaId = "persona-qq",
            defaultProviderId = "provider-qq",
        )

        val draft = CronJobEditorDraft(
            platform = RuntimePlatform.QQ_ONEBOT.wireValue,
            conversationId = "chat-main",
            selectedBotId = selectedBot.id,
        )

        val target = draft.toTargetContext(selectedBot)

        assertEquals(RuntimePlatform.QQ_ONEBOT.wireValue, target.platform)
        assertEquals("chat-main", target.conversationId)
        assertEquals("bot-qq", target.botId)
        assertEquals("config-qq", target.configProfileId)
        assertEquals("persona-qq", target.personaId)
        assertEquals("provider-qq", target.providerId)
        assertEquals("ui", target.origin)
    }

    @Test
    fun `editor draft can be initialized from existing bot or user created cron job`() {
        val job = CronJob(
            jobId = "job-1",
            name = "Drink water",
            description = "提醒我喝水",
            cronExpression = "",
            payloadJson = JSONObject()
                .put("note", "提醒我喝水")
                .put("run_at", "2026-04-18T15:00:00+08:00")
                .toString(),
            runOnce = true,
            platform = RuntimePlatform.QQ_ONEBOT.wireValue,
            conversationId = "friend:123",
            botId = "bot-1",
        )

        val draft = CronJobEditorDraft.fromCronJob(job)

        assertEquals("Drink water", draft.name)
        assertEquals("提醒我喝水", draft.note)
        assertEquals("", draft.cronExpression)
        assertEquals("2026-04-18T15:00:00+08:00", draft.runAt)
        assertTrue(draft.runOnce)
        assertEquals(RuntimePlatform.QQ_ONEBOT.wireValue, draft.platform)
        assertEquals("friend:123", draft.conversationId)
        assertEquals("bot-1", draft.selectedBotId)
    }

    @Test
    fun `editor draft updates existing cron job metadata and target context`() {
        val existing = CronJob(
            jobId = "job-1",
            name = "Old",
            description = "old note",
            cronExpression = "0 9 * * *",
            payloadJson = "{}",
            enabled = true,
            platform = RuntimePlatform.APP_CHAT.wireValue,
            conversationId = "chat-main",
            botId = "old-bot",
            configProfileId = "old-config",
            providerId = "old-provider",
            nextRunTime = 1_000L,
            createdAt = 500L,
        )
        val selectedBot = BotProfile(
            id = "bot-qq",
            configProfileId = "config-qq",
            defaultPersonaId = "persona-qq",
            defaultProviderId = "provider-qq",
        )
        val draft = CronJobEditorDraft(
            name = "New",
            note = "new note",
            cronExpression = "0 15 * * *",
            runOnce = false,
            platform = RuntimePlatform.QQ_ONEBOT.wireValue,
            conversationId = "friend:123",
            selectedBotId = selectedBot.id,
        )

        val updated = draft.toUpdatedCronJob(
            existing = existing,
            selectedBot = selectedBot,
            timezone = "Asia/Shanghai",
            nextRunTime = 2_000L,
            updatedAt = 3_000L,
        )

        assertEquals("job-1", updated.jobId)
        assertEquals("New", updated.name)
        assertEquals("new note", updated.description)
        assertEquals("0 15 * * *", updated.cronExpression)
        assertEquals(2_000L, updated.nextRunTime)
        assertEquals(500L, updated.createdAt)
        assertEquals(3_000L, updated.updatedAt)
        assertEquals(RuntimePlatform.QQ_ONEBOT.wireValue, updated.platform)
        assertEquals("friend:123", updated.conversationId)
        assertEquals("bot-qq", updated.botId)
        assertEquals("config-qq", updated.configProfileId)
        assertEquals("persona-qq", updated.personaId)
        assertEquals("provider-qq", updated.providerId)
        assertEquals("new note", JSONObject(updated.payloadJson).getString("note"))
    }
}
