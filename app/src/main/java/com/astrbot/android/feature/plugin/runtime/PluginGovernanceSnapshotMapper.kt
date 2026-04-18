package com.astrbot.android.feature.plugin.runtime

import com.astrbot.android.model.plugin.PluginCompatibilityStatus
import com.astrbot.android.model.plugin.PluginConfigStateSummary
import com.astrbot.android.model.plugin.PluginCapabilityGrantSnapshot
import com.astrbot.android.model.plugin.PluginDiagnosticsSummary
import com.astrbot.android.model.plugin.PluginAutoRecoveryStateSnapshot
import com.astrbot.android.model.plugin.PluginBootstrapStatusSnapshot
import com.astrbot.android.model.plugin.PluginGovernanceFailureSnapshot
import com.astrbot.android.model.plugin.PluginGovernanceSnapshot
import com.astrbot.android.model.plugin.PluginLifecycleDiagnostic
import com.astrbot.android.model.plugin.PluginLifecycleDiagnosticsStore
import com.astrbot.android.model.plugin.PluginRegistrationSummary
import com.astrbot.android.model.plugin.PluginReviewState
import com.astrbot.android.model.plugin.PluginRiskLevel
import com.astrbot.android.model.plugin.PluginRuntimeHealthSnapshot
import com.astrbot.android.model.plugin.PluginRuntimeHealthStatus
import com.astrbot.android.model.plugin.PluginSourceType
import com.astrbot.android.model.plugin.PluginSuspensionState
import com.astrbot.android.model.plugin.PluginTrustLevel
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginWorkspaceStateSummary

