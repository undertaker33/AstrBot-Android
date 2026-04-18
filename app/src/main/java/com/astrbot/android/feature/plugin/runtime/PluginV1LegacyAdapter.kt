package com.astrbot.android.feature.plugin.runtime

import com.astrbot.android.model.plugin.PluginExecutionStage
import com.astrbot.android.model.plugin.PluginTriggerSource
import com.astrbot.android.runtime.plugin.PluginLegacyDispatchAttempt
import com.astrbot.android.runtime.plugin.PluginRuntimeDispatcher
import com.astrbot.android.runtime.plugin.PluginRuntimePlugin

/**
 * Explicit legacy/freeze boundary for V1 plugin dispatch.
 *
 * Phase 6 keeps V1 behavior intact, but new runtime code should only touch the
 * V1 path through this adapter so the freeze is visible in the structure.
 */
@Deprecated("Phase 6 legacy freeze boundary for V1 plugin runtime dispatch.")
class PluginV1LegacyAdapter(
    private val dispatchLegacyCall: (
        trigger: PluginTriggerSource?,
        plugins: List<PluginRuntimePlugin>,
        requestedStage: PluginExecutionStage?,
    ) -> PluginLegacyDispatchAttempt,
) {

    constructor(
        dispatcher: PluginRuntimeDispatcher,
    ) : this(dispatchLegacyCall = dispatcher::dispatchLegacy)

    fun dispatchLegacy(
        trigger: PluginTriggerSource?,
        plugins: List<PluginRuntimePlugin>,
        requestedStage: PluginExecutionStage? = null,
    ): PluginLegacyDispatchAttempt = dispatchLegacyCall(trigger, plugins, requestedStage)
}
