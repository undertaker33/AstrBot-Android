@file:Suppress("DEPRECATION")

package com.astrbot.android.runtime.llm

import com.astrbot.android.core.runtime.llm.ChatCompletionServiceLlmClient
import com.astrbot.android.core.runtime.llm.LlmInvocationResult
import com.astrbot.android.core.runtime.llm.LlmToolCall
import com.astrbot.android.core.runtime.llm.LlmToolDefinition
import org.json.JSONObject

@Deprecated("Compat-only seam. Production Hilt binding uses ChatCompletionServiceLlmClient.")
internal class LegacyChatCompletionServiceAdapter : ChatCompletionServiceLlmClient() {
    companion object {
        internal fun LlmToolDefinition.toLegacyToolDefinition(): com.astrbot.android.core.runtime.llm.ChatCompletionService.ChatToolDefinition {
            return toChatToolDefinition()
        }

        internal fun com.astrbot.android.core.runtime.llm.ChatCompletionService.ChatCompletionResult.toLlmInvocationResult(): LlmInvocationResult {
            return toPortInvocationResult()
        }
    }
}

private fun LlmToolDefinition.toChatToolDefinition(): com.astrbot.android.core.runtime.llm.ChatCompletionService.ChatToolDefinition {
    val safeParameters = parametersJson.ifBlank { "{}" }
    return com.astrbot.android.core.runtime.llm.ChatCompletionService.ChatToolDefinition(
        name = name,
        description = description,
        parameters = JSONObject(safeParameters),
    )
}

private fun com.astrbot.android.core.runtime.llm.ChatCompletionService.ChatCompletionResult.toPortInvocationResult(): LlmInvocationResult {
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
