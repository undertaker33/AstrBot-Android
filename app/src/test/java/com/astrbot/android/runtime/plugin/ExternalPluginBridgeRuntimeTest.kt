package com.astrbot.android.runtime.plugin

import com.astrbot.android.model.plugin.ExternalPluginExecutionBindingStatus
import com.astrbot.android.model.plugin.ExternalPluginExecutionContract
import com.astrbot.android.model.plugin.ExternalPluginExecutionEntryPoint
import com.astrbot.android.model.plugin.ExternalPluginRuntimeBinding
import com.astrbot.android.model.plugin.ExternalPluginRuntimeKind
import com.astrbot.android.model.plugin.PluginTriggerSource
import com.astrbot.android.model.plugin.TextResult
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalPluginBridgeRuntimeTest {

    @Test
    fun bridge_runtime_decodes_quickjs_result_into_protocol_result() {
        val executor = RecordingExternalPluginScriptExecutor(
            outputs = listOf(
                JSONObject(
                    mapOf(
                        "resultType" to "text",
                        "text" to "hello from quickjs runtime",
                    ),
                ).toString(),
            ),
        )
        val runtime = ExternalPluginBridgeRuntime(
            scriptExecutor = executor,
            timeoutMs = 4_000L,
        )

        val result = runtime.execute(
            binding = readyBinding(runtimeKind = ExternalPluginRuntimeKind.JsQuickJs),
            context = executionContextFor(runtimePlugin("bridge-plugin")),
        )

        assertTrue(result is TextResult)
        assertEquals("hello from quickjs runtime", (result as TextResult).text)
        assertEquals(1, executor.requests.size)
        assertEquals("C:/tmp/runtime/index.js", executor.requests.single().scriptAbsolutePath)
        assertEquals("handleEvent", executor.requests.single().entrySymbol)
        assertTrue(executor.requests.single().contextJson.contains("\"trigger\":\"on_command\""))
    }

    @Test
    fun bridge_runtime_rejects_empty_quickjs_response() {
        val runtime = ExternalPluginBridgeRuntime(
            scriptExecutor = RecordingExternalPluginScriptExecutor(outputs = listOf("   ")),
            timeoutMs = 4_000L,
        )

        val error = try {
            runtime.execute(
                binding = readyBinding(runtimeKind = ExternalPluginRuntimeKind.JsQuickJs),
                context = executionContextFor(runtimePlugin("bridge-plugin")),
            )
            throw AssertionError("Expected empty quickjs response to fail")
        } catch (expected: IllegalStateException) {
            expected
        }

        assertTrue(error.message.orEmpty().contains("empty response"))
    }

    @Test
    fun bridge_runtime_rejects_invalid_quickjs_json_response() {
        val runtime = ExternalPluginBridgeRuntime(
            scriptExecutor = RecordingExternalPluginScriptExecutor(outputs = listOf("{not-valid-json")),
            timeoutMs = 4_000L,
        )

        val error = try {
            runtime.execute(
                binding = readyBinding(runtimeKind = ExternalPluginRuntimeKind.JsQuickJs),
                context = executionContextFor(runtimePlugin("bridge-plugin")),
            )
            throw AssertionError("Expected invalid quickjs json to fail")
        } catch (expected: IllegalStateException) {
            expected
        }

        assertTrue(error.message.orEmpty().contains("invalid JSON"))
    }

    @Test
    fun bridge_runtime_raises_structured_failure_for_quickjs_timeout() {
        val runtime = ExternalPluginBridgeRuntime(
            scriptExecutor = RecordingExternalPluginScriptExecutor(
                errors = listOf(
                    IllegalStateException("External plugin timed out after 2000ms: bridge-plugin"),
                ),
            ),
            timeoutMs = 2_000L,
        )

        val error = try {
            runtime.execute(
                binding = readyBinding(runtimeKind = ExternalPluginRuntimeKind.JsQuickJs),
                context = executionContextFor(runtimePlugin("bridge-plugin")),
            )
            throw AssertionError("Expected quickjs timeout to fail")
        } catch (expected: IllegalStateException) {
            expected
        }

        assertTrue(error.message.orEmpty().contains("timed out"))
    }

    private fun readyBinding(): ExternalPluginRuntimeBinding {
        return readyBinding(runtimeKind = ExternalPluginRuntimeKind.JsQuickJs)
    }

    private fun readyBinding(
        runtimeKind: ExternalPluginRuntimeKind,
    ): ExternalPluginRuntimeBinding {
        val record = samplePluginInstallRecord(
            pluginId = "bridge-plugin",
            lastUpdatedAt = 100L,
        )
        return ExternalPluginRuntimeBinding(
            installRecord = record,
            status = ExternalPluginExecutionBindingStatus.READY,
            contract = ExternalPluginExecutionContract(
                contractVersion = 1,
                enabled = true,
                entryPoint = ExternalPluginExecutionEntryPoint(
                    runtimeKind = runtimeKind,
                    path = "runtime/index.js",
                    entrySymbol = "handleEvent",
                ),
                supportedTriggers = setOf(PluginTriggerSource.OnCommand),
            ),
            entryAbsolutePath = "C:/tmp/runtime/index.js",
        )
    }
}

internal class RecordingExternalPluginScriptExecutor(
    private val outputs: List<String> = emptyList(),
    private val errors: List<IllegalStateException> = emptyList(),
) : ExternalPluginScriptExecutor {
    val requests = mutableListOf<ExternalPluginScriptExecutionRequest>()
    private var outputIndex = 0
    private var errorIndex = 0

    override fun execute(request: ExternalPluginScriptExecutionRequest): String {
        requests += request
        errors.getOrNull(errorIndex++)?.let { throw it }
        return outputs.getOrElse(outputIndex++) {
            error("Missing fake script output for request #${requests.size}")
        }
    }
}
