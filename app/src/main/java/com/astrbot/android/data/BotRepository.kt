package com.astrbot.android.data

import android.content.Context
import android.content.SharedPreferences
import com.astrbot.android.data.db.AppPreferenceDao
import com.astrbot.android.data.db.AppPreferenceEntity
import com.astrbot.android.data.db.AstrBotDatabase
import com.astrbot.android.data.db.BotAggregate
import com.astrbot.android.data.db.BotAggregateDao
import com.astrbot.android.data.db.BotEntity
import com.astrbot.android.data.db.toProfile
import com.astrbot.android.data.db.toWriteModel
import com.astrbot.android.model.BotProfile
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
import kotlinx.coroutines.withContext

object BotRepository {
    private const val BOT_BINDINGS_PREFS_NAME = "bot_bindings"
    private const val KEY_BOT_BINDINGS_JSON = "bot_bindings_json"
    private const val PREF_SELECTED_BOT_ID = "selected_bot_id"
    private const val PREF_LEGACY_BOT_BINDINGS_MIGRATED = "legacy_bot_bindings_migrated"

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initialized = AtomicBoolean(false)

    private val defaultBot = BotProfile(
        id = "qq-main",
        displayName = "Primary Bot",
        tag = "Default",
        accountHint = "QQ account not linked",
        triggerWords = listOf("astrbot", "assistant"),
        configProfileId = ConfigRepository.DEFAULT_CONFIG_ID,
    )

    private val _botProfiles = MutableStateFlow(listOf(defaultBot))
    private val _selectedBotId = MutableStateFlow(defaultBot.id)
    private val _botProfile = MutableStateFlow(defaultBot)

    private var botDao: BotAggregateDao = AstrBotAggregateDaoHolder.placeholder
    private var appPreferenceDao: AppPreferenceDao = BotAppPreferenceDaoPlaceholder.instance
    private var bindingsPreferences: SharedPreferences? = null

    val botProfile: StateFlow<BotProfile> = _botProfile.asStateFlow()
    val botProfiles: StateFlow<List<BotProfile>> = _botProfiles.asStateFlow()
    val selectedBotId: StateFlow<String> = _selectedBotId.asStateFlow()

    fun initialize(context: Context) {
        if (!initialized.compareAndSet(false, true)) return

        val database = AstrBotDatabase.get(context)
        botDao = database.botAggregateDao()
        appPreferenceDao = database.appPreferenceDao()
        bindingsPreferences = context.applicationContext.getSharedPreferences(BOT_BINDINGS_PREFS_NAME, Context.MODE_PRIVATE)

        runBlocking(Dispatchers.IO) {
            seedStorageIfNeeded()
            migrateLegacyBindingsIfNeeded()
        }
        repositoryScope.launch {
            combine(
                botDao.observeBotAggregates(),
                appPreferenceDao.observeValue(PREF_SELECTED_BOT_ID),
            ) { entities, selectedId -> entities to selectedId }
                .collect { (entities, selectedId) ->
                    val profiles = entities.map { entity -> normalizeProfile(entity.toProfile()) }.ifEmpty { listOf(defaultBot) }
                    _botProfiles.value = profiles
                    val currentSelected = profiles.firstOrNull { it.id == selectedId } ?: profiles.first()
                    _selectedBotId.value = currentSelected.id
                    _botProfile.value = currentSelected
                    RuntimeLogRepository.append("Bot database synced: count=${profiles.size} selected=${currentSelected.id}")
                }
        }
    }

    fun select(botId: String) {
        val selected = _botProfiles.value.firstOrNull { it.id == botId } ?: return
        _selectedBotId.value = selected.id
        _botProfile.value = selected
        persistSelectedBotId(selected.id)
        RuntimeLogRepository.append("Bot selected: ${selected.displayName} (${selected.id})")
    }

