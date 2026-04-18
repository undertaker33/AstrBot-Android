package com.astrbot.android.feature.plugin.runtime.samples

import com.astrbot.android.data.PluginRepository
import com.astrbot.android.feature.plugin.data.PluginStoragePaths
import com.astrbot.android.model.plugin.PluginInstallIntent
import com.astrbot.android.model.plugin.PluginRuntimeDeclarationSnapshot
import com.astrbot.android.model.plugin.PluginSourceType
import com.astrbot.android.model.plugin.PluginUpdateAvailability
import com.astrbot.android.feature.plugin.runtime.PluginInstaller
import com.astrbot.android.feature.plugin.runtime.PluginPackageValidator
import com.astrbot.android.feature.plugin.runtime.RemotePluginPackageDownloader
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemeManagerSampleInstallAndUpgradeTest {

    @Test
    fun sample_package_can_install_from_catalog_and_persists_repository_origin_fields() = runBlocking {
        val tempDir = Files.createTempDirectory("meme-manager-sample-install").toFile()
        try {
            resetPluginRepositoryForSampleTest(initialized = true)

            val packageZip = SampleAssetPaths.packageZip("1.0.0")
            assertTrue("Missing sample zip: ${packageZip.absolutePath}", packageZip.exists())
            val expectedUrl = "https://samples.astrbot.local/catalog/packages/meme-manager-1.0.0.zip"
            val installer = PluginInstaller(
                validator = PluginPackageValidator(hostVersion = "0.3.6", supportedProtocolVersion = 2),
                storagePaths = PluginStoragePaths.fromFilesDir(tempDir),
                installStore = PluginRepository,
                remotePackageDownloader = RemotePluginPackageDownloader { _, destinationFile, _ ->
                    packageZip.copyTo(destinationFile, overwrite = true)
                },
                clock = { 1234L },
            )

            val installed = installer.install(
                PluginInstallIntent.catalogVersion(
                    pluginId = SAMPLE_PLUGIN_ID,
                    version = "1.0.0",
                    packageUrl = expectedUrl,
                    catalogSourceId = "sample",
                ),
            )

            assertEquals(PluginSourceType.REPOSITORY, installed.source.sourceType)
            assertEquals(expectedUrl, installed.source.location)
            assertEquals(expectedUrl, installed.installedPackageUrl)
            assertEquals("sample", installed.catalogSourceId)
            assertEquals(2, installed.manifestSnapshot.protocolVersion)
            assertEquals(2, installed.packageContractSnapshot?.protocolVersion)
            assertEquals("_conf_schema.json", installed.packageContractSnapshot?.config?.staticSchema)
            assertEquals(
                PluginRuntimeDeclarationSnapshot(
                    kind = "js_quickjs",
                    bootstrap = "runtime/index.js",
                    apiVersion = 1,
                ),
                installed.packageContractSnapshot?.runtime,
            )
            val contractJson = JSONObject(File(installed.extractedDir, "android-plugin.json").readText(Charsets.UTF_8))
            assertTrue(File(installed.localPackagePath).exists())
            assertTrue(File(installed.extractedDir, "android-plugin.json").exists())
            assertTrue(File(installed.extractedDir, "runtime/index.js").exists())
            assertTrue(File(installed.extractedDir, "_conf_schema.json").exists())
            assertFalse(contractJson.has("supportedTriggers"))
            assertFalse(contractJson.has("triggers"))
            assertFalse(File(installed.extractedDir, "android-execution.json").exists())
            assertTrue(File(installed.extractedDir, "assets/readme.txt").exists())
            assertTrue(File(installed.extractedDir, "resources/memes/index.json").exists())
            assertTrue(
                File(installed.extractedDir, "resources/memes/angry/9D03FF21BB828C2AF9CCC7FCCB1E25B3.jpg").exists(),
            )
        } finally {
            resetPluginRepositoryForSampleTest(initialized = false)
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun sample_plugin_upgrade_reinstalls_v2_contract_and_packaged_assets_from_catalog_package() = runBlocking {
        val tempDir = Files.createTempDirectory("meme-manager-sample-upgrade-install").toFile()
        try {
            resetPluginRepositoryForSampleTest(initialized = true)

            val storagePaths = PluginStoragePaths.fromFilesDir(tempDir)
            val downloader = RemotePluginPackageDownloader { packageUrl, destinationFile, _ ->
                val version = if (packageUrl.contains("1.1.0")) "1.1.0" else "1.0.0"
                val packageZip = SampleAssetPaths.packageZip(version)
                assertTrue("Missing sample zip: ${packageZip.absolutePath}", packageZip.exists())
                packageZip.copyTo(destinationFile, overwrite = true)
            }
            val installUrl = "https://samples.astrbot.local/catalog/packages/meme-manager-1.0.0.zip"
            val upgradeUrl = "https://samples.astrbot.local/catalog/packages/meme-manager-1.1.0.zip"

            val installed = PluginInstaller(
                validator = PluginPackageValidator(hostVersion = "0.3.6", supportedProtocolVersion = 2),
                storagePaths = storagePaths,
                installStore = PluginRepository,
                remotePackageDownloader = downloader,
                clock = { 1000L },
            ).install(
                PluginInstallIntent.catalogVersion(
                    pluginId = SAMPLE_PLUGIN_ID,
                    version = "1.0.0",
                    packageUrl = installUrl,
                    catalogSourceId = "sample",
                ),
            )

            val upgraded = PluginInstaller(
                validator = PluginPackageValidator(hostVersion = "0.3.6", supportedProtocolVersion = 2),
                storagePaths = storagePaths,
                installStore = PluginRepository,
                remotePackageDownloader = downloader,
                clock = { 2000L },
            ).upgrade(
                PluginUpdateAvailability(
                    pluginId = SAMPLE_PLUGIN_ID,
                    installedVersion = installed.installedVersion,
                    latestVersion = "1.1.0",
                    updateAvailable = true,
                    canUpgrade = true,
                    catalogSourceId = "sample",
                    packageUrl = upgradeUrl,
                ),
            )

            val persisted = PluginRepository.findByPluginId(SAMPLE_PLUGIN_ID)
            val contractJson = JSONObject(File(upgraded.extractedDir, "android-plugin.json").readText(Charsets.UTF_8))

            assertEquals("1.1.0", upgraded.installedVersion)
            assertEquals("sample", upgraded.catalogSourceId)
            assertEquals(upgradeUrl, upgraded.installedPackageUrl)
            assertEquals(1000L, upgraded.installedAt)
            assertEquals(2000L, upgraded.lastUpdatedAt)
            assertEquals(2000L, upgraded.lastCatalogCheckAtEpochMillis)
            assertEquals(2, upgraded.packageContractSnapshot?.protocolVersion)
            assertEquals("_conf_schema.json", upgraded.packageContractSnapshot?.config?.staticSchema)
            assertEquals(
                PluginRuntimeDeclarationSnapshot(
                    kind = "js_quickjs",
                    bootstrap = "runtime/index.js",
                    apiVersion = 1,
                ),
                upgraded.packageContractSnapshot?.runtime,
            )
            assertEquals(upgraded.packageContractSnapshot, persisted?.packageContractSnapshot)
            assertTrue(File(upgraded.localPackagePath).exists())
            assertTrue(File(upgraded.extractedDir, "assets/readme.txt").exists())
            assertTrue(File(upgraded.extractedDir, "resources/admin/seed.txt").exists())
            assertTrue(File(upgraded.extractedDir, "resources/memes/index.json").exists())
            assertTrue(File(upgraded.extractedDir, "runtime/index.js").exists())
            assertTrue(File(upgraded.extractedDir, "_conf_schema.json").exists())
            assertFalse(contractJson.has("supportedTriggers"))
            assertFalse(contractJson.has("triggers"))
            assertFalse(File(upgraded.extractedDir, "android-execution.json").exists())
        } finally {
            resetPluginRepositoryForSampleTest(initialized = false)
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun sample_plugin_participates_in_update_check_with_1_1_0_available_over_1_0_0() = runBlocking {
        val tempDir = Files.createTempDirectory("meme-manager-sample-update").toFile()
        try {
            resetPluginRepositoryForSampleTest(initialized = true)

            val packageZip = SampleAssetPaths.packageZip("1.0.0")
            assertTrue("Missing sample zip: ${packageZip.absolutePath}", packageZip.exists())
            val installer = PluginInstaller(
                validator = PluginPackageValidator(hostVersion = "0.3.6", supportedProtocolVersion = 2),
                storagePaths = PluginStoragePaths.fromFilesDir(tempDir),
                installStore = PluginRepository,
                remotePackageDownloader = RemotePluginPackageDownloader { _, destinationFile, _ ->
                    packageZip.copyTo(destinationFile, overwrite = true)
                },
                clock = { 1000L },
            )

            installer.install(
                PluginInstallIntent.catalogVersion(
                    pluginId = SAMPLE_PLUGIN_ID,
                    version = "1.0.0",
                    packageUrl = "https://samples.astrbot.local/catalog/packages/meme-manager-1.0.0.zip",
                    catalogSourceId = "sample",
                ),
            )

            // Catalog fixture includes both 1.0.0 and 1.1.0, and the repository logic should prefer latest.
            val fixtureJson = SampleAssetPaths.catalogFixture.readText(Charsets.UTF_8)
            PluginRepository.replaceRepositoryCatalog(
                com.astrbot.android.feature.plugin.data.catalog.PluginCatalogJson.decodeRepositorySource(fixtureJson).copy(
                    sourceId = "sample",
                    catalogUrl = "https://samples.astrbot.local/catalog/meme-manager.json",
                ),
            )

            val availability = PluginRepository.getUpdateAvailability(
                pluginId = SAMPLE_PLUGIN_ID,
                hostVersion = "0.3.6",
                supportedProtocolVersion = 2,
            )

            assertNotNull(availability)
            assertEquals(true, availability!!.updateAvailable)
            assertEquals("1.1.0", availability.latestVersion)
            assertEquals("sample", availability.catalogSourceId)
            assertTrue(availability.packageUrl.contains("meme-manager-1.1.0.zip"))
            assertEquals(true, availability.canUpgrade)
            assertTrue(availability.compatibilityState.isCompatible())
        } finally {
            resetPluginRepositoryForSampleTest(initialized = false)
            tempDir.deleteRecursively()
        }
    }
}

private const val SAMPLE_PLUGIN_ID = "com.astrbot.samples.meme_manager"
