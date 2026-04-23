package com.astrbot.android.ui.chat

import com.astrbot.android.model.chat.ConversationSession
import com.astrbot.android.model.chat.MessageType

private const val DefaultAppChatConversationId: String = "chat-main"

internal fun ConversationSession.isQqConversation(): Boolean {
    return platformId == "qq"
}

internal fun ConversationSession.isQqPrivateConversation(): Boolean {
    return platformId == "qq" && messageType == MessageType.FriendMessage
}

internal fun ConversationSession.canRenameConversation(): Boolean {
    return isQqPrivateConversation()
}

internal fun ConversationSession.canDeleteConversation(): Boolean {
    return id != DefaultAppChatConversationId
}

