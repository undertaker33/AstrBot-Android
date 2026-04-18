package com.astrbot.android.feature.plugin.runtime

import com.astrbot.android.model.plugin.PluginRuntimeLogCategory
import com.astrbot.android.model.plugin.PluginRuntimeLogLevel
import com.astrbot.android.model.plugin.PluginRuntimeLogRecord
import com.astrbot.android.model.plugin.PluginRuntimeHealthStatus
import com.astrbot.android.model.plugin.PluginSuspensionState
import com.astrbot.android.model.plugin.PluginFailureCategory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PluginGovernanceRepositoryTest {

    @Test
    fun getSilently_does_not_emit_projection_feedback_logs() = runTest {
        val pluginId = "com.example.governance.silent"
        val recordsFlow = MutableStateFlow(
            listOf(samplePluginV2InstallRecord(pluginId = pluginId)),
        )
        val bus = InMemoryPluginRuntimeLogBus(capacity = 16)
        val repository = PluginGovernanceRepository(
            installRecordsFlow = recordsFlow,
            findInstallRecord = { requestedId ->
                recordsFlow.value.firstOrNull { record -> record.pluginId == requestedId }
            },
            runtimeSnapshotProvider = { PluginV2ActiveRuntimeSnapshot() },
            failureStateStore = InMemoryPluginFailureStateStore(),
            diagnosticsSnapshotProvider = { emptyList() },
            logBus = bus,
            clock = { 1_000L },
        )

        val readModel = repository.getSilently(pluginId)

        assertEquals(pluginId, readModel?.snapshot?.pluginId)
        assertEquals(emptyList<String>(), bus.snapshot(pluginId = pluginId).map(PluginRuntimeLogRecord::code))
    }

    @Test
    fun observeReadModels_reacts_to_runtime_logs_without_emitting_projection_feedback_logs() = runTest {
        val pluginId = "com.example.governance.observe"
        val recordsFlow = MutableStateFlow(
            listOf(samplePluginV2InstallRecord(pluginId = pluginId)),
        )
        val bus = InMemoryPluginRuntimeLogBus(capacity = 16)
        val repository = PluginGovernanceRepository(
            installRecordsFlow = recordsFlow,
            findInstallRecord = { requestedId ->
                recordsFlow.value.firstOrNull { record -> record.pluginId == requestedId }
            },
            runtimeSnapshotProvider = { PluginV2ActiveRuntimeSnapshot() },
            failureStateStore = InMemoryPluginFailureStateStore(),
            diagnosticsSnapshotProvider = { emptyList() },
            logBus = bus,
            clock = { 1_000L },
        )

        val initialReadModels = repository.observeReadModels().first()
        assertEquals(pluginId, initialReadModels.getValue(pluginId).snapshot.pluginId)
        assertEquals(emptyList<String>(), bus.snapshot(pluginId = pluginId).map(PluginRuntimeLogRecord::code))

        bus.publish(
            PluginRuntimeLogRecord(
                occurredAtEpochMillis = 1_050L,
                pluginId = pluginId,
                category = PluginRuntimeLogCategory.Dispatcher,
                level = PluginRuntimeLogLevel.Warning,
                code = "failure_guard_suspended",
                message = "Plugin suspended by failure guard.",
            ),
        )

        val latestReadModel = repository.observeReadModels().first().getValue(pluginId)
        assertEquals("failure_guard_suspended", latestReadModel.observabilitySummary.lastCode)
        assertEquals(listOf("failure_guard_suspended"), latestReadModel.recentLogs.map(PluginRuntimeLogRecord::code))
        assertEquals(
            listOf("failure_guard_suspended"),
            bus.snapshot(pluginId = pluginId).map(PluginRuntimeLogRecord::code),
        )
    }

    @Test
    fun expired_suspension_projects_idle_even_when_raw_failure_snapshot_still_has_history() = runTest {
        val pluginId = "com.example.governance.expired"
        val recordsFlow = MutableStateFlow(
            listOf(samplePluginV2InstallRecord(pluginId = pluginId)),
        )
        val failureStore = InMemoryPluginFailureStateStore().apply {
            put(
                PluginFailureSnapshot(
                    pluginId = pluginId,
                    consecutiveFailureCount = 3,
                    lastFailureAtEpochMillis = 500L,
                    lastErrorSummary = "old socket timeout",
                    failureCategory = PluginFailureCategory.Timeout,
                    isSuspended = true,
                    suspendedUntilEpochMillis = 900L,
                ),
            )
        }
        val repository = PluginGovernanceRepository(
            installRecordsFlow = recordsFlow,
            findInstallRecord = { requestedId ->
                recordsFlow.value.firstOrNull { record -> record.pluginId == requestedId }
            },
            runtimeSnapshotProvider = { PluginV2ActiveRuntimeSnapshot() },
            failureStateStore = failureStore,
            diagnosticsSnapshotProvider = { emptyList() },
            logBus = InMemoryPluginRuntimeLogBus(capacity = 16),
            clock = { 1_000L },
        )

        val readModel = requireNotNull(repository.getSilently(pluginId))

        assertEquals(PluginSuspensionState.NOT_SUSPENDED, readModel.snapshot.suspensionState)
        assertEquals("IDLE", readModel.snapshot.autoRecoveryState.status)
        assertEquals(0, readModel.snapshot.autoRecoveryState.consecutiveFailureCount)
        assertEquals(PluginGovernanceRecoveryStatus.IDLE, readModel.failureProjection.status)
        assertEquals(0, readModel.failureProjection.consecutiveFailureCount)
        assertEquals("old socket timeout", readModel.failureProjection.lastErrorSummary)
    }

    @Test
    fun recovered_failure_history_without_recent_logs_does_not_project_active_failure() = runTest {
        val pluginId = "com.example.governance.recovered.evicted"
        val recordsFlow = MutableStateFlow(
            listOf(samplePluginV2InstallRecord(pluginId = pluginId)),
        )
        val failureStore = InMemoryPluginFailureStateStore().apply {
            put(
                PluginFailureSnapshot(
                    pluginId = pluginId,
                    consecutiveFailureCount = 0,
                    lastFailureAtEpochMillis = 500L,
                    lastErrorSummary = "transient bootstrap error",
                    failureCategory = PluginFailureCategory.RuntimeError,
                    isSuspended = false,
                    suspendedUntilEpochMillis = null,
                ),
            )
        }
        val repository = PluginGovernanceRepository(
            installRecordsFlow = recordsFlow,
            findInstallRecord = { requestedId ->
                recordsFlow.value.firstOrNull { record -> record.pluginId == requestedId }
            },
            runtimeSnapshotProvider = { PluginV2ActiveRuntimeSnapshot() },
            failureStateStore = failureStore,
            diagnosticsSnapshotProvider = { emptyList() },
            logBus = InMemoryPluginRuntimeLogBus(capacity = 1),
            clock = { 1_000L },
        )

        val readModel = requireNotNull(repository.getSilently(pluginId))

        assertEquals(PluginRuntimeHealthStatus.Disabled, readModel.snapshot.runtimeHealth.status)
        assertEquals(PluginSuspensionState.NOT_SUSPENDED, readModel.snapshot.suspensionState)
        assertEquals("IDLE", readModel.snapshot.autoRecoveryState.status)
        assertEquals(PluginGovernanceRecoveryStatus.IDLE, readModel.failureProjection.status)
        assertEquals(0, readModel.failureProjection.consecutiveFailureCount)
        assertEquals("transient bootstrap error", readModel.failureProjection.lastErrorSummary)
    }
}
