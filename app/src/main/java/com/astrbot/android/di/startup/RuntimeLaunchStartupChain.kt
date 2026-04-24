package com.astrbot.android.di.startup

import com.astrbot.android.core.common.logging.RuntimeLogRepository
import com.astrbot.android.core.runtime.container.ContainerRuntimeInstaller
import com.astrbot.android.di.hilt.ApplicationScope
import com.astrbot.android.feature.cron.runtime.CronJobReconciler
import com.astrbot.android.feature.qq.runtime.QqBridgeRuntime
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

internal class RuntimeLaunchStartupChain @Inject constructor(
    private val qqBridgeRuntime: QqBridgeRuntime,
    private val containerRuntimeInstaller: ContainerRuntimeInstaller,
    private val cronJobReconciler: CronJobReconciler,
    @ApplicationScope private val appScope: CoroutineScope,
) : AppStartupChain {

    override fun run() {
        qqBridgeRuntime.start()
        containerRuntimeInstaller.warmUpAsync(appScope)
        cronJobReconciler.reconcileAsync(appScope)
        RuntimeLogRepository.append("App started")
    }
}
