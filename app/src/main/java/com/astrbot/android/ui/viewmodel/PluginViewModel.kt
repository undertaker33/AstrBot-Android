package com.astrbot.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astrbot.android.R
import com.astrbot.android.data.PluginUninstallResult
import com.astrbot.android.di.DefaultPluginViewModelDependencies
import com.astrbot.android.di.PluginViewModelDependencies
import com.astrbot.android.model.plugin.PluginCompatibilityStatus
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginUninstallPolicy
import com.astrbot.android.model.plugin.isBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PluginSummaryMetrics(
    val totalInstalled: Int = 0,
    val highRisk: Int = 0,
    val incompatible: Int = 0,
)

data class PluginScreenUiState(
    val records: List<PluginInstallRecord> = emptyList(),
    val summaryMetrics: PluginSummaryMetrics = PluginSummaryMetrics(),
    val selectedPluginId: String? = null,
    val selectedPlugin: PluginInstallRecord? = null,
    val isShowingDetail: Boolean = false,
    val detailActionState: PluginDetailActionState = PluginDetailActionState(),
)

data class PluginDetailActionState(
    val compatibilityNotes: String = "",
    val enableBlockedReason: PluginActionFeedback? = null,
    val isEnableActionEnabled: Boolean = false,
    val isDisableActionEnabled: Boolean = false,
    val uninstallPolicy: PluginUninstallPolicy = PluginUninstallPolicy.default(),
    val lastActionMessage: PluginActionFeedback? = null,
)

sealed interface PluginActionFeedback {
    data class Resource(
        val resId: Int,
        val formatArgs: List<Any> = emptyList(),
    ) : PluginActionFeedback

    data class Text(val value: String) : PluginActionFeedback
}

