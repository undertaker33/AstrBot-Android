package com.astrbot.android.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "astrbot_settings")

class AppPreferencesRepository(private val context: Context) {
    private object Keys {
        val qqEnabled = booleanPreferencesKey("qq_enabled")
        val napCatContainerEnabled = booleanPreferencesKey("napcat_container_enabled")
        val preferredChatProvider = stringPreferencesKey("preferred_chat_provider")
        val themeMode = stringPreferencesKey("theme_mode")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            qqEnabled = prefs[Keys.qqEnabled] ?: true,
            napCatContainerEnabled = prefs[Keys.napCatContainerEnabled] ?: true,
            preferredChatProvider = prefs[Keys.preferredChatProvider].orEmpty(),
            themeMode = ThemeMode.fromValue(prefs[Keys.themeMode]),
        )
    }

    suspend fun setQqEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.qqEnabled] = enabled }
    }

    suspend fun setNapCatContainerEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.napCatContainerEnabled] = enabled }
    }

    suspend fun setPreferredChatProvider(providerId: String) {
        context.dataStore.edit { it[Keys.preferredChatProvider] = providerId }
    }

    suspend fun setThemeMode(themeMode: ThemeMode) {
        context.dataStore.edit { it[Keys.themeMode] = themeMode.value }
    }
}

data class AppSettings(
    val qqEnabled: Boolean,
    val napCatContainerEnabled: Boolean,
    val preferredChatProvider: String,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)

enum class AppLanguage(val value: String) {
    CHINESE("zh"),
    ENGLISH("en");

    companion object {
        fun fromValue(value: String?): AppLanguage {
            return entries.firstOrNull { it.value == value } ?: CHINESE
        }
    }
}

enum class ThemeMode(val value: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromValue(value: String?): ThemeMode {
            return entries.firstOrNull { it.value == value } ?: SYSTEM
        }
    }
}
