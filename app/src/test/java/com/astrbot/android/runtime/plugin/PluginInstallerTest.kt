package com.astrbot.android.runtime.plugin

import com.astrbot.android.data.PluginRepository
import com.astrbot.android.data.db.PluginInstallAggregate
import com.astrbot.android.data.db.PluginInstallAggregateDao
import com.astrbot.android.data.db.PluginInstallRecordEntity
import com.astrbot.android.data.db.PluginInstallWriteModel
import com.astrbot.android.data.db.PluginManifestPermissionEntity
import com.astrbot.android.data.db.PluginManifestSnapshotEntity
import com.astrbot.android.data.db.PluginPermissionSnapshotEntity
import com.astrbot.android.data.db.toInstallRecord
import com.astrbot.android.data.plugin.PluginStoragePaths
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginSource
import com.astrbot.android.model.plugin.PluginSourceType
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginInstallerTest {
    @Test
    fun installer_rejects_same_plugin_id_when_version_is_not_an_upgrade() {
        val tempDir = Files.createTempDirectory("plugin-installer-duplicate").toFile()
        try {
            val repositoryDao = InMemoryPluginInstallAggregateDao()
            resetPluginRepositoryForTest(dao = repositoryDao, initialized = true)
            PluginRepository.upsert(existingRecord(tempDir, version = "1.0.0"))

            val installer = PluginInstaller(
                validator = PluginPackageValidator(hostVersion = "0.3.6", supportedProtocolVersion = 1),
                storagePaths = PluginStoragePaths.fromFilesDir(tempDir),
                installStore = PluginRepository,
            )
            val candidate = createPluginPackage(
                directory = tempDir,
                fileName = "candidate.zip",
                manifest = validManifest(version = "1.0.0"),
            )

            val failure = runCatching {
                installer.installFromLocalPackage(candidate)
            }.exceptionOrNull()

            assertTrue(failure is IllegalStateException)
            assertTrue(failure?.message?.contains("already installed") == true)
        } finally {
            resetPluginRepositoryForTest()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun installer_rejects_downgrade_when_same_plugin_is_already_installed() {
        val tempDir = Files.createTempDirectory("plugin-installer-downgrade").toFile()
        try {
            val repositoryDao = InMemoryPluginInstallAggregateDao()
            resetPluginRepositoryForTest(dao = repositoryDao, initialized = true)
            PluginRepository.upsert(existingRecord(tempDir, version = "2.0.0"))

            val installer = PluginInstaller(
                validator = PluginPackageValidator(hostVersion = "0.3.6", supportedProtocolVersion = 1),
                storagePaths = PluginStoragePaths.fromFilesDir(tempDir),
                installStore = PluginRepository,
            )
            val candidate = createPluginPackage(
                directory = tempDir,
                fileName = "candidate.zip",
                manifest = validManifest(version = "1.5.0"),
            )

            val failure = runCatching {
                installer.installFromLocalPackage(candidate)
            }.exceptionOrNull()

            assertTrue(failure is IllegalStateException)
            assertTrue(failure?.message?.contains("not an upgrade") == true)
        } finally {
            resetPluginRepositoryForTest()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun installer_copies_package_extracts_resources_and_persists_upgrade_record() {
        val tempDir = Files.createTempDirectory("plugin-installer-upgrade").toFile()
        try {
            val repositoryDao = InMemoryPluginInstallAggregateDao()
            resetPluginRepositoryForTest(dao = repositoryDao, initialized = true)
            PluginRepository.upsert(
                existingRecord(
                    tempDir = tempDir,
                    version = "1.0.0",
                    enabled = true,
                    installedAt = 100L,
                    lastUpdatedAt = 100L,
                    localPackagePath = "/old/package.zip",
                    extractedDir = "/old/extracted",
                ),
            )
            val installer = PluginInstaller(
                validator = PluginPackageValidator(hostVersion = "0.3.6", supportedProtocolVersion = 1),
                storagePaths = PluginStoragePaths.fromFilesDir(tempDir),
                installStore = PluginRepository,
                clock = { 200L },
            )
            val candidate = createPluginPackage(
                directory = tempDir,
                fileName = "candidate.zip",
                manifest = validManifest(version = "1.1.0"),
                extraEntries = mapOf(
                    "assets/readme.txt" to "hello",
                    "resources/config.json" to """{"ok":true}""",
                ),
            )

            val installed = installer.installFromLocalPackage(candidate)

            assertEquals("1.1.0", installed.installedVersion)
            assertTrue(installed.enabled)
            assertEquals(100L, installed.installedAt)
            assertEquals(200L, installed.lastUpdatedAt)
            assertTrue(File(installed.localPackagePath).exists())
            assertTrue(File(installed.extractedDir, "assets/readme.txt").exists())
            assertTrue(File(installed.extractedDir, "resources/config.json").exists())
            assertEquals(installed, PluginRepository.findByPluginId("com.example.demo"))
            assertEquals(
                installed,
                runBlocking { repositoryDao.getPluginInstallAggregate("com.example.demo") }?.toInstallRecord(),
            )
        } finally {
            resetPluginRepositoryForTest()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun installer_rejects_incompatible_package() {
        val tempDir = Files.createTempDirectory("plugin-installer-incompatible").toFile()
        try {
            resetPluginRepositoryForTest(dao = InMemoryPluginInstallAggregateDao(), initialized = true)
            val installer = PluginInstaller(
                validator = PluginPackageValidator(hostVersion = "0.3.6", supportedProtocolVersion = 1),
                storagePaths = PluginStoragePaths.fromFilesDir(tempDir),
                installStore = PluginRepository,
            )
            val candidate = createPluginPackage(
                directory = tempDir,
                fileName = "candidate.zip",
                manifest = validManifest(version = "1.0.0").apply {
                    put("protocolVersion", 2)
                },
            )

            val failure = runCatching {
                installer.installFromLocalPackage(candidate)
            }.exceptionOrNull()

            assertTrue(failure is IllegalStateException)
            assertTrue(failure?.message?.contains("incompatible") == true)
            assertFalse(File(tempDir, "plugins").exists())
        } finally {
            resetPluginRepositoryForTest()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun installer_rejects_unsafe_archive_entries_before_persisting() {
        val tempDir = Files.createTempDirectory("plugin-installer-unsafe").toFile()
        try {
            resetPluginRepositoryForTest(dao = InMemoryPluginInstallAggregateDao(), initialized = true)
            val installer = PluginInstaller(
                validator = PluginPackageValidator(hostVersion = "0.3.6", supportedProtocolVersion = 1),
                storagePaths = PluginStoragePaths.fromFilesDir(tempDir),
                installStore = PluginRepository,
            )
            val candidate = createPluginPackage(
                directory = tempDir,
                fileName = "candidate.zip",
                manifest = validManifest(version = "1.0.0"),
                extraEntries = mapOf("../escape.txt" to "blocked"),
            )

            val failure = runCatching {
                installer.installFromLocalPackage(candidate)
            }.exceptionOrNull()

            assertTrue(failure is IllegalStateException)
            assertTrue(failure?.message?.contains("unsafe") == true)
            assertEquals(null, PluginRepository.findByPluginId("com.example.demo"))
        } finally {
            resetPluginRepositoryForTest()
            tempDir.deleteRecursively()
        }
    }

    private fun existingRecord(
        tempDir: File,
        version: String,
        enabled: Boolean = false,
        installedAt: Long = 0L,
        lastUpdatedAt: Long = 0L,
        localPackagePath: String = "",
        extractedDir: String = "",
    ): PluginInstallRecord {
        return PluginInstallRecord.restoreFromPersistedState(
            manifestSnapshot = PluginPackageValidator(
                hostVersion = "0.3.6",
                supportedProtocolVersion = 1,
            ).validate(
                createPluginPackage(
                    directory = tempDir,
                    fileName = "existing-$version.zip",
                    manifest = validManifest(version = version),
                ),
            ).manifest,
            source = PluginSource(
                sourceType = PluginSourceType.LOCAL_FILE,
                location = File(tempDir, "existing-$version.zip").absolutePath,
            ),
            enabled = enabled,
            installedAt = installedAt,
            lastUpdatedAt = lastUpdatedAt,
            localPackagePath = localPackagePath,
            extractedDir = extractedDir,
        )
    }

    private fun validManifest(version: String): JSONObject {
        return JSONObject()
            .put("pluginId", "com.example.demo")
            .put("version", version)
            .put("protocolVersion", 1)
            .put("author", "AstrBot")
            .put("title", "Demo Plugin")
            .put("description", "Example plugin")
            .put(
                "permissions",
                JSONArray().put(
                    JSONObject()
                        .put("permissionId", "net.access")
                        .put("title", "Network access")
                        .put("description", "Allows outgoing requests")
                        .put("riskLevel", "MEDIUM")
                        .put("required", true),
                ),
            )
            .put("minHostVersion", "0.3.0")
            .put("maxHostVersion", "")
            .put("sourceType", "LOCAL_FILE")
            .put("entrySummary", "Example entry")
            .put("riskLevel", "LOW")
    }

    private fun createPluginPackage(
        directory: File,
        fileName: String,
        manifest: JSONObject,
        extraEntries: Map<String, String> = mapOf("assets/readme.txt" to "hello"),
    ): File {
        val packageFile = File(directory, fileName)
        ZipOutputStream(packageFile.outputStream()).use { output ->
            output.putNextEntry(ZipEntry("manifest.json"))
            output.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
            output.closeEntry()
            extraEntries.forEach { (path, content) ->
                output.putNextEntry(ZipEntry(path))
                output.write(content.toByteArray(Charsets.UTF_8))
                output.closeEntry()
            }
        }
        return packageFile
    }
}

private class InMemoryPluginInstallAggregateDao : PluginInstallAggregateDao() {
    private val aggregates = linkedMapOf<String, PluginInstallAggregate>()
    private val state = MutableStateFlow<List<PluginInstallAggregate>>(emptyList())

    override fun observePluginInstallAggregates(): Flow<List<PluginInstallAggregate>> = state

    override suspend fun listPluginInstallAggregates(): List<PluginInstallAggregate> = state.value

    override fun observePluginInstallAggregate(pluginId: String): Flow<PluginInstallAggregate?> {
        return state.map { aggregates -> aggregates.firstOrNull { aggregate -> aggregate.record.pluginId == pluginId } }
    }

    override suspend fun getPluginInstallAggregate(pluginId: String): PluginInstallAggregate? = aggregates[pluginId]

    override suspend fun upsertRecord(writeModel: PluginInstallWriteModel) {
        aggregates[writeModel.record.pluginId] = PluginInstallAggregate(
            record = writeModel.record,
            manifestSnapshots = listOf(writeModel.manifestSnapshot),
            manifestPermissions = writeModel.manifestPermissions,
            permissionSnapshots = writeModel.permissionSnapshots,
        )
        publish()
    }

    override suspend fun upsertRecords(entities: List<PluginInstallRecordEntity>) = Unit

    override suspend fun upsertManifestSnapshots(entities: List<PluginManifestSnapshotEntity>) = Unit

    override suspend fun upsertManifestPermissions(entities: List<PluginManifestPermissionEntity>) = Unit

    override suspend fun upsertPermissionSnapshots(entities: List<PluginPermissionSnapshotEntity>) = Unit

    override suspend fun deleteManifestPermissions(pluginId: String) = Unit

    override suspend fun deletePermissionSnapshots(pluginId: String) = Unit

    override suspend fun delete(pluginId: String) {
        aggregates.remove(pluginId)
        publish()
    }

    override suspend fun count(): Int = aggregates.size

    private fun publish() {
        state.value = aggregates.values.sortedWith(
            compareByDescending<PluginInstallAggregate> { aggregate -> aggregate.record.lastUpdatedAt }
                .thenBy { aggregate -> aggregate.record.pluginId },
        )
    }
}

private fun resetPluginRepositoryForTest(
    dao: PluginInstallAggregateDao = InMemoryPluginInstallAggregateDao(),
    initialized: Boolean = false,
) {
    val repositoryClass = PluginRepository::class.java
    repositoryClass.getDeclaredField("pluginDao").apply {
        isAccessible = true
        set(PluginRepository, dao)
    }

    @Suppress("UNCHECKED_CAST")
    val recordsField = repositoryClass.getDeclaredField("_records").apply {
        isAccessible = true
    }.get(PluginRepository) as MutableStateFlow<List<PluginInstallRecord>>
    recordsField.value = emptyList()

    val initializedField = repositoryClass.getDeclaredField("initialized").apply {
        isAccessible = true
    }.get(PluginRepository) as AtomicBoolean
    initializedField.set(initialized)
}
