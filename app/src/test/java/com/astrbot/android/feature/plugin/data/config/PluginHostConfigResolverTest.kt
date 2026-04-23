package com.astrbot.android.feature.plugin.data.config

import com.astrbot.android.feature.plugin.data.PluginStoragePaths
import com.astrbot.android.model.plugin.PluginConfigEntryPointsSnapshot
import com.astrbot.android.model.plugin.PluginConfigStoreSnapshot
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginManifest
import com.astrbot.android.model.plugin.PluginPackageContractSnapshot
import com.astrbot.android.model.plugin.PluginRuntimeDeclarationSnapshot
import com.astrbot.android.model.plugin.PluginSource
import com.astrbot.android.model.plugin.PluginSourceType
import com.astrbot.android.model.plugin.PluginStaticConfigJson
import com.astrbot.android.model.plugin.PluginStaticConfigSchema
import com.astrbot.android.model.plugin.PluginStaticConfigValue
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class PluginHostConfigResolverTest {
    @Test
    fun resolve_returns_workspace_snapshot_boundary_snapshot_and_merged_settings() {
        val pluginId = "com.example.phase1.host-config"
        val filesDir = Files.createTempDirectory("plugin-host-config-files").toFile()
        val extractedDir = Files.createTempDirectory("plugin-host-config-extracted").toFile()
        val schemaFile = File(extractedDir, "config/static-schema.json").apply {
            parentFile?.mkdirs()
            writeText(
                """
                {
                  "token": { "type": "string", "default": "seed" },
                  "enabled": { "type": "bool", "default": true }
                }
                """.trimIndent(),
                Charsets.UTF_8,
            )
        }
        val settingsSchemaFile = File(extractedDir, "config/settings-schema.json").apply {
            parentFile?.mkdirs()
            writeText(
                """
                {
                  "title": "Runtime settings",
                  "sections": [
                    {
                      "sectionId": "general",
                      "title": "General",
                      "fields": [
                        {
                          "fieldType": "toggle",
                          "fieldId": "runtimeEnabled",
                          "label": "Runtime enabled",
                          "defaultValue": false
                        }
                      ]
                    }
                  ]
                }
                """.trimIndent(),
                Charsets.UTF_8,
            )
        }
        val configStore = FakePluginConfigStorage(
            installRecord = installedRecord(
                pluginId = pluginId,
                extractedDir = extractedDir,
                staticSchemaPath = extractedDir.toPath().relativize(schemaFile.toPath()).toString().replace("\\", "/"),
                settingsSchemaPath = extractedDir.toPath().relativize(settingsSchemaFile.toPath()).toString().replace("\\", "/"),
            ),
            schema = PluginStaticConfigJson.decodeSchema(
                JSONObject(schemaFile.readText(Charsets.UTF_8)),
            ),
            snapshot = PluginConfigStoreSnapshot(
                coreValues = mapOf(
                    "token" to PluginStaticConfigValue.StringValue("override"),
                ),
            ),
        )

        val resolver = DefaultPluginHostConfigResolver(
            storagePaths = PluginStoragePaths.fromFilesDir(filesDir),
            configStorage = configStore,
        )

        val resolved = resolver.resolve(pluginId)

        assertEquals(setOf("token", "enabled"), resolved.configBoundary?.coreFieldKeys)
        assertEquals("override", resolved.configSnapshot.coreValues["token"]?.let { (it as PluginStaticConfigValue.StringValue).value })
        assertEquals("override", resolved.mergedSettings["token"])
        assertEquals(true, resolved.mergedSettings["enabled"])
        assertTrue(
            resolved.workspaceSnapshot.privateRootPath
                .replace('\\', '/')
                .endsWith("plugins/private/$pluginId"),
        )
    }

    @Test
    fun resolve_includes_settings_schema_fields_in_boundary_snapshot_and_merged_settings() {
        val pluginId = "com.example.phase1.host-config.settings"
        val filesDir = Files.createTempDirectory("plugin-host-config-files").toFile()
        val extractedDir = Files.createTempDirectory("plugin-host-config-extracted").toFile()
        val schemaFile = File(extractedDir, "config/static-schema.json").apply {
            parentFile?.mkdirs()
            writeText(
                """
                {
                  "token": { "type": "string", "default": "seed" }
                }
                """.trimIndent(),
                Charsets.UTF_8,
            )
        }
        val settingsSchemaFile = File(extractedDir, "config/settings-schema.json").apply {
            parentFile?.mkdirs()
            writeText(
                """
                {
                  "title": "Runtime settings",
                  "sections": [
                    {
                      "sectionId": "general",
                      "title": "General",
                      "fields": [
                        {
                          "fieldType": "toggle",
                          "fieldId": "runtimeEnabled",
                          "label": "Runtime enabled",
                          "defaultValue": true
                        },
                        {
                          "fieldType": "text_input",
                          "fieldId": "runtimeToken",
                          "label": "Runtime token",
                          "defaultValue": "settings-default"
                        }
                      ]
                    }
                  ]
                }
                """.trimIndent(),
                Charsets.UTF_8,
            )
        }
        val configStore = FakePluginConfigStorage(
            installRecord = installedRecord(
                pluginId = pluginId,
                extractedDir = extractedDir,
                staticSchemaPath = extractedDir.toPath().relativize(schemaFile.toPath()).toString().replace("\\", "/"),
                settingsSchemaPath = extractedDir.toPath().relativize(settingsSchemaFile.toPath()).toString().replace("\\", "/"),
            ),
            schema = PluginStaticConfigJson.decodeSchema(
                JSONObject(schemaFile.readText(Charsets.UTF_8)),
            ),
            snapshot = PluginConfigStoreSnapshot(
                coreValues = mapOf(
                    "token" to PluginStaticConfigValue.StringValue("core-override"),
                ),
                extensionValues = mapOf(
                    "runtimeToken" to PluginStaticConfigValue.StringValue("runtime-override"),
                    "runtimeEnabled" to PluginStaticConfigValue.BoolValue(false),
                ),
            ),
        )

        val resolver = DefaultPluginHostConfigResolver(
            storagePaths = PluginStoragePaths.fromFilesDir(filesDir),
            configStorage = configStore,
        )

        val resolved = resolver.resolve(pluginId)

        assertEquals(setOf("token"), resolved.configBoundary?.coreFieldKeys)
        assertEquals(
            setOf("runtimeEnabled", "runtimeToken"),
            resolved.configBoundary?.extensionFieldKeys,
        )
        assertEquals(
            "runtime-override",
            (resolved.configSnapshot.extensionValues["runtimeToken"] as? PluginStaticConfigValue.StringValue)?.value,
        )
        assertEquals(
            false,
            (resolved.configSnapshot.extensionValues["runtimeEnabled"] as? PluginStaticConfigValue.BoolValue)?.value,
        )
        assertEquals("core-override", resolved.mergedSettings["token"])
        assertEquals("runtime-override", resolved.mergedSettings["runtimeToken"])
        assertEquals(false, resolved.mergedSettings["runtimeEnabled"])
    }

    @Test
    fun resolve_supports_plugins_that_only_define_settings_schema() {
        val pluginId = "com.example.phase1.host-config.settings-only"
        val filesDir = Files.createTempDirectory("plugin-host-config-files").toFile()
        val extractedDir = Files.createTempDirectory("plugin-host-config-extracted").toFile()
        val settingsSchemaFile = File(extractedDir, "config/settings-schema.json").apply {
            parentFile?.mkdirs()
            writeText(
                """
                {
                  "title": "Runtime settings",
                  "sections": [
                    {
                      "sectionId": "general",
                      "title": "General",
                      "fields": [
                        {
                          "fieldType": "text_input",
                          "fieldId": "runtimeToken",
                          "label": "Runtime token",
                          "defaultValue": "settings-default"
                        }
                      ]
                    }
                  ]
                }
                """.trimIndent(),
                Charsets.UTF_8,
            )
        }
        val configStore = FakePluginConfigStorage(
            installRecord = installedRecord(
                pluginId = pluginId,
                extractedDir = extractedDir,
                staticSchemaPath = "",
                settingsSchemaPath = extractedDir.toPath().relativize(settingsSchemaFile.toPath()).toString().replace("\\", "/"),
            ),
            schema = null,
            snapshot = PluginConfigStoreSnapshot(
                extensionValues = mapOf(
                    "runtimeToken" to PluginStaticConfigValue.StringValue("runtime-override"),
                ),
            ),
        )

        val resolver = DefaultPluginHostConfigResolver(
            storagePaths = PluginStoragePaths.fromFilesDir(filesDir),
            configStorage = configStore,
        )

        val resolved = resolver.resolve(pluginId)

        assertEquals(emptySet<String>(), resolved.configBoundary?.coreFieldKeys)
        assertEquals(setOf("runtimeToken"), resolved.configBoundary?.extensionFieldKeys)
        assertEquals(
            "runtime-override",
            (resolved.configSnapshot.extensionValues["runtimeToken"] as? PluginStaticConfigValue.StringValue)?.value,
        )
        assertEquals("runtime-override", resolved.mergedSettings["runtimeToken"])
    }

    private fun installedRecord(
        pluginId: String,
        extractedDir: File,
        staticSchemaPath: String,
        settingsSchemaPath: String = "config/settings-schema.json",
    ): PluginInstallRecord {
        return PluginInstallRecord.restoreFromPersistedState(
            manifestSnapshot = PluginManifest(
                pluginId = pluginId,
                version = "1.0.0",
                protocolVersion = 2,
                author = "AstrBot",
                title = "Demo",
                description = "Demo plugin",
                minHostVersion = "0.0.1",
                sourceType = PluginSourceType.LOCAL_FILE,
                entrySummary = "summary",
            ),
            source = PluginSource(
                sourceType = PluginSourceType.LOCAL_FILE,
                location = File(extractedDir.parentFile, "$pluginId.zip").absolutePath,
                importedAt = 1L,
            ),
            packageContractSnapshot = PluginPackageContractSnapshot(
                protocolVersion = 2,
                runtime = PluginRuntimeDeclarationSnapshot(
                    kind = "js_quickjs",
                    bootstrap = "runtime/index.js",
                    apiVersion = 2,
                ),
                config = PluginConfigEntryPointsSnapshot(
                    staticSchema = staticSchemaPath,
                    settingsSchema = settingsSchemaPath,
                ),
            ),
            enabled = true,
            installedAt = 1L,
            lastUpdatedAt = 2L,
            localPackagePath = File(extractedDir.parentFile, "$pluginId.zip").absolutePath,
            extractedDir = extractedDir.absolutePath,
        )
    }
}

