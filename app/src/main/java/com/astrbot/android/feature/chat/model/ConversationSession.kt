package com.astrbot.android.model.chat

data class ConversationSession(
    val id: String,
    val title: String,
    val botId: String,
    val personaId: String,
    val providerId: String,
    val platformId: String = defaultSessionRefFor(id).platformId,
    val messageType: MessageType = defaultSessionRefFor(id).messageType,
    val originSessionId: String = defaultSessionRefFor(id).originSessionId,
    val maxContextMessages: Int,
    val sessionSttEnabled: Boolean = true,
    val sessionTtsEnabled: Boolean = true,
    val pinned: Boolean = false,
    val titleCustomized: Boolean = false,
    val messages: List<ConversationMessage>,
)
