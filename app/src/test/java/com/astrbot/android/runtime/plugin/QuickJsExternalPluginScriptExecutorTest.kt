package com.astrbot.android.runtime.plugin

import java.io.File
import java.nio.file.Files
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

@Ignore("Requires JVM-native QuickJS library; current workspace only resolves Android native binaries.")
class QuickJsExternalPluginScriptExecutorTest {

    @Test
    fun quickjs_executor_runs_exported_entry_function_and_serializes_result() {
        val tempDir = Files.createTempDirectory("quickjs-executor-success").toFile()
        try {
            val scriptFile = File(tempDir, "index.js").apply {
                writeText(
                    """
                    export function handleEvent(context) {
                      return {
                        resultType: "text",
                        text: "Hello " + context.pluginId
                      };
                    }
                    """.trimIndent(),
                    Charsets.UTF_8,
                )
            }
            val executor = QuickJsExternalPluginScriptExecutor(initializeQuickJs = {})

            val output = executor.execute(
                ExternalPluginScriptExecutionRequest(
                    pluginId = "quickjs-sample",
                    scriptAbsolutePath = scriptFile.absolutePath,
                    entrySymbol = "handleEvent",
                    contextJson = JSONObject(mapOf("pluginId" to "quickjs-sample")).toString(),
                    pluginRootDirectory = tempDir.absolutePath,
                    timeoutMs = 2_000L,
                ),
            )

            val resultJson = JSONObject(output)
            assertEquals("text", resultJson.getString("resultType"))
            assertEquals("Hello quickjs-sample", resultJson.getString("text"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun quickjs_executor_raises_structured_failure_for_invalid_script() {
        val tempDir = Files.createTempDirectory("quickjs-executor-invalid-script").toFile()
        try {
            val scriptFile = File(tempDir, "index.js").apply {
                writeText(
                    """
                    export function handleEvent(context) {
                    """.trimIndent(),
                    Charsets.UTF_8,
                )
            }
            val executor = QuickJsExternalPluginScriptExecutor(initializeQuickJs = {})

            val error = try {
                executor.execute(
                    ExternalPluginScriptExecutionRequest(
                        pluginId = "quickjs-invalid",
                        scriptAbsolutePath = scriptFile.absolutePath,
                        entrySymbol = "handleEvent",
                        contextJson = "{}",
                        pluginRootDirectory = tempDir.absolutePath,
                        timeoutMs = 2_000L,
                    ),
                )
                throw AssertionError("Expected invalid script to fail")
            } catch (expected: IllegalStateException) {
                expected
            }

            assertTrue(error.message.orEmpty().contains("Failed to execute QuickJS entry"))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
