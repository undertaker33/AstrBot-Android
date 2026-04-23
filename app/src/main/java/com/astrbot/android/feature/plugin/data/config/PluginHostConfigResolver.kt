package com.astrbot.android.feature.plugin.data.config

import com.astrbot.android.feature.plugin.data.PluginStoragePaths
import com.astrbot.android.model.plugin.ExternalPluginWorkspacePolicy
import com.astrbot.android.model.plugin.PluginConfigStorageBoundary
import com.astrbot.android.model.plugin.PluginConfigStoreSnapshot
import com.astrbot.android.model.plugin.PluginHostWorkspaceSnapshot
import com.astrbot.android.model.plugin.PluginExecutionProtocolJson
import com.astrbot.android.model.plugin.PluginSettingsField
import com.astrbot.android.model.plugin.PluginSettingsSchema
import com.astrbot.android.model.plugin.PluginStaticConfigSchema
import com.astrbot.android.model.plugin.PluginStaticConfigValue
import com.astrbot.android.model.plugin.SelectSettingField
import com.astrbot.android.model.plugin.SettingsUiRequest
import com.astrbot.android.model.plugin.TextInputSettingField
import com.astrbot.android.model.plugin.ToggleSettingField
import com.astrbot.android.model.plugin.toStorageBoundary
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

data class ResolvedPluginHostConfig(
    val installedStaticSchema: PluginStaticConfigSchema? = null,
    val workspaceSnapshot: PluginHostWorkspaceSnapshot = PluginHostWorkspaceSnapshot(),
    val configBoundary: PluginConfigStorageBoundary? = null,
    val configSnapshot: PluginConfigStoreSnapshot = PluginConfigStoreSnapshot(),
    val mergedSettings: Map<String, Any?> = emptyMap(),
)

interface PluginHostConfigResolver {
    fun resolve(pluginId: String): ResolvedPluginHostConfig
}

internal object EmptyPluginHostConfigResolver : PluginHostConfigResolver {
    override fun resolve(pluginId: String): ResolvedPluginHostConfig = ResolvedPluginHostConfig()
}

