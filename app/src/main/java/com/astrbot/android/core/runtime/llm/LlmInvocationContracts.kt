package com.astrbot.android.core.runtime.llm

import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.chat.ConversationMessage

data class LlmToolDefinition(
    val name: String,
    val description: String,
    val parametersJson: String,
)

data class LlmToolCall(
    val id: String? = null,
    val name: String,
    val arguments: String,
)

data class LlmInvocationRequest(
    val provider: ProviderProfile,
    val messages: List<ConversationMessage>,
    val systemPrompt: String? = null,
    val config: ConfigProfile? = null,
    val availableProviders: List<ProviderProfile> = emptyList(),
    val tools: List<LlmToolDefinition> = emptyList(),
)

data class LlmInvocationResult(
    val text: String,
    val toolCalls: List<LlmToolCall> = emptyList(),
    val finishReason: String? = null,
)

sealed interface LlmStreamEvent {
    data class TextDelta(val text: String) : LlmStreamEvent
    data class ToolCallDelta(
        val index: Int,
        val name: String?,
        val argumentsFragment: String,
    ) : LlmStreamEvent
    data class Completed(val result: LlmInvocationResult) : LlmStreamEvent
    data class Failed(val throwable: Throwable) : LlmStreamEvent
}
