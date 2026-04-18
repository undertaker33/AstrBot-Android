package com.astrbot.android.feature.plugin.runtime.toolsource

import com.astrbot.android.feature.config.data.FeatureConfigRepository
import com.astrbot.android.feature.plugin.runtime.PluginToolDescriptor
import com.astrbot.android.feature.plugin.runtime.PluginToolResult
import com.astrbot.android.feature.plugin.runtime.PluginToolResultStatus
import com.astrbot.android.feature.plugin.runtime.PluginToolSourceKind
import com.astrbot.android.feature.plugin.runtime.PluginToolVisibility

/**
 * Context strategy tool source provider.
 *
 * When the active config profile uses the `llm_compress` context strategy,
 * this provider exports a host-internal tool that the LLM pipeline can invoke
 * to compress conversation context when the turn limit is reached.
 */
class ContextStrategyToolSourceProvider : FutureToolSourceProvider {
    override val sourceKind: PluginToolSourceKind = PluginToolSourceKind.CONTEXT_STRATEGY

    override suspend fun listBindings(
        context: ToolSourceRegistryIngestContext,
    ): List<ToolSourceDescriptorBinding> {
        val configProfile = FeatureConfigRepository.resolve(context.configProfileId)
        if (configProfile.contextLimitStrategy != "llm_compress") return emptyList()

        return listOf(buildCompressContextBinding())
    }

    override suspend fun availabilityOf(
        identity: ToolSourceIdentity,
        context: ToolSourceAvailabilityContext,
    ): ToolSourceAvailability {
        val configProfile = FeatureConfigRepository.resolve(context.configProfileId)
        return if (configProfile.contextLimitStrategy == "llm_compress") {
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
        // TODO: Phase 6 鈥?invoke the LLM with compression instruction to compress context.
        return ToolSourceInvokeResult(
            result = PluginToolResult(
                toolCallId = request.args.toolCallId,
                requestId = request.args.requestId,
                toolId = request.args.toolId,
                status = PluginToolResultStatus.ERROR,
                errorCode = "context_compress_not_implemented",
                text = "LLM context compression is not yet implemented on Android.",
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

