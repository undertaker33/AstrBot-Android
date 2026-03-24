package com.astrbot.android

import android.app.Application
import com.astrbot.android.data.BotRepository
import com.astrbot.android.data.ChatCompletionService
import com.astrbot.android.data.ConfigRepository
import com.astrbot.android.data.ConversationRepository
import com.astrbot.android.data.ConversationBackupRepository
import com.astrbot.android.data.NapCatBridgeRepository
import com.astrbot.android.data.NapCatLoginRepository
import com.astrbot.android.data.PersonaRepository
import com.astrbot.android.data.ProviderRepository
import com.astrbot.android.data.RuntimeAssetRepository
import com.astrbot.android.data.SherpaOnnxBridge
import com.astrbot.android.data.TtsVoiceAssetRepository
import com.astrbot.android.runtime.ContainerRuntimeInstaller
import com.astrbot.android.runtime.OneBotBridgeServer
import com.astrbot.android.runtime.RuntimeLogRepository
import com.astrbot.android.runtime.TencentSilkEncoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AstrBotApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        AppStrings.initialize(this)
        ChatCompletionService.initialize(this)
        OneBotBridgeServer.initialize(this)
        TencentSilkEncoder.initialize(this)
        NapCatBridgeRepository.initialize(this)
        NapCatLoginRepository.initialize(this)
        RuntimeAssetRepository.initialize(this)
        SherpaOnnxBridge.initialize(this)
        TtsVoiceAssetRepository.initialize(this)
        ProviderRepository.initialize(this)
        PersonaRepository.initialize(this)
        ConfigRepository.initialize(this)
        ConversationRepository.initialize(this)
        ConversationBackupRepository.initialize(this)
        OneBotBridgeServer.start()
        appScope.launch(Dispatchers.IO) {
            BotRepository.initialize(this@AstrBotApplication)
        }
        ContainerRuntimeInstaller.warmUpAsync(this, appScope)
        RuntimeLogRepository.append("App started")
    }
}
