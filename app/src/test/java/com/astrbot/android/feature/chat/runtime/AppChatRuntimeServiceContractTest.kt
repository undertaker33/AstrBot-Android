package com.astrbot.android.feature.chat.runtime

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Source-scanning contract test for [AppChatRuntimeService].
 *
 * Verifies that runtime orchestration logic lives in the service
 * and NOT in the ViewModel after migration (Task 6).
 */
class AppChatRuntimeServiceContractTest {

    private val sourceRoot = File("src/main/java/com/astrbot/android")

    /**
     * AppChatRuntimeService must import RuntimeContextResolver
     * and RuntimeOrchestrator (the classes it now owns).
     */
    @Test
    fun `service imports runtime context resolver and orchestrator`() {
        val serviceFile = sourceRoot.resolve("feature/chat/runtime/AppChatRuntimeService.kt")
        assertTrue("AppChatRuntimeService.kt must exist", serviceFile.exists())
        val text = serviceFile.readText()
        assertTrue(
            "Must import RuntimeContextResolver",
            text.contains("import com.astrbot.android.runtime.context.RuntimeContextResolver"),
        )
        assertTrue(
            "Must import RuntimeOrchestrator",
            text.contains("import com.astrbot.android.runtime.plugin.RuntimeOrchestrator"),
        )
    }

    /**
     * ChatViewModel must NOT import RuntimeContextResolver or RuntimeOrchestrator
     * after migration. This test is expected to FAIL until Task 6 removes the
     * imports from ChatViewModel.
     */
    @Test
    fun `chatViewModel does not import runtime resolver or orchestrator`() {
        val viewModelFile = sourceRoot.resolve("ui/viewmodel/ChatViewModel.kt")
        assertTrue("ChatViewModel.kt must exist", viewModelFile.exists())
        val text = viewModelFile.readText()
        assertTrue(
            "ChatViewModel must NOT import RuntimeContextResolver",
            !text.contains("import com.astrbot.android.runtime.context.RuntimeContextResolver"),
        )
        assertTrue(
            "ChatViewModel must NOT import RuntimeOrchestrator",
            !text.contains("import com.astrbot.android.runtime.plugin.RuntimeOrchestrator"),
        )
    }
}
