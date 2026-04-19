package com.astrbot.android.ui.viewmodel

import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astrbot.android.AppStrings
import com.astrbot.android.R
import com.astrbot.android.di.ChatViewModelDependencies
import com.astrbot.android.di.hilt.IoDispatcher
import com.astrbot.android.feature.chat.presentation.AppChatRuntimeDecision
import com.astrbot.android.feature.chat.presentation.AppChatSendEvent
import com.astrbot.android.feature.chat.presentation.AppChatSendHandler
import com.astrbot.android.feature.chat.presentation.AppChatSendRequest
import com.astrbot.android.feature.chat.runtime.AppChatPluginCommandService
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.PersonaProfile

import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.chat.ConversationSession
import com.astrbot.android.model.chat.MessageSessionRef
import com.astrbot.android.model.plugin.ErrorResult
import com.astrbot.android.model.plugin.ExternalPluginHostActionPolicy
import com.astrbot.android.model.plugin.ExternalPluginTriggerPolicy
import com.astrbot.android.model.plugin.HostActionRequest
import com.astrbot.android.model.plugin.MediaResult
import com.astrbot.android.model.plugin.NoOp
import com.astrbot.android.model.plugin.PluginBotSummary
import com.astrbot.android.model.plugin.PluginConfigSummary
import com.astrbot.android.model.plugin.PluginExecutionContext
import com.astrbot.android.model.plugin.PluginExecutionResult
import com.astrbot.android.model.plugin.PluginHostAction
import com.astrbot.android.model.plugin.PluginMessageSummary
import com.astrbot.android.model.plugin.PluginPermissionGrant
import com.astrbot.android.model.plugin.PluginTriggerMetadata
import com.astrbot.android.model.plugin.PluginTriggerSource
import com.astrbot.android.model.plugin.TextResult
import com.astrbot.android.feature.plugin.runtime.AppChatPluginRuntime
import com.astrbot.android.feature.plugin.runtime.DefaultAppChatPluginRuntime
import com.astrbot.android.feature.plugin.runtime.HOST_SKIP_COMMAND_STAGE_EXTRA_KEY
import com.astrbot.android.feature.plugin.runtime.PluginDispatchSkipReason
import com.astrbot.android.feature.plugin.runtime.PluginExecutionHostApi
import com.astrbot.android.feature.plugin.runtime.PluginMessageEvent
import com.astrbot.android.feature.plugin.runtime.PluginRuntimePlugin
import com.astrbot.android.feature.plugin.runtime.PluginV2CommandResponse
import com.astrbot.android.feature.plugin.runtime.PluginV2CommandResponseAttachment
import com.astrbot.android.feature.plugin.runtime.PluginV2MessageDispatchResult
import com.astrbot.android.feature.chat.runtime.botcommand.BotCommandContext
import com.astrbot.android.feature.chat.runtime.botcommand.BotCommandParser
import com.astrbot.android.feature.chat.runtime.botcommand.BotCommandRouter
import com.astrbot.android.feature.chat.runtime.botcommand.BotCommandSource

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.coroutines.CoroutineContext
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

data class ChatUiState(
    val selectedBotId: String = "qq-main",
    val selectedProviderId: String = "",
    val selectedSessionId: String = "",
    val streamingEnabled: Boolean = false,
    val isSending: Boolean = false,
    val error: String = "",
)

private data class PluginCommandConsumption(
    val replyText: String = "",
    val attachments: List<ConversationAttachment> = emptyList(),
    val handled: Boolean = false,
)

