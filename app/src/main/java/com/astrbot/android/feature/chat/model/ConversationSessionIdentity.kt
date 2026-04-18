package com.astrbot.android.model.chat

fun ConversationSession.importDedupKey(): String {
    if (platformId != "qq") return "app:$id"
    val peerType = when (messageType) {
        MessageType.FriendMessage -> "friend"
        MessageType.GroupMessage -> "group"
        MessageType.OtherMessage -> "other"
    }
    return "qq:$botId:$peerType:$originSessionId"
}
