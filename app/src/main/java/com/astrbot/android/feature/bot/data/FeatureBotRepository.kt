
package com.astrbot.android.feature.bot.data

import android.content.SharedPreferences
import com.astrbot.android.core.common.logging.AppLogger
import com.astrbot.android.core.common.profile.ProfileCatalogKind
import com.astrbot.android.core.common.profile.ProfileDeletionGuard
import com.astrbot.android.data.db.AppPreferenceDao
import com.astrbot.android.data.db.AppPreferenceEntity
import com.astrbot.android.data.db.BotAggregateDao
import com.astrbot.android.data.db.toProfile
import com.astrbot.android.data.db.toWriteModel
import com.astrbot.android.data.parseLegacyBotBindings
import com.astrbot.android.feature.config.data.FeatureConfigRepository
import com.astrbot.android.feature.config.data.FeatureConfigRepositoryStore
import com.astrbot.android.model.BotProfile
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Provider
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
import kotlinx.coroutines.withContext

@Deprecated("Use BotRepositoryPort from feature/bot/domain. Direct access will be removed.")
object FeatureBotRepository {
    private const val KEY_BOT_BINDINGS_JSON = "bot_bindings_json"
    private const val PREF_LEGACY_BOT_BINDINGS_MIGRATED = "legacy_bot_bindings_migrated"
    const val PREF_SELECTED_BOT_ID = "selected_bot_id"

    @Volatile
    private var delegate: FeatureBotRepositoryStore? = null

    internal fun installDelegate(store: FeatureBotRepositoryStore) {
        delegate = store
    }

    private fun repository(): FeatureBotRepositoryStore {
        return checkNotNull(delegate) {
            "FeatureBotRepository was accessed before the Hilt graph created FeatureBotRepositoryStore."
        }
    }

    val botProfile: StateFlow<BotProfile>
        get() = repository().botProfile

    val botProfiles: StateFlow<List<BotProfile>>
        get() = repository().botProfiles

    val selectedBotId: StateFlow<String>
        get() = repository().selectedBotId

    fun select(botId: String) = repository().select(botId)

    fun save(profile: BotProfile) = repository().save(profile)

    fun create(name: String = "New Bot"): BotProfile = repository().create(name)

    fun snapshotProfiles(): List<BotProfile> = repository().snapshotProfiles()

    suspend fun restoreProfiles(
        profiles: List<BotProfile>,
        selectedBotId: String?,
    ) = repository().restoreProfiles(profiles, selectedBotId)

    fun delete(botId: String) = repository().delete(botId)

    fun replaceConfigBinding(deletedConfigId: String, fallbackConfigId: String) =
        repository().replaceConfigBinding(deletedConfigId, fallbackConfigId)

    fun resolveBoundBot(selfId: String): BotProfile? = repository().resolveBoundBot(selfId)

    fun shouldPersistConversation(botId: String): Boolean = repository().shouldPersistConversation(botId)

    internal fun legacyBindingsJson(preferences: SharedPreferences): String? =
        preferences.getString(KEY_BOT_BINDINGS_JSON, null)

    internal fun legacyBindingsMigratedPreference(): String = PREF_LEGACY_BOT_BINDINGS_MIGRATED
}

