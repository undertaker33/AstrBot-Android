package com.elymbot.android.app.integration.geofence

import com.elymbot.android.core.runtime.context.RuntimePlatform
import com.elymbot.android.core.runtime.context.ToolSourceContext
import com.elymbot.android.feature.bot.domain.BotRepositoryPort
import com.elymbot.android.feature.bot.domain.model.BotProfile
import com.elymbot.android.feature.config.domain.ConfigRepositoryPort
import com.elymbot.android.feature.config.domain.model.ConfigProfile
import com.elymbot.android.feature.geofence.domain.GeofenceRuleRepositoryPort
import com.elymbot.android.feature.geofence.domain.model.ConfigGeofenceBinding
import com.elymbot.android.feature.geofence.domain.model.GeofenceActionType
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceRegistrationStatus
import com.elymbot.android.feature.geofence.domain.model.GeofenceRegion
import com.elymbot.android.feature.geofence.domain.model.GeofenceRule
import com.elymbot.android.feature.geofence.domain.model.GeofenceRuleStatus
import com.elymbot.android.feature.geofence.domain.runtime.GeofencePermissionStatus
import com.elymbot.android.feature.geofence.domain.runtime.GeofencePermissionStatusPort
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceRuntimeReconciliationPort
import com.elymbot.android.feature.plugin.domain.runtime.GeofenceActiveCapabilityFacade
import java.util.UUID
import javax.inject.Inject
import org.json.JSONArray
import org.json.JSONObject

