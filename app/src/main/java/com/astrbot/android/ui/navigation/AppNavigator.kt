package com.astrbot.android.ui.navigation

import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController

// 缁熶竴鏀跺彛瀵艰埅鍔ㄤ綔锛岄伩鍏?AstrBotApp 鍜岄〉闈㈠洖璋冮噷鏁ｈ惤 route 鎷兼帴缁嗚妭銆?
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
