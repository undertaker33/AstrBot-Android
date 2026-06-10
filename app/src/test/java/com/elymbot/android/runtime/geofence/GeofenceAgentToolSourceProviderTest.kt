package com.elymbot.android.runtime.geofence

import com.elymbot.android.app.integration.geofence.GeofenceActiveCapabilityFacadeAdapter
import com.elymbot.android.core.common.logging.RuntimeLogger
import com.elymbot.android.core.runtime.context.IngressTrigger
import com.elymbot.android.core.runtime.context.RuntimePlatform
import com.elymbot.android.feature.cron.domain.CronJobRepositoryPort
import com.elymbot.android.feature.cron.domain.CronSchedulerPort
import com.elymbot.android.feature.cron.domain.EmptyActiveCapabilityPromptStrings
import com.elymbot.android.feature.cron.domain.model.CronJob
import com.elymbot.android.feature.cron.domain.model.CronJobExecutionRecord
import com.elymbot.android.feature.bot.domain.BotRepositoryPort
import com.elymbot.android.feature.bot.domain.model.BotProfile
import com.elymbot.android.feature.config.domain.ConfigRepositoryPort
import com.elymbot.android.feature.config.domain.model.ConfigProfile
import com.elymbot.android.feature.geofence.domain.GeofenceRuleRepositoryPort
import com.elymbot.android.feature.geofence.domain.model.ConfigGeofenceBinding
import com.elymbot.android.feature.geofence.domain.model.GeofenceExecutionRecord
import com.elymbot.android.feature.geofence.domain.model.GeofenceRegion
import com.elymbot.android.feature.geofence.domain.model.GeofenceRule
import com.elymbot.android.feature.geofence.domain.model.GeofenceRuleStatus
import com.elymbot.android.feature.geofence.domain.runtime.GeofencePermissionStatus
import com.elymbot.android.feature.geofence.domain.runtime.GeofencePermissionStatusPort
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceRegistrationStatus
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceRegistrationSummary
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceRuntimeReconciliationPort
import com.elymbot.android.feature.plugin.runtime.PluginToolArgs
import com.elymbot.android.feature.plugin.runtime.PluginToolResultStatus
import com.elymbot.android.feature.plugin.runtime.toolsource.ActiveCapabilityRuntimeFacade
import com.elymbot.android.feature.plugin.runtime.toolsource.ActiveCapabilityToolSourceProvider
import com.elymbot.android.feature.plugin.runtime.toolsource.FutureToolSourceContextResolver
import com.elymbot.android.feature.plugin.runtime.toolsource.ToolSourceContext
import com.elymbot.android.feature.plugin.runtime.toolsource.ToolSourceAvailabilityContext
import com.elymbot.android.feature.plugin.runtime.toolsource.ToolSourceIdentity
import com.elymbot.android.feature.plugin.runtime.toolsource.ToolSourceInvokeRequest
import com.elymbot.android.feature.plugin.runtime.toolsource.ToolSourceRegistryIngestContext
import com.elymbot.android.feature.plugin.runtime.PluginToolSourceKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeofenceAgentToolSourceProviderTest {
    @Test
    fun schema_can_create_rule_with_location_action_and_target_context() = runBlocking {
        val provider = provider()

        val create = provider.listBindings(ToolSourceRegistryIngestContext(userToolContext()))
            .single { it.descriptor.name == "create_geofence_rule" }

        val properties = create.descriptor.inputSchema["properties"] as Map<*, *>
        assertTrue(properties.containsKey("latitude"))
        assertTrue(properties.containsKey("longitude"))
        assertTrue(properties.containsKey("radius_meters"))
        assertTrue(properties.containsKey("trigger"))
        assertTrue(properties.containsKey("action_type"))
        assertTrue(properties.containsKey("conversation_id"))
        assertTrue(properties.containsKey("minimum_trigger_interval_millis"))
        assertFalse(properties.containsKey("config_profile_id"))
        assertFalse(properties.containsKey("persona_id"))
        assertFalse(properties.containsKey("provider_id"))
    }

    @Test
    fun create_derives_hidden_target_context_from_selected_bot() = runBlocking {
        val repository = InMemoryGeofenceRuleRepositoryForAgentTool()
        val provider = provider(
            repository = repository,
            botRepositoryPort = FakeBotRepositoryPort(
                listOf(
                    BotProfile(
                        id = "bot-selected",
                        configProfileId = "config-1",
                        defaultPersonaId = "persona-selected",
                        defaultProviderId = "provider-selected",
                    ),
                ),
            ),
        )

        val result = provider.invoke(createRequest(payload = validCreatePayload())).result

        assertEquals(PluginToolResultStatus.SUCCESS, result.status)
        val rule = repository.rulesSnapshot.single()
        assertEquals("bot-selected", rule.targetBotId)
        assertEquals("config-1", rule.targetConfigProfileId)
        assertEquals("persona-selected", rule.targetPersonaId)
        assertEquals("provider-selected", rule.targetProviderId)
    }

    @Test
    fun create_without_location_returns_missing_location() = runBlocking {
        val provider = provider()

        val result = provider.invoke(createRequest(payload = validCreatePayload() - setOf("latitude", "longitude"))).result

        assertEquals(PluginToolResultStatus.ERROR, result.status)
        assertEquals("missing_location", result.errorCode)
    }

    @Test
    fun create_using_current_location_without_permission_returns_permission_required() = runBlocking {
        val provider = provider(permission = GeofencePermissionStatus(foregroundGranted = false, backgroundGranted = false))

        val result = provider.invoke(
            createRequest(
                payload = validCreatePayload() - setOf("latitude", "longitude") + ("use_current_location" to true),
            ),
        ).result

        assertEquals(PluginToolResultStatus.ERROR, result.status)
        assertEquals("permission_required", result.errorCode)
    }

    @Test
    fun create_using_current_location_with_trusted_metadata_saves_metadata_location() = runBlocking {
        val repository = InMemoryGeofenceRuleRepositoryForAgentTool()
        val provider = provider(repository = repository)

        val result = provider.invoke(
            createRequest(
                payload = validCreatePayload() - setOf("latitude", "longitude") + ("use_current_location" to true),
                metadata = hostCurrentLocationMetadata(latitude = 35.6812, longitude = 139.7671),
            ),
        ).result

        assertEquals(PluginToolResultStatus.SUCCESS, result.status)
        val region = repository.rulesSnapshot.single().regions.single()
        assertEquals(35.6812, region.latitude, 0.0)
        assertEquals(139.7671, region.longitude, 0.0)
    }

    @Test
    fun create_with_invalid_config_profile_id_returns_missing_target_context() = runBlocking {
        val repository = InMemoryGeofenceRuleRepositoryForAgentTool()
        val provider = provider(repository = repository)

        val result = provider.invoke(
            createRequest(payload = validCreatePayload() + ("config_profile_id" to "missing-config")),
        ).result

        assertEquals(PluginToolResultStatus.ERROR, result.status)
        assertEquals("missing_target_context", result.errorCode)
        assertTrue(repository.rulesSnapshot.isEmpty())
    }

    @Test
    fun create_with_invalid_target_platform_returns_invalid_target_platform() = runBlocking {
        val repository = InMemoryGeofenceRuleRepositoryForAgentTool()
        val provider = provider(repository = repository)

        val result = provider.invoke(
            createRequest(payload = validCreatePayload() + ("target_platform" to "sms")),
        ).result

        assertEquals(PluginToolResultStatus.ERROR, result.status)
        assertEquals("invalid_target_platform", result.errorCode)
        assertTrue(repository.rulesSnapshot.isEmpty())
    }

    @Test
    fun create_with_bot_from_other_config_returns_missing_target_context() = runBlocking {
        val repository = InMemoryGeofenceRuleRepositoryForAgentTool()
        val provider = provider(
            repository = repository,
            botRepositoryPort = FakeBotRepositoryPort(
                listOf(BotProfile(id = "bot-other", configProfileId = "config-other")),
            ),
        )

        val result = provider.invoke(
            createRequest(payload = validCreatePayload() + ("bot_id" to "bot-other")),
        ).result

        assertEquals(PluginToolResultStatus.ERROR, result.status)
        assertEquals("missing_target_context", result.errorCode)
        assertTrue(repository.rulesSnapshot.isEmpty())
    }

    @Test
    fun create_when_config_binding_fails_returns_error_and_removes_created_rule() = runBlocking {
        val repository = InMemoryGeofenceRuleRepositoryForAgentTool(failConfigBinding = true)
        val provider = provider(repository = repository)

        val result = provider.invoke(createRequest(payload = validCreatePayload())).result

        assertEquals(PluginToolResultStatus.ERROR, result.status)
        assertEquals("config_binding_failed", result.errorCode)
        assertTrue(repository.rulesSnapshot.isEmpty())
    }

    @Test
    fun create_when_reconciliation_fails_does_not_report_success() = runBlocking {
        val provider = provider(
            reconciliationPort = RecordingGeofenceReconciliationPort(
                status = GeofenceRegistrationStatus.REGISTRATION_FAILED,
            ),
        )

        val result = provider.invoke(createRequest(payload = validCreatePayload())).result

        assertEquals(PluginToolResultStatus.ERROR, result.status)
        assertEquals("reconciliation_failed", result.errorCode)
    }

    @Test
    fun create_with_background_permission_gap_saves_permission_required_rule() = runBlocking {
        val repository = InMemoryGeofenceRuleRepositoryForAgentTool()
        val provider = provider(
            repository = repository,
            permission = GeofencePermissionStatus(foregroundGranted = true, backgroundGranted = false),
        )

        val result = provider.invoke(createRequest(payload = validCreatePayload())).result

        assertEquals(PluginToolResultStatus.SUCCESS, result.status)
        assertEquals(GeofenceRuleStatus.PERMISSION_REQUIRED, repository.rulesSnapshot.single().status)
        assertTrue(result.text.orEmpty().contains("permission_required"))
    }

    @Test
    fun geofence_triggered_turn_hides_mutating_geofence_tools() = runBlocking {
        val provider = provider()

        val names = provider.listBindings(
            ToolSourceRegistryIngestContext(
                userToolContext(ingressTrigger = IngressTrigger.GEOFENCE_EVENT),
            ),
        ).map { it.descriptor.name }

        assertTrue(names.contains("list_geofence_rules"))
        assertFalse(names.contains("create_geofence_rule"))
        assertFalse(names.contains("update_geofence_rule"))
        assertFalse(names.contains("delete_geofence_rule"))
        assertFalse(names.contains("pause_geofence_rule"))
        assertFalse(names.contains("resume_geofence_rule"))
    }

    @Test
    fun update_with_invalid_config_profile_id_returns_missing_target_context() = runBlocking {
        val repository = InMemoryGeofenceRuleRepositoryForAgentTool().apply { seedRule() }
        val provider = provider(repository = repository)

        val result = provider.invoke(
            updateRequest(
                payload = mapOf(
                    "rule_id" to "rule-1",
                    "config_profile_id" to "missing-config",
                ),
            ),
        ).result

        assertEquals(PluginToolResultStatus.ERROR, result.status)
        assertEquals("missing_target_context", result.errorCode)
    }

    @Test
    fun update_with_invalid_target_platform_returns_invalid_target_platform() = runBlocking {
        val repository = InMemoryGeofenceRuleRepositoryForAgentTool().apply { seedRule() }
        val provider = provider(repository = repository)

        val result = provider.invoke(
            updateRequest(payload = mapOf("rule_id" to "rule-1", "target_platform" to "sms")),
        ).result

        assertEquals(PluginToolResultStatus.ERROR, result.status)
        assertEquals("invalid_target_platform", result.errorCode)
    }

    @Test
    fun update_with_bot_from_other_config_returns_missing_target_context() = runBlocking {
        val repository = InMemoryGeofenceRuleRepositoryForAgentTool().apply { seedRule() }
        val provider = provider(
            repository = repository,
            botRepositoryPort = FakeBotRepositoryPort(
                listOf(BotProfile(id = "bot-other", configProfileId = "config-other")),
            ),
        )

        val result = provider.invoke(
            updateRequest(payload = mapOf("rule_id" to "rule-1", "bot_id" to "bot-other")),
        ).result

        assertEquals(PluginToolResultStatus.ERROR, result.status)
        assertEquals("missing_target_context", result.errorCode)
    }

    @Test
    fun update_using_current_location_without_permission_returns_permission_required() = runBlocking {
        val repository = InMemoryGeofenceRuleRepositoryForAgentTool().apply { seedRule() }
        val provider = provider(
            repository = repository,
            permission = GeofencePermissionStatus(foregroundGranted = false, backgroundGranted = true),
        )

        val result = provider.invoke(
            updateRequest(payload = mapOf("rule_id" to "rule-1", "use_current_location" to true)),
        ).result

        assertEquals(PluginToolResultStatus.ERROR, result.status)
        assertEquals("permission_required", result.errorCode)
    }

    @Test
    fun update_using_current_location_without_trusted_metadata_returns_missing_location() = runBlocking {
        val repository = InMemoryGeofenceRuleRepositoryForAgentTool().apply { seedRule() }
        val provider = provider(repository = repository)

        val result = provider.invoke(
            updateRequest(payload = mapOf("rule_id" to "rule-1", "use_current_location" to true)),
        ).result

        assertEquals(PluginToolResultStatus.ERROR, result.status)
        assertEquals("missing_location", result.errorCode)
    }

    @Test
    fun management_tools_update_list_delete_pause_resume_and_reconcile_mutations() = runBlocking {
        val repository = InMemoryGeofenceRuleRepositoryForAgentTool().apply { seedRule() }
        val reconciliation = RecordingGeofenceReconciliationPort()
        val provider = provider(repository = repository, reconciliationPort = reconciliation)

        val listResult = provider.invoke(geofenceRequest("list_geofence_rules")).result
        assertEquals(PluginToolResultStatus.SUCCESS, listResult.status)
        assertTrue(listResult.text.orEmpty().contains("\"count\": 1"))

        assertEquals(PluginToolResultStatus.SUCCESS, provider.invoke(geofenceRequest("pause_geofence_rule", mapOf("rule_id" to "rule-1"))).result.status)
        assertEquals(GeofenceRuleStatus.PAUSED, repository.rulesSnapshot.single().status)
        assertEquals(1, reconciliation.reconcileCount)

        assertEquals(PluginToolResultStatus.SUCCESS, provider.invoke(geofenceRequest("resume_geofence_rule", mapOf("rule_id" to "rule-1"))).result.status)
        assertEquals(GeofenceRuleStatus.ACTIVE, repository.rulesSnapshot.single().status)
        assertEquals(2, reconciliation.reconcileCount)

        assertEquals(
            PluginToolResultStatus.SUCCESS,
            provider.invoke(geofenceRequest("update_geofence_rule", mapOf("rule_id" to "rule-1", "name" to "Updated name"))).result.status,
        )
        assertEquals("Updated name", repository.rulesSnapshot.single().name)
        assertEquals(3, reconciliation.reconcileCount)

        assertEquals(PluginToolResultStatus.SUCCESS, provider.invoke(geofenceRequest("delete_geofence_rule", mapOf("rule_id" to "rule-1"))).result.status)
        assertTrue(repository.rulesSnapshot.isEmpty())
        assertEquals(4, reconciliation.reconcileCount)
    }

    @Test
    fun invoke_mutating_geofence_tool_during_geofence_event_returns_hidden_error() = runBlocking {
        val repository = InMemoryGeofenceRuleRepositoryForAgentTool().apply { seedRule() }
        val provider = provider(repository = repository)

        val result = provider.invoke(
            geofenceRequest(
                toolName = "delete_geofence_rule",
                payload = mapOf("rule_id" to "rule-1"),
                toolSourceContext = userToolContext(ingressTrigger = IngressTrigger.GEOFENCE_EVENT),
            ),
        ).result

        assertEquals(PluginToolResultStatus.ERROR, result.status)
        assertEquals("geofence_tools_hidden_during_geofence_event", result.errorCode)
        assertEquals(1, repository.rulesSnapshot.size)
    }

    @Test
    fun availability_rejects_mutating_geofence_tool_during_geofence_event() = runBlocking {
        val provider = provider()
        val identity = ToolSourceIdentity(
            sourceKind = PluginToolSourceKind.ACTIVE_CAPABILITY,
            ownerId = "cap.geofence",
            sourceRef = "delete_geofence_rule",
            displayName = "delete_geofence_rule",
        )

        val availability = provider.availabilityOf(
            identity,
            ToolSourceAvailabilityContext(userToolContext(ingressTrigger = IngressTrigger.GEOFENCE_EVENT)),
        )

        assertEquals(false, availability.capabilityAllowed)
        assertEquals("geofence_tools_hidden_during_geofence_event", availability.detailCode)
    }

    private fun provider(
        repository: InMemoryGeofenceRuleRepositoryForAgentTool = InMemoryGeofenceRuleRepositoryForAgentTool(),
        permission: GeofencePermissionStatus = GeofencePermissionStatus(foregroundGranted = true, backgroundGranted = true),
        reconciliationPort: RecordingGeofenceReconciliationPort = RecordingGeofenceReconciliationPort(),
        configRepositoryPort: ConfigRepositoryPort = FakeConfigRepositoryPort(),
        botRepositoryPort: BotRepositoryPort = FakeBotRepositoryPort(),
    ): ActiveCapabilityToolSourceProvider {
        val geofenceFacade = GeofenceActiveCapabilityFacadeAdapter(
            repository = repository,
            configRepositoryPort = configRepositoryPort,
            botRepositoryPort = botRepositoryPort,
            permissionStatusPort = StaticGeofencePermissionStatusPort(permission),
            reconciliationPort = reconciliationPort,
        )
        return ActiveCapabilityToolSourceProvider(
            facade = ActiveCapabilityRuntimeFacade(
                repository = EmptyCronRepositoryForGeofenceTool(),
                scheduler = EmptyCronSchedulerForGeofenceTool,
                promptStrings = EmptyActiveCapabilityPromptStrings(),
            ),
            geofenceFacade = geofenceFacade,
            promptStrings = EmptyActiveCapabilityPromptStrings(),
            contextResolver = noopContextResolver,
            runtimeLogger = RuntimeLogger.noop(),
        )
    }

    private fun createRequest(
        payload: Map<String, Any?>,
        metadata: Map<String, Any?>? = null,
    ): ToolSourceInvokeRequest =
        geofenceRequest(
            toolName = "create_geofence_rule",
            payload = payload,
            metadata = metadata,
        )

    private fun updateRequest(
        payload: Map<String, Any?>,
        metadata: Map<String, Any?>? = null,
    ): ToolSourceInvokeRequest =
        geofenceRequest(
            toolName = "update_geofence_rule",
            payload = payload,
            metadata = metadata,
        )

    private fun geofenceRequest(
        toolName: String,
        payload: Map<String, Any?> = emptyMap(),
        metadata: Map<String, Any?>? = null,
        toolSourceContext: ToolSourceContext = userToolContext(),
    ): ToolSourceInvokeRequest =
        ToolSourceInvokeRequest(
            identity = ToolSourceIdentity(
                sourceKind = PluginToolSourceKind.ACTIVE_CAPABILITY,
                ownerId = "cap.geofence",
                sourceRef = toolName,
                displayName = toolName,
            ),
            args = PluginToolArgs(
                toolCallId = "call-1",
                requestId = "req-1",
                toolId = "cap.geofence:$toolName",
                payload = payload,
                metadata = metadata,
            ),
            timeoutMs = 1_000L,
            toolSourceContext = toolSourceContext,
        )

    private fun validCreatePayload(): Map<String, Any?> =
        mapOf(
            "name" to "Office reminder",
            "latitude" to 31.2304,
            "longitude" to 121.4737,
            "radius_meters" to 150,
            "trigger" to "enter",
            "action_type" to "agent_prompt",
            "action_prompt" to "Remind me",
            "conversation_id" to "conversation-1",
            "minimum_trigger_interval_millis" to 60000,
        )

    private fun hostCurrentLocationMetadata(latitude: Double, longitude: Double): Map<String, Any?> =
        mapOf(
            "__host" to mapOf(
                "current_location" to mapOf(
                    "latitude" to latitude,
                    "longitude" to longitude,
                    "radius_meters" to 75,
                ),
            ),
        )
}

