package com.astrbot.android.data

import android.content.Context
import com.astrbot.android.data.db.ConversationAggregate
import com.astrbot.android.data.db.PluginCatalogDao
import com.astrbot.android.data.db.PluginCatalogEntryEntity
import com.astrbot.android.data.db.PluginCatalogSourceEntity
import com.astrbot.android.data.db.PluginCatalogVersionEntity
import com.astrbot.android.data.db.PluginConfigSnapshotDao
import com.astrbot.android.data.db.ConversationAggregateDao
import com.astrbot.android.data.db.ConversationAggregateWriteModel
import com.astrbot.android.data.db.ConversationAttachmentEntity
import com.astrbot.android.data.db.ConversationEntity
import com.astrbot.android.data.db.ConversationMessageEntity
import com.astrbot.android.data.db.PluginInstallAggregate
import com.astrbot.android.data.db.PluginInstallAggregateDao
import com.astrbot.android.data.db.toWriteModel
import com.astrbot.android.data.db.toEntity
import com.astrbot.android.data.db.toEntryRecord
import com.astrbot.android.data.db.toInstallRecord
import com.astrbot.android.data.db.toModel
import com.astrbot.android.data.db.toSnapshot
import com.astrbot.android.data.db.toSyncState
import com.astrbot.android.data.db.toWriteModel
import com.astrbot.android.feature.bot.data.FeatureBotRepository
import com.astrbot.android.feature.chat.data.FeatureConversationRepository
import com.astrbot.android.feature.config.data.FeatureConfigRepository
import com.astrbot.android.feature.cron.data.FeatureCronJobRepository
import com.astrbot.android.feature.persona.data.FeaturePersonaRepository
import com.astrbot.android.feature.plugin.data.PluginCatalogVersionGate
import com.astrbot.android.feature.plugin.data.catalog.PluginCatalogSyncStore
import com.astrbot.android.feature.plugin.runtime.PluginPackageValidationResult
import com.astrbot.android.feature.plugin.runtime.PluginPackageValidator
import com.astrbot.android.feature.plugin.runtime.compareVersions
import com.astrbot.android.feature.provider.data.FeatureProviderRepository
import com.astrbot.android.feature.qq.data.NapCatLoginRepository
import com.astrbot.android.feature.resource.data.FeatureResourceCenterRepository
import com.astrbot.android.model.NapCatBridgeConfig
import com.astrbot.android.model.NapCatRuntimeState
import com.astrbot.android.model.RuntimeStatus
import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.chat.ConversationSession
import com.astrbot.android.model.chat.importDedupKey
import com.astrbot.android.model.plugin.PluginCatalogEntry
import com.astrbot.android.model.plugin.PluginCatalogEntryRecord
import com.astrbot.android.model.plugin.PluginCatalogSyncState
import com.astrbot.android.model.plugin.PluginCatalogVersion
import com.astrbot.android.model.plugin.PluginCompatibilityState
import com.astrbot.android.model.plugin.PluginCompatibilityStatus
import com.astrbot.android.model.plugin.PluginConfigStorageBoundary
import com.astrbot.android.model.plugin.PluginConfigStoreSnapshot
import com.astrbot.android.model.plugin.PluginFailureState
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginPackageContractJson
import com.astrbot.android.model.plugin.PluginPermissionDeclaration
import com.astrbot.android.model.plugin.PluginPermissionDiff
import com.astrbot.android.model.plugin.PluginPermissionUpgrade
import com.astrbot.android.model.plugin.PluginRepositorySource
import com.astrbot.android.model.plugin.PluginSourceBadge
import com.astrbot.android.model.plugin.PluginSourceType
import com.astrbot.android.model.plugin.PluginStaticConfigJson
import com.astrbot.android.model.plugin.PluginStaticConfigSchema
import com.astrbot.android.model.plugin.PluginStaticConfigValue
import com.astrbot.android.model.plugin.PluginUninstallPolicy
import com.astrbot.android.model.plugin.PluginUpdateAvailability
import com.astrbot.android.model.plugin.resolvePluginPackageSnapshotFile
import com.astrbot.android.model.plugin.toSnapshot as toPackageContractSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipInputStream

val BotRepository: FeatureBotRepository
    get() {
        RepositoryCompatibilityTestHarness.ensureInstalled()
        return FeatureBotRepository
    }
val ConfigRepository: FeatureConfigRepository
    get() {
        RepositoryCompatibilityTestHarness.ensureInstalled()
        return FeatureConfigRepository
    }
val PersonaRepository: FeaturePersonaRepository
    get() {
        RepositoryCompatibilityTestHarness.ensureInstalled()
        return FeaturePersonaRepository
    }
val ProviderRepository: FeatureProviderRepository
    get() {
        RepositoryCompatibilityTestHarness.ensureInstalled()
        return FeatureProviderRepository
    }
val CronJobRepository = FeatureCronJobRepository
val ResourceCenterRepository: FeatureResourceCenterRepository
    get() {
        RepositoryCompatibilityTestHarness.ensureInstalled()
        return FeatureResourceCenterRepository
    }

data class ConversationImportPreview(
    val totalSessions: Int,
    val duplicateSessions: List<ConversationSession>,
    val newSessions: List<ConversationSession>,
)

data class ConversationImportResult(
    val importedCount: Int,
    val overwrittenCount: Int,
    val skippedCount: Int,
)

