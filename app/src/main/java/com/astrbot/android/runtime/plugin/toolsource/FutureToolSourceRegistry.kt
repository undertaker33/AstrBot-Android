package com.astrbot.android.runtime.plugin.toolsource

import com.astrbot.android.runtime.plugin.PluginToolDescriptor
import com.astrbot.android.runtime.plugin.PluginToolSourceKind

/**
 * Aggregates all [FutureToolSourceProvider] instances and produces a unified list
 * of [PluginToolDescriptor]s for the centralized tool registry compiler.
 *
 * This is the single gateway that [PluginV2ToolSourceGateway] and the host capability
 * system delegate to when resolving non-PLUGIN_V2/HOST_BUILTIN source kinds.
 */
class FutureToolSourceRegistry(
    private val providers: List<FutureToolSourceProvider> = defaultProviders(),
) {
    private val providersByKind: Map<PluginToolSourceKind, FutureToolSourceProvider> =
        providers.associateBy { it.sourceKind }

    suspend fun collectToolDescriptors(
        configProfileId: String,
    ): List<PluginToolDescriptor> {
        val context = ToolSourceRegistryIngestContext(configProfileId = configProfileId)
        return providers.flatMap { provider ->
            provider.listBindings(context).map { binding -> binding.descriptor }
        }
    }

    suspend fun isAvailable(
        sourceKind: PluginToolSourceKind,
        ownerId: String,
        configProfileId: String,
    ): Boolean {
        val provider = providersByKind[sourceKind] ?: return false
        val identity = ToolSourceIdentity(
            sourceKind = sourceKind,
            ownerId = ownerId,
            sourceRef = "",
            displayName = "",
        )
        val availability = provider.availabilityOf(
            identity = identity,
            context = ToolSourceAvailabilityContext(configProfileId = configProfileId),
        )
        return availability.providerReachable && availability.capabilityAllowed
    }

    suspend fun invoke(
        request: ToolSourceInvokeRequest,
    ): ToolSourceInvokeResult? {
        val provider = providersByKind[request.identity.sourceKind] ?: return null
        return provider.invoke(request)
    }

    fun providerFor(sourceKind: PluginToolSourceKind): FutureToolSourceProvider? {
        return providersByKind[sourceKind]
    }

    companion object {
        fun defaultProviders(): List<FutureToolSourceProvider> = listOf(
            McpToolSourceProvider(),
            SkillToolSourceProvider(),
            ActiveCapabilityToolSourceProvider(),
            ContextStrategyToolSourceProvider(),
            WebSearchToolSourceProvider(),
        )
    }
}
