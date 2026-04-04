package com.astrbot.android.runtime.plugin

import com.astrbot.android.model.plugin.ExternalPluginExecutionBindingStatus
import com.astrbot.android.model.plugin.ExternalPluginExecutionContract
import com.astrbot.android.model.plugin.ExternalPluginExecutionEntryPoint
import com.astrbot.android.model.plugin.ExternalPluginRuntimeBinding
import com.astrbot.android.model.plugin.ExternalPluginRuntimeKind
import com.astrbot.android.model.plugin.PluginTriggerSource
import com.astrbot.android.model.plugin.TextResult
import java.io.IOException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalPluginBridgeRuntimeTest {

    @Test
    fun bridge_runtime_decodes_python_process_result_into_protocol_result() {
        val runner = RecordingExternalPluginProcessRunner(
            results = listOf(
                ExternalPluginProcessResult(
                    exitCode = 0,
                    stdout = JSONObject(
                        mapOf(
                            "resultType" to "text",
                            "text" to "hello from external runtime",
                        ),
                    ).toString(),
                    stderr = "",
                ),
            ),
        )
        val runtime = ExternalPluginBridgeRuntime(
            processRunner = runner,
            pythonCommandCandidates = listOf("python-test"),
            timeoutMs = 4_000L,
        )

        val result = runtime.execute(
            binding = readyBinding(),
            context = executionContextFor(runtimePlugin("bridge-plugin")),
        )

        assertTrue(result is TextResult)
        assertEquals("hello from external runtime", (result as TextResult).text)
        assertEquals(1, runner.requests.size)
        assertEquals("python-test", runner.requests.single().command.first())
        assertTrue(runner.requests.single().stdin.contains("\"trigger\":\"on_command\""))
        assertTrue(runner.requests.single().stdin.contains("\"pluginId\":\"bridge-plugin\""))
    }

    @Test
    fun bridge_runtime_falls_back_to_next_python_candidate_when_launch_fails() {
        val runner = RecordingExternalPluginProcessRunner(
            errorsByCommand = mapOf(
                "python-missing" to IOException("missing"),
            ),
            results = listOf(
                ExternalPluginProcessResult(
                    exitCode = 0,
                    stdout = JSONObject(
                        mapOf(
                            "resultType" to "text",
                            "text" to "fallback runtime",
                        ),
                    ).toString(),
                    stderr = "",
                ),
            ),
        )
        val runtime = ExternalPluginBridgeRuntime(
            processRunner = runner,
            pythonCommandCandidates = listOf("python-missing", "python-ready"),
        )

        val result = runtime.execute(
            binding = readyBinding(),
            context = executionContextFor(runtimePlugin("bridge-plugin")),
        )

        assertTrue(result is TextResult)
        assertEquals("fallback runtime", (result as TextResult).text)
        assertEquals(listOf("python-missing", "python-ready"), runner.requests.map { it.command.first() })
    }

    @Test
    fun bridge_runtime_raises_structured_failure_for_timeout() {
        val runtime = ExternalPluginBridgeRuntime(
            processRunner = RecordingExternalPluginProcessRunner(
                results = listOf(
                    ExternalPluginProcessResult(
                        exitCode = -1,
                        stdout = "",
                        stderr = "timed out",
                        timedOut = true,
                    ),
                ),
            ),
            pythonCommandCandidates = listOf("python-test"),
            timeoutMs = 2_000L,
        )

        val error = try {
            runtime.execute(
                binding = readyBinding(),
                context = executionContextFor(runtimePlugin("bridge-plugin")),
            )
            throw AssertionError("Expected bridge runtime timeout to fail")
        } catch (expected: IllegalStateException) {
            expected
        }

        assertTrue(error.message.orEmpty().contains("timed out"))
    }

    private fun readyBinding(): ExternalPluginRuntimeBinding {
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
                    runtimeKind = ExternalPluginRuntimeKind.PythonMain,
                    path = "runtime/entry.py",
                    entrySymbol = "handle_event",
                ),
                supportedTriggers = setOf(PluginTriggerSource.OnCommand),
            ),
            entryAbsolutePath = "C:/tmp/runtime/entry.py",
        )
    }
}

internal class RecordingExternalPluginProcessRunner(
    private val results: List<ExternalPluginProcessResult> = emptyList(),
    private val errorsByCommand: Map<String, IOException> = emptyMap(),
) : ExternalPluginProcessRunner {
    val requests = mutableListOf<ExternalPluginProcessRequest>()
    private var successIndex = 0

    override fun execute(request: ExternalPluginProcessRequest): ExternalPluginProcessResult {
        requests += request
        errorsByCommand[request.command.first()]?.let { throw it }
        return results.getOrElse(successIndex++) {
            error("Missing fake process result for request #${requests.size}")
        }
    }
}
