package com.astrbot.android.feature.plugin.data

import java.nio.file.Path
import kotlin.io.path.readText
import org.junit.Assert.assertFalse
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
