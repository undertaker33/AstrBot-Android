package com.astrbot.android.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PluginInstallAggregateDao {
    @Transaction
    @Query("SELECT * FROM plugin_install_records ORDER BY lastUpdatedAt DESC, pluginId ASC")
    abstract fun observePluginInstallAggregates(): Flow<List<PluginInstallAggregate>>

    @Transaction
    @Query("SELECT * FROM plugin_install_records ORDER BY lastUpdatedAt DESC, pluginId ASC")
    abstract suspend fun listPluginInstallAggregates(): List<PluginInstallAggregate>

    @Transaction
    @Query("SELECT * FROM plugin_install_records WHERE pluginId = :pluginId")
    abstract fun observePluginInstallAggregate(pluginId: String): Flow<PluginInstallAggregate?>

    @Transaction
    @Query("SELECT * FROM plugin_install_records WHERE pluginId = :pluginId")
    abstract suspend fun getPluginInstallAggregate(pluginId: String): PluginInstallAggregate?

    @Upsert
    protected abstract suspend fun upsertRecords(entities: List<PluginInstallRecordEntity>)

    @Upsert
    protected abstract suspend fun upsertManifestSnapshots(entities: List<PluginManifestSnapshotEntity>)

    @Upsert
    protected abstract suspend fun upsertManifestPermissions(entities: List<PluginManifestPermissionEntity>)

    @Upsert
    protected abstract suspend fun upsertPermissionSnapshots(entities: List<PluginPermissionSnapshotEntity>)

    @Query("DELETE FROM plugin_manifest_permissions WHERE pluginId = :pluginId")
    protected abstract suspend fun deleteManifestPermissions(pluginId: String)

    @Query("DELETE FROM plugin_permission_snapshots WHERE pluginId = :pluginId")
    protected abstract suspend fun deletePermissionSnapshots(pluginId: String)

    @Query("DELETE FROM plugin_install_records WHERE pluginId = :pluginId")
    abstract suspend fun delete(pluginId: String)

    @Query("SELECT COUNT(*) FROM plugin_install_records")
    abstract suspend fun count(): Int

    @Transaction
    open suspend fun upsertRecord(writeModel: PluginInstallWriteModel) {
        upsertRecords(listOf(writeModel.record))
        upsertManifestSnapshots(listOf(writeModel.manifestSnapshot))
        deleteManifestPermissions(writeModel.record.pluginId)
        deletePermissionSnapshots(writeModel.record.pluginId)
        if (writeModel.manifestPermissions.isNotEmpty()) {
            upsertManifestPermissions(writeModel.manifestPermissions)
        }
        if (writeModel.permissionSnapshots.isNotEmpty()) {
            upsertPermissionSnapshots(writeModel.permissionSnapshots)
        }
    }
}
