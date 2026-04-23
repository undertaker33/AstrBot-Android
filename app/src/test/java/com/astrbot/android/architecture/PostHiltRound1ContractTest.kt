package com.astrbot.android.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class PostHiltRound1ContractTest {

    private val projectRoot: Path = detectProjectRoot()
    private val mainRoot: Path = projectRoot.resolve("app/src/main/java/com/astrbot/android")

    @Test
    fun production_sources_must_not_reintroduce_non_hilt_di_entrypoints() {
        val forbiddenTokens = listOf(
            "org.koin",
            "KoinComponent",
            "Kodein",
            "Toothpick",
            "ViewModelProvider.Factory",
            "viewModelFactory",
            "astrBotViewModel(",
            "serviceLocator",
            "ServiceLocator",
        )

        val violations = kotlinFilesUnder(mainRoot).flatMap { file ->
            val relative = relativePathOf(file)
            val text = file.readText()
            forbiddenTokens.mapNotNull { token ->
                if (text.contains(token)) "$relative contains $token" else null
            }
        }

        assertTrue(
            "Production sources must remain Hilt-only after phase 5: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun production_sources_must_not_retain_static_install_or_registry_callbacks() {
        val forbiddenTokens = listOf(
            "installRuntimeDependencies(",
            "configureRuntimeDependenciesProvider(",
            "configureRuntimeDependenciesProvider {",
            "installFromHilt(",
            "RuntimeContextDataRegistry",
            "AppBackupDataRegistry",
            "ConversationBackupDataRegistry",
            "ContainerBridgeStateRegistry",
            "Phase3DataTransactionServiceRegistry",
        )

        val violations = kotlinFilesUnder(mainRoot).flatMap { file ->
            val relative = relativePathOf(file)
            val text = file.readText()
            forbiddenTokens.mapNotNull { token ->
                if (text.contains(token)) "$relative contains $token" else null
            }
        }

        assertTrue(
            "Production sources must fail closed on static install/registry callbacks: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun production_sources_must_not_contain_legacy_adapter_names() {
        val legacyAdapterPattern = Regex("""\bLegacy[A-Za-z0-9_]*Adapter\b""")
        val violations = kotlinFilesUnder(mainRoot).flatMap { file ->
            val relative = relativePathOf(file)
            legacyAdapterPattern.findAll(file.readText())
                .map { match -> "$relative references ${match.value}" }
                .toList()
        }

        assertTrue(
            "Post-Hilt production sources must not retain legacy adapter names: $violations",
            violations.isEmpty(),
        )
    }

    private fun kotlinFilesUnder(root: Path): List<Path> {
        return Files.walk(root).use { stream ->
            stream
                .filter { it.isRegularFile() && it.fileName.toString().endsWith(".kt") }
                .toList()
        }
    }

    private fun relativePathOf(file: Path): String =
        mainRoot.relativize(file).toString().replace('\\', '/')

    private fun detectProjectRoot(): Path {
        val cwd = Path.of("").toAbsolutePath()
        return when {
            cwd.resolve("app/src/main/java/com/astrbot/android").exists() -> cwd
            cwd.parent?.resolve("app/src/main/java/com/astrbot/android")?.exists() == true -> cwd.parent
            else -> error("Unable to resolve project root from $cwd")
        }
    }
}
