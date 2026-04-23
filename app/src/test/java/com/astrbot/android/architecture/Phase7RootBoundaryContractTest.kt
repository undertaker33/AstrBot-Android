package com.astrbot.android.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase7RootBoundaryContractTest {

    private val mainRoot: Path = listOf(
        Path.of("src/main/java/com/astrbot/android"),
        Path.of("app/src/main/java/com/astrbot/android"),
    ).first { it.exists() }

    @Test
    fun app_main_must_not_contain_legacy_adapter_files_or_type_names() {
        val legacyAdapterPattern = Regex("""\bLegacy[A-Za-z0-9_]*Adapter\b""")
        val fileNameViolations = kotlinFilesUnder(".")
            .map { mainRoot.relativize(it).toString().replace('\\', '/') }
            .filter { it.substringAfterLast('/').startsWith("Legacy") && it.endsWith("Adapter.kt") }

        val typeViolations = kotlinFilesUnder(".").flatMap { file ->
            val relative = mainRoot.relativize(file).toString().replace('\\', '/')
            legacyAdapterPattern.findAll(file.readText())
                .map { match -> "$relative references ${match.value}" }
                .toList()
        }

        assertTrue(
            "app/src/main must not retain legacy adapter filenames: $fileNameViolations",
            fileNameViolations.isEmpty(),
        )
        assertTrue(
            "app/src/main must not retain legacy adapter type names: $typeViolations",
            typeViolations.isEmpty(),
        )
    }

    @Test
    fun root_runtime_llm_tree_must_not_keep_legacy_adapter_paths() {
        val legacyPaths = listOf(
            "runtime/llm/LegacyChatCompletionServiceAdapter.kt",
            "runtime/llm/LegacyLlmProviderProbeAdapter.kt",
            "runtime/llm/LegacyRuntimeOrchestratorAdapter.kt",
        ).filter { relativePath -> mainRoot.resolve(relativePath).exists() }

        assertTrue(
            "Root runtime/llm must not keep legacy adapter paths after phase 5: $legacyPaths",
            legacyPaths.isEmpty(),
        )
    }

    @Test
    fun feature_tree_must_not_new_legacy_adapters_or_reference_legacy_adapter_files() {
        val violations = kotlinFilesUnder("feature").flatMap { file ->
            val relative = mainRoot.relativize(file).toString().replace('\\', '/')
            val text = file.readText()
            buildList {
                if (Regex("""\bLegacy[A-Za-z0-9_]*Adapter\(""").containsMatchIn(text)) {
                    add("$relative instantiates a legacy adapter")
                }
                if (Regex("""Legacy[A-Za-z0-9_]*Adapter\.kt""").containsMatchIn(text)) {
                    add("$relative references a legacy adapter file path")
                }
            }
        }

        assertTrue(
            "Feature production code must not instantiate or path-reference legacy adapters: $violations",
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
