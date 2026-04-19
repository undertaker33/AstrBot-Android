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
     * and RuntimeLlmOrchestratorPort (the classes it now owns).
     */
    @Test
    fun `service imports runtime context resolver and orchestrator port`() {
        val serviceFile = sourceRoot.resolve("feature/chat/runtime/AppChatRuntimeService.kt")
        assertTrue("AppChatRuntimeService.kt must exist", serviceFile.exists())
        val text = serviceFile.readText()
        assertTrue(
            "Must import RuntimeContextResolver",
            text.contains("import com.astrbot.android.core.runtime.context.RuntimeContextResolver"),
        )
        assertTrue(
            "Must import RuntimeLlmOrchestratorPort",
            text.contains("import com.astrbot.android.feature.plugin.runtime.RuntimeLlmOrchestratorPort"),
        )
        assertTrue(
            "AppChatRuntimeService must not import the static RuntimeOrchestrator compatibility shell",
            !text.contains("import com.astrbot.android.feature.plugin.runtime.RuntimeOrchestrator"),
        )
    }

    /**
     * ChatViewModel must NOT import RuntimeContextResolver or RuntimeOrchestrator
     * after migration. This test is expected to FAIL until Task 6 removes the
     * imports from ChatViewModel.
     */
    @Test
    fun `chatViewModel does not import runtime resolver or orchestrator`() {
        val viewModelFile = sourceRoot.resolve("feature/chat/presentation/ChatViewModel.kt")
        assertTrue("ChatViewModel.kt must exist", viewModelFile.exists())
        val text = viewModelFile.readText()
        assertTrue(
            "ChatViewModel must NOT import RuntimeContextResolver",
            !text.contains("import com.astrbot.android.core.runtime.context.RuntimeContextResolver"),
        )
        assertTrue(
            "ChatViewModel must NOT import RuntimeOrchestrator",
            !text.contains("import com.astrbot.android.feature.plugin.runtime.RuntimeOrchestrator"),
        )
    }

    @Test
    fun `app chat runtime helper services exist after phase 3 extraction`() {
        val required = listOf(
            "feature/chat/runtime/AppChatProviderInvocationService.kt",
            "feature/chat/runtime/AppChatPreparedReplyService.kt",
        )
        val missing = required.filterNot { relativePath ->
            sourceRoot.resolve(relativePath).exists()
        }
        assertTrue("Missing App Chat runtime helper services: $missing", missing.isEmpty())
    }

    @Test
    fun `service does not directly invoke provider tool APIs after extraction`() {
        val serviceFile = sourceRoot.resolve("feature/chat/runtime/AppChatRuntimeService.kt")
        assertTrue("AppChatRuntimeService.kt must exist", serviceFile.exists())
        val text = serviceFile.readText()
        assertTrue(
            "AppChatRuntimeService must not call sendConfiguredChatWithTools directly after phase 3 extraction",
            !text.contains("sendConfiguredChatWithTools"),
        )
        assertTrue(
            "AppChatRuntimeService must not call sendConfiguredChatStreamWithTools directly after phase 3 extraction",
            !text.contains("sendConfiguredChatStreamWithTools"),
        )
    }
}
