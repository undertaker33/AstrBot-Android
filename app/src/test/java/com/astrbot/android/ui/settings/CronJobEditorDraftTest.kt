package com.astrbot.android.ui.settings

import com.astrbot.android.model.BotProfile
import com.astrbot.android.data.ConversationRepository
import com.astrbot.android.core.runtime.context.RuntimePlatform
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
}
