package com.astrbot.android.feature.qq.domain

import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.chat.ConversationSession
import com.astrbot.android.model.chat.MessageType

interface QqConversationPort {
    fun sessions(): List<ConversationSession>

    fun resolveOrCreateSession(
        sessionId: String,
        title: String,
        messageType: MessageType,
    ): ConversationSession

    fun session(sessionId: String): ConversationSession

    fun appendMessage(
        sessionId: String,
        role: String,
        content: String,
        attachments: List<ConversationAttachment> = emptyList(),
    ): String

    fun updateSessionBindings(
        sessionId: String,
        botId: String,
        providerId: String,
        personaId: String,
    )

    fun updateSessionServiceFlags(
        sessionId: String,
        sessionSttEnabled: Boolean? = null,
        sessionTtsEnabled: Boolean? = null,
    )

    fun replaceMessages(
        sessionId: String,
        messages: List<ConversationMessage>,
    )

    fun renameSession(sessionId: String, title: String)

    fun deleteSession(sessionId: String)
}
