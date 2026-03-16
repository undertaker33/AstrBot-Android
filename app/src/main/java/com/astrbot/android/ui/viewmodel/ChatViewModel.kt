package com.astrbot.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astrbot.android.data.BotRepository
import com.astrbot.android.data.ChatCompletionService
import com.astrbot.android.data.ConfigRepository
import com.astrbot.android.data.ConversationRepository
import com.astrbot.android.data.PersonaRepository
import com.astrbot.android.data.ProviderRepository
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ConversationMessage
import com.astrbot.android.model.ConversationSession
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.runtime.RuntimeLogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

data class ChatUiState(
    val selectedBotId: String = "qq-main",
    val selectedProviderId: String = "",
    val selectedSessionId: String = ConversationRepository.DEFAULT_SESSION_ID,
    val streamingEnabled: Boolean = false,
    val isSending: Boolean = false,
    val error: String = "",
)

class ChatViewModel : ViewModel() {
    val bots = BotRepository.botProfiles
    val providers = ProviderRepository.providers
    val configProfiles = ConfigRepository.profiles
    val sessions = ConversationRepository.sessions
    val personas = PersonaRepository.personas

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        val firstBot = BotRepository.botProfiles.value.firstOrNull()
        val firstSession = ConversationRepository.sessions.value.firstOrNull()
        val providerId = resolveProviderId(
            preferredProviderId = firstSession?.providerId,
            fallbackBot = firstBot,
        )
        _uiState.value = _uiState.value.copy(
            selectedBotId = firstBot?.id ?: "qq-main",
            selectedProviderId = providerId,
            selectedSessionId = firstSession?.id ?: ConversationRepository.DEFAULT_SESSION_ID,
        )
        syncSessionBindings(_uiState.value.selectedSessionId, providerId)

