package com.elymbot.android.feature.plugin.runtime.toolsource
import com.elymbot.android.feature.plugin.runtime.PluginLlmUsageSnapshot
import com.elymbot.android.feature.plugin.runtime.PluginToolDescriptor
import com.elymbot.android.feature.plugin.runtime.PluginToolResult
import com.elymbot.android.feature.plugin.runtime.PluginToolResultStatus
import com.elymbot.android.feature.plugin.runtime.PluginToolSourceKind
import com.elymbot.android.feature.plugin.runtime.PluginToolVisibility
import com.elymbot.android.feature.plugin.runtime.PluginV2ContextCompressApi
import com.elymbot.android.feature.plugin.runtime.PluginV2ContextCompressRequest
import com.elymbot.android.feature.plugin.runtime.PluginV2ContextCompressResult
import com.elymbot.android.feature.plugin.runtime.PluginV2HostApiRequestContext
import com.elymbot.android.feature.plugin.runtime.PluginV2HostApiResult
import com.elymbot.android.model.plugin.PluginPermissionGrant
import com.elymbot.android.model.plugin.PluginRiskLevel
import javax.inject.Inject

/**
 * Context strategy tool source provider.
 *
 * When the active config profile uses the `llm_compress` context strategy,
 * this provider exports a host-internal tool that the LLM pipeline can invoke
 * to compress conversation context when the turn limit is reached.
 */
