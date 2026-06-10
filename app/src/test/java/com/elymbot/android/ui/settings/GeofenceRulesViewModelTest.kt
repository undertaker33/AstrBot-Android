package com.elymbot.android.ui.settings

import com.elymbot.android.core.common.logging.RuntimeLogger
import com.elymbot.android.feature.bot.domain.BotRepositoryPort
import com.elymbot.android.feature.bot.domain.model.BotProfile
import com.elymbot.android.feature.config.domain.ConfigRepositoryPort
import com.elymbot.android.feature.config.domain.model.ConfigProfile
import com.elymbot.android.feature.conversation.domain.ConversationRepositoryPort
import com.elymbot.android.feature.geofence.domain.GeofenceRuleRepositoryPort
import com.elymbot.android.feature.geofence.domain.model.ConfigGeofenceBinding
import com.elymbot.android.feature.geofence.domain.model.GeofenceActionType
import com.elymbot.android.feature.geofence.domain.model.GeofenceExecutionRecord
import com.elymbot.android.feature.geofence.domain.model.GeofenceRegion
import com.elymbot.android.feature.geofence.domain.model.GeofenceRule
import com.elymbot.android.feature.geofence.domain.model.GeofenceRuleStatus
import com.elymbot.android.feature.geofence.domain.model.GeofenceTransition
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceCurrentLocationPort
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceCurrentLocationResult
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceCurrentLocationSnapshot
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceMapAvailability
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceMapAvailabilityPort
import com.elymbot.android.feature.geofence.domain.runtime.GeofencePermissionStatus
import com.elymbot.android.feature.geofence.domain.runtime.GeofencePermissionStatusPort
import com.elymbot.android.feature.geofence.presentation.GeofenceRuleEditorDraft
import com.elymbot.android.feature.geofence.presentation.GeofenceRulesPresentationController
import com.elymbot.android.feature.provider.domain.ProviderRepositoryPort
import com.elymbot.android.feature.provider.domain.model.FeatureSupportState
import com.elymbot.android.feature.provider.domain.model.ProviderCapability
import com.elymbot.android.feature.provider.domain.model.ProviderProfile
import com.elymbot.android.feature.provider.domain.model.ProviderType
import com.elymbot.android.model.chat.ConversationAttachment
import com.elymbot.android.model.chat.ConversationMessage
import com.elymbot.android.model.chat.ConversationSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GeofenceRulesViewModelTest {
    private val mainDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun create_update_and_delete_delegate_to_repository_port() = runTest {
        val repository = FakeGeofenceRuleRepositoryPort(
            initialRules = listOf(sampleRule()),
        )
        val viewModel = viewModel(repository)

        // skipcq: KT-W1042
        viewModel.createRule(validDraft(name = "Created"), permissionReady())
        val created = awaitAsync { repository.createdRule.await() }

        // skipcq: KT-W1042
        viewModel.updateRule(sampleRule(), validDraft(name = "Updated"), permissionReady())
        val updated = awaitAsync { repository.updatedRule.await() }

        // skipcq: KT-W1042
        viewModel.deleteRule("rule-1")
        val deletedRuleId = awaitAsync { repository.deletedRuleId.await() }

        assertEquals("Created", created.rule.name)
        assertEquals(1, created.regions.size)
        assertEquals("Updated", updated.name)
        assertEquals("rule-1", deletedRuleId)
    }

    @Test
    fun create_derives_hidden_target_ids_from_selected_bot() = runTest {
        val repository = FakeGeofenceRuleRepositoryPort()
        val viewModel = viewModel(repository)

        viewModel.createRule(
            validDraft().copy(
                configProfileId = "",
                personaId = "",
                providerId = "",
            ),
            permissionReady(),
        )
        val created = awaitAsync { repository.createdRule.await() }.rule

        // skipcq: KT-W1042
        assertEquals("chat-main", created.targetConversationId)
        // skipcq: KT-W1042
        assertEquals("bot-1", created.targetBotId)
        // skipcq: KT-W1042
        assertEquals("config-1", created.targetConfigProfileId)
        // skipcq: KT-W1042
        assertEquals("persona-1", created.targetPersonaId)
        // skipcq: KT-W1042
        assertEquals("provider-1", created.targetProviderId)
    }

    @Test
    fun create_and_update_mark_enabled_rules_permission_required_when_permission_is_missing() = runTest {
        val repository = FakeGeofenceRuleRepositoryPort(
            initialRules = listOf(sampleRule()),
        )
        val viewModel = viewModel(repository)

        viewModel.createRule(
            draft = validDraft(name = "Created"),
            permissionStatus = GeofencePermissionStatus(foregroundGranted = false, backgroundGranted = true),
        )
        val created = awaitAsync { repository.createdRule.await() }

        viewModel.updateRule(
            existing = sampleRule(),
            draft = validDraft(name = "Updated"),
            permissionStatus = GeofencePermissionStatus(foregroundGranted = true, backgroundGranted = false),
        )
        val updated = awaitAsync { repository.updatedRule.await() }

        assertEquals(GeofenceRuleStatus.PERMISSION_REQUIRED, created.rule.status)
        assertEquals(GeofenceRuleStatus.PERMISSION_REQUIRED, updated.status)
    }

    @Test
    fun create_update_and_delete_failures_update_operation_error_state() = runTest {
        val repository = FakeGeofenceRuleRepositoryPort(
            initialRules = listOf(sampleRule()),
        )
        val viewModel = viewModel(repository)

        repository.createFailure = IllegalStateException("create failed")
        viewModel.createRule(validDraft(), permissionReady())
        assertTrue(viewModel.awaitOperationErrorMessage().contains("create failed"))

        repository.createFailure = null
        viewModel.createRule(validDraft(name = "Created after failure"), permissionReady())
        awaitAsync { repository.createdRule.await() }
        viewModel.awaitOperationErrorCleared()

        repository.updateFailure = IllegalStateException("update failed")
        viewModel.updateRule(sampleRule(), validDraft(name = "Updated"), permissionReady())
        assertTrue(viewModel.awaitOperationErrorMessage().contains("update failed"))

        viewModel.dismissOperationError()
        repository.deleteFailure = IllegalStateException("delete failed")
        viewModel.deleteRule("rule-1")
        assertTrue(viewModel.awaitOperationErrorMessage().contains("delete failed"))
    }

    @Test
    fun pause_resume_and_list_runs_failures_update_operation_error_state() = runTest {
        val repository = FakeGeofenceRuleRepositoryPort(
            initialRules = listOf(sampleRule()),
        )
        val viewModel = viewModel(repository)

        repository.pauseFailure = IllegalStateException("pause failed")
        viewModel.pauseRule("rule-1")
        assertTrue(viewModel.awaitOperationErrorMessage().contains("pause failed"))

        viewModel.dismissOperationError()
        repository.resumeFailure = IllegalStateException("resume failed")
        viewModel.resumeRule("rule-1")
        assertTrue(viewModel.awaitOperationErrorMessage().contains("resume failed"))

        viewModel.dismissOperationError()
        // skipcq: KT-W1042
        repository.listRunsFailure = IllegalStateException("runs failed")
        viewModel.showRuns(sampleRule())
        assertTrue(viewModel.awaitOperationErrorMessage().contains("runs failed"))
        awaitAsync {
            while (viewModel.runHistoryState.value.loading) {
                delay(10)
            }
        }
        assertTrue(viewModel.runHistoryState.value.errorMessage.contains("runs failed"))
    }

    @Test
    fun enabled_save_with_stale_selected_bot_reports_error_and_skips_repository() = runTest {
        val repository = FakeGeofenceRuleRepositoryPort(
            initialRules = listOf(sampleRule()),
        )
        val viewModel = viewModel(
            repository = repository,
            bots = listOf(sampleBot(id = "bot-current")),
            selectedBotId = "bot-current",
        )

        viewModel.createRule(validDraft(selectedBotId = "bot-stale"), permissionReady())
        assertTrue(viewModel.awaitOperationErrorMessage().contains("bot"))
        assertEquals(0, repository.createRuleCallCount)

        viewModel.dismissOperationError()
        viewModel.updateRule(sampleRule(), validDraft(selectedBotId = "bot-stale"), permissionReady())
        assertTrue(viewModel.awaitOperationErrorMessage().contains("bot"))
        assertEquals(0, repository.updateRuleCallCount)
    }

    @Test
    fun pause_and_resume_update_rule_status_through_repository_port() = runTest {
        val repository = FakeGeofenceRuleRepositoryPort(
            initialRules = listOf(sampleRule()),
        )
        val viewModel = viewModel(repository)

        viewModel.pauseRule("rule-1")
        val pausedRuleId = awaitAsync { repository.pausedRuleId.await() }

        viewModel.resumeRule("rule-1")
        val resumedRuleId = awaitAsync { repository.resumedRuleId.await() }

        assertEquals("rule-1", pausedRuleId)
        assertEquals("rule-1", resumedRuleId)
    }

    @Test
    fun list_runs_queries_recent_execution_records_with_limit_ten() = runTest {
        val runs = listOf(
            GeofenceExecutionRecord(
                executionId = "run-1",
                ruleId = "rule-1",
                regionId = "region-1",
                configId = "config-1",
                transition = GeofenceTransition.ENTER,
                startedAt = 10L,
                status = "succeeded",
                deliverySummary = "Delivered",
            ),
        )
        val repository = FakeGeofenceRuleRepositoryPort(
            initialRules = listOf(sampleRule()),
            executionRecords = runs,
        )
        val viewModel = viewModel(repository)

        viewModel.showRuns(sampleRule())
        val request = awaitAsync { repository.listRunsRequest.await() }
        awaitAsync {
            while (viewModel.runHistoryState.value.loading) {
                delay(10)
            }
        }

        assertEquals("rule-1" to 10, request)
        assertEquals(runs, viewModel.runHistoryState.value.runs)
        assertFalse(viewModel.runHistoryState.value.loading)
    }

    @Test
    fun load_current_location_success_invokes_callback_and_clears_loading_state() = runTest {
        val snapshot = GeofenceCurrentLocationSnapshot(
            latitude = 35.681236,
            longitude = 139.767125,
            accuracyMeters = 18f,
            capturedAtMillis = 100L,
        )
        val viewModel = viewModel(
            repository = FakeGeofenceRuleRepositoryPort(),
            currentLocationPort = FakeGeofenceCurrentLocationPort(
                GeofenceCurrentLocationResult.Success(snapshot),
            ),
        )
        var captured: GeofenceCurrentLocationSnapshot? = null

        viewModel.loadCurrentLocation { location -> captured = location }
        mainDispatcher.scheduler.advanceUntilIdle()

        assertEquals(snapshot, captured)
        assertFalse(viewModel.locationPermissionUiState.value.currentLocationLoading)
        assertEquals("", viewModel.locationPermissionUiState.value.errorMessage)
    }

    @Test
    fun load_current_location_failure_keeps_callback_empty_and_reports_error() = runTest {
        val viewModel = viewModel(
            repository = FakeGeofenceRuleRepositoryPort(),
            currentLocationPort = FakeGeofenceCurrentLocationPort(
                GeofenceCurrentLocationResult.Failure(
                    errorCode = "location_unavailable",
                    message = "Location unavailable",
                ),
            ),
        )
        var callbackCalled = false

        viewModel.loadCurrentLocation { callbackCalled = true }
        mainDispatcher.scheduler.advanceUntilIdle()

        assertFalse(callbackCalled)
        assertFalse(viewModel.locationPermissionUiState.value.currentLocationLoading)
        assertTrue(viewModel.locationPermissionUiState.value.errorMessage.contains("Location unavailable"))
    }

    @Test
    fun default_target_context_uses_selected_bot_and_provider_fallback() {
        val target = resolveDefaultGeofenceRuleTargetContext(
            botPort = FakeGeofenceBotRepositoryPort(
                bots = listOf(
                    sampleBot(defaultProviderId = ""),
                ),
                selectedBotId = "bot-1",
            ),
            conversationPort = FakeConversationRepositoryPort(defaultSessionId = "chat-main"),
            configPort = FakeGeofenceConfigRepositoryPort(
                selectedProfileId = "config-1",
                configs = listOf(
                    ConfigProfile(id = "config-1", defaultChatProviderId = "provider-config"),
                ),
            ),
            providerPort = FakeGeofenceProviderRepositoryPort(
                providers = listOf(
                    providerProfile("provider-disabled", enabled = false),
                    providerProfile("provider-fallback", enabled = true),
                ),
            ),
        )

        assertEquals("chat-main", target.conversationId)
        assertEquals("bot-1", target.botId)
        assertEquals("config-1", target.configProfileId)
        assertEquals("provider-config", target.providerId)
    }

    private fun viewModel(
        repository: FakeGeofenceRuleRepositoryPort,
        bots: List<BotProfile> = listOf(sampleBot()),
        selectedBotId: String = "bot-1",
        currentLocationPort: GeofenceCurrentLocationPort = FakeGeofenceCurrentLocationPort(),
    ): GeofenceRulesViewModel {
        return GeofenceRulesViewModel(
            repository = repository,
            controller = GeofenceRulesPresentationController(repository),
            botPort = FakeGeofenceBotRepositoryPort(
                bots = bots,
                selectedBotId = selectedBotId,
            ),
            conversationPort = FakeConversationRepositoryPort(defaultSessionId = "chat-main"),
            configPort = FakeGeofenceConfigRepositoryPort(
                selectedProfileId = "config-1",
                configs = listOf(ConfigProfile(id = "config-1", defaultChatProviderId = "provider-1")),
            ),
            providerPort = FakeGeofenceProviderRepositoryPort(
                providers = listOf(providerProfile("provider-1", enabled = true)),
            ),
            runtimeLogger = RuntimeLogger.noop(),
            permissionStatusPort = FakeGeofencePermissionStatusPort(),
            mapAvailabilityPort = FakeGeofenceMapAvailabilityPort(),
            currentLocationPort = currentLocationPort,
        )
    }

    private fun validDraft(
        // skipcq: KT-W1042
        name: String = "Office",
        selectedBotId: String = "bot-1",
    ): GeofenceRuleEditorDraft {
        return GeofenceRuleEditorDraft(
            name = name,
            latitude = "31.2304",
            longitude = "121.4737",
            radiusMeters = "150",
            triggerEnter = true,
            actionPrompt = "Send a summary",
            conversationId = "chat-main",
            selectedBotId = selectedBotId,
            configProfileId = "config-1",
            personaId = "persona-1",
            providerId = "provider-1",
        )
    }

    private fun permissionReady(): GeofencePermissionStatus =
        GeofencePermissionStatus(foregroundGranted = true, backgroundGranted = true)

    private suspend fun GeofenceRulesViewModel.awaitOperationErrorMessage(): String =
        awaitAsync {
            while (!operationErrorState.value.visible) {
                delay(10)
            }
            operationErrorState.value.message
        }

    private suspend fun GeofenceRulesViewModel.awaitOperationErrorCleared() {
        awaitAsync {
            while (operationErrorState.value.visible) {
                delay(10)
            }
        }
    }

    private suspend fun <T> awaitAsync(block: suspend () -> T): T =
        withContext(Dispatchers.Default) {
            withTimeout(5_000) { block() }
        }
}

