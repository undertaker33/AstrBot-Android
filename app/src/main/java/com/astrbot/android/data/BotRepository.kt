package com.astrbot.android.data

import android.content.Context
import android.content.SharedPreferences
import com.astrbot.android.data.db.AstrBotDatabase
import com.astrbot.android.data.db.BotDao
import com.astrbot.android.data.db.BotEntity
import com.astrbot.android.data.db.toEntity
import com.astrbot.android.data.db.toProfile
import com.astrbot.android.model.BotProfile
import com.astrbot.android.runtime.RuntimeLogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

object BotRepository {
    private const val BOT_BINDINGS_PREFS_NAME = "bot_bindings"
    private const val KEY_BOT_BINDINGS_JSON = "bot_bindings_json"

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

    private var botDao: BotDao = AstrBotDatabaseHolder.placeholder
    private var bindingsPreferences: SharedPreferences? = null
    private var botBindings: Map<String, BotBindingState> = emptyMap()

    val botProfile: StateFlow<BotProfile> = _botProfile.asStateFlow()
    val botProfiles: StateFlow<List<BotProfile>> = _botProfiles.asStateFlow()
    val selectedBotId: StateFlow<String> = _selectedBotId.asStateFlow()

    fun initialize(context: Context) {
        if (!initialized.compareAndSet(false, true)) return

        botDao = AstrBotDatabase.get(context).botDao()
        bindingsPreferences = context.applicationContext.getSharedPreferences(BOT_BINDINGS_PREFS_NAME, Context.MODE_PRIVATE)
        botBindings = loadBotBindings()
        repositoryScope.launch {
            if (botDao.count() == 0) {
                botDao.upsert(defaultBot.toEntity())
            }
            botDao.observeBots().collect { entities ->
                val profiles = entities.map { entity ->
                    val binding = botBindings[entity.id]
                    entity.toProfile(
                        configProfileId = ConfigRepository.resolveExistingId(binding?.configProfileId ?: ConfigRepository.DEFAULT_CONFIG_ID),
                        boundQqUins = binding?.boundQqUins.orEmpty(),
                        persistConversationLocally = binding?.persistConversationLocally ?: false,
                    )
                }
                if (profiles.isEmpty()) {
                    _botProfiles.value = listOf(defaultBot)
                    _selectedBotId.value = defaultBot.id
                    _botProfile.value = defaultBot
                    return@collect
                }

                _botProfiles.value = profiles
                val currentSelected = profiles.firstOrNull { it.id == _selectedBotId.value } ?: profiles.first()
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
        RuntimeLogRepository.append("Bot selected: ${selected.displayName} (${selected.id})")
    }

    fun save(profile: BotProfile) {
        repositoryScope.launch {
            val normalized = profile.copy(
                accountHint = profile.boundQqUins.joinToString(", ").ifBlank { "QQ account not linked" },
                configProfileId = ConfigRepository.resolveExistingId(profile.configProfileId),
                boundQqUins = profile.boundQqUins.map { it.trim() }.filter { it.isNotBlank() }.distinct(),
            )
            persistBinding(
                normalized.id,
                BotBindingState(
                    configProfileId = normalized.configProfileId,
                    boundQqUins = normalized.boundQqUins,
                    persistConversationLocally = normalized.persistConversationLocally,
                ),
            )
            botDao.upsert(normalized.toEntity())
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
        )
        repositoryScope.launch {
            persistBinding(
                created.id,
                BotBindingState(
                    configProfileId = created.configProfileId,
                    boundQqUins = created.boundQqUins,
                    persistConversationLocally = created.persistConversationLocally,
                ),
            )
            botDao.upsert(created.toEntity())
            RuntimeLogRepository.append("Bot created: ${created.displayName} (${created.id})")
            select(created.id)
        }
        return created
    }

    fun delete(botId: String) {
        if (_botProfiles.value.size == 1) {
            RuntimeLogRepository.append("Bot delete skipped: last bot cannot be removed")
            return
        }

        val removed = _botProfiles.value.firstOrNull { it.id == botId } ?: return
        repositoryScope.launch {
            removeBinding(botId)
            botDao.deleteById(botId)
            ConversationRepository.deleteSessionsForBot(botId)
            RuntimeLogRepository.append("Bot deleted: ${removed.displayName} (${removed.id})")
        }
    }

    fun replaceConfigBinding(deletedConfigId: String, fallbackConfigId: String) {
        val resolvedFallbackId = ConfigRepository.resolveExistingId(fallbackConfigId)
        val nextBindings = botBindings.mapValues { (_, current) ->
            if (current.configProfileId == deletedConfigId) {
                current.copy(configProfileId = resolvedFallbackId)
            } else {
                current
            }
        }
        botBindings = nextBindings
        persistBindings()
        _botProfiles.value = _botProfiles.value.map { profile ->
            if (profile.configProfileId == deletedConfigId) {
                profile.copy(configProfileId = resolvedFallbackId)
            } else {
                profile
            }
        }
        _botProfile.value = _botProfiles.value.firstOrNull { it.id == _selectedBotId.value } ?: _botProfiles.value.firstOrNull() ?: defaultBot
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

    private fun loadBotBindings(): Map<String, BotBindingState> {
        val raw = bindingsPreferences?.getString(KEY_BOT_BINDINGS_JSON, null)?.takeIf { it.isNotBlank() } ?: return emptyMap()
        return runCatching {
            val objectJson = org.json.JSONObject(raw)
            mutableMapOf<String, BotBindingState>().apply {
                objectJson.keys().forEach { key ->
                    val rawValue = objectJson.opt(key)
                    this[key] = when (rawValue) {
                        is org.json.JSONObject -> {
                            val qqArray = rawValue.optJSONArray("boundQqUins")
                            val boundQqUins = buildList {
                                if (qqArray != null) {
                                    for (index in 0 until qqArray.length()) {
                                        qqArray.optString(index)?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
                                    }
                                }
                            }
                            BotBindingState(
                                configProfileId = rawValue.optString("configProfileId").ifBlank { ConfigRepository.DEFAULT_CONFIG_ID },
                                boundQqUins = boundQqUins,
                                persistConversationLocally = rawValue.optBoolean("persistConversationLocally", false),
                            )
                        }

                        else -> {
                            BotBindingState(
                                configProfileId = objectJson.optString(key).ifBlank { ConfigRepository.DEFAULT_CONFIG_ID },
                            )
                        }
                    }
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun persistBinding(botId: String, bindingState: BotBindingState) {
        botBindings = botBindings + (botId to bindingState)
        persistBindings()
    }

    private fun removeBinding(botId: String) {
        botBindings = botBindings - botId
        persistBindings()
    }

    private fun persistBindings() {
        val json = org.json.JSONObject()
        botBindings.forEach { (botId, bindingState) ->
            json.put(
                botId,
                org.json.JSONObject().apply {
                    put("configProfileId", bindingState.configProfileId)
                    put("persistConversationLocally", bindingState.persistConversationLocally)
                    put(
                        "boundQqUins",
                        org.json.JSONArray().apply {
                            bindingState.boundQqUins.forEach(::put)
                        },
                    )
                },
            )
        }
        bindingsPreferences?.edit()?.putString(KEY_BOT_BINDINGS_JSON, json.toString())?.apply()
    }
}

private data class BotBindingState(
    val configProfileId: String = ConfigRepository.DEFAULT_CONFIG_ID,
    val boundQqUins: List<String> = emptyList(),
    val persistConversationLocally: Boolean = false,
)

private object AstrBotDatabaseHolder {
    val placeholder = object : BotDao {
        override fun observeBots() = flowOf(emptyList<BotEntity>())
        override suspend fun upsert(entity: BotEntity) = Unit
        override suspend fun deleteById(botId: String) = Unit
        override suspend fun count(): Int = 0
    }
}
