package com.astrbot.android.feature.qq.data

import com.astrbot.android.feature.chat.data.FeatureConversationRepository
import com.astrbot.android.feature.qq.domain.QqConversationPort
import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.chat.ConversationSession
import com.astrbot.android.model.chat.MessageType

class LegacyQqConversationAdapter : QqConversationPort {

    override fun sessions(): List<ConversationSession> {
        return FeatureConversationRepository.sessions.value
    }

    override fun resolveOrCreateSession(
        sessionId: String,
        title: String,
        messageType: MessageType,
    ): ConversationSession {
        val existing = FeatureConversationRepository.session(sessionId)
        if (existing.title != title || !existing.titleCustomized) {
            FeatureConversationRepository.syncSystemSessionTitle(sessionId, title)
        }
        return FeatureConversationRepository.session(sessionId)
    }

    override fun session(sessionId: String): ConversationSession {
        return FeatureConversationRepository.session(sessionId)
    }

    override fun appendMessage(
        sessionId: String,
        role: String,
        content: String,
        attachments: List<ConversationAttachment>,
    ): String {
        return FeatureConversationRepository.appendMessage(
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
        FeatureConversationRepository.updateSessionBindings(
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
        FeatureConversationRepository.updateSessionServiceFlags(
            sessionId = sessionId,
            sessionSttEnabled = sessionSttEnabled,
            sessionTtsEnabled = sessionTtsEnabled,
        )
    }

    override fun replaceMessages(
        sessionId: String,
        messages: List<ConversationMessage>,
    ) {
        FeatureConversationRepository.replaceMessages(sessionId, messages)
    }

    override fun renameSession(sessionId: String, title: String) {
        FeatureConversationRepository.renameSession(sessionId, title)
    }

    override fun deleteSession(sessionId: String) {
        FeatureConversationRepository.deleteSession(sessionId)
    }
}