/** Returns true if this session belongs to the app platform (not QQ). */
private fun ConversationSession.isAppSession(): Boolean = platformId != "qq"

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val dependencies: ChatViewModelDependencies,
    private val appChatPluginRuntime: AppChatPluginRuntime = DefaultAppChatPluginRuntime,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val appChatSendHandler: AppChatSendHandler = dependencies.createChatSendHandler(
        appChatPluginRuntime = appChatPluginRuntime,
        ioDispatcher = ioDispatcher,
    )
    private val appChatPluginCommandService: AppChatPluginCommandService =
        dependencies.createAppChatPluginCommandService(appChatPluginRuntime)
    private val chatSessionController: ChatSessionController = dependencies.chatSessionController
    val bots = dependencies.bots
    val providers = dependencies.providers
    val configProfiles = dependencies.configProfiles
    val sessions = dependencies.sessions
    val personas = dependencies.personas

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    private var hasAppliedInitialSessionRestore = false
    private val defaultSessionId = dependencies.defaultSessionId
    private val defaultSessionTitle = dependencies.defaultSessionTitle

    init {
        val firstBot = dependencies.bots.value.firstOrNull()
        val firstSession = dependencies.sessions.value.firstAppSession()
        val providerId = resolveProviderId(
            preferredProviderId = firstSession?.providerId,
            fallbackBot = firstBot,
        )
        _uiState.value = _uiState.value.copy(
            selectedBotId = firstBot?.id ?: "qq-main",
            selectedProviderId = providerId,
            selectedSessionId = firstSession?.id ?: defaultSessionId,
        )
        syncSessionBindings(_uiState.value.selectedSessionId, providerId)

        viewModelScope.launch {
            sessions.collectLatest { allSessions ->
                if (allSessions.isEmpty()) return@collectLatest

                val currentId = _uiState.value.selectedSessionId
                val currentSession = allSessions.firstOrNull { it.id == currentId }

                if (!hasAppliedInitialSessionRestore) {
                    val preferredSession = when {
                        currentSession == null -> allSessions.firstAppSession()
                        currentSession.isAppSession() && currentSession.id != defaultSessionId -> currentSession
                        else -> allSessions.firstAppSession { it.id != defaultSessionId }
                            ?: currentSession.takeIf { it.isAppSession() }
                            ?: allSessions.firstAppSession()
                    } ?: return@collectLatest

                    hasAppliedInitialSessionRestore = true
                    if (preferredSession.id != currentId) {
                        val restoredProviderId = resolveProviderId(
                            preferredProviderId = preferredSession.providerId,
                            fallbackBot = bots.value.firstOrNull { it.id == preferredSession.botId } ?: selectedBot(),
                        )
                        _uiState.value = _uiState.value.copy(
                            selectedSessionId = preferredSession.id,
                            selectedBotId = preferredSession.botId,
                            selectedProviderId = restoredProviderId,
                            error = "",
                        )
                        dependencies.log("Chat session restored on launch: ${preferredSession.id}")
                        syncSessionBindings(preferredSession.id, restoredProviderId)
                        return@collectLatest
                    }
                }

                currentSession?.let { session ->
                    val syncedProviderId = resolveProviderId(
                        preferredProviderId = session.providerId,
                        fallbackBot = bots.value.firstOrNull { it.id == session.botId } ?: selectedBot(),
                    )
                    val shouldSyncBot = _uiState.value.selectedBotId != session.botId
                    val shouldSyncProvider = session.providerId.isNotBlank() && _uiState.value.selectedProviderId != syncedProviderId
                    if (shouldSyncBot || shouldSyncProvider) {
                        _uiState.value = _uiState.value.copy(
                            selectedBotId = session.botId,
                            selectedProviderId = syncedProviderId,
                        )
                        dependencies.log(
                            "Chat session context synced: session=${session.id} bot=${session.botId} provider=${syncedProviderId.ifBlank { "none" }}",
                        )
                    }
                }
            }
        }
    }

    fun selectBot(botId: String) {
        _uiState.value = chatSessionController.selectBot(_uiState.value, botId)
    }

    fun selectProvider(providerId: String) {
        _uiState.value = chatSessionController.selectProvider(_uiState.value, providerId)
    }

    fun selectSession(sessionId: String) {
        _uiState.value = chatSessionController.selectSession(_uiState.value, sessionId)
    }

    fun createSession() {
        createSessionInternal()
    }

    private fun createSessionInternal(): ConversationSession {
        val result = chatSessionController.createSession(_uiState.value)
        _uiState.value = result.uiState
        return requireNotNull(result.session) { "ChatSessionController.createSession must return the created session" }
    }

    fun deleteSelectedSession() {
        _uiState.value = chatSessionController.deleteSelectedSession(_uiState.value).uiState
    }

    fun deleteSession(sessionId: String) {
        _uiState.value = chatSessionController.deleteSession(_uiState.value, sessionId).uiState
    }

    fun renameSession(sessionId: String, title: String) {
        chatSessionController.renameSession(sessionId, title)
    }

    fun toggleSessionPinned(sessionId: String) {
        chatSessionController.toggleSessionPinned(sessionId)
    }

    fun toggleSessionStt() {
        chatSessionController.toggleSessionStt(_uiState.value)
    }

    fun toggleSessionTts() {
        chatSessionController.toggleSessionTts(_uiState.value)
    }

    fun toggleStreaming() {
        _uiState.value = _uiState.value.copy(streamingEnabled = !_uiState.value.streamingEnabled)
        dependencies.log("Chat streaming toggled: enabled=${_uiState.value.streamingEnabled}")
    }

    fun sendMessage(input: String, attachments: List<ConversationAttachment> = emptyList()) {
        var sessionId = _uiState.value.selectedSessionId
        val content = input.trim()
        if ((content.isBlank() && attachments.isEmpty()) || _uiState.value.isSending) return

        // Prevent sending app input into a QQ session. Auto-create a new app session.
        val currentSendSession = sessions.value.firstOrNull { it.id == sessionId }
        if (currentSendSession != null && !currentSendSession.isAppSession()) {
            val newSession = createSessionInternal()
            sessionId = newSession.id
            dependencies.log("Chat send redirected: QQ session ${currentSendSession.id} -> new app session $sessionId")
        }

        val unsupportedSlashCommand = attachments.isEmpty() && appChatPluginCommandService.isUnsupportedPluginCommand(content)

        if (attachments.isEmpty() && handleSessionCommand(sessionId, content)) {
            return
        }

        val provider = selectedProvider()
        if (provider == null) {
            _uiState.value = _uiState.value.copy(error = AppStrings.get(R.string.chat_no_provider_selected))
            dependencies.log("Chat send blocked: no available provider")
            return
        }

        val botSnapshot = selectedBot()
        val botIdSnapshot = botSnapshot?.id ?: dependencies.selectedBotId.value
        val personaIdSnapshot = resolveSessionPersonaId(sessionId)
        val configSnapshot = botSnapshot?.configProfileId?.let { dependencies.resolveConfig(it) }
        val audioAttachments = attachments.filter { it.type == "audio" }
        val nonAudioAttachments = attachments.filterNot { it.type == "audio" }

        _uiState.value = _uiState.value.copy(isSending = true, error = "")

        viewModelScope.launch {
            var assistantMessageId: String? = null
            try {
                dependencies.withSessionLock(sessionId) sessionLock@{
                    val session = dependencies.session(sessionId)
                    val config = configSnapshot
                    val ttsSuffixMatched = content.endsWith("~")
                    val alwaysTtsEnabled = config?.alwaysTtsEnabled == true
                    val cleanedContent = content.removeSuffix("~").trim()
                    val normalizedInput = cleanedContent.ifBlank { content }
                    val sttProvider = config
                        ?.defaultSttProviderId
                        ?.takeIf { config.sttEnabled && session.sessionSttEnabled }
                        ?.let(::resolveSttProvider)
                    val ttsProvider = config
                        ?.defaultTtsProviderId
                        ?.takeIf { config.ttsEnabled && session.sessionTtsEnabled }
                        ?.let(::resolveTtsProvider)

                    when {
                        config?.ttsEnabled != true -> dependencies.log("Chat TTS skipped: config TTS is disabled")
                        !session.sessionTtsEnabled -> dependencies.log("Chat TTS skipped: session TTS is disabled")
                        ttsProvider == null -> dependencies.log(
                            "Chat TTS skipped: no usable TTS provider configured (selected=${config.defaultTtsProviderId.ifBlank { "-" }})",
                        )
                        alwaysTtsEnabled -> dependencies.log("Chat TTS trigger matched: provider=${ttsProvider.name} mode=always-on")
                        !ttsSuffixMatched -> dependencies.log("Chat TTS skipped: latest input has no ~ suffix")
                        else -> dependencies.log("Chat TTS trigger matched: provider=${ttsProvider.name}")
                    }

                    val transcribedAudioText = if (audioAttachments.isNotEmpty() && sttProvider != null) {
                        withContext(ioDispatcher) {
                            runCatching {
                                buildList {
                                    for (attachment in audioAttachments) {
                                        add(dependencies.transcribeAudio(sttProvider, attachment))
                                    }
                                }.joinToString("\n")
                            }.onFailure { error ->
                                error.rethrowIfCancellation()
                                dependencies.log("Chat STT failed: ${error.message ?: error.javaClass.simpleName}")
                            }.getOrNull()
                        }
                    } else {
                        null
                    }

                    val finalUserContent = buildString {
                        if (normalizedInput.isNotBlank()) {
                            append(normalizedInput)
                        }
                        transcribedAudioText?.takeIf { it.isNotBlank() }?.let { sttText ->
                            if (isNotBlank()) append("\n\n")
                            append(sttText)
                        }
                    }.trim()

                    dependencies.updateSessionBindings(
                        sessionId = sessionId,
                        providerId = provider.id,
                        personaId = personaIdSnapshot,
                        botId = botIdSnapshot,
                    )
                    var runtimeSkipped = false
                    var runtimeFailed = false
                    var completedAssistantMessageId: String? = null
                    appChatSendHandler.send(
                        AppChatSendRequest(
                            sessionId = sessionId,
                            text = finalUserContent,
                            attachments = nonAudioAttachments + audioAttachments,
                            beforeRuntime = { context ->
                                maybeAutoRenameSession(
                                    sessionId,
                                    finalUserContent.ifBlank { attachments.firstOrNull()?.fileName ?: "Image" },
                                )
                                val userMessage = dependencies.session(context.sessionId)
                                    .messages
                                    .firstOrNull { it.id == context.userMessageId }
                                    ?: error("User message ${context.userMessageId} not found in session ${context.sessionId}")
                                val consumedByPlugin = appChatPluginCommandService.dispatchPlugins(
                                    trigger = PluginTriggerSource.BeforeSendMessage,
                                    session = dependencies.session(context.sessionId),
                                    message = userMessage,
                                    provider = provider,
                                    bot = botSnapshot,
                                    personaId = personaIdSnapshot,
                                    config = config,
                                    suppressV2CommandStage = unsupportedSlashCommand,
                                )
                                if (consumedByPlugin) {
                                    AppChatRuntimeDecision.Skip("plugin_consumed")
                                } else {
                                    AppChatRuntimeDecision.Continue
                                }
                            },
                            failureMessage = { message ->
                                AppStrings.get(R.string.chat_request_failed_prefix, message)
                            },
                        ),
                    ).collect { event ->
                        when (event) {
                            is AppChatSendEvent.Rejected -> {
                                _uiState.value = _uiState.value.copy(error = event.reason)
                            }
                            is AppChatSendEvent.RuntimeSkipped -> {
                                runtimeSkipped = true
                            }
                            is AppChatSendEvent.Started -> {
                                assistantMessageId = event.assistantMessageId
                            }
                            is AppChatSendEvent.AssistantUpdated -> {
                                assistantMessageId = event.assistantMessageId
                            }
                            is AppChatSendEvent.AttachmentsUpdated -> {
                                assistantMessageId = event.assistantMessageId
                            }
                            is AppChatSendEvent.Completed -> {
                                assistantMessageId = event.assistantMessageId
                                completedAssistantMessageId = event.assistantMessageId
                            }
                            is AppChatSendEvent.Failed -> {
                                assistantMessageId = event.assistantMessageId
                                runtimeFailed = true
                                _uiState.value = _uiState.value.copy(error = event.message)
                            }
                        }
                    }
                    if (runtimeSkipped || runtimeFailed) {
                        return@sessionLock
                    }
                    val llmAssistantMessageId = completedAssistantMessageId ?: assistantMessageId ?: return@sessionLock
                    val llmAssistantMessage = dependencies.session(sessionId)
                        .messages
                        .firstOrNull { it.id == llmAssistantMessageId }
                        ?: return@sessionLock
                    appChatPluginCommandService.dispatchPlugins(
                        trigger = PluginTriggerSource.AfterModelResponse,
                        session = dependencies.session(sessionId),
                        message = llmAssistantMessage,
                        provider = provider,
                        bot = botSnapshot,
                        personaId = personaIdSnapshot,
                        config = config,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val message = error.message ?: error.javaClass.simpleName
                assistantMessageId?.let { messageId ->
                    dependencies.updateMessage(
                        sessionId = sessionId,
                        messageId = messageId,
                        content = AppStrings.get(R.string.chat_request_failed_prefix, message),
                    )
                } ?: dependencies.appendMessage(
                    sessionId = sessionId,
                    role = "assistant",
                    content = AppStrings.get(R.string.chat_request_failed_prefix, message),
                )
                _uiState.value = _uiState.value.copy(error = message)
            } finally {
                _uiState.value = _uiState.value.copy(isSending = false)
            }
        }
    }

    fun sessionMessages(sessionId: String = _uiState.value.selectedSessionId): List<ConversationMessage> {
        return dependencies.session(sessionId).messages
    }

    fun currentSession(): ConversationSession? {
        return sessions.value.firstOrNull { it.id == _uiState.value.selectedSessionId }
            ?: sessions.value.firstAppSession()
    }

    fun currentPersona() = personas.value.firstOrNull {
        it.enabled && it.id == resolveSessionPersonaId(_uiState.value.selectedSessionId)
    } ?: selectedPersona()

    fun selectPersona(personaId: String) {
        val persona = personas.value.firstOrNull { it.id == personaId && it.enabled } ?: return
        val sessionId = _uiState.value.selectedSessionId
        val providerId = _uiState.value.selectedProviderId
        dependencies.updateSessionBindings(
            sessionId = sessionId,
            providerId = providerId,
            personaId = persona.id,
            botId = selectedBot()?.id ?: dependencies.selectedBotId.value,
        )
        dependencies.log("Chat persona selected: session=$sessionId persona=${persona.id}")
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
        return chatSessionController.resolveProviderId(preferredProviderId, fallbackBot)
    }

    private fun syncSessionBindings(sessionId: String, providerId: String) {
        chatSessionController.syncSessionBindings(
            state = _uiState.value,
            sessionId = sessionId,
            providerId = providerId,
        )
    }

    private fun resolveSessionPersonaId(sessionId: String): String {
        val sessionPersonaId = sessions.value
            .firstOrNull { it.id == sessionId }
            ?.personaId
            ?.takeIf { it.isNotBlank() && it != "default" }
        if (sessionPersonaId != null) {
            return sessionPersonaId
        }
        return selectedPersona()?.id.orEmpty()
    }

    private fun selectedPersona() = personas.value.firstOrNull {
        it.enabled && it.id == selectedBot()?.defaultPersonaId
    } ?: personas.value.firstOrNull { it.enabled }

    private fun maybeAutoRenameSession(sessionId: String, content: String) {
        val session = dependencies.session(sessionId)
        if (session.title != defaultSessionTitle) return
        val title = content.lines().firstOrNull().orEmpty().trim().take(32)
            .ifBlank { defaultSessionTitle }
        dependencies.syncSystemSessionTitle(sessionId, title)
    }

    private fun handleSessionCommand(sessionId: String, content: String): Boolean {
        val session = dependencies.session(sessionId)
        val bot = selectedBot() ?: return false
        val parsedCommand = BotCommandParser.parse(content)
        if (parsedCommand != null && !BotCommandRouter.supports(parsedCommand.name)) {
            return appChatPluginCommandService.handlePluginCommand(
                session = session,
                bot = bot,
                content = content,
                provider = selectedProvider(),
                personaId = resolveSessionPersonaId(session.id),
                languageTag = currentLanguageTag(),
            )
        }
        val result = BotCommandRouter.handle(
            input = content,
            context = BotCommandContext(
                source = BotCommandSource.APP_CHAT,
                languageTag = currentLanguageTag(),
                sessionId = sessionId,
                session = session,
                sessions = sessions.value,
                bot = bot,
                availableBots = bots.value,
                config = dependencies.resolveConfig(bot.configProfileId),
                activeProviderId = _uiState.value.selectedProviderId.ifBlank { session.providerId },
                availableProviders = providers.value.filter { it.enabled && ProviderCapability.CHAT in it.capabilities },
                currentPersona = currentPersona(),
                availablePersonas = personas.value.filter { it.enabled },
                messageType = session.messageType,
                createSession = { createSessionInternal() },
                deleteSession = { targetSessionId ->
                    deleteSession(targetSessionId)
                },
                renameSession = { targetSessionId, title ->
                    renameSession(targetSessionId, title)
                },
                selectSession = { targetSessionId ->
                    selectSession(targetSessionId)
                },
                updateConfig = { profile ->
                    dependencies.saveConfig(profile)
                },
                updateBot = { profile ->
                    dependencies.saveBot(profile)
                },
                updateProvider = { provider ->
                    dependencies.saveProvider(provider)
                },
                updateSessionServiceFlags = { sttEnabled, ttsEnabled ->
                    dependencies.updateSessionServiceFlags(
                        sessionId = sessionId,
                        sessionSttEnabled = sttEnabled,
                        sessionTtsEnabled = ttsEnabled,
                    )
                },
                replaceMessages = { messages ->
                    dependencies.replaceMessages(sessionId, messages)
                },
                updateSessionBindings = { providerId, personaId, botId ->
                    dependencies.updateSessionBindings(
                        sessionId = sessionId,
                        providerId = providerId,
                        personaId = personaId,
                        botId = botId,
                    )
                },
            ),
        )
        if (result.handled) {
            result.replyText?.let { reply ->
                dependencies.appendMessage(
                    sessionId = sessionId,
                    role = "assistant",
                    content = reply,
                )
            }
            dependencies.log("Chat command handled via router: ${content.substringBefore(' ')} session=$sessionId")
            return result.stopModelDispatch
        }
        return false
    }

    private fun currentLanguageTag(): String {
        return AppCompatDelegate.getApplicationLocales()[0]
            ?.toLanguageTag()
            .orEmpty()
            .ifBlank { "zh" }
    }

    private fun Throwable.rethrowIfCancellation() {
        if (this is CancellationException) {
            throw this
        }
    }



    private fun resolveSttProvider(providerId: String): ProviderProfile? {
        return providers.value.firstOrNull {
            it.id == providerId &&
                it.enabled &&
                ProviderCapability.STT in it.capabilities
        }
    }

    private fun resolveTtsProvider(providerId: String): ProviderProfile? {
        return providers.value.firstOrNull {
            it.id == providerId &&
                it.enabled &&
                ProviderCapability.TTS in it.capabilities
        }
    }

    private fun selectedStreamingIntervalMs(
        config: ConfigProfile? = selectedBot()
            ?.configProfileId
            ?.let { dependencies.resolveConfig(it) },
    ): Long {
        return config
            ?.streamingMessageIntervalMs
            ?.coerceIn(0, 5000)
            ?.toLong()
            ?: 120L
    }
}

private fun List<ConversationSession>.firstAppSession(
    predicate: (ConversationSession) -> Boolean = { true },
): ConversationSession? = firstOrNull { it.isAppSession() && predicate(it) }

