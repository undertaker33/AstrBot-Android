package com.astrbot.android.ui.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Source-scanning contract test for Chat auto-scroll trigger.
 */
class ChatAutoScrollContractTest {

    private val chatScreenFile = resolvePreferredFile(
        "app/src/main/java/com/astrbot/android/feature/chat/presentation/ChatScreen.kt",
        "src/main/java/com/astrbot/android/feature/chat/presentation/ChatScreen.kt",
    )

    @Test
    fun `source does not contain content length near scroll key`() {
        val text = chatScreenFile.readText()
        assertFalse(
            "ChatScreen must not use content.length as a scroll trigger key",
            text.contains("content.length"),
        )
    }

    @Test
    fun `source contains latestMessageId`() {
        val text = chatScreenFile.readText()
        assertTrue(
            "ChatScreen must define latestMessageId",
            text.contains("latestMessageId"),
        )
    }

    @Test
    fun `LaunchedEffect uses messages size and latestMessageId`() {
        val text = chatScreenFile.readText()
        assertTrue(
            "ChatScreen must use LaunchedEffect(messages.size, latestMessageId)",
            text.contains("LaunchedEffect(messages.size, latestMessageId)"),
        )
    }

    @Test
    fun `feature chat screen exists and legacy ui chat screen does not`() {
        assertTrue("feature/chat/presentation ChatScreen.kt must exist", chatScreenFile.exists())
        assertFalse(
            "Legacy ui/chat ChatScreen.kt must not be used to satisfy this contract",
            File("app/src/main/java/com/astrbot/android/ui/chat/ChatScreen.kt").exists() ||
                File("src/main/java/com/astrbot/android/ui/chat/ChatScreen.kt").exists(),
        )
    }

    private fun resolvePreferredFile(vararg candidates: String): File {
        return candidates
            .map(::File)
            .firstOrNull { it.exists() }
            ?: error("Expected one of these source files to exist: ${candidates.joinToString()}")
    }
}
