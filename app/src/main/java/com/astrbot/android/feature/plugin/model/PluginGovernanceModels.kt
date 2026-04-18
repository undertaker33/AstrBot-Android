package com.astrbot.android.model.plugin

import com.astrbot.android.feature.plugin.runtime.PluginGovernanceSnapshotMapper
import com.astrbot.android.feature.plugin.runtime.PluginV2CompiledRegistrySnapshot
import com.astrbot.android.feature.plugin.runtime.PluginV2InternalStage
import java.io.Serializable
import java.util.Collections

enum class PluginTrustLevel {
    LOCAL_PACKAGE,
    DIRECT_SOURCE,
    REPOSITORY_LISTED,
}

enum class PluginReviewState {
    UNREVIEWED,
    LOCAL_CHECKS_PASSED,
    HOST_COMPATIBILITY_BLOCKED,
}

enum class PluginRuntimeHealthStatus {
    Healthy,
    BootstrapFailed,
    Suspended,
    Disabled,
    UnsupportedProtocol,
    UpgradeRequired,
}

enum class PluginSuspensionState {
    NOT_SUSPENDED,
    SUSPENDED,
}

data class PluginRuntimeHealthSnapshot(
    val status: PluginRuntimeHealthStatus = PluginRuntimeHealthStatus.Disabled,
    val source: String = "governance_snapshot_mapper",
    val detail: String = "",
)

data class PluginBootstrapStatusSnapshot(
    val status: String = "UNAVAILABLE",
    val compiledAtEpochMillis: Long? = null,
    val warningCount: Int = 0,
    val errorCount: Int = 0,
)

data class PluginAutoRecoveryStateSnapshot(
    val status: String = "IDLE",
    val consecutiveFailureCount: Int = 0,
    val suspendedUntilEpochMillis: Long? = null,
    val lastFailureAtEpochMillis: Long? = null,
)

data class PluginCapabilityGrantSnapshot(
    val permissionId: String,
    val title: String = "",
    val riskLevel: PluginRiskLevel = PluginRiskLevel.LOW,
    val required: Boolean = true,
    val source: String = "install_permission_snapshot",
)

data class PluginGovernanceFailureSnapshot(
    val category: String = "",
    val summary: String = "",
    val occurredAtEpochMillis: Long? = null,
    val suspendedUntilEpochMillis: Long? = null,
)

data class PluginRegistrationSummary(
    val messageHandlerCount: Int = 0,
    val commandCount: Int = 0,
    val commandGroupCount: Int = 0,
    val regexCount: Int = 0,
    val filterCount: Int = 0,
    val lifecycleHookCount: Int = 0,
    val llmHookCount: Int = 0,
    val toolCount: Int = 0,
)

data class PluginDiagnosticsSummary(
    val bootstrapWarningCount: Int = 0,
    val bootstrapErrorCount: Int = 0,
    val lifecycleDiagnosticCount: Int = 0,
    val toolWarningCount: Int = 0,
    val toolErrorCount: Int = 0,
    val activeFailureCount: Int = 0,
    val lastFailureSummary: String = "",
) {
    val totalCount: Int
        get() = bootstrapWarningCount +
            bootstrapErrorCount +
            lifecycleDiagnosticCount +
            toolWarningCount +
            toolErrorCount +
            activeFailureCount
}

data class PluginWorkspaceStateSummary(
    val source: String = "install_record",
    val extractedDir: String = "",
    val localPackagePath: String = "",
    val hasExtractedDir: Boolean = false,
    val hasLocalPackage: Boolean = false,
)

data class PluginConfigStateSummary(
    val source: String = "package_contract_snapshot",
    val hasStaticSchema: Boolean = false,
    val hasSettingsSchema: Boolean = false,
)

