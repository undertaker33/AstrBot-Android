package com.astrbot.android.feature.config.data

import android.content.SharedPreferences
import com.astrbot.android.core.common.logging.AppLogger
import com.astrbot.android.data.LegacyConfigImport
import com.astrbot.android.data.db.AppPreferenceDao
import com.astrbot.android.data.db.AppPreferenceEntity
import com.astrbot.android.data.db.ConfigAggregateDao
import com.astrbot.android.data.db.toProfile
import com.astrbot.android.data.db.toWriteModel
import com.astrbot.android.data.parseLegacyConfigProfiles
import com.astrbot.android.model.ConfigProfile
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Deprecated("Use ConfigRepositoryPort from feature/config/domain. Direct access will be removed.")
object FeatureConfigRepository {
    private const val KEY_PROFILES_JSON = "profiles_json"
    private const val KEY_SELECTED_ID = "selected_id"
    const val PREF_SELECTED_PROFILE_ID = "selected_config_profile_id"
    const val DEFAULT_CONFIG_ID = "default"

    @Volatile
    private var delegate: FeatureConfigRepositoryStore? = null

    internal fun installDelegate(store: FeatureConfigRepositoryStore) {
        delegate = store
    }

    private fun repository(): FeatureConfigRepositoryStore {
        return checkNotNull(delegate) {
            "FeatureConfigRepository was accessed before the Hilt graph created FeatureConfigRepositoryStore."
        }
    }

    val profiles: StateFlow<List<ConfigProfile>>
        get() = repository().profiles

    val selectedProfileId: StateFlow<String>
        get() = repository().selectedProfileId

    fun select(profileId: String) = repository().select(profileId)

    fun save(profile: ConfigProfile): ConfigProfile = repository().save(profile)

    fun create(name: String = "New Config"): ConfigProfile = repository().create(name)

    fun delete(profileId: String): String = repository().delete(profileId)

    fun resolve(profileId: String): ConfigProfile = repository().resolve(profileId)

    fun resolveExistingId(profileId: String?): String = repository().resolveExistingId(profileId)

    fun snapshotProfiles(): List<ConfigProfile> = repository().snapshotProfiles()

    fun restoreProfiles(
        profiles: List<ConfigProfile>,
        selectedProfileId: String?,
    ) = repository().restoreProfiles(profiles, selectedProfileId)

    internal fun legacyProfilesJson(preferences: SharedPreferences): String? =
        preferences.getString(KEY_PROFILES_JSON, null)

    internal fun legacySelectedId(preferences: SharedPreferences): String? =
        preferences.getString(KEY_SELECTED_ID, DEFAULT_CONFIG_ID)
}

