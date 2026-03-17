package com.astrbot.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.astrbot.android.data.ProviderRepository
import com.astrbot.android.model.FeatureSupportState
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ProviderType
import kotlinx.coroutines.flow.StateFlow

class ProviderViewModel : ViewModel() {
    val providers: StateFlow<List<ProviderProfile>> = ProviderRepository.providers

    fun save(
        id: String?,
        name: String,
        baseUrl: String,
        model: String,
        providerType: ProviderType,
        apiKey: String,
        capabilities: Set<ProviderCapability>,
        enabled: Boolean = true,
        multimodalRuleSupport: FeatureSupportState = FeatureSupportState.UNKNOWN,
        multimodalProbeSupport: FeatureSupportState = FeatureSupportState.UNKNOWN,
    ) {
        ProviderRepository.save(
            id = id,
            name = name,
            baseUrl = baseUrl,
            model = model,
            providerType = providerType,
            apiKey = apiKey,
            capabilities = capabilities,
            enabled = enabled,
            multimodalRuleSupport = multimodalRuleSupport,
            multimodalProbeSupport = multimodalProbeSupport,
        )
    }

    fun toggleEnabled(id: String) {
        ProviderRepository.toggleEnabled(id)
    }

    fun delete(id: String) {
        ProviderRepository.delete(id)
    }
}
