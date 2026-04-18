package com.astrbot.android.feature.qq.runtime

import com.astrbot.android.feature.qq.domain.IncomingQqMessage
import com.astrbot.android.feature.qq.domain.QqReplyPayload
import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.chat.MessageType
import com.astrbot.android.feature.qq.runtime.QqOneBotPayloadCodec
import com.astrbot.android.feature.plugin.runtime.PluginV2HostSendResult
import com.astrbot.android.feature.qq.runtime.QqReplyDecoration
import com.astrbot.android.feature.qq.runtime.QqReplyFormatter
import org.json.JSONObject

internal class QqReplySender(
    private val socketSender: (String) -> Unit,
    private val resolveReplyConfig: (String) -> ReplyConfig?,
    private val mapAudioAttachment: (ConversationAttachment) -> String? = { null },
    private val sendOverride: ((QqReplyPayload, IncomingQqMessage) -> PluginV2HostSendResult)? = null,
    private val log: (String) -> Unit = {},
) {
    fun send(payload: QqReplyPayload, originalMessage: IncomingQqMessage) {
        sendWithOutcome(payload, originalMessage)
    }

    fun sendWithOutcome(payload: QqReplyPayload, originalMessage: IncomingQqMessage): PluginV2HostSendResult {
        sendOverride?.let { override ->
            return override(payload, originalMessage)
        }
        val config = resolveReplyConfig(originalMessage.selfId)
        val decoration = QqReplyFormatter.buildDecoration(
            messageType = originalMessage.messageType,
            messageId = originalMessage.messageId,
            senderUserId = originalMessage.senderId,
            replyTextPrefix = config?.replyTextPrefix.orEmpty(),
            quoteSenderMessageEnabled = config?.quoteSenderMessageEnabled == true,
            mentionSenderEnabled = config?.mentionSenderEnabled == true,
        )
        val messagePayload: Any = QqOneBotPayloadCodec.buildReplyPayload(
            text = payload.text,
            attachments = payload.attachments,
            decoration = decoration,
            mapAudioAttachment = mapAudioAttachment,
        )
        val params = JSONObject().apply {
            put("message", messagePayload)
            put("auto_escape", false)
            when (payload.messageType) {
                MessageType.GroupMessage -> put("group_id", originalMessage.conversationId)
                else -> put("user_id", originalMessage.senderId)
            }
        }
        val action = JSONObject().apply {
            put(
                "action",
                if (payload.messageType == MessageType.GroupMessage) "send_group_msg" else "send_private_msg",
            )
            put("params", params)
            put("echo", "astrbot-${System.currentTimeMillis()}")
        }
        return try {
            socketSender(action.toString())
            log("QQ reply sent: type=${payload.messageType} target=${originalMessage.conversationId} chars=${payload.text.length}")
            PluginV2HostSendResult(success = true)
        } catch (e: Exception) {
            log("QQ reply send failed: ${e.message}")
            PluginV2HostSendResult(success = false, errorSummary = e.message ?: "send_failed")
        }
    }

    data class ReplyConfig(
        val replyTextPrefix: String = "",
        val quoteSenderMessageEnabled: Boolean = false,
        val mentionSenderEnabled: Boolean = false,
    )
}