object PluginGovernanceSnapshotMapper {
    fun map(
        installRecord: PluginInstallRecord,
        runtimeSnapshot: PluginV2ActiveRuntimeSnapshot = PluginV2ActiveRuntimeStoreProvider.store().snapshot(),
        failureSnapshot: PluginFailureSnapshot? = PluginRuntimeFailureStateStoreProvider.store().get(installRecord.pluginId),
        lifecycleDiagnostics: List<PluginLifecycleDiagnostic> = PluginLifecycleDiagnosticsStore.snapshot(),
        clock: () -> Long = System::currentTimeMillis,
        logBus: PluginRuntimeLogBus = PluginRuntimeLogBusProvider.bus(),
        publishProjectionLogs: Boolean = true,
    ): PluginGovernanceSnapshot {
        val pluginId = installRecord.pluginId
        val bootstrapDiagnostics = runtimeSnapshot.diagnosticsByPluginId[pluginId].orEmpty()
        val bootstrapSummary = runtimeSnapshot.lastBootstrapSummariesByPluginId[pluginId]
        val runtimeSession = runtimeSnapshot.activeSessionsByPluginId[pluginId]
        val normalizedFailureSnapshot = failureSnapshot.normalizeForGovernance(clock())
        val toolDiagnostics = runtimeSnapshot.toolRegistryDiagnostics.filter { diagnostic ->
            diagnostic.pluginId == pluginId
        }
        val bootstrapStatus = bootstrapSummary.toBootstrapStatus()
        val runtimeHealth = resolveRuntimeHealth(
            installRecord = installRecord,
            runtimeSession = runtimeSession,
            bootstrapStatus = bootstrapStatus,
            failureSnapshot = normalizedFailureSnapshot,
        )
        val diagnosticsSummary = PluginDiagnosticsSummary(
            bootstrapWarningCount = bootstrapDiagnostics
                .takeIf(List<PluginV2CompilerDiagnostic>::isNotEmpty)
                ?.count { diagnostic -> diagnostic.severity == DiagnosticSeverity.Warning }
                ?: bootstrapSummary?.warningCount
                ?: 0,
            bootstrapErrorCount = bootstrapDiagnostics
                .takeIf(List<PluginV2CompilerDiagnostic>::isNotEmpty)
                ?.count { diagnostic -> diagnostic.severity == DiagnosticSeverity.Error }
                ?: bootstrapSummary?.errorCount
                ?: 0,
            lifecycleDiagnosticCount = lifecycleDiagnostics.count { diagnostic ->
                diagnostic.pluginId == pluginId
            },
            toolWarningCount = toolDiagnostics.count { diagnostic ->
                diagnostic.severity == DiagnosticSeverity.Warning
            },
            toolErrorCount = toolDiagnostics.count { diagnostic ->
                diagnostic.severity == DiagnosticSeverity.Error
            },
            activeFailureCount = if (normalizedFailureSnapshot.hasActiveFailure()) 1 else 0,
            lastFailureSummary = normalizedFailureSnapshot?.lastErrorSummary.orEmpty(),
        )

        val snapshot = PluginGovernanceSnapshot(
            riskLevel = installRecord.resolveRiskLevel(),
            trustLevel = installRecord.resolveTrustLevel(),
            reviewState = installRecord.resolveReviewState(),
            pluginId = pluginId,
            pluginVersion = installRecord.installedVersion,
            protocolVersion = installRecord.packageContractSnapshot?.protocolVersion
                ?: installRecord.manifestSnapshot.protocolVersion,
            runtimeKind = installRecord.packageContractSnapshot?.runtime?.kind.orEmpty(),
            runtimeSessionState = runtimeSession?.state?.name ?: "UNAVAILABLE",
            bootstrapStatus = bootstrapStatus,
            runtimeHealth = runtimeHealth,
            suspensionState = if (normalizedFailureSnapshot?.isSuspended == true) {
                PluginSuspensionState.SUSPENDED
            } else {
                PluginSuspensionState.NOT_SUSPENDED
            },
            autoRecoveryState = normalizedFailureSnapshot.toAutoRecoveryState(),
            capabilityGrants = installRecord.permissionSnapshot.map { declaration ->
                PluginCapabilityGrantSnapshot(
                    permissionId = declaration.permissionId,
                    title = declaration.title,
                    riskLevel = declaration.riskLevel,
                    required = declaration.required,
                )
            },
            lastFailure = normalizedFailureSnapshot.toGovernanceFailureSnapshot(),
            lastSuccessAtEpochMillis = null,
            workspaceStateSummary = PluginWorkspaceStateSummary(
                extractedDir = installRecord.extractedDir,
                localPackagePath = installRecord.localPackagePath,
                hasExtractedDir = installRecord.extractedDir.isNotBlank(),
                hasLocalPackage = installRecord.localPackagePath.isNotBlank(),
            ),
            configStateSummary = PluginConfigStateSummary(
                hasStaticSchema = installRecord.packageContractSnapshot?.config?.staticSchema?.isNotBlank() == true,
                hasSettingsSchema = installRecord.packageContractSnapshot?.config?.settingsSchema?.isNotBlank() == true,
            ),
            registrationSummary = runtimeSnapshot.compiledRegistriesByPluginId[pluginId]
                .toRegistrationSummary(
                    pluginId = pluginId,
                    toolRegistrySnapshot = runtimeSnapshot.toolRegistrySnapshot,
            ),
            diagnosticsSummary = diagnosticsSummary,
        )
        if (publishProjectionLogs) {
            logBus.publishGovernanceSnapshotRefreshed(
                pluginId = snapshot.pluginId,
                pluginVersion = snapshot.pluginVersion,
                occurredAtEpochMillis = clock(),
                runtimeSessionId = runtimeSession?.sessionInstanceId.orEmpty(),
                diagnosticsTotalCount = snapshot.diagnosticsSummary.totalCount,
                activeFailureCount = snapshot.diagnosticsSummary.activeFailureCount,
            )
            logBus.publishPluginRuntimeHealthProjected(
                pluginId = snapshot.pluginId,
                pluginVersion = snapshot.pluginVersion,
                occurredAtEpochMillis = clock(),
                runtimeSessionId = runtimeSession?.sessionInstanceId.orEmpty(),
                healthStatus = snapshot.runtimeHealth.status.name.uppercase(),
                suspensionState = snapshot.suspensionState.name,
                diagnosticsTotalCount = snapshot.diagnosticsSummary.totalCount,
            )
        }
        return snapshot
    }