object ConversationRepository {
    const val DEFAULT_SESSION_ID = "chat-main"
    const val DEFAULT_SESSION_TITLE = "\u65B0\u5BF9\u8BDD"

    @JvmField
    var conversationAggregateDao: ConversationAggregateDao = NoOpConversationAggregateDao()

    private var selectedBotIdProvider: () -> String = { "qq-main" }
    private val _sessions = MutableStateFlow<List<ConversationSession>>(emptyList())

    val sessions: StateFlow<List<ConversationSession>> = _sessions.asStateFlow()
    val isReady: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()

    fun setSelectedBotIdProvider(provider: () -> String) {
        selectedBotIdProvider = provider
    }

    fun session(sessionId: String = DEFAULT_SESSION_ID): ConversationSession {
        return _sessions.value.firstOrNull { it.id == sessionId } ?: createMissingSession(sessionId)
    }

    fun createSession(
        title: String = DEFAULT_SESSION_TITLE,
        botId: String = selectedBotIdProvider(),
    ): ConversationSession {
        val created = ConversationSession(
            id = java.util.UUID.randomUUID().toString(),
            title = title,
            botId = botId,
            personaId = "",
            providerId = "",
            maxContextMessages = 12,
            sessionSttEnabled = true,
            sessionTtsEnabled = true,
            messages = emptyList(),
        )
        _sessions.value = sortedSessions(_sessions.value + created)
        return created
    }

    fun deleteSession(sessionId: String) {
        _sessions.value = _sessions.value.filterNot { it.id == sessionId }
    }

    fun deleteSessionsForBot(botId: String) {
        _sessions.value = _sessions.value.filterNot { it.botId == botId }
    }

    fun renameSession(sessionId: String, title: String) {
        _sessions.value = _sessions.value.map { session ->
            if (session.id == sessionId) {
                session.copy(title = title, titleCustomized = true)
            } else {
                session
            }
        }
    }

    fun syncSystemSessionTitle(sessionId: String, title: String) {
        _sessions.value = _sessions.value.map { session ->
            if (session.id == sessionId && !session.titleCustomized) {
                session.copy(title = title)
            } else {
                session
            }
        }
    }

    fun toggleSessionPinned(sessionId: String) {
        _sessions.value = sortedSessions(
            _sessions.value.map { session ->
                if (session.id == sessionId) session.copy(pinned = !session.pinned) else session
            },
        )
    }

    fun buildContextPreview(sessionId: String): String =
        session(sessionId).messages.joinToString("\n") { message -> "${message.role}: ${message.content}" }

    fun appendMessage(
        sessionId: String,
        role: String,
        content: String,
        attachments: List<ConversationAttachment> = emptyList(),
    ): String {
        val message = ConversationMessage(
            id = java.util.UUID.randomUUID().toString(),
            role = role,
            content = content,
            timestamp = System.currentTimeMillis(),
            attachments = attachments,
        )
        val current = session(sessionId)
        upsertSession(current.copy(messages = current.messages + message))
        return message.id
    }

    fun updateMessage(
        sessionId: String,
        messageId: String,
        content: String? = null,
        attachments: List<ConversationAttachment>? = null,
    ) {
        val current = session(sessionId)
        upsertSession(
            current.copy(
                messages = current.messages.map { message ->
                    if (message.id == messageId) {
                        message.copy(
                            content = content ?: message.content,
                            attachments = attachments ?: message.attachments,
                        )
                    } else {
                        message
                    }
                },
            ),
        )
    }

    fun replaceMessages(sessionId: String, messages: List<ConversationMessage>) {
        upsertSession(session(sessionId).copy(messages = messages))
    }

    fun updateSessionBindings(sessionId: String, providerId: String, personaId: String, botId: String) {
        upsertSession(session(sessionId).copy(providerId = providerId, personaId = personaId, botId = botId))
    }

    fun updateSessionServiceFlags(
        sessionId: String,
        sessionSttEnabled: Boolean? = null,
        sessionTtsEnabled: Boolean? = null,
    ) {
        val current = session(sessionId)
        upsertSession(
            current.copy(
                sessionSttEnabled = sessionSttEnabled ?: current.sessionSttEnabled,
                sessionTtsEnabled = sessionTtsEnabled ?: current.sessionTtsEnabled,
            ),
        )
    }

    fun syncPersistenceForBot(@Suppress("UNUSED_PARAMETER") botId: String, @Suppress("UNUSED_PARAMETER") persistConversationLocally: Boolean) = Unit

    fun snapshotSessions(): List<ConversationSession> = _sessions.value.map { it.copy(messages = it.messages.toList()) }

    fun restoreSessions(restoredSessions: List<ConversationSession>) {
        _sessions.value = normalizeSessions(restoredSessions)
    }

    suspend fun restoreSessionsDurable(restoredSessions: List<ConversationSession>) {
        val snapshot = _sessions.value
        val normalized = normalizeSessions(restoredSessions)
        _sessions.value = normalized
        runCatching {
            conversationAggregateDao.replaceAll(normalized.map { it.toWriteModel() })
        }.onFailure { error ->
            _sessions.value = snapshot
            throw error
        }
    }

