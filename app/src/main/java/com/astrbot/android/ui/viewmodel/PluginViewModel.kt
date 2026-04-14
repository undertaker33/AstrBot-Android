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
import com.astrbot.android.model.plugin.ExternalPluginHostActionPolicy
import com.astrbot.android.model.plugin.ExternalPluginMediaSourceResolver
import com.astrbot.android.model.plugin.ExternalPluginTriggerPolicy
import com.astrbot.android.model.plugin.HostActionRequest
import com.astrbot.android.model.plugin.MediaResult
import com.astrbot.android.model.plugin.NoOp
import com.astrbot.android.model.plugin.PluginBotSummary
import com.astrbot.android.model.plugin.PluginCardAction
import com.astrbot.android.model.plugin.PluginCardSchema
import com.astrbot.android.model.plugin.PluginCatalogEntryRecord
import com.astrbot.android.model.plugin.PluginCatalogVersion
import com.astrbot.android.model.plugin.PluginCompatibilityState
import com.astrbot.android.model.plugin.PluginCompatibilityStatus
import com.astrbot.android.model.plugin.PluginConfigSummary
import com.astrbot.android.model.plugin.PluginExecutionContext
import com.astrbot.android.model.plugin.PluginExecutionResult
import com.astrbot.android.model.plugin.PluginGovernanceSnapshot
import com.astrbot.android.model.plugin.PluginHostAction
import com.astrbot.android.model.plugin.PluginDownloadProgress
import com.astrbot.android.model.plugin.PluginInstallIntent
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginInstallIntentResult
import com.astrbot.android.model.plugin.PluginMessageSummary
import com.astrbot.android.model.plugin.PluginPackageValidationIssue
import com.astrbot.android.model.plugin.PluginPermissionGrant
import com.astrbot.android.model.plugin.PluginConfigStorageBoundary
import com.astrbot.android.model.plugin.PluginExecutionProtocolJson
import com.astrbot.android.model.plugin.PluginSettingsField
import com.astrbot.android.model.plugin.PluginSettingsSchema
import com.astrbot.android.model.plugin.PluginStaticConfigField
import com.astrbot.android.model.plugin.PluginStaticConfigFieldType
import com.astrbot.android.model.plugin.PluginStaticConfigSchema
import com.astrbot.android.model.plugin.PluginStaticConfigValue
import com.astrbot.android.model.plugin.PluginHostWorkspaceSnapshot
import com.astrbot.android.model.plugin.toStorageBoundary
import com.astrbot.android.model.plugin.PluginUpdateAvailability
import com.astrbot.android.model.plugin.PluginTriggerMetadata
import com.astrbot.android.model.plugin.PluginTriggerSource
import com.astrbot.android.model.plugin.PluginUninstallPolicy
import com.astrbot.android.model.plugin.SelectSettingField
import com.astrbot.android.model.plugin.SettingsUiRequest
import com.astrbot.android.model.plugin.TextResult
import com.astrbot.android.model.plugin.TextInputSettingField
import com.astrbot.android.model.plugin.ToggleSettingField
import com.astrbot.android.runtime.plugin.ExternalPluginHostActionExecutor
import com.astrbot.android.runtime.plugin.DefaultPluginHostCapabilityGateway
import com.astrbot.android.runtime.plugin.PluginExecutionHostApi
import com.astrbot.android.runtime.plugin.PluginExecutionHostSnapshot
import com.astrbot.android.runtime.plugin.PluginExecutionEngine
import com.astrbot.android.runtime.plugin.PluginFailureGuard
import com.astrbot.android.runtime.plugin.PluginGovernanceFailureProjection
import com.astrbot.android.runtime.plugin.PluginGovernanceReadModel
import com.astrbot.android.runtime.plugin.PluginGovernanceRecoveryStatus
import com.astrbot.android.runtime.plugin.PluginObservabilitySummary
import com.astrbot.android.runtime.plugin.PluginRuntimePlugin
import com.astrbot.android.runtime.plugin.PluginRuntimeDispatcher
import com.astrbot.android.runtime.plugin.PluginRuntimeFailureStateStoreProvider
import com.astrbot.android.runtime.plugin.PluginRuntimeRegistry
import com.astrbot.android.runtime.plugin.compareVersions
import com.astrbot.android.runtime.plugin.publishPluginRecoveryCompleted
import com.astrbot.android.runtime.plugin.publishPluginRecoveryFailed
import com.astrbot.android.runtime.plugin.publishPluginRecoveryRequested
import com.astrbot.android.runtime.plugin.publishUiGovernanceProjectionBuilt
import com.astrbot.android.runtime.RuntimeLogRepository
import com.astrbot.android.ui.plugin.PluginLocalFilter
import com.astrbot.android.ui.plugin.buildPluginMarketVersionOptions
import java.io.File
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
import org.json.JSONObject

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
    val repositoryUrl: String = "",
    val sourceName: String = "",
    val versions: List<PluginCatalogEntryVersionUiState> = emptyList(),
)

data class PluginCatalogEntryVersionUiState(
    val version: String,
    val packageUrl: String,
    val publishedAt: Long,
    val protocolVersion: Int,
    val minHostVersion: String,
    val maxHostVersion: String = "",
    val changelog: String = "",
    val compatibilityState: PluginCompatibilityState = PluginCompatibilityState.unknown(),
    val installable: Boolean = false,
    val validationIssues: List<PluginPackageValidationIssue> = emptyList(),
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

data class PluginGovernanceSummaryUiState(
    val snapshot: PluginGovernanceSnapshot,
    val observabilitySummary: PluginObservabilitySummary = PluginObservabilitySummary(),
    val failureProjection: PluginGovernanceFailureProjection = PluginGovernanceFailureProjection(
        pluginId = snapshot.pluginId,
    ),
) {
    val hasLogEntry: Boolean
        get() = observabilitySummary.totalCount > 0 || snapshot.diagnosticsSummary.totalCount > 0
}

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
    val governanceState: PluginGovernanceSnapshot? = null,
    val governanceSummary: PluginGovernanceSummaryUiState? = null,
)

private data class PluginWorkspaceSnapshot(
    val records: List<PluginInstallRecord>,
    val repositorySources: List<com.astrbot.android.model.plugin.PluginRepositorySource>,
    val catalogEntries: List<PluginCatalogEntryRecord>,
    val selectedId: String?,
    val isShowingDetail: Boolean,
)

private data class WorkspaceUiSnapshot(
    val snapshot: PluginHostWorkspaceSnapshot,
    val schemaState: PluginSchemaUiState,
    val actionMessage: PluginActionFeedback?,
    val isImportRunning: Boolean,
)

