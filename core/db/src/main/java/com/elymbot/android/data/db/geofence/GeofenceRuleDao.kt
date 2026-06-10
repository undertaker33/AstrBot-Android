package com.elymbot.android.data.db.geofence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface GeofenceRuleDao {
    @Transaction
    @Query("SELECT * FROM geofence_rules ORDER BY updatedAt DESC, name ASC, ruleId ASC")
    fun observeRulesWithRegions(): Flow<List<GeofenceRuleAggregate>>

    @Transaction
    @Query("SELECT * FROM geofence_rules ORDER BY updatedAt DESC, name ASC, ruleId ASC")
    suspend fun listRulesWithRegions(): List<GeofenceRuleAggregate>

    @Transaction
    @Query("SELECT * FROM geofence_rules WHERE ruleId = :ruleId LIMIT 1")
    suspend fun getRuleWithRegions(ruleId: String): GeofenceRuleAggregate?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRule(entity: GeofenceRuleEntity)

    @Transaction
    suspend fun upsertRuleWithRegions(rule: GeofenceRuleEntity, regions: List<GeofenceRegionEntity>) {
        upsertRule(rule)
        replaceRegionsForRule(rule.ruleId, regions)
    }

    @Transaction
    suspend fun deleteRule(ruleId: String) {
        deleteRegionsForRule(ruleId)
        deleteConfigBindingsForRule(ruleId)
        deleteRuleEntity(ruleId)
    }

    @Query("DELETE FROM geofence_rules WHERE ruleId = :ruleId")
    suspend fun deleteRuleEntity(ruleId: String)

    @Transaction
    suspend fun replaceRegionsForRule(ruleId: String, regions: List<GeofenceRegionEntity>) {
        deleteRegionsForRule(ruleId)
        if (regions.isNotEmpty()) {
            upsertRegions(regions)
        }
    }

    @Transaction
    suspend fun replaceRegionsAndTouchRule(rule: GeofenceRuleEntity, regions: List<GeofenceRegionEntity>) {
        upsertRule(rule)
        replaceRegionsForRule(rule.ruleId, regions)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRegion(entity: GeofenceRegionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRegions(entities: List<GeofenceRegionEntity>)

    @Query("DELETE FROM geofence_regions WHERE ruleId = :ruleId")
    suspend fun deleteRegionsForRule(ruleId: String)

    @Query("DELETE FROM geofence_regions WHERE regionId = :regionId")
    suspend fun deleteRegion(regionId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM geofence_rules WHERE ruleId = :ruleId)")
    suspend fun ruleExists(ruleId: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM geofence_regions WHERE ruleId = :ruleId AND regionId = :regionId)")
    suspend fun regionBelongsToRule(ruleId: String, regionId: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM config_profiles WHERE id = :configId)")
    suspend fun configExists(configId: String): Boolean

    @Query("SELECT * FROM config_geofence_bindings ORDER BY configId ASC, sortIndex ASC, ruleId ASC")
    fun observeConfigBindings(): Flow<List<ConfigGeofenceBindingEntity>>

    @Query("SELECT * FROM config_geofence_bindings ORDER BY configId ASC, sortIndex ASC, ruleId ASC")
    suspend fun listAllConfigBindings(): List<ConfigGeofenceBindingEntity>

    @Query(
        """
        SELECT * FROM config_geofence_bindings
        WHERE configId = :configId
        ORDER BY sortIndex ASC, ruleId ASC
        """,
    )
    suspend fun listConfigBindings(configId: String): List<ConfigGeofenceBindingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConfigBinding(entity: ConfigGeofenceBindingEntity)

    @Query("DELETE FROM config_geofence_bindings WHERE configId = :configId AND ruleId = :ruleId")
    suspend fun deleteConfigBinding(configId: String, ruleId: String)

    @Query("DELETE FROM config_geofence_bindings WHERE ruleId = :ruleId")
    suspend fun deleteConfigBindingsForRule(ruleId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM config_geofence_bindings WHERE configId = :configId AND ruleId = :ruleId)")
    suspend fun configBindingExists(configId: String, ruleId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExecutionRecord(entity: GeofenceExecutionRecordEntity)

    @Query(
        """
        SELECT * FROM geofence_execution_records
        WHERE ruleId = :ruleId
        ORDER BY startedAt DESC
        LIMIT :limit
        """,
    )
    suspend fun listRecentExecutionRecordsForRule(ruleId: String, limit: Int): List<GeofenceExecutionRecordEntity>

    @Query(
        """
        SELECT * FROM geofence_execution_records
        WHERE ruleId = :ruleId
        ORDER BY startedAt DESC
        LIMIT 1
        """,
    )
    suspend fun latestExecutionRecordForRule(ruleId: String): GeofenceExecutionRecordEntity?
}
