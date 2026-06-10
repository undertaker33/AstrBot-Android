package com.elymbot.android.feature.geofence.domain.model

data class GeofenceRule(
    val ruleId: String,
    val name: String,
    val description: String = "",
    val enabled: Boolean = true,
    val triggerEnter: Boolean = false,
    val triggerExit: Boolean = false,
    val triggerDwell: Boolean = false,
    val dwellDelayMillis: Long = 0L,
    val actionType: GeofenceActionType = GeofenceActionType.AGENT_PROMPT,
    val actionPrompt: String = "",
    val targetPlatform: String = "",
    val targetConversationId: String = "",
    val targetBotId: String = "",
    val targetConfigProfileId: String = "",
    val targetPersonaId: String = "",
    val targetProviderId: String = "",
    val minimumTriggerIntervalMillis: Long = 0L,
    val status: GeofenceRuleStatus = GeofenceRuleStatus.ACTIVE,
    val lastRegisteredAt: Long = 0L,
    val lastTriggeredAt: Long = 0L,
    val lastError: String = "",
    val regions: List<GeofenceRegion> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

data class GeofenceRegion(
    val regionId: String,
    val ruleId: String,
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
    val addressLabel: String = "",
    val sortIndex: Int = 0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

data class ConfigGeofenceBinding(
    val configId: String,
    val ruleId: String,
    val enabled: Boolean,
    val sortIndex: Int = 0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

data class GeofenceExecutionRecord(
    val executionId: String,
    val ruleId: String,
    val regionId: String,
    val configId: String,
    val transition: GeofenceTransition,
    val startedAt: Long,
    val completedAt: Long = 0L,
    val status: String = "",
    val errorCode: String = "",
    val errorMessage: String = "",
    val deliverySummary: String = "",
    val locationSnapshotJson: String = "",
    val triggerPayloadJson: String = "",
)

object GeofenceExecutionFailureCodes {
    const val INVALID_REQUEST_ID = "invalid_request_id"
    const val MISSING_RULE = "missing_rule"
    const val MISSING_REGION = "missing_region"
    const val MISSING_BINDING = "missing_binding"

    val staleTriggerAuditCodes: Set<String> = setOf(
        MISSING_RULE,
        MISSING_REGION,
        MISSING_BINDING,
    )
}

enum class GeofenceTransition(val persistedValue: String) {
    ENTER("enter"),
    EXIT("exit"),
    DWELL("dwell");

    companion object {
        fun fromPersistedValue(value: String): GeofenceTransition =
            entries.firstOrNull { it.persistedValue == value } ?: ENTER
    }
}

enum class GeofenceActionType(val persistedValue: String) {
    AGENT_PROMPT("agent_prompt"),
    SEND_MESSAGE("send_message"),
    WEATHER_FORECAST("weather_forecast"),
    NEWS_DIGEST("news_digest"),
    HOST_CAPABILITY("host_capability");

    companion object {
        fun fromPersistedValue(value: String): GeofenceActionType =
            entries.firstOrNull { it.persistedValue == value } ?: AGENT_PROMPT
    }
}

enum class GeofenceRuleStatus(val persistedValue: String) {
    ACTIVE("active"),
    PAUSED("paused"),
    PERMISSION_REQUIRED("permission_required"),
    REGISTRATION_FAILED("registration_failed"),
    PLAY_SERVICES_UNAVAILABLE("play_services_unavailable"),
    CAPACITY_EXCEEDED("capacity_exceeded"),
    INVALID_REGION("invalid_region");

    companion object {
        fun fromPersistedValue(value: String): GeofenceRuleStatus =
            entries.firstOrNull { it.persistedValue == value } ?: ACTIVE
    }
}
