package com.astrbot.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.astrbot.android.di.hilt.BridgeConfig
import com.astrbot.android.di.hilt.BridgeConfigSaver
import com.astrbot.android.di.hilt.BridgeRuntimeState
import com.astrbot.android.model.NapCatBridgeConfig
import com.astrbot.android.model.NapCatRuntimeState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class BridgeViewModel @Inject constructor(
    @BridgeConfig val config: StateFlow<NapCatBridgeConfig>,
    @BridgeRuntimeState val runtimeState: StateFlow<NapCatRuntimeState>,
    private val configSaver: BridgeConfigSaver,
) : ViewModel() {

    fun saveConfig(config: NapCatBridgeConfig) {
        configSaver.save(config)
    }
}