private class FakePluginConfigStorage(
    private val installRecord: PluginInstallRecord,
    private val schema: PluginStaticConfigSchema?,
    private val snapshot: PluginConfigStoreSnapshot,
) : PluginConfigStorage {
    override fun resolveConfigSnapshot(
        pluginId: String,
        boundary: com.astrbot.android.model.plugin.PluginConfigStorageBoundary,
    ): PluginConfigStoreSnapshot {
        return boundary.createSnapshot(
            coreValues = (boundary.coreDefaults + snapshot.coreValues)
                .filterKeys { key -> key in boundary.coreFieldKeys },
            extensionValues = snapshot.extensionValues
                .filterKeys { key -> key in boundary.extensionFieldKeys },
        )
    }

    override fun saveCoreConfig(
        pluginId: String,
        boundary: com.astrbot.android.model.plugin.PluginConfigStorageBoundary,
        coreValues: Map<String, PluginStaticConfigValue>,
    ): PluginConfigStoreSnapshot = snapshot

    override fun saveExtensionConfig(
        pluginId: String,
        boundary: com.astrbot.android.model.plugin.PluginConfigStorageBoundary,
        extensionValues: Map<String, PluginStaticConfigValue>,
    ): PluginConfigStoreSnapshot = snapshot

    override fun getInstalledStaticConfigSchema(pluginId: String): PluginStaticConfigSchema? = schema

    override fun resolveInstalledStaticConfigSchemaPath(pluginId: String): String? {
        val relativePath = installRecord.packageContractSnapshot?.config?.staticSchema.orEmpty()
        if (relativePath.isBlank()) return null
        return File(installRecord.extractedDir, relativePath).absolutePath
    }

    override fun resolveInstalledSettingsSchemaPath(pluginId: String): String? {
        val relativePath = installRecord.packageContractSnapshot?.config?.settingsSchema.orEmpty()
        if (relativePath.isBlank()) return null
        return File(installRecord.extractedDir, relativePath).absolutePath
    }

    override fun deleteSnapshot(pluginId: String) = Unit
}
