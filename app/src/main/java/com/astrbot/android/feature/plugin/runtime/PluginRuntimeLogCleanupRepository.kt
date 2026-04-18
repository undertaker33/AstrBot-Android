package com.astrbot.android.feature.plugin.runtime

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PluginRuntimeLogCleanupSettings(
    val enabled: Boolean = false,
    val intervalHours: Int = 12,
    val intervalMinutes: Int = 0,
    val lastCleanupAtEpochMillis: Long = 0L,
) {
    init {
        require(intervalHours >= 0) { "intervalHours must not be negative." }
        require(intervalMinutes in 0..59) { "intervalMinutes must be between 0 and 59." }
    }

    fun intervalMillis(): Long {
        return intervalHours * 60L * 60L * 1000L + intervalMinutes * 60L * 1000L
    }

    fun shouldAutoClear(now: Long): Boolean {
        if (!enabled) return false
        val intervalMillis = intervalMillis()
        if (intervalMillis <= 0L) return false
        if (lastCleanupAtEpochMillis <= 0L) return false
        return now - lastCleanupAtEpochMillis >= intervalMillis
    }
}

internal interface PluginRuntimeLogCleanupSettingsStore {
    fun loadAll(): Map<String, PluginRuntimeLogCleanupSettings>

    fun save(
        pluginId: String,
        settings: PluginRuntimeLogCleanupSettings,
    )
}

internal class InMemoryPluginRuntimeLogCleanupSettingsStore : PluginRuntimeLogCleanupSettingsStore {
    private val values = linkedMapOf<String, PluginRuntimeLogCleanupSettings>()

    override fun loadAll(): Map<String, PluginRuntimeLogCleanupSettings> = values.toMap()

    override fun save(
        pluginId: String,
        settings: PluginRuntimeLogCleanupSettings,
    ) {
        values[pluginId] = settings
    }
}

private class SharedPreferencesPluginRuntimeLogCleanupSettingsStore(
    private val prefs: SharedPreferences,
) : PluginRuntimeLogCleanupSettingsStore {
    override fun loadAll(): Map<String, PluginRuntimeLogCleanupSettings> {
        val all = prefs.all
        val pluginIds = all.keys
            .mapNotNull { key ->
                key.substringBefore('.', "").takeIf { key.contains('.') && it.isNotBlank() }
            }
            .toSet()

        return pluginIds.associateWith { pluginId ->
            PluginRuntimeLogCleanupSettings(
                enabled = prefs.getBoolean(key(pluginId, "enabled"), false),
                intervalHours = prefs.getInt(key(pluginId, "hours"), 12),
                intervalMinutes = prefs.getInt(key(pluginId, "minutes"), 0),
                lastCleanupAtEpochMillis = prefs.getLong(key(pluginId, "last_cleanup"), 0L),
            )
        }
    }

    override fun save(
        pluginId: String,
        settings: PluginRuntimeLogCleanupSettings,
    ) {
        prefs.edit()
            .putBoolean(key(pluginId, "enabled"), settings.enabled)
            .putInt(key(pluginId, "hours"), settings.intervalHours)
            .putInt(key(pluginId, "minutes"), settings.intervalMinutes)
            .putLong(key(pluginId, "last_cleanup"), settings.lastCleanupAtEpochMillis)
            .apply()
    }

    private fun key(
        pluginId: String,
        suffix: String,
    ): String = "$pluginId.$suffix"
}

object PluginRuntimeLogCleanupRepository {
    private const val PREFS_NAME = "plugin_runtime_log_cleanup"

    private val initialized = AtomicBoolean(false)
    private val _settings = MutableStateFlow<Map<String, PluginRuntimeLogCleanupSettings>>(emptyMap())
    val settings: StateFlow<Map<String, PluginRuntimeLogCleanupSettings>> = _settings.asStateFlow()

    @Volatile
    private var storeOverrideForTests: PluginRuntimeLogCleanupSettingsStore? = null

    private var store: PluginRuntimeLogCleanupSettingsStore = InMemoryPluginRuntimeLogCleanupSettingsStore()

    fun initialize(context: Context) {
        if (!initialized.compareAndSet(false, true) && storeOverrideForTests == null) return
        if (storeOverrideForTests != null) {
            store = storeOverrideForTests!!
        } else {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            store = SharedPreferencesPluginRuntimeLogCleanupSettingsStore(prefs)
        }
        _settings.value = store.loadAll()
    }

    fun settingsFor(pluginId: String): PluginRuntimeLogCleanupSettings {
        return _settings.value[pluginId] ?: PluginRuntimeLogCleanupSettings()
    }

    fun updateSettings(
        pluginId: String,
        enabled: Boolean,
        intervalHours: Int,
        intervalMinutes: Int,
        now: Long = System.currentTimeMillis(),
    ) {
        val current = settingsFor(pluginId)
        val normalized = normalizeSettings(
            current = current,
            enabled = enabled,
            intervalHours = intervalHours,
            intervalMinutes = intervalMinutes,
            now = now,
        )
        persist(pluginId, normalized)
    }

    fun recordCleanup(
        pluginId: String,
        now: Long = System.currentTimeMillis(),
    ) {
        val current = settingsFor(pluginId)
        if (!current.enabled) return
        persist(pluginId, current.copy(lastCleanupAtEpochMillis = now))
    }

    fun maybeAutoClear(
        pluginId: String,
        now: Long = System.currentTimeMillis(),
        onClear: () -> Unit,
    ): Boolean {
        val current = settingsFor(pluginId)
        if (!current.shouldAutoClear(now)) return false
        onClear()
        persist(pluginId, current.copy(lastCleanupAtEpochMillis = now))
        return true
    }

    internal fun setStoreOverrideForTests(store: PluginRuntimeLogCleanupSettingsStore?) {
        storeOverrideForTests = store
        this.store = store ?: InMemoryPluginRuntimeLogCleanupSettingsStore()
        initialized.set(store != null)
        _settings.value = this.store.loadAll()
    }

    private fun persist(
        pluginId: String,
        settings: PluginRuntimeLogCleanupSettings,
    ) {
        store.save(pluginId, settings)
        _settings.value = _settings.value.toMutableMap().apply {
            put(pluginId, settings)
        }
    }

    private fun normalizeSettings(
        current: PluginRuntimeLogCleanupSettings,
        enabled: Boolean,
        intervalHours: Int,
        intervalMinutes: Int,
        now: Long,
    ): PluginRuntimeLogCleanupSettings {
        val hours = intervalHours.coerceAtLeast(0)
        val minutes = intervalMinutes.coerceIn(0, 59)
        val normalizedMinutes = if (enabled && hours == 0 && minutes == 0) 1 else minutes
        val lastCleanupAt = when {
            enabled && (!current.enabled || current.lastCleanupAtEpochMillis <= 0L) -> now
            else -> current.lastCleanupAtEpochMillis
        }
        return PluginRuntimeLogCleanupSettings(
            enabled = enabled,
            intervalHours = hours,
            intervalMinutes = normalizedMinutes,
            lastCleanupAtEpochMillis = lastCleanupAt,
        )
    }
}
