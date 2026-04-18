package com.astrbot.android.model.chat

data class ConversationMessage(
    val id: String,
    val role: String,
    val content: String,
    val timestamp: Long,
    val attachments: List<ConversationAttachment> = emptyList(),
    val toolCallId: String = "",
    val assistantToolCalls: List<ConversationToolCall> = emptyList(),
)

data class ConversationToolCall(
    val id: String,
    val name: String,
    val arguments: String,
)
