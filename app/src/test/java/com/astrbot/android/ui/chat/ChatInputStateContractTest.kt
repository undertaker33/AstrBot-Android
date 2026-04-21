package com.astrbot.android.ui.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Source-scanning contract test for Chat input state ownership.
 */
class ChatInputStateContractTest {

    private val chatScreenFile = resolvePreferredFile(
        "app/src/main/java/com/astrbot/android/feature/chat/presentation/ChatScreen.kt",
        "src/main/java/com/astrbot/android/feature/chat/presentation/ChatScreen.kt",
    )
    private val chatInputFile = resolvePreferredFile(
        "app/src/main/java/com/astrbot/android/feature/chat/presentation/ChatInputComponents.kt",
        "src/main/java/com/astrbot/android/feature/chat/presentation/ChatInputComponents.kt",
    )

    @Test
    fun `ChatScreen does not derive latestMessageScrollKey from content length`() {
        val text = chatScreenFile.readText()
        assertFalse(
            "ChatScreen must not use content.length in a scroll key",
            text.contains("content.length") && text.contains("latestMessageScrollKey"),
        )
    }

    @Test
    fun `ChatInputComponents owns BasicTextField`() {
        val text = chatInputFile.readText()
        assertTrue(
            "ChatInputComponents must contain BasicTextField",
            text.contains("BasicTextField("),
        )
    }

    @Test
    fun `ChatScreen passes onSend as callback and does not perform runtime orchestration`() {
        val text = chatScreenFile.readText()
        assertTrue(
            "ChatScreen must contain onSend callback",
            text.contains("onSend ="),
        )
        assertFalse(
            "ChatScreen must not import RuntimeOrchestrator",
            text.contains("import com.astrbot.android.feature.plugin.runtime.RuntimeOrchestrator"),
        )
        assertFalse(
            "ChatScreen must not import RuntimeContextResolver",
            text.contains("import com.astrbot.android.core.runtime.context.RuntimeContextResolver"),
        )
    }

    @Test
    fun `feature chat sources exist and legacy ui chat sources do not`() {
        assertTrue("feature/chat/presentation ChatScreen.kt must exist", chatScreenFile.exists())
        assertTrue("feature/chat/presentation ChatInputComponents.kt must exist", chatInputFile.exists())
        assertFalse(
            "Legacy ui/chat ChatScreen.kt must not be used to satisfy this contract",
            File("app/src/main/java/com/astrbot/android/ui/chat/ChatScreen.kt").exists() ||
                File("src/main/java/com/astrbot/android/ui/chat/ChatScreen.kt").exists(),
        )
        assertFalse(
            "Legacy ui/chat ChatInputComponents.kt must not be used to satisfy this contract",
            File("app/src/main/java/com/astrbot/android/ui/chat/ChatInputComponents.kt").exists() ||
                File("src/main/java/com/astrbot/android/ui/chat/ChatInputComponents.kt").exists(),
        )
    }

    private fun resolvePreferredFile(vararg candidates: String): File {
        return candidates
            .map(::File)
            .firstOrNull { it.exists() }
            ?: error("Expected one of these source files to exist: ${candidates.joinToString()}")
    }
}
