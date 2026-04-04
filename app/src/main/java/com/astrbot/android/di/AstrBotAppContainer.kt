package com.astrbot.android.di

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.astrbot.android.AppStrings
import com.astrbot.android.data.AppBackupRepository
import com.astrbot.android.data.BotRepository
import com.astrbot.android.data.ChatCompletionService
import com.astrbot.android.data.ConfigRepository
import com.astrbot.android.data.ConversationBackupRepository
import com.astrbot.android.data.ConversationRepository
import com.astrbot.android.data.NapCatBridgeRepository
import com.astrbot.android.data.NapCatLoginRepository
import com.astrbot.android.data.PersonaRepository
import com.astrbot.android.data.PluginRepository
import com.astrbot.android.data.ProviderRepository
import com.astrbot.android.data.RuntimeAssetRepository
import com.astrbot.android.data.SherpaOnnxBridge
import com.astrbot.android.data.TtsVoiceAssetRepository
import com.astrbot.android.runtime.ContainerRuntimeInstaller
import com.astrbot.android.runtime.OneBotBridgeServer
import com.astrbot.android.runtime.RuntimeLogRepository
import com.astrbot.android.runtime.RuntimeSecretRepository
import com.astrbot.android.runtime.TencentSilkEncoder
import com.astrbot.android.runtime.plugin.ExternalPluginRuntimeCatalog
import com.astrbot.android.runtime.plugin.PluginRuntimeRegistry
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
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AstrBotAppContainer(
    private val application: Application,
) {
    private val bootstrapped = AtomicBoolean(false)
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

    val viewModelFactory: ViewModelProvider.Factory = AstrBotViewModelFactory(this, application)

    fun bootstrap() {
        if (!bootstrapped.compareAndSet(false, true)) return

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            RuntimeLogRepository.append(
                "App uncaught exception: thread=${thread.name} reason=${throwable.message ?: throwable.javaClass.simpleName}",
            )
        }

        AppStrings.initialize(application)
        RuntimeSecretRepository.initialize(application)
        ChatCompletionService.initialize(application)
        OneBotBridgeServer.initialize(application)
        TencentSilkEncoder.initialize(application)
        NapCatBridgeRepository.initialize(application)
        NapCatLoginRepository.initialize(application)
        RuntimeAssetRepository.initialize(application)
        SherpaOnnxBridge.initialize(application)
        TtsVoiceAssetRepository.initialize(application)
        ProviderRepository.initialize(application)
        PersonaRepository.initialize(application)
        ConfigRepository.initialize(application)
        ConversationRepository.initialize(application)
        PluginRepository.initialize(application)
        PluginRuntimeRegistry.registerExternalProvider {
            ExternalPluginRuntimeCatalog.plugins()
        }
        ConversationBackupRepository.initialize(application)
        AppBackupRepository.initialize(application)
        OneBotBridgeServer.start()
        appScope.launch(Dispatchers.IO) {
            BotRepository.initialize(application)
        }
        ContainerRuntimeInstaller.warmUpAsync(application, appScope)
        RuntimeLogRepository.append("App started")
    }
}

private class AstrBotViewModelFactory(
    private val container: AstrBotAppContainer,
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
