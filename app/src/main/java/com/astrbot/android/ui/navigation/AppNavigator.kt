package com.astrbot.android.ui.navigation

import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController

// 统一收口导航动作，避免 AstrBotApp 和页面回调里散落 route 拼接细节。
internal object AppNavigator {
    fun openTopLevel(navController: NavHostController, destination: AppDestination) {
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openTopLevelFresh(navController: NavHostController, destination: AppDestination) {
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = false }
            launchSingleTop = true
            restoreState = false
        }
    }

    fun open(navController: NavHostController, route: String) {
        navController.navigate(route)
    }

    fun back(navController: NavHostController) {
        navController.popBackStack()
    }
}
