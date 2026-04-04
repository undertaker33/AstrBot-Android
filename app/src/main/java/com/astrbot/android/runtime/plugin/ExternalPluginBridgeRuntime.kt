package com.astrbot.android.runtime.plugin

import com.astrbot.android.model.plugin.ExternalPluginRuntimeBinding
import com.astrbot.android.model.plugin.ExternalPluginRuntimeKind
import com.astrbot.android.model.plugin.PluginExecutionContext
import com.astrbot.android.model.plugin.PluginExecutionProtocolJson
import com.astrbot.android.model.plugin.PluginExecutionResult
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.json.JSONObject

data class ExternalPluginProcessRequest(
    val command: List<String>,
    val stdin: String,
    val workingDirectory: String,
    val timeoutMs: Long,
    val environment: Map<String, String> = emptyMap(),
)

data class ExternalPluginProcessResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean = false,
)

fun interface ExternalPluginProcessRunner {
    @Throws(IOException::class)
    fun execute(request: ExternalPluginProcessRequest): ExternalPluginProcessResult
}

class ExternalPluginBridgeRuntime(
    private val processRunner: ExternalPluginProcessRunner = DefaultExternalPluginProcessRunner,
    private val pythonCommandCandidates: List<String> = defaultPythonCommandCandidates(),
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {
    fun execute(
        binding: ExternalPluginRuntimeBinding,
        context: PluginExecutionContext,
    ): PluginExecutionResult {
        require(binding.isReady) { "External plugin binding is not ready: ${binding.status.name}" }
        val contract = binding.contract ?: error("External plugin binding is missing contract.")
        require(contract.entryPoint.runtimeKind == ExternalPluginRuntimeKind.PythonMain) {
            "Unsupported external runtime kind: ${contract.entryPoint.runtimeKind.wireValue}"
        }

        val requestBody = PluginExecutionProtocolJson
            .encodeExecutionContext(context)
            .toString()
        val lastLaunchErrors = mutableListOf<String>()

        pythonCommandCandidates.forEach { command ->
            val request = ExternalPluginProcessRequest(
                command = listOf(
                    command,
                    "-c",
                    PYTHON_BRIDGE_BOOTSTRAP,
                    binding.entryAbsolutePath,
                    contract.entryPoint.entrySymbol,
                ),
                stdin = requestBody,
                workingDirectory = binding.installRecord.extractedDir,
                timeoutMs = timeoutMs,
                environment = mapOf("PYTHONIOENCODING" to "utf-8"),
            )
            val processResult = try {
                processRunner.execute(request)
            } catch (error: IOException) {
                lastLaunchErrors += "$command: ${error.message ?: error.javaClass.simpleName}"
                return@forEach
            }

            if (processResult.timedOut) {
                throw IllegalStateException(
                    "External plugin timed out after ${timeoutMs}ms: ${binding.installRecord.pluginId}",
                )
            }
            if (processResult.exitCode != 0) {
                throw IllegalStateException(
                    processResult.stderr.ifBlank {
                        "External plugin exited with code ${processResult.exitCode}: ${binding.installRecord.pluginId}"
                    },
                )
            }

            val stdout = processResult.stdout.trim()
            require(stdout.isNotBlank()) {
                "External plugin returned an empty response: ${binding.installRecord.pluginId}"
            }
            return PluginExecutionProtocolJson.decodeResult(JSONObject(stdout))
        }

        throw IllegalStateException(
            buildString {
                append("No external Python runtime is available for ")
                append(binding.installRecord.pluginId)
                if (lastLaunchErrors.isNotEmpty()) {
                    append(". Tried: ")
                    append(lastLaunchErrors.joinToString("; "))
                }
            },
        )
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 10_000L

        internal val PYTHON_BRIDGE_BOOTSTRAP = """
import importlib.util
import json
import pathlib
import sys

entry_path = pathlib.Path(sys.argv[1]).resolve()
entry_symbol = sys.argv[2]
plugin_root = entry_path.parent.parent
spec = importlib.util.spec_from_file_location('astrbot_external_plugin_entry', entry_path)
if spec is None or spec.loader is None:
    raise RuntimeError('Cannot load plugin entry: {}'.format(entry_path))
module = importlib.util.module_from_spec(spec)
sys.path.insert(0, str(plugin_root))
spec.loader.exec_module(module)
handler = getattr(module, entry_symbol)
payload = json.load(sys.stdin)
result = handler(payload)
if result is None:
    result = {'resultType': 'noop', 'reason': 'External plugin returned no result.'}
json.dump(result, sys.stdout, ensure_ascii=False)
""".trimIndent()

        private fun defaultPythonCommandCandidates(): List<String> {
            return listOfNotNull(
                System.getProperty("astrbot.externalPlugin.python"),
                System.getenv("ASTRBOT_EXTERNAL_PLUGIN_PYTHON"),
                "python3",
                "python",
            ).map(String::trim).filter(String::isNotBlank).distinct()
        }
    }
}

object DefaultExternalPluginProcessRunner : ExternalPluginProcessRunner {
    override fun execute(request: ExternalPluginProcessRequest): ExternalPluginProcessResult {
        val process = ProcessBuilder(request.command)
            .directory(java.io.File(request.workingDirectory))
            .redirectErrorStream(false)
            .apply {
                environment().putAll(request.environment)
            }
            .start()

        process.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(request.stdin)
            writer.flush()
        }

        var stdout = ""
        var stderr = ""
        val stdoutThread = thread(name = "external-plugin-stdout") {
            stdout = process.inputStream.use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText()
            }
        }
        val stderrThread = thread(name = "external-plugin-stderr") {
            stderr = process.errorStream.use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText()
            }
        }

        val finished = process.waitFor(request.timeoutMs, TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroyForcibly()
            stdoutThread.join()
            stderrThread.join()
            return ExternalPluginProcessResult(
                exitCode = -1,
                stdout = stdout,
                stderr = stderr.ifBlank { "External plugin timed out." },
                timedOut = true,
            )
        }

        stdoutThread.join()
        stderrThread.join()
        return ExternalPluginProcessResult(
            exitCode = process.exitValue(),
            stdout = stdout,
            stderr = stderr,
        )
    }
}
