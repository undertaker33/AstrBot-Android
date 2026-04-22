package com.astrbot.android.feature.plugin.data

import kotlinx.coroutines.flow.collect

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
import com.astrbot.android.feature.plugin.data.PluginStoragePaths
import com.astrbot.android.feature.plugin.data.catalog.PluginCatalogSyncStore
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
import com.astrbot.android.model.plugin.PluginPackageContractJson
import com.astrbot.android.model.plugin.PluginPackageValidationIssue
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
import com.astrbot.android.model.plugin.resolvePluginPackageSnapshotFile
import com.astrbot.android.model.plugin.toSnapshot as toPackageContractSnapshot
import com.astrbot.android.feature.plugin.runtime.PluginPackageValidationResult
import com.astrbot.android.feature.plugin.runtime.PluginPackageValidator
import com.astrbot.android.feature.plugin.runtime.compareVersions
import com.astrbot.android.feature.plugin.runtime.normalizeArchiveEntryName
import java.io.File
import java.util.zip.ZipInputStream
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext
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

interface PluginRepositoryStatePort {
    val records: StateFlow<List<PluginInstallRecord>>
    val repositorySources: StateFlow<List<PluginRepositorySource>>
    val catalogEntries: StateFlow<List<PluginCatalogEntryRecord>>

    fun findByPluginId(pluginId: String): PluginInstallRecord?
}

object NoOpPluginDataRemover : PluginDataRemover {
    override fun removePluginData(record: PluginInstallRecord) = Unit
}

internal object EmptyPluginRepositoryStatePort : PluginRepositoryStatePort {
    override val records: StateFlow<List<PluginInstallRecord>> = MutableStateFlow(emptyList())
    override val repositorySources: StateFlow<List<PluginRepositorySource>> = MutableStateFlow(emptyList())
    override val catalogEntries: StateFlow<List<PluginCatalogEntryRecord>> = MutableStateFlow(emptyList())

    override fun findByPluginId(pluginId: String): PluginInstallRecord? = null
}

@Singleton
class FeaturePluginRepositoryStateOwner @Inject constructor(
    @ApplicationContext appContext: Context,
    pluginDao: PluginInstallAggregateDao,
    pluginCatalogDao: PluginCatalogDao,
    pluginConfigDao: PluginConfigSnapshotDao,
) : PluginRepositoryStatePort {
    private val repository = FeaturePluginRepository
    private val ownerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _records = MutableStateFlow<List<PluginInstallRecord>>(emptyList())
    private val _repositorySources = MutableStateFlow<List<PluginRepositorySource>>(emptyList())
    private val _catalogEntries = MutableStateFlow<List<PluginCatalogEntryRecord>>(emptyList())

    init {
        repository.installPersistence(
            pluginDao = pluginDao,
            pluginCatalogDao = pluginCatalogDao,
            pluginConfigDao = pluginConfigDao,
        )
        repository.initialize(appContext)
        ownerScope.launch {
            repository.records.collect { records ->
                _records.value = records
            }
        }
        ownerScope.launch {
            repository.repositorySources.collect { sources ->
                _repositorySources.value = sources
            }
        }
        ownerScope.launch {
            repository.catalogEntries.collect { entries ->
                _catalogEntries.value = entries
            }
        }
    }

    override val records: StateFlow<List<PluginInstallRecord>> = _records.asStateFlow()
    override val repositorySources: StateFlow<List<PluginRepositorySource>> = _repositorySources.asStateFlow()
    override val catalogEntries: StateFlow<List<PluginCatalogEntryRecord>> = _catalogEntries.asStateFlow()

    override fun findByPluginId(pluginId: String): PluginInstallRecord? {
        _records.value.firstOrNull { record -> record.pluginId == pluginId }?.let { return it }
        return repository.findByPluginId(pluginId)
    }

    fun initialize(context: Context) {
        repository.initialize(context)
    }
}