private fun userToolContext(
    ingressTrigger: IngressTrigger = IngressTrigger.USER_MESSAGE,
): ToolSourceContext =
    ToolSourceContext(
        requestId = "req-1",
        platform = RuntimePlatform.APP_CHAT,
        configProfileId = "config-1",
        webSearchEnabled = true,
        activeCapabilityEnabled = true,
        mcpServers = emptyList(),
        promptSkills = emptyList(),
        toolSkills = emptyList(),
        conversationId = "conversation-1",
        ingressTrigger = ingressTrigger,
    )

private val noopContextResolver = object : FutureToolSourceContextResolver {
    override fun resolveForConfig(configProfileId: String): ToolSourceContext = userToolContext()
}

private class InMemoryGeofenceRuleRepositoryForAgentTool(
    private val failConfigBinding: Boolean = false,
) : GeofenceRuleRepositoryPort {
    private val mutableRules = linkedMapOf<String, GeofenceRule>()
    private val mutableBindings = mutableListOf<ConfigGeofenceBinding>()
    override val rules: StateFlow<List<GeofenceRule>> = MutableStateFlow(emptyList())
    override val bindings: StateFlow<List<ConfigGeofenceBinding>> = MutableStateFlow(emptyList())
    val rulesSnapshot: List<GeofenceRule> get() = mutableRules.values.toList()

    fun seedRule() {
        val region = GeofenceRegion(
            regionId = "region-1",
            ruleId = "rule-1",
            label = "Office",
            latitude = 31.2304,
            longitude = 121.4737,
            radiusMeters = 100f,
        )
        val rule = GeofenceRule(
            ruleId = "rule-1",
            name = "Office reminder",
            enabled = true,
            triggerEnter = true,
            actionPrompt = "Remind me",
            targetPlatform = RuntimePlatform.APP_CHAT.wireValue,
            targetConversationId = "conversation-1",
            targetConfigProfileId = "config-1",
            status = GeofenceRuleStatus.ACTIVE,
            regions = listOf(region),
        )
        mutableRules[rule.ruleId] = rule
        (rules as MutableStateFlow).value = rulesSnapshot
    }

    override suspend fun createRule(rule: GeofenceRule, regions: List<GeofenceRegion>): GeofenceRule {
        val created = rule.copy(regions = regions)
        mutableRules[created.ruleId] = created
        (rules as MutableStateFlow).value = rulesSnapshot
        return created
    }

    override suspend fun updateRule(rule: GeofenceRule): GeofenceRule {
        mutableRules[rule.ruleId] = rule.copy(regions = mutableRules[rule.ruleId]?.regions.orEmpty())
        (rules as MutableStateFlow).value = rulesSnapshot
        return mutableRules.getValue(rule.ruleId)
    }

    override suspend fun deleteRule(ruleId: String) {
        mutableRules.remove(ruleId)
        (rules as MutableStateFlow).value = rulesSnapshot
    }

    override suspend fun pauseRule(ruleId: String): GeofenceRule? =
        mutableRules[ruleId]?.copy(enabled = false, status = GeofenceRuleStatus.PAUSED)?.also { updateRule(it) }

    override suspend fun resumeRule(ruleId: String): GeofenceRule? =
        mutableRules[ruleId]?.copy(enabled = true, status = GeofenceRuleStatus.ACTIVE)?.also { updateRule(it) }

    override suspend fun getRule(ruleId: String): GeofenceRule? = mutableRules[ruleId]
    override suspend fun listRules(): List<GeofenceRule> = rulesSnapshot
    override suspend fun replaceRegions(ruleId: String, regions: List<GeofenceRegion>): GeofenceRule? {
        val existing = mutableRules[ruleId] ?: return null
        val updated = existing.copy(regions = regions)
        mutableRules[ruleId] = updated
        return updated
    }
    override suspend fun upsertRegion(region: GeofenceRegion): GeofenceRegion = region
    override suspend fun deleteRegion(regionId: String) = Unit
    override suspend fun listAllConfigBindings(): List<ConfigGeofenceBinding> = mutableBindings.toList()
    override suspend fun listConfigBindings(configId: String): List<ConfigGeofenceBinding> =
        mutableBindings.filter { it.configId == configId }
    override suspend fun upsertConfigBinding(binding: ConfigGeofenceBinding): ConfigGeofenceBinding {
        if (failConfigBinding) {
            throw IllegalStateException("binding failure")
        }
        mutableBindings.removeAll { it.configId == binding.configId && it.ruleId == binding.ruleId }
        mutableBindings += binding
        return binding
    }
    override suspend fun deleteConfigBinding(configId: String, ruleId: String) = Unit
    override suspend fun recordExecution(record: GeofenceExecutionRecord): GeofenceExecutionRecord = record
    override suspend fun listRecentExecutionRecords(ruleId: String, limit: Int): List<GeofenceExecutionRecord> = emptyList()
    override suspend fun latestExecutionRecord(ruleId: String): GeofenceExecutionRecord? = null
}

