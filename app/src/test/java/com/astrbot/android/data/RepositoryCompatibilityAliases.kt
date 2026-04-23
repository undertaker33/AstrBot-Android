package com.astrbot.android.data

import com.astrbot.android.feature.bot.data.FeatureBotRepository
import com.astrbot.android.feature.chat.data.FeatureConversationRepository
import com.astrbot.android.feature.config.data.FeatureConfigRepository
import com.astrbot.android.feature.cron.data.FeatureCronJobRepository
import com.astrbot.android.feature.persona.data.FeaturePersonaRepository
import com.astrbot.android.feature.plugin.data.FeaturePluginRepository
import com.astrbot.android.feature.provider.data.FeatureProviderRepository
import com.astrbot.android.feature.qq.data.NapCatLoginRepository
import com.astrbot.android.feature.resource.data.FeatureResourceCenterRepository
import com.astrbot.android.model.NapCatBridgeConfig
import com.astrbot.android.model.NapCatRuntimeState
import com.astrbot.android.model.RuntimeStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

val BotRepository = FeatureBotRepository
val ConfigRepository = FeatureConfigRepository
val PersonaRepository = FeaturePersonaRepository
val ProviderRepository = FeatureProviderRepository
val ConversationRepository = FeatureConversationRepository
val CronJobRepository = FeatureCronJobRepository
val PluginRepository = FeaturePluginRepository
val ResourceCenterRepository = FeatureResourceCenterRepository

val NoOpPluginDataRemover = com.astrbot.android.feature.plugin.data.NoOpPluginDataRemover

typealias PluginInstallStore = com.astrbot.android.feature.plugin.data.PluginInstallStore
typealias PluginDataRemover = com.astrbot.android.feature.plugin.data.PluginDataRemover
typealias PluginFileDataRemover = com.astrbot.android.feature.plugin.data.PluginFileDataRemover
typealias PluginUninstallResult = com.astrbot.android.feature.plugin.data.PluginUninstallResult
typealias PluginCatalogVersionGateResult = com.astrbot.android.feature.plugin.data.PluginCatalogVersionGateResult
typealias PluginPackageInstallBlockedException =
    com.astrbot.android.feature.plugin.data.PluginPackageInstallBlockedException

typealias AppBackupRepository = com.astrbot.android.core.db.backup.AppBackupRepository
typealias ChatCompletionService = com.astrbot.android.core.runtime.llm.ChatCompletionService
typealias LlmResponseSegmenter = com.astrbot.android.core.runtime.llm.LlmResponseSegmenter
internal typealias NapCatLoginDiagnostics = com.astrbot.android.feature.qq.data.NapCatLoginDiagnostics
typealias NapCatLoginRepository = com.astrbot.android.feature.qq.data.NapCatLoginRepository
typealias NapCatLoginService = com.astrbot.android.feature.qq.data.NapCatLoginService
typealias ProfileDeletionGuard = com.astrbot.android.core.common.profile.ProfileDeletionGuard
typealias LastProfileDeletionBlockedException =
    com.astrbot.android.core.common.profile.LastProfileDeletionBlockedException
typealias ProfileCatalogKind = com.astrbot.android.core.common.profile.ProfileCatalogKind

object NapCatBridgeRepository {
    private val _config = MutableStateFlow(NapCatBridgeConfig())
    private val _runtimeState = MutableStateFlow(NapCatRuntimeState())

    val config: StateFlow<NapCatBridgeConfig> = _config.asStateFlow()
    val runtimeState: StateFlow<NapCatRuntimeState> = _runtimeState.asStateFlow()

    init {
        syncWithLoginRepository()
    }

    fun updateConfig(config: NapCatBridgeConfig) {
        _config.value = config
        syncWithLoginRepository()
    }

    fun applyRuntimeDefaults(defaults: NapCatBridgeConfig) {
        updateConfig(defaults)
    }

    fun markStarting() {
        _runtimeState.value = _runtimeState.value.copy(
            statusType = RuntimeStatus.STARTING,
            lastAction = "Start requested",
            details = "Preparing container and network installer",
        )
    }

    fun markRunning(
        pidHint: String = "local",
        details: String = "Local bridge is ready for QQ message transport",
    ) {
        _runtimeState.value = _runtimeState.value.copy(
            statusType = RuntimeStatus.RUNNING,
            pidHint = pidHint,
            details = details,
            progressPercent = 100,
        )
    }

    fun markProcessRunning(
        pidHint: String = "local",
        details: String = "NapCat process is running and waiting for the HTTP endpoint",
    ) {
        _runtimeState.value = _runtimeState.value.copy(
            statusType = RuntimeStatus.STARTING,
            pidHint = pidHint,
            details = details,
        )
    }

    fun markStopped(reason: String = "Stopped manually") {
        _runtimeState.value = NapCatRuntimeState(
            statusType = RuntimeStatus.STOPPED,
            lastAction = reason,
        )
    }

    fun markChecking() {
        val nextStatus = when (_runtimeState.value.statusType) {
            RuntimeStatus.RUNNING -> RuntimeStatus.RUNNING
            RuntimeStatus.STARTING -> RuntimeStatus.STARTING
            else -> RuntimeStatus.CHECKING
        }
        _runtimeState.value = _runtimeState.value.copy(
            statusType = nextStatus,
            lastAction = "Health check",
            details = "Checking NapCat runtime health",
        )
    }

    fun markError(message: String) {
        _runtimeState.value = _runtimeState.value.copy(
            statusType = RuntimeStatus.ERROR,
            lastAction = "Bridge error",
            details = message,
        )
    }

    fun updateProgress(
        label: String,
        percent: Int,
        indeterminate: Boolean,
        installerCached: Boolean = _runtimeState.value.installerCached,
    ) {
        _runtimeState.value = _runtimeState.value.copy(
            progressLabel = label,
            progressPercent = percent.coerceIn(0, 100),
            progressIndeterminate = indeterminate,
            installerCached = installerCached,
        )
    }

    fun markInstallerCached(cached: Boolean) {
        _runtimeState.value = _runtimeState.value.copy(installerCached = cached)
    }

    fun resetRuntimeStateForTests() {
        _runtimeState.value = NapCatRuntimeState()
        _config.value = NapCatBridgeConfig()
        syncWithLoginRepository()
    }

    private fun syncWithLoginRepository() {
        NapCatLoginRepository.installBridgeStateAccessors(
            configSnapshot = { _config.value },
            runtimeStateSnapshot = { _runtimeState.value },
        )
    }
}
