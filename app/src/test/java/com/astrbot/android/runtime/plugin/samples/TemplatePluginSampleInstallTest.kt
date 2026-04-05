package com.astrbot.android.runtime.plugin.samples

import com.astrbot.android.data.PluginRepository
import com.astrbot.android.data.plugin.PluginStoragePaths
import com.astrbot.android.model.plugin.ExternalPluginExecutionBindingStatus
import com.astrbot.android.model.plugin.ExternalPluginExecutionContractJson
import com.astrbot.android.model.plugin.ExternalPluginRuntimeKind
import com.astrbot.android.model.plugin.PluginTriggerSource
import com.astrbot.android.model.plugin.TextResult
import com.astrbot.android.runtime.plugin.ExternalPluginBridgeRuntime
import com.astrbot.android.runtime.plugin.ExternalPluginRuntimeBinder
import com.astrbot.android.runtime.plugin.ExternalPluginScriptExecutionRequest
import com.astrbot.android.runtime.plugin.ExternalPluginScriptExecutor
import com.astrbot.android.runtime.plugin.PluginInstaller
import com.astrbot.android.runtime.plugin.PluginPackageValidator
import com.astrbot.android.runtime.plugin.executionContextFor
import com.astrbot.android.runtime.plugin.runtimePlugin
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplatePluginSampleInstallTest {

    @Test
    fun template_sample_package_installs_and_binds_quickjs_entry() = runBlocking {
        val tempDir = Files.createTempDirectory("template-sample-install").toFile()
        try {
            resetPluginRepositoryForSampleTest(initialized = true)
            val packageZip = SampleAssetPaths.templatePackageZip()
            assertTrue("Missing template sample zip: ${packageZip.absolutePath}", packageZip.exists())

            val installer = PluginInstaller(
                validator = PluginPackageValidator(hostVersion = "0.4.2", supportedProtocolVersion = 1),
                storagePaths = PluginStoragePaths.fromFilesDir(tempDir),
                installStore = PluginRepository,
                clock = { 3456L },
            )

            val installed = installer.installFromLocalPackage(packageZip)
            val binding = ExternalPluginRuntimeBinder().bind(installed)

            assertEquals("com.example.astrbot.plugin.template", installed.pluginId)
            assertEquals(ExternalPluginExecutionBindingStatus.READY, binding.status)
            assertTrue(File(installed.extractedDir, "runtime/index.js").exists())
            assertFalse(File(installed.extractedDir, "runtime/entry.py").exists())
            val contract = ExternalPluginExecutionContractJson.decodeContract(
                JSONObject(File(installed.extractedDir, "android-execution.json").readText(Charsets.UTF_8)),
            )
            assertEquals(ExternalPluginRuntimeKind.JsQuickJs, contract.entryPoint.runtimeKind)
            assertEquals("runtime/index.js", contract.entryPoint.path)
            assertEquals("handleEvent", contract.entryPoint.entrySymbol)
        } finally {
            resetPluginRepositoryForSampleTest(initialized = false)
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun template_sample_runtime_executes_via_quickjs_bridge_contract() = runBlocking {
        val tempDir = Files.createTempDirectory("template-sample-runtime").toFile()
        try {
            resetPluginRepositoryForSampleTest(initialized = true)
            val packageZip = SampleAssetPaths.templatePackageZip()
            val installer = PluginInstaller(
                validator = PluginPackageValidator(hostVersion = "0.4.2", supportedProtocolVersion = 1),
                storagePaths = PluginStoragePaths.fromFilesDir(tempDir),
                installStore = PluginRepository,
                clock = { 3456L },
            )

            val installed = installer.installFromLocalPackage(packageZip)
            val binding = ExternalPluginRuntimeBinder().bind(installed)
            val scriptRequests = mutableListOf<ExternalPluginScriptExecutionRequest>()
            val runtime = ExternalPluginBridgeRuntime(
                scriptExecutor = object : ExternalPluginScriptExecutor {
                    override fun execute(request: ExternalPluginScriptExecutionRequest): String {
                        scriptRequests += request
                        val script = File(request.scriptAbsolutePath).readText(Charsets.UTF_8)
                        assertTrue(script.contains("function handleEvent"))
                        assertTrue(script.contains("AstrBot Template"))
                        return """{"resultType":"text","text":"Template quickjs runtime"}"""
                    }
                },
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
            assertEquals("Template quickjs runtime", (result as TextResult).text)
            assertEquals(1, scriptRequests.size)
            assertTrue(
                scriptRequests.single().scriptAbsolutePath.endsWith("runtime\\index.js") ||
                    scriptRequests.single().scriptAbsolutePath.endsWith("runtime/index.js"),
            )
            assertEquals("handleEvent", scriptRequests.single().entrySymbol)
        } finally {
            resetPluginRepositoryForSampleTest(initialized = false)
            tempDir.deleteRecursively()
        }
    }
}
