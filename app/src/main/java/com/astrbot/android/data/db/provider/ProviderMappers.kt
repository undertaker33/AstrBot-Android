package com.astrbot.android.data.db

import com.astrbot.android.model.FeatureSupportState
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ProviderType

fun ProviderAggregate.toProfile(): ProviderProfile {
    return ProviderProfile(
        id = provider.id,
        name = provider.name,
        baseUrl = provider.baseUrl,
        model = provider.model,
        providerType = runCatching { ProviderType.valueOf(provider.providerType) }.getOrDefault(ProviderType.OPENAI_COMPATIBLE),
        apiKey = provider.apiKey,
        capabilities = capabilities.mapNotNull { runCatching { ProviderCapability.valueOf(it.capability) }.getOrNull() }.toSet(),
        enabled = provider.enabled,
        multimodalRuleSupport = runCatching { FeatureSupportState.valueOf(provider.multimodalRuleSupport) }.getOrDefault(FeatureSupportState.UNKNOWN),
        multimodalProbeSupport = runCatching { FeatureSupportState.valueOf(provider.multimodalProbeSupport) }.getOrDefault(FeatureSupportState.UNKNOWN),
        nativeStreamingRuleSupport = runCatching { FeatureSupportState.valueOf(provider.nativeStreamingRuleSupport) }.getOrDefault(FeatureSupportState.UNKNOWN),
        nativeStreamingProbeSupport = runCatching { FeatureSupportState.valueOf(provider.nativeStreamingProbeSupport) }.getOrDefault(FeatureSupportState.UNKNOWN),
        sttProbeSupport = runCatching { FeatureSupportState.valueOf(provider.sttProbeSupport) }.getOrDefault(FeatureSupportState.UNKNOWN),
        ttsProbeSupport = runCatching { FeatureSupportState.valueOf(provider.ttsProbeSupport) }.getOrDefault(FeatureSupportState.UNKNOWN),
        ttsVoiceOptions = ttsVoiceOptions.sortedBy { it.sortIndex }.map { it.voiceOption },
    )
}

fun ProviderProfile.toWriteModel(sortIndex: Int): ProviderWriteModel {
    return ProviderWriteModel(
        provider = ProviderEntity(
            id = id,
            name = name,
            baseUrl = baseUrl,
            model = model,
            providerType = providerType.name,
            apiKey = apiKey,
            enabled = enabled,
            multimodalRuleSupport = multimodalRuleSupport.name,
            multimodalProbeSupport = multimodalProbeSupport.name,
            nativeStreamingRuleSupport = nativeStreamingRuleSupport.name,
            nativeStreamingProbeSupport = nativeStreamingProbeSupport.name,
            sttProbeSupport = sttProbeSupport.name,
            ttsProbeSupport = ttsProbeSupport.name,
            sortIndex = sortIndex,
            updatedAt = System.currentTimeMillis(),
        ),
        capabilities = capabilities.map { ProviderCapabilityEntity(id, it.name) },
        ttsVoiceOptions = ttsVoiceOptions.mapIndexed { index, option -> ProviderTtsVoiceOptionEntity(id, option, index) },
    )
}
