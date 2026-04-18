package com.astrbot.android.ui.settings

import com.astrbot.android.data.ConversationRepository
import com.astrbot.android.model.BotProfile
import com.astrbot.android.core.runtime.context.RuntimePlatform
import com.astrbot.android.feature.plugin.runtime.toolsource.ActiveCapabilityTargetContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CronJobsViewModelRequestTest {
    @Test
    fun build_create_future_task_request_preserves_bot_derived_target_context_and_payload() {
        val selectedBot = BotProfile(
            id = "bot-1",
            platformName = "QQ",
            displayName = "Primary Bot",
            configProfileId = "cfg-1",
            defaultPersonaId = "persona-1",
            defaultProviderId = "provider-1",
        )
        val draft = CronJobEditorDraft(
            name = "Morning check",
            cronExpression = "0 9 * * *",
            conversationId = ConversationRepository.DEFAULT_SESSION_ID,
            selectedBotId = selectedBot.id,
        )
        val target = draft.toTargetContext(selectedBot)

        val request = buildCronJobCreateRequest(
            name = draft.name,
            cronExpression = draft.cronExpression,
            runAt = "",
            note = "Summarize overnight messages",
            runOnce = false,
            targetContext = target,
        )

        assertEquals(target.platform, request.targetPlatform)
        assertEquals(target.conversationId, request.targetConversationId)
        assertEquals(target.botId, request.targetBotId)
        assertEquals(target.configProfileId, request.targetConfigProfileId)
        assertEquals(target.personaId, request.targetPersonaId)
        assertEquals(target.providerId, request.targetProviderId)
        assertEquals(target.origin, request.targetOrigin)
        assertEquals("Morning check", request.payload["name"])
        assertEquals("0 9 * * *", request.payload["cron_expression"])
        assertEquals("", request.payload["run_at"])
        assertEquals("Summarize overnight messages", request.payload["note"])
        assertEquals(false, request.payload["run_once"])
        assertEquals(true, request.payload["enabled"])
        assertEquals("ui", request.payload["origin"])
        assertTrue((request.payload["timezone"] as String).isNotBlank())
        assertFalse((request.payload["timezone"] as String).isBlank())
    }

    @Test
    fun bot_target_context_helper_uses_default_app_chat_platform_and_conversation_when_not_overridden() {
        val selectedBot = BotProfile(
            id = "bot-2",
            platformName = "QQ",
            displayName = "Secondary Bot",
            configProfileId = "cfg-2",
            defaultPersonaId = "persona-2",
            defaultProviderId = "provider-2",
        )

        val target = selectedBot.toCronJobTargetContext()

        assertEquals(RuntimePlatform.APP_CHAT.wireValue, target.platform)
        assertEquals(ConversationRepository.DEFAULT_SESSION_ID, target.conversationId)
        assertEquals("bot-2", target.botId)
        assertEquals("cfg-2", target.configProfileId)
        assertEquals("persona-2", target.personaId)
        assertEquals("provider-2", target.providerId)
        assertEquals("ui", target.origin)
    }
}
