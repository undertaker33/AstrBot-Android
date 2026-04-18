package com.astrbot.android.feature.plugin.runtime.samples

import com.astrbot.android.data.NoOpPluginDataRemover
import com.astrbot.android.data.PluginRepository
import com.astrbot.android.data.db.PluginCatalogDao
import com.astrbot.android.data.db.PluginCatalogEntryEntity
import com.astrbot.android.data.db.PluginCatalogSourceEntity
import com.astrbot.android.data.db.PluginCatalogVersionEntity
import com.astrbot.android.data.db.PluginInstallAggregate
import com.astrbot.android.data.db.PluginInstallAggregateDao
import com.astrbot.android.data.db.PluginInstallRecordEntity
import com.astrbot.android.data.db.PluginInstallWriteModel
import com.astrbot.android.data.db.PluginManifestPermissionEntity
import com.astrbot.android.data.db.PluginManifestSnapshotEntity
import com.astrbot.android.data.db.PluginPackageContractSnapshotEntity
import com.astrbot.android.data.db.PluginPermissionSnapshotEntity
import com.astrbot.android.model.plugin.PluginCatalogEntryRecord
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginRepositorySource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.util.concurrent.atomic.AtomicBoolean

internal fun resetPluginRepositoryForSampleTest(
    initialized: Boolean,
    now: Long = 0L,
    installDao: PluginInstallAggregateDao = InMemoryPluginInstallAggregateDao(),
    catalogDao: PluginCatalogDao = InMemoryPluginCatalogDao(),
) {
    val repositoryClass = PluginRepository::class.java

    repositoryClass.getDeclaredField("pluginDao").apply {
        isAccessible = true
        set(PluginRepository, if (initialized) installDao else null)
    }
    repositoryClass.getDeclaredField("pluginCatalogDao").apply {
        isAccessible = true
        set(PluginRepository, if (initialized) catalogDao else null)
    }
    repositoryClass.getDeclaredField("timeProvider").apply {
        isAccessible = true
        set(PluginRepository, { now })
    }
    repositoryClass.getDeclaredField("pluginDataRemover").apply {
        isAccessible = true
        set(PluginRepository, NoOpPluginDataRemover)
    }

    @Suppress("UNCHECKED_CAST")
    (repositoryClass.getDeclaredField("_records").apply { isAccessible = true }.get(PluginRepository)
        as MutableStateFlow<List<PluginInstallRecord>>).value = emptyList()
    @Suppress("UNCHECKED_CAST")
    (repositoryClass.getDeclaredField("_repositorySources").apply { isAccessible = true }.get(PluginRepository)
        as MutableStateFlow<List<PluginRepositorySource>>).value = emptyList()
    @Suppress("UNCHECKED_CAST")
    (repositoryClass.getDeclaredField("_catalogEntries").apply { isAccessible = true }.get(PluginRepository)
        as MutableStateFlow<List<PluginCatalogEntryRecord>>).value = emptyList()

    val initializedField = repositoryClass.getDeclaredField("initialized").apply {
        isAccessible = true
    }.get(PluginRepository) as AtomicBoolean
    initializedField.set(initialized)
}

private class InMemoryPluginCatalogDao : PluginCatalogDao() {
    private val sources = linkedMapOf<String, PluginCatalogSourceEntity>()
    private val entries = linkedMapOf<String, MutableList<PluginCatalogEntryEntity>>()
    private val versions = linkedMapOf<String, MutableList<PluginCatalogVersionEntity>>()

    override suspend fun listSources(): List<PluginCatalogSourceEntity> = sources.values.sortedBy { it.sourceId }

    override suspend fun getSource(sourceId: String): PluginCatalogSourceEntity? = sources[sourceId]

    override suspend fun listEntries(sourceId: String): List<PluginCatalogEntryEntity> {
        return entries[sourceId].orEmpty().sortedBy { it.sortIndex }
    }

    override suspend fun getEntry(sourceId: String, pluginId: String): PluginCatalogEntryEntity? {
        return entries[sourceId].orEmpty().firstOrNull { it.pluginId == pluginId }
    }