@Singleton
class FeatureBotRepositoryStore @Inject constructor(
    private val botDao: BotAggregateDao,
    private val appPreferenceDao: AppPreferenceDao,
    @Named("botBindingsPreferences") private val bindingsPreferences: SharedPreferences,
    private val configRepositoryProvider: Provider<FeatureConfigRepositoryStore>,
) {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()
    private var syncJob: Job? = null

    private val defaultBot by lazy {
        BotProfile(
            id = "qq-main",
            displayName = "Primary Bot",
            tag = "Default",
            accountHint = "QQ account not linked",
            triggerWords = listOf("astrbot", "assistant"),
            configProfileId = configRepository().resolveExistingId(FeatureConfigRepository.DEFAULT_CONFIG_ID),
        )
    }

    private val _botProfiles = MutableStateFlow<List<BotProfile>>(emptyList())
    private val _selectedBotId = MutableStateFlow(defaultBot.id)
    private val _botProfile = MutableStateFlow(defaultBot)

    val botProfile: StateFlow<BotProfile> = _botProfile.asStateFlow()
    val botProfiles: StateFlow<List<BotProfile>> = _botProfiles.asStateFlow()
    val selectedBotId: StateFlow<String> = _selectedBotId.asStateFlow()

    init {
        FeatureBotRepository.installDelegate(this)
        repositoryScope.launch {
            writeMutex.withLock {
                seedStorageIfNeeded()
                migrateLegacyBindingsIfNeeded()
            }
        }
        startStateSync()
    }

    fun select(botId: String) {
        val selected = _botProfiles.value.firstOrNull { it.id == botId } ?: return
        repositoryScope.launch {
            writeMutex.withLock {
                persistSelectedBotId(selected.id)
            }
        }
        AppLogger.append("Bot selected: ${selected.displayName} (${selected.id})")
    }

    fun save(profile: BotProfile) {
        repositoryScope.launch {
            writeMutex.withLock {
                val normalized = normalizeProfile(profile)
                val updated = _botProfiles.value.map { if (it.id == normalized.id) normalized else it }
                    .let { current -> if (current.any { it.id == normalized.id }) current else current + normalized }
                _botProfiles.value = updated
                _selectedBotId.value = normalized.id
                _botProfile.value = updated.first { it.id == normalized.id }
                persistProfiles(updated, normalized.id)
                AppLogger.append(
                    "Bot saved: ${normalized.displayName}, provider=${normalized.defaultProviderId.ifBlank { "none" }}, persona=${normalized.defaultPersonaId.ifBlank { "none" }}, config=${normalized.configProfileId}, qqBindings=${normalized.boundQqUins.size}, persist=${normalized.persistConversationLocally}",
                )
            }
        }
    }

    fun create(name: String = "New Bot"): BotProfile {
        val resolvedConfigId = configRepository().resolveExistingId(FeatureConfigRepository.DEFAULT_CONFIG_ID)
        val created = BotProfile(
            id = "bot-${UUID.randomUUID()}",
            displayName = name,
            configProfileId = resolvedConfigId,
            defaultProviderId = configRepository().resolve(resolvedConfigId).defaultChatProviderId,
        )
        repositoryScope.launch {
            writeMutex.withLock {
                val updated = _botProfiles.value + created
                _botProfiles.value = updated
                _selectedBotId.value = created.id
                _botProfile.value = created
                persistProfiles(updated, created.id)
                AppLogger.append("Bot created: ${created.displayName} (${created.id})")
            }
        }
        return created
    }

    fun snapshotProfiles(): List<BotProfile> {
        return _botProfiles.value.map { profile ->
            profile.copy(
                boundQqUins = profile.boundQqUins.toList(),
                triggerWords = profile.triggerWords.toList(),
            )
        }
    }

    suspend fun restoreProfiles(
        profiles: List<BotProfile>,
        selectedBotId: String?,
    ) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val normalized = profiles
                .map(::normalizeProfile)
                .distinctBy { it.id }
                .ifEmpty { listOf(defaultBot) }
            val resolvedSelected = normalized.firstOrNull { it.id == selectedBotId }?.id ?: normalized.first().id
            _botProfiles.value = normalized
            _selectedBotId.value = resolvedSelected
            _botProfile.value = normalized.first { it.id == resolvedSelected }
            persistProfiles(normalized, resolvedSelected)
            AppLogger.append("Bot profiles restored: count=${normalized.size} selected=$resolvedSelected")
        }
    }

    fun delete(botId: String) {
        val removed = _botProfiles.value.firstOrNull { it.id == botId } ?: return
        ProfileDeletionGuard.requireCanDelete(
            remainingCount = _botProfiles.value.size,
            kind = ProfileCatalogKind.BOT,
        )
        repositoryScope.launch {
            writeMutex.withLock {
                val updated = _botProfiles.value.filterNot { it.id == botId }
                val selectedId = if (_selectedBotId.value == botId) updated.first().id else _selectedBotId.value
                _botProfiles.value = updated
                _selectedBotId.value = selectedId
                _botProfile.value = updated.first { it.id == selectedId }
                persistProfiles(updated, selectedId)
                AppLogger.append("Bot deleted: ${removed.displayName} (${removed.id})")
            }
        }
    }

    fun replaceConfigBinding(deletedConfigId: String, fallbackConfigId: String) {
        val resolvedFallbackId = configRepository().resolveExistingId(fallbackConfigId)
        val updated = _botProfiles.value.map { profile ->
            if (profile.configProfileId == deletedConfigId) {
                profile.copy(
                    configProfileId = resolvedFallbackId,
                    defaultProviderId = configRepository().resolve(resolvedFallbackId).defaultChatProviderId,
                )
            } else {
                profile
            }
        }
        _botProfiles.value = updated
        _botProfile.value = updated.firstOrNull { it.id == _selectedBotId.value } ?: updated.firstOrNull() ?: defaultBot
        repositoryScope.launch {
            writeMutex.withLock {
                persistProfiles(updated, _selectedBotId.value)
            }
        }
        AppLogger.append("Bot config bindings reassigned: removed=$deletedConfigId fallback=$resolvedFallbackId")
    }

    fun resolveBoundBot(selfId: String): BotProfile? {
        val cleanedSelfId = selfId.trim()
        val enabledBots = _botProfiles.value.filter {
            it.platformName.equals("QQ", ignoreCase = true) && it.autoReplyEnabled
        }
        if (cleanedSelfId.isBlank()) {
            return enabledBots.firstOrNull()
        }
        val selectedBoundBot = enabledBots.firstOrNull { bot ->
            bot.id == _selectedBotId.value && bot.boundQqUins.contains(cleanedSelfId)
        }
        if (selectedBoundBot != null) return selectedBoundBot
        return enabledBots.firstOrNull { bot -> bot.boundQqUins.contains(cleanedSelfId) }
            ?: enabledBots.firstOrNull()
    }

    fun shouldPersistConversation(botId: String): Boolean {
        return _botProfiles.value.firstOrNull { it.id == botId }?.persistConversationLocally == true
    }

    private fun startStateSync() {
        syncJob?.cancel()
        syncJob = repositoryScope.launch {
            combine(
                botDao.observeBotAggregates(),
                appPreferenceDao.observeValue(FeatureBotRepository.PREF_SELECTED_BOT_ID),
            ) { entities, selectedId -> entities to selectedId }
                .collect { (entities, selectedId) ->
                    val profiles = entities.map { entity -> normalizeProfile(entity.toProfile()) }.ifEmpty { listOf(defaultBot) }
                    _botProfiles.value = profiles
                    val currentSelected = profiles.firstOrNull { it.id == selectedId } ?: profiles.first()
                    _selectedBotId.value = currentSelected.id
                    _botProfile.value = currentSelected
                    AppLogger.append("Bot database synced: count=${profiles.size} selected=${currentSelected.id}")
                }
        }
    }

    private suspend fun persistProfiles(
        profiles: List<BotProfile>,
        selectedId: String,
    ) {
        if (profiles.isEmpty()) {
            return
        }
        botDao.replaceAll(profiles.map { profile -> normalizeProfile(profile).toWriteModel() })
        appPreferenceDao.upsert(
            AppPreferenceEntity(
                key = FeatureBotRepository.PREF_SELECTED_BOT_ID,
                value = selectedId,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun seedStorageIfNeeded() {
        if (botDao.count() == 0) {
            botDao.replaceAll(listOf(defaultBot.toWriteModel()))
            appPreferenceDao.upsert(
                AppPreferenceEntity(
                    key = FeatureBotRepository.PREF_SELECTED_BOT_ID,
                    value = defaultBot.id,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        } else if (appPreferenceDao.getValue(FeatureBotRepository.PREF_SELECTED_BOT_ID).isNullOrBlank()) {
            val firstBotId = botDao.listBotAggregates().firstOrNull()?.bot?.id ?: defaultBot.id
            appPreferenceDao.upsert(
                AppPreferenceEntity(
                    key = FeatureBotRepository.PREF_SELECTED_BOT_ID,
                    value = firstBotId,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    private suspend fun migrateLegacyBindingsIfNeeded() {
        if (appPreferenceDao.getValue(FeatureBotRepository.legacyBindingsMigratedPreference()) == "true") return
        val legacyRaw = FeatureBotRepository.legacyBindingsJson(bindingsPreferences)
        val imported = runCatching { parseLegacyBotBindings(legacyRaw) }.getOrDefault(emptyMap())
        if (imported.isNotEmpty()) {
            val currentProfiles = botDao.listBotAggregates().map { entity -> normalizeProfile(entity.toProfile()) }.ifEmpty { listOf(defaultBot) }
            val updated = currentProfiles.map { current ->
                imported[current.id]?.let { binding ->
                    normalizeProfile(
                        current.copy(
                            configProfileId = configRepository().resolveExistingId(binding.configProfileId),
                            boundQqUins = binding.boundQqUins,
                            persistConversationLocally = binding.persistConversationLocally,
                        ),
                    )
                } ?: current
            }
            _botProfiles.value = updated
            persistProfiles(updated, _selectedBotId.value)
            AppLogger.append("Bot bindings migrated from SharedPreferences: count=${imported.size}")
        }
        appPreferenceDao.upsert(
            AppPreferenceEntity(
                key = FeatureBotRepository.legacyBindingsMigratedPreference(),
                value = "true",
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun persistSelectedBotId(botId: String) {
        appPreferenceDao.upsert(
            AppPreferenceEntity(
                key = FeatureBotRepository.PREF_SELECTED_BOT_ID,
                value = botId,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    private fun normalizeProfile(profile: BotProfile): BotProfile {
        val resolvedConfigId = configRepository().resolveExistingId(profile.configProfileId)
        val configDefaultProviderId = configRepository().resolve(resolvedConfigId).defaultChatProviderId
        val normalizedBoundQqUins = profile.boundQqUins.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        return profile.copy(
            accountHint = normalizedBoundQqUins.joinToString(", ").ifBlank { "QQ account not linked" },
            configProfileId = resolvedConfigId,
            boundQqUins = normalizedBoundQqUins,
            defaultProviderId = configDefaultProviderId,
            triggerWords = profile.triggerWords.map(String::trim).filter(String::isNotBlank).distinct(),
        )
    }

    private fun configRepository(): FeatureConfigRepositoryStore = configRepositoryProvider.get()
}
