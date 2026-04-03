package com.astrbot.android.runtime.plugin

import com.astrbot.android.data.PluginRepository
import com.astrbot.android.model.plugin.PluginFailureState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginFailureGuardTest {
    @Test
    fun failure_guard_records_error_summary_and_enters_suspension_after_reaching_failure_threshold() {
        val clock = TestClock(now = 5_000L)
        val guard = PluginFailureGuard(
            policy = PluginFailurePolicy(
                maxConsecutiveFailures = 2,
                suspensionWindowMillis = 300L,
            ),
            clock = { clock.now },
        )

        val afterFirstFailure = guard.recordFailure(
            pluginId = "plugin-a",
            errorSummary = "socket timeout",
        )
        clock.advanceBy(50L)
        val afterSecondFailure = guard.recordFailure(
            pluginId = "plugin-a",
            errorSummary = "socket timeout",
        )

        assertEquals(1, afterFirstFailure.consecutiveFailureCount)
        assertFalse(afterFirstFailure.isSuspended)
        assertEquals("socket timeout", afterFirstFailure.lastErrorSummary)
        assertEquals(2, afterSecondFailure.consecutiveFailureCount)
        assertTrue(afterSecondFailure.isSuspended)
        assertEquals(5_050L, afterSecondFailure.lastFailureAtEpochMillis)
        assertEquals("socket timeout", afterSecondFailure.lastErrorSummary)
        assertEquals(5_350L, afterSecondFailure.suspendedUntilEpochMillis)
    }

    @Test
    fun failure_guard_recovers_after_suspension_window_expires_and_preserves_recent_failure_details() {
        val clock = TestClock(now = 10_000L)
        val guard = PluginFailureGuard(
            policy = PluginFailurePolicy(
                maxConsecutiveFailures = 2,
                suspensionWindowMillis = 200L,
            ),
            clock = { clock.now },
        )
        guard.recordFailure(
            pluginId = "plugin-b",
            errorSummary = "plugin exploded",
        )
        guard.recordFailure(
            pluginId = "plugin-b",
            errorSummary = "plugin exploded",
        )

        assertTrue(guard.isSuspended("plugin-b"))

        clock.advanceBy(250L)
        val recovered = guard.snapshot("plugin-b")

        assertFalse(guard.isSuspended("plugin-b"))
        assertFalse(recovered.isSuspended)
        assertEquals(0, recovered.consecutiveFailureCount)
        assertEquals(10_000L, recovered.lastFailureAtEpochMillis)
        assertEquals("plugin exploded", recovered.lastErrorSummary)
        assertEquals(null, recovered.suspendedUntilEpochMillis)
    }

    @Test
    fun persistent_failure_store_round_trips_through_plugin_repository() {
        val record = samplePluginInstallRecord(
            pluginId = "plugin-persistent",
            version = "1.0.0",
            lastUpdatedAt = 100L,
        )
        installPluginRepositoryForTest(
            records = listOf(record),
            initialized = true,
            now = 200L,
        )
        val store = PersistentPluginFailureStateStore()
        val snapshot = PluginFailureSnapshot(
            pluginId = record.pluginId,
            consecutiveFailureCount = 2,
            lastFailureAtEpochMillis = 150L,
            lastErrorSummary = "socket timeout",
            isSuspended = true,
            suspendedUntilEpochMillis = 300L,
        )

        store.put(snapshot)

        assertEquals(snapshot, store.get(record.pluginId))
        assertEquals(
            PluginFailureState(
                consecutiveFailureCount = 2,
                lastFailureAtEpochMillis = 150L,
                lastErrorSummary = "socket timeout",
                suspendedUntilEpochMillis = 300L,
            ),
            PluginRepository.findByPluginId(record.pluginId)?.failureState,
        )
    }

    @Test
    fun persistent_failure_store_safely_degrades_when_repository_is_uninitialized() {
        val record = samplePluginInstallRecord(
            pluginId = "plugin-offline",
            version = "1.0.0",
            lastUpdatedAt = 10L,
        )
        installPluginRepositoryForTest(
            records = listOf(record),
            initialized = false,
            now = 20L,
        )
        val store = PersistentPluginFailureStateStore()
        val snapshot = PluginFailureSnapshot(
            pluginId = record.pluginId,
            consecutiveFailureCount = 1,
            lastFailureAtEpochMillis = 20L,
            lastErrorSummary = "bootstrap unavailable",
            isSuspended = false,
            suspendedUntilEpochMillis = null,
        )

        val failure = runCatching {
            store.put(snapshot)
            store.remove(record.pluginId)
            store.get(record.pluginId)
        }.exceptionOrNull()

        assertEquals(null, failure)
        assertEquals(null, store.get(record.pluginId))
    }
}
