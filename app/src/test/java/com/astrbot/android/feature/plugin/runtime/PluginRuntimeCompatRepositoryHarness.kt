package com.astrbot.android.feature.plugin.runtime

import android.content.SharedPreferences
import com.astrbot.android.data.BotRepository
import com.astrbot.android.data.ConfigRepository
import com.astrbot.android.data.ConversationRepository
import com.astrbot.android.data.PersonaRepository
import com.astrbot.android.data.ProviderRepository
import com.astrbot.android.data.ResourceCenterRepository
import com.astrbot.android.data.db.AppPreferenceDao
import com.astrbot.android.data.db.AppPreferenceEntity
import com.astrbot.android.data.db.BotAggregate
import com.astrbot.android.data.db.BotAggregateDao
import com.astrbot.android.data.db.BotBoundQqUinEntity
import com.astrbot.android.data.db.BotEntity
import com.astrbot.android.data.db.BotTriggerWordEntity
import com.astrbot.android.data.db.BotWriteModel
import com.astrbot.android.data.db.ConfigAdminUidEntity
import com.astrbot.android.data.db.ConfigAggregate
import com.astrbot.android.data.db.ConfigAggregateDao
import com.astrbot.android.data.db.ConfigKeywordPatternEntity
import com.astrbot.android.data.db.ConfigMcpServerEntity
import com.astrbot.android.data.db.ConfigProfileEntity
import com.astrbot.android.data.db.ConfigSkillEntity
import com.astrbot.android.data.db.ConfigTextRuleEntity
import com.astrbot.android.data.db.ConfigWakeWordEntity
import com.astrbot.android.data.db.ConfigWhitelistEntryEntity
import com.astrbot.android.data.db.ConfigWriteModel
import com.astrbot.android.data.db.ConversationAggregate
import com.astrbot.android.data.db.ConversationAggregateDao
import com.astrbot.android.data.db.ConversationAggregateWriteModel
import com.astrbot.android.data.db.ConversationAttachmentEntity
import com.astrbot.android.data.db.ConversationEntity
import com.astrbot.android.data.db.ConversationMessageAggregate
import com.astrbot.android.data.db.ConversationMessageEntity
import com.astrbot.android.data.db.PersonaAggregate
import com.astrbot.android.data.db.PersonaAggregateDao
import com.astrbot.android.data.db.PersonaEnabledToolEntity
import com.astrbot.android.data.db.PersonaEntity
import com.astrbot.android.data.db.PersonaPromptEntity
import com.astrbot.android.data.db.PersonaWriteModel
import com.astrbot.android.data.db.ProviderAggregate
import com.astrbot.android.data.db.ProviderAggregateDao
import com.astrbot.android.data.db.ProviderCapabilityEntity
import com.astrbot.android.data.db.ProviderEntity
import com.astrbot.android.data.db.ProviderTtsVoiceOptionEntity
import com.astrbot.android.data.db.ProviderWriteModel
import com.astrbot.android.data.db.resource.ConfigResourceProjectionEntity
import com.astrbot.android.data.db.resource.ResourceCenterDao
import com.astrbot.android.data.db.resource.ResourceCenterItemEntity
import com.astrbot.android.data.db.resource.toEntity
import com.astrbot.android.data.db.toWriteModel
import com.astrbot.android.feature.bot.data.FeatureBotRepository
import com.astrbot.android.feature.bot.data.FeatureBotRepositoryStore
import com.astrbot.android.feature.chat.data.FeatureConversationRepository
import com.astrbot.android.feature.chat.data.FeatureConversationRepositoryStore
import com.astrbot.android.feature.config.data.FeatureConfigRepository
import com.astrbot.android.feature.config.data.FeatureConfigRepositoryStore
import com.astrbot.android.feature.persona.data.FeaturePersonaRepository
import com.astrbot.android.feature.persona.data.FeaturePersonaRepositoryStore
import com.astrbot.android.feature.provider.data.FeatureProviderRepository
import com.astrbot.android.feature.provider.data.FeatureProviderRepositoryStore
import com.astrbot.android.feature.resource.data.FeatureResourceCenterRepository
import com.astrbot.android.feature.resource.data.FeatureResourceCenterRepositoryStore
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.ConfigResourceProjection
import com.astrbot.android.model.PersonaProfile
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ResourceCenterItem
import com.astrbot.android.model.chat.ConversationSession
import java.io.File
import javax.inject.Provider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal object PluginRuntimeCompatRepositoryHarness {
    private var installed = false

    private lateinit var botDao: InMemoryBotAggregateDao
    private lateinit var botPreferenceDao: ImmediateAppPreferenceDao
    private lateinit var configDao: InMemoryConfigAggregateDao
    private lateinit var configPreferenceDao: ImmediateAppPreferenceDao
    private lateinit var providerDao: InMemoryProviderAggregateDao
    private lateinit var personaDao: InMemoryPersonaAggregateDao
    private lateinit var conversationDao: InMemoryConversationAggregateDao
    private lateinit var resourceCenterDao: InMemoryResourceCenterDao

    private lateinit var configStore: FeatureConfigRepositoryStore
    private lateinit var botStore: FeatureBotRepositoryStore
    private lateinit var providerStore: FeatureProviderRepositoryStore
    private lateinit var personaStore: FeaturePersonaRepositoryStore
    private lateinit var conversationStore: FeatureConversationRepositoryStore
    private lateinit var resourceCenterStore: FeatureResourceCenterRepositoryStore

    fun ensureInstalled() {
        if (installed && ownsInstalledStores()) return
        installed = false

        configDao = InMemoryConfigAggregateDao(
            initialProfiles = listOf(
                ConfigProfile(
                    id = FeatureConfigRepository.DEFAULT_CONFIG_ID,
                    name = "Default",
                ),
            ),
        )
        configPreferenceDao = ImmediateAppPreferenceDao(FeatureConfigRepository.DEFAULT_CONFIG_ID)
        configStore = FeatureConfigRepositoryStore(
            configProfileDao = configDao,
            appPreferenceDao = configPreferenceDao,
            preferences = InMemorySharedPreferences(),
        )

        botDao = InMemoryBotAggregateDao(
            initialProfiles = listOf(
                BotProfile(
                    id = "qq-main",
                    displayName = "Primary Bot",
                    tag = "Default",
                    accountHint = "QQ account not linked",
                    triggerWords = listOf("astrbot"),
                    configProfileId = FeatureConfigRepository.DEFAULT_CONFIG_ID,
                ),
            ),
        )
        botPreferenceDao = ImmediateAppPreferenceDao("qq-main")
        botStore = FeatureBotRepositoryStore(
            botDao = botDao,
            appPreferenceDao = botPreferenceDao,
            bindingsPreferences = InMemorySharedPreferences(),
            configRepositoryProvider = Provider { configStore },
        )

        providerDao = InMemoryProviderAggregateDao()
        providerStore = FeatureProviderRepositoryStore(
            providerDao = providerDao,
            preferences = InMemorySharedPreferences(),
        )

        personaDao = InMemoryPersonaAggregateDao()
        personaStore = FeaturePersonaRepositoryStore(
            personaDao = personaDao,
            preferences = InMemorySharedPreferences(),
        )

        conversationDao = InMemoryConversationAggregateDao()
        conversationStore = FeatureConversationRepositoryStore(
            conversationAggregateDao = conversationDao,
            legacyStorageFile = File.createTempFile("plugin-runtime-compat", ".json").apply { deleteOnExit() },
            botRepositoryProvider = Provider { botStore },
        )

        resourceCenterDao = InMemoryResourceCenterDao()
        resourceCenterStore = FeatureResourceCenterRepositoryStore(
            resourceCenterDao = resourceCenterDao,
            configAggregateDao = configDao,
        )

        waitUntil("compat repositories should initialize") {
            runCatching {
                BotRepository.snapshotProfiles().isNotEmpty() &&
                    ConfigRepository.snapshotProfiles().isNotEmpty() &&
                    ProviderRepository.snapshotProfiles().isNotEmpty() &&
                    PersonaRepository.snapshotProfiles().isNotEmpty() &&
                    ConversationRepository.isReady.value
            }.getOrDefault(false)
        }

        installed = true
    }

    fun ownsInstalledStores(): Boolean {
        if (!installed || !::configStore.isInitialized) return false
        return ownsDelegate(FeatureConfigRepository, configStore) &&
            ownsDelegate(FeatureBotRepository, botStore) &&
            ownsDelegate(FeatureProviderRepository, providerStore) &&
            ownsDelegate(FeaturePersonaRepository, personaStore) &&
            ownsDelegate(FeatureConversationRepository, conversationStore) &&
            ownsDelegate(FeatureResourceCenterRepository, resourceCenterStore)
    }

    fun captureSnapshot(): CompatRepositorySnapshot {
        ensureInstalled()
        return CompatRepositorySnapshot(
            bots = BotRepository.snapshotProfiles(),
            selectedBotId = BotRepository.selectedBotId.value,
            configs = ConfigRepository.snapshotProfiles(),
            selectedConfigId = ConfigRepository.selectedProfileId.value,
            providers = ProviderRepository.snapshotProfiles(),
            personas = PersonaRepository.snapshotProfiles(),
            sessions = ConversationRepository.snapshotSessions(),
            resources = ResourceCenterRepository.resources.value.map { resource -> resource.copy() },
            projections = ResourceCenterRepository.projections.value.map { projection -> projection.copy() },
        )
    }

    suspend fun applyOneBotState(
        bot: BotProfile,
        config: ConfigProfile,
        providers: List<ProviderProfile>,
    ) {
        ensureInstalled()
        ConfigRepository.restoreProfiles(listOf(config), config.id)
        waitUntil("config repository should restore one-bot state") {
            ConfigRepository.selectedProfileId.value == config.id &&
                ConfigRepository.snapshotProfiles().map { profile -> profile.id } == listOf(config.id)
        }
        BotRepository.restoreProfiles(listOf(bot), bot.id)
        ProviderRepository.restoreProfiles(providers)
        PersonaRepository.restoreProfiles(PersonaRepository.snapshotProfiles())
        ConversationRepository.restoreSessions(emptyList())
        resourceCenterDao.replaceAll(emptyList(), emptyList())
        waitUntil("one-bot compat repositories should settle") {
            BotRepository.selectedBotId.value == bot.id &&
                BotRepository.snapshotProfiles().map { profile -> profile.id } == listOf(bot.id) &&
                ProviderRepository.snapshotProfiles().map { provider -> provider.id } == providers.map { provider -> provider.id } &&
                ConversationRepository.isReady.value
        }
    }

    suspend fun restore(snapshot: CompatRepositorySnapshot) {
        ensureInstalled()
        ConfigRepository.restoreProfiles(snapshot.configs, snapshot.selectedConfigId)
        waitUntil("config repository snapshot should restore") {
            ConfigRepository.selectedProfileId.value == snapshot.selectedConfigId &&
                ConfigRepository.snapshotProfiles().map { profile -> profile.id } == snapshot.configs.map { profile -> profile.id }
        }
        BotRepository.restoreProfiles(snapshot.bots, snapshot.selectedBotId)
        ProviderRepository.restoreProfiles(snapshot.providers)
        PersonaRepository.restoreProfiles(snapshot.personas)
        ConversationRepository.restoreSessions(snapshot.sessions)
        resourceCenterDao.replaceAll(snapshot.resources, snapshot.projections)
        waitUntil("compat repository snapshot should restore") {
            BotRepository.selectedBotId.value == snapshot.selectedBotId &&
                BotRepository.snapshotProfiles().map { profile -> profile.id } == snapshot.bots.map { profile -> profile.id } &&
                ProviderRepository.snapshotProfiles().map { provider -> provider.id } == snapshot.providers.map { provider -> provider.id } &&
                PersonaRepository.snapshotProfiles().map { persona -> persona.id } == snapshot.personas.map { persona -> persona.id } &&
                ConversationRepository.snapshotSessions().map { session -> session.id } == snapshot.sessions.map { session -> session.id } &&
                ResourceCenterRepository.resources.value.map { resource -> resource.resourceId } == snapshot.resources.map { resource -> resource.resourceId }
        }
    }
}

