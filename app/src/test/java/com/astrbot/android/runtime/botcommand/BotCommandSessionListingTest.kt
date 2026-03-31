package com.astrbot.android.runtime.botcommand

import com.astrbot.android.model.PersonaProfile
import com.astrbot.android.model.chat.ConversationSession
import com.astrbot.android.model.chat.MessageType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BotCommandSessionListingTest {

    @Test
    fun `session listing shows current conversation type and page hint`() {
        val personas = listOf(
            PersonaProfile(id = "default", name = "Default Assistant", systemPrompt = "default", enabledTools = emptySet()),
        )
        val sessions = (1..12).map { index ->
            ConversationSession(
                id = "session-$index",
                title = "会话$index",
                botId = "qqbot",
                personaId = "default",
                providerId = "provider-1",
                platformId = "app",
                messageType = MessageType.OtherMessage,
                originSessionId = "session-$index",
                maxContextMessages = 12,
                messages = emptyList(),
            )
        }

        val listing = BotCommandSessionListing.render(
            sessions = sessions,
            personas = personas,
            currentSessionId = "session-12",
            page = 2,
            languageTag = "zh",
        )

        assertTrue(listing.contains("会话列表"))
        assertTrue(listing.contains("会话12（session-12）"))
        assertTrue(listing.contains("当前对话：会话12（session-12）"))
        assertTrue(listing.contains("会话类型：应用内聊天"))
        assertTrue(listing.contains("输入/ls 2"))
        assertFalse(listing.contains("会话1（session-1）"))
    }
}