    fun save(profile: BotProfile) {
        repositoryScope.launch {
            val normalized = normalizeProfile(profile)
            val updated = _botProfiles.value.map { if (it.id == normalized.id) normalized else it }
                .let { current -> if (current.any { it.id == normalized.id }) current else current + normalized }
            _botProfiles.value = updated
            persistProfiles(updated, normalized.id)
            ConversationRepository.syncPersistenceForBot(normalized.id, normalized.persistConversationLocally)
            RuntimeLogRepository.append(
                "Bot saved: ${normalized.displayName}, provider=${normalized.defaultProviderId.ifBlank { "none" }}, persona=${normalized.defaultPersonaId.ifBlank { "none" }}, config=${normalized.configProfileId}, qqBindings=${normalized.boundQqUins.size}, persist=${normalized.persistConversationLocally}",
            )
            select(normalized.id)
        }
    }

    fun create(name: String = "New Bot"): BotProfile {
        val created = BotProfile(
            id = "bot-${UUID.randomUUID()}",
            displayName = name,
            configProfileId = ConfigRepository.resolveExistingId(ConfigRepository.selectedProfileId.value),
            defaultProviderId = ConfigRepository.resolveExistingId(ConfigRepository.selectedProfileId.value)
                .let { ConfigRepository.resolve(it).defaultChatProviderId },
        )
        repositoryScope.launch {
            val updated = _botProfiles.value + created
            _botProfiles.value = updated
            persistProfiles(updated, created.id)
            RuntimeLogRepository.append("Bot created: ${created.displayName} (${created.id})")
            select(created.id)
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
        val normalized = profiles
            .map(::normalizeProfile)
            .distinctBy { it.id }
            .ifEmpty { listOf(defaultBot) }
        val resolvedSelected = normalized.firstOrNull { it.id == selectedBotId }?.id ?: normalized.first().id
        _botProfiles.value = normalized
        _selectedBotId.value = resolvedSelected
        _botProfile.value = normalized.first { it.id == resolvedSelected }
        persistProfiles(normalized, resolvedSelected)
        RuntimeLogRepository.append("Bot profiles restored: count=${normalized.size} selected=$resolvedSelected")
    }

    fun delete(botId: String) {
        val removed = _botProfiles.value.firstOrNull { it.id == botId } ?: return
        ProfileDeletionGuard.requireCanDelete(
            remainingCount = _botProfiles.value.size,
            kind = ProfileCatalogKind.BOT,
        )
        repositoryScope.launch {
            val updated = _botProfiles.value.filterNot { it.id == botId }
            val selectedId = if (_selectedBotId.value == botId) updated.first().id else _selectedBotId.value
            _botProfiles.value = updated
            _selectedBotId.value = selectedId
            _botProfile.value = updated.first { it.id == selectedId }
            persistProfiles(updated, selectedId)
            ConversationRepository.deleteSessionsForBot(botId)
            RuntimeLogRepository.append("Bot deleted: ${removed.displayName} (${removed.id})")
        }
    }

    fun replaceConfigBinding(deletedConfigId: String, fallbackConfigId: String) {
        val resolvedFallbackId = ConfigRepository.resolveExistingId(fallbackConfigId)
        val updated = _botProfiles.value.map { profile ->
            if (profile.configProfileId == deletedConfigId) {
                profile.copy(
                    configProfileId = resolvedFallbackId,
                    defaultProviderId = ConfigRepository.resolve(resolvedFallbackId).defaultChatProviderId,
                )
            } else {
                profile
            }
        }
        _botProfiles.value = updated
        _botProfile.value = updated.firstOrNull { it.id == _selectedBotId.value } ?: updated.firstOrNull() ?: defaultBot
        repositoryScope.launch {
            persistProfiles(updated, _selectedBotId.value)
        }
        RuntimeLogRepository.append("Bot config bindings reassigned: removed=$deletedConfigId fallback=$resolvedFallbackId")
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
                key = PREF_SELECTED_BOT_ID,
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
                    key = PREF_SELECTED_BOT_ID,
                    value = defaultBot.id,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        } else if (appPreferenceDao.getValue(PREF_SELECTED_BOT_ID).isNullOrBlank()) {
            val firstBotId = botDao.listBotAggregates().firstOrNull()?.bot?.id ?: defaultBot.id
            appPreferenceDao.upsert(
                AppPreferenceEntity(
                    key = PREF_SELECTED_BOT_ID,
                    value = firstBotId,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    private suspend fun migrateLegacyBindingsIfNeeded() {
        if (appPreferenceDao.getValue(PREF_LEGACY_BOT_BINDINGS_MIGRATED) == "true") return
        val legacyRaw = bindingsPreferences?.getString(KEY_BOT_BINDINGS_JSON, null)
        val imported = runCatching { parseLegacyBotBindings(legacyRaw) }.getOrDefault(emptyMap())
        if (imported.isNotEmpty()) {
            val currentProfiles = botDao.listBotAggregates().map { entity -> normalizeProfile(entity.toProfile()) }.ifEmpty { listOf(defaultBot) }
            val updated = currentProfiles.map { current ->
                imported[current.id]?.let { binding ->
                    normalizeProfile(
                        current.copy(
                            configProfileId = ConfigRepository.resolveExistingId(binding.configProfileId),
                            boundQqUins = binding.boundQqUins,
                            persistConversationLocally = binding.persistConversationLocally,
                        ),
                    )
                } ?: current
            }
            _botProfiles.value = updated
            persistProfiles(updated, _selectedBotId.value)
            RuntimeLogRepository.append("Bot bindings migrated from SharedPreferences: count=${imported.size}")
        }
        appPreferenceDao.upsert(
            AppPreferenceEntity(
                key = PREF_LEGACY_BOT_BINDINGS_MIGRATED,
                value = "true",
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    private fun persistSelectedBotId(botId: String) {
        repositoryScope.launch {
            appPreferenceDao.upsert(
                AppPreferenceEntity(
                    key = PREF_SELECTED_BOT_ID,
                    value = botId,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    private fun normalizeProfile(profile: BotProfile): BotProfile {
        val resolvedConfigId = ConfigRepository.resolveExistingId(profile.configProfileId)
        val configDefaultProviderId = ConfigRepository.resolve(resolvedConfigId).defaultChatProviderId
        val normalizedBoundQqUins = profile.boundQqUins.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        return profile.copy(
            accountHint = normalizedBoundQqUins.joinToString(", ").ifBlank { "QQ account not linked" },
            configProfileId = resolvedConfigId,
            boundQqUins = normalizedBoundQqUins,
            defaultProviderId = configDefaultProviderId,
            triggerWords = profile.triggerWords.map(String::trim).filter(String::isNotBlank).distinct(),
        )
    }
}

private object AstrBotAggregateDaoHolder {
    val placeholder = object : BotAggregateDao() {
        override fun observeBotAggregates() = flowOf(emptyList<BotAggregate>())
        override suspend fun listBotAggregates(): List<BotAggregate> = emptyList()
        override suspend fun upsertBots(entities: List<BotEntity>) = Unit
        override suspend fun upsertBoundQqUins(entities: List<com.astrbot.android.data.db.BotBoundQqUinEntity>) = Unit
        override suspend fun upsertTriggerWords(entities: List<com.astrbot.android.data.db.BotTriggerWordEntity>) = Unit
        override suspend fun deleteMissingBots(ids: List<String>) = Unit
        override suspend fun clearBots() = Unit
        override suspend fun deleteBoundQqUins(botIds: List<String>) = Unit
        override suspend fun deleteTriggerWords(botIds: List<String>) = Unit
        override suspend fun count(): Int = 0
    }
}

private object BotAppPreferenceDaoPlaceholder {
    val instance = object : AppPreferenceDao {
        override fun observeValue(key: String) = flowOf<String?>(null)
        override suspend fun getValue(key: String): String? = null
        override suspend fun upsert(entity: AppPreferenceEntity) = Unit
    }
}
