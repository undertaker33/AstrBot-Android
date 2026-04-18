package com.astrbot.android.model.chat

data class MessageSessionRef(
    val platformId: String,
    val messageType: MessageType,
    val originSessionId: String,
) {
    val unifiedOrigin: String
        get() = "$platformId:${messageType.wireValue}:$originSessionId"
}
