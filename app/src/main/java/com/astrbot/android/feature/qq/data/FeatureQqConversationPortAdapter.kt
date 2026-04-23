package com.astrbot.android.feature.qq.data

import com.astrbot.android.feature.chat.data.FeatureConversationRepository
import com.astrbot.android.feature.chat.domain.ConversationRepositoryPort
import com.astrbot.android.feature.qq.domain.QqConversationPort
import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.chat.ConversationSession
import com.astrbot.android.model.chat.MessageType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class FeatureQqConversationPortAdapter private constructor(
    private val sessionsReader: () -> List<ConversationSession>,
    private val sessionReader: (String) -> ConversationSession,
    private val syncSystemSessionTitle: (String, String) -> Unit,
    private val appendMessageWriter: (String, String, String, List<ConversationAttachment>) -> String,
    private val updateSessionBindingsWriter: (String, String, String, String) -> Unit,
    private val updateSessionServiceFlagsWriter: (String, Boolean?, Boolean?) -> Unit,
    private val replaceMessagesWriter: (String, List<ConversationMessage>) -> Unit,
    private val renameSessionWriter: (String, String) -> Unit,
    private val deleteSessionWriter: (String) -> Unit,
) : QqConversationPort {

    @Inject
    constructor(
        conversationRepositoryPort: ConversationRepositoryPort,
    ) : this(
        sessionsReader = { conversationRepositoryPort.sessions.value },
        sessionReader = conversationRepositoryPort::session,
        syncSystemSessionTitle = conversationRepositoryPort::syncSystemSessionTitle,
        appendMessageWriter = conversationRepositoryPort::appendMessage,
        updateSessionBindingsWriter = conversationRepositoryPort::updateSessionBindings,
        updateSessionServiceFlagsWriter = conversationRepositoryPort::updateSessionServiceFlags,
        replaceMessagesWriter = conversationRepositoryPort::replaceMessages,
        renameSessionWriter = conversationRepositoryPort::renameSession,
        deleteSessionWriter = conversationRepositoryPort::deleteSession,
    )

    constructor() : this(
        sessionsReader = { FeatureConversationRepository.sessions.value },
        sessionReader = FeatureConversationRepository::session,
        syncSystemSessionTitle = FeatureConversationRepository::syncSystemSessionTitle,
        appendMessageWriter = FeatureConversationRepository::appendMessage,
        updateSessionBindingsWriter = FeatureConversationRepository::updateSessionBindings,
        updateSessionServiceFlagsWriter = FeatureConversationRepository::updateSessionServiceFlags,
        replaceMessagesWriter = FeatureConversationRepository::replaceMessages,
        renameSessionWriter = FeatureConversationRepository::renameSession,
        deleteSessionWriter = FeatureConversationRepository::deleteSession,
    )

    override fun sessions(): List<ConversationSession> {
        return sessionsReader()
    }

    override fun resolveOrCreateSession(
        sessionId: String,
        title: String,
        messageType: MessageType,
    ): ConversationSession {
        val existing = sessionReader(sessionId)
        if (existing.title != title || !existing.titleCustomized) {
            syncSystemSessionTitle(sessionId, title)
        }
        return sessionReader(sessionId)
    }

    override fun session(sessionId: String): ConversationSession {
        return sessionReader(sessionId)
    }

    override fun appendMessage(
        sessionId: String,
        role: String,
        content: String,
        attachments: List<ConversationAttachment>,
    ): String {
        return appendMessageWriter(sessionId, role, content, attachments)
    }

    override fun updateSessionBindings(
        sessionId: String,
        botId: String,
        providerId: String,
        personaId: String,
    ) {
        updateSessionBindingsWriter(sessionId, providerId, personaId, botId)
    }

    override fun updateSessionServiceFlags(
        sessionId: String,
        sessionSttEnabled: Boolean?,
        sessionTtsEnabled: Boolean?,
    ) {
        updateSessionServiceFlagsWriter(sessionId, sessionSttEnabled, sessionTtsEnabled)
    }

    override fun replaceMessages(
        sessionId: String,
        messages: List<ConversationMessage>,
    ) {
        replaceMessagesWriter(sessionId, messages)
    }

    override fun renameSession(sessionId: String, title: String) {
        renameSessionWriter(sessionId, title)
    }

    override fun deleteSession(sessionId: String) {
        deleteSessionWriter(sessionId)
    }
}
