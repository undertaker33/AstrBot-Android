package com.astrbot.android.feature.plugin.runtime.samples

import com.astrbot.android.data.PluginRepository
import com.astrbot.android.feature.plugin.data.PluginStoragePaths
import com.astrbot.android.model.plugin.PluginInstallIntent
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginRuntimeDeclarationSnapshot
import com.astrbot.android.feature.plugin.runtime.PluginInstaller
import com.astrbot.android.feature.plugin.runtime.PluginPackageValidator
import com.astrbot.android.feature.plugin.runtime.RemotePluginPackageDownloader
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemeManagerSampleRuntimeAndAdminPathTest {

    @Test
    fun sample_install_snapshot_keeps_v2_package_contract_without_legacy_execution_manifest() = runTest {
        val tempDir = Files.createTempDirectory("meme-manager-sample-runtime-contract").toFile()
        try {
            resetPluginRepositoryForSampleTest(initialized = true)
            val installed = installSample(tempDir = tempDir, version = "1.0.0")
            val persisted = PluginRepository.findByPluginId(SAMPLE_PLUGIN_ID)
            val contractJson = JSONObject(File(installed.extractedDir, "android-plugin.json").readText(Charsets.UTF_8))

            assertEquals(2, installed.manifestSnapshot.protocolVersion)
            assertNotNull(installed.packageContractSnapshot)
            assertEquals(2, installed.packageContractSnapshot!!.protocolVersion)
            assertEquals(
                PluginRuntimeDeclarationSnapshot(
                    kind = "js_quickjs",
                    bootstrap = "runtime/index.js",
                    apiVersion = 1,
                ),
                installed.packageContractSnapshot!!.runtime,
            )
            assertEquals("_conf_schema.json", installed.packageContractSnapshot!!.config.staticSchema)
            assertEquals("", installed.packageContractSnapshot!!.config.settingsSchema)
            assertEquals(installed.packageContractSnapshot, persisted?.packageContractSnapshot)
            assertEquals("js_quickjs", contractJson.getJSONObject("runtime").getString("kind"))
            assertEquals("runtime/index.js", contractJson.getJSONObject("runtime").getString("bootstrap"))
            assertEquals("_conf_schema.json", contractJson.getJSONObject("config").getString("staticSchema"))
            assertTrue(File(installed.extractedDir, "runtime/index.js").exists())
            assertTrue(File(installed.extractedDir, "android-plugin.json").exists())
            assertTrue(File(installed.extractedDir, "_conf_schema.json").exists())
            assertFalse(contractJson.has("supportedTriggers"))
            assertFalse(contractJson.has("triggers"))
            assertFalse(File(installed.extractedDir, "android-execution.json").exists())
        } finally {
            resetPluginRepositoryForSampleTest(initialized = false)
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun sample_install_snapshot_keeps_packaged_seed_and_media_index_as_packaged_truth() = runTest {
        val tempDir = Files.createTempDirectory("meme-manager-sample-admin-assets").toFile()
        try {
            resetPluginRepositoryForSampleTest(initialized = true)
            val installed = installSample(tempDir = tempDir, version = "1.0.0")
            val adminSeed = File(installed.extractedDir, "resources/admin/seed.txt")
            val mediaIndex = File(installed.extractedDir, "resources/memes/index.json")
            val firstMeme = File(
                installed.extractedDir,
                "resources/memes/angry/9D03FF21BB828C2AF9CCC7FCCB1E25B3.jpg",
            )
            val contractJson = JSONObject(File(installed.extractedDir, "android-plugin.json").readText(Charsets.UTF_8))
            val indexJson = JSONObject(mediaIndex.readText(Charsets.UTF_8))
            val staticSchema = JSONObject(
                File(installed.extractedDir, "_conf_schema.json").readText(Charsets.UTF_8),
            )

            assertTrue(adminSeed.exists())
            assertTrue(adminSeed.readText(Charsets.UTF_8).trim().isNotBlank())
            assertTrue(mediaIndex.exists())
            assertTrue(firstMeme.exists())
            assertEquals("_conf_schema.json", installed.packageContractSnapshot!!.config.staticSchema)
            assertEquals("angry", staticSchema.getJSONObject("default_category").getString("default"))
            assertFalse(contractJson.has("supportedTriggers"))
            assertFalse(contractJson.has("triggers"))
            assertEquals("/meme", indexJson.getJSONArray("triggers").getJSONObject(0).getString("keyword"))
            assertTrue(indexJson.getJSONArray("categories").length() > 0)
            assertTrue(indexJson.getJSONArray("triggers").length() > 0)
        } finally {
            resetPluginRepositoryForSampleTest(initialized = false)
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun sample_install_snapshot_keeps_repository_origin_and_v2_packaged_assets_for_1_1_0() = runTest {
        val tempDir = Files.createTempDirectory("meme-manager-sample-runtime-admin-upgrade").toFile()
        try {
            resetPluginRepositoryForSampleTest(initialized = true)
            val installed = installSample(tempDir = tempDir, version = "1.1.0")
            val persisted = PluginRepository.findByPluginId(SAMPLE_PLUGIN_ID)
            val contractJson = JSONObject(File(installed.extractedDir, "android-plugin.json").readText(Charsets.UTF_8))

            assertEquals("sample", installed.catalogSourceId)
            assertTrue(installed.source.location.endsWith("meme-manager-1.1.0.zip"))
            assertTrue(File(installed.localPackagePath).exists())
            assertEquals(installed.packageContractSnapshot, persisted?.packageContractSnapshot)
            assertEquals("_conf_schema.json", installed.packageContractSnapshot!!.config.staticSchema)
            assertTrue(File(installed.extractedDir, "assets/readme.txt").exists())
            assertTrue(File(installed.extractedDir, "resources/admin/seed.txt").exists())
            assertTrue(File(installed.extractedDir, "resources/memes/index.json").exists())
            assertTrue(File(installed.extractedDir, "runtime/index.js").exists())
            assertTrue(File(installed.extractedDir, "_conf_schema.json").exists())
            assertFalse(contractJson.has("supportedTriggers"))
            assertFalse(File(installed.extractedDir, "android-execution.json").exists())
        } finally {
            resetPluginRepositoryForSampleTest(initialized = false)
            tempDir.deleteRecursively()
        }
    }

    private suspend fun installSample(tempDir: File, version: String): PluginInstallRecord {
        val zip = SampleAssetPaths.packageZip(version)
        assertTrue("Missing sample zip: ${zip.absolutePath}", zip.exists())
        val installer = PluginInstaller(
            validator = PluginPackageValidator(hostVersion = "0.3.6", supportedProtocolVersion = 2),
            storagePaths = PluginStoragePaths.fromFilesDir(tempDir),
            installStore = PluginRepository,
            remotePackageDownloader = RemotePluginPackageDownloader { _, destinationFile, _ ->
                zip.copyTo(destinationFile, overwrite = true)
            },
            clock = { 2000L },
        )
        return installer.install(
            PluginInstallIntent.catalogVersion(
                pluginId = SAMPLE_PLUGIN_ID,
                version = version,
                packageUrl = "https://samples.astrbot.local/catalog/packages/meme-manager-$version.zip",
                catalogSourceId = "sample",
            ),
        )
    }
}

private const val SAMPLE_PLUGIN_ID = "com.astrbot.samples.meme_manager"
