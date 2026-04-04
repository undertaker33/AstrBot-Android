package com.astrbot.android.model.plugin

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
