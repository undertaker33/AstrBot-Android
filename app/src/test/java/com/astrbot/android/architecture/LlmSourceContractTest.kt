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
            "core/runtime/llm/LlmProviderProbePort.kt",
            "core/runtime/llm/ChatCompletionServiceLlmClient.kt",
            "core/runtime/llm/HiltLlmProviderProbePort.kt",
            "feature/plugin/runtime/RuntimeLlmOrchestratorPort.kt",
            "feature/plugin/runtime/DefaultRuntimeLlmOrchestrator.kt",
        )
        val missing = required.filterNot { relativePath -> mainRoot.resolve(relativePath).exists() }

        assertTrue("Missing LLM contract/support files: $missing", missing.isEmpty())
    }

    @Test
    fun legacy_llm_adapter_paths_and_names_must_be_absent_from_production() {
        val legacyPaths = listOf(
            "runtime/llm/LegacyChatCompletionServiceAdapter.kt",
            "runtime/llm/LegacyLlmProviderProbeAdapter.kt",
            "runtime/llm/LegacyRuntimeOrchestratorAdapter.kt",
        ).filter { relativePath -> mainRoot.resolve(relativePath).exists() }

        val legacyNameViolations = kotlinFilesUnder(".").flatMap { file ->
            val relative = mainRoot.relativize(file).toString().replace('\\', '/')
            Regex("""\bLegacy(?:ChatCompletionService|LlmProviderProbe|RuntimeOrchestrator)Adapter\b""")
                .findAll(file.readText())
                .map { match -> "$relative references ${match.value}" }
                .toList()
        }

        assertTrue("Legacy LLM adapter paths must be removed: $legacyPaths", legacyPaths.isEmpty())
        assertTrue(
            "Production code must not reference legacy LLM adapter names: $legacyNameViolations",
            legacyNameViolations.isEmpty(),
        )
    }

    @Test
    fun feature_code_must_not_directly_import_chat_completion_service() {
        val allowedFiles = setOf(
            "core/runtime/llm/ChatCompletionService.kt",
            "core/runtime/llm/ChatCompletionServiceLlmClient.kt",
            "core/runtime/llm/HiltLlmProviderProbePort.kt",
            "core/runtime/llm/LlmMediaService.kt",
        )

        val violations = kotlinFilesUnder(".").flatMap { file ->
            val relative = mainRoot.relativize(file).toString().replace('\\', '/')
            if (relative in allowedFiles) {
                emptyList()
            } else if (file.readText().contains("import com.astrbot.android.core.runtime.llm.ChatCompletionService")) {
                listOf("$relative imports ChatCompletionService directly")
            } else {
                emptyList()
            }
        }

        assertTrue(
            "Only core LLM implementation files may import ChatCompletionService directly: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun production_code_must_not_directly_new_default_runtime_orchestrator_outside_hilt_module() {
        val allowedFiles = setOf(
            "di/hilt/RuntimeServicesModule.kt",
            "feature/plugin/runtime/DefaultRuntimeLlmOrchestrator.kt",
        )

        val violations = kotlinFilesUnder(".").flatMap { file ->
            val relative = mainRoot.relativize(file).toString().replace('\\', '/')
            if (relative in allowedFiles) {
                emptyList()
            } else if (file.readText().contains("DefaultRuntimeLlmOrchestrator()")) {
                listOf("$relative directly constructs DefaultRuntimeLlmOrchestrator()")
            } else {
                emptyList()
            }
        }

        assertTrue(
            "Only the Hilt module/definition site may construct DefaultRuntimeLlmOrchestrator(): $violations",
            violations.isEmpty(),
        )
    }

    private fun kotlinFilesUnder(relativeRoot: String): List<Path> {
        val root = if (relativeRoot == ".") mainRoot else mainRoot.resolve(relativeRoot)
        return Files.walk(root).use { stream ->
            stream
                .filter { it.isRegularFile() && it.fileName.toString().endsWith(".kt") }
                .toList()
        }
    }
}