private class StaticGeofencePermissionStatusPort(
    private val status: GeofencePermissionStatus,
) : GeofencePermissionStatusPort {
    override fun currentStatus(): GeofencePermissionStatus = status
}

private class RecordingGeofenceReconciliationPort(
    private val status: GeofenceRegistrationStatus = GeofenceRegistrationStatus.REGISTERED,
    private val throwOnReconcile: Boolean = false,
) : GeofenceRuntimeReconciliationPort {
    var reconcileCount: Int = 0
        private set

    override fun reconcileAsync(scope: CoroutineScope) = Unit
    override suspend fun reconcileNow(): GeofenceRegistrationSummary {
        if (throwOnReconcile) {
            throw IllegalStateException("reconciliation failure")
        }
        reconcileCount += 1
        return GeofenceRegistrationSummary(status = status)
    }
}

private class FakeBotRepositoryPort(
    private val configs: List<BotProfile> = listOf(BotProfile(id = "bot-1", configProfileId = "config-1")),
) : BotRepositoryPort {
    override val bots: StateFlow<List<BotProfile>> = MutableStateFlow(configs)
    override val selectedBotId: StateFlow<String> = MutableStateFlow(configs.firstOrNull()?.id.orEmpty())
    override fun currentBot(): BotProfile = configs.firstOrNull() ?: BotProfile(id = "fallback")
    override fun snapshotProfiles(): List<BotProfile> = configs
    override fun create(name: String): BotProfile = BotProfile(id = "created", displayName = name)
    override suspend fun create(profile: BotProfile) = Unit
    override suspend fun save(profile: BotProfile) = Unit
    override suspend fun delete(id: String) = Unit
    override suspend fun select(id: String) = Unit
}

