package com.astrbot.android.model.plugin

import org.json.JSONObject

object PluginPackageContractJson {
    fun decode(json: JSONObject): PluginPackageContract {
        val protocolVersion = readRequiredInt(json, "protocolVersion", "protocolVersion")
        require(protocolVersion == PluginPackageContract.SUPPORTED_PROTOCOL_VERSION) {
            "protocolVersion has unsupported value: $protocolVersion"
        }

        val runtimeJson = readRequiredObject(json, "runtime", "runtime")
        val runtimeKind = readRequiredString(runtimeJson, "kind", "runtime.kind")
        require(runtimeKind == ExternalPluginRuntimeKind.JsQuickJs.wireValue) {
            "runtime.kind has unsupported value: $runtimeKind"
        }

        val bootstrap = normalizeRuntimeBootstrap(
            readRequiredString(runtimeJson, "bootstrap", "runtime.bootstrap"),
        )

        val apiVersion = readRequiredInt(runtimeJson, "apiVersion", "runtime.apiVersion")
        val configJson = readOptionalObject(json, "config")

        return PluginPackageContract(
            protocolVersion = protocolVersion,
            runtime = PluginRuntimeDeclaration(
                kind = runtimeKind,
                bootstrap = bootstrap,
                apiVersion = apiVersion,
            ),
            config = decodeConfig(configJson),
        )
    }

    private fun decodeConfig(json: JSONObject?): PluginConfigEntryPoints {
        if (json == null) return PluginConfigEntryPoints()
        return PluginConfigEntryPoints(
            staticSchema = readOptionalConfigString(json, "staticSchema", "config.staticSchema"),
            settingsSchema = readOptionalConfigString(json, "settingsSchema", "config.settingsSchema"),
        )
    }

    private fun normalizeRuntimeBootstrap(value: String): String {
        val trimmed = value.trim()
        require(trimmed.isNotBlank()) {
            "runtime.bootstrap is required"
        }
        require(!trimmed.startsWith('/')) {
            "runtime.bootstrap must be a relative path under runtime/"
        }
        require(!trimmed.startsWith('\\')) {
            "runtime.bootstrap must be a relative path under runtime/"
        }
        require(!Regex("^[A-Za-z]:").containsMatchIn(trimmed)) {
            "runtime.bootstrap must be a relative path under runtime/"
        }
        require(!trimmed.contains('\\')) {
            "runtime.bootstrap must use forward slashes"
        }

        val segments = trimmed.split('/').filter { it.isNotBlank() }
        require(segments.isNotEmpty()) {
            "runtime.bootstrap must be a relative path under runtime/"
        }
        require(segments.none { it == "." || it == ".." }) {
            "runtime.bootstrap must be a relative path under runtime/"
        }
        require(segments.first() == "runtime") {
            "runtime.bootstrap must be under runtime/"
        }
        return segments.joinToString("/")
    }

    private fun readRequiredInt(json: JSONObject, key: String, path: String): Int {
        require(json.has(key) && !json.isNull(key)) {
            "$path is required"
        }
        return when (val value = json.get(key)) {
            is Int -> value
            is Long -> value.toInt()
            else -> throw IllegalArgumentException("$path must be an integer")
        }
    }

    private fun readRequiredString(json: JSONObject, key: String, path: String): String {
        val value = json.optString(key).trim()
        require(value.isNotBlank()) {
            "$path is required"
        }
        return value
    }

    private fun readRequiredObject(json: JSONObject, key: String, path: String): JSONObject {
        return json.optJSONObject(key)
            ?: throw IllegalArgumentException("$path must be an object")
    }

    private fun readOptionalObject(json: JSONObject, key: String): JSONObject? {
        if (!json.has(key) || json.isNull(key)) return null
        return json.optJSONObject(key)
            ?: throw IllegalArgumentException("$key must be an object")
    }

    private fun readOptionalConfigString(json: JSONObject, key: String, path: String): String {
        if (!json.has(key) || json.isNull(key)) return ""
        return when (val value = json.get(key)) {
            is String -> value
            else -> throw IllegalArgumentException("$path must be a string")
        }
    }
}
