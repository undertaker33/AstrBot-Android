package com.astrbot.android.feature.plugin.runtime

import com.astrbot.android.model.plugin.PluginTriggerSource
import com.astrbot.android.model.plugin.TextResult
import com.astrbot.android.model.plugin.ExternalPluginExecutionBindingStatus
import java.io.File
import java.nio.file.Files
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalPluginRuntimeCatalogTest {

    @Test
    fun runtime_catalog_builds_external_runtime_plugin_from_ready_quickjs_install_record() {
        val extractedDir = Files.createTempDirectory("external-runtime-catalog").toFile()
        try {
            val record = createQuickJsExternalPluginInstallRecord(
                extractedDir = extractedDir,
                pluginId = "com.astrbot.samples.external_catalog",
                supportedTriggers = listOf(
                    "on_command",
                    "after_model_response",
                    "on_schedule",
                ),
            )
            val executor = RecordingExternalPluginScriptExecutor(
                outputs = listOf(
                    JSONObject(
                        mapOf(
                            "resultType" to "text",
                            "text" to "catalog runtime executed",
                        ),
                    ).toString(),
                ),
            )

            val plugin = ExternalPluginRuntimeCatalog.createRuntimePlugin(
                record = record,
                bridgeRuntime = ExternalPluginBridgeRuntime(
                    scriptExecutor = executor,
                ),
            )

            assertNotNull(plugin)
            assertEquals(
                setOf(
                    PluginTriggerSource.OnCommand,
                    PluginTriggerSource.AfterModelResponse,
                ),
                plugin?.supportedTriggers,
            )

            val result = plugin!!.handler.execute(
                executionContextFor(plugin, trigger = PluginTriggerSource.AfterModelResponse),
            )
            assertTrue(result is TextResult)
            assertEquals("catalog runtime executed", (result as TextResult).text)
            assertEquals(1, executor.requests.size)
            assertEquals(
                extractedDir.resolve("runtime/index.js").absolutePath,
                executor.requests.single().scriptAbsolutePath,
            )
            assertEquals("handleEvent", executor.requests.single().entrySymbol)
        } finally {
            extractedDir.deleteRecursively()
        }
    }

    @Test
    fun runtime_catalog_skips_plugin_v2_records_and_legacy_binder_marks_them_not_applicable() {
        val record = samplePluginV2InstallRecord(
            pluginId = "com.astrbot.samples.v2_catalog_skip",
        )

        val binding = ExternalPluginRuntimeBinder().bind(record)
        val plugin = ExternalPluginRuntimeCatalog.createRuntimePlugin(record)

        assertFalse(binding.isReady)
        assertEquals(ExternalPluginExecutionBindingStatus.INVALID_CONTRACT, binding.status)
        assertEquals("Plugin v2 records are skipped by the legacy external runtime binder.", binding.errorSummary)
        assertNull(plugin)
    }
}
