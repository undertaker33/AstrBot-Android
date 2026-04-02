package com.astrbot.android.data

import com.astrbot.android.data.db.PluginInstallAggregate
import com.astrbot.android.data.db.PluginInstallAggregateDao
import com.astrbot.android.data.db.PluginInstallRecordEntity
import com.astrbot.android.data.db.PluginInstallWriteModel
import com.astrbot.android.data.db.PluginManifestPermissionEntity
import com.astrbot.android.data.db.PluginManifestSnapshotEntity
import com.astrbot.android.data.db.PluginPermissionSnapshotEntity
import com.astrbot.android.data.db.toInstallRecord
import com.astrbot.android.data.db.toWriteModel
import com.astrbot.android.model.plugin.PluginCompatibilityState
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginManifest
import com.astrbot.android.model.plugin.PluginPermissionDeclaration
import com.astrbot.android.model.plugin.PluginRiskLevel
import com.astrbot.android.model.plugin.PluginSource
import com.astrbot.android.model.plugin.PluginSourceType
import com.astrbot.android.model.plugin.PluginUninstallPolicy
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginRepositoryTest {
    @Test
    fun repository_rejects_reads_before_initialize_instead_of_silent_placeholder_success() {
        resetPluginRepositoryForTest(initialized = false)

        val failure = runCatching {
            PluginRepository.findByPluginId("com.example.demo")
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message?.contains("initialize") == true)
    }

    @Test
    fun repository_rejects_writes_before_initialize_instead_of_silent_placeholder_success() {
        resetPluginRepositoryForTest(initialized = false)

        val failure = runCatching {
            PluginRepository.upsert(sampleRecord(version = "1.0.0"))
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message?.contains("initialize") == true)
    }

    @Test
    fun repository_reads_existing_record_from_store_when_memory_cache_is_empty() {
        val dao = InMemoryPluginInstallAggregateDao()
        val record = sampleRecord(version = "1.2.0", enabled = true, installedAt = 111L, lastUpdatedAt = 222L)
        dao.seed(record)
        resetPluginRepositoryForTest(dao = dao, initialized = true)

        val found = PluginRepository.findByPluginId(record.pluginId)

        assertEquals(record, found)
    }

    @Test
    fun repository_upsert_persists_record_through_store_and_updates_cache() {
        val dao = InMemoryPluginInstallAggregateDao()
        val record = sampleRecord(version = "2.0.0", enabled = true, installedAt = 333L, lastUpdatedAt = 444L)
        resetPluginRepositoryForTest(dao = dao, initialized = true)

        PluginRepository.upsert(record)

        assertEquals(record, runBlocking { dao.getPluginInstallAggregate(record.pluginId) }?.toInstallRecord())
        assertEquals(record, PluginRepository.records.value.single())
    }

    @Test
    fun repository_enables_compatible_plugin_and_updates_store() {
        val dao = InMemoryPluginInstallAggregateDao()
        val record = sampleRecord(version = "1.0.0", enabled = false, lastUpdatedAt = 10L)
        dao.seed(record)
        resetPluginRepositoryForTest(dao = dao, initialized = true, now = 500L)

        val updated = PluginRepository.setEnabled(record.pluginId, true)

        assertTrue(updated.enabled)
        assertEquals(500L, updated.lastUpdatedAt)
        assertTrue(runBlocking { dao.getPluginInstallAggregate(record.pluginId) }!!.toInstallRecord().enabled)
    }

    @Test
    fun repository_blocks_enabling_incompatible_plugin() {
        val dao = InMemoryPluginInstallAggregateDao()
        val record = sampleRecord(
            version = "1.0.0",
            enabled = false,
            compatibilityState = PluginCompatibilityState.evaluated(
                protocolSupported = true,
                minHostVersionSatisfied = false,
                maxHostVersionSatisfied = true,
                notes = "Requires a newer host build.",
            ),
        )
        dao.seed(record)
        resetPluginRepositoryForTest(dao = dao, initialized = true, now = 700L)

        val failure = runCatching {
            PluginRepository.setEnabled(record.pluginId, true)
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message?.contains("compatibility", ignoreCase = true) == true)
        assertEquals(record, runBlocking { dao.getPluginInstallAggregate(record.pluginId) }?.toInstallRecord())
    }

    @Test
    fun repository_updates_uninstall_policy_and_persists_it() {
        val dao = InMemoryPluginInstallAggregateDao()
        val record = sampleRecord(version = "1.0.0", uninstallPolicy = PluginUninstallPolicy.KEEP_DATA, lastUpdatedAt = 10L)
        dao.seed(record)
        resetPluginRepositoryForTest(dao = dao, initialized = true, now = 800L)

        val updated = PluginRepository.updateUninstallPolicy(record.pluginId, PluginUninstallPolicy.REMOVE_DATA)

        assertEquals(PluginUninstallPolicy.REMOVE_DATA, updated.uninstallPolicy)
        assertEquals(800L, updated.lastUpdatedAt)
        assertEquals(
            PluginUninstallPolicy.REMOVE_DATA,
            runBlocking { dao.getPluginInstallAggregate(record.pluginId) }!!.toInstallRecord().uninstallPolicy,
        )
    }

    @Test
    fun repository_uninstall_keep_data_skips_data_cleanup_and_removes_record() {
        val dao = InMemoryPluginInstallAggregateDao()
        val record = sampleRecord(version = "1.0.0", uninstallPolicy = PluginUninstallPolicy.KEEP_DATA)
        dao.seed(record)
        val remover = FakePluginDataRemover()
        resetPluginRepositoryForTest(dao = dao, initialized = true, dataRemover = remover)

        val result = PluginRepository.uninstall(record.pluginId, PluginUninstallPolicy.KEEP_DATA)

        assertEquals(record.pluginId, result.pluginId)
        assertEquals(PluginUninstallPolicy.KEEP_DATA, result.policy)
        assertEquals(false, result.removedData)
        assertTrue(remover.removedRecords.isEmpty())
        assertEquals(null, runBlocking { dao.getPluginInstallAggregate(record.pluginId) })
    }

    @Test
    fun repository_uninstall_remove_data_triggers_cleanup_and_removes_record() {
        val dao = InMemoryPluginInstallAggregateDao()
        val record = sampleRecord(version = "1.0.0", uninstallPolicy = PluginUninstallPolicy.KEEP_DATA)
        dao.seed(record)
        val remover = FakePluginDataRemover()
        resetPluginRepositoryForTest(dao = dao, initialized = true, dataRemover = remover)

        val result = PluginRepository.uninstall(record.pluginId, PluginUninstallPolicy.REMOVE_DATA)

        assertEquals(record.pluginId, result.pluginId)
        assertEquals(PluginUninstallPolicy.REMOVE_DATA, result.policy)
        assertEquals(true, result.removedData)
        assertEquals(listOf(record.pluginId), remover.removedRecords.map { it.pluginId })
        assertEquals(null, runBlocking { dao.getPluginInstallAggregate(record.pluginId) })
    }
}

