package com.astrbot.android.feature.plugin.data

import java.nio.file.Path
import kotlin.io.path.readText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeaturePluginRepositoryHiltOnlySeamTest {

    private val projectRoot: Path = detectProjectRoot()
    private val sourceFile: Path = projectRoot.resolve(
        "app/src/main/java/com/astrbot/android/feature/plugin/data/FeaturePluginRepository.kt",
    )

    @Test
    fun feature_plugin_repository_state_owner_must_not_bootstrap_static_repository_state() {
        val source = sourceFile.readText()

        assertFalse(
            "FeaturePluginRepositoryStateOwner must not call repository.initialize(appContext).",
            source.contains("repository.initialize(appContext)"),
        )
        assertFalse(
            "FeaturePluginRepositoryStateOwner must not delegate initialize(context) back into the static repository.",
            source.contains("repository.initialize(context)"),
        )
    }

    @Test
    fun phase1_plugin_config_state_and_cleanup_boundaries_must_exist_outside_feature_plugin_repository() {
        val requiredFiles = listOf(
            "app/src/main/java/com/astrbot/android/feature/plugin/data/config/PluginConfigStorage.kt",
            "app/src/main/java/com/astrbot/android/feature/plugin/data/config/PluginHostConfigResolver.kt",
            "app/src/main/java/com/astrbot/android/feature/plugin/data/state/PluginStateStore.kt",
            "app/src/main/java/com/astrbot/android/feature/plugin/domain/cleanup/PluginDataCleanupService.kt",
        )
        val missing = requiredFiles.filterNot { relativePath ->
            projectRoot.resolve(relativePath).toFile().isFile
        }

        assertTrue(
            "Phase 1 must create dedicated plugin config/state/cleanup boundaries: $missing",
            missing.isEmpty(),
        )
    }

    @Test
    fun feature_plugin_repository_uninstall_path_must_not_inline_file_or_config_cleanup_logic() {
        val source = sourceFile.readText()

        assertFalse(
            "FeaturePluginRepository should no longer keep PluginFileDataRemover inline after Phase 1.",
            source.contains("class PluginFileDataRemover"),
        )
        assertFalse(
            "FeaturePluginRepository uninstall should no longer delete config snapshots inline.",
            source.contains("requireConfigDao().delete(pluginId)"),
        )
        assertFalse(
            "FeaturePluginRepository should no longer own pluginDataRemover state.",
            source.contains("pluginDataRemover"),
        )
    }

    @Test
    fun plugin_view_model_bindings_must_not_use_static_feature_plugin_repository_companion_calls() {
        val source = projectRoot.resolve(
            "app/src/main/java/com/astrbot/android/feature/plugin/presentation/PluginViewModel.kt",
        ).readText()

        val forbiddenTokens = listOf(
            "PluginRepository.",
            "FeaturePluginRepository.",
        )
        val violations = forbiddenTokens.filter(source::contains)

        assertTrue(
            "Plugin presentation bindings should use injected FeaturePluginRepository seams, not static companion entry points: $violations",
            violations.isEmpty(),
        )
    }

    private fun detectProjectRoot(): Path {
        var current = Path.of("").toAbsolutePath()
        while (current.parent != null) {
            if (current.resolve("settings.gradle.kts").toFile().isFile ||
                current.resolve("settings.gradle").toFile().isFile
            ) {
                return current
            }
            current = current.parent
        }
        error("Unable to locate project root from ${Path.of("").toAbsolutePath()}")
    }
}
