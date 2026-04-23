package com.astrbot.android.feature.plugin.domain.cleanup

import com.astrbot.android.feature.plugin.data.PluginStoragePaths
import com.astrbot.android.feature.plugin.data.config.PluginConfigStorage
import com.astrbot.android.feature.plugin.data.state.RoomPluginStateStore
import com.astrbot.android.feature.plugin.data.state.PluginStateStore
import com.astrbot.android.feature.plugin.data.state.PluginStateScope
import com.astrbot.android.data.db.PluginStateEntryDao
import com.astrbot.android.data.db.PluginStateEntryEntity
import com.astrbot.android.model.plugin.PluginConfigEntryPointsSnapshot
import com.astrbot.android.model.plugin.PluginConfigStorageBoundary
import com.astrbot.android.model.plugin.PluginConfigStoreSnapshot
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginManifest
import com.astrbot.android.model.plugin.PluginPackageContractSnapshot
import com.astrbot.android.model.plugin.PluginRuntimeDeclarationSnapshot
import com.astrbot.android.model.plugin.PluginSource
import com.astrbot.android.model.plugin.PluginSourceType
import com.astrbot.android.model.plugin.PluginStaticConfigSchema
import com.astrbot.android.model.plugin.PluginStaticConfigValue
import com.astrbot.android.model.plugin.PluginUninstallPolicy
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginDataCleanupServiceTest {
    @Test
    fun keep_data_removes_install_artifacts_but_preserves_config_state_and_workspace() {
        val filesDir = Files.createTempDirectory("plugin-cleanup-keep-files").toFile()
        val storagePaths = PluginStoragePaths.fromFilesDir(filesDir)
        val record = createInstalledRecord(
            pluginId = "plugin.keep",
            packageFile = File(filesDir, "packages/plugin.keep.zip").apply {
                parentFile?.mkdirs()
                writeText("package")
            },
            extractedDir = File(filesDir, "extracted/plugin.keep").apply {
                mkdirs()
                File(this, "runtime.js").writeText("runtime")
            },
        )
        val privateDir = storagePaths.privateDir(record.pluginId).apply {
            mkdirs()
            File(this, "workspace.txt").writeText("workspace")
        }
        val configStorage = RecordingPluginConfigStorage()
        val stateStore = RoomPluginStateStore(
            dao = InMemoryPluginStateEntryDao(),
            clock = { 10L },
        ).also { store ->
            store.putValueJson(
                pluginId = record.pluginId,
                scope = PluginStateScope.plugin(),
                key = "plugin.alpha",
                valueJson = "\"persisted\"",
            )
            store.putValueJson(
                pluginId = record.pluginId,
                scope = PluginStateScope.session("qq:group:group:30003:user:20002"),
                key = "session.beta",
                valueJson = "2",
            )
        }
        val runtimeCleaner = RecordingPluginRuntimeArtifactCleaner()
        val service = DefaultPluginDataCleanupService(
            storagePaths = storagePaths,
            configStorage = configStorage,
            stateStore = stateStore,
            runtimeArtifactCleaner = runtimeCleaner,
        )

        service.cleanupForUninstall(record, PluginUninstallPolicy.KEEP_DATA)

        assertFalse(File(record.localPackagePath).exists())
        assertFalse(File(record.extractedDir).exists())
        assertTrue(privateDir.exists())
        assertEquals(emptyList<String>(), configStorage.deletedPluginIds)
        assertEquals(
            "\"persisted\"",
            stateStore.getValueJson(record.pluginId, PluginStateScope.plugin(), "plugin.alpha"),
        )
        assertEquals(
            "2",
            stateStore.getValueJson(
                record.pluginId,
                PluginStateScope.session("qq:group:group:30003:user:20002"),
                "session.beta",
            ),
        )
        assertEquals(listOf(record.pluginId), runtimeCleaner.cleanedPluginIds)
    }

    @Test
    fun remove_data_removes_install_artifacts_config_state_and_workspace() {
        val filesDir = Files.createTempDirectory("plugin-cleanup-remove-files").toFile()
        val storagePaths = PluginStoragePaths.fromFilesDir(filesDir)
        val record = createInstalledRecord(
            pluginId = "plugin.remove",
            packageFile = File(filesDir, "packages/plugin.remove.zip").apply {
                parentFile?.mkdirs()
                writeText("package")
            },
            extractedDir = File(filesDir, "extracted/plugin.remove").apply {
                mkdirs()
                File(this, "runtime.js").writeText("runtime")
            },
        )
        val privateDir = storagePaths.privateDir(record.pluginId).apply {
            mkdirs()
            File(this, "workspace.txt").writeText("workspace")
        }
        val configStorage = RecordingPluginConfigStorage()
        val stateStore = RoomPluginStateStore(
            dao = InMemoryPluginStateEntryDao(),
            clock = { 20L },
        ).also { store ->
            store.putValueJson(
                pluginId = record.pluginId,
                scope = PluginStateScope.plugin(),
                key = "plugin.alpha",
                valueJson = "\"remove-me\"",
            )
            store.putValueJson(
                pluginId = record.pluginId,
                scope = PluginStateScope.session("qq:group:group:30003:user:20002"),
                key = "session.beta",
                valueJson = "3",
            )
        }
        val runtimeCleaner = RecordingPluginRuntimeArtifactCleaner()
        val service = DefaultPluginDataCleanupService(
            storagePaths = storagePaths,
            configStorage = configStorage,
            stateStore = stateStore,
            runtimeArtifactCleaner = runtimeCleaner,
        )

        service.cleanupForUninstall(record, PluginUninstallPolicy.REMOVE_DATA)

        assertFalse(File(record.localPackagePath).exists())
        assertFalse(File(record.extractedDir).exists())
        assertFalse(privateDir.exists())
        assertEquals(listOf(record.pluginId), configStorage.deletedPluginIds)
        assertEquals(
            null,
            stateStore.getValueJson(record.pluginId, PluginStateScope.plugin(), "plugin.alpha"),
        )
        assertEquals(
            emptyList<String>(),
            stateStore.listKeys(
                record.pluginId,
                PluginStateScope.session("qq:group:group:30003:user:20002"),
            ),
        )
        assertEquals(listOf(record.pluginId), runtimeCleaner.cleanedPluginIds)
    }

    private fun createInstalledRecord(
        pluginId: String,
        packageFile: File,
        extractedDir: File,
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
                location = packageFile.absolutePath,
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
                    staticSchema = "config/static-schema.json",
                    settingsSchema = "config/settings-schema.json",
                ),
            ),
            enabled = true,
            installedAt = 1L,
            lastUpdatedAt = 2L,
            localPackagePath = packageFile.absolutePath,
            extractedDir = extractedDir.absolutePath,
        )
    }
}

