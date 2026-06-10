package com.elymbot.android.runtime

import com.elymbot.android.di.runtime.container.buildRuntimeBridgeStartFailureLog
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeBridgeCompatibilityContractTest {
    private val projectRoot: Path = detectProjectRoot()
    private val bridgeServiceSource: Path = projectRoot.resolve(
        "app/src/main/java/com/elymbot/android/core/runtime/container/ContainerBridgeService.kt",
    )

    @Test
    fun container_bridge_service_uses_v110_startup_model_without_blocking_preflight() {
        val source = bridgeServiceSource.readText(UTF_8)
        val ensureInstalledIndex = source.indexOf("containerRuntimeInstaller.ensureInstalled()")
        val startNapCatIndex = source.indexOf("containerRuntimeController.startNapCat()")

        assertFalse(
            "ContainerBridgeService must not block the v1.1.0 startup path with RuntimeCompatibilityProbe",
            source.contains("runtimeCompatibilityProbe") || source.contains("runPreflight()"),
        )
        assertTrue(
            "ContainerBridgeService must install runtime assets before startNapCat",
            ensureInstalledIndex >= 0 &&
                startNapCatIndex > ensureInstalledIndex,
        )
    }

    @Test
    fun container_bridge_service_uses_original_initial_health_retry() {
        val source = bridgeServiceSource.readText(UTF_8)

        assertTrue(
            "The v1.1.0 startup model must use BridgeHealthChecker.checkWithRetry after startNapCat",
            source.contains("bridgeHealthChecker.checkWithRetry(config.healthUrl)") &&
                !source.contains("checkInitialHealthWithStartupDiagnostics(config.healthUrl)"),
        )
    }

    @Test
    fun runtime_bridge_controller_classifies_start_failures() {
        val securityMessage = buildRuntimeBridgeStartFailureLog(
            logLabel = "Bridge start",
            foreground = true,
            error = SecurityException("missing foreground service permission"),
        )
        assertTrue(securityMessage.contains("permission/security policy"))

        val backgroundMessage = buildRuntimeBridgeStartFailureLog(
            logLabel = "Bridge start",
            foreground = true,
            error = ForegroundServiceStartNotAllowedException("background launch"),
        )
        assertTrue(backgroundMessage.contains("Android 12+ foreground service background start restriction"))

        val genericMessage = buildRuntimeBridgeStartFailureLog(
            logLabel = "Bridge health check",
            foreground = false,
            error = IllegalStateException("service unavailable"),
        )
        assertTrue(genericMessage.contains("service start failed"))
    }

    private class ForegroundServiceStartNotAllowedException(message: String) : RuntimeException(message)

    private fun detectProjectRoot(): Path {
        val cwd = Path.of("").toAbsolutePath()
        return when {
            cwd.resolve("settings.gradle.kts").exists() -> cwd
            cwd.parent?.resolve("settings.gradle.kts")?.exists() == true -> cwd.parent
            else -> error("Unable to resolve project root from $cwd")
        }
    }
}
