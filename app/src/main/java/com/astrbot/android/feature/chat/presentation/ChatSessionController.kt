package com.astrbot.android.ui.viewmodel

import com.astrbot.android.di.ChatViewModelDependencies
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.chat.ConversationSession

data class ChatSessionControllerResult(
    val uiState: ChatUiState,
    val session: ConversationSession? = null,
)

class ChatSessionController(
    private val dependencies: ChatViewModelDependencies,
) {
    fun selectBot(state: ChatUiState, botId: String): ChatUiState {
        val bot = dependencies.bots.value.firstOrNull { it.id == botId }
        val providerId = resolveProviderId(
            preferredProviderId = bot?.defaultProviderId?.ifBlank { currentSession(state)?.providerId },
            fallbackBot = bot,
        )
        val updatedState = state.copy(
            selectedBotId = botId,
            selectedProviderId = providerId,
            error = "",
        )
        dependencies.log(
            "Chat bot selected: ${bot?.displayName ?: botId}, provider=${providerId.ifBlank { "none" }}",
        )
        syncSessionBindings(
            sessionId = updatedState.selectedSessionId,
            providerId = providerId,
            selectedBotId = updatedState.selectedBotId,
        )
        return updatedState
    }

    fun selectProvider(state: ChatUiState, providerId: String): ChatUiState {
        val updatedState = state.copy(selectedProviderId = providerId, error = "")
        dependencies.log("Chat provider selected: ${providerId.ifBlank { "none" }}")
        syncSessionBindings(
            sessionId = updatedState.selectedSessionId,
            providerId = providerId,
            selectedBotId = updatedState.selectedBotId,
        )
        return updatedState
    }

    fun selectSession(state: ChatUiState, sessionId: String): ChatUiState {
        val session = dependencies.session(sessionId)
        val sessionBot = dependencies.bots.value.firstOrNull { it.id == session.botId }
        val providerId = resolveProviderId(
            preferredProviderId = session.providerId,
            fallbackBot = sessionBot ?: selectedBot(state.selectedBotId),
        )
        val updatedState = state.copy(
            selectedSessionId = session.id,
            selectedBotId = session.botId,
            selectedProviderId = providerId,
            error = "",
        )
        dependencies.log("Chat session selected: ${session.id}")
        syncSessionBindings(
            sessionId = session.id,
            providerId = providerId,
            selectedBotId = updatedState.selectedBotId,
        )
        return updatedState
    }

    fun createSession(state: ChatUiState): ChatSessionControllerResult {
        val created = dependencies.createSession(
            botId = selectedBot(state.selectedBotId)?.id ?: dependencies.selectedBotId.value,
        )
        val updatedState = state.copy(
            selectedSessionId = created.id,
            error = "",
        )
        dependencies.log("Chat session created and selected: ${created.id}")
        syncSessionBindings(
            sessionId = created.id,
            providerId = updatedState.selectedProviderId,
            selectedBotId = updatedState.selectedBotId,
        )
        return ChatSessionControllerResult(
            uiState = updatedState,
            session = created,
        )
    }

    fun deleteSelectedSession(state: ChatUiState): ChatSessionControllerResult {
        dependencies.deleteSession(state.selectedSessionId)
        val nextSession = dependencies.sessions.value.firstAppSession()
        return if (nextSession != null) {
            ChatSessionControllerResult(
                uiState = selectSession(state, nextSession.id),
                session = nextSession,
            )
        } else {
            createSession(state)
        }
    }

    fun deleteSession(state: ChatUiState, sessionId: String): ChatSessionControllerResult {
        val deletingCurrent = sessionId == state.selectedSessionId
        dependencies.deleteSession(sessionId)
        if (!deletingCurrent) {
            return ChatSessionControllerResult(uiState = state)
        }
        val nextSession = dependencies.sessions.value.firstAppSession()
        return if (nextSession != null) {
            ChatSessionControllerResult(
                uiState = selectSession(state, nextSession.id),
                session = nextSession,
            )
        } else {
            createSession(state)
        }
    }

    fun renameSession(sessionId: String, title: String) {
        dependencies.renameSession(sessionId, title)
    }

    fun toggleSessionPinned(sessionId: String) {
        dependencies.toggleSessionPinned(sessionId)
    }

    fun toggleSessionStt(state: ChatUiState) {
        val sessionId = state.selectedSessionId
        val session = dependencies.session(sessionId)
        val next = !session.sessionSttEnabled
        dependencies.updateSessionServiceFlags(sessionId, sessionSttEnabled = next)
        dependencies.log("Chat session STT toggled: session=$sessionId enabled=$next")
    }

    fun toggleSessionTts(state: ChatUiState) {
        val sessionId = state.selectedSessionId
        val session = dependencies.session(sessionId)
        val next = !session.sessionTtsEnabled
        dependencies.updateSessionServiceFlags(sessionId, sessionTtsEnabled = next)
        dependencies.log("Chat session TTS toggled: session=$sessionId enabled=$next")
    }

    fun resolveProviderId(
        preferredProviderId: String?,
        fallbackBot: BotProfile?,
    ): String {
        val enabledProviders = dependencies.providers.value.filter { provider ->
            provider.enabled && ProviderCapability.CHAT in provider.capabilities
        }
        if (!preferredProviderId.isNullOrBlank() && enabledProviders.any { it.id == preferredProviderId }) {
            return preferredProviderId
        }
        val configProviderId = fallbackBot
            ?.configProfileId
            ?.let { dependencies.resolveConfig(it).defaultChatProviderId }
        if (!configProviderId.isNullOrBlank() && enabledProviders.any { it.id == configProviderId }) {
            return configProviderId
        }
        return enabledProviders.firstOrNull()?.id.orEmpty()
    }

    fun syncSessionBindings(
        state: ChatUiState,
        sessionId: String = state.selectedSessionId,
        providerId: String = state.selectedProviderId,
    ) {
        syncSessionBindings(
            sessionId = sessionId,
            providerId = providerId,
            selectedBotId = state.selectedBotId,
        )
    }

    private fun syncSessionBindings(
        sessionId: String,
        providerId: String,
        selectedBotId: String,
    ) {
        val personaId = resolveSessionPersonaId(sessionId, selectedBotId)
        dependencies.updateSessionBindings(
            sessionId = sessionId,
            providerId = providerId,
            personaId = personaId,
            botId = selectedBot(selectedBotId)?.id ?: dependencies.selectedBotId.value,
        )
    }

    private fun currentSession(state: ChatUiState): ConversationSession? {
        return dependencies.sessions.value.firstOrNull { it.id == state.selectedSessionId }
    }

    private fun resolveSessionPersonaId(sessionId: String, selectedBotId: String): String {
        val sessionPersonaId = dependencies.sessions.value
            .firstOrNull { it.id == sessionId }
            ?.personaId
            ?.takeIf { it.isNotBlank() && it != "default" }
        if (sessionPersonaId != null) {
            return sessionPersonaId
        }
        return selectedPersona(selectedBotId)?.id.orEmpty()
    }

    private fun selectedPersona(selectedBotId: String) = dependencies.personas.value.firstOrNull {
        it.enabled && it.id == selectedBot(selectedBotId)?.defaultPersonaId
    } ?: dependencies.personas.value.firstOrNull { it.enabled }

    private fun selectedBot(selectedBotId: String): BotProfile? {
        return dependencies.bots.value.firstOrNull { it.id == selectedBotId }
            ?: dependencies.bots.value.firstOrNull()
    }
}

private fun ConversationSession.isAppSession(): Boolean = platformId != "qq"

private fun List<ConversationSession>.firstAppSession(
    predicate: (ConversationSession) -> Boolean = { true },
): ConversationSession? = firstOrNull { it.isAppSession() && predicate(it) }

