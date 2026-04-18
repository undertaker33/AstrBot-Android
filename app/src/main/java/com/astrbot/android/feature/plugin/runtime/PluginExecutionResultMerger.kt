package com.astrbot.android.feature.plugin.runtime

import com.astrbot.android.model.plugin.CardResult
import com.astrbot.android.model.plugin.HostActionRequest
import com.astrbot.android.model.plugin.MediaResult
import com.astrbot.android.model.plugin.NoOp
import com.astrbot.android.model.plugin.PluginExecutionStage
import com.astrbot.android.model.plugin.PluginExecutionResult
import com.astrbot.android.model.plugin.PluginRuntimeLogCategory
import com.astrbot.android.model.plugin.PluginRuntimeLogLevel
import com.astrbot.android.model.plugin.PluginRuntimeLogRecord
import com.astrbot.android.model.plugin.PluginTriggerSource
import com.astrbot.android.model.plugin.SettingsUiRequest
import com.astrbot.android.model.plugin.TextResult
import com.astrbot.android.model.plugin.ErrorResult

data class PluginExecutionMergeConflict(
    val pluginId: String,
    val overriddenByPluginId: String,
    val resultType: String,
    val reason: String,
)

data class PluginExecutionMergeSnapshot(
    val orderedPluginIds: List<String> = emptyList(),
    val resultTypeCounts: Map<String, Int> = emptyMap(),
    val primaryInteractivePluginId: String = "",
    val primaryInteractiveResultType: String = "",
    val conflicts: List<PluginExecutionMergeConflict> = emptyList(),
)

class PluginExecutionResultMerger(
    private val logBus: PluginRuntimeLogBus = PluginRuntimeLogBusProvider.bus(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun merge(
        trigger: PluginTriggerSource,
        outcomes: List<PluginExecutionOutcome>,
        requestedStage: PluginExecutionStage? = null,
    ): PluginExecutionMergeSnapshot {
        if (requestedStage != null) {
            logBus.publish(
                PluginRuntimeLogRecord(
                    occurredAtEpochMillis = clock(),
                    pluginId = LEGACY_RESULT_MERGER_PLUGIN_ID,
                    trigger = trigger,
                    category = PluginRuntimeLogCategory.ResultMerger,
                    level = PluginRuntimeLogLevel.Warning,
                    code = "legacy_result_merger_guardrail",
                    message = "Legacy result merger no-oped because a phase4 LLM stage was routed into the legacy path.",
                    succeeded = false,
                    metadata = mapOf(
                        "requestedStage" to requestedStage.guardrailStageWireValue(),
                        "reason" to requestedStage.guardrailReasonWireValue(),
                    ),
                ),
            )
            return PluginExecutionMergeSnapshot()
        }
        return mergeLegacy(
            trigger = trigger,
            outcomes = outcomes,
        )
    }

    fun mergeLegacy(
        trigger: PluginTriggerSource,
        outcomes: List<PluginExecutionOutcome>,
    ): PluginExecutionMergeSnapshot {
        val ordered = outcomes.map(PluginExecutionOutcome::pluginId)
        val typeCounts = outcomes
            .map { outcome -> outcome.result.runtimeResultTypeWire() }
            .groupingBy { type -> type }
            .eachCount()
        val interactive = outcomes.filter { outcome ->
            outcome.succeeded && (
                outcome.result is CardResult ||
                    outcome.result is SettingsUiRequest
                )
        }
        val primary = interactive.lastOrNull()
        val conflicts = if (primary == null) {
            emptyList()
        } else {
            interactive.dropLast(1).map { outcome ->
                PluginExecutionMergeConflict(
                    pluginId = outcome.pluginId,
                    overriddenByPluginId = primary.pluginId,
                    resultType = outcome.result.runtimeResultTypeWire(),
                    reason = "interactive_result_overridden",
                )
            }
        }
        conflicts.forEach { conflict ->
            logBus.publish(
                PluginRuntimeLogRecord(
                    occurredAtEpochMillis = clock(),
                    pluginId = conflict.overriddenByPluginId,
                    trigger = trigger,
                    category = PluginRuntimeLogCategory.ResultMerger,
                    level = PluginRuntimeLogLevel.Warning,
                    code = "result_merge_conflict",
                    message = "Interactive result from ${conflict.pluginId} was overridden by ${conflict.overriddenByPluginId}.",
                    succeeded = false,
                    resultType = conflict.resultType,
                    metadata = mapOf("conflictReason" to conflict.reason),
                ),
            )
        }
        return PluginExecutionMergeSnapshot(
            orderedPluginIds = ordered,
            resultTypeCounts = typeCounts,
            primaryInteractivePluginId = primary?.pluginId.orEmpty(),
            primaryInteractiveResultType = primary?.result?.runtimeResultTypeWire().orEmpty(),
            conflicts = conflicts,
        )
    }

    private companion object {
        private const val LEGACY_RESULT_MERGER_PLUGIN_ID = "__legacy_result_merger__"
    }
}

internal fun PluginExecutionStage.guardrailStageWireValue(): String = wireValue

internal fun PluginExecutionStage.guardrailReasonWireValue(): String = "phase4_stage_${guardrailStageWireValue()}"

internal fun PluginExecutionResult.runtimeResultTypeWire(): String {
    return when (this) {
        is TextResult -> "text"
        is CardResult -> "card"
        is MediaResult -> "media"
        is HostActionRequest -> "host_action"
        is SettingsUiRequest -> "settings_ui"
        is NoOp -> "noop"
        is ErrorResult -> "error"
    }
}