internal data class CompatRepositorySnapshot(
    val bots: List<BotProfile>,
    val selectedBotId: String,
    val configs: List<ConfigProfile>,
    val selectedConfigId: String,
    val providers: List<ProviderProfile>,
    val personas: List<PersonaProfile>,
    val sessions: List<ConversationSession>,
    val resources: List<ResourceCenterItem>,
    val projections: List<ConfigResourceProjection>,
)

private fun waitUntil(
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

private fun ownsDelegate(repositoryFacade: Any, expectedStore: Any): Boolean {
    return runCatching {
        val field = repositoryFacade::class.java.getDeclaredField("delegate")
        field.isAccessible = true
        field.get(repositoryFacade) === expectedStore
    }.getOrDefault(false)
}

private class ImmediateAppPreferenceDao(initialValue: String?) : AppPreferenceDao {
    private val values = MutableStateFlow(initialValue)

    override fun observeValue(key: String): Flow<String?> = values

    override suspend fun getValue(key: String): String? = values.value

    override suspend fun upsert(entity: AppPreferenceEntity) {
        values.value = entity.value
    }
}

private class InMemorySharedPreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()

    override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        @Suppress("UNCHECKED_CAST")
        ((values[key] as? Set<String>)?.toMutableSet() ?: defValues)

    override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue

    override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue

    override fun contains(key: String?): Boolean = values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor(values)

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    private class Editor(
        private val values: MutableMap<String, Any?>,
    ) : SharedPreferences.Editor {
        override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
            if (key != null) values[key] = value
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply {
            if (key != null) this.values[key] = values?.toSet()
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
            if (key != null) values[key] = value
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
            if (key != null) values[key] = value
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
            if (key != null) values[key] = value
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
            if (key != null) values[key] = value
        }

        override fun remove(key: String?): SharedPreferences.Editor = apply {
            if (key != null) values.remove(key)
        }

        override fun clear(): SharedPreferences.Editor = apply {
            values.clear()
        }

        override fun commit(): Boolean = true

        override fun apply() = Unit
    }
}