private data class CreatedRuleCall(
    val rule: GeofenceRule,
    val regions: List<GeofenceRegion>,
)

private class FakeGeofenceRuleRepositoryPort(
    initialRules: List<GeofenceRule> = emptyList(),
    private val executionRecords: List<GeofenceExecutionRecord> = emptyList(),
) : GeofenceRuleRepositoryPort {
    override val rules: StateFlow<List<GeofenceRule>> = MutableStateFlow(initialRules)
    override val bindings: StateFlow<List<ConfigGeofenceBinding>> = MutableStateFlow(emptyList())
    val createdRule = CompletableDeferred<CreatedRuleCall>()
    val updatedRule = CompletableDeferred<GeofenceRule>()
    val deletedRuleId = CompletableDeferred<String>()
    val pausedRuleId = CompletableDeferred<String>()
    val resumedRuleId = CompletableDeferred<String>()
    val listRunsRequest = CompletableDeferred<Pair<String, Int>>()
    var createFailure: Throwable? = null
    var updateFailure: Throwable? = null
    var deleteFailure: Throwable? = null
    var pauseFailure: Throwable? = null
    var resumeFailure: Throwable? = null
    var listRunsFailure: Throwable? = null
    var createRuleCallCount: Int = 0
    var updateRuleCallCount: Int = 0

    override suspend fun createRule(
        rule: GeofenceRule,
        regions: List<GeofenceRegion>,
    ): GeofenceRule {
        createRuleCallCount += 1
        createFailure?.let { throw it }
        createdRule.complete(CreatedRuleCall(rule, regions))
        return rule.copy(regions = regions)
    }

    override suspend fun updateRule(rule: GeofenceRule): GeofenceRule {
        updateRuleCallCount += 1
        updateFailure?.let { throw it }
        updatedRule.complete(rule)
        return rule
    }

    override suspend fun deleteRule(ruleId: String) {
        deleteFailure?.let { throw it }
        deletedRuleId.complete(ruleId)
    }

    override suspend fun pauseRule(ruleId: String): GeofenceRule? {
        pauseFailure?.let { throw it }
        pausedRuleId.complete(ruleId)
        return getRule(ruleId)?.copy(enabled = false)
    }

    override suspend fun resumeRule(ruleId: String): GeofenceRule? {
        resumeFailure?.let { throw it }
        resumedRuleId.complete(ruleId)
        return getRule(ruleId)?.copy(enabled = true)
    }

    override suspend fun getRule(ruleId: String): GeofenceRule? =
        rules.value.firstOrNull { it.ruleId == ruleId }

    override suspend fun listRules(): List<GeofenceRule> = rules.value

    override suspend fun replaceRegions(
        ruleId: String,
        regions: List<GeofenceRegion>,
    ): GeofenceRule? = getRule(ruleId)?.copy(regions = regions)

    override suspend fun upsertRegion(region: GeofenceRegion): GeofenceRegion = region

    override suspend fun deleteRegion(regionId: String) = Unit

    override suspend fun listAllConfigBindings(): List<ConfigGeofenceBinding> = emptyList()

    override suspend fun listConfigBindings(configId: String): List<ConfigGeofenceBinding> = emptyList()

    override suspend fun upsertConfigBinding(binding: ConfigGeofenceBinding): ConfigGeofenceBinding = binding

    override suspend fun deleteConfigBinding(configId: String, ruleId: String) = Unit

    override suspend fun recordExecution(record: GeofenceExecutionRecord): GeofenceExecutionRecord = record

    override suspend fun listRecentExecutionRecords(
        ruleId: String,
        limit: Int,
    ): List<GeofenceExecutionRecord> {
        listRunsFailure?.let { throw it }
        listRunsRequest.complete(ruleId to limit)
        return executionRecords
    }

    override suspend fun latestExecutionRecord(ruleId: String): GeofenceExecutionRecord? = null
}

