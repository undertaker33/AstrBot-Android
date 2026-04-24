package com.astrbot.android.feature.cron.runtime

import com.astrbot.android.feature.chat.domain.ConversationRepositoryPort
import com.astrbot.android.feature.qq.runtime.QqScheduledMessageSender
import com.astrbot.android.model.chat.ConversationAttachment
import javax.inject.Inject

internal interface ScheduledMessageDeliveryPort {
    suspend fun deliver(request: ScheduledMessageDeliveryRequest): ScheduledMessageDeliveryResult
}

internal data class ScheduledMessageDeliveryRequest(
    val platform: String,
    val conversationId: String,
    val text: String,
    val attachments: List<ConversationAttachment> = emptyList(),
    val botId: String,
)

internal data class ScheduledMessageDeliveryResult(
    val success: Boolean,
    val deliveredMessageCount: Int,
    val receiptIds: List<String> = emptyList(),
    val errorCode: String = "",
    val errorSummary: String = "",
)

internal class DefaultScheduledMessageDeliveryPort @Inject constructor(
    private val conversationPort: ConversationRepositoryPort,
    private val qqScheduledMessageSender: QqScheduledMessageSender,
) : ScheduledMessageDeliveryPort {
    override suspend fun deliver(request: ScheduledMessageDeliveryRequest): ScheduledMessageDeliveryResult {
        return when (request.platform.normalizedPlatform()) {
            "app", "app_chat" -> deliverAppChat(request)
            "qq", "onebot", "qq_onebot" -> deliverQq(request)
            else -> ScheduledMessageDeliveryResult(
                success = false,
                deliveredMessageCount = 0,
                errorCode = "unsupported_platform",
                errorSummary = "Unsupported scheduled message platform: ${request.platform}",
            )
        }
    }

    private fun deliverAppChat(request: ScheduledMessageDeliveryRequest): ScheduledMessageDeliveryResult {
        appendAssistantReply(request)
        return ScheduledMessageDeliveryResult(
            success = true,
            deliveredMessageCount = 1,
        )
    }

    private fun deliverQq(request: ScheduledMessageDeliveryRequest): ScheduledMessageDeliveryResult {
        val result = qqScheduledMessageSender.sendScheduledMessage(
            conversationId = request.conversationId,
            text = request.text,
            attachments = request.attachments,
            botId = request.botId,
        )
        if (result.success) {
            appendAssistantReply(request)
        }
        return ScheduledMessageDeliveryResult(
            success = result.success,
            deliveredMessageCount = if (result.success) 1 else 0,
            receiptIds = result.receiptIds,
            errorCode = if (result.success) "" else "qq_delivery_failed",
            errorSummary = result.errorSummary,
        )
    }

    private fun appendAssistantReply(request: ScheduledMessageDeliveryRequest) {
        conversationPort.appendMessage(
            sessionId = request.conversationId,
            role = "assistant",
            content = request.text,
            attachments = request.attachments,
        )
    }

    private fun String.normalizedPlatform(): String = trim().lowercase()
}
