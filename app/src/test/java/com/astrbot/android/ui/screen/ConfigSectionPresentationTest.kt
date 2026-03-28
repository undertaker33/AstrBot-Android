package com.astrbot.android.ui.screen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigSectionPresentationTest {
    @Test
    fun `blank descriptions are not rendered`() {
        assertFalse(shouldRenderConfigDescription(""))
        assertFalse(shouldRenderConfigDescription("   "))
        assertTrue(shouldRenderConfigDescription("visible"))
    }
}
