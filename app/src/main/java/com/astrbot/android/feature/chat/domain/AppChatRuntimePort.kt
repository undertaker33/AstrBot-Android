package com.astrbot.android.feature.chat.domain

import com.astrbot.android.model.chat.ConversationAttachment
import kotlinx.coroutines.flow.Flow

interface AppChatRuntimePort {
    fun send(request: AppChatRequest): Flow<AppChatRuntimeEvent>
}

data class AppChatRequest(
    val sessionId: String,
    val userMessageId: String,
    val assistantMessageId: String,
    val text: String,
    val attachments: List<ConversationAttachment>,
)

sealed interface AppChatRuntimeEvent {
    data class AssistantDelta(val content: String) : AppChatRuntimeEvent
    data class AssistantFinal(val content: String) : AppChatRuntimeEvent
    data class AttachmentUpdate(val attachments: List<ConversationAttachment>) : AppChatRuntimeEvent
    data class Failure(val message: String, val cause: Throwable? = null) : AppChatRuntimeEvent
}
