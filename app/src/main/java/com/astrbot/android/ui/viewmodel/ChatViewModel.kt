package com.astrbot.android.ui.viewmodel

import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astrbot.android.AppStrings
import com.astrbot.android.R
import com.astrbot.android.data.ChatCompletionService
import com.astrbot.android.data.PluginRepository
import com.astrbot.android.data.StreamingResponseSegmenter
import com.astrbot.android.data.plugin.PluginStoragePaths
import com.astrbot.android.di.ChatViewModelDependencies
import com.astrbot.android.di.DefaultChatViewModelDependencies
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.PersonaProfile
import com.astrbot.android.model.PersonaToolEnablementSnapshot
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.chat.ConversationSession
import com.astrbot.android.model.chat.MessageSessionRef
import com.astrbot.android.model.plugin.PluginV2StreamingMode
import com.astrbot.android.model.plugin.ErrorResult
import com.astrbot.android.model.plugin.ExternalPluginHostActionPolicy
import com.astrbot.android.model.plugin.ExternalPluginMediaSourceResolver
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
import com.astrbot.android.model.hasNativeStreamingSupport
import com.astrbot.android.runtime.plugin.AppChatLlmPipelineRuntime
import com.astrbot.android.runtime.plugin.AppChatPluginRuntime
import com.astrbot.android.runtime.plugin.DefaultAppChatPluginRuntime
import com.astrbot.android.runtime.plugin.DefaultPluginHostCapabilityGateway
import com.astrbot.android.runtime.plugin.ExternalPluginHostActionExecutor
import com.astrbot.android.runtime.plugin.HOST_SKIP_COMMAND_STAGE_EXTRA_KEY
import com.astrbot.android.runtime.plugin.PluginDispatchSkipReason
import com.astrbot.android.runtime.plugin.PluginExecutionHostApi
import com.astrbot.android.runtime.plugin.PluginLlmResponse
import com.astrbot.android.runtime.plugin.PluginMessageEvent
import com.astrbot.android.runtime.plugin.PluginMessageEventResult
import com.astrbot.android.runtime.plugin.PluginProviderMessageDto
import com.astrbot.android.runtime.plugin.PluginProviderMessagePartDto
import com.astrbot.android.runtime.plugin.PluginProviderMessageRole
import com.astrbot.android.runtime.plugin.PluginProviderRequest
import com.astrbot.android.runtime.plugin.PluginLlmToolCall
import com.astrbot.android.runtime.plugin.PluginLlmToolCallDelta
import com.astrbot.android.runtime.plugin.PluginRuntimePlugin
import com.astrbot.android.runtime.plugin.PluginV2AfterSentView
import com.astrbot.android.runtime.plugin.PluginV2CommandResponse
import com.astrbot.android.runtime.plugin.PluginV2CommandResponseAttachment
import com.astrbot.android.runtime.plugin.PluginV2DispatchEngineProvider
import com.astrbot.android.runtime.plugin.PluginV2HostLlmDeliveryRequest
import com.astrbot.android.runtime.plugin.PluginV2HostLlmDeliveryResult
import com.astrbot.android.runtime.plugin.PluginV2HostPreparedReply
import com.astrbot.android.runtime.plugin.PluginV2HostSendResult
import com.astrbot.android.runtime.plugin.PluginV2LlmPipelineInput
import com.astrbot.android.runtime.plugin.PluginV2MessageDispatchResult
import com.astrbot.android.runtime.plugin.PluginV2ProviderInvocationResult
import com.astrbot.android.runtime.plugin.PluginV2ProviderStreamChunk
import com.astrbot.android.runtime.botcommand.BotCommandContext
import com.astrbot.android.runtime.botcommand.BotCommandParser
import com.astrbot.android.runtime.botcommand.BotCommandRouter
import com.astrbot.android.runtime.botcommand.BotCommandSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
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

private const val APP_CHAT_PLATFORM_ADAPTER_TYPE = "app_chat"

