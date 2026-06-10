package com.elymbot.android.feature.geofence.presentation

import com.elymbot.android.core.runtime.context.RuntimePlatform
import com.elymbot.android.feature.bot.domain.model.BotProfile
import com.elymbot.android.feature.geofence.domain.model.GeofenceActionType
import com.elymbot.android.feature.geofence.domain.model.GeofenceRegion
import com.elymbot.android.feature.geofence.domain.model.GeofenceRule
import com.elymbot.android.feature.geofence.domain.model.GeofenceRuleStatus
import com.elymbot.android.feature.geofence.domain.model.GeofenceRuleValidation
import com.elymbot.android.feature.geofence.domain.runtime.GeofencePermissionStatus
import java.util.Locale
import java.util.UUID

const val DefaultGeofenceConversationId: String = "chat-main"

data class GeofenceRuleTargetContext(
    val platform: String = RuntimePlatform.APP_CHAT.wireValue,
    val conversationId: String = DefaultGeofenceConversationId,
    val botId: String = "",
    val configProfileId: String = "",
    val personaId: String = "",
    val providerId: String = "",
    val origin: String = "ui",
)

data class GeofenceRuleEditorDraft(
    val name: String = "",
    val description: String = "",
    val enabled: Boolean = true,
    val regionLabel: String = "",
    val latitude: String = "",
    val longitude: String = "",
    val radiusMeters: String = GeofenceRuleValidation.RECOMMENDED_MIN_RADIUS_METERS.toInt().toString(),
    val addressLabel: String = "",
    val triggerEnter: Boolean = true,
    val triggerExit: Boolean = false,
    val triggerDwell: Boolean = false,
    val dwellDelayMillis: String = "300000",
    val actionType: String = GeofenceActionType.AGENT_PROMPT.persistedValue,
    val actionPrompt: String = "",
    val platform: String = RuntimePlatform.APP_CHAT.wireValue,
    val conversationId: String = DefaultGeofenceConversationId,
    val selectedBotId: String = "",
    val configProfileId: String = "",
    val personaId: String = "",
    val providerId: String = "",
    val minimumTriggerIntervalMillis: String = "600000",
) {
    fun canSubmit(): Boolean = missingFields().isEmpty()

    // skipcq: KT-R1006
    fun missingFields(): List<String> {
        val parsedLatitude = parsedLatitude()
        val parsedLongitude = parsedLongitude()
        val parsedRadius = parsedRadiusMeters()
        val parsedDwellDelay = parsedDwellDelayMillis()
        val parsedMinimumInterval = parsedMinimumTriggerIntervalMillis()
        return buildList {
            if (name.trim().isBlank()) add("name")
            if (parsedLatitude == null || parsedLatitude !in -90.0..90.0) add("latitude")
            if (parsedLongitude == null || parsedLongitude !in -180.0..180.0) add("longitude")
            if (parsedRadius == null || parsedRadius < GeofenceRuleValidation.MIN_RADIUS_METERS) add("radius_meters")
            if (!triggerEnter && !triggerExit && !triggerDwell) add("trigger")
            if (triggerDwell && (parsedDwellDelay == null || parsedDwellDelay <= 0L)) add("dwell_delay")
            if (actionPrompt.trim().isBlank()) add("action_prompt")
            if (parsedMinimumInterval == null || parsedMinimumInterval < 0L) add("minimum_trigger_interval")
            if (enabled) {
                if (conversationId.trim().isBlank()) add("conversation_id")
                if (selectedBotId.trim().isBlank()) add("bot_id")
            }
        }
    }

    fun toRule(
        selectedBot: BotProfile,
        existing: GeofenceRule? = null,
        ruleId: String = existing?.ruleId ?: newGeofenceId("rule"),
        now: Long = System.currentTimeMillis(),
        permissionStatus: GeofencePermissionStatus = GeofencePermissionStatus(
            foregroundGranted = true,
            backgroundGranted = true,
        ),
    ): GeofenceRule {
        val resolvedTarget = resolvedTarget(selectedBot)
        val nextStatus = when {
            !enabled -> GeofenceRuleStatus.PAUSED
            !permissionStatus.canRegister -> GeofenceRuleStatus.PERMISSION_REQUIRED
            existing == null -> GeofenceRuleStatus.ACTIVE
            existing.status == GeofenceRuleStatus.PAUSED -> GeofenceRuleStatus.ACTIVE
            existing.status == GeofenceRuleStatus.PERMISSION_REQUIRED -> GeofenceRuleStatus.ACTIVE
            else -> existing.status
        }
        return GeofenceRule(
            ruleId = ruleId,
            name = name.trim(),
            description = description.trim(),
            enabled = enabled,
            triggerEnter = triggerEnter,
            triggerExit = triggerExit,
            triggerDwell = triggerDwell,
            dwellDelayMillis = if (triggerDwell) parsedDwellDelayMillis() ?: 0L else 0L,
            actionType = GeofenceActionType.fromPersistedValue(actionType.trim()),
            actionPrompt = actionPrompt.trim(),
            targetPlatform = resolvedTarget.platform,
            targetConversationId = resolvedTarget.conversationId,
            targetBotId = resolvedTarget.botId,
            targetConfigProfileId = resolvedTarget.configProfileId,
            targetPersonaId = resolvedTarget.personaId,
            targetProviderId = resolvedTarget.providerId,
            minimumTriggerIntervalMillis = parsedMinimumTriggerIntervalMillis() ?: 0L,
            status = nextStatus,
            lastRegisteredAt = existing?.lastRegisteredAt ?: 0L,
            lastTriggeredAt = existing?.lastTriggeredAt ?: 0L,
            // skipcq: KT-R1004
            lastError = existing?.lastError ?: "",
            regions = toRegions(ruleId, existing?.regions.orEmpty(), now),
            createdAt = existing?.createdAt?.takeIf { it > 0L } ?: now,
            updatedAt = now,
        )
    }

    fun toRegions(
        ruleId: String,
        existingRegions: List<GeofenceRegion> = emptyList(),
        now: Long = System.currentTimeMillis(),
    ): List<GeofenceRegion> {
        val existing = existingRegions.firstOrNull()
        val fallbackLabel = addressLabel.trim()
            .ifBlank { name.trim() }
            .ifBlank { "Default region" }
        return listOf(
            GeofenceRegion(
                regionId = existing?.regionId ?: newGeofenceId("region"),
                ruleId = ruleId,
                label = regionLabel.trim().ifBlank { fallbackLabel },
                latitude = parsedLatitude() ?: 0.0,
                longitude = parsedLongitude() ?: 0.0,
                radiusMeters = parsedRadiusMeters() ?: GeofenceRuleValidation.RECOMMENDED_MIN_RADIUS_METERS,
                addressLabel = addressLabel.trim(),
                sortIndex = existing?.sortIndex ?: 0,
                createdAt = existing?.createdAt?.takeIf { it > 0L } ?: now,
                updatedAt = now,
            ),
        )
    }

    fun resolvedTarget(selectedBot: BotProfile): GeofenceRuleTargetContext =
        GeofenceRuleTargetContext(
            platform = platform.trim().ifBlank { RuntimePlatform.APP_CHAT.wireValue },
            conversationId = conversationId.trim(),
            botId = selectedBotId.trim().ifBlank { selectedBot.id },
            configProfileId = configProfileId.trim().ifBlank { selectedBot.configProfileId },
            personaId = personaId.trim().ifBlank { selectedBot.defaultPersonaId },
            providerId = providerId.trim().ifBlank { selectedBot.defaultProviderId },
        )

    private fun parsedLatitude(): Double? = latitude.trim().toDoubleOrNull()

    private fun parsedLongitude(): Double? = longitude.trim().toDoubleOrNull()

    private fun parsedRadiusMeters(): Float? = radiusMeters.trim().toFloatOrNull()

    private fun parsedDwellDelayMillis(): Long? = dwellDelayMillis.trim().toLongOrNull()

    private fun parsedMinimumTriggerIntervalMillis(): Long? =
        minimumTriggerIntervalMillis.trim().toLongOrNull()

    companion object {
        fun fromRule(rule: GeofenceRule): GeofenceRuleEditorDraft {
            val region = rule.regions.firstOrNull()
            return GeofenceRuleEditorDraft(
                name = rule.name,
                description = rule.description,
                enabled = rule.enabled,
                regionLabel = region?.label.orEmpty(),
                latitude = region?.latitude?.formatGeofenceNumber().orEmpty(),
                longitude = region?.longitude?.formatGeofenceNumber().orEmpty(),
                radiusMeters = region?.radiusMeters?.formatGeofenceNumber()
                    ?: GeofenceRuleValidation.RECOMMENDED_MIN_RADIUS_METERS.toInt().toString(),
                addressLabel = region?.addressLabel.orEmpty(),
                triggerEnter = rule.triggerEnter,
                triggerExit = rule.triggerExit,
                triggerDwell = rule.triggerDwell,
                dwellDelayMillis = rule.dwellDelayMillis.toString(),
                actionType = rule.actionType.persistedValue,
                actionPrompt = rule.actionPrompt,
                platform = rule.targetPlatform,
                conversationId = rule.targetConversationId,
                selectedBotId = rule.targetBotId,
                configProfileId = rule.targetConfigProfileId,
                personaId = rule.targetPersonaId,
                providerId = rule.targetProviderId,
                minimumTriggerIntervalMillis = rule.minimumTriggerIntervalMillis.toString(),
            )
        }

        fun fromTargetContext(targetContext: GeofenceRuleTargetContext): GeofenceRuleEditorDraft {
            return GeofenceRuleEditorDraft(
                platform = targetContext.platform,
                conversationId = targetContext.conversationId,
                selectedBotId = targetContext.botId,
                configProfileId = targetContext.configProfileId,
                personaId = targetContext.personaId,
                providerId = targetContext.providerId,
            )
        }
    }
}

internal fun newGeofenceId(prefix: String): String = "$prefix-${UUID.randomUUID()}"

private fun Double.formatGeofenceNumber(): String =
    String.format(Locale.US, "%.6f", this).trimEnd('0').trimEnd('.')

private fun Float.formatGeofenceNumber(): String =
    if (this % 1f == 0f) toInt().toString() else String.format(Locale.US, "%.2f", this).trimEnd('0').trimEnd('.')