    fun previewImportedSessions(importedSessions: List<ConversationSession>): ConversationImportPreview {
        val normalizedIncoming = normalizeIncoming(importedSessions)
        val existingKeys = _sessions.value.map { it.importDedupKey() }.toSet()
        val duplicateSessions = normalizedIncoming.filter { session -> session.importDedupKey() in existingKeys }
        val newSessions = normalizedIncoming.filterNot { session -> session.importDedupKey() in existingKeys }
        return ConversationImportPreview(
            totalSessions = normalizedIncoming.size,
            duplicateSessions = duplicateSessions,
            newSessions = newSessions,
        )
    }

    fun importSessions(
        importedSessions: List<ConversationSession>,
        overwriteDuplicates: Boolean,
    ): ConversationImportResult {
        val incoming = normalizeIncoming(importedSessions)
        val current = _sessions.value.toMutableList()
        var importedCount = 0
        var overwrittenCount = 0
        var skippedCount = 0
        incoming.forEach { session ->
            val duplicateIndex = current.indexOfFirst { existing -> existing.importDedupKey() == session.importDedupKey() }
            if (duplicateIndex >= 0) {
                if (overwriteDuplicates) {
                    current[duplicateIndex] = session
                    overwrittenCount += 1
                } else {
                    skippedCount += 1
                }
            } else {
                current += session
                importedCount += 1
            }
        }
        _sessions.value = sortedSessions(current)
        return ConversationImportResult(importedCount, overwrittenCount, skippedCount)
    }

    suspend fun importSessionsDurable(
        importedSessions: List<ConversationSession>,
        overwriteDuplicates: Boolean,
    ): ConversationImportResult {
        val snapshot = _sessions.value
        val result = importSessions(importedSessions, overwriteDuplicates)
        runCatching {
            conversationAggregateDao.replaceAll(_sessions.value.map { it.toWriteModel() })
        }.onFailure { error ->
            _sessions.value = snapshot
            throw error
        }
        return result
    }

    private fun upsertSession(session: ConversationSession) {
        _sessions.value = sortedSessions(_sessions.value.filterNot { it.id == session.id } + session)
    }

    private fun createMissingSession(sessionId: String): ConversationSession {
        val session = ConversationSession(
            id = sessionId,
            title = DEFAULT_SESSION_TITLE,
            botId = selectedBotIdProvider(),
            personaId = "",
            providerId = "",
            maxContextMessages = 12,
            sessionSttEnabled = true,
            sessionTtsEnabled = true,
            messages = emptyList(),
        )
        upsertSession(session)
        return session
    }

    private fun normalizeSessions(restoredSessions: List<ConversationSession>): List<ConversationSession> =
        sortedSessions(restoredSessions.distinctBy { it.id })

    private fun normalizeIncoming(importedSessions: List<ConversationSession>): List<ConversationSession> =
        importedSessions.distinctBy { it.id }

    private fun sortedSessions(value: List<ConversationSession>): List<ConversationSession> =
        value.sortedWith(
            compareByDescending<ConversationSession> { it.pinned }
                .thenByDescending { session -> session.messages.maxOfOrNull { it.timestamp } ?: 0L }
                .thenBy { it.id },
        )
}

private class NoOpConversationAggregateDao : ConversationAggregateDao() {
    override fun observeConversationAggregates(): Flow<List<ConversationAggregate>> = MutableStateFlow(emptyList())

    override suspend fun listConversationAggregates(): List<ConversationAggregate> = emptyList()

    override suspend fun upsertSessions(entities: List<ConversationEntity>) = Unit

    override suspend fun upsertMessages(entities: List<ConversationMessageEntity>) = Unit

    override suspend fun upsertAttachments(entities: List<ConversationAttachmentEntity>) = Unit

    override suspend fun deleteMissingSessions(ids: List<String>) = Unit

    override suspend fun clearSessions() = Unit

    override suspend fun deleteMessagesForSessions(sessionIds: List<String>) = Unit

    override suspend fun count(): Int = 0

    override suspend fun replaceAll(writeModels: List<ConversationAggregateWriteModel>) = Unit
}

object PluginRepository : PluginInstallStore, PluginCatalogSyncStore {
    const val SUPPORTED_PROTOCOL_VERSION = 2

    @JvmField
    var pluginDao: PluginInstallAggregateDao? = null

    @JvmField
    var pluginCatalogDao: PluginCatalogDao? = null

    @JvmField
    var pluginConfigDao: PluginConfigSnapshotDao? = null

    @JvmField
    var appContext: Context? = null

    @JvmField
    var timeProvider: () -> Long = { System.currentTimeMillis() }

    @JvmField
    var pluginDataRemover: PluginDataRemover = NoOpPluginDataRemover

    @JvmField
    val _records = MutableStateFlow<List<PluginInstallRecord>>(emptyList())

    @JvmField
    val _repositorySources = MutableStateFlow<List<PluginRepositorySource>>(emptyList())

    @JvmField
    val _catalogEntries = MutableStateFlow<List<PluginCatalogEntryRecord>>(emptyList())

    @JvmField
    val initialized = AtomicBoolean(false)

    val records: StateFlow<List<PluginInstallRecord>> = _records.asStateFlow()
    val repositorySources: StateFlow<List<PluginRepositorySource>> = _repositorySources.asStateFlow()
    val catalogEntries: StateFlow<List<PluginCatalogEntryRecord>> = _catalogEntries.asStateFlow()

    override fun findByPluginId(pluginId: String): PluginInstallRecord? {
        requireInitialized()
        _records.value.firstOrNull { record -> record.pluginId == pluginId }?.let { return it }
        return runBlocking(Dispatchers.IO) {
            requireDao().getPluginInstallAggregate(pluginId)
                ?.toInstallRecord()
                ?.let(::repairAndProjectInstallRecordForHost)
        }?.also(::publishRecord)
    }

