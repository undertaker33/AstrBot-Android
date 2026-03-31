package com.astrbot.android.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MainSwipeRailStateTest {

    @Test
    fun `main swipe pages follow the agreed seven page order`() {
        assertEquals(
            listOf(
                MainSwipePage.BOTS,
                MainSwipePage.MODELS,
                MainSwipePage.PERSONAS,
                MainSwipePage.PLUGINS,
                MainSwipePage.CHAT,
                MainSwipePage.CONFIG,
                MainSwipePage.ME,
            ),
            MainSwipePage.entries.toList(),
        )
    }

    @Test
    fun `drag preview reveals adjacent page while keeping current page visible`() {
        val preview = MainSwipeRailPreviewState.drag(
            from = MainSwipePage.CHAT,
            deltaFraction = -0.42f,
        )

        assertEquals(MainSwipePage.CHAT, preview.currentPage)
        assertEquals(MainSwipePage.CONFIG, preview.adjacentPage)
        assertTrue(preview.currentPageVisibleFraction > 0f)
        assertTrue(preview.adjacentPageVisibleFraction > 0f)
    }

    @Test
    fun `dragging past rail edge applies resistance instead of crossing boundary`() {
        val resisted = applyMainSwipeRailDrag(
            current = MainSwipePage.BOTS,
            currentOffsetFraction = 0f,
            dragDeltaFraction = 0.4f,
        )

        assertTrue(resisted > 0f)
        assertTrue(resisted < 0.4f)
    }

    @Test
    fun `settle keeps page when threshold is not crossed`() {
        assertEquals(
            MainSwipePage.PLUGINS,
            settleMainSwipePage(
                current = MainSwipePage.PLUGINS,
                deltaFraction = -0.18f,
                velocity = 0f,
            ),
        )
    }

    @Test
    fun `settle advances to adjacent page when drag crosses threshold`() {
        assertEquals(
            MainSwipePage.CHAT,
            settleMainSwipePage(
                current = MainSwipePage.PLUGINS,
                deltaFraction = -0.38f,
                velocity = 0f,
            ),
        )
    }

    @Test
    fun `chat page is reachable but does not allow horizontal page switching`() {
        assertEquals(false, mainSwipeEnabledForPage(MainSwipePage.CHAT))
        assertEquals(true, mainSwipeEnabledForPage(MainSwipePage.PLUGINS))
        assertEquals(true, mainSwipeEnabledForPage(MainSwipePage.CONFIG))
    }

    @Test
    fun `chat drawer container is rendered only while chat page is current`() {
        assertEquals(false, shouldRenderChatDrawer(renderedPage = MainSwipePage.CHAT, currentPage = MainSwipePage.PLUGINS))
        assertEquals(true, shouldRenderChatDrawer(renderedPage = MainSwipePage.CHAT, currentPage = MainSwipePage.CHAT))
    }
}
