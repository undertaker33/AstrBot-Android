package com.astrbot.android.runtime.qq

import com.astrbot.android.model.chat.MessageType

data class QqReplyDecoration(
    val textPrefix: String,
    val quoteMessageId: String?,
    val mentionUserId: String?,
)

object QqReplyFormatter {
    fun buildDecoration(
        messageType: MessageType,
        messageId: String,
        senderUserId: String,
        replyTextPrefix: String,
        quoteSenderMessageEnabled: Boolean,
        mentionSenderEnabled: Boolean,
    ): QqReplyDecoration {
        if (messageType != MessageType.GroupMessage) {
            return QqReplyDecoration(
                textPrefix = replyTextPrefix,
                quoteMessageId = null,
                mentionUserId = null,
            )
        }
        return QqReplyDecoration(
            textPrefix = replyTextPrefix,
            quoteMessageId = messageId.takeIf { quoteSenderMessageEnabled && it.isNotBlank() },
            mentionUserId = senderUserId.takeIf { mentionSenderEnabled && it.isNotBlank() },
        )
    }
}
