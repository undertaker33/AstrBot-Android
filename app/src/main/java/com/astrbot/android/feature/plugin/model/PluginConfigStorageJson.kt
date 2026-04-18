package com.astrbot.android.model.plugin

import org.json.JSONObject

object PluginConfigStorageJson {
    fun encodeValues(values: Map<String, PluginStaticConfigValue>): String {
        val json = JSONObject()
        values.forEach { (key, value) ->
            json.put(key, encodeValue(value))
        }
        return json.toString()
    }

    fun decodeValues(json: String): Map<String, PluginStaticConfigValue> {
        if (json.isBlank()) return emptyMap()
        val objectJson = JSONObject(json)
        return buildMap {
            objectJson.keys().forEach { key ->
                val rawValue = objectJson.get(key)
                put(key, decodeValue(rawValue, "values.$key"))
            }
        }
    }

    private fun encodeValue(value: PluginStaticConfigValue): Any {
        return when (value) {
            is PluginStaticConfigValue.StringValue -> value.value
            is PluginStaticConfigValue.IntValue -> value.value
            is PluginStaticConfigValue.FloatValue -> value.value
            is PluginStaticConfigValue.BoolValue -> value.value
        }
    }

    private fun decodeValue(
        rawValue: Any,
        path: String,
    ): PluginStaticConfigValue {
        return when (rawValue) {
            is String -> PluginStaticConfigValue.StringValue(rawValue)
            is Int -> PluginStaticConfigValue.IntValue(rawValue)
            is Long -> PluginStaticConfigValue.IntValue(rawValue.toInt())
            is Double -> PluginStaticConfigValue.FloatValue(rawValue)
            is Float -> PluginStaticConfigValue.FloatValue(rawValue.toDouble())
            is Boolean -> PluginStaticConfigValue.BoolValue(rawValue)
            else -> error("$path has unsupported value type: ${rawValue::class.java.simpleName}")
        }
    }
}
