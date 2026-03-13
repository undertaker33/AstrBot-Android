package com.astrbot.android

import android.app.Application
import com.astrbot.android.data.BotRepository
import com.astrbot.android.data.NapCatBridgeRepository
import com.astrbot.android.data.NapCatLoginRepository
import com.astrbot.android.data.PersonaRepository
import com.astrbot.android.data.ProviderRepository
import com.astrbot.android.runtime.ContainerRuntimeInstaller
import com.astrbot.android.runtime.OneBotBridgeServer
import com.astrbot.android.runtime.RuntimeLogRepository

class AstrBotApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        BotRepository.initialize(this)
        NapCatBridgeRepository.initialize(this)
        NapCatLoginRepository.initialize(this)
        ProviderRepository.initialize(this)
        PersonaRepository.initialize(this)
        OneBotBridgeServer.start()
        ContainerRuntimeInstaller.install(this)
        RuntimeLogRepository.append("App started")
    }
}