private class FakeConfigRepositoryPort(
    private val configs: List<ConfigProfile> = listOf(ConfigProfile(id = "config-1")),
) : ConfigRepositoryPort {
    override val profiles: StateFlow<List<ConfigProfile>> = MutableStateFlow(configs)
    override val selectedProfileId: StateFlow<String> = MutableStateFlow(configs.firstOrNull()?.id.orEmpty())
    override fun snapshotProfiles(): List<ConfigProfile> = configs
    override fun create(name: String): ConfigProfile = ConfigProfile(id = "created", name = name)
    override fun resolve(id: String): ConfigProfile = configs.firstOrNull() ?: ConfigProfile(id = "fallback")
    override fun resolveExistingId(id: String?): String = configs.firstOrNull { it.id == id }?.id ?: configs.firstOrNull()?.id.orEmpty()
    override suspend fun save(profile: ConfigProfile) = Unit
    override suspend fun delete(id: String) = Unit
    override suspend fun select(id: String) = Unit
}

private class EmptyCronRepositoryForGeofenceTool : CronJobRepositoryPort {
    override val jobs: StateFlow<List<CronJob>> = MutableStateFlow(emptyList())
    override suspend fun create(job: CronJob): CronJob = job
    override suspend fun update(job: CronJob): CronJob = job
    override suspend fun delete(jobId: String) = Unit
    override suspend fun getByJobId(jobId: String): CronJob? = null
    override suspend fun listAll(): List<CronJob> = emptyList()
    override suspend fun listEnabled(): List<CronJob> = emptyList()
    override suspend fun updateStatus(jobId: String, status: String, lastRunAt: Long?, lastError: String?) = Unit
    override suspend fun recordExecutionStarted(record: CronJobExecutionRecord): CronJobExecutionRecord = record
    override suspend fun updateExecutionRecord(record: CronJobExecutionRecord): CronJobExecutionRecord = record
    override suspend fun listRecentExecutionRecords(jobId: String, limit: Int): List<CronJobExecutionRecord> = emptyList()
    override suspend fun latestExecutionRecord(jobId: String): CronJobExecutionRecord? = null
}

private object EmptyCronSchedulerForGeofenceTool : CronSchedulerPort {
    override fun schedule(job: CronJob) = Unit
    override fun cancel(jobId: String) = Unit
    override fun cancelAll() = Unit
}
