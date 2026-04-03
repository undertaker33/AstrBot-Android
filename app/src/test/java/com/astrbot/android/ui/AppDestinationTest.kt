package com.astrbot.android.ui

import java.lang.reflect.Method
import org.junit.Assert.assertEquals
import org.junit.Test

class AppDestinationTest {

    @Test
    fun `plugin workspace redesign keeps plugins as the top-level route`() {
        assertEquals("plugins", AppDestination.Plugins.route)
    }

    @Test
    fun `plugin detail route exposes parameterized route string`() {
        assertEquals("plugins/detail/{pluginId}", AppDestination.PluginDetail.route)
        assertEquals("plugins/detail/weather-toolkit", AppDestination.PluginDetail.routeFor("weather-toolkit"))
    }

    @Test
    fun `plugin detail route is treated as a detail motion level`() {
        assertEquals(NavigationMotionLevel.Detail, classifyNavigationMotionLevel(AppDestination.PluginDetail.route))
    }

    @Test
    fun `plugin workspace toggle selection index follows local and market tabs`() {
        val pluginWorkspaceTabClass = Class.forName("com.astrbot.android.ui.screen.PluginWorkspaceTab")
        val localTab = java.lang.Enum.valueOf(pluginWorkspaceTabClass.asSubclass(Enum::class.java), "LOCAL")
        val marketTab = java.lang.Enum.valueOf(pluginWorkspaceTabClass.asSubclass(Enum::class.java), "MARKET")
        val selectionIndexMethod = scaffoldMethod(
            "pluginWorkspaceToggleSelectionIndex",
            pluginWorkspaceTabClass,
        )

        assertEquals(0, selectionIndexMethod.invoke(null, localTab))
        assertEquals(1, selectionIndexMethod.invoke(null, marketTab))
    }

    private fun scaffoldMethod(name: String, vararg parameterTypes: Class<*>): Method {
        return Class.forName("com.astrbot.android.ui.navigation.AstrBotAppScaffoldPartsKt")
            .getDeclaredMethod(name, *parameterTypes)
    }
}