        viewModelScope.launch {
            BotRepository.selectedBotId.collectLatest { botId ->
                val bot = bots.value.firstOrNull { it.id == botId } ?: return@collectLatest
                val provider = resolveProviderId(
                    preferredProviderId = currentSession()?.providerId,
                    fallbackBot = bot,
                )
                _uiState.value = _uiState.value.copy(
                    selectedBotId = bot.id,
                    selectedProviderId = provider,
                )
                syncSessionBindings(_uiState.value.selectedSessionId, provider)
            }
        }
    }

    fun selectBot(botId: String) {
        BotRepository.select(botId)
        val bot = bots.value.firstOrNull { it.id == botId }
        val providerId = resolveProviderId(
            preferredProviderId = bot?.defaultProviderId?.ifBlank { currentSession()?.providerId },
            fallbackBot = bot,
        )
        _uiState.value = _uiState.value.copy(
            selectedBotId = botId,
            selectedProviderId = providerId,
            error = "",
        )
        RuntimeLogRepository.append(
            "Chat bot selected: ${bot?.displayName ?: botId}, provider=${providerId.ifBlank { "none" }}",
        )
        syncSessionBindings(_uiState.value.selectedSessionId, providerId)
    }

    fun selectProvider(providerId: String) {
        _uiState.value = _uiState.value.copy(selectedProviderId = providerId, error = "")
        RuntimeLogRepository.append("Chat provider selected: ${providerId.ifBlank { "none" }}")
        syncSessionBindings(_uiState.value.selectedSessionId, providerId)
    }

    fun selectSession(sessionId: String) {
        val session = ConversationRepository.session(sessionId)
        val providerId = resolveProviderId(
            preferredProviderId = session.providerId,
            fallbackBot = selectedBot(),
        )
        _uiState.value = _uiState.value.copy(
            selectedSessionId = session.id,
            selectedProviderId = providerId,
            error = "",
        )
        RuntimeLogRepository.append("Chat session selected: ${session.id}")
        syncSessionBindings(session.id, providerId)
    }

    fun createSession() {
        val created = ConversationRepository.createSession(botId = selectedBot()?.id ?: BotRepository.selectedBotId.value)
        _uiState.value = _uiState.value.copy(
            selectedSessionId = created.id,
            error = "",
        )
        RuntimeLogRepository.append("Chat session created and selected: ${created.id}")
        syncSessionBindings(created.id, _uiState.value.selectedProviderId)
    }

    fun deleteSelectedSession() {
        val currentId = _uiState.value.selectedSessionId
        ConversationRepository.deleteSession(currentId)
        val nextSession = sessions.value.firstOrNull()
        if (nextSession != null) {
            selectSession(nextSession.id)
        } else {
            createSession()
        }
    }

    fun toggleStreaming() {
        _uiState.value = _uiState.value.copy(streamingEnabled = !_uiState.value.streamingEnabled)
        RuntimeLogRepository.append("Chat streaming toggled: enabled=${_uiState.value.streamingEnabled}")
    }

    fun sendMessage(input: String) {
        val sessionId = _uiState.value.selectedSessionId
        val content = input.trim()
        if (content.isBlank() || _uiState.value.isSending) return

        val provider = selectedProvider()
        if (provider == null) {
            _uiState.value = _uiState.value.copy(error = "未选择对话模型")
            RuntimeLogRepository.append("Chat send blocked: no available provider")
            return
        }

        val persona = selectedPersona()
        syncSessionBindings(sessionId, provider.id)
        ConversationRepository.appendMessage(sessionId, "user", content)
        maybeAutoRenameSession(sessionId, content)
        _uiState.value = _uiState.value.copy(isSending = true, error = "")

        viewModelScope.launch {
            try {
                val currentSession = ConversationRepository.session(sessionId)
                val response = withContext(Dispatchers.IO) {
                    ChatCompletionService.sendChat(
                        provider = provider,
                        messages = currentSession.messages.takeLast(currentSession.maxContextMessages),
                        systemPrompt = buildSystemPrompt(persona?.systemPrompt),
                    )
                }
                ConversationRepository.appendMessage(sessionId, "assistant", response)
                _uiState.value = _uiState.value.copy(isSending = false)
            } catch (error: Exception) {
                val message = error.message ?: error.javaClass.simpleName
                ConversationRepository.appendMessage(
                    sessionId = sessionId,
                    role = "assistant",
                    content = "请求失败：$message",
                )
                _uiState.value = _uiState.value.copy(
                    isSending = false,
                    error = message,
                )
            }
        }
    }

    fun sessionMessages(sessionId: String = _uiState.value.selectedSessionId): List<ConversationMessage> {
        return ConversationRepository.session(sessionId).messages
    }

    fun currentSession(): ConversationSession? {
        return sessions.value.firstOrNull { it.id == _uiState.value.selectedSessionId }
            ?: sessions.value.firstOrNull()
    }

    fun selectedBot(): BotProfile? {
        return bots.value.firstOrNull { it.id == _uiState.value.selectedBotId }
            ?: bots.value.firstOrNull()
    }

    private fun selectedProvider(): ProviderProfile? {
        return providers.value.firstOrNull {
            it.id == _uiState.value.selectedProviderId &&
                it.enabled &&
                ProviderCapability.CHAT in it.capabilities
        } ?: firstEnabledChatProvider()
    }

    private fun firstEnabledChatProvider(): ProviderProfile? {
        return providers.value.firstOrNull { it.enabled && ProviderCapability.CHAT in it.capabilities }
    }

    private fun resolveProviderId(
        preferredProviderId: String?,
        fallbackBot: BotProfile?,
    ): String {
        val enabledProviders = providers.value.filter { it.enabled && ProviderCapability.CHAT in it.capabilities }
        if (!preferredProviderId.isNullOrBlank() && enabledProviders.any { it.id == preferredProviderId }) {
            return preferredProviderId
        }
        val botProviderId = fallbackBot?.defaultProviderId
        val configProviderId = fallbackBot
            ?.configProfileId
            ?.let { ConfigRepository.resolve(it).defaultChatProviderId }
        if (!configProviderId.isNullOrBlank() && enabledProviders.any { it.id == configProviderId }) {
            return configProviderId
        }
        if (!botProviderId.isNullOrBlank() && enabledProviders.any { it.id == botProviderId }) {
            return botProviderId
        }
        return enabledProviders.firstOrNull()?.id.orEmpty()
    }

    private fun syncSessionBindings(sessionId: String, providerId: String) {
        val personaId = selectedPersona()?.id.orEmpty()
        ConversationRepository.updateSessionBindings(
            sessionId = sessionId,
            providerId = providerId,
            personaId = personaId,
            botId = selectedBot()?.id ?: BotRepository.selectedBotId.value,
        )
    }

    private fun selectedPersona() = personas.value.firstOrNull {
        it.enabled && it.id == selectedBot()?.defaultPersonaId
    } ?: personas.value.firstOrNull { it.enabled }

    private fun maybeAutoRenameSession(sessionId: String, content: String) {
        val session = ConversationRepository.session(sessionId)
        if (session.title != ConversationRepository.DEFAULT_SESSION_TITLE) return
        val title = content.lines().firstOrNull().orEmpty().trim().take(32)
            .ifBlank { ConversationRepository.DEFAULT_SESSION_TITLE }
        ConversationRepository.renameSession(sessionId, title)
    }

    private fun buildSystemPrompt(personaPrompt: String?): String? {
        val config = selectedBot()?.configProfileId?.let(ConfigRepository::resolve)
        val promptParts = mutableListOf<String>()
        personaPrompt?.trim()?.takeIf { it.isNotBlank() }?.let(promptParts::add)
        if (config?.realWorldTimeAwarenessEnabled == true) {
            val now = ZonedDateTime.now()
            promptParts += "Current local time: ${now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"))}."
        }
        return promptParts.joinToString("\n\n").ifBlank { null }
    }
}
