package com.elymbot.android.ui.settings

import com.elymbot.android.feature.geofence.domain.model.GeofenceActionType
import com.elymbot.android.feature.geofence.domain.model.GeofenceExecutionRecord
import com.elymbot.android.feature.geofence.domain.model.GeofenceRegion
import com.elymbot.android.feature.geofence.domain.model.GeofenceRule
import com.elymbot.android.feature.geofence.domain.model.GeofenceRuleStatus
import com.elymbot.android.feature.geofence.domain.model.GeofenceTransition
import com.elymbot.android.feature.geofence.presentation.buildGeofenceRuleRunUiPresentations
import com.elymbot.android.feature.geofence.presentation.buildGeofenceRulesUiPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeofenceRulesPresentationTest {

    @Test
    fun `geofence rules page shows two items per page`() {
        val presentation = buildGeofenceRulesUiPresentation(
            rules = sampleRules(5),
            requestedPage = 1,
        )

        assertEquals(1, presentation.currentPage)
        assertEquals(3, presentation.totalPages)
        assertEquals(listOf("rule-1", "rule-2"), presentation.visibleRules.map { it.ruleId })
        assertFalse(presentation.canGoPrevious)
        assertTrue(presentation.canGoNext)
    }

    @Test
    fun `geofence rules page exposes empty state`() {
        val presentation = buildGeofenceRulesUiPresentation(
            rules = emptyList(),
            requestedPage = 3,
        )

        assertEquals(1, presentation.currentPage)
        assertEquals(1, presentation.totalPages)
        assertTrue(presentation.visibleRules.isEmpty())
        assertFalse(presentation.canGoPrevious)
        assertFalse(presentation.canGoNext)
    }

    @Test
    fun `geofence card includes region trigger action and status summaries`() {
        val presentation = buildGeofenceRulesUiPresentation(
            rules = sampleRules(1),
            requestedPage = 1,
        )

        val item = presentation.visibleRules.single()

        assertEquals("Office", item.name)
        assertTrue(item.regionSummary.contains("Office Gate"))
        assertTrue(item.regionSummary.contains("150m"))
        assertFalse(item.regionSummary.contains("31.2304"))
        assertFalse(item.regionSummary.contains("121.4737"))
        assertEquals("enter, dwell", item.triggerSummary)
        assertTrue(item.actionSummary.contains(GeofenceActionType.AGENT_PROMPT.persistedValue))
        assertEquals(GeofenceRuleStatus.ACTIVE.persistedValue, item.statusSummary)
    }

    @Test
    fun `geofence requested page is clamped to available range`() {
        val presentation = buildGeofenceRulesUiPresentation(
            rules = sampleRules(5),
            requestedPage = 99,
        )

        assertEquals(3, presentation.currentPage)
        assertEquals(listOf("rule-5"), presentation.visibleRules.map { it.ruleId })
        assertTrue(presentation.canGoPrevious)
        assertFalse(presentation.canGoNext)
    }

    @Test
    fun `geofence run presentation prefers delivery summary then error detail`() {
        val presentations = buildGeofenceRuleRunUiPresentations(
            listOf(
                GeofenceExecutionRecord(
                    executionId = "run-1",
                    ruleId = "rule-1",
                    regionId = "region-1",
                    configId = "config-1",
                    transition = GeofenceTransition.ENTER,
                    startedAt = 10L,
                    completedAt = 20L,
                    status = "succeeded",
                    deliverySummary = "Delivered to app chat",
                    errorMessage = "ignored",
                ),
                GeofenceExecutionRecord(
                    executionId = "run-2",
                    ruleId = "rule-1",
                    regionId = "region-1",
                    configId = "config-1",
                    transition = GeofenceTransition.EXIT,
                    startedAt = 30L,
                    status = "failed",
                    errorCode = "delivery_failed",
                    errorMessage = "Conversation missing",
                ),
            ),
        )

        assertEquals("run-1", presentations[0].executionId)
        assertEquals("Delivered to app chat", presentations[0].summary)
        assertEquals("Conversation missing", presentations[1].summary)
        assertEquals("delivery_failed", presentations[1].errorCode)
    }

    private fun sampleRules(count: Int): List<GeofenceRule> {
        return (1..count).map { index ->
            GeofenceRule(
                ruleId = "rule-$index",
                name = if (index == 1) "Office" else "Rule $index",
                description = "Rule $index description",
                triggerEnter = true,
                triggerDwell = index == 1,
                dwellDelayMillis = 300_000L,
                actionType = GeofenceActionType.AGENT_PROMPT,
                actionPrompt = "Summarize the location event",
                targetPlatform = "app_chat",
                targetConversationId = "chat-main",
                targetBotId = "bot-1",
                targetConfigProfileId = "config-1",
                targetProviderId = "provider-1",
                status = GeofenceRuleStatus.ACTIVE,
                lastTriggeredAt = 1_735_000_000_000L + index,
                regions = listOf(
                    GeofenceRegion(
                        regionId = "region-$index",
                        ruleId = "rule-$index",
                        label = "Office Gate",
                        latitude = 31.2304,
                        longitude = 121.4737,
                        radiusMeters = 150f,
                    ),
                ),
            )
        }
    }
}
