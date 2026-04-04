package com.astrbot.android.ui.screen.plugin

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.astrbot.android.model.plugin.PluginCompatibilityStatus
import com.astrbot.android.model.plugin.PluginRiskLevel
import com.astrbot.android.model.plugin.PluginUiActionStyle
import com.astrbot.android.model.plugin.PluginUiStatus
import com.astrbot.android.ui.MonochromeUi

data class PluginBadgePalette(
    val containerColor: Color,
    val contentColor: Color,
)

data class PluginSchemaStatusPalette(
    val containerColor: Color,
    val contentColor: Color,
)

data class PluginSchemaActionPalette(
    val containerColor: Color,
    val contentColor: Color,
    val borderColor: Color,
)

object PluginUiSpec {
    val ScreenHorizontalPadding: Dp = 14.dp
    val ScreenVerticalPadding: Dp = 14.dp
    val SectionSpacing: Dp = 12.dp
    val CardSpacing: Dp = 10.dp
    val InnerSpacing: Dp = 8.dp
    val ListContentBottomPadding: Dp = 104.dp
    val SchemaFieldSpacing: Dp = 8.dp
    val SchemaActionSpacing: Dp = 8.dp
    val SchemaContainerPadding: Dp = 18.dp
    val SchemaFieldGroupSpacing: Dp = 6.dp
    val SchemaRowHorizontalPadding: Dp = 14.dp
    val SchemaRowVerticalPadding: Dp = 10.dp
    val SchemaSectionPadding: Dp = 14.dp
    val SchemaSectionInnerSpacing: Dp = 10.dp
    val SchemaStatusChipHorizontalPadding: Dp = 10.dp
    val SchemaStatusChipVerticalPadding: Dp = 6.dp
    val SchemaActionBorderWidth: Dp = 1.dp
    val SchemaSelectedBorderWidth: Dp = 1.5.dp
    val FailureBannerPadding: Dp = 16.dp
    val FailureBannerSpacing: Dp = 8.dp
    val FailureBannerChipHorizontalPadding: Dp = 10.dp
    val FailureBannerChipVerticalPadding: Dp = 6.dp

    val SummaryShape = RoundedCornerShape(28.dp)
    val SectionShape = RoundedCornerShape(26.dp)
    val BadgeShape = RoundedCornerShape(999.dp)
    val EmptyStateShape = RoundedCornerShape(30.dp)

    val CardBorder: BorderStroke
        @Composable
        get() = BorderStroke(1.dp, MonochromeUi.border.copy(alpha = 0.9f))

