package com.astrbot.android.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class NapCatRuntimeScriptContractTest {
    private val projectRoot: Path = generateSequence(Path.of("").toAbsolutePath()) { current ->
        current.parent
    }.firstOrNull { candidate ->
        Files.exists(candidate.resolve("app/src/main/assets/runtime/scripts/root_launcher.sh"))
    } ?: error("Could not locate project root from ${Path.of("").toAbsolutePath()}")

    private val rootLauncher: Path = projectRoot.resolve("app/src/main/assets/runtime/scripts/root_launcher.sh")
    private val runtimeSupportSource: Path = projectRoot.resolve(
        "app/src/main/java/com/astrbot/android/core/runtime/container/ContainerBridgeRuntimeSupport.kt",
    )

    @Test
    fun root_launcher_must_fallback_to_upstream_installer_when_bundled_assets_are_missing() {
        val source = rootLauncher.readText()

        assertTrue(
            "root_launcher.sh must keep an upstream installer fallback for network-install mode",
            source.contains("NAPCAT_INSTALLER_URL=") && source.contains("download_napcat_installer()"),
        )
        assertTrue(
            "root_launcher.sh must download the upstream installer when bundled runtime assets are unavailable",
            Regex(
                """if prepare_bundled_napcat_installer; then[\s\S]*else[\s\S]*download_napcat_installer "\${'$'}NAPCAT_INSTALLER_CACHE"[\s\S]*fi""",
            ).containsMatchIn(source),
        )
    }

    @Test
    fun root_launcher_must_tolerate_missing_bundled_launcher_shim_for_existing_installs() {
        val source = rootLauncher.readText()

        assertTrue(
            "root_launcher.sh must not hard-fail existing installs when bundled launcher assets are unavailable",
            Regex(
                """repair_existing_napcat_install\(\) \{[\s\S]*if prepare_bundled_launcher_shim; then[\s\S]*else[\s\S]*bundled launcher shim unavailable[\s\S]*fi""",
            ).containsMatchIn(source),
        )
    }

    @Test
    fun runtime_progress_labels_must_keep_network_install_messages() {
        val source = runtimeSupportSource.readText()

        assertTrue(
            "ContainerBridgeRuntimeSupport must keep network-install progress labels for NapCat startup",
            source.contains("\"download-installer\" -> \"Downloading upstream installer\"") &&
                source.contains("\"installer-downloaded\" -> \"Installer script downloaded\""),
        )
    }
}
