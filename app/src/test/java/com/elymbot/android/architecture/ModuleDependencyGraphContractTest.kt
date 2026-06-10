package com.elymbot.android.architecture

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleDependencyGraphContractTest {

    private val projectRoot: Path = detectProjectRoot()
    private val settingsFile: Path = projectRoot.resolve("settings.gradle.kts")
    private val rootBuildFile: Path = projectRoot.resolve("build.gradle.kts")
    private val appBuildFile: Path = projectRoot.resolve("app/build.gradle.kts")
    private val appIntegrationBuildFile: Path = projectRoot.resolve("app-integration/build.gradle.kts")

    private val phase3Modules = listOf(
        ":core:common",
        ":core:network",
        ":core:runtime-audio",
        ":core:runtime-cache",
        ":core:runtime-container",
        ":core:runtime-context",
        ":core:runtime-llm",
        ":core:runtime-search",
        ":core:runtime-secret",
        ":core:runtime-session",
        ":core:runtime-tool",
        ":download:api",
        ":download:impl",
        ":feature:bot:api",
        ":feature:bot:data",
        ":feature:config:api",
        ":feature:config:data",
        ":feature:cron:api",
        ":feature:persona:api",
        ":feature:persona:data",
        ":feature:plugin:api",
        ":feature:provider:api",
        ":feature:provider:data",
        ":feature:resource:api",
        ":feature:resource:data",
    )

    private val phase14Modules = listOf(
        ":feature:conversation:api",
        ":feature:conversation:data",
        ":feature:chat:runtime",
        ":feature:chat:presentation",
    )

    private val phase18To20Modules = listOf(
        ":app-integration",
        ":core:backup",
        ":core:logging",
        ":core:runtime-container",
        ":core:runtime-search",
        ":feature:qq:api",
        ":feature:qq:impl",
        ":feature:cron:runtime",
        ":feature:settings:api",
        ":feature:settings:presentation",
        ":feature:voiceasset:api",
    )
    private val phase18To20SourceRootModules = phase18To20Modules.filterNot { module ->
        module == ":feature:qq:impl"
    }

    private val phase22PluginModules = listOf(
        ":feature:plugin:data",
        ":feature:plugin:presentation",
        ":feature:plugin:runtime",
    )

    private val phase25Modules = listOf(
        ":feature:provider:runtime",
        ":feature:voiceasset:data",
        ":feature:voiceasset:presentation",
    )

    private val phase26ArchitectureTestModules = listOf(
        ":architecture-tests",
    )

    private val phase23AppShellAllowedTransitionalDependencies = emptyMap<String, TransitionalDependency>()

    private val phase23ForbiddenAppShellDependencies = listOf(
        ":download:impl",
        ":feature:plugin:data",
        ":feature:cron:data",
        ":feature:cron:runtime",
        ":feature:resource:data",
        ":core:runtime-secret",
        ":core:runtime-tool",
        ":feature:qq:data",
        ":feature:qq:impl",
        ":feature:qq:runtime",
    )

    private val phase25ForbiddenAppShellDependencies = listOf(
        ":feature:bot:data",
        ":feature:chat:runtime",
        ":feature:config:data",
        ":feature:conversation:data",
        ":feature:persona:data",
        ":feature:provider:data",
        ":feature:provider:runtime",
        ":feature:voiceasset:data",
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
    fun phase14_conversation_and_chat_modules_must_be_registered_in_settings() {
        val text = settingsFile.readText(UTF_8)
        val missing = phase14Modules.filterNot { module ->
            text.contains("""include("$module")""")
        }

        assertTrue(
            "Phase 14 conversation/chat Gradle modules must be registered in settings.gradle.kts: $missing",
            missing.isEmpty(),
        )
    }

    @Test
    fun phase18_to_20_terminal_modules_must_be_registered_in_settings() {
        val text = settingsFile.readText(UTF_8)
        val missing = phase18To20Modules.filterNot { module ->
            text.contains("""include("$module")""")
        }

        assertTrue(
            "Phase 18-20 terminal modules must be registered in settings.gradle.kts: $missing",
            missing.isEmpty(),
        )
    }

    @Test
    fun phase22_plugin_modules_must_be_registered_in_settings() {
        val text = settingsFile.readText(UTF_8)
        val missing = phase22PluginModules.filterNot { module ->
            text.contains("""include("$module")""")
        }

        assertTrue(
            "Phase 22 plugin data/runtime/presentation modules must be registered in settings.gradle.kts: $missing",
            missing.isEmpty(),
        )
    }

    @Test
    fun phase25_voiceasset_and_provider_runtime_modules_must_be_registered_in_settings() {
        val text = settingsFile.readText(UTF_8)
        val missing = phase25Modules.filterNot { module ->
            text.contains("""include("$module")""")
        }

        assertTrue(
            "Phase 25 voiceasset/provider runtime modules must be registered in settings.gradle.kts: $missing",
            missing.isEmpty(),
        )
    }

    @Test
    fun phase26_architecture_tests_module_must_be_registered_in_settings() {
        val text = settingsFile.readText(UTF_8)
        val missing = phase26ArchitectureTestModules.filterNot { module ->
            text.contains("""include("$module")""")
        }

        assertTrue(
            "Phase 26 architecture test modules must be registered in settings.gradle.kts: $missing",
            missing.isEmpty(),
        )
    }

    @Test
    fun architecture_check_must_run_independent_architecture_tests_module() {
        val text = rootBuildFile.readText(UTF_8)

        assertTrue(
            "architectureCheck must run :architecture-tests:test after Phase 26-A.",
            text.contains("""dependsOn(":architecture-tests:test")""") ||
                text.contains("""dependsOn(project(":architecture-tests").tasks.named("test"))"""),
        )
    }

    @Test
    fun phase22_plugin_modules_must_be_in_architecture_source_roots() {
        val text = rootBuildFile.readText(UTF_8)
        val missing = phase22PluginModules
            .map(::sourceRootForModule)
            .filterNot { sourceRoot -> text.contains("\"$sourceRoot\"") }

        assertTrue(
            "Phase 22 plugin module source roots must be scanned by architecture contracts: $missing",
            missing.isEmpty(),
        )
    }

    @Test
    fun phase25_voiceasset_and_provider_runtime_modules_must_be_in_architecture_source_roots() {
        val text = rootBuildFile.readText(UTF_8)
        val missing = phase25Modules
            .map(::sourceRootForModule)
            .filterNot { sourceRoot -> text.contains("\"$sourceRoot\"") }

        assertTrue(
            "Phase 25 voiceasset/provider runtime module source roots must be scanned by architecture contracts: $missing",
            missing.isEmpty(),
        )
    }

    @Test
    fun phase18_to_20_terminal_modules_must_be_in_architecture_source_roots() {
        val text = rootBuildFile.readText(UTF_8)
        val missing = phase18To20SourceRootModules
            .map(::sourceRootForModule)
            .filterNot { sourceRoot -> text.contains("\"$sourceRoot\"") }

        assertTrue(
            "Phase 18-20 terminal module source roots must be scanned by architecture contracts: $missing",
            missing.isEmpty(),
        )
    }

    @Test
    fun app_shell_must_depend_on_app_integration_module() {
        val text = appBuildFile.readText(UTF_8)

        assertTrue(
            "App shell must depend on :app-integration for production wiring.",
            text.contains("""implementation(project(":app-integration"))""") ||
                text.contains("""api(project(":app-integration"))"""),
        )
    }

    @Test
    fun app_integration_must_not_depend_on_feature_presentation_modules() {
        val text = appIntegrationBuildFile.readText(UTF_8)
        val violations = Regex("""project\(":feature:[^"]+:presentation"\)""")
            .findAll(text)
            .map { match -> match.value }
            .toList()

        assertTrue(
            "app-integration is wiring only and must not depend on feature presentation modules: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun phase27_app_shell_project_dependencies_must_match_allowed_set() {
        val dependencies = projectDependencies(appBuildFile).toSet()

        assertEquals(
            "Phase 27 app shell project dependencies must stay limited to app-integration, core UI, and feature presentation modules.",
            PHASE27_APP_ALLOWED_PROJECT_DEPENDENCIES,
            dependencies,
        )
    }

    @Test
    fun phase23_app_shell_must_not_keep_split_feature_data_runtime_or_old_impl_dependencies() {
        val dependencies = projectDependencies(appBuildFile)
        val oldImplDependencies = dependencies
            .filter { dependency -> Regex(""":feature:[^:]+:impl$""").matches(dependency) }
            .filterNot(phase23AppShellAllowedTransitionalDependencies::containsKey)
        val splitDataRuntimeDependencies = phase23ForbiddenAppShellDependencies.filter(dependencies::contains)

        assertTrue(
            "Phase 23 app shell must not directly depend on old feature impl modules except precise QQ transition: $oldImplDependencies",
            oldImplDependencies.isEmpty(),
        )
        assertTrue(
            "Phase 23 app shell must move split feature data/runtime and core runtime implementation dependencies behind app-integration: $splitDataRuntimeDependencies",
            splitDataRuntimeDependencies.isEmpty(),
        )
    }

    @Test
    fun phase25_app_shell_must_not_keep_feature_data_or_runtime_dependencies() {
        val dependencies = projectDependencies(appBuildFile)
        val forbiddenDependencies = phase25ForbiddenAppShellDependencies.filter(dependencies::contains)

        assertTrue(
            "Phase 25 app shell must not directly depend on feature data/runtime modules; move wiring behind :app-integration or owner modules: $forbiddenDependencies",
            forbiddenDependencies.isEmpty(),
        )
    }

    @Test
    fun phase25_app_shell_hilt_and_navigation_must_not_import_feature_implementation_packages() {
        val sourceRoots = listOf(
            projectRoot.resolve("app/src/main/java/com/elymbot/android/di/hilt"),
            projectRoot.resolve("app/src/main/java/com/elymbot/android/di/startup"),
            projectRoot.resolve("app/src/main/java/com/elymbot/android/ui/navigation"),
        )
        val forbiddenReferencePattern = Regex(
            """(?:import\s+)?com\.elymbot\.android\.feature\.[A-Za-z0-9_]+\.(data|runtime|impl)(?:\.|\s)""",
        )
        val violations = sourceRoots
            .flatMap(::kotlinFilesUnder)
            .flatMap { file ->
                forbiddenReferencePattern.findAll(file.readText(UTF_8))
                    .map { match -> "${relativePath(file)} -> ${match.value.trim()}" }
            }

        assertTrue(
            "Phase 25 app shell Hilt/startup/navigation glue must depend on feature api/presentation contracts, not data/runtime/impl packages: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun phase23_remaining_app_shell_transitional_dependencies_must_be_precise() {
        val malformed = phase23AppShellAllowedTransitionalDependencies.filter { (_, entry) ->
            entry.owner.isBlank() ||
                entry.target.isBlank() ||
                entry.reason.isBlank() ||
                entry.expires.isBlank() ||
                entry.issue.isBlank() ||
                entry.owner.contains("*") ||
                entry.target.contains("*")
        }

        assertTrue(
            "Phase 23 app shell transition entries must be precise and documented: $malformed",
            malformed.isEmpty(),
        )

        val dependencies = projectDependencies(appBuildFile)
        val stale = phase23AppShellAllowedTransitionalDependencies.keys.filterNot(dependencies::contains)
        assertEquals(
            "Remove stale Phase 23 app shell transition entries once the direct dependency is gone.",
            emptyList<String>(),
            stale,
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
    fun foundational_core_modules_must_not_depend_on_runtime_or_business_modules() {
        val foundationalModules = listOf(
            "core/common/build.gradle.kts",
            "core/logging/build.gradle.kts",
            "core/network/build.gradle.kts",
            "core/runtime-audio/build.gradle.kts",
            "core/runtime-cache/build.gradle.kts",
            "core/runtime-container/build.gradle.kts",
            "core/runtime-context/build.gradle.kts",
            "core/runtime-llm/build.gradle.kts",
            "core/runtime-search/build.gradle.kts",
            "core/runtime-secret/build.gradle.kts",
            "core/runtime-session/build.gradle.kts",
            "core/runtime-tool/build.gradle.kts",
        )
        val forbiddenDependencyPatterns = mapOf(
            ":core:runtime" to Regex("""project\(":core:runtime"\)"""),
            ":app" to Regex("""project\(":app"\)"""),
            ":app-integration" to Regex("""project\(":app-integration"\)"""),
            ":feature:*" to Regex("""project\(":feature:"""),
        )
        val violations = foundationalModules
            .map { path -> projectRoot.resolve(path) }
            .filter { file -> file.exists() }
            .flatMap { file ->
                val text = file.readText(UTF_8)
                forbiddenDependencyPatterns
                    .filter { (_, pattern) -> pattern.containsMatchIn(text) }
                    .map { (dependency, _) -> "${relativePath(file)} depends on $dependency" }
            }

        assertTrue(
            "Foundational core modules must stay independent from runtime/app/feature modules: $violations",
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

    @Test
    fun api_configurations_must_not_expose_feature_data_runtime_or_impl_modules() {
        val buildFiles = buildFilesUnder(projectRoot.resolve("feature"))
            .filter { file -> relativePath(file).contains("/api/build.gradle.kts") } +
            listOf(appIntegrationBuildFile).filter { it.exists() }
        val forbiddenApiExposurePattern =
            Regex("""api\(project\(":feature:[^"]+:(?:data|runtime|impl|presentation)"\)\)""")
        val violations = buildFiles.flatMap { file ->
            forbiddenApiExposurePattern.findAll(file.readText(UTF_8))
                .map { match -> "${relativePath(file)} -> ${match.value}" }
        }

        assertTrue(
            "API configurations must not expose feature data/runtime/impl/presentation modules; feature API fan-out remains a later 26-B/26-C cleanup: $violations",
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

    private fun kotlinFilesUnder(root: Path): List<Path> {
        if (!root.exists()) {
            return emptyList()
        }

        return Files.walk(root).use { stream ->
            stream
                .filter { path -> path.isRegularFile() && path.fileName.toString().endsWith(".kt") }
                .toList()
        }
    }

    private fun relativePath(file: Path): String {
        return projectRoot.relativize(file).toString().replace('\\', '/')
    }

    private fun sourceRootForModule(module: String): String {
        return module.removePrefix(":").replace(':', '/') + "/src/main/java"
    }

    private fun projectDependencies(buildFile: Path): List<String> {
        val text = buildFile.readText(UTF_8)
        return Regex("""(?:api|implementation|compileOnly|runtimeOnly)\(project\("([^"]+)"\)\)""")
            .findAll(text)
            .map { match -> match.groupValues[1] }
            .toList()
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

        val PHASE27_APP_ALLOWED_PROJECT_DEPENDENCIES = setOf(
            ":app-integration",
            ":core:ui",
            ":feature:bot:presentation",
            ":feature:chat:presentation",
            ":feature:config:presentation",
            ":feature:cron:presentation",
            ":feature:geofence:presentation",
            ":feature:persona:presentation",
            ":feature:plugin:presentation",
            ":feature:provider:presentation",
            ":feature:qq:presentation",
            ":feature:resource:presentation",
            ":feature:settings:presentation",
            ":feature:voiceasset:presentation",
        )

        val forbiddenFeatureApiDependencyPatterns = listOf(
            Regex("""project\(":app"\)"""),
            Regex("""project\(":feature:[^"]+:impl"\)"""),
            Regex("""project\(":feature:[^"]+:data"\)"""),
            Regex("""project\(":feature:[^"]+:runtime"\)"""),
            Regex("""project\(":feature:[^"]+:presentation"\)"""),
        )
    }

    private data class TransitionalDependency(
        val owner: String,
        val target: String,
        val reason: String,
        val expires: String,
        val issue: String,
    )
}
