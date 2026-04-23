package com.astrbot.android.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class StrictHiltOnlyContractTest {

    private val projectRoot: Path = detectProjectRoot()
    private val mainRoot: Path = projectRoot.resolve("app/src/main/java/com/astrbot/android")
    private val contractDoc: Path = projectRoot.resolve("docs/architecture/post-hilt-a-round1-contract.md")

    @Test
    fun strict_contract_doc_must_exist_and_capture_frozen_round1_rules() {
        assertTrue(
            "Round-1 contract doc must exist at ${contractDoc.toAbsolutePath()}",
            contractDoc.exists(),
        )

        val text = contractDoc.readText()
        val requiredHeadings = listOf(
            "# Post-Hilt A Round 1 Contract",
            "## Allowed Production DI Paths",
            "## Forbidden Patterns",
            "## Transition Allowlist",
            "## Round 2 Debt",
            "### Round 3 Debt",
        )
        val missingHeadings = requiredHeadings.filterNot(text::contains)
        assertTrue("Round-1 contract doc is missing required headings: $missingHeadings", missingHeadings.isEmpty())

        val requiredDebtInventory = listOf(
            "PluginRuntimeFailureStateStoreProvider",
            "PluginRuntimeScopedFailureStateStoreProvider",
            "PluginRuntimeLogBusProvider",
            "PluginRuntimeScheduleStateStoreProvider",
            "PluginV2ActiveRuntimeStoreProvider",
            "PluginV2DispatchEngineProvider",
            "PluginV2LifecycleManagerProvider",
            "PluginV2RuntimeLoaderProvider",
            "PluginExecutionHostApi",
            "DefaultPluginHostCapabilityGateway",
            "ProviderRepositoryInitializer",
            "Round 3 Debt",
        )
        val missingDebtInventory = requiredDebtInventory.filterNot(text::contains)
        assertTrue(
            "Round-1 contract doc is missing frozen debt inventory entries: $missingDebtInventory",
            missingDebtInventory.isEmpty(),
        )
    }

    @Test
    fun explicit_runtime_seams_must_stay_confined_to_tiny_allowlists() {
        val explicitSeams = mapOf(
            "EntryPointAccessors.fromApplication(" to setOf(
                "core/runtime/container/ContainerRuntimeEntryPoint.kt",
            ),
            "installRuntimeDependencies(" to setOf(
                "feature/qq/runtime/QqOneBotBridgeServer.kt",
            ),
        )

        explicitSeams.forEach { (token, allowedPaths) ->
            assertTokenConfinedToAllowedProductionFiles(token, allowedPaths)
        }
    }

    @Test
    fun compat_gateway_helpers_must_be_absent_from_production() {
        val compatTokens = listOf(
            "createCompatPluginHostCapabilityGatewayFactory(",
            "createCompatPluginHostCapabilityGateway(",
        )

        val violations = buildList {
            compatTokens.forEach { token ->
                val actualPaths = kotlinFilesUnder(mainRoot)
                    .filter { file -> file.readText().contains(token) }
                    .map { file -> mainRoot.relativize(file).toString().replace('\\', '/') }
                    .toSet()
                if (actualPaths.isNotEmpty()) {
                    add("$token -> $actualPaths")
                }
            }
        }

        assertTrue(
            "Strict Hilt-only production must not retain compat gateway helpers: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun shell_repositories_must_remain_confined_to_current_shell_files() {
        assertTokenConfinedToAllowedProductionFiles(
            "NapCatBridgeRepository",
            setOf(
                "MainActivity.kt",
                "di/ContainerBridgeStatePorts.kt",
                "di/hilt/ViewModelDependencyModule.kt",
                "di/startup/BootstrapPrerequisitesStartupChain.kt",
                "feature/qq/data/NapCatBridgeRepository.kt",
                "feature/qq/data/NapCatLoginRepository.kt",
            ),
        )
        assertTokenConfinedToAllowedProductionFiles(
            "RuntimeAssetRepository",
            setOf(
                "data/RuntimeAssetRepository.kt",
                "di/ContainerBridgeStatePorts.kt",
                "di/hilt/ViewModelDependencyModule.kt",
                "di/startup/BootstrapPrerequisitesStartupChain.kt",
                "feature/provider/runtime/ProviderRuntimePort.kt",
                "ui/common/AssetScreens.kt",
            ),
        )
    }

    @Test
    fun plugin_runtime_dependency_bridge_residue_must_be_absent_from_production() {
        val actualPaths = kotlinFilesUnder(mainRoot)
            .filter { file -> file.readText().contains("PluginRuntimeDependencyBridge.") }
            .map { file -> mainRoot.relativize(file).toString().replace('\\', '/') }
            .toSet()

        assertTrue(
            "Strict Hilt-only completion state must not retain PluginRuntimeDependencyBridge references: $actualPaths",
            actualPaths.isEmpty(),
        )
    }

    @Test
    fun runtime_constructor_defaults_must_not_backslide_to_static_provider_sources() {
        val forbiddenTokensByFile = mapOf(
            "feature/plugin/runtime/PluginExecutionEngine.kt" to listOf(
                "private val logBus: PluginRuntimeLogBus = PluginRuntimeLogBusProvider.bus()",
            ),
            "feature/plugin/runtime/PluginFailureGuard.kt" to listOf(
                "private val store: PluginFailureStateStore = InMemoryPluginFailureStateStore()",
                "private val scopedStore: PluginScopedFailureStateStore = InMemoryPluginScopedFailureStateStore()",
                "private val scopedStore: PluginScopedFailureStateStore = PluginRuntimeScopedFailureStateStoreProvider.store()",
                "internal val logBus: PluginRuntimeLogBus = InMemoryPluginRuntimeLogBus()",
                "private val logBus: PluginRuntimeLogBus = PluginRuntimeLogBusProvider.bus()",
            ),
            "feature/plugin/runtime/PluginRuntimeDispatcher.kt" to listOf(
                "internal val scheduler: PluginRuntimeScheduler = PluginRuntimeScheduler(clock = clock)",
                "private val logBus: PluginRuntimeLogBus = PluginRuntimeLogBusProvider.bus()",
            ),
            "feature/plugin/runtime/PluginRuntimeScheduler.kt" to listOf(
                "private val store: PluginScheduleStateStore = InMemoryPluginScheduleStateStore()",
                "private val store: PluginScheduleStateStore = PluginRuntimeScheduleStateStoreProvider.store()",
            ),
            "feature/plugin/runtime/PluginV2ActiveRuntimeStore.kt" to listOf(
                "private val logBus: PluginRuntimeLogBus = PluginRuntimeLogBusProvider.bus()",
            ),
            "feature/plugin/runtime/PluginV2DispatchEngine.kt" to listOf(
                "private val store: PluginV2ActiveRuntimeStore = PluginV2ActiveRuntimeStoreProvider.store()",
                "private val logBus: PluginRuntimeLogBus = PluginRuntimeLogBusProvider.bus()",
            ),
            "feature/plugin/runtime/PluginV2LifecycleManager.kt" to listOf(
                "private val store: PluginV2ActiveRuntimeStore = PluginV2ActiveRuntimeStoreProvider.store()",
                "private val logBus: PluginRuntimeLogBus = PluginRuntimeLogBusProvider.bus()",
            ),
            "feature/plugin/runtime/PluginV2RuntimeLoader.kt" to listOf(
                "private val store: PluginV2ActiveRuntimeStore = PluginV2ActiveRuntimeStoreProvider.store()",
                "private val logBus: PluginRuntimeLogBus = PluginRuntimeLogBusProvider.bus()",
                "private val lifecycleManager: PluginV2LifecycleManager = PluginV2LifecycleManagerProvider.manager()",
            ),
            "feature/plugin/runtime/PluginGovernanceRepository.kt" to listOf(
                "PluginV2ActiveRuntimeStoreProvider.store().snapshot()",
                "PluginRuntimeFailureStateStoreProvider.store()",
                "PluginRuntimeLogBusProvider.bus()",
            ),
            "feature/plugin/runtime/PluginGovernanceSnapshotMapper.kt" to listOf(
                "PluginV2ActiveRuntimeStoreProvider.store().snapshot()",
                "PluginRuntimeFailureStateStoreProvider.store().get(installRecord.pluginId)",
                "PluginRuntimeLogBusProvider.bus()",
            ),
            "feature/plugin/runtime/PluginInstaller.kt" to listOf(
                "logBus: PluginRuntimeLogBus = PluginRuntimeLogBusProvider.bus()",
            ),
            "feature/plugin/runtime/PluginV2BootstrapHostApi.kt" to listOf(
                "private val logBus: PluginRuntimeLogBus = PluginRuntimeLogBusProvider.bus()",
            ),
            "feature/plugin/runtime/PluginV2FilterEvaluator.kt" to listOf(
                "private val logBus: PluginRuntimeLogBus = PluginRuntimeLogBusProvider.bus()",
            ),
            "feature/plugin/runtime/PluginV2ToolLoopCoordinator.kt" to listOf(
                "private val dispatchEngine: PluginV2DispatchEngine = PluginV2DispatchEngineProvider.engine()",
                "private val lifecycleManager: PluginV2LifecycleManager = PluginV2LifecycleManagerProvider.manager()",
                "private val logBus: PluginRuntimeLogBus = PluginRuntimeLogBusProvider.bus()",
            ),
            "feature/plugin/runtime/catalog/PluginCatalogSynchronizer.kt" to listOf(
                "logBus: PluginRuntimeLogBus = PluginRuntimeLogBusProvider.bus()",
            ),
        )

        val violations = buildList {
            forbiddenTokensByFile.forEach { (relativePath, forbiddenTokens) ->
                val text = mainRoot.resolve(relativePath).readText()
                forbiddenTokens.forEach { token ->
                    if (text.contains(token)) {
                        add("$relativePath -> $token")
                    }
                }
            }
        }

        assertTrue(
            "Phase-3 runtime constructors must not keep static provider defaults: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun runtime_hilt_modules_must_wire_scheduler_and_governance_repository_explicitly() {
        val runtimeServicesText = mainRoot.resolve("di/hilt/RuntimeServicesModule.kt").readText()
        assertTrue(
            "RuntimeServicesModule must inject PluginRuntimeScheduler into PluginExecutionEngine wiring.",
            runtimeServicesText.contains("scheduler: PluginRuntimeScheduler"),
        )
        assertTrue(
            "RuntimeServicesModule must thread the injected scheduler into PluginRuntimeDispatcher.",
            runtimeServicesText.contains("scheduler = scheduler"),
        )

        val viewModelModuleText = mainRoot.resolve("di/hilt/ViewModelDependencyModule.kt").readText()
        val requiredTokens = listOf(
            "repositoryStatePort: PluginRepositoryStatePort",
            "activeRuntimeStore: PluginV2ActiveRuntimeStore",
            "failureStateStore: PluginFailureStateStore",
            "logBus: PluginRuntimeLogBus",
            "runtimeSnapshotProvider = activeRuntimeStore::snapshot",
        )
        val missingTokens = requiredTokens.filterNot(viewModelModuleText::contains)
        assertTrue(
            "ViewModelDependencyModule must build PluginGovernanceRepository from explicit Hilt-owned ports: $missingTokens",
            missingTokens.isEmpty(),
        )
        assertTrue(
            "ViewModelDependencyModule must not keep the zero-arg PluginGovernanceRepository fallback.",
            !viewModelModuleText.contains("PluginGovernanceRepository()"),
        )
    }

    @Test
    fun phase4_hilt_shells_must_not_read_static_repository_or_shared_singleton_sources_directly() {
        val forbiddenTokensByFile = mapOf(
            "MainActivity.kt" to listOf(
                "NapCatBridgeRepository.config",
                "NapCatBridgeRepository.runtimeState",
            ),
            "ui/common/AssetScreens.kt" to listOf(
                "ProviderRepository.providers",
                "TtsVoiceAssetRepository.assets",
                "TtsVoiceAssetRepository.importReferenceAudio",
                "TtsVoiceAssetRepository.saveProviderBinding",
                "TtsVoiceAssetRepository.renameBinding",
                "TtsVoiceAssetRepository.deleteBinding",
                "TtsVoiceAssetRepository.clearReferenceAudio",
                "TtsVoiceAssetRepository.deleteReferenceClip",
                "RuntimeAssetRepository.ttsAssetState(",
            ),
            "di/ContainerBridgeStatePorts.kt" to listOf(
                "NapCatBridgeRepository.config",
                "NapCatBridgeRepository.runtimeState",
                "NapCatBridgeRepository.applyRuntimeDefaults",
                "NapCatBridgeRepository.markStarting",
                "NapCatBridgeRepository.markRunning",
                "NapCatBridgeRepository.markProcessRunning",
                "NapCatBridgeRepository.markStopped",
                "NapCatBridgeRepository.markChecking",
                "NapCatBridgeRepository.markError",
                "NapCatBridgeRepository.updateProgress",
                "NapCatBridgeRepository.markInstallerCached",
            ),
            "di/hilt/ViewModelDependencyModule.kt" to listOf(
                "NapCatBridgeRepository.config",
                "NapCatBridgeRepository.runtimeState",
                "NapCatBridgeRepository.updateConfig",
                "RuntimeAssetRepository.state",
                "RuntimeAssetRepository.refresh",
                "RuntimeAssetRepository.downloadAsset",
                "RuntimeAssetRepository.clearAsset",
                "RuntimeAssetRepository.downloadOnDeviceTtsModel",
                "RuntimeAssetRepository.clearOnDeviceTtsModel",
                "FeaturePluginRepository.records",
                "FeaturePluginRepository.repositorySources",
                "FeaturePluginRepository.catalogEntries",
            ),
            "feature/plugin/runtime/PluginV2RuntimeLoader.kt" to listOf(
                "FeaturePluginRepository.records",
                "FeaturePluginRepository.findByPluginId",
            ),
            "feature/plugin/runtime/PluginGovernanceRepository.kt" to listOf(
                "FeaturePluginRepository.records",
                "FeaturePluginRepository::findByPluginId",
            ),
            "feature/plugin/runtime/ExternalPluginRuntimeCatalog.kt" to listOf(
                "FeaturePluginRepository.records",
            ),
            "feature/provider/runtime/ProviderRuntimePort.kt" to listOf(
                "RuntimeAssetRepository.ttsAssetState",
            ),
            "data/http/AstrBotHttpClient.kt" to listOf(
                "SharedRuntimeNetworkTransport.get()",
            ),
            "feature/plugin/runtime/toolsource/WebSearchToolSourceProvider.kt" to listOf(
                "SharedRuntimeNetworkTransport.get()",
                "resolveProductionRuntimeNetworkTransport()",
            ),
            "feature/plugin/data/FeaturePluginRepository.kt" to listOf(
                "AstrBotDatabase.get(context)",
                "resolveProductionAstrBotDatabase(context)",
                "override val records: StateFlow<List<PluginInstallRecord>> = FeaturePluginRepository.records",
                "override val repositorySources: StateFlow<List<PluginRepositorySource>> = FeaturePluginRepository.repositorySources",
                "override val catalogEntries: StateFlow<List<PluginCatalogEntryRecord>> = FeaturePluginRepository.catalogEntries",
            ),
            "feature/qq/data/NapCatBridgeRepository.kt" to listOf(
                "AstrBotDatabase.get(context)",
                "resolveProductionAstrBotDatabase(context)",
                "val config: StateFlow<NapCatBridgeConfig> = NapCatBridgeRepository.config",
                "val runtimeState: StateFlow<NapCatRuntimeState> = NapCatBridgeRepository.runtimeState",
            ),
            "data/RuntimeAssetRepository.kt" to listOf(
                "val state: StateFlow<RuntimeAssetState> = RuntimeAssetRepository.state",
                "RuntimeAssetRepository.refresh(context)",
                "RuntimeAssetRepository.downloadAsset(context, assetId)",
                "RuntimeAssetRepository.clearAsset(context, assetId)",
                "RuntimeAssetRepository.downloadOnDeviceTtsModel(context, modelId)",
                "RuntimeAssetRepository.clearOnDeviceTtsModel(context, modelId)",
                "RuntimeAssetRepository.ttsAssetState(context)",
            ),
            "di/hilt/PluginRuntimeModule.kt" to listOf(
                "repositoryStatePort = StaticPluginRepositoryStatePort",
            ),
        )

        val violations = buildList {
            forbiddenTokensByFile.forEach { (relativePath, forbiddenTokens) ->
                val text = mainRoot.resolve(relativePath).readText()
                forbiddenTokens.forEach { token ->
                    if (text.contains(token)) {
                        add("$relativePath -> $token")
                    }
                }
            }
        }

        assertTrue(
            "Phase-4 Hilt-only production shells must not keep direct static repository/shared singleton reads: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun plugin_runtime_catalog_must_not_remain_a_production_registry_wiring_point() {
        val runtimeServices = mainRoot.resolve("di/hilt/RuntimeServicesModule.kt").readText()

        assertTrue(
            "RuntimeServicesModule must not install Hilt-owned plugin catalogs into the global static registry",
            !runtimeServices.contains("PluginRuntimeCatalog.installProviderFromHilt"),
        )
    }

    @Test
    fun closed_hilt_only_mainlines_must_not_backslide_to_transition_runtime_or_compat_helpers() {
        val closedMainlines = listOf(
            "feature/chat/presentation/ChatViewModel.kt",
            "feature/chat/runtime/AppChatPluginCommandService.kt",
            "feature/plugin/presentation/PluginViewModel.kt",
            "feature/qq/runtime/QqOneBotBridgeServer.kt",
            "feature/qq/runtime/QqOneBotRuntimeGraph.kt",
            "feature/qq/runtime/QqPluginDispatchService.kt",
            "feature/qq/runtime/QqPluginExecutionService.kt",
        )
        val transitionTokens = listOf(
            "PluginRuntimeLogBusProvider.",
            "PluginRuntimeDependencyBridge.",
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
            "Closed Hilt-only mainlines must stay in completion state instead of regressing to transition helpers: $violations",
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
            "Token '$token' must stay inside the allowlist. Unexpected files: $unexpectedPaths",
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