@Singleton
class FeatureConfigRepositoryStore @Inject constructor(
    private val configProfileDao: ConfigAggregateDao,
    private val appPreferenceDao: AppPreferenceDao,
    @Named("configProfilesPreferences") private val preferences: SharedPreferences,
) {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()
    private var syncJob: Job? = null

    private val _profiles = MutableStateFlow(defaultProfiles())
    private val _selectedProfileId = MutableStateFlow(FeatureConfigRepository.DEFAULT_CONFIG_ID)

    val profiles: StateFlow<List<ConfigProfile>> = _profiles.asStateFlow()
    val selectedProfileId: StateFlow<String> = _selectedProfileId.asStateFlow()

    init {
        FeatureConfigRepository.installDelegate(this)
        repositoryScope.launch {
            writeMutex.withLock {
                seedStorageIfNeeded()
            }
        }
        startStateSync()
    }

    fun select(profileId: String) {
        val resolvedId = resolveExistingId(profileId)
        repositoryScope.launch {
            writeMutex.withLock {
                persistSelectedProfileId(resolvedId)
            }
        }
        AppLogger.append("Config profile selected: $resolvedId")
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
        val resolvedSelected = updatedProfiles.firstOrNull { it.id == updatedSelected }?.id ?: normalized.id
        persistStateAsync(updatedProfiles, resolvedSelected)
        AppLogger.append(
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
            return FeatureConfigRepository.DEFAULT_CONFIG_ID
        }
        val updatedProfiles = _profiles.value.filterNot { it.id == profileId }
        val updatedSelected = if (_selectedProfileId.value == profileId) {
            updatedProfiles.firstOrNull()?.id ?: FeatureConfigRepository.DEFAULT_CONFIG_ID
        } else {
            resolveExistingId(_selectedProfileId.value)
        }
        persistStateAsync(updatedProfiles, updatedSelected)
        AppLogger.append("Config profile deleted: $profileId")
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
            else -> _profiles.value.firstOrNull()?.id ?: FeatureConfigRepository.DEFAULT_CONFIG_ID
        }
    }

    fun snapshotProfiles(): List<ConfigProfile> {
        return snapshotProfiles(_profiles.value)
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
        persistStateAsync(restored, resolvedSelected)
        AppLogger.append("Config profiles restored: count=${restored.size} selected=$resolvedSelected")
    }

    private fun snapshotProfiles(profiles: List<ConfigProfile>): List<ConfigProfile> {
        return profiles.map { profile ->
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

    private fun startStateSync() {
        syncJob?.cancel()
        syncJob = repositoryScope.launch {
            combine(
                configProfileDao.observeConfigAggregates(),
                appPreferenceDao.observeValue(FeatureConfigRepository.PREF_SELECTED_PROFILE_ID),
            ) { entities, selectedId ->
                entities to selectedId
            }.collect { (entities, selectedId) ->
                val loaded = entities.map { entity -> normalizeProfile(entity.toProfile()) }.ifEmpty { defaultProfiles() }
                _profiles.value = loaded
                _selectedProfileId.value = resolveExistingId(selectedId)
                AppLogger.append("Config profiles loaded: count=${loaded.size}")
            }
        }
    }

    private fun persistStateAsync(
        profiles: List<ConfigProfile>,
        selectedId: String,
    ) {
        val snapshot = snapshotProfiles(profiles)
        repositoryScope.launch {
            writeMutex.withLock {
                persistState(snapshot, selectedId)
            }
        }
    }

    private suspend fun persistState(
        profiles: List<ConfigProfile>,
        selectedId: String,
    ) {
        if (profiles.isEmpty()) {
            configProfileDao.replaceAll(emptyList())
        } else {
            configProfileDao.replaceAll(
                profiles.mapIndexed { index, profile -> profile.toWriteModel(sortIndex = index) },
            )
        }
        appPreferenceDao.upsert(
            AppPreferenceEntity(
                key = FeatureConfigRepository.PREF_SELECTED_PROFILE_ID,
                value = selectedId,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun persistSelectedProfileId(selectedId: String) {
        appPreferenceDao.upsert(
            AppPreferenceEntity(
                key = FeatureConfigRepository.PREF_SELECTED_PROFILE_ID,
                value = selectedId,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun seedStorageIfNeeded() {
        val imported = runCatching {
            parseLegacyConfigProfiles(
                rawProfilesJson = FeatureConfigRepository.legacyProfilesJson(preferences),
                rawSelectedId = FeatureConfigRepository.legacySelectedId(preferences),
            )
        }.onFailure { error ->
            AppLogger.append("Config profiles legacy import failed: ${error.message ?: error.javaClass.simpleName}")
        }.getOrElse {
            LegacyConfigImport(emptyList(), FeatureConfigRepository.DEFAULT_CONFIG_ID)
        }

        if (configProfileDao.count() == 0) {
            val seededProfiles = imported.profiles.map(::normalizeProfile).ifEmpty { defaultProfiles() }
            configProfileDao.replaceAll(
                seededProfiles.mapIndexed { index, profile -> profile.toWriteModel(sortIndex = index) },
            )
            AppLogger.append(
                if (imported.profiles.isNotEmpty()) {
                    "Config profiles migrated from SharedPreferences: count=${seededProfiles.size}"
                } else {
                    "Config profiles seeded with defaults: count=${seededProfiles.size}"
                },
            )
        }

        if (appPreferenceDao.getValue(FeatureConfigRepository.PREF_SELECTED_PROFILE_ID).isNullOrBlank()) {
            val availableIds = configProfileDao.listConfigAggregates().map { it.config.id }
            val resolvedSelected = when {
                imported.selectedProfileId != null && availableIds.contains(imported.selectedProfileId) -> imported.selectedProfileId
                availableIds.isNotEmpty() -> availableIds.first()
                else -> FeatureConfigRepository.DEFAULT_CONFIG_ID
            }
            appPreferenceDao.upsert(
                AppPreferenceEntity(
                    key = FeatureConfigRepository.PREF_SELECTED_PROFILE_ID,
                    value = resolvedSelected,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    private fun normalizeProfile(profile: ConfigProfile): ConfigProfile {
        return profile.copy(
            id = profile.id.ifBlank { FeatureConfigRepository.DEFAULT_CONFIG_ID },
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
                id = FeatureConfigRepository.DEFAULT_CONFIG_ID,
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
