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

class TemplatePluginSampleInstallTest {

    @Test
    fun template_sample_package_installs_with_v2_contract_snapshot() = runBlocking {
        val tempDir = Files.createTempDirectory("template-sample-install").toFile()
        try {
            resetPluginRepositoryForSampleTest(initialized = true)
            val packageZip = SampleAssetPaths.templatePackageZip()
            assertTrue("Missing template sample zip: ${packageZip.absolutePath}", packageZip.exists())

            val installer = PluginInstaller(
                validator = PluginPackageValidator(hostVersion = "0.4.2", supportedProtocolVersion = 2),
                storagePaths = PluginStoragePaths.fromFilesDir(tempDir),
                installStore = PluginRepository,
                clock = { 3456L },
            )

            val installed = installer.installFromLocalPackage(packageZip)
            val persisted = PluginRepository.findByPluginId(installed.pluginId)

            assertEquals("com.example.astrbot.plugin.template", installed.pluginId)
            assertEquals(2, installed.manifestSnapshot.protocolVersion)
            assertNotNull(installed.packageContractSnapshot)
            assertTrue(File(installed.extractedDir, "runtime/index.js").exists())
            assertTrue(File(installed.extractedDir, "android-plugin.json").exists())
            assertFalse(File(installed.extractedDir, "android-execution.json").exists())
            assertFalse(File(installed.extractedDir, "runtime/entry.py").exists())
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