private class InMemoryPluginInstallAggregateDao : PluginInstallAggregateDao() {
    private val aggregates = linkedMapOf<String, PluginInstallAggregate>()
    private val state = MutableStateFlow<List<PluginInstallAggregate>>(emptyList())

    fun seed(record: PluginInstallRecord) {
        val writeModel = record.toWriteModel()
        aggregates[record.pluginId] = PluginInstallAggregate(
            record = writeModel.record,
            manifestSnapshots = listOf(writeModel.manifestSnapshot),
            manifestPermissions = writeModel.manifestPermissions,
            permissionSnapshots = writeModel.permissionSnapshots,
        )
        publish()
    }

    override fun observePluginInstallAggregates(): Flow<List<PluginInstallAggregate>> = state

    override suspend fun listPluginInstallAggregates(): List<PluginInstallAggregate> = state.value

    override fun observePluginInstallAggregate(pluginId: String): Flow<PluginInstallAggregate?> {
        return state.map { aggregates -> aggregates.firstOrNull { aggregate -> aggregate.record.pluginId == pluginId } }
    }

    override suspend fun getPluginInstallAggregate(pluginId: String): PluginInstallAggregate? = aggregates[pluginId]

    override suspend fun upsertRecord(writeModel: PluginInstallWriteModel) {
        aggregates[writeModel.record.pluginId] = PluginInstallAggregate(
            record = writeModel.record,
            manifestSnapshots = listOf(writeModel.manifestSnapshot),
            manifestPermissions = writeModel.manifestPermissions,
            permissionSnapshots = writeModel.permissionSnapshots,
        )
        publish()
    }

