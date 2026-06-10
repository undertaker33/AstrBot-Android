package com.elymbot.android.ui.config.geofence

import com.elymbot.android.feature.geofence.domain.GeofenceRuleRepositoryPort
import com.elymbot.android.feature.geofence.domain.model.ConfigGeofenceBinding
import com.elymbot.android.feature.geofence.domain.model.GeofenceActionType
import com.elymbot.android.feature.geofence.domain.model.GeofenceRegion
import com.elymbot.android.feature.geofence.domain.model.GeofenceRule
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceRuntimeReconciliationPort
import javax.inject.Inject

data class ConfigGeofenceBindingPresentation(
    val configId: String,
    val allRuleCount: Int,
    val loadedCount: Int,
    val summaryItems: List<ConfigGeofenceBindingSummary>,
    val emptyRules: Boolean,
    val showOpenRulesAction: Boolean,
    val selectedRuleIds: List<String>,
    val staleBindingRuleIds: List<String>,
    val items: List<ConfigGeofenceBindingListItem>,
)

data class ConfigGeofenceBindingSummary(
    val ruleName: String,
    val bindingEnabled: Boolean,
    val regionLabel: String?,
    val radiusMeters: Int?,
    val additionalRegionCount: Int,
    val triggerEnter: Boolean,
    val triggerExit: Boolean,
    val triggerDwell: Boolean,
    val actionType: GeofenceActionType,
    val actionPromptPreview: String,
)

data class ConfigGeofenceBindingListItem(
    val ruleId: String,
    val name: String,
    val description: String,
    val selected: Boolean,
    val bindingEnabled: Boolean,
    val ruleEnabled: Boolean,
    val sortIndex: Int,
    val summary: ConfigGeofenceBindingSummary,
)

data class ConfigGeofenceBindingDraft(
    val entries: List<Entry> = emptyList(),
) {
    data class Entry(
        val ruleId: String,
        val enabled: Boolean,
        val sortIndex: Int,
    )

    val selectedRuleIds: List<String>
        get() = entries.sortedBy { it.sortIndex }.map { it.ruleId }

    val enabledByRuleId: Map<String, Boolean>
        get() = entries.associate { it.ruleId to it.enabled }

    fun withRuleSelected(ruleId: String, selected: Boolean): ConfigGeofenceBindingDraft {
        val existing = entries.firstOrNull { it.ruleId == ruleId }
        return if (selected) {
            if (existing != null) {
                this
            } else {
                copy(entries = entries + Entry(ruleId = ruleId, enabled = true, sortIndex = entries.size))
            }
        } else {
            copy(entries = entries.filterNot { it.ruleId == ruleId }.reindexed())
        }
    }

    fun withBindingEnabled(ruleId: String, enabled: Boolean): ConfigGeofenceBindingDraft {
        return copy(
            entries = entries.map { entry ->
                if (entry.ruleId == ruleId) entry.copy(enabled = enabled) else entry
            },
        )
    }

    fun mergeAvailableRules(presentation: ConfigGeofenceBindingPresentation): ConfigGeofenceBindingDraft {
        return mergeAvailableRules(presentation.items.mapTo(linkedSetOf()) { item -> item.ruleId })
    }

    fun mergeAvailableRules(availableRuleIds: Set<String>): ConfigGeofenceBindingDraft {
        return copy(entries = entries.filter { entry -> entry.ruleId in availableRuleIds }.reindexed())
    }

    companion object {
        fun fromPresentation(presentation: ConfigGeofenceBindingPresentation): ConfigGeofenceBindingDraft {
            return ConfigGeofenceBindingDraft(
                entries = presentation.items
                    .filter { item -> item.selected }
                    .sortedWith(compareBy<ConfigGeofenceBindingListItem> { it.sortIndex }.thenBy { it.name.lowercase() })
                    .mapIndexed { index, item ->
                        Entry(ruleId = item.ruleId, enabled = item.bindingEnabled, sortIndex = index)
                    },
            )
        }
    }
}

