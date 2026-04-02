package com.astrbot.android.runtime.plugin

import com.astrbot.android.model.plugin.PluginCompatibilityState
import com.astrbot.android.model.plugin.PluginManifest
import com.astrbot.android.model.plugin.PluginPermissionDeclaration
import com.astrbot.android.model.plugin.PluginRiskLevel
import com.astrbot.android.model.plugin.PluginSourceType
import java.io.File
import java.util.zip.ZipInputStream
import org.json.JSONArray
import org.json.JSONObject

data class PluginPackageValidationResult(
    val manifest: PluginManifest,
    val compatibilityState: PluginCompatibilityState,
)

class PluginPackageValidator(
    private val hostVersion: String,
    private val supportedProtocolVersion: Int,
) {
    fun validate(packageFile: File): PluginPackageValidationResult {
        require(packageFile.exists() && packageFile.isFile) {
            "Plugin package file was not found: ${packageFile.absolutePath}"
        }
        val manifest = parseManifest(readManifestJson(packageFile))
        return PluginPackageValidationResult(
            manifest = manifest,
            compatibilityState = PluginCompatibilityState.fromChecks(
                protocolSupported = manifest.protocolVersion == supportedProtocolVersion,
                minHostVersionSatisfied = compareVersions(hostVersion, manifest.minHostVersion) >= 0,
                maxHostVersionSatisfied = manifest.maxHostVersion.isBlank() ||
                    compareVersions(hostVersion, manifest.maxHostVersion) <= 0,
                notes = buildCompatibilityNotes(manifest),
            ),
        )
    }

    private fun buildCompatibilityNotes(manifest: PluginManifest): String {
        val notes = mutableListOf<String>()
        if (manifest.protocolVersion != supportedProtocolVersion) {
            notes += "Protocol version ${manifest.protocolVersion} is not supported."
        }
        if (compareVersions(hostVersion, manifest.minHostVersion) < 0) {
            notes += "Host version $hostVersion is below required minimum ${manifest.minHostVersion}."
        }
        if (manifest.maxHostVersion.isNotBlank() && compareVersions(hostVersion, manifest.maxHostVersion) > 0) {
            notes += "Host version $hostVersion exceeds supported maximum ${manifest.maxHostVersion}."
        }
        return notes.joinToString(separator = " ")
    }

    private fun readManifestJson(packageFile: File): JSONObject {
        var manifestText: String? = null
        ZipInputStream(packageFile.inputStream().buffered()).use { input ->
            var entry = input.nextEntry
            while (entry != null) {
                val normalizedName = normalizeArchiveEntryName(entry.name)
                if (!entry.isDirectory && normalizedName == "manifest.json") {
                    manifestText = input.readBytes().toString(Charsets.UTF_8)
                }
                entry = input.nextEntry
            }
        }
        return JSONObject(manifestText ?: throw IllegalArgumentException("Plugin package is missing manifest.json"))
    }

    private fun parseManifest(json: JSONObject): PluginManifest {
        return PluginManifest(
            pluginId = json.requireString("pluginId"),
            version = json.requireString("version"),
            protocolVersion = json.requireInt("protocolVersion"),
            author = json.requireString("author"),
            title = json.requireString("title"),
            description = json.requireString("description"),
            permissions = json.requireArray("permissions").toPermissionDeclarations(),
            minHostVersion = json.requireString("minHostVersion"),
            maxHostVersion = json.optString("maxHostVersion").trim(),
            sourceType = json.requireEnum("sourceType"),
            entrySummary = json.requireString("entrySummary"),
            riskLevel = json.optionalEnum("riskLevel", PluginRiskLevel.LOW),
        )
    }

    private fun JSONArray?.toPermissionDeclarations(): List<PluginPermissionDeclaration> {
        if (this == null) return emptyList()
        return buildList(length()) {
            for (index in 0 until length()) {
                val permission = optJSONObject(index)
                    ?: throw IllegalArgumentException("permissions[$index] must be an object")
                add(
                    PluginPermissionDeclaration(
                        permissionId = permission.requireString("permissionId"),
                        title = permission.requireString("title"),
                        description = permission.requireString("description"),
                        riskLevel = permission.optionalEnum("riskLevel", PluginRiskLevel.MEDIUM),
                        required = permission.optionalBoolean("required", defaultValue = true),
                    ),
                )
            }
        }
    }

    private fun JSONObject.requireString(key: String): String {
        val value = optString(key).trim()
        require(value.isNotBlank()) {
            "Missing required manifest field: $key"
        }
        return value
    }

    private fun JSONObject.requireInt(key: String): Int {
        require(has(key)) {
            "Missing required manifest field: $key"
        }
        val rawValue = get(key)
        return when (rawValue) {
            is Int -> rawValue
            is Long -> rawValue.toInt()
            else -> throw IllegalArgumentException("Manifest field $key must be an integer")
        }
    }

    private fun JSONObject.requireArray(key: String): JSONArray {
        require(has(key)) {
            "Missing required manifest field: $key"
        }
        return optJSONArray(key)
            ?: throw IllegalArgumentException("Manifest field $key must be an array")
    }

    private inline fun <reified T : Enum<T>> JSONObject.requireEnum(key: String): T {
        val rawValue = requireString(key)
        return runCatching { enumValueOf<T>(rawValue) }
            .getOrElse { throw IllegalArgumentException("Manifest field $key has unsupported value: $rawValue") }
    }

    private inline fun <reified T : Enum<T>> JSONObject.optionalEnum(key: String, defaultValue: T): T {
        if (!has(key)) return defaultValue
        val rawValue = optString(key).trim()
        if (rawValue.isBlank()) return defaultValue
        return runCatching { enumValueOf<T>(rawValue) }
            .getOrElse { throw IllegalArgumentException("Manifest field $key has unsupported value: $rawValue") }
    }

    private fun JSONObject.optionalBoolean(key: String, defaultValue: Boolean): Boolean {
        if (!has(key)) return defaultValue
        return when (val rawValue = get(key)) {
            is Boolean -> rawValue
            else -> throw IllegalArgumentException("Manifest field $key must be a boolean")
        }
    }
}

internal fun normalizeArchiveEntryName(entryName: String): String {
    val normalized = entryName.replace('\\', '/').removePrefix("./").trimStart('/')
    check(normalized.isNotBlank() || entryName.isBlank()) {
        "Blocked unsafe plugin archive entry: $entryName"
    }
    val segments = normalized.split('/').filter { it.isNotBlank() }
    check(!normalized.startsWith("../") && segments.none { it == ".." }) {
        "Blocked unsafe plugin archive entry: $entryName"
    }
    check(!Regex("^[A-Za-z]:").containsMatchIn(normalized)) {
        "Blocked unsafe plugin archive entry: $entryName"
    }
    return normalized
}

internal fun compareVersions(left: String, right: String): Int {
    val leftParts = left.split('.')
    val rightParts = right.split('.')
    val segmentCount = maxOf(leftParts.size, rightParts.size)
    for (index in 0 until segmentCount) {
        val leftPart = leftParts.getOrElse(index) { "0" }
        val rightPart = rightParts.getOrElse(index) { "0" }
        val leftNumber = leftPart.toIntOrNull()
        val rightNumber = rightPart.toIntOrNull()
        val comparison = when {
            leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
            else -> leftPart.compareTo(rightPart)
        }
        if (comparison != 0) return comparison
    }
    return 0
}