    const val SummaryCardTag = "plugin-summary-card"
    const val HeroSectionTag = "plugin-homepage-hero"
    const val QuickInstallSectionTag = "plugin-homepage-quick-install"
    const val HealthOverviewSectionTag = "plugin-homepage-health-overview"
    const val InstalledLibrarySectionTag = "plugin-installed-library-section"
    const val InstalledLibraryControlsTag = "plugin-installed-library-controls"
    const val InstalledLibrarySearchTag = "plugin-installed-library-search"
    const val InstalledLibraryCardTag = "plugin-installed-library-card"
    const val LocalPageTag = "plugin-local-page"
    const val LocalSearchTag = "plugin-local-search"
    const val LocalFilterRowTag = "plugin-local-filter-row"
    const val LocalFilterChipTag = "plugin-local-filter-chip"
    const val LocalCardTag = "plugin-local-card"
    const val LocalCardChevronTag = "plugin-local-card-chevron"
    const val LocalInstallFabTag = "plugin-local-install-fab"
    const val LocalInstallDialogTag = "plugin-local-install-dialog"
    const val LocalInstallModeSelectorTag = "plugin-local-install-mode-selector"
    const val MarketPageTag = "plugin-market-page"
    const val DiscoverSectionTag = "plugin-discover-section"
    const val RepositoriesSectionTag = "plugin-repositories-section"
    const val PluginListTag = "plugin-list"
    const val InstalledSectionTag = "plugin-installed-section"
    const val RepositorySectionTag = "plugin-repository-section"
    const val DiscoverableSectionTag = "plugin-discoverable-section"
    const val DetailPanelTag = "plugin-detail-panel"
    const val DetailBackActionTag = "plugin-detail-back-action"
    const val DetailTopSummaryTag = "plugin-detail-top-summary"
    const val DetailPrimaryActionsTag = "plugin-detail-primary-actions"
    const val DetailOverviewTag = "plugin-detail-overview"
    const val DetailSafetyCompatibilityTag = "plugin-detail-safety-compatibility"
    const val DetailPluginPanelTag = "plugin-detail-plugin-panel"
    const val DetailTechnicalMetadataTag = "plugin-detail-technical-metadata"
    const val DetailOpenConfigActionTag = "plugin-detail-open-config-action"
    const val DetailOpenWorkspaceActionTag = "plugin-detail-open-workspace-action"
    const val DetailMetadataTag = "plugin-detail-metadata"
    const val DetailUpdateHintTag = "plugin-detail-update-hint"
    const val DetailPermissionDiffTag = "plugin-detail-permission-diff"
    const val DetailActionMessageTag = "plugin-detail-action-message"
    const val DetailUpgradeActionTag = "plugin-detail-upgrade-action"
    const val DetailUpgradeBlockedReasonTag = "plugin-detail-upgrade-blocked-reason"
    const val DetailEnableActionTag = "plugin-detail-enable-action"
    const val DetailDisableActionTag = "plugin-detail-disable-action"
    const val DetailUninstallActionTag = "plugin-detail-uninstall-action"
    const val DetailKeepDataPolicyTag = "plugin-detail-keep-data-policy"
    const val DetailRemoveDataPolicyTag = "plugin-detail-remove-data-policy"
    const val SchemaWorkspaceTag = "plugin-schema-workspace"
    const val SchemaTextTag = "plugin-schema-text"
    const val SchemaCardTag = "plugin-schema-card"
    const val SchemaCardStatusTag = "plugin-schema-card-status"
    const val SchemaCardFeedbackTag = "plugin-schema-card-feedback"
    const val SchemaMediaTag = "plugin-schema-media"
    const val SchemaErrorTag = "plugin-schema-error"
    const val SchemaSettingsTag = "plugin-schema-settings"
    const val SchemaStaticConfigTag = "plugin-schema-static-config"
    const val ConfigPageTag = "plugin-config-page"
    const val ConfigBackActionTag = "plugin-config-back-action"
    const val ConfigSummaryTag = "plugin-config-summary"
    const val ConfigStaticSectionTag = "plugin-config-static-section"
    const val ConfigRuntimeSectionTag = "plugin-config-runtime-section"
    const val ConfigEmptyStateTag = "plugin-config-empty-state"
    const val WorkspacePageTag = "plugin-workspace-page"
    const val WorkspaceSummaryTag = "plugin-workspace-summary"
    const val WorkspaceFilesSectionTag = "plugin-workspace-files-section"
    const val WorkspaceManagementSectionTag = "plugin-workspace-management-section"
    const val WorkspaceImportActionTag = "plugin-workspace-import-action"
    const val WorkspaceEmptyFilesTag = "plugin-workspace-empty-files"
    const val DetailFailureBannerTag = "plugin-failure-banner"
    const val DetailFailureSummaryTag = "plugin-failure-summary"
    const val DetailFailureRecoveryTag = "plugin-failure-recovery"

