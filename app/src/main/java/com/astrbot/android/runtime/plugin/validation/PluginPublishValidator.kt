package com.astrbot.android.runtime.plugin.validation

import com.astrbot.android.model.plugin.PluginStaticConfigJson
import com.astrbot.android.model.plugin.PluginValidationIssue
import com.astrbot.android.model.plugin.PluginValidationReport
import com.astrbot.android.model.plugin.PluginValidationRule
import com.astrbot.android.model.plugin.PluginValidationSeverity
import com.astrbot.android.runtime.plugin.PluginPackageValidator
import com.astrbot.android.runtime.plugin.normalizeArchiveEntryName
import java.io.File
import java.util.zip.ZipInputStream
import org.json.JSONObject

class PluginPublishValidator(
    hostVersion: String,
    supportedProtocolVersion: Int,
) {
    private val packageValidator = PluginPackageValidator(
        hostVersion = hostVersion,
        supportedProtocolVersion = supportedProtocolVersion,
    )

    fun validatePackage(packageFile: File): PluginValidationReport {
        val issues = mutableListOf<PluginValidationIssue>()
        val archiveContents = inspectArchive(packageFile = packageFile, issues = issues)
        val manifestValidation = runCatching { packageValidator.validate(packageFile) }
            .getOrElse { error ->
                issues += PluginValidationIssue(
                    rule = MANIFEST_INVALID_RULE,
                    message = error.message ?: "Manifest validation failed.",
                )
                return PluginValidationReport(issues = issues)
            }

        if (!manifestValidation.compatibilityState.isCompatible()) {
            issues += PluginValidationIssue(
                rule = MANIFEST_COMPATIBILITY_RULE,
                message = manifestValidation.compatibilityState.notes.ifBlank {
                    "Plugin manifest is incompatible with the current host."
                },
            )
        }

        val staticSchemaText = archiveContents["_conf_schema.json"]
        if (staticSchemaText == null) {
            issues += PluginValidationIssue(
                rule = SCHEMA_FILE_MISSING_RULE,
                message = "Android import package must include _conf_schema.json at zip root.",
            )
        } else {
            runCatching {
                PluginStaticConfigJson.decodeSchema(JSONObject(staticSchemaText))
            }.onFailure { error ->
                issues += PluginValidationIssue(
                    rule = SCHEMA_DECODE_FAILED_RULE,
                    message = error.message ?: "Static config schema cannot be parsed.",
                )
            }
        }

        archiveContents.keys
            .filter(::isRuntimeWorkspaceEntry)
            .forEach { path ->
                issues += PluginValidationIssue(
                    rule = PACKAGE_RUNTIME_WORKSPACE_RULE,
                    message = "Package should not bundle runtime workspace content: $path",
                )
            }

        return PluginValidationReport(
            pluginId = manifestValidation.manifest.pluginId,
            pluginVersion = manifestValidation.manifest.version,
            issues = issues,
        )
    }

    private fun inspectArchive(
        packageFile: File,
        issues: MutableList<PluginValidationIssue>,
    ): Map<String, String> {
        if (!packageFile.exists() || !packageFile.isFile) {
            issues += PluginValidationIssue(
                rule = MANIFEST_INVALID_RULE,
                message = "Plugin package file was not found: ${packageFile.absolutePath}",
            )
            return emptyMap()
        }

        val contents = linkedMapOf<String, String>()
        runCatching {
            ZipInputStream(packageFile.inputStream().buffered()).use { input ->
                var entry = input.nextEntry
                while (entry != null) {
                    val normalizedName = normalizeArchiveEntryName(entry.name)
                    if (!entry.isDirectory && normalizedName.isNotBlank()) {
                        contents[normalizedName] = input.readBytes().toString(Charsets.UTF_8)
                    }
                    entry = input.nextEntry
                }
            }
        }.onFailure { error ->
            issues += PluginValidationIssue(
                rule = PACKAGE_UNSAFE_ARCHIVE_RULE,
                message = error.message ?: "Plugin archive contains unsafe entries.",
            )
        }
        return contents
    }

    private fun isRuntimeWorkspaceEntry(path: String): Boolean {
        return path.startsWith("data/") || path.startsWith("cache/") || path.startsWith("exports/")
    }

    private companion object {
        val MANIFEST_INVALID_RULE = PluginValidationRule(
            ruleId = "manifest.invalid",
            title = "Manifest invalid",
            defaultSeverity = PluginValidationSeverity.ERROR,
        )
        val MANIFEST_COMPATIBILITY_RULE = PluginValidationRule(
            ruleId = "manifest.compatibility.unsupported",
            title = "Manifest compatibility unsupported",
            defaultSeverity = PluginValidationSeverity.ERROR,
        )
        val SCHEMA_FILE_MISSING_RULE = PluginValidationRule(
            ruleId = "schema.file.missing",
            title = "Static schema file missing",
            defaultSeverity = PluginValidationSeverity.ERROR,
        )
        val SCHEMA_DECODE_FAILED_RULE = PluginValidationRule(
            ruleId = "schema.decode.failed",
            title = "Static schema decode failed",
            defaultSeverity = PluginValidationSeverity.ERROR,
        )
        val PACKAGE_RUNTIME_WORKSPACE_RULE = PluginValidationRule(
            ruleId = "package.runtime.workspace.content",
            title = "Runtime workspace content bundled",
            defaultSeverity = PluginValidationSeverity.WARNING,
        )
        val PACKAGE_UNSAFE_ARCHIVE_RULE = PluginValidationRule(
            ruleId = "package.archive.unsafe_path",
            title = "Unsafe archive path",
            defaultSeverity = PluginValidationSeverity.ERROR,
        )
    }
}