private class InMemoryBotAggregateDao(
    initialProfiles: List<BotProfile>,
) : BotAggregateDao() {
    private val aggregates = MutableStateFlow(initialProfiles.map(::toAggregate))

    override fun observeBotAggregates(): Flow<List<BotAggregate>> = aggregates

    override suspend fun listBotAggregates(): List<BotAggregate> = aggregates.value

    override suspend fun upsertBots(entities: List<BotEntity>) = Unit

    override suspend fun upsertBoundQqUins(entities: List<BotBoundQqUinEntity>) = Unit

    override suspend fun upsertTriggerWords(entities: List<BotTriggerWordEntity>) = Unit

    override suspend fun deleteMissingBots(ids: List<String>) = Unit

    override suspend fun clearBots() = Unit

    override suspend fun deleteBoundQqUins(botIds: List<String>) = Unit

    override suspend fun deleteTriggerWords(botIds: List<String>) = Unit

    override suspend fun count(): Int = aggregates.value.size

    override suspend fun replaceAll(writeModels: List<BotWriteModel>) {
        aggregates.value = writeModels.map(::toAggregate)
    }

    private fun toAggregate(profile: BotProfile): BotAggregate = toAggregate(profile.toWriteModel())

    private fun toAggregate(writeModel: BotWriteModel): BotAggregate {
        return BotAggregate(
            bot = writeModel.bot,
            boundQqUins = writeModel.boundQqUins,
            triggerWords = writeModel.triggerWords,
        )
    }
}

