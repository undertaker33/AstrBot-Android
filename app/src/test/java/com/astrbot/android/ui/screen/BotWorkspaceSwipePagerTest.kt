package com.astrbot.android.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BotWorkspaceSwipePagerTest {

    @Test
    fun `swipe preview keeps current page edge while revealing adjacent page`() {
        val preview = BotWorkspaceSwipePagerPreviewState.drag(
            from = BotWorkspaceTab.BOTS,
            deltaFraction = -0.45f,
        )

        assertEquals(BotWorkspaceTab.BOTS, preview.currentTab)
        assertEquals(BotWorkspaceTab.MODELS, preview.adjacentTab)
        assertTrue(preview.currentTabVisibleFraction > 0f)
        assertTrue(preview.adjacentTabVisibleFraction > 0f)
    }

    @Test
    fun `drag settles to next tab only when threshold is crossed`() {
        assertEquals(
            BotWorkspaceTab.BOTS,
            settleBotWorkspaceTab(
                current = BotWorkspaceTab.BOTS,
                deltaFraction = -0.2f,
                velocity = 0f,
            ),
        )
        assertEquals(
            BotWorkspaceTab.MODELS,
            settleBotWorkspaceTab(
                current = BotWorkspaceTab.BOTS,
                deltaFraction = -0.45f,
                velocity = 0f,
            ),
        )
    }
}
