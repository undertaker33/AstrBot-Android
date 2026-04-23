package com.astrbot.android.feature.plugin.data.config

import com.astrbot.android.data.db.PluginConfigSnapshotDao
import com.astrbot.android.data.db.PluginInstallAggregateDao
import com.astrbot.android.data.db.toInstallRecord
import com.astrbot.android.data.db.toSnapshot
import com.astrbot.android.data.db.toEntity
import com.astrbot.android.model.plugin.PluginConfigStorageBoundary
import com.astrbot.android.model.plugin.PluginConfigStoreSnapshot
import com.astrbot.android.model.plugin.PluginStaticConfigJson
import com.astrbot.android.model.plugin.PluginStaticConfigSchema
import com.astrbot.android.model.plugin.PluginStaticConfigValue
import com.astrbot.android.model.plugin.resolvePluginPackageSnapshotFile
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

interface PluginConfigStorage {
    fun resolveConfigSnapshot(
        pluginId: String,
        boundary: PluginConfigStorageBoundary,
    ): PluginConfigStoreSnapshot

    fun saveCoreConfig(
        pluginId: String,
        boundary: PluginConfigStorageBoundary,
        coreValues: Map<String, PluginStaticConfigValue>,
    ): PluginConfigStoreSnapshot

    fun saveExtensionConfig(
        pluginId: String,
        boundary: PluginConfigStorageBoundary,
        extensionValues: Map<String, PluginStaticConfigValue>,
    ): PluginConfigStoreSnapshot

    fun getInstalledStaticConfigSchema(pluginId: String): PluginStaticConfigSchema?

    fun resolveInstalledStaticConfigSchemaPath(pluginId: String): String?

    fun resolveInstalledSettingsSchemaPath(pluginId: String): String?

    fun deleteSnapshot(pluginId: String)
}

@Singleton
class RoomPluginConfigStorage @Inject constructor(
    private val pluginInstallDao: PluginInstallAggregateDao,
    private val pluginConfigDao: PluginConfigSnapshotDao,
) : PluginConfigStorage {

    override fun resolveConfigSnapshot(
        pluginId: String,
        boundary: PluginConfigStorageBoundary,
    ): PluginConfigStoreSnapshot {
        requireInstalledPlugin(pluginId)
        val persisted = runBlocking(Dispatchers.IO) {
            pluginConfigDao.get(pluginId)?.toSnapshot()
        } ?: PluginConfigStoreSnapshot()
        return boundary.createSnapshot(
            coreValues = (boundary.coreDefaults + persisted.coreValues)
                .filterKeys { key -> key in boundary.coreFieldKeys },
            extensionValues = persisted.extensionValues
                .filterKeys { key -> key in boundary.extensionFieldKeys },
        )
    }

    override fun saveCoreConfig(
        pluginId: String,
        boundary: PluginConfigStorageBoundary,
        coreValues: Map<String, PluginStaticConfigValue>,
    ): PluginConfigStoreSnapshot {
        requireInstalledPlugin(pluginId)
        val current = resolveConfigSnapshot(pluginId = pluginId, boundary = boundary)
        val snapshot = boundary.createSnapshot(
            coreValues = coreValues,
            extensionValues = current.extensionValues,
        )
        persistSnapshot(pluginId = pluginId, snapshot = snapshot)
        return snapshot
    }

    override fun saveExtensionConfig(
        pluginId: String,
        boundary: PluginConfigStorageBoundary,
        extensionValues: Map<String, PluginStaticConfigValue>,
    ): PluginConfigStoreSnapshot {
        requireInstalledPlugin(pluginId)
        val current = resolveConfigSnapshot(pluginId = pluginId, boundary = boundary)
        val snapshot = boundary.createSnapshot(
            coreValues = current.coreValues,
            extensionValues = extensionValues,
        )
        persistSnapshot(pluginId = pluginId, snapshot = snapshot)
        return snapshot
    }

    override fun getInstalledStaticConfigSchema(pluginId: String): PluginStaticConfigSchema? {
        val schemaPath = resolveInstalledStaticConfigSchemaPath(pluginId) ?: return null
        return runCatching {
            PluginStaticConfigJson.decodeSchema(
                JSONObject(File(schemaPath).readText(Charsets.UTF_8)),
            )
        }.getOrNull()
    }

    override fun resolveInstalledStaticConfigSchemaPath(pluginId: String): String? {
        val record = requireInstalledPlugin(pluginId)
        return resolveInstalledSnapshotConfigFile(
            extractedDir = record.extractedDir,
            relativePath = record.packageContractSnapshot?.config?.staticSchema.orEmpty(),
        )?.absolutePath
    }

    override fun resolveInstalledSettingsSchemaPath(pluginId: String): String? {
        val record = requireInstalledPlugin(pluginId)
        return resolveInstalledSnapshotConfigFile(
            extractedDir = record.extractedDir,
            relativePath = record.packageContractSnapshot?.config?.settingsSchema.orEmpty(),
        )?.absolutePath
    }

    override fun deleteSnapshot(pluginId: String) {
        runBlocking(Dispatchers.IO) {
            pluginConfigDao.delete(pluginId)
        }
    }

    private fun requireInstalledPlugin(pluginId: String) = runBlocking(Dispatchers.IO) {
        pluginInstallDao.getPluginInstallAggregate(pluginId)?.toInstallRecord()
            ?: error("Plugin install record not found for pluginId=$pluginId")
    }

    private fun resolveInstalledSnapshotConfigFile(
        extractedDir: String,
        relativePath: String,
    ): File? {
        val rootDir = extractedDir.takeIf { it.isNotBlank() }?.let(::File) ?: return null
        return resolvePluginPackageSnapshotFile(
            rootDir = rootDir,
            relativePath = relativePath,
        )
    }

    private fun persistSnapshot(
        pluginId: String,
        snapshot: PluginConfigStoreSnapshot,
    ) {
        runBlocking(Dispatchers.IO) {
            pluginConfigDao.upsert(
                snapshot.toEntity(
                    pluginId = pluginId,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }
}