private class RecordingPluginConfigStorage : PluginConfigStorage {
    val deletedPluginIds = mutableListOf<String>()

    override fun resolveConfigSnapshot(
        pluginId: String,
        boundary: PluginConfigStorageBoundary,
    ): PluginConfigStoreSnapshot = PluginConfigStoreSnapshot()

    override fun saveCoreConfig(
        pluginId: String,
        boundary: PluginConfigStorageBoundary,
        coreValues: Map<String, PluginStaticConfigValue>,
    ): PluginConfigStoreSnapshot = PluginConfigStoreSnapshot(coreValues = coreValues)

    override fun saveExtensionConfig(
        pluginId: String,
        boundary: PluginConfigStorageBoundary,
        extensionValues: Map<String, PluginStaticConfigValue>,
    ): PluginConfigStoreSnapshot = PluginConfigStoreSnapshot(extensionValues = extensionValues)

    override fun getInstalledStaticConfigSchema(pluginId: String): PluginStaticConfigSchema? = null

    override fun resolveInstalledStaticConfigSchemaPath(pluginId: String): String? = null

    override fun resolveInstalledSettingsSchemaPath(pluginId: String): String? = null

    override fun deleteSnapshot(pluginId: String) {
        deletedPluginIds += pluginId
    }
}

private class RecordingPluginRuntimeArtifactCleaner : PluginRuntimeArtifactCleaner {
    val cleanedPluginIds = mutableListOf<String>()

    override fun cleanup(pluginId: String) {
        cleanedPluginIds += pluginId
    }
}

private class InMemoryPluginStateEntryDao : PluginStateEntryDao {
    private val entries = linkedMapOf<String, PluginStateEntryEntity>()

    override suspend fun get(
        pluginId: String,
        scopeKind: String,
        scopeId: String,
        key: String,
    ): PluginStateEntryEntity? = entries[index(pluginId, scopeKind, scopeId, key)]

    override suspend fun upsert(entity: PluginStateEntryEntity) {
        entries[index(entity.pluginId, entity.scopeKind, entity.scopeId, entity.key)] = entity
    }

    override suspend fun listKeys(
        pluginId: String,
        scopeKind: String,
        scopeId: String,
        prefix: String,
    ): List<String> {
        return entries.values
            .filter { entry ->
                entry.pluginId == pluginId &&
                    entry.scopeKind == scopeKind &&
                    entry.scopeId == scopeId &&
                    (prefix.isBlank() || entry.key.startsWith(prefix))
            }
            .map(PluginStateEntryEntity::key)
            .sorted()
    }

    override suspend fun delete(
        pluginId: String,
        scopeKind: String,
        scopeId: String,
        key: String,
    ) {
        entries.remove(index(pluginId, scopeKind, scopeId, key))
    }

    override suspend fun clearScope(
        pluginId: String,
        scopeKind: String,
        scopeId: String,
        prefix: String,
    ) {
        entries.keys
            .filter { composite ->
                val entry = entries.getValue(composite)
                entry.pluginId == pluginId &&
                    entry.scopeKind == scopeKind &&
                    entry.scopeId == scopeId &&
                    (prefix.isBlank() || entry.key.startsWith(prefix))
            }
            .toList()
            .forEach(entries::remove)
    }

    override suspend fun deleteByPluginId(pluginId: String) {
        entries.keys
            .filter { composite -> entries.getValue(composite).pluginId == pluginId }
            .toList()
            .forEach(entries::remove)
    }

    private fun index(
        pluginId: String,
        scopeKind: String,
        scopeId: String,
        key: String,
    ): String = listOf(pluginId, scopeKind, scopeId, key).joinToString(separator = "::")
}
