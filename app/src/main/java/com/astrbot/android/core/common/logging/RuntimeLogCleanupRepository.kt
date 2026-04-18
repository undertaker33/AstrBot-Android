package com.astrbot.android.core.common.logging

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RuntimeLogCleanupSettings(
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

internal interface RuntimeLogCleanupSettingsStore {
    fun load(): RuntimeLogCleanupSettings

    fun save(settings: RuntimeLogCleanupSettings)
}

internal class InMemoryRuntimeLogCleanupSettingsStore : RuntimeLogCleanupSettingsStore {
    private var value = RuntimeLogCleanupSettings()

    override fun load(): RuntimeLogCleanupSettings = value

    override fun save(settings: RuntimeLogCleanupSettings) {
        value = settings
    }
}

private class SharedPreferencesRuntimeLogCleanupSettingsStore(
    private val prefs: SharedPreferences,
) : RuntimeLogCleanupSettingsStore {
    override fun load(): RuntimeLogCleanupSettings {
        return RuntimeLogCleanupSettings(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            intervalHours = prefs.getInt(KEY_HOURS, 12),
            intervalMinutes = prefs.getInt(KEY_MINUTES, 0),
            lastCleanupAtEpochMillis = prefs.getLong(KEY_LAST_CLEANUP, 0L),
        )
    }

    override fun save(settings: RuntimeLogCleanupSettings) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, settings.enabled)
            .putInt(KEY_HOURS, settings.intervalHours)
            .putInt(KEY_MINUTES, settings.intervalMinutes)
            .putLong(KEY_LAST_CLEANUP, settings.lastCleanupAtEpochMillis)
            .apply()
    }

    private companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_HOURS = "hours"
        private const val KEY_MINUTES = "minutes"
        private const val KEY_LAST_CLEANUP = "last_cleanup"
    }
}

object RuntimeLogCleanupRepository {
    private const val PREFS_NAME = "runtime_log_cleanup"

    private val initialized = AtomicBoolean(false)
    private val _settings = MutableStateFlow(RuntimeLogCleanupSettings())
    val settings: StateFlow<RuntimeLogCleanupSettings> = _settings.asStateFlow()

    @Volatile
    private var storeOverrideForTests: RuntimeLogCleanupSettingsStore? = null

    private var store: RuntimeLogCleanupSettingsStore = InMemoryRuntimeLogCleanupSettingsStore()

    fun initialize(context: Context) {
        if (!initialized.compareAndSet(false, true) && storeOverrideForTests == null) return
        if (storeOverrideForTests != null) {
            store = storeOverrideForTests!!
        } else {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            store = SharedPreferencesRuntimeLogCleanupSettingsStore(prefs)
        }
        _settings.value = store.load()
    }

    fun updateSettings(
        enabled: Boolean,
        intervalHours: Int,
        intervalMinutes: Int,
        now: Long = System.currentTimeMillis(),
    ) {
        val current = _settings.value
        val normalized = normalizeSettings(
            current = current,
            enabled = enabled,
            intervalHours = intervalHours,
            intervalMinutes = intervalMinutes,
            now = now,
        )
        persist(normalized)
    }

    fun recordCleanup(now: Long = System.currentTimeMillis()) {
        val current = _settings.value
        if (!current.enabled) return
        persist(current.copy(lastCleanupAtEpochMillis = now))
    }

    fun maybeAutoClear(
        now: Long = System.currentTimeMillis(),
        onClear: () -> Unit,
    ): Boolean {
        val current = _settings.value
        if (!current.shouldAutoClear(now)) return false
        onClear()
        persist(current.copy(lastCleanupAtEpochMillis = now))
        return true
    }

    internal fun setStoreOverrideForTests(store: RuntimeLogCleanupSettingsStore?) {
        storeOverrideForTests = store
        this.store = store ?: InMemoryRuntimeLogCleanupSettingsStore()
        initialized.set(store != null)
        _settings.value = this.store.load()
    }

    private fun persist(settings: RuntimeLogCleanupSettings) {
        store.save(settings)
        _settings.value = settings
    }

    private fun normalizeSettings(
        current: RuntimeLogCleanupSettings,
        enabled: Boolean,
        intervalHours: Int,
        intervalMinutes: Int,
        now: Long,
    ): RuntimeLogCleanupSettings {
        val hours = intervalHours.coerceAtLeast(0)
        val minutes = intervalMinutes.coerceIn(0, 59)
        val normalizedMinutes = if (enabled && hours == 0 && minutes == 0) 1 else minutes
        val lastCleanupAt = when {
            enabled && (!current.enabled || current.lastCleanupAtEpochMillis <= 0L) -> now
            else -> current.lastCleanupAtEpochMillis
        }
        return RuntimeLogCleanupSettings(
            enabled = enabled,
            intervalHours = hours,
            intervalMinutes = normalizedMinutes,
            lastCleanupAtEpochMillis = lastCleanupAt,
        )
    }
}
