package com.elymbot.android.ui.settings

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elymbot.android.core.common.logging.RuntimeLogger
import com.elymbot.android.core.runtime.context.RuntimePlatform
import com.elymbot.android.feature.bot.domain.BotRepositoryPort
import com.elymbot.android.feature.bot.domain.model.BotProfile
import com.elymbot.android.feature.config.domain.ConfigRepositoryPort
import com.elymbot.android.feature.conversation.domain.ConversationRepositoryPort
import com.elymbot.android.feature.geofence.domain.GeofenceRuleRepositoryPort
import com.elymbot.android.feature.geofence.domain.model.GeofenceExecutionRecord
import com.elymbot.android.feature.geofence.domain.model.GeofenceRule
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceCurrentLocationPort
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceCurrentLocationResult
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceCurrentLocationSnapshot
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceMapAvailability
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceMapAvailabilityPort
import com.elymbot.android.feature.geofence.domain.runtime.GeofencePermissionStatus
import com.elymbot.android.feature.geofence.domain.runtime.GeofencePermissionStatusPort
import com.elymbot.android.feature.geofence.presentation.GeofenceLocationPermissionUiAction
import com.elymbot.android.feature.geofence.presentation.GeofenceLocationPermissionUiState
import com.elymbot.android.feature.geofence.presentation.GeofenceRuleEditorDraft
import com.elymbot.android.feature.geofence.presentation.GeofenceRuleTargetContext
import com.elymbot.android.feature.geofence.presentation.GeofenceRulesPresentationController
import com.elymbot.android.feature.geofence.presentation.GeofenceRunHistoryLimit
import com.elymbot.android.feature.provider.domain.ProviderRepositoryPort
import com.elymbot.android.feature.provider.domain.model.ProviderCapability
import com.elymbot.android.model.chat.ConversationSession
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
internal class GeofenceRulesViewModel @Inject constructor(
    private val repository: GeofenceRuleRepositoryPort,
    private val controller: GeofenceRulesPresentationController,
    private val botPort: BotRepositoryPort,
    private val conversationPort: ConversationRepositoryPort,
    private val configPort: ConfigRepositoryPort,
    private val providerPort: ProviderRepositoryPort,
    private val runtimeLogger: RuntimeLogger,
    private val permissionStatusPort: GeofencePermissionStatusPort,
    private val mapAvailabilityPort: GeofenceMapAvailabilityPort,
    private val currentLocationPort: GeofenceCurrentLocationPort,
) : ViewModel() {
    val rules: StateFlow<List<GeofenceRule>> = repository.rules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val botProfiles: StateFlow<List<BotProfile>> = botPort.bots
    val selectedBotId: StateFlow<String> = botPort.selectedBotId
    val conversationSessions: StateFlow<List<ConversationSession>> = conversationPort.sessions
    val runHistoryState = mutableStateOf(GeofenceRuleRunHistoryUiState())
    val operationErrorState = mutableStateOf(GeofenceRuleOperationErrorUiState())
    val locationPermissionUiState = mutableStateOf(GeofenceLocationPermissionUiState())

    fun createRule(
        draft: GeofenceRuleEditorDraft,
        permissionStatus: GeofencePermissionStatus,
    ) {
        val selectedBot = resolveSelectedBotForSave(draft) ?: run {
            publishTargetError(draft)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                controller.createRule(draft, selectedBot, permissionStatus)
            }.onSuccess {
                clearOperationError()
            }.onFailure { error ->
                publishOperationFailure(operation = "create", error = error)
            }
        }
    }

    fun updateRule(
        existing: GeofenceRule,
        draft: GeofenceRuleEditorDraft,
        permissionStatus: GeofencePermissionStatus,
    ) {
        val selectedBot = resolveSelectedBotForSave(draft) ?: run {
            publishTargetError(draft)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                controller.updateRule(existing, draft, selectedBot, permissionStatus)
            }.onSuccess {
                clearOperationError()
            }.onFailure { error ->
                publishOperationFailure(operation = "update", error = error)
            }
        }
    }

    fun pauseRule(ruleId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                controller.pauseRule(ruleId)
            }.onSuccess {
                clearOperationError()
            }.onFailure { error ->
                publishOperationFailure(operation = "pause", error = error)
            }
        }
    }

    fun resumeRule(ruleId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                controller.resumeRule(ruleId)
            }.onSuccess {
                clearOperationError()
            }.onFailure { error ->
                publishOperationFailure(operation = "resume", error = error)
            }
        }
    }

    fun deleteRule(ruleId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                controller.deleteRule(ruleId)
            }.onSuccess {
                clearOperationError()
            }.onFailure { error ->
                publishOperationFailure(operation = "delete", error = error)
            }
        }
    }

    fun showRuns(rule: GeofenceRule) {
        runHistoryState.value = GeofenceRuleRunHistoryUiState(
            ruleId = rule.ruleId,
            ruleName = rule.name.ifBlank { rule.ruleId },
            loading = true,
        )
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                controller.listRuns(rule.ruleId, limit = GeofenceRunHistoryLimit)
            }.onSuccess { records ->
                clearOperationError()
                runHistoryState.value = GeofenceRuleRunHistoryUiState(
                    ruleId = rule.ruleId,
                    ruleName = rule.name.ifBlank { rule.ruleId },
                    runs = records,
                    loading = false,
                )
            }.onFailure { error ->
                publishOperationFailure(operation = "listRuns", error = error)
                runHistoryState.value = GeofenceRuleRunHistoryUiState(
                    ruleId = rule.ruleId,
                    ruleName = rule.name.ifBlank { rule.ruleId },
                    loading = false,
                    errorMessage = error.message ?: error.javaClass.simpleName,
                )
            }
        }
    }

    fun dismissRuns() {
        runHistoryState.value = GeofenceRuleRunHistoryUiState()
    }

    fun dismissOperationError() {
        clearOperationError()
    }

    fun currentPermissionStatus(): GeofencePermissionStatus =
        runCatching { permissionStatusPort.currentStatus() }.getOrElse { error ->
            runtimeLogger.append(
                "GeofenceRulesViewModel permissionStatus fallback: ${error.message ?: error.javaClass.simpleName}",
            )
            GeofencePermissionStatus(foregroundGranted = false, backgroundGranted = false)
        }

    fun currentMapAvailability(): GeofenceMapAvailability =
        runCatching { mapAvailabilityPort.currentAvailability() }.getOrElse { error ->
            runtimeLogger.append(
                "GeofenceRulesViewModel mapAvailability fallback: ${error.message ?: error.javaClass.simpleName}",
            )
            GeofenceMapAvailability.SDK_UNAVAILABLE
        }

    fun logMapDiagnostic(message: String) {
        runtimeLogger.append("Geofence map: $message")
    }

    fun onUseCurrentLocationClick(status: GeofencePermissionStatus): GeofenceLocationPermissionUiAction {
        val decision = locationPermissionUiState.value.onUseCurrentLocation(status)
        locationPermissionUiState.value = decision.state
        return decision.action
    }

    fun onForegroundLocationPermissionResult(granted: Boolean): GeofenceLocationPermissionUiAction {
        val decision = locationPermissionUiState.value.onForegroundPermissionResult(granted)
        locationPermissionUiState.value = decision.state
        return decision.action
    }

    fun loadCurrentLocation(onLocation: (GeofenceCurrentLocationSnapshot) -> Unit) {
        locationPermissionUiState.value = locationPermissionUiState.value.copy(
            currentLocationLoading = true,
            errorMessage = "",
        )
        viewModelScope.launch {
            val result = currentLocationPort.currentLocation()
            locationPermissionUiState.value = locationPermissionUiState.value.onCurrentLocationResult(result)
            if (result is GeofenceCurrentLocationResult.Success) {
                onLocation(result.location)
            }
        }
    }

    fun showBackgroundPermissionGuide() {
        locationPermissionUiState.value = locationPermissionUiState.value.showBackgroundPermissionGuide()
    }

    fun dismissBackgroundPermissionGuide() {
        locationPermissionUiState.value = locationPermissionUiState.value.dismissBackgroundPermissionGuide()
    }

    fun onBackgroundLocationSettingsOpened(): GeofenceLocationPermissionUiAction {
        val decision = locationPermissionUiState.value.onBackgroundSettingsOpened()
        locationPermissionUiState.value = decision.state
        return decision.action
    }

    fun defaultTargetContext(): GeofenceRuleTargetContext {
        return runCatching {
            resolveDefaultGeofenceRuleTargetContext(
                botPort = botPort,
                conversationPort = conversationPort,
                configPort = configPort,
                providerPort = providerPort,
            )
        }.getOrElse { error ->
            runtimeLogger.append(
                "GeofenceRulesViewModel defaultTargetContext fallback: ${error.message ?: error.javaClass.simpleName}",
            )
            GeofenceRuleTargetContext(
                platform = RuntimePlatform.APP_CHAT.wireValue,
                conversationId = conversationPort.defaultSessionId,
                configProfileId = configPort.selectedProfileId.value,
            )
        }
    }

    private fun resolveSelectedBotForSave(draft: GeofenceRuleEditorDraft): BotProfile? {
        val currentProfiles = botProfiles.value
        val existingBot = currentProfiles.firstOrNull { it.id == draft.selectedBotId }
        if (existingBot != null) return existingBot
        if (draft.enabled) return null
        return BotProfile(
            id = draft.selectedBotId.ifBlank { "manual-geofence-bot" },
            displayName = draft.selectedBotId.ifBlank { "Manual geofence target" },
            configProfileId = draft.configProfileId,
            defaultPersonaId = draft.personaId,
            defaultProviderId = draft.providerId,
        )
    }

    private fun publishTargetError(draft: GeofenceRuleEditorDraft) {
        val message = if (draft.selectedBotId.isBlank()) {
            "Target bot is required before enabling this geofence rule."
        } else {
            "Selected bot '${draft.selectedBotId}' is no longer available. Choose an existing bot before enabling this geofence rule."
        }
        runtimeLogger.append("GeofenceRulesViewModel target validation failed: $message")
        operationErrorState.value = GeofenceRuleOperationErrorUiState(message = message)
    }

    private fun publishOperationFailure(
        operation: String,
        error: Throwable,
    ) {
        val errorMessage = error.message ?: error.javaClass.simpleName
        runtimeLogger.append("GeofenceRulesViewModel $operation failed: $errorMessage")
        operationErrorState.value = GeofenceRuleOperationErrorUiState(
            message = "$operation failed: $errorMessage",
        )
    }

    private fun clearOperationError() {
        if (operationErrorState.value.visible) {
            operationErrorState.value = GeofenceRuleOperationErrorUiState()
        }
    }
}

