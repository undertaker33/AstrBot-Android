package com.astrbot.android.ui.plugin

import com.astrbot.android.R
import com.astrbot.android.model.plugin.PluginGovernanceSnapshot
import com.astrbot.android.model.plugin.PluginReviewState
import com.astrbot.android.model.plugin.PluginRiskLevel
import com.astrbot.android.model.plugin.PluginRuntimeHealthStatus
import com.astrbot.android.model.plugin.PluginTrustLevel
import com.astrbot.android.ui.viewmodel.PluginDetailActionState
import com.astrbot.android.ui.viewmodel.PluginScreenUiState

enum class PluginDetailSection {
    TopSummary,
    UnderstandPlugin,
    ManagePlugin,
    TechnicalMetadata,
}

internal data class PluginGovernanceDisplayItem(
    val labelRes: Int,
    val valueRes: Int,
)

internal data class PluginGovernanceReadOnlyState(
    val isReadOnly: Boolean = false,
    val message: String = "",
)

@Suppress("UNUSED_PARAMETER")
internal fun buildPluginDetailSections(
    uiState: PluginScreenUiState,
): List<PluginDetailSection> {
    return buildList {
        add(PluginDetailSection.TopSummary)
        add(PluginDetailSection.ManagePlugin)
        add(PluginDetailSection.UnderstandPlugin)
        add(PluginDetailSection.TechnicalMetadata)
    }
}

internal fun buildGovernanceDisplayItems(governance: PluginGovernanceSnapshot): List<PluginGovernanceDisplayItem> {
    return listOf(
        PluginGovernanceDisplayItem(
            labelRes = R.string.plugin_field_risk_level,
            valueRes = riskLevelLabelRes(governance.riskLevel),
        ),
        PluginGovernanceDisplayItem(
            labelRes = R.string.plugin_field_trust_level,
            valueRes = trustLevelLabelRes(governance.trustLevel),
        ),
        PluginGovernanceDisplayItem(
            labelRes = R.string.plugin_field_review_state,
            valueRes = reviewStateLabelRes(governance.reviewState),
        ),
    )
}

internal fun buildRegistrationSummaryText(governance: PluginGovernanceSnapshot): String {
    val summary = governance.registrationSummary
    return "Handlers ${summary.messageHandlerCount}, commands ${summary.commandCount}, tools ${summary.toolCount}"
}

internal fun buildCapabilityGrantSummaryText(governance: PluginGovernanceSnapshot): String {
    return governance.capabilityGrants
        .map { grant -> grant.title.ifBlank { grant.permissionId } }
        .takeIf { grants -> grants.isNotEmpty() }
        ?.joinToString(separator = ", ")
        ?: "No capability grants"
}

internal fun buildDiagnosticsSummaryText(governance: PluginGovernanceSnapshot): String {
    val summary = governance.diagnosticsSummary
    val base = "${summary.totalCount} issues"
    return summary.lastFailureSummary
        .takeIf { it.isNotBlank() }
        ?.let { lastFailure -> "$base, last: $lastFailure" }
        ?: base
}

internal fun buildPluginGovernanceReadOnlyState(
    record: com.astrbot.android.model.plugin.PluginInstallRecord?,
    actionState: PluginDetailActionState,
): PluginGovernanceReadOnlyState {
    if (record == null) return PluginGovernanceReadOnlyState()
    return PluginGovernanceReadOnlyState(
        isReadOnly = actionState.mutationGate.isReadOnly,
        message = actionState.mutationGate.blockedMessage,
    )
}

internal fun runtimeHealthLabel(status: PluginRuntimeHealthStatus): String {
    return when (status) {
        PluginRuntimeHealthStatus.Healthy -> "Healthy"
        PluginRuntimeHealthStatus.BootstrapFailed -> "Bootstrap failed"
        PluginRuntimeHealthStatus.Suspended -> "Suspended"
        PluginRuntimeHealthStatus.Disabled -> "Disabled"
        PluginRuntimeHealthStatus.UnsupportedProtocol -> "Unsupported protocol"
        PluginRuntimeHealthStatus.UpgradeRequired -> "Upgrade required"
    }
}

private fun riskLevelLabelRes(riskLevel: PluginRiskLevel): Int {
    return when (riskLevel) {
        PluginRiskLevel.LOW -> R.string.plugin_risk_low
        PluginRiskLevel.MEDIUM -> R.string.plugin_risk_medium
        PluginRiskLevel.HIGH -> R.string.plugin_risk_high
        PluginRiskLevel.CRITICAL -> R.string.plugin_risk_critical
    }
}

private fun trustLevelLabelRes(trustLevel: PluginTrustLevel): Int {
    return when (trustLevel) {
        PluginTrustLevel.LOCAL_PACKAGE -> R.string.plugin_trust_local_package
        PluginTrustLevel.DIRECT_SOURCE -> R.string.plugin_trust_direct_source
        PluginTrustLevel.REPOSITORY_LISTED -> R.string.plugin_trust_repository_listed
    }
}

private fun reviewStateLabelRes(reviewState: PluginReviewState): Int {
    return when (reviewState) {
        PluginReviewState.UNREVIEWED -> R.string.plugin_review_unreviewed
        PluginReviewState.LOCAL_CHECKS_PASSED -> R.string.plugin_review_local_checks_passed
        PluginReviewState.HOST_COMPATIBILITY_BLOCKED -> R.string.plugin_review_host_compatibility_blocked
    }
}
