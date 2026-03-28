package com.astrbot.android.ui.chat

import com.astrbot.android.data.ConversationRepository
import com.astrbot.android.model.chat.ConversationSession
import com.astrbot.android.model.chat.MessageType

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
    return id != ConversationRepository.DEFAULT_SESSION_ID
}
