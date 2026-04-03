package com.astrbot.android.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AppDestinationTest {

    @Test
    fun `plugin detail route exposes parameterized route string`() {
        assertEquals("plugins/detail/{pluginId}", AppDestination.PluginDetail.route)
        assertEquals("plugins/detail/weather-toolkit", AppDestination.PluginDetail.routeFor("weather-toolkit"))
    }

    @Test
    fun `plugin detail route is treated as a detail motion level`() {
        assertEquals(NavigationMotionLevel.Detail, classifyNavigationMotionLevel(AppDestination.PluginDetail.route))
    }
}
