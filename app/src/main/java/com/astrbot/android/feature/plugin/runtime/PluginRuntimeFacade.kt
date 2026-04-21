@file:Suppress("DEPRECATION")

package com.astrbot.android.feature.plugin.runtime

import com.astrbot.android.feature.plugin.domain.PluginRuntimePort
import com.astrbot.android.feature.plugin.runtime.PluginRuntimeDispatcher

class PluginRuntimeFacade(
    dispatcher: PluginRuntimeDispatcher,
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
}
