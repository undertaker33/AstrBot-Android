package com.elymbot.android.feature.geofence.runtime

import android.annotation.SuppressLint
import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.elymbot.android.core.common.logging.RuntimeLogger
import com.elymbot.android.feature.geofence.domain.model.GeofenceRule
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceCurrentLocationPort
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceCurrentLocationResult
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceCurrentLocationSnapshot
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceMapAvailability
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceMapAvailabilityPort
import com.elymbot.android.feature.geofence.domain.runtime.GeofencePermissionStatus
import com.elymbot.android.feature.geofence.domain.runtime.GeofencePermissionStatusPort
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.tasks.await

internal class AndroidGeofencePermissionStatusPort @Inject constructor(
    @ApplicationContext private val context: Context,
) : GeofencePermissionStatusPort {
    override fun currentStatus(): GeofencePermissionStatus {
        val fineGranted = context.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarseGranted = context.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        val backgroundGranted = context.hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        return GeofencePermissionStatus(
            foregroundGranted = fineGranted || coarseGranted,
            backgroundGranted = backgroundGranted,
        )
    }

    private fun Context.hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}

internal class GeofencePlayServicesAvailability @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun isAvailable(): Boolean =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
}

internal class AndroidGeofenceMapAvailabilityPort @Inject constructor(
) : GeofenceMapAvailabilityPort {
    override fun currentAvailability(): GeofenceMapAvailability = GeofenceMapAvailability.AVAILABLE
}

internal class AndroidGeofenceCurrentLocationPort @Inject constructor(
    @ApplicationContext private val context: Context,
    private val permissionStatusPort: GeofencePermissionStatusPort,
) : GeofenceCurrentLocationPort {
    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(): GeofenceCurrentLocationResult {
        if (!permissionStatusPort.currentStatus().foregroundGranted) {
            return GeofenceCurrentLocationResult.Failure(
                errorCode = "foreground_permission_required",
                message = "Foreground location permission is required.",
            )
        }
        return runCatching {
            val cancellationTokenSource = CancellationTokenSource()
            val current = fusedLocationClient
                .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellationTokenSource.token)
                .await()
            val location = current ?: fusedLocationClient.lastLocation.await()
            if (location == null) {
                GeofenceCurrentLocationResult.Failure(
                    errorCode = "location_unavailable",
                    message = "Current location is unavailable.",
                )
            } else {
                GeofenceCurrentLocationResult.Success(
                    GeofenceCurrentLocationSnapshot(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracyMeters = location.accuracy,
                        capturedAtMillis = location.time.takeIf { it > 0L } ?: System.currentTimeMillis(),
                    ),
                )
            }
        }.getOrElse { error ->
            GeofenceCurrentLocationResult.Failure(
                errorCode = "location_failed",
                message = error.message ?: error.javaClass.simpleName,
            )
        }
    }
}

internal class AndroidGeofenceRegistrationBackend @Inject constructor(
    @ApplicationContext private val context: Context,
    private val runtimeLogger: RuntimeLogger,
) : GeofenceRegistrationBackend {
    private val geofencingClient by lazy { LocationServices.getGeofencingClient(context) }

    @SuppressLint("MissingPermission")
    override suspend fun replaceRegisteredGeofences(request: GeofenceRegistrationRequest) {
        val pendingIntent = transitionPendingIntent()
        runCatching {
            geofencingClient.removeGeofences(pendingIntent).await()
        }.onFailure { error ->
            runtimeLogger.append("Geofence remove before replace skipped: ${error.javaClass.simpleName}")
        }
        if (request.regions.isEmpty()) {
            return
        }
        geofencingClient.addGeofences(
            GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofences(request.regions.map(::toGoogleGeofence))
                .build(),
            pendingIntent,
        ).await()
    }

    private fun transitionPendingIntent(): PendingIntent {
        val intent = Intent(context, GeofenceTransitionReceiver::class.java)
            .setPackage(context.packageName)
        return PendingIntent.getBroadcast(
            context,
            GEOFENCE_TRANSITION_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun toGoogleGeofence(registration: GeofenceRegistrationRegion): Geofence {
        val rule = registration.rule
        val region = registration.region
        val builder = Geofence.Builder()
            .setRequestId(GeofenceRequestIdCodec.encode(rule.ruleId, region.regionId))
            .setCircularRegion(region.latitude, region.longitude, region.radiusMeters)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(rule.toGoogleTransitionTypes())
        if (rule.triggerDwell) {
            builder.setLoiteringDelay(rule.dwellDelayMillis.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        }
        return builder.build()
    }

    private fun GeofenceRule.toGoogleTransitionTypes(): Int {
        var transitionTypes = 0
        if (triggerEnter) {
            transitionTypes = transitionTypes or Geofence.GEOFENCE_TRANSITION_ENTER
        }
        if (triggerExit) {
            transitionTypes = transitionTypes or Geofence.GEOFENCE_TRANSITION_EXIT
        }
        if (triggerDwell) {
            transitionTypes = transitionTypes or Geofence.GEOFENCE_TRANSITION_DWELL
        }
        return transitionTypes
    }

    private companion object {
        const val GEOFENCE_TRANSITION_REQUEST_CODE = 26060702
    }
}
