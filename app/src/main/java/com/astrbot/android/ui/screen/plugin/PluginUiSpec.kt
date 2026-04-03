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
    const val PluginListTag = "plugin-list"
    const val DetailPanelTag = "plugin-detail-panel"
    const val DetailBackActionTag = "plugin-detail-back-action"
    const val DetailActionMessageTag = "plugin-detail-action-message"
    const val DetailEnableActionTag = "plugin-detail-enable-action"
    const val DetailDisableActionTag = "plugin-detail-disable-action"
    const val DetailUninstallActionTag = "plugin-detail-uninstall-action"
    const val DetailKeepDataPolicyTag = "plugin-detail-keep-data-policy"
    const val DetailRemoveDataPolicyTag = "plugin-detail-remove-data-policy"
    const val SchemaWorkspaceTag = "plugin-schema-workspace"
    const val SchemaCardTag = "plugin-schema-card"
    const val SchemaCardStatusTag = "plugin-schema-card-status"
    const val SchemaCardFeedbackTag = "plugin-schema-card-feedback"
    const val SchemaSettingsTag = "plugin-schema-settings"
    const val DetailFailureBannerTag = "plugin-failure-banner"
    const val DetailFailureSummaryTag = "plugin-failure-summary"
    const val DetailFailureRecoveryTag = "plugin-failure-recovery"

    fun pluginCardTag(pluginId: String): String = "plugin-card-$pluginId"
    fun pluginFailureChipTag(pluginId: String): String = "plugin-failure-chip-${toTagSegment(pluginId)}"
    fun schemaCardActionTag(actionId: String): String = "plugin-schema-card-action-${toTagSegment(actionId)}"
    fun schemaSettingsSectionTag(sectionId: String): String = "plugin-schema-section-${toTagSegment(sectionId)}"
    fun schemaSettingsToggleTag(fieldId: String): String = "plugin-schema-toggle-${toTagSegment(fieldId)}"
    fun schemaSettingsTextInputTag(fieldId: String): String = "plugin-schema-text-${toTagSegment(fieldId)}"
    fun schemaSettingsSelectTag(fieldId: String): String = "plugin-schema-select-${toTagSegment(fieldId)}"
    fun schemaSettingsSelectOptionTag(fieldId: String, optionValue: String): String {
        return "plugin-schema-select-${toTagSegment(fieldId)}-option-${toTagSegment(optionValue)}"
    }

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