private class FakeGeofenceBotRepositoryPort(
    bots: List<BotProfile>,
    selectedBotId: String,
) : BotRepositoryPort {
    override val bots: StateFlow<List<BotProfile>> = MutableStateFlow(bots)
    override val selectedBotId: StateFlow<String> = MutableStateFlow(selectedBotId)

    override fun currentBot(): BotProfile = bots.value.first()

    override fun snapshotProfiles(): List<BotProfile> = bots.value

    override fun create(name: String): BotProfile = BotProfile(id = name, displayName = name)

    override suspend fun save(profile: BotProfile) = Unit

    override suspend fun create(profile: BotProfile) = Unit

    override suspend fun delete(id: String) = Unit

    override suspend fun select(id: String) = Unit
}

private class FakeGeofenceConfigRepositoryPort(
    selectedProfileId: String,
    configs: List<ConfigProfile>,
) : ConfigRepositoryPort {
    override val profiles: StateFlow<List<ConfigProfile>> = MutableStateFlow(configs)
    override val selectedProfileId: StateFlow<String> = MutableStateFlow(selectedProfileId)

    override fun snapshotProfiles(): List<ConfigProfile> = profiles.value

    override fun create(name: String): ConfigProfile = ConfigProfile(id = name, name = name)

    override fun resolve(id: String): ConfigProfile =
        profiles.value.firstOrNull { it.id == id } ?: ConfigProfile(id = id)

    override fun resolveExistingId(id: String?): String = id.orEmpty()

    override suspend fun save(profile: ConfigProfile) = Unit

    override suspend fun delete(id: String) = Unit

    override suspend fun select(id: String) = Unit
}