class PluginFileDataRemover(
    private val storagePaths: PluginStoragePaths,
) : PluginDataRemover {
    override fun removePluginData(record: PluginInstallRecord) {
        cleanupPath(storagePaths.privateDir(record.pluginId))
    }

    private fun cleanupPath(path: File) {
        if (path.exists()) {
            path.deleteRecursively()
        }
    }
}

data class PluginUninstallResult(
    val pluginId: String,
    val policy: PluginUninstallPolicy,
    val removedData: Boolean,
)

data class PluginCatalogVersionGateResult(
    val compatibilityState: PluginCompatibilityState,
    val installable: Boolean,
    val validationIssues: List<PluginPackageValidationIssue> = emptyList(),
) {
    val unsupportedReason: String
        get() = validationIssues.firstOrNull()?.message?.takeIf(String::isNotBlank)
            ?: compatibilityState.notes
}

class PluginPackageInstallBlockedException(
    val installable: Boolean,
    val compatibilityState: PluginCompatibilityState,
    val validationIssues: List<PluginPackageValidationIssue>,
    message: String,
) : IllegalStateException(message)

@Deprecated("Use PluginRepositoryPort from feature/plugin/domain. Direct access will be removed.")
@Suppress("DEPRECATION")
object FeaturePluginRepository : PluginInstallStore, PluginCatalogSyncStore {
    const val SUPPORTED_PROTOCOL_VERSION = 2

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

    internal fun installPersistence(
        pluginDao: PluginInstallAggregateDao,
        pluginCatalogDao: PluginCatalogDao,
        pluginConfigDao: PluginConfigSnapshotDao,
    ) {
        this.pluginDao = pluginDao
        this.pluginCatalogDao = pluginCatalogDao
        this.pluginConfigDao = pluginConfigDao
    }

    fun initialize(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        val applicationContext = context.applicationContext
        appContext = applicationContext
        pluginDataRemover = PluginFileDataRemover(PluginStoragePaths.fromFilesDir(applicationContext.filesDir))
        ensurePersistence(context)
        val dao = requireDao()
        _records.value = runBlocking(Dispatchers.IO) {
            dao.listPluginInstallAggregates()
                .map(PluginInstallAggregate::toInstallRecord)
                .map(::repairAndProjectInstallRecordForHost)
        }
        refreshCatalogState()
        repositoryScope.launch {
            dao.observePluginInstallAggregates().collect { aggregates ->
                _records.value = aggregates
                    .map(PluginInstallAggregate::toInstallRecord)
                    .map(::repairAndProjectInstallRecordForHost)
            }
        }
    }

    override fun findByPluginId(pluginId: String): PluginInstallRecord? {
        requireInitialized()
        _records.value.firstOrNull { record -> record.pluginId == pluginId }?.let { return it }
        return runBlocking(Dispatchers.IO) {
            requireDao().getPluginInstallAggregate(pluginId)
                ?.toInstallRecord()
                ?.let(::repairAndProjectInstallRecordForHost)
        }?.also { persistedRecord ->
            _records.value = _records.value
                .filterNot { current -> current.pluginId == persistedRecord.pluginId }
                .plus(persistedRecord)
                .sortedByDescending { current -> current.lastUpdatedAt }
        }
    }

    override fun upsert(record: PluginInstallRecord) {
        requireInitialized()
        val projectedRecord = projectInstallRecordForHost(record)
        runBlocking(Dispatchers.IO) {
            requireDao().upsertRecord(projectedRecord.toWriteModel())
        }
        _records.value = _records.value
            .filterNot { current -> current.pluginId == projectedRecord.pluginId }
            .plus(projectedRecord)
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
    ): PluginUpdateAvailability? {
        requireInitialized()
        val record = findByPluginId(pluginId) ?: return null
        return computeUpdateAvailability(
            record = record,
            hostVersion = hostVersion,
        )
    }

