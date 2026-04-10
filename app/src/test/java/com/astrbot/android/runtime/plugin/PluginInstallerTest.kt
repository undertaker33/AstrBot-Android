package com.astrbot.android.runtime.plugin

import com.astrbot.android.data.PluginRepository
import com.astrbot.android.data.db.PluginInstallAggregate
import com.astrbot.android.data.db.PluginInstallAggregateDao
import com.astrbot.android.data.db.PluginInstallRecordEntity
import com.astrbot.android.data.db.PluginInstallWriteModel
import com.astrbot.android.data.db.PluginManifestPermissionEntity
import com.astrbot.android.data.db.PluginManifestSnapshotEntity
import com.astrbot.android.data.db.PluginPackageContractSnapshotEntity
import com.astrbot.android.data.db.PluginPermissionSnapshotEntity
import com.astrbot.android.data.db.toInstallRecord
import com.astrbot.android.data.plugin.PluginStoragePaths
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginInstallIntent
import com.astrbot.android.model.plugin.PluginDownloadProgress
import com.astrbot.android.model.plugin.PluginDownloadProgressStage
import com.astrbot.android.model.plugin.PluginConfigEntryPointsSnapshot
import com.astrbot.android.model.plugin.PluginSource
import com.astrbot.android.model.plugin.PluginSourceType
import com.astrbot.android.model.plugin.PluginPackageContractSnapshot
import com.astrbot.android.model.plugin.PluginRuntimeDeclarationSnapshot
import com.astrbot.android.model.plugin.PluginUpdateAvailability
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
    fun installer_downloads_remote_package_and_marks_direct_link_source() = runBlocking {
        val tempDir = Files.createTempDirectory("plugin-installer-direct-link").toFile()
        try {
            resetPluginRepositoryForTest(dao = InMemoryPluginInstallAggregateDao(), initialized = true)
            val remotePackage = createPluginPackage(
                directory = tempDir,
                fileName = "remote-source.zip",
                manifest = validManifest(version = "1.2.0"),
            )
            val downloadRequests = mutableListOf<Pair<String, File>>()
            val installer = PluginInstaller(
                validator = PluginPackageValidator(hostVersion = "0.3.6", supportedProtocolVersion = 2),
                storagePaths = PluginStoragePaths.fromFilesDir(tempDir),
                installStore = PluginRepository,
                remotePackageDownloader = RemotePluginPackageDownloader { packageUrl, destinationFile, _ ->
                    downloadRequests += packageUrl to destinationFile
                    remotePackage.copyTo(destinationFile, overwrite = true)
                },
                clock = { 300L },
            )

            val installed = installer.install(
                PluginInstallIntent.directPackageUrl(" https://plugins.example.com/packages/demo-1.2.0.zip "),
            )

            assertEquals(listOf("https://plugins.example.com/packages/demo-1.2.0.zip"), downloadRequests.map { it.first })
            assertEquals(PluginSourceType.DIRECT_LINK, installed.source.sourceType)
            assertEquals(PluginSourceType.DIRECT_LINK, installed.manifestSnapshot.sourceType)
            assertEquals("https://plugins.example.com/packages/demo-1.2.0.zip", installed.source.location)
            assertEquals("https://plugins.example.com/packages/demo-1.2.0.zip", installed.installedPackageUrl)
            assertEquals(null, installed.catalogSourceId)
            assertEquals(300L, installed.lastUpdatedAt)
            assertTrue(File(installed.localPackagePath).exists())
        } finally {
            resetPluginRepositoryForTest()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun installer_downloads_catalog_package_and_writes_repository_origin() = runBlocking {
        val tempDir = Files.createTempDirectory("plugin-installer-repository").toFile()
        try {
            resetPluginRepositoryForTest(dao = InMemoryPluginInstallAggregateDao(), initialized = true)
            val remotePackage = createPluginPackage(
                directory = tempDir,
                fileName = "catalog-package.zip",
                manifest = validManifest(version = "1.3.0"),
            )
            val installer = PluginInstaller(
                validator = PluginPackageValidator(hostVersion = "0.3.6", supportedProtocolVersion = 2),
                storagePaths = PluginStoragePaths.fromFilesDir(tempDir),
                installStore = PluginRepository,
                remotePackageDownloader = RemotePluginPackageDownloader { _, destinationFile, _ ->
                    remotePackage.copyTo(destinationFile, overwrite = true)
                },
                clock = { 400L },
            )

            val installed = installer.install(
                PluginInstallIntent.catalogVersion(
                    pluginId = "com.example.demo",
                    version = "1.3.0",
                    packageUrl = "https://repo.example.com/packages/demo-1.3.0.zip",
                    catalogSourceId = "official",
                ),
            )

            assertEquals(PluginSourceType.REPOSITORY, installed.source.sourceType)
            assertEquals(PluginSourceType.REPOSITORY, installed.manifestSnapshot.sourceType)
            assertEquals("https://repo.example.com/packages/demo-1.3.0.zip", installed.source.location)
            assertEquals("https://repo.example.com/packages/demo-1.3.0.zip", installed.installedPackageUrl)
            assertEquals("official", installed.catalogSourceId)
            assertEquals(400L, installed.lastUpdatedAt)
        } finally {
            resetPluginRepositoryForTest()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun installer_reports_remote_download_progress_and_installing_stage() = runBlocking {
        val tempDir = Files.createTempDirectory("plugin-installer-download-progress").toFile()
        try {
            resetPluginRepositoryForTest(dao = InMemoryPluginInstallAggregateDao(), initialized = true)
            val remotePackage = createPluginPackage(
                directory = tempDir,
                fileName = "progress.zip",
                manifest = validManifest(version = "1.4.0"),
            )
            val progressEvents = mutableListOf<PluginDownloadProgress>()
            val installer = PluginInstaller(
                validator = PluginPackageValidator(hostVersion = "0.3.6", supportedProtocolVersion = 2),
                storagePaths = PluginStoragePaths.fromFilesDir(tempDir),
                installStore = PluginRepository,
                remotePackageDownloader = RemotePluginPackageDownloader { _, destinationFile, onProgress ->
                    onProgress(
                        PluginDownloadProgress.downloading(
                            bytesDownloaded = 1_024L,
                            totalBytes = 2_048L,
                            bytesPerSecond = 4_096L,
                        ),
                    )
                    remotePackage.copyTo(destinationFile, overwrite = true)
                },
            )

            installer.install(
                intent = PluginInstallIntent.catalogVersion(
                    pluginId = "com.example.demo",
                    version = "1.4.0",
                    packageUrl = "https://repo.example.com/packages/demo-1.4.0.zip",
                    catalogSourceId = "official",
                ),
                onProgress = progressEvents::add,
            )

            assertTrue(progressEvents.any { it.stage == PluginDownloadProgressStage.DOWNLOADING })
            val installing = progressEvents.last()
            assertEquals(PluginDownloadProgressStage.INSTALLING, installing.stage)
            assertEquals(remotePackage.length(), installing.bytesDownloaded)
            assertEquals(remotePackage.length(), installing.totalBytes)
        } finally {
            resetPluginRepositoryForTest()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun installer_rejects_same_plugin_id_when_version_is_not_an_upgrade() {
        val tempDir = Files.createTempDirectory("plugin-installer-duplicate").toFile()
        try {
            val repositoryDao = InMemoryPluginInstallAggregateDao()
            resetPluginRepositoryForTest(dao = repositoryDao, initialized = true)
            PluginRepository.upsert(existingRecord(tempDir, version = "1.0.0"))

            val installer = PluginInstaller(
                validator = PluginPackageValidator(hostVersion = "0.3.6", supportedProtocolVersion = 2),
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
                validator = PluginPackageValidator(hostVersion = "0.3.6", supportedProtocolVersion = 2),
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
                validator = PluginPackageValidator(hostVersion = "0.3.6", supportedProtocolVersion = 2),
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
    fun installer_extracts_static_config_schema_into_installed_plugin_directory() {
        val tempDir = Files.createTempDirectory("plugin-installer-static-schema").toFile()
        try {
            resetPluginRepositoryForTest(dao = InMemoryPluginInstallAggregateDao(), initialized = true)
            val installer = PluginInstaller(
                validator = PluginPackageValidator(hostVersion = "0.3.6", supportedProtocolVersion = 2),
                storagePaths = PluginStoragePaths.fromFilesDir(tempDir),
                installStore = PluginRepository,
                clock = { 250L },
            )
            val candidate = createPluginPackage(
                directory = tempDir,
                fileName = "candidate.zip",
                manifest = validManifest(version = "1.1.0"),
                extraEntries = mapOf(
                    "_conf_schema.json" to """
                        {
                          "api_key": {
                            "type": "string",
                            "description": "API key",
                            "default": "demo-key"
                          }
                        }
                    """.trimIndent(),
                ),
            )

            val installed = installer.installFromLocalPackage(candidate)

            assertTrue(File(installed.extractedDir, "_conf_schema.json").exists())
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
                validator = PluginPackageValidator(hostVersion = "0.3.6", supportedProtocolVersion = 2),
                storagePaths = PluginStoragePaths.fromFilesDir(tempDir),
                installStore = PluginRepository,
            )
            val candidate = createPluginPackage(
                directory = tempDir,
                fileName = "candidate.zip",
                manifest = validManifest(version = "1.0.0").apply {
                    put("minHostVersion", "9.0.0")
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
    fun installer_persists_package_contract_snapshot_into_install_record_and_store() {
        val tempDir = Files.createTempDirectory("plugin-installer-contract-snapshot").toFile()
        try {
            val repositoryDao = InMemoryPluginInstallAggregateDao()
            resetPluginRepositoryForTest(dao = repositoryDao, initialized = true)
            val installer = PluginInstaller(
                validator = PluginPackageValidator(hostVersion = "0.3.6", supportedProtocolVersion = 2),
                storagePaths = PluginStoragePaths.fromFilesDir(tempDir),
                installStore = PluginRepository,
                clock = { 275L },
            )
            val candidate = createPluginPackage(
                directory = tempDir,
                fileName = "candidate.zip",
                manifest = validManifest(version = "1.1.0"),
                staticSchemaPath = "schemas/static.schema.json",
                settingsSchemaPath = "schemas/settings.schema.json",
                extraEntries = mapOf(
                    "schemas/static.schema.json" to """{"type":"object"}""",
                    "schemas/settings.schema.json" to """{"type":"object"}""",
                ),
            )

            val installed = installer.installFromLocalPackage(candidate)
            val stored = runBlocking { repositoryDao.getPluginInstallAggregate(installed.pluginId) }?.toInstallRecord()

            val expectedSnapshot = PluginPackageContractSnapshot(
                protocolVersion = 2,
                runtime = PluginRuntimeDeclarationSnapshot(
                    kind = "js_quickjs",
                    bootstrap = "runtime/index.js",
                    apiVersion = 1,
                ),
                config = PluginConfigEntryPointsSnapshot(
                    staticSchema = "schemas/static.schema.json",
                    settingsSchema = "schemas/settings.schema.json",
                ),
            )
            assertEquals(expectedSnapshot, installed.packageContractSnapshot)
            assertEquals(expectedSnapshot, PluginRepository.findByPluginId(installed.pluginId)?.packageContractSnapshot)
            assertEquals(expectedSnapshot, stored?.packageContractSnapshot)
        } finally {
            resetPluginRepositoryForTest()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun installer_rejects_structurally_damaged_but_protocol_and_host_compatible_package() {
        val tempDir = Files.createTempDirectory("plugin-installer-structural-damage").toFile()
        try {
            resetPluginRepositoryForTest(dao = InMemoryPluginInstallAggregateDao(), initialized = true)
            val installer = PluginInstaller(
                validator = PluginPackageValidator(hostVersion = "0.3.6", supportedProtocolVersion = 2),
                storagePaths = PluginStoragePaths.fromFilesDir(tempDir),
                installStore = PluginRepository,
            )
            val candidate = createPluginPackage(
                directory = tempDir,
                fileName = "damaged.zip",
                manifest = validManifest(version = "1.0.0"),
                includeRuntimeBootstrap = false,
            )

            val failure = runCatching {
                installer.installFromLocalPackage(candidate)
            }.exceptionOrNull()

            assertTrue(failure is IllegalStateException)
            assertTrue(failure?.message?.contains("installable") == true)
            assertEquals(null, PluginRepository.findByPluginId("com.example.demo"))
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
                validator = PluginPackageValidator(hostVersion = "0.3.6", supportedProtocolVersion = 2),
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

    @Test
    fun installer_blocks_upgrade_when_target_version_is_incompatible() = runBlocking {
        val tempDir = Files.createTempDirectory("plugin-installer-blocked-upgrade").toFile()
        try {
            val repositoryDao = InMemoryPluginInstallAggregateDao()
            resetPluginRepositoryForTest(dao = repositoryDao, initialized = true)
            PluginRepository.upsert(
                existingRecord(
                    tempDir = tempDir,
                    version = "1.0.0",
                    localPackagePath = File(tempDir, "existing-1.0.0.zip").absolutePath,
                    extractedDir = File(tempDir, "existing").absolutePath,
                ),
            )
            val remotePackage = createPluginPackage(
                directory = tempDir,
                fileName = "upgrade.zip",
                manifest = validManifest(version = "1.2.0").apply {
                    put("minHostVersion", "9.0.0")
                },
            )
            val installer = PluginInstaller(
                validator = PluginPackageValidator(hostVersion = "0.3.6", supportedProtocolVersion = 2),
                storagePaths = PluginStoragePaths.fromFilesDir(tempDir),
                installStore = PluginRepository,
                remotePackageDownloader = RemotePluginPackageDownloader { _, destinationFile, _ ->
                    remotePackage.copyTo(destinationFile, overwrite = true)
                },
            )

            val failure = runCatching {
                installer.upgrade(
                    PluginUpdateAvailability(
                        pluginId = "com.example.demo",
                        installedVersion = "1.0.0",
                        latestVersion = "1.2.0",
                        updateAvailable = true,
                        canUpgrade = false,
                        incompatibilityReason = "Host version 0.3.6 is below required minimum 9.0.0.",
                        catalogSourceId = "official",
                        packageUrl = "https://repo.example.com/packages/demo-1.2.0.zip",
                    ),
                )
            }.exceptionOrNull()

            assertTrue(failure is IllegalStateException)
            assertTrue(failure?.message?.contains("below required minimum 9.0.0") == true)
            assertEquals("1.0.0", PluginRepository.findByPluginId("com.example.demo")?.installedVersion)
        } finally {
            resetPluginRepositoryForTest()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun installer_upgrade_refreshes_snapshots_and_catalog_tracking_fields() = runBlocking {
        val tempDir = Files.createTempDirectory("plugin-installer-upgrade-refresh").toFile()
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
                    localPackagePath = File(tempDir, "existing-1.0.0.zip").absolutePath,
                    extractedDir = File(tempDir, "existing").absolutePath,
                ),
            )
            val remotePackage = createPluginPackage(
                directory = tempDir,
                fileName = "upgrade.zip",
                manifest = validManifest(version = "1.2.0").apply {
                    put(
                        "permissions",
                        JSONArray().put(
                            JSONObject()
                                .put("permissionId", "net.admin")
                                .put("title", "Admin network access")
                                .put("description", "Allows privileged outgoing requests")
                                .put("riskLevel", "HIGH")
                                .put("required", true),
                        ),
                    )
                },
            )
            val installer = PluginInstaller(
                validator = PluginPackageValidator(hostVersion = "0.3.6", supportedProtocolVersion = 2),
                storagePaths = PluginStoragePaths.fromFilesDir(tempDir),
                installStore = PluginRepository,
                remotePackageDownloader = RemotePluginPackageDownloader { _, destinationFile, _ ->
                    remotePackage.copyTo(destinationFile, overwrite = true)
                },
                clock = { 500L },
            )

            val upgraded = installer.upgrade(
                PluginUpdateAvailability(
                    pluginId = "com.example.demo",
                    installedVersion = "1.0.0",
                    latestVersion = "1.2.0",
                    updateAvailable = true,
                    canUpgrade = true,
                    catalogSourceId = "official",
                    packageUrl = "https://repo.example.com/packages/demo-1.2.0.zip",
                ),
            )

            assertEquals("1.2.0", upgraded.installedVersion)
            assertEquals("official", upgraded.catalogSourceId)
            assertEquals("https://repo.example.com/packages/demo-1.2.0.zip", upgraded.installedPackageUrl)
            assertEquals(500L, upgraded.lastCatalogCheckAtEpochMillis)
            assertEquals("net.admin", upgraded.permissionSnapshot.single().permissionId)
            assertEquals(PluginSourceType.REPOSITORY, upgraded.source.sourceType)
            assertEquals(100L, upgraded.installedAt)
            assertEquals(500L, upgraded.lastUpdatedAt)
        } finally {
            resetPluginRepositoryForTest()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun installer_upgrade_from_direct_link_switches_plugin_to_repository_baseline() = runBlocking {
        val tempDir = Files.createTempDirectory("plugin-installer-direct-link-upgrade").toFile()
        try {
            val repositoryDao = InMemoryPluginInstallAggregateDao()
            resetPluginRepositoryForTest(dao = repositoryDao, initialized = true)
            PluginRepository.upsert(
                existingRecord(
                    tempDir = tempDir,
                    version = "1.0.0",
                    sourceType = PluginSourceType.DIRECT_LINK,
                    catalogSourceId = null,
                    installedPackageUrl = "https://plugins.example.com/demo-1.0.0.zip",
                    localPackagePath = File(tempDir, "direct-link-1.0.0.zip").absolutePath,
                    extractedDir = File(tempDir, "existing-direct-link").absolutePath,
                ),
            )
            val remotePackage = createPluginPackage(
                directory = tempDir,
                fileName = "repository-upgrade.zip",
                manifest = validManifest(version = "1.2.0"),
            )
            val installer = PluginInstaller(
                validator = PluginPackageValidator(hostVersion = "0.3.6", supportedProtocolVersion = 2),
                storagePaths = PluginStoragePaths.fromFilesDir(tempDir),
                installStore = PluginRepository,
                remotePackageDownloader = RemotePluginPackageDownloader { _, destinationFile, _ ->
                    remotePackage.copyTo(destinationFile, overwrite = true)
                },
                clock = { 650L },
            )

            val upgraded = installer.upgrade(
                PluginUpdateAvailability(
                    pluginId = "com.example.demo",
                    installedVersion = "1.0.0",
                    latestVersion = "1.2.0",
                    updateAvailable = true,
                    canUpgrade = true,
                    catalogSourceId = "official",
                    packageUrl = "https://repo.example.com/packages/demo-1.2.0.zip",
                ),
            )

            assertEquals(PluginSourceType.REPOSITORY, upgraded.source.sourceType)
            assertEquals(PluginSourceType.REPOSITORY, upgraded.manifestSnapshot.sourceType)
            assertEquals("official", upgraded.catalogSourceId)
            assertEquals("https://repo.example.com/packages/demo-1.2.0.zip", upgraded.installedPackageUrl)
            assertEquals(650L, upgraded.lastCatalogCheckAtEpochMillis)
        } finally {
            resetPluginRepositoryForTest()
            tempDir.deleteRecursively()
        }
    }

    private fun existingRecord(
        tempDir: File,
        version: String,
        sourceType: PluginSourceType = PluginSourceType.LOCAL_FILE,
        catalogSourceId: String? = null,
        installedPackageUrl: String = "",
        enabled: Boolean = false,
        installedAt: Long = 0L,
        lastUpdatedAt: Long = 0L,
        localPackagePath: String = "",
        extractedDir: String = "",
    ): PluginInstallRecord {
        return PluginInstallRecord.restoreFromPersistedState(
            manifestSnapshot = PluginPackageValidator(
                hostVersion = "0.3.6",
                supportedProtocolVersion = 2,
            ).validate(
                createPluginPackage(
                    directory = tempDir,
                    fileName = "existing-$version.zip",
                    manifest = validManifest(version = version).apply {
                        put("sourceType", sourceType.name)
                    },
                ),
            ).manifest,
            source = PluginSource(
                sourceType = sourceType,
                location = installedPackageUrl.ifBlank { File(tempDir, "existing-$version.zip").absolutePath },
            ),
            catalogSourceId = catalogSourceId,
            installedPackageUrl = installedPackageUrl,
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
            .put("protocolVersion", 2)
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
        includeAndroidPlugin: Boolean = true,
        includeRuntimeBootstrap: Boolean = true,
        androidPluginProtocolVersion: Int = 2,
        runtimeBootstrap: String = "runtime/index.js",
        staticSchemaPath: String = "",
        settingsSchemaPath: String = "",
        extraEntries: Map<String, String> = mapOf("assets/readme.txt" to "hello"),
    ): File {
        val packageFile = File(directory, fileName)
        ZipOutputStream(packageFile.outputStream()).use { output ->
            output.putNextEntry(ZipEntry("manifest.json"))
            output.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
            output.closeEntry()
            if (includeAndroidPlugin) {
                output.putNextEntry(ZipEntry("android-plugin.json"))
                output.write(
                    validAndroidPluginJson(
                        protocolVersion = androidPluginProtocolVersion,
                        runtimeBootstrap = runtimeBootstrap,
                        staticSchemaPath = staticSchemaPath,
                        settingsSchemaPath = settingsSchemaPath,
                    ).toString(2).toByteArray(Charsets.UTF_8),
                )
                output.closeEntry()
            }
            if (includeRuntimeBootstrap) {
                output.putNextEntry(ZipEntry(runtimeBootstrap))
                output.write("console.log('hello')".toByteArray(Charsets.UTF_8))
                output.closeEntry()
            }
            extraEntries.forEach { (path, content) ->
                output.putNextEntry(ZipEntry(path))
                output.write(content.toByteArray(Charsets.UTF_8))
                output.closeEntry()
            }
        }
        return packageFile
    }

    private fun validAndroidPluginJson(
        protocolVersion: Int,
        runtimeBootstrap: String,
        staticSchemaPath: String = "",
        settingsSchemaPath: String = "",
    ): JSONObject {
        val config = JSONObject()
        if (staticSchemaPath.isNotBlank()) {
            config.put("staticSchema", staticSchemaPath)
        }
        if (settingsSchemaPath.isNotBlank()) {
            config.put("settingsSchema", settingsSchemaPath)
        }
        return JSONObject()
            .put("protocolVersion", protocolVersion)
            .put(
                "runtime",
                JSONObject()
                    .put("kind", "js_quickjs")
                    .put("bootstrap", runtimeBootstrap)
                    .put("apiVersion", 1),
            )
            .put("config", config)
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
            packageContractSnapshots = listOfNotNull(writeModel.packageContractSnapshot),
            manifestPermissions = writeModel.manifestPermissions,
            permissionSnapshots = writeModel.permissionSnapshots,
        )
        publish()
    }

    override suspend fun upsertRecords(entities: List<PluginInstallRecordEntity>) = Unit

    override suspend fun upsertManifestSnapshots(entities: List<PluginManifestSnapshotEntity>) = Unit

    override suspend fun upsertPackageContractSnapshots(entities: List<PluginPackageContractSnapshotEntity>) = Unit

    override suspend fun upsertManifestPermissions(entities: List<PluginManifestPermissionEntity>) = Unit

    override suspend fun upsertPermissionSnapshots(entities: List<PluginPermissionSnapshotEntity>) = Unit

    override suspend fun deleteManifestPermissions(pluginId: String) = Unit

    override suspend fun deletePackageContractSnapshots(pluginId: String) = Unit

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