    override suspend fun upsertRecords(entities: List<PluginInstallRecordEntity>) = Unit

    override suspend fun upsertManifestSnapshots(entities: List<PluginManifestSnapshotEntity>) = Unit

    override suspend fun upsertManifestPermissions(entities: List<PluginManifestPermissionEntity>) = Unit

    override suspend fun upsertPermissionSnapshots(entities: List<PluginPermissionSnapshotEntity>) = Unit

    override suspend fun deleteManifestPermissions(pluginId: String) = Unit

    override suspend fun deletePermissionSnapshots(pluginId: String) = Unit

    override suspend fun delete(pluginId: String) {
        aggregates.remove(pluginId)
        publish()
    }

    override suspend fun count(): Int = aggregates.size

    private fun publish() {
        state.value = aggregates.values.sortedWith(
            compareByDescending<PluginInstallAggregate> { aggregate -> aggregate.record.lastUpdatedAt }
                .thenBy { aggregate -> aggregate.record.pluginId },
        )
    }
}

private fun sampleRecord(
    version: String,
    enabled: Boolean = false,
    installedAt: Long = 0L,
    lastUpdatedAt: Long = 0L,
    compatibilityState: PluginCompatibilityState = PluginCompatibilityState.evaluated(
        protocolSupported = true,
        minHostVersionSatisfied = true,
        maxHostVersionSatisfied = true,
    ),
    uninstallPolicy: PluginUninstallPolicy = PluginUninstallPolicy.default(),
): PluginInstallRecord {
    val manifest = PluginManifest(
        pluginId = "com.example.demo",
        version = version,
        protocolVersion = 1,
        author = "AstrBot",
        title = "Demo Plugin",
        description = "Example plugin",
        permissions = listOf(
            PluginPermissionDeclaration(
                permissionId = "net.access",
                title = "Network access",
                description = "Allows outgoing requests",
                riskLevel = PluginRiskLevel.MEDIUM,
                required = true,
            ),
        ),
        minHostVersion = "0.3.0",
        maxHostVersion = "",
        sourceType = PluginSourceType.LOCAL_FILE,
        entrySummary = "Example entry",
        riskLevel = PluginRiskLevel.LOW,
    )
    return PluginInstallRecord.restoreFromPersistedState(
        manifestSnapshot = manifest,
        source = PluginSource(
            sourceType = PluginSourceType.LOCAL_FILE,
            location = "/tmp/$version.zip",
            importedAt = lastUpdatedAt,
        ),
        permissionSnapshot = manifest.permissions,
        compatibilityState = compatibilityState,
        uninstallPolicy = uninstallPolicy,
        enabled = enabled,
        installedAt = installedAt,
        lastUpdatedAt = lastUpdatedAt,
        localPackagePath = "/tmp/$version.zip",
        extractedDir = "/tmp/$version",
    )
}

private fun resetPluginRepositoryForTest(
    dao: PluginInstallAggregateDao = InMemoryPluginInstallAggregateDao(),
    initialized: Boolean,
    now: Long = 0L,
    dataRemover: PluginDataRemover = NoOpPluginDataRemover,
) {
    val repositoryClass = PluginRepository::class.java
    repositoryClass.getDeclaredField("pluginDao").apply {
        isAccessible = true
        set(PluginRepository, dao)
    }

    @Suppress("UNCHECKED_CAST")
    val recordsField = repositoryClass.getDeclaredField("_records").apply {
        isAccessible = true
    }.get(PluginRepository) as MutableStateFlow<List<PluginInstallRecord>>
    recordsField.value = emptyList()

    val initializedField = repositoryClass.getDeclaredField("initialized").apply {
        isAccessible = true
    }.get(PluginRepository) as AtomicBoolean
    initializedField.set(initialized)

    repositoryClass.getDeclaredField("timeProvider").apply {
        isAccessible = true
        set(PluginRepository, { now })
    }
    repositoryClass.getDeclaredField("pluginDataRemover").apply {
        isAccessible = true
        set(PluginRepository, dataRemover)
    }
}

private class FakePluginDataRemover : PluginDataRemover {
    val removedRecords = mutableListOf<PluginInstallRecord>()

    override fun removePluginData(record: PluginInstallRecord) {
        removedRecords += record
    }
}
