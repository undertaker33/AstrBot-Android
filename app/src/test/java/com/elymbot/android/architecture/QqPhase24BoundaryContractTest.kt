package com.elymbot.android.architecture

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QqPhase24BoundaryContractTest {
    private val projectRoot: Path = detectProjectRoot()

    @Test
    fun app_shell_must_not_import_qq_data_or_runtime_owners() {
        val appMain = projectRoot.resolve("app/src/main/java/com/elymbot/android")
        val violations = kotlinFilesUnder(appMain)
            .flatMap { file ->
                val text = file.readText(UTF_8)
                forbiddenAppTokens.mapNotNull { token ->
                    if (text.contains(token)) "${relativePath(file)} contains $token" else null
                }
            }

        assertTrue(
            "App shell must not wire QQ data/runtime implementation owners directly: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun app_shell_must_not_import_plugin_data_or_runtime_owners() {
        val appMain = projectRoot.resolve("app/src/main/java/com/elymbot/android")
        val violations = kotlinFilesUnder(appMain)
            .flatMap { file ->
                val relativePath = relativePath(file)
                val text = file.readText(UTF_8)
                forbiddenAppPluginTokens.mapNotNull { token ->
                    if (text.contains(token) && relativePath !in allowedAppPluginImplementationImportFiles) {
                        "$relativePath contains $token"
                    } else {
                        null
                    }
                }
            }

        assertTrue(
            "App shell must use plugin API/presentation ports, not plugin data/runtime implementation owners: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun app_integration_must_not_expose_plugin_or_qq_implementation_modules_as_api() {
        val buildText = projectRoot.resolve("app-integration/build.gradle.kts").readText(UTF_8)
        val forbiddenApiProjects = listOf(
            ":feature:plugin:data",
            ":feature:plugin:runtime",
            ":feature:qq:data",
            ":feature:qq:impl",
            ":feature:qq:runtime",
        ).filter { module ->
            buildText.contains("""api(project("$module"))""")
        }

        assertTrue(
            "app-integration must keep implementation/runtime owner modules non-transitive: $forbiddenApiProjects",
            forbiddenApiProjects.isEmpty(),
        )
    }

    @Test
    fun app_integration_qq_container_bridge_wiring_must_not_use_feature_runtime_package() {
        val appIntegrationRoot = projectRoot.resolve("app-integration/src/main/java")
        val violations = kotlinFilesUnder(appIntegrationRoot)
            .filter { file -> file.fileName.toString().startsWith("QqContainerBridgeStatePort") }
            .filter { file ->
                file.readText(UTF_8).contains("package com.elymbot.android.feature.qq.runtime")
            }
            .map(::relativePath)

        assertTrue(
            "app-integration QQ bridge wiring must live under an app-integration package: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun app_integration_plugin_and_qq_implementation_imports_must_be_precise_wiring_only() {
        val appIntegrationRoot = projectRoot.resolve("app-integration/src/main/java")
        val allowedPluginImplementationImportFiles = setOf(
            "app-integration/src/main/java/com/elymbot/android/di/hilt/PluginHostCapabilityModule.kt",
            "app-integration/src/main/java/com/elymbot/android/di/hilt/PluginDataWiringFactory.kt",
            "app-integration/src/main/java/com/elymbot/android/di/hilt/PluginProvisioningModule.kt",
            "app-integration/src/main/java/com/elymbot/android/di/hilt/PluginRuntimeModule.kt",
            "app-integration/src/main/java/com/elymbot/android/di/hilt/QqPhase24PortModule.kt",
            "app-integration/src/main/java/com/elymbot/android/app/integration/plugin/PluginRuntimeObservationPortAdapter.kt",
            "app-integration/src/main/java/com/elymbot/android/di/hilt/runtime/CronRuntimeServicesModule.kt",
            "app-integration/src/main/java/com/elymbot/android/di/hilt/runtime/PluginV2ScheduledTaskDispatchPortAdapter.kt",
            "app-integration/src/main/java/com/elymbot/android/di/hilt/runtime/PluginRuntimeServicesModule.kt",
        )
        val allowedQqImplementationImportFiles = setOf(
            "app-integration/src/main/java/com/elymbot/android/app/integration/qq/QqContainerBridgeStatePortAdapter.kt",
        )
        val violations = kotlinFilesUnder(appIntegrationRoot)
            .flatMap { file ->
                val relativePath = relativePath(file)
                file.readText(UTF_8)
                    .lineSequence()
                    .filter { line ->
                        line.startsWith("import com.elymbot.android.feature.plugin.data.") ||
                            line.startsWith("import com.elymbot.android.feature.plugin.runtime.") ||
                            line.startsWith("import com.elymbot.android.feature.qq.data.") ||
                            line.startsWith("import com.elymbot.android.feature.qq.runtime.")
                    }
                    .filterNot { line ->
                        when {
                            line.startsWith("import com.elymbot.android.feature.plugin.") ->
                                relativePath in allowedPluginImplementationImportFiles
                            line.startsWith("import com.elymbot.android.feature.qq.data.") ->
                                relativePath in allowedQqImplementationImportFiles
                            else -> false
                        }
                    }
                    .map { line -> "$relativePath contains $line" }
                    .toList()
            }

        assertTrue(
            "app-integration may import implementation owners only from precise Hilt wiring files: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun global_singleton_allowlist_must_not_keep_stale_qq_static_service_reasons() {
        val allowlistText = projectRoot
            .resolve("app/src/test/resources/architecture/global-singleton-allowlist.txt")
            .readText(UTF_8)
        val staleReasonTokens = listOf(
            "static NapCatLoginService",
            "QQ login data remains a static service",
            "static QQ data residue",
            "QQ data static service",
        ).filter(allowlistText::contains)

        assertTrue(
            "Global singleton allowlist reasons must describe the real remaining blocker, not retired QQ static services: $staleReasonTokens",
            staleReasonTokens.isEmpty(),
        )
    }

    @Test
    fun qq_runtime_must_not_depend_on_plugin_data_or_runtime_implementation_modules() {
        val buildFile = projectRoot.resolve("feature/qq/runtime/build.gradle.kts")
        val text = buildFile.readText(UTF_8)
        val forbiddenProjects = listOf(
            """project(":feature:plugin:data")""",
            """project(":feature:plugin:runtime")""",
        ).filter(text::contains)

        assertTrue(
            "QQ runtime must consume plugin capability/API ports, not plugin data/runtime modules: $forbiddenProjects",
            forbiddenProjects.isEmpty(),
        )
    }

    @Test
    fun qq_runtime_must_not_receive_plugin_runtime_through_chat_runtime_api() {
        val qqRuntimeBuildText = projectRoot.resolve("feature/qq/runtime/build.gradle.kts").readText(UTF_8)
        val chatRuntimeBuildText = projectRoot.resolve("feature/chat/runtime/build.gradle.kts").readText(UTF_8)
        val qqDependsOnChatRuntime = qqRuntimeBuildText.contains("""project(":feature:chat:runtime")""")
        val chatExposesPluginRuntime = chatRuntimeBuildText.contains("""api(project(":feature:plugin:runtime"))""")

        assertFalse(
            "QQ runtime debug compile classpath must not receive :feature:plugin:runtime through " +
                ":feature:chat:runtime api exposure.",
            qqDependsOnChatRuntime && chatExposesPluginRuntime,
        )
    }

    @Test
    fun qq_runtime_production_sources_must_not_import_plugin_runtime_implementation_package() {
        val runtimeRoot = projectRoot.resolve("feature/qq/runtime/src/main/java/com/elymbot/android")
        val violations = kotlinFilesUnder(runtimeRoot)
            .flatMap { file ->
                file.readText(UTF_8)
                    .lineSequence()
                    .filter { line ->
                        line.startsWith("import com.elymbot.android.feature.plugin.runtime.") ||
                            line.contains("com.elymbot.android.feature.plugin.runtime.")
                    }
                    .map { line -> "${relativePath(file)} contains ${line.trim()}" }
                    .toList()
            }

        assertTrue(
            "QQ runtime production sources must depend on plugin API ports/models, not plugin runtime implementation: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun qq_runtime_must_not_import_plugin_storage_paths() {
        val runtimeRoot = projectRoot.resolve("feature/qq/runtime/src/main/java/com/elymbot/android")
        val violations = kotlinFilesUnder(runtimeRoot)
            .filter { file ->
                file.readText(UTF_8).contains("com.elymbot.android.feature.plugin.data.PluginStoragePaths")
            }
            .map(::relativePath)

        assertTrue(
            "QQ runtime must resolve plugin private roots through an injected plugin API port: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun qq_presentation_must_not_depend_on_logging_or_runtime_secret_implementations() {
        val buildText = projectRoot.resolve("feature/qq/presentation/build.gradle.kts").readText(UTF_8)
        val sourceRoot = projectRoot.resolve("feature/qq/presentation/src/main/java/com/elymbot/android")
        val sourceViolations = kotlinFilesUnder(sourceRoot)
            .flatMap { file ->
                val text = file.readText(UTF_8)
                forbiddenPresentationImports.mapNotNull { token ->
                    if (text.contains(token)) "${relativePath(file)} contains $token" else null
                }
            }
        val dependencyViolations = listOf(
            """:core:logging""",
            """:core:runtime-secret""",
        ).filter(buildText::contains)

        assertTrue(
            "QQ presentation must use QQ API/presentation ports instead of logging/secret implementations. " +
                "Source: $sourceViolations dependencies: $dependencyViolations",
            sourceViolations.isEmpty() && dependencyViolations.isEmpty(),
        )
    }

    @Test
    fun qq_static_repository_allowlist_entry_must_be_removed() {
        val allowlistText = projectRoot
            .resolve("app/src/test/resources/architecture/static-repository-usage-allowlist.txt")
            .readText(UTF_8)

        assertFalse(
            "QqLoginRepositoryAdapter must no longer delegate to static NapCatLoginRepository.",
            allowlistText.contains("NapCatLoginRepository"),
        )
    }

    @Test
    fun qq_data_napcat_login_owners_must_not_be_static_objects() {
        val qqDataRoot = projectRoot.resolve("feature/qq/data/src/main/java/com/elymbot/android/feature/qq/data")
        val forbiddenObjects = mapOf(
            "NapCatLoginLocalStore.kt" to "object NapCatLoginLocalStore",
            "NapCatLoginService.kt" to "object NapCatLoginService",
        )
        val violations = forbiddenObjects.mapNotNull { (fileName, token) ->
            val source = qqDataRoot.resolve(fileName).readText(UTF_8)
            if (source.contains(token)) "feature/qq/data/$fileName contains $token" else null
        }

        assertTrue(
            "NapCat QQ data owners must be constructor-injected instances, not production static objects: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun qq_data_napcat_login_owners_must_not_be_global_singleton_allowlisted() {
        val allowlistText = projectRoot
            .resolve("app/src/test/resources/architecture/global-singleton-allowlist.txt")
            .readText(UTF_8)
        val forbiddenEntries = listOf(
            "feature/qq/data/NapCatLoginLocalStore.kt",
            "feature/qq/data/NapCatLoginService.kt",
        ).filter(allowlistText::contains)

        assertTrue(
            "NapCat QQ data static owner allowlist entries must be removed: $forbiddenEntries",
            forbiddenEntries.isEmpty(),
        )
    }

    private fun kotlinFilesUnder(root: Path): List<Path> {
        if (!root.exists()) return emptyList()
        return Files.walk(root).use { stream ->
            stream
                .filter { path -> path.isRegularFile() && path.fileName.toString().endsWith(".kt") }
                .toList()
        }
    }

    private fun relativePath(file: Path): String =
        projectRoot.relativize(file).toString().replace('\\', '/')

    private fun detectProjectRoot(): Path {
        val cwd = Path.of("").toAbsolutePath()
        return when {
            cwd.resolve("settings.gradle.kts").exists() -> cwd
            cwd.parent?.resolve("settings.gradle.kts")?.exists() == true -> cwd.parent
            else -> error("Unable to resolve project root from $cwd")
        }
    }

    private companion object {
        val forbiddenAppTokens = listOf(
            "import com.elymbot.android.feature.qq.data.",
            "import com.elymbot.android.feature.qq.runtime.",
            "NapCatBridgeStateOwner",
            "QqLoginRepositoryAdapter",
        )

        val forbiddenAppPluginTokens = listOf(
            "import com.elymbot.android.feature.plugin.data.",
            "import com.elymbot.android.feature.plugin.runtime.",
        )

        val allowedAppPluginImplementationImportFiles = setOf(
            "app/src/main/java/com/elymbot/android/di/hilt/DefaultChatViewModelRuntimeBindings.kt",
        )

        val forbiddenPresentationImports = listOf(
            "import com.elymbot.android.core.common.logging.RuntimeLogger",
            "import com.elymbot.android.core.runtime.secret.RuntimeSecretStore",
        )
    }
}
