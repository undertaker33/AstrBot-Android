package com.astrbot.android.architecture

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class NoManualRuntimeSubgraphContractTest {

    private val projectRoot: Path = detectProjectRoot()
    private val mainRoot: Path = projectRoot.resolve("app/src/main/java/com/astrbot/android")
    private val forbiddenTokens = listOf(
        "QqPluginDispatchService(",
        "QqMessageRuntimeService(",
        "QqBotCommandRuntimeService(",
        "QqStreamingReplyService(",
    )

    @Test
    fun qq_runtime_graph_must_not_directly_construct_core_runtime_nodes() {
        val guardedFiles = listOf(
            "feature/qq/runtime/QqOneBotRuntimeGraph.kt",
            "feature/qq/runtime/QqOneBotRuntimeGraphFactory.kt",
        )
        val violations = guardedFiles.flatMap(::findViolations)

        assertTrue(
            "QQ runtime graph wiring must not directly construct runtime core nodes. Violations: $violations",
            violations.isEmpty(),
        )
    }

    private fun findViolations(relativePath: String): List<String> {
        val source = mainRoot.resolve(relativePath).readText()
        return forbiddenTokens
            .filter(source::contains)
            .map { token -> "$relativePath -> $token" }
    }

    private fun detectProjectRoot(): Path {
        val cwd = Path.of("").toAbsolutePath()
        return when {
            cwd.resolve("app/src/main/java/com/astrbot/android").exists() -> cwd
            cwd.resolve("src/main/java/com/astrbot/android").exists() -> cwd.parent
            cwd.parent?.resolve("app/src/main/java/com/astrbot/android")?.exists() == true -> cwd.parent
            else -> error("Unable to resolve project root from $cwd")
        }
    }
}
