package com.astrbot.android.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.navigation.NavBackStackEntry

internal enum class NavigationMotionLevel {
    TopLevel,
    Secondary,
    Detail,
}

// 把 route 归类为主导航、二级页、详情页，方便统一决定转场强度。
internal fun classifyNavigationMotionLevel(route: String?): NavigationMotionLevel {
    val normalizedRoute = route.orEmpty()
    return when {
        normalizedRoute in setOf("bots", "plugins", "chat", "config", "me") -> NavigationMotionLevel.TopLevel
        normalizedRoute.startsWith("plugins/detail/") || normalizedRoute == "plugins/detail/{pluginId}" -> NavigationMotionLevel.Detail
        normalizedRoute.startsWith("plugins/market/detail/") ||
            normalizedRoute == "plugins/market/detail/{pluginId}" -> NavigationMotionLevel.Detail
        normalizedRoute.startsWith("config/detail/") || normalizedRoute == "config/detail/{configId}" -> NavigationMotionLevel.Detail
        normalizedRoute.startsWith("asset-management/") && normalizedRoute != "asset-management" -> NavigationMotionLevel.Detail
        normalizedRoute in setOf(
            "logs",
            "qq-account",
            "qq-login",
            "settings-hub",
            "asset-management",
            "backup-hub",
            "backup/conversations",
            "models",
            "runtime",
        ) -> NavigationMotionLevel.Secondary
        else -> NavigationMotionLevel.Secondary
    }
}

// 底部导航栏的几个主页面切换，统一使用同一套轻量转场。
internal fun isBottomNavigationSwitch(
    initialRoute: String?,
    targetRoute: String?,
): Boolean {
    return classifyNavigationMotionLevel(initialRoute) == NavigationMotionLevel.TopLevel &&
        classifyNavigationMotionLevel(targetRoute) == NavigationMotionLevel.TopLevel
}

internal object AppNavigationTransitions {
    // 前进动画：主导航之间偏轻，层级越深位移感越明显。
    fun enterTransition(
        initialRoute: String?,
        targetRoute: String?,
    ): EnterTransition {
        val targetLevel = classifyNavigationMotionLevel(targetRoute)
        return when {
            isBottomNavigationSwitch(initialRoute, targetRoute) -> {
                EnterTransition.None
            }

            targetLevel == NavigationMotionLevel.TopLevel -> {
                fadeIn(animationSpec = tween(AppMotionTokens.routeFadeMillis))
            }

            else -> {
                slideInHorizontally(
                    animationSpec = tween(AppMotionTokens.routePushMillis),
                    initialOffsetX = { width -> width / AppMotionTokens.routeSlideDivisor },
                ) + fadeIn(animationSpec = tween(AppMotionTokens.routePushMillis))
            }
        }
    }

    // 当前页退出动画与进入动画配对，保证层级感一致。
    fun exitTransition(
        initialRoute: String?,
        targetRoute: String?,
    ): ExitTransition {
        val initialLevel = classifyNavigationMotionLevel(initialRoute)
        val targetLevel = classifyNavigationMotionLevel(targetRoute)
        return when {
            isBottomNavigationSwitch(initialRoute, targetRoute) -> {
                ExitTransition.None
            }

            initialLevel == NavigationMotionLevel.TopLevel && targetLevel != NavigationMotionLevel.TopLevel -> {
                ExitTransition.None
            }

            else -> {
                slideOutHorizontally(
                    animationSpec = tween(AppMotionTokens.routePushMillis),
                    targetOffsetX = { width -> -width / 12 },
                ) + fadeOut(animationSpec = tween(AppMotionTokens.routePushMillis))
            }
        }
    }

    // 返回动画与前进方向相反，让用户更容易感知"回退"。
    fun popEnterTransition(
        initialRoute: String?,
        targetRoute: String?,
    ): EnterTransition {
        val targetLevel = classifyNavigationMotionLevel(targetRoute)
        return when {
            isBottomNavigationSwitch(initialRoute, targetRoute) -> {
                EnterTransition.None
            }

            targetLevel == NavigationMotionLevel.TopLevel -> {
                EnterTransition.None
            }

            else -> {
                slideInHorizontally(
                    animationSpec = tween(AppMotionTokens.routePopMillis),
                    initialOffsetX = { width -> -width / AppMotionTokens.routeSlideDivisor },
                ) + fadeIn(animationSpec = tween(AppMotionTokens.routePopMillis))
            }
        }
    }

    fun popExitTransition(
        initialRoute: String?,
        targetRoute: String?,
    ): ExitTransition {
        return when {
            isBottomNavigationSwitch(initialRoute, targetRoute) -> {
                ExitTransition.None
            }

            else -> {
                slideOutHorizontally(
                    animationSpec = tween(AppMotionTokens.routePopMillis),
                    targetOffsetX = { width -> width / AppMotionTokens.routeSlideDivisor },
                ) + fadeOut(animationSpec = tween(AppMotionTokens.routePopMillis))
            }
        }
    }
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.appDestinationTransitions() =
    AppNavigationTransitions.enterTransition(initialState.destination.route, targetState.destination.route)
        .togetherWith(AppNavigationTransitions.exitTransition(initialState.destination.route, targetState.destination.route))
