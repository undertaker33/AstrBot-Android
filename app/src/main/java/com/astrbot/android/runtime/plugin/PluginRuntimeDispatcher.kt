package com.astrbot.android.runtime.plugin

import com.astrbot.android.model.plugin.PluginCompatibilityStatus
import com.astrbot.android.model.plugin.PluginExecutionContext
import com.astrbot.android.model.plugin.PluginExecutionResult
import com.astrbot.android.model.plugin.PluginInstallState
import com.astrbot.android.model.plugin.PluginInstallStatus
import com.astrbot.android.model.plugin.PluginTriggerSource

fun interface PluginRuntimeHandler {
    fun execute(context: PluginExecutionContext): PluginExecutionResult
}

data class PluginRuntimePlugin(
    val pluginId: String,
    val pluginVersion: String,
    val installState: PluginInstallState,
    val supportedTriggers: Set<PluginTriggerSource> = PluginTriggerSource.entries.toSet(),
    val handler: PluginRuntimeHandler,
) {
    init {
        installState.manifestSnapshot?.let { manifest ->
            require(manifest.pluginId == pluginId) {
                "PluginRuntimePlugin pluginId must match the install state manifest."
            }
            require(manifest.version == pluginVersion) {
                "PluginRuntimePlugin pluginVersion must match the install state manifest."
            }
        }
    }
}

enum class PluginDispatchSkipReason {
    NotInstalled,
    Disabled,
    Incompatible,
    FailureSuspended,
    UnsupportedTrigger,
}

data class PluginDispatchSkip(
    val plugin: PluginRuntimePlugin,
    val reason: PluginDispatchSkipReason,
)

data class PluginDispatchPlan(
    val trigger: PluginTriggerSource,
    val executable: List<PluginRuntimePlugin>,
    val skipped: List<PluginDispatchSkip>,
)

class PluginRuntimeDispatcher(
    private val failureGuard: PluginFailureGuard,
) {
    fun dispatch(
        trigger: PluginTriggerSource,
        plugins: List<PluginRuntimePlugin>,
    ): PluginDispatchPlan {
        val executable = mutableListOf<PluginRuntimePlugin>()
        val skipped = mutableListOf<PluginDispatchSkip>()
        plugins.forEach { plugin ->
            val skipReason = when {
                plugin.installState.status != PluginInstallStatus.INSTALLED -> PluginDispatchSkipReason.NotInstalled
                !plugin.installState.enabled -> PluginDispatchSkipReason.Disabled
                plugin.installState.compatibilityState.status == PluginCompatibilityStatus.INCOMPATIBLE ->
                    PluginDispatchSkipReason.Incompatible
                failureGuard.isSuspended(plugin.pluginId) -> PluginDispatchSkipReason.FailureSuspended
                trigger !in plugin.supportedTriggers -> PluginDispatchSkipReason.UnsupportedTrigger
                else -> null
            }
            if (skipReason == null) {
                executable += plugin
            } else {
                skipped += PluginDispatchSkip(plugin = plugin, reason = skipReason)
            }
        }
        return PluginDispatchPlan(
            trigger = trigger,
            executable = executable,
            skipped = skipped,
        )
    }
}
