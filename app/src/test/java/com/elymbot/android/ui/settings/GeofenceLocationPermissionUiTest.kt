package com.elymbot.android.ui.settings

import com.elymbot.android.feature.geofence.domain.runtime.GeofenceCurrentLocationResult
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceCurrentLocationSnapshot
import com.elymbot.android.feature.geofence.domain.runtime.GeofencePermissionStatus
import com.elymbot.android.feature.geofence.presentation.GeofenceLocationPermissionUiAction
import com.elymbot.android.feature.geofence.presentation.GeofenceLocationPermissionUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeofenceLocationPermissionUiTest {
    @Test
    fun use_current_location_without_foreground_permission_requests_foreground_location() {
        val decision = GeofenceLocationPermissionUiState().onUseCurrentLocation(
            GeofencePermissionStatus(foregroundGranted = false, backgroundGranted = false),
        )

        assertEquals(GeofenceLocationPermissionUiAction.REQUEST_FOREGROUND_LOCATION, decision.action)
        assertTrue(decision.state.foregroundPermissionRequestPending)
        assertFalse(decision.state.currentLocationLoading)
    }

    @Test
    fun granted_foreground_permission_fetches_current_location() {
        val decision = GeofenceLocationPermissionUiState(
            foregroundPermissionRequestPending = true,
        ).onForegroundPermissionResult(granted = true)

        assertEquals(GeofenceLocationPermissionUiAction.FETCH_CURRENT_LOCATION, decision.action)
        assertFalse(decision.state.foregroundPermissionRequestPending)
        assertTrue(decision.state.currentLocationLoading)
    }

    @Test
    fun denied_foreground_permission_keeps_draft_owned_state_intact() {
        val selectorBefore = GeofenceMapSelectorStateTestSelector.state()

        val decision = GeofenceLocationPermissionUiState(
            foregroundPermissionRequestPending = true,
        ).onForegroundPermissionResult(granted = false)

        assertEquals(GeofenceLocationPermissionUiAction.NONE, decision.action)
        assertFalse(decision.state.foregroundPermissionRequestPending)
        assertTrue(decision.state.message.contains("permission", ignoreCase = true))
        assertEquals(selectorBefore, GeofenceMapSelectorStateTestSelector.state())
    }

    @Test
    fun current_location_result_success_clears_loading_message() {
        val state = GeofenceLocationPermissionUiState(currentLocationLoading = true)
        val next = state.onCurrentLocationResult(
            GeofenceCurrentLocationResult.Success(
                GeofenceCurrentLocationSnapshot(
                    latitude = 31.2304,
                    longitude = 121.4737,
                    accuracyMeters = 18f,
                    capturedAtMillis = 100L,
                ),
            ),
        )

        assertFalse(next.currentLocationLoading)
        assertTrue(next.message.contains("updated", ignoreCase = true))
        assertEquals("", next.errorMessage)
    }

    @Test
    fun background_permission_guide_is_explicit_and_dismissible() {
        val guided = GeofenceLocationPermissionUiState().showBackgroundPermissionGuide()

        assertTrue(guided.backgroundPermissionGuideVisible)
        assertTrue(guided.message.contains("background", ignoreCase = true))
        assertFalse(guided.dismissBackgroundPermissionGuide().backgroundPermissionGuideVisible)
    }
}

private object GeofenceMapSelectorStateTestSelector {
    fun state() = com.elymbot.android.feature.geofence.presentation.GeofenceMapSelectorState(
        latitudeText = "31.2304",
        longitudeText = "121.4737",
        radiusText = "150",
        addressLabel = "Office",
        availability = com.elymbot.android.feature.geofence.domain.runtime.GeofenceMapAvailability.AVAILABLE,
    )
}
