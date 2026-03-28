package com.astrbot.android.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StringListDialogPresentationTest {
    @Test
    fun `string list editor dialog keeps a stable scroll threshold`() {
        assertEquals(360, stringListEditorDialogScrollableMaxHeightDp())
    }

    @Test
    fun `string list manager uses monochrome manage button styling`() {
        assertTrue(stringListManagerUsesMonochromeManageButton())
    }
}