    fun pluginCardTag(pluginId: String): String = "plugin-card-$pluginId"
    fun installedLibraryCardTag(pluginId: String): String = "plugin-installed-library-card-${toTagSegment(pluginId)}"
    fun installedLibraryFilterTag(filterKey: String): String = "plugin-installed-library-filter-${toTagSegment(filterKey)}"
    fun installedLibraryPrimaryActionTag(pluginId: String): String = "plugin-installed-library-primary-action-${toTagSegment(pluginId)}"
    fun installedLibraryStatusTag(pluginId: String): String = "plugin-installed-library-status-${toTagSegment(pluginId)}"
    fun repositoryCardTag(sourceId: String): String = "plugin-repository-card-${toTagSegment(sourceId)}"
    fun discoverableCardTag(pluginId: String): String = "plugin-discoverable-card-${toTagSegment(pluginId)}"
    fun pluginFailureChipTag(pluginId: String): String = "plugin-failure-chip-${toTagSegment(pluginId)}"
    fun schemaCardActionTag(actionId: String): String = "plugin-schema-card-action-${toTagSegment(actionId)}"
    fun schemaMediaItemTag(index: Int): String = "plugin-schema-media-item-$index"
    fun schemaSettingsSectionTag(sectionId: String): String = "plugin-schema-section-${toTagSegment(sectionId)}"
    fun schemaSettingsToggleTag(fieldId: String): String = "plugin-schema-toggle-${toTagSegment(fieldId)}"
    fun schemaSettingsTextInputTag(fieldId: String): String = "plugin-schema-text-${toTagSegment(fieldId)}"
    fun schemaSettingsSelectTag(fieldId: String): String = "plugin-schema-select-${toTagSegment(fieldId)}"
    fun schemaSettingsSelectOptionTag(fieldId: String, optionValue: String): String {
        return "plugin-schema-select-${toTagSegment(fieldId)}-option-${toTagSegment(optionValue)}"
    }
    fun schemaStaticConfigSectionTag(sectionId: String): String = "plugin-static-config-section-${toTagSegment(sectionId)}"
    fun schemaStaticConfigFieldTag(fieldKey: String): String = "plugin-static-config-field-${toTagSegment(fieldKey)}"
    fun schemaStaticConfigDescriptionTag(fieldKey: String): String {
        return "plugin-static-config-description-${toTagSegment(fieldKey)}"
    }
    fun schemaStaticConfigHintTag(fieldKey: String): String = "plugin-static-config-hint-${toTagSegment(fieldKey)}"
    fun schemaStaticConfigDefaultTag(fieldKey: String): String = "plugin-static-config-default-${toTagSegment(fieldKey)}"
    fun schemaStaticConfigToggleTag(fieldKey: String): String = "plugin-static-config-toggle-${toTagSegment(fieldKey)}"
    fun schemaStaticConfigTextInputTag(fieldKey: String): String = "plugin-static-config-text-${toTagSegment(fieldKey)}"
    fun schemaStaticConfigSelectTag(fieldKey: String): String = "plugin-static-config-select-${toTagSegment(fieldKey)}"
    fun schemaStaticConfigSelectOptionTag(fieldKey: String, optionValue: String): String {
        return "plugin-static-config-select-${toTagSegment(fieldKey)}-option-${toTagSegment(optionValue)}"
    }
    fun localInstallModeOptionTag(mode: String): String = "plugin-local-install-mode-${toTagSegment(mode)}"
    fun workspaceFileTag(relativePath: String): String = "plugin-workspace-file-${toTagSegment(relativePath)}"
    fun workspaceDeleteActionTag(relativePath: String): String = "plugin-workspace-delete-${toTagSegment(relativePath)}"

    val EmptyStateContainerColor: Color
        @Composable
        get() = MonochromeUi.cardBackground

    val EmptyStateAccentColor: Color
        @Composable
        get() = MonochromeUi.mutedSurface

    val EmptyStateBodyColor: Color
        @Composable
        get() = MonochromeUi.textSecondary

    fun riskBadgePalette(level: PluginRiskLevel): PluginBadgePalette {
        return when (level) {
            PluginRiskLevel.LOW -> PluginBadgePalette(Color(0xFFD7F4DF), Color(0xFF155724))
            PluginRiskLevel.MEDIUM -> PluginBadgePalette(Color(0xFFFFE7BF), Color(0xFF8A5300))
            PluginRiskLevel.HIGH -> PluginBadgePalette(Color(0xFFFFD8CC), Color(0xFF9F2D00))
            PluginRiskLevel.CRITICAL -> PluginBadgePalette(Color(0xFFFFD4DA), Color(0xFF9C1731))
        }
    }

