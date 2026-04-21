@file:Suppress("DEPRECATION")

package com.astrbot.android.core.runtime.llm

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONObject

@Suppress("DEPRECATION")
internal open class ChatCompletionServiceLlmClient(
    context: Context? = null,
) : LlmClientPort {

    init {
        context?.let(ChatCompletionService::initialize)
    }

    override suspend fun sendWithTools(request: LlmInvocationRequest): LlmInvocationResult {
        val chatTools = request.tools.map { it.toChatToolDefinition() }
        val result = ChatCompletionService.sendConfiguredChatWithTools(
            provider = request.provider,
            messages = request.messages,
            systemPrompt = request.systemPrompt,
            config = request.config,
            availableProviders = request.availableProviders,
            tools = chatTools,
        )
        return result.toPortInvocationResult()
    }

    override fun streamWithTools(request: LlmInvocationRequest): Flow<LlmStreamEvent> = flow {
        val chatTools = request.tools.map { it.toChatToolDefinition() }
        try {
            val result = ChatCompletionService.sendConfiguredChatStreamWithTools(
                provider = request.provider,
                messages = request.messages,
                systemPrompt = request.systemPrompt,
                config = request.config,
                availableProviders = request.availableProviders,
                tools = chatTools,
                onDelta = { delta ->
                    if (delta.isNotBlank()) {
                        emit(LlmStreamEvent.TextDelta(delta))
                    }
                },
                onToolCallDelta = { index, name, argumentsFragment ->
                    emit(
                        LlmStreamEvent.ToolCallDelta(
                            index = index,
                            name = name,
                            argumentsFragment = argumentsFragment,
                        ),
                    )
                },
            )
            emit(LlmStreamEvent.Completed(result.toPortInvocationResult()))
        } catch (e: Throwable) {
            emit(LlmStreamEvent.Failed(e))
        }
    }
}

private fun LlmToolDefinition.toChatToolDefinition(): ChatCompletionService.ChatToolDefinition {
    val safeParameters = parametersJson.ifBlank { "{}" }
    return ChatCompletionService.ChatToolDefinition(
        name = name,
        description = description,
        parameters = JSONObject(safeParameters),
    )
}

private fun ChatCompletionService.ChatCompletionResult.toPortInvocationResult(): LlmInvocationResult {
    return LlmInvocationResult(
        text = text,
        toolCalls = toolCalls.map { tc ->
            LlmToolCall(
                id = tc.id,
                name = tc.name,
                arguments = tc.arguments,
            )
        },
        finishReason = if (toolCalls.isNotEmpty()) "tool_calls" else "stop",
    )
}
