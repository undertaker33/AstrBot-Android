package com.elymbot.android.feature.qq.domain

import com.elymbot.android.feature.qq.domain.model.NapCatBridgeConfig
import com.elymbot.android.feature.qq.domain.model.NapCatRuntimeState
import kotlinx.coroutines.flow.StateFlow

interface QqBridgeStatePort {
    val config: StateFlow<NapCatBridgeConfig>
    val runtimeState: StateFlow<NapCatRuntimeState>

    fun updateConfig(config: NapCatBridgeConfig)
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
    fun updateProgress(
        label: String,
        percent: Int,
        indeterminate: Boolean,
        installerCached: Boolean,
    )

    fun markInstallerCached(cached: Boolean)
}