    fun getUpdateAvailability(
        pluginId: String,
        hostVersion: String,
        @Suppress("UNUSED_PARAMETER") supportedProtocolVersion: Int,
    ): PluginUpdateAvailability? {
        return getUpdateAvailability(
            pluginId = pluginId,
            hostVersion = hostVersion,
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
            removeInstalledPluginFiles(current)
            runBlocking(Dispatchers.IO) {
                requireConfigDao().delete(pluginId)
            }
        }
        delete(pluginId)
        return PluginUninstallResult(
            pluginId = pluginId,
            policy = policy,
            removedData = policy == PluginUninstallPolicy.REMOVE_DATA,
        )
    }

    private fun removeInstalledPluginFiles(record: PluginInstallRecord) {
        listOf(
            record.localPackagePath,
            record.extractedDir,
        ).filter(String::isNotBlank)
            .map(::File)
            .forEach { path ->
                if (path.exists()) {
                    path.deleteRecursively()
                }
            }
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
        val schemaPath = resolveInstalledStaticConfigSchemaPath(pluginId) ?: return null
        return runCatching {
            PluginStaticConfigJson.decodeSchema(
                JSONObject(File(schemaPath).readText()),
            )
        }.getOrNull()
    }

    fun resolveInstalledStaticConfigSchemaPath(pluginId: String): String? {
        requireInitialized()
        val record = requireRecord(pluginId)
        return resolveInstalledSnapshotConfigFile(
            record = record,
            relativePath = record.packageContractSnapshot?.config?.staticSchema.orEmpty(),
        )?.absolutePath
    }