class PluginViewModel(
    private val dependencies: PluginViewModelDependencies = DefaultPluginViewModelDependencies,
) : ViewModel() {
    private val selectedPluginId = MutableStateFlow<String?>(null)
    private val showingDetail = MutableStateFlow(false)
    private val lastActionMessage = MutableStateFlow<PluginActionFeedback?>(null)

    val uiState: StateFlow<PluginScreenUiState> = combine(
        dependencies.records,
        selectedPluginId,
        showingDetail,
        lastActionMessage,
    ) { records, selectedId, isShowingDetail, actionMessage ->
        val selectedPlugin = records.firstOrNull { it.pluginId == selectedId }
        PluginScreenUiState(
            records = records,
            summaryMetrics = buildSummaryMetrics(records),
            selectedPluginId = selectedPlugin?.pluginId,
            selectedPlugin = selectedPlugin,
            isShowingDetail = isShowingDetail && selectedPlugin != null,
            detailActionState = buildDetailActionState(selectedPlugin, actionMessage),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = PluginScreenUiState(),
    )

    init {
        viewModelScope.launch {
            dependencies.records.collect { records ->
                val resolvedSelection = resolveSelection(
                    records = records,
                    requestedPluginId = selectedPluginId.value,
                )
                if (selectedPluginId.value != resolvedSelection) {
                    selectedPluginId.value = resolvedSelection
                }
                if (resolvedSelection == null && showingDetail.value) {
                    showingDetail.value = false
                }
            }
        }
    }

    fun selectPlugin(pluginId: String) {
        val selected = resolveSelection(
            records = dependencies.records.value,
            requestedPluginId = pluginId,
        ) ?: return
        selectedPluginId.value = selected
        showingDetail.value = true
        lastActionMessage.value = null
    }

    fun showList() {
        showingDetail.value = false
    }

    fun enableSelectedPlugin() {
        updateSelectedPluginEnabled(enabled = true)
    }

    fun disableSelectedPlugin() {
        updateSelectedPluginEnabled(enabled = false)
    }

    fun updateSelectedUninstallPolicy(policy: PluginUninstallPolicy) {
        val selected = uiState.value.selectedPlugin ?: return
        runCatching {
            dependencies.updatePluginUninstallPolicy(selected.pluginId, policy)
        }.onSuccess {
            lastActionMessage.value = PluginActionFeedback.Resource(
                resId = policy.feedbackResId(),
            )
        }.onFailure { error ->
            lastActionMessage.value = error.message?.let(PluginActionFeedback::Text)
                ?: PluginActionFeedback.Resource(R.string.plugin_action_feedback_update_uninstall_policy_failed)
        }
    }

    fun uninstallSelectedPlugin() {
        val selected = uiState.value.selectedPlugin ?: return
        runCatching {
            dependencies.uninstallPlugin(selected.pluginId, selected.uninstallPolicy)
        }.onSuccess { result ->
            lastActionMessage.value = result.toUserMessage()
        }.onFailure { error ->
            lastActionMessage.value = error.message?.let(PluginActionFeedback::Text)
                ?: PluginActionFeedback.Resource(R.string.plugin_action_feedback_uninstall_failed)
        }
    }

    private fun resolveSelection(
        records: List<PluginInstallRecord>,
        requestedPluginId: String?,
    ): String? {
        if (records.isEmpty()) return null
        if (requestedPluginId != null && records.any { it.pluginId == requestedPluginId }) {
            return requestedPluginId
        }
        return records.first().pluginId
    }

    private fun buildSummaryMetrics(records: List<PluginInstallRecord>): PluginSummaryMetrics {
        return PluginSummaryMetrics(
            totalInstalled = records.size,
            highRisk = records.count { record ->
                record.manifestSnapshot.riskLevel.isBlocking() ||
                    record.permissionSnapshot.any { permission -> permission.riskLevel.isBlocking() }
            },
            incompatible = records.count { record ->
                record.compatibilityState.status == PluginCompatibilityStatus.INCOMPATIBLE
            },
        )
    }

    private fun buildDetailActionState(
        record: PluginInstallRecord?,
        actionMessage: PluginActionFeedback?,
    ): PluginDetailActionState {
        if (record == null) {
            return PluginDetailActionState(lastActionMessage = actionMessage)
        }
        val enableBlockedReason = if (!record.enabled &&
            record.compatibilityState.status == PluginCompatibilityStatus.INCOMPATIBLE
        ) {
            buildIncompatibleEnableMessage(record)
        } else {
            null
        }
        return PluginDetailActionState(
            compatibilityNotes = record.compatibilityState.notes,
            enableBlockedReason = enableBlockedReason,
            isEnableActionEnabled = !record.enabled && enableBlockedReason == null,
            isDisableActionEnabled = record.enabled,
            uninstallPolicy = record.uninstallPolicy,
            lastActionMessage = actionMessage,
        )
    }

    private fun updateSelectedPluginEnabled(enabled: Boolean) {
        val selected = uiState.value.selectedPlugin ?: return
        if (enabled && selected.compatibilityState.status == PluginCompatibilityStatus.INCOMPATIBLE) {
            lastActionMessage.value = buildIncompatibleEnableMessage(selected)
            return
        }
        runCatching {
            dependencies.setPluginEnabled(selected.pluginId, enabled)
        }.onSuccess {
            lastActionMessage.value = PluginActionFeedback.Resource(
                if (enabled) R.string.plugin_action_feedback_enabled else R.string.plugin_action_feedback_disabled,
            )
        }.onFailure { error ->
            lastActionMessage.value = error.message?.let(PluginActionFeedback::Text)
                ?: PluginActionFeedback.Resource(R.string.plugin_action_feedback_update_state_failed)
        }
    }

    private fun buildIncompatibleEnableMessage(record: PluginInstallRecord): PluginActionFeedback.Resource {
        val notes = record.compatibilityState.notes.takeIf { it.isNotBlank() }
        return if (notes != null) {
            PluginActionFeedback.Resource(
                resId = R.string.plugin_action_feedback_enable_blocked_incompatible_with_notes,
                formatArgs = listOf(notes),
            )
        } else {
            PluginActionFeedback.Resource(R.string.plugin_action_feedback_enable_blocked_incompatible)
        }
    }

    private fun PluginUninstallResult.toUserMessage(): PluginActionFeedback.Resource {
        return if (removedData) {
            PluginActionFeedback.Resource(R.string.plugin_action_feedback_uninstalled_remove_data)
        } else {
            PluginActionFeedback.Resource(R.string.plugin_action_feedback_uninstalled_keep_data)
        }
    }

    private fun PluginUninstallPolicy.feedbackResId(): Int {
        return when (this) {
            PluginUninstallPolicy.KEEP_DATA -> R.string.plugin_action_feedback_uninstall_policy_keep_data
            PluginUninstallPolicy.REMOVE_DATA -> R.string.plugin_action_feedback_uninstall_policy_remove_data
        }
    }
}
