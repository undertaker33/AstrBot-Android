package com.astrbot.android.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AppNavigationTransitionsTest {

    @Test
    fun `classifies top level routes`() {
        assertEquals(NavigationMotionLevel.TopLevel, classifyNavigationMotionLevel("chat"))
        assertEquals(NavigationMotionLevel.TopLevel, classifyNavigationMotionLevel("me"))
        assertEquals(NavigationMotionLevel.TopLevel, classifyNavigationMotionLevel("config"))
    }

    @Test
    fun `classifies secondary routes`() {
        assertEquals(NavigationMotionLevel.Secondary, classifyNavigationMotionLevel("settings-hub"))
        assertEquals(NavigationMotionLevel.Secondary, classifyNavigationMotionLevel("asset-management"))
        assertEquals(NavigationMotionLevel.Secondary, classifyNavigationMotionLevel("qq-account"))
    }

    @Test
    fun `classifies detail routes by pattern`() {
        assertEquals(NavigationMotionLevel.Detail, classifyNavigationMotionLevel("config/detail/{configId}"))
        assertEquals(NavigationMotionLevel.Detail, classifyNavigationMotionLevel("config/detail/profile-1"))
        assertEquals(NavigationMotionLevel.Detail, classifyNavigationMotionLevel("asset-management/tts-voice-assets"))
    }

    @Test
    fun `detects bottom navigation switches only between top level routes`() {
        assertEquals(true, isBottomNavigationSwitch("chat", "me"))
        assertEquals(false, isBottomNavigationSwitch("me", "settings-hub"))
        assertEquals(false, isBottomNavigationSwitch("asset-management", "asset-management/tts-voice-assets"))
    }

    @Test
    fun `pushing from top level to secondary keeps the current page stationary`() {
        assertSame(
            ExitTransition.None,
            AppNavigationTransitions.exitTransition("config", "settings-hub"),
        )
    }

    @Test
    fun `popping from secondary to top level keeps the destination page stationary`() {
        assertSame(
            EnterTransition.None,
            AppNavigationTransitions.popEnterTransition("settings-hub", "config"),
        )
    }
}
