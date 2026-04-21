package com.astrbot.android.feature.chat.data

import com.astrbot.android.feature.chat.domain.ConversationRepositoryPort
import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.chat.ConversationSession
import kotlinx.coroutines.flow.StateFlow

@Suppress("DEPRECATION")
open class FeatureConversationRepositoryPortAdapter : ConversationRepositoryPort {
    override val defaultSessionId: String = FeatureConversationRepository.DEFAULT_SESSION_ID
    override val sessions: StateFlow<List<ConversationSession>> = FeatureConversationRepository.sessions

    override fun contextPreview(sessionId: String): String =
        FeatureConversationRepository.buildContextPreview(sessionId)

    override fun session(sessionId: String): ConversationSession = FeatureConversationRepository.session(sessionId)

    override fun appendMessage(
        sessionId: String,
        role: String,
        content: String,
        attachments: List<ConversationAttachment>,
    ): String = FeatureConversationRepository.appendMessage(
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
        FeatureConversationRepository.updateMessage(
            sessionId = sessionId,
            messageId = messageId,
            content = content,
            attachments = attachments,
        )
    }

    override fun replaceMessages(sessionId: String, messages: List<ConversationMessage>) {
        FeatureConversationRepository.replaceMessages(sessionId, messages)
    }

    override fun renameSession(sessionId: String, title: String) {
        FeatureConversationRepository.renameSession(sessionId, title)
    }

    override fun deleteSession(sessionId: String) {
        FeatureConversationRepository.deleteSession(sessionId)
    }
}

/**
 * Compat-only adapter for targeted tests and transitional callers.
 * Production mainline uses [FeatureConversationRepositoryPortAdapter].
 */
@Deprecated(
    "Compat-only seam. Production mainline uses FeatureConversationRepositoryPortAdapter.",
    level = DeprecationLevel.WARNING,
)
class LegacyConversationRepositoryAdapter : FeatureConversationRepositoryPortAdapter()

