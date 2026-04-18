package com.astrbot.android.model.chat

private val QQ_PRIVATE_SESSION_PATTERN = Regex("""^qq-(.+?)-private-(.+)$""")
private val QQ_GROUP_SESSION_PATTERN = Regex("""^qq-(.+?)-group-(.+)$""")

fun defaultSessionRefFor(sessionId: String): MessageSessionRef {
    QQ_PRIVATE_SESSION_PATTERN.matchEntire(sessionId)?.destructured?.let { (_, userId) ->
        return MessageSessionRef(
            platformId = "qq",
            messageType = MessageType.FriendMessage,
            originSessionId = "friend:$userId",
        )
    }
    QQ_GROUP_SESSION_PATTERN.matchEntire(sessionId)?.destructured?.let { (_, peerId) ->
        val originSessionId = peerId.substringBefore("-user-").let { groupId ->
            val isolatedUserId = peerId.substringAfter("-user-", "")
            if (isolatedUserId.isBlank()) {
                "group:$groupId"
            } else {
                "group:$groupId:user:$isolatedUserId"
            }
        }
        return MessageSessionRef(
            platformId = "qq",
            messageType = MessageType.GroupMessage,
            originSessionId = originSessionId,
        )
    }
    return MessageSessionRef(
        platformId = "app",
        messageType = MessageType.OtherMessage,
        originSessionId = sessionId,
    )
}
