package com.astrbot.android.model.plugin

import org.json.JSONArray
import org.json.JSONObject

object ExternalPluginExecutionContractJson {
    fun decodeContract(json: JSONObject): ExternalPluginExecutionContract {
        val contractVersion = readRequiredInt(json, "contractVersion", "contractVersion")
        require(contractVersion == ExternalPluginExecutionContract.CURRENT_VERSION) {
            "contractVersion has unsupported value: $contractVersion"
        }
        val entryPointJson = readRequiredObject(json, "entryPoint", "entryPoint")
        return ExternalPluginExecutionContract(
            contractVersion = contractVersion,
            enabled = json.optBoolean("enabled", true),
            entryPoint = decodeEntryPoint(entryPointJson, "entryPoint"),
            supportedTriggers = decodeSupportedTriggers(
                readOptionalArray(json, "supportedTriggers"),
                "supportedTriggers",
            ),
        )
    }

    private fun decodeEntryPoint(
        json: JSONObject,
        path: String,
    ): ExternalPluginExecutionEntryPoint {
        val runtimeKindValue = readRequiredString(json, "runtimeKind", "$path.runtimeKind")
        val runtimeKind = ExternalPluginRuntimeKind.fromWireValue(runtimeKindValue)
            ?: throw IllegalArgumentException("$path.runtimeKind has unsupported value: $runtimeKindValue")
        return ExternalPluginExecutionEntryPoint(
            runtimeKind = runtimeKind,
            path = readRequiredString(json, "path", "$path.path"),
            entrySymbol = readRequiredString(json, "entrySymbol", "$path.entrySymbol"),
        )
    }

    private fun decodeSupportedTriggers(
        array: JSONArray?,
        path: String,
    ): Set<PluginTriggerSource> {
        if (array == null) {
            return emptySet()
        }
        val triggers = linkedSetOf<PluginTriggerSource>()
        for (index in 0 until array.length()) {
            val rawValue = array.optString(index).trim()
            require(rawValue.isNotBlank()) { "$path[$index] must not be blank" }
            val trigger = PluginTriggerSource.fromWireValue(rawValue)
                ?: throw IllegalArgumentException("$path[$index] has unsupported value: $rawValue")
            triggers += trigger
        }
        return triggers
    }

    private fun readRequiredInt(
        json: JSONObject,
        key: String,
        path: String,
    ): Int {
        require(json.has(key) && !json.isNull(key)) { "$path is required" }
        return json.optInt(key, Int.MIN_VALUE).also { value ->
            require(value != Int.MIN_VALUE) { "$path must be an integer" }
        }
    }

    private fun readRequiredString(
        json: JSONObject,
        key: String,
        path: String,
    ): String {
        val value = json.optString(key).trim()
        require(value.isNotBlank()) { "$path is required" }
        return value
    }

    private fun readRequiredObject(
        json: JSONObject,
        key: String,
        path: String,
    ): JSONObject {
        return json.optJSONObject(key)
            ?: throw IllegalArgumentException("$path must be an object")
    }

    private fun readOptionalArray(
        json: JSONObject,
        key: String,
    ): JSONArray? {
        return json.optJSONArray(key)
    }
}
