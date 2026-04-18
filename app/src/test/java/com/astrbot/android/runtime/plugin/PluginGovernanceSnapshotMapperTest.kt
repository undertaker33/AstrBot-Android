package com.astrbot.android.feature.plugin.runtime

import com.astrbot.android.model.plugin.DiagnosticSeverity
import com.astrbot.android.model.plugin.PluginAutoRecoveryStateSnapshot
import com.astrbot.android.model.plugin.PluginBootstrapStatusSnapshot
import com.astrbot.android.model.plugin.PluginCapabilityGrantSnapshot
import com.astrbot.android.model.plugin.PluginConfigStateSummary
import com.astrbot.android.model.plugin.PluginDiagnosticsSummary
import com.astrbot.android.model.plugin.PluginGovernanceFailureSnapshot
import com.astrbot.android.model.plugin.PluginLifecycleDiagnostic
import com.astrbot.android.model.plugin.PluginRegistrationSummary
import com.astrbot.android.model.plugin.PluginRiskLevel
import com.astrbot.android.model.plugin.PluginRuntimeHealthStatus
import com.astrbot.android.model.plugin.PluginSuspensionState
import com.astrbot.android.model.plugin.PluginWorkspaceStateSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class PluginGovernanceSnapshotMapperTest {

    @Test
    fun mapper_aggregates_phase6_runtime_failure_registry_and_diagnostics_sources() {
        val fixture = governanceRuntimeFixture(pluginId = "com.example.governance.mapper")
        val failureSnapshot = PluginFailureSnapshot(
            pluginId = fixture.installRecord.pluginId,
            consecutiveFailureCount = 3,
            lastFailureAtEpochMillis = 1_700L,
            lastErrorSummary = "permission denied while calling tool",
            failureCategory = com.astrbot.android.model.plugin.PluginFailureCategory.PermissionDenied,
            isSuspended = true,
            suspendedUntilEpochMillis = 9_999L,
        )
        val lifecycleDiagnostics = listOf(
            PluginLifecycleDiagnostic(
                pluginId = fixture.installRecord.pluginId,
                hook = "on_plugin_loaded",
                code = "lifecycle_hook_failed",
                message = "Lifecycle hook failed.",
                occurredAtEpochMillis = 2_000L,
            ),
        )

        val snapshot = PluginGovernanceSnapshotMapper.map(
            installRecord = fixture.installRecord,
            runtimeSnapshot = fixture.runtimeSnapshot,
            failureSnapshot = failureSnapshot,
            lifecycleDiagnostics = lifecycleDiagnostics,
            clock = { 9_000L },
        )

        assertEquals("com.example.governance.mapper", snapshot.pluginId)
        assertEquals("1.0.0", snapshot.pluginVersion)
        assertEquals("js_quickjs", snapshot.runtimeKind)
        assertEquals(PluginV2RuntimeSessionState.Active.name, snapshot.runtimeSessionState)
        assertEquals(2, snapshot.protocolVersion)
        assertEquals(PluginRuntimeHealthStatus.Suspended, snapshot.runtimeHealth.status)
        assertEquals(PluginSuspensionState.SUSPENDED, snapshot.suspensionState)
        assertEquals(
            PluginBootstrapStatusSnapshot(
                status = "FAILED",
                compiledAtEpochMillis = 1_000L,
                warningCount = 1,
                errorCount = 1,
            ),
            snapshot.bootstrapStatus,
        )
        assertEquals(
            PluginAutoRecoveryStateSnapshot(
                status = "SUSPENDED",
                consecutiveFailureCount = 3,
                suspendedUntilEpochMillis = 9_999L,
                lastFailureAtEpochMillis = 1_700L,
            ),
            snapshot.autoRecoveryState,
        )
        assertEquals(
            listOf(
                PluginCapabilityGrantSnapshot(
                    permissionId = "net.access",
                    title = "Network access",
                    riskLevel = PluginRiskLevel.MEDIUM,
                    required = true,
                    source = "install_permission_snapshot",
                ),
            ),
            snapshot.capabilityGrants,
        )
        assertEquals(
            PluginGovernanceFailureSnapshot(
                category = "permission_denied",
                summary = "permission denied while calling tool",
                occurredAtEpochMillis = 1_700L,
                suspendedUntilEpochMillis = 9_999L,
            ),
            snapshot.lastFailure,
        )
        assertEquals(null, snapshot.lastSuccessAtEpochMillis)
        assertEquals(
            PluginWorkspaceStateSummary(
                source = "install_record",
                extractedDir = "/tmp/com.example.governance.mapper",
                localPackagePath = "/tmp/com.example.governance.mapper-1.0.0.zip",
                hasExtractedDir = true,
                hasLocalPackage = true,
            ),
            snapshot.workspaceStateSummary,
        )
        assertEquals(
            PluginConfigStateSummary(
                source = "package_contract_snapshot",
                hasStaticSchema = false,
                hasSettingsSchema = false,
            ),
            snapshot.configStateSummary,
        )
        assertEquals(
            PluginRegistrationSummary(
                messageHandlerCount = 1,
                commandCount = 2,
                commandGroupCount = 2,
                regexCount = 1,
                filterCount = 6,
                lifecycleHookCount = 1,
                llmHookCount = 1,
                toolCount = 2,
            ),
            snapshot.registrationSummary,
        )
        assertEquals(
            PluginDiagnosticsSummary(
                bootstrapWarningCount = 1,
                bootstrapErrorCount = 1,
                lifecycleDiagnosticCount = 1,
                toolWarningCount = 1,
                toolErrorCount = 1,
                activeFailureCount = 1,
                lastFailureSummary = "permission denied while calling tool",
            ),
            snapshot.diagnosticsSummary,
        )
        assertEquals(6, snapshot.diagnosticsSummary.totalCount)
    }

    @Test
    fun mapper_returns_healthy_snapshot_for_active_runtime_without_failures_or_diagnostics() {
        val fixture = governanceRuntimeFixture(
            pluginId = "com.example.governance.healthy",
            bootstrapDiagnostics = emptyList(),
            toolDiagnostics = emptyList(),
        )

        val snapshot = PluginGovernanceSnapshotMapper.map(
            installRecord = fixture.installRecord,
            runtimeSnapshot = fixture.runtimeSnapshot,
            failureSnapshot = null,
            lifecycleDiagnostics = emptyList(),
        )

        assertEquals(PluginRuntimeHealthStatus.Healthy, snapshot.runtimeHealth.status)
        assertEquals(PluginSuspensionState.NOT_SUSPENDED, snapshot.suspensionState)
        assertEquals(PluginDiagnosticsSummary(), snapshot.diagnosticsSummary)
    }

    @Test
    fun mapper_distinguishes_all_shared_definition_runtime_health_states_and_ignores_recovered_history() {
        val activeHealthy = governanceRuntimeFixture(
            pluginId = "com.example.governance.health.healthy",
            bootstrapDiagnostics = emptyList(),
            toolDiagnostics = emptyList(),
        )
        val disabled = governanceInstallRecord(
            pluginId = "com.example.governance.health.disabled",
            enabled = false,
        )
        val unsupportedProtocol = governanceInstallRecord(
            pluginId = "com.example.governance.health.protocol",
            compatibilityState = com.astrbot.android.model.plugin.PluginCompatibilityState.fromChecks(
                protocolSupported = false,
                minHostVersionSatisfied = true,
                maxHostVersionSatisfied = true,
            ),
        )
        val upgradeRequired = governanceInstallRecord(
            pluginId = "com.example.governance.health.upgrade",
            compatibilityState = com.astrbot.android.model.plugin.PluginCompatibilityState.fromChecks(
                protocolSupported = true,
                minHostVersionSatisfied = false,
                maxHostVersionSatisfied = true,
            ),
        )
        val bootstrapFailed = governanceRuntimeFixture(
            pluginId = "com.example.governance.health.bootstrap",
            bootstrapDiagnostics = listOf(
                PluginV2CompilerDiagnostic(
                    severity = DiagnosticSeverity.Error,
                    code = "bootstrap_error",
                    message = "Bootstrap error.",
                    pluginId = "com.example.governance.health.bootstrap",
                ),
            ),
            toolDiagnostics = emptyList(),
        )
        val suspended = governanceRuntimeFixture(
            pluginId = "com.example.governance.health.suspended",
            bootstrapDiagnostics = emptyList(),
            toolDiagnostics = emptyList(),
        )
        val loading = governanceSessionOnlyFixture(
            pluginId = "com.example.governance.health.loading",
            sessionState = PluginV2RuntimeSessionState.Loading,
        )
        val bootstrapRunning = governanceSessionOnlyFixture(
            pluginId = "com.example.governance.health.running",
            sessionState = PluginV2RuntimeSessionState.BootstrapRunning,
        )
        val unloaded = governanceSessionOnlyFixture(
            pluginId = "com.example.governance.health.unloaded",
            sessionState = PluginV2RuntimeSessionState.Unloaded,
        )
        val disposed = governanceSessionOnlyFixture(
            pluginId = "com.example.governance.health.disposed",
            sessionState = PluginV2RuntimeSessionState.Disposed,
        )
        val noSession = governanceInstallRecord(
            pluginId = "com.example.governance.health.no-session",
        )
        val recoveredFailure = PluginFailureSnapshot(
            pluginId = activeHealthy.installRecord.pluginId,
            consecutiveFailureCount = 0,
            lastFailureAtEpochMillis = 5_000L,
            lastErrorSummary = "historical failure already recovered",
            isSuspended = false,
            suspendedUntilEpochMillis = null,
        )

        assertEquals(
            PluginRuntimeHealthStatus.Healthy,
            PluginGovernanceSnapshotMapper.map(
                installRecord = activeHealthy.installRecord,
                runtimeSnapshot = activeHealthy.runtimeSnapshot,
                failureSnapshot = recoveredFailure,
                lifecycleDiagnostics = emptyList(),
            ).runtimeHealth.status,
        )
        assertEquals(
            0,
            PluginGovernanceSnapshotMapper.map(
                installRecord = activeHealthy.installRecord,
                runtimeSnapshot = activeHealthy.runtimeSnapshot,
                failureSnapshot = recoveredFailure,
                lifecycleDiagnostics = emptyList(),
            ).diagnosticsSummary.activeFailureCount,
        )
        assertEquals(
            PluginRuntimeHealthStatus.Disabled,
            PluginGovernanceSnapshotMapper.map(
                installRecord = disabled,
                runtimeSnapshot = PluginV2ActiveRuntimeSnapshot(),
                failureSnapshot = null,
                lifecycleDiagnostics = emptyList(),
            ).runtimeHealth.status,
        )
        assertEquals(
            PluginRuntimeHealthStatus.UnsupportedProtocol,
            PluginGovernanceSnapshotMapper.map(
                installRecord = unsupportedProtocol,
                runtimeSnapshot = PluginV2ActiveRuntimeSnapshot(),
                failureSnapshot = null,
                lifecycleDiagnostics = emptyList(),
            ).runtimeHealth.status,
        )
        assertEquals(
            PluginRuntimeHealthStatus.UpgradeRequired,
            PluginGovernanceSnapshotMapper.map(
                installRecord = upgradeRequired,
                runtimeSnapshot = PluginV2ActiveRuntimeSnapshot(),
                failureSnapshot = null,
                lifecycleDiagnostics = emptyList(),
            ).runtimeHealth.status,
        )
        assertEquals(
            PluginRuntimeHealthStatus.BootstrapFailed,
            PluginGovernanceSnapshotMapper.map(
                installRecord = bootstrapFailed.installRecord,
                runtimeSnapshot = bootstrapFailed.runtimeSnapshot,
                failureSnapshot = null,
                lifecycleDiagnostics = emptyList(),
            ).runtimeHealth.status,
        )
        assertEquals(
            PluginRuntimeHealthStatus.Disabled,
            PluginGovernanceSnapshotMapper.map(
                installRecord = loading.installRecord,
                runtimeSnapshot = loading.runtimeSnapshot,
                failureSnapshot = null,
                lifecycleDiagnostics = emptyList(),
            ).runtimeHealth.status,
        )
        assertEquals(
            PluginRuntimeHealthStatus.Disabled,
            PluginGovernanceSnapshotMapper.map(
                installRecord = bootstrapRunning.installRecord,
                runtimeSnapshot = bootstrapRunning.runtimeSnapshot,
                failureSnapshot = null,
                lifecycleDiagnostics = emptyList(),
            ).runtimeHealth.status,
        )
        assertEquals(
            PluginRuntimeHealthStatus.Disabled,
            PluginGovernanceSnapshotMapper.map(
                installRecord = unloaded.installRecord,
                runtimeSnapshot = unloaded.runtimeSnapshot,
                failureSnapshot = null,
                lifecycleDiagnostics = emptyList(),
            ).runtimeHealth.status,
        )
        assertEquals(
            PluginRuntimeHealthStatus.Disabled,
            PluginGovernanceSnapshotMapper.map(
                installRecord = disposed.installRecord,
                runtimeSnapshot = disposed.runtimeSnapshot,
                failureSnapshot = null,
                lifecycleDiagnostics = emptyList(),
            ).runtimeHealth.status,
        )
        assertEquals(
            PluginRuntimeHealthStatus.Disabled,
            PluginGovernanceSnapshotMapper.map(
                installRecord = noSession,
                runtimeSnapshot = PluginV2ActiveRuntimeSnapshot(),
                failureSnapshot = null,
                lifecycleDiagnostics = emptyList(),
            ).runtimeHealth.status,
        )
        assertEquals(
            PluginRuntimeHealthStatus.Suspended,
            PluginGovernanceSnapshotMapper.map(
                installRecord = suspended.installRecord,
                runtimeSnapshot = suspended.runtimeSnapshot,
                failureSnapshot = PluginFailureSnapshot(
                    pluginId = suspended.installRecord.pluginId,
                    consecutiveFailureCount = 1,
                    lastFailureAtEpochMillis = 6_000L,
                    lastErrorSummary = "still suspended",
                    isSuspended = true,
                    suspendedUntilEpochMillis = 9_000L,
                ),
                lifecycleDiagnostics = emptyList(),
                clock = { 8_000L },
            ).runtimeHealth.status,
        )
    }

    @Test
    fun mapper_normalizes_expired_suspension_before_projecting_governance_state() {
        val fixture = governanceRuntimeFixture(
            pluginId = "com.example.governance.health.expired-suspension",
            bootstrapDiagnostics = emptyList(),
            toolDiagnostics = emptyList(),
        )
        val expiredSuspension = PluginFailureSnapshot(
            pluginId = fixture.installRecord.pluginId,
            consecutiveFailureCount = 2,
            lastFailureAtEpochMillis = 4_000L,
            lastErrorSummary = "historical suspension expired",
            failureCategory = com.astrbot.android.model.plugin.PluginFailureCategory.RuntimeError,
            isSuspended = true,
            suspendedUntilEpochMillis = 5_000L,
        )

        val snapshot = PluginGovernanceSnapshotMapper.map(
            installRecord = fixture.installRecord,
            runtimeSnapshot = fixture.runtimeSnapshot,
            failureSnapshot = expiredSuspension,
            lifecycleDiagnostics = emptyList(),
            clock = { 5_001L },
        )

        assertEquals(PluginSuspensionState.NOT_SUSPENDED, snapshot.suspensionState)
        assertEquals(PluginRuntimeHealthStatus.Healthy, snapshot.runtimeHealth.status)
        assertEquals(0, snapshot.diagnosticsSummary.activeFailureCount)
        assertEquals("IDLE", snapshot.autoRecoveryState.status)
        assertEquals(
            PluginGovernanceFailureSnapshot(
                category = "runtime_error",
                summary = "historical suspension expired",
                occurredAtEpochMillis = 4_000L,
                suspendedUntilEpochMillis = null,
            ),
            snapshot.lastFailure,
        )
    }

    private fun governanceRuntimeFixture(
        pluginId: String,
        bootstrapDiagnostics: List<PluginV2CompilerDiagnostic> = listOf(
            PluginV2CompilerDiagnostic(
                severity = DiagnosticSeverity.Warning,
                code = "bootstrap_warning",
                message = "Bootstrap warning.",
                pluginId = pluginId,
            ),
            PluginV2CompilerDiagnostic(
                severity = DiagnosticSeverity.Error,
                code = "bootstrap_error",
                message = "Bootstrap error.",
                pluginId = pluginId,
            ),
        ),
        toolDiagnostics: List<PluginV2CompilerDiagnostic> = listOf(
            PluginV2CompilerDiagnostic(
                severity = DiagnosticSeverity.Warning,
                code = "tool_warning",
                message = "Tool warning.",
                pluginId = pluginId,
                registrationKind = "tool",
                registrationKey = "alpha",
            ),
            PluginV2CompilerDiagnostic(
                severity = DiagnosticSeverity.Error,
                code = "tool_error",
                message = "Tool error.",
                pluginId = pluginId,
                registrationKind = "tool",
                registrationKey = "beta",
            ),
        ),
    ): GovernanceRuntimeFixture {
        val installRecord = governanceInstallRecord(pluginId = pluginId)
        val session = PluginV2RuntimeSession(
            installRecord = installRecord,
            sessionInstanceId = "session-$pluginId",
        )
        session.transitionTo(PluginV2RuntimeSessionState.Loading)
        session.transitionTo(PluginV2RuntimeSessionState.BootstrapRunning)

        val rawRegistry = PluginV2RawRegistry(pluginId)
        rawRegistry.appendMessageHandler(
            callbackToken = session.allocateCallbackToken(PluginV2CallbackHandle {}),
            descriptor = MessageHandlerRegistrationInput(
                base = BaseHandlerRegistrationInput(
                    registrationKey = "message.primary",
                    declaredFilters = listOf(BootstrapFilterDescriptor.message("hello")),
                ),
                handler = PluginV2CallbackHandle {},
            ),
        )
        rawRegistry.appendCommandHandler(
            callbackToken = session.allocateCallbackToken(PluginV2CallbackHandle {}),
            descriptor = CommandHandlerRegistrationInput(
                base = BaseHandlerRegistrationInput(
                    registrationKey = "command.alpha",
                    declaredFilters = listOf(BootstrapFilterDescriptor.command("/alpha")),
                ),
                command = "/alpha",
                aliases = listOf("/a"),
                groupPath = listOf("admin"),
                handler = PluginV2CallbackHandle {},
            ),
        )
        rawRegistry.appendCommandHandler(
            callbackToken = session.allocateCallbackToken(PluginV2CallbackHandle {}),
            descriptor = CommandHandlerRegistrationInput(
                base = BaseHandlerRegistrationInput(
                    registrationKey = "command.beta",
                    declaredFilters = listOf(BootstrapFilterDescriptor.command("/beta")),
                ),
                command = "/beta",
                aliases = listOf("/b"),
                groupPath = listOf("admin", "ops"),
                handler = PluginV2CallbackHandle {},
            ),
        )
        rawRegistry.appendRegexHandler(
            callbackToken = session.allocateCallbackToken(PluginV2CallbackHandle {}),
            descriptor = RegexHandlerRegistrationInput(
                base = BaseHandlerRegistrationInput(
                    registrationKey = "regex.primary",
                    declaredFilters = listOf(BootstrapFilterDescriptor.regex("^hello$")),
                ),
                pattern = "^hello$",
                handler = PluginV2CallbackHandle {},
            ),
        )
        rawRegistry.appendLifecycleHandler(
            callbackToken = session.allocateCallbackToken(PluginV2CallbackHandle {}),
            descriptor = LifecycleHandlerRegistrationInput(
                registrationKey = "lifecycle.loaded",
                hook = "on_plugin_loaded",
                declaredFilters = listOf(BootstrapFilterDescriptor.message("loaded")),
                handler = PluginV2CallbackHandle {},
            ),
        )
        rawRegistry.appendLlmHook(
            callbackToken = session.allocateCallbackToken(PluginV2CallbackHandle {}),
            descriptor = LlmHookRegistrationInput(
                registrationKey = "llm.request",
                hook = "on_llm_request",
                declaredFilters = listOf(BootstrapFilterDescriptor.message("llm")),
                handler = PluginV2CallbackHandle {},
            ),
        )
        rawRegistry.appendTool(
            callbackToken = session.allocateCallbackToken(PluginV2CallbackHandle {}),
            descriptor = PluginToolDescriptor(
                pluginId = pluginId,
                name = "alpha",
                description = "Alpha tool",
                inputSchema = linkedMapOf("type" to "object"),
            ),
        )
        rawRegistry.appendTool(
            callbackToken = session.allocateCallbackToken(PluginV2CallbackHandle {}),
            descriptor = PluginToolDescriptor(
                pluginId = pluginId,
                name = "beta",
                description = "Beta tool",
                inputSchema = linkedMapOf("type" to "object"),
            ),
        )
        session.attachRawRegistry(rawRegistry)

        val compiledRegistry = requireNotNull(
            PluginV2RegistryCompiler(logBus = NoOpPluginRuntimeLogBus).compile(rawRegistry).compiledRegistry,
        )
        session.attachCompiledRegistry(compiledRegistry)
        session.transitionTo(PluginV2RuntimeSessionState.Active)

        val toolState = compileCentralizedToolState(mapOf(pluginId to session))
        val bootstrapSummary = PluginV2BootstrapSummary(
            pluginId = pluginId,
            sessionInstanceId = session.sessionInstanceId,
            compiledAtEpochMillis = 1_000L,
            handlerCount = compiledRegistry.handlerRegistry.totalHandlerCount,
            warningCount = bootstrapDiagnostics.count { it.severity == DiagnosticSeverity.Warning },
            errorCount = bootstrapDiagnostics.count { it.severity == DiagnosticSeverity.Error },
        )
        val runtimeEntry = PluginV2ActiveRuntimeEntry(
            session = session,
            compiledRegistry = compiledRegistry,
            lastBootstrapSummary = bootstrapSummary,
            diagnostics = bootstrapDiagnostics,
            callbackTokens = session.snapshotCallbackTokens(),
        )

        return GovernanceRuntimeFixture(
            installRecord = installRecord,
            runtimeSnapshot = PluginV2ActiveRuntimeSnapshot(
                activeRuntimeEntriesByPluginId = mapOf(pluginId to runtimeEntry),
                activeSessionsByPluginId = mapOf(pluginId to session),
                compiledRegistriesByPluginId = mapOf(pluginId to compiledRegistry),
                callbackTokenIndexByPluginId = mapOf(
                    pluginId to session.snapshotCallbackTokens().map(PluginV2CallbackToken::value),
                ),
                diagnosticsByPluginId = mapOf(pluginId to bootstrapDiagnostics),
                lastBootstrapSummariesByPluginId = mapOf(pluginId to bootstrapSummary),
                toolRegistrySnapshot = toolState.activeRegistry,
                toolRegistryDiagnostics = toolDiagnostics,
                toolAvailabilityByName = toolState.availabilityByName,
            ),
        )
    }

    private data class GovernanceRuntimeFixture(
        val installRecord: com.astrbot.android.model.plugin.PluginInstallRecord,
        val runtimeSnapshot: PluginV2ActiveRuntimeSnapshot,
    )

    private fun governanceSessionOnlyFixture(
        pluginId: String,
        sessionState: PluginV2RuntimeSessionState,
    ): GovernanceRuntimeFixture {
        val installRecord = governanceInstallRecord(pluginId = pluginId)
        val session = PluginV2RuntimeSession(
            installRecord = installRecord,
            sessionInstanceId = "session-$pluginId",
        )
        when (sessionState) {
            PluginV2RuntimeSessionState.Unloaded -> Unit
            PluginV2RuntimeSessionState.Loading -> session.transitionTo(PluginV2RuntimeSessionState.Loading)
            PluginV2RuntimeSessionState.BootstrapRunning -> {
                session.transitionTo(PluginV2RuntimeSessionState.Loading)
                session.transitionTo(PluginV2RuntimeSessionState.BootstrapRunning)
            }

            PluginV2RuntimeSessionState.Active -> {
                session.transitionTo(PluginV2RuntimeSessionState.Loading)
                session.transitionTo(PluginV2RuntimeSessionState.BootstrapRunning)
                session.transitionTo(PluginV2RuntimeSessionState.Active)
            }

            PluginV2RuntimeSessionState.BootstrapFailed -> {
                session.transitionTo(PluginV2RuntimeSessionState.Loading)
                session.transitionTo(PluginV2RuntimeSessionState.BootstrapFailed)
            }

            PluginV2RuntimeSessionState.Disposed -> session.dispose()
        }
        return GovernanceRuntimeFixture(
            installRecord = installRecord,
            runtimeSnapshot = PluginV2ActiveRuntimeSnapshot(
                activeSessionsByPluginId = mapOf(pluginId to session),
            ),
        )
    }

    private fun governanceInstallRecord(
        pluginId: String,
        enabled: Boolean = true,
        compatibilityState: com.astrbot.android.model.plugin.PluginCompatibilityState =
            com.astrbot.android.model.plugin.PluginCompatibilityState.evaluated(
                protocolSupported = true,
                minHostVersionSatisfied = true,
                maxHostVersionSatisfied = true,
            ),
    ): com.astrbot.android.model.plugin.PluginInstallRecord {
        return samplePluginV2InstallRecord(pluginId = pluginId).copyWith(
            compatibilityState = compatibilityState,
            enabled = enabled,
        )
    }
}
