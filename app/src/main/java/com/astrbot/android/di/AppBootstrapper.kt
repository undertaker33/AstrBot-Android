@file:Suppress("DEPRECATION")

package com.astrbot.android.di

import android.app.Application
import android.util.Log
import com.astrbot.android.AppStrings
import com.astrbot.android.core.di.InitializationCoordinator
import com.astrbot.android.core.db.backup.AppBackupRepository
import com.astrbot.android.core.runtime.llm.ChatCompletionService
import com.astrbot.android.core.db.backup.ConversationBackupRepository
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
import com.astrbot.android.feature.provider.data.ProviderRepositoryWarmup
import com.astrbot.android.core.runtime.container.ContainerRuntimeInstaller
import com.astrbot.android.feature.qq.runtime.QqBridgeRuntime
import com.astrbot.android.core.common.logging.RuntimeLogRepository
import com.astrbot.android.core.runtime.secret.RuntimeSecretRepository
import com.astrbot.android.core.runtime.audio.TencentSilkEncoder
import com.astrbot.android.feature.plugin.runtime.PluginRuntimeLogCleanupRepository
import com.astrbot.android.feature.plugin.runtime.PluginV2RuntimeLoader
import com.astrbot.android.feature.plugin.runtime.PluginV2LifecycleManager
import com.astrbot.android.feature.plugin.runtime.PluginV2RuntimeSyncResult
import com.astrbot.android.feature.plugin.runtime.toolsource.ActiveCapabilityToolSourceProvider
import com.astrbot.android.feature.cron.runtime.CronJobScheduler
import com.astrbot.android.core.common.profile.PersonaReferenceGuard
import com.astrbot.android.core.common.profile.ProviderReferenceGuard
import com.astrbot.android.di.hilt.ApplicationScope
import com.astrbot.android.model.plugin.PluginInstallRecord
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

internal class AppBootstrapper @Inject constructor(
    private val application: Application,
    private val qqBridgeRuntime: QqBridgeRuntime,
    private val pluginRuntimeLoader: PluginV2RuntimeLoader,
    private val pluginLifecycleManager: PluginV2LifecycleManager,
    private val providerRepositoryWarmup: ProviderRepositoryWarmup,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    private val bootstrapped = AtomicBoolean(false)
    private var pluginRuntimeLoaderSyncJob: Job? = null

    // Keep the config coordinator seam before ResourceCenterRepository startup.
    fun bootstrap() {
        if (!bootstrapped.compareAndSet(false, true)) return
        Log.i("AstrBotRuntime", "AppBootstrapper.bootstrap entered")

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            RuntimeLogRepository.append(
                "App uncaught exception: thread=${thread.name} reason=${throwable.message ?: throwable.javaClass.simpleName}",
            )
        }

        AppStrings.initialize(application)
        RuntimeSecretRepository.initialize(application)
        // Post-Hilt R2 residue: static LLM service context init needed by
        // legacy adapters. This call must NOT be expanded beyond initialize().
        ChatCompletionService.initialize(application)
        qqBridgeRuntime.initialize(application)
        TencentSilkEncoder.initialize(application)
        appScope.launch(Dispatchers.IO) {
            AppDownloadManager.initialize(application)
        }
        warmUpTtsVoiceAssets()
        NapCatBridgeRepository.initialize(application)
        NapCatLoginRepository.initialize(application)
        RuntimeAssetRepository.initialize(application)
        SherpaOnnxBridge.initialize(application)
        InitializationCoordinator(
            listOf(
                ConfigRepositoryInitializer(),
                BotRepositoryInitializer(),
            ),
        ).initializeAll(application)
        ActiveCapabilityToolSourceProvider.initialize(application)
        CronJobRepository.initialize(application)
        ResourceCenterRepository.initialize(application)
        providerRepositoryWarmup.warmUp()
        InitializationCoordinator(
            listOf(
                PersonaRepositoryInitializer(),
            ),
        ).initializeAll(application)
        ConversationRepository.initialize(application)
        PluginRepository.initialize(application)
        CronJobScheduler.initialize(application)
        PluginRuntimeLogCleanupRepository.initialize(application)
        // Task10 Phase3 – Task C: wire persona/provider reference guards so delete is rejected when in use.
        PersonaReferenceGuard.register { personaId ->
            BotRepository.botProfiles.value.any { it.defaultPersonaId == personaId }
        }
        ProviderReferenceGuard.register { providerId ->
                ConfigRepository.profiles.value.any { config ->
                config.defaultChatProviderId == providerId ||
                    config.defaultVisionProviderId == providerId ||
                    config.defaultSttProviderId == providerId ||
                    config.defaultTtsProviderId == providerId
            } || BotRepository.botProfiles.value.any { it.defaultProviderId == providerId }
        }
        pluginRuntimeLoaderSyncJob = appScope.observePluginRuntimeRecords(
            records = PluginRepository.records,
            sync = { currentRecords ->
                syncPluginRuntimeRecordsAndSignalReady(
                    records = currentRecords,
                    loader = pluginRuntimeLoader,
                    lifecycleManager = pluginLifecycleManager,
                )
            },
        )
        ConversationBackupRepository.initialize(application)
        AppBackupRepository.initialize(application)
        qqBridgeRuntime.start()
        ContainerRuntimeInstaller.warmUpAsync(application, appScope)
        RuntimeLogRepository.append("App started")
        Log.i("AstrBotRuntime", "AppBootstrapper.bootstrap completed")
    }

    private fun warmUpTtsVoiceAssets() {
        appScope.launch(Dispatchers.IO) {
            TtsVoiceAssetRepository.initialize(application)
        }
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

