package com.elymbot.android.feature.geofence.presentation

import com.elymbot.android.feature.geofence.domain.model.GeofenceRuleValidation
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceCurrentLocationResult
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceCurrentLocationSnapshot
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceMapAvailability
import com.elymbot.android.feature.geofence.domain.runtime.GeofencePermissionStatus
import java.util.Locale

data class GeofenceMapSelection(
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
    val addressLabel: String,
)

data class GeofenceMapCameraCenter(
    val latitude: Double,
    val longitude: Double,
    val isDefaultCenter: Boolean,
)

data class GeofenceMapSelectorState(
    val latitudeText: String,
    val longitudeText: String,
    val radiusText: String,
    val addressLabel: String,
    val availability: GeofenceMapAvailability,
    val mapLoaded: Boolean = availability != GeofenceMapAvailability.AVAILABLE,
) {
    val selection: GeofenceMapSelection?
        get() {
            val latitude = latitudeText.trim().toDoubleOrNull()?.takeIf { it in -90.0..90.0 }
            val longitude = longitudeText.trim().toDoubleOrNull()?.takeIf { it in -180.0..180.0 }
            val radius = radiusText.trim().toFloatOrNull()
                ?.takeIf { it >= GeofenceRuleValidation.MIN_RADIUS_METERS }
            return if (latitude != null && longitude != null && radius != null) {
                GeofenceMapSelection(
                    latitude = latitude,
                    longitude = longitude,
                    radiusMeters = radius,
                    addressLabel = addressLabel.trim(),
                )
            } else {
                null
            }
        }

    val cameraCenter: GeofenceMapCameraCenter
        get() {
            val selected = selection
            return if (selected != null) {
                GeofenceMapCameraCenter(
                    latitude = selected.latitude,
                    longitude = selected.longitude,
                    isDefaultCenter = false,
                )
            } else {
                GeofenceMapCameraCenter(
                    latitude = DefaultCameraLatitude,
                    longitude = DefaultCameraLongitude,
                    isDefaultCenter = true,
                )
            }
        }

    val shouldShowMap: Boolean
        get() = true

    val shouldShowFallback: Boolean
        get() = false

    fun onMapClick(latitude: Double, longitude: Double): GeofenceMapSelectorState =
        copy(
            latitudeText = latitude.formatSelectorNumber(),
            longitudeText = longitude.formatSelectorNumber(),
        )

    fun onLatitudeTextChange(value: String): GeofenceMapSelectorState =
        copy(latitudeText = value)

    fun onLongitudeTextChange(value: String): GeofenceMapSelectorState =
        copy(longitudeText = value)

    fun onRadiusTextChange(value: String): GeofenceMapSelectorState =
        copy(radiusText = value)

    fun onRadiusSliderChange(value: Float): GeofenceMapSelectorState =
        copy(radiusText = value.coerceAtLeast(GeofenceRuleValidation.MIN_RADIUS_METERS).formatRadiusNumber())

    fun onAddressLabelChange(value: String): GeofenceMapSelectorState =
        copy(addressLabel = value)

    fun withAvailability(nextAvailability: GeofenceMapAvailability): GeofenceMapSelectorState =
        copy(
            availability = nextAvailability,
            mapLoaded = nextAvailability != GeofenceMapAvailability.AVAILABLE,
        )

    fun onMapLoaded(): GeofenceMapSelectorState =
        copy(mapLoaded = true)

    fun onMapLoadTimeout(): GeofenceMapSelectorState =
        this

    fun applyCurrentLocation(location: GeofenceCurrentLocationSnapshot): GeofenceMapSelectorState =
        onMapClick(latitude = location.latitude, longitude = location.longitude)

    private companion object {
        const val DefaultCameraLatitude = 31.2304
        const val DefaultCameraLongitude = 121.4737
    }
}

enum class GeofenceLocationPermissionUiAction {
    NONE,
    REQUEST_FOREGROUND_LOCATION,
    FETCH_CURRENT_LOCATION,
    OPEN_BACKGROUND_LOCATION_SETTINGS,
}

