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
import com.astrbot.android.model.plugin.PluginCatalogEntryRecord
import com.astrbot.android.model.plugin.PluginCatalogVersion
import com.astrbot.android.model.plugin.PluginCompatibilityStatus
import com.astrbot.android.model.plugin.PluginConfigSummary
import com.astrbot.android.model.plugin.PluginExecutionContext
import com.astrbot.android.model.plugin.PluginExecutionResult
import com.astrbot.android.model.plugin.PluginFailureState
import com.astrbot.android.model.plugin.PluginHostAction
import com.astrbot.android.model.plugin.PluginInstallIntent
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginInstallIntentResult
import com.astrbot.android.model.plugin.PluginMessageSummary
import com.astrbot.android.model.plugin.PluginPermissionGrant
import com.astrbot.android.model.plugin.PluginConfigStorageBoundary
import com.astrbot.android.model.plugin.PluginSettingsField
import com.astrbot.android.model.plugin.PluginSettingsSchema
import com.astrbot.android.model.plugin.PluginStaticConfigField
import com.astrbot.android.model.plugin.PluginStaticConfigFieldType
import com.astrbot.android.model.plugin.PluginStaticConfigSchema
import com.astrbot.android.model.plugin.PluginStaticConfigValue
import com.astrbot.android.model.plugin.PluginUpdateAvailability
import com.astrbot.android.model.plugin.PluginTriggerMetadata
import com.astrbot.android.model.plugin.PluginTriggerSource
import com.astrbot.android.model.plugin.PluginUninstallPolicy
import com.astrbot.android.model.plugin.SelectSettingField
import com.astrbot.android.model.plugin.SettingsUiRequest
import com.astrbot.android.model.plugin.TextInputSettingField
import com.astrbot.android.model.plugin.ToggleSettingField
import com.astrbot.android.runtime.plugin.PluginRuntimePlugin
import com.astrbot.android.runtime.plugin.PluginRuntimeRegistry
import com.astrbot.android.runtime.plugin.compareVersions
import com.astrbot.android.ui.screen.PluginLocalFilter
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

data class PluginRepositorySourceCardUiState(
    val sourceId: String,
    val title: String,
    val catalogUrl: String,
    val pluginCount: Int,
    val lastSyncAtEpochMillis: Long? = null,
    val lastSyncStatusText: String = "",
)

data class PluginCatalogEntryCardUiState(
    val sourceId: String,
    val pluginId: String,
    val title: String,
    val author: String,
    val summary: String,
    val latestVersion: String = "",
    val sourceName: String = "",
)

data class PluginSourceBadgeUiState(
    val sourceType: com.astrbot.android.model.plugin.PluginSourceType,
    val label: String,
)

data class PluginUpdateHintUiState(
    val latestVersion: String,
    val changelogSummary: String = "",
    val blockedReason: String = "",
)

data class PluginPermissionDiffHintUiState(
    val addedPermissions: List<String> = emptyList(),
    val removedPermissions: List<String> = emptyList(),
    val changedPermissions: List<String> = emptyList(),
    val upgradedPermissions: List<String> = emptyList(),
)

data class PluginDetailMetadataState(
    val sourceBadge: PluginSourceBadgeUiState? = null,
    val repositoryNameOrHost: String = "",
    val repositoryHost: String = "",
    val lastSyncAtEpochMillis: Long? = null,
    val lastUpdatedAtEpochMillis: Long? = null,
    val versionHistorySummary: String = "",
    val changelogSummary: String = "",
    val updateHint: PluginUpdateHintUiState? = null,
    val permissionDiffHint: PluginPermissionDiffHintUiState? = null,
)

private data class PluginWorkspaceSnapshot(
    val records: List<PluginInstallRecord>,
    val repositorySources: List<com.astrbot.android.model.plugin.PluginRepositorySource>,
    val catalogEntries: List<PluginCatalogEntryRecord>,
    val selectedId: String?,
    val isShowingDetail: Boolean,
)

