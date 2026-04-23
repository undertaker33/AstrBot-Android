package com.astrbot.android.feature.plugin.data.state

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

object PluginStateValueCodec {
    fun encode(value: Any?): String {
        return when (val sanitized = sanitize(value, "value")) {
            null -> "null"
            is String -> JSONObject.quote(sanitized)
            is Boolean -> sanitized.toString()
            is Number -> JSONObject.numberToString(sanitized)

            is Map<*, *> -> toJsonObject(sanitized).toString()
            is List<*> -> toJsonArray(sanitized).toString()
            else -> throw IllegalArgumentException(
                "value contains unsupported type: ${sanitized::class.java.name}",
            )
        }
    }

    fun decode(valueJson: String): Any? {
        val normalized = valueJson.trim()
        require(normalized.isNotEmpty()) { "valueJson must not be blank." }
        val rawValue = JSONTokener(normalized).nextValue()
        return fromJsonValue(rawValue)
    }

    private fun sanitize(
        value: Any?,
        path: String,
    ): Any? {
        return when (value) {
            null,
            is String,
            is Boolean,
            is Int,
            is Long,
            -> value

            is Float -> sanitizeNumber(value.toDouble(), path)
            is Double -> sanitizeNumber(value, path)
            is Number -> sanitizeNumber(value.toDouble(), path)

            is List<*> -> value.mapIndexed { index, item ->
                sanitize(item, "$path[$index]")
            }

            is Map<*, *> -> {
                val sanitized = linkedMapOf<String, Any?>()
                value.forEach { (key, item) ->
                    require(key is String) {
                        "$path contains a non-string key: ${key?.javaClass?.name ?: "null"}"
                    }
                    sanitized[key] = sanitize(item, "$path['$key']")
                }
                sanitized
            }

            else -> throw IllegalArgumentException(
                "$path contains unsupported type: ${value::class.java.name}",
            )
        }
    }

    private fun sanitizeNumber(
        value: Double,
        path: String,
    ): Double {
        require(value.isFinite()) { "$path must be a finite number." }
        return value
    }

    private fun toJsonObject(value: Map<*, *>): JSONObject {
        val json = JSONObject()
        value.forEach { (key, item) ->
            json.put(key.toString(), toJsonCompatibleValue(item))
        }
        return json
    }

    private fun toJsonArray(value: List<*>): JSONArray {
        return JSONArray().also { array ->
            value.forEach { item ->
                array.put(toJsonCompatibleValue(item))
            }
        }
    }

    private fun toJsonCompatibleValue(value: Any?): Any? {
        return when (value) {
            null,
            is String,
            is Boolean,
            is Int,
            is Long,
            is Double,
            -> value

            is Map<*, *> -> toJsonObject(value)
            is List<*> -> toJsonArray(value)
            else -> throw IllegalArgumentException(
                "Unsupported JSON value type: ${value::class.java.name}",
            )
        }
    }

    private fun fromJsonValue(value: Any?): Any? {
        return when (value) {
            null,
            JSONObject.NULL,
            -> null

            is JSONObject -> {
                val mapped = linkedMapOf<String, Any?>()
                value.keys().forEach { key ->
                    mapped[key] = fromJsonValue(value.get(key))
                }
                mapped
            }

            is JSONArray -> {
                buildList(value.length()) {
                    for (index in 0 until value.length()) {
                        add(fromJsonValue(value.get(index)))
                    }
                }
            }

            is Number -> {
                when {
                    value is Int || value is Long || value is Double -> value
                    else -> value.toDouble()
                }
            }

            else -> value
        }
    }
}
