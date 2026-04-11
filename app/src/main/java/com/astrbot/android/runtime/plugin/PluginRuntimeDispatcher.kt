package com.astrbot.android.runtime.plugin

import com.astrbot.android.model.plugin.PluginCompatibilityStatus
import com.astrbot.android.model.plugin.PluginExecutionStage
import com.astrbot.android.model.plugin.PluginExecutionContext
import com.astrbot.android.model.plugin.PluginExecutionResult
import com.astrbot.android.model.plugin.PluginInstallState
import com.astrbot.android.model.plugin.PluginInstallStatus
import com.astrbot.android.model.plugin.PluginRuntimeLogCategory
import com.astrbot.android.model.plugin.PluginRuntimeLogLevel
import com.astrbot.android.model.plugin.PluginRuntimeLogRecord
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
    SchedulerCoolingDown,
    SchedulerRetryBackoff,
    SchedulerSilenced,
}

data class PluginDispatchSkip(
    val plugin: PluginRuntimePlugin,
    val reason: PluginDispatchSkipReason,
)

data class PluginDispatchPlan(
    val trigger: PluginTriggerSource,
    val scope: PluginDispatchScope,
    val scheduledAtEpochMillis: Long,
    val executable: List<PluginRuntimePlugin>,
    val skipped: List<PluginDispatchSkip>,
)

data class PluginLegacyDispatchAttempt(
    val accepted: Boolean,
    val reason: String = "",
    val plan: PluginDispatchPlan? = null,
)

class PluginRuntimeDispatcher(
    private val failureGuard: PluginFailureGuard,
    private val clock: () -> Long = System::currentTimeMillis,
    internal val scheduler: PluginRuntimeScheduler = PluginRuntimeScheduler(clock = clock),
    internal val policyResolver: (PluginRuntimePlugin, PluginTriggerSource) -> PluginSchedulePolicy = DefaultPluginSchedulePolicyResolver,
    private val logBus: PluginRuntimeLogBus = PluginRuntimeLogBusProvider.bus(),
) {
    fun dispatch(
        trigger: PluginTriggerSource,
        plugins: List<PluginRuntimePlugin>,
    ): PluginDispatchPlan {
        return checkNotNull(
            dispatchLegacy(
                trigger = trigger,
                plugins = plugins,
            ).plan,
        ) {
            "Legacy dispatcher rejected a valid legacy trigger unexpectedly."
        }
    }

    fun dispatchLegacy(
        trigger: PluginTriggerSource?,
        plugins: List<PluginRuntimePlugin>,
        requestedStage: PluginExecutionStage? = null,
    ): PluginLegacyDispatchAttempt {
        val guardrailReason = when {
            requestedStage != null -> requestedStage.guardrailReasonWireValue()
            trigger == null -> "missing_legacy_trigger_source"
            else -> ""
        }
        if (guardrailReason.isNotEmpty()) {
            logBus.publish(
                PluginRuntimeLogRecord(
                    occurredAtEpochMillis = clock(),
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
            return PluginLegacyDispatchAttempt(
                accepted = false,
                reason = guardrailReason,
            )
        }
        return PluginLegacyDispatchAttempt(
            accepted = true,
            plan = dispatchPlan(
                trigger = checkNotNull(trigger),
                plugins = plugins,
            ),
        )
    }

    private fun dispatchPlan(
        trigger: PluginTriggerSource,
        plugins: List<PluginRuntimePlugin>,
    ): PluginDispatchPlan {
        val scope = trigger.toDispatchScope()
        val scheduledAtEpochMillis = clock()
        val executable = mutableListOf<PluginRuntimePlugin>()
        val skipped = mutableListOf<PluginDispatchSkip>()
        plugins.forEach { plugin ->
            val policy = policyResolver(plugin, trigger)
            val scheduleDecision = scheduler.evaluate(
                pluginId = plugin.pluginId,
                trigger = trigger,
                policy = policy,
            )
            val skipReason = when {
                plugin.installState.status != PluginInstallStatus.INSTALLED -> PluginDispatchSkipReason.NotInstalled
                !plugin.installState.enabled -> PluginDispatchSkipReason.Disabled
                plugin.installState.compatibilityState.status == PluginCompatibilityStatus.INCOMPATIBLE ->
                    PluginDispatchSkipReason.Incompatible
                failureGuard.isSuspended(plugin.pluginId, trigger) -> PluginDispatchSkipReason.FailureSuspended
                trigger !in plugin.supportedTriggers -> PluginDispatchSkipReason.UnsupportedTrigger
                !scheduleDecision.allowed -> scheduleDecision.skipReason
                else -> null
            }
            if (skipReason == null) {
                executable += plugin
                scheduler.recordDispatched(
                    pluginId = plugin.pluginId,
                    trigger = trigger,
                )
                logBus.publish(
                    PluginRuntimeLogRecord(
                        occurredAtEpochMillis = scheduledAtEpochMillis,
                        pluginId = plugin.pluginId,
                        pluginVersion = plugin.pluginVersion,
                        trigger = trigger,
                        category = PluginRuntimeLogCategory.Dispatcher,
                        level = PluginRuntimeLogLevel.Info,
                        code = "dispatcher_queued",
                        message = "Plugin queued for trigger ${trigger.wireValue}.",
                        succeeded = true,
                        metadata = mapOf(
                            "dispatchScope" to scope.name,
                            "scheduledAtEpochMillis" to scheduledAtEpochMillis.toString(),
                        ),
                    ),
                )
            } else {
                skipped += PluginDispatchSkip(plugin = plugin, reason = skipReason)
                logBus.publish(
                    PluginRuntimeLogRecord(
                        occurredAtEpochMillis = scheduledAtEpochMillis,
                        pluginId = plugin.pluginId,
                        pluginVersion = plugin.pluginVersion,
                        trigger = trigger,
                        category = PluginRuntimeLogCategory.Dispatcher,
                        level = PluginRuntimeLogLevel.Warning,
                        code = "dispatcher_skipped",
                        message = "Plugin skipped for trigger ${trigger.wireValue}: ${skipReason.name}",
                        succeeded = false,
                        metadata = buildMap {
                            put("skipReason", skipReason.name)
                            put("dispatchScope", scope.name)
                            put("scheduledAtEpochMillis", scheduledAtEpochMillis.toString())
                            scheduleDecision.snapshot.cooldownUntilEpochMillis?.let { cooldownUntil ->
                                put("cooldownUntilEpochMillis", cooldownUntil.toString())
                            }
                            scheduleDecision.snapshot.retryAtEpochMillis?.let { retryAt ->
                                put("retryAtEpochMillis", retryAt.toString())
                            }
                            scheduleDecision.snapshot.silentUntilEpochMillis?.let { silentUntil ->
                                put("silentUntilEpochMillis", silentUntil.toString())
                            }
                        },
                    ),
                )
            }
        }
        return PluginDispatchPlan(
            trigger = trigger,
            scope = scope,
            scheduledAtEpochMillis = scheduledAtEpochMillis,
            executable = executable,
            skipped = skipped,
        )
    }

    private companion object {
        private const val LEGACY_DISPATCHER_PLUGIN_ID = "__legacy_dispatcher__"
    }
}
