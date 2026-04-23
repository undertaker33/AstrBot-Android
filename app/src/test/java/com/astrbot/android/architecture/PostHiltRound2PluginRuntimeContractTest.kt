package com.astrbot.android.architecture

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class PostHiltRound2PluginRuntimeContractTest {

    private val projectRoot: Path = detectProjectRoot()
    private val mainRoot: Path = projectRoot.resolve("app/src/main/java/com/astrbot/android")

    @Test
    fun plugin_runtime_hotspots_must_not_reference_transition_provider_or_bridge_tokens() {
        val hotspotFiles = listOf(
            "di/hilt/PluginRuntimeModule.kt",
            "di/hilt/ViewModelDependencyModule.kt",
            "feature/plugin/runtime/PluginFailureGuard.kt",
            "feature/plugin/runtime/PluginRuntimeFacade.kt",
            "feature/plugin/runtime/PluginRuntimeLogBus.kt",
            "feature/plugin/runtime/PluginRuntimeScheduler.kt",
            "feature/plugin/runtime/PluginV2ActiveRuntimeStore.kt",
            "feature/plugin/runtime/PluginV2DispatchEngine.kt",
            "feature/plugin/runtime/PluginV2LifecycleManager.kt",
            "feature/plugin/runtime/PluginV2RuntimeLoader.kt",
        )

        val forbiddenTokens = listOf(
            "PluginRuntimeDependencyBridge.",
            "PluginRuntimeFailureStateStoreProvider.",
            "PluginRuntimeScopedFailureStateStoreProvider.",
            "PluginRuntimeLogBusProvider.",
            "PluginRuntimeScheduleStateStoreProvider.",
            "PluginV2ActiveRuntimeStoreProvider.",
            "PluginV2DispatchEngineProvider.",
            "PluginV2LifecycleManagerProvider.",
            "PluginV2RuntimeLoaderProvider.",
            "installFromHilt(",
        )

        assertHotspotFilesDoNotContain(forbiddenTokens, hotspotFiles)
    }

    @Test
    fun plugin_runtime_mainlines_must_not_backslide_to_legacy_adapter_or_bridge_names() {
        val hotspotFiles = listOf(
            "feature/plugin/runtime/AppChatPluginRuntime.kt",
            "feature/plugin/runtime/PluginRuntimeFacade.kt",
            "feature/qq/runtime/QqOneBotRuntimeGraph.kt",
            "feature/chat/runtime/AppChatPluginCommandService.kt",
        )

        val forbiddenTokens = listOf(
            "LegacyChatCompletionServiceAdapter",
            "LegacyLlmProviderProbeAdapter",
            "LegacyRuntimeOrchestratorAdapter",
            "PluginRuntimeDependencyBridge.",
        )

        assertHotspotFilesDoNotContain(forbiddenTokens, hotspotFiles)
    }

    private fun assertHotspotFilesDoNotContain(forbiddenTokens: List<String>, hotspotFiles: List<String>) {
        val violations = buildList {
            hotspotFiles.forEach { relativePath ->
                val file = mainRoot.resolve(relativePath)
                assertTrue("Expected plugin runtime hotspot to exist: ${file.toAbsolutePath()}", file.exists())
                val text = file.readText()
                forbiddenTokens.forEach { token ->
                    if (text.contains(token)) {
                        add("$relativePath contains $token")
                    }
                }
            }
        }

        assertTrue(
            "Plugin runtime hotspots must stay fail-closed after phase 5: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun production_plugin_runtime_store_providers_must_be_deleted_after_hilt_closure() {
        val forbiddenDeclarations = listOf(
            "object PluginRuntimeFailureStateStoreProvider",
            "object PluginRuntimeScopedFailureStateStoreProvider",
            "object PluginRuntimeScheduleStateStoreProvider",
        )

        val violations = buildList {
            kotlinFilesUnder(mainRoot).forEach { file ->
                val relativePath = mainRoot.relativize(file).toString().replace('\\', '/')
                val text = file.readText()
                forbiddenDeclarations.forEach { token ->
                    if (text.contains(token)) {
                        add("$relativePath contains $token")
                    }
                }
            }
        }

        assertTrue(
            "Plugin runtime store providers must not remain in production after Hilt closure: $violations",
            violations.isEmpty(),
        )
    }

    private fun kotlinFilesUnder(root: Path): Sequence<Path> =
        java.nio.file.Files.walk(root).use { paths ->
            paths.filter { path -> path.toString().endsWith(".kt") }.toList().asSequence()
        }

    private fun detectProjectRoot(): Path {
        val cwd = Path.of("").toAbsolutePath()
        return when {
            cwd.resolve("app/src/main/java/com/astrbot/android").exists() -> cwd
            cwd.parent?.resolve("app/src/main/java/com/astrbot/android")?.exists() == true -> cwd.parent
            else -> error("Unable to resolve project root from $cwd")
        }
    }
}
