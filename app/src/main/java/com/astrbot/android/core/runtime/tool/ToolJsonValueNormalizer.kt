package com.astrbot.android.core.runtime.tool

import org.json.JSONArray
import org.json.JSONObject

internal object ToolJsonValueNormalizer {
    fun normalizeObject(value: JSONObject?): Map<String, Any?> {
        if (value == null) return emptyMap()
        return value.keys().asSequence().associateWith { key ->
            normalizeValue(value.opt(key))
        }
    }

    fun normalizeArray(value: JSONArray): List<Any?> {
        return (0 until value.length()).map { index ->
            normalizeValue(value.opt(index))
        }
    }

    fun normalizeValue(value: Any?): Any? {
        return when (value) {
            null, JSONObject.NULL -> null
            is JSONObject -> normalizeObject(value)
            is JSONArray -> normalizeArray(value)
            else -> value
        }
    }
}
