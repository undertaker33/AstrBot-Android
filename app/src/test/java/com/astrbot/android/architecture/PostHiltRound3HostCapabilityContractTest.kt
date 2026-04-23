package com.astrbot.android.architecture

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class PostHiltRound3HostCapabilityContractTest {

    private val projectRoot: Path = detectProjectRoot()
    private val mainRoot: Path = projectRoot.resolve("app/src/main/java/com/astrbot/android")

    @Test
    fun host_capability_hotspots_must_not_use_compat_helpers_or_direct_execution_api_calls() {
        val hotspotFiles = listOf(
            "feature/plugin/presentation/PluginViewModel.kt",
            "feature/chat/runtime/AppChatPluginCommandService.kt",
            "feature/qq/runtime/QqPluginDispatchService.kt",
        )
        val forbiddenTokens = listOf(
            "createCompatPluginHostCapabilityGatewayFactory(",
            "createCompatPluginHostCapabilityGateway(",
            "PluginExecutionHostApi.resolve(",
            "PluginExecutionHostApi.inject(",
            "PluginExecutionHostApi.registerHostBuiltinTools(",
            "PluginExecutionHostApi.executeHostBuiltinTool(",
        )

        val violations = buildList {
            hotspotFiles.forEach { relativePath ->
                val file = mainRoot.resolve(relativePath)
                assertTrue("Expected host-capability hotspot to exist: ${file.toAbsolutePath()}", file.exists())
                val text = file.readText()
                forbiddenTokens.forEach { token ->
                    if (text.contains(token)) {
                        add("$relativePath contains $token")
                    }
                }
            }
        }

        assertTrue(
            "Host-capability hotspots must stay free of compat helpers and direct execution API calls: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun host_capability_production_boundary_must_still_use_resolver_and_factory() {
        val requiredFiles = listOf(
            "feature/plugin/runtime/PluginExecutionHostResolver.kt",
            "feature/plugin/runtime/PluginHostCapabilityGatewayFactory.kt",
            "di/hilt/PluginHostCapabilityModule.kt",
        )
        val missing = requiredFiles.filterNot { relativePath -> mainRoot.resolve(relativePath).exists() }

        assertTrue(
            "Host-capability final state must keep resolver/factory/module production boundary: $missing",
            missing.isEmpty(),
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
