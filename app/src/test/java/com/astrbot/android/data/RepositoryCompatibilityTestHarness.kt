package com.astrbot.android.data

import android.content.SharedPreferences
import android.content.ContextWrapper
import com.astrbot.android.core.db.backup.AppBackupDataPort
import com.astrbot.android.core.db.backup.AppBackupExternalState
import com.astrbot.android.core.db.backup.AppBackupRepository
import com.astrbot.android.core.runtime.audio.TtsVoiceAssetRepository
import com.astrbot.android.data.db.TtsVoiceAssetAggregate
import com.astrbot.android.data.db.TtsVoiceAssetAggregateDao
import com.astrbot.android.data.db.TtsVoiceAssetEntity
import com.astrbot.android.data.db.TtsVoiceAssetWriteModel
import com.astrbot.android.data.db.TtsVoiceClipEntity
import com.astrbot.android.data.db.TtsVoiceProviderBindingEntity
import com.astrbot.android.feature.plugin.runtime.PluginRuntimeCompatRepositoryHarness
import com.astrbot.android.feature.qq.data.NapCatLoginRepository
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.PersonaProfile
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.SavedQqAccount
import com.astrbot.android.model.chat.ConversationSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

internal object RepositoryCompatibilityTestHarness {
    private var installed = false
    private var installing = false

    fun ensureInstalled() {
        if (installed) {
            installBackupPortOverrideIfAbsent()
            return
        }
        if (installing) return
        installing = true
        try {
            PluginRuntimeCompatRepositoryHarness.ensureInstalled()
            installing = false
            installed = true
            installBackupPortOverrideIfAbsent()
        } finally {
            installing = false
        }
    }

    private fun installBackupPortOverrideIfAbsent() {
        installTtsAssetRepositoryForBackupTests()
        if (!AppBackupRepository.hasDataPortOverrideForTests()) {
            AppBackupRepository.setDataPortOverrideForTests(CompatibilityAppBackupDataPort)
        }
    }

    private fun installTtsAssetRepositoryForBackupTests() {
        val existing = runCatching { TtsVoiceAssetRepository.snapshotAssets() }.getOrNull()
        if (existing != null) return
        val repository = TtsVoiceAssetRepository(
            appContext = CompatibilityContext,
            assetDao = InMemoryTtsVoiceAssetAggregateDao(),
        )
        val field = runCatching {
            TtsVoiceAssetRepository::class.java.getDeclaredField("graphInstance")
        }.getOrElse {
            TtsVoiceAssetRepository.Companion::class.java.getDeclaredField("graphInstance")
        }
        field.isAccessible = true
        field.set(TtsVoiceAssetRepository.Companion, repository)
    }
}

private object CompatibilityAppBackupDataPort : AppBackupDataPort {
    override fun snapshotBots(): List<BotProfile> = BotRepository.snapshotProfiles()

    override fun snapshotProviders(): List<ProviderProfile> = ProviderRepository.snapshotProfiles()

    override fun snapshotPersonas(): List<PersonaProfile> = PersonaRepository.snapshotProfiles()

    override fun snapshotConfigs(): List<ConfigProfile> = ConfigRepository.snapshotProfiles()

    override fun snapshotConversations(): List<ConversationSession> = ConversationRepository.snapshotSessions()

    override fun snapshotExternalState(): AppBackupExternalState {
        val loginState = NapCatLoginRepository.loginState.value
        return AppBackupExternalState(
            selectedBotId = BotRepository.selectedBotId.value,
            selectedConfigId = ConfigRepository.selectedProfileId.value,
            quickLoginUin = loginState.quickLoginUin,
            savedAccounts = loginState.savedAccounts,
        )
    }

    override suspend fun restoreBots(profiles: List<BotProfile>, selectedBotId: String) {
        BotRepository.restoreProfiles(profiles, selectedBotId)
    }

    override fun restoreProviders(profiles: List<ProviderProfile>) {
        ProviderRepository.restoreProfiles(profiles)
    }

    override fun restorePersonas(profiles: List<PersonaProfile>) {
        PersonaRepository.restoreProfiles(profiles)
    }

    override fun restoreConfigs(profiles: List<ConfigProfile>, selectedConfigId: String) {
        ConfigRepository.restoreProfiles(profiles, selectedConfigId)
        waitUntilCompat("config backup restore should settle") {
            ConfigRepository.selectedProfileId.value == selectedConfigId &&
                ConfigRepository.snapshotProfiles().map { profile -> profile.id } == profiles.map { profile -> profile.id }
        }
    }

    override suspend fun restoreConversations(sessions: List<ConversationSession>) {
        ConversationRepository.restoreSessionsDurable(sessions)
    }