private class FakeConversationRepositoryPort(
    override val defaultSessionId: String,
) : ConversationRepositoryPort {
    override val sessions: StateFlow<List<ConversationSession>> = MutableStateFlow(emptyList())

    override fun contextPreview(sessionId: String): String = ""

    override fun session(sessionId: String): ConversationSession =
        error("Conversation session lookup is not used by geofence rules tests")

    override fun syncSystemSessionTitle(sessionId: String, title: String) = Unit

    override fun appendMessage(
        sessionId: String,
        role: String,
        content: String,
        attachments: List<ConversationAttachment>,
    ): String = "message-1"

    override fun updateSessionBindings(
        sessionId: String,
        providerId: String,
        personaId: String,
        botId: String,
    ) = Unit

    override fun updateSessionServiceFlags(
        sessionId: String,
        sessionSttEnabled: Boolean?,
        sessionTtsEnabled: Boolean?,
    ) = Unit

    override fun updateMessage(
        sessionId: String,
        messageId: String,
        content: String?,
        attachments: List<ConversationAttachment>?,
    ) = Unit

    override fun replaceMessages(sessionId: String, messages: List<ConversationMessage>) = Unit

    override fun renameSession(sessionId: String, title: String) = Unit

    override fun deleteSession(sessionId: String) = Unit
}

