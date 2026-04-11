package com.astrbot.android.runtime.plugin

import com.astrbot.android.model.plugin.PluginExecutionContext
import com.astrbot.android.model.plugin.PluginTriggerSource

interface AppChatPluginRuntime {
    fun execute(
        trigger: PluginTriggerSource,
        contextFactory: (PluginRuntimePlugin) -> PluginExecutionContext,
    ): PluginExecutionBatchResult
}

class EngineBackedAppChatPluginRuntime(
    private val pluginProvider: () -> List<PluginRuntimePlugin>,
    private val engine: PluginExecutionEngine,
) : AppChatPluginRuntime {
    override fun execute(
        trigger: PluginTriggerSource,
        contextFactory: (PluginRuntimePlugin) -> PluginExecutionContext,
    ): PluginExecutionBatchResult {
        return engine.executeBatch(
            trigger = trigger,
            plugins = pluginProvider(),
            contextFactory = contextFactory,
        )
    }
}

object PluginRuntimeRegistry {
    @Volatile
    private var pluginProvider: () -> List<PluginRuntimePlugin> = { emptyList() }
    @Volatile
    private var externalProviders: List<() -> List<PluginRuntimePlugin>> = emptyList()

    fun plugins(): List<PluginRuntimePlugin> {
        return buildList {
            addAll(pluginProvider())
            externalProviders.forEach { provider ->
                addAll(provider())
            }
        }
    }

    fun registerProvider(provider: () -> List<PluginRuntimePlugin>) {
        pluginProvider = provider
    }

    fun registerExternalProvider(provider: () -> List<PluginRuntimePlugin>) {
        externalProviders = externalProviders + provider
    }

    fun reset() {
        pluginProvider = { emptyList() }
        externalProviders = emptyList()
    }
}

object DefaultAppChatPluginRuntime : AppChatPluginRuntime {
    override fun execute(
        trigger: PluginTriggerSource,
        contextFactory: (PluginRuntimePlugin) -> PluginExecutionContext,
    ): PluginExecutionBatchResult {
        val plugins = PluginRuntimeRegistry.plugins()
        val failureGuard = PluginFailureGuard(
            store = PluginRuntimeFailureStateStoreProvider.store(),
        )
        val delegate = EngineBackedAppChatPluginRuntime(
            pluginProvider = { plugins },
            engine = PluginExecutionEngine(
                dispatcher = PluginRuntimeDispatcher(failureGuard),
                failureGuard = failureGuard,
            ),
        )
        return delegate.execute(trigger, contextFactory)
    }
}
