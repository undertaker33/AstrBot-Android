package com.astrbot.android.feature.plugin.runtime

object PluginRuntimeCatalog {
    @Volatile
    private var installedProvider: (() -> List<PluginRuntimePlugin>)? = null

    @Volatile
    private var compatProvider: (() -> List<PluginRuntimePlugin>)? = null

    fun plugins(): List<PluginRuntimePlugin> {
        return (compatProvider ?: installedProvider)?.invoke().orEmpty()
    }

    internal fun installProviderFromHilt(
        provider: () -> List<PluginRuntimePlugin>,
    ) {
        installedProvider = provider
    }

    fun registerProvider(provider: () -> List<PluginRuntimePlugin>) {
        compatProvider = provider
    }

    fun reset() {
        compatProvider = null
    }
}
