package com.astrbot.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astrbot.android.R
import com.astrbot.android.data.PluginUninstallResult
import com.astrbot.android.di.DefaultPluginViewModelDependencies
import com.astrbot.android.di.PluginViewModelDependencies
import com.astrbot.android.model.chat.MessageSessionRef
import com.astrbot.android.model.chat.MessageType
import com.astrbot.android.model.plugin.CardResult
import com.astrbot.android.model.plugin.ErrorResult
import com.astrbot.android.model.plugin.NoOp
import com.astrbot.android.model.plugin.PluginBotSummary
import com.astrbot.android.model.plugin.PluginCardAction
import com.astrbot.android.model.plugin.PluginCardSchema
import com.astrbot.android.model.plugin.PluginCompatibilityStatus
import com.astrbot.android.model.plugin.PluginConfigSummary
import com.astrbot.android.model.plugin.PluginExecutionContext
import com.astrbot.android.model.plugin.PluginExecutionResult
import com.astrbot.android.model.plugin.PluginFailureState
import com.astrbot.android.model.plugin.PluginHostAction
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginMessageSummary
import com.astrbot.android.model.plugin.PluginPermissionGrant
import com.astrbot.android.model.plugin.PluginSettingsField
import com.astrbot.android.model.plugin.PluginSettingsSchema
import com.astrbot.android.model.plugin.PluginTriggerMetadata
import com.astrbot.android.model.plugin.PluginTriggerSource
import com.astrbot.android.model.plugin.PluginUninstallPolicy
import com.astrbot.android.model.plugin.SelectSettingField
import com.astrbot.android.model.plugin.SettingsUiRequest
import com.astrbot.android.model.plugin.TextInputSettingField
import com.astrbot.android.model.plugin.ToggleSettingField
import com.astrbot.android.model.plugin.isBlocking
import com.astrbot.android.runtime.plugin.PluginRuntimePlugin
import com.astrbot.android.runtime.plugin.PluginRuntimeRegistry
import java.text.DateFormat
import java.util.Date
import java.util.Locale
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
    val failureStatesByPluginId: Map<String, PluginFailureUiState> = emptyMap(),
    val summaryMetrics: PluginSummaryMetrics = PluginSummaryMetrics(),
    val selectedPluginId: String? = null,
    val selectedPlugin: PluginInstallRecord? = null,
    val isShowingDetail: Boolean = false,
    val detailActionState: PluginDetailActionState = PluginDetailActionState(),
    val schemaUiState: PluginSchemaUiState = PluginSchemaUiState.None,
)

data class PluginDetailActionState(
    val compatibilityNotes: String = "",
    val enableBlockedReason: PluginActionFeedback? = null,
    val isEnableActionEnabled: Boolean = false,
    val isDisableActionEnabled: Boolean = false,
    val uninstallPolicy: PluginUninstallPolicy = PluginUninstallPolicy.default(),
    val lastActionMessage: PluginActionFeedback? = null,
    val failureState: PluginFailureUiState? = null,
)

data class PluginFailureUiState(
    val consecutiveFailureCount: Int,
    val isSuspended: Boolean,
    val statusMessage: PluginActionFeedback,
    val summaryMessage: PluginActionFeedback,
    val recoveryMessage: PluginActionFeedback,
    val enableBlockedReason: PluginActionFeedback? = null,
)

sealed interface PluginActionFeedback {
    data class Resource(
        val resId: Int,
        val formatArgs: List<Any> = emptyList(),
    ) : PluginActionFeedback

    data class Text(val value: String) : PluginActionFeedback
}

sealed interface PluginSettingDraftValue {
    data class Toggle(val value: Boolean) : PluginSettingDraftValue

    data class Text(val value: String) : PluginSettingDraftValue
}

sealed interface PluginSchemaUiState {
    data object None : PluginSchemaUiState

    data class Card(
        val schema: PluginCardSchema,
        val lastActionFeedback: PluginActionFeedback? = null,
    ) : PluginSchemaUiState

    data class Settings(
        val schema: PluginSettingsSchema,
        val draftValues: Map<String, PluginSettingDraftValue> = emptyMap(),
    ) : PluginSchemaUiState
}