data class PluginScreenUiState(
    val records: List<PluginInstallRecord> = emptyList(),
    val repositorySources: List<PluginRepositorySourceCardUiState> = emptyList(),
    val catalogEntries: List<PluginCatalogEntryCardUiState> = emptyList(),
    val governanceByPluginId: Map<String, PluginGovernanceSummaryUiState> = emptyMap(),
    val updateAvailabilitiesByPluginId: Map<String, PluginUpdateAvailability> = emptyMap(),
    val failureStatesByPluginId: Map<String, PluginFailureUiState> = emptyMap(),
    val localSearchQuery: String = "",
    val marketSearchQuery: String = "",
    val selectedLocalFilter: PluginLocalFilter = PluginLocalFilter.ALL,
    val repositoryUrlDraft: String = "",
    val directPackageUrlDraft: String = "",
    val isInstallActionRunning: Boolean = false,
    val downloadProgress: PluginDownloadProgress? = null,
    val isMarketRefreshRunning: Boolean = false,
    val marketRefreshFeedback: PluginActionFeedback? = null,
    val selectedPluginId: String? = null,
    val selectedPlugin: PluginInstallRecord? = null,
    val isShowingDetail: Boolean = false,
    val installFollowUpGuide: PluginInstallFollowUpGuideState? = null,
    val detailMetadataState: PluginDetailMetadataState = PluginDetailMetadataState(),
    val detailActionState: PluginDetailActionState = PluginDetailActionState(),
    val upgradeDialogState: PluginUpgradeDialogState? = null,
    val staticConfigUiState: PluginStaticConfigUiState? = null,
    val schemaUiState: PluginSchemaUiState = PluginSchemaUiState.None,
    val hostWorkspaceState: PluginHostWorkspaceUiState = PluginHostWorkspaceUiState(),
    val hostVersion: String = "0.0.0",
    val selectedMarketVersionKeys: Map<String, String> = emptyMap(),
)

data class PluginDetailActionState(
    val compatibilityNotes: String = "",
    val enableBlockedReason: PluginActionFeedback? = null,
    val updateBlockedReason: PluginActionFeedback? = null,
    val updateAvailability: PluginUpdateAvailability? = null,
    val manageAvailability: PluginDetailManageActionAvailability = PluginDetailManageActionAvailability(),
    val mutationGate: PluginGovernanceMutationGate = PluginGovernanceMutationGate(),
    val lastActionMessage: PluginActionFeedback? = null,
    val failureState: PluginFailureUiState? = null,
    val governanceSummary: PluginGovernanceSummaryUiState? = null,
) {
    val isEnableActionEnabled: Boolean
        get() = manageAvailability.canEnable

    val isDisableActionEnabled: Boolean
        get() = manageAvailability.canDisable

    val isUpgradeActionEnabled: Boolean
        get() = manageAvailability.canUpgrade
}

data class PluginGovernanceMutationGate(
    val isReadOnly: Boolean = false,
    val blockedMessage: String = "",
)

data class PluginDetailManageActionAvailability(
    val canEnable: Boolean = false,
    val canDisable: Boolean = false,
    val canRecover: Boolean = false,
    val canUpgrade: Boolean = false,
    val canUninstall: Boolean = false,
    val canViewLogs: Boolean = false,
    val canOpenConfig: Boolean = false,
    val canRestoreDefaults: Boolean = false,
    val canClearCache: Boolean = false,
)

data class PluginInstallFollowUpGuideState(
    val pluginId: String,
    val pluginTitle: String,
    val author: String,
    val permissionCount: Int,
    val runtimeLabel: String,
    val riskLabel: String,
    val canEnableNow: Boolean,
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

    data class Text(
        val title: String,
        val text: String,
        val markdown: Boolean = false,
    ) : PluginSchemaUiState

    data class Card(
        val schema: PluginCardSchema,
        val lastActionFeedback: PluginActionFeedback? = null,
    ) : PluginSchemaUiState

    data class Media(
        val items: List<PluginSchemaMediaItem> = emptyList(),
    ) : PluginSchemaUiState

    data class Error(
        val message: String,
    ) : PluginSchemaUiState

    data class Settings(
        val schema: PluginSettingsSchema,
        val draftValues: Map<String, PluginSettingDraftValue> = emptyMap(),
    ) : PluginSchemaUiState
}

data class PluginSchemaMediaItem(
    val label: String,
    val resolvedSource: String,
    val mimeType: String,
)

data class PluginStaticConfigUiState(
    val schema: PluginStaticConfigSchema,
    val draftValues: Map<String, PluginSettingDraftValue> = emptyMap(),
)

data class PluginHostWorkspaceFileUiState(
    val relativePath: String,
    val sizeBytes: Long = 0L,
    val lastModifiedAtEpochMillis: Long = 0L,
)

data class PluginHostWorkspaceUiState(
    val isVisible: Boolean = false,
    val pluginId: String = "",
    val title: String = "",
    val description: String = "",
    val privateRootPath: String = "",
    val importsPath: String = "",
    val runtimePath: String = "",
    val exportsPath: String = "",
    val cachePath: String = "",
    val files: List<PluginHostWorkspaceFileUiState> = emptyList(),
    val managementSchemaState: PluginSchemaUiState = PluginSchemaUiState.None,
    val lastActionMessage: PluginActionFeedback? = null,
    val isImportActionRunning: Boolean = false,
)