    fun compatibilityBadgePalette(status: PluginCompatibilityStatus): PluginBadgePalette {
        return when (status) {
            PluginCompatibilityStatus.COMPATIBLE -> PluginBadgePalette(Color(0xFFDCEEFF), Color(0xFF0B4F8A))
            PluginCompatibilityStatus.INCOMPATIBLE -> PluginBadgePalette(Color(0xFFFFD9D6), Color(0xFF9A1F14))
            PluginCompatibilityStatus.UNKNOWN -> PluginBadgePalette(Color(0xFFE6E8EC), Color(0xFF4C5665))
        }
    }

    fun failureBadgePalette(isSuspended: Boolean): PluginBadgePalette {
        return if (isSuspended) {
            PluginBadgePalette(Color(0xFFFFD9D6), Color(0xFF9A1F14))
        } else {
            PluginBadgePalette(Color(0xFFFFE8C1), Color(0xFF8A5300))
        }
    }

    fun failureBannerPalette(isSuspended: Boolean): PluginSchemaStatusPalette {
        return if (isSuspended) {
            PluginSchemaStatusPalette(
                containerColor = Color(0xFFFFECEA),
                contentColor = Color(0xFF9A1F14),
            )
        } else {
            PluginSchemaStatusPalette(
                containerColor = Color(0xFFFFF4DB),
                contentColor = Color(0xFF8A5300),
            )
        }
    }

    fun schemaStatusPalette(status: PluginUiStatus): PluginSchemaStatusPalette {
        return when (status) {
            PluginUiStatus.Info -> PluginSchemaStatusPalette(
                containerColor = Color(0xFFE6EFFA),
                contentColor = Color(0xFF0F4A7D),
            )
            PluginUiStatus.Success -> PluginSchemaStatusPalette(
                containerColor = Color(0xFFDEF4E5),
                contentColor = Color(0xFF165A2D),
            )
            PluginUiStatus.Warning -> PluginSchemaStatusPalette(
                containerColor = Color(0xFFFFEDC8),
                contentColor = Color(0xFF8D5600),
            )
            PluginUiStatus.Error -> PluginSchemaStatusPalette(
                containerColor = Color(0xFFFFE1DF),
                contentColor = Color(0xFFA1261A),
            )
        }
    }

    fun schemaActionPalette(style: PluginUiActionStyle): PluginSchemaActionPalette {
        return when (style) {
            PluginUiActionStyle.Default -> PluginSchemaActionPalette(
                containerColor = MonochromeUi.cardBackground,
                contentColor = MonochromeUi.textPrimary,
                borderColor = MonochromeUi.border,
            )
            PluginUiActionStyle.Primary -> PluginSchemaActionPalette(
                containerColor = MonochromeUi.fabBackground,
                contentColor = MonochromeUi.textPrimary,
                borderColor = MonochromeUi.fabBackground,
            )
            PluginUiActionStyle.Danger -> PluginSchemaActionPalette(
                containerColor = Color(0xFFFFECEA),
                contentColor = Color(0xFF9D2116),
                borderColor = Color(0xFFE2A29C),
            )
        }
    }

    fun detailTransition(isShowingDetail: Boolean): ContentTransform {
        return if (isShowingDetail) {
            (fadeIn(animationSpec = androidx.compose.animation.core.tween(220)) +
                slideInHorizontally(
                    animationSpec = androidx.compose.animation.core.tween(220),
                    initialOffsetX = { fullWidth -> fullWidth / 6 },
                )).togetherWith(
                fadeOut(animationSpec = androidx.compose.animation.core.tween(180)) +
                    slideOutHorizontally(
                        animationSpec = androidx.compose.animation.core.tween(180),
                        targetOffsetX = { fullWidth -> -fullWidth / 8 },
                    ),
            )
        } else {
            (fadeIn(animationSpec = androidx.compose.animation.core.tween(220)) +
                slideInHorizontally(
                    animationSpec = androidx.compose.animation.core.tween(220),
                    initialOffsetX = { fullWidth -> -fullWidth / 6 },
                )).togetherWith(
                fadeOut(animationSpec = androidx.compose.animation.core.tween(180)) +
                    slideOutHorizontally(
                        animationSpec = androidx.compose.animation.core.tween(180),
                        targetOffsetX = { fullWidth -> fullWidth / 8 },
                    ),
            )
        }
    }

    private fun toTagSegment(value: String): String {
        return value.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "unknown" }
    }
}