    override fun upsert(record: PluginInstallRecord) {
        requireInitialized()
        val projected = projectInstallRecordForHost(record)
        runBlocking(Dispatchers.IO) {
            requireDao().upsertRecord(projected.toWriteModel())
        }
        publishRecord(projected)
    }

    fun delete(pluginId: String) {
        requireInitialized()
        runBlocking(Dispatchers.IO) {
            requireDao().delete(pluginId)
        }
        _records.value = _records.value.filterNot { record -> record.pluginId == pluginId }
    }

    override fun replaceRepositoryCatalog(source: PluginRepositorySource) {
        requireInitialized()
        val writeModel = source.toWriteModel()
        runBlocking(Dispatchers.IO) {
            requireCatalogDao().replaceCatalog(
                source = writeModel.source,
                entries = writeModel.entries,
                versions = writeModel.versions,
            )
        }
        refreshCatalogState()
    }

    override fun upsertRepositorySource(source: PluginRepositorySource) {
        requireInitialized()
        runBlocking(Dispatchers.IO) {
            requireCatalogDao().upsertSources(listOf(source.toWriteModel().source))
        }
        refreshCatalogState()
    }

    override fun listRepositorySources(): List<PluginRepositorySource> {
        requireInitialized()
        return runBlocking(Dispatchers.IO) {
            requireCatalogDao().listSources().map { source -> assembleRepositorySource(source) }
        }
    }

    override fun getRepositorySource(sourceId: String): PluginRepositorySource? {
        requireInitialized()
        return runBlocking(Dispatchers.IO) {
            requireCatalogDao().getSource(sourceId)?.let { source -> assembleRepositorySource(source) }
        }
    }

    override fun getRepositorySourceSyncState(sourceId: String): PluginCatalogSyncState? {
        requireInitialized()
        return runBlocking(Dispatchers.IO) {
            requireCatalogDao().getSource(sourceId)?.toSyncState()
        }
    }

    override fun listAllCatalogEntries(): List<PluginCatalogEntryRecord> {
        requireInitialized()
        return listRepositorySources().flatMap { source ->
            source.plugins.map { entry -> source.toEntryRecord(entry) }
        }
    }

    fun listCatalogEntries(sourceId: String): List<PluginCatalogEntry> {
        requireInitialized()
        return runBlocking(Dispatchers.IO) {
            requireCatalogDao().listEntries(sourceId).map { entry -> assembleCatalogEntry(entry) }
        }
    }

    fun getCatalogEntry(sourceId: String, pluginId: String): PluginCatalogEntry? {
        requireInitialized()
        return runBlocking(Dispatchers.IO) {
            requireCatalogDao().getEntry(sourceId, pluginId)?.let { entry -> assembleCatalogEntry(entry) }
        }
    }

    override fun listCatalogVersions(sourceId: String, pluginId: String): List<PluginCatalogVersion> {
        requireInitialized()
        return runBlocking(Dispatchers.IO) {
            requireCatalogDao().listVersions(sourceId, pluginId).map(PluginCatalogVersionEntity::toModel)
        }
    }

    fun getUpdateAvailability(pluginId: String, hostVersion: String): PluginUpdateAvailability? {
        requireInitialized()
        val record = findByPluginId(pluginId) ?: return null
        val sourceId = record.catalogSourceId?.takeIf { it.isNotBlank() } ?: return null
        val candidates = listCatalogVersions(sourceId, pluginId)
            .filter { version -> compareVersions(version.version, record.installedVersion) > 0 }
            .sortedWith { left, right -> compareVersions(right.version, left.version) }
            .map { version -> version to evaluateCatalogVersion(version, hostVersion) }
        val (candidate, gate) = candidates.firstOrNull { (_, gate) -> gate.installable }
            ?: candidates.firstOrNull()
            ?: return null
        val source = getRepositorySource(sourceId)
        return PluginUpdateAvailability(
            pluginId = record.pluginId,
            installedVersion = record.installedVersion,
            latestVersion = candidate.version,
            updateAvailable = true,
            canUpgrade = gate.installable,
            publishedAt = candidate.publishedAt,
            changelogSummary = summarizeChangelog(candidate.changelog),
            permissionDiff = calculatePermissionDiff(
                current = record.permissionSnapshot,
                target = candidate.permissions,
            ),
            compatibilityState = gate.compatibilityState,
            incompatibilityReason = gate.unsupportedReason.takeIf { !gate.installable }.orEmpty(),
            catalogSourceId = sourceId,
            packageUrl = candidate.packageUrl,
            sourceBadge = source?.let {
                PluginSourceBadge(
                    sourceType = PluginSourceType.REPOSITORY,
                    label = it.title,
                    highlighted = true,
                )
            },
        )
    }

    fun getUpdateAvailability(
        pluginId: String,
        hostVersion: String,
        @Suppress("UNUSED_PARAMETER") supportedProtocolVersion: Int,
    ): PluginUpdateAvailability? = getUpdateAvailability(pluginId, hostVersion)

