package com.astrbot.android.runtime.plugin.samples

import com.astrbot.android.data.PluginRepository
import com.astrbot.android.data.plugin.PluginStoragePaths
import com.astrbot.android.model.plugin.PluginRuntimeDeclarationSnapshot
import com.astrbot.android.runtime.plugin.PluginInstaller
import com.astrbot.android.runtime.plugin.PluginPackageValidator
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GreetingToolkitSampleInstallTest {

    @Test
    fun greeting_toolkit_sample_package_installs_with_v2_contract_snapshot() = runBlocking {
        val tempDir = Files.createTempDirectory("greeting-toolkit-sample-install").toFile()
        try {
            resetPluginRepositoryForSampleTest(initialized = true)
            val packageZip = SampleAssetPaths.greetingToolkitPackageZip("1.0.0")
            assertTrue("Missing greeting toolkit sample zip: ${packageZip.absolutePath}", packageZip.exists())

            val installer = PluginInstaller(
                validator = PluginPackageValidator(hostVersion = "0.4.2", supportedProtocolVersion = 2),
                storagePaths = PluginStoragePaths.fromFilesDir(tempDir),
                installStore = PluginRepository,
                clock = { 1234L },
            )

            val installed = installer.installFromLocalPackage(packageZip)
            val persisted = PluginRepository.findByPluginId(installed.pluginId)

            assertEquals("com.astrbot.samples.greeting_toolkit", installed.pluginId)
            assertTrue(File(installed.extractedDir, "_conf_schema.json").exists())
            assertTrue(File(installed.extractedDir, "assets/prompts/default.txt").exists())
            assertTrue(File(installed.extractedDir, "android-plugin.json").exists())
            assertTrue(File(installed.extractedDir, "runtime/index.js").exists())
            assertFalse(File(installed.extractedDir, "android-execution.json").exists())
            assertFalse(File(installed.extractedDir, "runtime/entry.py").exists())
            assertNotNull(installed.packageContractSnapshot)
            assertEquals(2, installed.manifestSnapshot.protocolVersion)
            assertEquals(2, installed.packageContractSnapshot!!.protocolVersion)
            assertEquals(
                PluginRuntimeDeclarationSnapshot(
                    kind = "js_quickjs",
                    bootstrap = "runtime/index.js",
                    apiVersion = 1,
                ),
                installed.packageContractSnapshot!!.runtime,
            )
            assertEquals(installed.packageContractSnapshot, persisted?.packageContractSnapshot)
        } finally {
            resetPluginRepositoryForSampleTest(initialized = false)
            tempDir.deleteRecursively()
        }
    }
}
