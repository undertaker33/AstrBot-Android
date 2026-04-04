package com.astrbot.android.runtime.plugin.samples

import com.astrbot.android.data.PluginRepository
import com.astrbot.android.data.plugin.PluginStoragePaths
import com.astrbot.android.model.plugin.ExternalPluginExecutionBindingStatus
import com.astrbot.android.model.plugin.PluginTriggerSource
import com.astrbot.android.model.plugin.TextResult
import com.astrbot.android.runtime.plugin.ExternalPluginBridgeRuntime
import com.astrbot.android.runtime.plugin.ExternalPluginRuntimeBinder
import com.astrbot.android.runtime.plugin.PluginInstaller
import com.astrbot.android.runtime.plugin.PluginPackageValidator
import com.astrbot.android.runtime.plugin.executionContextFor
import com.astrbot.android.runtime.plugin.runtimePlugin
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class GreetingToolkitSampleInstallTest {

    @Test
    fun greeting_toolkit_sample_package_can_install_from_local_zip_and_extract_static_schema() = runBlocking {
        val tempDir = Files.createTempDirectory("greeting-toolkit-sample-install").toFile()
        try {
            resetPluginRepositoryForSampleTest(initialized = true)
            val packageZip = SampleAssetPaths.greetingToolkitPackageZip("1.0.0")
            assertTrue("Missing greeting toolkit sample zip: ${packageZip.absolutePath}", packageZip.exists())

            val installer = PluginInstaller(
                validator = PluginPackageValidator(hostVersion = "0.4.2", supportedProtocolVersion = 1),
                storagePaths = PluginStoragePaths.fromFilesDir(tempDir),
                installStore = PluginRepository,
                clock = { 1234L },
            )

            val installed = installer.installFromLocalPackage(packageZip)
            val binding = ExternalPluginRuntimeBinder().bind(installed)

            assertEquals("com.astrbot.samples.greeting_toolkit", installed.pluginId)
            assertTrue(File(installed.extractedDir, "_conf_schema.json").exists())
            assertTrue(File(installed.extractedDir, "assets/prompts/default.txt").exists())
            assertTrue(File(installed.extractedDir, "android-execution.json").exists())
            assertEquals(ExternalPluginExecutionBindingStatus.READY, binding.status)
            assertTrue(binding.entryAbsolutePath.isNotBlank())
            assertTrue(File(binding.entryAbsolutePath).exists())
        } finally {
            resetPluginRepositoryForSampleTest(initialized = false)
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun greeting_toolkit_sample_runtime_executes_via_python_bridge() = runBlocking {
        assumeTrue(isPythonAvailable())
        val tempDir = Files.createTempDirectory("greeting-toolkit-sample-runtime").toFile()
        try {
            resetPluginRepositoryForSampleTest(initialized = true)
            val packageZip = SampleAssetPaths.greetingToolkitPackageZip("1.0.0")
            val installer = PluginInstaller(
                validator = PluginPackageValidator(hostVersion = "0.4.2", supportedProtocolVersion = 1),
                storagePaths = PluginStoragePaths.fromFilesDir(tempDir),
                installStore = PluginRepository,
                clock = { 1234L },
            )

            val installed = installer.installFromLocalPackage(packageZip)
            val binding = ExternalPluginRuntimeBinder().bind(installed)
            val runtime = ExternalPluginBridgeRuntime(
                pythonCommandCandidates = listOf("python"),
            )

            val result = runtime.execute(
                binding = binding,
                context = executionContextFor(
                    runtimePlugin(
                        pluginId = installed.pluginId,
                        version = installed.installedVersion,
                        supportedTriggers = setOf(PluginTriggerSource.OnCommand),
                    ),
                    trigger = PluginTriggerSource.OnCommand,
                ),
            )

            assertTrue(result is TextResult)
            assertTrue((result as TextResult).text.contains("Greeting Toolkit", ignoreCase = true))
        } finally {
            resetPluginRepositoryForSampleTest(initialized = false)
            tempDir.deleteRecursively()
        }
    }

    private fun isPythonAvailable(): Boolean {
        return runCatching {
            ProcessBuilder("python", "--version")
                .redirectErrorStream(true)
                .start()
                .waitFor() == 0
        }.getOrDefault(false)
    }
}