    private fun resolveRuntimeHealth(
        installRecord: PluginInstallRecord,
        runtimeSession: PluginV2RuntimeSession?,
        bootstrapStatus: PluginBootstrapStatusSnapshot,
        failureSnapshot: PluginFailureSnapshot?,
    ): PluginRuntimeHealthSnapshot {
        val status = when {
            failureSnapshot?.isSuspended == true -> PluginRuntimeHealthStatus.Suspended
            installRecord.compatibilityState.protocolSupported == false -> PluginRuntimeHealthStatus.UnsupportedProtocol
            installRecord.compatibilityState.minHostVersionSatisfied == false ||
                installRecord.compatibilityState.maxHostVersionSatisfied == false -> PluginRuntimeHealthStatus.UpgradeRequired
            !installRecord.enabled -> PluginRuntimeHealthStatus.Disabled
            bootstrapStatus.errorCount > 0 ||
                runtimeSession?.state == PluginV2RuntimeSessionState.BootstrapFailed -> PluginRuntimeHealthStatus.BootstrapFailed
            runtimeSession?.state == PluginV2RuntimeSessionState.Active -> PluginRuntimeHealthStatus.Healthy
            runtimeSession == null -> PluginRuntimeHealthStatus.Disabled
            runtimeSession.state == PluginV2RuntimeSessionState.Loading ||
                runtimeSession.state == PluginV2RuntimeSessionState.BootstrapRunning ||
                runtimeSession.state == PluginV2RuntimeSessionState.Disposed ||
                runtimeSession.state == PluginV2RuntimeSessionState.Unloaded -> PluginRuntimeHealthStatus.Disabled
            else -> PluginRuntimeHealthStatus.Disabled
        }
        return PluginRuntimeHealthSnapshot(
            status = status,
            detail = when (status) {
                PluginRuntimeHealthStatus.Healthy -> runtimeSession?.state?.name ?: "No active failure projection."
                PluginRuntimeHealthStatus.BootstrapFailed -> bootstrapStatus.status
                PluginRuntimeHealthStatus.Suspended -> "Failure guard has suspended the plugin."
                PluginRuntimeHealthStatus.Disabled -> runtimeSession?.state?.name ?: "No active runtime session is currently published."
                PluginRuntimeHealthStatus.UnsupportedProtocol -> "Host compatibility rejected the plugin protocol version."
                PluginRuntimeHealthStatus.UpgradeRequired -> "Host compatibility requires an upgrade path before activation."
            },
        )
    }
}

private fun PluginFailureSnapshot?.normalizeForGovernance(
    nowEpochMillis: Long,
): PluginFailureSnapshot? {
    if (this == null) {
        return null
    }
    if (!isSuspended) {
        return this
    }
    val suspendedUntil = suspendedUntilEpochMillis ?: return this
    if (nowEpochMillis < suspendedUntil) {
        return this
    }
    return copy(
        consecutiveFailureCount = 0,
        isSuspended = false,
        suspendedUntilEpochMillis = null,
    )
}

private fun PluginV2BootstrapSummary?.toBootstrapStatus(): PluginBootstrapStatusSnapshot {
    if (this == null) {
        return PluginBootstrapStatusSnapshot()
    }
    return PluginBootstrapStatusSnapshot(
        status = when {
            errorCount > 0 -> "FAILED"
            warningCount > 0 -> "WARNINGS"
            else -> "SUCCEEDED"
        },
        compiledAtEpochMillis = compiledAtEpochMillis,
        warningCount = warningCount,
        errorCount = errorCount,
    )
}

private fun PluginFailureSnapshot?.toAutoRecoveryState(): PluginAutoRecoveryStateSnapshot {
    if (this == null) {
        return PluginAutoRecoveryStateSnapshot()
    }
    return PluginAutoRecoveryStateSnapshot(
        status = when {
            isSuspended -> "SUSPENDED"
            consecutiveFailureCount > 0 -> "TRACKING_FAILURES"
            else -> "IDLE"
        },
        consecutiveFailureCount = consecutiveFailureCount,
        suspendedUntilEpochMillis = suspendedUntilEpochMillis,
        lastFailureAtEpochMillis = lastFailureAtEpochMillis,
    )
}

