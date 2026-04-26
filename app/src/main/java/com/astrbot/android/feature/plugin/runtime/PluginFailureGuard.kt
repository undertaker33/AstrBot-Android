@file:Suppress("DEPRECATION")

package com.astrbot.android.feature.plugin.runtime

import com.astrbot.android.feature.plugin.data.FeaturePluginRepository
import com.astrbot.android.model.plugin.PluginFailureCategory
import com.astrbot.android.model.plugin.PluginRuntimeLogCategory
import com.astrbot.android.model.plugin.PluginRuntimeLogLevel
import com.astrbot.android.model.plugin.PluginRuntimeLogRecord
import com.astrbot.android.model.plugin.PluginFailureState
import com.astrbot.android.model.plugin.PluginTriggerSource

data class PluginFailurePolicy(
    val maxConsecutiveFailures: Int = 3,
    val suspensionWindowMillis: Long = 5 * 60 * 1000L,
) {
    init {
        require(maxConsecutiveFailures > 0) { "maxConsecutiveFailures must be greater than zero." }
        require(suspensionWindowMillis >= 0L) { "suspensionWindowMillis must not be negative." }
    }
}

data class PluginFailureSnapshot(
    val pluginId: String,
    val trigger: PluginTriggerSource? = null,
    val consecutiveFailureCount: Int = 0,
    val lastFailureAtEpochMillis: Long? = null,
    val lastErrorSummary: String = "",
    val failureCategory: PluginFailureCategory = PluginFailureCategory.Unknown,
    val isSuspended: Boolean = false,
    val suspendedUntilEpochMillis: Long? = null,
)

interface PluginFailureStateStore {
    fun get(pluginId: String): PluginFailureSnapshot?

    fun put(snapshot: PluginFailureSnapshot)

    fun remove(pluginId: String)
}

class InMemoryPluginFailureStateStore : PluginFailureStateStore {
    private val states = linkedMapOf<String, PluginFailureSnapshot>()

    override fun get(pluginId: String): PluginFailureSnapshot? = states[pluginId]

    override fun put(snapshot: PluginFailureSnapshot) {
        states[snapshot.pluginId] = snapshot
    }

    override fun remove(pluginId: String) {
        states.remove(pluginId)
    }
}

class PersistentPluginFailureStateStore(
    private val findState: (String) -> PluginFailureState? = { pluginId ->
        repositoryFailureStateOrNull(pluginId)
    },
    private val updateState: (String, PluginFailureState) -> Unit = { pluginId, failureState ->
        if (repositoryFailureStateOrNull(pluginId) != null) {
            FeaturePluginRepository.updateFailureState(pluginId, failureState)
        }
    },
    private val clearState: (String) -> Unit = { pluginId ->
        if (repositoryFailureStateOrNull(pluginId) != null) {
            FeaturePluginRepository.clearFailureState(pluginId)
        }
    },
) : PluginFailureStateStore {
    override fun get(pluginId: String): PluginFailureSnapshot? {
        return findState(pluginId)
            ?.takeIf { it.hasFailures }
            ?.toSnapshot(pluginId)
    }

    override fun put(snapshot: PluginFailureSnapshot) {
        val failureState = snapshot.toFailureState()
        if (failureState.hasFailures) {
            updateState(snapshot.pluginId, failureState)
        } else {
            clearState(snapshot.pluginId)
        }
    }

    override fun remove(pluginId: String) {
        clearState(pluginId)
    }
}

interface PluginScopedFailureStateStore {
    fun get(
        pluginId: String,
        trigger: PluginTriggerSource,
    ): PluginFailureSnapshot?

    fun put(snapshot: PluginFailureSnapshot)

    fun snapshot(pluginId: String): List<PluginFailureSnapshot>

    fun remove(
        pluginId: String,
        trigger: PluginTriggerSource,
    )
}

class InMemoryPluginScopedFailureStateStore : PluginScopedFailureStateStore {
    private val states = linkedMapOf<String, PluginFailureSnapshot>()

