package com.astrbot.android.feature.plugin.runtime

object PluginRuntimeCatalog {
    @Volatile
    private var pluginProvider: () -> List<PluginRuntimePlugin> = { ExternalPluginRuntimeCatalog.plugins() }

    fun plugins(): List<PluginRuntimePlugin> {
        return pluginProvider()
    }

    fun registerProvider(provider: () -> List<PluginRuntimePlugin>) {
        pluginProvider = provider
    }

    fun reset() {
        pluginProvider = { ExternalPluginRuntimeCatalog.plugins() }
    }
}
