package com.elymbot.android.feature.geofence.runtime

import com.elymbot.android.core.common.logging.RuntimeLogger
import com.elymbot.android.feature.geofence.domain.GeofenceRuleRepositoryPort
import com.elymbot.android.feature.geofence.domain.model.ConfigGeofenceBinding
import com.elymbot.android.feature.geofence.domain.model.GeofenceRegion
import com.elymbot.android.feature.geofence.domain.model.GeofenceRule
import com.elymbot.android.feature.geofence.domain.model.GeofenceRuleStatus
import com.elymbot.android.feature.geofence.domain.model.GeofenceRuleValidation
import com.elymbot.android.feature.geofence.domain.runtime.GeofencePermissionStatus
import com.elymbot.android.feature.geofence.domain.runtime.GeofencePermissionStatusPort
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceRegistrationPort
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceRegistrationStatus
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceRegistrationSummary
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceRuntimeReconciliationPort
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class GeofenceRegistrationRegion(
    val rule: GeofenceRule,
    val region: GeofenceRegion,
)

internal data class GeofenceRegistrationRequest(
    val regions: List<GeofenceRegistrationRegion>,
)

internal interface GeofenceRegistrationBackend {
    suspend fun replaceRegisteredGeofences(request: GeofenceRegistrationRequest)
}

