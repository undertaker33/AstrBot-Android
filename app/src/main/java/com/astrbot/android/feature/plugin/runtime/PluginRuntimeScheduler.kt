package com.astrbot.android.feature.plugin.runtime

import com.astrbot.android.model.plugin.PluginTriggerSource

enum class PluginDispatchScope {
    Manual,
    Message,
    Scheduled,
}

internal fun PluginTriggerSource.toDispatchScope(): PluginDispatchScope {
    return when (this) {
        PluginTriggerSource.OnPluginEntryClick -> PluginDispatchScope.Manual
        PluginTriggerSource.OnSchedule -> PluginDispatchScope.Scheduled
        else -> PluginDispatchScope.Message
    }
}

data class PluginSchedulePolicy(
    val successCooldownMillis: Long = 0L,
    val failureRetryBackoffMillis: Long = 0L,
    val failureSilenceMillis: Long = 0L,
) {
    init {
        require(successCooldownMillis >= 0L) { "successCooldownMillis must not be negative." }
        require(failureRetryBackoffMillis >= 0L) { "failureRetryBackoffMillis must not be negative." }
        require(failureSilenceMillis >= 0L) { "failureSilenceMillis must not be negative." }
    }
}

data class PluginScheduleSnapshot(
    val pluginId: String,
    val trigger: PluginTriggerSource,
    val lastDispatchedAtEpochMillis: Long? = null,
    val lastSucceededAtEpochMillis: Long? = null,
    val lastFailedAtEpochMillis: Long? = null,
    val cooldownUntilEpochMillis: Long? = null,
    val retryAtEpochMillis: Long? = null,
    val silentUntilEpochMillis: Long? = null,
    val consecutiveFailureCount: Int = 0,
) {
    init {
        require(consecutiveFailureCount >= 0) { "consecutiveFailureCount must not be negative." }
    }
}

data class PluginScheduleDecision(
    val allowed: Boolean,
    val skipReason: PluginDispatchSkipReason? = null,
    val snapshot: PluginScheduleSnapshot,
)

interface PluginScheduleStateStore {
    fun get(pluginId: String, trigger: PluginTriggerSource): PluginScheduleSnapshot?

    fun put(snapshot: PluginScheduleSnapshot)

    fun remove(pluginId: String, trigger: PluginTriggerSource)
}

class InMemoryPluginScheduleStateStore : PluginScheduleStateStore {
    private val states = linkedMapOf<String, PluginScheduleSnapshot>()

    override fun get(
        pluginId: String,
        trigger: PluginTriggerSource,
    ): PluginScheduleSnapshot? = states[key(pluginId, trigger)]

    override fun put(snapshot: PluginScheduleSnapshot) {
        states[key(snapshot.pluginId, snapshot.trigger)] = snapshot
    }

    override fun remove(
        pluginId: String,
        trigger: PluginTriggerSource,
    ) {
        states.remove(key(pluginId, trigger))
    }

    private fun key(
        pluginId: String,
        trigger: PluginTriggerSource,
    ): String = "$pluginId#${trigger.wireValue}"
}

object PluginRuntimeScheduleStateStoreProvider {
    @Volatile
    private var storeOverrideForTests: PluginScheduleStateStore? = null

    private val sharedStore: PluginScheduleStateStore by lazy {
        InMemoryPluginScheduleStateStore()
    }

    fun store(): PluginScheduleStateStore = storeOverrideForTests ?: sharedStore

    internal fun setStoreOverrideForTests(store: PluginScheduleStateStore?) {
        storeOverrideForTests = store
    }
}

internal val DefaultPluginSchedulePolicyResolver: (PluginRuntimePlugin, PluginTriggerSource) -> PluginSchedulePolicy =
    { _, trigger ->
        when (trigger) {
            PluginTriggerSource.OnSchedule -> PluginSchedulePolicy(
                successCooldownMillis = 60_000L,
                failureRetryBackoffMillis = 15_000L,
                failureSilenceMillis = 60_000L,
            )

            else -> PluginSchedulePolicy()
        }
    }

class PluginRuntimeScheduler(
    private val store: PluginScheduleStateStore = InMemoryPluginScheduleStateStore(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun snapshot(
        pluginId: String,
        trigger: PluginTriggerSource,
    ): PluginScheduleSnapshot {
        return store.get(pluginId, trigger)
            ?: PluginScheduleSnapshot(
                pluginId = pluginId,
                trigger = trigger,
            )
    }

    fun evaluate(
        pluginId: String,
        trigger: PluginTriggerSource,
        policy: PluginSchedulePolicy = PluginSchedulePolicy(),
    ): PluginScheduleDecision {
        val current = snapshot(pluginId, trigger)
        val now = clock()
        val skipReason = when {
            current.retryAtEpochMillis != null &&
                policy.failureRetryBackoffMillis > 0L &&
                now < current.retryAtEpochMillis -> PluginDispatchSkipReason.SchedulerRetryBackoff
            current.silentUntilEpochMillis != null &&
                policy.failureSilenceMillis > 0L &&
                now < current.silentUntilEpochMillis -> PluginDispatchSkipReason.SchedulerSilenced
            current.cooldownUntilEpochMillis != null &&
                policy.successCooldownMillis > 0L &&
                now < current.cooldownUntilEpochMillis -> PluginDispatchSkipReason.SchedulerCoolingDown
            else -> null
        }
        return PluginScheduleDecision(
            allowed = skipReason == null,
            skipReason = skipReason,
            snapshot = current,
        )
    }

    fun recordDispatched(
        pluginId: String,
        trigger: PluginTriggerSource,
    ): PluginScheduleSnapshot {
        val updated = snapshot(pluginId, trigger).copy(
            lastDispatchedAtEpochMillis = clock(),
        )
        store.put(updated)
        return updated
    }

    fun recordSuccess(
        pluginId: String,
        trigger: PluginTriggerSource,
        policy: PluginSchedulePolicy = PluginSchedulePolicy(),
    ): PluginScheduleSnapshot {
        val now = clock()
        val updated = snapshot(pluginId, trigger).copy(
            lastSucceededAtEpochMillis = now,
            cooldownUntilEpochMillis = if (policy.successCooldownMillis > 0L) {
                now + policy.successCooldownMillis
            } else {
                null
            },
            retryAtEpochMillis = null,
            silentUntilEpochMillis = null,
            consecutiveFailureCount = 0,
        )
        store.put(updated)
        return updated
    }

    fun recordFailure(
        pluginId: String,
        trigger: PluginTriggerSource,
        policy: PluginSchedulePolicy = PluginSchedulePolicy(),
    ): PluginScheduleSnapshot {
        val now = clock()
        val current = snapshot(pluginId, trigger)
        val updated = current.copy(
            lastFailedAtEpochMillis = now,
            retryAtEpochMillis = if (policy.failureRetryBackoffMillis > 0L) {
                now + policy.failureRetryBackoffMillis
            } else {
                null
            },
            silentUntilEpochMillis = if (policy.failureSilenceMillis > 0L) {
                now + policy.failureSilenceMillis
            } else {
                null
            },
            consecutiveFailureCount = current.consecutiveFailureCount + 1,
        )
        store.put(updated)
        return updated
    }
}
