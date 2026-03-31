package com.astrbot.android.ui

import kotlin.math.abs

private const val MainSwipeSettleThresholdFraction = 0.33f
private const val MainSwipeVelocityThreshold = 1200f
private const val MainSwipeEdgeResistanceFactor = 0.35f

internal enum class MainSwipePage(
    val route: String,
) {
    BOTS(AppDestination.Bots.route),
    MODELS(AppDestination.Models.route),
    PERSONAS("personas-inline"),
    PLUGINS(AppDestination.Plugins.route),
    CHAT(AppDestination.Chat.route),
    CONFIG(AppDestination.Config.route),
    ME(AppDestination.Me.route),
}

internal data class MainSwipeRailPreviewState(
    val currentPage: MainSwipePage,
    val adjacentPage: MainSwipePage?,
    val currentPageVisibleFraction: Float,
    val adjacentPageVisibleFraction: Float,
) {
    companion object {
        fun drag(
            from: MainSwipePage,
            deltaFraction: Float,
        ): MainSwipeRailPreviewState {
            val adjacentPage = adjacentMainSwipePage(from, deltaFraction)
            val revealedFraction = abs(deltaFraction).coerceIn(0f, 1f)
            return MainSwipeRailPreviewState(
                currentPage = from,
                adjacentPage = adjacentPage,
                currentPageVisibleFraction = 1f - revealedFraction,
                adjacentPageVisibleFraction = if (adjacentPage == null) 0f else revealedFraction,
            )
        }
    }
}

internal fun mainSwipePageForBottomNavRoute(route: String?): MainSwipePage? {
    return when (route) {
        AppDestination.Bots.route -> MainSwipePage.BOTS
        AppDestination.Plugins.route -> MainSwipePage.PLUGINS
        AppDestination.Chat.route -> MainSwipePage.CHAT
        AppDestination.Config.route -> MainSwipePage.CONFIG
        AppDestination.Me.route -> MainSwipePage.ME
        else -> null
    }
}

internal fun currentMainSwipePage(
    activeMainRoute: String?,
    botWorkspaceTab: com.astrbot.android.ui.screen.BotWorkspaceTab,
): MainSwipePage {
    return when (activeMainRoute) {
        AppDestination.Bots.route -> mainSwipePageForBotWorkspaceTab(botWorkspaceTab)
        AppDestination.Plugins.route -> MainSwipePage.PLUGINS
        AppDestination.Chat.route -> MainSwipePage.CHAT
        AppDestination.Config.route -> MainSwipePage.CONFIG
        AppDestination.Me.route -> MainSwipePage.ME
        else -> MainSwipePage.CHAT
    }
}

internal fun mainSwipeEnabledForPage(page: MainSwipePage): Boolean = page != MainSwipePage.CHAT

internal fun shouldRenderChatDrawer(
    renderedPage: MainSwipePage,
    currentPage: MainSwipePage,
): Boolean = renderedPage == MainSwipePage.CHAT && currentPage == MainSwipePage.CHAT

internal fun applyMainSwipeRailDrag(
    current: MainSwipePage,
    currentOffsetFraction: Float,
    dragDeltaFraction: Float,
): Float {
    val proposed = currentOffsetFraction + dragDeltaFraction
    val canMoveForward = current != MainSwipePage.ME
    val canMoveBackward = current != MainSwipePage.BOTS
    return when {
        proposed < 0f && canMoveForward -> proposed.coerceAtLeast(-1f)
        proposed > 0f && canMoveBackward -> proposed.coerceAtMost(1f)
        else -> proposed * MainSwipeEdgeResistanceFactor
    }
}

internal fun settleMainSwipePage(
    current: MainSwipePage,
    deltaFraction: Float,
    velocity: Float,
): MainSwipePage {
    val adjacent = adjacentMainSwipePage(current, deltaFraction) ?: return current
    val crossedThreshold = abs(deltaFraction) >= MainSwipeSettleThresholdFraction
    val crossedVelocity = abs(velocity) >= MainSwipeVelocityThreshold
    return if (crossedThreshold || crossedVelocity) adjacent else current
}

internal fun mainSwipePageForBotWorkspaceTab(tab: com.astrbot.android.ui.screen.BotWorkspaceTab): MainSwipePage {
    return when (tab) {
        com.astrbot.android.ui.screen.BotWorkspaceTab.BOTS -> MainSwipePage.BOTS
        com.astrbot.android.ui.screen.BotWorkspaceTab.MODELS -> MainSwipePage.MODELS
        com.astrbot.android.ui.screen.BotWorkspaceTab.PERSONAS -> MainSwipePage.PERSONAS
    }
}

internal fun botWorkspaceTabForMainSwipePage(page: MainSwipePage): com.astrbot.android.ui.screen.BotWorkspaceTab? {
    return when (page) {
        MainSwipePage.BOTS -> com.astrbot.android.ui.screen.BotWorkspaceTab.BOTS
        MainSwipePage.MODELS -> com.astrbot.android.ui.screen.BotWorkspaceTab.MODELS
        MainSwipePage.PERSONAS -> com.astrbot.android.ui.screen.BotWorkspaceTab.PERSONAS
        else -> null
    }
}

private fun adjacentMainSwipePage(
    current: MainSwipePage,
    deltaFraction: Float,
): MainSwipePage? {
    return when {
        deltaFraction < 0f -> MainSwipePage.entries.getOrNull(current.ordinal + 1)
        deltaFraction > 0f -> MainSwipePage.entries.getOrNull(current.ordinal - 1)
        else -> null
    }
}
