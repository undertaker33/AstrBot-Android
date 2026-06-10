package com.elymbot.android.feature.geofence.data

import com.elymbot.android.data.db.geofence.ConfigGeofenceBindingEntity
import com.elymbot.android.data.db.geofence.GeofenceExecutionRecordEntity
import com.elymbot.android.data.db.geofence.GeofenceRegionEntity
import com.elymbot.android.data.db.geofence.GeofenceRuleAggregate
import com.elymbot.android.data.db.geofence.GeofenceRuleDao
import com.elymbot.android.data.db.geofence.GeofenceRuleEntity
import com.elymbot.android.feature.geofence.domain.model.ConfigGeofenceBinding
import com.elymbot.android.feature.geofence.domain.model.GeofenceActionType
import com.elymbot.android.feature.geofence.domain.model.GeofenceExecutionFailureCodes
import com.elymbot.android.feature.geofence.domain.model.GeofenceExecutionRecord
import com.elymbot.android.feature.geofence.domain.model.GeofenceRegion
import com.elymbot.android.feature.geofence.domain.model.GeofenceRule
import com.elymbot.android.feature.geofence.domain.model.GeofenceRuleStatus
import com.elymbot.android.feature.geofence.domain.model.GeofenceRuleValidation
import com.elymbot.android.feature.geofence.domain.model.GeofenceTransition
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Singleton
class FeatureGeofenceRuleRepositoryStore @Inject constructor(
    private val dao: GeofenceRuleDao,
) {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _rules = MutableStateFlow<List<GeofenceRule>>(emptyList())
    private val _bindings = MutableStateFlow<List<ConfigGeofenceBinding>>(emptyList())

    val rules: StateFlow<List<GeofenceRule>> = _rules.asStateFlow()
    val bindings: StateFlow<List<ConfigGeofenceBinding>> = _bindings.asStateFlow()

    init {
        repositoryScope.launch {
            dao.observeRulesWithRegions().collect { aggregates ->
                _rules.value = aggregates.map { it.toDomain() }
            }
        }
        repositoryScope.launch {
            dao.observeConfigBindings().collect { entities ->
                _bindings.value = entities.map { it.toDomain() }
            }
        }
    }

    suspend fun createRule(
        rule: GeofenceRule,
        regions: List<GeofenceRegion> = rule.regions,
    ): GeofenceRule {
        val now = System.currentTimeMillis()
        val normalizedRegions = regions.map { region -> normalizeRegion(rule.ruleId, region, now) }
        val normalizedRule = rule.copy(
            regions = normalizedRegions,
            createdAt = if (rule.createdAt == 0L) now else rule.createdAt,
            updatedAt = now,
        )
        GeofenceRuleValidation.requireValidRule(normalizedRule)
        dao.upsertRuleWithRegions(
            rule = normalizedRule.toEntity(),
            regions = normalizedRegions.map { it.toEntity() },
        )
        return getRule(normalizedRule.ruleId) ?: normalizedRule
    }

    suspend fun updateRule(rule: GeofenceRule): GeofenceRule {
        GeofenceRuleValidation.requireValidRule(rule.copy(regions = emptyList()))
        val existing = dao.getRuleWithRegions(rule.ruleId)?.toDomain()
        val updated = rule.copy(
            regions = existing?.regions.orEmpty(),
            createdAt = existing?.createdAt ?: rule.createdAt,
            updatedAt = System.currentTimeMillis(),
        )
        dao.upsertRule(updated.toEntity())
        return getRule(updated.ruleId) ?: updated
    }

    suspend fun deleteRule(ruleId: String) {
        dao.deleteRule(ruleId)
    }

    suspend fun pauseRule(ruleId: String): GeofenceRule? {
        val existing = getRule(ruleId) ?: return null
        val updated = existing.copy(enabled = false, status = GeofenceRuleStatus.PAUSED)
        return updateRule(updated)
    }

    suspend fun resumeRule(ruleId: String): GeofenceRule? {
        val existing = getRule(ruleId) ?: return null
        val updated = existing.copy(enabled = true, status = GeofenceRuleStatus.ACTIVE)
        return updateRule(updated)
    }

    suspend fun getRule(ruleId: String): GeofenceRule? =
        dao.getRuleWithRegions(ruleId)?.toDomain()

    suspend fun listRules(): List<GeofenceRule> =
        dao.listRulesWithRegions().map { it.toDomain() }

    suspend fun replaceRegions(ruleId: String, regions: List<GeofenceRegion>): GeofenceRule? {
        val existing = getRule(ruleId) ?: return null
        val now = System.currentTimeMillis()
        val normalizedRegions = regions.map { region -> normalizeRegion(ruleId, region, now) }
        normalizedRegions.forEach(GeofenceRuleValidation::requireValidRegion)
        dao.replaceRegionsAndTouchRule(
            rule = existing.copy(updatedAt = now).toEntity(),
            regions = normalizedRegions.map { it.toEntity() },
        )
        return getRule(ruleId)
    }

    suspend fun upsertRegion(region: GeofenceRegion): GeofenceRegion {
        val normalized = normalizeRegion(region.ruleId, region, System.currentTimeMillis())
        GeofenceRuleValidation.requireValidRegion(normalized)
        requireReference(dao.ruleExists(normalized.ruleId)) {
            "Cannot upsert geofence region for missing rule ${normalized.ruleId}"
        }
        dao.upsertRegion(normalized.toEntity())
        return normalized
    }

    suspend fun deleteRegion(regionId: String) {
        dao.deleteRegion(regionId)
    }

    suspend fun listAllConfigBindings(): List<ConfigGeofenceBinding> =
        dao.listAllConfigBindings().map { it.toDomain() }

    suspend fun listConfigBindings(configId: String): List<ConfigGeofenceBinding> =
        dao.listConfigBindings(configId).map { it.toDomain() }

    suspend fun upsertConfigBinding(binding: ConfigGeofenceBinding): ConfigGeofenceBinding {
        val now = System.currentTimeMillis()
        val normalized = binding.copy(
            createdAt = if (binding.createdAt == 0L) now else binding.createdAt,
            updatedAt = now,
        )
        GeofenceRuleValidation.requireValidBinding(normalized)
        requireReference(dao.ruleExists(normalized.ruleId)) {
            "Cannot bind missing geofence rule ${normalized.ruleId}"
        }
        requireReference(dao.configExists(normalized.configId)) {
            "Cannot bind geofence rule to missing config ${normalized.configId}"
        }
        dao.upsertConfigBinding(normalized.toEntity())
        return normalized
    }

    suspend fun deleteConfigBinding(configId: String, ruleId: String) {
        dao.deleteConfigBinding(configId, ruleId)
    }

    suspend fun recordExecution(record: GeofenceExecutionRecord): GeofenceExecutionRecord {
        GeofenceRuleValidation.requireValidExecutionRecord(record)
        if (record.requiresLiveReferences()) {
            requireReference(dao.ruleExists(record.ruleId)) {
                "Cannot record geofence execution for missing rule ${record.ruleId}"
            }
            requireReference(dao.regionBelongsToRule(record.ruleId, record.regionId)) {
                "Cannot record geofence execution for missing region ${record.regionId}"
            }
            requireReference(dao.configBindingExists(record.configId, record.ruleId)) {
                "Cannot record geofence execution for missing config binding ${record.configId}/${record.ruleId}"
            }
        }
        dao.insertExecutionRecord(record.toEntity())
        return record
    }

    suspend fun listRecentExecutionRecords(ruleId: String, limit: Int = 5): List<GeofenceExecutionRecord> =
        dao.listRecentExecutionRecordsForRule(ruleId, limit.coerceAtLeast(1)).map { it.toDomain() }

    suspend fun latestExecutionRecord(ruleId: String): GeofenceExecutionRecord? =
        dao.latestExecutionRecordForRule(ruleId)?.toDomain()

    private fun normalizeRegion(ruleId: String, region: GeofenceRegion, now: Long): GeofenceRegion =
        region.copy(
            ruleId = ruleId,
            createdAt = if (region.createdAt == 0L) now else region.createdAt,
            updatedAt = now,
        )

    private inline fun requireReference(condition: Boolean, lazyMessage: () -> String) {
        if (!condition) {
            throw GeofenceReferenceException(lazyMessage())
        }
    }

    private fun GeofenceExecutionRecord.requiresLiveReferences(): Boolean =
        status != "failed" || errorCode !in GeofenceExecutionFailureCodes.staleTriggerAuditCodes
}

