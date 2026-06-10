package com.elymbot.android.feature.geofence.data

import com.elymbot.android.feature.geofence.domain.GeofenceRuleRepositoryPort
import com.elymbot.android.feature.geofence.domain.model.ConfigGeofenceBinding
import com.elymbot.android.feature.geofence.domain.model.GeofenceExecutionRecord
import com.elymbot.android.feature.geofence.domain.model.GeofenceRegion
import com.elymbot.android.feature.geofence.domain.model.GeofenceRule
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow

@Singleton
class FeatureGeofenceRuleRepositoryPortAdapter @Inject constructor(
    private val repository: FeatureGeofenceRuleRepositoryStore,
) : GeofenceRuleRepositoryPort {
    override val rules: StateFlow<List<GeofenceRule>>
        get() = repository.rules

    override val bindings: StateFlow<List<ConfigGeofenceBinding>>
        get() = repository.bindings

    override suspend fun createRule(rule: GeofenceRule, regions: List<GeofenceRegion>): GeofenceRule =
        repository.createRule(rule, regions)

    override suspend fun updateRule(rule: GeofenceRule): GeofenceRule =
        repository.updateRule(rule)

    override suspend fun deleteRule(ruleId: String) =
        repository.deleteRule(ruleId)

    override suspend fun pauseRule(ruleId: String): GeofenceRule? =
        repository.pauseRule(ruleId)

    override suspend fun resumeRule(ruleId: String): GeofenceRule? =
        repository.resumeRule(ruleId)

    override suspend fun getRule(ruleId: String): GeofenceRule? =
        repository.getRule(ruleId)

    override suspend fun listRules(): List<GeofenceRule> =
        repository.listRules()

    override suspend fun replaceRegions(ruleId: String, regions: List<GeofenceRegion>): GeofenceRule? =
        repository.replaceRegions(ruleId, regions)

    override suspend fun upsertRegion(region: GeofenceRegion): GeofenceRegion =
        repository.upsertRegion(region)

    override suspend fun deleteRegion(regionId: String) =
        repository.deleteRegion(regionId)

    override suspend fun listAllConfigBindings(): List<ConfigGeofenceBinding> =
        repository.listAllConfigBindings()

    override suspend fun listConfigBindings(configId: String): List<ConfigGeofenceBinding> =
        repository.listConfigBindings(configId)

    override suspend fun upsertConfigBinding(binding: ConfigGeofenceBinding): ConfigGeofenceBinding =
        repository.upsertConfigBinding(binding)

    override suspend fun deleteConfigBinding(configId: String, ruleId: String) =
        repository.deleteConfigBinding(configId, ruleId)

    override suspend fun recordExecution(record: GeofenceExecutionRecord): GeofenceExecutionRecord =
        repository.recordExecution(record)

    override suspend fun listRecentExecutionRecords(ruleId: String, limit: Int): List<GeofenceExecutionRecord> =
        repository.listRecentExecutionRecords(ruleId, limit)

    override suspend fun latestExecutionRecord(ruleId: String): GeofenceExecutionRecord? =
        repository.latestExecutionRecord(ruleId)
}
