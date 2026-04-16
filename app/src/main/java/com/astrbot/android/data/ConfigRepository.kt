package com.astrbot.android.data

import android.content.Context
import android.content.SharedPreferences
import com.astrbot.android.data.db.AppPreferenceDao
import com.astrbot.android.data.db.AppPreferenceEntity
import com.astrbot.android.data.db.AstrBotDatabase
import com.astrbot.android.data.db.ConfigAggregate
import com.astrbot.android.data.db.ConfigAggregateDao
import com.astrbot.android.data.db.ConfigProfileEntity
import com.astrbot.android.data.db.toProfile
import com.astrbot.android.data.db.toWriteModel
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.runtime.RuntimeLogRepository
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

object ConfigRepository {
    private const val PREFS_NAME = "config_profiles"
    private const val KEY_PROFILES_JSON = "profiles_json"
    private const val KEY_SELECTED_ID = "selected_id"
    private const val PREF_SELECTED_PROFILE_ID = "selected_config_profile_id"
    const val DEFAULT_CONFIG_ID = "default"

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initialized = AtomicBoolean(false)

    private var preferences: SharedPreferences? = null
    private var configProfileDao: ConfigAggregateDao = ConfigProfileDaoPlaceholder.instance
    private var appPreferenceDao: AppPreferenceDao = ConfigAppPreferenceDaoPlaceholder.instance

    private val _profiles = MutableStateFlow(defaultProfiles())
    private val _selectedProfileId = MutableStateFlow(DEFAULT_CONFIG_ID)

    val profiles: StateFlow<List<ConfigProfile>> = _profiles.asStateFlow()
    val selectedProfileId: StateFlow<String> = _selectedProfileId.asStateFlow()

    fun initialize(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val database = AstrBotDatabase.get(context)
        configProfileDao = database.configAggregateDao()
        appPreferenceDao = database.appPreferenceDao()

        runBlocking(Dispatchers.IO) {
            seedStorageIfNeeded()
        }
        repositoryScope.launch {
            combine(
                configProfileDao.observeConfigAggregates(),
                appPreferenceDao.observeValue(PREF_SELECTED_PROFILE_ID),
            ) { entities, selectedId ->
                entities to selectedId
            }.collect { (entities, selectedId) ->
                val loaded = entities.map { entity -> normalizeProfile(entity.toProfile()) }.ifEmpty { defaultProfiles() }
                _profiles.value = loaded
                _selectedProfileId.value = resolveExistingId(selectedId)
                RuntimeLogRepository.append("Config profiles loaded: count=${loaded.size}")
            }
        }
    }

    fun select(profileId: String) {
        val resolvedId = resolveExistingId(profileId)
        _selectedProfileId.value = resolvedId
        persistSelectedProfileId(resolvedId)
        RuntimeLogRepository.append("Config profile selected: $resolvedId")
    }

    fun save(profile: ConfigProfile): ConfigProfile {
        val normalized = normalizeProfile(
            profile.copy(
                id = profile.id.ifBlank { UUID.randomUUID().toString() },
            ),
        )
        val exists = _profiles.value.any { it.id == normalized.id }
        val updatedProfiles = if (exists) {
            _profiles.value.map { if (it.id == normalized.id) normalized else it }
        } else {
            _profiles.value + normalized
        }
        val updatedSelected = if (_selectedProfileId.value.isBlank()) normalized.id else _selectedProfileId.value
        _profiles.value = updatedProfiles
        _selectedProfileId.value = resolveExistingId(updatedSelected)
        persistState(updatedProfiles, _selectedProfileId.value)
        RuntimeLogRepository.append(
            if (exists) "Config profile updated: ${normalized.name}" else "Config profile created: ${normalized.name}",
        )
        return normalized
    }

    fun create(name: String = "New Config"): ConfigProfile {
        val created = ConfigProfile(
            id = UUID.randomUUID().toString(),
            name = name,
        )
        return save(created)
    }

    fun delete(profileId: String): String {
        if (_profiles.value.size == 1) {
            return DEFAULT_CONFIG_ID
        }
        val updatedProfiles = _profiles.value.filterNot { it.id == profileId }
        val updatedSelected = if (_selectedProfileId.value == profileId) {
            updatedProfiles.firstOrNull()?.id ?: DEFAULT_CONFIG_ID
        } else {
            resolveExistingId(_selectedProfileId.value)
        }
        _profiles.value = updatedProfiles
        _selectedProfileId.value = updatedSelected
        persistState(updatedProfiles, updatedSelected)
        RuntimeLogRepository.append("Config profile deleted: $profileId")
        return updatedSelected
    }

