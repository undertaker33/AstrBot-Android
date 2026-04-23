package com.astrbot.android.feature.chat.domain

import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.chat.ConversationSession
import kotlinx.coroutines.flow.StateFlow

interface ConversationRepositoryPort {
    val defaultSessionId: String
    val sessions: StateFlow<List<ConversationSession>>

    fun contextPreview(sessionId: String): String

    fun session(sessionId: String): ConversationSession

    fun syncSystemSessionTitle(sessionId: String, title: String)

    fun appendMessage(
        sessionId: String,
        role: String,
        content: String,
        attachments: List<ConversationAttachment> = emptyList(),
    ): String

    fun updateSessionBindings(
        sessionId: String,
        providerId: String,
        personaId: String,
        botId: String,
    )

    fun updateSessionServiceFlags(
        sessionId: String,
        sessionSttEnabled: Boolean? = null,
        sessionTtsEnabled: Boolean? = null,
    )

    fun updateMessage(
        sessionId: String,
        messageId: String,
        content: String? = null,
        attachments: List<ConversationAttachment>? = null,
    )

    fun replaceMessages(sessionId: String, messages: List<ConversationMessage>)

    fun renameSession(sessionId: String, title: String)

    fun deleteSession(sessionId: String)
}
