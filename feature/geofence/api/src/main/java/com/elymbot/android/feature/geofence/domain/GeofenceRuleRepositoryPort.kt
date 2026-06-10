package com.elymbot.android.feature.geofence.domain

import com.elymbot.android.feature.geofence.domain.model.ConfigGeofenceBinding
import com.elymbot.android.feature.geofence.domain.model.GeofenceExecutionRecord
import com.elymbot.android.feature.geofence.domain.model.GeofenceRegion
import com.elymbot.android.feature.geofence.domain.model.GeofenceRule
import kotlinx.coroutines.flow.StateFlow

interface GeofenceRuleRepositoryPort {
    val rules: StateFlow<List<GeofenceRule>>
    val bindings: StateFlow<List<ConfigGeofenceBinding>>

    suspend fun createRule(rule: GeofenceRule, regions: List<GeofenceRegion> = rule.regions): GeofenceRule
    suspend fun updateRule(rule: GeofenceRule): GeofenceRule
    suspend fun deleteRule(ruleId: String)
    suspend fun pauseRule(ruleId: String): GeofenceRule?
    suspend fun resumeRule(ruleId: String): GeofenceRule?
    suspend fun getRule(ruleId: String): GeofenceRule?
    suspend fun listRules(): List<GeofenceRule>
    suspend fun replaceRegions(ruleId: String, regions: List<GeofenceRegion>): GeofenceRule?
    suspend fun upsertRegion(region: GeofenceRegion): GeofenceRegion
    suspend fun deleteRegion(regionId: String)
    suspend fun listAllConfigBindings(): List<ConfigGeofenceBinding>
    suspend fun listConfigBindings(configId: String): List<ConfigGeofenceBinding>
    suspend fun upsertConfigBinding(binding: ConfigGeofenceBinding): ConfigGeofenceBinding
    suspend fun deleteConfigBinding(configId: String, ruleId: String)
    suspend fun recordExecution(record: GeofenceExecutionRecord): GeofenceExecutionRecord
    suspend fun listRecentExecutionRecords(ruleId: String, limit: Int = 5): List<GeofenceExecutionRecord>
    suspend fun latestExecutionRecord(ruleId: String): GeofenceExecutionRecord?
}
