package com.astrbot.android.feature.plugin.runtime

import com.astrbot.android.model.plugin.ErrorResult
import com.astrbot.android.model.plugin.PluginExecutionContext
import com.astrbot.android.model.plugin.PluginExecutionStage
import com.astrbot.android.model.plugin.PluginExecutionResult
import com.astrbot.android.model.plugin.PluginInstallState
import com.astrbot.android.model.plugin.PluginRuntimeLogCategory
import com.astrbot.android.model.plugin.PluginRuntimeLogLevel
import com.astrbot.android.model.plugin.PluginRuntimeLogRecord
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
    val merged: PluginExecutionMergeSnapshot = PluginExecutionMergeSnapshot(),
)

data class PluginLegacyBatchAttempt(
    val accepted: Boolean,
    val reason: String = "",
    val batchResult: PluginExecutionBatchResult? = null,
)

class PluginExecutionEngine(
    private val dispatcher: PluginRuntimeDispatcher,
    private val failureGuard: PluginFailureGuard,
    private val clock: () -> Long = System::currentTimeMillis,
    private val scheduler: PluginRuntimeScheduler = dispatcher.scheduler,
    private val policyResolver: (PluginRuntimePlugin, PluginTriggerSource) -> PluginSchedulePolicy = dispatcher.policyResolver,
    private val resultMerger: PluginExecutionResultMerger = PluginExecutionResultMerger(
        logBus = dispatcher.logBus,
        clock = clock,
    ),
    private val logBus: PluginRuntimeLogBus = dispatcher.logBus,
) {
    fun execute(
        plugin: PluginRuntimePlugin,
        context: PluginExecutionContext,
    ): PluginExecutionOutcome {
        val normalizedContext = normalizeContext(plugin = plugin, context = context)
        val startedAt = clock()
        val policy = policyResolver(plugin, normalizedContext.trigger)
        return try {
            val result = plugin.handler.execute(normalizedContext)
            val durationMillis = (clock() - startedAt).coerceAtLeast(0L)
            scheduler.recordSuccess(
                pluginId = plugin.pluginId,
                trigger = normalizedContext.trigger,
                policy = policy,
            )
            logBus.publish(
                PluginRuntimeLogRecord(
                    occurredAtEpochMillis = startedAt,
                    pluginId = plugin.pluginId,
                    pluginVersion = plugin.pluginVersion,
                    trigger = normalizedContext.trigger,
                    category = PluginRuntimeLogCategory.Execution,
                    level = PluginRuntimeLogLevel.Info,
                    code = "execution_succeeded",
                    message = "Plugin execution succeeded.",
                    succeeded = true,
                    durationMillis = durationMillis,
                    resultType = result.runtimeResultTypeWire(),
                ),
            )
            PluginExecutionOutcome(
                pluginId = plugin.pluginId,
                pluginVersion = plugin.pluginVersion,
                installState = plugin.installState,
                context = normalizedContext,
                result = result,
                succeeded = true,
                failureSnapshot = failureGuard.recordSuccess(
                    pluginId = plugin.pluginId,
                    trigger = normalizedContext.trigger,
                ),
            )
        } catch (error: Throwable) {
            val durationMillis = (clock() - startedAt).coerceAtLeast(0L)
            scheduler.recordFailure(
                pluginId = plugin.pluginId,
                trigger = normalizedContext.trigger,
                policy = policy,
            )
            logBus.publish(
                PluginRuntimeLogRecord(
                    occurredAtEpochMillis = startedAt,
                    pluginId = plugin.pluginId,
                    pluginVersion = plugin.pluginVersion,
                    trigger = normalizedContext.trigger,
                    category = PluginRuntimeLogCategory.Execution,
                    level = PluginRuntimeLogLevel.Error,
                    code = "execution_failed",
                    message = error.message ?: "Plugin execution failed.",
                    succeeded = false,
                    durationMillis = durationMillis,
                    resultType = "error",
                ),
            )
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
                    trigger = normalizedContext.trigger,
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
        return checkNotNull(
            executeLegacyBatch(
                trigger = trigger,
                plugins = plugins,
                contextFactory = contextFactory,
            ).batchResult,
        ) {
            "Legacy execution engine rejected a valid legacy trigger unexpectedly."
        }
    }

    fun executeLegacyBatch(
        trigger: PluginTriggerSource?,
        plugins: List<PluginRuntimePlugin>,
        contextFactory: (PluginRuntimePlugin) -> PluginExecutionContext,
        requestedStage: PluginExecutionStage? = null,
    ): PluginLegacyBatchAttempt {
        val guardrailReason = when {
            requestedStage != null -> requestedStage.guardrailReasonWireValue()
            trigger == null -> "missing_legacy_trigger_source"
            else -> ""
        }
        if (guardrailReason.isNotEmpty()) {
            logBus.publish(
                PluginRuntimeLogRecord(
                    occurredAtEpochMillis = clock(),
                    pluginId = LEGACY_EXECUTION_ENGINE_PLUGIN_ID,
                    trigger = trigger,
                    category = PluginRuntimeLogCategory.Execution,
                    level = PluginRuntimeLogLevel.Warning,
                    code = "legacy_execution_guardrail",
                    message = "Legacy execution engine only accepts legacy PluginTriggerSource batches.",
                    succeeded = false,
                    metadata = buildMap {
                        put("reason", guardrailReason)
                        requestedStage?.let { stage -> put("requestedStage", stage.guardrailStageWireValue()) }
                    },
                ),
            )
            return PluginLegacyBatchAttempt(
                accepted = false,
                reason = guardrailReason,
            )
        }

        val acceptedTrigger = checkNotNull(trigger)
        val plan = checkNotNull(
            dispatcher.dispatchLegacy(
                trigger = acceptedTrigger,
                plugins = plugins,
            ).plan,
        ) {
            "Legacy dispatcher rejected a valid legacy trigger unexpectedly."
        }
        val outcomes = plan.executable.map { plugin ->
            val batchContext = contextFactory(plugin).copy(trigger = acceptedTrigger)
            execute(
                plugin = plugin,
                context = batchContext,
            )
        }
        return PluginLegacyBatchAttempt(
            accepted = true,
            batchResult = PluginExecutionBatchResult(
                trigger = acceptedTrigger,
                outcomes = outcomes,
                skipped = plan.skipped,
                merged = resultMerger.mergeLegacy(
                    trigger = acceptedTrigger,
                    outcomes = outcomes,
                ),
            ),
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

    private companion object {
        private const val LEGACY_EXECUTION_ENGINE_PLUGIN_ID = "__legacy_execution_engine__"
    }
}