private class InMemoryConfigAggregateDao(
    initialProfiles: List<ConfigProfile> = emptyList(),
) : ConfigAggregateDao() {
    private val aggregates = MutableStateFlow(initialProfiles.map(::toAggregate))

    override fun observeConfigAggregates(): Flow<List<ConfigAggregate>> = aggregates

    override suspend fun listConfigAggregates(): List<ConfigAggregate> = aggregates.value

    override suspend fun upsertConfigs(entities: List<ConfigProfileEntity>) = Unit

    override suspend fun upsertAdminUids(entities: List<ConfigAdminUidEntity>) = Unit

    override suspend fun upsertWakeWords(entities: List<ConfigWakeWordEntity>) = Unit

    override suspend fun upsertWhitelistEntries(entities: List<ConfigWhitelistEntryEntity>) = Unit

    override suspend fun upsertKeywordPatterns(entities: List<ConfigKeywordPatternEntity>) = Unit

    override suspend fun upsertTextRules(entities: List<ConfigTextRuleEntity>) = Unit

    override suspend fun upsertMcpServers(entities: List<ConfigMcpServerEntity>) = Unit

    override suspend fun upsertSkills(entities: List<ConfigSkillEntity>) = Unit

    override suspend fun deleteMissingConfigs(ids: List<String>) = Unit

    override suspend fun clearConfigs() = Unit

    override suspend fun deleteAdminUids(configIds: List<String>) = Unit

    override suspend fun deleteWakeWords(configIds: List<String>) = Unit

    override suspend fun deleteWhitelistEntries(configIds: List<String>) = Unit

    override suspend fun deleteKeywordPatterns(configIds: List<String>) = Unit

    override suspend fun deleteTextRules(configIds: List<String>) = Unit

    override suspend fun deleteMcpServers(configIds: List<String>) = Unit

    override suspend fun deleteSkills(configIds: List<String>) = Unit

    override suspend fun count(): Int = aggregates.value.size

    override suspend fun replaceAll(writeModels: List<ConfigWriteModel>) {
        aggregates.value = writeModels.map(::toAggregate)
    }

    private fun toAggregate(profile: ConfigProfile): ConfigAggregate = toAggregate(profile.toWriteModel(sortIndex = 0))

    private fun toAggregate(writeModel: ConfigWriteModel): ConfigAggregate {
        return ConfigAggregate(
            config = writeModel.config,
            adminUids = writeModel.adminUids,
            wakeWords = writeModel.wakeWords,
            whitelistEntries = writeModel.whitelistEntries,
            keywordPatterns = writeModel.keywordPatterns,
            textRules = listOf(writeModel.textRule),
            mcpServers = writeModel.mcpServers,
            skills = writeModel.skills,
        )
    }
}

