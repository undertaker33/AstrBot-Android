package com.astrbot.android.model.plugin

import com.astrbot.android.runtime.plugin.PluginV2CompiledRegistrySnapshot
import com.astrbot.android.runtime.plugin.PluginV2InternalStage
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

data class PluginGovernanceSnapshot(
    val riskLevel: PluginRiskLevel,
    val trustLevel: PluginTrustLevel,
    val reviewState: PluginReviewState,
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
    val declaredRisk = manifestSnapshot.riskLevel
    val permissionRisk = permissionSnapshot
        .map(PluginPermissionDeclaration::riskLevel)
        .maxByOrNull(PluginRiskLevel::ordinal)
        ?: PluginRiskLevel.LOW

    val trustLevel = when (source.sourceType) {
        PluginSourceType.LOCAL_FILE,
        PluginSourceType.MANUAL_IMPORT,
        -> PluginTrustLevel.LOCAL_PACKAGE

        PluginSourceType.DIRECT_LINK -> PluginTrustLevel.DIRECT_SOURCE
        PluginSourceType.REPOSITORY -> PluginTrustLevel.REPOSITORY_LISTED
    }

    val reviewState = when (compatibilityState.status) {
        PluginCompatibilityStatus.COMPATIBLE -> PluginReviewState.LOCAL_CHECKS_PASSED
        PluginCompatibilityStatus.INCOMPATIBLE -> PluginReviewState.HOST_COMPATIBILITY_BLOCKED
        PluginCompatibilityStatus.UNKNOWN -> PluginReviewState.UNREVIEWED
    }

    return PluginGovernanceSnapshot(
        riskLevel = if (declaredRisk.ordinal >= permissionRisk.ordinal) declaredRisk else permissionRisk,
        trustLevel = trustLevel,
        reviewState = reviewState,
    )
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
