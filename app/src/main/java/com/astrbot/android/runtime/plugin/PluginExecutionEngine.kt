package com.astrbot.android.runtime.plugin

import com.astrbot.android.model.plugin.ErrorResult
import com.astrbot.android.model.plugin.PluginExecutionContext
import com.astrbot.android.model.plugin.PluginExecutionResult
import com.astrbot.android.model.plugin.PluginInstallState
import com.astrbot.android.model.plugin.PluginTriggerSource

data class PluginExecutionOutcome(
    val pluginId: String,
    val pluginVersion: String,
    val installState: PluginInstallState,
    val context: PluginExecutionContext,
    val result: PluginExecutionResult,
    val succeeded: Boolean,
    val failureSnapshot: PluginFailureSnapshot,
    val error: Throwable? = null,
)

data class PluginExecutionBatchResult(
    val trigger: PluginTriggerSource,
    val outcomes: List<PluginExecutionOutcome>,
    val skipped: List<PluginDispatchSkip>,
)

class PluginExecutionEngine(
    private val dispatcher: PluginRuntimeDispatcher,
    private val failureGuard: PluginFailureGuard,
) {
    fun execute(
        plugin: PluginRuntimePlugin,
        context: PluginExecutionContext,
    ): PluginExecutionOutcome {
        val normalizedContext = normalizeContext(plugin = plugin, context = context)
        return try {
            val result = plugin.handler.execute(normalizedContext)
            PluginExecutionOutcome(
                pluginId = plugin.pluginId,
                pluginVersion = plugin.pluginVersion,
                installState = plugin.installState,
                context = normalizedContext,
                result = result,
                succeeded = true,
                failureSnapshot = failureGuard.recordSuccess(plugin.pluginId),
            )
        } catch (error: Throwable) {
            PluginExecutionOutcome(
                pluginId = plugin.pluginId,
                pluginVersion = plugin.pluginVersion,
                installState = plugin.installState,
                context = normalizedContext,
                result = ErrorResult(
                    message = error.message ?: "Plugin execution failed",
                    code = "plugin_execution_failed",
                    recoverable = true,
                ),
                succeeded = false,
                failureSnapshot = failureGuard.recordFailure(
                    pluginId = plugin.pluginId,
                    errorSummary = error.message?.takeIf { it.isNotBlank() }
                        ?: error.javaClass.simpleName,
                ),
                error = error,
            )
        }
    }

    fun executeBatch(
        trigger: PluginTriggerSource,
        plugins: List<PluginRuntimePlugin>,
        contextFactory: (PluginRuntimePlugin) -> PluginExecutionContext,
    ): PluginExecutionBatchResult {
        val plan = dispatcher.dispatch(trigger = trigger, plugins = plugins)
        val outcomes = plan.executable.map { plugin ->
            execute(
                plugin = plugin,
                context = contextFactory(plugin),
            )
        }
        return PluginExecutionBatchResult(
            trigger = trigger,
            outcomes = outcomes,
            skipped = plan.skipped,
        )
    }

    private fun normalizeContext(
        plugin: PluginRuntimePlugin,
        context: PluginExecutionContext,
    ): PluginExecutionContext {
        return context.copy(
            pluginId = plugin.pluginId,
            pluginVersion = plugin.pluginVersion,
        )
    }
}
