package com.elymbot.android.app.integration.qq

import com.elymbot.android.core.runtime.container.ContainerBridgeConfig
import com.elymbot.android.core.runtime.container.ContainerBridgeStatePort
import com.elymbot.android.core.runtime.container.ContainerRuntimeState
import com.elymbot.android.core.runtime.container.ContainerRuntimeStatus
import com.elymbot.android.di.hilt.ApplicationScope
import com.elymbot.android.feature.qq.domain.QqBridgeStatePort
import com.elymbot.android.feature.qq.domain.model.NapCatBridgeConfig
import com.elymbot.android.feature.qq.domain.model.NapCatRuntimeState
import com.elymbot.android.feature.qq.domain.model.RuntimeStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Singleton
internal class ProductionContainerBridgeStatePort @Inject constructor(
    private val bridgeStateOwner: QqBridgeStatePort,
    @ApplicationScope appScope: CoroutineScope,
) : ContainerBridgeStatePort {
    private val _config = MutableStateFlow(bridgeStateOwner.config.value.toContainerBridgeConfig())
    private val _runtimeState = MutableStateFlow(bridgeStateOwner.runtimeState.value.toContainerRuntimeState())

    override val config: StateFlow<ContainerBridgeConfig> = _config.asStateFlow()
    override val runtimeState: StateFlow<ContainerRuntimeState> = _runtimeState.asStateFlow()

    init {
        appScope.launch {
            bridgeStateOwner.config.collect { _config.value = it.toContainerBridgeConfig() }
        }
        appScope.launch {
            bridgeStateOwner.runtimeState.collect { _runtimeState.value = it.toContainerRuntimeState() }
        }
    }

    override fun applyRuntimeDefaults(defaults: ContainerBridgeConfig) {
        bridgeStateOwner.applyRuntimeDefaults(defaults.toNapCatBridgeConfig(bridgeStateOwner.config.value))
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

private fun NapCatBridgeConfig.toContainerBridgeConfig(): ContainerBridgeConfig {
    return ContainerBridgeConfig(
        runtimeMode = runtimeMode,
        endpoint = endpoint,
        healthUrl = healthUrl,
        autoStart = autoStart,
        commandPreview = commandPreview,
    )
}

private fun ContainerBridgeConfig.toNapCatBridgeConfig(current: NapCatBridgeConfig): NapCatBridgeConfig {
    return current.copy(
        runtimeMode = runtimeMode,
        endpoint = endpoint,
        healthUrl = healthUrl,
        autoStart = autoStart,
        commandPreview = commandPreview,
    )
}

private fun NapCatRuntimeState.toContainerRuntimeState(): ContainerRuntimeState {
    return ContainerRuntimeState(
        statusType = statusType.toContainerRuntimeStatus(),
        lastAction = lastAction,
        lastCheckAt = lastCheckAt,
        pidHint = pidHint,
        details = details,
        progressLabel = progressLabel,
        progressPercent = progressPercent,
        progressIndeterminate = progressIndeterminate,
        installerCached = installerCached,
    )
}

private fun RuntimeStatus.toContainerRuntimeStatus(): ContainerRuntimeStatus {
    return when (this) {
        RuntimeStatus.STOPPED -> ContainerRuntimeStatus.STOPPED
        RuntimeStatus.CHECKING -> ContainerRuntimeStatus.CHECKING
        RuntimeStatus.STARTING -> ContainerRuntimeStatus.STARTING
        RuntimeStatus.RUNNING -> ContainerRuntimeStatus.RUNNING
        RuntimeStatus.ERROR -> ContainerRuntimeStatus.ERROR
    }
}
