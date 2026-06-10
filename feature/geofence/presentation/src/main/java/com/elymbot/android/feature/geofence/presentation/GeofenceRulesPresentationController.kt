package com.elymbot.android.feature.geofence.presentation

import com.elymbot.android.feature.bot.domain.model.BotProfile
import com.elymbot.android.feature.geofence.domain.GeofenceRuleRepositoryPort
import com.elymbot.android.feature.geofence.domain.model.GeofenceExecutionRecord
import com.elymbot.android.feature.geofence.domain.model.GeofenceRule
import com.elymbot.android.feature.geofence.domain.runtime.GeofencePermissionStatus
import javax.inject.Inject

internal class GeofenceRulesPresentationController @Inject constructor(
    private val repository: GeofenceRuleRepositoryPort,
) {
    suspend fun createRule(
        draft: GeofenceRuleEditorDraft,
        selectedBot: BotProfile,
        permissionStatus: GeofencePermissionStatus,
    ): GeofenceRule {
        val ruleId = newGeofenceId("rule")
        val regions = draft.toRegions(ruleId)
        val rule = draft.toRule(
            selectedBot = selectedBot,
            ruleId = ruleId,
            permissionStatus = permissionStatus,
        ).copy(regions = regions)
        return repository.createRule(rule, regions)
    }

    suspend fun updateRule(
        existing: GeofenceRule,
        draft: GeofenceRuleEditorDraft,
        selectedBot: BotProfile,
        permissionStatus: GeofencePermissionStatus,
    ): GeofenceRule {
        val updated = draft.toRule(
            selectedBot = selectedBot,
            existing = existing,
            ruleId = existing.ruleId,
            permissionStatus = permissionStatus,
        )
        val saved = repository.updateRule(updated)
        return repository.replaceRegions(
            ruleId = existing.ruleId,
            regions = draft.toRegions(existing.ruleId, existing.regions),
        ) ?: saved
    }

    suspend fun pauseRule(ruleId: String): GeofenceRule? =
        repository.pauseRule(ruleId)

    suspend fun resumeRule(ruleId: String): GeofenceRule? =
        repository.resumeRule(ruleId)

    suspend fun deleteRule(ruleId: String) =
        repository.deleteRule(ruleId)

    suspend fun listRuns(
        ruleId: String,
        limit: Int = GeofenceRunHistoryLimit,
    ): List<GeofenceExecutionRecord> =
        repository.listRecentExecutionRecords(ruleId, limit)
}
