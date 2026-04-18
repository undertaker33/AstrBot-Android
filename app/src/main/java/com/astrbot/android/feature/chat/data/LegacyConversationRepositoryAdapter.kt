package com.astrbot.android.feature.chat.data

import com.astrbot.android.data.ConversationRepository
import com.astrbot.android.feature.chat.domain.ConversationRepositoryPort
import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.chat.ConversationSession
import kotlinx.coroutines.flow.StateFlow

class LegacyConversationRepositoryAdapter(
    private val legacy: ConversationRepository = ConversationRepository,
) : ConversationRepositoryPort {
    override val sessions: StateFlow<List<ConversationSession>> = legacy.sessions

    override fun session(sessionId: String): ConversationSession = legacy.session(sessionId)

    override fun appendMessage(
        sessionId: String,
        role: String,
        content: String,
        attachments: List<ConversationAttachment>,
    ): String = legacy.appendMessage(
        sessionId = sessionId,
        role = role,
        content = content,
        attachments = attachments,
    )

    override fun updateMessage(
        sessionId: String,
        messageId: String,
        content: String?,
        attachments: List<ConversationAttachment>?,
    ) {
        legacy.updateMessage(
            sessionId = sessionId,
            messageId = messageId,
            content = content,
            attachments = attachments,
        )
    }

    override fun renameSession(sessionId: String, title: String) {
        legacy.renameSession(sessionId, title)
    }

    override fun deleteSession(sessionId: String) {
        legacy.deleteSession(sessionId)
    }
}
