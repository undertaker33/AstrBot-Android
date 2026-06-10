package com.elymbot.android.feature.geofence

import com.elymbot.android.feature.geofence.domain.model.GeofenceActionType
import com.elymbot.android.feature.geofence.domain.model.GeofenceExecutionRecord
import com.elymbot.android.feature.geofence.domain.model.GeofenceRegion
import com.elymbot.android.feature.geofence.domain.model.GeofenceRule
import com.elymbot.android.feature.geofence.domain.model.GeofenceRuleValidation
import com.elymbot.android.feature.geofence.domain.model.GeofenceTransition
import com.elymbot.android.feature.geofence.domain.model.GeofenceValidationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeofenceRuleValidationTest {
    @Test(expected = GeofenceValidationException::class)
    fun rejects_blank_rule_name() {
        GeofenceRuleValidation.requireValidRule(validRule().copy(name = "  "))
    }

    @Test(expected = GeofenceValidationException::class)
    fun rejects_rule_without_trigger() {
        GeofenceRuleValidation.requireValidRule(
            validRule().copy(
                triggerEnter = false,
                triggerExit = false,
                triggerDwell = false,
            ),
        )
    }

    @Test(expected = GeofenceValidationException::class)
    fun rejects_invalid_latitude_and_longitude() {
        GeofenceRuleValidation.requireValidRegion(
            validRegion().copy(latitude = 91.0, longitude = -181.0),
        )
    }

    @Test(expected = GeofenceValidationException::class)
    fun rejects_radius_below_hard_floor() {
        GeofenceRuleValidation.requireValidRegion(
            validRegion().copy(radiusMeters = GeofenceRuleValidation.MIN_RADIUS_METERS - 1f),
        )
    }

    @Test(expected = GeofenceValidationException::class)
    fun rejects_negative_dwell_delay() {
        GeofenceRuleValidation.requireValidRule(validRule().copy(dwellDelayMillis = -1L))
    }

    @Test(expected = GeofenceValidationException::class)
    fun rejects_zero_dwell_delay_when_dwell_trigger_is_enabled() {
        GeofenceRuleValidation.requireValidRule(validRule().copy(triggerEnter = false, triggerDwell = true))
    }

    @Test(expected = GeofenceValidationException::class)
    fun rejects_negative_minimum_trigger_interval() {
        GeofenceRuleValidation.requireValidRule(validRule().copy(minimumTriggerIntervalMillis = -1L))
    }

    @Test(expected = GeofenceValidationException::class)
    fun rejects_negative_execution_record_time() {
        GeofenceRuleValidation.requireValidExecutionRecord(validExecution().copy(startedAt = -1L))
    }

    @Test(expected = GeofenceValidationException::class)
    fun rejects_execution_record_completed_before_started() {
        GeofenceRuleValidation.requireValidExecutionRecord(
            validExecution().copy(startedAt = 10L, completedAt = 9L),
        )
    }

    @Test
    fun allows_radius_below_recommended_floor_and_marks_warning_state() {
        val region = validRegion().copy(radiusMeters = 75f)

        GeofenceRuleValidation.requireValidRegion(region)

        assertEquals(
            GeofenceRuleValidation.RadiusStability.BELOW_RECOMMENDED,
            GeofenceRuleValidation.radiusStability(region),
        )
        assertTrue(region.radiusMeters < GeofenceRuleValidation.RECOMMENDED_MIN_RADIUS_METERS)
    }

    private fun validRule(): GeofenceRule =
        GeofenceRule(
            ruleId = "rule-1",
            name = "Office reminder",
            description = "Trigger at office",
            triggerEnter = true,
            actionType = GeofenceActionType.AGENT_PROMPT,
            actionPrompt = "Remind me",
        )

    private fun validRegion(): GeofenceRegion =
        GeofenceRegion(
            regionId = "region-1",
            ruleId = "rule-1",
            label = "Office",
            latitude = 31.2304,
            longitude = 121.4737,
            radiusMeters = 100f,
        )

    private fun validExecution(): GeofenceExecutionRecord =
        GeofenceExecutionRecord(
            executionId = "execution-1",
            ruleId = "rule-1",
            regionId = "region-1",
            configId = "config-1",
            transition = GeofenceTransition.ENTER,
            startedAt = 10L,
            completedAt = 0L,
        )
}
