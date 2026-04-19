package com.astrbot.android.feature.qq.runtime

import com.astrbot.android.feature.bot.domain.BotRepositoryPort
import com.astrbot.android.feature.config.domain.ConfigRepositoryPort
import com.astrbot.android.feature.plugin.runtime.PluginV2HostSendResult
import com.astrbot.android.feature.qq.domain.IncomingQqMessage
import com.astrbot.android.feature.qq.domain.QqPlatformConfigPort
import com.astrbot.android.feature.qq.domain.QqReplyPayload
import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.chat.MessageType
import org.json.JSONObject

internal class QqOneBotOutboundGateway(
    private val transport: OneBotReverseWebSocketTransport,
    private val botPort: BotRepositoryPort,
    private val configPort: ConfigRepositoryPort,
    private val platformConfigPort: QqPlatformConfigPort,
    private val audioMaterializer: QqAudioAttachmentMaterializer,
    private val replyOverrideProvider: () -> ((IncomingMessageEvent, String, List<ConversationAttachment>) -> OneBotSendResult)?,
    private val log: (String) -> Unit,
) {
    fun buildReplySender(): QqReplySender {
        return QqReplySender(
            socketSender = { payload: String ->
                transport.send(payload).getOrThrow()
            },
            resolveReplyConfig = ::resolveReplyConfig,
            mapAudioAttachment = audioMaterializer::materialize,
            sendOverride = { payload, originalMessage ->
                replyOverrideProvider()?.let { override ->
                    override(
                        originalMessage.toLegacyIncomingMessageEvent(),
                        payload.text,
                        payload.attachments,
                    ).toHostSendResult()
                } ?: sendLegacyReply(
                    event = originalMessage.toLegacyIncomingMessageEvent(),
                    text = payload.text,
                    attachments = payload.attachments,
                ).toHostSendResult()
            },
            log = log,
        )
    }

    fun sendScheduledMessage(
        conversationId: String,
        text: String,
        attachments: List<ConversationAttachment>,
        botId: String,
    ): OneBotSendResult {
        if (!transport.isConnected()) {
            log("QQ scheduled reply skipped: reverse WS is not connected")
            return OneBotSendResult.failure("reverse_ws_not_connected")
        }

        val normalizedConversationId = conversationId.trim()
        val isGroup = normalizedConversationId.startsWith("group:")
        val targetId = normalizedConversationId.substringAfter(':', "").trim()
        if (targetId.isBlank()) {
            return OneBotSendResult.failure("invalid_conversation_id")
        }

        val bot = botId.takeIf { it.isNotBlank() }?.let { targetBotId ->
            botPort.snapshotProfiles().firstOrNull { it.id == targetBotId }
        }
        val config = bot?.let { configPort.resolve(it.configProfileId) }
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
            mapAudioAttachment = audioMaterializer::materialize,
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
            transport.send(action.toString()).getOrThrow()
            log(
                "QQ scheduled reply sent: conversation=$normalizedConversationId chars=${text.length} attachments=${attachments.size}",
            )
            OneBotSendResult.success()
        }.getOrElse { error ->
            val summary = error.message ?: error.javaClass.simpleName
            log("QQ scheduled reply send failed: $summary")
            OneBotSendResult.failure(summary)
        }
    }

    fun sendLegacyReply(
        event: IncomingMessageEvent,
        text: String,
        attachments: List<ConversationAttachment> = emptyList(),
    ): OneBotSendResult {
        replyOverrideProvider()?.let { sender ->
            return sender(event, text, attachments)
        }

        if (!transport.isConnected()) {
            log("OneBot reply skipped: reverse WS is not connected")
            return OneBotSendResult.failure("reverse_ws_not_connected")
        }

        val replyConfig = resolveReplyConfig(event.selfId)
        val decoration = QqReplyFormatter.buildDecoration(
            messageType = event.messageType,
            messageId = event.messageId,
            senderUserId = event.userId,
            replyTextPrefix = replyConfig?.replyTextPrefix.orEmpty(),
            quoteSenderMessageEnabled = replyConfig?.quoteSenderMessageEnabled == true,
            mentionSenderEnabled = replyConfig?.mentionSenderEnabled == true,
        )
        val messagePayload: Any = QqOneBotPayloadCodec.buildReplyPayload(
            text = text,
            attachments = attachments,
            decoration = decoration,
            mapAudioAttachment = audioMaterializer::materialize,
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
        log("QQ reply payload: ${action.toString().take(1200)}")

        return runCatching {
            transport.send(action.toString()).getOrThrow()
            log(
                "QQ reply sent: type=${event.messageType} target=${event.targetId} chars=${text.length} attachments=${attachments.size}",
            )
            OneBotSendResult.success()
        }.getOrElse { error ->
            val summary = error.message ?: error.javaClass.simpleName
            log("QQ reply send failed: $summary")
            OneBotSendResult.failure(summary)
        }
    }

    private fun resolveReplyConfig(selfId: String): QqReplySender.ReplyConfig? {
        val bot = platformConfigPort.resolveQqBot(selfId) ?: return null
        val config = configPort.resolve(bot.configProfileId)
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
}

internal fun OneBotSendResult.toHostSendResult(): PluginV2HostSendResult {
    return PluginV2HostSendResult(
        success = success,
        receiptIds = receiptIds,
        errorSummary = errorSummary,
    )
}