    fun setEnabled(pluginId: String, enabled: Boolean): PluginInstallRecord {
        requireInitialized()
        val current = requireRecord(pluginId)
        if (enabled && current.compatibilityState.status == PluginCompatibilityStatus.INCOMPATIBLE) {
            val noteSuffix = current.compatibilityState.notes
                .takeIf { it.isNotBlank() }
                ?.let { " $it" }
                .orEmpty()
            throw IllegalStateException("Cannot enable plugin due to compatibility issues.$noteSuffix")
        }
        if (current.enabled == enabled) return current
        return persistUpdatedRecord(current = current, enabled = enabled, lastUpdatedAt = timeProvider())
    }

    fun updateFailureState(pluginId: String, failureState: PluginFailureState): PluginInstallRecord {
        requireInitialized()
        val current = requireRecord(pluginId)
        if (current.failureState == failureState) return current
        return persistUpdatedRecord(current = current, failureState = failureState, lastUpdatedAt = timeProvider())
    }

    fun clearFailureState(pluginId: String): PluginInstallRecord =
        updateFailureState(pluginId, PluginFailureState.none())

    fun uninstall(pluginId: String, policy: PluginUninstallPolicy): PluginUninstallResult {
        requireInitialized()
        val current = requireRecord(pluginId)
        if (policy == PluginUninstallPolicy.REMOVE_DATA) {
            pluginDataRemover.removePluginData(current)
            current.localPackagePath.takeIf { it.isNotBlank() }?.let(::File)?.delete()
            current.extractedDir.takeIf { it.isNotBlank() }?.let(::File)?.deleteRecursively()
            runBlocking(Dispatchers.IO) {
                pluginConfigDao?.delete(pluginId)
            }
        }
        delete(pluginId)
        return PluginUninstallResult(
            pluginId = pluginId,
            policy = policy,
            removedData = policy == PluginUninstallPolicy.REMOVE_DATA,
        )
    }

    fun resolveConfigSnapshot(
        pluginId: String,
        boundary: PluginConfigStorageBoundary,
    ): PluginConfigStoreSnapshot {
        requireRecord(pluginId)
        val persisted = runBlocking(Dispatchers.IO) {
            requireConfigDao().get(pluginId)?.toSnapshot()
        } ?: PluginConfigStoreSnapshot()
        return boundary.createSnapshot(
            coreValues = (boundary.coreDefaults + persisted.coreValues)
                .filterKeys { key -> key in boundary.coreFieldKeys },
            extensionValues = persisted.extensionValues
                .filterKeys { key -> key in boundary.extensionFieldKeys },
        )
    }

    fun saveCoreConfig(
        pluginId: String,
        boundary: PluginConfigStorageBoundary,
        coreValues: Map<String, PluginStaticConfigValue>,
    ): PluginConfigStoreSnapshot {
        val current = resolveConfigSnapshot(pluginId, boundary)
        val snapshot = boundary.createSnapshot(
            coreValues = coreValues,
            extensionValues = current.extensionValues,
        )
        persistConfigSnapshot(pluginId, snapshot)
        return snapshot
    }

    fun saveExtensionConfig(
        pluginId: String,
        boundary: PluginConfigStorageBoundary,
        extensionValues: Map<String, PluginStaticConfigValue>,
    ): PluginConfigStoreSnapshot {
        val current = resolveConfigSnapshot(pluginId, boundary)
        val snapshot = boundary.createSnapshot(
            coreValues = current.coreValues,
            extensionValues = extensionValues,
        )
        persistConfigSnapshot(pluginId, snapshot)
        return snapshot
    }

    fun getInstalledStaticConfigSchema(pluginId: String): PluginStaticConfigSchema? {
        val schemaPath = resolveInstalledStaticConfigSchemaPath(pluginId) ?: return null
        return runCatching {
            PluginStaticConfigJson.decodeSchema(JSONObject(File(schemaPath).readText(Charsets.UTF_8)))
        }.getOrNull()
    }

    fun resolveInstalledStaticConfigSchemaPath(pluginId: String): String? {
        val record = requireRecord(pluginId)
        return resolveInstalledSnapshotConfigFile(
            extractedDir = record.extractedDir,
            relativePath = record.packageContractSnapshot?.config?.staticSchema.orEmpty(),
        )?.absolutePath
    }

    fun resolveInstalledSettingsSchemaPath(pluginId: String): String? {
        val record = requireRecord(pluginId)
        return resolveInstalledSnapshotConfigFile(
            extractedDir = record.extractedDir,
            relativePath = record.packageContractSnapshot?.config?.settingsSchema.orEmpty(),
        )?.absolutePath
    }

    fun evaluateCatalogVersion(
        version: PluginCatalogVersion,
        hostVersion: String,
    ): PluginCatalogVersionGateResult {
        return PluginCatalogVersionGate.evaluate(
            version = version,
            hostVersion = hostVersion,
            supportedProtocolVersion = SUPPORTED_PROTOCOL_VERSION,
        )
    }

    fun evaluateCatalogVersion(
        version: PluginCatalogVersion,
        hostVersion: String,
        supportedProtocolVersion: Int,
    ): PluginCatalogVersionGateResult {
        return PluginCatalogVersionGate.evaluate(
            version = version,
            hostVersion = hostVersion,
            supportedProtocolVersion = supportedProtocolVersion,
        )
    }

    fun validateLocalPackage(packageFile: File, hostVersion: String): PluginPackageValidationResult {
        return PluginPackageValidator(
            hostVersion = hostVersion,
            supportedProtocolVersion = SUPPORTED_PROTOCOL_VERSION,
        ).validate(packageFile)
    }

