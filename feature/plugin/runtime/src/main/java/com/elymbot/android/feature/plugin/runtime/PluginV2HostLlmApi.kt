package com.elymbot.android.feature.plugin.runtime

import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class PluginV2HostLlmMessage(
    val role: String,
    val text: String,
)

data class PluginV2HostLlmRequest(
    val providerId: String = "",
    val modelId: String = "",
    val messages: List<PluginV2HostLlmMessage> = emptyList(),
    val systemPrompt: String? = null,
    val temperature: Double? = null,
    val topP: Double? = null,
    val maxTokens: Int? = null,
    val tools: List<PluginProviderToolDefinition> = emptyList(),
)

data class PluginV2HostLlmResult(
    val text: String,
    val finishReason: String?,
    val providerId: String,
    val modelId: String,
    val usage: PluginLlmUsageSnapshot?,
    val toolCalls: List<PluginLlmToolCall>,
)

data class PluginV2HostLlmPortRequest(
    val pluginId: String,
    val requestId: String,
    val conversationId: String,
    val providerId: String,
    val modelId: String,
    val messages: List<PluginV2HostLlmMessage>,
    val systemPrompt: String?,
    val temperature: Double?,
    val topP: Double?,
    val maxTokens: Int?,
    val tools: List<PluginProviderToolDefinition>,
    val bypassPluginLlmHooks: Boolean = true,
)

data class PluginV2HostLlmPortResult(
    val text: String,
    val finishReason: String?,
    val providerId: String,
    val modelId: String,
    val usage: PluginLlmUsageSnapshot? = null,
    val toolCalls: List<PluginLlmToolCall> = emptyList(),
)

fun interface PluginV2HostLlmPort {
    suspend fun generate(request: PluginV2HostLlmPortRequest): PluginV2HostLlmPortResult
}

