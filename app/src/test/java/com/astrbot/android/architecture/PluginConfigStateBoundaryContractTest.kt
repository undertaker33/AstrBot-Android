package com.astrbot.android.architecture

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginConfigStateBoundaryContractTest {

    private val projectRoot: Path = detectProjectRoot()
    private val mainRoot: Path = projectRoot.resolve("app/src/main/java/com/astrbot/android")

    @Test
    fun plugin_view_model_must_not_assemble_controller_or_anonymous_ports_inline() {
        val source = mainRoot.resolve("feature/plugin/presentation/PluginViewModel.kt").readText()

        val forbiddenTokens = listOf(
            "PluginManagementUseCases(",
            "object : PluginRepositoryPort",
            "object : PluginRuntimePort",
            "object : PluginGovernancePort",
        )
        val violations = forbiddenTokens.filter(source::contains)

        assertTrue(
            "PluginViewModel must not keep inline controller/port assembly after Phase 3: $violations",
            violations.isEmpty(),
        )
        assertTrue(
            "PluginViewModel should inject a PluginPresentationController instead of hand-assembling one.",
            source.contains("private val pluginPresentationController: PluginPresentationController"),
        )
    }

    @Test
    fun plugin_presentation_bindings_must_be_split_by_market_config_workspace_and_governance() {
        val requiredFiles = listOf(
            "feature/plugin/presentation/bindings/PluginMarketBindings.kt",
            "feature/plugin/presentation/bindings/PluginConfigBindings.kt",
            "feature/plugin/presentation/bindings/PluginWorkspaceBindings.kt",
            "feature/plugin/presentation/bindings/PluginGovernanceBindings.kt",
        )
        val missing = requiredFiles.filterNot { relativePath ->
            mainRoot.resolve(relativePath).exists()
        }

        assertTrue(
            "Phase 3 must land smaller PluginViewModel binding groups: $missing",
            missing.isEmpty(),
        )
    }

    @Test
    fun plugin_view_model_bindings_must_not_rely_on_static_feature_plugin_repository_entry_points() {
        val source = mainRoot.resolve("feature/plugin/presentation/PluginViewModel.kt").readText()

        val forbiddenTokens = listOf(
            "PluginRepository.",
            "FeaturePluginRepository.",
        )
        val violations = forbiddenTokens.filter(source::contains)

        assertTrue(
            "DefaultPluginViewModelBindings should use injected seams instead of static FeaturePluginRepository entry points: $violations",
            violations.isEmpty(),
        )
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
