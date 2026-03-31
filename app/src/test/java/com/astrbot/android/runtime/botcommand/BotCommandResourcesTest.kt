package com.astrbot.android.runtime.botcommand

import org.junit.Assert.assertTrue
import org.junit.Test

class BotCommandResourcesTest {

    @Test
    fun `command resources expose localized command summary`() {
        val zhHelp = BotCommandResources.help("zh")
        val enHelp = BotCommandResources.help("en")

        assertTrue(zhHelp.contains("/help"))
        assertTrue(zhHelp.contains("/agent"))
        assertTrue(zhHelp.contains("/ls"))
        assertTrue(enHelp.contains("Built-in commands"))
        assertTrue(enHelp.contains("/sid"))
    }
}
