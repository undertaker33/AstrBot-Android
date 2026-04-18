package com.astrbot.android.feature.plugin.runtime

import com.astrbot.android.data.PluginRepository
import com.astrbot.android.model.plugin.PluginLifecycleDiagnostic
import com.astrbot.android.model.plugin.PluginFailureCategory
import com.astrbot.android.model.plugin.PluginFailureState
import com.astrbot.android.model.plugin.PluginRuntimeLogCategory
import com.astrbot.android.model.plugin.PluginRuntimeLogLevel
import com.astrbot.android.model.plugin.PluginRuntimeLogRecord
import com.astrbot.android.model.plugin.PluginTriggerSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
        assertEquals(PluginFailureCategory.Timeout, afterFirstFailure.failureCategory)
        assertEquals(2, afterSecondFailure.consecutiveFailureCount)
        assertTrue(afterSecondFailure.isSuspended)
        assertEquals(5_050L, afterSecondFailure.lastFailureAtEpochMillis)
        assertEquals("socket timeout", afterSecondFailure.lastErrorSummary)
        assertEquals(PluginFailureCategory.Timeout, afterSecondFailure.failureCategory)
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
        assertEquals(PluginFailureCategory.RuntimeError, recovered.failureCategory)
        assertEquals(null, recovered.suspendedUntilEpochMillis)
    }

    @Test
    fun failure_guard_classifies_permission_and_payload_related_errors() {
        val guard = PluginFailureGuard(
            policy = PluginFailurePolicy(
                maxConsecutiveFailures = 5,
                suspensionWindowMillis = 1_000L,
            ),
            clock = { 1_000L },
        )

        val permissionFailure = guard.recordFailure(
            pluginId = "plugin-permission",
            errorSummary = "Host action SendNotification requires granted permission: send_notification",
        )
        val payloadFailure = guard.recordFailure(
            pluginId = "plugin-payload",
            errorSummary = "Host action OpenHostPage requires payload.route",
        )

        assertEquals(PluginFailureCategory.PermissionDenied, permissionFailure.failureCategory)
        assertEquals(PluginFailureCategory.InvalidPayload, payloadFailure.failureCategory)
    }

    @Test
    fun failure_guard_isolates_suspension_by_trigger_scope() {
        val clock = TestClock(now = 30_000L)
        val guard = PluginFailureGuard(
            policy = PluginFailurePolicy(
                maxConsecutiveFailures = 2,
                suspensionWindowMillis = 500L,
            ),
            clock = { clock.now },
        )

        guard.recordFailure(
            pluginId = "plugin-scoped",
            trigger = com.astrbot.android.model.plugin.PluginTriggerSource.BeforeSendMessage,
            errorSummary = "socket timeout",
        )
        guard.recordFailure(
            pluginId = "plugin-scoped",
            trigger = com.astrbot.android.model.plugin.PluginTriggerSource.BeforeSendMessage,
            errorSummary = "socket timeout",
        )

        assertTrue(
            guard.isSuspended(
                pluginId = "plugin-scoped",
                trigger = com.astrbot.android.model.plugin.PluginTriggerSource.BeforeSendMessage,
            ),
        )
        assertFalse(
            guard.isSuspended(
                pluginId = "plugin-scoped",
                trigger = com.astrbot.android.model.plugin.PluginTriggerSource.OnCommand,
            ),
        )
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
            failureCategory = PluginFailureCategory.Timeout,
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
            failureCategory = PluginFailureCategory.RuntimeError,
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

    @Test
    fun failure_guard_projects_suspended_recovered_and_recovery_failed_states_to_governance_repository() {
        val pluginId = "plugin-governance"
        val clock = TestClock(now = 1_000L)
        val bus = InMemoryPluginRuntimeLogBus(capacity = 32)
        val failureStore = InMemoryPluginFailureStateStore()
        installPluginRepositoryForTest(
            records = listOf(samplePluginV2InstallRecord(pluginId = pluginId)),
            initialized = true,
            now = clock.now,
        )
        val guard = PluginFailureGuard(
            store = failureStore,
            policy = PluginFailurePolicy(
                maxConsecutiveFailures = 2,
                suspensionWindowMillis = 100L,
            ),
            clock = { clock.now },
            logBus = bus,
        )
        val repository = PluginGovernanceRepository(
            findInstallRecord = PluginRepository::findByPluginId,
            runtimeSnapshotProvider = { PluginV2ActiveRuntimeSnapshot() },
            failureStateStore = failureStore,
            diagnosticsSnapshotProvider = { emptyList() },
            logBus = bus,
            clock = { clock.now },
        )

        guard.recordFailure(pluginId = pluginId, errorSummary = "first failure")
        guard.recordFailure(pluginId = pluginId, errorSummary = "second failure")

        val suspendedView = requireNotNull(repository.get(pluginId))
        assertEquals(
            PluginGovernanceRecoveryStatus.SUSPENDED,
            suspendedView.failureProjection.status,
        )
        assertEquals(
            listOf("plugin_suspension_state_changed"),
            bus.snapshot(pluginId = pluginId, code = "plugin_suspension_state_changed")
                .map { record -> record.code },
        )

        clock.advanceBy(150L)
        guard.snapshot(pluginId)

        val recoveredView = requireNotNull(repository.get(pluginId))
        assertEquals(
            PluginGovernanceRecoveryStatus.RECOVERED,
            recoveredView.failureProjection.status,
        )

        guard.recordFailure(pluginId = pluginId, errorSummary = "recovery failed immediately")

        val recoveryFailedView = requireNotNull(repository.get(pluginId))
        assertEquals(
            PluginGovernanceRecoveryStatus.RECOVERY_FAILED,
            recoveryFailedView.failureProjection.status,
        )
        assertEquals(
            "failure_guard_recovery_failed",
            recoveryFailedView.failureProjection.lastTransitionCode,
        )
    }

    @Test
    fun governance_repository_merges_failure_state_observability_summary_and_diagnostics_without_self_pollution() {
        val pluginId = "plugin-governance-merged"
        val clock = TestClock(now = 5_000L)
        val bus = InMemoryPluginRuntimeLogBus(capacity = 32)
        val failureStore = InMemoryPluginFailureStateStore().apply {
            put(
                PluginFailureSnapshot(
                    pluginId = pluginId,
                    consecutiveFailureCount = 2,
                    lastFailureAtEpochMillis = 4_900L,
                    lastErrorSummary = "permission denied",
                    failureCategory = PluginFailureCategory.PermissionDenied,
                    isSuspended = true,
                    suspendedUntilEpochMillis = 5_500L,
                ),
            )
        }
        val lifecycleDiagnostics = listOf(
            PluginLifecycleDiagnostic(
                pluginId = pluginId,
                hook = "on_plugin_loaded",
                code = "plugin_suspension_state_changed",
                message = "Plugin suspended for governance projection.",
                occurredAtEpochMillis = 4_950L,
            ),
        )
        installPluginRepositoryForTest(
            records = listOf(samplePluginV2InstallRecord(pluginId = pluginId)),
            initialized = true,
            now = clock.now,
        )
        bus.publish(
            PluginRuntimeLogRecord(
                occurredAtEpochMillis = 4_960L,
                pluginId = pluginId,
                category = PluginRuntimeLogCategory.FailureGuard,
                level = PluginRuntimeLogLevel.Warning,
                code = "failure_guard_suspended",
                message = "Plugin suspended.",
                succeeded = false,
                metadata = linkedMapOf(
                    "code" to "failure_guard_suspended",
                    "requestId" to "req-existing",
                    "toolCallId" to "call-existing",
                    "stage" to "FailureGuard",
                    "outcome" to "SUSPENDED",
                ),
            ),
        )

        val repository = PluginGovernanceRepository(
            findInstallRecord = PluginRepository::findByPluginId,
            runtimeSnapshotProvider = { PluginV2ActiveRuntimeSnapshot() },
            failureStateStore = failureStore,
            diagnosticsSnapshotProvider = { lifecycleDiagnostics },
            logBus = bus,
            clock = { clock.now },
        )

        val view = repository.get(pluginId)

        assertNotNull(view)
        requireNotNull(view)
        assertEquals(pluginId, view.snapshot.pluginId)
        assertEquals(view.snapshot.diagnosticsSummary, view.diagnosticsSummary)
        assertEquals(1, view.observabilitySummary.totalCount)
        assertEquals("failure_guard_suspended", view.observabilitySummary.lastCode)
        assertEquals(PluginRuntimeLogLevel.Warning, view.observabilitySummary.lastLevel)
        assertEquals(4_960L, view.observabilitySummary.lastOccurredAtEpochMillis)
        assertEquals(1, view.observabilitySummary.failureCount)
        assertEquals(0, view.observabilitySummary.recoveryCount)
        assertEquals("req-existing", view.observabilitySummary.latestRequestId)
        assertEquals("call-existing", view.observabilitySummary.latestToolCallId)
        assertEquals(listOf("failure_guard_suspended"), view.recentLogs.map { record -> record.code })
        assertEquals(
            PluginGovernanceRecoveryStatus.SUSPENDED,
            view.failureProjection.status,
        )
        assertEquals("permission denied", view.failureProjection.lastErrorSummary)
        assertEquals(1, view.diagnosticsSummary.lifecycleDiagnosticCount)
        assertTrue(bus.snapshot(pluginId = pluginId).any { record -> record.code == "governance_snapshot_refreshed" })
        assertTrue(bus.snapshot(pluginId = pluginId).any { record -> record.code == "plugin_runtime_health_projected" })
    }

    @Test
    fun failure_guard_emits_scoped_and_plugin_wide_observability_for_multi_trigger_suspension_recovery_and_recovery_failed() {
        val pluginId = "plugin-multi-trigger"
        val clock = TestClock(now = 10_000L)
        val bus = InMemoryPluginRuntimeLogBus(capacity = 64)
        val guard = PluginFailureGuard(
            store = InMemoryPluginFailureStateStore(),
            scopedStore = InMemoryPluginScopedFailureStateStore(),
            policy = PluginFailurePolicy(
                maxConsecutiveFailures = 2,
                suspensionWindowMillis = 100L,
            ),
            clock = { clock.now },
            logBus = bus,
        )

        guard.recordFailure(
            pluginId = pluginId,
            trigger = PluginTriggerSource.OnCommand,
            errorSummary = "failure-1",
        )
        guard.recordFailure(
            pluginId = pluginId,
            trigger = PluginTriggerSource.BeforeSendMessage,
            errorSummary = "failure-2",
        )
        guard.recordFailure(
            pluginId = pluginId,
            trigger = PluginTriggerSource.OnCommand,
            errorSummary = "failure-3",
        )

        val suspensionChanges = bus.snapshot(pluginId = pluginId, code = "plugin_suspension_state_changed")
        assertTrue(
            suspensionChanges.any { record -> record.metadata["failureScope"] == PluginTriggerSource.OnCommand.wireValue },
        )
        assertTrue(
            suspensionChanges.any { record -> record.metadata["failureScope"] == "plugin" },
        )
        assertTrue(guard.isSuspended(pluginId))

        clock.advanceBy(150L)
        guard.snapshot(pluginId = pluginId)
        guard.recordSuccess(
            pluginId = pluginId,
            trigger = PluginTriggerSource.OnCommand,
        )
        guard.recordSuccess(
            pluginId = pluginId,
            trigger = PluginTriggerSource.BeforeSendMessage,
        )

        val recoveredEvents = bus.snapshot(pluginId = pluginId, code = "failure_guard_recovered")
        assertTrue(recoveredEvents.any { record -> record.metadata["failureScope"] == "plugin" })

        guard.recordFailure(
            pluginId = pluginId,
            trigger = PluginTriggerSource.BeforeSendMessage,
            errorSummary = "aggregate recovery failed",
        )

        val recoveryFailedEvents = bus.snapshot(pluginId = pluginId, code = "failure_guard_recovery_failed")
        assertTrue(recoveryFailedEvents.any { record -> record.metadata["failureScope"] == "plugin" })
    }

    @Test
    fun recover_clears_plugin_and_scoped_suspension_without_dropping_recovery_observability() {
        val pluginId = "plugin-manual-recover"
        val clock = TestClock(now = 20_000L)
        val bus = InMemoryPluginRuntimeLogBus(capacity = 64)
        val guard = PluginFailureGuard(
            store = InMemoryPluginFailureStateStore(),
            scopedStore = InMemoryPluginScopedFailureStateStore(),
            policy = PluginFailurePolicy(
                maxConsecutiveFailures = 2,
                suspensionWindowMillis = 500L,
            ),
            clock = { clock.now },
            logBus = bus,
        )

        guard.recordFailure(
            pluginId = pluginId,
            trigger = PluginTriggerSource.BeforeSendMessage,
            errorSummary = "socket timeout",
        )
        guard.recordFailure(
            pluginId = pluginId,
            trigger = PluginTriggerSource.BeforeSendMessage,
            errorSummary = "socket timeout",
        )

        assertTrue(guard.isSuspended(pluginId))
        assertTrue(guard.isSuspended(pluginId, PluginTriggerSource.BeforeSendMessage))

        val recovered = guard.recover(pluginId)

        assertFalse(recovered.isSuspended)
        assertEquals(0, recovered.consecutiveFailureCount)
        assertFalse(guard.isSuspended(pluginId))
        assertFalse(guard.isSuspended(pluginId, PluginTriggerSource.BeforeSendMessage))

        val recoveredEvents = bus.snapshot(pluginId = pluginId, code = "failure_guard_recovered")
        assertTrue(recoveredEvents.any { record -> record.metadata["failureScope"] == PluginTriggerSource.BeforeSendMessage.wireValue })
        assertTrue(recoveredEvents.any { record -> record.metadata["failureScope"] == "plugin" })

        val suspensionChanges = bus.snapshot(pluginId = pluginId, code = "plugin_suspension_state_changed")
        assertTrue(
            suspensionChanges.any { record ->
                record.metadata["failureScope"] == PluginTriggerSource.BeforeSendMessage.wireValue &&
                    record.metadata["currentState"] == "NOT_SUSPENDED"
            },
        )
        assertTrue(
            suspensionChanges.any { record ->
                record.metadata["failureScope"] == "plugin" &&
                    record.metadata["currentState"] == "NOT_SUSPENDED"
            },
        )
    }
}
