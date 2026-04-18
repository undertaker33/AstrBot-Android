package com.astrbot.android.core.runtime.container

import com.astrbot.android.model.NapCatBridgeConfig
import com.astrbot.android.model.NapCatRuntimeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface ContainerBridgeStatePort {
    val config: StateFlow<NapCatBridgeConfig>
    val runtimeState: StateFlow<NapCatRuntimeState>

    fun applyRuntimeDefaults(defaults: NapCatBridgeConfig)
    fun markStarting()
    fun markRunning(
        pidHint: String = "local",
        details: String = "Local bridge is ready for QQ message transport",
    )

    fun markProcessRunning(
        pidHint: String = "local",
        details: String = "NapCat process is running and waiting for the HTTP endpoint",
    )

    fun markStopped(reason: String = "Stopped manually")
    fun markChecking()
    fun markError(message: String)
    fun updateProgress(label: String, percent: Int, indeterminate: Boolean, installerCached: Boolean)
    fun markInstallerCached(cached: Boolean)
}

object ContainerBridgeStateRegistry {
    @Volatile
    var port: ContainerBridgeStatePort = MissingContainerBridgeStatePort
}

private object MissingContainerBridgeStatePort : ContainerBridgeStatePort {
    private val defaultConfig = MutableStateFlow(
        NapCatBridgeConfig(
            commandPreview = "Start NapCat runtime",
            startCommand = "sh /data/local/tmp/napcat/start.sh",
            stopCommand = "sh /data/local/tmp/napcat/stop.sh",
            statusCommand = "sh /data/local/tmp/napcat/status.sh",
        ),
    )
    private val defaultRuntimeState = MutableStateFlow(NapCatRuntimeState())

    override val config: StateFlow<NapCatBridgeConfig> = defaultConfig
    override val runtimeState: StateFlow<NapCatRuntimeState> = defaultRuntimeState

    override fun applyRuntimeDefaults(defaults: NapCatBridgeConfig) {
        defaultConfig.value = defaults
    }

    override fun markStarting() = Unit

    override fun markRunning(pidHint: String, details: String) = Unit

    override fun markProcessRunning(pidHint: String, details: String) = Unit

    override fun markStopped(reason: String) = Unit

    override fun markChecking() = Unit

    override fun markError(message: String) = Unit

    override fun updateProgress(label: String, percent: Int, indeterminate: Boolean, installerCached: Boolean) = Unit

    override fun markInstallerCached(cached: Boolean) = Unit
}