private class InMemoryProviderAggregateDao(
    initialProfiles: List<ProviderProfile> = emptyList(),
) : ProviderAggregateDao() {
    private val aggregates = MutableStateFlow(initialProfiles.map(::toAggregate))

    override fun observeProviderAggregates(): Flow<List<ProviderAggregate>> = aggregates

    override suspend fun listProviderAggregates(): List<ProviderAggregate> = aggregates.value

    override suspend fun upsertProviders(entities: List<ProviderEntity>) = Unit

    override suspend fun upsertCapabilities(entities: List<ProviderCapabilityEntity>) = Unit

    override suspend fun upsertVoiceOptions(entities: List<ProviderTtsVoiceOptionEntity>) = Unit

    override suspend fun deleteMissingProviders(ids: List<String>) = Unit

    override suspend fun clearProviders() = Unit

    override suspend fun deleteCapabilities(providerIds: List<String>) = Unit

    override suspend fun deleteVoiceOptions(providerIds: List<String>) = Unit

    override suspend fun count(): Int = aggregates.value.size

    override suspend fun replaceAll(writeModels: List<ProviderWriteModel>) {
        aggregates.value = writeModels.map(::toAggregate)
    }

    private fun toAggregate(profile: ProviderProfile): ProviderAggregate =
        toAggregate(profile.toWriteModel(sortIndex = 0))

    private fun toAggregate(writeModel: ProviderWriteModel): ProviderAggregate {
        return ProviderAggregate(
            provider = writeModel.provider,
            capabilities = writeModel.capabilities,
            ttsVoiceOptions = writeModel.ttsVoiceOptions,
        )
    }
}

