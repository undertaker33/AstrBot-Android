package com.astrbot.android.feature.plugin.runtime

import com.astrbot.android.di.hilt.PluginHostVersion
import com.astrbot.android.di.hilt.SupportedPluginProtocolVersion
import com.astrbot.android.feature.plugin.data.normalizePackageValidationIssueMessage
import com.astrbot.android.feature.plugin.data.unsupportedProtocolCompatibilityNote
import com.astrbot.android.model.plugin.PluginCompatibilityState
import com.astrbot.android.model.plugin.PluginManifest
import com.astrbot.android.model.plugin.PluginPackageContract
import com.astrbot.android.model.plugin.PluginPackageContractJson
import com.astrbot.android.model.plugin.PluginPackageValidationIssue
import com.astrbot.android.model.plugin.PluginPermissionDeclaration
import com.astrbot.android.model.plugin.PluginRiskLevel
import com.astrbot.android.model.plugin.PluginSourceType
import java.io.File
import java.util.zip.ZipInputStream
import javax.inject.Inject
import org.json.JSONArray
import org.json.JSONObject

data class PluginPackageValidationResult(
    val manifest: PluginManifest,
    val compatibilityState: PluginCompatibilityState,
    val installable: Boolean,
    val packageContract: PluginPackageContract? = null,
    val validationIssues: List<PluginPackageValidationIssue> = emptyList(),
)

