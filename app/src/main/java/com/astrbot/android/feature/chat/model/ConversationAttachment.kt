package com.astrbot.android.model.chat

data class ConversationAttachment(
    val id: String,
    val type: String = "image",
    val mimeType: String = "image/jpeg",
    val fileName: String = "",
    val base64Data: String = "",
    val remoteUrl: String = "",
)
