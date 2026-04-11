package com.astrbot.android.model.plugin

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
