package com.astrbot.android.runtime

import android.content.Context
import com.astrbot.android.data.BotRepository
import com.astrbot.android.data.ChatCompletionService
import com.astrbot.android.data.ConfigRepository
import com.astrbot.android.data.ConversationRepository
import com.astrbot.android.data.PersonaRepository
import com.astrbot.android.data.ProviderRepository
import com.astrbot.android.data.StreamingResponseSegmenter
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.PersonaProfile
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.chat.ConversationSession
import com.astrbot.android.model.chat.MessageType
import com.astrbot.android.model.hasNativeStreamingSupport
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

object OneBotBridgeServer {
    private const val PORT = 6199
    private const val PATH = "/ws"
    private const val AUTH_TOKEN = "astrbot_android_bridge"
    private const val MAX_RECENT_MESSAGE_IDS = 512
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

        val event = parseMessageEvent(json) ?: return
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
            if (handleBotCommand(event, bot, sessionId, session, persona)) {
                return@lock
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
            RuntimeLogRepository.append(
                "QQ message received: type=${event.messageType} session=$sessionId chars=${event.text.length} attachments=${event.attachments.size}",
            )

            val response = runCatching {
                val currentSession = ConversationRepository.session(sessionId)
                if (config.textStreamingEnabled && provider.hasNativeStreamingSupport()) {
                    val outboundBuffer = StringBuilder()
                    ChatCompletionService.sendConfiguredChatStream(
                        provider = provider,
                        messages = currentSession.messages.takeLast(persona?.maxContextMessages ?: currentSession.maxContextMessages),
                        systemPrompt = buildSystemPrompt(bot, persona, event),
                        config = config,
                        availableProviders = ProviderRepository.providers.value,
                    ) { delta ->
                        if (delta.isBlank()) return@sendConfiguredChatStream
                        outboundBuffer.append(delta)
                        if (!wantsTts) {
                            flushStreamingReplyBuffer(
                                event = event,
                                outboundBuffer = outboundBuffer,
                                intervalMs = streamingDelayMs(config),
                                force = false,
                            )
                        }
                    }.also {
                        if (!wantsTts) {
                            flushStreamingReplyBuffer(
                                event = event,
                                outboundBuffer = outboundBuffer,
                                intervalMs = streamingDelayMs(config),
                                force = true,
                            )
                        }
                    }
                } else {
                    ChatCompletionService.sendConfiguredChat(
                        provider = provider,
                        messages = currentSession.messages.takeLast(persona?.maxContextMessages ?: currentSession.maxContextMessages),
                        systemPrompt = buildSystemPrompt(bot, persona, event),
                        config = config,
                        availableProviders = ProviderRepository.providers.value,
                    )
                }
            }.getOrElse { error ->
                val details = error.message ?: error.javaClass.simpleName
                RuntimeLogRepository.append("Auto reply failed: $details")
                sendFailureNoticeIfNeeded(event, "Auto reply failed: $details")
                return@lock
            }

            val assistantAttachments = if (wantsTts && ttsProvider != null) {
                buildVoiceReplyAttachments(
                    provider = ttsProvider,
                    response = response,
                    voiceId = config.ttsVoiceId,
                    voiceStreamingEnabled = config.voiceStreamingEnabled,
                    readBracketedContent = config.ttsReadBracketedContent,
                )
            } else {
                emptyList()
            }

            val outboundBlocked = config.keywordDetectionEnabled && QqKeywordDetector(config.keywordPatterns).matches(response)
            val outboundText = if (outboundBlocked) {
                RuntimeLogRepository.append("QQ outbound keyword blocked: session=$sessionId")
                KEYWORD_BLOCK_NOTICE
            } else {
                response
            }
            val outboundAttachments = if (outboundBlocked) emptyList() else assistantAttachments

            ConversationRepository.appendMessage(sessionId, "assistant", outboundText, attachments = outboundAttachments)
            val sentVoiceReply = wantsTts && outboundAttachments.isNotEmpty()
            if (sentVoiceReply) {
                if (config.voiceStreamingEnabled && outboundAttachments.size > 1) {
                    sendStreamingVoiceReply(event, outboundAttachments, config)
                } else {
                    sendReply(
                        event = event,
                        text = "",
                        attachments = outboundAttachments,
                    )
                }
            } else if (config.textStreamingEnabled && !provider.hasNativeStreamingSupport()) {
                sendPseudoStreamingReply(event, outboundText, config)
            } else if (config.textStreamingEnabled && provider.hasNativeStreamingSupport()) {
                if (wantsTts) {
                    sendReply(event = event, text = outboundText)
                }
            } else {
                sendReply(
                    event = event,
                    text = outboundText,
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

    private fun sendReply(
        event: IncomingMessageEvent,
        text: String,
        attachments: List<ConversationAttachment> = emptyList(),
    ) {
        val socket = activeSocket
        if (socket == null) {
            RuntimeLogRepository.append("OneBot reply skipped: reverse WS is not connected")
            return
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
        val messagePayload: Any = buildReplyPayload(text, attachments, decoration)
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

        runCatching {
            socket.send(action.toString())
            RuntimeLogRepository.append(
                "QQ reply sent: type=${event.messageType} target=${event.targetId} chars=${text.length} attachments=${attachments.size}",
            )
        }.onFailure { error ->
            RuntimeLogRepository.append(
                "QQ reply send failed: ${error.message ?: error.javaClass.simpleName}",
            )
        }
    }

    private fun buildReplyPayload(
        text: String,
        attachments: List<ConversationAttachment>,
        decoration: com.astrbot.android.runtime.qq.QqReplyDecoration,
    ): Any {
        val finalText = decoration.textPrefix + text
        if (
            attachments.isEmpty() &&
            decoration.quoteMessageId == null &&
            decoration.mentionUserId == null
        ) {
            return finalText
        }
        val payload = JSONArray().apply {
            decoration.quoteMessageId?.let { messageId ->
                put(
                    JSONObject().put("type", "reply").put(
                        "data",
                        JSONObject().put("id", messageId),
                    ),
                )
            }
            decoration.mentionUserId?.let { userId ->
                put(
                    JSONObject().put("type", "at").put(
                        "data",
                        JSONObject().put("qq", userId),
                    ),
                )
            }
            if (finalText.isNotBlank()) {
                put(
                    JSONObject().put("type", "text").put(
                        "data",
                        JSONObject().put("text", finalText),
                    ),
                )
            }
            attachments.forEach { attachment ->
                when (attachment.type) {
                    "audio" -> {
            val fileValue = materializeAudioAttachmentForOneBot(attachment)
                            ?: attachment.remoteUrl
                        if (fileValue.isNotBlank()) {
                            put(
                                JSONObject().put("type", "record").put(
                                    "data",
                                    JSONObject().put("file", fileValue),
                                ),
                            )
                        }
                    }

                    "image" -> {
                        val fileValue = attachment.base64Data.takeIf { it.isNotBlank() }?.let { "base64://$it" }
                            ?: attachment.remoteUrl
                        if (fileValue.isNotBlank()) {
                            put(
                                JSONObject().put("type", "image").put(
                                    "data",
                                    JSONObject().put("file", fileValue),
                                ),
                            )
                        }
                    }
                }
            }
        }
        return if (payload.length() > 0) payload else finalText
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
        val segments = StreamingResponseSegmenter.split(
            text = response,
            stripTrailingBoundaryPunctuation = true,
        )
        if (segments.isEmpty()) {
            sendReply(event, response)
            return
        }
        RuntimeLogRepository.append(
            "QQ pseudo streaming started: target=${event.targetId} segments=${segments.size} chars=${response.length}",
        )
        segments.forEachIndexed { index, segment ->
            sendReply(event, segment)
            if (index < segments.lastIndex) {
                delay(streamingDelayMs(config))
            }
        }
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
        sessionId: String,
        session: ConversationSession,
        currentPersona: PersonaProfile?,
    ): Boolean {
        val trimmedText = event.text.trim()
        return when {
            trimmedText.equals("/reset", ignoreCase = true) -> {
                ConversationRepository.replaceMessages(sessionId, emptyList())
                sendReply(event, "Current conversation context cleared.")
                RuntimeLogRepository.append("Bot command handled: /reset session=$sessionId")
                true
            }

            trimmedText.equals("/stt", ignoreCase = true) -> {
                val next = !session.sessionSttEnabled
                ConversationRepository.updateSessionServiceFlags(sessionId, sessionSttEnabled = next)
                sendReply(
                    event,
                    if (next) "STT enabled for this conversation." else "STT disabled for this conversation.",
                )
                RuntimeLogRepository.append("Bot command handled: /stt session=$sessionId enabled=$next")
                true
            }

            trimmedText.equals("/tts", ignoreCase = true) -> {
                val next = !session.sessionTtsEnabled
                ConversationRepository.updateSessionServiceFlags(sessionId, sessionTtsEnabled = next)
                sendReply(
                    event,
                    if (next) "TTS enabled for this conversation." else "TTS disabled for this conversation.",
                )
                RuntimeLogRepository.append("Bot command handled: /tts session=$sessionId enabled=$next")
                true
            }

            trimmedText.equals("/persona list", ignoreCase = true) -> {
                val personas = PersonaRepository.personas.value.filter { it.enabled }
                val message = if (personas.isEmpty()) {
                    "No personas available."
                } else {
                    buildString {
                        appendLine("Available personas:")
                        personas.forEach { persona ->
                            appendLine(
                                if (persona.id == currentPersona?.id) "- ${persona.name} (current)" else "- ${persona.name}",
                            )
                        }
                    }.trim()
                }
                sendReply(event, message)
                RuntimeLogRepository.append("Bot command handled: /persona list session=$sessionId")
                true
            }

            trimmedText.equals("/persona", ignoreCase = true) -> {
                sendReply(
                    event,
                    listOf(
                        "Persona commands:",
                        "/persona list - list all personas and mark the current one",
                        "/persona <name> - switch persona for the current conversation",
                        "/persona view <name> - show the persona system prompt",
                    ).joinToString("\n"),
                )
                RuntimeLogRepository.append("Bot command handled: /persona help session=$sessionId")
                true
            }

            trimmedText.startsWith("/persona view ", ignoreCase = true) -> {
                val name = trimmedText.substringAfter("/persona view", "").trim()
                val persona = findPersonaByName(name)
                sendReply(
                    event,
                    if (persona == null) {
                        "Persona not found. Use /persona list to check available personas."
                    } else {
                        "Persona ${persona.name} system prompt:\n${persona.systemPrompt}"
                    },
                )
                RuntimeLogRepository.append("Bot command handled: /persona view session=$sessionId target=${name.ifBlank { "-" }}")
                true
            }

            trimmedText.startsWith("/persona ", ignoreCase = true) -> {
                val name = trimmedText.substringAfter("/persona", "").trim()
                val targetPersona = findPersonaByName(name)
                if (targetPersona == null) {
                    sendReply(event, "Persona not found. Use /persona list to check available personas.")
                    RuntimeLogRepository.append("Bot command failed: persona not found target=${name.ifBlank { "-" }}")
                    true
                } else {
                    val providerId = resolveProvider(bot)?.id ?: session.providerId
                    ConversationRepository.updateSessionBindings(
                        sessionId = sessionId,
                        providerId = providerId,
                        personaId = targetPersona.id,
                        botId = bot.id,
                    )
                    sendReply(
                        event,
                        "Persona switched to ${targetPersona.name}. Use /reset to clear current context if you want to avoid old context affecting the new persona.",
                    )
                    RuntimeLogRepository.append("Bot command handled: /persona session=$sessionId target=${targetPersona.id}")
                    true
                }
            }

            else -> false
        }
    }

    private fun findPersonaByName(name: String): PersonaProfile? {
        val normalized = name.trim()
        if (normalized.isBlank()) return null
        return PersonaRepository.personas.value.firstOrNull {
            it.enabled && it.name.equals(normalized, ignoreCase = true)
        }
    }

    private fun parseMessageEvent(json: JSONObject): IncomingMessageEvent? {
        val messageType = when (json.optString("message_type")) {
            "private" -> MessageType.FriendMessage
            "group" -> MessageType.GroupMessage
            else -> null
        } ?: return null

        val selfId = jsonValueAsString(json, "self_id")
        val userId = jsonValueAsString(json, "user_id")
        if (userId.isBlank()) return null

        val parsedMessage = parseMessage(
            rawMessage = json.opt("message"),
            fallbackText = json.optString("raw_message"),
            selfId = selfId,
        )
        val sender = json.optJSONObject("sender")
        val senderName = sender
            ?.optString("card")
            .orEmpty()
            .ifBlank { sender?.optString("nickname").orEmpty() }

        return IncomingMessageEvent(
            selfId = selfId,
            userId = userId,
            groupId = jsonValueAsString(json, "group_id"),
            messageId = jsonValueAsString(json, "message_id"),
            messageType = messageType,
            text = parsedMessage.text,
            promptContent = when (messageType) {
                MessageType.GroupMessage -> "${senderName.ifBlank { userId }}: ${parsedMessage.text}"
                else -> parsedMessage.text
            },
            mentionsSelf = parsedMessage.mentionsSelf,
            mentionsAll = parsedMessage.mentionsAll,
            attachments = parsedMessage.attachments,
            senderName = senderName,
        )
    }

    private fun parseMessage(
        rawMessage: Any?,
        fallbackText: String,
        selfId: String,
    ): ParsedMessage {
        if (rawMessage is JSONArray) {
            val builder = StringBuilder()
            var mentionsSelf = false
            var mentionsAll = false
            val attachments = mutableListOf<ConversationAttachment>()
            for (index in 0 until rawMessage.length()) {
                val segment = rawMessage.optJSONObject(index) ?: continue
                when (segment.optString("type")) {
                    "text" -> builder.append(segment.optJSONObject("data")?.optString("text").orEmpty())
                    "at" -> {
                        val qq = segment.optJSONObject("data")?.optString("qq").orEmpty()
                        if (qq.isNotBlank() && qq == selfId) {
                            mentionsSelf = true
                        }
                        if (qq.equals("all", ignoreCase = true)) {
                            mentionsAll = true
                        }
                    }
                    "image" -> {
                        val data = segment.optJSONObject("data")
                        attachments += ConversationAttachment(
                            id = "${System.currentTimeMillis()}-$index",
                            type = "image",
                            mimeType = "image/jpeg",
                            fileName = data?.optString("file").orEmpty(),
                            remoteUrl = data?.optString("url").orEmpty(),
                        )
                    }
                    "record" -> {
                        val data = segment.optJSONObject("data")
                        attachments += ConversationAttachment(
                            id = "${System.currentTimeMillis()}-$index",
                            type = "audio",
                            mimeType = "audio/mpeg",
                            fileName = data?.optString("file").orEmpty(),
                            remoteUrl = data?.optString("url").orEmpty(),
                        )
                    }
                }
            }
            return ParsedMessage(
                text = builder.toString().trim(),
                mentionsSelf = mentionsSelf,
                mentionsAll = mentionsAll,
                attachments = attachments,
            )
        }

        if (rawMessage is String) {
            return ParsedMessage(
                text = rawMessage.trim(),
                mentionsSelf = false,
                mentionsAll = false,
                attachments = emptyList(),
            )
        }

        return ParsedMessage(
            text = fallbackText.trim(),
            mentionsSelf = false,
            mentionsAll = false,
            attachments = emptyList(),
        )
    }

    private fun isBotCommand(text: String): Boolean {
        val normalized = text.trim().lowercase()
        return normalized == "/reset" ||
            normalized == "/stt" ||
            normalized == "/tts" ||
            normalized.startsWith("/persona")
    }

    private fun jsonValueAsString(json: JSONObject, key: String): String {
        return json.opt(key)?.toString().orEmpty()
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

    private data class ParsedMessage(
        val text: String,
        val mentionsSelf: Boolean,
        val mentionsAll: Boolean,
        val attachments: List<ConversationAttachment>,
    )

    private data class IncomingMessageEvent(
        val selfId: String,
        val userId: String,
        val groupId: String,
        val messageId: String,
        val messageType: MessageType,
        val text: String,
        val promptContent: String,
        val mentionsSelf: Boolean,
        val mentionsAll: Boolean,
        val attachments: List<ConversationAttachment>,
        val senderName: String,
    ) {
        val targetId: String
            get() = if (messageType == MessageType.GroupMessage) groupId else userId
    }
}
