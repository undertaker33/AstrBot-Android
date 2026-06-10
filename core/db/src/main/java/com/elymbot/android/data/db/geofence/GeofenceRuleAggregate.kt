package com.elymbot.android.data.db.geofence

import androidx.room.Embedded
import androidx.room.Relation

data class GeofenceRuleAggregate(
    @Embedded val rule: GeofenceRuleEntity,
    @Relation(parentColumn = "ruleId", entityColumn = "ruleId")
    val regions: List<GeofenceRegionEntity>,
)
