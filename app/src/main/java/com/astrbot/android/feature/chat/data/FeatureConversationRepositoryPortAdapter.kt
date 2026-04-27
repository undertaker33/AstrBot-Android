
package com.astrbot.android.feature.chat.data

import com.astrbot.android.feature.chat.domain.ConversationRepositoryPort
import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.chat.ConversationSession
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow

@Singleton
class FeatureConversationRepositoryPortAdapter @Inject constructor(
    private val repository: FeatureConversationRepositoryStore,
) : ConversationRepositoryPort {
    override val defaultSessionId: String = FeatureConversationRepository.DEFAULT_SESSION_ID
    override val sessions: StateFlow<List<ConversationSession>> = repository.sessions

    override fun contextPreview(sessionId: String): String =
        repository.buildContextPreview(sessionId)

    override fun session(sessionId: String): ConversationSession = repository.session(sessionId)

    override fun syncSystemSessionTitle(sessionId: String, title: String) {
        repository.syncSystemSessionTitle(sessionId, title)
    }

    override fun appendMessage(
        sessionId: String,
        role: String,
        content: String,
        attachments: List<ConversationAttachment>,
    ): String = repository.appendMessage(
        sessionId = sessionId,
        role = role,
        content = content,
        attachments = attachments,
    )

    override fun updateSessionBindings(
        sessionId: String,
        providerId: String,
        personaId: String,
        botId: String,
    ) {
        repository.updateSessionBindings(
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
        repository.updateSessionServiceFlags(
            sessionId = sessionId,
            sessionSttEnabled = sessionSttEnabled,
            sessionTtsEnabled = sessionTtsEnabled,
        )
    }

    override fun updateMessage(
        sessionId: String,
        messageId: String,
        content: String?,
        attachments: List<ConversationAttachment>?,
    ) {
        repository.updateMessage(
            sessionId = sessionId,
            messageId = messageId,
            content = content,
            attachments = attachments,
        )
    }

    override fun replaceMessages(sessionId: String, messages: List<ConversationMessage>) {
        repository.replaceMessages(sessionId, messages)
    }

    override fun renameSession(sessionId: String, title: String) {
        repository.renameSession(sessionId, title)
    }

    override fun deleteSession(sessionId: String) {
        repository.deleteSession(sessionId)
    }
}
