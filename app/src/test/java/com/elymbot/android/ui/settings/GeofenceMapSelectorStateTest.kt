package com.elymbot.android.ui.settings

import com.elymbot.android.feature.geofence.domain.runtime.GeofenceCurrentLocationSnapshot
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceMapAvailability
import com.elymbot.android.feature.geofence.presentation.GeofenceMapSelectorState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeofenceMapSelectorStateTest {
    @Test
    fun map_click_updates_center_without_changing_radius() {
        val state = selectorState()

        val updated = state.onMapClick(latitude = 32.0615, longitude = 118.7913)

        assertEquals("32.0615", updated.latitudeText)
        // skipcq: KT-W1042
        assertEquals("118.7913", updated.longitudeText)
        assertEquals("150", updated.radiusText)
        assertEquals(32.0615, updated.selection?.latitude ?: 0.0, 0.000001)
        assertEquals(118.7913, updated.selection?.longitude ?: 0.0, 0.000001)
        assertEquals(150f, updated.selection?.radiusMeters ?: 0f, 0.001f)
    }

    @Test
    fun radius_slider_and_numeric_input_share_selection_state() {
        val sliderUpdated = selectorState().onRadiusSliderChange(245f)

        assertEquals("245", sliderUpdated.radiusText)
        assertEquals(245f, sliderUpdated.selection?.radiusMeters ?: 0f, 0.001f)

        val textUpdated = sliderUpdated.onRadiusTextChange("320.5")

        assertEquals("320.5", textUpdated.radiusText)
        assertEquals(320.5f, textUpdated.selection?.radiusMeters ?: 0f, 0.001f)
    }

    @Test
    fun invalid_coordinates_or_radius_do_not_produce_effective_selection() {
        assertNull(selectorState().onLatitudeTextChange("91").selection)
        assertNull(selectorState().onLongitudeTextChange("-181").selection)
        assertNull(selectorState().onRadiusTextChange("20").selection)
    }

    @Test
    fun available_map_with_empty_coordinates_still_shows_map_without_effective_selection() {
        val state = selectorState(latitudeText = "", longitudeText = "")

        assertNull(state.selection)
        assertTrue(state.shouldShowMap)
        assertFalse(state.shouldShowFallback)
        assertTrue(state.cameraCenter.isDefaultCenter)
        assertEquals("", state.latitudeText)
        assertEquals("", state.longitudeText)
    }

    @Test
    fun map_click_from_empty_coordinates_creates_effective_selection() {
        val state = selectorState(latitudeText = "", longitudeText = "")

        val updated = state.onMapClick(latitude = 32.0615, longitude = 118.7913)

        assertEquals("32.0615", updated.latitudeText)
        assertEquals("118.7913", updated.longitudeText)
        assertEquals(32.0615, updated.selection?.latitude ?: 0.0, 0.000001)
        assertEquals(118.7913, updated.selection?.longitude ?: 0.0, 0.000001)
        assertFalse(updated.cameraCenter.isDefaultCenter)
    }

    @Test
    fun missing_map_key_and_load_failure_stay_map_first_without_manual_fallback() {
        val missingKey = selectorState(availability = GeofenceMapAvailability.MISSING_API_KEY)
        val loadFailed = selectorState(availability = GeofenceMapAvailability.LOAD_FAILED)

        assertTrue(missingKey.shouldShowMap)
        assertFalse(missingKey.shouldShowFallback)
        assertTrue(loadFailed.shouldShowMap)
        assertFalse(loadFailed.shouldShowFallback)
    }

    @Test
    fun available_unloaded_map_timeout_keeps_network_map_selector() {
        val state = selectorState(
            availability = GeofenceMapAvailability.AVAILABLE,
            mapLoaded = false,
        )

        val timedOut = state.onMapLoadTimeout()

        assertEquals(GeofenceMapAvailability.AVAILABLE, timedOut.availability)
        assertTrue(timedOut.shouldShowMap)
        assertFalse(timedOut.shouldShowFallback)
    }

    @Test
    fun loaded_map_does_not_timeout_to_fallback() {
        val state = selectorState(
            availability = GeofenceMapAvailability.AVAILABLE,
            mapLoaded = false,
        ).onMapLoaded()

        val timedOut = state.onMapLoadTimeout()

        assertEquals(GeofenceMapAvailability.AVAILABLE, timedOut.availability)
        assertTrue(timedOut.mapLoaded)
        assertTrue(timedOut.shouldShowMap)
    }

    @Test
    fun load_timeout_retains_current_selection_text() {
        val state = selectorState(
            latitudeText = "",
            longitudeText = "118.7913",
            radiusText = "175",
            availability = GeofenceMapAvailability.AVAILABLE,
            mapLoaded = false,
        )

        val timedOut = state.onMapLoadTimeout()

        assertEquals("", timedOut.latitudeText)
        assertEquals("118.7913", timedOut.longitudeText)
        assertEquals("175", timedOut.radiusText)
        assertNull(timedOut.selection)
    }

    @Test
    fun current_location_success_updates_selection_and_keeps_radius() {
        val updated = selectorState().applyCurrentLocation(
            GeofenceCurrentLocationSnapshot(
                latitude = 35.681236,
                longitude = 139.767125,
                accuracyMeters = 24f,
                capturedAtMillis = 100L,
            ),
        )

        assertEquals("35.681236", updated.latitudeText)
        assertEquals("139.767125", updated.longitudeText)
        assertEquals("150", updated.radiusText)
        assertEquals(35.681236, updated.selection?.latitude ?: 0.0, 0.000001)
        assertEquals(139.767125, updated.selection?.longitude ?: 0.0, 0.000001)
    }

    private fun selectorState(
        latitudeText: String = "31.2304",
        longitudeText: String = "121.4737",
        radiusText: String = "150",
        availability: GeofenceMapAvailability = GeofenceMapAvailability.AVAILABLE,
        mapLoaded: Boolean = availability != GeofenceMapAvailability.AVAILABLE,
    ): GeofenceMapSelectorState {
        return GeofenceMapSelectorState(
            latitudeText = latitudeText,
            longitudeText = longitudeText,
            radiusText = radiusText,
            addressLabel = "Office",
            availability = availability,
            mapLoaded = mapLoaded,
        )
    }
}
