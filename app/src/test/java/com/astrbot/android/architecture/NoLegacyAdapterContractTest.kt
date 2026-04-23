package com.astrbot.android.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class NoLegacyAdapterContractTest {

    private val mainRoot: Path = listOf(
        Path.of("src/main/java/com/astrbot/android"),
        Path.of("app/src/main/java/com/astrbot/android"),
    ).first { it.exists() }

    @Test
    fun app_main_must_not_contain_legacy_adapter_filenames_or_declarations() {
        val legacyAdapterDeclarationPattern = Regex(
            """\b(?:class|interface|object|typealias)\s+Legacy[A-Za-z0-9_]*Adapter\b""",
        )

        val fileNameViolations = kotlinFilesUnder().map { mainRoot.relativize(it).toString().replace('\\', '/') }
            .filter { it.substringAfterLast('/').startsWith("Legacy") && it.endsWith("Adapter.kt") }

        val declarationViolations = kotlinFilesUnder().flatMap { file ->
            val relative = mainRoot.relativize(file).toString().replace('\\', '/')
            legacyAdapterDeclarationPattern.findAll(file.readText())
                .map { match -> "$relative declares ${match.value}" }
                .toList()
        }

        assertTrue(
            "Production filenames must not retain Legacy*Adapter.kt: $fileNameViolations",
            fileNameViolations.isEmpty(),
        )
        assertTrue(
            "Production declarations must not retain Legacy*Adapter names: $declarationViolations",
            declarationViolations.isEmpty(),
        )
    }

    private fun kotlinFilesUnder(): List<Path> {
        return Files.walk(mainRoot).use { stream ->
            stream
                .filter { it.isRegularFile() && it.fileName.toString().endsWith(".kt") }
                .toList()
        }
    }
}
