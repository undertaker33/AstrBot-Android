package com.astrbot.android.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PluginConfigSnapshotDao {
    @Query("SELECT * FROM plugin_config_snapshots WHERE pluginId = :pluginId")
    suspend fun get(pluginId: String): PluginConfigSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PluginConfigSnapshotEntity)

    @Query("DELETE FROM plugin_config_snapshots WHERE pluginId = :pluginId")
    suspend fun delete(pluginId: String)
}
