package com.astrbot.android.runtime.plugin

import com.astrbot.android.data.PluginRepository
import com.astrbot.android.model.plugin.PluginDiagnosticsSummary
import com.astrbot.android.model.plugin.PluginGovernanceSnapshot
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginLifecycleDiagnostic
import com.astrbot.android.model.plugin.PluginLifecycleDiagnosticsStore
import com.astrbot.android.model.plugin.PluginRuntimeLogRecord
import com.astrbot.android.model.plugin.PluginSuspensionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

enum class PluginGovernanceRecoveryStatus {
    IDLE,
    TRACKING_FAILURES,
    SUSPENDED,
    RECOVERED,
    RECOVERY_FAILED,
}

data class PluginGovernanceFailureProjection(
    val pluginId: String,
    val status: PluginGovernanceRecoveryStatus = PluginGovernanceRecoveryStatus.IDLE,
    val lastTransitionCode: String = "",
    val lastTransitionAtEpochMillis: Long? = null,
    val consecutiveFailureCount: Int = 0,
    val suspendedUntilEpochMillis: Long? = null,
    val lastErrorSummary: String = "",
)

data class PluginGovernanceReadModel(
    val snapshot: PluginGovernanceSnapshot,
    val observabilitySummary: PluginObservabilitySummary,
    val recentLogs: List<PluginRuntimeLogRecord>,
    val diagnosticsSummary: PluginDiagnosticsSummary,
    val lifecycleDiagnostics: List<PluginLifecycleDiagnostic>,
    val failureProjection: PluginGovernanceFailureProjection,
)

