package com.astrbot.android.ui.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Source-scanning contract test for Chat auto-scroll trigger.
 */
class ChatAutoScrollContractTest {

    private val chatScreenFile = File("src/main/java/com/astrbot/android/ui/chat/ChatScreen.kt")

    @Test
    fun `source does not contain content length near scroll key`() {
        assertTrue("ChatScreen.kt must exist", chatScreenFile.exists())
        val text = chatScreenFile.readText()
        assertFalse(
            "ChatScreen must not use content.length as a scroll trigger key",
            text.contains("content.length"),
        )
    }

    @Test
    fun `source contains latestMessageId`() {
        assertTrue("ChatScreen.kt must exist", chatScreenFile.exists())
        val text = chatScreenFile.readText()
        assertTrue(
            "ChatScreen must define latestMessageId",
            text.contains("latestMessageId"),
        )
    }

    @Test
    fun `LaunchedEffect uses messages size and latestMessageId`() {
        assertTrue("ChatScreen.kt must exist", chatScreenFile.exists())
        val text = chatScreenFile.readText()
        assertTrue(
            "ChatScreen must use LaunchedEffect(messages.size, latestMessageId)",
            text.contains("LaunchedEffect(messages.size, latestMessageId)"),
        )
    }
}
