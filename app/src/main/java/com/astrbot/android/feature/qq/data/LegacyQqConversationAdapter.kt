package com.astrbot.android.feature.qq.data

import com.astrbot.android.data.ConversationRepository
import com.astrbot.android.feature.qq.domain.QqConversationPort
import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.chat.ConversationSession
import com.astrbot.android.model.chat.MessageType

class LegacyQqConversationAdapter : QqConversationPort {

    override fun sessions(): List<ConversationSession> {
        return ConversationRepository.sessions.value
    }

    override fun resolveOrCreateSession(
        sessionId: String,
        title: String,
        messageType: MessageType,
    ): ConversationSession {
        val existing = ConversationRepository.session(sessionId)
        if (existing.title != title || !existing.titleCustomized) {
            ConversationRepository.syncSystemSessionTitle(sessionId, title)
        }
        return ConversationRepository.session(sessionId)
    }

    override fun session(sessionId: String): ConversationSession {
        return ConversationRepository.session(sessionId)
    }

    override fun appendMessage(
        sessionId: String,
        role: String,
        content: String,
        attachments: List<ConversationAttachment>,
    ): String {
        return ConversationRepository.appendMessage(
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
        ConversationRepository.updateSessionBindings(
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
        ConversationRepository.updateSessionServiceFlags(
            sessionId = sessionId,
            sessionSttEnabled = sessionSttEnabled,
            sessionTtsEnabled = sessionTtsEnabled,
        )
    }

    override fun replaceMessages(
        sessionId: String,
        messages: List<ConversationMessage>,
    ) {
        ConversationRepository.replaceMessages(sessionId, messages)
    }

    override fun renameSession(sessionId: String, title: String) {
        ConversationRepository.renameSession(sessionId, title)
    }

    override fun deleteSession(sessionId: String) {
        ConversationRepository.deleteSession(sessionId)
    }
}