class ContextStrategyToolSourceProvider @Inject constructor(
    override val contextResolver: FutureToolSourceContextResolver,
    private val contextCompressor: PluginV2ContextCompressApi,
) : FutureToolSourceProvider {
    override val sourceKind: PluginToolSourceKind = PluginToolSourceKind.CONTEXT_STRATEGY

    override suspend fun listBindings(
        context: ToolSourceRegistryIngestContext,
    ): List<ToolSourceDescriptorBinding> {
        if (context.toolSourceContext.contextLimitStrategy != "llm_compress") return emptyList()

        return listOf(buildCompressContextBinding())
    }

    override suspend fun availabilityOf(
        identity: ToolSourceIdentity,
        context: ToolSourceAvailabilityContext,
    ): ToolSourceAvailability {
        return if (context.toolSourceContext.contextLimitStrategy == "llm_compress") {
            ToolSourceAvailability(
                providerReachable = true,
                permissionGranted = true,
                capabilityAllowed = true,
            )
        } else {
            ToolSourceAvailability(
                providerReachable = false,
                permissionGranted = true,
                capabilityAllowed = false,
                detailCode = "context_strategy_not_llm_compress",
                detailMessage = "LLM compress context strategy is not active for this config profile.",
            )
        }
    }

    override suspend fun invoke(
        request: ToolSourceInvokeRequest,
    ): ToolSourceInvokeResult {
        val toolSourceContext = request.toolSourceContext ?: request.configProfileId
            ?.takeIf(String::isNotBlank)
            ?.let(contextResolver::resolveForConfig)
        if (toolSourceContext?.contextLimitStrategy != "llm_compress") {
            return errorResult(
                request = request,
                errorCode = "context_strategy_not_llm_compress",
                text = "LLM compress context strategy is not active for this config profile.",
            )
        }
        val result = contextCompressor.compress(
            context = PluginV2HostApiRequestContext(
                pluginId = request.identity.ownerId.ifBlank { "ctx.compress" },
                requestId = request.args.requestId,
                conversationId = toolSourceContext.conversationId,
                platformAdapterType = toolSourceContext.platform.wireValue,
                manifestPermissionIds = setOf("context_compress"),
                permissionSnapshot = listOf(
                    PluginPermissionGrant(
                        permissionId = "context_compress",
                        title = "Compress context",
                        granted = true,
                        riskLevel = PluginRiskLevel.MEDIUM,
                    ),
                ),
                triggerPermissionWhitelist = setOf("context_compress"),
            ),
            request = PluginV2ContextCompressRequest(
                conversationId = stringPayload(request, "conversationId").ifBlank { toolSourceContext.conversationId },
                providerId = stringPayload(request, "providerId"),
                modelId = stringPayload(request, "modelId"),
                maxTokens = intPayload(request, "maxTokens", PluginV2ContextCompressApi.DEFAULT_MAX_TOKENS),
                limit = intPayload(request, "limit", PluginV2ContextCompressApi.DEFAULT_HISTORY_LIMIT),
                targetLanguage = stringPayload(request, "targetLanguage"),
                outputLength = stringPayload(request, "outputLength"),
            ),
        )
        return when (result) {
            is PluginV2HostApiResult.Success -> {
                val value = result.value as PluginV2ContextCompressResult
                ToolSourceInvokeResult(
                    result = PluginToolResult(
                        toolCallId = request.args.toolCallId,
                        requestId = request.args.requestId,
                        toolId = request.args.toolId,
                        status = PluginToolResultStatus.SUCCESS,
                        text = value.summary,
                        structuredContent = value.toStructuredMap(),
                    ),
                )
            }

            is PluginV2HostApiResult.Failure -> errorResult(
                request = request,
                errorCode = result.error.code,
                text = result.error.message,
                structuredContent = mapOf(
                    "code" to result.error.code,
                    "message" to result.error.message,
                    "details" to result.error.details,
                ),
            )
        }
    }

    private fun stringPayload(
        request: ToolSourceInvokeRequest,
        key: String,
    ): String = request.args.payload[key]?.toString()?.trim().orEmpty()

    private fun intPayload(
        request: ToolSourceInvokeRequest,
        key: String,
        defaultValue: Int,
    ): Int {
        return when (val value = request.args.payload[key]) {
            null -> defaultValue
            is Number -> value.toInt()
            is String -> value.trim().toIntOrNull() ?: defaultValue
            else -> defaultValue
        }
    }

    private fun PluginV2ContextCompressResult.toStructuredMap(): Map<String, Any?> {
        return mapOf(
            "summary" to summary,
            "sourceMessageCount" to sourceMessageCount,
            "truncated" to truncated,
            "usage" to usage.toStructuredMap(),
        )
    }

    private fun PluginLlmUsageSnapshot?.toStructuredMap(): Map<String, Any?>? {
        if (this == null) return null
        return mapOf(
            "promptTokens" to promptTokens,
            "completionTokens" to completionTokens,
            "totalTokens" to totalTokens,
            "inputCostMicros" to inputCostMicros,
            "outputCostMicros" to outputCostMicros,
            "currencyCode" to normalizedCurrencyCode,
        )
    }

    private fun errorResult(
        request: ToolSourceInvokeRequest,
        errorCode: String,
        text: String,
        structuredContent: Map<String, Any?> = mapOf("code" to errorCode, "message" to text),
    ): ToolSourceInvokeResult {
        return ToolSourceInvokeResult(
            result = PluginToolResult(
                toolCallId = request.args.toolCallId,
                requestId = request.args.requestId,
                toolId = request.args.toolId,
                status = PluginToolResultStatus.ERROR,
                errorCode = errorCode,
                text = text,
                structuredContent = structuredContent,
            ),
        )
    }

    private fun buildCompressContextBinding(): ToolSourceDescriptorBinding {
        val ownerId = "ctx.compress"
        return ToolSourceDescriptorBinding(
            identity = ToolSourceIdentity(
                sourceKind = PluginToolSourceKind.CONTEXT_STRATEGY,
                ownerId = ownerId,
                sourceRef = "compress_context",
                displayName = "Compress Context",
            ),
            descriptor = PluginToolDescriptor(
                pluginId = ownerId,
                name = "compress_context",
                description = "Compress conversation context using the configured LLM compression strategy.",
                visibility = PluginToolVisibility.HOST_INTERNAL,
                sourceKind = PluginToolSourceKind.CONTEXT_STRATEGY,
                inputSchema = mapOf("type" to "object" as Any),
            ),
        )
    }
}