    fun resolve(profileId: String): ConfigProfile {
        return _profiles.value.firstOrNull { it.id == profileId }
            ?: _profiles.value.firstOrNull()
            ?: defaultProfiles().first()
    }

    fun resolveExistingId(profileId: String?): String {
        val requestedId = profileId?.takeIf { it.isNotBlank() }
        return when {
            requestedId != null && _profiles.value.any { it.id == requestedId } -> requestedId
            else -> _profiles.value.firstOrNull()?.id ?: DEFAULT_CONFIG_ID
        }
    }

    fun snapshotProfiles(): List<ConfigProfile> {
        return _profiles.value.map { profile ->
            profile.copy(
                adminUids = profile.adminUids.toList(),
                wakeWords = profile.wakeWords.toList(),
                whitelistEntries = profile.whitelistEntries.toList(),
                keywordPatterns = profile.keywordPatterns.toList(),
                mcpServers = profile.mcpServers.toList(),
                skills = profile.skills.toList(),
            )
        }
    }

    fun restoreProfiles(
        profiles: List<ConfigProfile>,
        selectedProfileId: String?,
    ) {
        val restored = profiles
            .map(::normalizeProfile)
            .distinctBy { it.id }
            .ifEmpty { defaultProfiles() }
        val resolvedSelected = restored.firstOrNull { it.id == selectedProfileId }?.id ?: restored.first().id
        _profiles.value = restored
        _selectedProfileId.value = resolvedSelected
        persistState(restored, resolvedSelected)
        RuntimeLogRepository.append("Config profiles restored: count=${restored.size} selected=$resolvedSelected")
    }