class GeofenceActiveCapabilityFacadeAdapter @Inject constructor(
    private val repository: GeofenceRuleRepositoryPort,
    private val configRepositoryPort: ConfigRepositoryPort,
    private val botRepositoryPort: BotRepositoryPort,
    private val permissionStatusPort: GeofencePermissionStatusPort,
    private val reconciliationPort: GeofenceRuntimeReconciliationPort,
) : GeofenceActiveCapabilityFacade {
    override suspend fun createRule(
        payload: Map<String, Any?>,
        metadata: Map<String, Any?>?,
        toolSourceContext: ToolSourceContext?,
    ): JSONObject {
        val permission = permissionStatusPort.currentStatus()
        val location = when (val resolved = resolveLocation(payload, metadata, permission)) {
            is LocationResolution.Resolved -> resolved.location
            is LocationResolution.Failed -> return structuredError(resolved.code, resolved.message)
            LocationResolution.NotRequested -> return locationError(payload)
        }
        val now = System.currentTimeMillis()
        val ruleId = payload.stringValue("rule_id").ifBlank { UUID.randomUUID().toString() }
        val config = resolveTargetConfig(
            requestedConfigId = payload.stringValue("config_profile_id"),
            fallbackConfigId = "",
            toolSourceContext = toolSourceContext,
        ) ?: return missingTargetConfigError()
        val configId = config.id
        val targetPlatform = when (val resolved = resolveTargetPlatform(payload.stringValue("target_platform"), toolSourceContext)) {
            is TargetPlatformResolution.Resolved -> resolved.platform
            is TargetPlatformResolution.Failed -> return structuredError(resolved.code, resolved.message)
        }
        val targetBot = when (val resolved = resolveTargetBot(payload.stringValue("bot_id"), config)) {
            is TargetBotResolution.Resolved -> resolved.bot
            is TargetBotResolution.Failed -> return structuredError(resolved.code, resolved.message)
        }
        val actionPrompt = payload.stringValue("action_prompt")
            .ifBlank { payload.stringValue("message") }
            .ifBlank { payload.stringValue("prompt") }
        if (actionPrompt.isBlank()) {
            return structuredError("missing_action_prompt", "Geofence action_prompt is required.")
        }
        val trigger = payload.stringValue("trigger").ifBlank { "enter" }
        val enabled = payload.booleanValue("enabled") ?: true
        val status = when {
            !permission.backgroundGranted -> GeofenceRuleStatus.PERMISSION_REQUIRED
            !enabled -> GeofenceRuleStatus.PAUSED
            else -> GeofenceRuleStatus.ACTIVE
        }
        val rule = GeofenceRule(
            ruleId = ruleId,
            name = payload.stringValue("name").ifBlank { "Geofence rule" },
            description = payload.stringValue("description"),
            enabled = enabled,
            triggerEnter = trigger == "enter" || trigger == "enter_exit",
            triggerExit = trigger == "exit" || trigger == "enter_exit",
            triggerDwell = trigger == "dwell",
            dwellDelayMillis = payload.longValue("dwell_delay_millis")
                .takeIf { it > 0L || trigger != "dwell" }
                ?: DEFAULT_DWELL_DELAY_MILLIS,
            actionType = GeofenceActionType.fromPersistedValue(payload.stringValue("action_type")),
            actionPrompt = actionPrompt,
            targetPlatform = targetPlatform,
            targetConversationId = payload.stringValue("conversation_id").ifBlank { toolSourceContext?.conversationId.orEmpty() },
            targetBotId = targetBot.id,
            targetConfigProfileId = configId,
            targetPersonaId = targetBot.defaultPersonaId,
            targetProviderId = targetBot.defaultProviderId.ifBlank { config.defaultChatProviderId },
            minimumTriggerIntervalMillis = payload.longValue("minimum_trigger_interval_millis"),
            status = status,
            lastError = if (status == GeofenceRuleStatus.PERMISSION_REQUIRED) "background_location_permission_required" else "",
            createdAt = now,
            updatedAt = now,
        )
        val region = GeofenceRegion(
            regionId = UUID.randomUUID().toString(),
            ruleId = rule.ruleId,
            label = payload.stringValue("region_label").ifBlank { rule.name },
            latitude = location.latitude,
            longitude = location.longitude,
            radiusMeters = location.radiusMeters,
            addressLabel = payload.stringValue("address_label"),
            createdAt = now,
            updatedAt = now,
        )
        val created = repository.createRule(rule.copy(regions = listOf(region)), listOf(region))
        if (configId.isNotBlank()) {
            val bindingResult = runCatching {
                repository.upsertConfigBinding(
                    ConfigGeofenceBinding(
                        configId = configId,
                        ruleId = created.ruleId,
                        enabled = enabled,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }
            if (bindingResult.isFailure) {
                runCatching { repository.deleteRule(created.ruleId) }
                return structuredError(
                    code = "config_binding_failed",
                    message = "Failed to bind geofence rule to target ConfigProfile.",
                )
            }
        }
        val reconciliation = reconcile()
        if (reconciliation is ReconciliationResolution.Failed) {
            return structuredError(reconciliation.code, reconciliation.message).apply {
                put("reconciliation_status", reconciliation.status)
            }
        }
        return ruleJson(created.copy(regions = listOf(region))).apply {
            put("success", true)
            put("reconciliation_status", (reconciliation as ReconciliationResolution.Resolved).status)
            if (!permission.backgroundGranted) {
                put("message", "Saved but not enabled for background triggering; background location permission is required.")
            }
        }
    }

    override suspend fun updateRule(
        payload: Map<String, Any?>,
        metadata: Map<String, Any?>?,
        toolSourceContext: ToolSourceContext?,
    ): JSONObject {
        val ruleId = payload.stringValue("rule_id")
        if (ruleId.isBlank()) return structuredError("missing_rule_id", "rule_id is required.")
        val existing = repository.getRule(ruleId)
            ?: return structuredError("not_found", "Geofence rule not found: $ruleId")
        val config = resolveTargetConfig(
            requestedConfigId = payload.stringValue("config_profile_id"),
            fallbackConfigId = existing.targetConfigProfileId,
            toolSourceContext = toolSourceContext,
        ) ?: return missingTargetConfigError()
        val locationResolution = resolveLocation(payload, metadata, permissionStatusPort.currentStatus())
        if (locationResolution is LocationResolution.Failed) {
            return structuredError(locationResolution.code, locationResolution.message)
        }
        val targetPlatform = when (
            val resolved = resolveTargetPlatform(
                requestedPlatform = payload.stringValue("target_platform").ifBlank { existing.targetPlatform },
                toolSourceContext = toolSourceContext,
            )
        ) {
            is TargetPlatformResolution.Resolved -> resolved.platform
            is TargetPlatformResolution.Failed -> return structuredError(resolved.code, resolved.message)
        }
        val targetBot = when (val resolved = resolveTargetBot(payload.stringValue("bot_id").ifBlank { existing.targetBotId }, config)) {
            is TargetBotResolution.Resolved -> resolved.bot
            is TargetBotResolution.Failed -> return structuredError(resolved.code, resolved.message)
        }
        val updated = repository.updateRule(
            existing.copy(
                name = payload.stringValue("name").ifBlank { existing.name },
                description = payload.stringValue("description").ifBlank { existing.description },
                enabled = payload.booleanValue("enabled") ?: existing.enabled,
                actionType = payload.stringValue("action_type")
                    .takeIf(String::isNotBlank)
                    ?.let(GeofenceActionType::fromPersistedValue)
                    ?: existing.actionType,
                actionPrompt = payload.stringValue("action_prompt").ifBlank { existing.actionPrompt },
                targetPlatform = targetPlatform,
                targetConversationId = payload.stringValue("conversation_id").ifBlank { existing.targetConversationId },
                targetBotId = targetBot.id,
                targetConfigProfileId = config.id,
                targetPersonaId = targetBot.defaultPersonaId.ifBlank { existing.targetPersonaId },
                targetProviderId = targetBot.defaultProviderId
                    .ifBlank { existing.targetProviderId }
                    .ifBlank { config.defaultChatProviderId },
                minimumTriggerIntervalMillis = payload.longValue("minimum_trigger_interval_millis")
                    .takeIf { payload.containsKey("minimum_trigger_interval_millis") }
                    ?: existing.minimumTriggerIntervalMillis,
            ).applyTriggerPatch(payload),
        )
        val ruleWithRegions = when (locationResolution) {
            is LocationResolution.Resolved -> {
                val firstRegion = existing.regions.firstOrNull()
                val region = GeofenceRegion(
                    regionId = firstRegion?.regionId ?: UUID.randomUUID().toString(),
                    ruleId = ruleId,
                    label = payload.stringValue("region_label").ifBlank { firstRegion?.label ?: updated.name },
                    latitude = locationResolution.location.latitude,
                    longitude = locationResolution.location.longitude,
                    radiusMeters = locationResolution.location.radiusMeters,
                    addressLabel = payload.stringValue("address_label").ifBlank { firstRegion?.addressLabel.orEmpty() },
                    sortIndex = firstRegion?.sortIndex ?: 0,
                    createdAt = firstRegion?.createdAt ?: System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )
                repository.replaceRegions(ruleId, listOf(region)) ?: updated
            }
            LocationResolution.NotRequested -> updated
            is LocationResolution.Failed -> error("handled above")
        }
        val reconciliation = reconcile()
        if (reconciliation is ReconciliationResolution.Failed) {
            return structuredError(reconciliation.code, reconciliation.message).apply {
                put("reconciliation_status", reconciliation.status)
            }
        }
        return ruleJson(ruleWithRegions).apply {
            put("success", true)
            put("reconciliation_status", (reconciliation as ReconciliationResolution.Resolved).status)
        }
    }

    override suspend fun listRules(): JSONObject {
        val rules = repository.listRules()
        return JSONObject().apply {
            put("success", true)
            put("count", rules.size)
            put("rules", JSONArray().apply {
                rules.forEach { rule -> put(ruleJson(rule, includeSensitiveLocation = false)) }
            })
        }
    }

    override suspend fun deleteRule(ruleId: String): JSONObject {
        if (ruleId.isBlank()) return structuredError("missing_rule_id", "rule_id is required.")
        val existing = repository.getRule(ruleId) ?: return structuredError("not_found", "Geofence rule not found: $ruleId")
        repository.deleteRule(ruleId)
        val reconciliation = reconcile()
        if (reconciliation is ReconciliationResolution.Failed) {
            return structuredError(reconciliation.code, reconciliation.message).apply {
                put("reconciliation_status", reconciliation.status)
            }
        }
        return JSONObject().apply {
            put("success", true)
            put("deleted_rule_id", ruleId)
            put("deleted_name", existing.name)
            put("reconciliation_status", (reconciliation as ReconciliationResolution.Resolved).status)
        }
    }

    override suspend fun pauseRule(ruleId: String): JSONObject {
        if (ruleId.isBlank()) return structuredError("missing_rule_id", "rule_id is required.")
        val updated = repository.pauseRule(ruleId) ?: return structuredError("not_found", "Geofence rule not found: $ruleId")
        val reconciliation = reconcile()
        if (reconciliation is ReconciliationResolution.Failed) {
            return structuredError(reconciliation.code, reconciliation.message).apply {
                put("reconciliation_status", reconciliation.status)
            }
        }
        return ruleJson(updated).apply {
            put("success", true)
            put("reconciliation_status", (reconciliation as ReconciliationResolution.Resolved).status)
        }
    }

    override suspend fun resumeRule(ruleId: String): JSONObject {
        if (ruleId.isBlank()) return structuredError("missing_rule_id", "rule_id is required.")
        val updated = repository.resumeRule(ruleId) ?: return structuredError("not_found", "Geofence rule not found: $ruleId")
        val reconciliation = reconcile()
        if (reconciliation is ReconciliationResolution.Failed) {
            return structuredError(reconciliation.code, reconciliation.message).apply {
                put("reconciliation_status", reconciliation.status)
            }
        }
        return ruleJson(updated).apply {
            put("success", true)
            put("reconciliation_status", (reconciliation as ReconciliationResolution.Resolved).status)
        }
    }

    private suspend fun reconcile(): ReconciliationResolution =
        runCatching { reconciliationPort.reconcileNow() }
            .fold(
                onSuccess = { summary ->
                    val status = summary.status.name.lowercase()
                    if (summary.status in RECONCILIATION_FAILURE_STATUSES) {
                        ReconciliationResolution.Failed(
                            code = "reconciliation_failed",
                            status = status,
                            message = summary.errorMessage.ifBlank {
                                "Geofence runtime reconciliation failed: $status."
                            },
                        )
                    } else {
                        ReconciliationResolution.Resolved(status)
                    }
                },
                onFailure = { error ->
                    ReconciliationResolution.Failed(
                        code = "reconciliation_failed",
                        status = "reconciliation_failed",
                        message = error.message ?: error.javaClass.simpleName,
                    )
                },
            )

    private fun resolveTargetConfig(
        requestedConfigId: String,
        fallbackConfigId: String,
        toolSourceContext: ToolSourceContext?,
    ): ConfigProfile? {
        val configId = requestedConfigId
            .ifBlank { fallbackConfigId }
            .ifBlank { toolSourceContext?.configProfileId.orEmpty() }
            .trim()
        if (configId.isBlank()) return null
        return configRepositoryPort.snapshotProfiles().firstOrNull { it.id == configId }
    }

    private fun resolveTargetPlatform(
        requestedPlatform: String,
        toolSourceContext: ToolSourceContext?,
    ): TargetPlatformResolution {
        val rawValue = requestedPlatform
            .ifBlank { toolSourceContext?.platform?.wireValue.orEmpty() }
            .trim()
        return when (rawValue.lowercase()) {
            "",
            RuntimePlatform.APP_CHAT.wireValue,
            -> TargetPlatformResolution.Resolved(RuntimePlatform.APP_CHAT.wireValue)
            "qq",
            "onebot",
            RuntimePlatform.QQ_ONEBOT.wireValue,
            -> TargetPlatformResolution.Resolved(RuntimePlatform.QQ_ONEBOT.wireValue)
            else -> TargetPlatformResolution.Failed(
                code = "invalid_target_platform",
                message = "Unsupported geofence target platform: $rawValue.",
            )
        }
    }

    private fun resolveTargetBot(botId: String, config: ConfigProfile): TargetBotResolution {
        val resolvedBotId = botId
            .ifBlank { botRepositoryPort.currentBot().id }
            .trim()
        if (resolvedBotId.isBlank()) {
            return TargetBotResolution.Failed(
                code = "missing_target_context",
                message = "Target Bot is missing.",
            )
        }
        val bot = botRepositoryPort.snapshotProfiles().firstOrNull { it.id == resolvedBotId }
        return if (bot != null && bot.configProfileId == config.id) {
            TargetBotResolution.Resolved(bot)
        } else {
            TargetBotResolution.Failed(
                code = "missing_target_context",
                message = "Target Bot is missing or does not belong to ConfigProfile ${config.id}.",
            )
        }
    }

    private fun resolveLocation(
        payload: Map<String, Any?>,
        metadata: Map<String, Any?>?,
        permission: GeofencePermissionStatus,
    ): LocationResolution {
        val latitude = payload.numberValue("latitude")?.toDouble()
        val longitude = payload.numberValue("longitude")?.toDouble()
        val radius = payload.numberValue("radius_meters")?.toFloat()
            ?: payload.numberValue("radiusMeters")?.toFloat()
            ?: 100f
        if (payload.booleanValue("use_current_location") == true) {
            if (!permission.foregroundGranted) {
                return LocationResolution.Failed(
                    code = "permission_required",
                    message = "Foreground location permission is required before using current location.",
                )
            }
            val currentLocation = metadata.trustedCurrentLocation()
                ?: return LocationResolution.Failed(
                    code = "missing_location",
                    message = "Current location is not available to the Agent runtime.",
                )
            val currentLatitude = currentLocation.numberValueFromAnyMap("latitude")?.toDouble()
            val currentLongitude = currentLocation.numberValueFromAnyMap("longitude")?.toDouble()
            if (currentLatitude == null || currentLongitude == null) {
                return LocationResolution.Failed(
                    code = "missing_location",
                    message = "Current location is not available to the Agent runtime.",
                )
            }
            val currentRadius = payload.numberValue("radius_meters")?.toFloat()
                ?: payload.numberValue("radiusMeters")?.toFloat()
                ?: currentLocation.numberValueFromAnyMap("radius_meters")?.toFloat()
                ?: currentLocation.numberValueFromAnyMap("radiusMeters")?.toFloat()
                ?: 100f
            return LocationResolution.Resolved(
                LocationPayload(latitude = currentLatitude, longitude = currentLongitude, radiusMeters = currentRadius),
            )
        }
        if (latitude != null && longitude != null) {
            return LocationResolution.Resolved(
                LocationPayload(latitude = latitude, longitude = longitude, radiusMeters = radius),
            )
        }
        if (latitude != null || longitude != null || payload.containsKey("radius_meters") || payload.containsKey("radiusMeters")) {
            return LocationResolution.Failed(
                code = "missing_location",
                message = "Geofence location is incomplete. Provide both latitude and longitude.",
            )
        }
        return LocationResolution.NotRequested
    }

    private fun locationError(payload: Map<String, Any?>): JSONObject {
        return if (payload.booleanValue("use_current_location") == true) {
            structuredError(
                code = "missing_location",
                message = "Current location is not available to the Agent runtime.",
            )
        } else {
            structuredError(
                code = "missing_location",
                message = "Geofence location is missing. Provide latitude/longitude or request current location with permission.",
            )
        }
    }

    private fun GeofenceRule.applyTriggerPatch(payload: Map<String, Any?>): GeofenceRule {
        val trigger = payload.stringValue("trigger")
        if (trigger.isBlank()) return this
        return copy(
            triggerEnter = trigger == "enter" || trigger == "enter_exit",
            triggerExit = trigger == "exit" || trigger == "enter_exit",
            triggerDwell = trigger == "dwell",
            dwellDelayMillis = payload.longValue("dwell_delay_millis")
                .takeIf { it > 0L || trigger != "dwell" }
                ?: DEFAULT_DWELL_DELAY_MILLIS,
        )
    }

    private fun ruleJson(rule: GeofenceRule, includeSensitiveLocation: Boolean = true): JSONObject =
        JSONObject().apply {
            put("rule_id", rule.ruleId)
            put("name", rule.name)
            put("description", rule.description)
            put("enabled", rule.enabled)
            put("status", rule.status.persistedValue)
            put("action_type", rule.actionType.persistedValue)
            put("target_platform", rule.targetPlatform)
            put("conversation_id", rule.targetConversationId)
            put("config_profile_id", rule.targetConfigProfileId)
            put("minimum_trigger_interval_millis", rule.minimumTriggerIntervalMillis)
            put("triggers", JSONArray().apply {
                if (rule.triggerEnter) put("enter")
                if (rule.triggerExit) put("exit")
                if (rule.triggerDwell) put("dwell")
            })
            put("regions", JSONArray().apply {
                rule.regions.forEach { region ->
                    put(JSONObject().apply {
                        put("region_id", region.regionId)
                        put("label", region.label)
                        put("radius_meters", region.radiusMeters)
                        put("address_label", region.addressLabel)
                        if (includeSensitiveLocation) {
                            put("latitude", region.latitude)
                            put("longitude", region.longitude)
                        } else {
                            put("location_present", true)
                        }
                    })
                }
            })
        }

    private fun structuredError(code: String, message: String): JSONObject =
        JSONObject().apply {
            put("success", false)
            put("error_code", code)
            put("message", message)
        }

    private fun missingTargetConfigError(): JSONObject =
        structuredError(
            code = "missing_target_context",
            message = "Target ConfigProfile is missing or unavailable.",
        )

    private data class LocationPayload(
        val latitude: Double,
        val longitude: Double,
        val radiusMeters: Float,
    )

    private sealed class LocationResolution {
        data class Resolved(val location: LocationPayload) : LocationResolution()
        data class Failed(val code: String, val message: String) : LocationResolution()
        object NotRequested : LocationResolution()
    }

    private sealed class TargetPlatformResolution {
        data class Resolved(val platform: String) : TargetPlatformResolution()
        data class Failed(val code: String, val message: String) : TargetPlatformResolution()
    }

    private sealed class TargetBotResolution {
        data class Resolved(val bot: BotProfile) : TargetBotResolution()
        data class Failed(val code: String, val message: String) : TargetBotResolution()
    }

    private sealed class ReconciliationResolution {
        data class Resolved(val status: String) : ReconciliationResolution()
        data class Failed(val code: String, val status: String, val message: String) : ReconciliationResolution()
    }

    private companion object {
        const val DEFAULT_DWELL_DELAY_MILLIS = 300_000L
        val RECONCILIATION_FAILURE_STATUSES = setOf(
            GeofenceRegistrationStatus.PLAY_SERVICES_UNAVAILABLE,
            GeofenceRegistrationStatus.CAPACITY_EXCEEDED,
            GeofenceRegistrationStatus.REGISTRATION_FAILED,
        )
    }
}

private fun Map<String, Any?>.stringValue(key: String): String =
    (this[key] as? String)?.trim().orEmpty()

private fun Map<String, Any?>.booleanValue(key: String): Boolean? =
    this[key] as? Boolean

private fun Map<String, Any?>.longValue(key: String): Long =
    numberValue(key)?.toLong() ?: 0L

private fun Map<String, Any?>.numberValue(key: String): Number? =
    this[key] as? Number

private fun Map<String, Any?>?.trustedCurrentLocation(): Map<*, *>? {
    val host = this?.get("__host") as? Map<*, *> ?: return null
    return host["current_location"] as? Map<*, *>
        ?: host["currentLocation"] as? Map<*, *>
}

private fun Map<*, *>.numberValueFromAnyMap(key: String): Number? =
    this[key] as? Number
