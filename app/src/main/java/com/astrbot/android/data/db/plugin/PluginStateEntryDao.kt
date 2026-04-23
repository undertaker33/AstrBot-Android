package com.astrbot.android.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PluginStateEntryDao {
    @Query(
        """
        SELECT * FROM plugin_state_entries
        WHERE pluginId = :pluginId AND scopeKind = :scopeKind AND scopeId = :scopeId AND `key` = :key
        """,
    )
    suspend fun get(
        pluginId: String,
        scopeKind: String,
        scopeId: String,
        key: String,
    ): PluginStateEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PluginStateEntryEntity)

    @Query(
        """
        SELECT `key` FROM plugin_state_entries
        WHERE pluginId = :pluginId AND scopeKind = :scopeKind AND scopeId = :scopeId
          AND (:escapedPrefix = '' OR `key` LIKE :escapedPrefix || '%' ESCAPE '\')
        ORDER BY `key` ASC
        """,
    )
    suspend fun listKeys(
        pluginId: String,
        scopeKind: String,
        scopeId: String,
        escapedPrefix: String,
    ): List<String>

    @Query(
        """
        DELETE FROM plugin_state_entries
        WHERE pluginId = :pluginId AND scopeKind = :scopeKind AND scopeId = :scopeId AND `key` = :key
        """,
    )
    suspend fun delete(
        pluginId: String,
        scopeKind: String,
        scopeId: String,
        key: String,
    )

    @Query(
        """
        DELETE FROM plugin_state_entries
        WHERE pluginId = :pluginId AND scopeKind = :scopeKind AND scopeId = :scopeId
          AND (:escapedPrefix = '' OR `key` LIKE :escapedPrefix || '%' ESCAPE '\')
        """,
    )
    suspend fun clearScope(
        pluginId: String,
        scopeKind: String,
        scopeId: String,
        escapedPrefix: String,
    )

    @Query("DELETE FROM plugin_state_entries WHERE pluginId = :pluginId")
    suspend fun deleteByPluginId(pluginId: String)
}