data class PluginGovernanceSnapshot(
    val riskLevel: PluginRiskLevel,
    val trustLevel: PluginTrustLevel,
    val reviewState: PluginReviewState,
    val pluginId: String = "",
    val pluginVersion: String = "",
    val protocolVersion: Int = 0,
    val runtimeKind: String = "",
    val runtimeSessionState: String = "UNAVAILABLE",
    val bootstrapStatus: PluginBootstrapStatusSnapshot = PluginBootstrapStatusSnapshot(),
    val runtimeHealth: PluginRuntimeHealthSnapshot = PluginRuntimeHealthSnapshot(),
    val suspensionState: PluginSuspensionState = PluginSuspensionState.NOT_SUSPENDED,
    val autoRecoveryState: PluginAutoRecoveryStateSnapshot = PluginAutoRecoveryStateSnapshot(),
    val capabilityGrants: List<PluginCapabilityGrantSnapshot> = emptyList(),
    val lastFailure: PluginGovernanceFailureSnapshot? = null,
    val lastSuccessAtEpochMillis: Long? = null,
    val workspaceStateSummary: PluginWorkspaceStateSummary = PluginWorkspaceStateSummary(),
    val configStateSummary: PluginConfigStateSummary = PluginConfigStateSummary(),
    val registrationSummary: PluginRegistrationSummary = PluginRegistrationSummary(),
    val diagnosticsSummary: PluginDiagnosticsSummary = PluginDiagnosticsSummary(),
)

data class PluginV2RegisteredLlmHooksProjection(
    val totalCount: Int,
    val byStage: Map<PluginV2InternalStage, Int>,
    val handlerIds: List<String>,
)

enum class DiagnosticSeverity : Serializable {
    Error,
    Warning,
}

data class PluginV2CompilerDiagnostic(
    val severity: DiagnosticSeverity,
    val code: String,
    val message: String,
    val pluginId: String,
    val registrationKind: String? = null,
    val registrationKey: String? = null,
) : Serializable

data class PluginLifecycleDiagnostic(
    val pluginId: String,
    val hook: String,
    val code: String,
    val message: String,
    val tracebackText: String = "",
    val occurredAtEpochMillis: Long,
) : Serializable

object PluginV2ToolDiagnosticCodes {
    const val TOOL_REGISTRY_COMPILED = "tool_registry_compiled"
    const val TOOL_AVAILABILITY_SNAPSHOT_BUILT = "tool_availability_snapshot_built"
    const val LLM_TOOL_SELECTED = "llm_tool_selected"
    const val LLM_TOOL_STARTED = "llm_tool_started"
    const val LLM_TOOL_COMPLETED = "llm_tool_completed"
    const val LLM_TOOL_FAILED = "llm_tool_failed"
    const val TOOL_PRE_HOOK_EMITTED = "tool_pre_hook_emitted"
    const val TOOL_POST_HOOK_EMITTED = "tool_post_hook_emitted"
    const val TOOL_CALL_LOOP_RESUMED = "tool_call_loop_resumed"
}

object PluginLifecycleDiagnosticsStore {
    private val records = Collections.synchronizedList(mutableListOf<PluginLifecycleDiagnostic>())

    fun record(record: PluginLifecycleDiagnostic) {
        records += record
    }

    fun snapshot(): List<PluginLifecycleDiagnostic> {
        return synchronized(records) {
            records.toList()
        }
    }

    fun clear() {
        synchronized(records) {
            records.clear()
        }
    }
}

fun PluginInstallRecord.resolveGovernanceSnapshot(): PluginGovernanceSnapshot {
    return PluginGovernanceSnapshotMapper.map(this)
}

fun PluginV2CompiledRegistrySnapshot.projectRegisteredLlmHooks(): PluginV2RegisteredLlmHooksProjection {
    val llmHandlers = handlerRegistry.llmHookHandlers
    return PluginV2RegisteredLlmHooksProjection(
        totalCount = llmHandlers.size,
        byStage = llmHandlers
            .groupBy { handler -> handler.surface.stage }
            .mapValues { (_, handlers) -> handlers.size },
        handlerIds = llmHandlers.map { handler -> handler.handlerId },
    )
}