data class GeofenceRuleRunHistoryUiState(
    val ruleId: String = "",
    val ruleName: String = "",
    val runs: List<GeofenceExecutionRecord> = emptyList(),
    val loading: Boolean = false,
    val errorMessage: String = "",
) {
    val visible: Boolean = ruleId.isNotBlank()
}

data class GeofenceRuleOperationErrorUiState(
    val message: String = "",
) {
    val visible: Boolean = message.isNotBlank()
}

internal fun resolveDefaultGeofenceRuleTargetContext(
    botPort: BotRepositoryPort,
    conversationPort: ConversationRepositoryPort,
    configPort: ConfigRepositoryPort,
    providerPort: ProviderRepositoryPort,
): GeofenceRuleTargetContext {
    val snapshot = botPort.snapshotProfiles()
    val selectedBot = snapshot
        .firstOrNull { it.id == botPort.selectedBotId.value }
        ?: snapshot.firstOrNull()
        ?: error("No bot profiles available for geofence rule creation")
    return selectedBot.toGeofenceRuleTargetContext(
        conversationPort = conversationPort,
        configPort = configPort,
        providerPort = providerPort,
    )
}

internal fun BotProfile.toGeofenceRuleTargetContext(
    conversationPort: ConversationRepositoryPort,
    configPort: ConfigRepositoryPort,
    providerPort: ProviderRepositoryPort,
    platform: String = RuntimePlatform.APP_CHAT.wireValue,
    conversationId: String = conversationPort.defaultSessionId,
    origin: String = "ui",
): GeofenceRuleTargetContext {
    val requestedConfigId = configProfileId.ifBlank { configPort.selectedProfileId.value }
    val config = configPort.resolve(requestedConfigId)
    val resolvedConfigId = configProfileId.ifBlank { config.id }
    val providerId = defaultProviderId
        .ifBlank { config.defaultChatProviderId }
        .ifBlank {
            providerPort.providers.value.firstOrNull { provider ->
                provider.enabled && ProviderCapability.CHAT in provider.capabilities
            }?.id.orEmpty()
        }
    return GeofenceRuleTargetContext(
        platform = platform,
        conversationId = conversationId,
        botId = id,
        configProfileId = resolvedConfigId,
        personaId = defaultPersonaId,
        providerId = providerId,
        origin = origin,
    )
}
