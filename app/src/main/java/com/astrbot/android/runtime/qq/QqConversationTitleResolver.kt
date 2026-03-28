package com.astrbot.android.runtime.qq

import com.astrbot.android.model.chat.MessageType

object QqConversationTitleResolver {
    fun build(
        messageType: MessageType,
        groupId: String,
        userId: String,
        senderName: String,
    ): String {
        val displayName = senderName.trim().ifBlank { userId }
        return when (messageType) {
            MessageType.GroupMessage -> "QQG ${groupId.ifBlank { "unknown" }} $displayName"
            else -> "QQP $displayName"
        }
    }
}
