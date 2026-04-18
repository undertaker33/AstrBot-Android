package com.astrbot.android.feature.qq.data

import com.astrbot.android.data.NapCatBridgeRepository
import com.astrbot.android.model.NapCatBridgeConfig
import com.astrbot.android.model.NapCatRuntimeState
import com.astrbot.android.model.RuntimeStatus
import kotlinx.coroutines.flow.StateFlow

class LegacyNapCatStateAdapter {
    val config: StateFlow<NapCatBridgeConfig> get() = NapCatBridgeRepository.config
    val runtimeState: StateFlow<NapCatRuntimeState> get() = NapCatBridgeRepository.runtimeState

    fun isRunning(): Boolean {
        return runtimeState.value.statusType == RuntimeStatus.RUNNING
    }
}
