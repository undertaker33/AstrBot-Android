package com.astrbot.android.model

enum class ProviderCapability {
    CHAT,
    STT,
    TTS,
    AGENT_EXECUTOR,
    SEARCH,
}

enum class ProviderType {
    OPENAI_COMPATIBLE,
    DEEPSEEK,
    GEMINI,
    OLLAMA,
    QWEN,
    ZHIPU,
    XAI,
    WHISPER_API,
    XINFERENCE_STT,
    BAILIAN_STT,
    SHERPA_ONNX_STT,
    OPENAI_TTS,
    BAILIAN_TTS,
    MINIMAX_TTS,
    SHERPA_ONNX_TTS,
    DIFY,
    BAILIAN_APP,
    TAVILY_SEARCH,
    BRAVE_SEARCH,
    BOCHA_SEARCH,
    BAIDU_AI_SEARCH,
    ANTHROPIC,
    CUSTOM,
}

enum class FeatureSupportState {
    UNKNOWN,
    SUPPORTED,
    UNSUPPORTED,
}

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

data class PersonaProfile(
    val id: String,
    val name: String,
    val tag: String = "",
    val systemPrompt: String,
    val enabledTools: Set<String>,
    val defaultProviderId: String = "",
    val maxContextMessages: Int = 12,
    val enabled: Boolean = true,
)

data class PersonaToolEnablementSnapshot(
    val personaId: String,
    val enabled: Boolean = true,
    val enabledTools: Set<String> = emptySet(),
)

data class BotProfile(
    val id: String = "qq-main",
    val platformName: String = "QQ",
    val displayName: String = "Host Bot",
    val tag: String = "",
    val accountHint: String = "",
    val boundQqUins: List<String> = emptyList(),
    val triggerWords: List<String> = listOf("astrbot"),
    val autoReplyEnabled: Boolean = true,
    val persistConversationLocally: Boolean = false,
    val bridgeMode: String = "NapCat local bridge",
    val bridgeEndpoint: String = "ws://127.0.0.1:6199/ws",
    val defaultProviderId: String = "",
    val defaultPersonaId: String = "default",
    val configProfileId: String = "default",
    val status: String = "Idle",
)

data class ConfigProfile(
    val id: String = "",
    val name: String = "",
    val defaultChatProviderId: String = "",
    val defaultVisionProviderId: String = "",
    val defaultSttProviderId: String = "",
    val defaultTtsProviderId: String = "",
    val sttEnabled: Boolean = false,
    val ttsEnabled: Boolean = false,
    val alwaysTtsEnabled: Boolean = false,
    val ttsReadBracketedContent: Boolean = true,
    val textStreamingEnabled: Boolean = false,
    val voiceStreamingEnabled: Boolean = false,
    val streamingMessageIntervalMs: Int = 120,
    val realWorldTimeAwarenessEnabled: Boolean = false,
    val imageCaptionTextEnabled: Boolean = false,
    val webSearchEnabled: Boolean = false,
    val proactiveEnabled: Boolean = false,
    val includeScheduledTaskConversationContext: Boolean = false,
    val ttsVoiceId: String = "",
    val imageCaptionPrompt: String = "Describe the image in detail before sending it to the chat model.",
    val adminUids: List<String> = emptyList(),
    val sessionIsolationEnabled: Boolean = false,
    val wakeWords: List<String> = emptyList(),
    val wakeWordsAdminOnlyEnabled: Boolean = false,
    val privateChatRequiresWakeWord: Boolean = false,
    val replyTextPrefix: String = "",
    val quoteSenderMessageEnabled: Boolean = false,
    val mentionSenderEnabled: Boolean = false,
    val replyOnAtOnlyEnabled: Boolean = true,
    val whitelistEnabled: Boolean = false,
    val whitelistEntries: List<String> = emptyList(),
    val logOnWhitelistMiss: Boolean = false,
    val adminGroupBypassWhitelistEnabled: Boolean = true,
    val adminPrivateBypassWhitelistEnabled: Boolean = true,
    val ignoreSelfMessageEnabled: Boolean = true,
    val ignoreAtAllEventEnabled: Boolean = true,
    val replyWhenPermissionDenied: Boolean = false,
    val rateLimitWindowSeconds: Int = 0,
    val rateLimitMaxCount: Int = 0,
    val rateLimitStrategy: String = "drop",
    val keywordDetectionEnabled: Boolean = false,
    val keywordPatterns: List<String> = emptyList(),

    // ── Context Strategy ──
    val contextLimitStrategy: String = "truncate_by_turns",
    val maxContextTurns: Int = -1,
    val dequeueContextTurns: Int = 1,
    val llmCompressInstruction: String = "",
    val llmCompressKeepRecent: Int = 6,
    val llmCompressProviderId: String = "",

    // ── MCP / Skill ──
    val mcpServers: List<McpServerEntry> = emptyList(),
    val skills: List<SkillEntry> = emptyList(),
)

data class McpServerEntry(
    val serverId: String = "",
    val name: String = "",
    val url: String = "",
    val transport: String = "streamable_http",
    val command: String = "",
    val args: List<String> = emptyList(),
    val headers: Map<String, String> = emptyMap(),
    val timeoutSeconds: Int = 30,
    val active: Boolean = true,
)

