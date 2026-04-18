package com.astrbot.android.feature.qq.runtime

import com.astrbot.android.core.runtime.audio.SilkAudioEncoder

import com.astrbot.android.core.runtime.session.ConversationSessionLockManager

import com.astrbot.android.core.common.logging.AppLogger

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.astrbot.android.feature.plugin.runtime.RuntimeLlmOrchestratorPort
import com.astrbot.android.feature.bot.data.FeatureBotRepository
import com.astrbot.android.feature.bot.domain.BotRepositoryPort
import com.astrbot.android.core.runtime.llm.LlmMediaService
import com.astrbot.android.feature.config.data.FeatureConfigRepository
import com.astrbot.android.feature.config.domain.ConfigRepositoryPort
import com.astrbot.android.feature.chat.data.FeatureConversationRepository
import com.astrbot.android.feature.persona.data.FeaturePersonaRepository
import com.astrbot.android.feature.persona.domain.PersonaRepositoryPort
import com.astrbot.android.feature.plugin.data.FeaturePluginRepository
import com.astrbot.android.feature.provider.data.FeatureProviderRepository
import com.astrbot.android.feature.provider.domain.ProviderRepositoryPort
import com.astrbot.android.core.runtime.llm.LlmResponseSegmenter
import com.astrbot.android.feature.plugin.data.PluginStoragePaths
import com.astrbot.android.feature.qq.domain.IncomingQqMessage
import com.astrbot.android.feature.qq.domain.QqConversationPort
import com.astrbot.android.feature.qq.domain.QqPlatformConfigPort
import com.astrbot.android.feature.qq.runtime.OneBotPayloadParser
import com.astrbot.android.feature.qq.runtime.OneBotServerAdapter
import com.astrbot.android.feature.qq.runtime.OneBotServerAdapterResult
import com.astrbot.android.feature.qq.runtime.QqMessageRuntimeService
import com.astrbot.android.feature.qq.runtime.QqReplySender
import com.astrbot.android.feature.qq.runtime.QqRuntimeCompatBridge
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.chat.ConversationToolCall
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
import com.astrbot.android.feature.plugin.runtime.AppChatLlmPipelineRuntime
import com.astrbot.android.feature.plugin.runtime.DefaultAppChatPluginRuntime
import com.astrbot.android.feature.plugin.runtime.DefaultPluginHostCapabilityGateway
import com.astrbot.android.feature.plugin.runtime.PlatformLlmCallbacks
import com.astrbot.android.feature.plugin.runtime.PluginHostCapabilityGateway
import com.astrbot.android.feature.plugin.runtime.PluginProviderRequest
import com.astrbot.android.feature.plugin.runtime.RuntimeOrchestrator
import com.astrbot.android.feature.plugin.runtime.ExternalPluginHostActionExecutor
import com.astrbot.android.feature.plugin.runtime.PluginExecutionOutcome
import com.astrbot.android.feature.plugin.runtime.PluginExecutionHostToolHandlers
import com.astrbot.android.feature.chat.runtime.botcommand.BotCommandContext
import com.astrbot.android.feature.chat.runtime.botcommand.BotCommandParser
import com.astrbot.android.feature.chat.runtime.botcommand.BotCommandResources
import com.astrbot.android.feature.chat.runtime.botcommand.BotCommandRouter
import com.astrbot.android.feature.chat.runtime.botcommand.BotCommandSource
import com.astrbot.android.core.runtime.context.RuntimeContextResolver
import com.astrbot.android.core.runtime.context.RuntimeIngressEvent
import com.astrbot.android.core.runtime.context.RuntimePlatform
import com.astrbot.android.core.runtime.context.ResolvedRuntimeContext
import com.astrbot.android.core.runtime.context.SenderInfo
import com.astrbot.android.core.runtime.context.StreamingModeResolver
import com.astrbot.android.core.runtime.context.SystemPromptBuilder
import com.astrbot.android.feature.plugin.runtime.PluginLlmResponse
import com.astrbot.android.feature.plugin.runtime.PluginLlmToolCall
import com.astrbot.android.feature.plugin.runtime.PluginLlmToolCallDelta
import com.astrbot.android.feature.plugin.runtime.PluginMessageEventResult
import com.astrbot.android.feature.plugin.runtime.PluginProviderMessageDto
import com.astrbot.android.feature.plugin.runtime.PluginProviderMessagePartDto
import com.astrbot.android.feature.plugin.runtime.PluginProviderMessageRole
import com.astrbot.android.feature.plugin.runtime.PluginV2AfterSentView
import com.astrbot.android.feature.plugin.runtime.PluginV2CommandResponse
import com.astrbot.android.feature.plugin.runtime.PluginV2CommandResponseAttachment
import com.astrbot.android.feature.plugin.runtime.PluginExecutionEngine
import com.astrbot.android.feature.plugin.runtime.PluginFailureGuard
import com.astrbot.android.feature.plugin.runtime.PluginV2ActiveRuntimeStoreProvider
import com.astrbot.android.feature.plugin.runtime.PluginV2EventResultCoordinator
import com.astrbot.android.feature.plugin.runtime.PluginV2FollowupSender
import com.astrbot.android.feature.plugin.runtime.PluginV2HostLlmDeliveryRequest
import com.astrbot.android.feature.plugin.runtime.PluginV2HostLlmDeliveryResult
import com.astrbot.android.feature.plugin.runtime.PluginV2HostPreparedReply
import com.astrbot.android.feature.plugin.runtime.PluginV2HostSendResult
import com.astrbot.android.feature.plugin.runtime.PluginV2InternalStage
import com.astrbot.android.feature.plugin.runtime.PluginV2LlmAfterSentPayload
import com.astrbot.android.feature.plugin.runtime.PluginV2LlmPipelineInput
import com.astrbot.android.feature.plugin.runtime.PluginV2LlmPipelineResult
import com.astrbot.android.feature.plugin.runtime.PluginV2ProviderInvocationResult
import com.astrbot.android.feature.plugin.runtime.PluginV2ProviderStreamChunk
import com.astrbot.android.feature.plugin.runtime.PluginMessageEvent
import com.astrbot.android.feature.plugin.runtime.PluginV2DispatchEngineProvider
import com.astrbot.android.feature.plugin.runtime.PluginV2MessageDispatchResult
import com.astrbot.android.feature.plugin.runtime.PluginV2RuntimeSession
import com.astrbot.android.feature.plugin.runtime.PluginRuntimeLogBusProvider
import com.astrbot.android.feature.plugin.runtime.PluginRuntimeDispatcher
import com.astrbot.android.feature.plugin.runtime.PluginRuntimeFailureStateStoreProvider
import com.astrbot.android.feature.plugin.runtime.PluginRuntimePlugin
import com.astrbot.android.feature.plugin.runtime.PluginRuntimeRegistry
import com.astrbot.android.feature.plugin.runtime.PluginV2LifecycleManagerProvider
import com.astrbot.android.feature.plugin.runtime.publishLifecycleRecord
import com.astrbot.android.feature.qq.runtime.QqKeywordDetector
import com.astrbot.android.feature.qq.runtime.QqConversationTitleResolver
import com.astrbot.android.feature.qq.runtime.QqReplyFormatter
import com.astrbot.android.feature.qq.runtime.QqReplyPolicyEvaluator
import com.astrbot.android.feature.qq.runtime.QqReplyPolicyInput
import com.astrbot.android.feature.qq.runtime.QqRateLimiter
import com.astrbot.android.feature.qq.runtime.QqSessionKeyFactory
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

