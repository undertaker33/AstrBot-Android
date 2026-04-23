package com.astrbot.android.di.startup

import android.app.Application
import com.astrbot.android.AppStrings
import com.astrbot.android.core.common.logging.RuntimeLogRepository
import com.astrbot.android.core.runtime.audio.SherpaOnnxBridge
import com.astrbot.android.feature.qq.data.NapCatLoginLocalStoreOwner
import com.astrbot.android.feature.qq.data.NapCatBridgeStateOwner
import com.astrbot.android.feature.plugin.data.FeaturePluginRepositoryStateOwner
import com.astrbot.android.download.AppDownloadManagerBootstrap
import com.astrbot.android.feature.qq.runtime.QqBridgeRuntime
import javax.inject.Inject

internal class BootstrapPrerequisitesStartupChain @Inject constructor(
    private val application: Application,
    private val qqBridgeRuntime: QqBridgeRuntime,
    pluginRepositoryStateOwner: FeaturePluginRepositoryStateOwner,
    bridgeStateOwner: NapCatBridgeStateOwner,
    napCatLoginLocalStoreOwner: NapCatLoginLocalStoreOwner,
    appDownloadManagerBootstrap: AppDownloadManagerBootstrap,
) : AppStartupChain {

    override fun run() {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            RuntimeLogRepository.append(
                "App uncaught exception: thread=${thread.name} reason=${throwable.message ?: throwable.javaClass.simpleName}",
            )
        }

        AppStrings.initialize(application)
        qqBridgeRuntime.initialize(application)
        SherpaOnnxBridge.initialize(application)
    }
}
