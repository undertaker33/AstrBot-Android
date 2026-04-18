package com.astrbot.android.feature.plugin.runtime

import com.astrbot.android.feature.plugin.domain.PluginRuntimePort
import com.astrbot.android.runtime.plugin.PluginRuntimeDispatcher

class PluginRuntimeFacade(
    dispatcher: PluginRuntimeDispatcher,
    private val pluginV1LegacyAdapter: PluginV1LegacyAdapter = PluginV1LegacyAdapter(dispatcher),
) : PluginRuntimePort {

    override suspend fun refreshRuntimeRegistry() {
        // no-op: runtime registration is handled by the legacy service layer
    }

    override fun runtimeLogSummary(pluginId: String): String {
        return "" // delegated via PluginRuntimeLogBus in presentation layer
    }

    override fun isPluginLoaded(pluginId: String): Boolean {
        return false // resolved via PluginViewModel existing flow
    }

    fun legacyV1Boundary(): PluginV1LegacyAdapter = pluginV1LegacyAdapter
}
