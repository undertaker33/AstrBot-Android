package com.elymbot.android.di.startup

import com.elymbot.android.core.common.logging.RuntimeLogger
import com.elymbot.android.core.runtime.container.ContainerRuntimeInstaller
import com.elymbot.android.app.integration.cron.CronRuntimeReconciliationPort
import com.elymbot.android.di.hilt.ApplicationScope
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceRuntimeReconciliationPort
import com.elymbot.android.feature.qq.domain.QqStartupPort
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

internal class RuntimeLaunchStartupChain @Inject constructor(
    private val qqStartupPort: QqStartupPort,
    private val containerRuntimeInstaller: ContainerRuntimeInstaller,
    private val cronRuntimeReconciliationPort: CronRuntimeReconciliationPort,
    private val geofenceRuntimeReconciliationPort: GeofenceRuntimeReconciliationPort,
    private val runtimeLogger: RuntimeLogger,
    @ApplicationScope private val appScope: CoroutineScope,
) : AppStartupChain {

    override fun run() {
        qqStartupPort.start()
        containerRuntimeInstaller.warmUpAsync(appScope)
        cronRuntimeReconciliationPort.reconcileAsync(appScope)
        geofenceRuntimeReconciliationPort.reconcileAsync(appScope)
        runtimeLogger.append("App started")
    }
}
