package com.elymbot.android.feature.geofence.domain.model

object GeofenceRuleValidation {
    const val MIN_RADIUS_METERS = 50f
    const val RECOMMENDED_MIN_RADIUS_METERS = 100f
    const val MAX_ACTIVE_GEOFENCE_REGIONS = 100

    enum class RadiusStability {
        STABLE,
        BELOW_RECOMMENDED,
    }

    fun requireValidRule(rule: GeofenceRule) {
        requireField(rule.ruleId.isNotBlank(), "ruleId must not be blank")
        requireField(rule.name.isNotBlank(), "name must not be blank")
        requireField(
            rule.triggerEnter || rule.triggerExit || rule.triggerDwell,
            "at least one geofence trigger must be enabled",
        )
        requireField(rule.dwellDelayMillis >= 0L, "dwellDelayMillis must be non-negative")
        requireField(
            !rule.triggerDwell || rule.dwellDelayMillis > 0L,
            "dwellDelayMillis must be positive when dwell trigger is enabled",
        )
        requireField(
            rule.minimumTriggerIntervalMillis >= 0L,
            "minimumTriggerIntervalMillis must be non-negative",
        )
        requireField(rule.actionPrompt.isNotBlank(), "actionPrompt must not be blank")
        rule.regions.forEach(::requireValidRegion)
    }

    fun requireValidRegion(region: GeofenceRegion) {
        requireField(region.regionId.isNotBlank(), "regionId must not be blank")
        requireField(region.ruleId.isNotBlank(), "region ruleId must not be blank")
        requireField(region.latitude in -90.0..90.0, "latitude must be in [-90, 90]")
        requireField(region.longitude in -180.0..180.0, "longitude must be in [-180, 180]")
        requireField(region.radiusMeters >= MIN_RADIUS_METERS, "radiusMeters must be at least $MIN_RADIUS_METERS")
    }

    fun requireValidBinding(binding: ConfigGeofenceBinding) {
        requireField(binding.configId.isNotBlank(), "configId must not be blank")
        requireField(binding.ruleId.isNotBlank(), "ruleId must not be blank")
    }

    fun requireValidExecutionRecord(record: GeofenceExecutionRecord) {
        requireField(record.executionId.isNotBlank(), "executionId must not be blank")
        requireField(record.ruleId.isNotBlank(), "ruleId must not be blank")
        requireField(record.regionId.isNotBlank(), "regionId must not be blank")
        requireField(record.configId.isNotBlank(), "configId must not be blank")
        requireField(record.startedAt >= 0L, "startedAt must be non-negative")
        requireField(record.completedAt >= 0L, "completedAt must be non-negative")
        requireField(
            record.completedAt == 0L || record.completedAt >= record.startedAt,
            "completedAt must be zero or greater than or equal to startedAt",
        )
    }

    fun radiusStability(region: GeofenceRegion): RadiusStability =
        if (region.radiusMeters < RECOMMENDED_MIN_RADIUS_METERS) {
            RadiusStability.BELOW_RECOMMENDED
        } else {
            RadiusStability.STABLE
        }

    private fun requireField(condition: Boolean, message: String) {
        if (!condition) {
            throw GeofenceValidationException(message)
        }
    }
}

class GeofenceValidationException(message: String) : IllegalArgumentException(message)