private fun PluginFailureSnapshot?.toGovernanceFailureSnapshot(): PluginGovernanceFailureSnapshot? {
    if (this == null) {
        return null
    }
    if (
        lastErrorSummary.isBlank() &&
        lastFailureAtEpochMillis == null &&
        suspendedUntilEpochMillis == null
    ) {
        return null
    }
    return PluginGovernanceFailureSnapshot(
        category = failureCategory.wireValue,
        summary = lastErrorSummary,
        occurredAtEpochMillis = lastFailureAtEpochMillis,
        suspendedUntilEpochMillis = suspendedUntilEpochMillis,
    )
}

private fun PluginInstallRecord.resolveRiskLevel(): PluginRiskLevel {
    val declaredRisk = manifestSnapshot.riskLevel
    val permissionRisk = permissionSnapshot
        .map { declaration -> declaration.riskLevel }
        .maxByOrNull(PluginRiskLevel::ordinal)
        ?: PluginRiskLevel.LOW
    return if (declaredRisk.ordinal >= permissionRisk.ordinal) declaredRisk else permissionRisk
}

private fun PluginInstallRecord.resolveTrustLevel(): PluginTrustLevel {
    return when (source.sourceType) {
        PluginSourceType.LOCAL_FILE,
        PluginSourceType.MANUAL_IMPORT,
        -> PluginTrustLevel.LOCAL_PACKAGE

        PluginSourceType.DIRECT_LINK -> PluginTrustLevel.DIRECT_SOURCE
        PluginSourceType.REPOSITORY -> PluginTrustLevel.REPOSITORY_LISTED
    }
}

private fun PluginInstallRecord.resolveReviewState(): PluginReviewState {
    return when (compatibilityState.status) {
        PluginCompatibilityStatus.COMPATIBLE -> PluginReviewState.LOCAL_CHECKS_PASSED
        PluginCompatibilityStatus.INCOMPATIBLE -> PluginReviewState.HOST_COMPATIBILITY_BLOCKED
        PluginCompatibilityStatus.UNKNOWN -> PluginReviewState.UNREVIEWED
    }
}

private fun PluginFailureSnapshot?.hasActiveFailure(): Boolean {
    return this != null && (consecutiveFailureCount > 0 || isSuspended || suspendedUntilEpochMillis != null)
}

private fun PluginV2CompiledRegistrySnapshot?.toRegistrationSummary(
    pluginId: String,
    toolRegistrySnapshot: PluginV2ToolRegistrySnapshot?,
): PluginRegistrationSummary {
    val handlerRegistry = this?.handlerRegistry
    val commandGroupCount = handlerRegistry?.commandHandlers
        ?.map { handler -> handler.groupPath }
        ?.filter(List<String>::isNotEmpty)
        ?.map(List<String>::toCommandGroupKey)
        ?.distinct()
        ?.size
        ?: 0
    val filterCount = handlerRegistry?.let { registry ->
        registry.messageHandlers.sumOf { handler -> handler.filterAttachments.size } +
            registry.commandHandlers.sumOf { handler -> handler.filterAttachments.size } +
            registry.regexHandlers.sumOf { handler -> handler.filterAttachments.size } +
            registry.lifecycleHandlers.sumOf { handler -> handler.filterAttachments.size } +
            registry.llmHookHandlers.sumOf { handler -> handler.filterAttachments.size }
    } ?: 0

    return PluginRegistrationSummary(
        messageHandlerCount = handlerRegistry?.messageHandlers?.size ?: 0,
        commandCount = handlerRegistry?.commandHandlers?.size ?: 0,
        commandGroupCount = commandGroupCount,
        regexCount = handlerRegistry?.regexHandlers?.size ?: 0,
        filterCount = filterCount,
        lifecycleHookCount = handlerRegistry?.lifecycleHandlers?.size ?: 0,
        llmHookCount = handlerRegistry?.llmHookHandlers?.size ?: 0,
        toolCount = toolRegistrySnapshot?.activeEntries?.count { entry -> entry.pluginId == pluginId } ?: 0,
    )
}

private fun List<String>.toCommandGroupKey(): String {
    return joinToString(separator = "\u0000")
}
