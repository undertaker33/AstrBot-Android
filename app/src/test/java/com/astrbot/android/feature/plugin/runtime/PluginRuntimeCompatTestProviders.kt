package com.astrbot.android.feature.plugin.runtime

internal object PluginRuntimeFailureStateStoreProvider {
    @Volatile
    private var storeOverrideForTests: PluginFailureStateStore? = null

    fun store(): PluginFailureStateStore = storeOverrideForTests ?: InMemoryPluginFailureStateStore()

    fun setStoreOverrideForTests(store: PluginFailureStateStore?) {
        storeOverrideForTests = store
    }
}

internal object PluginRuntimeScopedFailureStateStoreProvider {
    @Volatile
    private var storeOverrideForTests: PluginScopedFailureStateStore? = null

    fun store(): PluginScopedFailureStateStore = storeOverrideForTests ?: InMemoryPluginScopedFailureStateStore()

    fun setStoreOverrideForTests(store: PluginScopedFailureStateStore?) {
        storeOverrideForTests = store
    }
}