data class PluginScreenUiState(
    val records: List<PluginInstallRecord> = emptyList(),
    val repositorySources: List<PluginRepositorySourceCardUiState> = emptyList(),
    val catalogEntries: List<PluginCatalogEntryCardUiState> = emptyList(),
    val updateAvailabilitiesByPluginId: Map<String, PluginUpdateAvailability> = emptyMap(),
    val failureStatesByPluginId: Map<String, PluginFailureUiState> = emptyMap(),
    val localSearchQuery: String = "",
    val selectedLocalFilter: PluginLocalFilter = PluginLocalFilter.ENABLED,
    val repositoryUrlDraft: String = "",
    val directPackageUrlDraft: String = "",
    val isInstallActionRunning: Boolean = false,
    val selectedPluginId: String? = null,
    val selectedPlugin: PluginInstallRecord? = null,
    val isShowingDetail: Boolean = false,
    val detailMetadataState: PluginDetailMetadataState = PluginDetailMetadataState(),
    val detailActionState: PluginDetailActionState = PluginDetailActionState(),
    val upgradeDialogState: PluginUpgradeDialogState? = null,
    val staticConfigUiState: PluginStaticConfigUiState? = null,
    val schemaUiState: PluginSchemaUiState = PluginSchemaUiState.None,
)

data class PluginDetailActionState(
    val compatibilityNotes: String = "",
    val enableBlockedReason: PluginActionFeedback? = null,
    val updateBlockedReason: PluginActionFeedback? = null,
    val isEnableActionEnabled: Boolean = false,
    val isDisableActionEnabled: Boolean = false,
    val updateAvailability: PluginUpdateAvailability? = null,
    val isUpgradeActionEnabled: Boolean = false,
    val uninstallPolicy: PluginUninstallPolicy = PluginUninstallPolicy.default(),
    val lastActionMessage: PluginActionFeedback? = null,
    val failureState: PluginFailureUiState? = null,
)

data class PluginUpgradeDialogState(
    val availability: PluginUpdateAvailability,
    val isVisible: Boolean = true,
    val isSecondaryConfirmationStep: Boolean = false,
    val isInstalling: Boolean = false,
    val message: PluginActionFeedback? = null,
) {
    val requiresSecondaryConfirmation: Boolean
        get() = availability.permissionDiff.requiresSecondaryConfirmation
}

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

data class PluginStaticConfigUiState(
    val schema: PluginStaticConfigSchema,
    val draftValues: Map<String, PluginSettingDraftValue> = emptyMap(),
)