internal class GeofenceRegistrationCoordinator(
    private val repository: GeofenceRuleRepositoryPort,
    private val permissionStatusPort: () -> GeofencePermissionStatus,
    private val playServicesAvailability: () -> Boolean,
    private val backend: GeofenceRegistrationBackend,
    private val clock: () -> Long = System::currentTimeMillis,
    private val runtimeLogger: RuntimeLogger = RuntimeLogger.noop(),
) {
    private val registrationMutex = Mutex()

    @Inject
    constructor(
        repository: GeofenceRuleRepositoryPort,
        permissionStatusPort: GeofencePermissionStatusPort,
        playServicesAvailability: GeofencePlayServicesAvailability,
        backend: GeofenceRegistrationBackend,
        runtimeLogger: RuntimeLogger,
    ) : this(
        repository = repository,
        permissionStatusPort = permissionStatusPort::currentStatus,
        playServicesAvailability = playServicesAvailability::isAvailable,
        backend = backend,
        runtimeLogger = runtimeLogger,
    )

    suspend fun registerActiveGeofences(): GeofenceRegistrationSummary = registrationMutex.withLock {
        withContext(Dispatchers.IO) {
            registerActiveGeofencesLocked()
        }
    }

    private suspend fun registerActiveGeofencesLocked(): GeofenceRegistrationSummary {
        val activeRules = repository.listRules()
            .filter { rule -> rule.enabled && rule.status != GeofenceRuleStatus.PAUSED }
        val enabledBindingsByRule = repository.listAllConfigBindings()
            .filter(ConfigGeofenceBinding::enabled)
            .groupBy(ConfigGeofenceBinding::ruleId)
        val boundRules = activeRules.filter { rule -> enabledBindingsByRule.containsKey(rule.ruleId) }
        val activeRegions = boundRules.flatMap { rule ->
            rule.regions.map { region -> GeofenceRegistrationRegion(rule = rule, region = region) }
        }

        if (activeRegions.isEmpty()) {
            clearRegisteredGeofences(reason = "no_active_regions")
            return GeofenceRegistrationSummary(
                status = GeofenceRegistrationStatus.NO_ACTIVE_REGIONS,
                activeRuleCount = boundRules.size,
                activeRegionCount = 0,
            )
        }

        val affectedRuleIds = boundRules.map { it.ruleId }.distinct()
        val permissionStatus = permissionStatusPort()
        if (!permissionStatus.canRegister) {
            clearRegisteredGeofences(reason = "permission_required")
            markRules(
                rules = boundRules,
                status = GeofenceRuleStatus.PERMISSION_REQUIRED,
                error = "location_permission_required",
            )
            return GeofenceRegistrationSummary(
                status = GeofenceRegistrationStatus.PERMISSION_REQUIRED,
                activeRuleCount = boundRules.size,
                activeRegionCount = activeRegions.size,
                affectedRuleIds = affectedRuleIds,
            )
        }

        if (!playServicesAvailability()) {
            clearRegisteredGeofences(reason = "play_services_unavailable")
            markRules(
                rules = boundRules,
                status = GeofenceRuleStatus.PLAY_SERVICES_UNAVAILABLE,
                error = "play_services_unavailable",
            )
            return GeofenceRegistrationSummary(
                status = GeofenceRegistrationStatus.PLAY_SERVICES_UNAVAILABLE,
                activeRuleCount = boundRules.size,
                activeRegionCount = activeRegions.size,
                affectedRuleIds = affectedRuleIds,
            )
        }

        if (activeRegions.size > GeofenceRuleValidation.MAX_ACTIVE_GEOFENCE_REGIONS) {
            clearRegisteredGeofences(reason = "capacity_exceeded")
            markRules(
                rules = boundRules,
                status = GeofenceRuleStatus.CAPACITY_EXCEEDED,
                error = "capacity_exceeded",
            )
            return GeofenceRegistrationSummary(
                status = GeofenceRegistrationStatus.CAPACITY_EXCEEDED,
                activeRuleCount = boundRules.size,
                activeRegionCount = activeRegions.size,
                affectedRuleIds = affectedRuleIds,
            )
        }

        try {
            backend.replaceRegisteredGeofences(GeofenceRegistrationRequest(activeRegions))
        // skipcq: KT-W1009
        } catch (error: Throwable) {
            markRules(
                rules = boundRules,
                status = GeofenceRuleStatus.REGISTRATION_FAILED,
                error = error.message ?: error.javaClass.simpleName,
            )
            runtimeLogger.append("Geofence registration failed for ${boundRules.size} rule(s): ${error.javaClass.simpleName}")
            return GeofenceRegistrationSummary(
                status = GeofenceRegistrationStatus.REGISTRATION_FAILED,
                activeRuleCount = boundRules.size,
                activeRegionCount = activeRegions.size,
                affectedRuleIds = affectedRuleIds,
                // skipcq: KT-R1004
                errorMessage = error.message ?: "",
            )
        }

        val registeredAt = clock()
        boundRules.forEach { rule ->
            repository.updateRule(
                rule.copy(
                    status = GeofenceRuleStatus.ACTIVE,
                    lastRegisteredAt = registeredAt,
                    lastError = "",
                ),
            )
        }
        runtimeLogger.append("Geofence registration reconciled ${activeRegions.size} active region(s)")
        return GeofenceRegistrationSummary(
            status = GeofenceRegistrationStatus.REGISTERED,
            activeRuleCount = boundRules.size,
            activeRegionCount = activeRegions.size,
            affectedRuleIds = affectedRuleIds,
        )
    }

    private suspend fun clearRegisteredGeofences(reason: String) {
        runCatching {
            backend.replaceRegisteredGeofences(GeofenceRegistrationRequest(emptyList()))
        }.onFailure { error ->
            runtimeLogger.append(
                "Geofence registration clear failed reason=$reason error=${error.javaClass.simpleName}",
            )
        }
    }

    private suspend fun markRules(
        rules: List<GeofenceRule>,
        status: GeofenceRuleStatus,
        error: String,
    ) {
        rules.forEach { rule ->
            repository.updateRule(rule.copy(status = status, lastError = error))
        }
    }
}

internal class HiltGeofenceRegistrationPort @Inject constructor(
    private val coordinator: GeofenceRegistrationCoordinator,
) : GeofenceRegistrationPort {
    override suspend fun registerActiveGeofences(): GeofenceRegistrationSummary =
        coordinator.registerActiveGeofences()
}

internal class HiltGeofenceRuntimeReconciliationPort @Inject constructor(
    private val coordinator: GeofenceRegistrationCoordinator,
    private val runtimeLogger: RuntimeLogger,
) : GeofenceRuntimeReconciliationPort {
    override fun reconcileAsync(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            reconcileNow()
        }
    }

    override suspend fun reconcileNow(): GeofenceRegistrationSummary {
        val summary = coordinator.registerActiveGeofences()
        runtimeLogger.append(
            "Geofence reconciliation status=${summary.status} activeRegions=${summary.activeRegionCount}",
        )
        return summary
    }
}
