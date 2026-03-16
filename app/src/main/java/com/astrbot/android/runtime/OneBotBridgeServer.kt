package com.astrbot.android.runtime

import com.astrbot.android.data.BotRepository
import com.astrbot.android.data.ChatCompletionService
import com.astrbot.android.data.ConfigRepository
import com.astrbot.android.data.ConversationRepository
import com.astrbot.android.data.PersonaRepository
import com.astrbot.android.data.ProviderRepository
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.PersonaProfile
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoWSD
import fi.iki.elonen.NanoWSD.WebSocket
import fi.iki.elonen.NanoWSD.WebSocketFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

object OneBotBridgeServer {
    private const val PORT = 6199
    private const val PATH = "/ws"
    private const val AUTH_TOKEN = "astrbot_android_bridge"
    private const val MAX_RECENT_MESSAGE_IDS = 512

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)
    private val conversationLocks = ConcurrentHashMap<String, Mutex>()
    private val recentMessageIds = object : LinkedHashMap<String, Unit>(MAX_RECENT_MESSAGE_IDS, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?): Boolean {
            return size > MAX_RECENT_MESSAGE_IDS
        }
    }

    @Volatile
    private var server: OneBotWebSocketServer? = null

    @Volatile
    private var activeSocket: OneBotWebSocket? = null

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
        if (!shouldReply(event)) return
        if (event.messageId.isNotBlank() && !markMessageId(event.messageId)) {
            RuntimeLogRepository.append("OneBot duplicate message ignored: ${event.messageId}")
            return
        }

        processMessageEvent(event)
    }

    private suspend fun processMessageEvent(event: IncomingMessageEvent) {
        val bot = resolveReplyBot(event) ?: run {
            RuntimeLogRepository.append("Auto reply skipped: no QQ bot profile available")
            return
        }
        if (!bot.autoReplyEnabled) {
            RuntimeLogRepository.append("Auto reply skipped: bot ${bot.id} is disabled")
            return
        }

        val sessionId = buildSessionId(bot, event)
        val sessionTitle = buildSessionTitle(event)
        val conversationLock = conversationLocks.getOrPut(sessionId) { Mutex() }

        conversationLock.withLock {
            val session = ConversationRepository.session(sessionId)
            if (session.title != sessionTitle) {
                ConversationRepository.renameSession(sessionId, sessionTitle)
            }
            val persona = resolvePersona(bot, session.personaId)
            if (handleBotCommand(event, bot, sessionId, session, persona)) {
                return@withLock
            }
            val provider = resolveProvider(bot, persona)
            if (provider == null) {
                RuntimeLogRepository.append("Auto reply skipped: no enabled chat provider configured")
                sendFailureNoticeIfNeeded(event, "No chat model is configured for this bot.")
                return@withLock
            }
            ConversationRepository.updateSessionBindings(
                sessionId = sessionId,
                providerId = provider.id,
                personaId = persona?.id.orEmpty(),
                botId = bot.id,
            )

            ConversationRepository.appendMessage(sessionId, "user", event.promptContent)
            RuntimeLogRepository.append(
                "QQ message received: type=${event.messageType} session=$sessionId chars=${event.text.length}",
            )

            val response = runCatching {
                val currentSession = ConversationRepository.session(sessionId)
                ChatCompletionService.sendChat(
                    provider = provider,
                    messages = currentSession.messages.takeLast(persona?.maxContextMessages ?: currentSession.maxContextMessages),
                    systemPrompt = buildSystemPrompt(bot, persona, event),
                )
            }.getOrElse { error ->
                val details = error.message ?: error.javaClass.simpleName
                RuntimeLogRepository.append("Auto reply failed: $details")
                sendFailureNoticeIfNeeded(event, "Auto reply failed: $details")
                return
            }

            ConversationRepository.appendMessage(sessionId, "assistant", response)
            sendReply(event, response)
        }
    }

    private fun shouldReply(event: IncomingMessageEvent): Boolean {
        if (event.text.isBlank()) return false
        if (event.selfId.isNotBlank() && event.selfId == event.userId) return false

        val bot = resolveReplyBot(event) ?: return false
        if (!bot.autoReplyEnabled) return false

        return when (event.messageType) {
            "private" -> true
            "group" -> isBotCommand(event.text) || event.mentionsSelf || containsTriggerWord(event.text, bot.triggerWords)
            else -> false
        }
    }

    private fun resolveReplyBot(event: IncomingMessageEvent): BotProfile? {
        return BotRepository.resolveBoundBot(event.selfId)
    }

    private fun resolveProvider(bot: BotProfile, persona: PersonaProfile?): ProviderProfile? {
        val providers = ProviderRepository.providers.value.filter {
            it.enabled && ProviderCapability.CHAT in it.capabilities
        }
        val config = ConfigRepository.resolve(bot.configProfileId)
        val preferredIds = listOf(
            config.defaultChatProviderId,
            bot.defaultProviderId,
            persona?.defaultProviderId.orEmpty(),
        ).filter { it.isNotBlank() }

        return preferredIds.firstNotNullOfOrNull { preferredId ->
            providers.firstOrNull { it.id == preferredId }
        } ?: providers.firstOrNull()
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
        val channelPrompt = if (event.messageType == "group") {
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

    private fun buildSessionId(bot: BotProfile, event: IncomingMessageEvent): String {
        return when (event.messageType) {
            "group" -> "qq-${bot.id}-group-${event.groupId}"
            else -> "qq-${bot.id}-private-${event.userId}"
        }
    }

    private fun buildSessionTitle(event: IncomingMessageEvent): String {
        return when (event.messageType) {
            "group" -> "QQ Group ${event.groupId}"
            else -> "QQ Private ${event.senderName.ifBlank { event.userId }}"
        }
    }

    private fun sendReply(event: IncomingMessageEvent, text: String) {
        val socket = activeSocket
        if (socket == null) {
            RuntimeLogRepository.append("OneBot reply skipped: reverse WS is not connected")
            return
        }

        val params = JSONObject().apply {
            put("message_type", event.messageType)
            put("message", text)
            put("auto_escape", false)
            when (event.messageType) {
                "group" -> put("group_id", event.groupId)
                else -> put("user_id", event.userId)
            }
        }
        val action = JSONObject().apply {
            put("action", "send_msg")
            put("params", params)
            put("echo", "astrbot-${System.currentTimeMillis()}")
        }

        runCatching {
            socket.send(action.toString())
            RuntimeLogRepository.append(
                "QQ reply sent: type=${event.messageType} target=${event.targetId} chars=${text.length}",
            )
        }.onFailure { error ->
            RuntimeLogRepository.append(
                "QQ reply send failed: ${error.message ?: error.javaClass.simpleName}",
            )
        }
    }

    private fun sendFailureNoticeIfNeeded(event: IncomingMessageEvent, message: String) {
        if (event.messageType == "private") {
            sendReply(event, message)
        }
    }

    private fun handleBotCommand(
        event: IncomingMessageEvent,
        bot: BotProfile,
        sessionId: String,
        session: com.astrbot.android.model.ConversationSession,
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
                    val providerId = resolveProvider(bot, targetPersona)?.id ?: session.providerId
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
        val messageType = json.optString("message_type")
        if (messageType != "private" && messageType != "group") {
            return null
        }

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
                "group" -> "${senderName.ifBlank { userId }}: ${parsedMessage.text}"
                else -> parsedMessage.text
            },
            mentionsSelf = parsedMessage.mentionsSelf,
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
            for (index in 0 until rawMessage.length()) {
                val segment = rawMessage.optJSONObject(index) ?: continue
                when (segment.optString("type")) {
                    "text" -> builder.append(segment.optJSONObject("data")?.optString("text").orEmpty())
                    "at" -> {
                        val qq = segment.optJSONObject("data")?.optString("qq").orEmpty()
                        if (qq.isNotBlank() && qq == selfId) {
                            mentionsSelf = true
                        }
                    }
                }
            }
            return ParsedMessage(
                text = builder.toString().trim(),
                mentionsSelf = mentionsSelf,
            )
        }

        if (rawMessage is String) {
            return ParsedMessage(
                text = rawMessage.trim(),
                mentionsSelf = false,
            )
        }

        return ParsedMessage(
            text = fallbackText.trim(),
            mentionsSelf = false,
        )
    }

    private fun containsTriggerWord(text: String, triggerWords: List<String>): Boolean {
        val normalizedText = text.lowercase()
        return triggerWords
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .any { trigger -> normalizedText.contains(trigger) }
    }

    private fun isBotCommand(text: String): Boolean {
        val normalized = text.trim().lowercase()
        return normalized == "/reset" || normalized.startsWith("/persona")
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
    )

    private data class IncomingMessageEvent(
        val selfId: String,
        val userId: String,
        val groupId: String,
        val messageId: String,
        val messageType: String,
        val text: String,
        val promptContent: String,
        val mentionsSelf: Boolean,
        val senderName: String,
    ) {
        val targetId: String
            get() = if (messageType == "group") groupId else userId
    }
}
