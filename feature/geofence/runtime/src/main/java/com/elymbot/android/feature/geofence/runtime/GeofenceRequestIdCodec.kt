package com.elymbot.android.feature.geofence.runtime

import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64

internal data class DecodedGeofenceRequestId(
    val ruleId: String,
    val regionId: String,
)

internal object GeofenceRequestIdCodec {
    private const val VERSION_PREFIX = "gf1"
    private const val SEPARATOR = "."
    private val encoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder: Base64.Decoder = Base64.getUrlDecoder()

    fun encode(ruleId: String, regionId: String): String =
        listOf(VERSION_PREFIX, encodePart(ruleId), encodePart(regionId)).joinToString(SEPARATOR)

    fun decode(requestId: String): DecodedGeofenceRequestId? {
        val parts = requestId.split(SEPARATOR)
        if (parts.size != 3 || parts[0] != VERSION_PREFIX) {
            return null
        }
        val ruleId = decodePart(parts[1]) ?: return null
        val regionId = decodePart(parts[2]) ?: return null
        return DecodedGeofenceRequestId(ruleId = ruleId, regionId = regionId)
    }

    private fun encodePart(value: String): String =
        encoder.encodeToString(value.toByteArray(UTF_8))

    private fun decodePart(value: String): String? =
        runCatching { String(decoder.decode(value), UTF_8) }.getOrNull()
}
