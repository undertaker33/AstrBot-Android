package com.elymbot.android.feature.geofence.runtime

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.elymbot.android.core.common.logging.RuntimeLogger
import com.elymbot.android.feature.geofence.domain.model.GeofenceTransition
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceTransitionEnqueuePort
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@HiltWorker
class GeofenceTransitionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val processor: GeofenceTransitionProcessor,
    private val runtimeLogger: RuntimeLogger,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val transition = inputData.getString(KEY_TRANSITION)
            ?.let(GeofenceTransition::fromPersistedValue)
        val requestIds = inputData.getStringArray(KEY_REQUEST_IDS)?.toList().orEmpty()
        val occurredAtMillis = inputData.getLong(KEY_OCCURRED_AT_MILLIS, System.currentTimeMillis())
        if (transition == null || requestIds.isEmpty()) {
            runtimeLogger.append("GeofenceTransitionWorker: missing transition input")
            return Result.failure()
        }
        val summary = processor.processTransition(
            transition = transition,
            geofenceRequestIds = requestIds,
            occurredAtMillis = occurredAtMillis,
        )
        runtimeLogger.append(
            "GeofenceTransitionWorker: completed=${summary.completedCount} throttled=${summary.throttledCount} failed=${summary.failedCount}",
        )
        return Result.success()
    }

    internal companion object {
        const val KEY_TRANSITION = "transition"
        const val KEY_REQUEST_IDS = "geofence_request_ids"
        const val KEY_OCCURRED_AT_MILLIS = "occurred_at_millis"
        const val TAG = "geofence-transition"
    }
}

internal class WorkManagerGeofenceTransitionEnqueuePort @Inject constructor(
    @ApplicationContext private val context: Context,
) : GeofenceTransitionEnqueuePort {
    override fun enqueueTransition(
        transition: GeofenceTransition,
        geofenceRequestIds: List<String>,
        occurredAtMillis: Long,
    ) {
        val inputData: Data = workDataOf(
            GeofenceTransitionWorker.KEY_TRANSITION to transition.persistedValue,
            GeofenceTransitionWorker.KEY_REQUEST_IDS to geofenceRequestIds.toTypedArray(),
            GeofenceTransitionWorker.KEY_OCCURRED_AT_MILLIS to occurredAtMillis,
        )
        val request = OneTimeWorkRequestBuilder<GeofenceTransitionWorker>()
            .setInputData(inputData)
            .addTag(GeofenceTransitionWorker.TAG)
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
