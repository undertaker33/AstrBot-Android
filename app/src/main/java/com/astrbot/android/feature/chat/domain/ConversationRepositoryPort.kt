package com.astrbot.android.feature.chat.domain

import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.chat.ConversationSession
import kotlinx.coroutines.flow.StateFlow

interface ConversationRepositoryPort {
    val sessions: StateFlow<List<ConversationSession>>

    fun session(sessionId: String): ConversationSession

    fun appendMessage(
        sessionId: String,
        role: String,
        content: String,
        attachments: List<ConversationAttachment> = emptyList(),
    ): String

    fun updateMessage(
        sessionId: String,
        messageId: String,
        content: String? = null,
        attachments: List<ConversationAttachment>? = null,
    )

    fun renameSession(sessionId: String, title: String)

    fun deleteSession(sessionId: String)
}
