package com.elymbot.android.feature.geofence.runtime

import com.elymbot.android.feature.geofence.domain.GeofenceRuleRepositoryPort
import com.elymbot.android.feature.geofence.domain.model.ConfigGeofenceBinding
import com.elymbot.android.feature.geofence.domain.model.GeofenceActionType
import com.elymbot.android.feature.geofence.domain.model.GeofenceExecutionRecord
import com.elymbot.android.feature.geofence.domain.model.GeofenceRegion
import com.elymbot.android.feature.geofence.domain.model.GeofenceRule
import com.elymbot.android.feature.geofence.domain.model.GeofenceRuleStatus
import com.elymbot.android.feature.geofence.domain.runtime.GeofencePermissionStatus
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceRegistrationStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class GeofenceRuntimeRegistrationTest {
    @Test
    fun enabled_bound_region_is_registered() = runBlocking {
        val repository = FakeGeofenceRuleRepository(
            rules = listOf(rule(regions = listOf(region()))),
            bindings = listOf(binding()),
        )
        val backend = RecordingGeofenceRegistrationBackend()
        val coordinator = GeofenceRegistrationCoordinator(
            repository = repository,
            permissionStatusPort = { GeofencePermissionStatus(foregroundGranted = true, backgroundGranted = true) },
            playServicesAvailability = { true },
            backend = backend,
            clock = { 100L },
        )

        val summary = coordinator.registerActiveGeofences()

        assertEquals(GeofenceRegistrationStatus.REGISTERED, summary.status)
        assertEquals(1, backend.requests.single().regions.size)
        // skipcq: KT-W1042
        assertEquals("rule-1", backend.requests.single().regions.single().rule.ruleId)
        assertEquals(GeofenceRuleStatus.ACTIVE, repository.ruleStatus("rule-1"))
    }

    @Test
    fun cold_start_registration_uses_current_bindings_instead_of_stale_flow_snapshot() = runBlocking {
        val repository = FakeGeofenceRuleRepository(
            rules = listOf(rule(regions = listOf(region()))),
            bindings = listOf(binding()),
            observedBindings = emptyList(),
        )
        val backend = RecordingGeofenceRegistrationBackend()
        val coordinator = GeofenceRegistrationCoordinator(
            repository = repository,
            permissionStatusPort = { GeofencePermissionStatus(foregroundGranted = true, backgroundGranted = true) },
            playServicesAvailability = { true },
            backend = backend,
            clock = { 100L },
        )

        val summary = coordinator.registerActiveGeofences()

        assertEquals(GeofenceRegistrationStatus.REGISTERED, summary.status)
        assertEquals(1, backend.requests.single().regions.size)
    }

    @Test
    fun rule_without_enabled_binding_is_not_registered() = runBlocking {
        val repository = FakeGeofenceRuleRepository(rules = listOf(rule(regions = listOf(region()))))
        val backend = RecordingGeofenceRegistrationBackend()
        val coordinator = GeofenceRegistrationCoordinator(
            repository = repository,
            permissionStatusPort = { GeofencePermissionStatus(foregroundGranted = true, backgroundGranted = true) },
            playServicesAvailability = { true },
            backend = backend,
        )

        val summary = coordinator.registerActiveGeofences()

        assertEquals(GeofenceRegistrationStatus.NO_ACTIVE_REGIONS, summary.status)
        assertEquals(0, backend.requests.single().regions.size)
    }

    @Test
    fun permission_gap_marks_bound_rules_permission_required_without_registering() = runBlocking {
        val repository = FakeGeofenceRuleRepository(
            rules = listOf(rule(regions = listOf(region()))),
            bindings = listOf(binding()),
        )
        val backend = RecordingGeofenceRegistrationBackend()
        val coordinator = GeofenceRegistrationCoordinator(
            repository = repository,
            permissionStatusPort = { GeofencePermissionStatus(foregroundGranted = true, backgroundGranted = false) },
            playServicesAvailability = { true },
            backend = backend,
        )

        val summary = coordinator.registerActiveGeofences()

        assertEquals(GeofenceRegistrationStatus.PERMISSION_REQUIRED, summary.status)
        assertEquals(GeofenceRuleStatus.PERMISSION_REQUIRED, repository.ruleStatus("rule-1"))
        assertEquals(1, backend.requests.size)
        assertEquals(0, backend.requests.single().regions.size)
    }

    @Test
    fun capacity_over_one_hundred_marks_rules_capacity_exceeded_without_registering() = runBlocking {
        val regions = (1..101).map { index -> region(regionId = "region-$index") }
        val repository = FakeGeofenceRuleRepository(
            rules = listOf(rule(regions = regions)),
            bindings = listOf(binding()),
        )
        val backend = RecordingGeofenceRegistrationBackend()
        val coordinator = GeofenceRegistrationCoordinator(
            repository = repository,
            permissionStatusPort = { GeofencePermissionStatus(foregroundGranted = true, backgroundGranted = true) },
            playServicesAvailability = { true },
            backend = backend,
        )

        val summary = coordinator.registerActiveGeofences()

        assertEquals(GeofenceRegistrationStatus.CAPACITY_EXCEEDED, summary.status)
        assertEquals(GeofenceRuleStatus.CAPACITY_EXCEEDED, repository.ruleStatus("rule-1"))
        assertEquals(1, backend.requests.size)
        assertEquals(0, backend.requests.single().regions.size)
    }

    @Test
    fun play_services_unavailable_marks_bound_rules_without_registering() = runBlocking {
        val repository = FakeGeofenceRuleRepository(
            rules = listOf(rule(regions = listOf(region()))),
            bindings = listOf(binding()),
        )
        val backend = RecordingGeofenceRegistrationBackend()
        val coordinator = GeofenceRegistrationCoordinator(
            repository = repository,
            permissionStatusPort = { GeofencePermissionStatus(foregroundGranted = true, backgroundGranted = true) },
            playServicesAvailability = { false },
            backend = backend,
        )

        val summary = coordinator.registerActiveGeofences()

        assertEquals(GeofenceRegistrationStatus.PLAY_SERVICES_UNAVAILABLE, summary.status)
        assertEquals(GeofenceRuleStatus.PLAY_SERVICES_UNAVAILABLE, repository.ruleStatus("rule-1"))
        assertEquals(1, backend.requests.size)
        assertEquals(0, backend.requests.single().regions.size)
    }

    @Test
    fun concurrent_registration_reconciliation_does_not_let_stale_snapshot_win() = runBlocking {
        val repository = FakeGeofenceRuleRepository(
            rules = listOf(rule(regions = listOf(region()))),
            bindings = listOf(binding()),
        )
        val backend = BlockingFirstActiveRegistrationBackend()
        val coordinator = GeofenceRegistrationCoordinator(
            repository = repository,
            permissionStatusPort = { GeofencePermissionStatus(foregroundGranted = true, backgroundGranted = true) },
            playServicesAvailability = { true },
            backend = backend,
            clock = { 100L },
        )

        val staleRegistration = async { coordinator.registerActiveGeofences() }
        backend.firstActiveRegistrationEntered.await()
        repository.upsertConfigBinding(binding(enabled = false))
        val currentRegistration = async { coordinator.registerActiveGeofences() }
        backend.releaseFirstActiveRegistration.complete(Unit)
        staleRegistration.await()
        val currentSummary = currentRegistration.await()

        assertEquals(GeofenceRegistrationStatus.NO_ACTIVE_REGIONS, currentSummary.status)
        assertEquals(0, backend.requests.last().regions.size)
    }
}