class PluginViewModel(
    private val dependencies: PluginViewModelDependencies = DefaultPluginViewModelDependencies,
) : ViewModel() {
    private val selectedPluginId = MutableStateFlow<String?>(null)
    private val showingDetail = MutableStateFlow(false)
    private val lastActionMessage = MutableStateFlow<PluginActionFeedback?>(null)
    private val schemaUiState = MutableStateFlow<PluginSchemaUiState>(PluginSchemaUiState.None)

    val uiState: StateFlow<PluginScreenUiState> = combine(
        dependencies.records,
        selectedPluginId,
        showingDetail,
        lastActionMessage,
        schemaUiState,
    ) { records, selectedId, isShowingDetail, actionMessage, schemaState ->
        val selectedPlugin = records.firstOrNull { it.pluginId == selectedId }
        val failureStates = buildFailureStates(records)
        PluginScreenUiState(
            records = records,
            failureStatesByPluginId = failureStates,
            summaryMetrics = buildSummaryMetrics(records),
            selectedPluginId = selectedPlugin?.pluginId,
            selectedPlugin = selectedPlugin,
            isShowingDetail = isShowingDetail && selectedPlugin != null,
            detailActionState = buildDetailActionState(selectedPlugin, actionMessage),
            schemaUiState = schemaState,
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
                    schemaUiState.value = PluginSchemaUiState.None
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
        executePluginEntry(pluginId = selected)
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

    fun onSchemaCardActionClick(
        actionId: String,
        payload: Map<String, String> = emptyMap(),
    ) {
        val current = schemaUiState.value as? PluginSchemaUiState.Card ?: return
        val action = current.schema.actions.firstOrNull { it.actionId == actionId }
        if (action == null) {
            schemaUiState.value = current.copy(
                lastActionFeedback = PluginActionFeedback.Text(
                    "Unsupported schema card action: $actionId",
                ),
            )
            return
        }
        schemaUiState.value = current.copy(
            lastActionFeedback = buildSchemaActionFeedback(action, payload),
        )
    }

    fun updateSettingsDraft(
        fieldId: String,
        draftValue: PluginSettingDraftValue,
    ) {
        val current = schemaUiState.value as? PluginSchemaUiState.Settings ?: return
        if (!current.schema.containsField(fieldId)) return
        schemaUiState.value = current.copy(
            draftValues = current.draftValues + (fieldId to draftValue),
        )
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
        val failureState = buildFailureUiState(record)
        val enableBlockedReason = buildEnableBlockedReason(record, failureState)
        return PluginDetailActionState(
            compatibilityNotes = record.compatibilityState.notes,
            enableBlockedReason = enableBlockedReason,
            isEnableActionEnabled = !record.enabled && enableBlockedReason == null,
            isDisableActionEnabled = record.enabled,
            uninstallPolicy = record.uninstallPolicy,
            lastActionMessage = actionMessage,
            failureState = failureState,
        )
    }

    private fun executePluginEntry(pluginId: String) {
        val selected = uiState.value.selectedPlugin ?: return
        val runtime = PluginRuntimeRegistry.plugins()
            .firstOrNull { plugin ->
                plugin.pluginId == pluginId &&
                    PluginTriggerSource.OnPluginEntryClick in plugin.supportedTriggers
            }
            ?: run {
                schemaUiState.value = PluginSchemaUiState.None
                return
            }
        val result = runCatching {
            runtime.handler.execute(
                buildEntryClickContext(
                    record = selected,
                    runtime = runtime,
                ),
            )
        }.getOrElse { throwable ->
            ErrorResult(message = throwable.message ?: "Plugin runtime failed")
        }
        schemaUiState.value = result.toSchemaUiState()
    }

    private fun buildEntryClickContext(
        record: PluginInstallRecord,
        runtime: PluginRuntimePlugin,
    ): PluginExecutionContext {
        return PluginExecutionContext(
            trigger = PluginTriggerSource.OnPluginEntryClick,
            pluginId = runtime.pluginId,
            pluginVersion = runtime.pluginVersion,
            sessionRef = MessageSessionRef(
                platformId = "host",
                messageType = MessageType.OtherMessage,
                originSessionId = record.pluginId,
            ),
            message = PluginMessageSummary(
                messageId = "plugin-entry-${record.pluginId}",
                contentPreview = "",
                messageType = "entry_click",
            ),
            bot = PluginBotSummary(
                botId = "host",
                displayName = "AstrBot Host",
                platformId = "host",
            ),
            config = PluginConfigSummary(),
            permissionSnapshot = record.permissionSnapshot.map { permission ->
                PluginPermissionGrant(
                    permissionId = permission.permissionId,
                    title = permission.title,
                    granted = true,
                    required = permission.required,
                    riskLevel = permission.riskLevel,
                )
            },
            hostActionWhitelist = PluginHostAction.entries.toList(),
            triggerMetadata = PluginTriggerMetadata(
                entryPoint = "plugin_detail",
            ),
        )
    }

    private fun PluginExecutionResult.toSchemaUiState(): PluginSchemaUiState {
        return when (this) {
            is CardResult -> PluginSchemaUiState.Card(schema = card)
            is SettingsUiRequest -> PluginSchemaUiState.Settings(
                schema = schema,
                draftValues = schema.defaultDraftValues(),
            )
            is NoOp -> PluginSchemaUiState.None
            is ErrorResult -> PluginSchemaUiState.None
            else -> PluginSchemaUiState.None
        }
    }

    private fun PluginSettingsSchema.defaultDraftValues(): Map<String, PluginSettingDraftValue> {
        val draftValues = linkedMapOf<String, PluginSettingDraftValue>()
        sections.forEach { section ->
            section.fields.forEach { field ->
                when (field) {
                    is ToggleSettingField -> {
                        draftValues[field.fieldId] = PluginSettingDraftValue.Toggle(field.defaultValue)
                    }
                    is TextInputSettingField -> {
                        draftValues[field.fieldId] = PluginSettingDraftValue.Text(field.defaultValue)
                    }
                    is SelectSettingField -> {
                        draftValues[field.fieldId] = PluginSettingDraftValue.Text(field.defaultValue)
                    }
                }
            }
        }
        return draftValues
    }

    private fun PluginSettingsSchema.containsField(fieldId: String): Boolean {
        return sections.any { section ->
            section.fields.any { field: PluginSettingsField ->
                field.fieldId == fieldId
            }
        }
    }

    private fun buildSchemaActionFeedback(
        action: PluginCardAction,
        payload: Map<String, String>,
    ): PluginActionFeedback {
        val resolvedPayload = if (payload.isEmpty()) action.payload else payload
        if (resolvedPayload.isEmpty()) {
            return PluginActionFeedback.Text(action.label)
        }
        val payloadText = resolvedPayload.entries
            .sortedBy { (key, _) -> key }
            .joinToString(separator = ", ") { (key, value) -> "$key=$value" }
        return PluginActionFeedback.Text("${action.label} · $payloadText")
    }

    private fun updateSelectedPluginEnabled(enabled: Boolean) {
        val selected = uiState.value.selectedPlugin ?: return
        val failureState = buildFailureUiState(selected)
        val blockedReason = buildEnableBlockedReason(selected, failureState)
        if (enabled && blockedReason != null) {
            lastActionMessage.value = blockedReason
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

    private fun buildFailureStates(records: List<PluginInstallRecord>): Map<String, PluginFailureUiState> {
        return records.mapNotNull { record ->
            buildFailureUiState(record)?.let { record.pluginId to it }
        }.toMap()
    }

    private fun buildFailureUiState(record: PluginInstallRecord): PluginFailureUiState? {
        val failureState = record.failureState
        if (!failureState.hasFailures) return null
        val isSuspended = failureState.isSuspended()
        val summary = failureState.lastErrorSummary.takeIf { it.isNotBlank() }
            ?: record.manifestSnapshot.title
        val summaryMessage = PluginActionFeedback.Resource(
            resId = R.string.plugin_failure_summary_with_error,
            formatArgs = listOf(summary),
        )
        val recoveryMessage = if (isSuspended) {
            PluginActionFeedback.Resource(
                resId = R.string.plugin_failure_recovery_at,
                formatArgs = listOf(formatRecoveryTime(failureState.suspendedUntilEpochMillis!!)),
            )
        } else {
            PluginActionFeedback.Resource(
                resId = R.string.plugin_failure_recovery_available,
            )
        }
        val statusMessage = if (isSuspended) {
            PluginActionFeedback.Resource(R.string.plugin_failure_status_suspended)
        } else {
            PluginActionFeedback.Resource(R.string.plugin_failure_status_active)
        }
        val blockedReason = if (isSuspended) {
            PluginActionFeedback.Resource(R.string.plugin_failure_enable_blocked_until_recovery)
        } else {
            null
        }
        return PluginFailureUiState(
            consecutiveFailureCount = failureState.consecutiveFailureCount,
            isSuspended = isSuspended,
            statusMessage = statusMessage,
            summaryMessage = summaryMessage,
            recoveryMessage = recoveryMessage,
            enableBlockedReason = blockedReason,
        )
    }

    private fun buildEnableBlockedReason(
        record: PluginInstallRecord,
        failureState: PluginFailureUiState?,
    ): PluginActionFeedback? {
        if (failureState?.enableBlockedReason != null) {
            return failureState.enableBlockedReason
        }
        if (!record.enabled &&
            record.compatibilityState.status == PluginCompatibilityStatus.INCOMPATIBLE
        ) {
            return buildIncompatibleEnableMessage(record)
        }
        return null
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

    private fun PluginFailureState.isSuspended(): Boolean {
        val suspendedUntil = suspendedUntilEpochMillis ?: return false
        return suspendedUntil > System.currentTimeMillis()
    }

    private fun formatRecoveryTime(epochMillis: Long): String {
        val formatter = DateFormat.getDateTimeInstance(
            DateFormat.MEDIUM,
            DateFormat.SHORT,
            Locale.getDefault(),
        )
        return formatter.format(Date(epochMillis))
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
