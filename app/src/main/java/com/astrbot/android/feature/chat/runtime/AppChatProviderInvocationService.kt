package com.astrbot.android.feature.chat.runtime

import com.astrbot.android.core.runtime.context.ResolvedRuntimeContext
import com.astrbot.android.core.runtime.llm.LlmToolDefinition
import com.astrbot.android.di.ChatViewModelDependencies
import com.astrbot.android.feature.plugin.runtime.MessageConverters.toConversationMessages
import com.astrbot.android.feature.plugin.runtime.PluginLlmResponse
import com.astrbot.android.feature.plugin.runtime.PluginLlmToolCall
import com.astrbot.android.feature.plugin.runtime.PluginLlmToolCallDelta
import com.astrbot.android.feature.plugin.runtime.PluginProviderRequest
import com.astrbot.android.feature.plugin.runtime.PluginV2ProviderInvocationResult
import com.astrbot.android.feature.plugin.runtime.PluginV2ProviderStreamChunk
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.plugin.PluginV2StreamingMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

internal class AppChatProviderInvocationService(
    private val chatDependencies: ChatViewModelDependencies,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO,
) {
    suspend fun invokeProvider(
        request: PluginProviderRequest,
        mode: PluginV2StreamingMode,
        config: ConfigProfile?,
        ctx: ResolvedRuntimeContext,
    ): PluginV2ProviderInvocationResult {
        val resolvedProvider = ctx.availableProviders.firstOrNull { profile ->
            profile.id == request.selectedProviderId &&
                profile.enabled &&
                ProviderCapability.CHAT in profile.capabilities
        } ?: error("Selected provider is unavailable: ${request.selectedProviderId}")

        val messages = request.messages.toConversationMessages(request.requestId)
        val chatTools = request.tools.map { def ->
            LlmToolDefinition(
                name = def.name,
                description = def.description,
                parametersJson = org.json.JSONObject(def.inputSchema.filterValues { it != null } as Map<*, *>)
                    .toString(),
            )
        }

        return if (mode != PluginV2StreamingMode.NATIVE_STREAM || !request.streamingEnabled || config == null) {
            val result = withContext(ioDispatcher) {
                chatDependencies.sendConfiguredChatWithTools(
                    provider = resolvedProvider,
                    messages = messages,
                    systemPrompt = request.systemPrompt,
                    config = config,
                    availableProviders = ctx.availableProviders,
                    tools = chatTools,
                )
            }
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
            val result = withContext(ioDispatcher) {
                chatDependencies.sendConfiguredChatStreamWithTools(
                    provider = resolvedProvider,
                    messages = messages,
                    systemPrompt = request.systemPrompt,
                    config = config,
                    availableProviders = ctx.availableProviders,
                    tools = chatTools,
                    onDelta = { delta ->
                        if (delta.isNotBlank()) {
                            chunks += PluginV2ProviderStreamChunk(deltaText = delta)
                        }
                    },
                    onToolCallDelta = { _, _, _ -> },
                )
            }
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
            val finishReason = if (result.toolCalls.isNotEmpty()) "tool_calls" else "stop"
            chunks += PluginV2ProviderStreamChunk(
                deltaText = "",
                isCompletion = true,
                finishReason = finishReason,
            )
            if (result.text.isNotBlank() && chunks.size == 1) {
                chunks.add(0, PluginV2ProviderStreamChunk(deltaText = result.text))
            }
            PluginV2ProviderInvocationResult.Streaming(events = chunks.toList())
        }
    }

    private fun parseToolCallArguments(json: String): Map<String, Any?> {
        return try {
            val obj = org.json.JSONObject(json)
            obj.keys().asSequence().associateWith { key -> obj.opt(key) }
        } catch (_: Exception) {
            emptyMap()
        }
    }
}