internal class RecordingGeofenceRegistrationBackend : GeofenceRegistrationBackend {
    val requests = mutableListOf<GeofenceRegistrationRequest>()

    override suspend fun replaceRegisteredGeofences(request: GeofenceRegistrationRequest) {
        requests += request
    }
}

private class BlockingFirstActiveRegistrationBackend : GeofenceRegistrationBackend {
    val firstActiveRegistrationEntered = CompletableDeferred<Unit>()
    val releaseFirstActiveRegistration = CompletableDeferred<Unit>()
    val requests = mutableListOf<GeofenceRegistrationRequest>()
    private var blockedFirstActiveRegistration = false

    override suspend fun replaceRegisteredGeofences(request: GeofenceRegistrationRequest) {
        if (!blockedFirstActiveRegistration && request.regions.isNotEmpty()) {
            blockedFirstActiveRegistration = true
            firstActiveRegistrationEntered.complete(Unit)
            releaseFirstActiveRegistration.await()
        }
        requests += request
    }
}

internal class FakeGeofenceRuleRepository(
    rules: List<GeofenceRule> = emptyList(),
    bindings: List<ConfigGeofenceBinding> = emptyList(),
    observedBindings: List<ConfigGeofenceBinding> = bindings,
) : GeofenceRuleRepositoryPort {
    private val mutableRules = rules.associateBy { it.ruleId }.toMutableMap()
    private val mutableBindings = bindings.toMutableList()
    override val rules: StateFlow<List<GeofenceRule>> = MutableStateFlow(rules)
    override val bindings: StateFlow<List<ConfigGeofenceBinding>> = MutableStateFlow(observedBindings)
    val records = mutableListOf<GeofenceExecutionRecord>()

    override suspend fun createRule(rule: GeofenceRule, regions: List<GeofenceRegion>): GeofenceRule = rule
    override suspend fun updateRule(rule: GeofenceRule): GeofenceRule {
        mutableRules[rule.ruleId] = rule
        (this.rules as MutableStateFlow).value = mutableRules.values.toList()
        return rule
    }
    override suspend fun deleteRule(ruleId: String) = Unit
    override suspend fun pauseRule(ruleId: String): GeofenceRule? = null
    override suspend fun resumeRule(ruleId: String): GeofenceRule? = null
    override suspend fun getRule(ruleId: String): GeofenceRule? = mutableRules[ruleId]
    override suspend fun listRules(): List<GeofenceRule> = mutableRules.values.toList()
    override suspend fun replaceRegions(ruleId: String, regions: List<GeofenceRegion>): GeofenceRule? = null
    override suspend fun upsertRegion(region: GeofenceRegion): GeofenceRegion = region
    override suspend fun deleteRegion(regionId: String) = Unit
    override suspend fun listAllConfigBindings(): List<ConfigGeofenceBinding> =
        mutableBindings.toList()
    override suspend fun listConfigBindings(configId: String): List<ConfigGeofenceBinding> =
        mutableBindings.filter { it.configId == configId }
    override suspend fun upsertConfigBinding(binding: ConfigGeofenceBinding): ConfigGeofenceBinding {
        mutableBindings.removeAll { it.configId == binding.configId && it.ruleId == binding.ruleId }
        mutableBindings += binding
        return binding
    }
    override suspend fun deleteConfigBinding(configId: String, ruleId: String) = Unit
    override suspend fun recordExecution(record: GeofenceExecutionRecord): GeofenceExecutionRecord {
        records += record
        return record
    }
    override suspend fun listRecentExecutionRecords(ruleId: String, limit: Int): List<GeofenceExecutionRecord> =
        records.filter { it.ruleId == ruleId }.sortedByDescending { it.startedAt }.take(limit)
    override suspend fun latestExecutionRecord(ruleId: String): GeofenceExecutionRecord? =
        listRecentExecutionRecords(ruleId, 1).firstOrNull()

    fun ruleStatus(ruleId: String): GeofenceRuleStatus? = mutableRules[ruleId]?.status

    fun ruleSnapshot(ruleId: String): GeofenceRule? = mutableRules[ruleId]
}

internal fun rule(
    ruleId: String = "rule-1",
    regions: List<GeofenceRegion> = emptyList(),
    minimumTriggerIntervalMillis: Long = 0L,
): GeofenceRule =
    GeofenceRule(
        ruleId = ruleId,
        name = "Office reminder",
        triggerEnter = true,
        triggerExit = true,
        actionType = GeofenceActionType.AGENT_PROMPT,
        actionPrompt = "Remind me",
        minimumTriggerIntervalMillis = minimumTriggerIntervalMillis,
        regions = regions,
    )

internal fun region(
    regionId: String = "region-1",
    ruleId: String = "rule-1",
): GeofenceRegion =
    GeofenceRegion(
        regionId = regionId,
        ruleId = ruleId,
        label = regionId,
        latitude = 31.2304,
        longitude = 121.4737,
        radiusMeters = 100f,
    )

internal fun binding(
    configId: String = "config-1",
    ruleId: String = "rule-1",
    enabled: Boolean = true,
): ConfigGeofenceBinding =
    ConfigGeofenceBinding(configId = configId, ruleId = ruleId, enabled = enabled)
