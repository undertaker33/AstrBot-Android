package com.astrbot.android.ui

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

    fun open(navController: NavHostController, route: String) {
        navController.navigate(route)
    }

    fun back(navController: NavHostController) {
        navController.popBackStack()
    }
}