@Singleton
class DefaultPluginHostConfigResolver @Inject constructor(
    private val storagePaths: PluginStoragePaths,
    private val configStorage: PluginConfigStorage,
) : PluginHostConfigResolver {
    override fun resolve(pluginId: String): ResolvedPluginHostConfig {
        val workspaceSnapshot = ExternalPluginWorkspacePolicy.snapshot(
            storagePaths = storagePaths,
            pluginId = pluginId,
        )
        val staticSchema = configStorage.getInstalledStaticConfigSchema(pluginId)
        val settingsSchema = loadSettingsSchema(pluginId)
        val configBoundary = buildBoundary(
            staticSchema = staticSchema,
            settingsSchema = settingsSchema,
        )
        val configSnapshot = configBoundary?.let { boundary ->
            configStorage.resolveConfigSnapshot(
                pluginId = pluginId,
                boundary = boundary,
            )
        } ?: PluginConfigStoreSnapshot()
        return ResolvedPluginHostConfig(
            installedStaticSchema = staticSchema,
            workspaceSnapshot = workspaceSnapshot,
            configBoundary = configBoundary,
            configSnapshot = configSnapshot,
            mergedSettings = mergeSettings(
                pluginId = pluginId,
                staticSchema = staticSchema,
                settingsSchema = settingsSchema,
                configSnapshot = configSnapshot,
            ),
        )
    }

    private fun buildBoundary(
        staticSchema: PluginStaticConfigSchema?,
        settingsSchema: PluginSettingsSchema?,
    ): PluginConfigStorageBoundary? {
        val coreFieldKeys = staticSchema?.fields
            ?.map { field -> field.fieldKey }
            ?.toSet()
            .orEmpty()
        val extensionFieldKeys = settingsSchema
            ?.allFieldIds()
            ?.minus(coreFieldKeys)
            .orEmpty()
        return when {
            staticSchema != null -> staticSchema.toStorageBoundary(extensionFieldKeys = extensionFieldKeys)
            extensionFieldKeys.isNotEmpty() -> PluginConfigStorageBoundary(
                coreFieldKeys = emptySet(),
                extensionFieldKeys = extensionFieldKeys,
            )
            else -> null
        }
    }

    private fun mergeSettings(
        pluginId: String,
        staticSchema: PluginStaticConfigSchema?,
        settingsSchema: PluginSettingsSchema?,
        configSnapshot: PluginConfigStoreSnapshot,
    ): Map<String, Any?> {
        val coreFieldKeys = staticSchema?.fields
            ?.map { field -> field.fieldKey }
            ?.toSet()
            .orEmpty()
        val staticDefaults = if (staticSchema != null && staticSchema.fields.isNotEmpty()) {
            staticSchema.fields.associate { field ->
                field.fieldKey to unwrapConfigValue(field.defaultValue)
            }
        } else {
            configStorage.resolveInstalledStaticConfigSchemaPath(pluginId)
                ?.let(::readJsonFile)
                ?.let(::extractDefaultsFromRawJson)
                .orEmpty()
        }
        val settingsDefaults = settingsSchema
            ?.defaultValues()
            ?.filterKeys { key -> key !in coreFieldKeys }
            ?.mapValues { (_, value) -> unwrapConfigValue(value) }
            .orEmpty()
        val persistedCore = configSnapshot.coreValues.mapValues { (_, value) -> unwrapConfigValue(value) }
        val persistedExtension = configSnapshot.extensionValues.mapValues { (_, value) -> unwrapConfigValue(value) }
        return staticDefaults + settingsDefaults + persistedCore + persistedExtension
    }

    private fun loadSettingsSchema(pluginId: String): PluginSettingsSchema? {
        val settingsSchemaJson = configStorage.resolveInstalledSettingsSchemaPath(pluginId)
            ?.let(::readJsonFile)
            ?: return null
        return runCatching {
            val result = PluginExecutionProtocolJson.decodeResult(
                JSONObject().apply {
                    put("resultType", "settings_ui")
                    put("schema", settingsSchemaJson)
                },
            )
            (result as? SettingsUiRequest)?.schema
        }.getOrNull()
    }

    private fun readJsonFile(path: String): JSONObject? {
        return File(path)
            .takeIf(File::isFile)
            ?.readText(Charsets.UTF_8)
            ?.let(::JSONObject)
    }

    private fun extractDefaultsFromRawJson(json: JSONObject): Map<String, Any?> {
        val result = linkedMapOf<String, Any?>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val field = json.optJSONObject(key) ?: continue
            if (!field.has("default")) continue
            result[key] = when (val defaultValue = field.get("default")) {
                is String, is Boolean, is Int, is Long, is Double, is Float -> defaultValue
                JSONObject.NULL -> null
                else -> defaultValue.toString()
            }
        }
        return result
    }

    private fun unwrapConfigValue(value: PluginStaticConfigValue?): Any? {
        return when (value) {
            is PluginStaticConfigValue.StringValue -> value.value
            is PluginStaticConfigValue.IntValue -> value.value
            is PluginStaticConfigValue.FloatValue -> value.value
            is PluginStaticConfigValue.BoolValue -> value.value
            null -> null
        }
    }

    private fun PluginSettingsSchema.allFieldIds(): Set<String> {
        return sections
            .flatMap { section -> section.fields }
            .map(PluginSettingsField::fieldId)
            .toSet()
    }

    private fun PluginSettingsSchema.defaultValues(): Map<String, PluginStaticConfigValue> {
        return sections
            .flatMap { section -> section.fields }
            .mapNotNull { field ->
                field.defaultConfigValue()?.let { defaultValue -> field.fieldId to defaultValue }
            }
            .toMap()
    }

    private fun PluginSettingsField.defaultConfigValue(): PluginStaticConfigValue? {
        return when (this) {
            is ToggleSettingField -> PluginStaticConfigValue.BoolValue(defaultValue)
            is TextInputSettingField -> PluginStaticConfigValue.StringValue(defaultValue)
            is SelectSettingField -> PluginStaticConfigValue.StringValue(defaultValue)
        }
    }
}
