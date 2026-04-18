package com.astrbot.android.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmSourceContractTest {

    private val mainRoot: Path = listOf(
        Path.of("src/main/java/com/astrbot/android"),
        Path.of("app/src/main/java/com/astrbot/android"),
    ).first { it.exists() }

    @Test
    fun core_runtime_llm_contracts_exist() {
        val required = listOf(
            "core/runtime/llm/LlmInvocationContracts.kt",
            "core/runtime/llm/LlmClientPort.kt",
            "core/runtime/llm/RuntimeLlmOrchestratorPort.kt",
        )
        val missing = required.filterNot { mainRoot.resolve(it).exists() }
        assertTrue("Missing LLM contract files: $missing", missing.isEmpty())
    }

    @Test
    fun runtime_llm_adapters_exist() {
        val required = listOf(
            "runtime/llm/LegacyChatCompletionServiceAdapter.kt",
            "runtime/llm/LegacyRuntimeOrchestratorAdapter.kt",
        )
        val missing = required.filterNot { mainRoot.resolve(it).exists() }
        assertTrue("Missing LLM adapter files: $missing", missing.isEmpty())
    }

    @Test
    fun scheduled_task_runtime_executor_does_not_import_chat_completion_service() {
        val file = mainRoot.resolve("runtime/cron/ScheduledTaskRuntimeExecutor.kt")
        assertTrue("ScheduledTaskRuntimeExecutor.kt must exist", file.exists())
        val text = file.readText()
        assertTrue(
            "ScheduledTaskRuntimeExecutor must not directly import ChatCompletionService (phase 3 migration)",
            !text.contains("import com.astrbot.android.data.ChatCompletionService"),
        )
    }

    @Test
    fun sendConfiguredChatWithTools_only_called_from_allowed_locations() {
        val allowedFiles = setOf(
            "runtime/llm/LegacyChatCompletionServiceAdapter.kt",
            // Legacy files not yet migrated — allowlist with phase notes:
            "runtime/OneBotBridgeServer.kt",                          // phase 5 migration target
            "di/AstrBotViewModelDependencies.kt",                     // legacy bridge, future migration
            "feature/chat/runtime/AppChatRuntimeService.kt",          // uses via dependencies interface
            "data/ChatCompletionService.kt",                          // definition site
        )

        val violations = kotlinFilesUnder(".")
            .filter { file ->
                val relative = mainRoot.relativize(file).toString().replace('\\', '/')
                relative !in allowedFiles && !relative.startsWith("../")
            }
            .flatMap { file ->
                val text = file.readText()
                val relative = mainRoot.relativize(file).toString().replace('\\', '/')
                if (text.contains("sendConfiguredChatWithTools") && !relative.startsWith("data/")) {
                    listOf("$relative calls sendConfiguredChatWithTools directly")
                } else {
                    emptyList()
                }
            }

        assertTrue(
            "sendConfiguredChatWithTools should only be called from LegacyChatCompletionServiceAdapter or allowed legacy files: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun sendConfiguredChatStreamWithTools_only_called_from_allowed_locations() {
        val allowedFiles = setOf(
            "runtime/llm/LegacyChatCompletionServiceAdapter.kt",
            // Legacy files not yet migrated — allowlist with phase notes:
            "runtime/OneBotBridgeServer.kt",                          // phase 5 migration target
            "di/AstrBotViewModelDependencies.kt",                     // legacy bridge, future migration
            "feature/chat/runtime/AppChatRuntimeService.kt",          // uses via dependencies interface
            "data/ChatCompletionService.kt",                          // definition site
        )

        val violations = kotlinFilesUnder(".")
            .filter { file ->
                val relative = mainRoot.relativize(file).toString().replace('\\', '/')
                relative !in allowedFiles && !relative.startsWith("../")
            }
            .flatMap { file ->
                val text = file.readText()
                val relative = mainRoot.relativize(file).toString().replace('\\', '/')
                if (text.contains("sendConfiguredChatStreamWithTools") && !relative.startsWith("data/")) {
                    listOf("$relative calls sendConfiguredChatStreamWithTools directly")
                } else {
                    emptyList()
                }
            }

        assertTrue(
            "sendConfiguredChatStreamWithTools should only be called from LegacyChatCompletionServiceAdapter or allowed legacy files: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun migrated_runtime_files_do_not_use_legacy_tool_definition() {
        val migratedPaths = listOf("runtime/cron")
        val violations = migratedPaths.flatMap { path ->
            kotlinFilesUnder(path).flatMap { file ->
                val text = file.readText()
                val relative = mainRoot.relativize(file).toString().replace('\\', '/')
                if (text.contains("ChatCompletionService.ChatToolDefinition")) {
                    listOf("$relative uses ChatCompletionService.ChatToolDefinition directly")
                } else {
                    emptyList()
                }
            }
        }
        assertTrue(
            "Migrated runtime files should use LlmToolDefinition instead of ChatCompletionService.ChatToolDefinition: $violations",
            violations.isEmpty(),
        )
    }

    private fun kotlinFilesUnder(relative: String): List<Path> {
        val root = if (relative == ".") mainRoot else mainRoot.resolve(relative)
        if (!root.exists()) return emptyList()
        return Files.walk(root).use { stream ->
            stream
                .filter { it.isRegularFile() && it.fileName.toString().endsWith(".kt") }
                .toList()
        }
    }
}
