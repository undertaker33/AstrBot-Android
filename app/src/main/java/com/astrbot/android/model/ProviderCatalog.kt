package com.astrbot.android.model

fun ProviderCapability.displayLabel(): String {
    return when (this) {
        ProviderCapability.CHAT -> "Chat"
        ProviderCapability.STT -> "STT"
        ProviderCapability.TTS -> "TTS"
        ProviderCapability.AGENT_EXECUTOR -> "Agent Executor"
    }
}

fun ProviderType.displayLabel(): String {
    return when (this) {
        ProviderType.OPENAI_COMPATIBLE -> "OpenAI Compatible"
        ProviderType.DEEPSEEK -> "DeepSeek"
        ProviderType.GEMINI -> "Gemini"
        ProviderType.OLLAMA -> "Ollama"
        ProviderType.QWEN -> "Qwen"
        ProviderType.ZHIPU -> "Zhipu"
        ProviderType.XAI -> "xAI"
        ProviderType.WHISPER_API -> "Whisper API"
        ProviderType.XINFERENCE_STT -> "Xinference STT"
        ProviderType.BAILIAN_STT -> "Alibaba Bailian STT"
        ProviderType.SHERPA_ONNX_STT -> "Sherpa ONNX STT"
        ProviderType.OPENAI_TTS -> "OpenAI TTS"
        ProviderType.BAILIAN_TTS -> "Alibaba Bailian TTS"
        ProviderType.MINIMAX_TTS -> "MiniMax TTS"
        ProviderType.SHERPA_ONNX_TTS -> "Sherpa ONNX TTS"
        ProviderType.DIFY -> "Dify"
        ProviderType.BAILIAN_APP -> "Alibaba Bailian App"
        ProviderType.ANTHROPIC -> "Anthropic"
        ProviderType.CUSTOM -> "Custom"
    }
}

fun ProviderType.defaultCapability(): ProviderCapability {
    return when (this) {
        ProviderType.WHISPER_API,
        ProviderType.XINFERENCE_STT,
        ProviderType.BAILIAN_STT,
        ProviderType.SHERPA_ONNX_STT,
        -> ProviderCapability.STT

        ProviderType.OPENAI_TTS,
        ProviderType.BAILIAN_TTS,
        ProviderType.MINIMAX_TTS,
        ProviderType.SHERPA_ONNX_TTS,
        -> ProviderCapability.TTS

        ProviderType.DIFY,
        ProviderType.BAILIAN_APP,
        -> ProviderCapability.AGENT_EXECUTOR

        else -> ProviderCapability.CHAT
    }
}

fun ProviderType.isVisibleInCatalog(): Boolean {
    return this != ProviderType.ANTHROPIC && this != ProviderType.CUSTOM
}

fun ProviderType.isLocalOnDeviceProvider(): Boolean {
    return this == ProviderType.SHERPA_ONNX_STT || this == ProviderType.SHERPA_ONNX_TTS
}

fun ProviderType.supportsPullModels(): Boolean {
    return when (defaultCapability()) {
        ProviderCapability.CHAT -> true
        else -> false
    }
}

fun ProviderType.supportsChatCompletions(): Boolean {
    return defaultCapability() == ProviderCapability.CHAT
}

fun ProviderType.usesOpenAiStyleChatApi(): Boolean {
    return this in setOf(
        ProviderType.OPENAI_COMPATIBLE,
        ProviderType.DEEPSEEK,
        ProviderType.QWEN,
        ProviderType.ZHIPU,
        ProviderType.XAI,
    )
}

fun ProviderType.supportsMultimodalCheck(): Boolean {
    return defaultCapability() == ProviderCapability.CHAT
}

fun ProviderType.supportsNativeStreamingCheck(): Boolean {
    return defaultCapability() == ProviderCapability.CHAT
}

fun visibleProviderTypesFor(capability: ProviderCapability): List<ProviderType> {
    return ProviderType.entries.filter { it.isVisibleInCatalog() && it.defaultCapability() == capability }
}

fun inferNativeStreamingRuleSupport(providerType: ProviderType, model: String): FeatureSupportState {
    val normalizedModel = model.trim().lowercase()
    if (!providerType.supportsNativeStreamingCheck()) return FeatureSupportState.UNSUPPORTED
    return when (providerType) {
        ProviderType.GEMINI -> FeatureSupportState.SUPPORTED
        ProviderType.OLLAMA -> if (normalizedModel.isBlank()) FeatureSupportState.UNKNOWN else FeatureSupportState.SUPPORTED
        ProviderType.OPENAI_COMPATIBLE,
        ProviderType.DEEPSEEK,
        ProviderType.QWEN,
        ProviderType.ZHIPU,
        ProviderType.XAI,
        -> if (normalizedModel.isBlank()) FeatureSupportState.UNKNOWN else FeatureSupportState.SUPPORTED

        else -> FeatureSupportState.UNSUPPORTED
    }
}

fun inferMultimodalRuleSupport(providerType: ProviderType, model: String): FeatureSupportState {
    val normalizedModel = model.trim().lowercase()
    if (!providerType.supportsMultimodalCheck()) return FeatureSupportState.UNSUPPORTED

    return when (providerType) {
        ProviderType.GEMINI -> FeatureSupportState.SUPPORTED
        ProviderType.OLLAMA -> {
            when {
                normalizedModel.contains("vision") || normalizedModel.contains("vl") || normalizedModel.contains("omni") -> FeatureSupportState.SUPPORTED
                normalizedModel.isBlank() -> FeatureSupportState.UNKNOWN
                else -> FeatureSupportState.UNSUPPORTED
            }
        }

        ProviderType.OPENAI_COMPATIBLE,
        ProviderType.DEEPSEEK,
        ProviderType.QWEN,
        ProviderType.ZHIPU,
        ProviderType.XAI,
        -> {
            when {
                normalizedModel.isBlank() -> FeatureSupportState.UNKNOWN
                normalizedModel.contains("gpt-4o") || normalizedModel.contains("vision") || normalizedModel.contains("vl") ||
                    normalizedModel.contains("omni") || normalizedModel.contains("4v") || normalizedModel.contains("janus") ||
                    normalizedModel.contains("gemma-3") -> FeatureSupportState.SUPPORTED
                providerType == ProviderType.XAI && normalizedModel.contains("grok") -> FeatureSupportState.SUPPORTED
                else -> FeatureSupportState.UNSUPPORTED
            }
        }

        else -> FeatureSupportState.UNSUPPORTED
    }
}

fun ProviderProfile.hasNativeStreamingSupport(): Boolean {
    return nativeStreamingProbeSupport == FeatureSupportState.SUPPORTED ||
        nativeStreamingRuleSupport == FeatureSupportState.SUPPORTED ||
        inferNativeStreamingRuleSupport(providerType, model) == FeatureSupportState.SUPPORTED
}
