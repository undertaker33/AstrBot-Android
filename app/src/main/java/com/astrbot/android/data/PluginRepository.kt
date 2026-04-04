package com.astrbot.android.data

import android.content.Context
import com.astrbot.android.data.db.AstrBotDatabase
import com.astrbot.android.data.db.PluginCatalogDao
import com.astrbot.android.data.db.PluginCatalogEntryEntity
import com.astrbot.android.data.db.PluginCatalogSourceEntity
import com.astrbot.android.data.db.PluginCatalogVersionEntity
import com.astrbot.android.data.db.PluginConfigSnapshotDao
import com.astrbot.android.data.db.toEntryRecord
import com.astrbot.android.data.db.PluginInstallAggregate
import com.astrbot.android.data.db.PluginInstallAggregateDao
import com.astrbot.android.data.db.toEntity
import com.astrbot.android.data.db.toSnapshot
import com.astrbot.android.data.db.toModel
import com.astrbot.android.data.db.toSyncState
import com.astrbot.android.data.db.toInstallRecord
import com.astrbot.android.data.db.toWriteModel
import com.astrbot.android.data.plugin.catalog.PluginCatalogSyncStore
import com.astrbot.android.model.plugin.PluginCatalogEntryRecord
import com.astrbot.android.model.plugin.PluginCatalogSyncState
import com.astrbot.android.model.plugin.PluginCatalogEntry
import com.astrbot.android.model.plugin.PluginCatalogVersion
import com.astrbot.android.model.plugin.PluginCompatibilityState
import com.astrbot.android.model.plugin.PluginCompatibilityStatus
import com.astrbot.android.model.plugin.PluginConfigStorageBoundary
import com.astrbot.android.model.plugin.PluginConfigStoreSnapshot
import com.astrbot.android.model.plugin.PluginFailureState
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginPermissionDeclaration
import com.astrbot.android.model.plugin.PluginRepositorySource
import com.astrbot.android.model.plugin.PluginPermissionDiff
import com.astrbot.android.model.plugin.PluginPermissionUpgrade
import com.astrbot.android.model.plugin.PluginStaticConfigJson
import com.astrbot.android.model.plugin.PluginStaticConfigSchema
import com.astrbot.android.model.plugin.PluginStaticConfigValue
import com.astrbot.android.model.plugin.PluginSourceBadge
import com.astrbot.android.model.plugin.PluginSourceType
import com.astrbot.android.model.plugin.PluginUpdateAvailability
import com.astrbot.android.model.plugin.PluginUninstallPolicy
import com.astrbot.android.runtime.plugin.compareVersions
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

interface PluginInstallStore {
    fun findByPluginId(pluginId: String): PluginInstallRecord?

    fun upsert(record: PluginInstallRecord)
}

interface PluginDataRemover {
    fun removePluginData(record: PluginInstallRecord)
}

object NoOpPluginDataRemover : PluginDataRemover {
    override fun removePluginData(record: PluginInstallRecord) = Unit
}

data class PluginUninstallResult(
    val pluginId: String,
    val policy: PluginUninstallPolicy,
    val removedData: Boolean,
)

object PluginRepository : PluginInstallStore, PluginCatalogSyncStore {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initialized = AtomicBoolean(false)

    private var appContext: Context? = null
    private var pluginDao: PluginInstallAggregateDao? = null
    private var pluginCatalogDao: PluginCatalogDao? = null
    private var pluginConfigDao: PluginConfigSnapshotDao? = null
    private var timeProvider: () -> Long = System::currentTimeMillis
    private var pluginDataRemover: PluginDataRemover = NoOpPluginDataRemover
    private val _records = MutableStateFlow<List<PluginInstallRecord>>(emptyList())
    private val _repositorySources = MutableStateFlow<List<PluginRepositorySource>>(emptyList())
    private val _catalogEntries = MutableStateFlow<List<PluginCatalogEntryRecord>>(emptyList())

    val records: StateFlow<List<PluginInstallRecord>> = _records.asStateFlow()
    val repositorySources: StateFlow<List<PluginRepositorySource>> = _repositorySources.asStateFlow()
    val catalogEntries: StateFlow<List<PluginCatalogEntryRecord>> = _catalogEntries.asStateFlow()

    fun initialize(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        appContext = context.applicationContext
        val database = AstrBotDatabase.get(context)
        val dao = database.pluginInstallAggregateDao()
        pluginCatalogDao = database.pluginCatalogDao()
        pluginConfigDao = database.pluginConfigSnapshotDao()
        pluginDao = dao
        _records.value = runBlocking(Dispatchers.IO) {
            dao.listPluginInstallAggregates().map(PluginInstallAggregate::toInstallRecord)
        }
        refreshCatalogState()
        repositoryScope.launch {
            dao.observePluginInstallAggregates().collect { aggregates ->
                _records.value = aggregates.map(PluginInstallAggregate::toInstallRecord)
            }
        }
    }

