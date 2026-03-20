package com.astrbot.android.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.astrbot.android.data.RuntimeAssetRepository
import com.astrbot.android.model.RuntimeAssetState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class RuntimeAssetViewModel(application: Application) : AndroidViewModel(application) {
    val state: StateFlow<RuntimeAssetState> = RuntimeAssetRepository.state

    fun refresh() {
        RuntimeAssetRepository.refresh(getApplication())
    }

    fun downloadAsset(assetId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            RuntimeAssetRepository.downloadAsset(getApplication(), assetId)
        }
    }

    fun clearAsset(assetId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            RuntimeAssetRepository.clearAsset(getApplication(), assetId)
        }
    }

    fun downloadOnDeviceTtsModel(modelId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            RuntimeAssetRepository.downloadOnDeviceTtsModel(getApplication(), modelId)
        }
    }

    fun clearOnDeviceTtsModel(modelId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            RuntimeAssetRepository.clearOnDeviceTtsModel(getApplication(), modelId)
        }
    }
}