    fun resolveInstalledSettingsSchemaPath(pluginId: String): String? {
        requireInitialized()
        val record = requireRecord(pluginId)
        return resolveInstalledSnapshotConfigFile(
            record = record,
            relativePath = record.packageContractSnapshot?.config?.settingsSchema.orEmpty(),
        )?.absolutePath
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

    private fun ensurePersistence(context: Context) {
        check(pluginDao != null && pluginCatalogDao != null && pluginConfigDao != null) {
            "FeaturePluginRepository persistence must be installed before initialize(context)."
        }
    }

    fun requireAppContext(): Context {
        return appContext ?: error("PluginRepository.initialize(context) must be called before use.")
    }

    private fun requireRecord(pluginId: String): PluginInstallRecord {
        return findByPluginId(pluginId)
            ?: error("Plugin install record not found for pluginId=$pluginId")
    }

    private fun resolveInstalledSnapshotConfigFile(
        record: PluginInstallRecord,
        relativePath: String,
    ): File? {
        val extractedDir = record.extractedDir.takeIf { it.isNotBlank() }?.let(::File) ?: return null
        return resolvePluginPackageSnapshotFile(
            rootDir = extractedDir,
            relativePath = relativePath,
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

    internal fun projectInstallRecordForHost(
        record: PluginInstallRecord,
        hostVersion: String? = currentHostVersionOrNull(),
    ): PluginInstallRecord {
        val projectedCompatibilityState = projectCompatibilityState(
            record = record,
            hostVersion = hostVersion,
        )
        if (projectedCompatibilityState == record.compatibilityState) {
            return record
        }
        return PluginInstallRecord.restoreFromPersistedState(
            manifestSnapshot = record.manifestSnapshot,
            source = record.source,
            packageContractSnapshot = record.packageContractSnapshot,
            permissionSnapshot = record.permissionSnapshot,
            compatibilityState = projectedCompatibilityState,
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

    private fun repairAndProjectInstallRecordForHost(record: PluginInstallRecord): PluginInstallRecord {
        val repairedRecord = recoverMissingPackageContractSnapshot(record)
        if (repairedRecord != record) {
            persistRecoveredPackageContractSnapshot(repairedRecord)
        }
        return projectInstallRecordForHost(repairedRecord)
    }

    private fun persistRecoveredPackageContractSnapshot(record: PluginInstallRecord) {
        runCatching {
            runBlocking(Dispatchers.IO) {
                requireDao().upsertRecord(record.toWriteModel())
            }
        }
    }

    private fun recoverMissingPackageContractSnapshot(record: PluginInstallRecord): PluginInstallRecord {
        if (record.packageContractSnapshot != null) {
            return record
        }
        if (record.manifestSnapshot.protocolVersion != SUPPORTED_PROTOCOL_VERSION) {
            return record
        }
        val recoveredSnapshot = loadPackageContractSnapshotFromExtractedDir(record)
            ?: loadPackageContractSnapshotFromPackage(record)
            ?: return record
        return PluginInstallRecord.restoreFromPersistedState(
            manifestSnapshot = record.manifestSnapshot,
            source = record.source,
            packageContractSnapshot = recoveredSnapshot,
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

    private fun loadPackageContractSnapshotFromExtractedDir(
        record: PluginInstallRecord,
    ): com.astrbot.android.model.plugin.PluginPackageContractSnapshot? {
        val extractedDir = record.extractedDir.takeIf { it.isNotBlank() }?.let(::File) ?: return null
        val contractFile = File(extractedDir, "android-plugin.json")
        if (!contractFile.isFile) {
            return null
        }
        return decodePackageContractSnapshot(contractFile.readText())
    }

    private fun loadPackageContractSnapshotFromPackage(
        record: PluginInstallRecord,
    ): com.astrbot.android.model.plugin.PluginPackageContractSnapshot? {
        val packageFile = record.localPackagePath.takeIf { it.isNotBlank() }?.let(::File) ?: return null
        if (!packageFile.isFile) {
            return null
        }
        return ZipInputStream(packageFile.inputStream().buffered()).use { input ->
            var entry = input.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && normalizeArchiveEntryName(entry.name) == "android-plugin.json") {
                    return@use decodePackageContractSnapshot(input.readBytes().toString(Charsets.UTF_8))
                }
                entry = input.nextEntry
            }
            null
        }
    }

    private fun decodePackageContractSnapshot(rawJson: String): com.astrbot.android.model.plugin.PluginPackageContractSnapshot? {
        return runCatching {
            PluginPackageContractJson.decode(JSONObject(rawJson)).toPackageContractSnapshot()
        }.getOrNull()
    }

    private fun projectCompatibilityState(
        record: PluginInstallRecord,
        hostVersion: String?,
    ): PluginCompatibilityState {
        val manifest = record.manifestSnapshot
        val minHostVersionSatisfied = hostVersion?.let { compareVersions(it, manifest.minHostVersion) >= 0 }
            ?: record.compatibilityState.minHostVersionSatisfied
        val maxHostVersionSatisfied = hostVersion?.let { resolvedHostVersion ->
            manifest.maxHostVersion.isBlank() || compareVersions(resolvedHostVersion, manifest.maxHostVersion) <= 0
        } ?: record.compatibilityState.maxHostVersionSatisfied
        val notes = buildProjectedInstallCompatibilityNotes(
            manifest = manifest,
            hostVersion = hostVersion,
            minHostVersionSatisfied = minHostVersionSatisfied,
            maxHostVersionSatisfied = maxHostVersionSatisfied,
            existingNotes = record.compatibilityState.notes,
        )
        return PluginCompatibilityState.fromChecks(
            protocolSupported = manifest.protocolVersion == SUPPORTED_PROTOCOL_VERSION,
            minHostVersionSatisfied = minHostVersionSatisfied,
            maxHostVersionSatisfied = maxHostVersionSatisfied,
            notes = notes,
        )
    }

    private fun buildProjectedInstallCompatibilityNotes(
        manifest: com.astrbot.android.model.plugin.PluginManifest,
        hostVersion: String?,
        minHostVersionSatisfied: Boolean?,
        maxHostVersionSatisfied: Boolean?,
        existingNotes: String,
    ): String {
        val notes = mutableListOf<String>()
        unsupportedProtocolCompatibilityNote(
            protocolVersion = manifest.protocolVersion,
            supportedProtocolVersion = SUPPORTED_PROTOCOL_VERSION,
        )?.let(notes::add)
        if (hostVersion != null && minHostVersionSatisfied == false) {
            notes += "Host version $hostVersion is below required minimum ${manifest.minHostVersion}."
        }
        if (hostVersion != null && maxHostVersionSatisfied == false) {
            notes += "Host version $hostVersion exceeds supported maximum ${manifest.maxHostVersion}."
        }
        return when {
            notes.isNotEmpty() -> notes.joinToString(separator = " ")
            existingNotes.isNotBlank() -> existingNotes
            else -> ""
        }
    }

    private fun currentHostVersionOrNull(): String? {
        val applicationContext = appContext ?: return null
        return runCatching {
            applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0).versionName
        }.getOrNull().orEmpty().ifBlank { null }
    }

    private fun computeUpdateAvailability(
        record: PluginInstallRecord,
        hostVersion: String,
    ): PluginUpdateAvailability? {
        val sourceId = record.catalogSourceId?.takeIf { it.isNotBlank() } ?: return null
        val versions = listCatalogVersions(sourceId = sourceId, pluginId = record.pluginId)
        val candidates = versions
            .filter { version -> compareVersions(version.version, record.installedVersion) > 0 }
            .sortedWith { left, right -> compareVersions(right.version, left.version) }
            .map { candidate ->
                candidate to evaluateCatalogVersion(
                    version = candidate,
                    hostVersion = hostVersion,
                )
            }
        val (candidate, gate) = candidates
            .firstOrNull { (_, gate) ->
                gate.installable
            }
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

    fun evaluateCatalogVersion(
        version: PluginCatalogVersion,
        hostVersion: String,
    ): PluginCatalogVersionGateResult {
        val minHostVersionSatisfied = version.minHostVersion.isBlank() ||
            compareVersions(hostVersion, version.minHostVersion) >= 0
        val maxHostVersionSatisfied = version.maxHostVersion.isBlank() ||
            compareVersions(hostVersion, version.maxHostVersion) <= 0
        val compatibilityState = PluginCompatibilityState.fromChecks(
            protocolSupported = version.protocolVersion == SUPPORTED_PROTOCOL_VERSION,
            minHostVersionSatisfied = minHostVersionSatisfied,
            maxHostVersionSatisfied = maxHostVersionSatisfied,
            notes = buildCompatibilityNotes(
                hostVersion = hostVersion,
                version = version,
            ),
        )
        return PluginCatalogVersionGateResult(
            compatibilityState = compatibilityState,
            installable = compatibilityState.status == PluginCompatibilityStatus.COMPATIBLE,
        )
    }

    fun evaluateCatalogVersion(
        version: PluginCatalogVersion,
        hostVersion: String,
        @Suppress("UNUSED_PARAMETER") supportedProtocolVersion: Int,
    ): PluginCatalogVersionGateResult {
        return evaluateCatalogVersion(
            version = version,
            hostVersion = hostVersion,
        )
    }

    fun validateLocalPackage(
        packageFile: File,
        hostVersion: String,
    ): PluginPackageValidationResult {
        return PluginPackageValidator(
            hostVersion = hostVersion,
            supportedProtocolVersion = SUPPORTED_PROTOCOL_VERSION,
        ).validate(packageFile)
    }

    fun validateLocalPackage(
        packageFile: File,
        hostVersion: String,
        @Suppress("UNUSED_PARAMETER") supportedProtocolVersion: Int,
    ): PluginPackageValidationResult {
        return validateLocalPackage(
            packageFile = packageFile,
            hostVersion = hostVersion,
        )
    }

    fun buildLocalPackageInstallBlockedException(
        validation: PluginPackageValidationResult,
    ): PluginPackageInstallBlockedException {
        return PluginPackageInstallBlockedException(
            installable = validation.installable,
            compatibilityState = validation.compatibilityState,
            validationIssues = validation.validationIssues,
            message = describeLocalPackageInstallFailure(
                compatibilityState = validation.compatibilityState,
                validationIssues = validation.validationIssues,
            ),
        )
    }

    fun buildLocalPackageInstallBlockedException(
        error: Throwable,
    ): PluginPackageInstallBlockedException {
        val issue = PluginPackageValidationIssue(
            code = "package_validation_failed",
            message = error.message ?: "Plugin package validation failed.",
        )
        return PluginPackageInstallBlockedException(
            installable = false,
            compatibilityState = PluginCompatibilityState.unknown(),
            validationIssues = listOf(issue),
            message = describeLocalPackageInstallFailure(
                compatibilityState = PluginCompatibilityState.unknown(),
                validationIssues = listOf(issue),
            ),
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
        version: PluginCatalogVersion,
    ): String {
        val notes = mutableListOf<String>()
        unsupportedProtocolCompatibilityNote(
            protocolVersion = version.protocolVersion,
            supportedProtocolVersion = SUPPORTED_PROTOCOL_VERSION,
        )?.let(notes::add)
        if (compareVersions(hostVersion, version.minHostVersion) < 0) {
            notes += "Host version $hostVersion is below required minimum ${version.minHostVersion}."
        }
        if (version.maxHostVersion.isNotBlank() && compareVersions(hostVersion, version.maxHostVersion) > 0) {
            notes += "Host version $hostVersion exceeds supported maximum ${version.maxHostVersion}."
        }
        return notes.joinToString(separator = " ")
    }

    private fun describeLocalPackageInstallFailure(
        compatibilityState: PluginCompatibilityState,
        validationIssues: List<PluginPackageValidationIssue>,
    ): String {
        validationIssues.firstOrNull { it.code == "legacy_contract" }?.let { issue ->
            return normalizePackageValidationIssueMessage(issue)
        }
        if (compatibilityState.protocolSupported == false && compatibilityState.notes.isNotBlank()) {
            return compatibilityState.notes
        }
        validationIssues.firstOrNull()?.let { issue ->
            return normalizePackageValidationIssueMessage(issue)
        }
        return compatibilityState.notes.takeIf(String::isNotBlank)
            ?: "Plugin package is not installable."
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

@Suppress("DEPRECATION")
internal fun unsupportedProtocolCompatibilityNote(
    protocolVersion: Int,
    supportedProtocolVersion: Int,
): String? {
    if (protocolVersion == supportedProtocolVersion) {
        return null
    }
    return if (protocolVersion == 1 && supportedProtocolVersion == FeaturePluginRepository.SUPPORTED_PROTOCOL_VERSION) {
        "Legacy v1 plugin packages are unsupported. Upgrade the plugin package to protocol version 2."
    } else {
        "Protocol version $protocolVersion is not supported."
    }
}

internal fun normalizePackageValidationIssueMessage(
    issue: PluginPackageValidationIssue,
): String {
    if (
        issue.message.startsWith("Damaged v2 plugin package:") ||
        issue.message.startsWith("Legacy v1 plugin packages are unsupported.")
    ) {
        return issue.message
    }
    return when (issue.code) {
        "legacy_contract" -> "Legacy v1 plugin packages are unsupported. Upgrade the plugin package to protocol version 2."
        "missing_package_contract" -> "Damaged v2 plugin package: Missing android-plugin.json."
        "missing_runtime_bootstrap" -> "Damaged v2 plugin package: ${issue.message}"
        "invalid_package_contract" -> normalizeInvalidPackageContractMessage(issue.message)
        else -> issue.message
    }
}

private fun normalizeInvalidPackageContractMessage(
    rawMessage: String,
): String {
    val runtimeKindPrefix = "runtime.kind has unsupported value:"
    return if (rawMessage.contains(runtimeKindPrefix)) {
        val runtimeKind = rawMessage.substringAfter(runtimeKindPrefix).trim().ifBlank { "unknown" }
        "Damaged v2 plugin package: Android requires runtime.kind = js_quickjs, but found $runtimeKind."
    } else {
        "Damaged v2 plugin package: $rawMessage"
    }
}

