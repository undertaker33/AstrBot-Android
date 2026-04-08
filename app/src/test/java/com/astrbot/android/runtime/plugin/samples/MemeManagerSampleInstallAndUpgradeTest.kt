package com.astrbot.android.runtime.plugin.samples

import com.astrbot.android.data.PluginRepository
import com.astrbot.android.data.plugin.PluginStoragePaths
import com.astrbot.android.model.plugin.PluginInstallIntent
import com.astrbot.android.model.plugin.PluginSourceType
import com.astrbot.android.runtime.plugin.PluginInstaller
import com.astrbot.android.runtime.plugin.PluginPackageValidator
import com.astrbot.android.runtime.plugin.RemotePluginPackageDownloader
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
                validator = PluginPackageValidator(hostVersion = "0.3.6", supportedProtocolVersion = 1),
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
            assertTrue(File(installed.localPackagePath).exists())
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
    fun sample_plugin_participates_in_update_check_with_1_1_0_available_over_1_0_0() = runBlocking {
        val tempDir = Files.createTempDirectory("meme-manager-sample-update").toFile()
        try {
            resetPluginRepositoryForSampleTest(initialized = true)

            val packageZip = SampleAssetPaths.packageZip("1.0.0")
            assertTrue("Missing sample zip: ${packageZip.absolutePath}", packageZip.exists())
            val installer = PluginInstaller(
                validator = PluginPackageValidator(hostVersion = "0.3.6", supportedProtocolVersion = 1),
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
                com.astrbot.android.data.plugin.catalog.PluginCatalogJson.decodeRepositorySource(fixtureJson).copy(
                    sourceId = "sample",
                    catalogUrl = "https://samples.astrbot.local/catalog/meme-manager.json",
                ),
            )

            val availability = PluginRepository.getUpdateAvailability(
                pluginId = SAMPLE_PLUGIN_ID,
                hostVersion = "0.3.6",
                supportedProtocolVersion = 1,
            )

            assertNotNull(availability)
            assertEquals(true, availability!!.updateAvailable)
            assertEquals("1.1.0", availability.latestVersion)
            assertEquals("sample", availability.catalogSourceId)
            assertTrue(availability.packageUrl.contains("meme-manager-1.1.0.zip"))
        } finally {
            resetPluginRepositoryForSampleTest(initialized = false)
            tempDir.deleteRecursively()
        }
    }
}

private const val SAMPLE_PLUGIN_ID = "com.astrbot.samples.meme_manager"
