package com.astrbot.android.feature.plugin.runtime

import com.astrbot.android.model.plugin.PluginExecutionStage
import com.astrbot.android.model.plugin.PluginTriggerSource

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
) {

    constructor(
        dispatcher: PluginRuntimeDispatcher,
    ) : this(dispatchV1Call = dispatcher::dispatchLegacy)

    fun dispatchLegacy(
        trigger: PluginTriggerSource?,
        plugins: List<PluginRuntimePlugin>,
        requestedStage: PluginExecutionStage? = null,
    ): PluginLegacyDispatchAttempt = dispatchV1Call(trigger, plugins, requestedStage)
}