    fun buildLocalPackageInstallBlockedException(
        validation: PluginPackageValidationResult,
    ): PluginPackageInstallBlockedException {
        return PluginPackageInstallBlockedException(
            installable = validation.installable,
            compatibilityState = validation.compatibilityState,
            validationIssues = validation.validationIssues,
            message = describeLocalPackageInstallFailure(validation),
        )
    }

    fun buildLocalPackageInstallBlockedException(error: Throwable): PluginPackageInstallBlockedException {
        return PluginPackageInstallBlockedException(
            installable = false,
            compatibilityState = PluginCompatibilityState.unknown(),
            validationIssues = emptyList(),
            message = error.message ?: "Plugin package validation failed.",
        )
    }

    private fun persistUpdatedRecord(
        current: PluginInstallRecord,
        enabled: Boolean = current.enabled,
        uninstallPolicy: PluginUninstallPolicy = current.uninstallPolicy,
        failureState: PluginFailureState = current.failureState,
        lastUpdatedAt: Long = current.lastUpdatedAt,
    ): PluginInstallRecord {
        val updated = PluginInstallRecord.restoreFromPersistedState(
            manifestSnapshot = current.manifestSnapshot,
            source = current.source,
            packageContractSnapshot = current.packageContractSnapshot,
            permissionSnapshot = current.permissionSnapshot,
            compatibilityState = current.compatibilityState,
            uninstallPolicy = uninstallPolicy,
            enabled = enabled,
            failureState = failureState,
            catalogSourceId = current.catalogSourceId,
            installedPackageUrl = current.installedPackageUrl,
            lastCatalogCheckAtEpochMillis = current.lastCatalogCheckAtEpochMillis,
            installedAt = current.installedAt,
            lastUpdatedAt = lastUpdatedAt,
            localPackagePath = current.localPackagePath,
            extractedDir = current.extractedDir,
        )
        upsert(updated)
        return updated
    }

    private fun requireRecord(pluginId: String): PluginInstallRecord =
        findByPluginId(pluginId) ?: error("Plugin install record not found for pluginId=$pluginId")

    private fun publishRecord(record: PluginInstallRecord) {
        _records.value = _records.value
            .filterNot { current -> current.pluginId == record.pluginId }
            .plus(record)
            .sortedByDescending { current -> current.lastUpdatedAt }
    }

    private fun refreshCatalogState() {
        val sources = listRepositorySources()
        _repositorySources.value = sources
        _catalogEntries.value = sources.flatMap { source ->
            source.plugins.map { entry -> source.toEntryRecord(entry) }
        }
    }

    private fun assembleRepositorySource(sourceEntity: PluginCatalogSourceEntity): PluginRepositorySource {
        val entries = runBlocking(Dispatchers.IO) {
            requireCatalogDao().listEntries(sourceEntity.sourceId).map { entity -> assembleCatalogEntry(entity) }
        }
        return sourceEntity.toModel(entries)
    }

    private fun assembleCatalogEntry(entity: PluginCatalogEntryEntity): PluginCatalogEntry {
        val versions = runBlocking(Dispatchers.IO) {
            requireCatalogDao().listVersions(entity.sourceId, entity.pluginId).map(PluginCatalogVersionEntity::toModel)
        }
        return entity.toModel(versions)
    }

    private fun persistConfigSnapshot(pluginId: String, snapshot: PluginConfigStoreSnapshot) {
        requireRecord(pluginId)
        runBlocking(Dispatchers.IO) {
            requireConfigDao().upsert(
                snapshot.toEntity(
                    pluginId = pluginId,
                    updatedAt = timeProvider(),
                ),
            )
        }
    }

    private fun repairAndProjectInstallRecordForHost(record: PluginInstallRecord): PluginInstallRecord {
        val repaired = recoverMissingPackageContractSnapshot(record)
        if (repaired != record) {
            runCatching {
                runBlocking(Dispatchers.IO) {
                    requireDao().upsertRecord(repaired.toWriteModel())
                }
            }
        }
        return projectInstallRecordForHost(repaired)
    }

    private fun projectInstallRecordForHost(record: PluginInstallRecord): PluginInstallRecord {
        val manifest = record.manifestSnapshot
        val notes = com.astrbot.android.feature.plugin.data.unsupportedProtocolCompatibilityNote(
            protocolVersion = manifest.protocolVersion,
            supportedProtocolVersion = SUPPORTED_PROTOCOL_VERSION,
        ) ?: record.compatibilityState.notes
        val compatibilityState = PluginCompatibilityState.fromChecks(
            protocolSupported = manifest.protocolVersion == SUPPORTED_PROTOCOL_VERSION,
            minHostVersionSatisfied = record.compatibilityState.minHostVersionSatisfied,
            maxHostVersionSatisfied = record.compatibilityState.maxHostVersionSatisfied,
            notes = notes,
        )
        if (compatibilityState == record.compatibilityState) return record
        return PluginInstallRecord.restoreFromPersistedState(
            manifestSnapshot = record.manifestSnapshot,
            source = record.source,
            packageContractSnapshot = record.packageContractSnapshot,
            permissionSnapshot = record.permissionSnapshot,
            compatibilityState = compatibilityState,
            uninstallPolicy = record.uninstallPolicy,
            enabled = record.enabled,
            failureState = record.failureState,
            catalogSourceId = record.catalogSourceId,
            installedPackageUrl = record.installedPackageUrl,
            lastCatalogCheckAtEpochMillis = record.lastCatalogCheckAtEpochMillis,
            installedAt = record.installedAt,
            lastUpdatedAt = record.lastUpdatedAt,
            localPackagePath = record.localPackagePath,
            extractedDir = record.extractedDir,
        )
    }

