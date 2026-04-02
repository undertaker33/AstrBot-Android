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
import com.astrbot.android.ui.MonochromeUi

data class PluginBadgePalette(
    val containerColor: Color,
    val contentColor: Color,
)

object PluginUiSpec {
    val ScreenHorizontalPadding: Dp = 14.dp
    val ScreenVerticalPadding: Dp = 14.dp
    val SectionSpacing: Dp = 12.dp
    val CardSpacing: Dp = 10.dp
    val InnerSpacing: Dp = 8.dp
    val ListContentBottomPadding: Dp = 104.dp

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

    fun pluginCardTag(pluginId: String): String = "plugin-card-$pluginId"

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
}
