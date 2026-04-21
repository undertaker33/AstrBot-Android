@file:Suppress("DEPRECATION")

package com.astrbot.android.di.startup

import android.app.Application
import com.astrbot.android.AppStrings
import com.astrbot.android.core.common.logging.RuntimeLogRepository
import com.astrbot.android.core.runtime.audio.SherpaOnnxBridge
import com.astrbot.android.core.runtime.audio.TencentSilkEncoder
import com.astrbot.android.core.runtime.audio.TtsVoiceAssetRepository
import com.astrbot.android.core.runtime.secret.RuntimeSecretRepository
import com.astrbot.android.data.RuntimeAssetRepository
import com.astrbot.android.di.hilt.ApplicationScope
import com.astrbot.android.download.AppDownloadManager
import com.astrbot.android.feature.qq.data.NapCatBridgeRepository
import com.astrbot.android.feature.qq.data.NapCatLoginRepository
import com.astrbot.android.feature.qq.runtime.QqBridgeRuntime
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal class BootstrapPrerequisitesStartupChain @Inject constructor(
    private val application: Application,
    private val qqBridgeRuntime: QqBridgeRuntime,
    @ApplicationScope private val appScope: CoroutineScope,
) : AppStartupChain {

    override fun run() {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            RuntimeLogRepository.append(
                "App uncaught exception: thread=${thread.name} reason=${throwable.message ?: throwable.javaClass.simpleName}",
            )
        }

        AppStrings.initialize(application)
        RuntimeSecretRepository.initialize(application)
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
    }

    private fun warmUpTtsVoiceAssets() {
        appScope.launch(Dispatchers.IO) {
            TtsVoiceAssetRepository.initialize(application)
        }
    }
}