    private fun persistState(
        profiles: List<ConfigProfile>,
        selectedId: String,
    ) {
        runBlocking(Dispatchers.IO) {
            if (profiles.isEmpty()) {
                configProfileDao.replaceAll(emptyList())
            } else {
                configProfileDao.replaceAll(
                    profiles.mapIndexed { index, profile -> profile.toWriteModel(sortIndex = index) },
                )
            }
            appPreferenceDao.upsert(
                AppPreferenceEntity(
                    key = PREF_SELECTED_PROFILE_ID,
                    value = selectedId,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    private fun persistSelectedProfileId(selectedId: String) {
        runBlocking(Dispatchers.IO) {
            appPreferenceDao.upsert(
                AppPreferenceEntity(
                    key = PREF_SELECTED_PROFILE_ID,
                    value = selectedId,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    private suspend fun seedStorageIfNeeded() {
        val imported = runCatching {
            parseLegacyConfigProfiles(
                rawProfilesJson = preferences?.getString(KEY_PROFILES_JSON, null),
                rawSelectedId = preferences?.getString(KEY_SELECTED_ID, DEFAULT_CONFIG_ID),
            )
        }.onFailure { error ->
            RuntimeLogRepository.append("Config profiles legacy import failed: ${error.message ?: error.javaClass.simpleName}")
        }.getOrElse {
            LegacyConfigImport(emptyList(), DEFAULT_CONFIG_ID)
        }

        if (configProfileDao.count() == 0) {
            val seededProfiles = imported.profiles.map(::normalizeProfile).ifEmpty { defaultProfiles() }
            configProfileDao.replaceAll(
                seededProfiles.mapIndexed { index, profile -> profile.toWriteModel(sortIndex = index) },
            )
            RuntimeLogRepository.append(
                if (imported.profiles.isNotEmpty()) {
                    "Config profiles migrated from SharedPreferences: count=${seededProfiles.size}"
                } else {
                    "Config profiles seeded with defaults: count=${seededProfiles.size}"
                },
            )
        }

        if (appPreferenceDao.getValue(PREF_SELECTED_PROFILE_ID).isNullOrBlank()) {
            val availableIds = configProfileDao.listConfigAggregates().map { it.config.id }
            val resolvedSelected = when {
                imported.selectedProfileId != null && availableIds.contains(imported.selectedProfileId) -> imported.selectedProfileId
                availableIds.isNotEmpty() -> availableIds.first()
                else -> DEFAULT_CONFIG_ID
            }
            appPreferenceDao.upsert(
                AppPreferenceEntity(
                    key = PREF_SELECTED_PROFILE_ID,
                    value = resolvedSelected,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    private fun normalizeProfile(profile: ConfigProfile): ConfigProfile {
        return profile.copy(
            id = profile.id.ifBlank { DEFAULT_CONFIG_ID },
            name = profile.name.trim().ifBlank { "Unnamed Config" },
            streamingMessageIntervalMs = profile.streamingMessageIntervalMs.coerceIn(0, 5000),
            imageCaptionPrompt = profile.imageCaptionPrompt.trim().ifBlank { defaultProfiles().first().imageCaptionPrompt },
            adminUids = profile.adminUids.normalizeStringList(),
            wakeWords = profile.wakeWords.normalizeStringList(),
            replyTextPrefix = profile.replyTextPrefix.trim(),
            whitelistEntries = profile.whitelistEntries.normalizeStringList(),
            rateLimitWindowSeconds = profile.rateLimitWindowSeconds.coerceAtLeast(0),
            rateLimitMaxCount = profile.rateLimitMaxCount.coerceAtLeast(0),
            rateLimitStrategy = profile.rateLimitStrategy.takeIf { it == "drop" || it == "stash" } ?: "drop",
            keywordPatterns = profile.keywordPatterns.normalizeStringList(),
            contextLimitStrategy = profile.contextLimitStrategy.takeIf { it == "truncate_by_turns" || it == "llm_compress" } ?: "truncate_by_turns",
            dequeueContextTurns = profile.dequeueContextTurns.coerceAtLeast(1),
            llmCompressKeepRecent = profile.llmCompressKeepRecent.coerceAtLeast(0),
        )
    }

    private fun defaultProfiles(): List<ConfigProfile> {
        return listOf(
            ConfigProfile(
                id = DEFAULT_CONFIG_ID,
                name = "Default Config",
            ),
        )
    }

    private fun List<String>.normalizeStringList(): List<String> {
        return asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
    }
}

private object ConfigProfileDaoPlaceholder {
    val instance = object : ConfigAggregateDao() {
        override fun observeConfigAggregates() = flowOf(emptyList<ConfigAggregate>())
        override suspend fun listConfigAggregates(): List<ConfigAggregate> = emptyList()
        override suspend fun upsertConfigs(entities: List<ConfigProfileEntity>) = Unit
        override suspend fun upsertAdminUids(entities: List<com.astrbot.android.data.db.ConfigAdminUidEntity>) = Unit
        override suspend fun upsertWakeWords(entities: List<com.astrbot.android.data.db.ConfigWakeWordEntity>) = Unit
        override suspend fun upsertWhitelistEntries(entities: List<com.astrbot.android.data.db.ConfigWhitelistEntryEntity>) = Unit
        override suspend fun upsertKeywordPatterns(entities: List<com.astrbot.android.data.db.ConfigKeywordPatternEntity>) = Unit
        override suspend fun upsertTextRules(entities: List<com.astrbot.android.data.db.ConfigTextRuleEntity>) = Unit
        override suspend fun deleteMissingConfigs(ids: List<String>) = Unit
        override suspend fun clearConfigs() = Unit
        override suspend fun deleteAdminUids(configIds: List<String>) = Unit
        override suspend fun deleteWakeWords(configIds: List<String>) = Unit
        override suspend fun deleteWhitelistEntries(configIds: List<String>) = Unit
        override suspend fun deleteKeywordPatterns(configIds: List<String>) = Unit
        override suspend fun deleteTextRules(configIds: List<String>) = Unit
        override suspend fun upsertMcpServers(entities: List<com.astrbot.android.data.db.ConfigMcpServerEntity>) = Unit
        override suspend fun upsertSkills(entities: List<com.astrbot.android.data.db.ConfigSkillEntity>) = Unit
        override suspend fun deleteMcpServers(configIds: List<String>) = Unit
        override suspend fun deleteSkills(configIds: List<String>) = Unit
        override suspend fun count(): Int = 0
    }
}

private object ConfigAppPreferenceDaoPlaceholder {
    val instance = object : AppPreferenceDao {
        override fun observeValue(key: String) = flowOf<String?>(null)

        override suspend fun getValue(key: String): String? = null

        override suspend fun upsert(entity: AppPreferenceEntity) = Unit
    }
}