data class SkillEntry(
    val skillId: String = "",
    val name: String = "",
    val description: String = "",
    val content: String = "",
    val priority: Int = 0,
    val active: Boolean = true,
)

data class CronJob(
    val jobId: String = "",
    val name: String = "",
    val description: String = "",
    val jobType: String = "active_agent",
    val cronExpression: String = "",
    val timezone: String = "",
    val payloadJson: String = "{}",
    val enabled: Boolean = true,
    val runOnce: Boolean = false,
    val platform: String = "",
    val conversationId: String = "",
    val botId: String = "",
    val configProfileId: String = "",
    val personaId: String = "",
    val providerId: String = "",
    val origin: String = "",
    val status: String = "scheduled",
    val lastRunAt: Long = 0L,
    val nextRunTime: Long = 0L,
    val lastError: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

data class CronJobExecutionRecord(
    val executionId: String = "",
    val jobId: String = "",
    val status: String = "",
    val startedAt: Long = 0L,
    val completedAt: Long = 0L,
    val durationMs: Long = 0L,
    val attempt: Int = 0,
    val trigger: String = "",
    val errorCode: String = "",
    val errorMessage: String = "",
    val deliverySummary: String = "",
)

data class BotRuntimeState(
    val qqEnabled: Boolean = true,
    val napCatContainerEnabled: Boolean = true,
    val bridgeStatus: String = "Stopped",
    val botStatus: String = "Idle",
)

data class NapCatBridgeConfig(
    val runtimeMode: String = "local_container",
    val endpoint: String = "ws://127.0.0.1:6199/ws",
    val healthUrl: String = "http://127.0.0.1:6099",
    val autoStart: Boolean = false,
    val startCommand: String = "",
    val stopCommand: String = "",
    val statusCommand: String = "",
    val commandPreview: String = "Start NapCat runtime",
)

enum class RuntimeStatus(val label: String) {
    STOPPED("Stopped"),
    CHECKING("Checking"),
    STARTING("Starting"),
    RUNNING("Running"),
    ERROR("Error"),
}

data class NapCatRuntimeState(
    val statusType: RuntimeStatus = RuntimeStatus.STOPPED,
    val lastAction: String = "",
    val lastCheckAt: Long = 0L,
    val pidHint: String = "",
    val details: String = "Bridge is not connected",
    val progressLabel: String = "",
    val progressPercent: Int = 0,
    val progressIndeterminate: Boolean = false,
    val installerCached: Boolean = false,
) {
    val status: String
        get() = statusType.label

    fun blocksAutoStart(): Boolean {
        return statusType == RuntimeStatus.RUNNING || statusType == RuntimeStatus.STARTING
    }
}

enum class RuntimeAssetId(val value: String) {
    TTS("tts"),
    ON_DEVICE_FRAMEWORK("on-device-framework"),
    ON_DEVICE_STT("on-device-stt"),
    ON_DEVICE_TTS("on-device-tts"),
    TTS_VOICE_ASSETS("tts-voice-assets");

    companion object {
        fun fromValue(value: String): RuntimeAssetId? = entries.firstOrNull { it.value == value }
    }
}

data class RuntimeAssetCatalogItem(
    val id: RuntimeAssetId,
    val titleRes: Int,
    val subtitleRes: Int,
    val descriptionRes: Int,
    val actionsEnabled: Boolean = true,
)

data class RuntimeAssetEntryState(
    val catalog: RuntimeAssetCatalogItem,
    val installed: Boolean = false,
    val busy: Boolean = false,
    val lastAction: String = "",
    val details: String = "",
)

data class RuntimeAssetState(
    val assets: List<RuntimeAssetEntryState> = emptyList(),
)

data class ClonedVoiceBinding(
    val id: String,
    val providerId: String,
    val providerType: ProviderType,
    val model: String,
    val voiceId: String,
    val displayName: String,
    val createdAt: Long,
    val lastVerifiedAt: Long = 0L,
    val status: String = "ready",
)

data class TtsVoiceReferenceClip(
    val id: String,
    val localPath: String,
    val durationMs: Long = 0L,
    val sampleRateHz: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

data class TtsVoiceReferenceAsset(
    val id: String,
    val name: String,
    val source: String = "",
    val localPath: String = "",
    val remoteUrl: String = "",
    val durationMs: Long = 0L,
    val sampleRateHz: Int = 0,
    val clips: List<TtsVoiceReferenceClip> = emptyList(),
    val providerBindings: List<ClonedVoiceBinding> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
)

data class SavedQqAccount(
    val uin: String,
    val nickName: String = "",
    val avatarUrl: String = "",
)

data class NapCatLoginState(
    val bridgeReady: Boolean = false,
    val authenticated: Boolean = false,
    val isLogin: Boolean = false,
    val isOffline: Boolean = false,
    val qrCodeUrl: String = "",
    val quickLoginUin: String = "",
    val savedAccounts: List<SavedQqAccount> = emptyList(),
    val loginError: String = "",
    val needCaptcha: Boolean = false,
    val captchaUrl: String = "",
    val needNewDevice: Boolean = false,
    val newDeviceJumpUrl: String = "",
    val newDeviceSig: String = "",
    val uin: String = "",
    val nick: String = "",
    val avatarUrl: String = "",
    val statusText: String = "Waiting for NapCat bridge",
    val lastUpdated: Long = 0L,
)
