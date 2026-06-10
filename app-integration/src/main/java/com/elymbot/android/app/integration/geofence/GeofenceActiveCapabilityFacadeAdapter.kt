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
        val request = when (val resolved = buildCreateRuleRequest(payload, metadata, toolSourceContext)) {
            is CreateRuleRequestResolution.Failed -> return structuredError(resolved.code, resolved.message)
            is CreateRuleRequestResolution.Resolved -> resolved.request
        }
        val created = repository.createRule(request.rule.copy(regions = listOf(request.region)), listOf(request.region))
        val bindingError = bindCreatedRuleToConfig(created.ruleId, request.configId, request.enabled, request.now)
        if (bindingError != null) return bindingError
        val reconciliation = reconcile()
        if (reconciliation is ReconciliationResolution.Failed) {
            return structuredError(reconciliation.code, reconciliation.message).apply {
                put(KEY_RECONCILIATION_STATUS, reconciliation.status)
            }
        }
        return ruleJson(created.copy(regions = listOf(request.region))).apply {
            put(KEY_SUCCESS, true)
            put(KEY_RECONCILIATION_STATUS, (reconciliation as ReconciliationResolution.Resolved).status)
            if (!request.permission.backgroundGranted) {
                put(KEY_MESSAGE, "Saved but not enabled for background triggering; background location permission is required.")
            }
        }
    }

    private fun buildCreateRuleRequest(
        payload: Map<String, Any?>,
        metadata: Map<String, Any?>?,
        toolSourceContext: ToolSourceContext?,
    ): CreateRuleRequestResolution {
        val permission = permissionStatusPort.currentStatus()
        val location = when (val resolved = resolveRequiredCreateLocation(payload, metadata, permission)) {
            is LocationResolution.Resolved -> resolved.location
            is LocationResolution.Failed -> return CreateRuleRequestResolution.Failed(resolved.code, resolved.message)
            LocationResolution.NotRequested -> error("required create location cannot be not requested")
        }
        val config = resolveCreateTargetConfig(payload, toolSourceContext)
            ?: return CreateRuleRequestResolution.Failed(CODE_MISSING_TARGET_CONTEXT, MESSAGE_MISSING_TARGET_CONFIG)
        val targetPlatform = when (val resolved = resolveTargetPlatform(payload.stringValue(KEY_TARGET_PLATFORM), toolSourceContext)) {
            is TargetPlatformResolution.Resolved -> resolved.platform
            is TargetPlatformResolution.Failed -> return CreateRuleRequestResolution.Failed(resolved.code, resolved.message)
        }
        val targetBot = when (val resolved = resolveTargetBot(payload.stringValue(KEY_BOT_ID), config)) {
            is TargetBotResolution.Resolved -> resolved.bot
            is TargetBotResolution.Failed -> return CreateRuleRequestResolution.Failed(resolved.code, resolved.message)
        }
        val actionPrompt = resolveCreateActionPrompt(payload)
            ?: return CreateRuleRequestResolution.Failed(CODE_MISSING_ACTION_PROMPT, MESSAGE_MISSING_ACTION_PROMPT)
        val trigger = payload.stringValue(KEY_TRIGGER).ifBlank { TRIGGER_ENTER }
        val enabled = payload.booleanValue(KEY_ENABLED) ?: true
        val now = System.currentTimeMillis()
        val rule = buildCreateRule(
            payload = payload,
            config = config,
            targetBot = targetBot,
            targetPlatform = targetPlatform,
            actionPrompt = actionPrompt,
            trigger = trigger,
            enabled = enabled,
            permission = permission,
            toolSourceContext = toolSourceContext,
            now = now,
        )
        val region = buildCreateRegion(payload, rule, location, now)
        return CreateRuleRequestResolution.Resolved(
            CreateRuleRequest(
                rule = rule,
                region = region,
                configId = config.id,
                enabled = enabled,
                now = now,
                permission = permission,
            ),
        )
    }

    private fun resolveRequiredCreateLocation(
        payload: Map<String, Any?>,
        metadata: Map<String, Any?>?,
        permission: GeofencePermissionStatus,
    ): LocationResolution =
        when (val resolved = resolveLocation(payload, metadata, permission)) {
            is LocationResolution.Resolved -> resolved
            is LocationResolution.Failed -> resolved
            LocationResolution.NotRequested -> LocationResolution.Failed(
                code = CODE_MISSING_LOCATION,
                message = locationErrorMessage(payload),
            )
        }

    private fun resolveCreateTargetConfig(
        payload: Map<String, Any?>,
        toolSourceContext: ToolSourceContext?,
    ): ConfigProfile? =
        resolveTargetConfig(
            requestedConfigId = payload.stringValue(KEY_CONFIG_PROFILE_ID),
            fallbackConfigId = EMPTY_VALUE,
            toolSourceContext = toolSourceContext,
        )

    private fun resolveCreateActionPrompt(payload: Map<String, Any?>): String? =
        payload.stringValue(KEY_ACTION_PROMPT)
            .ifBlank { payload.stringValue(KEY_MESSAGE) }
            .ifBlank { payload.stringValue(KEY_PROMPT) }
            .takeIf(String::isNotBlank)

    private fun buildCreateRule(
        payload: Map<String, Any?>,
        config: ConfigProfile,
        targetBot: BotProfile,
        targetPlatform: String,
        actionPrompt: String,
        trigger: String,
        enabled: Boolean,
        permission: GeofencePermissionStatus,
        toolSourceContext: ToolSourceContext?,
        now: Long,
    ): GeofenceRule {
        val status = createRuleStatus(permission, enabled)
        val ruleId = payload.stringValue(KEY_RULE_ID).ifBlank { UUID.randomUUID().toString() }
        return GeofenceRule(
            ruleId = ruleId,
            name = payload.stringValue(KEY_NAME).ifBlank { DEFAULT_RULE_NAME },
            description = payload.stringValue(KEY_DESCRIPTION),
            enabled = enabled,
            triggerEnter = trigger == TRIGGER_ENTER || trigger == TRIGGER_ENTER_EXIT,
            triggerExit = trigger == TRIGGER_EXIT || trigger == TRIGGER_ENTER_EXIT,
            triggerDwell = trigger == TRIGGER_DWELL,
            dwellDelayMillis = dwellDelayForPayload(payload, trigger),
            actionType = GeofenceActionType.fromPersistedValue(payload.stringValue(KEY_ACTION_TYPE)),
            actionPrompt = actionPrompt,
            targetPlatform = targetPlatform,
            targetConversationId = payload.stringValue(KEY_CONVERSATION_ID).ifBlank { toolSourceContext?.conversationId.orEmpty() },
            targetBotId = targetBot.id,
            targetConfigProfileId = config.id,
            targetPersonaId = targetBot.defaultPersonaId,
            targetProviderId = targetBot.defaultProviderId.ifBlank { config.defaultChatProviderId },
            minimumTriggerIntervalMillis = payload.longValue(KEY_MINIMUM_TRIGGER_INTERVAL_MILLIS),
            status = status,
            lastError = if (status == GeofenceRuleStatus.PERMISSION_REQUIRED) BACKGROUND_LOCATION_PERMISSION_ERROR else EMPTY_VALUE,
            createdAt = now,
            updatedAt = now,
        )
    }

    private fun createRuleStatus(permission: GeofencePermissionStatus, enabled: Boolean): GeofenceRuleStatus =
        when {
            !permission.backgroundGranted -> GeofenceRuleStatus.PERMISSION_REQUIRED
            !enabled -> GeofenceRuleStatus.PAUSED
            else -> GeofenceRuleStatus.ACTIVE
        }

    private fun buildCreateRegion(
        payload: Map<String, Any?>,
        rule: GeofenceRule,
        location: LocationPayload,
        now: Long,
    ): GeofenceRegion =
        GeofenceRegion(
            regionId = UUID.randomUUID().toString(),
            ruleId = rule.ruleId,
            label = payload.stringValue(KEY_REGION_LABEL).ifBlank { rule.name },
            latitude = location.latitude,
            longitude = location.longitude,
            radiusMeters = location.radiusMeters,
            addressLabel = payload.stringValue(KEY_ADDRESS_LABEL),
            createdAt = now,
            updatedAt = now,
        )

    private suspend fun bindCreatedRuleToConfig(
        ruleId: String,
        configId: String,
        enabled: Boolean,
        now: Long,
    ): JSONObject? {
        if (configId.isNotBlank()) {
            val bindingResult = runCatching {
                repository.upsertConfigBinding(
                    ConfigGeofenceBinding(
                        configId = configId,
                        ruleId = ruleId,
                        enabled = enabled,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }
            if (bindingResult.isFailure) {
                runCatching { repository.deleteRule(ruleId) }
                return structuredError(
                    code = "config_binding_failed",
                    message = "Failed to bind geofence rule to target ConfigProfile.",
                )
            }
        }
        return null
    }

    override suspend fun updateRule(
        payload: Map<String, Any?>,
        metadata: Map<String, Any?>?,
        toolSourceContext: ToolSourceContext?,
    ): JSONObject {
        val ruleId = payload.stringValue(KEY_RULE_ID)
        if (ruleId.isBlank()) return structuredError(CODE_MISSING_RULE_ID, MESSAGE_MISSING_RULE_ID)
        val existing = repository.getRule(ruleId)
            ?: return ruleNotFoundError(ruleId)
        val config = resolveTargetConfig(
            requestedConfigId = payload.stringValue(KEY_CONFIG_PROFILE_ID),
            fallbackConfigId = existing.targetConfigProfileId,
            toolSourceContext = toolSourceContext,
        ) ?: return missingTargetConfigError()
        val locationResolution = resolveLocation(payload, metadata, permissionStatusPort.currentStatus())
        if (locationResolution is LocationResolution.Failed) {
            return structuredError(locationResolution.code, locationResolution.message)
        }
        val targets = when (val resolved = resolveUpdateTargets(payload, existing, config, toolSourceContext)) {
            is UpdateRuleTargetResolution.Resolved -> resolved.targets
            is UpdateRuleTargetResolution.Failed -> return structuredError(resolved.code, resolved.message)
        }
        val updated = repository.updateRule(existing.updatedWith(payload, config, targets))
        val ruleWithRegions = replaceRegionsForUpdate(ruleId, existing, updated, payload, locationResolution)
        val reconciliation = reconcile()
        if (reconciliation is ReconciliationResolution.Failed) {
            return structuredError(reconciliation.code, reconciliation.message).apply {
                put(KEY_RECONCILIATION_STATUS, reconciliation.status)
            }
        }
        return ruleJson(ruleWithRegions).apply {
            put(KEY_SUCCESS, true)
            put(KEY_RECONCILIATION_STATUS, (reconciliation as ReconciliationResolution.Resolved).status)
        }
    }

    private fun resolveUpdateTargets(
        payload: Map<String, Any?>,
        existing: GeofenceRule,
        config: ConfigProfile,
        toolSourceContext: ToolSourceContext?,
    ): UpdateRuleTargetResolution {
        val platform = when (
            val resolved = resolveTargetPlatform(
                requestedPlatform = payload.stringValue(KEY_TARGET_PLATFORM).ifBlank { existing.targetPlatform },
                toolSourceContext = toolSourceContext,
            )
        ) {
            is TargetPlatformResolution.Resolved -> resolved.platform
            is TargetPlatformResolution.Failed -> return UpdateRuleTargetResolution.Failed(resolved.code, resolved.message)
        }
        val bot = when (val resolved = resolveTargetBot(payload.stringValue(KEY_BOT_ID).ifBlank { existing.targetBotId }, config)) {
            is TargetBotResolution.Resolved -> resolved.bot
            is TargetBotResolution.Failed -> return UpdateRuleTargetResolution.Failed(resolved.code, resolved.message)
        }
        return UpdateRuleTargetResolution.Resolved(UpdateRuleTargets(platform = platform, bot = bot))
    }

    private fun GeofenceRule.updatedWith(
        payload: Map<String, Any?>,
        config: ConfigProfile,
        targets: UpdateRuleTargets,
    ): GeofenceRule =
        copy(
            name = payload.stringValue(KEY_NAME).ifBlank { name },
            description = payload.stringValue(KEY_DESCRIPTION).ifBlank { description },
            enabled = payload.booleanValue(KEY_ENABLED) ?: enabled,
            actionType = payload.stringValue(KEY_ACTION_TYPE)
                .takeIf(String::isNotBlank)
                ?.let(GeofenceActionType::fromPersistedValue)
                ?: actionType,
            actionPrompt = payload.stringValue(KEY_ACTION_PROMPT).ifBlank { actionPrompt },
            targetPlatform = targets.platform,
            targetConversationId = payload.stringValue(KEY_CONVERSATION_ID).ifBlank { targetConversationId },
            targetBotId = targets.bot.id,
            targetConfigProfileId = config.id,
            targetPersonaId = targets.bot.defaultPersonaId.ifBlank { targetPersonaId },
            targetProviderId = targets.bot.defaultProviderId
                .ifBlank { targetProviderId }
                .ifBlank { config.defaultChatProviderId },
            minimumTriggerIntervalMillis = payload.longValue(KEY_MINIMUM_TRIGGER_INTERVAL_MILLIS)
                .takeIf { payload.containsKey(KEY_MINIMUM_TRIGGER_INTERVAL_MILLIS) }
                ?: minimumTriggerIntervalMillis,
        ).applyTriggerPatch(payload)

    private suspend fun replaceRegionsForUpdate(
        ruleId: String,
        existing: GeofenceRule,
        updated: GeofenceRule,
        payload: Map<String, Any?>,
        locationResolution: LocationResolution,
    ): GeofenceRule =
        when (locationResolution) {
            is LocationResolution.Resolved -> repository.replaceRegions(
                ruleId,
                listOf(updateRegion(ruleId, updated, existing.regions.firstOrNull(), payload, locationResolution.location)),
            ) ?: updated
            LocationResolution.NotRequested -> updated
            is LocationResolution.Failed -> error("handled above")
        }

    private fun updateRegion(
        ruleId: String,
        updated: GeofenceRule,
        firstRegion: GeofenceRegion?,
        payload: Map<String, Any?>,
        location: LocationPayload,
    ): GeofenceRegion =
        GeofenceRegion(
            regionId = firstRegion?.regionId ?: UUID.randomUUID().toString(),
            ruleId = ruleId,
            label = payload.stringValue(KEY_REGION_LABEL).ifBlank { firstRegion?.label ?: updated.name },
            latitude = location.latitude,
            longitude = location.longitude,
            radiusMeters = location.radiusMeters,
            addressLabel = payload.stringValue(KEY_ADDRESS_LABEL).ifBlank { firstRegion?.addressLabel.orEmpty() },
            sortIndex = firstRegion?.sortIndex ?: 0,
            createdAt = firstRegion?.createdAt ?: System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )

    override suspend fun listRules(): JSONObject {
        val rules = repository.listRules()
        return JSONObject().apply {
            put(KEY_SUCCESS, true)
            put("count", rules.size)
            put("rules", JSONArray().apply {
                rules.forEach { rule -> put(ruleJson(rule, includeSensitiveLocation = false)) }
            })
        }
    }

    override suspend fun deleteRule(ruleId: String): JSONObject {
        if (ruleId.isBlank()) return structuredError(CODE_MISSING_RULE_ID, MESSAGE_MISSING_RULE_ID)
        val existing = repository.getRule(ruleId) ?: return ruleNotFoundError(ruleId)
        repository.deleteRule(ruleId)
        val reconciliation = reconcile()
        if (reconciliation is ReconciliationResolution.Failed) {
            return structuredError(reconciliation.code, reconciliation.message).apply {
                put(KEY_RECONCILIATION_STATUS, reconciliation.status)
            }
        }
        return JSONObject().apply {
            put(KEY_SUCCESS, true)
            put(KEY_DELETED_RULE_ID, ruleId)
            put(KEY_DELETED_NAME, existing.name)
            put(KEY_RECONCILIATION_STATUS, (reconciliation as ReconciliationResolution.Resolved).status)
        }
    }

    override suspend fun pauseRule(ruleId: String): JSONObject {
        if (ruleId.isBlank()) return structuredError(CODE_MISSING_RULE_ID, MESSAGE_MISSING_RULE_ID)
        val updated = repository.pauseRule(ruleId) ?: return ruleNotFoundError(ruleId)
        val reconciliation = reconcile()
        if (reconciliation is ReconciliationResolution.Failed) {
            return structuredError(reconciliation.code, reconciliation.message).apply {
                put(KEY_RECONCILIATION_STATUS, reconciliation.status)
            }
        }
        return ruleJson(updated).apply {
            put(KEY_SUCCESS, true)
            put(KEY_RECONCILIATION_STATUS, (reconciliation as ReconciliationResolution.Resolved).status)
        }
    }

    override suspend fun resumeRule(ruleId: String): JSONObject {
        if (ruleId.isBlank()) return structuredError(CODE_MISSING_RULE_ID, MESSAGE_MISSING_RULE_ID)
        val updated = repository.resumeRule(ruleId) ?: return ruleNotFoundError(ruleId)
        val reconciliation = reconcile()
        if (reconciliation is ReconciliationResolution.Failed) {
            return structuredError(reconciliation.code, reconciliation.message).apply {
                put(KEY_RECONCILIATION_STATUS, reconciliation.status)
            }
        }
        return ruleJson(updated).apply {
            put(KEY_SUCCESS, true)
            put(KEY_RECONCILIATION_STATUS, (reconciliation as ReconciliationResolution.Resolved).status)
        }
    }

    private suspend fun reconcile(): ReconciliationResolution =
        runCatching { reconciliationPort.reconcileNow() }
            .fold(
                onSuccess = { summary ->
                    val status = summary.status.name.lowercase()
                    if (summary.status in RECONCILIATION_FAILURE_STATUSES) {
                        ReconciliationResolution.Failed(
                            code = CODE_RECONCILIATION_FAILED,
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
                        code = CODE_RECONCILIATION_FAILED,
                        status = CODE_RECONCILIATION_FAILED,
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
            EMPTY_VALUE,
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
                code = CODE_MISSING_TARGET_CONTEXT,
                message = "Target Bot is missing.",
            )
        }
        val bot = botRepositoryPort.snapshotProfiles().firstOrNull { it.id == resolvedBotId }
        return if (bot != null && bot.configProfileId == config.id) {
            TargetBotResolution.Resolved(bot)
        } else {
            TargetBotResolution.Failed(
                code = CODE_MISSING_TARGET_CONTEXT,
                message = "Target Bot is missing or does not belong to ConfigProfile ${config.id}.",
            )
        }
    }

    private fun resolveLocation(
        payload: Map<String, Any?>,
        metadata: Map<String, Any?>?,
        permission: GeofencePermissionStatus,
    ): LocationResolution =
        if (payload.booleanValue(KEY_USE_CURRENT_LOCATION) == true) {
            resolveCurrentLocation(payload, metadata, permission)
        } else {
            resolveExplicitLocation(payload)
        }

    private fun resolveCurrentLocation(
        payload: Map<String, Any?>,
        metadata: Map<String, Any?>?,
        permission: GeofencePermissionStatus,
    ): LocationResolution {
        if (!permission.foregroundGranted) {
            return LocationResolution.Failed(
                code = CODE_PERMISSION_REQUIRED,
                message = MESSAGE_FOREGROUND_LOCATION_REQUIRED,
            )
        }
        val currentLocation = metadata.trustedCurrentLocation()
            ?: return LocationResolution.Failed(
                code = CODE_MISSING_LOCATION,
                message = MESSAGE_CURRENT_LOCATION_UNAVAILABLE,
            )
        val currentLatitude = currentLocation.numberValueFromAnyMap(KEY_LATITUDE)?.toDouble()
        val currentLongitude = currentLocation.numberValueFromAnyMap(KEY_LONGITUDE)?.toDouble()
        if (currentLatitude == null || currentLongitude == null) {
            return LocationResolution.Failed(
                code = CODE_MISSING_LOCATION,
                message = MESSAGE_CURRENT_LOCATION_UNAVAILABLE,
            )
        }
        return LocationResolution.Resolved(
            LocationPayload(
                latitude = currentLatitude,
                longitude = currentLongitude,
                radiusMeters = currentRadiusMeters(payload, currentLocation),
            ),
        )
    }

    private fun resolveExplicitLocation(payload: Map<String, Any?>): LocationResolution {
        val latitude = payload.numberValue(KEY_LATITUDE)?.toDouble()
        val longitude = payload.numberValue(KEY_LONGITUDE)?.toDouble()
        if (latitude != null && longitude != null) {
            return LocationResolution.Resolved(
                LocationPayload(latitude = latitude, longitude = longitude, radiusMeters = payloadRadiusMeters(payload)),
            )
        }
        if (payload.hasPartialLocation()) {
            return LocationResolution.Failed(
                code = CODE_MISSING_LOCATION,
                message = MESSAGE_INCOMPLETE_LOCATION,
            )
        }
        return LocationResolution.NotRequested
    }

    private fun currentRadiusMeters(payload: Map<String, Any?>, currentLocation: Map<*, *>): Float =
        payloadRadiusMetersOrNull(payload)
            ?: currentLocation.numberValueFromAnyMap(KEY_RADIUS_METERS)?.toFloat()
            ?: currentLocation.numberValueFromAnyMap(KEY_RADIUS_METERS_CAMEL)?.toFloat()
            ?: DEFAULT_RADIUS_METERS

    private fun payloadRadiusMeters(payload: Map<String, Any?>): Float =
        payloadRadiusMetersOrNull(payload) ?: DEFAULT_RADIUS_METERS

    private fun payloadRadiusMetersOrNull(payload: Map<String, Any?>): Float? =
        payload.numberValue(KEY_RADIUS_METERS)?.toFloat()
            ?: payload.numberValue(KEY_RADIUS_METERS_CAMEL)?.toFloat()

    private fun Map<String, Any?>.hasPartialLocation(): Boolean =
        numberValue(KEY_LATITUDE) != null ||
            numberValue(KEY_LONGITUDE) != null ||
            containsKey(KEY_RADIUS_METERS) ||
            containsKey(KEY_RADIUS_METERS_CAMEL)

    private fun dwellDelayForPayload(payload: Map<String, Any?>, trigger: String): Long =
        payload.longValue(KEY_DWELL_DELAY_MILLIS)
            .takeIf { it > 0L || trigger != TRIGGER_DWELL }
            ?: DEFAULT_DWELL_DELAY_MILLIS

    private fun locationErrorMessage(payload: Map<String, Any?>): String =
        if (payload.booleanValue(KEY_USE_CURRENT_LOCATION) == true) {
            MESSAGE_CURRENT_LOCATION_UNAVAILABLE
        } else {
            "Geofence location is missing. Provide latitude/longitude or request current location with permission."
        }

    private fun GeofenceRule.applyTriggerPatch(payload: Map<String, Any?>): GeofenceRule {
        val trigger = payload.stringValue(KEY_TRIGGER)
        if (trigger.isBlank()) return this
        return copy(
            triggerEnter = trigger == TRIGGER_ENTER || trigger == TRIGGER_ENTER_EXIT,
            triggerExit = trigger == TRIGGER_EXIT || trigger == TRIGGER_ENTER_EXIT,
            triggerDwell = trigger == TRIGGER_DWELL,
            dwellDelayMillis = dwellDelayForPayload(payload, trigger),
        )
    }

    private fun ruleJson(rule: GeofenceRule, includeSensitiveLocation: Boolean = true): JSONObject =
        JSONObject().apply {
            put(KEY_RULE_ID, rule.ruleId)
            put(KEY_NAME, rule.name)
            put(KEY_DESCRIPTION, rule.description)
            put(KEY_ENABLED, rule.enabled)
            put("status", rule.status.persistedValue)
            put(KEY_ACTION_TYPE, rule.actionType.persistedValue)
            put(KEY_TARGET_PLATFORM, rule.targetPlatform)
            put(KEY_CONVERSATION_ID, rule.targetConversationId)
            put(KEY_CONFIG_PROFILE_ID, rule.targetConfigProfileId)
            put(KEY_MINIMUM_TRIGGER_INTERVAL_MILLIS, rule.minimumTriggerIntervalMillis)
            put("triggers", JSONArray().apply {
                if (rule.triggerEnter) put(TRIGGER_ENTER)
                if (rule.triggerExit) put(TRIGGER_EXIT)
                if (rule.triggerDwell) put(TRIGGER_DWELL)
            })
            put("regions", JSONArray().apply {
                rule.regions.forEach { region ->
                    put(JSONObject().apply {
                        put("region_id", region.regionId)
                        put("label", region.label)
                        put(KEY_RADIUS_METERS, region.radiusMeters)
                        put(KEY_ADDRESS_LABEL, region.addressLabel)
                        if (includeSensitiveLocation) {
                            put(KEY_LATITUDE, region.latitude)
                            put(KEY_LONGITUDE, region.longitude)
                        } else {
                            put("location_present", true)
                        }
                    })
                }
            })
        }

    private fun structuredError(code: String, message: String): JSONObject =
        JSONObject().apply {
            put(KEY_SUCCESS, false)
            put("error_code", code)
            put(KEY_MESSAGE, message)
        }

    private fun missingTargetConfigError(): JSONObject =
        structuredError(
            code = CODE_MISSING_TARGET_CONTEXT,
            message = MESSAGE_MISSING_TARGET_CONFIG,
        )

    private fun ruleNotFoundError(ruleId: String): JSONObject =
        structuredError(CODE_NOT_FOUND, "$MESSAGE_RULE_NOT_FOUND_PREFIX$ruleId")

    private data class CreateRuleRequest(
        val rule: GeofenceRule,
        val region: GeofenceRegion,
        val configId: String,
        val enabled: Boolean,
        val now: Long,
        val permission: GeofencePermissionStatus,
    )

    private data class UpdateRuleTargets(
        val platform: String,
        val bot: BotProfile,
    )

    private sealed class CreateRuleRequestResolution {
        data class Resolved(val request: CreateRuleRequest) : CreateRuleRequestResolution()
        data class Failed(val code: String, val message: String) : CreateRuleRequestResolution()
    }

    private sealed class UpdateRuleTargetResolution {
        data class Resolved(val targets: UpdateRuleTargets) : UpdateRuleTargetResolution()
        data class Failed(val code: String, val message: String) : UpdateRuleTargetResolution()
    }

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
        const val DEFAULT_RADIUS_METERS = 100f
        const val BACKGROUND_LOCATION_PERMISSION_ERROR = "background_location_permission_required"
        const val CODE_MISSING_ACTION_PROMPT = "missing_action_prompt"
        const val CODE_MISSING_LOCATION = "missing_location"
        const val CODE_MISSING_RULE_ID = "missing_rule_id"
        const val CODE_MISSING_TARGET_CONTEXT = "missing_target_context"
        const val CODE_NOT_FOUND = "not_found"
        const val CODE_PERMISSION_REQUIRED = "permission_required"
        const val CODE_RECONCILIATION_FAILED = "reconciliation_failed"
        const val DEFAULT_RULE_NAME = "Geofence rule"
        const val EMPTY_VALUE = ""
        const val KEY_ACTION_PROMPT = "action_prompt"
        const val KEY_ACTION_TYPE = "action_type"
        const val KEY_ADDRESS_LABEL = "address_label"
        const val KEY_BOT_ID = "bot_id"
        const val KEY_CONFIG_PROFILE_ID = "config_profile_id"
        const val KEY_CONVERSATION_ID = "conversation_id"
        const val KEY_DELETED_NAME = "deleted_name"
        const val KEY_DELETED_RULE_ID = "deleted_rule_id"
        const val KEY_DESCRIPTION = "description"
        const val KEY_DWELL_DELAY_MILLIS = "dwell_delay_millis"
        const val KEY_ENABLED = "enabled"
        const val KEY_LATITUDE = "latitude"
        const val KEY_LONGITUDE = "longitude"
        const val KEY_MESSAGE = "message"
        const val KEY_MINIMUM_TRIGGER_INTERVAL_MILLIS = "minimum_trigger_interval_millis"
        const val KEY_NAME = "name"
        const val KEY_PROMPT = "prompt"
        const val KEY_RADIUS_METERS = "radius_meters"
        const val KEY_RADIUS_METERS_CAMEL = "radiusMeters"
        const val KEY_RECONCILIATION_STATUS = "reconciliation_status"
        const val KEY_REGION_LABEL = "region_label"
        const val KEY_RULE_ID = "rule_id"
        const val KEY_SUCCESS = "success"
        const val KEY_TARGET_PLATFORM = "target_platform"
        const val KEY_TRIGGER = "trigger"
        const val KEY_USE_CURRENT_LOCATION = "use_current_location"
        const val MESSAGE_CURRENT_LOCATION_UNAVAILABLE = "Current location is not available to the Agent runtime."
        const val MESSAGE_FOREGROUND_LOCATION_REQUIRED = "Foreground location permission is required before using current location."
        const val MESSAGE_INCOMPLETE_LOCATION = "Geofence location is incomplete. Provide both latitude and longitude."
        const val MESSAGE_MISSING_ACTION_PROMPT = "Geofence action_prompt is required."
        const val MESSAGE_MISSING_RULE_ID = "rule_id is required."
        const val MESSAGE_MISSING_TARGET_CONFIG = "Target ConfigProfile is missing or unavailable."
        const val MESSAGE_RULE_NOT_FOUND_PREFIX = "Geofence rule not found: "
        const val TRIGGER_DWELL = "dwell"
        const val TRIGGER_ENTER = "enter"
        const val TRIGGER_ENTER_EXIT = "enter_exit"
        const val TRIGGER_EXIT = "exit"
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