class PluginV2HostLlmApi(
    private val facade: PluginV2HostApiFacade,
    private val providerReader: PluginV2ProviderReadPort,
    private val llmPort: PluginV2HostLlmPort,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val maxConcurrentCallsPerPlugin: Int = DEFAULT_MAX_CONCURRENT_CALLS_PER_PLUGIN,
) {
    private val pluginSemaphores = ConcurrentHashMap<String, Semaphore>()

    suspend fun callLlm(
        context: PluginV2HostApiRequestContext,
        request: PluginV2HostLlmRequest,
    ): PluginV2HostApiResult = callLlmWithApiName(
        context = context,
        request = request,
        api = HOST_API_CALL_LLM,
    )

    suspend fun generate(
        context: PluginV2HostApiRequestContext,
        request: PluginV2HostLlmRequest,
    ): PluginV2HostApiResult = callLlmWithApiName(
        context = context,
        request = request,
        api = HOST_API_LLM_GENERATE,
    )

    private suspend fun callLlmWithApiName(
        context: PluginV2HostApiRequestContext,
        request: PluginV2HostLlmRequest,
        api: String,
    ): PluginV2HostApiResult {
        return facade.call(
            context = context,
            api = api,
            permissionId = PluginV2HostApiPermissions.CALL_MODEL,
            timeoutMs = timeoutMs,
        ) {
            semaphoreFor(context.pluginId).withPermit {
                generateInternal(context = context, request = request)
            }
        }
    }

    private suspend fun generateInternal(
        context: PluginV2HostApiRequestContext,
        request: PluginV2HostLlmRequest,
    ): PluginV2HostLlmResult {
        val providerId = request.providerId.trim()
        val modelId = request.modelId.trim()
        val providers = providerReader.providers()
        val provider = providers.firstOrNull { it.providerId.trim() == providerId && it.enabled }
            ?: throw PluginV2HostApiException(
                PluginV2HostApiError(
                    code = PROVIDER_NOT_FOUND,
                    message = "Provider was not found or is disabled.",
                    details = mapOf("providerId" to providerId),
                ),
            )
        val model = provider.models.firstOrNull { it.modelId.trim() == modelId }
            ?: throw PluginV2HostApiException(
                PluginV2HostApiError(
                    code = MODEL_NOT_FOUND,
                    message = "Model was not found for the selected provider.",
                    details = mapOf("providerId" to providerId, "modelId" to modelId),
                ),
            )
        val messages = sanitizeMessages(request.messages)
        val maxTokens = sanitizeMaxTokens(request.maxTokens)
        val result = try {
            llmPort.generate(
                PluginV2HostLlmPortRequest(
                    pluginId = context.pluginId,
                    requestId = context.requestId,
                    conversationId = context.conversationId,
                    providerId = provider.providerId.trim(),
                    modelId = model.modelId.trim(),
                    messages = messages,
                    systemPrompt = request.systemPrompt?.trim()?.takeIf(String::isNotBlank),
                    temperature = sanitizeTemperature(request.temperature),
                    topP = sanitizeTopP(request.topP),
                    maxTokens = maxTokens,
                    tools = request.tools,
                    bypassPluginLlmHooks = true,
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: PluginV2HostApiException) {
            throw error
        } catch (_: Throwable) {
            throw PluginV2HostApiException(
                PluginV2HostApiError(
                    code = HOST_LLM_FAILED,
                    message = "Host LLM call failed.",
                    details = mapOf(
                        "providerId" to provider.providerId.trim(),
                        "modelId" to model.modelId.trim(),
                    ),
                ),
            )
        }
        return PluginV2HostLlmResult(
            text = result.text,
            finishReason = result.finishReason?.trim()?.takeIf(String::isNotBlank),
            providerId = result.providerId.trim(),
            modelId = result.modelId.trim(),
            usage = result.usage,
            toolCalls = result.toolCalls,
        )
    }

    private fun semaphoreFor(pluginId: String): Semaphore {
        return pluginSemaphores.getOrPut(pluginId) {
            Semaphore(maxConcurrentCallsPerPlugin.coerceAtLeast(1))
        }
    }

    private fun sanitizeMessages(messages: List<PluginV2HostLlmMessage>): List<PluginV2HostLlmMessage> {
        if (messages.isEmpty()) {
            throw invalidPayload("At least one LLM message is required.")
        }
        return messages.map { message ->
            val role = message.role.trim().lowercase(Locale.US)
            if (role !in WRITABLE_MESSAGE_ROLES) {
                throw PluginV2HostApiException(
                    PluginV2HostApiError(
                        code = UNSUPPORTED_MESSAGE_ROLE,
                        message = "Only system, user, and assistant message roles are supported.",
                        details = mapOf("role" to role),
                    ),
                )
            }
            val text = message.text.trim()
            if (text.isBlank()) {
                throw invalidPayload("LLM message text must not be blank.")
            }
            PluginV2HostLlmMessage(role = role, text = text)
        }
    }

    private fun sanitizeTemperature(value: Double?): Double? {
        if (value == null) return null
        if (value.isNaN() || value !in 0.0..2.0) {
            throw invalidPayload("temperature must be between 0 and 2.")
        }
        return value
    }

    private fun sanitizeTopP(value: Double?): Double? {
        if (value == null) return null
        if (value.isNaN() || value !in 0.0..1.0) {
            throw invalidPayload("topP must be between 0 and 1.")
        }
        return value
    }

    private fun sanitizeMaxTokens(value: Int?): Int? {
        if (value == null) return null
        if (value !in 1..MAX_TOKENS_LIMIT) {
            throw invalidPayload("maxTokens must be between 1 and $MAX_TOKENS_LIMIT.")
        }
        return value
    }

    private fun invalidPayload(message: String): PluginV2HostApiException {
        return PluginV2HostApiException(
            PluginV2HostApiError(
                code = PluginV2HostApiErrorCodes.INVALID_PAYLOAD,
                message = message,
            ),
        )
    }

    companion object {
        const val HOST_API_CALL_LLM = "hostApi.callLlm"
        const val HOST_API_LLM_GENERATE = "hostApi.llm.generate"
        const val PROVIDER_NOT_FOUND = "provider_not_found"
        const val MODEL_NOT_FOUND = "model_not_found"
        const val UNSUPPORTED_MESSAGE_ROLE = "unsupported_message_role"
        const val HOST_LLM_FAILED = "host_llm_failed"
        const val DEFAULT_TIMEOUT_MS = 30_000L
        const val DEFAULT_MAX_CONCURRENT_CALLS_PER_PLUGIN = 2
        const val MAX_TOKENS_LIMIT = 32_768

        private val WRITABLE_MESSAGE_ROLES = setOf("system", "user", "assistant")
    }
}
