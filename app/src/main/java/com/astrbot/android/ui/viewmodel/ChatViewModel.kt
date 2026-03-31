package com.astrbot.android.ui.viewmodel

import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astrbot.android.AppStrings
import com.astrbot.android.R
import com.astrbot.android.data.StreamingResponseSegmenter
import com.astrbot.android.di.ChatViewModelDependencies
import com.astrbot.android.di.DefaultChatViewModelDependencies
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.chat.ConversationSession
import com.astrbot.android.model.hasNativeStreamingSupport
import com.astrbot.android.runtime.botcommand.BotCommandContext
import com.astrbot.android.runtime.botcommand.BotCommandRouter
import com.astrbot.android.runtime.botcommand.BotCommandSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    val selectedSessionId: String = "",
    val streamingEnabled: Boolean = false,
    val isSending: Boolean = false,
    val error: String = "",
)

class ChatViewModel(
    private val dependencies: ChatViewModelDependencies = DefaultChatViewModelDependencies,
) : ViewModel() {
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
                        withContext(Dispatchers.IO) {
                            runCatching {
                                buildList {
                                    for (attachment in audioAttachments) {
                                        add(dependencies.transcribeAudio(sttProvider, attachment))
                                    }
                                }
                                    .joinToString("\n")
                            }.onFailure { error ->
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
                    dependencies.appendMessage(
                        sessionId = sessionId,
                        role = "user",
                        content = finalUserContent,
                        attachments = nonAudioAttachments + audioAttachments,
                    )
                    maybeAutoRenameSession(
                        sessionId,
                        finalUserContent.ifBlank { attachments.firstOrNull()?.fileName ?: "Image" },
                    )

                    val currentSession = dependencies.session(sessionId)
                    val contextWindow = personaSnapshot?.maxContextMessages ?: currentSession.maxContextMessages
                    val scopedSession = currentSession.copy(maxContextMessages = contextWindow)
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
                        val fullResponse = withContext(Dispatchers.IO) {
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
                }
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
        val result = BotCommandRouter.handle(
            input = content,
            context = BotCommandContext(
                source = BotCommandSource.APP_CHAT,
                languageTag = currentLanguageTag(),
                sessionId = sessionId,
                session = session,
                sessions = sessions.value,
                bot = bot,
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
        if (!result.handled) return false
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

    private fun currentLanguageTag(): String {
        return AppCompatDelegate.getApplicationLocales()[0]
            ?.toLanguageTag()
            .orEmpty()
            .ifBlank { "zh" }
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
        val response = withContext(Dispatchers.IO) {
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
        return withContext(Dispatchers.IO) {
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
                dependencies.log("Chat TTS failed: ${error.message ?: error.javaClass.simpleName}")
            }.getOrNull()
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
