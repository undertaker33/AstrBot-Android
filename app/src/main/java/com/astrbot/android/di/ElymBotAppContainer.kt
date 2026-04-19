package com.astrbot.android.di

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.astrbot.android.AppStrings
import com.astrbot.android.core.di.InitializationCoordinator
import com.astrbot.android.core.db.backup.AppBackupDataPort
import com.astrbot.android.core.db.backup.AppBackupDataRegistry
import com.astrbot.android.core.db.backup.AppBackupExternalState
import com.astrbot.android.core.db.backup.AppBackupRepository
import com.astrbot.android.core.db.backup.ConversationBackupDataPort
import com.astrbot.android.core.db.backup.ConversationBackupDataRegistry
import com.astrbot.android.core.db.backup.ConversationImportPreview
import com.astrbot.android.core.db.backup.ConversationImportResult
import com.astrbot.android.core.runtime.llm.ChatCompletionService
import com.astrbot.android.core.db.backup.ConversationBackupRepository
import com.astrbot.android.core.runtime.container.ContainerBridgeStatePort
import com.astrbot.android.core.runtime.container.ContainerBridgeStateRegistry
import com.astrbot.android.feature.chat.data.FeatureConversationRepository as ConversationRepository
import com.astrbot.android.feature.bot.data.FeatureBotRepository as BotRepository
import com.astrbot.android.feature.config.data.FeatureConfigRepository as ConfigRepository
import com.astrbot.android.feature.cron.data.FeatureCronJobRepository as CronJobRepository
import com.astrbot.android.feature.qq.data.NapCatBridgeRepository
import com.astrbot.android.feature.qq.data.NapCatLoginRepository
import com.astrbot.android.feature.persona.data.FeaturePersonaRepository as PersonaRepository
import com.astrbot.android.feature.plugin.data.FeaturePluginRepository as PluginRepository
import com.astrbot.android.feature.provider.data.FeatureProviderRepository as ProviderRepository
import com.astrbot.android.feature.resource.data.FeatureResourceCenterRepository as ResourceCenterRepository
import com.astrbot.android.data.RuntimeAssetRepository
import com.astrbot.android.core.runtime.audio.SherpaOnnxBridge
import com.astrbot.android.core.runtime.audio.TtsVoiceAssetRepository
import com.astrbot.android.download.AppDownloadManager
import com.astrbot.android.feature.bot.data.BotRepositoryInitializer
import com.astrbot.android.feature.config.data.ConfigRepositoryInitializer
import com.astrbot.android.feature.persona.data.PersonaRepositoryInitializer
import com.astrbot.android.feature.provider.data.ProviderRepositoryInitializer
import com.astrbot.android.core.runtime.container.ContainerRuntimeInstaller
import com.astrbot.android.feature.qq.runtime.QqOneBotBridgeServer
import com.astrbot.android.feature.qq.runtime.QqOneBotRuntimeDependencies
import com.astrbot.android.core.common.logging.RuntimeLogRepository
import com.astrbot.android.core.runtime.secret.RuntimeSecretRepository
import com.astrbot.android.core.runtime.audio.TencentSilkEncoder
import com.astrbot.android.core.runtime.context.RuntimeContextDataPort
import com.astrbot.android.core.runtime.context.RuntimeContextDataRegistry
import com.astrbot.android.feature.bot.data.LegacyBotRepositoryAdapter
import com.astrbot.android.feature.config.data.LegacyConfigRepositoryAdapter
import com.astrbot.android.feature.persona.data.LegacyPersonaRepositoryAdapter
import com.astrbot.android.feature.plugin.runtime.PluginRuntimeLogCleanupRepository
import com.astrbot.android.feature.plugin.runtime.ExternalPluginRuntimeCatalog
import com.astrbot.android.feature.plugin.runtime.DefaultAppChatPluginRuntime
import com.astrbot.android.feature.plugin.runtime.PluginV2LifecycleManager
import com.astrbot.android.feature.plugin.runtime.PluginV2LifecycleManagerProvider
import com.astrbot.android.feature.plugin.runtime.PluginV2RuntimeLoaderProvider
import com.astrbot.android.feature.plugin.runtime.PluginV2RuntimeSyncResult
import com.astrbot.android.feature.plugin.runtime.PluginRuntimeRegistry
import com.astrbot.android.feature.plugin.runtime.DefaultRuntimeLlmOrchestrator
import com.astrbot.android.feature.plugin.runtime.RuntimeLlmOrchestratorPort
import com.astrbot.android.feature.provider.data.LegacyProviderRepositoryAdapter
import com.astrbot.android.feature.plugin.runtime.toolsource.ActiveCapabilityToolSourceProvider
import com.astrbot.android.feature.cron.runtime.CronJobExecutionBridge
import com.astrbot.android.feature.cron.runtime.CronJobScheduler
import com.astrbot.android.feature.cron.runtime.ScheduledTaskRuntimeDependencies
import com.astrbot.android.feature.cron.runtime.ScheduledTaskRuntimeExecutor
import com.astrbot.android.feature.chat.data.LegacyConversationRepositoryAdapter
import com.astrbot.android.feature.qq.data.LegacyQqConversationAdapter
import com.astrbot.android.feature.qq.data.LegacyQqPlatformConfigAdapter
import com.astrbot.android.feature.qq.runtime.DefaultQqProviderInvoker
import com.astrbot.android.runtime.llm.LegacyChatCompletionServiceAdapter
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.ui.viewmodel.BotViewModel
import com.astrbot.android.ui.viewmodel.BridgeViewModel
import com.astrbot.android.ui.viewmodel.ChatViewModel
import com.astrbot.android.ui.viewmodel.ConfigViewModel
import com.astrbot.android.ui.viewmodel.ConversationViewModel
import com.astrbot.android.ui.viewmodel.PersonaViewModel
import com.astrbot.android.ui.viewmodel.PluginViewModel
import com.astrbot.android.ui.viewmodel.ProviderViewModel
import com.astrbot.android.ui.viewmodel.QQLoginViewModel
import com.astrbot.android.ui.viewmodel.RuntimeAssetViewModel
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ElymBotAppContainer(
    private val application: Application,
) {
    private val bootstrapped = AtomicBoolean(false)
    private var pluginRuntimeLoaderSyncJob: Job? = null
    private val runtimeLlmOrchestrator: RuntimeLlmOrchestratorPort by lazy {
        DefaultRuntimeLlmOrchestrator()
    }
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        RuntimeLogRepository.append(
            "App scope uncaught exception: ${throwable.message ?: throwable.javaClass.simpleName}",
        )
    }

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + exceptionHandler)

    val bridgeViewModelDependencies: BridgeViewModelDependencies = DefaultBridgeViewModelDependencies
    val botViewModelDependencies: BotViewModelDependencies = DefaultBotViewModelDependencies
    val providerViewModelDependencies: ProviderViewModelDependencies = DefaultProviderViewModelDependencies
    val configViewModelDependencies: ConfigViewModelDependencies = DefaultConfigViewModelDependencies
    val conversationViewModelDependencies: ConversationViewModelDependencies = DefaultConversationViewModelDependencies
    val personaViewModelDependencies: PersonaViewModelDependencies = DefaultPersonaViewModelDependencies
    val pluginViewModelDependencies: PluginViewModelDependencies = DefaultPluginViewModelDependencies
    val qqLoginViewModelDependencies: QQLoginViewModelDependencies = DefaultQQLoginViewModelDependencies
    val chatViewModelDependencies: ChatViewModelDependencies = DefaultChatViewModelDependencies
    val mainActivityDependencies: MainActivityDependencies = DefaultMainActivityDependencies
    val runtimeAssetViewModelDependencies: RuntimeAssetViewModelDependencies by lazy {
        DefaultRuntimeAssetViewModelDependencies(application.applicationContext)
    }

    val viewModelFactory: ViewModelProvider.Factory = ElymBotViewModelFactory(this, application)

    // Keep the config coordinator seam before ResourceCenterRepository startup.
    fun bootstrap() {
        if (!bootstrapped.compareAndSet(false, true)) return
        Log.i("AstrBotRuntime", "ElymBotAppContainer.bootstrap entered")

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            RuntimeLogRepository.append(
                "App uncaught exception: thread=${thread.name} reason=${throwable.message ?: throwable.javaClass.simpleName}",
            )
        }

        AppStrings.initialize(application)
        RuntimeSecretRepository.initialize(application)
        ChatCompletionService.initialize(application)
        ScheduledTaskRuntimeExecutor.configureLlmClientProvider {
            LegacyChatCompletionServiceAdapter()
        }
        ScheduledTaskRuntimeExecutor.configureRuntimeDependenciesProvider {
            ScheduledTaskRuntimeDependencies(
                botPort = LegacyBotRepositoryAdapter(),
                conversationPort = LegacyConversationRepositoryAdapter(),
                orchestrator = runtimeLlmOrchestrator,
            )
        }
        QqOneBotBridgeServer.configureRuntimeDependenciesProvider {
            QqOneBotRuntimeDependencies(
                botPort = LegacyBotRepositoryAdapter(),
                configPort = LegacyConfigRepositoryAdapter(),
                personaPort = LegacyPersonaRepositoryAdapter(),
                providerPort = LegacyProviderRepositoryAdapter(),
                conversationPort = LegacyQqConversationAdapter(),
                platformConfigPort = LegacyQqPlatformConfigAdapter(),
                orchestrator = runtimeLlmOrchestrator,
                providerInvoker = DefaultQqProviderInvoker(LegacyChatCompletionServiceAdapter()),
            )
        }
        QqOneBotBridgeServer.initialize(application)
        QqOneBotBridgeServer.setAppChatPluginRuntimeOverrideForTests(DefaultAppChatPluginRuntime)
        TencentSilkEncoder.initialize(application)
        appScope.launch(Dispatchers.IO) {
            AppDownloadManager.initialize(application)
        }
        NapCatBridgeRepository.initialize(application)
        NapCatLoginRepository.initialize(application)
        RuntimeAssetRepository.initialize(application)
        SherpaOnnxBridge.initialize(application)
        TtsVoiceAssetRepository.initialize(application)
        InitializationCoordinator(
            listOf(
                ConfigRepositoryInitializer(),
            ),
        ).initializeAll(application)
        ActiveCapabilityToolSourceProvider.initialize(application)
        CronJobRepository.initialize(application)
        ResourceCenterRepository.initialize(application)
        CronJobExecutionBridge.instance = CronJobExecutionBridge { context ->
            ScheduledTaskRuntimeExecutor.execute(context)
        }
        InitializationCoordinator(
            listOf(
                ProviderRepositoryInitializer(),
                PersonaRepositoryInitializer(),
            ),
        ).initializeAll(application)
        ConversationRepository.initialize(application)
        PluginRepository.initialize(application)
        RuntimeContextDataRegistry.port = object : RuntimeContextDataPort {
            override fun resolveConfig(configProfileId: String) = ConfigRepository.resolve(configProfileId)

            override fun listProviders() = ProviderRepository.providers.value

            override fun findEnabledPersona(personaId: String) =
                PersonaRepository.personas.value.firstOrNull { it.id == personaId && it.enabled }

            override fun session(sessionId: String) = ConversationRepository.session(sessionId)

            override fun compatibilitySnapshotForConfig(config: com.astrbot.android.model.ConfigProfile) =
                ResourceCenterRepository.compatibilitySnapshotForConfig(config)
        }
        ContainerBridgeStateRegistry.port = object : ContainerBridgeStatePort {
            override val config = NapCatBridgeRepository.config
            override val runtimeState = NapCatBridgeRepository.runtimeState

            override fun applyRuntimeDefaults(defaults: com.astrbot.android.model.NapCatBridgeConfig) {
                NapCatBridgeRepository.applyRuntimeDefaults(defaults)
            }

            override fun markStarting() = NapCatBridgeRepository.markStarting()

            override fun markRunning(pidHint: String, details: String) {
                NapCatBridgeRepository.markRunning(pidHint = pidHint, details = details)
            }

            override fun markProcessRunning(pidHint: String, details: String) {
                NapCatBridgeRepository.markProcessRunning(pidHint = pidHint, details = details)
            }

            override fun markStopped(reason: String) = NapCatBridgeRepository.markStopped(reason)

            override fun markChecking() = NapCatBridgeRepository.markChecking()

            override fun markError(message: String) = NapCatBridgeRepository.markError(message)

            override fun updateProgress(label: String, percent: Int, indeterminate: Boolean, installerCached: Boolean) {
                NapCatBridgeRepository.updateProgress(
                    label = label,
                    percent = percent,
                    indeterminate = indeterminate,
                    installerCached = installerCached,
                )
            }

            override fun markInstallerCached(cached: Boolean) = NapCatBridgeRepository.markInstallerCached(cached)
        }
        ConversationBackupDataRegistry.port = object : ConversationBackupDataPort {
            override val isReady = ConversationRepository.isReady
            override val sessions = ConversationRepository.sessions
            override val defaultSessionTitle: String = ConversationRepository.DEFAULT_SESSION_TITLE

            override fun selectedBotId(): String = BotRepository.selectedBotId.value

            override fun snapshotSessions() = ConversationRepository.snapshotSessions()

            override fun restoreSessions(restoredSessions: List<com.astrbot.android.model.chat.ConversationSession>) {
                ConversationRepository.restoreSessions(restoredSessions)
            }

            override fun previewImportedSessions(
                importedSessions: List<com.astrbot.android.model.chat.ConversationSession>,
            ): ConversationImportPreview {
                val preview = ConversationRepository.previewImportedSessions(importedSessions)
                return ConversationImportPreview(
                    totalSessions = preview.totalSessions,
                    duplicateSessions = preview.duplicateSessions,
                    newSessions = preview.newSessions,
                )
            }

            override fun importSessions(
                importedSessions: List<com.astrbot.android.model.chat.ConversationSession>,
                overwriteDuplicates: Boolean,
            ): ConversationImportResult {
                val result = ConversationRepository.importSessions(
                    importedSessions = importedSessions,
                    overwriteDuplicates = overwriteDuplicates,
                )
                return ConversationImportResult(
                    importedCount = result.importedCount,
                    overwrittenCount = result.overwrittenCount,
                    skippedCount = result.skippedCount,
                )
            }
        }
        AppBackupDataRegistry.port = object : AppBackupDataPort {
            override fun snapshotBots() = BotRepository.snapshotProfiles()

            override fun snapshotProviders() = ProviderRepository.snapshotProfiles()

            override fun snapshotPersonas() = PersonaRepository.snapshotProfiles()

            override fun snapshotConfigs() = ConfigRepository.snapshotProfiles()

            override fun snapshotConversations() = ConversationRepository.snapshotSessions()

            override fun snapshotExternalState(): AppBackupExternalState {
                val loginState = NapCatLoginRepository.loginState.value
                return AppBackupExternalState(
                    selectedBotId = BotRepository.selectedBotId.value,
                    selectedConfigId = ConfigRepository.selectedProfileId.value,
                    quickLoginUin = loginState.quickLoginUin,
                    savedAccounts = loginState.savedAccounts,
                )
            }

            override suspend fun restoreBots(
                profiles: List<com.astrbot.android.model.BotProfile>,
                selectedBotId: String,
            ) {
                BotRepository.restoreProfiles(profiles, selectedBotId)
            }

            override fun restoreProviders(profiles: List<com.astrbot.android.model.ProviderProfile>) {
                ProviderRepository.restoreProfiles(profiles)
            }

            override fun restorePersonas(profiles: List<com.astrbot.android.model.PersonaProfile>) {
                PersonaRepository.restoreProfiles(profiles)
            }

            override fun restoreConfigs(
                profiles: List<com.astrbot.android.model.ConfigProfile>,
                selectedConfigId: String,
            ) {
                ConfigRepository.restoreProfiles(profiles, selectedConfigId)
            }

            override fun restoreConversations(sessions: List<com.astrbot.android.model.chat.ConversationSession>) {
                ConversationRepository.restoreSessions(sessions)
            }

            override fun restoreQqLoginState(
                quickLoginUin: String,
                savedAccounts: List<com.astrbot.android.model.SavedQqAccount>,
            ) {
                NapCatLoginRepository.restoreSavedLoginState(
                    quickLoginUin = quickLoginUin,
                    savedAccounts = savedAccounts,
                )
            }
        }
        CronJobScheduler.initialize(application)
        PluginRuntimeLogCleanupRepository.initialize(application)
        PluginRuntimeRegistry.registerExternalProvider {
            ExternalPluginRuntimeCatalog.plugins()
        }
        pluginRuntimeLoaderSyncJob = appScope.observePluginRuntimeRecords(
            records = PluginRepository.records,
            sync = { currentRecords ->
                syncPluginRuntimeRecordsAndSignalReady(
                    records = currentRecords,
                    loader = PluginV2RuntimeLoaderProvider.loader(),
                    lifecycleManager = PluginV2LifecycleManagerProvider.manager(),
                )
            },
        )
        ConversationBackupRepository.initialize(application)
        AppBackupRepository.initialize(application)
        QqOneBotBridgeServer.start()
        appScope.launch(Dispatchers.IO) {
            InitializationCoordinator(
                listOf(
                    ConfigRepositoryInitializer(),
                    BotRepositoryInitializer(),
                ),
            ).initializeAll(application)
        }
        ContainerRuntimeInstaller.warmUpAsync(application, appScope)
        RuntimeLogRepository.append("App started")
        Log.i("AstrBotRuntime", "ElymBotAppContainer.bootstrap completed")
    }
}