    override fun findByPluginId(pluginId: String): PluginInstallRecord? {
        requireInitialized()
        _records.value.firstOrNull { record -> record.pluginId == pluginId }?.let { return it }
        return runBlocking(Dispatchers.IO) {
            requireDao().getPluginInstallAggregate(pluginId)?.toInstallRecord()
        }?.also { persistedRecord ->
            _records.value = _records.value
                .filterNot { current -> current.pluginId == persistedRecord.pluginId }
                .plus(persistedRecord)
                .sortedByDescending { current -> current.lastUpdatedAt }
        }
    }

    override fun upsert(record: PluginInstallRecord) {
        requireInitialized()
        runBlocking(Dispatchers.IO) {
            requireDao().upsertRecord(record.toWriteModel())
        }
        _records.value = _records.value
            .filterNot { current -> current.pluginId == record.pluginId }
            .plus(record)
            .sortedByDescending { current -> current.lastUpdatedAt }
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
        val sourceEntity = PluginCatalogSourceEntity(
            sourceId = source.sourceId,
            title = source.title,
            catalogUrl = source.catalogUrl,
            updatedAt = source.updatedAt,
            lastSyncAtEpochMillis = source.lastSyncAtEpochMillis,
            lastSyncStatus = source.lastSyncStatus.name,
            lastSyncErrorSummary = source.lastSyncErrorSummary,
        )
        runBlocking(Dispatchers.IO) {
            requireCatalogDao().upsertSources(listOf(sourceEntity))
        }
        refreshCatalogState()
    }

    override fun listRepositorySources(): List<PluginRepositorySource> {
        requireInitialized()
        return runBlocking(Dispatchers.IO) {
            buildList {
                requireCatalogDao().listSources().forEach { entity ->
                    add(assembleRepositorySource(entity))
                }
            }
        }
    }

    override fun getRepositorySource(sourceId: String): PluginRepositorySource? {
        requireInitialized()
        return runBlocking(Dispatchers.IO) {
            val sourceEntity = requireCatalogDao().getSource(sourceId) ?: return@runBlocking null
            assembleRepositorySource(sourceEntity)
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
        return runBlocking(Dispatchers.IO) {
            buildList {
                requireCatalogDao().listSources().forEach { sourceEntity ->
                    val source = assembleRepositorySource(sourceEntity)
                    source.plugins.forEach { entry ->
                        add(source.toEntryRecord(entry))
                    }
                }
            }
        }
    }

    fun listCatalogEntries(sourceId: String): List<PluginCatalogEntry> {
        requireInitialized()
        return runBlocking(Dispatchers.IO) {
            buildList {
                requireCatalogDao().listEntries(sourceId).forEach { entity ->
                    add(assembleCatalogEntry(entity))
                }
            }
        }
    }

    fun getCatalogEntry(sourceId: String, pluginId: String): PluginCatalogEntry? {
        requireInitialized()
        return runBlocking(Dispatchers.IO) {
            val entryEntity = requireCatalogDao().getEntry(sourceId, pluginId) ?: return@runBlocking null
            assembleCatalogEntry(entryEntity)
        }
    }

    override fun listCatalogVersions(sourceId: String, pluginId: String): List<PluginCatalogVersion> {
        requireInitialized()
        return runBlocking(Dispatchers.IO) {
            requireCatalogDao().listVersions(sourceId, pluginId).map(PluginCatalogVersionEntity::toModel)
        }
    }

    fun getUpdateAvailability(
        pluginId: String,
        hostVersion: String,
        supportedProtocolVersion: Int,
    ): PluginUpdateAvailability? {
        requireInitialized()
        val record = findByPluginId(pluginId) ?: return null
        return computeUpdateAvailability(
            record = record,
            hostVersion = hostVersion,
            supportedProtocolVersion = supportedProtocolVersion,
        )
    }

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
        if (current.enabled == enabled) {
            return current
        }
        return persistUpdatedRecord(
            current = current,
            enabled = enabled,
            lastUpdatedAt = timeProvider(),
        )
    }

    fun updateUninstallPolicy(
        pluginId: String,
        policy: PluginUninstallPolicy,
    ): PluginInstallRecord {
        requireInitialized()
        val current = requireRecord(pluginId)
        if (current.uninstallPolicy == policy) {
            return current
        }
        return persistUpdatedRecord(
            current = current,
            uninstallPolicy = policy,
            lastUpdatedAt = timeProvider(),
        )
    }

