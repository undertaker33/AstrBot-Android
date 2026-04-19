package com.astrbot.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.astrbot.android.di.BridgeViewModelDependencies
import com.astrbot.android.model.NapCatBridgeConfig
import com.astrbot.android.model.NapCatRuntimeState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class BridgeViewModel @Inject constructor(
    private val dependencies: BridgeViewModelDependencies,
) : ViewModel() {
    val config: StateFlow<NapCatBridgeConfig> = dependencies.config
    val runtimeState: StateFlow<NapCatRuntimeState> = dependencies.runtimeState

    fun saveConfig(config: NapCatBridgeConfig) {
        dependencies.saveConfig(config)
    }
}
