package com.astrbot.android.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingBottomNavBarTest {

    @Test
    fun `main navigation destinations replace personas with plugins`() {
        assertEquals(
            listOf("bots", "plugins", "chat", "config", "me"),
            mainNavigationDestinationsForTest().map { it.route },
        )
    }

    @Test
    fun `bottom navigation routes map to seven page rail anchors`() {
        assertEquals(MainSwipePage.BOTS, mainSwipePageForBottomNavRoute(AppDestination.Bots.route))
        assertEquals(MainSwipePage.PLUGINS, mainSwipePageForBottomNavRoute(AppDestination.Plugins.route))
        assertEquals(MainSwipePage.CHAT, mainSwipePageForBottomNavRoute(AppDestination.Chat.route))
        assertEquals(MainSwipePage.CONFIG, mainSwipePageForBottomNavRoute(AppDestination.Config.route))
        assertEquals(MainSwipePage.ME, mainSwipePageForBottomNavRoute(AppDestination.Me.route))
    }

    @Test
    fun `shows floating bottom nav on top level routes without ime overlap`() {
        assertEquals(true, shouldShowFloatingBottomNav(AppDestination.Me.route, imeVisible = false, chatDrawerOpen = false))
        assertEquals(true, shouldShowFloatingBottomNav(AppDestination.Chat.route, imeVisible = false, chatDrawerOpen = false))
    }

    @Test
    fun `hides floating bottom nav when no top level route or chat ime is visible`() {
        assertEquals(false, shouldShowFloatingBottomNav(null, imeVisible = false, chatDrawerOpen = false))
        assertEquals(false, shouldShowFloatingBottomNav(AppDestination.Chat.route, imeVisible = true, chatDrawerOpen = false))
    }

    @Test
    fun `keeps floating bottom nav visible while chat drawer is open`() {
        assertEquals(true, shouldShowFloatingBottomNav(AppDestination.Chat.route, imeVisible = false, chatDrawerOpen = true))
    }

    @Test
    fun `global chat drawer stays attached for chat route or while drawer remains open`() {
        assertEquals(true, shouldHostGlobalChatDrawer(AppDestination.Chat.route, chatDrawerOpen = false))
        assertEquals(true, shouldHostGlobalChatDrawer(AppDestination.Plugins.route, chatDrawerOpen = true))
        assertEquals(false, shouldHostGlobalChatDrawer(AppDestination.Plugins.route, chatDrawerOpen = false))
    }

    @Test
    fun `route container does not reserve extra bottom padding for floating bottom nav`() {
        assertEquals(0.dp, floatingBottomNavContentPadding(activeMainRoute = AppDestination.Me.route, visible = true))
        assertEquals(0.dp, floatingBottomNavContentPadding(activeMainRoute = AppDestination.Chat.route, visible = false))
        assertEquals(0.dp, floatingBottomNavContentPadding(activeMainRoute = AppDestination.Chat.route, visible = true))
    }

    @Test
    fun `chat input area still reserves space for floating bottom nav`() {
        assertEquals(0.dp, chatBottomBarPadding(visible = false))
        assertEquals(FloatingBottomNavReservedPadding, chatBottomBarPadding(visible = true))
    }

    @Test
    fun `top level content and chat drawer both offset below top bar`() {
        assertEquals(82.dp, topLevelContentTopPadding(24.dp))
        assertEquals(90.dp, chatDrawerContentTopPadding(24.dp))
    }

    @Test
    fun `secondary pages do not inherit top level content padding`() {
        assertEquals(82.dp, navGraphContentTopPadding(AppDestination.Chat.route, 24.dp))
        assertEquals(0.dp, navGraphContentTopPadding(null, 24.dp))
    }

    @Test
    fun `secondary page headers use the same total top height as main top bars`() {
        assertEquals(82.dp, secondaryPageHeaderTotalHeight(24.dp))
        assertEquals(topLevelContentTopPadding(24.dp), secondaryPageHeaderTotalHeight(24.dp))
    }
}