class PluginViewModel(
    private val dependencies: PluginViewModelDependencies = DefaultPluginViewModelDependencies,
) : ViewModel() {
    private val selectedPluginId = MutableStateFlow<String?>(null)
    private val showingDetail = MutableStateFlow(false)
    private val lastActionMessage = MutableStateFlow<PluginActionFeedback?>(null)
    private val schemaUiState = MutableStateFlow<PluginSchemaUiState>(PluginSchemaUiState.None)
    private val staticConfigUiState = MutableStateFlow<PluginStaticConfigUiState?>(null)
    private val repositoryUrlDraft = MutableStateFlow("")
    private val directPackageUrlDraft = MutableStateFlow("")
    private val installActionRunning = MutableStateFlow(false)
    private val upgradeDialogState = MutableStateFlow<PluginUpgradeDialogState?>(null)
    private val localSearchQuery = MutableStateFlow("")
    private val selectedLocalFilter = MutableStateFlow(PluginLocalFilter.ENABLED)
    private var currentConfigBoundary: PluginConfigStorageBoundary? = null

    val uiState: StateFlow<PluginScreenUiState> = combine(
        combine(
            dependencies.records,
            dependencies.repositorySources,
            dependencies.catalogEntries,
            selectedPluginId,
            showingDetail,
        ) { records, repositorySources, catalogEntries, selectedId, isShowingDetail ->
            PluginWorkspaceSnapshot(
                records = records,
                repositorySources = repositorySources,
                catalogEntries = catalogEntries,
                selectedId = selectedId,
                isShowingDetail = isShowingDetail,
            )
        },
        lastActionMessage,
        schemaUiState,
        staticConfigUiState,
    ) { snapshot, actionMessage, schemaState, staticSchemaState ->
        val selectedPlugin = snapshot.records.firstOrNull { it.pluginId == snapshot.selectedId }
        val updateAvailabilities = snapshot.records.mapNotNull { record ->
            dependencies.getUpdateAvailability(record.pluginId)?.let { record.pluginId to it }
        }.toMap()
        val failureStates = buildFailureStates(snapshot.records)
        PluginScreenUiState(
            records = snapshot.records,
            repositorySources = snapshot.repositorySources.map(::toRepositorySourceCardUiState),
            catalogEntries = snapshot.catalogEntries.map(::toCatalogEntryCardUiState),
            updateAvailabilitiesByPluginId = updateAvailabilities,
            failureStatesByPluginId = failureStates,
            selectedPluginId = selectedPlugin?.pluginId,
            selectedPlugin = selectedPlugin,
            isShowingDetail = snapshot.isShowingDetail && selectedPlugin != null,
            detailMetadataState = buildDetailMetadataState(
                record = selectedPlugin,
                repositorySources = snapshot.repositorySources,
                catalogEntries = snapshot.catalogEntries,
                updateAvailability = selectedPlugin?.let { updateAvailabilities[it.pluginId] },
            ),
            detailActionState = buildDetailActionState(
                record = selectedPlugin,
                actionMessage = actionMessage,
                updateAvailability = selectedPlugin?.let { updateAvailabilities[it.pluginId] },
            ),
            staticConfigUiState = staticSchemaState,
            schemaUiState = schemaState,
        )
    }.combine(upgradeDialogState) { state, upgradeDialog ->
        state.copy(upgradeDialogState = upgradeDialog)
    }.combine(localSearchQuery) { state, searchQuery ->
        state.copy(localSearchQuery = searchQuery)
    }.combine(selectedLocalFilter) { state, filter ->
        state.copy(selectedLocalFilter = filter)
    }.combine(repositoryUrlDraft) { state, repositoryDraft ->
        state.copy(repositoryUrlDraft = repositoryDraft)
    }.combine(directPackageUrlDraft) { state, directDraft ->
        state.copy(directPackageUrlDraft = directDraft)
    }.combine(installActionRunning) { state, isInstallRunning ->
        state.copy(isInstallActionRunning = isInstallRunning)
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
                    staticConfigUiState.value = null
                    currentConfigBoundary = null
                }
                if (resolvedSelection == null && showingDetail.value) {
                    showingDetail.value = false
                }
            }
        }
    }

    fun selectPlugin(pluginId: String) {
        selectPluginForConfig(pluginId)
    }

    fun selectPluginForDetail(pluginId: String) {
        val selected = resolveSelection(
            records = dependencies.records.value,
            requestedPluginId = pluginId,
        ) ?: return
        selectedPluginId.value = selected
        showingDetail.value = true
        lastActionMessage.value = null
        schemaUiState.value = PluginSchemaUiState.None
        staticConfigUiState.value = null
        currentConfigBoundary = null
    }

    fun selectPluginForConfig(pluginId: String) {
        val selected = resolveSelection(
            records = dependencies.records.value,
            requestedPluginId = pluginId,
        ) ?: return
        selectedPluginId.value = selected
        showingDetail.value = true
        lastActionMessage.value = null
        val runtimeSchemaState = executePluginEntry(pluginId = selected)
        loadConfigState(
            pluginId = selected,
            runtimeSchemaState = runtimeSchemaState,
        )
    }

    fun updateRepositoryUrlDraft(value: String) {
        repositoryUrlDraft.value = value
    }

    fun updateLocalSearchQuery(value: String) {
        localSearchQuery.value = value
    }

    fun updateSelectedLocalFilter(filter: PluginLocalFilter) {
        selectedLocalFilter.value = filter
    }

    fun updateDirectPackageUrlDraft(value: String) {
        directPackageUrlDraft.value = value
    }

    fun submitRepositoryUrl() {
        submitInstallIntent(
            buildIntent = { PluginInstallIntent.repositoryUrl(repositoryUrlDraft.value) },
            onSuccess = {
                repositoryUrlDraft.value = ""
            },
        )
    }

    fun submitDirectPackageUrl() {
        submitInstallIntent(
            buildIntent = { PluginInstallIntent.directPackageUrl(directPackageUrlDraft.value) },
            onSuccess = {
                directPackageUrlDraft.value = ""
            },
        )
    }

    fun submitLocalPackageUri(uri: String) {
        if (installActionRunning.value) return
        viewModelScope.launch {
            installActionRunning.value = true
            runCatching {
                dependencies.installFromLocalPackageUri(uri)
            }.onSuccess { result ->
                lastActionMessage.value = result.toFeedback()
            }.onFailure { error ->
                lastActionMessage.value = PluginActionFeedback.Text(
                    error.message ?: "Plugin local import failed.",
                )
            }
            installActionRunning.value = false
        }
    }

    fun showList() {
        showingDetail.value = false
    }

    fun requestUpgradeForSelectedPlugin() {
        val selected = uiState.value.selectedPlugin ?: return
        val availability = uiState.value.updateAvailabilitiesByPluginId[selected.pluginId]
        if (availability == null || !availability.updateAvailable) {
            lastActionMessage.value = PluginActionFeedback.Text("No update is available for this plugin.")
            return
        }
        if (!availability.canUpgrade) {
            lastActionMessage.value = PluginActionFeedback.Text(
                availability.incompatibilityReason.ifBlank {
                    "This update cannot be installed on the current host."
                },
            )
            return
        }
        upgradeDialogState.value = PluginUpgradeDialogState(availability = availability)
    }

    fun dismissUpgradeDialog() {
        upgradeDialogState.value = null
    }

    fun confirmUpgrade() {
        val dialog = upgradeDialogState.value ?: return
        if (dialog.isInstalling) return
        if (dialog.requiresSecondaryConfirmation && !dialog.isSecondaryConfirmationStep) {
            upgradeDialogState.value = dialog.copy(
                isSecondaryConfirmationStep = true,
            )
            return
        }
        viewModelScope.launch {
            upgradeDialogState.value = dialog.copy(isInstalling = true)
            runCatching {
                dependencies.upgradePlugin(dialog.availability)
            }.onSuccess { record ->
                lastActionMessage.value = PluginActionFeedback.Text(
                    "Updated ${record.manifestSnapshot.title} to ${record.installedVersion}.",
                )
                upgradeDialogState.value = null
            }.onFailure { error ->
                upgradeDialogState.value = dialog.copy(
                    isInstalling = false,
                    message = PluginActionFeedback.Text(
                        error.message ?: "Plugin upgrade failed.",
                    ),
                )
            }
        }
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
        val updated = current.copy(
            draftValues = current.draftValues + (fieldId to draftValue),
        )
        schemaUiState.value = updated
        persistRuntimeConfig(updated)
    }

    fun updateStaticConfigDraft(
        fieldKey: String,
        draftValue: PluginSettingDraftValue,
    ) {
        val current = staticConfigUiState.value ?: return
        if (current.schema.fields.none { field -> field.fieldKey == fieldKey }) return
        val updated = current.copy(
            draftValues = current.draftValues + (fieldKey to draftValue),
        )
        staticConfigUiState.value = updated
        persistStaticConfig(updated)
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

    private fun submitInstallIntent(
        buildIntent: () -> PluginInstallIntent,
        onSuccess: () -> Unit,
    ) {
        val intent = runCatching(buildIntent)
            .onFailure { error ->
                lastActionMessage.value = PluginActionFeedback.Text(
                    error.message ?: "Invalid plugin install URL.",
                )
            }
            .getOrNull() ?: return
        if (installActionRunning.value) return
        viewModelScope.launch {
            installActionRunning.value = true
            runCatching {
                dependencies.handleInstallIntent(intent)
            }.onSuccess { result ->
                onSuccess()
                lastActionMessage.value = result.toFeedback()
            }.onFailure { error ->
                lastActionMessage.value = PluginActionFeedback.Text(
                    error.message ?: "Plugin install action failed.",
                )
            }
            installActionRunning.value = false
        }
    }

    private fun buildDetailMetadataState(
        record: PluginInstallRecord?,
        repositorySources: List<com.astrbot.android.model.plugin.PluginRepositorySource>,
        catalogEntries: List<PluginCatalogEntryRecord>,
        updateAvailability: PluginUpdateAvailability?,
    ): PluginDetailMetadataState {
        if (record == null) return PluginDetailMetadataState()
        val catalogEntry = record.catalogSourceId?.let { sourceId ->
            catalogEntries.firstOrNull { entry ->
                entry.sourceId == sourceId && entry.entry.pluginId == record.pluginId
            }
        }
        val repositorySource = record.catalogSourceId?.let { sourceId ->
            repositorySources.firstOrNull { source -> source.sourceId == sourceId }
        }
        val repositoryUrl = repositorySource?.catalogUrl
            ?: record.installedPackageUrl.takeIf { it.isNotBlank() }
            ?: record.source.location
        val repositoryHost = repositoryUrl.toUriHost()
        val repositoryNameOrHost = repositorySource?.title
            ?.takeIf { it.isNotBlank() }
            ?: repositoryHost
        val permissionDiffHint = updateAvailability
            ?.takeIf { it.updateAvailable }
            ?.permissionDiff
            ?.let(::toPermissionDiffHintUiState)
            ?.takeIf { hint ->
                hint.addedPermissions.isNotEmpty() ||
                    hint.removedPermissions.isNotEmpty() ||
                    hint.changedPermissions.isNotEmpty() ||
                    hint.upgradedPermissions.isNotEmpty()
            }
        return PluginDetailMetadataState(
            sourceBadge = buildSourceBadge(record),
            repositoryNameOrHost = repositoryNameOrHost,
            repositoryHost = repositoryHost,
            lastSyncAtEpochMillis = repositorySource?.lastSyncAtEpochMillis,
            lastUpdatedAtEpochMillis = record.lastUpdatedAt.takeIf { it > 0L },
            versionHistorySummary = summarizeVersionHistory(catalogEntry?.entry?.versions.orEmpty()),
            changelogSummary = resolveChangelogSummary(record, catalogEntry, updateAvailability),
            updateHint = updateAvailability?.takeIf { it.updateAvailable }?.let(::toUpdateHintUiState),
            permissionDiffHint = permissionDiffHint,
        )
    }

    private fun buildDetailActionState(
        record: PluginInstallRecord?,
        actionMessage: PluginActionFeedback?,
        updateAvailability: PluginUpdateAvailability?,
    ): PluginDetailActionState {
        if (record == null) {
            return PluginDetailActionState(lastActionMessage = actionMessage)
        }
        val failureState = buildFailureUiState(record)
        val enableBlockedReason = buildEnableBlockedReason(record, failureState)
        return PluginDetailActionState(
            compatibilityNotes = record.compatibilityState.notes,
            enableBlockedReason = enableBlockedReason,
            updateBlockedReason = updateAvailability
                ?.takeIf { it.updateAvailable && !it.canUpgrade }
                ?.incompatibilityReason
                ?.takeIf { it.isNotBlank() }
                ?.let(PluginActionFeedback::Text),
            isEnableActionEnabled = !record.enabled && enableBlockedReason == null,
            isDisableActionEnabled = record.enabled,
            updateAvailability = updateAvailability,
            isUpgradeActionEnabled = updateAvailability?.updateAvailable == true && updateAvailability.canUpgrade,
            uninstallPolicy = record.uninstallPolicy,
            lastActionMessage = actionMessage,
            failureState = failureState,
        )
    }

    internal fun buildSourceBadgeForTest(record: PluginInstallRecord): PluginSourceBadgeUiState {
        return buildSourceBadge(record)
    }

    private fun executePluginEntry(pluginId: String): PluginSchemaUiState {
        val selected = uiState.value.selectedPlugin ?: return PluginSchemaUiState.None
        val runtime = PluginRuntimeRegistry.plugins()
            .firstOrNull { plugin ->
                plugin.pluginId == pluginId &&
                    PluginTriggerSource.OnPluginEntryClick in plugin.supportedTriggers
            }
            ?: return PluginSchemaUiState.None
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
        return result.toSchemaUiState()
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

    private fun loadConfigState(
        pluginId: String,
        runtimeSchemaState: PluginSchemaUiState,
    ) {
        val staticSchema = dependencies.getPluginStaticConfigSchema(pluginId)
        val boundary = buildConfigStorageBoundary(
            staticSchema = staticSchema,
            runtimeSchemaState = runtimeSchemaState,
        )
        currentConfigBoundary = boundary
        val snapshot = dependencies.resolvePluginConfigSnapshot(
            pluginId = pluginId,
            boundary = boundary,
        )
        staticConfigUiState.value = staticSchema?.toUiState(snapshot.coreValues)
        schemaUiState.value = runtimeSchemaState.withPersistedExtensionValues(snapshot.extensionValues)
    }

    private fun buildConfigStorageBoundary(
        staticSchema: PluginStaticConfigSchema?,
        runtimeSchemaState: PluginSchemaUiState,
    ): PluginConfigStorageBoundary {
        val extensionFieldKeys = (runtimeSchemaState as? PluginSchemaUiState.Settings)
            ?.schema
            ?.allFieldIds()
            .orEmpty()
        return if (staticSchema != null) {
            PluginConfigStorageBoundary(
                coreFieldKeys = staticSchema.fields.map(PluginStaticConfigField::fieldKey).toSet(),
                extensionFieldKeys = extensionFieldKeys,
                coreDefaults = staticSchema.fields.mapNotNull { field ->
                    field.defaultValue?.let { defaultValue -> field.fieldKey to defaultValue }
                }.toMap(),
            )
        } else {
            PluginConfigStorageBoundary(
                coreFieldKeys = emptySet(),
                extensionFieldKeys = extensionFieldKeys,
            )
        }
    }

    private fun persistStaticConfig(state: PluginStaticConfigUiState) {
        val pluginId = uiState.value.selectedPluginId ?: return
        val boundary = currentConfigBoundary ?: return
        val coreValues = state.schema.fields.mapNotNull { field ->
            state.draftValues[field.fieldKey]
                ?.toStaticConfigValue(field.fieldType)
                ?.let { value -> field.fieldKey to value }
        }.toMap()
        dependencies.savePluginCoreConfig(
            pluginId = pluginId,
            boundary = boundary,
            coreValues = coreValues,
        )
    }

    private fun persistRuntimeConfig(state: PluginSchemaUiState.Settings) {
        val pluginId = uiState.value.selectedPluginId ?: return
        val boundary = currentConfigBoundary ?: return
        val extensionValues = state.schema.sections
            .flatMap { section -> section.fields }
            .mapNotNull { field ->
                val value = state.draftValues[field.fieldId]?.toExtensionConfigValue(field) ?: return@mapNotNull null
                field.fieldId to value
            }
            .toMap()
        dependencies.savePluginExtensionConfig(
            pluginId = pluginId,
            boundary = boundary,
            extensionValues = extensionValues,
        )
    }

    private fun PluginStaticConfigSchema.toUiState(
        persistedCoreValues: Map<String, PluginStaticConfigValue>,
    ): PluginStaticConfigUiState {
        val draftValues = fields.mapNotNull { field ->
            val persistedValue = persistedCoreValues[field.fieldKey] ?: field.defaultValue
            persistedValue?.toDraftValue(field.fieldType)?.let { draftValue ->
                field.fieldKey to draftValue
            }
        }.toMap()
        return PluginStaticConfigUiState(
            schema = this,
            draftValues = draftValues,
        )
    }

    private fun PluginSchemaUiState.withPersistedExtensionValues(
        extensionValues: Map<String, PluginStaticConfigValue>,
    ): PluginSchemaUiState {
        val settingsState = this as? PluginSchemaUiState.Settings ?: return this
        return settingsState.copy(
            draftValues = settingsState.schema.sections
                .flatMap { section -> section.fields }
                .fold(settingsState.draftValues) { drafts, field ->
                    val persistedValue = extensionValues[field.fieldId] ?: return@fold drafts
                    val persistedDraft = persistedValue.toDraftValue(field) ?: return@fold drafts
                    drafts + (field.fieldId to persistedDraft)
                },
        )
    }

    private fun PluginSettingsSchema.allFieldIds(): Set<String> {
        return sections
            .flatMap { section -> section.fields }
            .map(PluginSettingsField::fieldId)
            .toSet()
    }

    private fun PluginStaticConfigValue.toDraftValue(
        fieldType: PluginStaticConfigFieldType,
    ): PluginSettingDraftValue? {
        return when (fieldType) {
            PluginStaticConfigFieldType.BoolField -> {
                (this as? PluginStaticConfigValue.BoolValue)?.let { PluginSettingDraftValue.Toggle(it.value) }
            }

            PluginStaticConfigFieldType.StringField,
            PluginStaticConfigFieldType.TextField,
            PluginStaticConfigFieldType.IntField,
            PluginStaticConfigFieldType.FloatField,
            -> PluginSettingDraftValue.Text(asStringValue())
        }
    }

    private fun PluginStaticConfigValue.toDraftValue(
        field: PluginSettingsField,
    ): PluginSettingDraftValue? {
        return when (field) {
            is ToggleSettingField -> (this as? PluginStaticConfigValue.BoolValue)
                ?.let { PluginSettingDraftValue.Toggle(it.value) }

            is TextInputSettingField,
            is SelectSettingField,
            -> PluginSettingDraftValue.Text(asStringValue())
        }
    }

    private fun PluginSettingDraftValue.toStaticConfigValue(
        fieldType: PluginStaticConfigFieldType,
    ): PluginStaticConfigValue? {
        return when (fieldType) {
            PluginStaticConfigFieldType.BoolField -> {
                (this as? PluginSettingDraftValue.Toggle)?.let { PluginStaticConfigValue.BoolValue(it.value) }
            }

            PluginStaticConfigFieldType.StringField,
            PluginStaticConfigFieldType.TextField,
            -> (this as? PluginSettingDraftValue.Text)?.let { PluginStaticConfigValue.StringValue(it.value) }

            PluginStaticConfigFieldType.IntField -> {
                (this as? PluginSettingDraftValue.Text)?.value?.toIntOrNull()?.let(PluginStaticConfigValue::IntValue)
            }

            PluginStaticConfigFieldType.FloatField -> {
                (this as? PluginSettingDraftValue.Text)?.value?.toDoubleOrNull()?.let(PluginStaticConfigValue::FloatValue)
            }
        }
    }

    private fun PluginSettingDraftValue.toExtensionConfigValue(
        field: PluginSettingsField,
    ): PluginStaticConfigValue? {
        return when (field) {
            is ToggleSettingField -> (this as? PluginSettingDraftValue.Toggle)
                ?.let { PluginStaticConfigValue.BoolValue(it.value) }

            is TextInputSettingField,
            is SelectSettingField,
            -> (this as? PluginSettingDraftValue.Text)?.let { PluginStaticConfigValue.StringValue(it.value) }
        }
    }

    private fun PluginStaticConfigValue.asStringValue(): String {
        return when (this) {
            is PluginStaticConfigValue.StringValue -> value
            is PluginStaticConfigValue.IntValue -> value.toString()
            is PluginStaticConfigValue.FloatValue -> value.toString()
            is PluginStaticConfigValue.BoolValue -> value.toString()
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

    private fun toRepositorySourceCardUiState(
        source: com.astrbot.android.model.plugin.PluginRepositorySource,
    ): PluginRepositorySourceCardUiState {
        return PluginRepositorySourceCardUiState(
            sourceId = source.sourceId,
            title = source.title,
            catalogUrl = source.catalogUrl,
            pluginCount = source.plugins.size,
            lastSyncAtEpochMillis = source.lastSyncAtEpochMillis,
            lastSyncStatusText = source.lastSyncStatus.name,
        )
    }

    private fun toCatalogEntryCardUiState(record: PluginCatalogEntryRecord): PluginCatalogEntryCardUiState {
        val latestVersion = record.entry.versions
            .sortedWith { left, right -> compareVersions(right.version, left.version) }
            .firstOrNull()
            ?.version
            .orEmpty()
        return PluginCatalogEntryCardUiState(
            sourceId = record.sourceId,
            pluginId = record.entry.pluginId,
            title = record.entry.title,
            author = record.entry.author,
            summary = record.entry.entrySummary.ifBlank { record.entry.description },
            latestVersion = latestVersion,
            sourceName = record.sourceTitle,
        )
    }

    private fun buildSourceBadge(record: PluginInstallRecord): PluginSourceBadgeUiState {
        val label = when (record.source.sourceType) {
            com.astrbot.android.model.plugin.PluginSourceType.LOCAL_FILE -> "Local file"
            com.astrbot.android.model.plugin.PluginSourceType.MANUAL_IMPORT -> "Manual import"
            com.astrbot.android.model.plugin.PluginSourceType.REPOSITORY -> "Repository"
            com.astrbot.android.model.plugin.PluginSourceType.DIRECT_LINK -> "Direct link"
        }
        return PluginSourceBadgeUiState(
            sourceType = record.source.sourceType,
            label = label,
        )
    }

    private fun summarizeVersionHistory(versions: List<PluginCatalogVersion>): String {
        if (versions.isEmpty()) return ""
        return versions
            .sortedWith { left, right -> compareVersions(right.version, left.version) }
            .take(3)
            .joinToString(separator = " | ") { version -> version.version }
    }

    private fun resolveChangelogSummary(
        record: PluginInstallRecord,
        catalogEntry: PluginCatalogEntryRecord?,
        updateAvailability: PluginUpdateAvailability?,
    ): String {
        if (!updateAvailability?.changelogSummary.isNullOrBlank()) {
            return updateAvailability?.changelogSummary.orEmpty()
        }
        return catalogEntry?.entry?.versions
            ?.firstOrNull { version -> version.version == record.installedVersion }
            ?.changelog
            ?.lineSequence()
            ?.map(String::trim)
            ?.firstOrNull { line -> line.isNotBlank() }
            .orEmpty()
    }

    private fun toUpdateHintUiState(update: PluginUpdateAvailability): PluginUpdateHintUiState {
        return PluginUpdateHintUiState(
            latestVersion = update.latestVersion,
            changelogSummary = update.changelogSummary,
            blockedReason = update.incompatibilityReason,
        )
    }

    private fun toPermissionDiffHintUiState(
        diff: com.astrbot.android.model.plugin.PluginPermissionDiff,
    ): PluginPermissionDiffHintUiState {
        return PluginPermissionDiffHintUiState(
            addedPermissions = diff.added.map { permission -> permission.title },
            removedPermissions = diff.removed.map { permission -> permission.title },
            changedPermissions = diff.changed.map { permission -> permission.title },
            upgradedPermissions = diff.riskUpgraded.map { upgrade -> upgrade.to.title },
        )
    }

    private fun String.toUriHost(): String {
        return runCatching { java.net.URI(this).host.orEmpty() }
            .getOrDefault("")
    }

    private fun PluginInstallIntentResult.toFeedback(): PluginActionFeedback {
        return when (this) {
            is PluginInstallIntentResult.Installed -> PluginActionFeedback.Text(
                "Installed ${record.manifestSnapshot.title} ${record.installedVersion}.",
            )
            is PluginInstallIntentResult.RepositorySynced -> PluginActionFeedback.Text(
                "Repository synced: ${syncState.sourceId}.",
            )
            PluginInstallIntentResult.Ignored -> PluginActionFeedback.Text("Plugin action completed.")
        }
    }
}
