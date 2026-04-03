package com.astrbot.android.runtime.plugin

import com.astrbot.android.data.PluginRepository
import com.astrbot.android.model.plugin.PluginFailureState

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
    val consecutiveFailureCount: Int = 0,
    val lastFailureAtEpochMillis: Long? = null,
    val lastErrorSummary: String = "",
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
            PluginRepository.updateFailureState(pluginId, failureState)
        }
    },
    private val clearState: (String) -> Unit = { pluginId ->
        if (repositoryFailureStateOrNull(pluginId) != null) {
            PluginRepository.clearFailureState(pluginId)
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

object PluginRuntimeFailureStateStoreProvider {
    @Volatile
    private var storeOverrideForTests: PluginFailureStateStore? = null

    private val persistentStore: PluginFailureStateStore by lazy {
        PersistentPluginFailureStateStore()
    }

    fun store(): PluginFailureStateStore = storeOverrideForTests ?: persistentStore

    internal fun setStoreOverrideForTests(store: PluginFailureStateStore?) {
        storeOverrideForTests = store
    }
}

class PluginFailureGuard(
    private val store: PluginFailureStateStore = InMemoryPluginFailureStateStore(),
    private val policy: PluginFailurePolicy = PluginFailurePolicy(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun snapshot(pluginId: String): PluginFailureSnapshot {
        val current = resolve(pluginId)
        return current ?: PluginFailureSnapshot(pluginId = pluginId)
    }

    fun isSuspended(pluginId: String): Boolean = snapshot(pluginId).isSuspended

    fun recordFailure(
        pluginId: String,
        errorSummary: String = "",
    ): PluginFailureSnapshot {
        val current = resolve(pluginId) ?: PluginFailureSnapshot(pluginId = pluginId)
        val now = clock()
        val failureCount = current.consecutiveFailureCount + 1
        val suspended = failureCount >= policy.maxConsecutiveFailures
        val snapshot = PluginFailureSnapshot(
            pluginId = pluginId,
            consecutiveFailureCount = failureCount,
            lastFailureAtEpochMillis = now,
            lastErrorSummary = errorSummary.ifBlank { current.lastErrorSummary },
            isSuspended = suspended,
            suspendedUntilEpochMillis = if (suspended) now + policy.suspensionWindowMillis else null,
        )
        store.put(snapshot)
        return snapshot
    }

    fun recordSuccess(pluginId: String): PluginFailureSnapshot {
        val current = resolve(pluginId) ?: PluginFailureSnapshot(pluginId = pluginId)
        val snapshot = current.copy(
            consecutiveFailureCount = 0,
            isSuspended = false,
            suspendedUntilEpochMillis = null,
        )
        if (snapshot.lastFailureAtEpochMillis == null) {
            store.remove(pluginId)
        } else {
            store.put(snapshot)
        }
        return snapshot
    }

    fun reset(pluginId: String) {
        store.remove(pluginId)
    }

    private fun resolve(pluginId: String): PluginFailureSnapshot? {
        val snapshot = store.get(pluginId) ?: return null
        if (!snapshot.isSuspended) return snapshot
        val suspendedUntil = snapshot.suspendedUntilEpochMillis ?: return snapshot
        if (clock() < suspendedUntil) return snapshot
        val recovered = snapshot.copy(
            consecutiveFailureCount = 0,
            isSuspended = false,
            suspendedUntilEpochMillis = null,
        )
        store.put(recovered)
        return recovered
    }
}

private fun PluginFailureState.toSnapshot(pluginId: String): PluginFailureSnapshot {
    val suspendedUntil = suspendedUntilEpochMillis
    return PluginFailureSnapshot(
        pluginId = pluginId,
        consecutiveFailureCount = consecutiveFailureCount,
        lastFailureAtEpochMillis = lastFailureAtEpochMillis,
        lastErrorSummary = lastErrorSummary,
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
        PluginRepository.findByPluginId(pluginId)?.failureState
    }.getOrNull()
}
