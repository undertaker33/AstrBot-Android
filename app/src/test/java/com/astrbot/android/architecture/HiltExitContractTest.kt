package com.astrbot.android.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class HiltExitContractTest {

    private val mainRoot: Path = listOf(
        Path.of("src/main/java/com/astrbot/android"),
        Path.of("app/src/main/java/com/astrbot/android"),
    ).first { it.exists() }

    @Test
    fun main_activity_must_be_android_entry_point() {
        val file = mainRoot.resolve("MainActivity.kt")
        assertTrue("MainActivity.kt must exist", file.exists())
        val text = file.readText()
        assertTrue(
            "MainActivity must be a Hilt Android entry point after phase 5",
            text.contains("@AndroidEntryPoint"),
        )
        assertTrue(
            "MainActivity must not read dependencies through the legacy app container",
            !text.contains("appContainer") && !text.contains("MainActivityDependencies"),
        )
    }

    @Test
    fun production_view_models_must_be_hilt_view_models() {
        val missing = productionViewModelFiles()
            .filterNot { file -> file.readText().contains("@HiltViewModel") }
            .map { file -> mainRoot.relativize(file).toString().replace('\\', '/') }

        assertTrue("Production ViewModels missing @HiltViewModel: $missing", missing.isEmpty())
    }

    @Test
    fun production_sources_must_not_use_legacy_view_model_helper_or_container_bridge() {
        val forbiddenTokens = listOf(
            "astrBotViewModel(",
            "LegacyContainerEntryPoint",
            "viewModelFactory",
            "DefaultBridgeViewModelDependencies",
            "DefaultBotViewModelDependencies",
            "DefaultProviderViewModelDependencies",
            "DefaultConfigViewModelDependencies",
            "DefaultConversationViewModelDependencies",
            "DefaultPersonaViewModelDependencies",
            "DefaultPluginViewModelDependencies",
            "DefaultQQLoginViewModelDependencies",
            "DefaultRuntimeAssetViewModelDependencies",
            "DefaultChatViewModelDependencies",
            "DefaultMainActivityDependencies",
            "MainActivityDependencies",
        )
        val violations = kotlinFilesUnder(mainRoot)
            .filterNot { file ->
                mainRoot.relativize(file).toString().replace('\\', '/') in allowedMentionFiles
            }
            .flatMap { file ->
                val relative = mainRoot.relativize(file).toString().replace('\\', '/')
                val text = file.readText()
                forbiddenTokens.mapNotNull { token ->
                    if (text.contains(token)) "$relative contains $token" else null
                }
            }

        assertTrue("Legacy DI tokens still present in production sources: $violations", violations.isEmpty())
    }

    @Test
    fun application_must_not_create_legacy_container() {
        val file = mainRoot.resolve("AstrBotApplication.kt")
        assertTrue("AstrBotApplication.kt must exist", file.exists())
        val text = file.readText()
        assertTrue(
            "AstrBotApplication must remain @HiltAndroidApp",
            text.contains("@HiltAndroidApp"),
        )
        assertTrue(
            "AstrBotApplication must implement WorkManager Configuration.Provider for HiltWorkerFactory",
            text.contains("Configuration.Provider") && text.contains("HiltWorkerFactory"),
        )
        assertTrue(
            "AstrBotApplication must not manually create ElymBotAppContainer after phase 5",
            !text.contains("ElymBotAppContainer("),
        )
    }

    private fun productionViewModelFiles(): List<Path> {
        return listOf(
            "ui/viewmodel/BridgeViewModel.kt",
            "feature/bot/presentation/BotViewModel.kt",
            "feature/provider/presentation/ProviderViewModel.kt",
            "feature/config/presentation/ConfigViewModel.kt",
            "feature/chat/presentation/ConversationViewModel.kt",
            "feature/persona/presentation/PersonaViewModel.kt",
            "feature/plugin/presentation/PluginViewModel.kt",
            "feature/qq/presentation/QQLoginViewModel.kt",
            "feature/chat/presentation/ChatViewModel.kt",
            "ui/viewmodel/RuntimeAssetViewModel.kt",
            "feature/cron/presentation/CronJobsViewModel.kt",
        ).map { relativePath -> mainRoot.resolve(relativePath) }
            .onEach { file -> assertTrue("${mainRoot.relativize(file)} must exist", file.exists()) }
    }

    private fun kotlinFilesUnder(root: Path): List<Path> {
        return Files.walk(root).use { stream ->
            stream
                .filter { it.isRegularFile() && it.fileName.toString().endsWith(".kt") }
                .toList()
        }
    }

    private companion object {
        val allowedMentionFiles = setOf(
            "di/hilt/ViewModelDependencyModule.kt",
        )
    }
}