data class GeofenceLocationPermissionUiDecision(
    val state: GeofenceLocationPermissionUiState,
    val action: GeofenceLocationPermissionUiAction,
)

data class GeofenceLocationPermissionUiState(
    val foregroundPermissionRequestPending: Boolean = false,
    val currentLocationLoading: Boolean = false,
    val backgroundPermissionGuideVisible: Boolean = false,
    val message: String = "",
    val errorMessage: String = "",
) {
    fun onUseCurrentLocation(status: GeofencePermissionStatus): GeofenceLocationPermissionUiDecision {
        return if (status.foregroundGranted) {
            GeofenceLocationPermissionUiDecision(
                state = copy(
                    foregroundPermissionRequestPending = false,
                    currentLocationLoading = true,
                    message = "",
                    errorMessage = "",
                ),
                action = GeofenceLocationPermissionUiAction.FETCH_CURRENT_LOCATION,
            )
        } else {
            GeofenceLocationPermissionUiDecision(
                state = copy(
                    foregroundPermissionRequestPending = true,
                    currentLocationLoading = false,
                    message = "Foreground location permission is required to use current location.",
                    errorMessage = "",
                ),
                action = GeofenceLocationPermissionUiAction.REQUEST_FOREGROUND_LOCATION,
            )
        }
    }

    fun onForegroundPermissionResult(granted: Boolean): GeofenceLocationPermissionUiDecision {
        return if (granted) {
            GeofenceLocationPermissionUiDecision(
                state = copy(
                    foregroundPermissionRequestPending = false,
                    currentLocationLoading = true,
                    message = "",
                    errorMessage = "",
                ),
                action = GeofenceLocationPermissionUiAction.FETCH_CURRENT_LOCATION,
            )
        } else {
            GeofenceLocationPermissionUiDecision(
                state = copy(
                    foregroundPermissionRequestPending = false,
                    currentLocationLoading = false,
                    message = "Location permission was denied. Your draft was kept.",
                    errorMessage = "",
                ),
                action = GeofenceLocationPermissionUiAction.NONE,
            )
        }
    }

    fun onCurrentLocationResult(result: GeofenceCurrentLocationResult): GeofenceLocationPermissionUiState {
        return when (result) {
            is GeofenceCurrentLocationResult.Success -> copy(
                foregroundPermissionRequestPending = false,
                currentLocationLoading = false,
                message = "Current location updated.",
                errorMessage = "",
            )
            is GeofenceCurrentLocationResult.Failure -> copy(
                foregroundPermissionRequestPending = false,
                currentLocationLoading = false,
                message = "Current location failed. Your draft was kept.",
                errorMessage = result.message.ifBlank { result.errorCode },
            )
        }
    }

    fun showBackgroundPermissionGuide(): GeofenceLocationPermissionUiState =
        copy(
            backgroundPermissionGuideVisible = true,
            message = "Background location is needed for enabled geofence triggers. Android 11+ requires granting it in system settings.",
        )

    fun dismissBackgroundPermissionGuide(): GeofenceLocationPermissionUiState =
        copy(backgroundPermissionGuideVisible = false)

    fun onBackgroundSettingsOpened(): GeofenceLocationPermissionUiDecision =
        GeofenceLocationPermissionUiDecision(
            state = copy(
                backgroundPermissionGuideVisible = false,
                message = "Background location settings opened. Save can continue as permission_required if it remains unavailable.",
            ),
            action = GeofenceLocationPermissionUiAction.OPEN_BACKGROUND_LOCATION_SETTINGS,
        )
}

private fun Double.formatSelectorNumber(): String =
    String.format(Locale.US, "%.6f", this).trimEnd('0').trimEnd('.')

private fun Float.formatRadiusNumber(): String =
    if (this % 1f == 0f) toInt().toString() else String.format(Locale.US, "%.1f", this).trimEnd('0').trimEnd('.')
