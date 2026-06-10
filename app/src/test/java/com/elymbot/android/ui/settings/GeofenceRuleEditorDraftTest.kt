package com.elymbot.android.ui.settings

import com.elymbot.android.core.runtime.context.RuntimePlatform
import com.elymbot.android.feature.bot.domain.model.BotProfile
import com.elymbot.android.feature.geofence.domain.model.GeofenceActionType
import com.elymbot.android.feature.geofence.domain.model.GeofenceRegion
import com.elymbot.android.feature.geofence.domain.model.GeofenceRule
import com.elymbot.android.feature.geofence.domain.model.GeofenceRuleStatus
import com.elymbot.android.feature.geofence.domain.model.GeofenceRuleValidation
import com.elymbot.android.feature.geofence.domain.runtime.GeofencePermissionStatus
import com.elymbot.android.feature.geofence.presentation.GeofenceRuleEditorDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeofenceRuleEditorDraftTest {

    @Test
    fun `editor draft validates latitude longitude and radius fields`() {
        val invalid = validDraft().copy(
            latitude = "91",
            longitude = "-181",
            radiusMeters = (GeofenceRuleValidation.MIN_RADIUS_METERS - 1).toString(),
        )

        assertFalse(invalid.canSubmit())
        assertTrue(invalid.missingFields().containsAll(listOf("latitude", "longitude", "radius_meters")))

        assertTrue(validDraft().canSubmit())
    }

    @Test
    fun `editor draft requires at least one trigger`() {
        val draft = validDraft().copy(
            triggerEnter = false,
            triggerExit = false,
            triggerDwell = false,
        )

        assertFalse(draft.canSubmit())
        assertTrue(draft.missingFields().contains("trigger"))
    }

    @Test
    fun `editor draft requires action prompt`() {
        val draft = validDraft().copy(actionPrompt = "")

        assertFalse(draft.canSubmit())
        assertTrue(draft.missingFields().contains("action_prompt"))
    }

    @Test
    fun `enabled editor draft only requires user selectable target context`() {
        val draft = validDraft().copy(
            selectedBotId = "",
            configProfileId = "",
            providerId = "",
            personaId = "",
            conversationId = "",
            platform = "",
        )

        assertFalse(draft.canSubmit())
        assertTrue(
            draft.missingFields().containsAll(
                listOf("bot_id", "conversation_id"),
            ),
        )
        assertFalse(draft.missingFields().contains("platform"))
        assertFalse(draft.missingFields().contains("config_profile_id"))
        assertFalse(draft.missingFields().contains("persona_id"))
        assertFalse(draft.missingFields().contains("provider_id"))
        assertTrue(draft.copy(enabled = false).canSubmit())
    }

    @Test
    fun `enabled draft derives hidden config persona and provider from selected bot`() {
        val selectedBot = sampleBot()

        val created = validDraft().copy(
            configProfileId = "",
            personaId = "",
            providerId = "",
        ).toRule(selectedBot = selectedBot)

        // skipcq: KT-W1042
        assertEquals("config-1", created.targetConfigProfileId)
        // skipcq: KT-W1042
        assertEquals("persona-1", created.targetPersonaId)
        // skipcq: KT-W1042
        assertEquals("provider-1", created.targetProviderId)
    }

    @Test
    fun `enabled draft defaults blank platform to app chat`() {
        val created = validDraft().copy(platform = "").toRule(selectedBot = sampleBot())

        assertEquals(RuntimePlatform.APP_CHAT.wireValue, created.targetPlatform)
    }

    @Test
    fun `blank region label falls back to address label or rule name`() {
        val addressRegion = validDraft().copy(
            regionLabel = "",
            addressLabel = "Office address",
        // skipcq: KT-W1042
        ).toRegions(ruleId = "rule-1").single()
        val nameRegion = validDraft().copy(
            regionLabel = "",
            addressLabel = "",
        ).toRegions(ruleId = "rule-2").single()

        assertEquals("Office address", addressRegion.label)
        // skipcq: KT-W1042
        assertEquals("Office", nameRegion.label)
    }

    @Test
    fun `enabled draft saves as permission required when foreground or background permission is missing`() {
        val selectedBot = sampleBot()

        val missingForeground = validDraft().toRule(
            selectedBot = selectedBot,
            permissionStatus = GeofencePermissionStatus(foregroundGranted = false, backgroundGranted = true),
        )
        val missingBackground = validDraft().toRule(
            selectedBot = selectedBot,
            permissionStatus = GeofencePermissionStatus(foregroundGranted = true, backgroundGranted = false),
        )

        assertEquals(GeofenceRuleStatus.PERMISSION_REQUIRED, missingForeground.status)
        assertEquals(GeofenceRuleStatus.PERMISSION_REQUIRED, missingBackground.status)
    }

    @Test
    fun `enabled draft saves as active when required permissions are present`() {
        val selectedBot = sampleBot()

        val created = validDraft().toRule(
            selectedBot = selectedBot,
            permissionStatus = GeofencePermissionStatus(foregroundGranted = true, backgroundGranted = true),
        )

        assertEquals(GeofenceRuleStatus.ACTIVE, created.status)
    }

    @Test
    fun `disabled draft saves as paused regardless of permission status`() {
        val selectedBot = sampleBot()

        val disabled = validDraft().copy(enabled = false).toRule(
            selectedBot = selectedBot,
            permissionStatus = GeofencePermissionStatus(foregroundGranted = false, backgroundGranted = false),
        )

        assertEquals(GeofenceRuleStatus.PAUSED, disabled.status)
    }

    @Test
    fun `draft can be initialized from existing rule and output rule and region`() {
        val existing = GeofenceRule(
            ruleId = "rule-1",
            name = "Office",
            description = "Arrive at office",
            enabled = true,
            triggerEnter = true,
            triggerDwell = true,
            dwellDelayMillis = 300_000L,
            actionType = GeofenceActionType.WEATHER_FORECAST,
            actionPrompt = "Send weather",
            targetPlatform = RuntimePlatform.QQ_ONEBOT.wireValue,
            targetConversationId = "group:100",
            // skipcq: KT-W1042
            targetBotId = "bot-1",
            targetConfigProfileId = "config-1",
            targetPersonaId = "persona-1",
            targetProviderId = "provider-1",
            minimumTriggerIntervalMillis = 600_000L,
            regions = listOf(
                GeofenceRegion(
                    regionId = "region-1",
                    ruleId = "rule-1",
                    label = "Office Gate",
                    latitude = 31.2304,
                    longitude = 121.4737,
                    radiusMeters = 150f,
                    addressLabel = "Office",
                ),
            ),
            createdAt = 100L,
        )
        val selectedBot = BotProfile(
            id = "bot-1",
            configProfileId = "config-1",
            defaultPersonaId = "persona-1",
            defaultProviderId = "provider-1",
        )

        val draft = GeofenceRuleEditorDraft.fromRule(existing)
        val updatedRule = draft.copy(name = "Office updated").toRule(
            selectedBot = selectedBot,
            existing = existing,
            now = 200L,
        )
        val regions = draft.toRegions(existing.ruleId, existing.regions, now = 200L)

        assertEquals("Office", draft.name)
        assertEquals("Office Gate", draft.regionLabel)
        assertEquals("31.2304", draft.latitude)
        assertEquals("121.4737", draft.longitude)
        assertEquals("150", draft.radiusMeters)
        assertEquals("Office updated", updatedRule.name)
        assertEquals("rule-1", updatedRule.ruleId)
        assertEquals(100L, updatedRule.createdAt)
        assertEquals(200L, updatedRule.updatedAt)
        assertEquals("region-1", regions.single().regionId)
        assertEquals(150f, regions.single().radiusMeters, 0.001f)
    }

    private fun validDraft(): GeofenceRuleEditorDraft {
        return GeofenceRuleEditorDraft(
            name = "Office",
            latitude = "31.2304",
            longitude = "121.4737",
            radiusMeters = "150",
            triggerEnter = true,
            actionPrompt = "Send a location summary",
            platform = RuntimePlatform.APP_CHAT.wireValue,
            conversationId = "chat-main",
            selectedBotId = "bot-1",
            configProfileId = "config-1",
            personaId = "persona-1",
            providerId = "provider-1",
        )
    }

    private fun sampleBot(): BotProfile {
        return BotProfile(
            id = "bot-1",
            configProfileId = "config-1",
            defaultPersonaId = "persona-1",
            defaultProviderId = "provider-1",
        )
    }
}
