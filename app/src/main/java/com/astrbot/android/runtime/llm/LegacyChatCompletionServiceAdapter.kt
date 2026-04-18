package com.astrbot.android.runtime.llm

import com.astrbot.android.core.runtime.llm.LlmClientPort
import com.astrbot.android.core.runtime.llm.LlmInvocationRequest
import com.astrbot.android.core.runtime.llm.LlmInvocationResult
import com.astrbot.android.core.runtime.llm.LlmStreamEvent
import com.astrbot.android.core.runtime.llm.LlmToolCall
import com.astrbot.android.core.runtime.llm.LlmToolDefinition
import com.astrbot.android.data.ChatCompletionService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONObject

internal class LegacyChatCompletionServiceAdapter : LlmClientPort {

    override suspend fun sendWithTools(request: LlmInvocationRequest): LlmInvocationResult {
        val chatTools = request.tools.map { it.toLegacyToolDefinition() }
        val result = ChatCompletionService.sendConfiguredChatWithTools(
            provider = request.provider,
            messages = request.messages,
            systemPrompt = request.systemPrompt,
            config = request.config,
            availableProviders = request.availableProviders,
            tools = chatTools,
        )
        return result.toLlmInvocationResult()
    }

    override fun streamWithTools(request: LlmInvocationRequest): Flow<LlmStreamEvent> = flow {
        val chatTools = request.tools.map { it.toLegacyToolDefinition() }
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
            emit(LlmStreamEvent.Completed(result.toLlmInvocationResult()))
        } catch (e: Throwable) {
            emit(LlmStreamEvent.Failed(e))
        }
    }

    companion object {
        internal fun LlmToolDefinition.toLegacyToolDefinition(): ChatCompletionService.ChatToolDefinition {
            val safeParameters = parametersJson.ifBlank { "{}" }
            return ChatCompletionService.ChatToolDefinition(
                name = name,
                description = description,
                parameters = JSONObject(safeParameters),
            )
        }

        internal fun ChatCompletionService.ChatCompletionResult.toLlmInvocationResult(): LlmInvocationResult {
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
    }
}
