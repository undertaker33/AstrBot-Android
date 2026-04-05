package com.astrbot.android.runtime.plugin

import com.whl.quickjs.wrapper.QuickJSContext
import java.io.File
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.json.JSONObject

data class ExternalPluginScriptExecutionRequest(
    val pluginId: String,
    val scriptAbsolutePath: String,
    val entrySymbol: String,
    val contextJson: String,
    val pluginRootDirectory: String,
    val timeoutMs: Long,
)

fun interface ExternalPluginScriptExecutor {
    fun execute(request: ExternalPluginScriptExecutionRequest): String
}

class QuickJsExternalPluginScriptExecutor(
    private val initializeQuickJs: () -> Unit = ::initializeQuickJsPlatformIfNeeded,
) : ExternalPluginScriptExecutor {
    override fun execute(request: ExternalPluginScriptExecutionRequest): String {
        initializeQuickJs()
        val executor = Executors.newSingleThreadExecutor()
        try {
            val task = executor.submit<String> {
                executeBlocking(request)
            }
            return try {
                task.get(request.timeoutMs, TimeUnit.MILLISECONDS)
            } catch (failure: ExecutionException) {
                val cause = failure.cause
                when (cause) {
                    is IllegalStateException -> throw cause
                    is RuntimeException -> throw cause
                    else -> throw IllegalStateException(
                        "Failed to execute QuickJS entry for ${request.pluginId}: ${cause?.message ?: failure.message ?: failure.javaClass.simpleName}",
                        cause ?: failure,
                    )
                }
            } catch (timeout: TimeoutException) {
                task.cancel(true)
                throw IllegalStateException(
                    "External plugin timed out after ${request.timeoutMs}ms: ${request.pluginId}",
                    timeout,
                )
            }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun executeBlocking(request: ExternalPluginScriptExecutionRequest): String {
        val scriptFile = File(request.scriptAbsolutePath)
        require(scriptFile.isFile) {
            "Missing external entry file: ${request.scriptAbsolutePath}"
        }
        val scriptSource = scriptFile.readText(Charsets.UTF_8)
        val moduleSource = buildQuickJsModuleSource(
            scriptSource = scriptSource,
            contextJson = request.contextJson,
            entrySymbol = request.entrySymbol,
        )
        return runCatching {
            QuickJSContext.create().use { context ->
                val evaluation = context.evaluateModule(moduleSource, scriptFile.name)
                val globalObject = context.getGlobalObject()
                val serializedResult = context.getProperty(globalObject, QUICKJS_RESULT_PROPERTY)
                resolveQuickJsSerializedResult(
                    evaluationResult = evaluation,
                    globalSerializedResult = serializedResult,
                )
            }
        }.getOrElse { error ->
            throw IllegalStateException(
                "Failed to execute QuickJS entry for ${request.pluginId}: ${error.message ?: error.javaClass.simpleName}",
                error,
            )
        }
    }

    private fun buildQuickJsModuleSource(
        scriptSource: String,
        contextJson: String,
        entrySymbol: String,
    ): String {
        val quotedContextJson = JSONObject.quote(contextJson)
        val quotedEntrySymbol = JSONObject.quote(entrySymbol)
        return buildString {
            appendLine(scriptSource)
            appendLine()
            appendLine("const __astrbotContext = JSON.parse($quotedContextJson);")
            appendLine("const __astrbotEntryName = $quotedEntrySymbol;")
            appendLine("globalThis.$QUICKJS_RESULT_PROPERTY = '';")
            appendLine("const __astrbotEntry = typeof globalThis[__astrbotEntryName] === 'function' ? globalThis[__astrbotEntryName] : (typeof handleEvent === 'function' && __astrbotEntryName === 'handleEvent' ? handleEvent : undefined);")
            appendLine("if (typeof __astrbotEntry !== 'function') {")
            appendLine("  throw new Error(`Missing entry symbol: ${'$'}{__astrbotEntryName}`);")
            appendLine("}")
            appendLine("const __astrbotResult = __astrbotEntry(__astrbotContext);")
            appendLine("if (__astrbotResult && typeof __astrbotResult.then === 'function') {")
            appendLine("  throw new Error('Async QuickJS entry is not supported in v1.');")
            appendLine("}")
            appendLine("globalThis.$QUICKJS_RESULT_PROPERTY = JSON.stringify(__astrbotResult ?? { resultType: 'noop', reason: 'External plugin returned no result.' });")
            appendLine("globalThis.$QUICKJS_RESULT_PROPERTY;")
        }
    }

    companion object {
        internal const val QUICKJS_RESULT_PROPERTY = "__astrbotSerializedResult"
    }
}

internal fun resolveQuickJsSerializedResult(
    evaluationResult: Any?,
    globalSerializedResult: Any?,
): String {
    val globalResult = globalSerializedResult?.toString().orEmpty().trim()
    if (globalResult.isNotBlank()) {
        return globalResult
    }
    return evaluationResult?.toString().orEmpty().trim()
}

private fun initializeQuickJsPlatformIfNeeded() {
    runCatching {
        val loaderClass = Class.forName("com.whl.quickjs.android.QuickJSLoader")
        val initMethod = loaderClass.getMethod("init")
        initMethod.invoke(null)
    }.onFailure { error ->
        if (error is ClassNotFoundException) {
            return
        }
        val cause = error.cause
        if (cause is ClassNotFoundException) {
            return
        }
        throw IllegalStateException(
            "Failed to initialize QuickJS platform loader: ${error.message ?: error.javaClass.simpleName}",
            error,
        )
    }
}
