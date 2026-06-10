package com.elymbot.android.feature.geofence.runtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.elymbot.android.core.common.logging.RuntimeLogger
import com.elymbot.android.feature.geofence.domain.model.GeofenceTransition
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceTransitionEnqueuePort
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class GeofenceTransitionReceiver : BroadcastReceiver() {
    // skipcq: KT-W1047
    @Inject
    internal lateinit var enqueuePort: GeofenceTransitionEnqueuePort

    // skipcq: KT-W1047
    @Inject
    internal lateinit var runtimeLogger: RuntimeLogger

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent)
        if (event == null || event.hasError()) {
            runtimeLogger.append("GeofenceTransitionReceiver: ignored geofencing event error")
            return
        }
        val requestIds = event.triggeringGeofences.orEmpty().map { geofence -> geofence.requestId }
        val accepted = enqueueParsedTransition(
            transitionType = event.geofenceTransition,
            requestIds = requestIds,
            enqueuePort = enqueuePort,
            occurredAtMillis = System.currentTimeMillis(),
        )
        if (!accepted) {
            runtimeLogger.append("GeofenceTransitionReceiver: ignored transition with no matching request id")
        }
    }

    internal companion object {
        fun enqueueParsedTransition(
            transitionType: Int,
            requestIds: List<String>,
            enqueuePort: GeofenceTransitionEnqueuePort,
            occurredAtMillis: Long,
        ): Boolean {
            val transition = when (transitionType) {
                Geofence.GEOFENCE_TRANSITION_ENTER -> GeofenceTransition.ENTER
                Geofence.GEOFENCE_TRANSITION_EXIT -> GeofenceTransition.EXIT
                Geofence.GEOFENCE_TRANSITION_DWELL -> GeofenceTransition.DWELL
                else -> return false
            }
            if (requestIds.isEmpty()) {
                return false
            }
            enqueuePort.enqueueTransition(
                transition = transition,
                geofenceRequestIds = requestIds,
                occurredAtMillis = occurredAtMillis,
            )
            return true
        }
    }
}
