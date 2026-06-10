package com.elymbot.android.data.db.geofence

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.elymbot.android.data.db.ConfigProfileEntity

@Entity(tableName = "geofence_rules")
data class GeofenceRuleEntity(
    @PrimaryKey val ruleId: String,
    val name: String,
    val description: String,
    val enabled: Boolean,
    val triggerEnter: Boolean,
    val triggerExit: Boolean,
    val triggerDwell: Boolean,
    val dwellDelayMillis: Long,
    val actionType: String,
    val actionPrompt: String,
    val targetPlatform: String,
    val targetConversationId: String,
    val targetBotId: String,
    val targetConfigProfileId: String,
    val targetPersonaId: String,
    val targetProviderId: String,
    val minimumTriggerIntervalMillis: Long,
    val status: String,
    val lastRegisteredAt: Long,
    val lastTriggeredAt: Long,
    val lastError: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "geofence_regions",
    foreignKeys = [
        ForeignKey(
            entity = GeofenceRuleEntity::class,
            parentColumns = ["ruleId"],
            childColumns = ["ruleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["ruleId", "sortIndex"]),
    ],
)
data class GeofenceRegionEntity(
    @PrimaryKey val regionId: String,
    val ruleId: String,
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
    val addressLabel: String,
    val sortIndex: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "config_geofence_bindings",
    primaryKeys = ["configId", "ruleId"],
    foreignKeys = [
        ForeignKey(
            entity = ConfigProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["configId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = GeofenceRuleEntity::class,
            parentColumns = ["ruleId"],
            childColumns = ["ruleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["configId", "sortIndex"]),
        Index(value = ["ruleId"]),
    ],
)
data class ConfigGeofenceBindingEntity(
    val configId: String,
    val ruleId: String,
    val enabled: Boolean,
    val sortIndex: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "geofence_execution_records",
    indices = [
        Index(value = ["ruleId", "startedAt"]),
    ],
)
data class GeofenceExecutionRecordEntity(
    @PrimaryKey val executionId: String,
    val ruleId: String,
    val regionId: String,
    val configId: String,
    val transition: String,
    val startedAt: Long,
    val completedAt: Long,
    val status: String,
    val errorCode: String,
    val errorMessage: String,
    val deliverySummary: String,
    val locationSnapshotJson: String,
    val triggerPayloadJson: String,
)
