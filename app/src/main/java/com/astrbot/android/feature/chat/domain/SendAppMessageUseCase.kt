package com.astrbot.android.feature.chat.domain

import com.astrbot.android.model.chat.ConversationAttachment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class SendAppMessageUseCase(
    private val conversations: ConversationRepositoryPort,
    private val runtime: AppChatRuntimePort,
    private val updatePolicy: ChatMessageUpdatePolicy = ChatMessageUpdatePolicy(),
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    fun send(
        sessionId: String,
        text: String,
        attachments: List<ConversationAttachment> = emptyList(),
        beforeRuntime: suspend (SendAppMessageRuntimeContext) -> SendAppMessageRuntimeDecision = {
            SendAppMessageRuntimeDecision.Continue
        },
        failureMessage: (String) -> String = { it },
    ): Flow<SendAppMessageEvent> = flow {
        val trimmed = text.trim()
        if (trimmed.isEmpty() && attachments.isEmpty()) {
            emit(SendAppMessageEvent.Rejected("Message is empty"))
            return@flow
        }

        val userMessageId = conversations.appendMessage(
            sessionId = sessionId,
            role = "user",
            content = trimmed,
            attachments = attachments,
        )
        val runtimeDecision = beforeRuntime(
            SendAppMessageRuntimeContext(
                sessionId = sessionId,
                userMessageId = userMessageId,
            ),
        )
        if (runtimeDecision is SendAppMessageRuntimeDecision.Skip) {
            emit(SendAppMessageEvent.RuntimeSkipped(userMessageId, runtimeDecision.reason))
            return@flow
        }

        var assistantMessageId: String? = null
        suspend fun ensureAssistantMessage(): String {
            val existing = assistantMessageId
            if (existing != null) {
                return existing
            }
            val created = conversations.appendMessage(
                sessionId = sessionId,
                role = "assistant",
                content = "",
                attachments = emptyList(),
            )
            assistantMessageId = created
            emit(SendAppMessageEvent.Started(userMessageId, created))
            return created
        }

        var lastPublished = ""
        var lastPublishedAt = nowMs()

        runtime.send(
            AppChatRequest(
                sessionId = sessionId,
                userMessageId = userMessageId,
                assistantMessageId = "",
                text = trimmed,
                attachments = attachments,
            ),
        ).collect { event ->
            when (event) {
                is AppChatRuntimeEvent.AssistantDelta -> {
                    val currentAssistantMessageId = ensureAssistantMessage()
                    val now = nowMs()
                    if (updatePolicy.shouldPublish(lastPublished, event.content, now - lastPublishedAt)) {
                        conversations.updateMessage(sessionId, currentAssistantMessageId, content = event.content)
                        lastPublished = event.content
                        lastPublishedAt = now
                        emit(SendAppMessageEvent.AssistantUpdated(currentAssistantMessageId, event.content))
                    }
                }
                is AppChatRuntimeEvent.AssistantFinal -> {
                    val currentAssistantMessageId = ensureAssistantMessage()
                    conversations.updateMessage(sessionId, currentAssistantMessageId, content = event.content)
                    lastPublished = event.content
                    lastPublishedAt = nowMs()
                    emit(SendAppMessageEvent.Completed(currentAssistantMessageId, event.content))
                }
                is AppChatRuntimeEvent.AttachmentUpdate -> {
                    val currentAssistantMessageId = ensureAssistantMessage()
                    conversations.updateMessage(sessionId, currentAssistantMessageId, attachments = event.attachments)
                    emit(SendAppMessageEvent.AttachmentsUpdated(currentAssistantMessageId))
                }
                is AppChatRuntimeEvent.Failure -> {
                    val cause = event.cause
                    if (cause is CancellationException) {
                        throw cause
                    }
                    val currentAssistantMessageId = ensureAssistantMessage()
                    conversations.updateMessage(sessionId, currentAssistantMessageId, content = failureMessage(event.message))
                    emit(SendAppMessageEvent.Failed(currentAssistantMessageId, event.message, cause))
                }
            }
        }
    }
}

data class SendAppMessageRuntimeContext(
    val sessionId: String,
    val userMessageId: String,
)

sealed interface SendAppMessageRuntimeDecision {
    object Continue : SendAppMessageRuntimeDecision
    data class Skip(val reason: String) : SendAppMessageRuntimeDecision
}

sealed interface SendAppMessageEvent {
    data class Rejected(val reason: String) : SendAppMessageEvent
    data class RuntimeSkipped(val userMessageId: String, val reason: String) : SendAppMessageEvent
    data class Started(val userMessageId: String, val assistantMessageId: String) : SendAppMessageEvent
    data class AssistantUpdated(val assistantMessageId: String, val content: String) : SendAppMessageEvent
    data class AttachmentsUpdated(val assistantMessageId: String) : SendAppMessageEvent
    data class Completed(val assistantMessageId: String, val content: String) : SendAppMessageEvent
    data class Failed(val assistantMessageId: String, val message: String, val cause: Throwable?) : SendAppMessageEvent
}
