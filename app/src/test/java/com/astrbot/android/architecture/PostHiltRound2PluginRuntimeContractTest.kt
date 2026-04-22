package com.astrbot.android.architecture

import com.astrbot.android.di.hilt.PluginRuntimeModule
import com.astrbot.android.feature.plugin.runtime.PluginRuntimeFailureStateStoreProvider
import com.astrbot.android.feature.plugin.runtime.PluginRuntimeLogBusProvider
import com.astrbot.android.feature.plugin.runtime.PluginRuntimeScheduleStateStoreProvider
import com.astrbot.android.feature.plugin.runtime.PluginRuntimeScopedFailureStateStoreProvider
import com.astrbot.android.feature.plugin.runtime.PluginV2ActiveRuntimeStoreProvider
import com.astrbot.android.feature.plugin.runtime.PluginV2DispatchEngineProvider
import com.astrbot.android.feature.plugin.runtime.PluginV2LifecycleManagerProvider
import com.astrbot.android.feature.plugin.runtime.PluginV2RuntimeLoaderProvider
import java.nio.file.Path
import org.junit.Assert.assertSame
import kotlin.io.path.exists
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class PostHiltRound2PluginRuntimeContractTest {

    private val projectRoot: Path = detectProjectRoot()
    private val moduleFile: Path = projectRoot.resolve("app/src/main/java/com/astrbot/android/di/hilt/PluginRuntimeModule.kt")
    private val mainRoot: Path = projectRoot.resolve("app/src/main/java/com/astrbot/android")

    @Test
    fun plugin_runtime_module_must_exist_and_expose_round2_providers() {
        assertTrue("PluginRuntimeModule.kt must exist at ${moduleFile.toAbsolutePath()}", moduleFile.exists())

        val text = moduleFile.readText()
        val requiredSymbols = listOf(
            "providePluginRuntimeLogBus",
            "providePluginFailureStateStore",
            "providePluginScopedFailureStateStore",
            "providePluginScheduleStateStore",
            "providePluginV2ActiveRuntimeStore",
            "providePluginV2DispatchEngine",
            "providePluginV2LifecycleManager",
            "providePluginV2RuntimeLoader",
        )

        val missingSymbols = requiredSymbols.filterNot(text::contains)
        assertTrue(
            "PluginRuntimeModule.kt must expose round2 provider methods: $missingSymbols",
            missingSymbols.isEmpty(),
        )
    }

    @Test
    fun plugin_runtime_module_must_not_keep_bridge_install_hooks_in_production() {
        val text = moduleFile.readText()

        assertTrue(
            "PluginRuntimeModule must stop installing Hilt-owned runtime objects into PluginRuntimeDependencyBridge",
            !text.contains("PluginRuntimeDependencyBridge.install"),
        )
    }

    @Test
    fun runtime_services_and_bootstrapper_must_route_runtime_mainline_through_hilt_owned_objects() {
        val bootstrapper = mainRoot.resolve("di/AppBootstrapper.kt").readText()
        val runtimeServices = mainRoot.resolve("di/hilt/RuntimeServicesModule.kt").readText()

        assertTrue(
            "AppBootstrapper must stop sourcing loader/lifecycle through static providers",
            !bootstrapper.contains("PluginV2RuntimeLoaderProvider.loader()") &&
                !bootstrapper.contains("PluginV2LifecycleManagerProvider.manager()"),
        )
        assertTrue(
            "RuntimeServicesModule must provide a Hilt-owned PluginExecutionEngine",
            runtimeServices.contains("providePluginExecutionEngine(") &&
                runtimeServices.contains("PluginRuntimeDispatcher(") &&
                runtimeServices.contains("logBus = logBus"),
        )
        assertTrue(
            "RuntimeServicesModule must stop exposing the static DefaultAppChatPluginRuntime mainline",
            runtimeServices.contains("provideAppChatPluginRuntime(") &&
                runtimeServices.contains("EngineBackedAppChatPluginRuntime") &&
                !runtimeServices.contains("DefaultAppChatPluginRuntime"),
        )
    }

    @Test
    fun app_chat_runtime_mainline_must_bind_catalog_and_v2_runtime_services_through_hilt_owned_instances() {
        val runtimeServices = mainRoot.resolve("di/hilt/RuntimeServicesModule.kt").readText()
        val appChatRuntime = mainRoot.resolve("feature/plugin/runtime/AppChatPluginRuntime.kt").readText()
        val productionRuntimeSection = appChatRuntime.substringBefore("object PluginRuntimeRegistry")

        val violations = buildList {
            if (!runtimeServices.contains("provideExternalPluginRuntimeCatalog(")) {
                add("RuntimeServicesModule must expose a Hilt-owned ExternalPluginRuntimeCatalog")
            }
            if (runtimeServices.contains("PluginRuntimeCatalog::plugins")) {
                add("RuntimeServicesModule still sources app-chat plugins from PluginRuntimeCatalog")
            }
            if (runtimeServices.contains("PluginRuntimeCatalog.installProviderFromHilt")) {
                add("RuntimeServicesModule still installs the plugin catalog into the global static registry")
            }
            if (!runtimeServices.contains("pluginCatalog = pluginCatalog")) {
                add("RuntimeServicesModule does not inject the Hilt-owned plugin catalog into AppChat runtime")
            }
            if (!runtimeServices.contains("activeRuntimeStore = activeRuntimeStore")) {
                add("RuntimeServicesModule does not inject the active runtime store into AppChat runtime")
            }
            if (!runtimeServices.contains("dispatchEngine = pluginV2DispatchEngine")) {
                add("RuntimeServicesModule does not inject the v2 dispatch engine into AppChat runtime")
            }
            if (!runtimeServices.contains("lifecycleManager = pluginV2LifecycleManager")) {
                add("RuntimeServicesModule does not inject the lifecycle manager into AppChat runtime")
            }
            if (!runtimeServices.contains("logBus = logBus")) {
                add("RuntimeServicesModule does not inject the log bus into AppChat runtime")
            }
            if (productionRuntimeSection.contains("PluginRuntimeDependencyBridge.")) {
                add("AppChatPluginRuntime production path still reads PluginRuntimeDependencyBridge")
            }
        }

        assertTrue(
            "App-chat runtime mainline must be composed from Hilt-owned catalog/runtime services instead of bridge or static catalog helpers: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun qq_runtime_graph_must_pass_hilt_owned_plugin_runtime_dependencies_to_dispatch_service() {
        val graph = mainRoot.resolve("feature/qq/runtime/QqOneBotRuntimeGraph.kt").readText()
        val dependencies = mainRoot.resolve("feature/qq/runtime/QqOneBotBridgeServer.kt").readText()

        assertTrue(
            "QqOneBotRuntimeDependencies must carry the Hilt-owned QQ plugin execution service seam",
            dependencies.contains("val pluginExecutionService: QqPluginExecutionService"),
        )
        assertTrue(
            "QqOneBotRuntimeGraph must pass the Hilt-owned plugin execution service into the QQ runtime graph",
            graph.contains("dispatchEngine = dependencies.pluginV2DispatchEngine") &&
                graph.contains("executionService = dependencies.pluginExecutionService"),
        )
    }

    @Test
    fun production_plugin_runtime_mainline_must_not_call_static_provider_accessors() {
        val runtimeServices = mainRoot.resolve("di/hilt/RuntimeServicesModule.kt").readText()
        val qqGraph = mainRoot.resolve("feature/qq/runtime/QqOneBotRuntimeGraph.kt").readText()
        val qqExecutionService = mainRoot.resolve("feature/qq/runtime/QqPluginExecutionService.kt").readText()
        val appChatCommand = mainRoot.resolve("feature/chat/runtime/AppChatPluginCommandService.kt").readText()
        val violations = buildList {
            if (!runtimeServices.contains("logBus = logBus")) {
                add("RuntimeServicesModule does not wire logBus explicitly into Hilt PluginExecutionEngine")
            }
            if (!qqGraph.contains("executionService = dependencies.pluginExecutionService")) {
                add("QqOneBotRuntimeGraph does not pass the Hilt-owned plugin execution service into the QQ runtime graph")
            }
            val executionServiceBody = qqExecutionService.substringAfter(
                "internal constructor(",
            ).substringBefore(
                "fun executeBatch(",
            )
            if (
                executionServiceBody.contains("PluginRuntimeFailureStateStoreProvider.store()") ||
                executionServiceBody.contains("PluginRuntimeLogBusProvider.bus()") ||
                executionServiceBody.contains("PluginRuntimeScopedFailureStateStoreProvider.store()")
            ) {
                add("QqPluginExecutionService still sources plugin runtime state from static providers instead of explicit constructor seams")
            }
            val primaryConstructor = appChatCommand.substringBefore("@Deprecated(")
            if (primaryConstructor.contains("PluginV2DispatchEngineProvider.engine()")) {
                add("AppChatPluginCommandService primary production constructor still defaults dispatchEngine from static provider")
            }
        }

        assertTrue(
            "Production plugin runtime mainlines must receive Hilt-owned instances instead of calling static provider accessors: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun round2_completed_runtime_mainlines_must_not_reference_transition_bridges_or_log_bus_providers() {
        val closedMainlines = listOf(
            "di/hilt/RuntimeServicesModule.kt",
            "feature/plugin/runtime/AppChatPluginRuntime.kt",
            "feature/qq/runtime/QqOneBotBridgeServer.kt",
            "feature/qq/runtime/QqPluginExecutionService.kt",
        )
        val transitionTokens = listOf(
            "PluginRuntimeDependencyBridge.",
            "PluginRuntimeLogBusProvider.",
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
            "Round-2 completed runtime mainlines must stay free of transition bridge/log-bus tokens: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun plugin_runtime_module_must_expose_hilt_owned_round2_dependencies_through_explicit_seams() {
        PluginRuntimeLogBusProvider.setBusOverrideForTests(null)
        PluginRuntimeFailureStateStoreProvider.setStoreOverrideForTests(null)
        PluginRuntimeScopedFailureStateStoreProvider.setStoreOverrideForTests(null)
        PluginRuntimeScheduleStateStoreProvider.setStoreOverrideForTests(null)
        PluginV2ActiveRuntimeStoreProvider.setStoreOverrideForTests(null)
        PluginV2LifecycleManagerProvider.setManagerOverrideForTests(null)
        PluginV2DispatchEngineProvider.setEngineOverrideForTests(null)
        PluginV2RuntimeLoaderProvider.setLoaderOverrideForTests(null)

        try {
            val logBus = PluginRuntimeModule.providePluginRuntimeLogBus()
            val failureStore = PluginRuntimeModule.providePluginFailureStateStore()
            val scopedFailureStore = PluginRuntimeModule.providePluginScopedFailureStateStore()
            val scheduleStateStore = PluginRuntimeModule.providePluginScheduleStateStore()
            val activeRuntimeStore = PluginRuntimeModule.providePluginV2ActiveRuntimeStore(logBus)
            val lifecycleManager = PluginRuntimeModule.providePluginV2LifecycleManager(
                activeRuntimeStore = activeRuntimeStore,
                logBus = logBus,
            )
            val dispatchEngine = PluginRuntimeModule.providePluginV2DispatchEngine(
                activeRuntimeStore = activeRuntimeStore,
                logBus = logBus,
                lifecycleManager = lifecycleManager,
            )
            val runtimeLoader = PluginRuntimeModule.providePluginV2RuntimeLoader(
                activeRuntimeStore = activeRuntimeStore,
                logBus = logBus,
                lifecycleManager = lifecycleManager,
            )

            assertSame(
                "PluginRuntimeLogBusProvider must expose the Hilt-owned log bus instance",
                logBus,
                PluginRuntimeLogBusProvider.bus(),
            )
            assertSame(
                "PluginRuntimeFailureStateStoreProvider must expose the Hilt-owned failure store",
                failureStore,
                PluginRuntimeFailureStateStoreProvider.store(),
            )
            assertSame(
                "PluginRuntimeScopedFailureStateStoreProvider must expose the Hilt-owned scoped failure store",
                scopedFailureStore,
                PluginRuntimeScopedFailureStateStoreProvider.store(),
            )
            assertSame(
                "PluginRuntimeScheduleStateStoreProvider must expose the Hilt-owned schedule store",
                scheduleStateStore,
                PluginRuntimeScheduleStateStoreProvider.store(),
            )
            assertSame(
                "PluginV2ActiveRuntimeStoreProvider must expose the Hilt-owned active runtime store",
                activeRuntimeStore,
                PluginV2ActiveRuntimeStoreProvider.store(),
            )
            assertSame(
                "PluginV2LifecycleManagerProvider must expose the Hilt-owned lifecycle manager",
                lifecycleManager,
                PluginV2LifecycleManagerProvider.manager(),
            )
            assertSame(
                "PluginV2DispatchEngineProvider must expose the Hilt-owned dispatch engine",
                dispatchEngine,
                PluginV2DispatchEngineProvider.engine(),
            )
            assertSame(
                "PluginV2RuntimeLoaderProvider must expose the Hilt-owned runtime loader",
                runtimeLoader,
                PluginV2RuntimeLoaderProvider.loader(),
            )
        } finally {
            PluginRuntimeLogBusProvider.setBusOverrideForTests(null)
            PluginRuntimeFailureStateStoreProvider.setStoreOverrideForTests(null)
            PluginRuntimeScopedFailureStateStoreProvider.setStoreOverrideForTests(null)
            PluginRuntimeScheduleStateStoreProvider.setStoreOverrideForTests(null)
            PluginV2ActiveRuntimeStoreProvider.setStoreOverrideForTests(null)
            PluginV2LifecycleManagerProvider.setManagerOverrideForTests(null)
            PluginV2DispatchEngineProvider.setEngineOverrideForTests(null)
            PluginV2RuntimeLoaderProvider.setLoaderOverrideForTests(null)
        }
    }

    @Test
    fun view_model_dependency_module_must_not_source_plugin_runtime_mainline_from_static_providers() {
        val viewModelModule = mainRoot.resolve("di/hilt/ViewModelDependencyModule.kt").readText()

        assertTrue(
            "ViewModelDependencyModule must not source PluginFailureGuard from static providers",
            !viewModelModule.contains("PluginRuntimeFailureStateStoreProvider.store()"),
        )
        assertTrue(
            "ViewModelDependencyModule must not source PluginRuntimeLogBus from static providers",
            !viewModelModule.contains("PluginRuntimeLogBusProvider.bus()"),
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
