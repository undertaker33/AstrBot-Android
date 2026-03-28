package com.astrbot.android.runtime.qq

import com.astrbot.android.model.chat.MessageType

object QqSessionKeyFactory {
    fun build(
        botId: String,
        messageType: MessageType,
        groupId: String,
        userId: String,
        isolated: Boolean,
    ): String {
        return when (messageType) {
            MessageType.GroupMessage -> {
                if (isolated) {
                    // When group isolation is enabled, the session key must include the sender.
                    // This keeps context and persona state independent per member, matching desktop behavior.
                    "qq-${botId}-group-${groupId}-user-${userId}"
                } else {
                    "qq-${botId}-group-${groupId}"
                }
            }

            MessageType.FriendMessage,
            MessageType.OtherMessage,
            -> "qq-${botId}-private-${userId}"
        }
    }
}