internal suspend fun syncPluginRuntimeRecordsAndSignalReady(
    records: List<PluginInstallRecord>,
    loader: com.astrbot.android.feature.plugin.runtime.PluginV2RuntimeLoader,
    lifecycleManager: PluginV2LifecycleManager,
    platformInstanceKey: String = ANDROID_PLATFORM_INSTANCE_KEY,
): PluginV2RuntimeSyncResult {
    return loader.sync(records).also {
        lifecycleManager.onAstrbotLoaded()
        lifecycleManager.onPlatformLoaded(platformInstanceKey)
    }
}

internal const val ANDROID_PLATFORM_INSTANCE_KEY: String = "astrbot-android"

internal fun CoroutineScope.observePluginRuntimeRecords(
    records: StateFlow<List<PluginInstallRecord>>,
    sync: suspend (List<PluginInstallRecord>) -> PluginV2RuntimeSyncResult,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
): Job {
    return launch(dispatcher) {
        records.collectLatest { currentRecords ->
            try {
                sync(currentRecords)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                RuntimeLogRepository.append(
                    "Plugin v2 runtime loader sync failed: ${error.message ?: error.javaClass.simpleName}",
                )
            }
        }
    }
}

private class ElymBotViewModelFactory(
    private val container: ElymBotAppContainer,
    private val application: Application,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when (modelClass) {
            BridgeViewModel::class.java -> BridgeViewModel(container.bridgeViewModelDependencies) as T
            BotViewModel::class.java -> BotViewModel(container.botViewModelDependencies) as T
            ProviderViewModel::class.java -> ProviderViewModel(container.providerViewModelDependencies) as T
            ConfigViewModel::class.java -> ConfigViewModel(container.configViewModelDependencies) as T
            ConversationViewModel::class.java -> ConversationViewModel(container.conversationViewModelDependencies) as T
            PersonaViewModel::class.java -> PersonaViewModel(container.personaViewModelDependencies) as T
            PluginViewModel::class.java -> PluginViewModel(container.pluginViewModelDependencies) as T
            QQLoginViewModel::class.java -> QQLoginViewModel(container.qqLoginViewModelDependencies) as T
            ChatViewModel::class.java -> ChatViewModel(container.chatViewModelDependencies) as T
            RuntimeAssetViewModel::class.java -> RuntimeAssetViewModel(
                application = application,
                dependencies = container.runtimeAssetViewModelDependencies,
            ) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

