package com.astrbot.android.data.plugin.catalog

import com.astrbot.android.model.plugin.PluginCatalogEntry
import com.astrbot.android.model.plugin.PluginCatalogVersion
import com.astrbot.android.model.plugin.PluginPermissionDeclaration
import com.astrbot.android.model.plugin.PluginRepositorySource
import com.astrbot.android.model.plugin.PluginRiskLevel
import com.astrbot.android.runtime.RuntimeLogRepository
import org.json.JSONArray
import org.json.JSONObject

object PluginCatalogJson {
    fun decodeRepositorySource(json: String): PluginRepositorySource {
        RuntimeLogRepository.append("Plugin market parse start: chars=${json.length}")
        return runCatching {
            decodeRepositorySource(JSONObject(json))
        }.onSuccess { source ->
            RuntimeLogRepository.append(
                "Plugin market parse success: " +
                    "sourceId=${source.sourceId} " +
                    "plugins=${source.plugins.size} " +
                    "versions=${source.plugins.sumOf { it.versions.size }} " +
                    "preview=${source.plugins.take(3).joinToString(separator = ",") { it.pluginId }.ifBlank { "-" }}",
            )
        }.onFailure { error ->
            RuntimeLogRepository.append("Plugin market parse failed: error=${error.toRuntimeLogSummary()}")
        }.getOrThrow()
    }

    fun decodeRepositorySource(json: JSONObject): PluginRepositorySource {
        return PluginRepositorySource(
            sourceId = readRequiredString(json, "sourceId"),
            title = readRequiredString(json, "title"),
            catalogUrl = readRequiredString(json, "catalogUrl"),
            updatedAt = json.optLong("updatedAt", 0L),
            plugins = decodeEntries(json.optJSONArray("plugins")),
        )
    }

    private fun decodeEntries(array: JSONArray?): List<PluginCatalogEntry> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index)
                    ?: throw IllegalArgumentException("plugins[$index] must be an object")
                add(
                    PluginCatalogEntry(
                        pluginId = readRequiredString(item, "pluginId"),
                        title = readRequiredString(item, "title"),
                        author = readRequiredString(item, "author"),
                        repositoryUrl = item.optString("repositoryUrl")
                            .ifBlank { item.optString("repoUrl") }
                            .trim(),
                        description = readRequiredString(item, "description"),
                        entrySummary = readRequiredString(item, "entrySummary"),
                        scenarios = decodeStringList(item.optJSONArray("scenarios"), "scenarios"),
                        versions = decodeVersions(item.optJSONArray("versions")),
                    ),
                )
            }
        }
    }

    private fun decodeVersions(array: JSONArray?): List<PluginCatalogVersion> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index)
                    ?: throw IllegalArgumentException("versions[$index] must be an object")
                add(
                    PluginCatalogVersion(
                        version = readRequiredString(item, "version"),
                        packageUrl = readRequiredString(item, "packageUrl"),
                        publishedAt = item.optLong("publishedAt", 0L),
                        protocolVersion = item.optInt("protocolVersion", 0),
                        minHostVersion = readRequiredString(item, "minHostVersion"),
                        maxHostVersion = item.optString("maxHostVersion"),
                        permissions = decodePermissions(item.optJSONArray("permissions")),
                        changelog = item.optString("changelog"),
                    ),
                )
            }
        }
    }

    private fun decodePermissions(array: JSONArray?): List<PluginPermissionDeclaration> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index)
                    ?: throw IllegalArgumentException("permissions[$index] must be an object")
                add(
                    PluginPermissionDeclaration(
                        permissionId = readRequiredString(item, "permissionId"),
                        title = readRequiredString(item, "title"),
                        description = readRequiredString(item, "description"),
                        riskLevel = item.optString("riskLevel")
                            .takeIf { it.isNotBlank() }
                            ?.let { PluginRiskLevel.valueOf(it) }
                            ?: PluginRiskLevel.MEDIUM,
                        required = item.optBoolean("required", true),
                    ),
                )
            }
        }
    }

    private fun decodeStringList(array: JSONArray?, fieldName: String): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val value = array.optString(index)
                if (value.isBlank()) {
                    throw IllegalArgumentException("$fieldName[$index] must be a non-blank string")
                }
                add(value)
            }
        }
    }

    private fun readRequiredString(json: JSONObject, key: String): String {
        val value = json.optString(key)
        if (value.isBlank()) {
            throw IllegalArgumentException("$key is required")
        }
        return value
    }
}

private fun Throwable.toRuntimeLogSummary(): String {
    return message?.trim().takeUnless { it.isNullOrBlank() } ?: javaClass.simpleName
}
