package com.astrbot.android.runtime.plugin

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

private fun defaultPluginV2ToolSourceResolution(
    entry: PluginV2ToolRegistryEntry,
): PluginV2ToolSourceGatewayResult {
    return if (isPhase5ActiveToolSourceKind(entry.sourceKind)) {
        PluginV2ToolSourceGatewayResult.ActiveEntry(entry)
    } else {
        // Reserved for future source integration in Phase 6+
        PluginV2ToolSourceGatewayResult.ReservedSourceUnavailable
    }
}

class PluginV2ToolSourceGateway(
    private val resolver: (PluginV2ToolRegistryEntry) -> PluginV2ToolSourceGatewayResult = ::defaultPluginV2ToolSourceResolution,
) {
    fun resolve(entry: PluginV2ToolRegistryEntry): PluginV2ToolSourceGatewayResult {
        return resolver(entry)
    }
}
