package com.astrbot.android.runtime

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.astrbot.android.data.BotRepository
import com.astrbot.android.data.ChatCompletionService
import com.astrbot.android.data.ConfigRepository
import com.astrbot.android.data.ConversationRepository
import com.astrbot.android.data.PersonaRepository
import com.astrbot.android.data.PluginRepository
import com.astrbot.android.data.ProviderRepository
import com.astrbot.android.data.StreamingResponseSegmenter
import com.astrbot.android.data.plugin.PluginStoragePaths
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.PersonaProfile
import com.astrbot.android.model.PersonaToolEnablementSnapshot
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.chat.ConversationSession
import com.astrbot.android.model.chat.MessageSessionRef
import com.astrbot.android.model.chat.MessageType
import com.astrbot.android.model.hasNativeStreamingSupport
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
import com.astrbot.android.model.plugin.PluginHostAction
import com.astrbot.android.model.plugin.PluginMessageSummary
import com.astrbot.android.model.plugin.PluginRuntimeLogLevel
import com.astrbot.android.model.plugin.PluginPermissionGrant
import com.astrbot.android.model.plugin.PluginTriggerMetadata
import com.astrbot.android.model.plugin.PluginTriggerSource
import com.astrbot.android.model.plugin.TextResult
import com.astrbot.android.runtime.plugin.AppChatLlmPipelineRuntime
import com.astrbot.android.runtime.plugin.DefaultAppChatPluginRuntime
import com.astrbot.android.runtime.plugin.DefaultPluginHostCapabilityGateway
import com.astrbot.android.runtime.plugin.ExternalPluginHostActionExecutor
import com.astrbot.android.runtime.plugin.PluginExecutionOutcome
import com.astrbot.android.runtime.plugin.PluginExecutionHostToolHandlers
import com.astrbot.android.runtime.botcommand.BotCommandContext
import com.astrbot.android.runtime.botcommand.BotCommandParser
import com.astrbot.android.runtime.botcommand.BotCommandResources
import com.astrbot.android.runtime.botcommand.BotCommandRouter
import com.astrbot.android.runtime.botcommand.BotCommandSource
import com.astrbot.android.runtime.plugin.PluginLlmResponse
import com.astrbot.android.runtime.plugin.PluginMessageEventResult
import com.astrbot.android.runtime.plugin.PluginProviderMessageDto
import com.astrbot.android.runtime.plugin.PluginProviderMessagePartDto
import com.astrbot.android.runtime.plugin.PluginProviderMessageRole
import com.astrbot.android.runtime.plugin.PluginV2AfterSentView
import com.astrbot.android.runtime.plugin.PluginExecutionEngine
import com.astrbot.android.runtime.plugin.PluginFailureGuard
import com.astrbot.android.runtime.plugin.PluginV2ActiveRuntimeStoreProvider
import com.astrbot.android.runtime.plugin.PluginV2EventResultCoordinator
import com.astrbot.android.runtime.plugin.PluginV2HostLlmDeliveryRequest
import com.astrbot.android.runtime.plugin.PluginV2HostLlmDeliveryResult
import com.astrbot.android.runtime.plugin.PluginV2HostPreparedReply
import com.astrbot.android.runtime.plugin.PluginV2HostSendResult
import com.astrbot.android.runtime.plugin.PluginV2InternalStage
import com.astrbot.android.runtime.plugin.PluginV2LlmAfterSentPayload
import com.astrbot.android.runtime.plugin.PluginV2LlmPipelineInput
import com.astrbot.android.runtime.plugin.PluginV2ProviderInvocationResult
import com.astrbot.android.runtime.plugin.PluginV2ProviderStreamChunk
import com.astrbot.android.runtime.plugin.PluginMessageEvent
import com.astrbot.android.runtime.plugin.PluginV2DispatchEngineProvider
import com.astrbot.android.runtime.plugin.PluginV2MessageDispatchResult
import com.astrbot.android.runtime.plugin.PluginV2RuntimeSession
import com.astrbot.android.runtime.plugin.PluginRuntimeLogBusProvider
import com.astrbot.android.runtime.plugin.PluginRuntimeDispatcher
import com.astrbot.android.runtime.plugin.PluginRuntimeFailureStateStoreProvider
import com.astrbot.android.runtime.plugin.PluginRuntimePlugin
import com.astrbot.android.runtime.plugin.PluginRuntimeRegistry
import com.astrbot.android.runtime.plugin.PluginV2LifecycleManagerProvider
import com.astrbot.android.runtime.plugin.publishLifecycleRecord
import com.astrbot.android.runtime.qq.QqKeywordDetector
import com.astrbot.android.runtime.qq.QqConversationTitleResolver
import com.astrbot.android.runtime.qq.QqReplyFormatter
import com.astrbot.android.runtime.qq.QqReplyPolicyEvaluator
import com.astrbot.android.runtime.qq.QqReplyPolicyInput
import com.astrbot.android.runtime.qq.QqRateLimiter
import com.astrbot.android.runtime.qq.QqSessionKeyFactory
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoWSD
import fi.iki.elonen.NanoWSD.WebSocket
import fi.iki.elonen.NanoWSD.WebSocketFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.LinkedHashMap
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

internal data class OneBotSendResult(
    val success: Boolean,
    val receiptIds: List<String> = emptyList(),
    val errorSummary: String = "",
) {
    companion object {
        fun success(receiptIds: List<String> = emptyList()): OneBotSendResult {
            return OneBotSendResult(
                success = true,
                receiptIds = receiptIds.filter(String::isNotBlank),
            )
        }

        fun failure(errorSummary: String): OneBotSendResult {
            return OneBotSendResult(
                success = false,
                errorSummary = errorSummary,
            )
        }
    }
}

