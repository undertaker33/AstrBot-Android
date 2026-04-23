package com.astrbot.android.ui.viewmodel

import com.astrbot.android.feature.chat.domain.ConversationRepositoryPort
import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.chat.ConversationSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationViewModelTest {
    @Test
    fun session_defaults_to_repository_default_session_id() {
        val conversationPort = FakeConversationPort()
        val viewModel = ConversationViewModel(conversationPort)

        val session = viewModel.session()

        assertEquals("default-session", session.id)
    }

    @Test
    fun replace_messages_delegates_to_repository_port() {
        val conversationPort = FakeConversationPort()
        val viewModel = ConversationViewModel(conversationPort)
        val messages = listOf(
            ConversationMessage(id = "msg-1", role = "assistant", content = "hello", timestamp = 1L),
        )

        viewModel.replaceMessages("default-session", messages)

        assertEquals(messages, conversationPort.replacedMessages)
    }

    private class FakeConversationPort : ConversationRepositoryPort {
        override val defaultSessionId: String = "default-session"
        override val sessions: StateFlow<List<ConversationSession>> = MutableStateFlow(
            listOf(
                ConversationSession(
                    id = defaultSessionId,
                    title = "Default",
                    botId = "bot-1",
                    personaId = "",
                    providerId = "",
                    maxContextMessages = 12,
                    messages = emptyList(),
                ),
            ),
        )

        var replacedMessages: List<ConversationMessage> = emptyList()

        override fun contextPreview(sessionId: String): String = "preview"

        override fun session(sessionId: String): ConversationSession = sessions.value.first { it.id == sessionId }

        override fun syncSystemSessionTitle(sessionId: String, title: String) = Unit

        override fun updateSessionBindings(
            sessionId: String,
            providerId: String,
            personaId: String,
            botId: String,
        ) = Unit

        override fun updateSessionServiceFlags(
            sessionId: String,
            sessionSttEnabled: Boolean?,
            sessionTtsEnabled: Boolean?,
        ) = Unit

        override fun appendMessage(
            sessionId: String,
            role: String,
            content: String,
            attachments: List<ConversationAttachment>,
        ): String = "message-id"

        override fun updateMessage(
            sessionId: String,
            messageId: String,
            content: String?,
            attachments: List<ConversationAttachment>?,
        ) = Unit

        override fun replaceMessages(sessionId: String, messages: List<ConversationMessage>) {
            replacedMessages = messages
        }

        override fun renameSession(sessionId: String, title: String) = Unit

        override fun deleteSession(sessionId: String) = Unit
    }
}
