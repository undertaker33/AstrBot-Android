package com.elymbot.android.feature.plugin.runtime

import kotlin.coroutines.cancellation.CancellationException

data class PluginV2ContextCompressRequest(
    val conversationId: String = "",
    val providerId: String = "",
    val modelId: String = "",
    val maxTokens: Int = 1_200,
    val limit: Int = 50,
    val targetLanguage: String = "",
    val outputLength: String = "",
)

data class PluginV2ContextCompressResult(
    val summary: String,
    val sourceMessageCount: Int,
    val truncated: Boolean,
    val usage: PluginLlmUsageSnapshot?,
)

class PluginV2ContextCompressApi(
    private val facade: PluginV2HostApiFacade,
    private val historyReader: PluginV2ConversationHistoryReadPort,
    private val llmPort: PluginV2HostLlmPort,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {
    suspend fun compress(
        context: PluginV2HostApiRequestContext,
        request: PluginV2ContextCompressRequest,
    ): PluginV2HostApiResult {
        return facade.call(
            context = context,
            api = HOST_API_CONTEXT_COMPRESS,
            permissionId = PluginV2HostApiPermissions.CONTEXT_COMPRESS,
            timeoutMs = timeoutMs,
        ) {
            compressInternal(context = context, request = request)
        }
    }

    private suspend fun compressInternal(
        context: PluginV2HostApiRequestContext,
        request: PluginV2ContextCompressRequest,
    ): PluginV2ContextCompressResult {
        val targetConversationId = resolveTargetConversationId(
            contextConversationId = context.conversationId,
            requestedConversationId = request.conversationId,
        )
        val providerId = request.providerId.trim()
        val modelId = request.modelId.trim()
        if (providerId.isBlank() || modelId.isBlank()) {
            throw PluginV2HostApiException(
                PluginV2HostApiError(
                    code = CONTEXT_COMPRESS_INVALID_REQUEST,
                    message = "Context compression requires providerId and modelId.",
                ),
            )
        }
        val limit = request.limit.coerceIn(1, MAX_HISTORY_LIMIT)
        val sourceMessages = try {
            historyReader.history(
                PluginV2ConversationHistoryPortRequest(
                    pluginId = context.pluginId,
                    requestId = context.requestId,
                    conversationId = targetConversationId,
                    limit = limit,
                    beforeMessageId = "",
                    includeAttachments = false,
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            throw PluginV2HostApiException(
                PluginV2HostApiError(
                    code = CONTEXT_HISTORY_READ_FAILED,
                    message = "Host conversation history read failed for context compression.",
                    details = mapOf("conversationId" to targetConversationId),
                ),
            )
        }
        if (sourceMessages.isEmpty()) {
            throw PluginV2HostApiException(
                PluginV2HostApiError(
                    code = CONTEXT_COMPRESS_INVALID_REQUEST,
                    message = "Context compression requires at least one source message.",
                ),
            )
        }
        val normalizedMaxTokens = request.maxTokens.coerceIn(MIN_MAX_TOKENS, MAX_MAX_TOKENS)
        val llmResult = try {
            llmPort.generate(
                PluginV2HostLlmPortRequest(
                    pluginId = context.pluginId,
                    requestId = context.requestId,
                    conversationId = targetConversationId,
                    providerId = providerId,
                    modelId = modelId,
                    messages = listOf(
                        PluginV2HostLlmMessage(
                            role = "user",
                            text = buildCompressionInput(sourceMessages),
                        ),
                    ),
                    systemPrompt = compressionSystemPrompt(request),
                    temperature = 0.1,
                    topP = 0.9,
                    maxTokens = normalizedMaxTokens,
                    tools = emptyList(),
                    bypassPluginLlmHooks = true,
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            throw PluginV2HostApiException(
                PluginV2HostApiError(
                    code = CONTEXT_COMPRESS_FAILED,
                    message = "Host LLM context compression failed.",
                    details = mapOf(
                        "conversationId" to targetConversationId,
                        "providerId" to providerId,
                        "modelId" to modelId,
                    ),
                ),
            )
        }
        val summary = llmResult.text.trim()
        if (summary.isBlank()) {
            throw PluginV2HostApiException(
                PluginV2HostApiError(
                    code = CONTEXT_COMPRESS_EMPTY_RESULT,
                    message = "Host LLM context compression returned an empty summary.",
                ),
            )
        }
        return PluginV2ContextCompressResult(
            summary = summary,
            sourceMessageCount = sourceMessages.size,
            truncated = sourceMessages.size >= limit,
            usage = llmResult.usage,
        )
    }

    private fun resolveTargetConversationId(
        contextConversationId: String,
        requestedConversationId: String,
    ): String {
        val current = contextConversationId.trim()
        if (current.isBlank()) {
            throw PluginV2HostApiException(
                PluginV2HostApiError(
                    code = MISSING_SESSION_SCOPE,
                    message = "Current conversation scope is required.",
                ),
            )
        }
        val requested = requestedConversationId.trim()
        if (requested.isBlank()) return current
        if (requested != current) {
            throw PluginV2HostApiException(
                PluginV2HostApiError(
                    code = CONVERSATION_SCOPE_VIOLATION,
                    message = "Context compression target must match the current conversation scope.",
                    details = mapOf(
                        "currentConversationId" to current,
                        "requestedConversationId" to requested,
                    ),
                ),
            )
        }
        return current
    }

    private fun buildCompressionInput(
        messages: List<PluginV2ConversationHistoryMessage>,
    ): String {
        return messages.sortedBy { it.timestampEpochMillis }.joinToString("\n") { message ->
            val role = message.role.ifBlank { "unknown" }
            "[$role] ${message.text}"
        }
    }

    private fun compressionSystemPrompt(request: PluginV2ContextCompressRequest): String {
        return buildString {
            append("You are the ElymBot host context compression service. ")
            append("Summarize conversation context into a concise, faithful memory for the next LLM turn. ")
            append("Preserve user intent, decisions, constraints, entities, and unresolved questions. ")
            append("Do not invent facts and do not reveal hidden provider credentials or internal database details.")
            request.targetLanguage.trim().takeIf(String::isNotBlank)?.let { language ->
                append(" Write the summary in ")
                append(language)
                append('.')
            }
            request.outputLength.trim().takeIf(String::isNotBlank)?.let { outputLength ->
                append(" Target output length: ")
                append(outputLength)
                append('.')
            }
        }
    }

    companion object {
        const val HOST_API_CONTEXT_COMPRESS = "hostApi.context.compress"
        const val MISSING_SESSION_SCOPE = "missing_session_scope"
        const val CONVERSATION_SCOPE_VIOLATION = "conversation_scope_violation"
        const val CONTEXT_COMPRESS_INVALID_REQUEST = "context_compress_invalid_request"
        const val CONTEXT_HISTORY_READ_FAILED = "context_history_read_failed"
        const val CONTEXT_COMPRESS_FAILED = "context_compress_failed"
        const val CONTEXT_COMPRESS_EMPTY_RESULT = "context_compress_empty_result"
        const val DEFAULT_TIMEOUT_MS = 30_000L
        const val DEFAULT_MAX_TOKENS = 1_200
        const val DEFAULT_HISTORY_LIMIT = 50
        const val MAX_HISTORY_LIMIT = 100
        const val MIN_MAX_TOKENS = 64
        const val MAX_MAX_TOKENS = 8_192
    }
}