private class InMemoryPersonaAggregateDao(
    initialProfiles: List<PersonaProfile> = emptyList(),
) : PersonaAggregateDao() {
    private val aggregates = MutableStateFlow(initialProfiles.map(::toAggregate))

    override fun observePersonaAggregates(): Flow<List<PersonaAggregate>> = aggregates

    override suspend fun listPersonaAggregates(): List<PersonaAggregate> = aggregates.value

    override suspend fun upsertPersonas(entities: List<PersonaEntity>) = Unit

    override suspend fun upsertPrompts(entities: List<PersonaPromptEntity>) = Unit

    override suspend fun upsertEnabledTools(entities: List<PersonaEnabledToolEntity>) = Unit

    override suspend fun deleteMissingPersonas(ids: List<String>) = Unit

    override suspend fun clearPersonas() = Unit

    override suspend fun deletePrompts(personaIds: List<String>) = Unit

    override suspend fun deleteEnabledTools(personaIds: List<String>) = Unit

    override suspend fun count(): Int = aggregates.value.size

    override suspend fun replaceAll(writeModels: List<PersonaWriteModel>) {
        aggregates.value = writeModels.map(::toAggregate)
    }

    private fun toAggregate(profile: PersonaProfile): PersonaAggregate =
        toAggregate(profile.toWriteModel(sortIndex = 0))

    private fun toAggregate(writeModel: PersonaWriteModel): PersonaAggregate {
        return PersonaAggregate(
            persona = writeModel.persona,
            prompts = listOf(writeModel.prompt),
            enabledTools = writeModel.enabledTools,
        )
    }
}

private class InMemoryConversationAggregateDao(
    initialSessions: List<ConversationSession> = emptyList(),
) : ConversationAggregateDao() {
    private val aggregates = MutableStateFlow(initialSessions.map(::toAggregate))

    override fun observeConversationAggregates(): Flow<List<ConversationAggregate>> = aggregates

    override suspend fun listConversationAggregates(): List<ConversationAggregate> = aggregates.value

    override suspend fun upsertSessions(entities: List<ConversationEntity>) = Unit

    override suspend fun upsertMessages(entities: List<ConversationMessageEntity>) = Unit

    override suspend fun upsertAttachments(entities: List<ConversationAttachmentEntity>) = Unit

    override suspend fun deleteMissingSessions(ids: List<String>) = Unit

    override suspend fun clearSessions() = Unit

    override suspend fun deleteMessagesForSessions(sessionIds: List<String>) = Unit

    override suspend fun count(): Int = aggregates.value.size

    override suspend fun replaceAll(writeModels: List<ConversationAggregateWriteModel>) {
        aggregates.value = writeModels.map(::toAggregate)
    }

    private fun toAggregate(session: ConversationSession): ConversationAggregate =
        toAggregate(session.toWriteModel())

    private fun toAggregate(writeModel: ConversationAggregateWriteModel): ConversationAggregate {
        return ConversationAggregate(
            session = writeModel.session,
            messageAggregates = writeModel.messages.map { message ->
                ConversationMessageAggregate(
                    message = message,
                    attachments = writeModel.attachments.filter { attachment -> attachment.messageId == message.id },
                )
            },
        )
    }
}

