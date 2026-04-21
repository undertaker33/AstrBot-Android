package com.astrbot.android.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class PostHiltRound3HostCapabilityContractTest {

    private val projectRoot: Path = detectProjectRoot()
    private val mainRoot: Path = projectRoot.resolve("app/src/main/java/com/astrbot/android")

    // -----------------------------------------------------------------------
    // 1. PluginHostCapabilityGateway.kt 不再直接调用 PluginExecutionHostApi
    // -----------------------------------------------------------------------

    @Test
    fun plugin_host_capability_gateway_must_not_call_execution_host_api_directly() {
        val gateway = mainRoot.resolve("feature/plugin/runtime/PluginHostCapabilityGateway.kt").readText()
        assertTrue(
            "PluginHostCapabilityGateway.kt must not call PluginExecutionHostApi.resolve()",
            !gateway.contains("PluginExecutionHostApi.resolve("),
        )
        assertTrue(
            "PluginHostCapabilityGateway.kt must not call PluginExecutionHostApi.inject()",
            !gateway.contains("PluginExecutionHostApi.inject("),
        )
        assertTrue(
            "PluginHostCapabilityGateway.kt must not call PluginExecutionHostApi.registerHostBuiltinTools()",
            !gateway.contains("PluginExecutionHostApi.registerHostBuiltinTools("),
        )
        assertTrue(
            "PluginHostCapabilityGateway.kt must not call PluginExecutionHostApi.executeHostBuiltinTool()",
            !gateway.contains("PluginExecutionHostApi.executeHostBuiltinTool("),
        )
    }

    @Test
    fun plugin_execution_host_resolver_seam_must_exist() {
        val resolver = mainRoot.resolve("feature/plugin/runtime/PluginExecutionHostResolver.kt")
        assertTrue(
            "PluginExecutionHostResolver.kt must exist at ${resolver.toAbsolutePath()}",
            resolver.exists(),
        )
        val text = resolver.readText()
        assertTrue("Must declare PluginExecutionHostResolver interface", text.contains("interface PluginExecutionHostResolver"))
        assertTrue("Must declare DefaultPluginExecutionHostResolver", text.contains("class DefaultPluginExecutionHostResolver"))
    }

    @Test
    fun plugin_host_capability_gateway_factory_must_exist() {
        val factory = mainRoot.resolve("feature/plugin/runtime/PluginHostCapabilityGatewayFactory.kt")
        assertTrue(
            "PluginHostCapabilityGatewayFactory.kt must exist",
            factory.exists(),
        )
        val text = factory.readText()
        assertTrue("Factory must use PluginExecutionHostResolver", text.contains("PluginExecutionHostResolver"))
        assertTrue("Factory must expose a create() method", text.contains("fun create("))
    }

    @Test
    fun hilt_module_must_own_gateway_factory_and_gateway_bindings() {
        val module = mainRoot.resolve("di/hilt/PluginHostCapabilityModule.kt").readText()
        assertTrue(
            "PluginHostCapabilityModule must provide PluginHostCapabilityGatewayFactory",
            module.contains("providePluginHostCapabilityGatewayFactory"),
        )
        assertTrue(
            "PluginHostCapabilityModule must bind PluginExecutionHostResolver",
            module.contains("bindPluginExecutionHostResolver"),
        )

        val runtimeServices = mainRoot.resolve("di/hilt/RuntimeServicesModule.kt").readText()
        assertTrue(
            "RuntimeServicesModule must provide PluginHostCapabilityGateway",
            runtimeServices.contains("providePluginHostCapabilityGateway"),
        )
    }

    // -----------------------------------------------------------------------
    // 2. A 范围热点不再 direct new DefaultPluginHostCapabilityGateway
    // -----------------------------------------------------------------------

    @Test
    fun a_scope_hotspots_must_not_directly_new_gateway() {
        val forbidden = listOf(
            "feature/chat/runtime/AppChatRuntimeService.kt" to "DefaultPluginHostCapabilityGateway(",
            "feature/chat/runtime/AppChatPluginCommandService.kt" to "DefaultPluginHostCapabilityGateway(",
            "feature/qq/runtime/QqMessageRuntimeService.kt" to "DefaultPluginHostCapabilityGateway(",
            "feature/qq/runtime/QqPluginDispatchService.kt" to "DefaultPluginHostCapabilityGateway(",
        )
        val violations = forbidden.filter { (path, token) ->
            mainRoot.resolve(path).readText().contains(token)
        }
        assertTrue(
            "A-scope hotspots must not direct-new DefaultPluginHostCapabilityGateway: $violations",
            violations.isEmpty(),
        )
    }

    // -----------------------------------------------------------------------
    // 3. PluginViewModel 不再读 PluginRuntimeRegistry.plugins() 也不再直接 inject
    // -----------------------------------------------------------------------

    @Test
    fun plugin_view_model_must_not_read_plugin_runtime_registry_or_inject_directly() {
        val vm = mainRoot.resolve("feature/plugin/presentation/PluginViewModel.kt").readText()
        assertTrue(
            "PluginViewModel must not call PluginRuntimeRegistry.plugins()",
            !vm.contains("PluginRuntimeRegistry.plugins()"),
        )
        assertTrue(
            "PluginViewModel must not call PluginExecutionHostApi.inject(",
            !vm.contains("PluginExecutionHostApi.inject("),
        )
        assertTrue(
            "PluginViewModel must not direct-new DefaultPluginHostCapabilityGateway",
            !vm.contains("DefaultPluginHostCapabilityGateway("),
        )
    }

    @Test
    fun plugin_view_model_entry_click_path_must_thread_hilt_log_bus_into_runtime_engine() {
        val vm = mainRoot.resolve("feature/plugin/presentation/PluginViewModel.kt")
            .readText()
            .replace("\r\n", "\n")
        assertTrue(
            "PluginViewModel entry click path must pass bindings.logBus into PluginRuntimeDispatcher",
            Regex(
                """dispatcher\s*=\s*PluginRuntimeDispatcher\(\s*failureGuard,\s*logBus\s*=\s*bindings\.logBus,\s*\)""",
            ).containsMatchIn(vm),
        )
        assertTrue(
            "PluginViewModel entry click path must pass bindings.logBus into PluginExecutionEngine",
            Regex(
                """failureGuard\s*=\s*failureGuard,\s*logBus\s*=\s*bindings\.logBus,""",
            ).containsMatchIn(vm),
        )
    }

    // -----------------------------------------------------------------------
    // 4. AppBootstrapper 不再拥有 ProviderRepositoryInitializer 或 registry 注册
    // -----------------------------------------------------------------------

    @Test
    fun app_bootstrapper_must_not_remain_provider_initializer_or_registry_owner() {
        val bootstrap = mainRoot.resolve("di/AppBootstrapper.kt").readText()
        assertTrue(
            "AppBootstrapper must not call ProviderRepositoryInitializer()",
            !bootstrap.contains("ProviderRepositoryInitializer()"),
        )
        assertTrue(
            "AppBootstrapper must not call PluginRuntimeRegistry.registerExternalProvider",
            !bootstrap.contains("PluginRuntimeRegistry.registerExternalProvider"),
        )
    }

    // -----------------------------------------------------------------------
    // 5. ProviderRepositoryWarmup 过渡服务必须存在
    // -----------------------------------------------------------------------

    @Test
    fun provider_repository_warmup_must_exist() {
        val warmup = mainRoot.resolve("feature/provider/data/ProviderRepositoryWarmup.kt")
        assertTrue(
            "ProviderRepositoryWarmup.kt must exist at ${warmup.toAbsolutePath()}",
            warmup.exists(),
        )
        val text = warmup.readText()
        assertTrue("ProviderRepositoryWarmup must expose warmUp()", text.contains("fun warmUp()"))
    }

    @Test
    fun qq_and_cron_mainlines_must_not_fall_back_to_static_default_runtime() {
        val qqBridge = mainRoot.resolve("feature/qq/runtime/QqOneBotBridgeServer.kt").readText()
        assertTrue(
            "QqOneBotBridgeServer mainline must source app chat runtime from injected dependencies",
            qqBridge.contains("requireRuntimeDependencies().appChatPluginRuntime"),
        )

        val cronExecutor = mainRoot.resolve("feature/cron/runtime/ScheduledTaskRuntimeExecutor.kt").readText()
        assertTrue(
            "ScheduledTaskRuntimeExecutor must not dispatch through DefaultAppChatPluginRuntime",
            !cronExecutor.contains("llmRuntime = DefaultAppChatPluginRuntime"),
        )
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    private fun detectProjectRoot(): Path {
        val cwd = Path.of("").toAbsolutePath()
        return when {
            cwd.resolve("app/src/main/java/com/astrbot/android").exists() -> cwd
            cwd.resolve("src/main/java/com/astrbot/android").exists() -> cwd.parent
            cwd.parent?.resolve("app/src/main/java/com/astrbot/android")?.exists() == true -> cwd.parent
            else -> error("Unable to resolve project root from $cwd")
        }
    }
}
