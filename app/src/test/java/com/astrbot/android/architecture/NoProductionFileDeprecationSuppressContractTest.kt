package com.astrbot.android.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class NoProductionFileDeprecationSuppressContractTest {

    private data class SuppressUsage(
        val relativePath: String,
        val lineNumber: Int,
    )

    private val mainRoot: Path = listOf(
        Path.of("src/main/java/com/astrbot/android"),
        Path.of("app/src/main/java/com/astrbot/android"),
    ).first { it.exists() }

    private val fileLevelDeprecationSuppressPattern = Regex(
        """@file:Suppress\s*\((?s:.*?)"DEPRECATION"(?s:.*?)\)""",
    )

    private val declarationLevelDeprecationSuppressPattern = Regex(
        """(?m)^[ \t]*@Suppress\s*\((?s:.*?)"DEPRECATION"(?s:.*?)\)""",
    )

    @Test
    fun file_level_deprecation_suppress_must_be_confined_to_other_worker_owned_files() {
        val allowedPaths = emptySet<String>()

        val actualPaths = kotlinFilesUnder()
            .filter { file -> fileLevelDeprecationSuppressPattern.containsMatchIn(file.readText()) }
            .map { file -> relativePathOf(file) }
            .sorted()

        val unexpectedPaths = actualPaths.filterNot { it in allowedPaths }

        assertTrue(
            "Unexpected production @file:Suppress(\"DEPRECATION\") usage: $unexpectedPaths",
            unexpectedPaths.isEmpty(),
        )
    }

    @Test
    fun declaration_level_deprecation_suppress_must_be_confined_to_explicit_transitional_seams() {
        val allowedPaths = setOf(
            "core/runtime/container/DebPayloadExtractor.kt",
            "core/runtime/container/RootfsExtractor.kt",
            "core/runtime/container/RootfsOverlayExtractor.kt",
            "core/runtime/llm/ChatCompletionServiceLlmClient.kt",
            "feature/cron/runtime/CronJobScheduler.kt",
            "feature/persona/presentation/PersonaScreen.kt",
            "feature/plugin/data/FeaturePluginRepository.kt",
        )

        val actualUsages = kotlinFilesUnder()
            .flatMap { file ->
                declarationLevelDeprecationSuppressPattern.findAll(file.readText())
                    .map { match ->
                        SuppressUsage(
                            relativePath = relativePathOf(file),
                            lineNumber = lineNumberOf(match.range.first, file.readText()),
                        )
                    }
                    .toList()
            }
            .sortedWith(compareBy(SuppressUsage::relativePath, SuppressUsage::lineNumber))

        val unexpectedUsages = actualUsages
            .filterNot { it.relativePath in allowedPaths }
            .map { "${it.relativePath}:${it.lineNumber}" }

        assertTrue(
            "Unexpected production declaration-level @Suppress(\"DEPRECATION\") usage: $unexpectedUsages. " +
                "Only explicit transitional seams may carry declaration-level deprecation suppress.",
            unexpectedUsages.isEmpty(),
        )
    }

    private fun kotlinFilesUnder(): List<Path> {
        return Files.walk(mainRoot).use { stream ->
            stream
                .filter { it.isRegularFile() && it.fileName.toString().endsWith(".kt") }
                .toList()
        }
    }

    private fun relativePathOf(file: Path): String {
        return mainRoot.relativize(file).toString().replace('\\', '/')
    }

    private fun lineNumberOf(offset: Int, source: String): Int {
        return source.take(offset).count { it == '\n' } + 1
    }
}