    override fun restoreQqLoginState(quickLoginUin: String, savedAccounts: List<SavedQqAccount>) {
        NapCatLoginRepository.restoreSavedLoginState(
            quickLoginUin = quickLoginUin,
            savedAccounts = savedAccounts,
        )
    }
}

private fun waitUntilCompat(
    message: String,
    timeoutMs: Long = 2_000L,
    condition: () -> Boolean,
) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        if (condition()) return
        Thread.sleep(10)
    }
    error(message)
}

private object CompatibilityContext : ContextWrapper(null) {
    private val root = File(System.getProperty("java.io.tmpdir"), "elymbot-compat-context").apply { mkdirs() }
    private val preferences = mutableMapOf<String, SharedPreferences>()

    override fun getFilesDir(): File = root

    override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
        return preferences.getOrPut(name.orEmpty()) { CompatibilitySharedPreferences() }
    }
}

private class InMemoryTtsVoiceAssetAggregateDao : TtsVoiceAssetAggregateDao() {
    private val assets = linkedMapOf<String, TtsVoiceAssetEntity>()
    private val clips = mutableListOf<TtsVoiceClipEntity>()
    private val bindings = mutableListOf<TtsVoiceProviderBindingEntity>()
    private val flow = MutableStateFlow<List<TtsVoiceAssetAggregate>>(emptyList())

    override fun observeAssetAggregates(): Flow<List<TtsVoiceAssetAggregate>> = flow

    override suspend fun listAssetAggregates(): List<TtsVoiceAssetAggregate> = snapshot()

    override suspend fun upsertAssets(entities: List<TtsVoiceAssetEntity>) {
        entities.forEach { entity -> assets[entity.id] = entity }
        refresh()
    }

    override suspend fun upsertClips(entities: List<TtsVoiceClipEntity>) {
        entities.forEach { entity ->
            clips.removeAll { it.id == entity.id }
            clips += entity
        }
        refresh()
    }

    override suspend fun upsertProviderBindings(entities: List<TtsVoiceProviderBindingEntity>) {
        entities.forEach { entity ->
            bindings.removeAll { it.id == entity.id }
            bindings += entity
        }
        refresh()
    }

    override suspend fun deleteMissingAssets(ids: List<String>) {
        assets.keys.toList().filterNot(ids::contains).forEach(assets::remove)
        refresh()
    }

    override suspend fun clearAssets() {
        assets.clear()
        clips.clear()
        bindings.clear()
        refresh()
    }

    override suspend fun deleteClipsForAssets(assetIds: List<String>) {
        clips.removeAll { it.assetId in assetIds }
        refresh()
    }

    override suspend fun deleteBindingsForAssets(assetIds: List<String>) {
        bindings.removeAll { it.assetId in assetIds }
        refresh()
    }

    override suspend fun count(): Int = assets.size

    override suspend fun replaceAll(writeModels: List<TtsVoiceAssetWriteModel>) {
        assets.clear()
        clips.clear()
        bindings.clear()
        writeModels.forEach { model ->
            assets[model.asset.id] = model.asset
            clips += model.clips
            bindings += model.providerBindings
        }
        refresh()
    }

    private fun refresh() {
        flow.value = snapshot()
    }

    private fun snapshot(): List<TtsVoiceAssetAggregate> {
        return assets.values
            .sortedByDescending(TtsVoiceAssetEntity::createdAt)
            .map { asset ->
                TtsVoiceAssetAggregate(
                    asset = asset,
                    clips = clips.filter { it.assetId == asset.id },
                    providerBindings = bindings.filter { it.assetId == asset.id },
                )
            }
    }
}

private class CompatibilitySharedPreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()
    override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        (values[key] as? Set<String>)?.toMutableSet() ?: defValues
    override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
    override fun contains(key: String?): Boolean = values.containsKey(key)
    override fun edit(): SharedPreferences.Editor = Editor()
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    private inner class Editor : SharedPreferences.Editor {
        private val staged = mutableMapOf<String, Any?>()
        private var clear = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply { staged[key.orEmpty()] = value }
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor =
            apply { staged[key.orEmpty()] = values?.toSet() }
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply { staged[key.orEmpty()] = value }
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply { staged[key.orEmpty()] = value }
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply { staged[key.orEmpty()] = value }
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply { staged[key.orEmpty()] = value }
        override fun remove(key: String?): SharedPreferences.Editor = apply { staged[key.orEmpty()] = null }
        override fun clear(): SharedPreferences.Editor = apply { clear = true }
        override fun commit(): Boolean {
            apply()
            return true
        }
        override fun apply() {
            if (clear) values.clear()
            staged.forEach { (key, value) ->
                if (value == null) values.remove(key) else values[key] = value
            }
        }
    }
}