object OneBotBridgeServer {
    private const val PORT = 6199
    private const val HOST_PIPELINE_PLUGIN_ID = "__host__"
    private val hostCapabilityGateway = DefaultPluginHostCapabilityGateway()
    private const val PATH = "/ws"
    private const val AUTH_TOKEN = "astrbot_android_bridge"
    private const val MAX_RECENT_MESSAGE_IDS = 512
    private const val ONE_BOT_PLATFORM_ADAPTER_TYPE = "onebot"
    internal const val KEYWORD_BLOCK_NOTICE = "你的消息或者大模型的响应中包含不适当的内容，已被屏蔽。"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)
    private val rateLimiter = QqRateLimiter()
    private val stashReplayJobs = ConcurrentHashMap<String, AtomicBoolean>()
    private val recentMessageIds = object : LinkedHashMap<String, Unit>(MAX_RECENT_MESSAGE_IDS, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?): Boolean {
            return size > MAX_RECENT_MESSAGE_IDS
        }
    }

    @Volatile
    private var server: OneBotWebSocketServer? = null

    @Volatile
    private var activeSocket: OneBotWebSocket? = null

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var appChatPluginRuntime: AppChatLlmPipelineRuntime = DefaultAppChatPluginRuntime

    @Volatile
    private var replySenderOverrideForTests: ((IncomingMessageEvent, String, List<ConversationAttachment>) -> OneBotSendResult)? =
        null

    internal fun setAppChatPluginRuntimeOverrideForTests(runtime: AppChatLlmPipelineRuntime?) {
        appChatPluginRuntime = runtime ?: DefaultAppChatPluginRuntime
    }

    internal fun setReplySenderOverrideForTests(
        sender: ((IncomingMessageEvent, String, List<ConversationAttachment>) -> OneBotSendResult)?,
    ) {
        replySenderOverrideForTests = sender
    }

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun start() {
        if (started.get()) return

        synchronized(this) {
            if (started.get()) return

            runCatching {
                val nextServer = OneBotWebSocketServer()
                nextServer.start(0, true)
                server = nextServer
                started.set(true)
                RuntimeLogRepository.append("OneBot reverse WS listening on ws://127.0.0.1:$PORT$PATH")
            }.onFailure { error ->
                RuntimeLogRepository.append(
                    "OneBot reverse WS start failed: ${error.message ?: error.javaClass.simpleName}",
                )
            }
        }
    }

    private fun onSocketOpened(socket: OneBotWebSocket, handshake: IHTTPSession) {
        val path = handshake.uri.orEmpty()
        if (path != PATH) {
            RuntimeLogRepository.append("OneBot reverse WS rejected: unexpected path=$path")
            socket.closeSafely(WebSocketFrame.CloseCode.PolicyViolation, "Unexpected path")
            return
        }

        val headers = handshake.headers.orEmpty()
        val authorization = headers["authorization"].orEmpty()
        val token = when {
            authorization.startsWith("Bearer ", ignoreCase = true) ->
                authorization.substringAfter("Bearer ", "").trim()

            else -> authorization.trim()
        }
        if (token.isNotBlank() && token != AUTH_TOKEN) {
            RuntimeLogRepository.append("OneBot reverse WS rejected: invalid authorization header")
            socket.closeSafely(WebSocketFrame.CloseCode.PolicyViolation, "Unauthorized")
            return
        }

        activeSocket = socket
        val selfId = headers["x-self-id"].orEmpty().ifBlank { "unknown" }
        val role = headers["x-client-role"].orEmpty().ifBlank { "unknown" }
        RuntimeLogRepository.append("OneBot reverse WS connected: self=$selfId role=$role")
    }

    private fun onSocketClosed(socket: OneBotWebSocket, reason: String) {
        if (activeSocket === socket) {
            activeSocket = null
        }
        RuntimeLogRepository.append("OneBot reverse WS disconnected: $reason")
    }

    private fun onSocketMessage(payload: String) {
        scope.launch {
            handlePayload(payload)
        }
    }

    private suspend fun handlePayload(payload: String) {
        val json = runCatching { JSONObject(payload) }
            .getOrElse { error ->
                RuntimeLogRepository.append(
                    "OneBot payload parse failed: ${error.message ?: error.javaClass.simpleName}",
                )
                return
            }

        if (json.has("retcode") || json.has("status")) {
            val echo = json.opt("echo")?.toString().orEmpty()
            val status = json.optString("status").ifBlank { "unknown" }
            val retcode = json.opt("retcode")?.toString().orEmpty()
            RuntimeLogRepository.append(
                "OneBot action response: status=$status retcode=${retcode.ifBlank { "-" }} echo=${echo.ifBlank { "-" }}",
            )
            return
        }

        if (json.optString("post_type") != "message") {
            return
        }

        val event = OneBotPayloadCodec.parseIncomingMessageEvent(json) ?: return
        val bot = resolveReplyBot(event) ?: return
        if (!bot.autoReplyEnabled) return
        val config = ConfigRepository.resolve(bot.configProfileId)
        val replyDecision = evaluateReplyPolicy(event, bot, config)
        if (!replyDecision.shouldReply) {
            if (replyDecision.shouldLogInfo) {
                RuntimeLogRepository.append(
                    "QQ reply blocked: reason=${replyDecision.reason} user=${event.userId} group=${event.groupId.ifBlank { "-" }}",
                )
            }
            replyDecision.permissionDeniedNotice?.let { notice ->
                sendReply(event, notice)
            }
            return
        }
        if (config.keywordDetectionEnabled && QqKeywordDetector(config.keywordPatterns).matches(event.text)) {
            RuntimeLogRepository.append("QQ inbound keyword blocked: user=${event.userId} group=${event.groupId.ifBlank { "-" }}")
            sendReply(event, KEYWORD_BLOCK_NOTICE)
            return
        }
        val rateLimitResult = rateLimiter.tryAcquire(
            sourceKey = buildRateLimitSourceKey(bot, event),
            windowSeconds = config.rateLimitWindowSeconds,
            maxCount = config.rateLimitMaxCount,
            strategy = config.rateLimitStrategy,
            payload = event,
        )
        if (!rateLimitResult.allowed) {
            RuntimeLogRepository.append(
                "QQ rate limit blocked: bot=${bot.id} user=${event.userId} group=${event.groupId.ifBlank { "-" }} strategy=${config.rateLimitStrategy}",
            )
            if (rateLimitResult.stashed) {
                scheduleStashReplay(
                    bot = bot,
                    config = config,
                    sourceKey = buildRateLimitSourceKey(bot, event),
                )
            } else if (config.replyWhenPermissionDenied) {
                sendReply(event, "Rate limit exceeded.")
            }
            return
        }
        if (event.messageId.isNotBlank() && !markMessageId(event.messageId)) {
            RuntimeLogRepository.append("OneBot duplicate message ignored: ${event.messageId}")
            return
        }

        processMessageEvent(event, bot, config)
    }

    private suspend fun processMessageEvent(
        event: IncomingMessageEvent,
        bot: BotProfile,
        config: com.astrbot.android.model.ConfigProfile,
    ) {
        if (!bot.autoReplyEnabled) {
            RuntimeLogRepository.append("Auto reply skipped: bot ${bot.id} is disabled")
            return
        }

        val sessionId = buildSessionId(bot, event, config)
        val sessionTitle = buildSessionTitle(event)
        ConversationSessionLockManager.withLock(sessionId) lock@{
            val session = ConversationRepository.session(sessionId)
            if (session.title != sessionTitle || !session.titleCustomized) {
                ConversationRepository.syncSystemSessionTitle(sessionId, sessionTitle)
            }
            val persona = resolvePersona(bot, session.personaId)
            val parsedBotCommand = BotCommandParser.parse(event.text)
            when {
                parsedBotCommand != null && BotCommandRouter.supports(parsedBotCommand.name) -> {
                    if (handleBotCommand(event, bot, config, sessionId, session, persona)) {
                        return@lock
                    }
                }

                parsedBotCommand != null -> {
                    if (handlePluginCommand(event, bot, config, sessionId, session, persona)) {
                        return@lock
                    }
                    sendReply(
                        event,
                        BotCommandResources.unsupportedCommand(parsedBotCommand.name, currentLanguageTag()),
                    )
                    RuntimeLogRepository.append(
                        "Bot command unsupported after plugin fallback: ${parsedBotCommand.name} session=$sessionId",
                    )
                    return@lock
                }

                handlePluginCommand(event, bot, config, sessionId, session, persona) -> {
                    return@lock
                }
            }
            val provider = resolveProvider(bot)
            if (provider == null) {
                RuntimeLogRepository.append("Auto reply skipped: no enabled chat provider configured")
                sendFailureNoticeIfNeeded(event, "No chat model is configured for this bot.")
                return@lock
            }
            ConversationRepository.updateSessionBindings(
                sessionId = sessionId,
                providerId = provider.id,
                personaId = persona?.id.orEmpty(),
                botId = bot.id,
            )
            val ttsSuffixMatched = event.text.trim().endsWith("~")
            val alwaysTtsEnabled = config.alwaysTtsEnabled
            val wantsTts = config.ttsEnabled &&
                session.sessionTtsEnabled &&
                (alwaysTtsEnabled || ttsSuffixMatched)
            val cleanedText = event.text.trim().removeSuffix("~").trim()
            val sttProvider = config.defaultSttProviderId
                .takeIf { config.sttEnabled && session.sessionSttEnabled }
                ?.let(::resolveSttProvider)
            val ttsProvider = config.defaultTtsProviderId
                .takeIf { config.ttsEnabled && session.sessionTtsEnabled }
                ?.let(::resolveTtsProvider)
            when {
                !config.ttsEnabled -> RuntimeLogRepository.append("QQ TTS skipped: config TTS is disabled")
                !session.sessionTtsEnabled -> RuntimeLogRepository.append("QQ TTS skipped: session TTS is disabled")
                ttsProvider == null -> RuntimeLogRepository.append(
                    "QQ TTS skipped: no usable TTS provider configured (selected=${config.defaultTtsProviderId.ifBlank { "-" }})",
                )
                alwaysTtsEnabled -> RuntimeLogRepository.append("QQ TTS trigger matched: provider=${ttsProvider.name} mode=always-on")
                !ttsSuffixMatched -> RuntimeLogRepository.append("QQ TTS skipped: latest input has no ~ suffix")
                else -> RuntimeLogRepository.append("QQ TTS trigger matched: provider=${ttsProvider.name}")
            }
            val transcribedAudioText = if (event.attachments.any { it.type == "audio" } && sttProvider != null) {
                runCatching {
                    event.attachments
                        .filter { it.type == "audio" }
                        .joinToString("\n") { attachment ->
                            ChatCompletionService.transcribeAudio(sttProvider, attachment)
                        }
                }.onFailure { error ->
                    RuntimeLogRepository.append("QQ STT failed: ${error.message ?: error.javaClass.simpleName}")
                }.getOrNull()
            } else {
                null
            }
            val finalPromptContent = buildPromptContent(
                event = event,
                cleanedText = cleanedText,
                transcribedAudioText = transcribedAudioText,
            )

            ConversationRepository.appendMessage(
                sessionId = sessionId,
                role = "user",
                content = finalPromptContent,
                attachments = event.attachments,
            )
            val preModelSession = ConversationRepository.session(sessionId)
            val userMessage = preModelSession.messages.lastOrNull { it.role == "user" } ?: return@lock
            RuntimeLogRepository.append(
                "QQ message received: type=${event.messageType} session=$sessionId chars=${event.text.length} attachments=${event.attachments.size}",
            )
            val llmEvent = event.toPluginMessageEvent(
                trigger = PluginTriggerSource.BeforeSendMessage,
                conversationId = session.pluginConversationId(),
            )
            val ingressDispatchResult = dispatchQqV2MessageIngress(
                trigger = PluginTriggerSource.BeforeSendMessage,
                event = event,
                materializedEvent = llmEvent,
            )
            if (ingressDispatchResult.terminatedByCustomFilterFailure || ingressDispatchResult.propagationStopped) {
                ingressDispatchResult.userVisibleFailureMessage
                    ?.takeIf { message -> message.isNotBlank() }
                    ?.let { message ->
                        sendReply(event, message)
                    }
                return@lock
            }

            val currentSession = ConversationRepository.session(sessionId)
            val contextWindow = persona?.maxContextMessages ?: currentSession.maxContextMessages
            val availableChatProviders = ProviderRepository.providers.value.filter { profile ->
                profile.enabled && ProviderCapability.CHAT in profile.capabilities
            }
            val streamingMode = resolveLlmStreamingMode(
                config = config,
                provider = provider,
            )
            val pipelineInput = PluginV2LlmPipelineInput(
                event = llmEvent,
                messageIds = listOf(userMessage.id),
                streamingMode = streamingMode,
                availableProviderIds = availableChatProviders.map { profile -> profile.id },
                availableModelIdsByProvider = availableChatProviders.associate { profile ->
                    profile.id to listOf(profile.model).filter { modelId -> modelId.isNotBlank() }
                },
                selectedProviderId = provider.id,
                selectedModelId = provider.model,
                systemPrompt = buildSystemPrompt(bot, persona, event),
                messages = currentSession.messages
                    .takeLast(contextWindow)
                    .toPluginProviderMessages(),
                personaToolEnablementSnapshot = persona?.let { activePersona ->
                    PersonaToolEnablementSnapshot(
                        personaId = activePersona.id,
                        enabled = activePersona.enabled,
                        enabledTools = activePersona.enabledTools.toSet(),
                    )
                },
                invokeProvider = { request, mode ->
                    invokeProviderForPipeline(
                        request = request,
                        mode = mode,
                        config = config,
                        availableProviders = availableChatProviders,
                    )
                },
            )
            val deliveryResult = runCatching {
                appChatPluginRuntime.deliverLlmPipeline(
                    PluginV2HostLlmDeliveryRequest(
                        pipelineInput = pipelineInput,
                        conversationId = llmEvent.conversationId,
                        platformAdapterType = ONE_BOT_PLATFORM_ADAPTER_TYPE,
                        platformInstanceKey = event.selfId.ifBlank { "onebot" },
                        hostCapabilityGateway = DefaultPluginHostCapabilityGateway(
                            hostToolHandlers = PluginExecutionHostToolHandlers(
                                sendMessageHandler = { text ->
                                    sendReply(event, text)
                                },
                                sendNotificationHandler = { title, message ->
                                    RuntimeLogRepository.append(
                                        "QQ v2 host notification requested: title=$title message=$message",
                                    )
                                },
                                openHostPageHandler = { route ->
                                    RuntimeLogRepository.append("QQ v2 host page requested: route=$route")
                                },
                            ),
                        ),
                        prepareReply = { pipelineResult ->
                            val sendableResult = pipelineResult.sendableResult
                            val decoratedAttachments = sendableResult.attachments.toConversationAttachments()
                            val assistantAttachments = if (wantsTts && ttsProvider != null) {
                                buildVoiceReplyAttachments(
                                    provider = ttsProvider,
                                    response = sendableResult.text,
                                    voiceId = config.ttsVoiceId,
                                    voiceStreamingEnabled = config.voiceStreamingEnabled,
                                    readBracketedContent = config.ttsReadBracketedContent,
                                )
                            } else {
                                decoratedAttachments
                            }

                            val outboundBlocked = config.keywordDetectionEnabled &&
                                QqKeywordDetector(config.keywordPatterns).matches(sendableResult.text)
                            val outboundText = if (outboundBlocked) {
                                RuntimeLogRepository.append("QQ outbound keyword blocked: session=$sessionId")
                                KEYWORD_BLOCK_NOTICE
                            } else {
                                sendableResult.text
                            }
                            val outboundAttachments = if (outboundBlocked) emptyList() else assistantAttachments
                            PluginV2HostPreparedReply(
                                text = outboundText,
                                attachments = outboundAttachments,
                                deliveredEntries = listOf(
                                    PluginV2AfterSentView.DeliveredEntry(
                                        entryId = pipelineResult.admission.messageIds.firstOrNull().orEmpty().ifBlank { "assistant" },
                                        entryType = "assistant",
                                        textPreview = outboundText.take(160),
                                        attachmentCount = outboundAttachments.size,
                                    ),
                                ),
                            )
                        },
                        sendReply = { preparedReply ->
                            if (streamingMode == PluginV2StreamingMode.PSEUDO_STREAM && preparedReply.attachments.isEmpty()) {
                                sendPseudoStreamingReplyWithOutcome(
                                    event = event,
                                    response = preparedReply.text,
                                    config = config,
                                )
                            } else {
                                sendReplyWithOutcome(
                                    event = event,
                                    text = preparedReply.text,
                                    attachments = preparedReply.attachments,
                                )
                            }.toHostSendResult()
                        },
                        persistDeliveredReply = { preparedReply, _, _ ->
                            ConversationRepository.appendMessage(
                                sessionId = sessionId,
                                role = "assistant",
                                content = preparedReply.text,
                                attachments = preparedReply.attachments,
                            )
                        },
                    ),
                )
            }.getOrElse { error ->
                val details = error.message ?: error.javaClass.simpleName
                RuntimeLogRepository.append("Auto reply failed: $details")
                sendFailureNoticeIfNeeded(event, "Auto reply failed: $details")
                return@lock
            }

            if (deliveryResult is PluginV2HostLlmDeliveryResult.Suppressed) {
                RuntimeLogRepository.append(
                    "QQ llm result suppressed: requestId=${deliveryResult.pipelineResult.admission.requestId} session=$sessionId",
                )
            }
        }
    }

    private fun evaluateReplyPolicy(
        event: IncomingMessageEvent,
        bot: BotProfile,
        config: com.astrbot.android.model.ConfigProfile,
    ) = QqReplyPolicyEvaluator.evaluate(
        QqReplyPolicyInput(
            messageType = event.messageType,
            text = event.text,
            userId = event.userId,
            groupId = event.groupId.ifBlank { null },
            isCommand = isBotCommand(event.text),
            mentionsSelf = event.mentionsSelf,
            mentionsAll = event.mentionsAll,
            isSelfMessage = event.selfId.isNotBlank() && event.selfId == event.userId,
            ignoreSelfMessageEnabled = config.ignoreSelfMessageEnabled,
            ignoreAtAllEventEnabled = config.ignoreAtAllEventEnabled,
            isAdmin = event.userId in config.adminUids,
            whitelistEnabled = config.whitelistEnabled,
            whitelistEntries = config.whitelistEntries,
            logOnWhitelistMiss = config.logOnWhitelistMiss,
            adminGroupBypassWhitelistEnabled = config.adminGroupBypassWhitelistEnabled,
            adminPrivateBypassWhitelistEnabled = config.adminPrivateBypassWhitelistEnabled,
            replyWhenPermissionDenied = config.replyWhenPermissionDenied,
            replyOnAtOnlyEnabled = config.replyOnAtOnlyEnabled,
            wakeWords = (bot.triggerWords + config.wakeWords).distinct(),
            wakeWordsAdminOnlyEnabled = config.wakeWordsAdminOnlyEnabled,
            privateChatRequiresWakeWord = config.privateChatRequiresWakeWord,
            hasExplicitAtTrigger = event.mentionsSelf || event.mentionsAll,
        ),
    )

    private fun buildRateLimitSourceKey(bot: BotProfile, event: IncomingMessageEvent): String {
        return listOf(bot.id, event.messageType.wireValue, event.groupId, event.userId)
            .filter { it.isNotBlank() }
            .joinToString(":")
    }

    private fun scheduleStashReplay(
        bot: BotProfile,
        config: com.astrbot.android.model.ConfigProfile,
        sourceKey: String,
    ) {
        if (config.rateLimitStrategy != "stash" || config.rateLimitWindowSeconds <= 0) return
        val guard = stashReplayJobs.getOrPut(sourceKey) { AtomicBoolean(false) }
        if (!guard.compareAndSet(false, true)) return
        scope.launch {
            try {
                while (true) {
                    delay(config.rateLimitWindowSeconds * 1000L)
                    val replayEvents = rateLimiter.releaseReady(
                        sourceKey = sourceKey,
                        windowSeconds = config.rateLimitWindowSeconds,
                        maxCount = config.rateLimitMaxCount,
                    )
                    if (replayEvents.isEmpty()) {
                        if (rateLimiter.drainReady(sourceKey).isEmpty()) {
                            return@launch
                        }
                        continue
                    }
                    replayEvents.forEach { payload ->
                        val event = payload as? IncomingMessageEvent ?: return@forEach
                        val reboundConfig = ConfigRepository.resolve(bot.configProfileId)
                        val replyDecision = evaluateReplyPolicy(event, bot, reboundConfig)
                        if (!replyDecision.shouldReply) return@forEach
                        if (reboundConfig.keywordDetectionEnabled && QqKeywordDetector(reboundConfig.keywordPatterns).matches(event.text)) {
                            return@forEach
                        }
                        if (event.messageId.isNotBlank() && !markMessageId(event.messageId)) {
                            return@forEach
                        }
                        processMessageEvent(event, bot, reboundConfig)
                    }
                    if (rateLimiter.drainReady(sourceKey).isEmpty()) {
                        return@launch
                    }
                }
            } finally {
                guard.set(false)
                if (rateLimiter.drainReady(sourceKey).isNotEmpty()) {
                    scheduleStashReplay(bot, ConfigRepository.resolve(bot.configProfileId), sourceKey)
                }
            }
        }
    }

    private fun resolveReplyBot(event: IncomingMessageEvent): BotProfile? {
        return BotRepository.resolveBoundBot(event.selfId)
    }

    private fun resolveProvider(bot: BotProfile): ProviderProfile? {
        val providers = ProviderRepository.providers.value.filter {
            it.enabled && ProviderCapability.CHAT in it.capabilities
        }
        val config = ConfigRepository.resolve(bot.configProfileId)
        val preferredIds = listOf(
            config.defaultChatProviderId,
        ).filter { it.isNotBlank() }

        return preferredIds.firstNotNullOfOrNull { preferredId ->
            providers.firstOrNull { it.id == preferredId }
        } ?: providers.firstOrNull()
    }

    private fun resolveSttProvider(providerId: String): ProviderProfile? {
        return ProviderRepository.providers.value.firstOrNull {
            it.id == providerId &&
                it.enabled &&
                ProviderCapability.STT in it.capabilities
        }
    }

    private fun resolveTtsProvider(providerId: String): ProviderProfile? {
        return ProviderRepository.providers.value.firstOrNull {
            it.id == providerId &&
                it.enabled &&
                ProviderCapability.TTS in it.capabilities
        }
    }

    private fun resolvePersona(bot: BotProfile, sessionPersonaId: String? = null): PersonaProfile? {
        val personas = PersonaRepository.personas.value.filter { it.enabled }
        return personas.firstOrNull { it.id == sessionPersonaId && sessionPersonaId.isNullOrBlank().not() }
            ?: personas.firstOrNull { it.id == bot.defaultPersonaId }
            ?: personas.firstOrNull()
    }

    private fun buildSystemPrompt(
        bot: BotProfile,
        persona: PersonaProfile?,
        event: IncomingMessageEvent,
    ): String? {
        val basePrompt = persona?.systemPrompt?.trim().orEmpty()
        val config = ConfigRepository.resolve(bot.configProfileId)
        val channelPrompt = if (event.messageType == MessageType.GroupMessage) {
            "You are replying inside a QQ group chat. Keep the answer concise and natural, and focus on the latest message."
        } else {
            "You are replying inside a QQ private chat. Keep the answer concise and natural."
        }
        val timePrompt = if (config.realWorldTimeAwarenessEnabled) {
            val now = ZonedDateTime.now()
            "Current local time: ${now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"))}."
        } else {
            null
        }
        return listOfNotNull(
            basePrompt.takeIf { it.isNotBlank() },
            channelPrompt,
            timePrompt,
        ).joinToString(separator = "\n\n").ifBlank { null }
    }

    private fun buildSessionId(
        bot: BotProfile,
        event: IncomingMessageEvent,
        config: com.astrbot.android.model.ConfigProfile,
    ): String {
        return QqSessionKeyFactory.build(
            botId = bot.id,
            messageType = event.messageType,
            groupId = event.groupId,
            userId = event.userId,
            isolated = config.sessionIsolationEnabled,
        )
    }

    private fun buildSessionTitle(event: IncomingMessageEvent): String {
        return QqConversationTitleResolver.build(
            messageType = event.messageType,
            groupId = event.groupId,
            userId = event.userId,
            senderName = event.senderName,
        )
    }

    private fun buildPromptContent(
        event: IncomingMessageEvent,
        cleanedText: String,
        transcribedAudioText: String?,
    ): String {
        val textContent = buildString {
            if (cleanedText.isNotBlank()) {
                append(cleanedText)
            }
            transcribedAudioText?.takeIf { it.isNotBlank() }?.let { sttText ->
                if (isNotBlank()) append("\n\n")
                append(sttText)
            }
        }.trim()
        return when (event.messageType) {
            MessageType.GroupMessage -> "${event.senderName.ifBlank { event.userId }}: $textContent".trim()
            else -> textContent
        }
    }

    private fun resolveLlmStreamingMode(
        config: com.astrbot.android.model.ConfigProfile,
        provider: ProviderProfile,
    ): PluginV2StreamingMode {
        return when {
            !config.textStreamingEnabled -> PluginV2StreamingMode.NON_STREAM
            provider.hasNativeStreamingSupport() -> PluginV2StreamingMode.NATIVE_STREAM
            else -> PluginV2StreamingMode.PSEUDO_STREAM
        }
    }

    private suspend fun invokeProviderForPipeline(
        request: com.astrbot.android.runtime.plugin.PluginProviderRequest,
        mode: PluginV2StreamingMode,
        config: com.astrbot.android.model.ConfigProfile,
        availableProviders: List<ProviderProfile>,
    ): PluginV2ProviderInvocationResult {
        val provider = availableProviders.firstOrNull { profile ->
            profile.id == request.selectedProviderId &&
                profile.enabled &&
                ProviderCapability.CHAT in profile.capabilities
        } ?: error("Selected provider is unavailable: ${request.selectedProviderId}")

        val messages = request.messages.toConversationMessages(
            requestId = request.requestId,
        )
        return if (mode != PluginV2StreamingMode.NATIVE_STREAM || !request.streamingEnabled) {
            val text = ChatCompletionService.sendConfiguredChat(
                provider = provider,
                messages = messages,
                systemPrompt = request.systemPrompt,
                config = config,
                availableProviders = availableProviders,
            )
            PluginV2ProviderInvocationResult.NonStreaming(
                response = PluginLlmResponse(
                    requestId = request.requestId,
                    providerId = provider.id,
                    modelId = request.selectedModelId.ifBlank { provider.model },
                    text = text,
                ),
            )
        } else {
            val chunks = mutableListOf<PluginV2ProviderStreamChunk>()
            val aggregatedText = ChatCompletionService.sendConfiguredChatStream(
                provider = provider,
                messages = messages,
                systemPrompt = request.systemPrompt,
                config = config,
                availableProviders = availableProviders,
            ) { delta ->
                if (delta.isNotBlank()) {
                    chunks += PluginV2ProviderStreamChunk(deltaText = delta)
                }
            }
            chunks += PluginV2ProviderStreamChunk(
                deltaText = "",
                isCompletion = true,
                finishReason = "stop",
            )
            if (aggregatedText.isNotBlank() && chunks.size == 1) {
                chunks.add(
                    0,
                    PluginV2ProviderStreamChunk(deltaText = aggregatedText),
                )
            }
            PluginV2ProviderInvocationResult.Streaming(
                events = chunks.toList(),
            )
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

    private fun OneBotSendResult.toHostSendResult(): PluginV2HostSendResult {
        return PluginV2HostSendResult(
            success = success,
            receiptIds = receiptIds,
            errorSummary = errorSummary,
        )
    }

    private fun executeQqPlugins(
        trigger: PluginTriggerSource,
        event: IncomingMessageEvent,
        contextFactory: (PluginRuntimePlugin) -> PluginExecutionContext,
    ): PluginV2MessageDispatchResult? {
        if (trigger.isV2MessageIngressTrigger()) {
            val dispatchResult = dispatchQqV2MessageIngress(trigger, event)
            if (dispatchResult.terminatedByCustomFilterFailure || dispatchResult.propagationStopped) {
                dispatchResult.userVisibleFailureMessage
                    ?.takeIf { message -> message.isNotBlank() }
                    ?.let { message ->
                        sendReply(event, message)
                    }
                return dispatchResult
            }
        }
        executeLegacyQqPlugins(
            trigger = trigger,
            event = event,
            contextFactory = contextFactory,
        )
        return null
    }

    private fun executeLegacyQqPlugins(
        trigger: PluginTriggerSource,
        event: IncomingMessageEvent,
        contextFactory: (PluginRuntimePlugin) -> PluginExecutionContext,
    ) {
        val plugins = PluginRuntimeRegistry.plugins()
        if (plugins.isEmpty()) {
            return
        }
        val pluginFailureGuard = PluginFailureGuard(
            store = PluginRuntimeFailureStateStoreProvider.store(),
        )
        val pluginEngine = PluginExecutionEngine(
            dispatcher = PluginRuntimeDispatcher(pluginFailureGuard),
            failureGuard = pluginFailureGuard,
        )
        val batch = runCatching {
            pluginEngine.executeBatch(
                trigger = trigger,
                plugins = plugins,
                contextFactory = contextFactory,
            )
        }.onFailure { error ->
            RuntimeLogRepository.append(
                "QQ runtime plugin dispatch failed: trigger=${trigger.wireValue} reason=${error.message ?: error.javaClass.simpleName}",
            )
        }.getOrNull() ?: return

        batch.skipped.forEach { skip ->
            RuntimeLogRepository.append(
                "QQ runtime plugin skipped: trigger=${trigger.wireValue} plugin=${skip.plugin.pluginId} reason=${skip.reason.name}",
            )
        }
        batch.merged.conflicts.forEach { conflict ->
            RuntimeLogRepository.append(
                "QQ runtime plugin merge conflict: trigger=${trigger.wireValue} plugin=${conflict.pluginId} overriddenBy=${conflict.overriddenByPluginId} type=${conflict.resultType}",
            )
        }
        batch.outcomes.forEach { outcome ->
            val resultName = outcome.result::class.simpleName ?: "UnknownResult"
            if (outcome.succeeded) {
                RuntimeLogRepository.append(
                    "QQ runtime plugin executed: trigger=${trigger.wireValue} plugin=${outcome.pluginId} result=$resultName",
                )
            } else {
                val errorResult = outcome.result as? ErrorResult
                RuntimeLogRepository.append(
                    "QQ runtime plugin failed: trigger=${trigger.wireValue} plugin=${outcome.pluginId} code=${errorResult?.code.orEmpty()} message=${errorResult?.message.orEmpty()}",
                )
            }
            consumeQqPluginOutcome(event = event, outcome = outcome)
        }
    }

    private fun buildQqPluginContext(
        plugin: PluginRuntimePlugin,
        trigger: PluginTriggerSource,
        session: ConversationSession,
        message: ConversationMessage,
        provider: ProviderProfile?,
        bot: BotProfile,
        persona: PersonaProfile?,
        config: com.astrbot.android.model.ConfigProfile,
        event: IncomingMessageEvent,
    ): PluginExecutionContext {
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
                contentPreview = message.content.take(500),
                senderId = if (message.role == "assistant") bot.id else event.userId,
                messageType = session.messageType.wireValue,
                attachmentCount = message.attachments.size,
                timestamp = message.timestamp,
            ),
            bot = PluginBotSummary(
                botId = bot.id,
                displayName = bot.displayName,
                platformId = session.platformId,
            ),
            config = PluginConfigSummary(
                providerId = provider?.id.orEmpty(),
                modelId = provider?.model.orEmpty(),
                personaId = persona?.id.orEmpty(),
                extras = buildMap {
                    put("sessionId", session.id)
                    put("source", "qq_runtime")
                    put("selfId", event.selfId)
                    put("userId", event.userId)
                    put("groupId", event.groupId)
                    put("streamingEnabled", config.textStreamingEnabled.toString())
                    put("ttsEnabled", config.ttsEnabled.toString())
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
                extras = mapOf(
                    "source" to "qq_runtime",
                    "selfId" to event.selfId,
                    "userId" to event.userId,
                    "groupId" to event.groupId,
                    "messageId" to event.messageId,
                ),
            ),
        )
        return hostCapabilityGateway.injectContext(base)
    }

    private fun sendReply(
        event: IncomingMessageEvent,
        text: String,
        attachments: List<ConversationAttachment> = emptyList(),
    ) {
        sendReplyWithOutcome(
            event = event,
            text = text,
            attachments = attachments,
        )
    }

    private fun sendReplyWithOutcome(
        event: IncomingMessageEvent,
        text: String,
        attachments: List<ConversationAttachment> = emptyList(),
    ): OneBotSendResult {
        replySenderOverrideForTests?.let { sender ->
            return sender(event, text, attachments)
        }

        val socket = activeSocket
        if (socket == null) {
            RuntimeLogRepository.append("OneBot reply skipped: reverse WS is not connected")
            return OneBotSendResult.failure("reverse_ws_not_connected")
        }

        val config = resolveReplyBot(event)?.let { ConfigRepository.resolve(it.configProfileId) }
        val decoration = QqReplyFormatter.buildDecoration(
            messageType = event.messageType,
            messageId = event.messageId,
            senderUserId = event.userId,
            replyTextPrefix = config?.replyTextPrefix.orEmpty(),
            quoteSenderMessageEnabled = config?.quoteSenderMessageEnabled == true,
            mentionSenderEnabled = config?.mentionSenderEnabled == true,
        )
        val messagePayload: Any = OneBotPayloadCodec.buildReplyPayload(
            text = text,
            attachments = attachments,
            decoration = decoration,
            mapAudioAttachment = ::materializeAudioAttachmentForOneBot,
        )
        val params = JSONObject().apply {
            put("message", messagePayload)
            put("auto_escape", false)
            when (event.messageType) {
                MessageType.GroupMessage -> put("group_id", event.groupId)
                else -> put("user_id", event.userId)
            }
        }
        val action = JSONObject().apply {
            put("action", if (event.messageType == MessageType.GroupMessage) "send_group_msg" else "send_private_msg")
            put("params", params)
            put("echo", "astrbot-${System.currentTimeMillis()}")
        }
        RuntimeLogRepository.append("QQ reply payload: ${action.toString().take(1200)}")

        return runCatching {
            socket.send(action.toString())
            RuntimeLogRepository.append(
                "QQ reply sent: type=${event.messageType} target=${event.targetId} chars=${text.length} attachments=${attachments.size}",
            )
            OneBotSendResult.success()
        }.getOrElse { error ->
            val summary = error.message ?: error.javaClass.simpleName
            RuntimeLogRepository.append(
                "QQ reply send failed: $summary",
            )
            OneBotSendResult.failure(summary)
        }
    }

    private fun handlePluginCommand(
        event: IncomingMessageEvent,
        bot: BotProfile,
        config: com.astrbot.android.model.ConfigProfile,
        sessionId: String,
        session: ConversationSession,
        currentPersona: PersonaProfile?,
    ): Boolean {
        val trimmedText = event.text.trim()
        if (!trimmedText.startsWith("/") || !ExternalPluginTriggerPolicy.isOpen(PluginTriggerSource.OnCommand)) {
            return false
        }
        val dispatchResult = dispatchQqV2MessageIngress(
            trigger = PluginTriggerSource.OnCommand,
            event = event,
            conversationId = session.pluginConversationId(),
        )
        if (dispatchResult.terminatedByCustomFilterFailure || dispatchResult.propagationStopped) {
            dispatchResult.userVisibleFailureMessage
                ?.takeIf { message -> message.isNotBlank() }
                ?.let { message ->
                    sendReply(event, message)
                }
            return true
        }
        val syntheticMessage = ConversationMessage(
            id = "qq-plugin-command:${sessionId}:${trimmedText.hashCode()}",
            role = "user",
            content = trimmedText,
            timestamp = System.currentTimeMillis(),
        )
        val batch = runCatching {
            val pluginFailureGuard = PluginFailureGuard(
                store = PluginRuntimeFailureStateStoreProvider.store(),
            )
            PluginExecutionEngine(
                dispatcher = PluginRuntimeDispatcher(pluginFailureGuard),
                failureGuard = pluginFailureGuard,
            ).executeBatch(
                trigger = PluginTriggerSource.OnCommand,
                plugins = PluginRuntimeRegistry.plugins(),
                contextFactory = { plugin ->
                    buildQqPluginContext(
                        plugin = plugin,
                        trigger = PluginTriggerSource.OnCommand,
                        session = session,
                        message = syntheticMessage,
                        provider = resolveProvider(bot),
                        bot = bot,
                        persona = currentPersona,
                        config = config,
                        event = event,
                    )
                },
            )
        }.onFailure { error ->
            RuntimeLogRepository.append(
                "QQ plugin command runtime failed: command=${trimmedText.substringBefore(' ')} reason=${error.message ?: error.javaClass.simpleName}",
            )
        }.getOrNull() ?: return false

        batch.skipped.forEach { skip ->
            RuntimeLogRepository.append(
                "QQ plugin command skipped: plugin=${skip.plugin.pluginId} reason=${skip.reason.name}",
            )
        }
        if (batch.outcomes.isEmpty()) {
            return false
        }
        val consumableOutcomes = batch.outcomes.filter(::isConsumableQqPluginOutcome)
        if (consumableOutcomes.isEmpty()) {
            RuntimeLogRepository.append(
                "QQ plugin command produced no consumable results: command=${trimmedText.substringBefore(' ')} outcomes=${batch.outcomes.joinToString { it.result::class.simpleName.orEmpty() }}",
            )
            return false
        }
        consumableOutcomes.forEach { outcome ->
            consumeQqPluginOutcome(event = event, outcome = outcome)
            RuntimeLogRepository.append(
                "QQ plugin command handled: plugin=${outcome.pluginId} result=${outcome.result::class.simpleName.orEmpty()}",
            )
        }
        return true
    }

    private fun dispatchQqV2MessageIngress(
        trigger: PluginTriggerSource,
        event: IncomingMessageEvent,
        materializedEvent: PluginMessageEvent? = null,
        conversationId: String? = null,
    ): PluginV2MessageDispatchResult {
        return runCatching {
            runBlocking {
                PluginV2DispatchEngineProvider.engine().dispatchMessage(
                    event = materializedEvent ?: event.toPluginMessageEvent(
                        trigger = trigger,
                        conversationId = conversationId,
                    ),
                )
            }
        }.onFailure { error ->
            error.rethrowIfCancellation()
            RuntimeLogRepository.append(
                "QQ v2 message ingress failed: trigger=${trigger.wireValue} reason=${error.message ?: error.javaClass.simpleName}",
            )
        }.getOrDefault(PluginV2MessageDispatchResult())
    }

    private fun Throwable.rethrowIfCancellation() {
        if (this is CancellationException) {
            throw this
        }
    }

    private fun PluginTriggerSource.isV2MessageIngressTrigger(): Boolean {
        return this == PluginTriggerSource.BeforeSendMessage || this == PluginTriggerSource.OnCommand
    }

    private fun IncomingMessageEvent.toPluginMessageEvent(
        trigger: PluginTriggerSource,
        conversationId: String? = null,
    ): PluginMessageEvent {
        val resolvedEventId = messageId.takeIf { value -> value.isNotBlank() }
            ?: "${trigger.wireValue}:${System.currentTimeMillis()}"
        return PluginMessageEvent(
            eventId = resolvedEventId,
            platformAdapterType = ONE_BOT_PLATFORM_ADAPTER_TYPE,
            messageType = messageType,
            conversationId = conversationId ?: fallbackConversationId(),
            senderId = userId,
            timestampEpochMillis = System.currentTimeMillis(),
            rawText = text,
            rawMentions = buildList {
                if (mentionsSelf && selfId.isNotBlank()) {
                    add(selfId)
                }
                if (mentionsAll) {
                    add("all")
                }
            },
            initialWorkingText = text,
            extras = buildMap {
                put("source", "qq_runtime")
                put("trigger", trigger.wireValue)
                put("selfId", selfId)
                put("groupId", groupId)
                put("messageId", messageId)
            },
        )
    }

    private fun IncomingMessageEvent.fallbackConversationId(): String {
        return when (messageType) {
            MessageType.GroupMessage -> "group:$groupId"
            MessageType.FriendMessage,
            MessageType.OtherMessage,
            -> "friend:$userId"
        }
    }

    private fun ConversationSession.pluginConversationId(): String {
        return originSessionId.ifBlank { id }
    }

    private fun isConsumableQqPluginOutcome(outcome: PluginExecutionOutcome): Boolean {
        return when (outcome.result) {
            is TextResult, is ErrorResult, is MediaResult, is HostActionRequest -> true
            else -> false
        }
    }

    private fun consumeQqPluginOutcome(
        event: IncomingMessageEvent,
        outcome: PluginExecutionOutcome,
    ) {
        when (val result = outcome.result) {
            is TextResult -> sendReply(event, result.text)
            is ErrorResult -> sendReply(event, result.message)
            is NoOp -> Unit
            is MediaResult -> {
                val attachments = result.items.mapIndexed { index, item ->
                    val resolved = ExternalPluginMediaSourceResolver.resolve(
                        item = item,
                        extractedDir = outcome.installState.extractedDir,
                        privateRootPath = resolvePluginPrivateRootPath(outcome.pluginId),
                    )
                    ConversationAttachment(
                        id = "qq-plugin-media-$index-${resolved.resolvedSource.hashCode()}",
                        type = if (resolved.mimeType.startsWith("audio/")) "audio" else "image",
                        mimeType = resolved.mimeType.ifBlank { "image/jpeg" },
                        fileName = resolved.altText.ifBlank { resolved.resolvedSource.substringAfterLast('/') },
                        remoteUrl = resolved.resolvedSource,
                    )
                }
                sendReply(event, text = "", attachments = attachments)
            }
            is HostActionRequest -> {
                val emittedMessages = mutableListOf<String>()
                val execution = DefaultPluginHostCapabilityGateway(
                    hostActionExecutor = ExternalPluginHostActionExecutor(
                        sendMessageHandler = { text ->
                            emittedMessages += text
                            sendReply(event, text)
                        },
                        sendNotificationHandler = { title, message ->
                            RuntimeLogRepository.append("QQ plugin notification requested: title=$title message=$message")
                        },
                        openHostPageHandler = { route ->
                            RuntimeLogRepository.append("QQ plugin requested host page: route=$route")
                        },
                    ),
                ).executeHostAction(
                    pluginId = outcome.pluginId,
                    request = result,
                    context = outcome.context,
                )
                if (!execution.succeeded) {
                    sendReply(event, execution.message)
                } else if (emittedMessages.isEmpty() && execution.message.isNotBlank()) {
                    sendReply(event, execution.message)
                }
            }
            else -> {
                RuntimeLogRepository.append(
                    "QQ runtime plugin result is not consumable yet: plugin=${outcome.pluginId} result=${result::class.simpleName.orEmpty()}",
                )
            }
        }
    }

    private fun resolvePluginPrivateRootPath(pluginId: String): String {
        return runCatching {
            PluginStoragePaths.fromFilesDir(
                PluginRepository.requireAppContext().filesDir,
            ).privateDir(pluginId).absolutePath
        }.getOrDefault("")
    }

    private fun materializeAudioAttachmentForOneBot(attachment: ConversationAttachment): String? {
        if (attachment.base64Data.isBlank()) {
            return null
        }
        val context = appContext ?: return null
        return runCatching {
            val outputDir = File(context.filesDir, "runtime/tts-out").apply { mkdirs() }
            val extension = when {
                attachment.fileName.contains('.') -> attachment.fileName.substringAfterLast('.')
                attachment.mimeType.contains("wav") -> "wav"
                else -> "mp3"
            }
            val rawFile = File(outputDir, "tts-${System.currentTimeMillis()}-${attachment.id.take(8)}.$extension")
            rawFile.writeBytes(Base64.getDecoder().decode(attachment.base64Data))
            RuntimeLogRepository.append("QQ TTS attachment materialized: ${rawFile.absolutePath}")
            val silkFile = TencentSilkEncoder.encode(rawFile)
            val napCatBase64 = "base64://${Base64.getEncoder().encodeToString(silkFile.readBytes())}"
            RuntimeLogRepository.append("QQ TTS attachment converted to silk: ${silkFile.absolutePath}")
            RuntimeLogRepository.append("QQ TTS attachment mapped for OneBot: base64://${silkFile.name} bytes=${silkFile.length()}")
            napCatBase64
        }.onFailure { error ->
            RuntimeLogRepository.append("QQ TTS attachment materialize failed: ${error.message ?: error.javaClass.simpleName}")
        }.getOrNull()
    }

    private suspend fun sendPseudoStreamingReply(
        event: IncomingMessageEvent,
        response: String,
        config: com.astrbot.android.model.ConfigProfile,
    ) {
        sendPseudoStreamingReplyWithOutcome(
            event = event,
            response = response,
            config = config,
        )
    }

    private suspend fun sendPseudoStreamingReplyWithOutcome(
        event: IncomingMessageEvent,
        response: String,
        config: com.astrbot.android.model.ConfigProfile,
    ): OneBotSendResult {
        val segments = StreamingResponseSegmenter.split(
            text = response,
            stripTrailingBoundaryPunctuation = true,
        )
        if (segments.isEmpty()) {
            return sendReplyWithOutcome(event, response)
        }
        RuntimeLogRepository.append(
            "QQ pseudo streaming started: target=${event.targetId} segments=${segments.size} chars=${response.length}",
        )
        val receiptIds = mutableListOf<String>()
        segments.forEachIndexed { index, segment ->
            val sendResult = sendReplyWithOutcome(event, segment)
            if (!sendResult.success) {
                return sendResult
            }
            receiptIds += sendResult.receiptIds
            if (index < segments.lastIndex) {
                delay(streamingDelayMs(config))
            }
        }
        return OneBotSendResult.success(receiptIds)
    }

    private suspend fun flushStreamingReplyBuffer(
        event: IncomingMessageEvent,
        outboundBuffer: StringBuilder,
        intervalMs: Long,
        force: Boolean,
    ) {
        val drainResult = StreamingResponseSegmenter.drain(
            text = outboundBuffer.toString(),
            forceTail = force,
            stripTrailingBoundaryPunctuation = true,
        )
        if (drainResult.segments.isEmpty()) {
            return
        }

        outboundBuffer.clear()
        outboundBuffer.append(drainResult.remainder)
        drainResult.segments.forEachIndexed { index, segment ->
            sendReply(event, segment)
            if (intervalMs > 0 && (index < drainResult.segments.lastIndex || !force)) {
                delay(intervalMs)
            }
        }
    }

    private fun buildVoiceReplyAttachments(
        provider: ProviderProfile,
        response: String,
        voiceId: String,
        voiceStreamingEnabled: Boolean,
        readBracketedContent: Boolean,
    ): List<ConversationAttachment> {
        if (!voiceStreamingEnabled) {
            return synthesizeSingleVoiceReply(
                provider = provider,
                response = response,
                voiceId = voiceId,
                readBracketedContent = readBracketedContent,
            )?.let(::listOf).orEmpty()
        }
        val segments = StreamingResponseSegmenter.splitForVoiceStreaming(response)
        if (segments.size <= 1) {
            return synthesizeSingleVoiceReply(
                provider = provider,
                response = response,
                voiceId = voiceId,
                readBracketedContent = readBracketedContent,
            )?.let(::listOf).orEmpty()
        }
        val streamedAttachments = mutableListOf<ConversationAttachment>()
        for (segment in segments) {
            val attachment = synthesizeSingleVoiceReply(
                provider = provider,
                response = segment,
                voiceId = voiceId,
                readBracketedContent = readBracketedContent,
            ) ?: return synthesizeSingleVoiceReply(
                provider = provider,
                response = response,
                voiceId = voiceId,
                readBracketedContent = readBracketedContent,
            )?.let(::listOf).orEmpty()
            streamedAttachments += attachment
        }
        RuntimeLogRepository.append(
            "QQ voice streaming prepared: provider=${provider.name} segments=${streamedAttachments.size}",
        )
        return streamedAttachments
    }

    private fun synthesizeSingleVoiceReply(
        provider: ProviderProfile,
        response: String,
        voiceId: String,
        readBracketedContent: Boolean,
    ): ConversationAttachment? {
        return runCatching {
            ChatCompletionService.synthesizeSpeech(
                provider = provider,
                text = response,
                voiceId = voiceId,
                readBracketedContent = readBracketedContent,
            )
        }.onFailure { error ->
            RuntimeLogRepository.append("QQ TTS failed: ${error.message ?: error.javaClass.simpleName}")
        }.onSuccess { attachment ->
            RuntimeLogRepository.append(
                "QQ TTS success: provider=${provider.name} mime=${attachment.mimeType} size=${attachment.base64Data.length}",
            )
        }.getOrNull()
    }

    private suspend fun sendStreamingVoiceReply(
        event: IncomingMessageEvent,
        attachments: List<ConversationAttachment>,
        config: com.astrbot.android.model.ConfigProfile,
    ) {
        RuntimeLogRepository.append(
            "QQ voice streaming started: target=${event.targetId} segments=${attachments.size}",
        )
        attachments.forEachIndexed { index, attachment ->
            sendReply(event = event, text = "", attachments = listOf(attachment))
            if (index < attachments.lastIndex) {
                delay(streamingDelayMs(config))
            }
        }
    }

    private fun streamingDelayMs(config: com.astrbot.android.model.ConfigProfile): Long {
        return config.streamingMessageIntervalMs.coerceIn(0, 5000).toLong()
    }

    private fun sendFailureNoticeIfNeeded(event: IncomingMessageEvent, message: String) {
        if (event.messageType == MessageType.FriendMessage) {
            sendReply(event, message)
        }
    }

    private fun handleBotCommand(
        event: IncomingMessageEvent,
        bot: BotProfile,
        config: com.astrbot.android.model.ConfigProfile,
        sessionId: String,
        session: ConversationSession,
        currentPersona: PersonaProfile?,
    ): Boolean {
        val trimmedText = event.text.trim()
        val result = BotCommandRouter.handle(
            input = trimmedText,
            context = BotCommandContext(
                source = BotCommandSource.QQ,
                languageTag = currentLanguageTag(),
                sessionId = sessionId,
                session = session,
                sessions = ConversationRepository.sessions.value,
                bot = bot,
                availableBots = BotRepository.botProfiles.value,
                config = config,
                activeProviderId = resolveProvider(bot)?.id ?: session.providerId,
                availableProviders = ProviderRepository.providers.value.filter { it.enabled && ProviderCapability.CHAT in it.capabilities },
                currentPersona = currentPersona,
                availablePersonas = PersonaRepository.personas.value.filter { it.enabled },
                messageType = event.messageType,
                sourceUid = event.userId,
                sourceGroupId = event.groupId,
                selfId = event.selfId,
                deleteSession = { targetSessionId ->
                    ConversationRepository.deleteSession(targetSessionId)
                },
                renameSession = { targetSessionId, title ->
                    ConversationRepository.renameSession(targetSessionId, title)
                },
                updateConfig = { updatedConfig ->
                    ConfigRepository.save(updatedConfig)
                },
                updateBot = { updatedBot ->
                    BotRepository.save(updatedBot)
                },
                updateProvider = { updatedProvider ->
                    ProviderRepository.save(
                        id = updatedProvider.id,
                        name = updatedProvider.name,
                        baseUrl = updatedProvider.baseUrl,
                        model = updatedProvider.model,
                        providerType = updatedProvider.providerType,
                        apiKey = updatedProvider.apiKey,
                        capabilities = updatedProvider.capabilities,
                        enabled = updatedProvider.enabled,
                        multimodalRuleSupport = updatedProvider.multimodalRuleSupport,
                        multimodalProbeSupport = updatedProvider.multimodalProbeSupport,
                        nativeStreamingRuleSupport = updatedProvider.nativeStreamingRuleSupport,
                        nativeStreamingProbeSupport = updatedProvider.nativeStreamingProbeSupport,
                        sttProbeSupport = updatedProvider.sttProbeSupport,
                        ttsProbeSupport = updatedProvider.ttsProbeSupport,
                        ttsVoiceOptions = updatedProvider.ttsVoiceOptions,
                    )
                },
                updateSessionServiceFlags = { sttEnabled, ttsEnabled ->
                    ConversationRepository.updateSessionServiceFlags(
                        sessionId = sessionId,
                        sessionSttEnabled = sttEnabled,
                        sessionTtsEnabled = ttsEnabled,
                    )
                },
                replaceMessages = { messages ->
                    ConversationRepository.replaceMessages(sessionId, messages)
                },
                updateSessionBindings = { providerId, personaId, botId ->
                    ConversationRepository.updateSessionBindings(
                        sessionId = sessionId,
                        providerId = providerId,
                        personaId = personaId,
                        botId = botId,
                    )
                },
            ),
        )
        if (!result.handled) {
            return false
        }
        result.replyText?.let { reply ->
            sendReply(event, reply)
        }
        RuntimeLogRepository.append("Bot command handled via router: ${trimmedText.substringBefore(' ')} session=$sessionId")
        return result.stopModelDispatch
    }

    private fun isBotCommand(text: String): Boolean {
        return BotCommandParser.parse(text) != null
    }

    private fun currentLanguageTag(): String {
        return AppCompatDelegate.getApplicationLocales()[0]
            ?.toLanguageTag()
            .orEmpty()
            .ifBlank { "zh" }
    }

    private fun markMessageId(messageId: String): Boolean {
        synchronized(recentMessageIds) {
            val existed = recentMessageIds.containsKey(messageId)
            recentMessageIds[messageId] = Unit
            return !existed
        }
    }

    private class OneBotWebSocketServer : NanoWSD(PORT) {
        override fun openWebSocket(handshake: IHTTPSession): WebSocket {
            return OneBotWebSocket(handshake)
        }
    }

    private class OneBotWebSocket(
        private val handshakeRequest: IHTTPSession,
    ) : WebSocket(handshakeRequest) {
        override fun onOpen() {
            onSocketOpened(this, handshakeRequest)
        }

        override fun onClose(
            code: WebSocketFrame.CloseCode,
            reason: String,
            initiatedByRemote: Boolean,
        ) {
            val source = if (initiatedByRemote) "remote" else "local"
            onSocketClosed(this, "$reason ($source, $code)")
        }

        override fun onMessage(message: WebSocketFrame) {
            onSocketMessage(message.textPayload)
        }

        override fun onPong(pong: WebSocketFrame) = Unit

        override fun onException(exception: IOException) {
            RuntimeLogRepository.append(
                "OneBot reverse WS exception: ${exception.message ?: exception.javaClass.simpleName}",
            )
        }

        fun closeSafely(code: WebSocketFrame.CloseCode, reason: String) {
            runCatching {
                close(code, reason, false)
            }
        }
    }
}