class PluginGovernanceRepository(
    private val installRecordsFlow: Flow<List<PluginInstallRecord>> = PluginRepository.records,
    private val findInstallRecord: (String) -> PluginInstallRecord? = PluginRepository::findByPluginId,
    private val runtimeSnapshotProvider: () -> PluginV2ActiveRuntimeSnapshot = { PluginV2ActiveRuntimeStoreProvider.store().snapshot() },
    private val failureStateStore: PluginFailureStateStore = PluginRuntimeFailureStateStoreProvider.store(),
    private val diagnosticsSnapshotProvider: () -> List<PluginLifecycleDiagnostic> = PluginLifecycleDiagnosticsStore::snapshot,
    private val logBus: PluginRuntimeLogBus = PluginRuntimeLogBusProvider.bus(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun get(
        pluginId: String,
        logLimit: Int = 20,
    ): PluginGovernanceReadModel? {
        val installRecord = findInstallRecord(pluginId) ?: return null
        return buildReadModel(
            installRecord = installRecord,
            logLimit = logLimit,
            publishProjectionLogs = true,
        )
    }

    fun getSilently(
        pluginId: String,
        logLimit: Int = 20,
    ): PluginGovernanceReadModel? {
        val installRecord = findInstallRecord(pluginId) ?: return null
        return buildReadModel(
            installRecord = installRecord,
            logLimit = logLimit,
            publishProjectionLogs = false,
        )
    }

    fun observeReadModels(
        logLimit: Int = 20,
    ): Flow<Map<String, PluginGovernanceReadModel>> {
        return combine(installRecordsFlow, logBus.records) { recordsSnapshot, _ ->
            recordsSnapshot.mapNotNull { record ->
                buildReadModel(
                    installRecord = record,
                    logLimit = logLimit,
                    publishProjectionLogs = false,
                )?.let { record.pluginId to it }
            }.toMap()
        }
    }

    private fun buildReadModel(
        installRecord: PluginInstallRecord,
        logLimit: Int,
        publishProjectionLogs: Boolean,
    ): PluginGovernanceReadModel? {
        val pluginId = installRecord.pluginId
        val runtimeSnapshot = runtimeSnapshotProvider()
        val lifecycleDiagnostics = diagnosticsSnapshotProvider()
            .filter { diagnostic -> diagnostic.pluginId == pluginId }
        val failureSnapshot = failureStateStore.get(pluginId)
        val recentLogs = logBus.snapshot(
            limit = logLimit,
            pluginId = pluginId,
        )
        val observabilitySummary = logBus.summary(
            pluginId = pluginId,
            limit = logLimit,
        )
        val snapshot = PluginGovernanceSnapshotMapper.map(
            installRecord = installRecord,
            runtimeSnapshot = runtimeSnapshot,
            failureSnapshot = failureSnapshot,
            lifecycleDiagnostics = lifecycleDiagnostics,
            clock = clock,
            logBus = logBus,
            publishProjectionLogs = publishProjectionLogs,
        )
        return PluginGovernanceReadModel(
            snapshot = snapshot,
            observabilitySummary = observabilitySummary,
            recentLogs = recentLogs,
            diagnosticsSummary = snapshot.diagnosticsSummary,
            lifecycleDiagnostics = lifecycleDiagnostics,
            failureProjection = projectFailureProjection(
                pluginId = pluginId,
                snapshot = snapshot,
                failureSnapshot = failureSnapshot,
                recentLogs = recentLogs,
            ),
        )
    }
}

@Suppress("UNUSED_PARAMETER")
private fun projectFailureProjection(
    pluginId: String,
    snapshot: PluginGovernanceSnapshot,
    failureSnapshot: PluginFailureSnapshot?,
    recentLogs: List<PluginRuntimeLogRecord>,
): PluginGovernanceFailureProjection {
    val transitionRecord = recentLogs.firstOrNull { record ->
        record.code == "failure_guard_recovery_failed" ||
            record.code == "failure_guard_recovered" ||
            record.code == "failure_guard_resumed" ||
            record.code == "plugin_recovery_completed" ||
            record.code == "plugin_recovery_failed" ||
            record.code == "plugin_suspension_state_changed"
    }
    val activeFailureCount = snapshot.autoRecoveryState.consecutiveFailureCount
    val autoRecoveryStatus = snapshot.autoRecoveryState.status
    val hasActiveFailure = snapshot.suspensionState == PluginSuspensionState.SUSPENDED ||
        autoRecoveryStatus == "SUSPENDED" ||
        (autoRecoveryStatus == "TRACKING_FAILURES" && activeFailureCount > 0)
    val status = when {
        hasActiveFailure &&
            (
                transitionRecord?.code == "failure_guard_recovery_failed" ||
                    transitionRecord?.code == "plugin_recovery_failed"
                ) -> PluginGovernanceRecoveryStatus.RECOVERY_FAILED
        snapshot.suspensionState == PluginSuspensionState.SUSPENDED ||
            autoRecoveryStatus == "SUSPENDED" -> PluginGovernanceRecoveryStatus.SUSPENDED
        transitionRecord?.code == "failure_guard_recovered" ||
            transitionRecord?.code == "failure_guard_resumed" ||
            transitionRecord?.code == "plugin_recovery_completed" -> PluginGovernanceRecoveryStatus.RECOVERED
        transitionRecord?.code == "plugin_suspension_state_changed" &&
            transitionRecord.metadata["currentState"] == "NOT_SUSPENDED" -> PluginGovernanceRecoveryStatus.RECOVERED
        autoRecoveryStatus == "TRACKING_FAILURES" && activeFailureCount > 0 -> {
            PluginGovernanceRecoveryStatus.TRACKING_FAILURES
        }
        else -> PluginGovernanceRecoveryStatus.IDLE
    }
    return PluginGovernanceFailureProjection(
        pluginId = pluginId,
        status = status,
        lastTransitionCode = transitionRecord?.code.orEmpty(),
        lastTransitionAtEpochMillis = transitionRecord?.occurredAtEpochMillis,
        consecutiveFailureCount = snapshot.autoRecoveryState.consecutiveFailureCount,
        suspendedUntilEpochMillis = snapshot.autoRecoveryState.suspendedUntilEpochMillis,
        lastErrorSummary = snapshot.lastFailure?.summary.orEmpty(),
    )
}
