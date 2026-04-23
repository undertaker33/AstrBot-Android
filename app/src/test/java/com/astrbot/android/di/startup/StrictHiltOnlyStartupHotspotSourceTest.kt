package com.astrbot.android.di.startup

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class StrictHiltOnlyStartupHotspotSourceTest {

    private val projectRoot: Path = detectProjectRoot()
    private val mainRoot: Path = projectRoot.resolve("app/src/main/java/com/astrbot/android")

    @Test
    fun bootstrap_prerequisites_chain_must_not_retain_manual_runtime_bootstrap_calls() {
        val source = mainRoot.resolve("di/startup/BootstrapPrerequisitesStartupChain.kt").readText()
        val forbiddenTokens = listOf(
            "RuntimeSecretRepository.initialize(application)",
            "AppDownloadManager.initialize(application)",
            "pluginRepositoryStateOwner.initialize(application)",
            "bridgeStateOwner.initialize(application)",
            "NapCatLoginRepository.initialize(application)",
        )

        val violations = forbiddenTokens.filter(source::contains)
        assertTrue(
            "BootstrapPrerequisitesStartupChain must not keep manual runtime bootstrap calls: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun hilt_probe_and_container_installer_must_not_force_static_app_context_initialization() {
        val probeSource = mainRoot.resolve("core/runtime/llm/HiltLlmProviderProbePort.kt").readText()
        val installerSource = mainRoot.resolve("core/runtime/container/ContainerRuntimeInstaller.kt").readText()

        assertTrue(
            "HiltLlmProviderProbePort must not initialize ChatCompletionService from constructor context bootstrap",
            !probeSource.contains("ChatCompletionService.initialize("),
        )
        assertTrue(
            "ContainerRuntimeInstaller must not initialize RuntimeSecretRepository from app context bootstrap",
            !installerSource.contains("RuntimeSecretRepository.initialize("),
        )
    }

    @Test
    fun static_runtime_storage_hotspots_must_not_use_astrbot_database_get() {
        val downloadManagerSource = mainRoot.resolve("download/DownloadManager.kt").readText()
        val localStoreSource = mainRoot.resolve("feature/qq/data/NapCatLoginLocalStore.kt").readText()

        assertTrue(
            "DownloadManager must not reach Room via AstrBotDatabase.get(...)",
            !downloadManagerSource.contains("AstrBotDatabase.get("),
        )
        assertTrue(
            "NapCatLoginLocalStore must not reach Room via AstrBotDatabase.get(...)",
            !localStoreSource.contains("AstrBotDatabase.get("),
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