class ChatViewModel(
    private val dependencies: ChatViewModelDependencies = DefaultChatViewModelDependencies,
    private val appChatPluginRuntime: AppChatPluginRuntime = DefaultAppChatPluginRuntime,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO,
) : ViewModel() {
    private val hostCapabilityGateway = DefaultPluginHostCapabilityGateway()
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
        val firstSession = dependencies.sessions.value.firstOrNull()
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
                        currentSession == null -> allSessions.firstOrNull()
                        currentSession.id != defaultSessionId -> currentSession
                        else -> allSessions.firstOrNull { it.id != defaultSessionId }
                            ?: currentSession
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
        dependencies.log(
            "Chat bot selected: ${bot?.displayName ?: botId}, provider=${providerId.ifBlank { "none" }}",
        )
        syncSessionBindings(_uiState.value.selectedSessionId, providerId)
    }

    fun selectProvider(providerId: String) {
        _uiState.value = _uiState.value.copy(selectedProviderId = providerId, error = "")
        dependencies.log("Chat provider selected: ${providerId.ifBlank { "none" }}")
        syncSessionBindings(_uiState.value.selectedSessionId, providerId)
    }

    fun selectSession(sessionId: String) {
        val session = dependencies.session(sessionId)
        val sessionBot = bots.value.firstOrNull { it.id == session.botId }
        val providerId = resolveProviderId(
            preferredProviderId = session.providerId,
            fallbackBot = sessionBot ?: selectedBot(),
        )
        _uiState.value = _uiState.value.copy(
            selectedSessionId = session.id,
            selectedBotId = session.botId,
            selectedProviderId = providerId,
            error = "",
        )
        dependencies.log("Chat session selected: ${session.id}")
        syncSessionBindings(session.id, providerId)
    }

    fun createSession() {
        createSessionInternal()
    }

    private fun createSessionInternal(): ConversationSession {
        val created = dependencies.createSession(botId = selectedBot()?.id ?: dependencies.selectedBotId.value)
        _uiState.value = _uiState.value.copy(
            selectedSessionId = created.id,
            error = "",
        )
        dependencies.log("Chat session created and selected: ${created.id}")
        syncSessionBindings(created.id, _uiState.value.selectedProviderId)
        return created
    }

    fun deleteSelectedSession() {
        val currentId = _uiState.value.selectedSessionId
        dependencies.deleteSession(currentId)
        val nextSession = sessions.value.firstOrNull()
        if (nextSession != null) {
            selectSession(nextSession.id)
        } else {
            createSession()
        }
    }

    fun deleteSession(sessionId: String) {
        val deletingCurrent = sessionId == _uiState.value.selectedSessionId
        dependencies.deleteSession(sessionId)
        if (deletingCurrent) {
            val nextSession = sessions.value.firstOrNull()
            if (nextSession != null) {
                selectSession(nextSession.id)
            } else {
                createSession()
            }
        }
    }

    fun renameSession(sessionId: String, title: String) {
        dependencies.renameSession(sessionId, title)
    }

    fun toggleSessionPinned(sessionId: String) {
        dependencies.toggleSessionPinned(sessionId)
    }

    fun toggleSessionStt() {
        val sessionId = _uiState.value.selectedSessionId
        val session = dependencies.session(sessionId)
        val next = !session.sessionSttEnabled
        dependencies.updateSessionServiceFlags(sessionId, sessionSttEnabled = next)
        dependencies.log("Chat session STT toggled: session=$sessionId enabled=$next")
    }

    fun toggleSessionTts() {
        val sessionId = _uiState.value.selectedSessionId
        val session = dependencies.session(sessionId)
        val next = !session.sessionTtsEnabled
        dependencies.updateSessionServiceFlags(sessionId, sessionTtsEnabled = next)
        dependencies.log("Chat session TTS toggled: session=$sessionId enabled=$next")
    }

    fun toggleStreaming() {
        _uiState.value = _uiState.value.copy(streamingEnabled = !_uiState.value.streamingEnabled)
        dependencies.log("Chat streaming toggled: enabled=${_uiState.value.streamingEnabled}")
    }

    fun sendMessage(input: String, attachments: List<ConversationAttachment> = emptyList()) {
        val sessionId = _uiState.value.selectedSessionId
        val content = input.trim()
        if ((content.isBlank() && attachments.isEmpty()) || _uiState.value.isSending) return
        val unsupportedSlashCommand = attachments.isEmpty() && isUnsupportedPluginCommand(content)

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
        val personaSnapshot = currentPersona()
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
                    val wantsTts = config?.ttsEnabled == true &&
                        session.sessionTtsEnabled &&
                        (alwaysTtsEnabled || ttsSuffixMatched)
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
                    val userMessageId = dependencies.appendMessage(
                        sessionId = sessionId,
                        role = "user",
                        content = finalUserContent,
                        attachments = nonAudioAttachments + audioAttachments,
                    )
                    maybeAutoRenameSession(
                        sessionId,
                        finalUserContent.ifBlank { attachments.firstOrNull()?.fileName ?: "Image" },
                    )
                    val userMessage = dependencies.session(sessionId)
                        .messages
                        .firstOrNull { it.id == userMessageId }
                        ?: return@sessionLock
                    val consumedByPlugin = dispatchAppChatPlugins(
                        trigger = PluginTriggerSource.BeforeSendMessage,
                        session = dependencies.session(sessionId),
                        message = userMessage,
                        provider = provider,
                        bot = botSnapshot,
                        personaId = personaIdSnapshot,
                        config = config,
                        suppressV2CommandStage = unsupportedSlashCommand,
                    )
                    if (consumedByPlugin) {
                        return@sessionLock
                    }

                    val currentSession = dependencies.session(sessionId)
                    val contextWindow = personaSnapshot?.maxContextMessages ?: currentSession.maxContextMessages
                    val scopedSession = currentSession.copy(maxContextMessages = contextWindow)
                    val llmAssistantMessage = deliverAppChatLlmPipelineIfSupported(
                        sessionId = sessionId,
                        session = currentSession,
                        scopedSession = scopedSession,
                        userMessage = userMessage,
                        provider = provider,
                        bot = botSnapshot,
                        persona = personaSnapshot,
                        personaId = personaIdSnapshot,
                        config = config,
                        wantsTts = wantsTts,
                        ttsProvider = ttsProvider,
                    )
                    if (llmAssistantMessage != null) {
                        assistantMessageId = llmAssistantMessage.id
                        dispatchAppChatPlugins(
                            trigger = PluginTriggerSource.AfterModelResponse,
                            session = dependencies.session(sessionId),
                            message = llmAssistantMessage,
                            provider = provider,
                            bot = botSnapshot,
                            personaId = personaIdSnapshot,
                            config = config,
                        )
                        return@sessionLock
                    }

                    assistantMessageId = dependencies.appendMessage(
                        sessionId = sessionId,
                        role = "assistant",
                        content = "",
                    )
                    val resolvedAssistantMessageId = assistantMessageId ?: return@sessionLock
                    val response = if (config?.textStreamingEnabled == true && provider.hasNativeStreamingSupport()) {
                        emitNativeStreamingResponse(
                            sessionId = sessionId,
                            messageId = resolvedAssistantMessageId,
                            provider = provider,
                            currentSession = scopedSession,
                            systemPrompt = buildSystemPrompt(personaSnapshot?.systemPrompt),
                            config = config,
                        )
                    } else {
                        val fullResponse = withContext(ioDispatcher) {
                            dependencies.sendConfiguredChat(
                                provider = provider,
                                messages = scopedSession.messages.takeLast(scopedSession.maxContextMessages),
                                systemPrompt = buildSystemPrompt(personaSnapshot?.systemPrompt),
                                config = config,
                                availableProviders = providers.value,
                            )
                        }
                        if (config?.textStreamingEnabled == true) {
                            emitPseudoStreamingResponse(sessionId, resolvedAssistantMessageId, fullResponse)
                        } else {
                            dependencies.updateMessage(sessionId, resolvedAssistantMessageId, content = fullResponse)
                        }
                        fullResponse
                    }

                    if (wantsTts && ttsProvider != null) {
                        if (config.voiceStreamingEnabled) {
                            emitVoiceStreamingAttachments(
                                sessionId = sessionId,
                                messageId = resolvedAssistantMessageId,
                                response = response,
                                provider = ttsProvider,
                                voiceId = config.ttsVoiceId,
                                readBracketedContent = config.ttsReadBracketedContent,
                            )
                        } else {
                            synthesizeSingleVoiceReply(
                                provider = ttsProvider,
                                text = response,
                                voiceId = config.ttsVoiceId,
                                readBracketedContent = config.ttsReadBracketedContent,
                            )?.let { attachment ->
                                dependencies.updateMessage(
                                    sessionId = sessionId,
                                    messageId = resolvedAssistantMessageId,
                                    attachments = listOf(attachment),
                                )
                            }
                        }
                    }

                    dependencies.session(sessionId)
                        .messages
                        .firstOrNull { it.id == resolvedAssistantMessageId }
                        ?.let { assistantMessage ->
                            dispatchAppChatPlugins(
                                trigger = PluginTriggerSource.AfterModelResponse,
                                session = dependencies.session(sessionId),
                                message = assistantMessage,
                                provider = provider,
                                bot = botSnapshot,
                                personaId = personaIdSnapshot,
                                config = config,
                            )
                        }
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

    private suspend fun deliverAppChatLlmPipelineIfSupported(
        sessionId: String,
        session: ConversationSession,
        scopedSession: ConversationSession,
        userMessage: ConversationMessage,
        provider: ProviderProfile,
        bot: BotProfile?,
        persona: PersonaProfile?,
        personaId: String,
        config: ConfigProfile?,
        wantsTts: Boolean,
        ttsProvider: ProviderProfile?,
    ): ConversationMessage? {
        val llmRuntime = appChatPluginRuntime as? AppChatLlmPipelineRuntime ?: return null
        val availableChatProviders = providers.value.filter { profile ->
            profile.enabled && ProviderCapability.CHAT in profile.capabilities
        }
        val streamingMode = resolveAppChatStreamingMode(config, provider)
        val llmEvent = buildAppChatPluginMessageEvent(
            trigger = PluginTriggerSource.BeforeSendMessage,
            session = session,
            message = userMessage,
            provider = provider,
            bot = bot,
            personaId = personaId,
            config = config,
        )
        var persistedAssistantMessageId: String? = null
        val deliveryResult = llmRuntime.deliverLlmPipeline(
            request = PluginV2HostLlmDeliveryRequest(
                pipelineInput = PluginV2LlmPipelineInput(
                    event = llmEvent,
                    messageIds = listOf(userMessage.id),
                    streamingMode = streamingMode,
                    availableProviderIds = availableChatProviders.map { profile -> profile.id },
                    availableModelIdsByProvider = availableChatProviders.associate { profile ->
                        profile.id to listOf(profile.model).filter { modelId -> modelId.isNotBlank() }
                    },
                    selectedProviderId = provider.id,
                    selectedModelId = provider.model,
                    systemPrompt = buildSystemPrompt(persona?.systemPrompt),
                    messages = scopedSession.messages
                        .takeLast(scopedSession.maxContextMessages)
                        .toPluginProviderMessages(),
                    personaToolEnablementSnapshot = persona?.let { activePersona ->
                        PersonaToolEnablementSnapshot(
                            personaId = activePersona.id,
                            enabled = activePersona.enabled,
                            enabledTools = activePersona.enabledTools.toSet(),
                        )
                    },
                    configProfileId = config?.id,
                    invokeProvider = { request, mode ->
                        invokeProviderForAppChatPipeline(
                            request = request,
                            mode = mode,
                            config = config,
                            availableProviders = availableChatProviders,
                        )
                    },
                ),
                conversationId = llmEvent.conversationId,
                platformAdapterType = APP_CHAT_PLATFORM_ADAPTER_TYPE,
                platformInstanceKey = bot?.id ?: session.botId,
                prepareReply = { pipelineResult ->
                    prepareAppChatPipelineReply(
                        pipelineResult = pipelineResult,
                        wantsTts = wantsTts,
                        ttsProvider = ttsProvider,
                        config = config,
                    )
                },
                sendReply = {
                    PluginV2HostSendResult(success = true)
                },
                persistDeliveredReply = { preparedReply, _, _ ->
                    persistedAssistantMessageId = persistAppChatPreparedReply(
                        sessionId = sessionId,
                        preparedReply = preparedReply,
                        streamingMode = streamingMode,
                    )
                },
            ),
        )
        return when (deliveryResult) {
            is PluginV2HostLlmDeliveryResult.Sent -> {
                persistedAssistantMessageId
                    ?.let { messageId ->
                        dependencies.session(sessionId).messages.firstOrNull { message -> message.id == messageId }
                    }
            }

            is PluginV2HostLlmDeliveryResult.Suppressed -> {
                dependencies.log(
                    "App chat llm result suppressed: requestId=${deliveryResult.pipelineResult.admission.requestId} session=$sessionId",
                )
                null
            }

            is PluginV2HostLlmDeliveryResult.SendFailed -> error(
                "App chat llm delivery failed: ${deliveryResult.sendResult.errorSummary.ifBlank { "send_failed" }}",
            )
        }
    }

    private suspend fun prepareAppChatPipelineReply(
        pipelineResult: com.astrbot.android.runtime.plugin.PluginV2LlmPipelineResult,
        wantsTts: Boolean,
        ttsProvider: ProviderProfile?,
        config: ConfigProfile?,
    ): PluginV2HostPreparedReply {
        val sendableResult = pipelineResult.sendableResult
        val attachments = if (wantsTts && ttsProvider != null && config != null) {
            buildAppChatVoiceReplyAttachments(
                response = sendableResult.text,
                provider = ttsProvider,
                voiceId = config.ttsVoiceId,
                voiceStreamingEnabled = config.voiceStreamingEnabled,
                readBracketedContent = config.ttsReadBracketedContent,
            )
        } else {
            sendableResult.attachments.toConversationAttachments()
        }
        return PluginV2HostPreparedReply(
            text = sendableResult.text,
            attachments = attachments,
            deliveredEntries = listOf(
                PluginV2AfterSentView.DeliveredEntry(
                    entryId = pipelineResult.admission.messageIds.firstOrNull().orEmpty().ifBlank { "assistant" },
                    entryType = "assistant",
                    textPreview = sendableResult.text.take(160),
                    attachmentCount = attachments.size,
                ),
            ),
        )
    }

    private suspend fun persistAppChatPreparedReply(
        sessionId: String,
        preparedReply: PluginV2HostPreparedReply,
        streamingMode: PluginV2StreamingMode,
    ): String {
        val shouldPseudoStream = streamingMode != PluginV2StreamingMode.NON_STREAM &&
            preparedReply.attachments.isEmpty() &&
            preparedReply.text.isNotBlank()
        val messageId = dependencies.appendMessage(
            sessionId = sessionId,
            role = "assistant",
            content = if (shouldPseudoStream) "" else preparedReply.text,
            attachments = if (shouldPseudoStream) emptyList() else preparedReply.attachments,
        )
        if (shouldPseudoStream) {
            emitPseudoStreamingResponse(
                sessionId = sessionId,
                messageId = messageId,
                response = preparedReply.text,
            )
        }
        return messageId
    }

    private suspend fun invokeProviderForAppChatPipeline(
        request: PluginProviderRequest,
        mode: PluginV2StreamingMode,
        config: ConfigProfile?,
        availableProviders: List<ProviderProfile>,
    ): PluginV2ProviderInvocationResult {
        val resolvedProvider = availableProviders.firstOrNull { profile ->
            profile.id == request.selectedProviderId &&
                profile.enabled &&
                ProviderCapability.CHAT in profile.capabilities
        } ?: error("Selected provider is unavailable: ${request.selectedProviderId}")

        val messages = request.messages.toConversationMessages(request.requestId)
        val chatTools = request.tools.map { def ->
            ChatCompletionService.ChatToolDefinition(
                name = def.name,
                description = def.description,
                parameters = org.json.JSONObject(def.inputSchema.filterValues { it != null } as Map<*, *>),
            )
        }
        return if (mode != PluginV2StreamingMode.NATIVE_STREAM || !request.streamingEnabled || config == null) {
            val result = withContext(ioDispatcher) {
                dependencies.sendConfiguredChatWithTools(
                    provider = resolvedProvider,
                    messages = messages,
                    systemPrompt = request.systemPrompt,
                    config = config,
                    availableProviders = availableProviders,
                    tools = chatTools,
                )
            }
            PluginV2ProviderInvocationResult.NonStreaming(
                response = PluginLlmResponse(
                    requestId = request.requestId,
                    providerId = resolvedProvider.id,
                    modelId = request.selectedModelId.ifBlank { resolvedProvider.model },
                    text = result.text,
                    toolCalls = result.toolCalls.map { tc ->
                        PluginLlmToolCall(
                            toolName = tc.name,
                            arguments = parseToolCallArguments(tc.arguments),
                        )
                    },
                ),
            )
        } else {
            val chunks = mutableListOf<PluginV2ProviderStreamChunk>()
            val result = withContext(ioDispatcher) {
                dependencies.sendConfiguredChatStreamWithTools(
                    provider = resolvedProvider,
                    messages = messages,
                    systemPrompt = request.systemPrompt,
                    config = config,
                    availableProviders = availableProviders,
                    tools = chatTools,
                    onDelta = { delta ->
                        if (delta.isNotBlank()) {
                            chunks += PluginV2ProviderStreamChunk(deltaText = delta)
                        }
                    },
                    onToolCallDelta = { index, name, args ->
                        if (name.isNotBlank() || args.isNotBlank()) {
                            chunks += PluginV2ProviderStreamChunk(
                                toolCallDeltas = listOf(
                                    PluginLlmToolCallDelta(
                                        index = index,
                                        toolName = name.ifBlank { "_pending" },
                                        arguments = if (args.isNotBlank()) {
                                            parseToolCallArguments(args)
                                        } else {
                                            emptyMap()
                                        },
                                    ),
                                ),
                            )
                        }
                    },
                )
            }
            val finishReason = if (result.toolCalls.isNotEmpty()) "tool_calls" else "stop"
            chunks += PluginV2ProviderStreamChunk(
                deltaText = "",
                isCompletion = true,
                finishReason = finishReason,
            )
            if (result.text.isNotBlank() && chunks.size == 1) {
                chunks.add(
                    0,
                    PluginV2ProviderStreamChunk(deltaText = result.text),
                )
            }
            PluginV2ProviderInvocationResult.Streaming(
                events = chunks.toList(),
            )
        }
    }

    private fun parseToolCallArguments(json: String): Map<String, Any?> {
        return try {
            val obj = org.json.JSONObject(json)
            obj.keys().asSequence().associateWith { key -> obj.opt(key) }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private suspend fun buildAppChatVoiceReplyAttachments(
        response: String,
        provider: ProviderProfile,
        voiceId: String,
        voiceStreamingEnabled: Boolean,
        readBracketedContent: Boolean,
    ): List<ConversationAttachment> {
        if (!voiceStreamingEnabled) {
            return synthesizeSingleVoiceReply(
                provider = provider,
                text = response,
                voiceId = voiceId,
                readBracketedContent = readBracketedContent,
            )?.let(::listOf) ?: emptyList()
        }

        val segments = StreamingResponseSegmenter.splitForVoiceStreaming(response)
        if (segments.size <= 1) {
            return synthesizeSingleVoiceReply(
                provider = provider,
                text = response,
                voiceId = voiceId,
                readBracketedContent = readBracketedContent,
            )?.let(::listOf) ?: emptyList()
        }

        val streamedAttachments = mutableListOf<ConversationAttachment>()
        for (segment in segments) {
            val attachment = synthesizeSingleVoiceReply(
                provider = provider,
                text = segment,
                voiceId = voiceId,
                readBracketedContent = readBracketedContent,
            ) ?: return synthesizeSingleVoiceReply(
                provider = provider,
                text = response,
                voiceId = voiceId,
                readBracketedContent = readBracketedContent,
            )?.let(::listOf) ?: emptyList()
            streamedAttachments += attachment
        }
        return streamedAttachments.toList()
    }


    fun sessionMessages(sessionId: String = _uiState.value.selectedSessionId): List<ConversationMessage> {
        return dependencies.session(sessionId).messages
    }

    fun currentSession(): ConversationSession? {
        return sessions.value.firstOrNull { it.id == _uiState.value.selectedSessionId }
            ?: sessions.value.firstOrNull()
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
        val enabledProviders = providers.value.filter { it.enabled && ProviderCapability.CHAT in it.capabilities }
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

    private fun syncSessionBindings(sessionId: String, providerId: String) {
        val personaId = resolveSessionPersonaId(sessionId)
        dependencies.updateSessionBindings(
            sessionId = sessionId,
            providerId = providerId,
            personaId = personaId,
            botId = selectedBot()?.id ?: dependencies.selectedBotId.value,
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
            return handlePluginCommand(
                session = session,
                bot = bot,
                content = content,
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

    private fun handlePluginCommand(
        session: ConversationSession,
        bot: BotProfile,
        content: String,
    ): Boolean {
        if (!content.startsWith("/") || !ExternalPluginTriggerPolicy.isOpen(PluginTriggerSource.OnCommand)) {
            return false
        }
        val personaId = resolveSessionPersonaId(session.id)
        val config = dependencies.resolveConfig(bot.configProfileId)
        val syntheticMessage = ConversationMessage(
            id = "plugin-command:${session.id}:${content.hashCode()}",
            role = "user",
            content = content,
            timestamp = System.currentTimeMillis(),
        )
        val v2DispatchResult = dispatchAppChatV2MessageIngress(
            trigger = PluginTriggerSource.OnCommand,
            session = session,
            message = syntheticMessage,
            provider = selectedProvider(),
            bot = bot,
            personaId = personaId,
            config = config,
        )
        dependencies.log(
            "App chat v2 command dispatch finished: command=${content.substringBefore(' ')} session=${session.id} " +
                "hasResponse=${v2DispatchResult.commandResponse != null} " +
                "terminal=${v2DispatchResult.isTerminal()} " +
                "stopped=${v2DispatchResult.propagationStopped}",
        )
        v2DispatchResult.commandResponse?.let { commandResponse ->
            consumeAppChatV2CommandResponse(
                sessionId = session.id,
                response = commandResponse,
            )
            dependencies.log(
                "App chat v2 command response consumed: command=${content.substringBefore(' ')} " +
                    "plugin=${commandResponse.pluginId} attachments=${commandResponse.attachments.size}",
            )
            return true
        }
        if (v2DispatchResult.isTerminal()) {
            appendV2UserVisibleFailure(session.id, v2DispatchResult)
            return true
        }
        val batch = runCatching {
            appChatPluginRuntime.execute(PluginTriggerSource.OnCommand) { plugin ->
                buildAppChatPluginContext(
                    plugin = plugin,
                    trigger = PluginTriggerSource.OnCommand,
                    session = session,
                    message = syntheticMessage,
                    provider = selectedProvider(),
                    bot = bot,
                    personaId = personaId,
                    config = config,
                )
            }
        }.onFailure { error ->
            error.rethrowIfCancellation()
            dependencies.log(
                "App chat plugin command runtime failed: command=${content.substringBefore(' ')} reason=${error.message ?: error.javaClass.simpleName}",
            )
        }.getOrElse { error ->
            dependencies.appendMessage(
                sessionId = session.id,
                role = "assistant",
                content = pluginCommandRuntimeFailureMessage(
                    reason = error.message ?: error.javaClass.simpleName,
                ),
            )
            return true
        }

        batch.skipped.forEach { skip ->
            dependencies.log(
                "App chat plugin command skipped: plugin=${skip.plugin.pluginId} reason=${skip.reason.name}",
            )
        }
        if (batch.outcomes.isEmpty()) {
            val suspendedPlugin = batch.skipped.firstOrNull { skip ->
                skip.reason == PluginDispatchSkipReason.FailureSuspended
            }
            if (suspendedPlugin != null) {
                dependencies.appendMessage(
                    sessionId = session.id,
                    role = "assistant",
                    content = pluginCommandSuspendedMessage(
                        pluginId = suspendedPlugin.plugin.pluginId,
                    ),
                )
                return true
            }
            return false
        }
        var handled = false
        batch.outcomes.forEach { outcome ->
            val consumption = consumePluginCommandResult(
                pluginId = outcome.pluginId,
                result = outcome.result,
                context = outcome.context,
                extractedDir = outcome.installState.extractedDir,
            )
            if (consumption.handled) {
                handled = true
                dependencies.appendMessage(
                    sessionId = session.id,
                    role = "assistant",
                    content = consumption.replyText,
                    attachments = consumption.attachments,
                )
            }
            dependencies.log(
                "App chat plugin command handled: plugin=${outcome.pluginId} result=${outcome.result::class.simpleName.orEmpty()} handled=${consumption.handled}",
            )
        }
        return handled
    }

    private fun dispatchAppChatPlugins(
        trigger: PluginTriggerSource,
        session: ConversationSession,
        message: ConversationMessage,
        provider: ProviderProfile,
        bot: BotProfile?,
        personaId: String,
        config: ConfigProfile?,
        suppressV2CommandStage: Boolean = false,
    ): Boolean {
        if (trigger.isV2MessageIngressTrigger()) {
            val v2DispatchResult = dispatchAppChatV2MessageIngress(
                trigger = trigger,
                session = session,
                message = message,
                provider = provider,
                bot = bot,
                personaId = personaId,
                config = config,
                suppressCommandStage = suppressV2CommandStage,
            )
            if (v2DispatchResult.isTerminal()) {
                appendV2UserVisibleFailure(session.id, v2DispatchResult)
                return true
            }
        }
        val batch = runCatching {
            appChatPluginRuntime.execute(trigger) { plugin ->
                buildAppChatPluginContext(
                    plugin = plugin,
                    trigger = trigger,
                    session = session,
                    message = message,
                    provider = provider,
                    bot = bot,
                    personaId = personaId,
                    config = config,
                )
            }
        }.onFailure { error ->
            dependencies.log(
                "App chat plugin runtime failed: trigger=${trigger.wireValue} reason=${error.message ?: error.javaClass.simpleName}",
            )
        }.getOrNull() ?: return false

        batch.skipped.forEach { skip ->
            dependencies.log(
                "App chat plugin skipped: trigger=${trigger.wireValue} plugin=${skip.plugin.pluginId} reason=${skip.reason.name}",
            )
        }
        batch.merged.conflicts.forEach { conflict ->
            dependencies.log(
                "App chat plugin merge conflict: trigger=${trigger.wireValue} plugin=${conflict.pluginId} overriddenBy=${conflict.overriddenByPluginId} type=${conflict.resultType}",
            )
        }
        batch.outcomes.forEach { outcome ->
            val resultName = outcome.result::class.simpleName ?: "UnknownResult"
            if (outcome.succeeded) {
                dependencies.log(
                    "App chat plugin executed: trigger=${trigger.wireValue} plugin=${outcome.pluginId} result=$resultName",
                )
            } else {
                val errorResult = outcome.result as? ErrorResult
                dependencies.log(
                    "App chat plugin failed: trigger=${trigger.wireValue} plugin=${outcome.pluginId} code=${errorResult?.code.orEmpty()} message=${errorResult?.message.orEmpty()}",
                )
            }
            val consumption = consumePluginCommandResult(
                pluginId = outcome.pluginId,
                result = outcome.result,
                context = outcome.context,
                extractedDir = outcome.installState.extractedDir,
            )
            if (consumption.handled) {
                dependencies.appendMessage(
                    sessionId = session.id,
                    role = "assistant",
                    content = consumption.replyText,
                    attachments = consumption.attachments,
                )
            }
        }
        return false
    }

    private fun dispatchAppChatV2MessageIngress(
        trigger: PluginTriggerSource,
        session: ConversationSession,
        message: ConversationMessage,
        provider: ProviderProfile?,
        bot: BotProfile?,
        personaId: String,
        config: ConfigProfile?,
        suppressCommandStage: Boolean = false,
    ): PluginV2MessageDispatchResult {
        return runCatching {
            runBlocking {
                PluginV2DispatchEngineProvider.engine().dispatchMessage(
                    event = buildAppChatPluginMessageEvent(
                        trigger = trigger,
                        session = session,
                        message = message,
                        provider = provider,
                        bot = bot,
                        personaId = personaId,
                        config = config,
                        suppressCommandStage = suppressCommandStage,
                    ),
                )
            }
        }.onFailure { error ->
            error.rethrowIfCancellation()
            dependencies.log(
                "App chat v2 message ingress failed: trigger=${trigger.wireValue} reason=${error.message ?: error.javaClass.simpleName}",
            )
        }.getOrDefault(PluginV2MessageDispatchResult())
    }

    private fun Throwable.rethrowIfCancellation() {
        if (this is CancellationException) {
            throw this
        }
    }

    private fun buildAppChatPluginMessageEvent(
        trigger: PluginTriggerSource,
        session: ConversationSession,
        message: ConversationMessage,
        provider: ProviderProfile?,
        bot: BotProfile?,
        personaId: String,
        config: ConfigProfile?,
        suppressCommandStage: Boolean = false,
    ): PluginMessageEvent {
        val rawText = message.content.take(500)
        return PluginMessageEvent(
            eventId = "${trigger.wireValue}:${session.id}:${message.id}",
            platformAdapterType = APP_CHAT_PLATFORM_ADAPTER_TYPE,
            messageType = session.messageType,
            conversationId = session.originSessionId.ifBlank { session.id },
            senderId = when (message.role) {
                "assistant" -> bot?.id.orEmpty()
                else -> "app-user"
            },
            timestampEpochMillis = message.timestamp,
            rawText = rawText,
            initialWorkingText = rawText,
            rawMentions = emptyList(),
            normalizedMentions = emptyList(),
            extras = buildMap {
                put("source", "app_chat")
                put("trigger", trigger.wireValue)
                put("sessionId", session.id)
                put("messageId", message.id)
                put("providerId", provider?.id.orEmpty())
                put("botId", bot?.id ?: session.botId)
                put("personaId", personaId)
                put("streamingEnabled", config?.textStreamingEnabled == true)
                put("ttsEnabled", config?.ttsEnabled == true)
                if (suppressCommandStage) {
                    put(HOST_SKIP_COMMAND_STAGE_EXTRA_KEY, true)
                }
            },
        )
    }

    private fun isUnsupportedPluginCommand(content: String): Boolean {
        val parsedCommand = BotCommandParser.parse(content) ?: return false
        return !BotCommandRouter.supports(parsedCommand.name)
    }

    private fun appendV2UserVisibleFailure(
        sessionId: String,
        result: PluginV2MessageDispatchResult,
    ) {
        result.userVisibleFailureMessage
            ?.takeIf { message -> message.isNotBlank() }
            ?.let { message ->
                dependencies.appendMessage(
                    sessionId = sessionId,
                    role = "assistant",
                    content = message,
                )
            }
    }

    private fun consumeAppChatV2CommandResponse(
        sessionId: String,
        response: PluginV2CommandResponse,
    ) {
        val attachments = response.attachments.mapIndexedNotNull { index, attachment ->
            resolveAppChatV2CommandAttachment(
                response = response,
                attachment = attachment,
            )?.let { resolvedSource ->
                ConversationAttachment(
                    id = "app-chat-v2-command-$index-${resolvedSource.hashCode()}",
                    type = if (attachment.mimeType.startsWith("audio/")) "audio" else "image",
                    mimeType = attachment.mimeType.ifBlank { "image/jpeg" },
                    fileName = attachment.label.ifBlank {
                        resolvedSource.substringAfterLast('/', missingDelimiterValue = "attachment-$index")
                    },
                    remoteUrl = resolvedSource,
                )
            }
        }
        dependencies.appendMessage(
            sessionId = sessionId,
            role = "assistant",
            content = response.text,
            attachments = attachments,
        )
        dependencies.log(
            "App chat v2 command handled: plugin=${response.pluginId} textLength=${response.text.length} attachments=${attachments.size}",
        )
    }

    private fun resolveAppChatV2CommandAttachment(
        response: PluginV2CommandResponse,
        attachment: PluginV2CommandResponseAttachment,
    ): String? {
        val source = attachment.source.trim()
        if (source.isBlank()) {
            return null
        }
        if (source.startsWith("http://") || source.startsWith("https://")) {
            return source
        }
        if (source.startsWith("plugin://package/")) {
            val relativePath = source.removePrefix("plugin://package/").trim()
            if (relativePath.isBlank()) {
                return null
            }
            return File(response.extractedDir, relativePath).absolutePath
        }
        val sourceFile = File(source)
        return when {
            sourceFile.isAbsolute -> sourceFile.absolutePath
            response.extractedDir.isNotBlank() -> File(response.extractedDir, source).absolutePath
            else -> source
        }
    }

    private fun PluginV2MessageDispatchResult.isTerminal(): Boolean {
        return propagationStopped || terminatedByCustomFilterFailure
    }

    private fun PluginTriggerSource.isV2MessageIngressTrigger(): Boolean {
        return this == PluginTriggerSource.BeforeSendMessage ||
            this == PluginTriggerSource.OnCommand
    }

    private fun resolvePluginPrivateRootPath(pluginId: String): String {
        return runCatching {
            PluginStoragePaths.fromFilesDir(
                PluginRepository.requireAppContext().filesDir,
            ).privateDir(pluginId).absolutePath
        }.getOrDefault("")
    }

    private fun buildAppChatPluginContext(
        plugin: PluginRuntimePlugin,
        trigger: PluginTriggerSource,
        session: ConversationSession,
        message: ConversationMessage,
        provider: ProviderProfile?,
        bot: BotProfile?,
        personaId: String,
        config: ConfigProfile?,
    ): PluginExecutionContext {
        val messagePreview = message.content.take(500)
        val base = PluginExecutionContext(
            trigger = trigger,
            pluginId = plugin.pluginId,
            pluginVersion = plugin.pluginVersion,
            sessionRef = MessageSessionRef(
                platformId = session.platformId,
                messageType = session.messageType,
                originSessionId = session.originSessionId,
            ),
            message = PluginMessageSummary(
                messageId = message.id,
                contentPreview = messagePreview,
                senderId = when (message.role) {
                    "assistant" -> bot?.id.orEmpty()
                    else -> "app-user"
                },
                messageType = session.messageType.wireValue,
                attachmentCount = message.attachments.size,
                timestamp = message.timestamp,
            ),
            bot = PluginBotSummary(
                botId = bot?.id ?: session.botId,
                displayName = bot?.displayName.orEmpty(),
                platformId = session.platformId,
            ),
            config = PluginConfigSummary(
                providerId = provider?.id.orEmpty(),
                modelId = provider?.model.orEmpty(),
                personaId = personaId,
                extras = buildMap {
                    put("sessionId", session.id)
                    put("streamingEnabled", (config?.textStreamingEnabled == true).toString())
                    put("ttsEnabled", (config?.ttsEnabled == true).toString())
                },
            ),
            permissionSnapshot = plugin.installState.permissionSnapshot.map { permission ->
                PluginPermissionGrant(
                    permissionId = permission.permissionId,
                    title = permission.title,
                    granted = true,
                    required = permission.required,
                    riskLevel = permission.riskLevel,
                )
            },
            hostActionWhitelist = ExternalPluginHostActionPolicy.openActions(),
            triggerMetadata = PluginTriggerMetadata(
                eventId = "${trigger.wireValue}:${session.id}:${message.id}",
                command = message.content.takeIf { trigger == PluginTriggerSource.OnCommand }.orEmpty(),
                extras = mapOf("source" to "app_chat"),
            ),
        )
        return hostCapabilityGateway.injectContext(base)
    }

    private fun consumePluginCommandResult(
        pluginId: String,
        result: PluginExecutionResult,
        context: PluginExecutionContext,
        extractedDir: String,
    ): PluginCommandConsumption {
        return when (result) {
            is TextResult -> PluginCommandConsumption(
                replyText = result.text,
                handled = true,
            )

            is MediaResult -> PluginCommandConsumption(
                attachments = result.items.mapIndexed { index, item ->
                    val resolved = ExternalPluginMediaSourceResolver.resolve(
                        item = item,
                        extractedDir = extractedDir,
                        privateRootPath = resolvePluginPrivateRootPath(pluginId),
                    )
                    ConversationAttachment(
                        id = "plugin-media-$index-${resolved.resolvedSource.hashCode()}",
                        type = if (resolved.mimeType.startsWith("audio/")) "audio" else "image",
                        mimeType = resolved.mimeType.ifBlank { "image/jpeg" },
                        fileName = resolved.altText.ifBlank { resolved.resolvedSource.substringAfterLast('/') },
                        remoteUrl = resolved.resolvedSource,
                    )
                },
                handled = true,
            )

            is NoOp -> PluginCommandConsumption(handled = false)

            is ErrorResult -> PluginCommandConsumption(
                replyText = result.message,
                handled = true,
            )

            is HostActionRequest -> {
                val emittedMessages = mutableListOf<String>()
                val execution = DefaultPluginHostCapabilityGateway(
                    hostActionExecutor = ExternalPluginHostActionExecutor(
                    sendMessageHandler = { text -> emittedMessages += text },
                    sendNotificationHandler = { title, message ->
                        dependencies.log("Plugin notification requested: title=$title message=$message")
                    },
                    openHostPageHandler = { route ->
                        dependencies.log("Plugin requested host page: route=$route")
                    },
                ),
                ).executeHostAction(
                    pluginId = pluginId,
                    request = result,
                    context = context,
                )
                if (execution.succeeded) {
                    PluginCommandConsumption(
                        replyText = emittedMessages.firstOrNull()
                            ?: when (result.action) {
                                PluginHostAction.SendNotification -> "Notification sent: ${execution.message}"
                                PluginHostAction.OpenHostPage -> "Opened host page: ${execution.message}"
                                else -> execution.message
                            },
                        handled = true,
                    )
                } else {
                    PluginCommandConsumption(
                        replyText = execution.message,
                        handled = true,
                    )
                }
            }

            else -> PluginCommandConsumption(
                replyText = "Command trigger does not support ${result::class.simpleName.orEmpty()} yet.",
                handled = true,
            )
        }
    }

    private fun currentLanguageTag(): String {
        return AppCompatDelegate.getApplicationLocales()[0]
            ?.toLanguageTag()
            .orEmpty()
            .ifBlank { "zh" }
    }

    private fun pluginCommandRuntimeFailureMessage(reason: String): String {
        val languageTag = currentLanguageTag()
        return AppStrings.getForLanguageTag(
            languageTag,
            R.string.chat_plugin_command_runtime_failed,
            reason,
        ).ifBlank {
            if (languageTag.startsWith("zh")) {
                "插件命令执行失败：$reason"
            } else {
                "Plugin command failed: $reason"
            }
        }
    }

    private fun pluginCommandSuspendedMessage(pluginId: String): String {
        val languageTag = currentLanguageTag()
        return AppStrings.getForLanguageTag(
            languageTag,
            R.string.chat_plugin_command_suspended,
            pluginId,
        ).ifBlank {
            if (languageTag.startsWith("zh")) {
                "插件 $pluginId 因连续失败已被暂时熔断，请稍后再试。"
            } else {
                "Plugin $pluginId is temporarily suspended after repeated failures. Try again later."
            }
        }
    }

    private suspend fun emitPseudoStreamingResponse(
        sessionId: String,
        messageId: String,
        response: String,
    ) {
        val segments = StreamingResponseSegmenter.split(
            text = response,
            stripTrailingBoundaryPunctuation = true,
        )
        if (segments.isEmpty()) {
            dependencies.updateMessage(sessionId, messageId, content = response)
            return
        }
        val buffer = StringBuilder()
        segments.forEachIndexed { index, segment ->
            buffer.append(segment)
            dependencies.updateMessage(sessionId, messageId, content = buffer.toString())
            if (index < segments.lastIndex) {
                delay(selectedStreamingIntervalMs())
            }
        }
        if (buffer.toString() != response) {
            dependencies.updateMessage(sessionId, messageId, content = response)
        }
    }

    private suspend fun emitNativeStreamingResponse(
        sessionId: String,
        messageId: String,
        provider: ProviderProfile,
        currentSession: ConversationSession,
        systemPrompt: String?,
        config: com.astrbot.android.model.ConfigProfile,
    ): String {
        val visibleBuffer = StringBuilder()
        val pendingBuffer = StringBuilder()
        val response = withContext(ioDispatcher) {
            dependencies.sendConfiguredChatStream(
                provider = provider,
                messages = currentSession.messages.takeLast(currentSession.maxContextMessages),
                systemPrompt = systemPrompt,
                config = config,
                availableProviders = providers.value,
            ) { delta ->
                if (delta.isBlank()) return@sendConfiguredChatStream
                pendingBuffer.append(delta)
                flushStreamingMessageBuffer(
                    sessionId = sessionId,
                    messageId = messageId,
                    visibleBuffer = visibleBuffer,
                    pendingBuffer = pendingBuffer,
                    intervalMs = selectedStreamingIntervalMs(config),
                    force = false,
                )
            }
        }
        flushStreamingMessageBuffer(
            sessionId = sessionId,
            messageId = messageId,
            visibleBuffer = visibleBuffer,
            pendingBuffer = pendingBuffer,
            intervalMs = selectedStreamingIntervalMs(config),
            force = true,
        )
        if (visibleBuffer.toString() != response) {
            dependencies.updateMessage(sessionId, messageId, content = response)
        }
        return response
    }

    private suspend fun flushStreamingMessageBuffer(
        sessionId: String,
        messageId: String,
        visibleBuffer: StringBuilder,
        pendingBuffer: StringBuilder,
        intervalMs: Long,
        force: Boolean,
    ) {
        val drainResult = StreamingResponseSegmenter.drain(
            text = pendingBuffer.toString(),
            forceTail = force,
            stripTrailingBoundaryPunctuation = true,
        )
        if (drainResult.segments.isEmpty()) {
            return
        }

        pendingBuffer.clear()
        pendingBuffer.append(drainResult.remainder)
        drainResult.segments.forEachIndexed { index, segment ->
            visibleBuffer.append(segment)
            dependencies.updateMessage(
                sessionId = sessionId,
                messageId = messageId,
                content = visibleBuffer.toString(),
            )
            if (intervalMs > 0 && (index < drainResult.segments.lastIndex || !force)) {
                delay(intervalMs)
            }
        }
    }

    private suspend fun emitVoiceStreamingAttachments(
        sessionId: String,
        messageId: String,
        response: String,
        provider: ProviderProfile,
        voiceId: String,
        readBracketedContent: Boolean,
    ) {
        val segments = StreamingResponseSegmenter.splitForVoiceStreaming(response)
        if (segments.size <= 1) {
            synthesizeSingleVoiceReply(
                provider = provider,
                text = response,
                voiceId = voiceId,
                readBracketedContent = readBracketedContent,
            )?.let { attachment ->
                dependencies.updateMessage(sessionId, messageId, attachments = listOf(attachment))
            }
            return
        }
        val streamedAttachments = mutableListOf<ConversationAttachment>()
        for (segment in segments) {
            val attachment = synthesizeSingleVoiceReply(
                provider = provider,
                text = segment,
                voiceId = voiceId,
                readBracketedContent = readBracketedContent,
            )
            if (attachment == null) {
                synthesizeSingleVoiceReply(
                    provider = provider,
                    text = response,
                    voiceId = voiceId,
                    readBracketedContent = readBracketedContent,
                )?.let { fallback ->
                    dependencies.updateMessage(sessionId, messageId, attachments = listOf(fallback))
                }
                return
            }
            streamedAttachments += attachment
            dependencies.updateMessage(
                sessionId = sessionId,
                messageId = messageId,
                attachments = streamedAttachments.toList(),
            )
            delay(selectedStreamingIntervalMs())
        }
    }

    private suspend fun synthesizeSingleVoiceReply(
        provider: ProviderProfile,
        text: String,
        voiceId: String,
        readBracketedContent: Boolean,
    ): ConversationAttachment? {
        return withContext(ioDispatcher) {
            runCatching {
                dependencies.synthesizeSpeech(
                    provider = provider,
                    text = text,
                    voiceId = voiceId,
                    readBracketedContent = readBracketedContent,
                )
            }.onSuccess { attachment ->
                dependencies.log(
                    "Chat TTS success: provider=${provider.name} mime=${attachment.mimeType} size=${attachment.base64Data.length}",
                )
            }.onFailure { error ->
                error.rethrowIfCancellation()
                dependencies.log("Chat TTS failed: ${error.message ?: error.javaClass.simpleName}")
            }.getOrNull()
        }
    }

    private fun resolveAppChatStreamingMode(
        config: ConfigProfile?,
        provider: ProviderProfile,
    ): PluginV2StreamingMode {
        return when {
            config?.textStreamingEnabled != true -> PluginV2StreamingMode.NON_STREAM
            provider.hasNativeStreamingSupport() -> PluginV2StreamingMode.NATIVE_STREAM
            else -> PluginV2StreamingMode.PSEUDO_STREAM
        }
    }

    private fun List<ConversationMessage>.toPluginProviderMessages(): List<PluginProviderMessageDto> {
        return map { message ->
            val role = when (message.role.lowercase(Locale.US)) {
                "system" -> PluginProviderMessageRole.SYSTEM
                "assistant" -> PluginProviderMessageRole.ASSISTANT
                else -> PluginProviderMessageRole.USER
            }
            val parts = mutableListOf<PluginProviderMessagePartDto>()
            message.content.takeIf { content -> content.isNotBlank() }?.let { content ->
                parts += PluginProviderMessagePartDto.TextPart(content)
            }
            message.attachments.forEach { attachment ->
                val uri = attachment.remoteUrl.ifBlank {
                    attachment.base64Data.takeIf(String::isNotBlank)?.let { base64 ->
                        "data:${attachment.mimeType};base64,$base64"
                    } ?: "attachment://${attachment.id}"
                }
                parts += PluginProviderMessagePartDto.MediaRefPart(
                    uri = uri,
                    mimeType = attachment.mimeType.ifBlank { "application/octet-stream" },
                )
            }
            if (parts.isEmpty()) {
                parts += PluginProviderMessagePartDto.TextPart("[empty]")
            }
            PluginProviderMessageDto(
                role = role,
                parts = parts,
            )
        }
    }

    private fun List<PluginProviderMessageDto>.toConversationMessages(
        requestId: String,
    ): List<ConversationMessage> {
        return mapIndexed { index, message ->
            val text = message.parts
                .filterIsInstance<PluginProviderMessagePartDto.TextPart>()
                .joinToString(separator = "\n") { part -> part.text }
            val attachments = message.parts
                .filterIsInstance<PluginProviderMessagePartDto.MediaRefPart>()
                .mapIndexed { attachmentIndex, part ->
                    ConversationAttachment(
                        id = "$requestId-$index-$attachmentIndex",
                        type = if (part.mimeType.startsWith("audio/")) "audio" else "image",
                        mimeType = part.mimeType,
                        remoteUrl = part.uri,
                    )
                }
            ConversationMessage(
                id = "$requestId-$index",
                role = message.role.wireValue,
                content = text,
                timestamp = System.currentTimeMillis(),
                attachments = attachments,
            )
        }
    }

    private fun List<PluginMessageEventResult.Attachment>.toConversationAttachments(): List<ConversationAttachment> {
        return mapIndexed { index, attachment ->
            ConversationAttachment(
                id = "llm-result-$index-${attachment.uri.hashCode()}",
                type = if (attachment.mimeType.startsWith("audio/")) "audio" else "image",
                mimeType = attachment.mimeType.ifBlank { "application/octet-stream" },
                remoteUrl = attachment.uri,
            )
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

    private fun buildSystemPrompt(personaPrompt: String?): String? {
        val config = selectedBot()?.configProfileId?.let { dependencies.resolveConfig(it) }
        val promptParts = mutableListOf<String>()
        personaPrompt?.trim()?.takeIf { it.isNotBlank() }?.let(promptParts::add)
        if (config?.realWorldTimeAwarenessEnabled == true) {
            val now = ZonedDateTime.now()
            promptParts += "Current local time: ${now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"))}."
        }
        return promptParts.joinToString("\n\n").ifBlank { null }
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
