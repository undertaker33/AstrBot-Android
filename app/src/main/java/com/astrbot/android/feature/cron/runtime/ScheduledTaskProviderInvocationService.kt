package com.astrbot.android.feature.cron.runtime

import com.astrbot.android.core.runtime.context.ResolvedRuntimeContext
import com.astrbot.android.core.runtime.llm.LlmClientPort
import com.astrbot.android.core.runtime.llm.LlmInvocationRequest
import com.astrbot.android.core.runtime.llm.LlmInvocationResult
import com.astrbot.android.core.runtime.llm.LlmStreamEvent
import com.astrbot.android.core.runtime.llm.LlmToolDefinition
import com.astrbot.android.feature.plugin.runtime.MessageConverters.toConversationMessages
import com.astrbot.android.feature.plugin.runtime.PluginLlmResponse
import com.astrbot.android.feature.plugin.runtime.PluginLlmToolCall
import com.astrbot.android.feature.plugin.runtime.PluginLlmToolCallDelta
import com.astrbot.android.feature.plugin.runtime.PluginProviderRequest
import com.astrbot.android.feature.plugin.runtime.PluginV2ProviderInvocationResult
import com.astrbot.android.feature.plugin.runtime.PluginV2ProviderStreamChunk
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.plugin.PluginV2StreamingMode
import kotlinx.coroutines.flow.collect
import org.json.JSONObject

internal class ScheduledTaskProviderInvocationService(
    private val llmClient: LlmClientPort,
) {
    suspend fun invokeProvider(
        request: PluginProviderRequest,
        mode: PluginV2StreamingMode,
        ctx: ResolvedRuntimeContext,
    ): PluginV2ProviderInvocationResult {
        val resolvedProvider = ctx.availableProviders.firstOrNull { profile ->
            profile.id == request.selectedProviderId &&
                profile.enabled &&
                ProviderCapability.CHAT in profile.capabilities
        } ?: error("Selected provider is unavailable: ${request.selectedProviderId}")

        val messages = request.messages.toConversationMessages(request.requestId)
        val llmTools = request.tools.map { def ->
            LlmToolDefinition(
                name = def.name,
                description = def.description,
                parametersJson = JSONObject(
                    def.inputSchema.filterValues { it != null } as Map<*, *>,
                ).toString(),
            )
        }
        val llmRequest = LlmInvocationRequest(
            provider = resolvedProvider,
            messages = messages,
            systemPrompt = request.systemPrompt,
            config = ctx.config,
            availableProviders = ctx.availableProviders,
            tools = llmTools,
        )

        return if (mode != PluginV2StreamingMode.NATIVE_STREAM || !request.streamingEnabled) {
            val result = llmClient.sendWithTools(llmRequest)
            PluginV2ProviderInvocationResult.NonStreaming(
                response = PluginLlmResponse(
                    requestId = request.requestId,
                    providerId = resolvedProvider.id,
                    modelId = request.selectedModelId.ifBlank { resolvedProvider.model },
                    text = result.text,
                    toolCalls = result.toolCalls.map { tc ->
                        PluginLlmToolCall(
                            toolCallId = tc.id,
                            toolName = tc.name,
                            arguments = parseToolCallArguments(tc.arguments),
                        )
                    },
                ),
            )
        } else {
            val chunks = mutableListOf<PluginV2ProviderStreamChunk>()
            var completedResult: LlmInvocationResult? = null
            llmClient.streamWithTools(llmRequest).collect { event ->
                when (event) {
                    is LlmStreamEvent.TextDelta -> {
                        chunks += PluginV2ProviderStreamChunk(deltaText = event.text)
                    }
                    is LlmStreamEvent.ToolCallDelta -> {
                        // Collect tool call deltas; finalization happens on Completed.
                    }
                    is LlmStreamEvent.Completed -> {
                        completedResult = event.result
                    }
                    is LlmStreamEvent.Failed -> {
                        throw event.throwable
                    }
                }
            }
            val result = completedResult ?: LlmInvocationResult(text = "")
            val finalizedToolDeltas = result.toolCalls.mapIndexedNotNull { index, toolCall ->
                val normalizedName = toolCall.name.trim()
                if (normalizedName.isBlank()) {
                    null
                } else {
                    PluginLlmToolCallDelta(
                        index = index,
                        toolCallId = toolCall.id,
                        toolName = normalizedName,
                        arguments = parseToolCallArguments(toolCall.arguments),
                    )
                }
            }
            if (finalizedToolDeltas.isNotEmpty()) {
                chunks += PluginV2ProviderStreamChunk(toolCallDeltas = finalizedToolDeltas)
            }
            chunks += PluginV2ProviderStreamChunk(
                isCompletion = true,
                finishReason = if (result.toolCalls.isNotEmpty()) "tool_calls" else "stop",
            )
            if (result.text.isNotBlank() && chunks.size == 1) {
                chunks.add(0, PluginV2ProviderStreamChunk(deltaText = result.text))
            }
            PluginV2ProviderInvocationResult.Streaming(events = chunks)
        }
    }

    private fun parseToolCallArguments(json: String): Map<String, Any?> {
        return try {
            val obj = JSONObject(json)
            obj.keys().asSequence().associateWith { key -> obj.opt(key) }
        } catch (_: Exception) {
            emptyMap()
        }
    }
}

