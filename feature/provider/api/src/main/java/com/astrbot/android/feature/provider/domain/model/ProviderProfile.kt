package com.astrbot.android.feature.provider.domain.model

data class ProviderProfile(
    val id: String,
    val name: String,
    val baseUrl: String,
    val model: String,
    val providerType: ProviderType,
    val apiKey: String,
    val capabilities: Set<ProviderCapability>,
    val enabled: Boolean = true,
    val multimodalRuleSupport: FeatureSupportState = FeatureSupportState.UNKNOWN,
    val multimodalProbeSupport: FeatureSupportState = FeatureSupportState.UNKNOWN,
    val nativeStreamingRuleSupport: FeatureSupportState = FeatureSupportState.UNKNOWN,
    val nativeStreamingProbeSupport: FeatureSupportState = FeatureSupportState.UNKNOWN,
    val sttProbeSupport: FeatureSupportState = FeatureSupportState.UNKNOWN,
    val ttsProbeSupport: FeatureSupportState = FeatureSupportState.UNKNOWN,
    val ttsVoiceOptions: List<String> = emptyList(),
)
