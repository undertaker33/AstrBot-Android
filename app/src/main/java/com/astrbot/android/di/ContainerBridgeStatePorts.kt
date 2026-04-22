package com.astrbot.android.di

import com.astrbot.android.core.runtime.container.ContainerBridgeStatePort
import com.astrbot.android.feature.qq.data.NapCatBridgeStateOwner
import com.astrbot.android.model.NapCatBridgeConfig
import com.astrbot.android.model.NapCatRuntimeState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow

@Singleton
internal class ProductionContainerBridgeStatePort @Inject constructor(
    private val bridgeStateOwner: NapCatBridgeStateOwner,
) : ContainerBridgeStatePort {
    override val config: StateFlow<NapCatBridgeConfig> = bridgeStateOwner.config
    override val runtimeState: StateFlow<NapCatRuntimeState> = bridgeStateOwner.runtimeState

    override fun applyRuntimeDefaults(defaults: NapCatBridgeConfig) {
        bridgeStateOwner.applyRuntimeDefaults(defaults)
    }

    override fun markStarting() = bridgeStateOwner.markStarting()

    override fun markRunning(pidHint: String, details: String) {
        bridgeStateOwner.markRunning(pidHint = pidHint, details = details)
    }

    override fun markProcessRunning(pidHint: String, details: String) {
        bridgeStateOwner.markProcessRunning(pidHint = pidHint, details = details)
    }

    override fun markStopped(reason: String) = bridgeStateOwner.markStopped(reason)

    override fun markChecking() = bridgeStateOwner.markChecking()

    override fun markError(message: String) = bridgeStateOwner.markError(message)

    override fun updateProgress(label: String, percent: Int, indeterminate: Boolean, installerCached: Boolean) {
        bridgeStateOwner.updateProgress(
            label = label,
            percent = percent,
            indeterminate = indeterminate,
            installerCached = installerCached,
        )
    }

    override fun markInstallerCached(cached: Boolean) = bridgeStateOwner.markInstallerCached(cached)
}
