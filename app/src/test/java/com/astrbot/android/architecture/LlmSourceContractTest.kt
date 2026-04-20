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
        )
        val missing = required.filterNot { mainRoot.resolve(it).exists() }
        assertTrue("Missing LLM contract files: $missing", missing.isEmpty())
    }

    @Test
    fun feature_runtime_orchestrator_port_exists_at_phase7_location() {
        val file = mainRoot.resolve("feature/plugin/runtime/RuntimeLlmOrchestratorPort.kt")
        assertTrue(
            "RuntimeLlmOrchestratorPort must live under feature/plugin/runtime after phase 7 ownership migration",
            file.exists(),
        )
    }

    @Test
    fun runtime_llm_adapters_exist() {
        val required = listOf(
            "runtime/llm/LegacyChatCompletionServiceAdapter.kt",
            "feature/chat/presentation/ChatViewModelRuntimeBindings.kt", // chat runtime contract declarations
            "runtime/llm/LegacyRuntimeOrchestratorAdapter.kt",
            "feature/plugin/runtime/DefaultRuntimeLlmOrchestrator.kt",
        )
        val missing = required.filterNot { mainRoot.resolve(it).exists() }
        assertTrue("Missing LLM adapter files: $missing", missing.isEmpty())
    }

    @Test
    fun legacy_runtime_orchestrator_adapter_does_not_depend_on_static_feature_orchestrator() {
        val file = mainRoot.resolve("runtime/llm/LegacyRuntimeOrchestratorAdapter.kt")
        assertTrue("LegacyRuntimeOrchestratorAdapter.kt must exist", file.exists())
        val text = file.readText()
        assertTrue(
            "LegacyRuntimeOrchestratorAdapter must not import RuntimeOrchestrator directly after phase 3 migration",
            !text.contains("import com.astrbot.android.feature.plugin.runtime.RuntimeOrchestrator"),
        )
    }

    @Test
    fun app_bootstrapper_must_not_construct_default_runtime_orchestrator_directly() {
        val file = mainRoot.resolve("di/AppBootstrapper.kt")
        assertTrue("AppBootstrapper.kt must exist", file.exists())
        val text = file.readText()
        assertTrue(
            "AppBootstrapper must receive RuntimeLlmOrchestratorPort dependencies from Hilt instead of new DefaultRuntimeLlmOrchestrator()",
            !text.contains("DefaultRuntimeLlmOrchestrator()"),
        )
    }

    @Test
    fun scheduled_task_runtime_executor_does_not_import_chat_completion_service() {
        val file = mainRoot.resolve("feature/cron/runtime/ScheduledTaskRuntimeExecutor.kt")
        assertTrue("ScheduledTaskRuntimeExecutor.kt must exist", file.exists())
        val text = file.readText()
        assertTrue(
            "ScheduledTaskRuntimeExecutor must not directly import ChatCompletionService (phase 3 migration)",
            !text.contains("import com.astrbot.android.core.runtime.llm.ChatCompletionService"),
        )
    }

    @Test
    fun scheduled_task_runtime_helpers_exist_after_phase3_extraction() {
        val required = listOf(
            "feature/cron/runtime/ScheduledTaskProviderInvocationService.kt",
            "feature/cron/runtime/ScheduledTaskLlmCallbacksFactory.kt",
        )
        val missing = required.filterNot { relativePath -> mainRoot.resolve(relativePath).exists() }
        assertTrue("Missing scheduled task runtime helper services: $missing", missing.isEmpty())
    }

    @Test
    fun scheduled_task_runtime_executor_does_not_import_static_runtime_orchestrator() {
        val file = mainRoot.resolve("feature/cron/runtime/ScheduledTaskRuntimeExecutor.kt")
        assertTrue("ScheduledTaskRuntimeExecutor.kt must exist", file.exists())
        val text = file.readText()
        assertTrue(
            "ScheduledTaskRuntimeExecutor must not import RuntimeOrchestrator directly after phase 3 migration",
            !text.contains("import com.astrbot.android.feature.plugin.runtime.RuntimeOrchestrator"),
        )
    }

    @Test
    fun sendConfiguredChatWithTools_only_called_from_allowed_locations() {
        val allowedFiles = setOf(
            "runtime/llm/LegacyChatCompletionServiceAdapter.kt",
            "feature/chat/presentation/ChatViewModelRuntimeBindings.kt", // chat runtime contract declarations
            "di/hilt/DefaultChatViewModelRuntimeBindings.kt",         // phase 3 Hilt bridge
            "feature/chat/runtime/AppChatProviderInvocationService.kt", // phase 3 provider adapter
            "core/runtime/llm/ChatCompletionService.kt",              // definition site
        )

        val violations = kotlinFilesUnder(".")
            .filter { file ->
                val relative = mainRoot.relativize(file).toString().replace('\\', '/')
                relative !in allowedFiles && !relative.startsWith("../")
            }
            .flatMap { file ->
                val text = file.readText()
                val relative = mainRoot.relativize(file).toString().replace('\\', '/')
                if (text.contains("sendConfiguredChatWithTools") &&
                    !relative.startsWith("core/runtime/llm/")
                ) {
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
            "feature/chat/presentation/ChatViewModelRuntimeBindings.kt", // chat runtime contract declarations
            "di/hilt/DefaultChatViewModelRuntimeBindings.kt",         // phase 3 Hilt bridge
            "feature/chat/runtime/AppChatProviderInvocationService.kt", // phase 3 provider adapter
            "core/runtime/llm/ChatCompletionService.kt",              // definition site
        )

        val violations = kotlinFilesUnder(".")
            .filter { file ->
                val relative = mainRoot.relativize(file).toString().replace('\\', '/')
                relative !in allowedFiles && !relative.startsWith("../")
            }
            .flatMap { file ->
                val text = file.readText()
                val relative = mainRoot.relativize(file).toString().replace('\\', '/')
                if (text.contains("sendConfiguredChatStreamWithTools") &&
                    !relative.startsWith("core/runtime/llm/")
                ) {
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
        val migratedPaths = listOf("feature/cron/runtime")
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

    @Test
    fun feature_code_must_not_directly_import_ChatCompletionService() {
        val allowedPrefixes = setOf(
            "core/runtime/llm/",        // definition site and core utilities
            "runtime/llm/",             // adapter layer (legacy adapters)
            "di/",                      // Hilt module / bootstrapper residue
        )
        val violations = kotlinFilesUnder(".")
            .filter { file ->
                val relative = mainRoot.relativize(file).toString().replace('\\', '/')
                allowedPrefixes.none { relative.startsWith(it) }
            }
            .flatMap { file ->
                val text = file.readText()
                val relative = mainRoot.relativize(file).toString().replace('\\', '/')
                if (text.contains("import com.astrbot.android.core.runtime.llm.ChatCompletionService")) {
                    listOf("$relative directly imports ChatCompletionService")
                } else {
                    emptyList()
                }
            }
        assertTrue(
            "Feature production code must not directly import ChatCompletionService (use Hilt-provided ports): $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun production_code_must_not_directly_new_DefaultRuntimeLlmOrchestrator_outside_hilt_module() {
        val allowedFiles = setOf(
            "di/hilt/RuntimeServicesModule.kt",                         // Hilt provider
            "feature/plugin/runtime/DefaultRuntimeLlmOrchestrator.kt",  // definition site
            "feature/plugin/runtime/RuntimeOrchestrator.kt",            // deprecated compat shell
            "feature/chat/runtime/AppChatRuntimeService.kt",            // deprecated compat constructor (test seam)
        )
        val violations = kotlinFilesUnder(".")
            .filter { file ->
                val relative = mainRoot.relativize(file).toString().replace('\\', '/')
                relative !in allowedFiles
            }
            .flatMap { file ->
                val text = file.readText()
                val relative = mainRoot.relativize(file).toString().replace('\\', '/')
                if (text.contains("DefaultRuntimeLlmOrchestrator()")) {
                    listOf("$relative directly constructs DefaultRuntimeLlmOrchestrator()")
                } else {
                    emptyList()
                }
            }
        assertTrue(
            "Production code must not directly construct DefaultRuntimeLlmOrchestrator() outside Hilt module: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun llm_provider_probe_port_exists() {
        val file = mainRoot.resolve("core/runtime/llm/LlmProviderProbePort.kt")
        assertTrue(
            "LlmProviderProbePort must exist as the port interface for provider capability probing",
            file.exists(),
        )
    }

    @Test
    fun provider_runtime_port_must_not_import_ChatCompletionService() {
        val file = mainRoot.resolve("feature/provider/runtime/ProviderRuntimePort.kt")
        assertTrue("ProviderRuntimePort.kt must exist", file.exists())
        val text = file.readText()
        assertTrue(
            "ProviderRuntimePort must not import ChatCompletionService after LLM static base convergence",
            !text.contains("import com.astrbot.android.core.runtime.llm.ChatCompletionService"),
        )
    }

    @Test
    fun app_bootstrapper_must_not_expand_ChatCompletionService_usage_beyond_initialize() {
        val file = mainRoot.resolve("di/AppBootstrapper.kt")
        assertTrue("AppBootstrapper.kt must exist", file.exists())
        val text = file.readText()
        val occurrences = "ChatCompletionService".toRegex()
            .findAll(text)
            .count()
        // Allowed: 1 import + 1 initialize call = 2 occurrences
        assertTrue(
            "AppBootstrapper must only reference ChatCompletionService for import + initialize (found $occurrences occurrences)",
            occurrences <= 2,
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
