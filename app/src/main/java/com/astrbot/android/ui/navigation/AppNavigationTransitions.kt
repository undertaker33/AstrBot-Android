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

// 鎶?route 褰掔被涓轰富瀵艰埅銆佷簩绾ч〉銆佽鎯呴〉锛屾柟渚跨粺涓€鍐冲畾杞満寮哄害銆?
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

// 搴曢儴瀵艰埅鏍忕殑鍑犱釜涓婚〉闈㈠垏鎹紝缁熶竴浣跨敤鍚屼竴濂楄交閲忚浆鍦恒€?
internal fun isBottomNavigationSwitch(
    initialRoute: String?,
    targetRoute: String?,
): Boolean {
    return classifyNavigationMotionLevel(initialRoute) == NavigationMotionLevel.TopLevel &&
        classifyNavigationMotionLevel(targetRoute) == NavigationMotionLevel.TopLevel
}

internal object AppNavigationTransitions {
    // 鍓嶈繘鍔ㄧ敾锛氫富瀵艰埅涔嬮棿鍋忚交锛屽眰绾ц秺娣变綅绉绘劅瓒婃槑鏄俱€?
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

    // 褰撳墠椤甸€€鍑哄姩鐢讳笌杩涘叆鍔ㄧ敾閰嶅锛屼繚璇佸眰绾ф劅涓€鑷淬€?
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

    // 杩斿洖鍔ㄧ敾涓庡墠杩涙柟鍚戠浉鍙嶏紝璁╃敤鎴锋洿瀹规槗鎰熺煡鈥滃洖閫€鈥濄€?
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
