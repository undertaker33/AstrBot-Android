package com.elymbot.android.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginV2HostApiArchitectureContractTest {

    private val projectRoot: Path = detectProjectRoot()
    private val runtimeRoot: Path =
        projectRoot.resolve("feature/plugin/runtime/src/main/java/com/elymbot/android")
    private val appRoot: Path =
        projectRoot.resolve("app/src/main/java/com/elymbot/android")
    private val appIntegrationRoot: Path =
        projectRoot.resolve("app-integration/src/main/java/com/elymbot/android")

    @Test
    fun no_new_production_static_host_api_path_is_added() {
        val forbiddenTokens = listOf(
            "PluginExecutionHostApi.resolve(",
            "PluginExecutionHostApi.inject(",
            "PluginExecutionHostApi.registerHostBuiltinTools(",
            "PluginExecutionHostApi.executeHostBuiltinTool(",
            "PluginExecutionHostApi.installCompatOperations(",
            "createCompatPluginHostCapabilityGateway(",
            "createCompatPluginHostCapabilityGatewayFactory(",
        )

        val violations = productionKotlinFiles(runtimeRoot, appIntegrationRoot).flatMap { file ->
            val text = file.readText()
            forbiddenTokens
                .filter(text::contains)
                .map { token -> "${projectRoot.relativize(file).toString().replace('\\', '/')} -> $token" }
        }

        assertTrue(
            "Plugin V2 host API foundation must not add production static host API paths: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun app_does_not_handwrite_plugin_runtime_graph_for_host_api_foundation() {
        val forbiddenAppTokens = listOf(
            "PluginV2HostApiFacade(",
            "PluginV2HostApiAsyncBridge(",
            "PluginV2HostApiPermissionPolicy(",
            "PluginV2HostApiAuditLogger(",
        )
        val appViolations = productionKotlinFiles(appRoot).flatMap { file ->
            val text = file.readText()
            forbiddenAppTokens
                .filter(text::contains)
                .map { token -> "${projectRoot.relativize(file).toString().replace('\\', '/')} -> $token" }
        }

        val hiltModuleText = appIntegrationRoot.resolve("di/hilt/PluginHostCapabilityModule.kt").readText()

        assertTrue(
            "App production source must not handwrite Plugin V2 host API runtime graph: $appViolations",
            appViolations.isEmpty(),
        )
        assertTrue(
            "PluginHostCapabilityModule must provide the PluginV2HostApiFacade production boundary.",
            hiltModuleText.contains("providePluginV2HostApiFacade"),
        )
        assertTrue(
            "PluginHostCapabilityModule must provide the PluginV2HostNetworkApi production boundary.",
            hiltModuleText.contains("providePluginV2HostNetworkApi"),
        )
    }

    @Test
    fun plugin_v2_host_network_api_does_not_create_raw_okhttp_or_android_permission_bypass() {
        val inspectedFiles = listOf(
            runtimeRoot.resolve("feature/plugin/runtime/ExternalPluginScriptExecutor.kt"),
            runtimeRoot.resolve("feature/plugin/runtime/PluginV2BootstrapHostApi.kt"),
            runtimeRoot.resolve("feature/plugin/runtime/PluginV2HostNetworkApi.kt"),
        )
        val forbiddenTokens = listOf(
            "OkHttpClient(",
            "OkHttpClient.Builder(",
            "android.permission.INTERNET",
            "<uses-permission",
        )
        val violations = inspectedFiles
            .filter { file -> file.exists() }
            .flatMap { file ->
                val text = file.readText()
                forbiddenTokens
                    .filter(text::contains)
                    .map { token -> "${projectRoot.relativize(file).toString().replace('\\', '/')} -> $token" }
            }

        assertTrue(
            "Plugin V2 host network API must use host-owned RuntimeNetworkTransport without raw client or permission bypass: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun quickjs_bootstrap_bridge_exposes_only_canonical_network_host_api_names() {
        val bridgeText = runtimeRoot.resolve("feature/plugin/runtime/ExternalPluginScriptExecutor.kt").readText()

        assertTrue(
            "QuickJS bridge must expose canonical hostApi.fetch.",
            bridgeText.contains("bindHostCall(bridge, \"fetch\")"),
        )
        assertTrue(
            "QuickJS bridge must expose canonical hostApi.network.request.",
            bridgeText.contains("bindHostCall(networkBridge, \"request\")"),
        )
        assertTrue(
            "QuickJS bridge must not expose AstrBot-style network aliases.",
            !bridgeText.contains("httpGet") && !bridgeText.contains("httpPost") && !bridgeText.contains("astrbot"),
        )
    }

    private fun productionKotlinFiles(vararg roots: Path): List<Path> {
        return roots
            .filter { root -> root.exists() }
            .flatMap { root ->
                Files.walk(root).use { stream ->
                    stream
                        .filter { file -> file.isRegularFile() && file.fileName.toString().endsWith(".kt") }
                        .toList()
                }
            }
    }

    private fun detectProjectRoot(): Path {
        val cwd = Path.of("").toAbsolutePath()
        return when {
            cwd.resolve("app/src/main/java/com/elymbot/android").exists() -> cwd
            cwd.parent?.resolve("app/src/main/java/com/elymbot/android")?.exists() == true -> cwd.parent
            else -> error("Unable to resolve project root from $cwd")
        }
    }
}
