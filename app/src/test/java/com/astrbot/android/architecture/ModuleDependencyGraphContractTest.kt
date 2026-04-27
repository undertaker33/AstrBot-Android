package com.astrbot.android.architecture

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleDependencyGraphContractTest {

    private val projectRoot: Path = detectProjectRoot()
    private val settingsFile: Path = projectRoot.resolve("settings.gradle.kts")
    private val appBuildFile: Path = projectRoot.resolve("app/build.gradle.kts")

    private val phase3Modules = listOf(
        ":core:common",
        ":feature:bot:api",
        ":feature:config:api",
        ":feature:cron:api",
        ":feature:persona:api",
        ":feature:plugin:api",
        ":feature:provider:api",
        ":feature:resource:api",
    )

    @Test
    fun phase3_modules_must_be_registered_in_settings() {
        val text = settingsFile.readText(UTF_8)
        val missing = phase3Modules.filterNot { module ->
            text.contains("""include("$module")""")
        }

        assertTrue(
            "Phase 3 Gradle modules must be registered in settings.gradle.kts: $missing",
            missing.isEmpty(),
        )
    }

    @Test
    fun app_must_depend_on_phase3_modules_during_transition() {
        val text = appBuildFile.readText(UTF_8)
        val missing = phase3Modules.filterNot { module ->
            text.contains("""implementation(project("$module"))""") ||
                text.contains("""api(project("$module"))""")
        }

        assertTrue(
            "App must depend on Phase 3 modules while implementation still lives in :app: $missing",
            missing.isEmpty(),
        )
    }

    @Test
    fun core_modules_must_not_depend_on_feature_or_app_modules() {
        val violations = buildFilesUnder(projectRoot.resolve("core"))
            .flatMap { file ->
                val text = file.readText(UTF_8)
                forbiddenCoreModuleDependencyPatterns
                    .filter { pattern -> pattern.containsMatchIn(text) }
                    .map { pattern -> "${relativePath(file)} -> ${pattern.pattern}" }
            }

        assertTrue(
            "Core modules must not depend on app or feature modules: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun feature_api_modules_must_not_depend_on_app_or_impl_modules() {
        val violations = buildFilesUnder(projectRoot.resolve("feature"))
            .filter { file -> relativePath(file).contains("/api/build.gradle.kts") }
            .flatMap { file ->
                val text = file.readText(UTF_8)
                forbiddenFeatureApiDependencyPatterns
                    .filter { pattern -> pattern.containsMatchIn(text) }
                    .map { pattern -> "${relativePath(file)} -> ${pattern.pattern}" }
            }

        assertTrue(
            "Feature API modules must not depend on app or implementation modules: $violations",
            violations.isEmpty(),
        )
    }

    private fun buildFilesUnder(root: Path): List<Path> {
        if (!root.exists()) {
            return emptyList()
        }

        return Files.walk(root).use { stream ->
            stream
                .filter { path -> path.isRegularFile() && path.fileName.toString() == "build.gradle.kts" }
                .toList()
        }
    }

    private fun relativePath(file: Path): String {
        return projectRoot.relativize(file).toString().replace('\\', '/')
    }

    private fun detectProjectRoot(): Path {
        val cwd = Path.of("").toAbsolutePath()
        return when {
            cwd.resolve("settings.gradle.kts").exists() -> cwd
            cwd.parent?.resolve("settings.gradle.kts")?.exists() == true -> cwd.parent
            else -> error("Unable to resolve project root from $cwd")
        }
    }

    private companion object {
        val forbiddenCoreModuleDependencyPatterns = listOf(
            Regex("""project\(":app"\)"""),
            Regex("""project\(":feature:"""),
        )

        val forbiddenFeatureApiDependencyPatterns = listOf(
            Regex("""project\(":app"\)"""),
            Regex("""project\(":feature:[^"]+:impl"\)"""),
            Regex("""project\(":feature:[^"]+:data"\)"""),
            Regex("""project\(":feature:[^"]+:runtime"\)"""),
            Regex("""project\(":feature:[^"]+:presentation"\)"""),
        )
    }
}