private class FakeGeofenceProviderRepositoryPort(
    providers: List<ProviderProfile>,
) : ProviderRepositoryPort {
    override val providers: StateFlow<List<ProviderProfile>> = MutableStateFlow(providers)

    override fun snapshotProfiles(): List<ProviderProfile> = providers.value

    override fun providersWithCapability(capability: ProviderCapability): List<ProviderProfile> =
        providers.value.filter { capability in it.capabilities }

    override fun toggleEnabled(id: String) = Unit

    override fun updateMultimodalProbeSupport(id: String, support: FeatureSupportState) = Unit

    override fun updateNativeStreamingProbeSupport(id: String, support: FeatureSupportState) = Unit

    override fun updateSttProbeSupport(id: String, support: FeatureSupportState) = Unit

    override fun updateTtsProbeSupport(id: String, support: FeatureSupportState) = Unit

    override suspend fun save(profile: ProviderProfile) = Unit

    override suspend fun delete(id: String) = Unit
}

private class FakeGeofencePermissionStatusPort : GeofencePermissionStatusPort {
    override fun currentStatus(): GeofencePermissionStatus =
        GeofencePermissionStatus(foregroundGranted = false, backgroundGranted = false)
}

private class FakeGeofenceMapAvailabilityPort : GeofenceMapAvailabilityPort {
    override fun currentAvailability(): GeofenceMapAvailability = GeofenceMapAvailability.AVAILABLE
}

