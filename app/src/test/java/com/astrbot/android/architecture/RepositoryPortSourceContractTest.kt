package com.astrbot.android.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryPortSourceContractTest {

    private val mainRoot: Path = listOf(
        Path.of("src/main/java/com/astrbot/android"),
        Path.of("app/src/main/java/com/astrbot/android"),
    ).first { it.exists() }

    @Test
    fun domain_ports_exist() {
        val required = listOf(
            "feature/provider/domain/ProviderRepositoryPort.kt",
            "feature/config/domain/ConfigRepositoryPort.kt",
            "feature/bot/domain/BotRepositoryPort.kt",
            "feature/persona/domain/PersonaRepositoryPort.kt",
        )
        val missing = required.filterNot { mainRoot.resolve(it).exists() }
        assertTrue("Missing domain port files: $missing", missing.isEmpty())
    }

    @Test
    fun domain_ports_do_not_import_legacy_repositories() {
        val domainDirs = listOf(
            "feature/provider/domain",
            "feature/config/domain",
            "feature/bot/domain",
            "feature/persona/domain",
        )
        val forbiddenImports = listOf(
            "import com.astrbot.android.data.ProviderRepository",
            "import com.astrbot.android.data.ConfigRepository",
            "import com.astrbot.android.data.BotRepository",
            "import com.astrbot.android.data.PersonaRepository",
        )

        val violations = domainDirs.flatMap { dir ->
            kotlinFilesUnder(dir).flatMap { file ->
                val text = file.readText()
                val relative = mainRoot.relativize(file).toString().replace('\\', '/')
                forbiddenImports.mapNotNull { forbidden ->
                    if (text.contains(forbidden)) "$relative imports $forbidden" else null
                }
            }
        }

        assertTrue(
            "Domain ports must not import legacy repository singletons: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun domain_ports_do_not_expose_mutable_flows() {
        val domainDirs = listOf(
            "feature/provider/domain",
            "feature/config/domain",
            "feature/bot/domain",
            "feature/persona/domain",
        )
        val violations = domainDirs.flatMap { dir ->
            kotlinFilesUnder(dir).flatMap { file ->
                val text = file.readText()
                val relative = mainRoot.relativize(file).toString().replace('\\', '/')
                if (text.contains("MutableStateFlow") || text.contains("MutableSharedFlow")) {
                    listOf("$relative exposes mutable flow")
                } else {
                    emptyList()
                }
            }
        }

        assertTrue(
            "Domain ports must not expose mutable flows: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun new_feature_domain_files_do_not_import_android_or_legacy_packages() {
        val domainDirs = listOf(
            "feature/provider/domain",
            "feature/config/domain",
            "feature/bot/domain",
            "feature/persona/domain",
        )
        val forbiddenPrefixes = listOf(
            "import android.",
            "import androidx.compose.",
            "import com.astrbot.android.data.",
            "import com.astrbot.android.runtime.",
        )

        val violations = domainDirs.flatMap { dir ->
            kotlinFilesUnder(dir).flatMap { file ->
                val text = file.readText()
                val relative = mainRoot.relativize(file).toString().replace('\\', '/')
                forbiddenPrefixes.mapNotNull { prefix ->
                    if (text.contains(prefix)) "$relative imports $prefix" else null
                }
            }
        }

        assertTrue(
            "Domain files must not import Android framework, Compose, or legacy packages: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun legacy_adapters_do_not_add_runBlocking() {
        val adapterFiles = listOf(
            "feature/provider/data/LegacyProviderRepositoryAdapter.kt",
            "feature/config/data/LegacyConfigRepositoryAdapter.kt",
            "feature/bot/data/LegacyBotRepositoryAdapter.kt",
            "feature/persona/data/LegacyPersonaRepositoryAdapter.kt",
        )

        val violations = adapterFiles.mapNotNull { path ->
            val file = mainRoot.resolve(path)
            if (!file.exists()) return@mapNotNull null
            val text = file.readText()
            if (text.contains("runBlocking")) "$path uses runBlocking" else null
        }

        assertTrue(
            "Legacy adapters must not add runBlocking: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun initialization_coordinator_exists() {
        val required = listOf(
            "core/di/AppInitializer.kt",
            "core/di/InitializationCoordinator.kt",
            "feature/provider/data/ProviderRepositoryInitializer.kt",
            "feature/config/data/ConfigRepositoryInitializer.kt",
            "feature/persona/data/PersonaRepositoryInitializer.kt",
            "feature/bot/data/BotRepositoryInitializer.kt",
        )
        val missing = required.filterNot { mainRoot.resolve(it).exists() }
        assertTrue("Missing initialization coordinator files: $missing", missing.isEmpty())
    }

    @Test
    fun app_container_bootstrap_uses_coordinator_for_configuration_domains() {
        val startupBody = functionBody(repositoryInitializationStartupChainSource(), "run")

        assertTrue(
            "RepositoryInitializationStartupChain.run must delegate configuration-domain startup to InitializationCoordinator",
            startupBody.contains("InitializationCoordinator("),
        )
        // Round3: ProviderRepositoryInitializer is no longer wired through InitializationCoordinator.
        // Provider warmup is now handled by ProviderRepositoryWarmup.warmUp().
        val requiredInitializers = listOf(
            "ConfigRepositoryInitializer()",
            "PersonaRepositoryInitializer()",
            "BotRepositoryInitializer()",
        )
        val missingInitializers = requiredInitializers.filterNot(startupBody::contains)
        assertTrue(
            "RepositoryInitializationStartupChain wiring is missing initializers: $missingInitializers",
            missingInitializers.isEmpty(),
        )
        assertTrue(
            "RepositoryInitializationStartupChain must execute the configuration-domain coordinator",
            startupBody.contains(".initializeAll(application)"),
        )
        assertTrue(
            "RepositoryInitializationStartupChain must call providerRepositoryWarmup.warmUp() (Round3 contract)",
            startupBody.contains("providerRepositoryWarmup.warmUp()"),
        )
        assertFalse(
            "RepositoryInitializationStartupChain must not wire ProviderRepositoryInitializer through InitializationCoordinator (Round3 contract)",
            startupBody.contains("ProviderRepositoryInitializer()"),
        )

        val forbiddenDirectCalls = listOf(
            "ProviderRepository.initialize(application)",
            "ConfigRepository.initialize(application)",
            "PersonaRepository.initialize(application)",
            "BotRepository.initialize(application)",
        )
        val remainingDirectCalls = forbiddenDirectCalls.filter(startupBody::contains)
        assertTrue(
            "RepositoryInitializationStartupChain must not directly initialize repositories now owned by InitializationCoordinator: $remainingDirectCalls",
            remainingDirectCalls.isEmpty(),
        )
    }

    @Test
    fun app_bootstrapper_avoids_sync_tts_and_duplicate_config_initialization() {
        val bootstrapBody = functionBody(appBootstrapperSource(), "bootstrap")
        val prerequisitesBody = functionBody(bootstrapPrerequisitesStartupChainSource(), "run")
        val repositoryBody = functionBody(repositoryInitializationStartupChainSource(), "run")

        assertTrue(
            "AppBootstrapper.bootstrap must not synchronously initialize TTS voice assets on the main startup path",
            !bootstrapBody.contains("TtsVoiceAssetRepository.initialize(application)"),
        )
        assertTrue(
            "BootstrapPrerequisitesStartupChain must own TTS warmup after bootstrap is thinned",
            prerequisitesBody.contains("warmUpTtsVoiceAssets()"),
        )
        assertEquals(
            "RepositoryInitializationStartupChain must wire ConfigRepositoryInitializer exactly once during phase-2 startup",
            1,
            "ConfigRepositoryInitializer()".toRegex(RegexOption.LITERAL).findAll(repositoryBody).count(),
        )
    }

    @Test
    fun bot_repository_initializer_runs_before_qq_bridge_server_start() {
        val repositoryBody = functionBody(repositoryInitializationStartupChainSource(), "run")
        val runtimeLaunchBody = functionBody(runtimeLaunchStartupChainSource(), "run")
        val startupRunnerBody = functionBody(appStartupRunnerSource(), "run")
        val botInitializerIndex = repositoryBody.indexOf("BotRepositoryInitializer()")
        val qqBridgeStartIndex = runtimeLaunchBody.indexOf("qqBridgeRuntime.start()")

        assertTrue(
            "RepositoryInitializationStartupChain must still wire BotRepositoryInitializer",
            botInitializerIndex >= 0,
        )
        assertTrue(
            "RuntimeLaunchStartupChain must still start the QQ bridge runtime",
            qqBridgeStartIndex >= 0,
        )
        assertTrue(
            "AppStartupRunner must invoke repository initialization before runtime launch",
            startupRunnerBody.indexOf("repositoryInitializationStartupChain.run()") <
                startupRunnerBody.indexOf("runtimeLaunchStartupChain.run()"),
        )
    }

    @Test
    fun bot_repository_initializer_shares_a_coordinator_with_config_initializer() {
        val startupBody = functionBody(repositoryInitializationStartupChainSource(), "run")
        val coordinatorBlocks = """InitializationCoordinator\(\s*listOf\((.*?)\)\s*,?\s*\)\.initializeAll\(application\)"""
            .toRegex(setOf(RegexOption.DOT_MATCHES_ALL))
            .findAll(startupBody)
            .map { it.groupValues[1] }
            .toList()

        val botBlocksWithoutConfig = coordinatorBlocks.filter { block ->
            block.contains("BotRepositoryInitializer()") && !block.contains("ConfigRepositoryInitializer()")
        }

        assertTrue(
            "BotRepositoryInitializer must be initialized in the same coordinator pass as ConfigRepositoryInitializer: $botBlocksWithoutConfig",
            botBlocksWithoutConfig.isEmpty(),
        )
    }

    @Test
    fun app_backup_wiring_uses_shared_production_port_with_durable_conversation_restore() {
        val startupMainline = startupMainlineSource()
        val dataPortSource = mainRoot.resolve("di/BackupDataPorts.kt").readText()
        val productionPortBody = objectBody(dataPortSource, "ProductionAppBackupDataPort")

        assertTrue(
            "Startup mainline must not install AppBackupDataPort manually once the production path is closed",
            !startupMainline.contains("createProductionAppBackupDataPort()"),
        )
        assertTrue(
            "Production AppBackupDataPort must route conversation restore through restoreSessionsDurable()",
            productionPortBody.contains("ConversationRepository.restoreSessionsDurable(sessions)"),
        )
    }

    @Test
    fun startup_blocking_phase2_repositories_do_not_use_runblocking_dispatchers_io() {
        val targetFiles = listOf(
            "feature/bot/data/FeatureBotRepository.kt",
            "feature/config/data/FeatureConfigRepository.kt",
            "core/runtime/audio/TtsVoiceAssetRepository.kt",
        )
        val offenders = targetFiles.mapNotNull { relativePath ->
            val source = mainRoot.resolve(relativePath).readText()
            if (source.contains("runBlocking(Dispatchers.IO)")) relativePath else null
        }

        assertTrue(
            "Phase-2 repositories must remove runBlocking(Dispatchers.IO) from initialization and persistence paths: $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun repository_selection_mutators_do_not_eagerly_assign_selected_state() {
        val botSelectBody = functionBody(
            mainRoot.resolve("feature/bot/data/FeatureBotRepository.kt").readText(),
            "select",
        )
        val configSelectBody = functionBody(
            mainRoot.resolve("feature/config/data/FeatureConfigRepository.kt").readText(),
            "select",
        )
        val configSaveBody = functionBody(
            mainRoot.resolve("feature/config/data/FeatureConfigRepository.kt").readText(),
            "save",
        )
        val configDeleteBody = functionBody(
            mainRoot.resolve("feature/config/data/FeatureConfigRepository.kt").readText(),
            "delete",
        )
        val configRestoreBody = functionBody(
            mainRoot.resolve("feature/config/data/FeatureConfigRepository.kt").readText(),
            "restoreProfiles",
        )

        assertTrue(
            "FeatureBotRepository.select must not directly assign selectedBotId before persistence flow catches up",
            !containsDirectAssignment(botSelectBody, "_selectedBotId.value"),
        )
        assertTrue(
            "FeatureBotRepository.select must not directly assign botProfile before persistence flow catches up",
            !containsDirectAssignment(botSelectBody, "_botProfile.value"),
        )
        assertTrue(
            "FeatureConfigRepository.select must not directly assign selectedProfileId before persistence flow catches up",
            !containsDirectAssignment(configSelectBody, "_selectedProfileId.value"),
        )
        assertTrue(
            "FeatureConfigRepository.save must not directly assign selectedProfileId before persistence flow catches up",
            !containsDirectAssignment(configSaveBody, "_selectedProfileId.value"),
        )
        assertTrue(
            "FeatureConfigRepository.save must not directly assign profiles before persistence flow catches up",
            !containsDirectAssignment(configSaveBody, "_profiles.value"),
        )
        assertTrue(
            "FeatureConfigRepository.delete must not directly assign selectedProfileId before persistence flow catches up",
            !containsDirectAssignment(configDeleteBody, "_selectedProfileId.value"),
        )
        assertTrue(
            "FeatureConfigRepository.delete must not directly assign profiles before persistence flow catches up",
            !containsDirectAssignment(configDeleteBody, "_profiles.value"),
        )
        assertTrue(
            "FeatureConfigRepository.restoreProfiles must not directly assign selectedProfileId before persistence flow catches up",
            !containsDirectAssignment(configRestoreBody, "_selectedProfileId.value"),
        )
        assertTrue(
            "FeatureConfigRepository.restoreProfiles must not directly assign profiles before persistence flow catches up",
            !containsDirectAssignment(configRestoreBody, "_profiles.value"),
        )
    }

    @Test
    fun initialization_coordinator_wiring_does_not_include_out_of_scope_initializers() {
        val startupBody = functionBody(repositoryInitializationStartupChainSource(), "run")

        val forbiddenInitializerTokens = listOf(
            "ConversationRepositoryInitializer",
            "RuntimeInitializer",
            "PlatformBridgeInitializer",
            "QQRepositoryInitializer",
            "QqRepositoryInitializer",
            "OneBotInitializer",
            "PluginRepositoryInitializer",
            "ToolSourceInitializer",
        )
        val violations = forbiddenInitializerTokens.filter(startupBody::contains)
        assertTrue(
            "RepositoryInitializationStartupChain must only wire Provider/Config/Persona/Bot initializers: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun feature_repository_imports_are_limited_to_legacy_adapters_and_initializers() {
        val featureDirs = listOf(
            "feature/provider",
            "feature/config",
            "feature/bot",
            "feature/persona",
        )
        val allowed = setOf(
            "feature/provider/data/LegacyProviderRepositoryAdapter.kt",
            "feature/provider/data/ProviderRepositoryInitializer.kt",
            "feature/config/data/LegacyConfigRepositoryAdapter.kt",
            "feature/config/data/ConfigRepositoryInitializer.kt",
            "feature/bot/data/LegacyBotRepositoryAdapter.kt",
            "feature/bot/data/BotRepositoryInitializer.kt",
            "feature/persona/data/LegacyPersonaRepositoryAdapter.kt",
            "feature/persona/data/PersonaRepositoryInitializer.kt",
        )
        val forbiddenRepositoryImports = listOf(
            "import com.astrbot.android.data.ProviderRepository",
            "import com.astrbot.android.data.ConfigRepository",
            "import com.astrbot.android.data.BotRepository",
            "import com.astrbot.android.data.PersonaRepository",
        )

        val violations = featureDirs.flatMap { dir ->
            kotlinFilesUnder(dir).flatMap { file ->
                val text = file.readText()
                val relative = mainRoot.relativize(file).toString().replace('\\', '/')
                if (relative in allowed) {
                    emptyList()
                } else {
                    forbiddenRepositoryImports.mapNotNull { forbidden ->
                        if (text.contains(forbidden)) "$relative imports $forbidden" else null
                    }
                }
            }
        }

        assertTrue(
            "Only legacy adapters and initializers may import legacy repository singletons: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun production_view_models_do_not_depend_on_phase1_dependency_facades() {
        val forbiddenByFile = listOf(
            "feature/bot/presentation/BotViewModel.kt" to "BotViewModelDependencies",
            "feature/config/presentation/ConfigViewModel.kt" to "ConfigViewModelDependencies",
            "feature/provider/presentation/ProviderViewModel.kt" to "ProviderViewModelDependencies",
            "feature/chat/presentation/ConversationViewModel.kt" to "ConversationViewModelDependencies",
            "feature/chat/presentation/ChatViewModel.kt" to "ChatViewModelDependencies",
            "feature/persona/presentation/PersonaViewModel.kt" to "PersonaViewModelDependencies",
            "feature/plugin/presentation/PluginViewModel.kt" to "PluginViewModelDependencies",
            "feature/qq/presentation/QQLoginViewModel.kt" to "QQLoginViewModelDependencies",
            "ui/viewmodel/BridgeViewModel.kt" to "BridgeViewModelDependencies",
            "ui/viewmodel/RuntimeAssetViewModel.kt" to "RuntimeAssetViewModelDependencies",
        )

        val dependencySource = mainRoot.resolve("di/AstrBotViewModelDependencies.kt")
            .takeIf { it.exists() }
            ?.readText()
            .orEmpty()
        val facadeViolations = forbiddenByFile.mapNotNull { (relativePath, forbiddenType) ->
            val source = mainRoot.resolve(relativePath).readText()
            if (source.contains(forbiddenType)) "$relativePath still references $forbiddenType" else null
        }
        val dependencyFileViolations = forbiddenByFile.mapNotNull { (_, forbiddenType) ->
            if (dependencySource.contains("interface $forbiddenType")) {
                "AstrBotViewModelDependencies.kt still declares $forbiddenType"
            } else {
                null
            }
        }
        val aggregateImportViolations = forbiddenByFile.mapNotNull { (relativePath, _) ->
            val source = mainRoot.resolve(relativePath).readText()
            if (source.contains("com.astrbot.android.di.AstrBotViewModelDependencies")) {
                "$relativePath imports AstrBotViewModelDependencies"
            } else {
                null
            }
        }
        val violations = facadeViolations + dependencyFileViolations + aggregateImportViolations

        assertTrue(
            "Production ViewModels must stop depending on phase-1 facade shells: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun production_sources_do_not_import_view_model_dependency_facade_contract_types() {
        val forbiddenImports = listOf(
            "import com.astrbot.android.di.Phase3DataTransactionService",
            "import com.astrbot.android.di.RoomPhase3DataTransactionService",
            "import com.astrbot.android.di.FeatureRepositoryPhase3DataTransactionService",
            "import com.astrbot.android.di.ChatViewModelRuntimeBindings",
            "import com.astrbot.android.di.AstrBotViewModelDependencies",
        )
        val violations = kotlinFilesUnder(".").flatMap { file ->
            val relative = mainRoot.relativize(file).toString().replace('\\', '/')
            val source = file.readText()
            forbiddenImports.mapNotNull { forbidden ->
                if (source.contains(forbidden)) "$relative imports $forbidden" else null
            }
        }

        assertTrue(
            "Production source must not import contracts from AstrBotViewModelDependencies.kt: $violations",
            violations.isEmpty(),
        )

        val facade = mainRoot.resolve("di/AstrBotViewModelDependencies.kt")
        if (facade.exists()) {
            val facadeSource = facade.readText()
            val forbiddenDeclarations = listOf(
                "interface Phase3DataTransactionService",
                "class RoomPhase3DataTransactionService",
                "object FeatureRepositoryPhase3DataTransactionService",
                "interface ChatViewModelRuntimeBindings",
            ).filter(facadeSource::contains)

            assertTrue(
                "AstrBotViewModelDependencies.kt must not declare production transaction or Chat runtime contracts: $forbiddenDeclarations",
                forbiddenDeclarations.isEmpty(),
            )
        }
    }

    @Test
    fun view_model_dependency_module_does_not_rebind_phase1_facades_into_hilt() {
        val source = mainRoot.resolve("di/hilt/ViewModelDependencyModule.kt").readText()
        val forbiddenTokens = listOf(
            "provideBridgeViewModelDependencies",
            "provideBotViewModelDependencies",
            "provideProviderViewModelDependencies",
            "provideConfigViewModelDependencies",
            "provideConversationViewModelDependencies",
            "providePersonaViewModelDependencies",
            "providePluginViewModelDependencies",
            "provideQqLoginViewModelDependencies",
            "provideRuntimeAssetViewModelDependencies",
            "provideChatViewModelDependencies",
            "HiltBridgeViewModelDependencies",
            "HiltBotViewModelDependencies",
            "HiltProviderViewModelDependencies",
            "HiltConfigViewModelDependencies",
            "HiltConversationViewModelDependencies",
            "HiltPersonaViewModelDependencies",
            "HiltPluginViewModelDependencies",
            "HiltQQLoginViewModelDependencies",
            "HiltRuntimeAssetViewModelDependencies",
            "HiltChatViewModelDependencies",
        )

        val violations = forbiddenTokens.filter(source::contains)

        assertTrue(
            "ViewModelDependencyModule must not rebind phase-1 facade shells into Hilt: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun chat_runtime_bindings_do_not_use_static_configure_callback_in_production_path() {
        val dependencySource = mainRoot.resolve("di/AstrBotViewModelDependencies.kt")
            .takeIf { it.exists() }
            ?.readText()
            .orEmpty()
        val contractSource = mainRoot.resolve("feature/chat/presentation/ChatViewModelRuntimeBindings.kt").readText()
        val bindingSource = mainRoot.resolve("di/hilt/DefaultChatViewModelRuntimeBindings.kt").readText()
        val moduleSource = mainRoot.resolve("di/hilt/ViewModelDependencyModule.kt").readText()

        assertTrue(
            "AstrBotViewModelDependencies.kt must not keep production Chat runtime binding implementations or plugin helper factories",
            !dependencySource.contains("HiltChatViewModelRuntimeBindings") &&
                !dependencySource.contains("defaultPluginInstaller(") &&
                !dependencySource.contains("defaultPluginCatalogSynchronizer(") &&
                !dependencySource.contains("defaultPluginRepositorySubscriptionManager(") &&
                !dependencySource.contains("createPluginInstallIntentHandler("),
        )
        assertTrue(
            "ChatViewModelRuntimeBindings must live with the chat presentation/runtime owner",
            contractSource.contains("interface ChatViewModelRuntimeBindings"),
        )
        assertTrue(
            "DefaultChatViewModelRuntimeBindings must not keep a static runtime binding callback in the production path",
            !bindingSource.contains("runtimeBindingsProvider") &&
                !bindingSource.contains("configureRuntimeBindingsProvider("),
        )
        assertTrue(
            "ViewModelDependencyModule must not configure Chat runtime bindings through a static callback",
            !moduleSource.contains("configureRuntimeBindingsProvider") &&
                !moduleSource.contains("HiltChatViewModelRuntimeBindings"),
        )
        assertTrue(
            "DefaultChatViewModelRuntimeBindings must delegate runtime/service construction to injected factories",
            !bindingSource.contains("AppChatRuntimeService(") &&
                !bindingSource.contains("AppChatProviderInvocationService(") &&
                !bindingSource.contains("AppChatPreparedReplyService(") &&
                !bindingSource.contains("\n        SendAppMessageUseCase(") &&
                !bindingSource.contains("AppChatSendHandler("),
        )
        assertTrue(
            "DefaultChatViewModelRuntimeBindings should depend on explicit runtime factories instead of hand-rolled service creation",
            bindingSource.contains("AppChatRuntimeServiceFactory") &&
                bindingSource.contains("SendAppMessageUseCaseFactory") &&
                bindingSource.contains("AppChatSendHandlerFactory"),
        )
        assertTrue(
            "ViewModelDependencyModule must bind ChatViewModelRuntimeBindings through Hilt instead of a direct provider shell",
            !moduleSource.contains("fun provideChatViewModelRuntimeBindings(") &&
                moduleSource.contains("abstract fun bindChatViewModelRuntimeBindings("),
        )
    }

    @Test
    fun plugin_provisioning_module_prefers_hilt_owned_business_services_over_direct_new() {
        val moduleSource = mainRoot.resolve("di/hilt/PluginProvisioningModule.kt").readText()
        val synchronizerSource = mainRoot.resolve("feature/plugin/runtime/catalog/PluginCatalogSynchronizer.kt").readText()
        val subscriptionSource = mainRoot.resolve("feature/plugin/runtime/catalog/PluginRepositorySubscriptionManager.kt").readText()
        val installerSource = mainRoot.resolve("feature/plugin/runtime/PluginInstaller.kt").readText()
        val handlerSource = mainRoot.resolve("feature/plugin/runtime/catalog/PluginInstallIntentHandler.kt").readText()

        assertTrue(
            "PluginProvisioningModule must stop directly constructing synchronizer/installer/handler business services",
            !moduleSource.contains("PluginCatalogSynchronizer(") &&
                !moduleSource.contains("PluginRepositorySubscriptionManager(") &&
                !moduleSource.contains("PluginInstaller(") &&
                !moduleSource.contains("PluginInstallIntentHandler("),
        )
        assertTrue(
            "Plugin provisioning business services should expose @Inject constructors for Hilt ownership",
            synchronizerSource.contains("@Inject constructor") &&
                subscriptionSource.contains("@Inject constructor") &&
                installerSource.contains("@Inject constructor") &&
                handlerSource.contains("@Inject constructor"),
        )
    }

    @Test
    fun repository_port_module_does_not_bind_production_ports_to_legacy_adapters() {
        val source = mainRoot.resolve("di/hilt/RepositoryPortModule.kt").readText()
        val forbiddenTokens = listOf(
            "LegacyBotRepositoryAdapter",
            "LegacyConfigRepositoryAdapter",
            "LegacyPersonaRepositoryAdapter",
            "LegacyProviderRepositoryAdapter",
            "LegacyConversationRepositoryAdapter",
            "LegacyQqConversationAdapter",
            "LegacyQqPlatformConfigAdapter",
        )

        val violations = forbiddenTokens.filter(source::contains)

        assertTrue(
            "RepositoryPortModule must bind production ports to Hilt-owned production boundaries, not legacy adapters: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun runtime_services_module_does_not_bind_core_services_to_legacy_compat_adapters() {
        val source = mainRoot.resolve("di/hilt/RuntimeServicesModule.kt").readText()
        val forbiddenTokens = listOf(
            "LegacyChatCompletionServiceAdapter",
            "LegacyRuntimeOrchestratorAdapter",
        )

        val violations = forbiddenTokens.filter(source::contains)

        assertTrue(
            "RuntimeServicesModule must bind core runtime services through Hilt-owned production services, not legacy compat adapters: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun production_runtime_mainline_does_not_use_static_dependency_provider_callbacks() {
        val startupMainline = startupMainlineSource()
        val scheduledTaskExecutorSource = mainRoot.resolve("feature/cron/runtime/ScheduledTaskRuntimeExecutor.kt").readText()
        val qqBridgeSource = mainRoot.resolve("feature/qq/runtime/QqOneBotBridgeServer.kt").readText()
        val forbiddenBootstrapCalls = listOf(
            "ScheduledTaskRuntimeExecutor.configureLlmClientProvider",
            "ScheduledTaskRuntimeExecutor.configureRuntimeDependenciesProvider",
            "QqOneBotBridgeServer.configureRuntimeDependenciesProvider",
        )
        val forbiddenRuntimeTokens = listOf(
            "runtimeDependenciesProvider",
            "llmClientProvider",
        )

        val bootstrapViolations = forbiddenBootstrapCalls.filter(startupMainline::contains)
        val runtimeViolations = forbiddenRuntimeTokens.flatMap { token ->
            listOf(
                "feature/cron/runtime/ScheduledTaskRuntimeExecutor.kt" to scheduledTaskExecutorSource,
                "feature/qq/runtime/QqOneBotBridgeServer.kt" to qqBridgeSource,
            ).mapNotNull { (path, source) ->
                if (source.contains(token)) "$path contains $token" else null
            }
        }
        val violations = bootstrapViolations + runtimeViolations

        assertTrue(
            "Runtime dependency selection must come from Hilt wiring, not static provider/configure callbacks: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun view_model_dependency_delete_shells_delegate_to_phase3_transaction_service() {
        val botViewModelSource = mainRoot.resolve("feature/bot/presentation/BotViewModel.kt").readText()
        val configViewModelSource = mainRoot.resolve("feature/config/presentation/ConfigViewModel.kt").readText()
        val configDeleteBody = functionBody(configViewModelSource, "delete")
        val moduleSource = mainRoot.resolve("di/hilt/ViewModelDependencyModule.kt").readText()

        assertTrue(
            "BotViewModel delete path must delegate to the injected phase-3 transaction service",
            botViewModelSource.contains("phase3DataTransactionService.deleteBotProfile(botId)"),
        )
        assertTrue(
            "BotViewModel must not keep hand-rolled conversation cleanup sequencing",
            !botViewModelSource.contains("ConversationRepository.deleteSessionsForBot(") &&
                !botViewModelSource.contains("ConversationRepository.restoreSessions(") &&
                !botViewModelSource.contains("BotRepository.delete(botId)"),
        )

        assertTrue(
            "ConfigViewModel delete path must delegate to the injected phase-3 transaction service",
            configDeleteBody.contains("phase3DataTransactionService.deleteConfigProfile(profileId)"),
        )
        assertTrue(
            "ConfigViewModel must not keep hand-rolled config delete / bot rebind sequencing",
            !configDeleteBody.contains("BotRepository.replaceConfigBinding(") &&
                !configDeleteBody.contains("ConfigRepository.delete(profileId)"),
        )
        assertTrue(
            "ViewModelDependencyModule must bind the production phase-3 transaction service through Hilt",
            moduleSource.contains("abstract fun bindPhase3DataTransactionService(") &&
                !moduleSource.contains("RoomPhase3DataTransactionService(database)"),
        )
        assertTrue(
            "RoomPhase3DataTransactionService should expose an @Inject constructor so Hilt owns the implementation",
            mainRoot.resolve("feature/config/data/RoomPhase3DataTransactionService.kt")
                .readText()
                .contains("@Inject constructor"),
        )
    }

    @Test
    fun phase3_delete_transaction_service_is_suspend_and_does_not_use_runblocking() {
        val source = mainRoot.resolve("feature/config/domain/Phase3DataTransactionService.kt").readText()
        val implementationSource = mainRoot.resolve("feature/config/data/RoomPhase3DataTransactionService.kt").readText()
        val serviceBody = interfaceBody(source, "Phase3DataTransactionService")

        assertTrue(
            "Phase3DataTransactionService.deleteConfigProfile must be suspend",
            serviceBody.contains("suspend fun deleteConfigProfile(profileId: String)"),
        )
        assertTrue(
            "Phase3DataTransactionService.deleteBotProfile must be suspend",
            serviceBody.contains("suspend fun deleteBotProfile(botId: String)"),
        )
        assertTrue(
            "Phase3 delete transaction service must not use runBlocking in the service file",
            !source.contains("runBlocking") && !implementationSource.contains("runBlocking"),
        )
    }

    @Test
    fun phase3_bootstrap_does_not_manually_install_transaction_or_qq_runtime_dependencies() {
        val startupMainline = startupMainlineSource()

        assertTrue(
            "Startup mainline must not install Phase3 transaction services manually once Hilt owns the production path",
            !startupMainline.contains("installProductionPhase3DataTransactionService("),
        )
        assertTrue(
            "Startup mainline must not manually install QQ runtime dependencies once Hilt owns the production path",
            !startupMainline.contains("QqOneBotBridgeServer.installRuntimeDependencies("),
        )
        assertTrue(
            "Startup mainline must not call QQ bridge test override hooks on the production path",
            !startupMainline.contains("QqOneBotBridgeServer.setAppChatPluginRuntimeOverrideForTests("),
        )
    }

    @Test
    fun phase3_transaction_service_must_not_come_from_a_registry_service_locator() {
        val dependencySource = mainRoot.resolve("di/AstrBotViewModelDependencies.kt")
            .takeIf { it.exists() }
            ?.readText()
            .orEmpty()
        val moduleSource = mainRoot.resolve("di/hilt/ViewModelDependencyModule.kt").readText()

        assertTrue(
            "AstrBotViewModelDependencies.kt must not keep Phase3DataTransactionServiceRegistry in the production path",
            !dependencySource.contains("Phase3DataTransactionServiceRegistry"),
        )
        assertTrue(
            "ViewModelDependencyModule must not source Phase3DataTransactionService from a registry service locator",
            !moduleSource.contains("Phase3DataTransactionServiceRegistry"),
        )
    }

    @Test
    fun phase3_bootstrap_must_not_install_registry_ports_for_runtime_backup_or_container_state() {
        val startupMainline = startupMainlineSource()
        val forbiddenAssignments = listOf(
            "RuntimeContextDataRegistry.port =",
            "ContainerBridgeStateRegistry.port =",
            "ConversationBackupDataRegistry.port =",
            "AppBackupDataRegistry.port =",
        )

        val violations = forbiddenAssignments.filter(startupMainline::contains)

        assertTrue(
            "Startup mainline must not install production registry ports once Hilt owns DI closure: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun runtime_context_resolution_in_production_must_not_depend_on_registry_service_locator() {
        val runtimeContextPortSource = mainRoot.resolve("core/runtime/context/RuntimeContextDataPort.kt").readText()
        val runtimeContextResolverSource = mainRoot.resolve("core/runtime/context/RuntimeContextResolver.kt").readText()
        val appChatRuntimeSource = mainRoot.resolve("feature/chat/runtime/AppChatRuntimeService.kt").readText()
        val scheduledTaskRuntimeSource = mainRoot.resolve("feature/cron/runtime/ScheduledTaskRuntimeExecutor.kt").readText()
        val qqRuntimeSource = mainRoot.resolve("feature/qq/runtime/QqMessageRuntimeService.kt").readText()

        assertTrue(
            "RuntimeContextDataPort production seam must not keep RuntimeContextDataRegistry service locator",
            !runtimeContextPortSource.contains("object RuntimeContextDataRegistry"),
        )
        assertTrue(
            "RuntimeContextResolver must not read RuntimeContextDataRegistry in the production path",
            !runtimeContextResolverSource.contains("RuntimeContextDataRegistry"),
        )

        val serviceViolations = listOf(
            "feature/chat/runtime/AppChatRuntimeService.kt" to appChatRuntimeSource,
            "feature/cron/runtime/ScheduledTaskRuntimeExecutor.kt" to scheduledTaskRuntimeSource,
            "feature/qq/runtime/QqMessageRuntimeService.kt" to qqRuntimeSource,
        ).mapNotNull { (path, source) ->
            if (source.contains("RuntimeContextResolver.resolve(")) "$path still calls RuntimeContextResolver directly" else null
        }

        assertTrue(
            "Production runtime entry points must resolve context through an injected seam instead of a static resolver: $serviceViolations",
            serviceViolations.isEmpty(),
        )
    }

    @Test
    fun runtime_backup_and_container_mainline_must_not_depend_on_registry_service_locators() {
        val targetFiles = listOf(
            "core/db/backup/AppBackupDataPort.kt",
            "core/db/backup/AppBackupRepository.kt",
            "core/db/backup/ConversationBackupDataPort.kt",
            "core/db/backup/ConversationBackupRepository.kt",
            "core/runtime/container/ContainerBridgeStatePort.kt",
            "core/runtime/container/ContainerBridgeController.kt",
            "core/runtime/container/ContainerBridgeService.kt",
            "core/runtime/container/ContainerRuntimeInstaller.kt",
        )
        val forbiddenTokens = listOf(
            "AppBackupDataRegistry",
            "ConversationBackupDataRegistry",
            "ContainerBridgeStateRegistry",
        )

        val violations = targetFiles.flatMap { relativePath ->
            val source = mainRoot.resolve(relativePath).readText()
            forbiddenTokens.mapNotNull { token ->
                if (source.contains(token)) "$relativePath contains $token" else null
            }
        }

        assertTrue(
            "Backup/container production mainline must not read registry service locators: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun scheduled_task_runtime_must_not_call_qq_bridge_through_static_singleton() {
        val source = mainRoot.resolve("feature/cron/runtime/ScheduledTaskLlmCallbacksFactory.kt").readText()

        assertTrue(
            "ScheduledTaskLlmCallbacksFactory must use an injected QQ sender, not QqOneBotBridgeServer singleton",
            !source.contains("QqOneBotBridgeServer."),
        )
    }

    @Test
    fun persona_port_exposes_designed_tool_enablement_and_explicit_enabled_semantics() {
        val port = mainRoot.resolve("feature/persona/domain/PersonaRepositoryPort.kt").readText()
        val adapter = mainRoot.resolve("feature/persona/data/LegacyPersonaRepositoryAdapter.kt").readText()

        assertTrue(
            "PersonaRepositoryPort must expose all persona tool enablement snapshots",
            port.contains("fun snapshotToolEnablement(): List<PersonaToolEnablementSnapshot>"),
        )
        assertTrue(
            "PersonaRepositoryPort must preserve lookup by persona id for existing callers",
            port.contains("fun snapshotToolEnablement(personaId: String): PersonaToolEnablementSnapshot?"),
        )
        assertTrue(
            "PersonaRepositoryPort must expose explicit enabled state updates",
            port.contains("suspend fun toggleEnabled(id: String, enabled: Boolean)"),
        )
        assertTrue(
            "PersonaRepositoryPort must preserve existing toggle semantics for existing callers",
            port.contains("suspend fun toggleEnabled(id: String)"),
        )
        assertTrue(
            "LegacyPersonaRepositoryAdapter must implement the all-snapshot port API",
            adapter.contains("override fun snapshotToolEnablement(): List<PersonaToolEnablementSnapshot>"),
        )
        assertTrue(
            "LegacyPersonaRepositoryAdapter must implement explicit enabled updates",
            adapter.contains("override suspend fun toggleEnabled(id: String, enabled: Boolean)"),
        )
    }

    private fun kotlinFilesUnder(relative: String): List<Path> {
        val root = mainRoot.resolve(relative)
        if (!root.exists()) return emptyList()
        return Files.walk(root).use { stream ->
            stream
                .filter { it.isRegularFile() && it.fileName.toString().endsWith(".kt") }
                .toList()
        }
    }

    private fun functionBody(source: String, functionName: String): String {
        val signatureIndex = source.indexOf("fun $functionName(")
        assertNotEquals("Missing function: $functionName", -1, signatureIndex)
        val bodyStart = source.indexOf('{', signatureIndex)
        assertNotEquals("Missing body for function: $functionName", -1, bodyStart)
        val bodyEnd = matchingBraceIndex(source, bodyStart)
        return source.substring(bodyStart + 1, bodyEnd)
    }

    private fun appBootstrapperSource(): String {
        val file = mainRoot.resolve("di/AppBootstrapper.kt")
        assertTrue("AppBootstrapper.kt must exist", file.exists())
        return file.readText()
    }

    private fun appStartupRunnerSource(): String {
        return startupSource("AppStartupRunner.kt")
    }

    private fun bootstrapPrerequisitesStartupChainSource(): String {
        return startupSource("BootstrapPrerequisitesStartupChain.kt")
    }

    private fun repositoryInitializationStartupChainSource(): String {
        return startupSource("RepositoryInitializationStartupChain.kt")
    }

    private fun runtimeLaunchStartupChainSource(): String {
        return startupSource("RuntimeLaunchStartupChain.kt")
    }

    private fun startupMainlineSource(): String {
        val startupFiles = listOf(
            "AppStartupChain.kt",
            "AppStartupRunner.kt",
            "BootstrapPrerequisitesStartupChain.kt",
            "RepositoryInitializationStartupChain.kt",
            "ReferenceGuardStartupChain.kt",
            "PluginRuntimeObservationStartupChain.kt",
            "RuntimeLaunchStartupChain.kt",
        )
        return buildString {
            append(appBootstrapperSource())
            startupFiles.forEach { fileName ->
                append('\n')
                append(startupSource(fileName))
            }
        }
    }

    private fun startupSource(fileName: String): String {
        val file = mainRoot.resolve("di/startup/$fileName")
        assertTrue("$fileName must exist under di/startup", file.exists())
        return file.readText()
    }

    private fun objectBody(source: String, objectName: String): String {
        val signatureIndex = source.indexOf("object $objectName")
        assertNotEquals("Missing object: $objectName", -1, signatureIndex)
        val bodyStart = source.indexOf('{', signatureIndex)
        assertNotEquals("Missing body for object: $objectName", -1, bodyStart)
        val bodyEnd = matchingBraceIndex(source, bodyStart)
        return source.substring(bodyStart + 1, bodyEnd)
    }

    private fun classBody(source: String, className: String): String {
        val signatureIndex = source.indexOf("class $className")
        assertNotEquals("Missing class: $className", -1, signatureIndex)
        val bodyStart = source.indexOf('{', signatureIndex)
        assertNotEquals("Missing body for class: $className", -1, bodyStart)
        val bodyEnd = matchingBraceIndex(source, bodyStart)
        return source.substring(bodyStart + 1, bodyEnd)
    }

    private fun interfaceBody(source: String, interfaceName: String): String {
        val signatureIndex = source.indexOf("interface $interfaceName")
        assertNotEquals("Missing interface: $interfaceName", -1, signatureIndex)
        val bodyStart = source.indexOf('{', signatureIndex)
        assertNotEquals("Missing body for interface: $interfaceName", -1, bodyStart)
        val bodyEnd = matchingBraceIndex(source, bodyStart)
        return source.substring(bodyStart + 1, bodyEnd)
    }

    private fun containsDirectAssignment(source: String, lhs: String): Boolean {
        val pattern = Regex("""${Regex.escape(lhs)}\s*=\s*[^=]""")
        return pattern.containsMatchIn(source)
    }

    private fun matchingBraceIndex(source: String, openIndex: Int): Int {
        return matchingIndex(source, openIndex, '{', '}')
    }

    private fun matchingIndex(source: String, openIndex: Int, open: Char, close: Char): Int {
        var depth = 0
        for (index in openIndex until source.length) {
            when (source[index]) {
                open -> depth += 1
                close -> {
                    depth -= 1
                    if (depth == 0) return index
                }
            }
        }
        throw AssertionError("No matching '$close' found")
    }
}
