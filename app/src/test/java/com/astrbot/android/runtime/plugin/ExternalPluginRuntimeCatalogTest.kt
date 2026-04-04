package com.astrbot.android.runtime.plugin

import com.astrbot.android.model.plugin.PluginTriggerSource
import com.astrbot.android.model.plugin.TextResult
import java.io.File
import java.nio.file.Files
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalPluginRuntimeCatalogTest {

    @Test
    fun runtime_catalog_builds_external_runtime_plugin_from_ready_install_record() {
        val extractedDir = Files.createTempDirectory("external-runtime-catalog").toFile()
        try {
            File(extractedDir, "runtime/entry.py").apply {
                parentFile?.mkdirs()
                writeText("def handle_event(context):\n    return {'resultType': 'text', 'text': 'ok'}\n")
            }
            File(extractedDir, "android-execution.json").writeText(
                JSONObject(
                    mapOf(
                        "contractVersion" to 1,
                        "enabled" to true,
                        "entryPoint" to JSONObject(
                            mapOf(
                                "runtimeKind" to "python_main",
                                "path" to "runtime/entry.py",
                                "entrySymbol" to "handle_event",
                            ),
                        ),
                        "supportedTriggers" to listOf(
                            "on_command",
                            "after_model_response",
                            "on_schedule",
                        ),
                    ),
                ).toString(),
            )
            val record = pluginInstallRecordForExternalCatalog(extractedDir)
            val runner = RecordingExternalPluginProcessRunner(
                results = listOf(
                    ExternalPluginProcessResult(
                        exitCode = 0,
                        stdout = JSONObject(
                            mapOf(
                                "resultType" to "text",
                                "text" to "catalog runtime executed",
                            ),
                        ).toString(),
                        stderr = "",
                    ),
                ),
            )

            val plugin = ExternalPluginRuntimeCatalog.createRuntimePlugin(
                record = record,
                bridgeRuntime = ExternalPluginBridgeRuntime(
                    processRunner = runner,
                    pythonCommandCandidates = listOf("python-test"),
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
        } finally {
            extractedDir.deleteRecursively()
        }
    }

    private fun pluginInstallRecordForExternalCatalog(extractedDir: File) = samplePluginInstallRecord(
        pluginId = "com.astrbot.samples.external_catalog",
        version = "1.0.0",
        lastUpdatedAt = 100L,
    ).let { current ->
        com.astrbot.android.model.plugin.PluginInstallRecord.restoreFromPersistedState(
            manifestSnapshot = current.manifestSnapshot,
            source = current.source,
            permissionSnapshot = current.permissionSnapshot,
            compatibilityState = current.compatibilityState,
            uninstallPolicy = current.uninstallPolicy,
            enabled = current.enabled,
            failureState = current.failureState,
            catalogSourceId = current.catalogSourceId,
            installedPackageUrl = current.installedPackageUrl,
            lastCatalogCheckAtEpochMillis = current.lastCatalogCheckAtEpochMillis,
            installedAt = current.installedAt,
            lastUpdatedAt = current.lastUpdatedAt,
            localPackagePath = current.localPackagePath,
            extractedDir = extractedDir.absolutePath,
        )
    }
}
