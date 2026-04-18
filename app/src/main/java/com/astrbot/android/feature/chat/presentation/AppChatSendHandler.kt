package com.astrbot.android.feature.chat.presentation

import com.astrbot.android.feature.chat.domain.SendAppMessageEvent
import com.astrbot.android.feature.chat.domain.SendAppMessageRuntimeContext
import com.astrbot.android.feature.chat.domain.SendAppMessageRuntimeDecision
import com.astrbot.android.feature.chat.domain.SendAppMessageUseCase
import com.astrbot.android.model.chat.ConversationAttachment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AppChatSendHandler(
    private val sendAppMessage: SendAppMessageUseCase,
) {
    fun send(request: AppChatSendRequest): Flow<AppChatSendEvent> {
        return sendAppMessage.send(
            sessionId = request.sessionId,
            text = request.text,
            attachments = request.attachments,
            beforeRuntime = { context ->
                when (val decision = request.beforeRuntime(context.toPresentation())) {
                    AppChatRuntimeDecision.Continue -> SendAppMessageRuntimeDecision.Continue
                    is AppChatRuntimeDecision.Skip -> SendAppMessageRuntimeDecision.Skip(decision.reason)
                }
            },
            failureMessage = request.failureMessage,
        ).map { event -> event.toPresentation() }
    }

    private fun SendAppMessageRuntimeContext.toPresentation(): AppChatRuntimeContext {
        return AppChatRuntimeContext(
            sessionId = sessionId,
            userMessageId = userMessageId,
        )
    }

    private fun SendAppMessageEvent.toPresentation(): AppChatSendEvent {
        return when (this) {
            is SendAppMessageEvent.Rejected -> AppChatSendEvent.Rejected(reason)
            is SendAppMessageEvent.RuntimeSkipped -> AppChatSendEvent.RuntimeSkipped(userMessageId, reason)
            is SendAppMessageEvent.Started -> AppChatSendEvent.Started(userMessageId, assistantMessageId)
            is SendAppMessageEvent.AssistantUpdated -> AppChatSendEvent.AssistantUpdated(assistantMessageId, content)
            is SendAppMessageEvent.AttachmentsUpdated -> AppChatSendEvent.AttachmentsUpdated(assistantMessageId)
            is SendAppMessageEvent.Completed -> AppChatSendEvent.Completed(assistantMessageId, content)
            is SendAppMessageEvent.Failed -> AppChatSendEvent.Failed(assistantMessageId, message, cause)
        }
    }
}

data class AppChatSendRequest(
    val sessionId: String,
    val text: String,
    val attachments: List<ConversationAttachment> = emptyList(),
    val beforeRuntime: suspend (AppChatRuntimeContext) -> AppChatRuntimeDecision = {
        AppChatRuntimeDecision.Continue
    },
    val failureMessage: (String) -> String = { it },
)

data class AppChatRuntimeContext(
    val sessionId: String,
    val userMessageId: String,
)

sealed interface AppChatRuntimeDecision {
    object Continue : AppChatRuntimeDecision
    data class Skip(val reason: String) : AppChatRuntimeDecision
}

sealed interface AppChatSendEvent {
    data class Rejected(val reason: String) : AppChatSendEvent
    data class RuntimeSkipped(val userMessageId: String, val reason: String) : AppChatSendEvent
    data class Started(val userMessageId: String, val assistantMessageId: String) : AppChatSendEvent
    data class AssistantUpdated(val assistantMessageId: String, val content: String) : AppChatSendEvent
    data class AttachmentsUpdated(val assistantMessageId: String) : AppChatSendEvent
    data class Completed(val assistantMessageId: String, val content: String) : AppChatSendEvent
    data class Failed(val assistantMessageId: String, val message: String, val cause: Throwable?) : AppChatSendEvent
}