class PluginPackageValidator @Inject constructor(
    @PluginHostVersion
    private val hostVersion: String,
    @SupportedPluginProtocolVersion
    private val supportedProtocolVersion: Int,
) {
    fun validate(packageFile: File): PluginPackageValidationResult {
        require(packageFile.exists() && packageFile.isFile) {
            "Plugin package file was not found: ${packageFile.absolutePath}"
        }

        val archiveEntries = readArchiveEntries(packageFile)
        val manifest = parseManifest(readJsonEntry(archiveEntries, MANIFEST_FILE))
        val androidPluginJson = archiveEntries[ANDROID_PLUGIN_FILE_NAME]?.let(::JSONObject)
        val legacyContractPresent = archiveEntries.containsKey(LEGACY_EXECUTION_FILE_NAME)
        val validationIssues = mutableListOf<PluginPackageValidationIssue>()
        val packageContract = when {
            androidPluginJson == null -> null
            else -> runCatching { PluginPackageContractJson.decode(androidPluginJson) }
                .getOrElse { error ->
                    validationIssues += issue(
                        code = INVALID_PACKAGE_CONTRACT_ISSUE_CODE,
                        message = normalizeValidationIssueMessage(
                            code = INVALID_PACKAGE_CONTRACT_ISSUE_CODE,
                            rawMessage = error.message ?: "android-plugin.json is invalid.",
                        ),
                    )
                    null
                }
        }

        if (legacyContractPresent) {
            validationIssues += issue(
                code = LEGACY_CONTRACT_ISSUE_CODE,
                message = normalizeValidationIssueMessage(
                    code = LEGACY_CONTRACT_ISSUE_CODE,
                    rawMessage = "android-execution.json is legacy and is no longer the installation truth source.",
                ),
            )
        }

        if (androidPluginJson == null) {
            validationIssues += issue(
                code = MISSING_PACKAGE_CONTRACT_ISSUE_CODE,
                message = normalizeValidationIssueMessage(
                    code = MISSING_PACKAGE_CONTRACT_ISSUE_CODE,
                    rawMessage = "Plugin package is missing android-plugin.json",
                ),
            )
        }

        packageContract?.let { contract ->
            if (manifest.protocolVersion != contract.protocolVersion) {
                validationIssues += issue(
                    code = MANIFEST_PROTOCOL_MISMATCH_ISSUE_CODE,
                    message = "manifest.protocolVersion must match android-plugin.json protocolVersion.",
                )
            }

            if (!archiveContains(archiveEntries, contract.runtime.bootstrap)) {
                validationIssues += issue(
                    code = MISSING_RUNTIME_BOOTSTRAP_ISSUE_CODE,
                    message = normalizeValidationIssueMessage(
                        code = MISSING_RUNTIME_BOOTSTRAP_ISSUE_CODE,
                        rawMessage = "Missing runtime bootstrap file: ${contract.runtime.bootstrap}",
                    ),
                )
            }

            contract.config.staticSchema.takeIf(String::isNotBlank)?.let { schemaPath ->
                if (!archiveContains(archiveEntries, schemaPath)) {
                    validationIssues += issue(
                        code = MISSING_SCHEMA_FILE_ISSUE_CODE,
                        message = "Missing declared schema file: $schemaPath",
                    )
                }
            }

            contract.config.settingsSchema.takeIf(String::isNotBlank)?.let { schemaPath ->
                if (!archiveContains(archiveEntries, schemaPath)) {
                    validationIssues += issue(
                        code = MISSING_SCHEMA_FILE_ISSUE_CODE,
                        message = "Missing declared schema file: $schemaPath",
                    )
                }
            }
        }

        val minHostVersionSatisfied = compareVersions(hostVersion, manifest.minHostVersion) >= 0
        val maxHostVersionSatisfied = manifest.maxHostVersion.isBlank() ||
            compareVersions(hostVersion, manifest.maxHostVersion) <= 0

        val protocolSupported = manifest.protocolVersion == supportedProtocolVersion
        val compatibilityNotes = buildCompatibilityNotes(
            manifest = manifest,
            minHostVersionSatisfied = minHostVersionSatisfied,
            maxHostVersionSatisfied = maxHostVersionSatisfied,
        )
        val compatibilityState = PluginCompatibilityState.fromChecks(
            protocolSupported = protocolSupported,
            minHostVersionSatisfied = minHostVersionSatisfied,
            maxHostVersionSatisfied = maxHostVersionSatisfied,
            notes = compatibilityNotes,
        )

        return PluginPackageValidationResult(
            manifest = manifest,
            compatibilityState = compatibilityState,
            installable = validationIssues.isEmpty() &&
                protocolSupported &&
                minHostVersionSatisfied &&
                maxHostVersionSatisfied,
            packageContract = packageContract,
            validationIssues = validationIssues.toList(),
        )
    }

    private fun readArchiveEntries(packageFile: File): Map<String, String> {
        val entries = linkedMapOf<String, String>()
        ZipInputStream(packageFile.inputStream().buffered()).use { input ->
            var entry = input.nextEntry
            while (entry != null) {
                val normalizedName = normalizeArchiveEntryName(entry.name)
                if (!entry.isDirectory) {
                    entries[normalizedName] = input.readBytes().toString(Charsets.UTF_8)
                }
                entry = input.nextEntry
            }
        }
        return entries
    }

    private fun readJsonEntry(entries: Map<String, String>, entryName: String): JSONObject {
        val manifestText = entries[entryName]
            ?: throw IllegalArgumentException("Plugin package is missing $entryName")
        return JSONObject(manifestText)
    }

    private fun archiveContains(entries: Map<String, String>, path: String): Boolean {
        val normalizedPath = normalizeArchiveEntryName(path)
        return entries.containsKey(normalizedPath)
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

    private fun issue(code: String, message: String): PluginPackageValidationIssue {
        return PluginPackageValidationIssue(code = code, message = message)
    }

    private fun normalizeValidationIssueMessage(code: String, rawMessage: String): String {
        return normalizePackageValidationIssueMessage(
            PluginPackageValidationIssue(code = code, message = rawMessage),
        )
    }

    private fun buildCompatibilityNotes(
        manifest: PluginManifest,
        minHostVersionSatisfied: Boolean,
        maxHostVersionSatisfied: Boolean,
    ): String {
        val notes = mutableListOf<String>()
        unsupportedProtocolCompatibilityNote(
            protocolVersion = manifest.protocolVersion,
            supportedProtocolVersion = supportedProtocolVersion,
        )?.let(notes::add)
        if (!minHostVersionSatisfied) {
            notes += "Host version $hostVersion is below required minimum ${manifest.minHostVersion}."
        }
        if (!maxHostVersionSatisfied) {
            notes += "Host version $hostVersion exceeds supported maximum ${manifest.maxHostVersion}."
        }
        return notes.joinToString(separator = " ")
    }

    private companion object {
        const val MANIFEST_FILE = "manifest.json"
        const val ANDROID_PLUGIN_FILE_NAME = "android-plugin.json"
        const val LEGACY_EXECUTION_FILE_NAME = "android-execution.json"
        const val LEGACY_PROTOCOL_VERSION = 1
        const val LEGACY_CONTRACT_ISSUE_CODE = "legacy_contract"
        const val MISSING_PACKAGE_CONTRACT_ISSUE_CODE = "missing_package_contract"
        const val INVALID_PACKAGE_CONTRACT_ISSUE_CODE = "invalid_package_contract"
        const val MANIFEST_PROTOCOL_MISMATCH_ISSUE_CODE = "manifest_protocol_mismatch"
        const val MISSING_RUNTIME_BOOTSTRAP_ISSUE_CODE = "missing_runtime_bootstrap"
        const val MISSING_SCHEMA_FILE_ISSUE_CODE = "missing_schema_file"
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