class ConfigGeofenceBindingController @Inject constructor(
    private val repository: GeofenceRuleRepositoryPort,
    private val reconciliationPort: GeofenceRuntimeReconciliationPort,
) {
    fun buildPresentation(
        configId: String,
        rules: List<GeofenceRule> = repository.rules.value,
        bindings: List<ConfigGeofenceBinding> = repository.bindings.value,
    ): ConfigGeofenceBindingPresentation =
        buildConfigGeofenceBindingPresentation(configId = configId, rules = rules, bindings = bindings)

    suspend fun saveDraft(
        configId: String,
        draft: ConfigGeofenceBindingDraft,
    ): Result<Unit> = runCatching {
        val availableRuleIds = repository.listRules().mapTo(mutableSetOf()) { rule -> rule.ruleId }
        val currentBindings = repository.listConfigBindings(configId)
        val currentBindingsByRuleId = currentBindings.associateBy { binding -> binding.ruleId }
        val selectedEntries = draft.entries
            .filter { entry -> entry.ruleId in availableRuleIds }
            .sortedBy { entry -> entry.sortIndex }
            .mapIndexed { index, entry -> entry.copy(sortIndex = index) }
        val selectedRuleIds = selectedEntries.mapTo(mutableSetOf()) { entry -> entry.ruleId }

        currentBindings
            .filter { binding -> binding.ruleId !in selectedRuleIds || binding.ruleId !in availableRuleIds }
            .forEach { binding -> repository.deleteConfigBinding(configId, binding.ruleId) }

        selectedEntries.forEach { entry ->
            val existing = currentBindingsByRuleId[entry.ruleId]
            repository.upsertConfigBinding(
                ConfigGeofenceBinding(
                    configId = configId,
                    ruleId = entry.ruleId,
                    enabled = entry.enabled,
                    sortIndex = entry.sortIndex,
                    createdAt = existing?.createdAt ?: 0L,
                ),
            )
        }

        reconciliationPort.reconcileNow()
    }
}

fun buildConfigGeofenceBindingPresentation(
    configId: String,
    rules: List<GeofenceRule>,
    bindings: List<ConfigGeofenceBinding>,
): ConfigGeofenceBindingPresentation {
    val sortedRules = rules.sortedWith(compareBy<GeofenceRule> { it.name.lowercase() }.thenBy { it.ruleId })
    val rulesById = sortedRules.associateBy { rule -> rule.ruleId }
    val configBindings = bindings.filter { binding -> binding.configId == configId }
    val bindingsByRuleId = configBindings.associateBy { binding -> binding.ruleId }
    val staleBindingRuleIds = configBindings
        .map { binding -> binding.ruleId }
        .filterNot(rulesById::containsKey)
        .distinct()
        .sorted()
    val items = sortedRules.map { rule ->
        val binding = bindingsByRuleId[rule.ruleId]
        val selected = binding != null
        val bindingEnabled = binding?.enabled ?: true
        val summary = rule.summary(bindingEnabled)
        ConfigGeofenceBindingListItem(
            ruleId = rule.ruleId,
            name = rule.name.ifBlank { rule.ruleId },
            description = rule.description,
            selected = selected,
            bindingEnabled = bindingEnabled,
            ruleEnabled = rule.enabled,
            sortIndex = binding?.sortIndex ?: Int.MAX_VALUE,
            summary = summary,
        )
    }
    val selectedItems = items
        .filter { item -> item.selected }
        .sortedWith(compareBy<ConfigGeofenceBindingListItem> { it.sortIndex }.thenBy { it.name.lowercase() })

    return ConfigGeofenceBindingPresentation(
        configId = configId,
        allRuleCount = sortedRules.size,
        loadedCount = selectedItems.size,
        summaryItems = selectedItems.take(2).map { item -> item.summary },
        emptyRules = sortedRules.isEmpty(),
        showOpenRulesAction = sortedRules.isEmpty(),
        selectedRuleIds = selectedItems.map { item -> item.ruleId },
        staleBindingRuleIds = staleBindingRuleIds,
        items = items,
    )
}

private fun List<ConfigGeofenceBindingDraft.Entry>.reindexed(): List<ConfigGeofenceBindingDraft.Entry> =
    sortedBy { entry -> entry.sortIndex }.mapIndexed { index, entry -> entry.copy(sortIndex = index) }

private fun GeofenceRule.summary(bindingEnabled: Boolean): ConfigGeofenceBindingSummary {
    val firstRegion = regions.firstOrNull()
    val promptPreview = actionPrompt.trim().lineSequence().firstOrNull().orEmpty()
    return ConfigGeofenceBindingSummary(
        ruleName = name.ifBlank { ruleId },
        bindingEnabled = bindingEnabled,
        regionLabel = firstRegion?.displayLabel(),
        radiusMeters = firstRegion?.radiusMeters?.toInt(),
        additionalRegionCount = (regions.size - 1).coerceAtLeast(0),
        triggerEnter = triggerEnter,
        triggerExit = triggerExit,
        triggerDwell = triggerDwell,
        actionType = actionType,
        actionPromptPreview = promptPreview,
    )
}

private fun GeofenceRegion.displayLabel(): String? =
    addressLabel.ifBlank { label }.takeIf { value -> value.isNotBlank() }
