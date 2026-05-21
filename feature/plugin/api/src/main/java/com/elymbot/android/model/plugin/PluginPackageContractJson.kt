package com.elymbot.android.model.plugin

import java.io.File
import org.json.JSONArray
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
        val networkJson = readOptionalObject(json, "network")

        return PluginPackageContract(
            protocolVersion = protocolVersion,
            runtime = PluginRuntimeDeclaration(
                kind = runtimeKind,
                bootstrap = bootstrap,
                apiVersion = apiVersion,
            ),
            config = decodeConfig(configJson),
            network = decodeNetwork(networkJson),
        )
    }

    private fun decodeConfig(json: JSONObject?): PluginConfigEntryPoints {
        if (json == null) return PluginConfigEntryPoints()
        return PluginConfigEntryPoints(
            staticSchema = readOptionalConfigPath(json, "staticSchema", "config.staticSchema"),
            settingsSchema = readOptionalConfigPath(json, "settingsSchema", "config.settingsSchema"),
        )
    }

    private fun decodeNetwork(json: JSONObject?): PluginNetworkAccessPolicy {
        if (json == null) return PluginNetworkAccessPolicy()
        val allowedDomains = readOptionalStringArray(json, "allowedDomains", "network.allowedDomains")
            .map(::normalizeNetworkDomain)
            .distinct()
        return PluginNetworkAccessPolicy(allowedDomains = allowedDomains)
    }

    private fun readOptionalStringArray(
        json: JSONObject,
        key: String,
        path: String,
    ): List<String> {
        if (!json.has(key) || json.isNull(key)) return emptyList()
        val array = json.optJSONArray(key)
            ?: throw IllegalArgumentException("$path must be an array")
        return array.toStringList(path)
    }

    private fun normalizeRuntimeBootstrap(value: String): String {
        val normalized = normalizePluginPackageRelativePath(
            value = value,
            path = "runtime.bootstrap",
            relativePathMessage = "runtime.bootstrap must be a relative path under runtime/",
        )
        require(normalized.substringBefore('/') == "runtime") {
            "runtime.bootstrap must be under runtime/"
        }
        return normalized
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

    private fun readOptionalConfigPath(json: JSONObject, key: String, path: String): String {
        if (!json.has(key) || json.isNull(key)) return ""
        return when (val value = json.get(key)) {
            is String -> normalizeOptionalPluginPackageRelativePath(value, path)
            else -> throw IllegalArgumentException("$path must be a string")
        }
    }

    private fun JSONArray.toStringList(path: String): List<String> {
        return buildList {
            for (index in 0 until length()) {
                val value = opt(index)
                require(value is String) {
                    "$path[$index] must be a string"
                }
                add(value)
            }
        }
    }

    private fun normalizeNetworkDomain(value: String): String {
        val normalized = value.trim().lowercase().removeSuffix(".")
        require(normalized.isNotBlank()) {
            "network.allowedDomains must not contain blank domains"
        }
        require(!normalized.contains("/") && !normalized.contains(":")) {
            "network.allowedDomains entries must be bare domains"
        }
        return normalized
    }
}

internal fun normalizeOptionalPluginPackageRelativePath(value: String, path: String): String {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return ""
    return normalizePluginPackageRelativePath(
        value = trimmed,
        path = path,
        relativePathMessage = "$path must be a relative path under plugin root",
    )
}

fun resolvePluginPackageSnapshotFile(rootDir: File, relativePath: String): File? {
    if (relativePath.isBlank()) return null
    val normalized = runCatching {
        normalizePluginPackageRelativePath(
            value = relativePath,
            path = "packageContractSnapshot",
            relativePathMessage = "packageContractSnapshot config path must stay under plugin root",
        )
    }.getOrNull() ?: return null
    val canonicalRoot = runCatching { rootDir.canonicalFile }.getOrNull() ?: return null
    val canonicalFile = runCatching { File(canonicalRoot, normalized).canonicalFile }.getOrNull() ?: return null
    return canonicalFile.takeIf { file ->
        file.isFile && file.toPath().startsWith(canonicalRoot.toPath())
    }
}

private fun normalizePluginPackageRelativePath(
    value: String,
    path: String,
    relativePathMessage: String,
): String {
    val trimmed = value.trim()
    require(trimmed.isNotBlank()) {
        "$path is required"
    }
    require(!trimmed.startsWith('/')) {
        relativePathMessage
    }
    require(!trimmed.startsWith('\\')) {
        relativePathMessage
    }
    require(!Regex("^[A-Za-z]:").containsMatchIn(trimmed)) {
        relativePathMessage
    }
    require(!trimmed.contains('\\')) {
        "$path must use forward slashes"
    }

    val segments = trimmed.split('/').filter { it.isNotBlank() }
    require(segments.isNotEmpty()) {
        relativePathMessage
    }
    require(segments.none { it == "." || it == ".." }) {
        relativePathMessage
    }
    return segments.joinToString("/")
}
