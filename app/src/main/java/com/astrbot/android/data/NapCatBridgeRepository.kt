package com.astrbot.android.data

import android.content.Context
import android.content.SharedPreferences
import com.astrbot.android.model.NapCatBridgeConfig
import com.astrbot.android.model.NapCatRuntimeState
import com.astrbot.android.runtime.RuntimeLogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object NapCatBridgeRepository {
    private const val PREFS_NAME = "napcat_bridge_config"
    private const val KEY_RUNTIME_MODE = "runtime_mode"
    private const val KEY_ENDPOINT = "endpoint"
    private const val KEY_HEALTH_URL = "health_url"
    private const val KEY_AUTO_START = "auto_start"
    private const val KEY_START_COMMAND = "start_command"
    private const val KEY_STOP_COMMAND = "stop_command"
    private const val KEY_STATUS_COMMAND = "status_command"
    private const val KEY_COMMAND_PREVIEW = "command_preview"

    private var preferences: SharedPreferences? = null
    private val _config = MutableStateFlow(
        NapCatBridgeConfig(
            commandPreview = "Start NapCat runtime",
            startCommand = "sh /data/local/tmp/napcat/start.sh",
            stopCommand = "sh /data/local/tmp/napcat/stop.sh",
            statusCommand = "sh /data/local/tmp/napcat/status.sh",
        ),
    )
    private val _runtimeState = MutableStateFlow(NapCatRuntimeState())

    val config: StateFlow<NapCatBridgeConfig> = _config.asStateFlow()
    val runtimeState: StateFlow<NapCatRuntimeState> = _runtimeState.asStateFlow()

    fun initialize(context: Context) {
        if (preferences != null) return
        preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _config.value = loadConfig(defaults = _config.value)
        RuntimeLogRepository.append(
            "Bridge config loaded: endpoint=${_config.value.endpoint} health=${_config.value.healthUrl} autoStart=${_config.value.autoStart}",
        )
    }

    fun updateConfig(config: NapCatBridgeConfig) {
        _config.value = config
        persistConfig(config)
        RuntimeLogRepository.append(
            "Bridge config updated: endpoint=${config.endpoint} health=${config.healthUrl} autoStart=${config.autoStart}",
        )
    }

    fun applyRuntimeDefaults(defaults: NapCatBridgeConfig) {
        val mergedConfig = loadConfig(defaults)
        _config.value = mergedConfig
        RuntimeLogRepository.append(
            "Bridge runtime defaults applied: endpoint=${mergedConfig.endpoint} health=${mergedConfig.healthUrl} autoStart=${mergedConfig.autoStart}",
        )
    }

    fun markStarting() {
        _runtimeState.value = _runtimeState.value.copy(
            status = "Starting",
            lastAction = "Start requested",
            lastCheckAt = System.currentTimeMillis(),
            details = "Preparing container and network installer",
            progressLabel = "Preparing start",
            progressPercent = 5,
            progressIndeterminate = false,
        )
    }

    fun markRunning(
        pidHint: String = "local",
        details: String = "Local bridge is ready for QQ message transport",
    ) {
        _runtimeState.value = _runtimeState.value.copy(
            status = "Running",
            lastAction = "Runtime active",
            lastCheckAt = System.currentTimeMillis(),
            pidHint = pidHint,
            details = details,
            progressLabel = "Running",
            progressPercent = 100,
            progressIndeterminate = false,
        )
    }

    fun markProcessRunning(
        pidHint: String = "local",
        details: String = "NapCat process is running and waiting for the HTTP endpoint",
    ) {
        val current = _runtimeState.value
        _runtimeState.value = current.copy(
            status = "Starting",
            lastAction = "Process started",
            lastCheckAt = System.currentTimeMillis(),
            pidHint = pidHint,
            details = details,
            progressLabel = current.progressLabel.ifBlank { "Waiting for HTTP" },
            progressIndeterminate = current.progressIndeterminate || current.progressPercent in 1..99,
        )
    }

    fun markStopped(reason: String = "Stopped manually") {
        _runtimeState.value = _runtimeState.value.copy(
            status = "Stopped",
            lastAction = reason,
            lastCheckAt = System.currentTimeMillis(),
            pidHint = "",
            details = "Bridge is not running",
            progressLabel = "",
            progressPercent = 0,
            progressIndeterminate = false,
        )
    }

    fun markChecking() {
        _runtimeState.value = _runtimeState.value.copy(
            status = when (_runtimeState.value.status) {
                "Running" -> "Running"
                "Starting" -> "Starting"
                else -> "Checking"
            },
            lastAction = "Health check",
            lastCheckAt = System.currentTimeMillis(),
            details = "Checking NapCat runtime health",
        )
    }

    fun markError(message: String) {
        _runtimeState.value = _runtimeState.value.copy(
            status = "Error",
            lastAction = "Bridge error",
            lastCheckAt = System.currentTimeMillis(),
            details = message,
            progressIndeterminate = false,
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
            lastCheckAt = System.currentTimeMillis(),
        )
    }

    fun markInstallerCached(cached: Boolean) {
        _runtimeState.value = _runtimeState.value.copy(
            installerCached = cached,
            lastCheckAt = System.currentTimeMillis(),
        )
    }

    private fun loadConfig(defaults: NapCatBridgeConfig): NapCatBridgeConfig {
        val prefs = preferences ?: return defaults
        return defaults.copy(
            runtimeMode = prefs.getString(KEY_RUNTIME_MODE, defaults.runtimeMode) ?: defaults.runtimeMode,
            endpoint = prefs.getString(KEY_ENDPOINT, defaults.endpoint) ?: defaults.endpoint,
            healthUrl = prefs.getString(KEY_HEALTH_URL, defaults.healthUrl) ?: defaults.healthUrl,
            autoStart = prefs.getBoolean(KEY_AUTO_START, defaults.autoStart),
            startCommand = prefs.getString(KEY_START_COMMAND, defaults.startCommand) ?: defaults.startCommand,
            stopCommand = prefs.getString(KEY_STOP_COMMAND, defaults.stopCommand) ?: defaults.stopCommand,
            statusCommand = prefs.getString(KEY_STATUS_COMMAND, defaults.statusCommand) ?: defaults.statusCommand,
            commandPreview = prefs.getString(KEY_COMMAND_PREVIEW, defaults.commandPreview) ?: defaults.commandPreview,
        )
    }

    private fun persistConfig(config: NapCatBridgeConfig) {
        preferences
            ?.edit()
            ?.putString(KEY_RUNTIME_MODE, config.runtimeMode)
            ?.putString(KEY_ENDPOINT, config.endpoint)
            ?.putString(KEY_HEALTH_URL, config.healthUrl)
            ?.putBoolean(KEY_AUTO_START, config.autoStart)
            ?.putString(KEY_START_COMMAND, config.startCommand)
            ?.putString(KEY_STOP_COMMAND, config.stopCommand)
            ?.putString(KEY_STATUS_COMMAND, config.statusCommand)
            ?.putString(KEY_COMMAND_PREVIEW, config.commandPreview)
            ?.apply()
    }
}