internal data class QqOneBotRuntimeDependencies(
    val botPort: BotRepositoryPort,
    val configPort: ConfigRepositoryPort,
    val personaPort: PersonaRepositoryPort,
    val providerPort: ProviderRepositoryPort,
    val conversationPort: QqConversationPort,
    val platformConfigPort: QqPlatformConfigPort,
    val orchestrator: RuntimeLlmOrchestratorPort,
    val providerInvoker: QqProviderInvoker,
)

object QqOneBotBridgeServer {
    private const val PORT = 6199
    private const val HOST_PIPELINE_PLUGIN_ID = "__host__"
    private val hostCapabilityGateway = DefaultPluginHostCapabilityGateway()
    private const val PATH = "/ws"
    private const val AUTH_TOKEN = "astrbot_android_bridge"
    private const val MAX_RECENT_MESSAGE_IDS = 512
    private const val ONE_BOT_PLATFORM_ADAPTER_TYPE = "onebot"
    internal const val AUTO_REPLY_FAILURE_NOTICE =
        "\u5de5\u5177\u8c03\u7528\u5931\u8d25\uff1a\u672c\u8f6e\u81ea\u52a8\u56de\u590d\u672a\u5b8c\u6210\uff0c\u8bf7\u7a0d\u540e\u518d\u8bd5\u3002"
    internal const val KEYWORD_BLOCK_NOTICE =
        "\u4f60\u7684\u6d88\u606f\u6216\u8005\u5927\u6a21\u578b\u7684\u54cd\u5e94\u4e2d\u5305\u542b\u4e0d\u9002\u5f53\u7684\u5185\u5bb9\uff0c\u5df2\u88ab\u5c4f\u853d\u3002"

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
    private var runtimeDependenciesProvider: (() -> QqOneBotRuntimeDependencies)? = null

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

