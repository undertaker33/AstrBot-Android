package com.astrbot.android.runtime.plugin

import com.whl.quickjs.wrapper.QuickJSContext
import com.whl.quickjs.wrapper.JSFunction
import com.whl.quickjs.wrapper.JSObject
import com.whl.quickjs.wrapper.ModuleLoader
import java.io.File
import java.nio.file.Path
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

data class ExternalPluginBootstrapSessionRequest(
    val pluginId: String,
    val bootstrapAbsolutePath: String,
    val pluginRootDirectory: String,
    val bootstrapTimeoutMs: Long,
)

interface ExternalPluginBootstrapSession {
    val pluginId: String
    val bootstrapAbsolutePath: String
    val bootstrapTimeoutMs: Long
    val liveHandleCount: Int

    fun installGlobal(name: String, value: Any?)

    fun executeBootstrap()

    fun dispose()
}

fun interface ExternalPluginScriptExecutor {
    fun execute(request: ExternalPluginScriptExecutionRequest): String

    fun openBootstrapSession(
        request: ExternalPluginBootstrapSessionRequest,
    ): ExternalPluginBootstrapSession {
        throw IllegalStateException(
            "V2 bootstrap sessions are not supported by ${this::class.java.simpleName}.",
        )
    }
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

    override fun openBootstrapSession(
        request: ExternalPluginBootstrapSessionRequest,
    ): ExternalPluginBootstrapSession {
        initializeQuickJs()
        return QuickJsExternalPluginBootstrapSession(request)
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
        internal const val QUICKJS_BOOTSTRAP_CALLABLE_PROPERTY = "__astrbotBootstrapCallable"
        internal const val QUICKJS_BOOTSTRAP_HOST_API_PROPERTY = "__astrbotBootstrapHostApi"
        internal const val QUICKJS_BOOTSTRAP_COMPLETION_STATE_PROPERTY =
            "__astrbotBootstrapCompletionState"
        internal const val QUICKJS_BOOTSTRAP_COMPLETION_ERROR_PROPERTY =
            "__astrbotBootstrapCompletionError"
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

private class QuickJsExternalPluginBootstrapSession(
    request: ExternalPluginBootstrapSessionRequest,
) : ExternalPluginBootstrapSession {
    override val pluginId: String = request.pluginId
    override val bootstrapAbsolutePath: String = request.bootstrapAbsolutePath
    override val bootstrapTimeoutMs: Long = request.bootstrapTimeoutMs

    private val pluginRootDirectory: String = request.pluginRootDirectory
    private val executor = Executors.newSingleThreadExecutor()

    @Volatile
    private var runtime: QuickJsBootstrapRuntime? = initializeRuntime()

    override val liveHandleCount: Int
        get() = runtime?.handleStore?.size ?: 0

    override fun installGlobal(name: String, value: Any?) {
        require(name.isNotBlank()) { "QuickJS bootstrap global name must not be blank." }
        executeOnRuntime("install global $name") { runtimeHandle ->
            runtimeHandle.context.setProperty(runtimeHandle.globalObject, name, value)
            if (value is JSObject) {
                runtimeHandle.handleStore["global::$name"] = value
            }
        }
    }

    override fun executeBootstrap() {
        executeOnRuntime("execute bootstrap") { runtimeHandle ->
            var bootstrapEvaluationResult: Any? = null
            try {
                bootstrapEvaluationResult = runtimeHandle.context.evaluate(
                    buildQuickJsBootstrapExecutionSource(),
                    runtimeHandle.bootstrapExecutionSourcePath,
                )
                val completionState = awaitQuickJsBootstrapCompletion(
                    initialState = bootstrapEvaluationResult?.toString(),
                    timeoutMs = bootstrapTimeoutMs,
                ) {
                    runtimeHandle.context.evaluate(
                        buildQuickJsBootstrapCompletionStatePollSource(),
                        runtimeHandle.bootstrapPollSourcePath,
                    )?.toString()
                }
                check(completionState == QUICKJS_BOOTSTRAP_COMPLETION_STATE_FULFILLED) {
                    val errorDetail = runtimeHandle.context.getProperty(
                        runtimeHandle.globalObject,
                        QuickJsExternalPluginScriptExecutor.QUICKJS_BOOTSTRAP_COMPLETION_ERROR_PROPERTY,
                    )?.toString().orEmpty().trim()
                    if (errorDetail.isBlank()) {
                        "QuickJS bootstrap completed with unexpected state $completionState for $pluginId."
                    } else {
                        "QuickJS bootstrap completed with state $completionState for $pluginId: $errorDetail"
                    }
                }
            } finally {
                releaseQuickJsValueIfNeeded(bootstrapEvaluationResult)
            }
        }
    }

    override fun dispose() {
        val runtimeToDispose = runtime
        runtime = null
        if (runtimeToDispose != null) {
            val task = executor.submit<Unit> {
                runtimeToDispose.dispose()
            }
            runCatching {
                task.get(bootstrapTimeoutMs, TimeUnit.MILLISECONDS)
            }.getOrElse {
                task.cancel(true)
            }
        }
        executor.shutdownNow()
    }

    private fun initializeRuntime(): QuickJsBootstrapRuntime {
        return executeWithTimeout("open bootstrap session") {
            val context = QuickJSContext.create()
            val handleStore = linkedMapOf<String, Any>()
            try {
                context.setModuleLoader(
                    FileSystemQuickJsModuleLoader(
                        pluginRootDirectory = pluginRootDirectory,
                    ),
                )
                val globalObject = context.getGlobalObject()
                handleStore["globalObject"] = globalObject
                val bootstrapCallable = resolveBootstrapCallable(
                    context = context,
                    pluginRootDirectory = pluginRootDirectory,
                    bootstrapAbsolutePath = bootstrapAbsolutePath,
                )
                handleStore["bootstrapCallable"] = bootstrapCallable
                QuickJsBootstrapRuntime(
                    context = context,
                    globalObject = globalObject,
                    bootstrapCallable = bootstrapCallable,
                    bootstrapExecutionSourcePath = bootstrapExecutionSourcePath(),
                    bootstrapPollSourcePath = bootstrapPollSourcePath(),
                    handleStore = handleStore,
                )
            } catch (error: Exception) {
                handleStore.values.filterIsInstance<JSObject>().forEach { handle ->
                    runCatching { handle.release() }
                }
                runCatching { context.close() }
                throw error
            }
        }
    }

    private fun executeOnRuntime(
        actionName: String,
        action: (QuickJsBootstrapRuntime) -> Unit,
    ) {
        executeWithTimeout(actionName) {
            val runtimeHandle = checkNotNull(runtime) {
                "QuickJS bootstrap session has already been disposed: $pluginId"
            }
            action(runtimeHandle)
        }
    }

    private fun <T> executeWithTimeout(
        actionName: String,
        action: () -> T,
    ): T {
        val task = executor.submit<T> {
            action()
        }
        return try {
            task.get(bootstrapTimeoutMs, TimeUnit.MILLISECONDS)
        } catch (failure: ExecutionException) {
            val cause = failure.cause
            when (cause) {
                is IllegalStateException -> throw cause
                is RuntimeException -> throw cause
                else -> throw IllegalStateException(
                    "Failed to $actionName for $pluginId: ${cause?.message ?: failure.message ?: failure.javaClass.simpleName}",
                    cause ?: failure,
                )
            }
        } catch (timeout: TimeoutException) {
            task.cancel(true)
            throw IllegalStateException(
                "QuickJS bootstrap timed out after ${bootstrapTimeoutMs}ms: $pluginId",
                timeout,
            )
        }
    }

    private fun bootstrapExecutionSourcePath(): String {
        return File(pluginRootDirectory, "__astrbot_bootstrap_exec__.js")
            .absolutePath
            .replace('\\', '/')
    }

    private fun bootstrapPollSourcePath(): String {
        return File(pluginRootDirectory, "__astrbot_bootstrap_poll__.js")
            .absolutePath
            .replace('\\', '/')
    }

    private fun resolveBootstrapCallable(
        context: QuickJSContext,
        pluginRootDirectory: String,
        bootstrapAbsolutePath: String,
    ): JSFunction {
        val pluginRootPath = File(pluginRootDirectory).toPath()
        val bootstrapPath = File(bootstrapAbsolutePath).toPath()
        val bootstrapSpecifier = "./" + pluginRootPath.relativize(bootstrapPath)
            .toString()
            .replace('\\', '/')
        val loaderModulePath = File(pluginRootDirectory, "__astrbot_runtime_loader__.mjs")
            .absolutePath
            .replace('\\', '/')
        val loaderSource = buildString {
            appendLine("import * as __astrbotBootstrapModule from ${JSONObject.quote(bootstrapSpecifier)};")
            appendLine("const __astrbotBootstrapCandidates = [];")
            appendLine("if (typeof __astrbotBootstrapModule.default === 'function') {")
            appendLine("  __astrbotBootstrapCandidates.push(__astrbotBootstrapModule.default);")
            appendLine("}")
            appendLine("if (typeof __astrbotBootstrapModule.bootstrap === 'function') {")
            appendLine("  __astrbotBootstrapCandidates.push(__astrbotBootstrapModule.bootstrap);")
            appendLine("}")
            appendLine("if (__astrbotBootstrapCandidates.length === 0) {")
            appendLine("  throw new Error('Missing bootstrap callable. Expected a default export or named bootstrap().');")
            appendLine("}")
            appendLine("if (__astrbotBootstrapCandidates.length > 1) {")
            appendLine("  throw new Error('Bootstrap module must resolve to a single callable.');")
            appendLine("}")
            appendLine("globalThis.${QuickJsExternalPluginScriptExecutor.QUICKJS_BOOTSTRAP_CALLABLE_PROPERTY} = __astrbotBootstrapCandidates[0];")
            appendLine("globalThis.${QuickJsExternalPluginScriptExecutor.QUICKJS_BOOTSTRAP_CALLABLE_PROPERTY};")
        }
        val loaderEvaluation = context.evaluateModule(loaderSource, loaderModulePath)
        releaseQuickJsValueIfNeeded(loaderEvaluation)
        val bootstrapCallable = context.getProperty(
            context.getGlobalObject(),
            QuickJsExternalPluginScriptExecutor.QUICKJS_BOOTSTRAP_CALLABLE_PROPERTY,
        )
        return bootstrapCallable as? JSFunction
            ?: throw IllegalStateException(
                "Resolved bootstrap export is not callable for plugin $pluginId.",
            )
    }
}

private data class QuickJsBootstrapRuntime(
    val context: QuickJSContext,
    val globalObject: JSObject,
    val bootstrapCallable: JSFunction,
    val bootstrapExecutionSourcePath: String,
    val bootstrapPollSourcePath: String,
    val handleStore: LinkedHashMap<String, Any>,
) {
    fun dispose() {
        handleStore.values
            .filterIsInstance<JSObject>()
            .forEach { handle ->
                runCatching { handle.release() }
            }
        handleStore.clear()
        runCatching { context.releaseObjectRecords(true) }
        runCatching { context.close() }
    }
}

internal const val QUICKJS_BOOTSTRAP_COMPLETION_STATE_PENDING: String = "pending"
internal const val QUICKJS_BOOTSTRAP_COMPLETION_STATE_FULFILLED: String = "fulfilled"
internal const val QUICKJS_BOOTSTRAP_COMPLETION_STATE_REJECTED: String = "rejected"
internal const val QUICKJS_BOOTSTRAP_POLL_INTERVAL_MS: Long = 10L

internal fun awaitQuickJsBootstrapCompletion(
    initialState: String?,
    timeoutMs: Long,
    pollIntervalMs: Long = QUICKJS_BOOTSTRAP_POLL_INTERVAL_MS,
    pollState: () -> String?,
): String {
    val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
    var completionState = normalizeQuickJsBootstrapCompletionState(initialState)
    while (completionState == QUICKJS_BOOTSTRAP_COMPLETION_STATE_PENDING) {
        if (Thread.currentThread().isInterrupted) {
            throw IllegalStateException("QuickJS bootstrap wait was interrupted.")
        }
        val remainingNanos = deadlineNanos - System.nanoTime()
        if (remainingNanos <= 0L) {
            throw IllegalStateException(
                "QuickJS bootstrap timed out after ${timeoutMs}ms while waiting for async bootstrap completion.",
            )
        }
        if (pollIntervalMs > 0L) {
            val sleepMillis = minOf(
                pollIntervalMs,
                TimeUnit.NANOSECONDS.toMillis(remainingNanos).coerceAtLeast(1L),
            )
            Thread.sleep(sleepMillis)
        }
        completionState = normalizeQuickJsBootstrapCompletionState(pollState())
    }
    return completionState
}

internal fun releaseQuickJsValueIfNeeded(value: Any?) {
    val jsObject = value as? JSObject ?: return
    runCatching {
        jsObject.release()
    }
}

internal fun resolveQuickJsModuleFile(
    pluginRootDirectory: String,
    baseName: String,
    moduleName: String,
): File {
    val canonicalPluginRoot = File(pluginRootDirectory).canonicalFile
    val baseFile = resolveQuickJsModuleBaseFile(
        canonicalPluginRoot = canonicalPluginRoot,
        baseName = baseName,
    )
    val baseDirectory = if (baseFile.isDirectory) {
        baseFile
    } else {
        baseFile.parentFile ?: canonicalPluginRoot
    }
    val candidateFile = if (File(moduleName).isAbsolute) {
        File(moduleName)
    } else {
        File(baseDirectory, moduleName)
    }
    val canonicalCandidate = candidateFile.canonicalFile
    check(canonicalCandidate.toPath().startsWith(canonicalPluginRoot.toPath())) {
        "QuickJS bootstrap module must stay within the plugin root: $moduleName"
    }
    return canonicalCandidate
}

private fun normalizeQuickJsBootstrapCompletionState(state: String?): String {
    return state?.trim().orEmpty().ifBlank {
        QUICKJS_BOOTSTRAP_COMPLETION_STATE_PENDING
    }
}

private fun resolveQuickJsModuleBaseFile(
    canonicalPluginRoot: File,
    baseName: String,
): File {
    if (baseName.isBlank()) {
        return canonicalPluginRoot
    }
    val baseFile = File(baseName)
    return if (baseFile.isAbsolute) {
        baseFile.canonicalFile
    } else {
        File(canonicalPluginRoot, baseName).canonicalFile
    }
}

private fun buildQuickJsBootstrapExecutionSource(): String {
    return buildString {
        appendLine(
            "globalThis.${QuickJsExternalPluginScriptExecutor.QUICKJS_BOOTSTRAP_COMPLETION_STATE_PROPERTY} = ${JSONObject.quote(QUICKJS_BOOTSTRAP_COMPLETION_STATE_PENDING)};",
        )
        appendLine(
            "globalThis.${QuickJsExternalPluginScriptExecutor.QUICKJS_BOOTSTRAP_COMPLETION_ERROR_PROPERTY} = '';",
        )
        appendLine("(async () => {")
        appendLine("  try {")
        appendLine(
            "    const __astrbotHasBootstrapHostApi = Object.prototype.hasOwnProperty.call(globalThis, ${JSONObject.quote(QuickJsExternalPluginScriptExecutor.QUICKJS_BOOTSTRAP_HOST_API_PROPERTY)});",
        )
        appendLine("    if (__astrbotHasBootstrapHostApi) {")
        appendLine(
            "      await globalThis.${QuickJsExternalPluginScriptExecutor.QUICKJS_BOOTSTRAP_CALLABLE_PROPERTY}(globalThis.${QuickJsExternalPluginScriptExecutor.QUICKJS_BOOTSTRAP_HOST_API_PROPERTY});",
        )
        appendLine("    } else {")
        appendLine(
            "      await globalThis.${QuickJsExternalPluginScriptExecutor.QUICKJS_BOOTSTRAP_CALLABLE_PROPERTY}();",
        )
        appendLine("    }")
        appendLine(
            "    globalThis.${QuickJsExternalPluginScriptExecutor.QUICKJS_BOOTSTRAP_COMPLETION_STATE_PROPERTY} = ${JSONObject.quote(QUICKJS_BOOTSTRAP_COMPLETION_STATE_FULFILLED)};",
        )
        appendLine("  } catch (error) {")
        appendLine(
            "    globalThis.${QuickJsExternalPluginScriptExecutor.QUICKJS_BOOTSTRAP_COMPLETION_STATE_PROPERTY} = ${JSONObject.quote(QUICKJS_BOOTSTRAP_COMPLETION_STATE_REJECTED)};",
        )
        appendLine(
            "    globalThis.${QuickJsExternalPluginScriptExecutor.QUICKJS_BOOTSTRAP_COMPLETION_ERROR_PROPERTY} = String(error?.stack ?? error?.message ?? error);",
        )
        appendLine("    throw error;")
        appendLine("  }")
        appendLine("})();")
        appendLine("globalThis.${QuickJsExternalPluginScriptExecutor.QUICKJS_BOOTSTRAP_COMPLETION_STATE_PROPERTY};")
    }
}

private fun buildQuickJsBootstrapCompletionStatePollSource(): String {
    return "globalThis.${QuickJsExternalPluginScriptExecutor.QUICKJS_BOOTSTRAP_COMPLETION_STATE_PROPERTY};"
}

private class FileSystemQuickJsModuleLoader(
    private val pluginRootDirectory: String,
) : ModuleLoader() {
    override fun isBytecodeMode(): Boolean = false

    override fun getModuleBytecode(moduleName: String): ByteArray {
        throw IllegalStateException("QuickJS bytecode modules are not used for plugin v2 bootstrap.")
    }

    override fun getModuleStringCode(moduleName: String): String {
        val moduleFile = normalizeModulePath(baseName = pluginRootDirectory, moduleName = moduleName).toFile()
        check(moduleFile.isFile) {
            "Missing QuickJS bootstrap module file: ${moduleFile.absolutePath}"
        }
        return moduleFile.readText(Charsets.UTF_8)
    }

    override fun moduleNormalizeName(
        baseName: String,
        moduleName: String,
    ): String {
        return normalizeModulePath(baseName = baseName, moduleName = moduleName)
            .toString()
            .replace('\\', '/')
    }

    private fun normalizeModulePath(
        baseName: String,
        moduleName: String,
    ): Path {
        return resolveQuickJsModuleFile(
            pluginRootDirectory = pluginRootDirectory,
            baseName = baseName,
            moduleName = moduleName,
        ).toPath()
    }
}
