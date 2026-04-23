package com.astrbot.android.feature.qq.data

import android.content.Context
import android.content.SharedPreferences
import com.astrbot.android.core.common.logging.AppLogger
import com.astrbot.android.data.db.AppPreferenceDao
import com.astrbot.android.data.db.AppPreferenceEntity
import com.astrbot.android.data.mergeNapCatBridgeConfig
import com.astrbot.android.data.parseLegacyNapCatBridgeConfig
import com.astrbot.android.model.NapCatBridgeConfig
import com.astrbot.android.model.NapCatRuntimeState
import com.astrbot.android.model.RuntimeStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking

@Singleton
class NapCatBridgeStateOwner @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val appPreferenceDao: AppPreferenceDao,
) {
    private var legacyPreferences: SharedPreferences? = null
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

    init {
        NapCatLoginRepository.installBridgeStateAccessors(
            configSnapshot = { _config.value },
            runtimeStateSnapshot = { _runtimeState.value },
        )
        legacyPreferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        runBlocking(Dispatchers.IO) {
            seedStorageIfNeeded(defaults = _config.value)
            _config.value = loadConfig(defaults = _config.value)
        }
        AppLogger.append(
            "Bridge config loaded: endpoint=${_config.value.endpoint} health=${_config.value.healthUrl} autoStart=${_config.value.autoStart}",
        )
    }

    fun updateConfig(config: NapCatBridgeConfig) {
        _config.value = config
        persistConfig(config)
        AppLogger.append(
            "Bridge config updated: endpoint=${config.endpoint} health=${config.healthUrl} autoStart=${config.autoStart}",
        )
    }

    fun applyRuntimeDefaults(defaults: NapCatBridgeConfig) {
        val mergedConfig = runBlocking(Dispatchers.IO) { loadConfig(defaults) }
        _config.value = mergedConfig
        AppLogger.append(
            "Bridge runtime defaults applied: endpoint=${mergedConfig.endpoint} health=${mergedConfig.healthUrl} autoStart=${mergedConfig.autoStart}",
        )
    }

    fun markStarting() {
        _runtimeState.value = _runtimeState.value.copy(
            statusType = RuntimeStatus.STARTING,
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
            statusType = RuntimeStatus.RUNNING,
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
            statusType = RuntimeStatus.STARTING,
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
            statusType = RuntimeStatus.STOPPED,
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
            statusType = when (_runtimeState.value.statusType) {
                RuntimeStatus.RUNNING -> RuntimeStatus.RUNNING
                RuntimeStatus.STARTING -> RuntimeStatus.STARTING
                else -> RuntimeStatus.CHECKING
            },
            lastAction = "Health check",
            lastCheckAt = System.currentTimeMillis(),
            details = "Checking NapCat runtime health",
        )
    }

    fun markError(message: String) {
        _runtimeState.value = _runtimeState.value.copy(
            statusType = RuntimeStatus.ERROR,
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

    internal fun resetRuntimeStateForTests() {
        _runtimeState.value = NapCatRuntimeState()
    }

    private suspend fun loadConfig(defaults: NapCatBridgeConfig): NapCatBridgeConfig {
        val rawValues = mapOf(
            KEY_RUNTIME_MODE to appPreferenceDao.getValue(KEY_RUNTIME_MODE),
            KEY_ENDPOINT to appPreferenceDao.getValue(KEY_ENDPOINT),
            KEY_HEALTH_URL to appPreferenceDao.getValue(KEY_HEALTH_URL),
            KEY_AUTO_START to appPreferenceDao.getValue(KEY_AUTO_START)?.toBooleanStrictOrNull(),
            KEY_START_COMMAND to appPreferenceDao.getValue(KEY_START_COMMAND),
            KEY_STOP_COMMAND to appPreferenceDao.getValue(KEY_STOP_COMMAND),
            KEY_STATUS_COMMAND to appPreferenceDao.getValue(KEY_STATUS_COMMAND),
            KEY_COMMAND_PREVIEW to appPreferenceDao.getValue(KEY_COMMAND_PREVIEW),
        )
        val merged = mergeNapCatBridgeConfig(defaults, rawValues)

        if (rawValues[KEY_START_COMMAND]?.toString()?.contains("/data/local/tmp/napcat/") == true) {
            persistPreference(KEY_START_COMMAND, merged.startCommand)
        }
        if (rawValues[KEY_STOP_COMMAND]?.toString()?.contains("/data/local/tmp/napcat/") == true) {
            persistPreference(KEY_STOP_COMMAND, merged.stopCommand)
        }
        if (rawValues[KEY_STATUS_COMMAND]?.toString()?.contains("/data/local/tmp/napcat/") == true) {
            persistPreference(KEY_STATUS_COMMAND, merged.statusCommand)
        }
        if (rawValues[KEY_COMMAND_PREVIEW]?.toString()?.contains("/data/local/tmp/napcat/") == true) {
            persistPreference(KEY_COMMAND_PREVIEW, merged.commandPreview)
        }

        return merged
    }

    private fun persistConfig(config: NapCatBridgeConfig) {
        runBlocking(Dispatchers.IO) {
            persistPreference(KEY_RUNTIME_MODE, config.runtimeMode)
            persistPreference(KEY_ENDPOINT, config.endpoint)
            persistPreference(KEY_HEALTH_URL, config.healthUrl)
            persistPreference(KEY_AUTO_START, config.autoStart.toString())
            persistPreference(KEY_START_COMMAND, config.startCommand)
            persistPreference(KEY_STOP_COMMAND, config.stopCommand)
            persistPreference(KEY_STATUS_COMMAND, config.statusCommand)
            persistPreference(KEY_COMMAND_PREVIEW, config.commandPreview)
        }
    }

    private suspend fun seedStorageIfNeeded(defaults: NapCatBridgeConfig) {
        if (appPreferenceDao.getValue(PREF_LEGACY_BRIDGE_CONFIG_MIGRATED) != "true") {
            val imported = parseLegacyNapCatBridgeConfig(
                defaults = defaults,
                values = mapOf(
                    KEY_RUNTIME_MODE to legacyPreferences?.getString(KEY_RUNTIME_MODE, null),
                    KEY_ENDPOINT to legacyPreferences?.getString(KEY_ENDPOINT, null),
                    KEY_HEALTH_URL to legacyPreferences?.getString(KEY_HEALTH_URL, null),
                    KEY_AUTO_START to legacyPreferences?.getBoolean(KEY_AUTO_START, defaults.autoStart),
                    KEY_START_COMMAND to legacyPreferences?.getString(KEY_START_COMMAND, null),
                    KEY_STOP_COMMAND to legacyPreferences?.getString(KEY_STOP_COMMAND, null),
                    KEY_STATUS_COMMAND to legacyPreferences?.getString(KEY_STATUS_COMMAND, null),
                    KEY_COMMAND_PREVIEW to legacyPreferences?.getString(KEY_COMMAND_PREVIEW, null),
                ),
            )
            persistPreference(KEY_RUNTIME_MODE, imported.runtimeMode)
            persistPreference(KEY_ENDPOINT, imported.endpoint)
            persistPreference(KEY_HEALTH_URL, imported.healthUrl)
            persistPreference(KEY_AUTO_START, imported.autoStart.toString())
            persistPreference(KEY_START_COMMAND, imported.startCommand)
            persistPreference(KEY_STOP_COMMAND, imported.stopCommand)
            persistPreference(KEY_STATUS_COMMAND, imported.statusCommand)
            persistPreference(KEY_COMMAND_PREVIEW, imported.commandPreview)
            persistPreference(PREF_LEGACY_BRIDGE_CONFIG_MIGRATED, "true")
        }
    }

    private suspend fun persistPreference(key: String, value: String) {
        appPreferenceDao.upsert(
            AppPreferenceEntity(
                key = key,
                value = value,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    companion object {
        private const val PREFS_NAME = "napcat_bridge_config"
        private const val KEY_RUNTIME_MODE = "runtime_mode"
        private const val KEY_ENDPOINT = "endpoint"
        private const val KEY_HEALTH_URL = "health_url"
        private const val KEY_AUTO_START = "auto_start"
        private const val KEY_START_COMMAND = "start_command"
        private const val KEY_STOP_COMMAND = "stop_command"
        private const val KEY_STATUS_COMMAND = "status_command"
        private const val KEY_COMMAND_PREVIEW = "command_preview"
        private const val PREF_LEGACY_BRIDGE_CONFIG_MIGRATED = "legacy_bridge_config_migrated"
    }
}