class GeofenceReferenceException(message: String) : IllegalArgumentException(message)

private fun GeofenceRuleAggregate.toDomain(): GeofenceRule =
    rule.toDomain(
        regions = regions
            .sortedWith(compareBy<GeofenceRegionEntity> { it.sortIndex }.thenBy { it.createdAt }.thenBy { it.regionId })
            .map { it.toDomain() },
    )

private fun GeofenceRuleEntity.toDomain(regions: List<GeofenceRegion>): GeofenceRule =
    GeofenceRule(
        ruleId = ruleId,
        name = name,
        description = description,
        enabled = enabled,
        triggerEnter = triggerEnter,
        triggerExit = triggerExit,
        triggerDwell = triggerDwell,
        dwellDelayMillis = dwellDelayMillis,
        actionType = GeofenceActionType.fromPersistedValue(actionType),
        actionPrompt = actionPrompt,
        targetPlatform = targetPlatform,
        targetConversationId = targetConversationId,
        targetBotId = targetBotId,
        targetConfigProfileId = targetConfigProfileId,
        targetPersonaId = targetPersonaId,
        targetProviderId = targetProviderId,
        minimumTriggerIntervalMillis = minimumTriggerIntervalMillis,
        status = GeofenceRuleStatus.fromPersistedValue(status),
        lastRegisteredAt = lastRegisteredAt,
        lastTriggeredAt = lastTriggeredAt,
        lastError = lastError,
        regions = regions,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

private fun GeofenceRule.toEntity(): GeofenceRuleEntity =
    GeofenceRuleEntity(
        ruleId = ruleId,
        name = name,
        description = description,
        enabled = enabled,
        triggerEnter = triggerEnter,
        triggerExit = triggerExit,
        triggerDwell = triggerDwell,
        dwellDelayMillis = dwellDelayMillis,
        actionType = actionType.persistedValue,
        actionPrompt = actionPrompt,
        targetPlatform = targetPlatform,
        targetConversationId = targetConversationId,
        targetBotId = targetBotId,
        targetConfigProfileId = targetConfigProfileId,
        targetPersonaId = targetPersonaId,
        targetProviderId = targetProviderId,
        minimumTriggerIntervalMillis = minimumTriggerIntervalMillis,
        status = status.persistedValue,
        lastRegisteredAt = lastRegisteredAt,
        lastTriggeredAt = lastTriggeredAt,
        lastError = lastError,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

private fun GeofenceRegionEntity.toDomain(): GeofenceRegion =
    GeofenceRegion(
        regionId = regionId,
        ruleId = ruleId,
        label = label,
        latitude = latitude,
        longitude = longitude,
        radiusMeters = radiusMeters,
        addressLabel = addressLabel,
        sortIndex = sortIndex,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

private fun GeofenceRegion.toEntity(): GeofenceRegionEntity =
    GeofenceRegionEntity(
        regionId = regionId,
        ruleId = ruleId,
        label = label,
        latitude = latitude,
        longitude = longitude,
        radiusMeters = radiusMeters,
        addressLabel = addressLabel,
        sortIndex = sortIndex,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

private fun ConfigGeofenceBindingEntity.toDomain(): ConfigGeofenceBinding =
    ConfigGeofenceBinding(
        configId = configId,
        ruleId = ruleId,
        enabled = enabled,
        sortIndex = sortIndex,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

private fun ConfigGeofenceBinding.toEntity(): ConfigGeofenceBindingEntity =
    ConfigGeofenceBindingEntity(
        configId = configId,
        ruleId = ruleId,
        enabled = enabled,
        sortIndex = sortIndex,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

private fun GeofenceExecutionRecordEntity.toDomain(): GeofenceExecutionRecord =
    GeofenceExecutionRecord(
        executionId = executionId,
        ruleId = ruleId,
        regionId = regionId,
        configId = configId,
        transition = GeofenceTransition.fromPersistedValue(transition),
        startedAt = startedAt,
        completedAt = completedAt,
        status = status,
        errorCode = errorCode,
        errorMessage = errorMessage,
        deliverySummary = deliverySummary,
        locationSnapshotJson = locationSnapshotJson,
        triggerPayloadJson = triggerPayloadJson,
    )

private fun GeofenceExecutionRecord.toEntity(): GeofenceExecutionRecordEntity =
    GeofenceExecutionRecordEntity(
        executionId = executionId,
        ruleId = ruleId,
        regionId = regionId,
        configId = configId,
        transition = transition.persistedValue,
        startedAt = startedAt,
        completedAt = completedAt,
        status = status,
        errorCode = errorCode,
        errorMessage = errorMessage,
        deliverySummary = deliverySummary,
        locationSnapshotJson = locationSnapshotJson,
        triggerPayloadJson = triggerPayloadJson,
    )