    private fun recoverMissingPackageContractSnapshot(record: PluginInstallRecord): PluginInstallRecord {
        if (record.packageContractSnapshot != null) return record
        if (record.manifestSnapshot.protocolVersion != SUPPORTED_PROTOCOL_VERSION) return record
        val recovered = loadPackageContractSnapshotFromExtractedDir(record)
            ?: loadPackageContractSnapshotFromPackage(record)
            ?: return record
        return PluginInstallRecord.restoreFromPersistedState(
            manifestSnapshot = record.manifestSnapshot,
            source = record.source,
            packageContractSnapshot = recovered,
            permissionSnapshot = record.permissionSnapshot,
            compatibilityState = record.compatibilityState,
            uninstallPolicy = record.uninstallPolicy,
            enabled = record.enabled,
            failureState = record.failureState,
            catalogSourceId = record.catalogSourceId,
            installedPackageUrl = record.installedPackageUrl,
            lastCatalogCheckAtEpochMillis = record.lastCatalogCheckAtEpochMillis,
            installedAt = record.installedAt,
            lastUpdatedAt = record.lastUpdatedAt,
            localPackagePath = record.localPackagePath,
            extractedDir = record.extractedDir,
        )
    }

    private fun loadPackageContractSnapshotFromExtractedDir(record: PluginInstallRecord) =
        record.extractedDir.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.resolve("android-plugin.json")
            ?.takeIf(File::isFile)
            ?.readText(Charsets.UTF_8)
            ?.let(::decodePackageContractSnapshot)

    private fun loadPackageContractSnapshotFromPackage(record: PluginInstallRecord): com.astrbot.android.model.plugin.PluginPackageContractSnapshot? {
        val packageFile = record.localPackagePath.takeIf { it.isNotBlank() }?.let(::File) ?: return null
        if (!packageFile.isFile) return null
        return ZipInputStream(packageFile.inputStream().buffered()).use { input ->
            var entry = input.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.replace('\\', '/') == "android-plugin.json") {
                    return@use decodePackageContractSnapshot(input.readBytes().toString(Charsets.UTF_8))
                }
                entry = input.nextEntry
            }
            null
        }
    }

    private fun decodePackageContractSnapshot(rawJson: String) =
        runCatching {
            PluginPackageContractJson.decode(JSONObject(rawJson)).toPackageContractSnapshot()
        }.getOrNull()

    private fun resolveInstalledSnapshotConfigFile(extractedDir: String, relativePath: String): File? {
        val rootDir = extractedDir.takeIf { it.isNotBlank() }?.let(::File) ?: return null
        return resolvePluginPackageSnapshotFile(rootDir = rootDir, relativePath = relativePath)
    }

    private fun calculatePermissionDiff(
        current: List<PluginPermissionDeclaration>,
        target: List<PluginPermissionDeclaration>,
    ): PluginPermissionDiff {
        val currentById = current.associateBy { it.permissionId }
        val targetById = target.associateBy { it.permissionId }
        val added = target.filter { permission -> permission.permissionId !in currentById }
        val removed = current.filter { permission -> permission.permissionId !in targetById }
        val changed = target.filter { permission ->
            val existing = currentById[permission.permissionId] ?: return@filter false
            existing != permission && existing.riskLevel == permission.riskLevel
        }
        val riskUpgraded = target.mapNotNull { permission ->
            val existing = currentById[permission.permissionId] ?: return@mapNotNull null
            if (permission.riskLevel.ordinal > existing.riskLevel.ordinal) {
                PluginPermissionUpgrade(from = existing, to = permission)
            } else {
                null
            }
        }
        return PluginPermissionDiff(
            added = added,
            removed = removed,
            changed = changed,
            riskUpgraded = riskUpgraded,
        )
    }

    private fun summarizeChangelog(changelog: String): String =
        changelog.lineSequence().map(String::trim).firstOrNull(String::isNotBlank).orEmpty()

    private fun describeLocalPackageInstallFailure(validation: PluginPackageValidationResult): String {
        validation.validationIssues.firstOrNull()?.let { issue ->
            if (
                issue.message.startsWith("Damaged v2 plugin package:") ||
                issue.message.startsWith("Legacy v1 plugin packages are unsupported.")
            ) {
                return issue.message
            }
            return when (issue.code) {
                "legacy_contract" ->
                    "Legacy v1 plugin packages are unsupported. Upgrade the plugin package to protocol version 2."
                "missing_package_contract" -> "Damaged v2 plugin package: Missing android-plugin.json."
                "missing_runtime_bootstrap" -> "Damaged v2 plugin package: ${issue.message}"
                "invalid_package_contract" -> "Damaged v2 plugin package: ${issue.message}"
                else -> issue.message
            }
        }
        return validation.compatibilityState.notes.ifBlank { "Plugin package validation failed." }
    }

    private fun requireInitialized() {
        check(initialized.get()) { "PluginRepository has not been initialized." }
    }

    private fun requireDao(): PluginInstallAggregateDao =
        pluginDao ?: error("PluginRepository has not been initialized.")

    private fun requireCatalogDao(): PluginCatalogDao =
        pluginCatalogDao ?: error("PluginRepository has not been initialized.")

    private fun requireConfigDao(): PluginConfigSnapshotDao =
        pluginConfigDao ?: error("PluginRepository has not been initialized.")
}

