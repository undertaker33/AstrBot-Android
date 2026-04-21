package com.astrbot.android.feature.qq.data

import com.astrbot.android.feature.qq.domain.QqConversationPort
import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.chat.ConversationSession
import com.astrbot.android.model.chat.MessageType

@Suppress("DEPRECATION")
open class FeatureQqConversationPortAdapter : QqConversationPort {

    override fun sessions(): List<ConversationSession> {
        return com.astrbot.android.feature.chat.data.FeatureConversationRepository.sessions.value
    }

    override fun resolveOrCreateSession(
        sessionId: String,
        title: String,
        messageType: MessageType,
    ): ConversationSession {
        val existing = com.astrbot.android.feature.chat.data.FeatureConversationRepository.session(sessionId)
        if (existing.title != title || !existing.titleCustomized) {
            com.astrbot.android.feature.chat.data.FeatureConversationRepository.syncSystemSessionTitle(sessionId, title)
        }
        return com.astrbot.android.feature.chat.data.FeatureConversationRepository.session(sessionId)
    }

    override fun session(sessionId: String): ConversationSession {
        return com.astrbot.android.feature.chat.data.FeatureConversationRepository.session(sessionId)
    }

    override fun appendMessage(
        sessionId: String,
        role: String,
        content: String,
        attachments: List<ConversationAttachment>,
    ): String {
        return com.astrbot.android.feature.chat.data.FeatureConversationRepository.appendMessage(
            sessionId = sessionId,
            role = role,
            content = content,
            attachments = attachments,
        )
    }

    override fun updateSessionBindings(
        sessionId: String,
        botId: String,
        providerId: String,
        personaId: String,
    ) {
        com.astrbot.android.feature.chat.data.FeatureConversationRepository.updateSessionBindings(
            sessionId = sessionId,
            providerId = providerId,
            personaId = personaId,
            botId = botId,
        )
    }

    override fun updateSessionServiceFlags(
        sessionId: String,
        sessionSttEnabled: Boolean?,
        sessionTtsEnabled: Boolean?,
    ) {
        com.astrbot.android.feature.chat.data.FeatureConversationRepository.updateSessionServiceFlags(
            sessionId = sessionId,
            sessionSttEnabled = sessionSttEnabled,
            sessionTtsEnabled = sessionTtsEnabled,
        )
    }

    override fun replaceMessages(
        sessionId: String,
        messages: List<ConversationMessage>,
    ) {
        com.astrbot.android.feature.chat.data.FeatureConversationRepository.replaceMessages(sessionId, messages)
    }

    override fun renameSession(sessionId: String, title: String) {
        com.astrbot.android.feature.chat.data.FeatureConversationRepository.renameSession(sessionId, title)
    }

    override fun deleteSession(sessionId: String) {
        com.astrbot.android.feature.chat.data.FeatureConversationRepository.deleteSession(sessionId)
    }
}

/**
 * Compat-only adapter for targeted tests and transitional callers.
 * Production mainline uses [FeatureQqConversationPortAdapter].
 */
@Deprecated(
    "Compat-only seam. Production mainline uses FeatureQqConversationPortAdapter.",
    level = DeprecationLevel.WARNING,
)
class LegacyQqConversationAdapter : FeatureQqConversationPortAdapter()