class PluginViewModel(
    private val dependencies: PluginViewModelDependencies = DefaultPluginViewModelDependencies,
) : ViewModel() {
    private val hostCapabilityGateway = DefaultPluginHostCapabilityGateway(
        hostActionExecutor = ExternalPluginHostActionExecutor(),
    )
    private val selectedPluginId = MutableStateFlow<String?>(null)
    private val showingDetail = MutableStateFlow(false)
    private val lastActionMessage = MutableStateFlow<PluginActionFeedback?>(null)
    private val schemaUiState = MutableStateFlow<PluginSchemaUiState>(PluginSchemaUiState.None)
    private val staticConfigUiState = MutableStateFlow<PluginStaticConfigUiState?>(null)
    private val hostWorkspaceSnapshot = MutableStateFlow(PluginHostWorkspaceSnapshot())
    private val hostWorkspaceSchemaUiState = MutableStateFlow<PluginSchemaUiState>(PluginSchemaUiState.None)
    private val hostWorkspaceActionMessage = MutableStateFlow<PluginActionFeedback?>(null)
    private val hostWorkspaceImportRunning = MutableStateFlow(false)
    private val repositoryUrlDraft = MutableStateFlow("")
    private val directPackageUrlDraft = MutableStateFlow("")
    private val installActionRunning = MutableStateFlow(false)
    private val downloadProgress = MutableStateFlow<PluginDownloadProgress?>(null)
    private val installFollowUpGuide = MutableStateFlow<PluginInstallFollowUpGuideState?>(null)
    private val marketRefreshRunning = MutableStateFlow(false)
    private val marketRefreshFeedback = MutableStateFlow<PluginActionFeedback?>(null)
    private val upgradeDialogState = MutableStateFlow<PluginUpgradeDialogState?>(null)
    private val localSearchQuery = MutableStateFlow("")
    private val marketSearchQuery = MutableStateFlow("")
    private val selectedLocalFilter = MutableStateFlow(PluginLocalFilter.ALL)
    private val selectedMarketVersionKeys = MutableStateFlow<Map<String, String>>(emptyMap())
    private var officialMarketBootstrapAttempted = false
    private var currentConfigBoundary: PluginConfigStorageBoundary? = null
    private var lastGovernanceProjectionLogKey: String = ""

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
        dependencies.governanceReadModels,
    ) { snapshot, actionMessage, schemaState, staticSchemaState, governanceReadModels ->
        val selectedPlugin = snapshot.records.firstOrNull { it.pluginId == snapshot.selectedId }
        val governanceByPluginId = governanceReadModels.mapValues { (_, readModel) ->
            readModel.toUiState()
        }
        val updateAvailabilities = snapshot.records.mapNotNull { record ->
            dependencies.getUpdateAvailability(record.pluginId)?.let { record.pluginId to it }
        }.toMap()
        val failureStates = buildFailureStates(
            records = snapshot.records,
            governanceByPluginId = governanceByPluginId,
        )
        publishUiGovernanceProjectionBuiltIfChanged(
            records = snapshot.records,
            selectedPlugin = selectedPlugin,
            isShowingDetail = snapshot.isShowingDetail && selectedPlugin != null,
            governanceByPluginId = governanceByPluginId,
            failureStates = failureStates,
        )
        PluginScreenUiState(
            records = snapshot.records,
            repositorySources = snapshot.repositorySources.map(::toRepositorySourceCardUiState),
            catalogEntries = snapshot.catalogEntries.map(::toCatalogEntryCardUiState),
            governanceByPluginId = governanceByPluginId,
            updateAvailabilitiesByPluginId = updateAvailabilities,
            failureStatesByPluginId = failureStates,
            selectedPluginId = selectedPlugin?.pluginId,
            selectedPlugin = selectedPlugin,
            isShowingDetail = snapshot.isShowingDetail && selectedPlugin != null,
            detailMetadataState = buildDetailMetadataState(
                record = selectedPlugin,
                governanceSummary = selectedPlugin?.let { governanceByPluginId[it.pluginId] },
                repositorySources = snapshot.repositorySources,
                catalogEntries = snapshot.catalogEntries,
                updateAvailability = selectedPlugin?.let { updateAvailabilities[it.pluginId] },
            ),
            detailActionState = buildDetailActionState(
                record = selectedPlugin,
                governanceSummary = selectedPlugin?.let { governanceByPluginId[it.pluginId] },
                actionMessage = actionMessage,
                updateAvailability = selectedPlugin?.let { updateAvailabilities[it.pluginId] },
            ),
            staticConfigUiState = staticSchemaState,
            schemaUiState = schemaState,
            hostVersion = dependencies.getHostVersion(),
        )
    }.combine(
        combine(
            hostWorkspaceSnapshot,
            hostWorkspaceSchemaUiState,
            hostWorkspaceActionMessage,
            hostWorkspaceImportRunning,
        ) { workspaceSnapshot, workspaceSchemaState, workspaceActionMessageState, isWorkspaceImportRunning ->
            WorkspaceUiSnapshot(
                snapshot = workspaceSnapshot,
                schemaState = workspaceSchemaState,
                actionMessage = workspaceActionMessageState,
                isImportRunning = isWorkspaceImportRunning,
            )
        },
    ) { state, workspaceState ->
        state.copy(
            hostWorkspaceState = buildHostWorkspaceState(
                record = state.selectedPlugin,
                snapshot = workspaceState.snapshot,
                managementSchemaState = workspaceState.schemaState,
                actionMessage = workspaceState.actionMessage,
                isImportActionRunning = workspaceState.isImportRunning,
            ),
        )
    }.combine(upgradeDialogState) { state, upgradeDialog ->
        state.copy(upgradeDialogState = upgradeDialog)
    }.combine(localSearchQuery) { state, searchQuery ->
        state.copy(localSearchQuery = searchQuery)
    }.combine(marketSearchQuery) { state, searchQuery ->
        state.copy(marketSearchQuery = searchQuery)
    }.combine(selectedLocalFilter) { state, filter ->
        state.copy(selectedLocalFilter = filter)
    }.combine(repositoryUrlDraft) { state, repositoryDraft ->
        state.copy(repositoryUrlDraft = repositoryDraft)
    }.combine(directPackageUrlDraft) { state, directDraft ->
        state.copy(directPackageUrlDraft = directDraft)
    }.combine(installActionRunning) { state, isInstallRunning ->
        state.copy(isInstallActionRunning = isInstallRunning)
    }.combine(downloadProgress) { state, progress ->
        state.copy(downloadProgress = progress)
    }.combine(installFollowUpGuide) { state, followUpGuide ->
        state.copy(installFollowUpGuide = followUpGuide)
    }.combine(marketRefreshRunning) { state, isMarketRefreshRunning ->
        state.copy(isMarketRefreshRunning = isMarketRefreshRunning)
    }.combine(marketRefreshFeedback) { state, feedback ->
        state.copy(marketRefreshFeedback = feedback)
    }.combine(selectedMarketVersionKeys) { state, versionKeys ->
        state.copy(selectedMarketVersionKeys = versionKeys)
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
                    hostWorkspaceSnapshot.value = PluginHostWorkspaceSnapshot()
                    hostWorkspaceSchemaUiState.value = PluginSchemaUiState.None
                    hostWorkspaceActionMessage.value = null
                    currentConfigBoundary = null
                }
                if (resolvedSelection == null && showingDetail.value) {
                    showingDetail.value = false
                }
            }
        }
        viewModelScope.launch {
            dependencies.repositorySources.collect { sources ->
                if (officialMarketBootstrapAttempted) return@collect
                if (sources.isNotEmpty()) {
                    officialMarketBootstrapAttempted = true
                    RuntimeLogRepository.append(
                        "Plugin market bootstrap skipped: sourceCount=${sources.size}",
                    )
                    return@collect
                }
                officialMarketBootstrapAttempted = true
                RuntimeLogRepository.append("Plugin market bootstrap requested: sourceCount=0")
                runCatching {
                    dependencies.ensureOfficialMarketCatalogSubscribed()
                }.onSuccess { syncState ->
                    RuntimeLogRepository.append(
                        "Plugin market bootstrap finished: " +
                            "sourceId=${syncState.sourceId} " +
                            "status=${syncState.lastSyncStatus.name}",
                    )
                }.onFailure { error ->
                    RuntimeLogRepository.append(
                        "Plugin market bootstrap failed: error=${error.toRuntimeLogSummary()}",
                    )
                }
            }
        }
    }

    fun selectPlugin(pluginId: String) {
        selectPluginForDetail(pluginId)
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
        hostWorkspaceSnapshot.value = PluginHostWorkspaceSnapshot()
        hostWorkspaceSchemaUiState.value = PluginSchemaUiState.None
        hostWorkspaceActionMessage.value = null
        currentConfigBoundary = null
    }

    fun selectPluginForConfig(pluginId: String) {
        val selectedId = resolveSelection(
            records = dependencies.records.value,
            requestedPluginId = pluginId,
        ) ?: return
        selectedPluginId.value = selectedId
        showingDetail.value = true
        lastActionMessage.value = null
        hostWorkspaceSnapshot.value = PluginHostWorkspaceSnapshot()
        hostWorkspaceSchemaUiState.value = PluginSchemaUiState.None
        hostWorkspaceActionMessage.value = null
        loadConfigState(
            pluginId = selectedId,
            runtimeSchemaState = loadPackagedSettingsSchemaState(selectedId),
        )
    }

    fun selectPluginForWorkspace(pluginId: String) {
        val selectedId = resolveSelection(
            records = dependencies.records.value,
            requestedPluginId = pluginId,
        ) ?: return
        val selectedRecord = dependencies.records.value.firstOrNull { it.pluginId == selectedId } ?: return
        selectedPluginId.value = selectedId
        showingDetail.value = true
        lastActionMessage.value = null
        schemaUiState.value = PluginSchemaUiState.None
        staticConfigUiState.value = null
        currentConfigBoundary = null
        loadWorkspaceState(record = selectedRecord)
    }

    fun updateRepositoryUrlDraft(value: String) {
        repositoryUrlDraft.value = value
    }

    fun updateLocalSearchQuery(value: String) {
        localSearchQuery.value = value
    }

    fun updateMarketSearchQuery(value: String) {
        marketSearchQuery.value = value
    }

    fun updateSelectedLocalFilter(filter: PluginLocalFilter) {
        selectedLocalFilter.value = filter
    }

    fun updateDirectPackageUrlDraft(value: String) {
        directPackageUrlDraft.value = value
    }

    fun selectMarketPluginVersion(pluginId: String, versionKey: String) {
        selectedMarketVersionKeys.value = selectedMarketVersionKeys.value.toMutableMap().apply {
            if (versionKey.isBlank()) {
                remove(pluginId)
            } else {
                put(pluginId, versionKey)
            }
        }
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

    fun installOrUpdateCatalogPlugin(pluginId: String) {
        submitInstallIntent(
            buildIntent = { buildCatalogVersionIntent(pluginId, selectedMarketVersionKeys.value[pluginId]) },
            onSuccess = {},
        )
    }

    fun refreshMarketCatalog() {
        if (marketRefreshRunning.value) return
        viewModelScope.launch {
            marketRefreshRunning.value = true
            marketRefreshFeedback.value = null
            RuntimeLogRepository.append(
                "Plugin market refresh requested: sourceCount=${dependencies.repositorySources.value.size}",
            )
            runCatching {
                dependencies.refreshMarketCatalog()
            }.onSuccess { states ->
                RuntimeLogRepository.append(
                    "Plugin market refresh finished: " +
                        "resultCount=${states.size} " +
                        "statuses=${states.joinToString(separator = ",") { "${it.sourceId}:${it.lastSyncStatus.name}" }}",
                )
            }.onFailure {
                RuntimeLogRepository.append(
                    "Plugin market refresh failed: error=${it.toRuntimeLogSummary()}",
                )
                marketRefreshFeedback.value = PluginActionFeedback.Resource(R.string.plugin_market_refresh_failed)
            }
            marketRefreshRunning.value = false
        }
    }

    fun clearMarketRefreshFeedback() {
        marketRefreshFeedback.value = null
    }

    fun submitLocalPackageUri(uri: String) {
        if (installActionRunning.value) return
        viewModelScope.launch {
            installActionRunning.value = true
            runCatching {
                dependencies.installFromLocalPackageUri(uri)
            }.onSuccess { result ->
                installFollowUpGuide.value = toInstallFollowUpGuide(result)
                lastActionMessage.value = result.toFeedback()
            }.onFailure { error ->
                lastActionMessage.value = PluginActionFeedback.Text(localImportFailureMessage(error))
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
            lastActionMessage.value = PluginActionFeedback.Resource(R.string.plugin_action_feedback_no_update_available)
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

    fun recoverSelectedPluginFromSuspension() {
        val selected = uiState.value.selectedPlugin ?: return
        val currentFailureProjection = uiState.value.detailActionState.governanceSummary?.failureProjection
        if (!uiState.value.detailActionState.manageAvailability.canRecover) {
            lastActionMessage.value = PluginActionFeedback.Text(
                "Plugin is not currently suspended, so there is nothing to recover.",
            )
            return
        }
        dependencies.logBus.publishPluginRecoveryRequested(
            pluginId = selected.pluginId,
            pluginVersion = selected.installedVersion,
            occurredAtEpochMillis = System.currentTimeMillis(),
            recoveryStatus = currentFailureProjection?.status?.name.orEmpty().ifBlank { "SUSPENDED" },
            consecutiveFailureCount = currentFailureProjection?.consecutiveFailureCount
                ?: selected.failureState.consecutiveFailureCount,
            suspendedUntilEpochMillis = currentFailureProjection?.suspendedUntilEpochMillis
                ?: selected.failureState.suspendedUntilEpochMillis,
        )
        runCatching {
            dependencies.recoverPluginFailureState(selected.pluginId)
        }.onSuccess {
            dependencies.logBus.publishPluginRecoveryCompleted(
                pluginId = selected.pluginId,
                pluginVersion = selected.installedVersion,
                occurredAtEpochMillis = System.currentTimeMillis(),
            )
            lastActionMessage.value = PluginActionFeedback.Text(
                "Plugin recovered from suspension. You can enable it again.",
            )
        }.onFailure { error ->
            dependencies.logBus.publishPluginRecoveryFailed(
                pluginId = selected.pluginId,
                pluginVersion = selected.installedVersion,
                occurredAtEpochMillis = System.currentTimeMillis(),
                recoveryStatus = currentFailureProjection?.status?.name.orEmpty().ifBlank { "SUSPENDED" },
                consecutiveFailureCount = currentFailureProjection?.consecutiveFailureCount
                    ?: selected.failureState.consecutiveFailureCount,
                suspendedUntilEpochMillis = currentFailureProjection?.suspendedUntilEpochMillis
                    ?: selected.failureState.suspendedUntilEpochMillis,
                errorSummary = error.message ?: error.javaClass.simpleName,
            )
            lastActionMessage.value = PluginActionFeedback.Text(
                error.message ?: "Failed to recover plugin suspension.",
            )
        }
    }

    fun enablePluginFromInstallGuide(pluginId: String) {
        selectPlugin(pluginId)
        enableSelectedPlugin()
        installFollowUpGuide.value = null
    }

    fun dismissInstallFollowUpGuide() {
        installFollowUpGuide.value = null
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

    fun onHostWorkspaceCardActionClick(
        actionId: String,
        payload: Map<String, String> = emptyMap(),
    ) {
        val current = hostWorkspaceSchemaUiState.value as? PluginSchemaUiState.Card ?: return
        val action = current.schema.actions.firstOrNull { it.actionId == actionId }
        if (action == null) {
            hostWorkspaceSchemaUiState.value = current.copy(
                lastActionFeedback = PluginActionFeedback.Text(
                    "Unsupported schema card action: $actionId",
                ),
            )
            return
        }
        hostWorkspaceSchemaUiState.value = current.copy(
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
    }

    fun updateHostWorkspaceSettingsDraft(
        fieldId: String,
        draftValue: PluginSettingDraftValue,
    ) {
        val current = hostWorkspaceSchemaUiState.value as? PluginSchemaUiState.Settings ?: return
        if (!current.schema.containsField(fieldId)) return
        hostWorkspaceSchemaUiState.value = current.copy(
            draftValues = current.draftValues + (fieldId to draftValue),
        )
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
    }

    fun saveSelectedPluginConfig() {
        selectedPluginMutationGate().takeIf { it.isReadOnly }?.let { gate ->
            lastActionMessage.value = PluginActionFeedback.Text(gate.blockedMessage)
            return
        }
        staticConfigUiState.value?.let(::persistStaticConfig)
        (schemaUiState.value as? PluginSchemaUiState.Settings)?.let(::persistRuntimeConfig)
        lastActionMessage.value = PluginActionFeedback.Resource(R.string.common_saved)
    }

    fun uninstallSelectedPlugin() {
        val selected = uiState.value.selectedPlugin ?: return
        runCatching {
            dependencies.uninstallPlugin(selected.pluginId, PluginUninstallPolicy.REMOVE_DATA)
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
            downloadProgress.value = null
            runCatching {
                dependencies.handleInstallIntent(
                    intent = intent,
                    onDownloadProgress = { progress ->
                        downloadProgress.value = progress
                    },
                )
            }.onSuccess { result ->
                onSuccess()
                installFollowUpGuide.value = toInstallFollowUpGuide(result)
                lastActionMessage.value = result.toFeedback()
            }.onFailure { error ->
                lastActionMessage.value = PluginActionFeedback.Text(
                    error.message ?: "Plugin install action failed.",
                )
            }
            downloadProgress.value = null
            installActionRunning.value = false
        }
    }

    private fun buildDetailMetadataState(
        record: PluginInstallRecord?,
        governanceSummary: PluginGovernanceSummaryUiState?,
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
            governanceState = governanceSummary?.snapshot,
            governanceSummary = governanceSummary,
        )
    }

    private fun buildDetailActionState(
        record: PluginInstallRecord?,
        governanceSummary: PluginGovernanceSummaryUiState?,
        actionMessage: PluginActionFeedback?,
        updateAvailability: PluginUpdateAvailability?,
    ): PluginDetailActionState {
        if (record == null) {
            return PluginDetailActionState(lastActionMessage = actionMessage)
        }
        val failureState = buildFailureUiState(
            record = record,
            governanceSummary = governanceSummary,
        )
        val enableBlockedReason = buildEnableBlockedReason(
            record = record,
            governanceSummary = governanceSummary,
            failureState = failureState,
        )
        val mutationGate = buildPluginGovernanceMutationGate(
            record = record,
            governanceSummary = governanceSummary,
            failureState = failureState,
        )
        val manageAvailability = PluginDetailManageActionAvailability(
            canEnable = !record.enabled && enableBlockedReason == null,
            canDisable = record.enabled,
            canRecover = failureState?.isSuspended == true,
            canUpgrade = updateAvailability?.updateAvailable == true && updateAvailability.canUpgrade,
            canUninstall = true,
            canViewLogs = governanceSummary?.hasLogEntry == true,
            canOpenConfig = true,
            canRestoreDefaults = !mutationGate.isReadOnly,
            canClearCache = !mutationGate.isReadOnly,
        )
        return PluginDetailActionState(
            compatibilityNotes = governanceSummary?.snapshot?.runtimeHealth?.detail
                ?.takeIf { detail -> detail.isNotBlank() }
                ?: record.compatibilityState.notes,
            enableBlockedReason = enableBlockedReason,
            updateBlockedReason = updateAvailability
                ?.takeIf { it.updateAvailable && !it.canUpgrade }
                ?.incompatibilityReason
                ?.takeIf { it.isNotBlank() }
                ?.let(PluginActionFeedback::Text),
            updateAvailability = updateAvailability,
            manageAvailability = manageAvailability,
            mutationGate = mutationGate,
            lastActionMessage = actionMessage,
            failureState = failureState,
            governanceSummary = governanceSummary,
        )
    }

    private fun buildHostWorkspaceState(
        record: PluginInstallRecord?,
        snapshot: PluginHostWorkspaceSnapshot,
        managementSchemaState: PluginSchemaUiState,
        actionMessage: PluginActionFeedback?,
        isImportActionRunning: Boolean,
    ): PluginHostWorkspaceUiState {
        if (record == null || snapshot.privateRootPath.isBlank()) {
            return PluginHostWorkspaceUiState()
        }
        return PluginHostWorkspaceUiState(
            isVisible = true,
            pluginId = record.pluginId,
            title = record.manifestSnapshot.title,
            description = record.manifestSnapshot.description,
            privateRootPath = snapshot.privateRootPath,
            importsPath = snapshot.importsPath,
            runtimePath = snapshot.runtimePath,
            exportsPath = snapshot.exportsPath,
            cachePath = snapshot.cachePath,
            files = snapshot.files.map { file ->
                PluginHostWorkspaceFileUiState(
                    relativePath = file.relativePath,
                    sizeBytes = file.sizeBytes,
                    lastModifiedAtEpochMillis = file.lastModifiedAtEpochMillis,
                )
            },
            managementSchemaState = managementSchemaState,
            lastActionMessage = actionMessage,
            isImportActionRunning = isImportActionRunning,
        )
    }

    internal fun buildSourceBadgeForTest(record: PluginInstallRecord): PluginSourceBadgeUiState {
        return buildSourceBadge(record)
    }

    private fun executePluginEntry(
        record: PluginInstallRecord,
        entryPoint: String,
    ): PluginSchemaUiState {
        if (record.packageContractSnapshot?.protocolVersion == 2) {
            return PluginSchemaUiState.Error(
                "Plugin v2 entry click is blocked until the structured v2 runtime entry is restored: $entryPoint",
            )
        }
        val runtime = PluginRuntimeRegistry.plugins()
            .firstOrNull { plugin ->
                plugin.pluginId == record.pluginId &&
                    ExternalPluginTriggerPolicy.isOpen(PluginTriggerSource.OnPluginEntryClick) &&
                    PluginTriggerSource.OnPluginEntryClick in plugin.supportedTriggers
            }
            ?: return PluginSchemaUiState.None
        val context = buildEntryClickContext(
            record = record,
            runtime = runtime,
            entryPoint = entryPoint,
        )
        val execution = runCatching {
            val failureGuard = PluginFailureGuard(
                store = PluginRuntimeFailureStateStoreProvider.store(),
            )
            PluginExecutionEngine(
                dispatcher = PluginRuntimeDispatcher(failureGuard),
                failureGuard = failureGuard,
            ).execute(
                plugin = runtime,
                context = context,
            )
        }
        val result = execution.getOrNull()?.result ?: ErrorResult(
            message = execution.exceptionOrNull()?.message ?: "Plugin runtime failed",
        )
        return result.toSchemaUiState(
            record = record,
            context = context,
        )
    }

    private fun buildEntryClickContext(
        record: PluginInstallRecord,
        runtime: PluginRuntimePlugin,
        entryPoint: String,
    ): PluginExecutionContext {
        val staticBoundary = dependencies.getPluginStaticConfigSchema(record.pluginId)?.toStorageBoundary()
        val configSnapshot = staticBoundary?.let { boundary ->
            runCatching {
                dependencies.resolvePluginConfigSnapshot(
                    pluginId = record.pluginId,
                    boundary = boundary,
                )
            }.getOrDefault(com.astrbot.android.model.plugin.PluginConfigStoreSnapshot())
        } ?: com.astrbot.android.model.plugin.PluginConfigStoreSnapshot()
        val base = PluginExecutionContext(
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
            hostActionWhitelist = ExternalPluginHostActionPolicy.openActions(),
            triggerMetadata = PluginTriggerMetadata(
                entryPoint = entryPoint,
            ),
        )
        return PluginExecutionHostApi.inject(
            context = base,
            hostSnapshot = PluginExecutionHostSnapshot(
                workspaceSnapshot = dependencies.resolvePluginWorkspaceSnapshot(record.pluginId),
                configBoundary = staticBoundary,
                configSnapshot = configSnapshot,
            ),
        )
    }

    private fun PluginExecutionResult.toSchemaUiState(
        record: PluginInstallRecord,
        context: PluginExecutionContext,
    ): PluginSchemaUiState {
        return when (this) {
            is TextResult -> PluginSchemaUiState.Text(
                title = displayTitle.ifBlank { record.manifestSnapshot.title },
                text = text,
                markdown = markdown,
            )
            is CardResult -> PluginSchemaUiState.Card(schema = card)
            is MediaResult -> runCatching {
                PluginSchemaUiState.Media(
                    items = items.map { item ->
                        val resolved = ExternalPluginMediaSourceResolver.resolve(
                            item = item,
                            extractedDir = record.extractedDir,
                            privateRootPath = dependencies.resolvePluginWorkspaceSnapshot(record.pluginId).privateRootPath,
                        )
                        PluginSchemaMediaItem(
                            label = resolved.altText.ifBlank { resolved.source.substringAfterLast('/') },
                            resolvedSource = resolved.resolvedSource,
                            mimeType = resolved.mimeType,
                        )
                    },
                )
            }.getOrElse { error ->
                PluginSchemaUiState.Error(error.message ?: "Plugin media result is invalid")
            }
            is HostActionRequest -> {
                val execution = hostCapabilityGateway.executeHostAction(
                    pluginId = record.pluginId,
                    request = this,
                    context = context,
                )
                if (execution.succeeded) {
                    PluginSchemaUiState.Text(
                        title = title.ifBlank { action.name },
                        text = execution.message.ifBlank { action.name },
                    )
                } else {
                    PluginSchemaUiState.Error(
                        execution.message.ifBlank { "Plugin host action failed." },
                    )
                }
            }
            is SettingsUiRequest -> PluginSchemaUiState.Settings(
                schema = schema,
                draftValues = schema.defaultDraftValues(),
            )
            is NoOp -> PluginSchemaUiState.None
            is ErrorResult -> PluginSchemaUiState.Error(message)
        }
    }

    private fun loadWorkspaceState(record: PluginInstallRecord) {
        hostWorkspaceSnapshot.value = dependencies.resolvePluginWorkspaceSnapshot(record.pluginId)
        hostWorkspaceSchemaUiState.value = loadPackagedSettingsSchemaState(record.pluginId)
        hostWorkspaceActionMessage.value = null
        hostWorkspaceImportRunning.value = false
    }

    fun importWorkspaceFile(uri: String) {
        val pluginId = uiState.value.selectedPluginId ?: return
        selectedPluginMutationGate().takeIf { it.isReadOnly }?.let { gate ->
            hostWorkspaceActionMessage.value = PluginActionFeedback.Text(gate.blockedMessage)
            return
        }
        if (hostWorkspaceImportRunning.value) return
        viewModelScope.launch {
            hostWorkspaceImportRunning.value = true
            runCatching {
                dependencies.importPluginWorkspaceFile(
                    pluginId = pluginId,
                    uri = uri,
                )
            }.onSuccess { snapshot ->
                hostWorkspaceSnapshot.value = snapshot
                hostWorkspaceActionMessage.value = PluginActionFeedback.Text("Workspace file imported.")
            }.onFailure { error ->
                hostWorkspaceActionMessage.value = PluginActionFeedback.Text(
                    error.message ?: "Plugin workspace import failed.",
                )
            }
            hostWorkspaceImportRunning.value = false
        }
    }

    fun deleteWorkspaceFile(relativePath: String) {
        val pluginId = uiState.value.selectedPluginId ?: return
        selectedPluginMutationGate().takeIf { it.isReadOnly }?.let { gate ->
            hostWorkspaceActionMessage.value = PluginActionFeedback.Text(gate.blockedMessage)
            return
        }
        runCatching {
            dependencies.deletePluginWorkspaceFile(
                pluginId = pluginId,
                relativePath = relativePath,
            )
        }.onSuccess { snapshot ->
            hostWorkspaceSnapshot.value = snapshot
            hostWorkspaceActionMessage.value = PluginActionFeedback.Text("Workspace file removed.")
        }.onFailure { error ->
            hostWorkspaceActionMessage.value = PluginActionFeedback.Text(
                error.message ?: "Plugin workspace delete failed.",
            )
        }
    }

    fun restoreSelectedPluginDefaultConfig() {
        val selectedPluginId = uiState.value.selectedPluginId ?: return
        selectedPluginMutationGate().takeIf { it.isReadOnly }?.let { gate ->
            lastActionMessage.value = PluginActionFeedback.Text(gate.blockedMessage)
            return
        }
        val staticResetState = staticConfigUiState.value?.schema?.toUiState(emptyMap())
        if (staticResetState != null) {
            staticConfigUiState.value = staticResetState
            persistStaticConfig(staticResetState)
        }

        val runtimeResetState = (schemaUiState.value as? PluginSchemaUiState.Settings)?.let { settingsState ->
            settingsState.copy(draftValues = settingsState.schema.defaultDraftValues())
        }
        if (runtimeResetState != null) {
            schemaUiState.value = runtimeResetState
            persistRuntimeConfig(runtimeResetState)
        }

        lastActionMessage.value = PluginActionFeedback.Text(
            "Restored default settings for $selectedPluginId.",
        )
    }

    fun clearSelectedPluginCache() {
        val selected = uiState.value.selectedPlugin ?: return
        selectedPluginMutationGate().takeIf { it.isReadOnly }?.let { gate ->
            val feedback = PluginActionFeedback.Text(gate.blockedMessage)
            hostWorkspaceActionMessage.value = feedback
            lastActionMessage.value = feedback
            return
        }
        val pluginId = selected.pluginId
        val cacheFiles = hostWorkspaceSnapshot.value.files
            .map { it.relativePath }
            .filter { it.startsWith("cache/") }
        if (cacheFiles.isEmpty()) {
            val feedback = PluginActionFeedback.Text("No cache files were found.")
            hostWorkspaceActionMessage.value = feedback
            lastActionMessage.value = feedback
            return
        }
        runCatching {
            cacheFiles.fold(hostWorkspaceSnapshot.value) { _, relativePath ->
                dependencies.deletePluginWorkspaceFile(
                    pluginId = pluginId,
                    relativePath = relativePath,
                )
            }
        }.onSuccess { snapshot ->
            hostWorkspaceSnapshot.value = snapshot
            val feedback = PluginActionFeedback.Text("Plugin cache cleared.")
            hostWorkspaceActionMessage.value = feedback
            lastActionMessage.value = feedback
        }.onFailure { error ->
            val feedback = PluginActionFeedback.Text(
                error.message ?: "Plugin cache clear failed.",
            )
            hostWorkspaceActionMessage.value = feedback
            lastActionMessage.value = feedback
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

    private fun loadPackagedSettingsSchemaState(pluginId: String): PluginSchemaUiState {
        val schemaPath = dependencies.resolvePluginSettingsSchemaPath(pluginId)
            ?.takeIf { path -> path.isNotBlank() }
            ?: return PluginSchemaUiState.None
        return runCatching {
            val schemaJson = JSONObject(File(schemaPath).readText())
            val result = PluginExecutionProtocolJson.decodeResult(
                JSONObject().apply {
                    put("resultType", "settings_ui")
                    put("schema", schemaJson)
                },
            )
            when (result) {
                is SettingsUiRequest -> PluginSchemaUiState.Settings(
                    schema = result.schema,
                    draftValues = result.schema.defaultDraftValues(),
                )
                else -> PluginSchemaUiState.None
            }
        }.getOrElse { error ->
            PluginSchemaUiState.Error(error.message ?: "Plugin settings schema is invalid.")
        }
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
        return PluginActionFeedback.Text("${action.label} 璺?$payloadText")
    }

    private fun updateSelectedPluginEnabled(enabled: Boolean) {
        val selected = uiState.value.selectedPlugin ?: return
        val blockedReason = uiState.value.detailActionState.enableBlockedReason
        if (enabled && blockedReason != null) {
            lastActionMessage.value = blockedReason
            return
        }
        runCatching {
            dependencies.setPluginEnabled(selected.pluginId, enabled)
        }.onSuccess {
            lastActionMessage.value = PluginActionFeedback.Resource(
                resId = if (enabled) {
                    R.string.plugin_action_feedback_enabled_with_name
                } else {
                    R.string.plugin_action_feedback_disabled
                },
                formatArgs = if (enabled) {
                    listOf(selected.manifestSnapshot.title)
                } else {
                    emptyList()
                },
            )
        }.onFailure { error ->
            lastActionMessage.value = error.message?.let(PluginActionFeedback::Text)
                ?: PluginActionFeedback.Resource(R.string.plugin_action_feedback_update_state_failed)
        }
    }

    private fun publishUiGovernanceProjectionBuiltIfChanged(
        records: List<PluginInstallRecord>,
        selectedPlugin: PluginInstallRecord?,
        isShowingDetail: Boolean,
        governanceByPluginId: Map<String, PluginGovernanceSummaryUiState>,
        failureStates: Map<String, PluginFailureUiState>,
    ) {
        val pluginIds = records.map { record -> record.pluginId }
        val projectionKey = buildString {
            append("selected=").append(selectedPlugin?.pluginId.orEmpty())
            append(";detail=").append(isShowingDetail)
            append(";plugins=")
            append(
                records.joinToString(separator = "|") { record ->
                    val governance = governanceByPluginId[record.pluginId]
                    listOf(
                        record.pluginId,
                        record.installedVersion,
                        record.enabled.toString(),
                        governance?.snapshot?.runtimeHealth?.status?.name.orEmpty(),
                        governance?.snapshot?.suspensionState?.name.orEmpty(),
                        governance?.snapshot?.autoRecoveryState?.status.orEmpty(),
                        governance?.failureProjection?.status?.name.orEmpty(),
                        failureStates.containsKey(record.pluginId).toString(),
                    ).joinToString(separator = ":")
                },
            )
        }
        if (projectionKey == lastGovernanceProjectionLogKey) {
            return
        }
        lastGovernanceProjectionLogKey = projectionKey
        dependencies.logBus.publishUiGovernanceProjectionBuilt(
            occurredAtEpochMillis = System.currentTimeMillis(),
            pluginCount = records.size,
            selectedPluginId = selectedPlugin?.pluginId,
            isShowingDetail = isShowingDetail,
            failureUiCount = failureStates.size,
            pluginIds = pluginIds,
            projectionKey = projectionKey,
        )
    }

    private fun buildFailureStates(
        records: List<PluginInstallRecord>,
        governanceByPluginId: Map<String, PluginGovernanceSummaryUiState>,
    ): Map<String, PluginFailureUiState> {
        return records.mapNotNull { record ->
            buildFailureUiState(
                record = record,
                governanceSummary = governanceByPluginId[record.pluginId],
            )?.let { record.pluginId to it }
        }.toMap()
    }

    private fun buildFailureUiState(
        record: PluginInstallRecord,
        governanceSummary: PluginGovernanceSummaryUiState?,
    ): PluginFailureUiState? {
        val failureProjection = governanceSummary?.failureProjection
        val autoRecoveryState = governanceSummary?.snapshot?.autoRecoveryState
        val lastFailure = governanceSummary?.snapshot?.lastFailure
        val recoveryStatus = failureProjection?.status
        val consecutiveFailureCount = failureProjection?.consecutiveFailureCount
            ?: autoRecoveryState?.consecutiveFailureCount
            ?: if (governanceSummary == null) record.failureState.consecutiveFailureCount else 0
        val suspendedUntilEpochMillis = failureProjection?.suspendedUntilEpochMillis
            ?: autoRecoveryState?.suspendedUntilEpochMillis
            ?: if (governanceSummary == null) record.failureState.suspendedUntilEpochMillis else null
        val snapshotSuspended = governanceSummary?.snapshot?.suspensionState ==
            com.astrbot.android.model.plugin.PluginSuspensionState.SUSPENDED
        if (governanceSummary != null) {
            val activeGovernanceFailure = snapshotSuspended ||
                recoveryStatus == PluginGovernanceRecoveryStatus.SUSPENDED ||
                recoveryStatus == PluginGovernanceRecoveryStatus.RECOVERY_FAILED ||
                (
                    recoveryStatus == PluginGovernanceRecoveryStatus.TRACKING_FAILURES &&
                        consecutiveFailureCount > 0
                    ) ||
                autoRecoveryState?.status == "SUSPENDED" ||
                (
                    autoRecoveryState?.status == "TRACKING_FAILURES" &&
                        consecutiveFailureCount > 0
                    )
            if (!activeGovernanceFailure) {
                return null
            }
        }
        val lastErrorSummary = failureProjection?.lastErrorSummary
            ?.takeIf { summary -> summary.isNotBlank() }
            ?: lastFailure?.summary
                ?.takeIf { summary -> summary.isNotBlank() }
            ?: if (governanceSummary == null) record.failureState.lastErrorSummary else null
        if (
            recoveryStatus == PluginGovernanceRecoveryStatus.RECOVERED &&
            consecutiveFailureCount <= 0 &&
            suspendedUntilEpochMillis == null
        ) {
            return null
        }
        if (
            consecutiveFailureCount <= 0 &&
            suspendedUntilEpochMillis == null &&
            lastErrorSummary.isNullOrBlank()
        ) {
            return null
        }
        val isSuspended = suspendedUntilEpochMillis?.let { suspendedUntil -> suspendedUntil > System.currentTimeMillis() }
            ?: snapshotSuspended
        val summary = lastErrorSummary?.takeIf { it.isNotBlank() }
            ?: record.manifestSnapshot.title
        val summaryMessage = PluginActionFeedback.Resource(
            resId = R.string.plugin_failure_summary_with_error,
            formatArgs = listOf(summary),
        )
        val recoveryMessage = if (isSuspended && suspendedUntilEpochMillis != null) {
            PluginActionFeedback.Resource(
                resId = R.string.plugin_failure_recovery_at,
                formatArgs = listOf(formatRecoveryTime(suspendedUntilEpochMillis)),
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
            consecutiveFailureCount = consecutiveFailureCount,
            isSuspended = isSuspended,
            statusMessage = statusMessage,
            summaryMessage = summaryMessage,
            recoveryMessage = recoveryMessage,
            enableBlockedReason = blockedReason,
        )
    }

    private fun buildEnableBlockedReason(
        record: PluginInstallRecord,
        governanceSummary: PluginGovernanceSummaryUiState?,
        failureState: PluginFailureUiState?,
    ): PluginActionFeedback? {
        if (failureState?.enableBlockedReason != null) {
            return failureState.enableBlockedReason
        }
        val runtimeHealthStatus = governanceSummary?.snapshot?.runtimeHealth?.status
        if (
            !record.enabled &&
            (
                runtimeHealthStatus == com.astrbot.android.model.plugin.PluginRuntimeHealthStatus.UnsupportedProtocol ||
                    runtimeHealthStatus == com.astrbot.android.model.plugin.PluginRuntimeHealthStatus.UpgradeRequired ||
                    record.compatibilityState.status == PluginCompatibilityStatus.INCOMPATIBLE
                )
        ) {
            return buildIncompatibleEnableMessage(record)
        }
        return null
    }

    private fun buildPluginGovernanceMutationGate(
        record: PluginInstallRecord,
        governanceSummary: PluginGovernanceSummaryUiState?,
        failureState: PluginFailureUiState?,
    ): PluginGovernanceMutationGate {
        if (failureState?.isSuspended == true) {
            return PluginGovernanceMutationGate(
                isReadOnly = true,
                blockedMessage = "This plugin is suspended until recovery completes. Configuration and workspace actions are read-only.",
            )
        }
        val runtimeHealth = governanceSummary?.snapshot?.runtimeHealth
        val compatibilityNotes = record.compatibilityState.notes.takeIf { it.isNotBlank() }.orEmpty()
        if (
            runtimeHealth?.status == com.astrbot.android.model.plugin.PluginRuntimeHealthStatus.UnsupportedProtocol ||
            record.compatibilityState.status == PluginCompatibilityStatus.INCOMPATIBLE
        ) {
            val detail = runtimeHealth?.detail
                ?.takeIf { it.isNotBlank() }
                ?: compatibilityNotes
            return PluginGovernanceMutationGate(
                isReadOnly = true,
                blockedMessage = if (detail.isNotBlank()) {
                    "This plugin is unsupported on this host. $detail"
                } else {
                    "This plugin is unsupported on this host. Configuration and workspace actions are read-only."
                },
            )
        }
        if (runtimeHealth?.status == com.astrbot.android.model.plugin.PluginRuntimeHealthStatus.UpgradeRequired) {
            val detail = runtimeHealth.detail
            return PluginGovernanceMutationGate(
                isReadOnly = true,
                blockedMessage = if (detail.isNotBlank()) {
                    "This plugin requires a host upgrade before management actions are available. $detail"
                } else {
                    "This plugin requires a host upgrade before management actions are available."
                },
            )
        }
        return PluginGovernanceMutationGate()
    }

    private fun selectedPluginMutationGate(): PluginGovernanceMutationGate {
        val selected = uiState.value.selectedPlugin ?: return PluginGovernanceMutationGate()
        return buildPluginGovernanceMutationGate(
            record = selected,
            governanceSummary = uiState.value.governanceByPluginId[selected.pluginId],
            failureState = uiState.value.failureStatesByPluginId[selected.pluginId],
        )
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

    private fun localImportFailureMessage(error: Throwable): String {
        return error.message?.takeIf(String::isNotBlank) ?: "Plugin local import failed."
    }

    private fun Throwable.toRuntimeLogSummary(): String {
        return message?.trim().takeUnless { it.isNullOrBlank() } ?: javaClass.simpleName
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
        val latestVersionLabel = latestVersion?.version.orEmpty()
        return PluginCatalogEntryCardUiState(
            sourceId = record.sourceId,
            pluginId = record.entry.pluginId,
            title = record.entry.title,
            author = record.entry.author,
            summary = record.entry.entrySummary.ifBlank { record.entry.description },
            latestVersion = latestVersionLabel,
            repositoryUrl = resolveRepositoryHomepage(
                explicitRepositoryUrl = record.entry.repositoryUrl,
                packageUrl = latestVersion?.resolvePackageUrl(record.catalogUrl).orEmpty(),
            ),
            sourceName = record.sourceTitle,
            versions = record.entry.versions.map { version ->
                val gate = dependencies.evaluateCatalogVersion(version)
                PluginCatalogEntryVersionUiState(
                    version = version.version,
                    packageUrl = version.resolvePackageUrl(record.catalogUrl),
                    publishedAt = version.publishedAt,
                    protocolVersion = version.protocolVersion,
                    minHostVersion = version.minHostVersion,
                    maxHostVersion = version.maxHostVersion,
                    changelog = version.changelog,
                    compatibilityState = gate.compatibilityState,
                    installable = gate.installable,
                    validationIssues = gate.validationIssues,
                )
            },
        )
    }

    private fun buildCatalogVersionIntent(
        pluginId: String,
        selectedVersionKey: String?,
    ): PluginInstallIntent.CatalogVersion {
        val entries = dependencies.catalogEntries.value
            .filter { record -> record.entry.pluginId == pluginId }
            .map(::toCatalogEntryCardUiState)
        if (entries.isEmpty()) {
            error("Plugin $pluginId is not available in the current market source.")
        }
        val options = buildPluginMarketVersionOptions(
            entries = entries,
            pluginId = pluginId,
        )
        val selectedVersion = selectedVersionKey
            ?.let { key -> options.firstOrNull { option -> option.stableKey == key && option.isSelectable } }
            ?: options.firstOrNull { option -> option.isSelectable }
            ?: error("Plugin $pluginId does not expose any compatible installable versions.")
        return PluginInstallIntent.catalogVersion(
            pluginId = pluginId,
            version = selectedVersion.versionLabel,
            packageUrl = selectedVersion.packageUrl,
            catalogSourceId = selectedVersion.sourceId,
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

    private fun PluginGovernanceReadModel.toUiState(): PluginGovernanceSummaryUiState {
        return PluginGovernanceSummaryUiState(
            snapshot = snapshot,
            observabilitySummary = observabilitySummary,
            failureProjection = failureProjection,
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

    private fun resolveRepositoryHomepage(
        explicitRepositoryUrl: String,
        packageUrl: String,
    ): String {
        explicitRepositoryUrl.trim().takeIf { it.isNotBlank() }?.let { return it }
        val uri = runCatching { java.net.URI(packageUrl) }.getOrNull() ?: return ""
        val host = uri.host.orEmpty()
        if (
            !host.equals("github.com", ignoreCase = true) &&
            !host.equals("raw.githubusercontent.com", ignoreCase = true)
        ) {
            return ""
        }
        val segments = uri.path.split('/').filter { it.isNotBlank() }
        if (segments.size < 2) return ""
        return "https://github.com/${segments[0]}/${segments[1]}"
    }

    private fun PluginInstallIntentResult.toFeedback(): PluginActionFeedback {
        return when (this) {
            is PluginInstallIntentResult.Installed -> PluginActionFeedback.Text(
                "Installed ${record.manifestSnapshot.title} ${record.installedVersion}.",
            )
            is PluginInstallIntentResult.RepositorySynced -> PluginActionFeedback.Text(
                when (syncState.lastSyncStatus) {
                    com.astrbot.android.model.plugin.PluginCatalogSyncStatus.SUCCESS ->
                        "Market source synced: ${syncState.sourceId}. Open Market to review plugins."
                    com.astrbot.android.model.plugin.PluginCatalogSyncStatus.EMPTY ->
                        "Market source synced: ${syncState.sourceId}. No plugins were found yet."
                    com.astrbot.android.model.plugin.PluginCatalogSyncStatus.FAILED ->
                        "Market source saved: ${syncState.sourceId}, but sync failed."
                    com.astrbot.android.model.plugin.PluginCatalogSyncStatus.NEVER_SYNCED ->
                        "Market source saved: ${syncState.sourceId}."
                },
            )
            PluginInstallIntentResult.Ignored -> PluginActionFeedback.Text("Plugin action completed.")
        }
    }

    private fun toInstallFollowUpGuide(result: PluginInstallIntentResult): PluginInstallFollowUpGuideState? {
        return when (result) {
            is PluginInstallIntentResult.Installed -> {
                val governanceSummary = dependencies.getPluginGovernanceSilently(result.record.pluginId)?.toUiState()
                val governanceSnapshot = governanceSummary?.snapshot
                PluginInstallFollowUpGuideState(
                    pluginId = result.record.pluginId,
                    pluginTitle = result.record.manifestSnapshot.title,
                    author = result.record.manifestSnapshot.author,
                    permissionCount = result.record.permissionSnapshot.size,
                    runtimeLabel = governanceSnapshot?.runtimeKind
                        ?.takeIf { runtimeKind -> runtimeKind.isNotBlank() }
                        ?: result.record.packageContractSnapshot?.runtime?.kind
                        .orEmpty()
                        .ifBlank { "unknown" },
                    riskLabel = (governanceSnapshot?.riskLevel ?: result.record.manifestSnapshot.riskLevel)
                        .name
                        .lowercase(Locale.ROOT),
                    canEnableNow = !result.record.enabled &&
                        governanceSnapshot?.runtimeHealth?.status ==
                        com.astrbot.android.model.plugin.PluginRuntimeHealthStatus.Disabled &&
                        governanceSnapshot.suspensionState !=
                        com.astrbot.android.model.plugin.PluginSuspensionState.SUSPENDED,
                )
            }

            is PluginInstallIntentResult.RepositorySynced,
            PluginInstallIntentResult.Ignored,
            -> null
        }
    }
}
