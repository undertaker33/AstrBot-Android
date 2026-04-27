package com.astrbot.android.model

typealias BotProfile = com.astrbot.android.feature.bot.domain.model.BotProfile
typealias ConfigProfile = com.astrbot.android.feature.config.domain.model.ConfigProfile
typealias CronJob = com.astrbot.android.feature.cron.domain.model.CronJob
typealias CronJobExecutionRecord = com.astrbot.android.feature.cron.domain.model.CronJobExecutionRecord
typealias PersonaProfile = com.astrbot.android.feature.persona.domain.model.PersonaProfile
typealias PersonaToolEnablementSnapshot = com.astrbot.android.feature.persona.domain.model.PersonaToolEnablementSnapshot
typealias FeatureSupportState = com.astrbot.android.feature.provider.domain.model.FeatureSupportState
typealias ProviderCapability = com.astrbot.android.feature.provider.domain.model.ProviderCapability
typealias ProviderProfile = com.astrbot.android.feature.provider.domain.model.ProviderProfile
typealias ProviderType = com.astrbot.android.feature.provider.domain.model.ProviderType

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
