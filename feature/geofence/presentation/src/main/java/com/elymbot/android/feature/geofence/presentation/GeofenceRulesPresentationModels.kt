package com.elymbot.android.feature.geofence.presentation

import com.elymbot.android.feature.geofence.domain.model.GeofenceActionType
import com.elymbot.android.feature.geofence.domain.model.GeofenceExecutionRecord
import com.elymbot.android.feature.geofence.domain.model.GeofenceRegion
import com.elymbot.android.feature.geofence.domain.model.GeofenceRule

internal const val GeofenceRunHistoryLimit: Int = 10

internal data class GeofenceRuleUiListItemPresentation(
    val ruleId: String,
    val name: String,
    val regionSummary: String,
    val triggerSummary: String,
    val actionSummary: String,
    val statusSummary: String,
    val lastTriggeredAt: Long,
    val enabled: Boolean,
    val lastError: String,
)

internal data class GeofenceRulesUiPagePresentation(
    val currentPage: Int,
    val totalPages: Int,
    val visibleRules: List<GeofenceRuleUiListItemPresentation>,
    val canGoPrevious: Boolean,
    val canGoNext: Boolean,
)

internal data class GeofenceRuleRunUiPresentation(
    val executionId: String,
    val transition: String,
    val status: String,
    val startedAt: Long,
    val completedAt: Long,
    val errorCode: String,
    val summary: String,
)

internal fun buildGeofenceRulesUiPresentation(
    rules: List<GeofenceRule>,
    requestedPage: Int,
    pageSize: Int = 2,
): GeofenceRulesUiPagePresentation {
    require(pageSize > 0) { "pageSize must be greater than 0." }
    val totalPages = maxOf(1, (rules.size + pageSize - 1) / pageSize)
    val currentPage = requestedPage.coerceIn(1, totalPages)
    val startIndex = (currentPage - 1) * pageSize
    val visibleRules = rules.drop(startIndex).take(pageSize).map(::toUiListItem)
    return GeofenceRulesUiPagePresentation(
        currentPage = currentPage,
        totalPages = totalPages,
        visibleRules = visibleRules,
        canGoPrevious = currentPage > 1,
        canGoNext = currentPage < totalPages,
    )
}

internal fun buildGeofenceRuleRunUiPresentations(
    records: List<GeofenceExecutionRecord>,
): List<GeofenceRuleRunUiPresentation> {
    return records.map { record ->
        GeofenceRuleRunUiPresentation(
            executionId = record.executionId,
            transition = record.transition.persistedValue,
            status = record.status.ifBlank { "-" },
            startedAt = record.startedAt,
            completedAt = record.completedAt,
            errorCode = record.errorCode,
            summary = record.deliverySummary
                .ifBlank { record.errorMessage }
                .ifBlank { record.errorCode },
        )
    }
}

private fun toUiListItem(rule: GeofenceRule): GeofenceRuleUiListItemPresentation =
    GeofenceRuleUiListItemPresentation(
        ruleId = rule.ruleId,
        name = rule.name.ifBlank { rule.ruleId },
        regionSummary = rule.regions.regionSummary(),
        triggerSummary = triggerSummary(rule),
        actionSummary = actionSummary(rule.actionType, rule.actionPrompt),
        statusSummary = if (rule.enabled) rule.status.persistedValue else "paused",
        lastTriggeredAt = rule.lastTriggeredAt,
        enabled = rule.enabled,
        lastError = rule.lastError,
    )

private fun List<GeofenceRegion>.regionSummary(): String {
    val first = firstOrNull() ?: return "-"
    val label = first.addressLabel.ifBlank { first.label }.ifBlank { "Region" }
    val radius = "${first.radiusMeters.toInt()}m"
    val base = "$label - $radius"
    return if (size > 1) "$base +${size - 1}" else base
}

private fun triggerSummary(rule: GeofenceRule): String {
    val triggers = buildList {
        if (rule.triggerEnter) add("enter")
        if (rule.triggerExit) add("exit")
        if (rule.triggerDwell) add("dwell")
    }
    return triggers.joinToString(", ").ifBlank { "-" }
}

private fun actionSummary(
    actionType: GeofenceActionType,
    prompt: String,
): String {
    val promptPreview = prompt.trim().lineSequence().firstOrNull().orEmpty()
    return if (promptPreview.isBlank()) {
        actionType.persistedValue
    } else {
        "${actionType.persistedValue}: $promptPreview"
    }
}
