package com.astrbot.android.ui.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Source-scanning contract test for Chat input state ownership.
 */
class ChatInputStateContractTest {

    private val chatScreenFile = File("src/main/java/com/astrbot/android/ui/chat/ChatScreen.kt")
    private val chatInputFile = File("src/main/java/com/astrbot/android/ui/chat/ChatInputComponents.kt")

    @Test
    fun `ChatScreen does not derive latestMessageScrollKey from content length`() {
        assertTrue("ChatScreen.kt must exist", chatScreenFile.exists())
        val text = chatScreenFile.readText()
        assertFalse(
            "ChatScreen must not use content.length in a scroll key",
            text.contains("content.length") && text.contains("latestMessageScrollKey"),
        )
    }

    @Test
    fun `ChatInputComponents owns BasicTextField`() {
        assertTrue("ChatInputComponents.kt must exist", chatInputFile.exists())
        val text = chatInputFile.readText()
        assertTrue(
            "ChatInputComponents must contain BasicTextField",
            text.contains("BasicTextField("),
        )
    }

    @Test
    fun `ChatScreen passes onSend as callback and does not perform runtime orchestration`() {
        assertTrue("ChatScreen.kt must exist", chatScreenFile.exists())
        val text = chatScreenFile.readText()
        assertTrue(
            "ChatScreen must contain onSend callback",
            text.contains("onSend ="),
        )
        assertFalse(
            "ChatScreen must not import RuntimeOrchestrator",
            text.contains("import com.astrbot.android.runtime.plugin.RuntimeOrchestrator"),
        )
        assertFalse(
            "ChatScreen must not import RuntimeContextResolver",
            text.contains("import com.astrbot.android.runtime.context.RuntimeContextResolver"),
        )
    }
}
