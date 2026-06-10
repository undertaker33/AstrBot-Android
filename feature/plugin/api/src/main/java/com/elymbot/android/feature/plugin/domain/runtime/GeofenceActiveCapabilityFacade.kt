package com.elymbot.android.feature.plugin.domain.runtime

import com.elymbot.android.core.runtime.context.ToolSourceContext
import org.json.JSONObject

/**
 * INTERNAL_ACTIVE_CAPABILITY_ONLY.
 *
 * Host-owned bridge used only by ActiveCapabilityToolSourceProvider; this is not a plugin Host API surface.
 */
interface GeofenceActiveCapabilityFacade {
    suspend fun createRule(
        payload: Map<String, Any?>,
        metadata: Map<String, Any?>?,
        toolSourceContext: ToolSourceContext?,
    ): JSONObject

    suspend fun updateRule(
        payload: Map<String, Any?>,
        metadata: Map<String, Any?>?,
        toolSourceContext: ToolSourceContext?,
    ): JSONObject

    suspend fun listRules(): JSONObject

    suspend fun deleteRule(ruleId: String): JSONObject

    suspend fun pauseRule(ruleId: String): JSONObject

    suspend fun resumeRule(ruleId: String): JSONObject
}

object DisabledGeofenceActiveCapabilityFacade : GeofenceActiveCapabilityFacade {
    override suspend fun createRule(
        payload: Map<String, Any?>,
        metadata: Map<String, Any?>?,
        toolSourceContext: ToolSourceContext?,
    ): JSONObject = unavailable()

    override suspend fun updateRule(
        payload: Map<String, Any?>,
        metadata: Map<String, Any?>?,
        toolSourceContext: ToolSourceContext?,
    ): JSONObject = unavailable()

    override suspend fun listRules(): JSONObject = unavailable()

    override suspend fun deleteRule(ruleId: String): JSONObject = unavailable()

    override suspend fun pauseRule(ruleId: String): JSONObject = unavailable()

    override suspend fun resumeRule(ruleId: String): JSONObject = unavailable()

    private fun unavailable(): JSONObject =
        JSONObject().apply {
            put("success", false)
            put("error_code", "geofence_unavailable")
            put("message", "Geofence capability is not wired in this runtime.")
        }
}
