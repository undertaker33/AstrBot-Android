package com.astrbot.android.feature.plugin.runtime

import com.astrbot.android.model.plugin.PluginExecutionStage
import com.astrbot.android.model.plugin.PluginRuntimeLogCategory
import com.astrbot.android.model.plugin.PluginRuntimeLogLevel
import com.astrbot.android.model.plugin.PluginRuntimeLogRecord
import com.astrbot.android.model.plugin.PluginTriggerSource

data class PluginLegacyDispatchAttempt(
    val accepted: Boolean,
    val reason: String = "",
    val plan: PluginDispatchPlan? = null,
)

data class PluginLegacyBatchAttempt(
    val accepted: Boolean,
    val reason: String = "",
    val batchResult: PluginExecutionBatchResult? = null,
)

/**
 * Explicit frozen boundary for V1 plugin dispatch.
 *
 * New production wiring should stay on the V2/Hilt-owned mainline, but the
 * remaining V1 dispatch path is still surfaced through this semantic adapter
 * so the boundary remains visible and testable.
 */
class PluginV1DispatchAdapter(
    private val dispatchV1Call: (
        trigger: PluginTriggerSource?,
        plugins: List<PluginRuntimePlugin>,
        requestedStage: PluginExecutionStage?,
    ) -> PluginLegacyDispatchAttempt,
    private val executeV1BatchCall: (
        trigger: PluginTriggerSource?,
        plugins: List<PluginRuntimePlugin>,
        contextFactory: (PluginRuntimePlugin) -> com.astrbot.android.model.plugin.PluginExecutionContext,
        requestedStage: PluginExecutionStage?,
    ) -> PluginLegacyBatchAttempt,
) {

    constructor(
        dispatcher: PluginRuntimeDispatcher,
        engine: PluginExecutionEngine,
    ) : this(
        dispatchV1Call = { trigger, plugins, requestedStage ->
            val guardrailReason = when {
                requestedStage != null -> requestedStage.guardrailReasonWireValue()
                trigger == null -> "missing_legacy_trigger_source"
                else -> ""
            }
            if (guardrailReason.isNotEmpty()) {
                dispatcher.logBus.publish(
                    PluginRuntimeLogRecord(
                        occurredAtEpochMillis = System.currentTimeMillis(),
                        pluginId = LEGACY_DISPATCHER_PLUGIN_ID,
                        trigger = trigger,
                        category = PluginRuntimeLogCategory.Dispatcher,
                        level = PluginRuntimeLogLevel.Warning,
                        code = "legacy_dispatch_guardrail",
                        message = "Legacy dispatcher only accepts legacy PluginTriggerSource entrypoints.",
                        succeeded = false,
                        metadata = buildMap {
                            put("reason", guardrailReason)
                            requestedStage?.let { stage -> put("requestedStage", stage.guardrailStageWireValue()) }
                        },
                    ),
                )
                PluginLegacyDispatchAttempt(
                    accepted = false,
                    reason = guardrailReason,
                )
            } else {
                PluginLegacyDispatchAttempt(
                    accepted = true,
                    plan = dispatcher.dispatch(
                        trigger = checkNotNull(trigger),
                        plugins = plugins,
                    ),
                )
            }
        },
        executeV1BatchCall = { trigger, plugins, contextFactory, requestedStage ->
            val guardrailReason = when {
                requestedStage != null -> requestedStage.guardrailReasonWireValue()
                trigger == null -> "missing_legacy_trigger_source"
                else -> ""
            }
            if (guardrailReason.isNotEmpty()) {
                dispatcher.logBus.publish(
                    PluginRuntimeLogRecord(
                        occurredAtEpochMillis = System.currentTimeMillis(),
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
                PluginLegacyBatchAttempt(
                    accepted = false,
                    reason = guardrailReason,
                )
            } else {
                PluginLegacyBatchAttempt(
                    accepted = true,
                    batchResult = engine.executeBatch(
                        trigger = checkNotNull(trigger),
                        plugins = plugins,
                        contextFactory = contextFactory,
                    ),
                )
            }
        },
    )

    fun dispatchLegacy(
        trigger: PluginTriggerSource?,
        plugins: List<PluginRuntimePlugin>,
        requestedStage: PluginExecutionStage? = null,
    ): PluginLegacyDispatchAttempt = dispatchV1Call(trigger, plugins, requestedStage)

    fun executeLegacyBatch(
        trigger: PluginTriggerSource?,
        plugins: List<PluginRuntimePlugin>,
        contextFactory: (PluginRuntimePlugin) -> com.astrbot.android.model.plugin.PluginExecutionContext,
        requestedStage: PluginExecutionStage? = null,
    ): PluginLegacyBatchAttempt = executeV1BatchCall(trigger, plugins, contextFactory, requestedStage)

    private companion object {
        private const val LEGACY_DISPATCHER_PLUGIN_ID = "__legacy_dispatcher__"
        private const val LEGACY_EXECUTION_ENGINE_PLUGIN_ID = "__legacy_execution_engine__"
    }
}