    override fun get(
        pluginId: String,
        trigger: PluginTriggerSource,
    ): PluginFailureSnapshot? = states[key(pluginId, trigger)]

    override fun put(snapshot: PluginFailureSnapshot) {
        val trigger = snapshot.trigger ?: return
        states[key(snapshot.pluginId, trigger)] = snapshot
    }

    override fun snapshot(pluginId: String): List<PluginFailureSnapshot> {
        return states.values.filter { snapshot -> snapshot.pluginId == pluginId }
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

class PluginFailureGuard(
    private val store: PluginFailureStateStore,
    private val scopedStore: PluginScopedFailureStateStore,
    private val policy: PluginFailurePolicy = PluginFailurePolicy(),
    private val clock: () -> Long = System::currentTimeMillis,
    internal val logBus: PluginRuntimeLogBus,
) {
    fun snapshot(
        pluginId: String,
        trigger: PluginTriggerSource? = null,
    ): PluginFailureSnapshot {
        val current = if (trigger == null) {
            resolve(pluginId)
        } else {
            resolveScoped(pluginId, trigger)
        }
        return current ?: PluginFailureSnapshot(
            pluginId = pluginId,
            trigger = trigger,
        )
    }

    fun isSuspended(
        pluginId: String,
        trigger: PluginTriggerSource? = null,
    ): Boolean = snapshot(pluginId, trigger).isSuspended

    fun recordFailure(
        pluginId: String,
        trigger: PluginTriggerSource? = null,
        errorSummary: String = "",
    ): PluginFailureSnapshot {
        val now = clock()
        val current = if (trigger == null) {
            resolve(pluginId)
        } else {
            resolveScoped(pluginId, trigger)
        } ?: PluginFailureSnapshot(pluginId = pluginId, trigger = trigger)
        val aggregateBefore = if (trigger == null) {
            current
        } else {
            resolve(pluginId) ?: PluginFailureSnapshot(pluginId = pluginId)
        }
        val shouldProjectRecoveryFailed = current.canProjectRecoveryFailure()
        val shouldProjectAggregateRecoveryFailed = trigger != null && (
            aggregateBefore.canProjectRecoveryFailure() ||
                logBus.hasRecoveredBoundary(pluginId = pluginId, failureScope = "plugin")
            )
        val category = classifyFailure(errorSummary.ifBlank { current.lastErrorSummary })
        val snapshot = evolveFailureSnapshot(
            current = current,
            pluginId = pluginId,
            trigger = trigger,
            errorSummary = errorSummary,
            category = category,
            now = now,
        )
        saveSnapshot(snapshot)
        var aggregateAfter = snapshot
        if (trigger != null) {
            aggregateAfter = saveAggregateFailure(
                pluginId = pluginId,
                errorSummary = errorSummary,
                category = category,
                now = now,
            )
        }
        logBus.publish(
            PluginRuntimeLogRecord(
                occurredAtEpochMillis = now,
                pluginId = pluginId,
                trigger = trigger,
                category = PluginRuntimeLogCategory.FailureGuard,
                level = if (snapshot.isSuspended) PluginRuntimeLogLevel.Error else PluginRuntimeLogLevel.Warning,
                code = if (snapshot.isSuspended) "failure_guard_suspended" else "failure_guard_recorded",
                message = errorSummary.ifBlank { "Plugin failure recorded." },
                succeeded = false,
                metadata = linkedMapOf(
                    "code" to if (snapshot.isSuspended) "failure_guard_suspended" else "failure_guard_recorded",
                    "stage" to "FailureGuard",
                    "outcome" to if (snapshot.isSuspended) "SUSPENDED" else "FAILED",
                    "consecutiveFailureCount" to snapshot.consecutiveFailureCount.toString(),
                    "failureCategory" to category.wireValue,
                    "failureScope" to (trigger?.wireValue ?: "plugin"),
                    "suspendedUntilEpochMillis" to (snapshot.suspendedUntilEpochMillis?.toString() ?: ""),
                ),
            ),
        )
        if (shouldProjectRecoveryFailed) {
            logBus.publish(
                PluginRuntimeLogRecord(
                    occurredAtEpochMillis = now,
                    pluginId = pluginId,
                    trigger = trigger,
                    category = PluginRuntimeLogCategory.FailureGuard,
                    level = PluginRuntimeLogLevel.Warning,
                    code = "failure_guard_recovery_failed",
                    message = errorSummary.ifBlank { "Plugin recovery attempt failed." },
                    succeeded = false,
                    metadata = linkedMapOf(
                        "code" to "failure_guard_recovery_failed",
                        "stage" to "FailureGuard",
                        "outcome" to "RECOVERY_FAILED",
                        "failureCategory" to category.wireValue,
                        "failureScope" to (trigger?.wireValue ?: "plugin"),
                        "consecutiveFailureCount" to snapshot.consecutiveFailureCount.toString(),
                    ),
                ),
            )
        }
        if (current.isSuspended != snapshot.isSuspended) {
            logBus.publishPluginSuspensionStateChanged(
                pluginId = pluginId,
                pluginVersion = "",
                occurredAtEpochMillis = now,
                isSuspended = snapshot.isSuspended,
                failureScope = trigger?.wireValue ?: "plugin",
                sourceCode = if (snapshot.isSuspended) "failure_guard_suspended" else "failure_guard_recorded",
                consecutiveFailureCount = snapshot.consecutiveFailureCount,
                suspendedUntilEpochMillis = snapshot.suspendedUntilEpochMillis,
            )
        }
        if (trigger != null) {
            if (shouldProjectAggregateRecoveryFailed) {
                publishRecoveryFailed(
                    logBus = logBus,
                    pluginId = pluginId,
                    trigger = null,
                    category = category,
                    failureCount = aggregateAfter.consecutiveFailureCount,
                    now = now,
                )
            }
            if (!aggregateBefore.isSuspended && aggregateAfter.isSuspended) {
                publishFailureRecord(
                    logBus = logBus,
                    snapshot = aggregateAfter,
                    trigger = null,
                    category = category,
                    now = now,
                    errorSummary = errorSummary,
                )
                logBus.publishPluginSuspensionStateChanged(
                    pluginId = pluginId,
                    pluginVersion = "",
                    occurredAtEpochMillis = now,
                    isSuspended = true,
                    failureScope = "plugin",
                    sourceCode = "failure_guard_suspended",
                    consecutiveFailureCount = aggregateAfter.consecutiveFailureCount,
                    suspendedUntilEpochMillis = aggregateAfter.suspendedUntilEpochMillis,
                )
            }
        }
        return snapshot
    }

    fun recordSuccess(
        pluginId: String,
        trigger: PluginTriggerSource? = null,
    ): PluginFailureSnapshot {
        val current = if (trigger == null) {
            resolve(pluginId)
        } else {
            resolveScoped(pluginId, trigger)
        } ?: PluginFailureSnapshot(pluginId = pluginId, trigger = trigger)
        val aggregateBefore = if (trigger == null) {
            current
        } else {
            resolve(pluginId) ?: PluginFailureSnapshot(pluginId = pluginId)
        }
        val snapshot = current.copy(
            consecutiveFailureCount = 0,
            isSuspended = false,
            suspendedUntilEpochMillis = null,
        )
        saveSnapshot(snapshot)
        var aggregateAfter = snapshot
        if (trigger != null) {
            aggregateAfter = saveAggregateRecovery(pluginId)
        }
        if (current.consecutiveFailureCount > 0 || current.isSuspended) {
            val now = clock()
            logBus.publish(
                PluginRuntimeLogRecord(
                    occurredAtEpochMillis = now,
                    pluginId = pluginId,
                    trigger = trigger,
                    category = PluginRuntimeLogCategory.FailureGuard,
                    level = PluginRuntimeLogLevel.Info,
                    code = "failure_guard_recovered",
                    message = "Plugin failure state recovered.",
                    succeeded = true,
                    metadata = linkedMapOf(
                        "code" to "failure_guard_recovered",
                        "stage" to "FailureGuard",
                        "outcome" to "RECOVERED",
                        "failureCategory" to current.failureCategory.wireValue,
                        "failureScope" to (trigger?.wireValue ?: "plugin"),
                        "previousConsecutiveFailureCount" to current.consecutiveFailureCount.toString(),
                    ),
                ),
            )
            if (current.isSuspended) {
                logBus.publishPluginSuspensionStateChanged(
                    pluginId = pluginId,
                    pluginVersion = "",
                    occurredAtEpochMillis = now,
                    isSuspended = false,
                    failureScope = trigger?.wireValue ?: "plugin",
                    sourceCode = "failure_guard_recovered",
                    consecutiveFailureCount = snapshot.consecutiveFailureCount,
                    suspendedUntilEpochMillis = snapshot.suspendedUntilEpochMillis,
                )
            }
            if (trigger != null && aggregateBefore.hasFailuresForProjection() && !aggregateAfter.hasFailuresForProjection()) {
                logBus.publish(
                    PluginRuntimeLogRecord(
                        occurredAtEpochMillis = now,
                        pluginId = pluginId,
                        category = PluginRuntimeLogCategory.FailureGuard,
                        level = PluginRuntimeLogLevel.Info,
                        code = "failure_guard_recovered",
                        message = "Plugin failure state recovered.",
                        succeeded = true,
                        metadata = linkedMapOf(
                            "code" to "failure_guard_recovered",
                            "stage" to "FailureGuard",
                            "outcome" to "RECOVERED",
                            "failureCategory" to aggregateBefore.failureCategory.wireValue,
                            "failureScope" to "plugin",
                            "previousConsecutiveFailureCount" to aggregateBefore.consecutiveFailureCount.toString(),
                        ),
                    ),
                )
            }
            if (trigger != null && aggregateBefore.isSuspended != aggregateAfter.isSuspended) {
                logBus.publishPluginSuspensionStateChanged(
                    pluginId = pluginId,
                    pluginVersion = "",
                    occurredAtEpochMillis = now,
                    isSuspended = aggregateAfter.isSuspended,
                    failureScope = "plugin",
                    sourceCode = "failure_guard_recovered",
                    consecutiveFailureCount = aggregateAfter.consecutiveFailureCount,
                    suspendedUntilEpochMillis = aggregateAfter.suspendedUntilEpochMillis,
                )
            }
        }
        return snapshot
    }

    fun recover(pluginId: String): PluginFailureSnapshot {
        scopedStore.snapshot(pluginId)
            .filter { snapshot ->
                snapshot.consecutiveFailureCount > 0 ||
                    snapshot.isSuspended ||
                    snapshot.suspendedUntilEpochMillis != null
            }
            .sortedBy { snapshot -> snapshot.trigger?.wireValue.orEmpty() }
            .forEach { snapshot ->
                snapshot.trigger?.let { trigger ->
                    recordSuccess(
                        pluginId = pluginId,
                        trigger = trigger,
                    )
                }
            }
        return recordSuccess(pluginId = pluginId)
    }

    fun reset(
        pluginId: String,
        trigger: PluginTriggerSource? = null,
    ) {
        val hadState = if (trigger == null) {
            store.get(pluginId) != null
        } else {
            scopedStore.get(pluginId, trigger) != null
        }
        removeSnapshot(pluginId, trigger)
        if (hadState) {
            val now = clock()
            logBus.publish(
                PluginRuntimeLogRecord(
                    occurredAtEpochMillis = now,
                    pluginId = pluginId,
                    trigger = trigger,
                    category = PluginRuntimeLogCategory.FailureGuard,
                    level = PluginRuntimeLogLevel.Info,
                    code = "failure_guard_reset",
                    message = "Plugin failure state reset.",
                    succeeded = true,
                    metadata = linkedMapOf(
                        "code" to "failure_guard_reset",
                        "stage" to "FailureGuard",
                        "outcome" to "RESET",
                    ),
                ),
            )
        }
    }

    private fun resolve(pluginId: String): PluginFailureSnapshot? {
        val snapshot = store.get(pluginId) ?: return null
        return normalizeRecoveredSnapshot(
            snapshot = snapshot,
            save = { recovered -> store.put(recovered) },
        )
    }

    private fun resolveScoped(
        pluginId: String,
        trigger: PluginTriggerSource,
    ): PluginFailureSnapshot? {
        val snapshot = scopedStore.get(pluginId, trigger) ?: return null
        return normalizeRecoveredSnapshot(
            snapshot = snapshot,
            save = { recovered -> scopedStore.put(recovered) },
        )
    }

    private fun normalizeRecoveredSnapshot(
        snapshot: PluginFailureSnapshot,
        save: (PluginFailureSnapshot) -> Unit,
    ): PluginFailureSnapshot {
        if (!snapshot.isSuspended) return snapshot
        val suspendedUntil = snapshot.suspendedUntilEpochMillis ?: return snapshot
        if (clock() < suspendedUntil) return snapshot
        val recovered = snapshot.copy(
            consecutiveFailureCount = 0,
            isSuspended = false,
            suspendedUntilEpochMillis = null,
        )
        save(recovered)
        val now = clock()
        logBus.publish(
            PluginRuntimeLogRecord(
                occurredAtEpochMillis = now,
                pluginId = snapshot.pluginId,
                trigger = snapshot.trigger,
                category = PluginRuntimeLogCategory.FailureGuard,
                level = PluginRuntimeLogLevel.Info,
                code = "failure_guard_resumed",
                message = "Plugin suspension window expired and execution resumed.",
                succeeded = true,
                metadata = linkedMapOf(
                    "code" to "failure_guard_resumed",
                    "stage" to "FailureGuard",
                    "outcome" to "RECOVERED",
                    "failureScope" to (snapshot.trigger?.wireValue ?: "plugin"),
                ),
            ),
        )
        logBus.publishPluginSuspensionStateChanged(
            pluginId = snapshot.pluginId,
            pluginVersion = "",
            occurredAtEpochMillis = now,
            isSuspended = false,
            failureScope = snapshot.trigger?.wireValue ?: "plugin",
            sourceCode = "failure_guard_resumed",
            consecutiveFailureCount = recovered.consecutiveFailureCount,
            suspendedUntilEpochMillis = recovered.suspendedUntilEpochMillis,
        )
        return recovered
    }

    private fun evolveFailureSnapshot(
        current: PluginFailureSnapshot,
        pluginId: String,
        trigger: PluginTriggerSource?,
        errorSummary: String,
        category: PluginFailureCategory,
        now: Long,
    ): PluginFailureSnapshot {
        val failureCount = current.consecutiveFailureCount + 1
        val suspended = failureCount >= policy.maxConsecutiveFailures
        return PluginFailureSnapshot(
            pluginId = pluginId,
            trigger = trigger,
            consecutiveFailureCount = failureCount,
            lastFailureAtEpochMillis = now,
            lastErrorSummary = errorSummary.ifBlank { current.lastErrorSummary },
            failureCategory = category,
            isSuspended = suspended,
            suspendedUntilEpochMillis = if (suspended) now + policy.suspensionWindowMillis else null,
        )
    }

    private fun saveSnapshot(snapshot: PluginFailureSnapshot) {
        if (snapshot.lastFailureAtEpochMillis == null) {
            removeSnapshot(snapshot.pluginId, snapshot.trigger)
            return
        }
        if (snapshot.trigger == null) {
            store.put(snapshot)
        } else {
            scopedStore.put(snapshot)
        }
    }

    private fun removeSnapshot(
        pluginId: String,
        trigger: PluginTriggerSource?,
    ) {
        if (trigger == null) {
            store.remove(pluginId)
        } else {
            scopedStore.remove(pluginId, trigger)
        }
    }

    private fun saveAggregateFailure(
        pluginId: String,
        errorSummary: String,
        category: PluginFailureCategory,
        now: Long,
    ): PluginFailureSnapshot {
        val aggregate = evolveFailureSnapshot(
            current = resolve(pluginId) ?: PluginFailureSnapshot(pluginId = pluginId),
            pluginId = pluginId,
            trigger = null,
            errorSummary = errorSummary,
            category = category,
            now = now,
        )
        store.put(aggregate)
        return aggregate
    }

    private fun saveAggregateRecovery(pluginId: String): PluginFailureSnapshot {
        val current = resolve(pluginId) ?: PluginFailureSnapshot(pluginId = pluginId)
        val scopedSnapshots = scopedStore.snapshot(pluginId)
        val snapshot = if (scopedSnapshots.isEmpty()) {
            current.copy(
                consecutiveFailureCount = 0,
                isSuspended = false,
                suspendedUntilEpochMillis = null,
            )
        } else {
            val latestScoped = scopedSnapshots.maxByOrNull { scoped -> scoped.lastFailureAtEpochMillis ?: Long.MIN_VALUE }
            val failureCount = scopedSnapshots.sumOf(PluginFailureSnapshot::consecutiveFailureCount)
            val suspended = scopedSnapshots.any(PluginFailureSnapshot::isSuspended) ||
                failureCount >= policy.maxConsecutiveFailures
            current.copy(
                consecutiveFailureCount = failureCount,
                lastFailureAtEpochMillis = latestScoped?.lastFailureAtEpochMillis ?: current.lastFailureAtEpochMillis,
                lastErrorSummary = latestScoped?.lastErrorSummary?.ifBlank { current.lastErrorSummary } ?: current.lastErrorSummary,
                failureCategory = latestScoped?.failureCategory ?: current.failureCategory,
                isSuspended = suspended,
                suspendedUntilEpochMillis = if (suspended) {
                    scopedSnapshots.maxOfOrNull { scoped -> scoped.suspendedUntilEpochMillis ?: Long.MIN_VALUE }
                        ?.takeIf { until -> until != Long.MIN_VALUE }
                } else {
                    null
                },
            )
        }
        if (snapshot.hasFailuresForProjection()) {
            store.put(snapshot)
        } else {
            store.remove(pluginId)
        }
        return snapshot
    }
}

private fun PluginFailureState.toSnapshot(pluginId: String): PluginFailureSnapshot {
    val suspendedUntil = suspendedUntilEpochMillis
    val category = classifyFailure(lastErrorSummary)
    return PluginFailureSnapshot(
        pluginId = pluginId,
        consecutiveFailureCount = consecutiveFailureCount,
        lastFailureAtEpochMillis = lastFailureAtEpochMillis,
        lastErrorSummary = lastErrorSummary,
        failureCategory = category,
        isSuspended = suspendedUntil != null,
        suspendedUntilEpochMillis = suspendedUntil,
    )
}

private fun PluginFailureSnapshot.toFailureState(): PluginFailureState {
    return PluginFailureState(
        consecutiveFailureCount = consecutiveFailureCount,
        lastFailureAtEpochMillis = lastFailureAtEpochMillis,
        lastErrorSummary = lastErrorSummary,
        suspendedUntilEpochMillis = suspendedUntilEpochMillis,
    )
}

private fun repositoryFailureStateOrNull(pluginId: String): PluginFailureState? {
    return runCatching {
        FeaturePluginRepository.findByPluginId(pluginId)?.failureState
    }.getOrNull()
}

internal fun classifyFailure(errorSummary: String): PluginFailureCategory {
    val summary = errorSummary.trim()
    if (summary.isBlank()) return PluginFailureCategory.Unknown
    val normalized = summary.lowercase()
    return when {
        "timeout" in normalized ||
            "timed out" in normalized ||
            "deadline exceeded" in normalized -> PluginFailureCategory.Timeout
        "requires granted permission" in normalized ||
            "permission denied" in normalized -> PluginFailureCategory.PermissionDenied
        "payload." in normalized ||
            "invalid payload" in normalized -> PluginFailureCategory.InvalidPayload
        "not open for v1" in normalized ||
            "not in the trigger whitelist" in normalized ||
            "not open" in normalized -> PluginFailureCategory.UnsupportedAction
        else -> PluginFailureCategory.RuntimeError
    }
}

private fun PluginFailureSnapshot.canProjectRecoveryFailure(): Boolean {
    return !isSuspended &&
        consecutiveFailureCount == 0 &&
        lastFailureAtEpochMillis != null &&
        lastErrorSummary.isNotBlank()
}

private fun PluginFailureSnapshot.hasFailuresForProjection(): Boolean {
    return consecutiveFailureCount > 0 || isSuspended || suspendedUntilEpochMillis != null
}

private fun PluginRuntimeLogBus.hasRecoveredBoundary(
    pluginId: String,
    failureScope: String,
): Boolean {
    val latestScopedBoundary = snapshot(
        limit = 50,
        pluginId = pluginId,
        category = PluginRuntimeLogCategory.FailureGuard,
    ).firstOrNull { record ->
        record.metadata["failureScope"] == failureScope &&
            (
                record.code == "failure_guard_recovered" ||
                    record.code == "failure_guard_resumed" ||
                    record.code == "failure_guard_recovery_failed" ||
                    record.code == "failure_guard_suspended" ||
                    record.code == "failure_guard_recorded"
                )
    } ?: return false
    return latestScopedBoundary.code == "failure_guard_recovered" ||
        latestScopedBoundary.code == "failure_guard_resumed"
}

private fun publishFailureRecord(
    logBus: PluginRuntimeLogBus,
    snapshot: PluginFailureSnapshot,
    trigger: PluginTriggerSource?,
    category: PluginFailureCategory,
    now: Long,
    errorSummary: String,
) {
    logBus.publish(
        PluginRuntimeLogRecord(
            occurredAtEpochMillis = now,
            pluginId = snapshot.pluginId,
            trigger = trigger,
            category = PluginRuntimeLogCategory.FailureGuard,
            level = if (snapshot.isSuspended) PluginRuntimeLogLevel.Error else PluginRuntimeLogLevel.Warning,
            code = if (snapshot.isSuspended) "failure_guard_suspended" else "failure_guard_recorded",
            message = errorSummary.ifBlank { "Plugin failure recorded." },
            succeeded = false,
            metadata = linkedMapOf(
                "code" to if (snapshot.isSuspended) "failure_guard_suspended" else "failure_guard_recorded",
                "stage" to "FailureGuard",
                "outcome" to if (snapshot.isSuspended) "SUSPENDED" else "FAILED",
                "consecutiveFailureCount" to snapshot.consecutiveFailureCount.toString(),
                "failureCategory" to category.wireValue,
                "failureScope" to (trigger?.wireValue ?: "plugin"),
                "suspendedUntilEpochMillis" to (snapshot.suspendedUntilEpochMillis?.toString() ?: ""),
            ),
        ),
    )
}

private fun publishRecoveryFailed(
    logBus: PluginRuntimeLogBus,
    pluginId: String,
    trigger: PluginTriggerSource?,
    category: PluginFailureCategory,
    failureCount: Int,
    now: Long,
) {
    logBus.publish(
        PluginRuntimeLogRecord(
            occurredAtEpochMillis = now,
            pluginId = pluginId,
            trigger = trigger,
            category = PluginRuntimeLogCategory.FailureGuard,
            level = PluginRuntimeLogLevel.Warning,
            code = "failure_guard_recovery_failed",
            message = "Plugin recovery attempt failed.",
            succeeded = false,
            metadata = linkedMapOf(
                "code" to "failure_guard_recovery_failed",
                "stage" to "FailureGuard",
                "outcome" to "RECOVERY_FAILED",
                "failureCategory" to category.wireValue,
                "failureScope" to (trigger?.wireValue ?: "plugin"),
                "consecutiveFailureCount" to failureCount.toString(),
            ),
        ),
    )
}