    internal fun configureRuntimeDependenciesProvider(provider: () -> QqOneBotRuntimeDependencies) {
        runtimeDependenciesProvider = provider
    }

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    internal fun sendScheduledMessage(
        conversationId: String,
        text: String,
        attachments: List<ConversationAttachment> = emptyList(),
        botId: String = "",
    ): OneBotSendResult {
        val socket = activeSocket
        if (socket == null) {
            AppLogger.append("QQ scheduled reply skipped: reverse WS is not connected")
            return OneBotSendResult.failure("reverse_ws_not_connected")
        }

        val normalizedConversationId = conversationId.trim()
        val isGroup = normalizedConversationId.startsWith("group:")
        val targetId = normalizedConversationId.substringAfter(':', "").trim()
        if (targetId.isBlank()) {
            return OneBotSendResult.failure("invalid_conversation_id")
        }

        val bot = botId.takeIf { it.isNotBlank() }?.let { targetBotId ->
            FeatureBotRepository.snapshotProfiles().firstOrNull { it.id == targetBotId }
        }
        val config = bot?.let { FeatureConfigRepository.resolve(it.configProfileId) }
        val decoration = QqReplyFormatter.buildDecoration(
            messageType = if (isGroup) MessageType.GroupMessage else MessageType.FriendMessage,
            messageId = "",
            senderUserId = "",
            replyTextPrefix = config?.replyTextPrefix.orEmpty(),
            quoteSenderMessageEnabled = false,
            mentionSenderEnabled = false,
        )
        val messagePayload: Any = QqOneBotPayloadCodec.buildReplyPayload(
            text = text,
            attachments = attachments,
            decoration = decoration,
            mapAudioAttachment = ::materializeAudioAttachmentForOneBot,
        )
        val params = JSONObject().apply {
            put("message", messagePayload)
            put("auto_escape", false)
            if (isGroup) {
                put("group_id", targetId)
            } else {
                put("user_id", targetId)
            }
        }
        val action = JSONObject().apply {
            put("action", if (isGroup) "send_group_msg" else "send_private_msg")
            put("params", params)
            put("echo", "astrbot-cron-${System.currentTimeMillis()}")
        }

        return runCatching {
            socket.send(action.toString())
            AppLogger.append(
                "QQ scheduled reply sent: conversation=$normalizedConversationId chars=${text.length} attachments=${attachments.size}",
            )
            OneBotSendResult.success()
        }.getOrElse { error ->
            val summary = error.message ?: error.javaClass.simpleName
            AppLogger.append("QQ scheduled reply send failed: $summary")
            OneBotSendResult.failure(summary)
        }
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
                AppLogger.append("OneBot reverse WS listening on ws://127.0.0.1:$PORT$PATH")
            }.onFailure { error ->
                AppLogger.append(
                    "OneBot reverse WS start failed: ${error.message ?: error.javaClass.simpleName}",
                )
            }
        }
    }

    private fun onSocketOpened(socket: OneBotWebSocket, handshake: IHTTPSession) {
        val path = handshake.uri.orEmpty()
        if (path != PATH) {
            AppLogger.append("OneBot reverse WS rejected: unexpected path=$path")
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
            AppLogger.append("OneBot reverse WS rejected: invalid authorization header")
            socket.closeSafely(WebSocketFrame.CloseCode.PolicyViolation, "Unauthorized")
            return
        }

        activeSocket = socket
        val selfId = headers["x-self-id"].orEmpty().ifBlank { "unknown" }
        val role = headers["x-client-role"].orEmpty().ifBlank { "unknown" }
        AppLogger.append("OneBot reverse WS connected: self=$selfId role=$role")
    }

    private fun onSocketClosed(socket: OneBotWebSocket, reason: String) {
        if (activeSocket === socket) {
            activeSocket = null
        }
        AppLogger.append("OneBot reverse WS disconnected: $reason")
    }

    private fun onSocketMessage(payload: String) {
        scope.launch {
            handlePayload(payload)
        }
    }

    private fun buildServerAdapter(): OneBotServerAdapter {
        return OneBotServerAdapter(
            parser = OneBotPayloadParser(),
            runtime = buildQqRuntimeService(),
            log = AppLogger::append,
        )
    }

    private fun buildQqRuntimeService(): QqMessageRuntimeService {
        val dependencies = runtimeDependenciesProvider?.invoke()
            ?: error("QqOneBotBridgeServer requires runtime dependencies from the app composition root.")
        return QqMessageRuntimeService(
            botPort = dependencies.botPort,
            configPort = dependencies.configPort,
            personaPort = dependencies.personaPort,
            providerPort = dependencies.providerPort,
            conversationPort = dependencies.conversationPort,
            platformConfigPort = dependencies.platformConfigPort,
            orchestrator = dependencies.orchestrator,
            replySender = buildReplySender(),
            llmRuntime = appChatPluginRuntime,
            providerInvoker = dependencies.providerInvoker,
            rateLimiter = rateLimiter,
            markMessageId = ::markMessageId,
            scheduleStashReplay = ::scheduleStashReplay,
            compatBridge = object : QqRuntimeCompatBridge {
                override fun currentLanguageTag(): String = this@QqOneBotBridgeServer.currentLanguageTag()

                override fun transcribeAudio(
                    provider: ProviderProfile,
                    attachment: ConversationAttachment,
                ): String {
                    return LlmMediaService.transcribeAudio(provider, attachment)
                }

                override fun buildVoiceReplyAttachments(
                    provider: ProviderProfile,
                    response: String,
                    config: com.astrbot.android.model.ConfigProfile,
                ): List<ConversationAttachment> {
                    return this@QqOneBotBridgeServer.buildVoiceReplyAttachments(
                        provider = provider,
                        response = response,
                        voiceId = config.ttsVoiceId,
                        voiceStreamingEnabled = config.voiceStreamingEnabled,
                        readBracketedContent = config.ttsReadBracketedContent,
                    )
                }

                override suspend fun sendPreparedReply(
                    message: IncomingQqMessage,
                    prepared: PluginV2HostPreparedReply,
                    config: com.astrbot.android.model.ConfigProfile,
                    streamingMode: PluginV2StreamingMode,
                ): PluginV2HostSendResult {
                    val event = message.toLegacyIncomingMessageEvent()
                    val sendResult = if (
                        prepared.attachments.size > 1 &&
                        prepared.attachments.all { attachment -> attachment.type == "audio" }
                    ) {
                        sendStreamingVoiceReplyWithOutcome(
                            event = event,
                            attachments = prepared.attachments,
                            config = config,
                        )
                    } else if (
                        streamingMode != PluginV2StreamingMode.NON_STREAM &&
                        prepared.attachments.isEmpty()
                    ) {
                        sendPseudoStreamingReplyWithOutcome(
                            event = event,
                            response = prepared.text,
                            config = config,
                        )
                    } else {
                        sendReplyWithOutcome(
                            event = event,
                            text = prepared.text,
                            attachments = prepared.attachments,
                        )
                    }
                    return sendResult.toHostSendResult()
                }

                override fun resolvePluginPrivateRootPath(pluginId: String): String {
                    return this@QqOneBotBridgeServer.resolvePluginPrivateRootPath(pluginId)
                }
            },
            log = AppLogger::append,
        )
    }

    private fun buildReplySender(): QqReplySender {
        return QqReplySender(
            socketSender = { payload: String ->
                val socket = activeSocket ?: error("reverse_ws_not_connected")
                socket.send(payload)
            },
            resolveReplyConfig = ::resolveReplyConfig,
            mapAudioAttachment = ::materializeAudioAttachmentForOneBot,
            sendOverride = replySenderOverrideForTests?.let { override ->
                { payload, originalMessage ->
                    override(
                        originalMessage.toLegacyIncomingMessageEvent(),
                        payload.text,
                        payload.attachments,
                    ).toHostSendResult()
                }
            },
            log = AppLogger::append,
        )
    }

    private fun resolveReplyConfig(selfId: String): QqReplySender.ReplyConfig? {
        val bot = FeatureBotRepository.resolveBoundBot(selfId) ?: return null
        val config = FeatureConfigRepository.resolve(bot.configProfileId)
        return QqReplySender.ReplyConfig(
            replyTextPrefix = config.replyTextPrefix,
            quoteSenderMessageEnabled = config.quoteSenderMessageEnabled,
            mentionSenderEnabled = config.mentionSenderEnabled,
        )
    }

    private fun IncomingQqMessage.toLegacyIncomingMessageEvent(): IncomingMessageEvent {
        return IncomingMessageEvent(
            selfId = selfId,
            userId = senderId,
            groupId = if (messageType == MessageType.GroupMessage) conversationId else "",
            messageId = messageId,
            messageType = messageType,
            text = text,
            promptContent = when (messageType) {
                MessageType.GroupMessage -> "${senderName.ifBlank { senderId }}: $text".trim()
                else -> text
            },
            mentionsSelf = mentionsSelf,
            mentionsAll = mentionsAll,
            attachments = attachments,
            senderName = senderName,
        )
    }

    private suspend fun handlePayload(payload: String) {
        val json = runCatching { JSONObject(payload) }
            .getOrElse { error ->
                AppLogger.append(
                    "OneBot payload parse failed: ${error.message ?: error.javaClass.simpleName}",
                )
                return
            }

        if (json.has("retcode") || json.has("status")) {
            val echo = json.opt("echo")?.toString().orEmpty()
            val status = json.optString("status").ifBlank { "unknown" }
            val retcode = json.opt("retcode")?.toString().orEmpty()
            AppLogger.append(
                "OneBot action response: status=$status retcode=${retcode.ifBlank { "-" }} echo=${echo.ifBlank { "-" }}",
                )
                return
            }

        when (val result = buildServerAdapter().handlePayload(payload)) {
            is OneBotServerAdapterResult.Handled -> Unit
            is OneBotServerAdapterResult.Ignored -> AppLogger.append("OneBot payload ignored: ${result.reason}")
            is OneBotServerAdapterResult.Invalid -> AppLogger.append("OneBot payload invalid: ${result.reason}")
        }
    }

    private suspend fun legacyProcessMessageEventDoNotUse(
        event: IncomingMessageEvent,
        bot: BotProfile,
        config: com.astrbot.android.model.ConfigProfile,
    ) {
        if (!bot.autoReplyEnabled) {
            AppLogger.append("Auto reply skipped: bot ${bot.id} is disabled")
            return
        }

        val sessionId = buildSessionId(bot, event, config)
        val sessionTitle = buildSessionTitle(event)
        ConversationSessionLockManager.withLock(sessionId) lock@{
        val session = FeatureConversationRepository.session(sessionId)
            if (session.title != sessionTitle || !session.titleCustomized) {
        FeatureConversationRepository.syncSystemSessionTitle(sessionId, sessionTitle)
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
                    AppLogger.append(
                        "Bot command unsupported after plugin fallback: ${parsedBotCommand.name} session=$sessionId",
                    )
                    return@lock
                }

                handlePluginCommand(event, bot, config, sessionId, session, persona) -> {
                    return@lock
                }
            }
            val provider = resolveProvider(bot, session.providerId)
            if (provider == null) {
                AppLogger.append("Auto reply skipped: no enabled chat provider configured")
                sendFailureNoticeIfNeeded(event, "No chat model is configured for this bot.")
                return@lock
            }
        FeatureConversationRepository.updateSessionBindings(
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
                !config.ttsEnabled -> AppLogger.append("QQ TTS skipped: config TTS is disabled")
                !session.sessionTtsEnabled -> AppLogger.append("QQ TTS skipped: session TTS is disabled")
                ttsProvider == null -> AppLogger.append(
                    "QQ TTS skipped: no usable TTS provider configured (selected=${config.defaultTtsProviderId.ifBlank { "-" }})",
                )
                alwaysTtsEnabled -> AppLogger.append("QQ TTS trigger matched: provider=${ttsProvider.name} mode=always-on")
                !ttsSuffixMatched -> AppLogger.append("QQ TTS skipped: latest input has no ~ suffix")
                else -> AppLogger.append("QQ TTS trigger matched: provider=${ttsProvider.name}")
            }
            val transcribedAudioText = if (event.attachments.any { it.type == "audio" } && sttProvider != null) {
                runCatching {
                    event.attachments
                        .filter { it.type == "audio" }
                        .joinToString("\n") { attachment ->
                            LlmMediaService.transcribeAudio(sttProvider, attachment)
                        }
                }.onFailure { error ->
                    AppLogger.append("QQ STT failed: ${error.message ?: error.javaClass.simpleName}")
                }.getOrNull()
            } else {
                null
            }
            val finalPromptContent = buildPromptContent(
                event = event,
                cleanedText = cleanedText,
                transcribedAudioText = transcribedAudioText,
            )

        FeatureConversationRepository.appendMessage(
                sessionId = sessionId,
                role = "user",
                content = finalPromptContent,
                attachments = event.attachments,
            )
        val preModelSession = FeatureConversationRepository.session(sessionId)
            val userMessage = preModelSession.messages.lastOrNull { it.role == "user" } ?: return@lock
            AppLogger.append(
                "QQ message received: type=${event.messageType} session=$sessionId chars=${event.text.length} attachments=${event.attachments.size}",
            )
            val llmEvent = event.toPluginMessageEvent(
                trigger = PluginTriggerSource.BeforeSendMessage,
                conversationId = session.pluginConversationId(),
                botId = bot.id,
                configProfileId = config.id,
                personaId = persona?.id.orEmpty(),
                providerId = provider.id,
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
            if (event.text.isBlank() && llmEvent.workingText.isBlank()) {
                llmEvent.workingText = buildLlmInputSnapshotFallback(
                    finalPromptContent = finalPromptContent,
                    attachments = event.attachments,
                )
            }
            if (shouldExecuteLegacyQqPluginsDuringLlmDispatch()) {
                executeLegacyQqPlugins(
                    trigger = PluginTriggerSource.BeforeSendMessage,
                    event = event,
                    contextFactory = { plugin ->
                        buildQqPluginContext(
                            plugin = plugin,
                            trigger = PluginTriggerSource.BeforeSendMessage,
            session = FeatureConversationRepository.session(sessionId),
                            message = userMessage,
                            provider = provider,
                            bot = bot,
                            persona = persona,
                            config = config,
                            event = event,
                        )
                    },
                )
            }

            val runtimeContext = RuntimeContextResolver.resolve(
                event = RuntimeIngressEvent(
                    platform = RuntimePlatform.QQ_ONEBOT,
                    conversationId = session.pluginConversationId(),
                    repositorySessionId = sessionId,
                    messageId = userMessage.id,
                    sender = SenderInfo(
                        userId = event.userId,
                        nickname = event.senderName,
                        groupId = event.groupId,
                    ),
                    messageType = event.messageType,
                    text = event.text,
                ),
                bot = bot,
                overrideProviderId = provider.id,
                overridePersonaId = persona?.id,
            )
            val streamingMode = StreamingModeResolver.resolve(runtimeContext)

            val qqCallbacks = object : PlatformLlmCallbacks {
                override val platformInstanceKey: String = event.selfId.ifBlank { "onebot" }
                override val hostCapabilityGateway: PluginHostCapabilityGateway =
                    DefaultPluginHostCapabilityGateway(
                        hostToolHandlers = PluginExecutionHostToolHandlers(
                            sendMessageHandler = { text ->
                                sendReply(event, text)
                            },
                            sendNotificationHandler = { title, message ->
                                AppLogger.append(
                                    "QQ v2 host notification requested: title=$title message=$message",
                                )
                            },
                            openHostPageHandler = { route ->
                                AppLogger.append("QQ v2 host page requested: route=$route")
                            },
                        ),
                    )
                override val followupSender: PluginV2FollowupSender = PluginV2FollowupSender { text, attachments ->
                    sendReplyWithOutcome(
                        event = event,
                        text = text,
                        attachments = attachments,
                    ).toHostSendResult()
                }

                override suspend fun prepareReply(
                    result: PluginV2LlmPipelineResult,
                ): PluginV2HostPreparedReply {
                    val sendableResult = result.sendableResult
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
                        AppLogger.append("QQ outbound keyword blocked: session=$sessionId")
                        KEYWORD_BLOCK_NOTICE
                    } else {
                        sendableResult.text
                    }
                    val outboundAttachments = if (outboundBlocked) emptyList() else assistantAttachments
                    AppLogger.append(
                        buildQqPreparedReplyLog(
                            requestId = result.admission.requestId,
                            text = outboundText,
                            attachmentCount = outboundAttachments.size,
                            hookTrace = result.hookInvocationTrace,
                            decoratingHandlers = result.decoratingRunResult.appliedHandlerIds,
                        ),
                    )
                    return PluginV2HostPreparedReply(
                        text = outboundText,
                        attachments = outboundAttachments,
                        deliveredEntries = listOf(
                            PluginV2AfterSentView.DeliveredEntry(
                                entryId = result.admission.messageIds.firstOrNull().orEmpty().ifBlank { "assistant" },
                                entryType = "assistant",
                                textPreview = outboundText.take(160),
                                attachmentCount = outboundAttachments.size,
                            ),
                        ),
                    )
                }

                override suspend fun sendReply(
                    prepared: PluginV2HostPreparedReply,
                ): PluginV2HostSendResult {
                    return (
                        if (
                            prepared.attachments.size > 1 &&
                            prepared.attachments.all { attachment -> attachment.type == "audio" }
                        ) {
                            sendStreamingVoiceReplyWithOutcome(
                                event = event,
                                attachments = prepared.attachments,
                                config = config,
                            )
                        } else if (
                            streamingMode != PluginV2StreamingMode.NON_STREAM &&
                            prepared.attachments.isEmpty()
                        ) {
                            sendPseudoStreamingReplyWithOutcome(
                                event = event,
                                response = prepared.text,
                                config = config,
                            )
                        } else {
                            sendReplyWithOutcome(
                                event = event,
                                text = prepared.text,
                                attachments = prepared.attachments,
                            )
                        }
                    ).toHostSendResult()
                }

                override suspend fun persistDeliveredReply(
                    prepared: PluginV2HostPreparedReply,
                    sendResult: PluginV2HostSendResult,
                    pipelineResult: PluginV2LlmPipelineResult,
                ) {
        FeatureConversationRepository.appendMessage(
                        sessionId = sessionId,
                        role = "assistant",
                        content = prepared.text,
                        attachments = prepared.attachments,
                    )
                }

                override suspend fun invokeProvider(
                    request: PluginProviderRequest,
                    mode: PluginV2StreamingMode,
                    ctx: ResolvedRuntimeContext,
                    ): PluginV2ProviderInvocationResult {
                        return legacyProviderPipelineInvokerDoNotUse(
                            request = request,
                            mode = mode,
                            config = config,
                            availableProviders = ctx.availableProviders,
                    )
                }
            }

            val deliveryResult = runCatching {
                RuntimeOrchestrator.dispatchLlm(
                    ctx = runtimeContext,
                    llmRuntime = appChatPluginRuntime,
                    callbacks = qqCallbacks,
                    userMessage = userMessage,
                    preBuiltPluginEvent = llmEvent,
                )
            }.getOrElse { error ->
                val details = error.message ?: error.javaClass.simpleName
                AppLogger.append("Auto reply failed: $details")
        FeatureConversationRepository.appendMessage(
                    sessionId = sessionId,
                    role = "assistant",
                    content = AUTO_REPLY_FAILURE_NOTICE,
                )
                sendFailureNoticeIfNeeded(event, AUTO_REPLY_FAILURE_NOTICE)
                return@lock
            }

            if (deliveryResult is PluginV2HostLlmDeliveryResult.Suppressed) {
                AppLogger.append(
                    "QQ llm result suppressed: requestId=${deliveryResult.pipelineResult.admission.requestId} session=$sessionId",
                )
            } else if (
                deliveryResult is PluginV2HostLlmDeliveryResult.Sent &&
                shouldExecuteLegacyQqPluginsDuringLlmDispatch()
            ) {
            FeatureConversationRepository.session(sessionId)
                    .messages
                    .lastOrNull { message -> message.role == "assistant" }
                    ?.let { assistantMessage ->
                        executeLegacyQqPlugins(
                            trigger = PluginTriggerSource.AfterModelResponse,
                            event = event,
                            contextFactory = { plugin ->
                                buildQqPluginContext(
                                    plugin = plugin,
                                    trigger = PluginTriggerSource.AfterModelResponse,
            session = FeatureConversationRepository.session(sessionId),
                                    message = assistantMessage,
                                    provider = provider,
                                    bot = bot,
                                    persona = persona,
                                    config = config,
                                    event = event,
                                )
                            },
                        )
                    }
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

    private fun shouldExecuteLegacyQqPluginsDuringLlmDispatch(): Boolean {
        return appChatPluginRuntime === DefaultAppChatPluginRuntime
    }

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
                        val message = payload as? IncomingQqMessage ?: return@forEach
                        buildQqRuntimeService().handleIncomingMessage(message)
                    }
                    if (rateLimiter.drainReady(sourceKey).isEmpty()) {
                        return@launch
                    }
                }
            } finally {
                guard.set(false)
                if (rateLimiter.drainReady(sourceKey).isNotEmpty()) {
                scheduleStashReplay(bot, FeatureConfigRepository.resolve(bot.configProfileId), sourceKey)
                }
            }
        }
    }

    private fun resolveReplyBot(event: IncomingMessageEvent): BotProfile? {
        return FeatureBotRepository.resolveBoundBot(event.selfId)
    }

    private fun resolveProvider(bot: BotProfile, preferredProviderId: String = ""): ProviderProfile? {
        val providers = FeatureProviderRepository.providers.value.filter {
            it.enabled && ProviderCapability.CHAT in it.capabilities
        }
        val config = FeatureConfigRepository.resolve(bot.configProfileId)
        val preferredIds = listOf(
            preferredProviderId,
            bot.defaultProviderId,
            config.defaultChatProviderId,
        ).filter { it.isNotBlank() }

        return preferredIds.firstNotNullOfOrNull { preferredId ->
            providers.firstOrNull { it.id == preferredId }
        } ?: providers.firstOrNull()
    }

    private fun resolveSttProvider(providerId: String): ProviderProfile? {
        return FeatureProviderRepository.providers.value.firstOrNull {
            it.id == providerId &&
                it.enabled &&
                ProviderCapability.STT in it.capabilities
        }
    }

    private fun resolveTtsProvider(providerId: String): ProviderProfile? {
        return FeatureProviderRepository.providers.value.firstOrNull {
            it.id == providerId &&
                it.enabled &&
                ProviderCapability.TTS in it.capabilities
        }
    }

    private fun resolvePersona(bot: BotProfile, sessionPersonaId: String? = null): PersonaProfile? {
        val personas = FeaturePersonaRepository.personas.value.filter { it.enabled }
        return personas.firstOrNull { it.id == sessionPersonaId && sessionPersonaId.isNullOrBlank().not() }
            ?: personas.firstOrNull { it.id == bot.defaultPersonaId }
            ?: personas.firstOrNull()
    }

    /**
     * @deprecated Replaced by [SystemPromptBuilder.build] via [RuntimeContextResolver].
     * Kept only as a documentation reference during Phase 1 migration; no callers remain.
     */
    @Suppress("unused")
    private fun buildSystemPrompt_legacy(
        bot: BotProfile,
        persona: PersonaProfile?,
        event: IncomingMessageEvent,
    ): String? {
        val basePrompt = persona?.systemPrompt?.trim().orEmpty()
        val config = FeatureConfigRepository.resolve(bot.configProfileId)
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

    private fun buildLlmInputSnapshotFallback(
        finalPromptContent: String,
        attachments: List<ConversationAttachment>,
    ): String {
        if (finalPromptContent.isNotBlank()) {
            return finalPromptContent
        }
        if (attachments.isEmpty()) {
            return "[empty]"
        }
        val attachmentSummary = attachments
            .groupingBy { attachment -> attachment.type.ifBlank { "attachment" } }
            .eachCount()
            .entries
            .joinToString(separator = ", ") { (type, count) ->
                if (count == 1) type else "$count x $type"
            }
        return "attachment: $attachmentSummary"
    }

    /**
     * @deprecated Replaced by [StreamingModeResolver.resolve]. Kept as reference during Phase 1.
     */
    @Suppress("unused")
    private fun resolveLlmStreamingMode_legacy(
        config: com.astrbot.android.model.ConfigProfile,
        provider: ProviderProfile,
    ): PluginV2StreamingMode {
        return when {
            !config.textStreamingEnabled -> PluginV2StreamingMode.NON_STREAM
            provider.hasNativeStreamingSupport() -> PluginV2StreamingMode.NATIVE_STREAM
            else -> PluginV2StreamingMode.PSEUDO_STREAM
        }
    }

    private suspend fun legacyProviderPipelineInvokerDoNotUse(
        request: com.astrbot.android.feature.plugin.runtime.PluginProviderRequest,
        mode: PluginV2StreamingMode,
        config: com.astrbot.android.model.ConfigProfile,
        availableProviders: List<ProviderProfile>,
    ): PluginV2ProviderInvocationResult {
        error(
            "Phase 5 migrated QQ provider invocation to feature/qq/runtime. " +
                "This legacy bridge should not be used.",
        )
    }

    private fun parseToolCallArguments(json: String): Map<String, Any?> {
        return try {
            val obj = org.json.JSONObject(json)
            obj.keys().asSequence().associateWith { key -> obj.opt(key) }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun List<ConversationMessage>.toPluginProviderMessages(): List<PluginProviderMessageDto> {
        return map { message ->
            val role = when (message.role.lowercase(Locale.US)) {
                "system" -> PluginProviderMessageRole.SYSTEM
                "assistant" -> PluginProviderMessageRole.ASSISTANT
                "tool" -> PluginProviderMessageRole.TOOL
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
            val toolName: String? = if (role == PluginProviderMessageRole.TOOL) {
                message.toolCallId.ifBlank { "tool" }
            } else {
                null
            }
            val toolMeta: Map<String, Any?>? = if (role == PluginProviderMessageRole.TOOL) {
                mapOf("__host" to mapOf("toolCallId" to message.toolCallId))
            } else {
                null
            }
            PluginProviderMessageDto(
                role = role,
                parts = parts,
                name = toolName,
                metadata = toolMeta,
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
            val toolCallId = if (message.role == PluginProviderMessageRole.TOOL) {
                extractHostToolCallId(message.metadata)
            } else {
                null
            }
            ConversationMessage(
                id = toolCallId ?: "$requestId-$index",
                role = message.role.wireValue,
                content = text,
                timestamp = System.currentTimeMillis(),
                attachments = attachments,
                toolCallId = toolCallId.orEmpty(),
                assistantToolCalls = message.toolCalls.map { toolCall ->
                    ConversationToolCall(
                        id = toolCall.normalizedId,
                        name = toolCall.normalizedToolName,
                        arguments = com.astrbot.android.model.plugin.PluginExecutionProtocolJson.canonicalJson(toolCall.normalizedArguments),
                    )
                },
            )
        }
    }

    private fun extractHostToolCallId(metadata: Map<String, *>?): String? {
        val host = metadata?.get("__host") as? Map<*, *> ?: return null
        return (host["toolCallId"] as? String)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun buildQqPreparedReplyLog(
        requestId: String,
        text: String,
        attachmentCount: Int,
        hookTrace: List<String>,
        decoratingHandlers: List<String>,
    ): String {
        val compactText = text.replace("\r", " ")
            .replace("\n", "\\n")
            .take(240)
        val leakedTag = Regex("\\[[a-zA-Z0-9_-]+]$").containsMatchIn(text.trim())
        return buildString {
            append("QQ prepared reply: request=").append(requestId)
            append(" attachments=").append(attachmentCount)
            append(" leakedTag=").append(leakedTag)
            append(" hooks=")
            append(hookTrace.joinToString(separator = ",").ifBlank { "none" })
            append(" decorators=")
            append(decoratingHandlers.joinToString(separator = ",").ifBlank { "none" })
            append(" text=\"").append(compactText).append('"')
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
            AppLogger.append(
                "QQ runtime plugin dispatch failed: trigger=${trigger.wireValue} reason=${error.message ?: error.javaClass.simpleName}",
            )
        }.getOrNull() ?: return

        batch.skipped.forEach { skip ->
            AppLogger.append(
                "QQ runtime plugin skipped: trigger=${trigger.wireValue} plugin=${skip.plugin.pluginId} reason=${skip.reason.name}",
            )
        }
        batch.merged.conflicts.forEach { conflict ->
            AppLogger.append(
                "QQ runtime plugin merge conflict: trigger=${trigger.wireValue} plugin=${conflict.pluginId} overriddenBy=${conflict.overriddenByPluginId} type=${conflict.resultType}",
            )
        }
        batch.outcomes.forEach { outcome ->
            val resultName = outcome.result::class.simpleName ?: "UnknownResult"
            if (outcome.succeeded) {
                AppLogger.append(
                    "QQ runtime plugin executed: trigger=${trigger.wireValue} plugin=${outcome.pluginId} result=$resultName",
                )
            } else {
                val errorResult = outcome.result as? ErrorResult
                AppLogger.append(
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
            AppLogger.append("OneBot reply skipped: reverse WS is not connected")
            return OneBotSendResult.failure("reverse_ws_not_connected")
        }

        val config = resolveReplyBot(event)?.let { FeatureConfigRepository.resolve(it.configProfileId) }
        val decoration = QqReplyFormatter.buildDecoration(
            messageType = event.messageType,
            messageId = event.messageId,
            senderUserId = event.userId,
            replyTextPrefix = config?.replyTextPrefix.orEmpty(),
            quoteSenderMessageEnabled = config?.quoteSenderMessageEnabled == true,
            mentionSenderEnabled = config?.mentionSenderEnabled == true,
        )
        val messagePayload: Any = QqOneBotPayloadCodec.buildReplyPayload(
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
        AppLogger.append("QQ reply payload: ${action.toString().take(1200)}")

        return runCatching {
            socket.send(action.toString())
            AppLogger.append(
                "QQ reply sent: type=${event.messageType} target=${event.targetId} chars=${text.length} attachments=${attachments.size}",
            )
            OneBotSendResult.success()
        }.getOrElse { error ->
            val summary = error.message ?: error.javaClass.simpleName
            AppLogger.append(
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
            botId = bot.id,
            configProfileId = config.id,
            personaId = currentPersona?.id.orEmpty(),
            providerId = resolveProvider(bot, session.providerId)?.id.orEmpty(),
        )
        dispatchResult.commandResponse?.let { commandResponse ->
            consumeQqV2CommandResponse(
                event = event,
                response = commandResponse,
            )
            return true
        }
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
                        provider = resolveProvider(bot, session.providerId),
                        bot = bot,
                        persona = currentPersona,
                        config = config,
                        event = event,
                    )
                },
            )
        }.onFailure { error ->
            AppLogger.append(
                "QQ plugin command runtime failed: command=${trimmedText.substringBefore(' ')} reason=${error.message ?: error.javaClass.simpleName}",
            )
        }.getOrNull() ?: return false

        batch.skipped.forEach { skip ->
            AppLogger.append(
                "QQ plugin command skipped: plugin=${skip.plugin.pluginId} reason=${skip.reason.name}",
            )
        }
        if (batch.outcomes.isEmpty()) {
            return false
        }
        val consumableOutcomes = batch.outcomes.filter(::isConsumableQqPluginOutcome)
        if (consumableOutcomes.isEmpty()) {
            AppLogger.append(
                "QQ plugin command produced no consumable results: command=${trimmedText.substringBefore(' ')} outcomes=${batch.outcomes.joinToString { it.result::class.simpleName.orEmpty() }}",
            )
            return false
        }
        consumableOutcomes.forEach { outcome ->
            consumeQqPluginOutcome(event = event, outcome = outcome)
            AppLogger.append(
                "QQ plugin command handled: plugin=${outcome.pluginId} result=${outcome.result::class.simpleName.orEmpty()}",
            )
        }
        return true
    }

    private fun consumeQqV2CommandResponse(
        event: IncomingMessageEvent,
        response: PluginV2CommandResponse,
    ) {
        val attachments = response.attachments.mapIndexedNotNull { index, attachment ->
            resolveQqV2CommandAttachment(
                response = response,
                attachment = attachment,
            )?.let { resolvedSource ->
                ConversationAttachment(
                    id = "qq-v2-command-$index-${resolvedSource.hashCode()}",
                    type = if (attachment.mimeType.startsWith("audio/")) "audio" else "image",
                    mimeType = attachment.mimeType.ifBlank { "image/jpeg" },
                    fileName = attachment.label.ifBlank { resolvedSource.substringAfterLast('/', missingDelimiterValue = "attachment-$index") },
                    remoteUrl = resolvedSource,
                )
            }
        }
        sendReply(
            event = event,
            text = response.text,
            attachments = attachments,
        )
        AppLogger.append(
            "QQ v2 command handled: plugin=${response.pluginId} textLength=${response.text.length} attachments=${attachments.size}",
        )
    }

    private fun resolveQqV2CommandAttachment(
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
        val sourceFile = File(source.toString())
        return when {
            sourceFile.isAbsolute -> sourceFile.absolutePath
            response.extractedDir.isNotBlank() -> File(response.extractedDir, source).absolutePath
            else -> source
        }
    }

    private fun dispatchQqV2MessageIngress(
        trigger: PluginTriggerSource,
        event: IncomingMessageEvent,
        materializedEvent: PluginMessageEvent? = null,
        conversationId: String? = null,
        botId: String = "",
        configProfileId: String = "",
        personaId: String = "",
        providerId: String = "",
    ): PluginV2MessageDispatchResult {
        return runCatching {
            runBlocking {
                PluginV2DispatchEngineProvider.engine().dispatchMessage(
                    event = materializedEvent ?: event.toPluginMessageEvent(
                        trigger = trigger,
                        conversationId = conversationId,
                        botId = botId,
                        configProfileId = configProfileId,
                        personaId = personaId,
                        providerId = providerId,
                    ),
                )
            }
        }.onFailure { error ->
            error.rethrowIfCancellation()
            AppLogger.append(
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
        botId: String = "",
        configProfileId: String = "",
        personaId: String = "",
        providerId: String = "",
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
                put("botId", botId)
                put("configProfileId", configProfileId)
                put("personaId", personaId)
                put("providerId", providerId)
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
                            AppLogger.append("QQ plugin notification requested: title=$title message=$message")
                        },
                        openHostPageHandler = { route ->
                            AppLogger.append("QQ plugin requested host page: route=$route")
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
                AppLogger.append(
                    "QQ runtime plugin result is not consumable yet: plugin=${outcome.pluginId} result=${result::class.simpleName.orEmpty()}",
                )
            }
        }
    }

    private fun resolvePluginPrivateRootPath(pluginId: String): String {
        return runCatching {
            PluginStoragePaths.fromFilesDir(
                FeaturePluginRepository.requireAppContext().filesDir,
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
            AppLogger.append("QQ TTS attachment materialized: ${rawFile.absolutePath}")
            val silkFile = SilkAudioEncoder.encode(rawFile)
            val napCatBase64 = "base64://${Base64.getEncoder().encodeToString(silkFile.readBytes())}"
            AppLogger.append("QQ TTS attachment converted to silk: ${silkFile.absolutePath}")
            AppLogger.append("QQ TTS attachment mapped for OneBot: base64://${silkFile.name} bytes=${silkFile.length()}")
            napCatBase64
        }.onFailure { error ->
            AppLogger.append("QQ TTS attachment materialize failed: ${error.message ?: error.javaClass.simpleName}")
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
        val segments = LlmResponseSegmenter.split(
            text = response,
            stripTrailingBoundaryPunctuation = true,
        )
        if (segments.isEmpty()) {
            return sendReplyWithOutcome(event, response)
        }
        AppLogger.append(
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
        val drainResult = LlmResponseSegmenter.drain(
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
        val segments = LlmResponseSegmenter.splitForVoiceStreaming(response)
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
        AppLogger.append(
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
            LlmMediaService.synthesizeSpeech(
                provider = provider,
                text = response,
                voiceId = voiceId,
                readBracketedContent = readBracketedContent,
            )
        }.onFailure { error ->
            AppLogger.append("QQ TTS failed: ${error.message ?: error.javaClass.simpleName}")
        }.onSuccess { attachment ->
            AppLogger.append(
                "QQ TTS success: provider=${provider.name} mime=${attachment.mimeType} size=${attachment.base64Data.length}",
            )
        }.getOrNull()
    }

    private suspend fun sendStreamingVoiceReply(
        event: IncomingMessageEvent,
        attachments: List<ConversationAttachment>,
        config: com.astrbot.android.model.ConfigProfile,
    ) {
        sendStreamingVoiceReplyWithOutcome(
            event = event,
            attachments = attachments,
            config = config,
        )
    }

    private suspend fun sendStreamingVoiceReplyWithOutcome(
        event: IncomingMessageEvent,
        attachments: List<ConversationAttachment>,
        config: com.astrbot.android.model.ConfigProfile,
    ): OneBotSendResult {
        AppLogger.append(
            "QQ voice streaming started: target=${event.targetId} segments=${attachments.size}",
        )
        val receiptIds = mutableListOf<String>()
        attachments.forEachIndexed { index, attachment ->
            val sendResult = sendReplyWithOutcome(
                event = event,
                text = "",
                attachments = listOf(attachment),
            )
            if (!sendResult.success) {
                return sendResult
            }
            receiptIds += sendResult.receiptIds
            if (index < attachments.lastIndex) {
                delay(streamingDelayMs(config))
            }
        }
        return OneBotSendResult.success(receiptIds)
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
            sessions = FeatureConversationRepository.sessions.value,
                bot = bot,
            availableBots = FeatureBotRepository.botProfiles.value,
                config = config,
                activeProviderId = resolveProvider(bot, session.providerId)?.id ?: session.providerId,
            availableProviders = FeatureProviderRepository.providers.value.filter { it.enabled && ProviderCapability.CHAT in it.capabilities },
                currentPersona = currentPersona,
            availablePersonas = FeaturePersonaRepository.personas.value.filter { it.enabled },
                messageType = event.messageType,
                sourceUid = event.userId,
                sourceGroupId = event.groupId,
                selfId = event.selfId,
                deleteSession = { targetSessionId ->
                    FeatureConversationRepository.deleteSession(targetSessionId)
                },
                renameSession = { targetSessionId, title ->
                    FeatureConversationRepository.renameSession(targetSessionId, title)
                },
                updateConfig = { updatedConfig ->
                    FeatureConfigRepository.save(updatedConfig)
                },
                updateBot = { updatedBot ->
                    FeatureBotRepository.save(updatedBot)
                },
                updateProvider = { updatedProvider ->
                    FeatureProviderRepository.save(
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
                    FeatureConversationRepository.updateSessionServiceFlags(
                        sessionId = sessionId,
                        sessionSttEnabled = sttEnabled,
                        sessionTtsEnabled = ttsEnabled,
                    )
                },
                replaceMessages = { messages ->
                    FeatureConversationRepository.replaceMessages(sessionId, messages)
                },
                updateSessionBindings = { providerId, personaId, botId ->
                    FeatureConversationRepository.updateSessionBindings(
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
        AppLogger.append("Bot command handled via router: ${trimmedText.substringBefore(' ')} session=$sessionId")
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
            AppLogger.append(
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

