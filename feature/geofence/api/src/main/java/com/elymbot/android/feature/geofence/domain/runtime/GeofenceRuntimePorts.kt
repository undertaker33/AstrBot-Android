package com.elymbot.android.feature.geofence.domain.runtime

import com.elymbot.android.feature.geofence.domain.model.ConfigGeofenceBinding
import com.elymbot.android.feature.geofence.domain.model.GeofenceRegion
import com.elymbot.android.feature.geofence.domain.model.GeofenceRule
import com.elymbot.android.feature.geofence.domain.model.GeofenceTransition
import kotlinx.coroutines.CoroutineScope

data class GeofencePermissionStatus(
    val foregroundGranted: Boolean,
    val backgroundGranted: Boolean,
) {
    val canRegister: Boolean
        get() = foregroundGranted && backgroundGranted
}

enum class GeofenceMapAvailability {
    AVAILABLE,
    MISSING_API_KEY,
    SDK_UNAVAILABLE,
    LOAD_FAILED,
}

data class GeofenceCurrentLocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val capturedAtMillis: Long,
)

sealed interface GeofenceCurrentLocationResult {
    data class Success(
        val location: GeofenceCurrentLocationSnapshot,
    ) : GeofenceCurrentLocationResult

    data class Failure(
        val errorCode: String,
        val message: String,
    ) : GeofenceCurrentLocationResult
}

enum class GeofenceRegistrationStatus {
    REGISTERED,
    NO_ACTIVE_REGIONS,
    PERMISSION_REQUIRED,
    PLAY_SERVICES_UNAVAILABLE,
    CAPACITY_EXCEEDED,
    REGISTRATION_FAILED,
}

data class GeofenceRegistrationSummary(
    val status: GeofenceRegistrationStatus,
    val activeRuleCount: Int = 0,
    val activeRegionCount: Int = 0,
    val affectedRuleIds: List<String> = emptyList(),
    val errorMessage: String = "",
)

interface GeofencePermissionStatusPort {
    fun currentStatus(): GeofencePermissionStatus
}

interface GeofenceMapAvailabilityPort {
    fun currentAvailability(): GeofenceMapAvailability
}

fun interface GeofenceCurrentLocationPort {
    suspend fun currentLocation(): GeofenceCurrentLocationResult
}

interface GeofenceRegistrationPort {
    suspend fun registerActiveGeofences(): GeofenceRegistrationSummary
}

interface GeofenceRuntimeReconciliationPort {
    fun reconcileAsync(scope: CoroutineScope)
    suspend fun reconcileNow(): GeofenceRegistrationSummary
}

interface GeofenceTransitionEnqueuePort {
    fun enqueueTransition(
        transition: GeofenceTransition,
        geofenceRequestIds: List<String>,
        occurredAtMillis: Long,
    )
}

data class GeofenceActionExecutionContext(
    val rule: GeofenceRule,
    val region: GeofenceRegion,
    val binding: ConfigGeofenceBinding,
    val transition: GeofenceTransition,
    val occurredAtMillis: Long,
)

data class GeofenceActionExecutionResult(
    val success: Boolean,
    val deliverySummary: String,
    val errorCode: String = "",
    val errorMessage: String = "",
) {
    companion object {
        fun success(deliverySummary: String): GeofenceActionExecutionResult =
            GeofenceActionExecutionResult(success = true, deliverySummary = deliverySummary)

        fun failure(
            errorCode: String,
            errorMessage: String,
            deliverySummary: String = errorMessage,
        ): GeofenceActionExecutionResult =
            GeofenceActionExecutionResult(
                success = false,
                deliverySummary = deliverySummary.ifBlank { errorCode },
                errorCode = errorCode,
                errorMessage = errorMessage,
            )
    }
}

fun interface GeofenceActionExecutorPort {
    suspend fun execute(context: GeofenceActionExecutionContext): GeofenceActionExecutionResult
}

data class GeofenceMessageDeliveryRequest(
    val platform: String,
    val conversationId: String,
    val text: String,
    val attachments: List<GeofenceMessageAttachment> = emptyList(),
    val botId: String = "",
)

data class GeofenceMessageAttachment(
    val id: String,
    val type: String,
    val mimeType: String = "application/octet-stream",
    val fileName: String = "",
    val base64Data: String = "",
    val remoteUrl: String = "",
)

data class GeofenceMessageDeliveryResult(
    val success: Boolean,
    val deliveredMessageCount: Int = 0,
    val receiptIds: List<String> = emptyList(),
    val errorCode: String = "",
    val errorSummary: String = "",
)

fun interface GeofenceMessageDeliveryPort {
    suspend fun deliver(request: GeofenceMessageDeliveryRequest): GeofenceMessageDeliveryResult
}
