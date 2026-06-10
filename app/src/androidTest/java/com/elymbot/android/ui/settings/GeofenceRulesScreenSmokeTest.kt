package com.elymbot.android.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class GeofenceRulesScreenSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun geofenceRulesFabOpensCreateCard() {
        composeRule.setContent {
            MaterialTheme {
                GeofenceRulesContent(
                    rules = emptyList(),
                )
            }
        }

        composeRule.onNodeWithTag("geofence-rules-add-fab").performClick()

        composeRule.onNodeWithTag("geofence-create-name").assertExists()
        composeRule.onNodeWithTag("geofence-create-radius").assertExists()
        composeRule.onNodeWithTag("geofence-map-selector").assertExists()
        composeRule.onNodeWithTag("geofence-network-map").assertExists()
        composeRule.onNodeWithTag("geofence-use-current-location").assertExists()
        composeRule.onNodeWithTag("geofence-map-radius-slider").assertExists()
        composeRule.onNodeWithTag("geofence-create-action-prompt").assertExists()
    }
}