typealias PluginInstallStore = com.astrbot.android.feature.plugin.data.PluginInstallStore
typealias PluginUninstallResult = com.astrbot.android.feature.plugin.data.PluginUninstallResult
typealias PluginCatalogVersionGateResult = com.astrbot.android.feature.plugin.data.PluginCatalogVersionGateResult
typealias PluginPackageInstallBlockedException =
    com.astrbot.android.feature.plugin.data.PluginPackageInstallBlockedException

interface PluginDataRemover {
    fun removePluginData(record: PluginInstallRecord)
}

object NoOpPluginDataRemover : PluginDataRemover {
    override fun removePluginData(record: PluginInstallRecord) = Unit
}

class PluginFileDataRemover : PluginDataRemover {
    override fun removePluginData(record: PluginInstallRecord) = Unit
}

typealias AppBackupRepository = com.astrbot.android.core.db.backup.AppBackupRepository
typealias ChatCompletionService = com.astrbot.android.core.runtime.llm.ChatCompletionService
typealias LlmResponseSegmenter = com.astrbot.android.core.runtime.llm.LlmResponseSegmenter
internal typealias NapCatLoginDiagnostics = com.astrbot.android.feature.qq.data.NapCatLoginDiagnostics
typealias NapCatLoginRepository = com.astrbot.android.feature.qq.data.NapCatLoginRepository
typealias NapCatLoginService = com.astrbot.android.feature.qq.data.NapCatLoginService
typealias ProfileDeletionGuard = com.astrbot.android.core.common.profile.ProfileDeletionGuard
typealias LastProfileDeletionBlockedException =
    com.astrbot.android.core.common.profile.LastProfileDeletionBlockedException
typealias ProfileCatalogKind = com.astrbot.android.core.common.profile.ProfileCatalogKind

object NapCatBridgeRepository {
    private val _config = MutableStateFlow(NapCatBridgeConfig())
    private val _runtimeState = MutableStateFlow(NapCatRuntimeState())

    val config: StateFlow<NapCatBridgeConfig> = _config.asStateFlow()
    val runtimeState: StateFlow<NapCatRuntimeState> = _runtimeState.asStateFlow()

    init {
        syncWithLoginRepository()
    }

    fun updateConfig(config: NapCatBridgeConfig) {
        _config.value = config
        syncWithLoginRepository()
    }

    fun applyRuntimeDefaults(defaults: NapCatBridgeConfig) {
        updateConfig(defaults)
    }

    fun markStarting() {
        _runtimeState.value = _runtimeState.value.copy(
            statusType = RuntimeStatus.STARTING,
            lastAction = "Start requested",
            details = "Preparing container and network installer",
        )
    }

    fun markRunning(
        pidHint: String = "local",
        details: String = "Local bridge is ready for QQ message transport",
    ) {
        _runtimeState.value = _runtimeState.value.copy(
            statusType = RuntimeStatus.RUNNING,
            pidHint = pidHint,
            details = details,
            progressPercent = 100,
        )
    }

    fun markProcessRunning(
        pidHint: String = "local",
        details: String = "NapCat process is running and waiting for the HTTP endpoint",
    ) {
        _runtimeState.value = _runtimeState.value.copy(
            statusType = RuntimeStatus.STARTING,
            pidHint = pidHint,
            details = details,
        )
    }

    fun markStopped(reason: String = "Stopped manually") {
        _runtimeState.value = NapCatRuntimeState(
            statusType = RuntimeStatus.STOPPED,
            lastAction = reason,
        )
    }

    fun markChecking() {
        val nextStatus = when (_runtimeState.value.statusType) {
            RuntimeStatus.RUNNING -> RuntimeStatus.RUNNING
            RuntimeStatus.STARTING -> RuntimeStatus.STARTING
            else -> RuntimeStatus.CHECKING
        }
        _runtimeState.value = _runtimeState.value.copy(
            statusType = nextStatus,
            lastAction = "Health check",
            details = "Checking NapCat runtime health",
        )
    }

    fun markError(message: String) {
        _runtimeState.value = _runtimeState.value.copy(
            statusType = RuntimeStatus.ERROR,
            lastAction = "Bridge error",
            details = message,
        )
    }

    fun updateProgress(
        label: String,
        percent: Int,
        indeterminate: Boolean,
        installerCached: Boolean = _runtimeState.value.installerCached,
    ) {
        _runtimeState.value = _runtimeState.value.copy(
            progressLabel = label,
            progressPercent = percent.coerceIn(0, 100),
            progressIndeterminate = indeterminate,
            installerCached = installerCached,
        )
    }

    fun markInstallerCached(cached: Boolean) {
        _runtimeState.value = _runtimeState.value.copy(installerCached = cached)
    }

    fun resetRuntimeStateForTests() {
        _runtimeState.value = NapCatRuntimeState()
        _config.value = NapCatBridgeConfig()
        syncWithLoginRepository()
    }

    private fun syncWithLoginRepository() {
        NapCatLoginRepository.installBridgeStateAccessors(
            configSnapshot = { _config.value },
            runtimeStateSnapshot = { _runtimeState.value },
        )
    }
}