private class InMemoryResourceCenterDao : ResourceCenterDao {
    private val resources = MutableStateFlow<List<ResourceCenterItemEntity>>(emptyList())
    private val projections = MutableStateFlow<List<ConfigResourceProjectionEntity>>(emptyList())

    override fun observeResources(): Flow<List<ResourceCenterItemEntity>> = resources

    override fun observeProjections(): Flow<List<ConfigResourceProjectionEntity>> = projections

    override suspend fun listResources(): List<ResourceCenterItemEntity> = resources.value

    override suspend fun listResources(kind: String): List<ResourceCenterItemEntity> =
        resources.value.filter { entity -> entity.kind == kind }

    override suspend fun listProjections(): List<ConfigResourceProjectionEntity> = projections.value

    override suspend fun projectionsForConfig(configId: String): List<ConfigResourceProjectionEntity> =
        projections.value.filter { entity -> entity.configId == configId }

    override suspend fun countResources(): Int = resources.value.size

    override suspend fun countProjections(): Int = projections.value.size

    override suspend fun upsertResource(entity: ResourceCenterItemEntity) {
        resources.value = resources.value
            .filterNot { current -> current.resourceId == entity.resourceId }
            .plus(entity)
            .sortedWith(compareBy<ResourceCenterItemEntity>({ it.kind }, { it.name }, { -it.updatedAt }))
    }

    override suspend fun upsertResources(entities: List<ResourceCenterItemEntity>) {
        var next = resources.value
        entities.forEach { entity ->
            next = next.filterNot { current -> current.resourceId == entity.resourceId }.plus(entity)
        }
        resources.value = next.sortedWith(compareBy<ResourceCenterItemEntity>({ it.kind }, { it.name }, { -it.updatedAt }))
    }

    override suspend fun deleteResource(resourceId: String) {
        resources.value = resources.value.filterNot { entity -> entity.resourceId == resourceId }
        projections.value = projections.value.filterNot { entity -> entity.resourceId == resourceId }
    }

    override suspend fun upsertProjection(entity: ConfigResourceProjectionEntity) {
        projections.value = projections.value
            .filterNot { current ->
                current.configId == entity.configId &&
                    current.kind == entity.kind &&
                    current.resourceId == entity.resourceId
            }
            .plus(entity)
            .sortedWith(compareBy<ConfigResourceProjectionEntity>({ it.configId }, { it.kind }, { it.sortIndex }, { -it.priority }))
    }

    override suspend fun upsertProjections(entities: List<ConfigResourceProjectionEntity>) {
        var next = projections.value
        entities.forEach { entity ->
            next = next.filterNot { current ->
                current.configId == entity.configId &&
                    current.kind == entity.kind &&
                    current.resourceId == entity.resourceId
            }.plus(entity)
        }
        projections.value = next.sortedWith(
            compareBy<ConfigResourceProjectionEntity>({ it.configId }, { it.kind }, { it.sortIndex }, { -it.priority }),
        )
    }

    fun replaceAll(
        items: List<ResourceCenterItem>,
        projections: List<ConfigResourceProjection>,
    ) {
        resources.value = items.map(ResourceCenterItem::toEntity)
            .sortedWith(compareBy<ResourceCenterItemEntity>({ it.kind }, { it.name }, { -it.updatedAt }))
        this.projections.value = projections.map(ConfigResourceProjection::toEntity)
            .sortedWith(compareBy<ConfigResourceProjectionEntity>({ it.configId }, { it.kind }, { it.sortIndex }, { -it.priority }))
    }
}
