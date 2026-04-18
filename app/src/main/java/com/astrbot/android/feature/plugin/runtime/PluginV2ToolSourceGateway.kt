package com.astrbot.android.feature.plugin.runtime

sealed interface PluginV2ToolSourceGatewayResult {
    data class ActiveEntry(
        val entry: PluginV2ToolRegistryEntry,
    ) : PluginV2ToolSourceGatewayResult

    data object SourceUnavailable : PluginV2ToolSourceGatewayResult

    data object ReservedSourceUnavailable : PluginV2ToolSourceGatewayResult
}

internal fun isPhase5ActiveToolSourceKind(sourceKind: PluginToolSourceKind): Boolean {
    return !sourceKind.reservedOnly
}

/**
 * Checks whether a [PluginToolSourceKind] has been activated by a registered
 * [FutureToolSourceProvider]. Source kinds in [activeFutureSourceKinds] pass
 * through as active; all other reserved kinds remain blocked.
 */
private fun futureAwareToolSourceResolution(
    entry: PluginV2ToolRegistryEntry,
    activeFutureSourceKinds: Set<PluginToolSourceKind>,
): PluginV2ToolSourceGatewayResult {
    if (isPhase5ActiveToolSourceKind(entry.sourceKind)) {
        return PluginV2ToolSourceGatewayResult.ActiveEntry(entry)
    }
    return if (entry.sourceKind in activeFutureSourceKinds) {
        PluginV2ToolSourceGatewayResult.ActiveEntry(entry)
    } else {
        PluginV2ToolSourceGatewayResult.ReservedSourceUnavailable
    }
}

class PluginV2ToolSourceGateway(
    private val activeFutureSourceKinds: Set<PluginToolSourceKind> = emptySet(),
    private val resolver: (PluginV2ToolRegistryEntry) -> PluginV2ToolSourceGatewayResult = { entry ->
        futureAwareToolSourceResolution(entry, activeFutureSourceKinds)
    },
) {
    fun resolve(entry: PluginV2ToolRegistryEntry): PluginV2ToolSourceGatewayResult {
        return resolver(entry)
    }
}
