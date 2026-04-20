package com.astrbot.android.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class PostHiltRound1ContractTest {

    private val projectRoot: Path = detectProjectRoot()
    private val mainRoot: Path = projectRoot.resolve("app/src/main/java/com/astrbot/android")
    private val contractDoc: Path = projectRoot.resolve("docs/architecture/post-hilt-a-round1-contract.md")

    @Test
    fun round1_contract_doc_must_exist_and_record_scope_rules_and_debts() {
        assertTrue(
            "Round-1 Post-Hilt contract doc must exist at ${contractDoc.toAbsolutePath()}",
            contractDoc.exists(),
        )
        val text = contractDoc.readText()
        val requiredHeadings = listOf(
            "# Post-Hilt A Round 1 Contract",
            "## Allowed Production DI Paths",
            "## Forbidden Patterns",
            "## Transition Allowlist",
            "## Round 2 Debt",
            "## Out of Scope",
        )
        val missingHeadings = requiredHeadings.filterNot(text::contains)
        assertTrue("Round-1 contract doc is missing required headings: $missingHeadings", missingHeadings.isEmpty())

        val requiredEntries = listOf(
            "AppBootstrapper",
            "ProviderRepositoryInitializer",
            "PluginRuntimeRegistry",
            "PluginRuntimeFailureStateStoreProvider",
            "PluginRuntimeLogBusProvider",
            "PluginV2DispatchEngineProvider",
            "PluginV2LifecycleManagerProvider",
            "PluginV2RuntimeLoaderProvider",
            "PluginExecutionHostApi",
            "DefaultPluginHostCapabilityGateway",
            "Root Boundary",
            "NapCat",
            "PluginV2 broad failures",
            "WebSearch",
            "Chat UI",
            "Catalog",
            "plugin v1 cleanup",
        )
        val missingEntries = requiredEntries.filterNot(text::contains)
        assertTrue("Round-1 contract doc is missing required inventory entries: $missingEntries", missingEntries.isEmpty())
    }

    @Test
    fun production_sources_must_not_reintroduce_non_hilt_di_entrypoints() {
        val forbiddenTokens = listOf(
            "org.koin",
            "KoinComponent",
            "Kodein",
            "Toothpick",
            "ViewModelProvider.Factory",
            "viewModelFactory",
            "astrBotViewModel(",
        )
        val violations = kotlinFilesUnder(mainRoot).flatMap { file ->
            val relative = mainRoot.relativize(file).toString().replace('\\', '/')
            val text = file.readText()
            forbiddenTokens.mapNotNull { token ->
                if (text.contains(token)) "$relative contains $token" else null
            }
        }

        assertTrue(
            "Production sources must stay Hilt-only and avoid third-party DI/container reentry points: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun round1_static_provider_residuals_must_not_spread_beyond_allowlist() {
        val allowlist = mapOf(
            "ProviderRepositoryInitializer" to setOf(
                "feature/provider/data/ProviderRepositoryInitializer.kt",
            ),
            "PluginRuntimeRegistry" to setOf(
                "feature/plugin/runtime/AppChatPluginRuntime.kt",
            ),
            "PluginRuntimeFailureStateStoreProvider" to setOf(
                "di/hilt/PluginRuntimeModule.kt",
                "di/hilt/ViewModelDependencyModule.kt",
                "feature/plugin/presentation/PluginViewModel.kt",
                "feature/plugin/runtime/AppChatPluginRuntime.kt",
                "feature/plugin/runtime/ExternalPluginHostActionExecutor.kt",
                "feature/plugin/runtime/PluginFailureGuard.kt",
                "feature/plugin/runtime/PluginGovernanceRepository.kt",
                "feature/plugin/runtime/PluginGovernanceSnapshotMapper.kt",
                "feature/qq/runtime/QqPluginDispatchService.kt",
            ),
            "PluginRuntimeScopedFailureStateStoreProvider" to setOf(
                "di/hilt/PluginRuntimeModule.kt",
                "feature/plugin/runtime/PluginFailureGuard.kt",
            ),
            "PluginRuntimeLogBusProvider" to setOf(
                "di/hilt/PluginRuntimeModule.kt",
                "di/hilt/ViewModelDependencyModule.kt",
                "feature/plugin/presentation/PluginRuntimeLogScreen.kt",
                "feature/plugin/runtime/PluginFailureGuard.kt",
                "feature/plugin/runtime/PluginRuntimeLogBus.kt",
                "feature/plugin/runtime/PluginV2ActiveRuntimeStore.kt",
                "feature/plugin/runtime/PluginV2DispatchEngine.kt",
                "feature/plugin/runtime/PluginV2LifecycleManager.kt",
                "feature/plugin/runtime/PluginV2LlmPipelineCoordinator.kt",
                "feature/plugin/runtime/PluginV2RuntimeLoader.kt",
                "feature/plugin/runtime/PluginV2ToolLoopCoordinator.kt",
                "feature/plugin/runtime/PluginV2ToolRegistry.kt",
                "feature/plugin/runtime/PluginV2RegistryCompiler.kt",
                "feature/plugin/runtime/PluginV2BootstrapHostApi.kt",
                "feature/plugin/runtime/PluginV2FilterEvaluator.kt",
                "feature/plugin/runtime/PluginExecutionEngine.kt",
                "feature/plugin/runtime/PluginExecutionResultMerger.kt",
                "feature/plugin/runtime/PluginGovernanceRepository.kt",
                "feature/plugin/runtime/PluginGovernanceSnapshotMapper.kt",
                "feature/plugin/runtime/PluginInstaller.kt",
                "feature/plugin/runtime/PluginRuntimeDispatcher.kt",
                "feature/plugin/runtime/ExternalPluginHostActionExecutor.kt",
                "feature/plugin/runtime/catalog/PluginCatalogSynchronizer.kt",
            ),
            "PluginRuntimeScheduleStateStoreProvider" to setOf(
                "di/hilt/PluginRuntimeModule.kt",
                "feature/plugin/runtime/PluginRuntimeScheduler.kt",
            ),
            "PluginV2ActiveRuntimeStoreProvider" to setOf(
                "di/hilt/PluginRuntimeModule.kt",
                "feature/plugin/runtime/AppChatPluginRuntime.kt",
                "feature/plugin/runtime/PluginGovernanceRepository.kt",
                "feature/plugin/runtime/PluginGovernanceSnapshotMapper.kt",
                "feature/plugin/runtime/PluginV2ActiveRuntimeStore.kt",
                "feature/plugin/runtime/PluginV2DispatchEngine.kt",
                "feature/plugin/runtime/PluginV2LifecycleManager.kt",
                "feature/plugin/runtime/PluginV2LlmPipelineCoordinator.kt",
                "feature/plugin/runtime/PluginV2RuntimeLoader.kt",
            ),
            "PluginV2DispatchEngineProvider" to setOf(
                "di/hilt/PluginRuntimeModule.kt",
                "feature/chat/runtime/AppChatPluginCommandService.kt",
                "feature/plugin/runtime/PluginV2DispatchEngine.kt",
                "feature/plugin/runtime/PluginV2LlmPipelineCoordinator.kt",
                "feature/plugin/runtime/PluginV2ToolLoopCoordinator.kt",
                "feature/qq/runtime/QqPluginDispatchService.kt",
            ),
            "PluginV2LifecycleManagerProvider" to setOf(
                "di/hilt/PluginRuntimeModule.kt",
                "feature/plugin/runtime/PluginV2LifecycleManager.kt",
                "feature/plugin/runtime/PluginV2LlmPipelineCoordinator.kt",
                "feature/plugin/runtime/PluginV2RuntimeLoader.kt",
                "feature/plugin/runtime/PluginV2ToolLoopCoordinator.kt",
            ),
            "PluginV2RuntimeLoaderProvider" to setOf(
                "di/hilt/PluginRuntimeModule.kt",
                "feature/plugin/runtime/PluginV2RuntimeLoader.kt",
            ),
        )

        allowlist.forEach { (token, allowedPaths) ->
            assertTokenConfinedToAllowedProductionFiles(token, allowedPaths)
        }
    }

    @Test
    fun round1_host_capability_residuals_must_not_spread_beyond_allowlist() {
        val allowlist = mapOf(
            "PluginExecutionHostApi" to setOf(
                "feature/chat/presentation/ChatViewModel.kt",
                "feature/plugin/runtime/PluginExecutionHostApi.kt",
                "feature/plugin/runtime/PluginExecutionHostResolver.kt",
                "feature/plugin/runtime/PluginV2ActiveRuntimeStore.kt",
            ),
            "DefaultPluginHostCapabilityGateway" to setOf(
                "feature/plugin/runtime/PluginHostCapabilityGateway.kt",
                "feature/plugin/runtime/PluginHostCapabilityGatewayFactory.kt",
            ),
            "ExternalPluginHostActionExecutor(" to setOf(
                "feature/plugin/runtime/ExternalPluginHostActionExecutor.kt",
                "feature/plugin/runtime/PluginHostCapabilityGatewayFactory.kt",
            ),
        )

        allowlist.forEach { (token, allowedPaths) ->
            assertTokenConfinedToAllowedProductionFiles(token, allowedPaths)
        }
    }

    @Test
    fun round1_direct_new_runtime_hotspots_must_not_spread_beyond_allowlist() {
        val allowlist = mapOf(
            "PluginFailureGuard(" to setOf(
                "di/hilt/PluginRuntimeModule.kt",
                "di/hilt/ViewModelDependencyModule.kt",
                "feature/plugin/presentation/PluginViewModel.kt",
                "feature/plugin/runtime/AppChatPluginRuntime.kt",
                "feature/plugin/runtime/ExternalPluginHostActionExecutor.kt",
                "feature/plugin/runtime/PluginFailureGuard.kt",
                "feature/qq/runtime/QqPluginDispatchService.kt",
            ),
            "PluginExecutionEngine(" to setOf(
                "di/hilt/RuntimeServicesModule.kt",
                "feature/plugin/presentation/PluginViewModel.kt",
                "feature/plugin/runtime/AppChatPluginRuntime.kt",
                "feature/plugin/runtime/PluginExecutionEngine.kt",
                "feature/qq/runtime/QqPluginDispatchService.kt",
            ),
            "PluginV2LlmPipelineCoordinator(" to setOf(
                "feature/plugin/runtime/AppChatPluginRuntime.kt",
                "feature/plugin/runtime/PluginV2LlmPipelineCoordinator.kt",
            ),
            "FutureToolSourceRegistry(" to setOf(
                "feature/plugin/runtime/AppChatPluginRuntime.kt",
                "feature/plugin/runtime/toolsource/FutureToolSourceRegistry.kt",
            ),
        )

        allowlist.forEach { (token, allowedPaths) ->
            assertTokenConfinedToAllowedProductionFiles(token, allowedPaths)
        }
    }

    // ---- Round-1 Hilt-only DI closure anti-backflow contracts ----

    @Test
    fun facade_file_must_not_exist_in_production_source() {
        val facade = mainRoot.resolve("di/AstrBotViewModelDependencies.kt")
        assertTrue(
            "AstrBotViewModelDependencies.kt must be deleted from production source. " +
                "All DI should go through Hilt modules in di/hilt/.",
            !facade.exists(),
        )
    }

    @Test
    fun production_sources_must_not_define_view_model_dependency_facade_interfaces() {
        val forbiddenDeclarations = listOf(
            "interface BotViewModelDependencies",
            "interface ConfigViewModelDependencies",
            "interface ProviderViewModelDependencies",
            "interface ConversationViewModelDependencies",
            "interface PersonaViewModelDependencies",
            "interface PluginViewModelDependencies",
            "interface BridgeViewModelDependencies",
            "interface RuntimeAssetViewModelDependencies",
            "interface QQLoginViewModelDependencies",
            "object HiltBotViewModelDependencies",
            "object HiltConfigViewModelDependencies",
            "object HiltProviderViewModelDependencies",
            "object HiltConversationViewModelDependencies",
            "object HiltPersonaViewModelDependencies",
            "object HiltPluginViewModelDependencies",
            "object HiltBridgeViewModelDependencies",
            "object Phase3DataTransactionServiceRegistry",
            "fun installProductionPhase3DataTransactionService",
        )
        val violations = kotlinFilesUnder(mainRoot).flatMap { file ->
            val relative = mainRoot.relativize(file).toString().replace('\\', '/')
            val source = file.readText()
            forbiddenDeclarations.mapNotNull { decl ->
                if (source.contains(decl)) "$relative declares '$decl'" else null
            }
        }

        assertTrue(
            "Production sources must not reintroduce Phase-1 ViewModelDependencies facade types: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun chat_runtime_bindings_must_not_be_defined_in_di_package() {
        val diFiles = mainRoot.resolve("di").let { diRoot ->
            if (!diRoot.exists()) return
            Files.walk(diRoot).use { stream ->
                stream
                    .filter { it.isRegularFile() && it.fileName.toString().endsWith(".kt") }
                    .toList()
            }
        }
        val violations = diFiles.mapNotNull { file ->
            val relative = mainRoot.relativize(file).toString().replace('\\', '/')
            val source = file.readText()
            if (source.contains("interface ChatViewModelRuntimeBindings")) {
                "$relative defines ChatViewModelRuntimeBindings interface — must live in feature/chat/"
            } else {
                null
            }
        }

        assertTrue(
            "ChatViewModelRuntimeBindings interface must not be defined in the di/ package: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun production_view_models_must_use_hilt_injection_not_facade_objects() {
        val viewModelFiles = listOf(
            "feature/bot/presentation/BotViewModel.kt",
            "feature/config/presentation/ConfigViewModel.kt",
            "feature/chat/presentation/ChatViewModel.kt",
        )
        val forbiddenImportPrefix = "import com.astrbot.android.di.Hilt"
        val forbiddenFacadeImport = "import com.astrbot.android.di.AstrBotViewModelDependencies"

        val violations = viewModelFiles.flatMap { relativePath ->
            val file = mainRoot.resolve(relativePath)
            if (!file.exists()) return@flatMap emptyList()
            val source = file.readText()
            val issues = mutableListOf<String>()
            if (source.contains(forbiddenImportPrefix)) {
                issues += "$relativePath imports a Hilt* facade object"
            }
            if (source.contains(forbiddenFacadeImport)) {
                issues += "$relativePath imports AstrBotViewModelDependencies"
            }
            if (!source.contains("@HiltViewModel")) {
                issues += "$relativePath is missing @HiltViewModel annotation"
            }
            if (!source.contains("@Inject constructor")) {
                issues += "$relativePath is missing @Inject constructor"
            }
            issues
        }

        assertTrue(
            "Production ViewModels must use @HiltViewModel + @Inject constructor, not facade objects: $violations",
            violations.isEmpty(),
        )
    }

    private fun assertTokenConfinedToAllowedProductionFiles(
        token: String,
        allowedPaths: Set<String>,
    ) {
        val actualPaths = kotlinFilesUnder(mainRoot)
            .filter { file -> file.readText().contains(token) }
            .map { file -> mainRoot.relativize(file).toString().replace('\\', '/') }
            .toSet()
        val unexpectedPaths = actualPaths - allowedPaths

        assertTrue(
            "Round-1 residual token '$token' must stay inside the allowlist. Unexpected files: $unexpectedPaths",
            unexpectedPaths.isEmpty(),
        )
    }

    private fun kotlinFilesUnder(root: Path): List<Path> {
        return Files.walk(root).use { stream ->
            stream
                .filter { it.isRegularFile() && it.fileName.toString().endsWith(".kt") }
                .toList()
        }
    }

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
