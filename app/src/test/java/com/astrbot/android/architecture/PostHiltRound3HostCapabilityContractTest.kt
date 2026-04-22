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
    fun plugin_view_model_entry_click_path_must_use_entry_execution_service() {
        val vm = mainRoot.resolve("feature/plugin/presentation/PluginViewModel.kt").readText()
        assertTrue(
            "PluginViewModel must depend on PluginEntryExecutionService for entry clicks",
            vm.contains("PluginEntryExecutionService"),
        )
        assertTrue(
            "PluginViewModel must not keep PluginHostCapabilityGatewayFactory on the production entry-click path",
            !vm.contains("PluginHostCapabilityGatewayFactory"),
        )
        assertTrue(
            "PluginViewModel must invoke entryExecutionService.execute(...)",
            vm.contains("entryExecutionService.execute("),
        )
        assertTrue(
            "PluginViewModel must not direct-new PluginFailureGuard in the entry click path",
            !vm.contains("PluginFailureGuard("),
        )
        assertTrue(
            "PluginViewModel must not direct-new PluginRuntimeDispatcher in the entry click path",
            !vm.contains("PluginRuntimeDispatcher("),
        )
        assertTrue(
            "PluginViewModel must not direct-new PluginExecutionEngine in the entry click path",
            !vm.contains("PluginExecutionEngine("),
        )
    }

    @Test
    fun qq_plugin_dispatch_service_must_use_execution_service_instead_of_direct_new_runtime_graph() {
        val qqDispatchService = mainRoot.resolve("feature/qq/runtime/QqPluginDispatchService.kt").readText()
        assertTrue(
            "QqPluginDispatchService must depend on QqPluginExecutionService",
            qqDispatchService.contains("QqPluginExecutionService"),
        )
        assertTrue(
            "QqPluginDispatchService must not default-new QqPluginExecutionService inside its constructor",
            !qqDispatchService.contains(
                "private val executionService: QqPluginExecutionService = QqPluginExecutionService(",
            ),
        )
        assertTrue(
            "QqPluginDispatchService must not direct-new PluginFailureGuard",
            !qqDispatchService.contains("PluginFailureGuard("),
        )
        assertTrue(
            "QqPluginDispatchService must not direct-new PluginRuntimeDispatcher",
            !qqDispatchService.contains("PluginRuntimeDispatcher("),
        )
        assertTrue(
            "QqPluginDispatchService must not direct-new PluginExecutionEngine",
            !qqDispatchService.contains("PluginExecutionEngine("),
        )
        assertTrue(
            "QqPluginDispatchService must not create host capability gateways at call sites",
            !qqDispatchService.contains("gatewayFactory.create("),
        )
    }

    @Test
    fun app_chat_plugin_command_service_must_not_use_compat_or_call_site_gateway_factory_entries() {
        val appChatCommandService = mainRoot.resolve("feature/chat/runtime/AppChatPluginCommandService.kt").readText()
        assertTrue(
            "AppChatPluginCommandService must not use createCompatPluginHostCapabilityGatewayFactory()",
            !appChatCommandService.contains("createCompatPluginHostCapabilityGatewayFactory("),
        )
        assertTrue(
            "AppChatPluginCommandService must not create host capability gateways at call sites",
            !appChatCommandService.contains("gatewayFactory.create("),
        )
    }

    @Test
    fun round3_completed_host_capability_mainlines_must_stay_free_of_transition_helpers() {
        val closedMainlines = listOf(
            "feature/chat/presentation/ChatViewModel.kt",
            "feature/chat/runtime/AppChatPluginCommandService.kt",
            "feature/plugin/presentation/PluginViewModel.kt",
            "feature/qq/runtime/QqOneBotBridgeServer.kt",
            "feature/qq/runtime/QqOneBotRuntimeGraph.kt",
            "feature/qq/runtime/QqPluginDispatchService.kt",
        )
        val transitionTokens = listOf(
            "PluginRuntimeDependencyBridge.",
            "PluginRuntimeLogBusProvider.",
            "createCompatPluginHostCapabilityGatewayFactory(",
            "createCompatPluginHostCapabilityGateway(",
        )

        val violations = buildList {
            closedMainlines.forEach { relativePath ->
                val text = mainRoot.resolve(relativePath).readText()
                transitionTokens.forEach { token ->
                    if (text.contains(token)) {
                        add("$relativePath -> $token")
                    }
                }
            }
        }

        assertTrue(
            "Round-3 completed host-capability mainlines must stay free of transition helper tokens: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun chat_view_model_secondary_constructors_must_not_route_through_compat_runtime() {
        val chatViewModel = mainRoot.resolve("feature/chat/presentation/ChatViewModel.kt").readText()
        assertTrue(
            "ChatViewModel must not call compatAppChatPluginRuntime() from production secondary constructors",
            !chatViewModel.contains("compatAppChatPluginRuntime()"),
        )
        assertTrue(
            "ChatViewModel secondary constructors must source runtime from bindings",
            chatViewModel.contains("dependencies.defaultAppChatPluginRuntime"),
        )
    }

    @Test
    fun app_chat_plugin_runtime_and_runtime_services_must_not_expose_compat_runtime_mainline() {
        val runtime = mainRoot.resolve("feature/plugin/runtime/AppChatPluginRuntime.kt").readText()
        assertTrue(
            "Production AppChatPluginRuntime.kt must not declare DefaultAppChatPluginRuntime",
            !runtime.contains("DefaultAppChatPluginRuntime"),
        )
        assertTrue(
            "Production AppChatPluginRuntime.kt must not declare compatAppChatPluginRuntime()",
            !runtime.contains("compatAppChatPluginRuntime("),
        )
        assertTrue(
            "Production AppChatPluginRuntime.kt must not declare compatAppChatLlmPipelineRuntime()",
            !runtime.contains("compatAppChatLlmPipelineRuntime("),
        )
        assertTrue(
            "Production AppChatPluginRuntime.kt must not keep AppChatPluginRuntimeCompatLocator",
            !runtime.contains("AppChatPluginRuntimeCompatLocator"),
        )
        assertTrue(
            "Production AppChatPluginRuntime.kt must not keep fallback runtime creation",
            !runtime.contains("createFallbackRuntime("),
        )

        val runtimeServices = mainRoot.resolve("di/hilt/RuntimeServicesModule.kt").readText()
        assertTrue(
            "RuntimeServicesModule must not install Hilt runtime into a compat locator",
            !runtimeServices.contains("AppChatPluginRuntimeCompatLocator.installFromHilt("),
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