    override suspend fun listVersions(sourceId: String, pluginId: String): List<PluginCatalogVersionEntity> {
        return versions["$sourceId::$pluginId"].orEmpty().sortedBy { it.sortIndex }
    }

    override suspend fun upsertSources(entities: List<PluginCatalogSourceEntity>) {
        entities.forEach { entity -> sources[entity.sourceId] = entity }
    }

    override suspend fun upsertEntries(entities: List<PluginCatalogEntryEntity>) {
        entities.groupBy { it.sourceId }.forEach { (sourceId, grouped) ->
            entries[sourceId] = grouped.sortedBy { it.sortIndex }.toMutableList()
        }
    }

    override suspend fun upsertVersions(entities: List<PluginCatalogVersionEntity>) {
        entities.groupBy { "${it.sourceId}::${it.pluginId}" }.forEach { (key, grouped) ->
            versions[key] = grouped.sortedBy { it.sortIndex }.toMutableList()
        }
    }

    override suspend fun deleteEntriesBySourceId(sourceId: String) {
        val pluginIds = entries[sourceId].orEmpty().map { it.pluginId }
        entries.remove(sourceId)
        pluginIds.forEach { pluginId -> versions.remove("$sourceId::$pluginId") }
    }

    override suspend fun deleteVersionsBySourceId(sourceId: String) {
        versions.keys.filter { it.startsWith("$sourceId::") }.toList().forEach(versions::remove)
    }
}

private class InMemoryPluginInstallAggregateDao : PluginInstallAggregateDao() {
    private val aggregates = linkedMapOf<String, PluginInstallAggregate>()
    private val state = MutableStateFlow<List<PluginInstallAggregate>>(emptyList())

    override fun observePluginInstallAggregates(): Flow<List<PluginInstallAggregate>> = state.asStateFlow()

    override suspend fun listPluginInstallAggregates(): List<PluginInstallAggregate> = state.value

    override fun observePluginInstallAggregate(pluginId: String): Flow<PluginInstallAggregate?> {
        return state.map { aggregates ->
            aggregates.firstOrNull { aggregate -> aggregate.record.pluginId == pluginId }
        }
    }

    override suspend fun getPluginInstallAggregate(pluginId: String): PluginInstallAggregate? = aggregates[pluginId]

    override suspend fun upsertRecord(writeModel: PluginInstallWriteModel) {
        aggregates[writeModel.record.pluginId] = PluginInstallAggregate(
            record = writeModel.record,
            manifestSnapshots = listOf(writeModel.manifestSnapshot),
            packageContractSnapshots = listOfNotNull(writeModel.packageContractSnapshot),
            manifestPermissions = writeModel.manifestPermissions,
            permissionSnapshots = writeModel.permissionSnapshots,
        )
        publish()
    }

    override suspend fun upsertRecords(entities: List<PluginInstallRecordEntity>) = Unit

    override suspend fun upsertManifestSnapshots(entities: List<PluginManifestSnapshotEntity>) = Unit

    override suspend fun upsertPackageContractSnapshots(entities: List<PluginPackageContractSnapshotEntity>) = Unit

    override suspend fun upsertManifestPermissions(entities: List<PluginManifestPermissionEntity>) = Unit

    override suspend fun upsertPermissionSnapshots(entities: List<PluginPermissionSnapshotEntity>) = Unit

    override suspend fun deleteManifestPermissions(pluginId: String) = Unit

    override suspend fun deletePackageContractSnapshots(pluginId: String) = Unit

    override suspend fun deletePermissionSnapshots(pluginId: String) = Unit

    override suspend fun delete(pluginId: String) {
        aggregates.remove(pluginId)
        publish()
    }

    override suspend fun count(): Int = aggregates.size

    private fun publish() {
        state.value = aggregates.values.sortedWith(
            compareByDescending<PluginInstallAggregate> { it.record.lastUpdatedAt }
                .thenBy { it.record.pluginId },
        )
    }
}