private class FakeGeofenceCurrentLocationPort(
    private val result: GeofenceCurrentLocationResult = GeofenceCurrentLocationResult.Success(
        GeofenceCurrentLocationSnapshot(
            latitude = 31.2304,
            longitude = 121.4737,
            accuracyMeters = 12f,
            capturedAtMillis = 100L,
        ),
    ),
) : GeofenceCurrentLocationPort {
    override suspend fun currentLocation(): GeofenceCurrentLocationResult = result
}

private fun sampleBot(
    id: String = "bot-1",
    defaultProviderId: String = "provider-1",
): BotProfile {
    return BotProfile(
        id = id,
        displayName = "Primary bot",
        configProfileId = "config-1",
        defaultPersonaId = "persona-1",
        defaultProviderId = defaultProviderId,
    )
}

private fun sampleRule(
    status: GeofenceRuleStatus = GeofenceRuleStatus.ACTIVE,
): GeofenceRule {
    return GeofenceRule(
        ruleId = "rule-1",
        name = "Office",
        triggerEnter = true,
        actionType = GeofenceActionType.AGENT_PROMPT,
        actionPrompt = "Send a summary",
        targetPlatform = "app_chat",
        targetConversationId = "chat-main",
        targetBotId = "bot-1",
        targetConfigProfileId = "config-1",
        targetPersonaId = "persona-1",
        targetProviderId = "provider-1",
        status = status,
        regions = listOf(
            GeofenceRegion(
                regionId = "region-1",
                ruleId = "rule-1",
                label = "Office",
                latitude = 31.2304,
                longitude = 121.4737,
                radiusMeters = 150f,
            ),
        ),
    )
}

private fun providerProfile(
    id: String,
    enabled: Boolean,
): ProviderProfile {
    return ProviderProfile(
        id = id,
        name = id,
        baseUrl = "https://example.com",
        model = "model",
        providerType = ProviderType.OPENAI_COMPATIBLE,
        apiKey = "key",
        capabilities = setOf(ProviderCapability.CHAT),
        enabled = enabled,
    )
}