    fun updateFailureState(
        pluginId: String,
        failureState: PluginFailureState,
    ): PluginInstallRecord {
        requireInitialized()
        val current = requireRecord(pluginId)
        if (current.failureState == failureState) {
            return current
        }
        return persistUpdatedRecord(
            current = current,
            failureState = failureState,
            lastUpdatedAt = timeProvider(),
        )
    }

    fun clearFailureState(pluginId: String): PluginInstallRecord {
        return updateFailureState(
            pluginId = pluginId,
            failureState = PluginFailureState.none(),
        )
    }

    fun uninstall(
        pluginId: String,
        policy: PluginUninstallPolicy,
    ): PluginUninstallResult {
        requireInitialized()
        val current = requireRecord(pluginId)
        if (policy == PluginUninstallPolicy.REMOVE_DATA) {
            pluginDataRemover.removePluginData(current)
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
        requireInitialized()
        requireRecord(pluginId)
        val persisted = runBlocking(Dispatchers.IO) {
            requireConfigDao().get(pluginId)?.toSnapshot()
        } ?: PluginConfigStoreSnapshot()
        return boundary.createSnapshot(
            coreValues = (boundary.coreDefaults + persisted.coreValues)
                .filterKeys { it in boundary.coreFieldKeys },
            extensionValues = persisted.extensionValues.filterKeys { it in boundary.extensionFieldKeys },
        )
    }

    fun getInstalledStaticConfigSchema(pluginId: String): PluginStaticConfigSchema? {
        requireInitialized()
        val record = requireRecord(pluginId)
        val extractedDir = record.extractedDir.takeIf { it.isNotBlank() } ?: return null
        val schemaFile = File(extractedDir, "_conf_schema.json")
        if (!schemaFile.isFile) {
            return null
        }
        return runCatching {
            PluginStaticConfigJson.decodeSchema(
                JSONObject(schemaFile.readText()),
            )
        }.getOrNull()
    }

    fun saveCoreConfig(
        pluginId: String,
        boundary: PluginConfigStorageBoundary,
        coreValues: Map<String, PluginStaticConfigValue>,
    ): PluginConfigStoreSnapshot {
        requireInitialized()
        requireRecord(pluginId)
        val current = resolveConfigSnapshot(pluginId = pluginId, boundary = boundary)
        val snapshot = boundary.createSnapshot(
            coreValues = coreValues,
            extensionValues = current.extensionValues,
        )
        persistConfigSnapshot(pluginId = pluginId, snapshot = snapshot)
        return snapshot
    }

    fun saveExtensionConfig(
        pluginId: String,
        boundary: PluginConfigStorageBoundary,
        extensionValues: Map<String, PluginStaticConfigValue>,
    ): PluginConfigStoreSnapshot {
        requireInitialized()
        requireRecord(pluginId)
        val current = resolveConfigSnapshot(pluginId = pluginId, boundary = boundary)
        val snapshot = boundary.createSnapshot(
            coreValues = current.coreValues,
            extensionValues = extensionValues,
        )
        persistConfigSnapshot(pluginId = pluginId, snapshot = snapshot)
        return snapshot
    }

    private fun requireInitialized() {
        check(initialized.get() && pluginDao != null) {
            "PluginRepository.initialize(context) must be called before use."
        }
    }

    fun requireAppContext(): Context {
        return appContext ?: error("PluginRepository.initialize(context) must be called before use.")
    }

    private fun requireRecord(pluginId: String): PluginInstallRecord {
        return findByPluginId(pluginId)
            ?: error("Plugin install record not found for pluginId=$pluginId")
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

    private fun computeUpdateAvailability(
        record: PluginInstallRecord,
        hostVersion: String,
        supportedProtocolVersion: Int,
    ): PluginUpdateAvailability? {
        val sourceId = record.catalogSourceId?.takeIf { it.isNotBlank() } ?: return null
        if (record.source.sourceType == PluginSourceType.DIRECT_LINK) {
            return null
        }
        val versions = listCatalogVersions(sourceId = sourceId, pluginId = record.pluginId)
        val candidate = versions
            .filter { version -> compareVersions(version.version, record.installedVersion) > 0 }
            .sortedWith { left, right -> compareVersions(right.version, left.version) }
            .firstOrNull()
            ?: return null
        val compatibilityState = PluginCompatibilityState.fromChecks(
            protocolSupported = candidate.protocolVersion == supportedProtocolVersion,
            minHostVersionSatisfied = compareVersions(hostVersion, candidate.minHostVersion) >= 0,
            maxHostVersionSatisfied = candidate.maxHostVersion.isBlank() ||
                compareVersions(hostVersion, candidate.maxHostVersion) <= 0,
            notes = buildCompatibilityNotes(
                hostVersion = hostVersion,
                supportedProtocolVersion = supportedProtocolVersion,
                version = candidate,
            ),
        )
        val source = getRepositorySource(sourceId)
        return PluginUpdateAvailability(
            pluginId = record.pluginId,
            installedVersion = record.installedVersion,
            latestVersion = candidate.version,
            updateAvailable = true,
            canUpgrade = compatibilityState.status != PluginCompatibilityStatus.INCOMPATIBLE,
            publishedAt = candidate.publishedAt,
            changelogSummary = summarizeChangelog(candidate.changelog),
            permissionDiff = calculatePermissionDiff(
                current = record.permissionSnapshot,
                target = candidate.permissions,
            ),
            compatibilityState = compatibilityState,
            incompatibilityReason = compatibilityState.notes.takeIf {
                compatibilityState.status == PluginCompatibilityStatus.INCOMPATIBLE
            }.orEmpty(),
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

    private fun calculatePermissionDiff(
        current: List<PluginPermissionDeclaration>,
        target: List<PluginPermissionDeclaration>,
    ): PluginPermissionDiff {
        val currentById = current.associateBy { it.permissionId }
        val targetById = target.associateBy { it.permissionId }
        val added = target.filter { permission -> permission.permissionId !in currentById }
        val removed = current.filter { permission -> permission.permissionId !in targetById }
        val changed = buildList {
            target.forEach { permission ->
                val existing = currentById[permission.permissionId] ?: return@forEach
                if (existing != permission && existing.riskLevel == permission.riskLevel) {
                    add(permission)
                }
            }
        }
        val riskUpgraded = buildList {
            target.forEach { permission ->
                val existing = currentById[permission.permissionId] ?: return@forEach
                if (permission.riskLevel.ordinal > existing.riskLevel.ordinal) {
                    add(
                        PluginPermissionUpgrade(
                            from = existing,
                            to = permission,
                        ),
                    )
                }
            }
        }
        return PluginPermissionDiff(
            added = added,
            removed = removed,
            changed = changed,
            riskUpgraded = riskUpgraded,
        )
    }

    private fun summarizeChangelog(changelog: String): String {
        return changelog
            .lineSequence()
            .map { line -> line.trim() }
            .firstOrNull { line -> line.isNotBlank() }
            .orEmpty()
    }

    private fun buildCompatibilityNotes(
        hostVersion: String,
        supportedProtocolVersion: Int,
        version: PluginCatalogVersion,
    ): String {
        val notes = mutableListOf<String>()
        if (version.protocolVersion != supportedProtocolVersion) {
            notes += "Protocol version ${version.protocolVersion} is not supported."
        }
        if (compareVersions(hostVersion, version.minHostVersion) < 0) {
            notes += "Host version $hostVersion is below required minimum ${version.minHostVersion}."
        }
        if (version.maxHostVersion.isNotBlank() && compareVersions(hostVersion, version.maxHostVersion) > 0) {
            notes += "Host version $hostVersion exceeds supported maximum ${version.maxHostVersion}."
        }
        return notes.joinToString(separator = " ")
    }

    private suspend fun assembleRepositorySource(entity: PluginCatalogSourceEntity): PluginRepositorySource {
        val entries = buildList {
            requireCatalogDao().listEntries(entity.sourceId).forEach { entry ->
                add(assembleCatalogEntry(entry))
            }
        }
        return entity.toModel(entries = entries)
    }

    private suspend fun assembleCatalogEntry(entity: PluginCatalogEntryEntity): PluginCatalogEntry {
        return entity.toModel(
            versions = requireCatalogDao()
                .listVersions(entity.sourceId, entity.pluginId)
                .map(PluginCatalogVersionEntity::toModel),
        )
    }

    private fun refreshCatalogState() {
        _repositorySources.value = listRepositorySources()
        _catalogEntries.value = listAllCatalogEntries()
    }

    private fun requireDao(): PluginInstallAggregateDao = pluginDao
        ?: error("PluginRepository.initialize(context) must be called before use.")

    private fun requireCatalogDao(): PluginCatalogDao = pluginCatalogDao
        ?: error("PluginRepository.initialize(context) must be called before use.")

    private fun requireConfigDao(): PluginConfigSnapshotDao = pluginConfigDao
        ?: error("PluginRepository.initialize(context) must be called before use.")

    private fun persistConfigSnapshot(
        pluginId: String,
        snapshot: PluginConfigStoreSnapshot,
    ) {
        runBlocking(Dispatchers.IO) {
            requireConfigDao().upsert(
                snapshot.toEntity(
                    pluginId = pluginId,
                    updatedAt = timeProvider(),
                ),
            )
        }
    }
}
