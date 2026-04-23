package com.astrbot.android.feature.qq.runtime

import com.astrbot.android.feature.plugin.runtime.ExternalPluginRuntimeCatalog
import com.astrbot.android.feature.plugin.runtime.PluginExecutionBatchResult
import com.astrbot.android.feature.plugin.runtime.PluginExecutionEngine
import com.astrbot.android.feature.plugin.runtime.PluginFailureGuard
import com.astrbot.android.feature.plugin.runtime.PluginFailureStateStore
import com.astrbot.android.feature.plugin.runtime.PluginRuntimeDispatcher
import com.astrbot.android.feature.plugin.runtime.PluginRuntimeLogBus
import com.astrbot.android.feature.plugin.runtime.PluginRuntimePlugin
import com.astrbot.android.feature.plugin.runtime.PluginRuntimeScheduler
import com.astrbot.android.feature.plugin.runtime.InMemoryPluginScheduleStateStore
import com.astrbot.android.feature.plugin.runtime.PluginScheduleStateStore
import com.astrbot.android.feature.plugin.runtime.PluginScopedFailureStateStore
import com.astrbot.android.model.plugin.PluginExecutionContext
import com.astrbot.android.model.plugin.PluginTriggerSource
import javax.inject.Inject

internal class QqPluginExecutionService(
    private val executeBatchBlock: (
        PluginTriggerSource,
        (PluginRuntimePlugin) -> PluginExecutionContext,
    ) -> PluginExecutionBatchResult,
) {
    @Inject
    constructor(
        engine: PluginExecutionEngine,
        pluginCatalog: ExternalPluginRuntimeCatalog,
    ) : this(
        executeBatchBlock = { trigger, contextFactory ->
            engine.executeBatch(
                trigger = trigger,
                plugins = pluginCatalog.plugins(),
                contextFactory = contextFactory,
            )
        },
    )

    internal constructor(
        pluginCatalog: () -> List<PluginRuntimePlugin>,
        failureStateStore: PluginFailureStateStore,
        scopedFailureStateStore: PluginScopedFailureStateStore,
        scheduleStateStore: PluginScheduleStateStore = InMemoryPluginScheduleStateStore(),
        logBus: PluginRuntimeLogBus,
    ) : this(
        executeBatchBlock = { trigger, contextFactory ->
            val failureGuard = PluginFailureGuard(
                store = failureStateStore,
                scopedStore = scopedFailureStateStore,
                logBus = logBus,
            )
            val scheduler = PluginRuntimeScheduler(
                store = scheduleStateStore,
            )
            PluginExecutionEngine(
                dispatcher = PluginRuntimeDispatcher(
                    failureGuard = failureGuard,
                    scheduler = scheduler,
                    logBus = logBus,
                ),
                failureGuard = failureGuard,
                scheduler = scheduler,
                logBus = logBus,
            ).executeBatch(
                trigger = trigger,
                plugins = pluginCatalog(),
                contextFactory = contextFactory,
            )
        },
    )

    fun executeBatch(
        trigger: PluginTriggerSource,
        contextFactory: (PluginRuntimePlugin) -> PluginExecutionContext,
    ): PluginExecutionBatchResult {
        return executeBatchBlock(trigger, contextFactory)
    }
}
