package com.astrbot.android.feature.plugin.runtime

internal object PluginRuntimeDependencyBridge {
    @Volatile
    private var activeRuntimeStoreOverride: PluginV2ActiveRuntimeStore? = null

    @Volatile
    private var dispatchEngineOverride: PluginV2DispatchEngine? = null

    @Volatile
    private var lifecycleManagerOverride: PluginV2LifecycleManager? = null

    @Volatile
    private var logBusOverride: PluginRuntimeLogBus? = null

    fun installActiveRuntimeStore(store: PluginV2ActiveRuntimeStore) {
        activeRuntimeStoreOverride = store
    }

    fun installDispatchEngine(engine: PluginV2DispatchEngine) {
        dispatchEngineOverride = engine
    }

    fun installLifecycleManager(manager: PluginV2LifecycleManager) {
        lifecycleManagerOverride = manager
    }

    fun installLogBus(logBus: PluginRuntimeLogBus) {
        logBusOverride = logBus
    }

    fun activeRuntimeStore(): PluginV2ActiveRuntimeStore {
        return activeRuntimeStoreOverride ?: PluginV2ActiveRuntimeStoreProvider.store()
    }

    fun dispatchEngine(): PluginV2DispatchEngine {
        return dispatchEngineOverride ?: PluginV2DispatchEngineProvider.engine()
    }

    fun lifecycleManager(): PluginV2LifecycleManager {
        return lifecycleManagerOverride ?: PluginV2LifecycleManagerProvider.manager()
    }

    fun logBus(): PluginRuntimeLogBus {
        return logBusOverride ?: PluginRuntimeLogBusProvider.bus()
    }

    internal fun resetForTests() {
        activeRuntimeStoreOverride = null
        dispatchEngineOverride = null
        lifecycleManagerOverride = null
        logBusOverride = null
    }
}
