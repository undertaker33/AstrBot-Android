package com.astrbot.android.feature.plugin.runtime

import com.astrbot.android.model.chat.ConversationAttachment
import com.whl.quickjs.wrapper.JSArray
import com.whl.quickjs.wrapper.JSCallFunction
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

private object NoOpBootstrapCallbackHandle : PluginV2CallbackHandle {
    override fun invoke() = Unit
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
            val normalizedValue = createBridgeValue(
                runtimeHandle = runtimeHandle,
                name = name,
                value = value,
            )
            runtimeHandle.context.setProperty(runtimeHandle.globalObject, name, normalizedValue)
            if (normalizedValue is JSObject) {
                runtimeHandle.handleStore["global::$name"] = normalizedValue
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

    private fun createBridgeValue(
        runtimeHandle: QuickJsBootstrapRuntime,
        name: String,
        value: Any?,
    ): Any? {
        return when (value) {
            is PluginV2BootstrapHostApi -> createBootstrapHostApiBridge(
                runtimeHandle = runtimeHandle,
                name = name,
                hostApi = value,
            )

            else -> value
        }
    }

    private fun createBootstrapHostApiBridge(
        runtimeHandle: QuickJsBootstrapRuntime,
        name: String,
        hostApi: PluginV2BootstrapHostApi,
    ): JSObject {
        hostApi.attachSessionUnifiedOriginProvider {
            runtimeHandle.currentSessionUnifiedOrigin
        }
        val bridge = runtimeHandle.context.createNewJSObject()
        bindHostCall(bridge, "registerMessageHandler") { args ->
            val descriptor = requireObjectArg(args, 0, "registerMessageHandler")
            hostApi.registerMessageHandler(parseMessageHandlerDescriptor(descriptor, runtimeHandle)).value
        }
        bindHostCall(bridge, "registerCommandHandler") { args ->
            val descriptor = requireObjectArg(args, 0, "registerCommandHandler")
            hostApi.registerCommandHandler(parseCommandHandlerDescriptor(descriptor, runtimeHandle)).value
        }
        bindHostCall(bridge, "registerRegexHandler") { args ->
            val descriptor = requireObjectArg(args, 0, "registerRegexHandler")
            hostApi.registerRegexHandler(parseRegexHandlerDescriptor(descriptor, runtimeHandle)).value
        }
        bindHostCall(bridge, "registerLifecycleHandler") { args ->
            val descriptor = requireObjectArg(args, 0, "registerLifecycleHandler")
            hostApi.registerLifecycleHandler(parseLifecycleHandlerDescriptor(descriptor, runtimeHandle)).value
        }
        bindHostCall(bridge, "registerLlmHook") { args ->
            val descriptor = requireObjectArg(args, 0, "registerLlmHook")
            hostApi.registerLlmHook(parseLlmHookDescriptor(descriptor, runtimeHandle)).value
        }
        bindHostCall(bridge, "registerTool") { args ->
            val descriptor = requireObjectArg(args, 0, "registerTool")
            val explicitHandler = args.getOrNull(1) as? JSFunction
            hostApi.registerTool(
                descriptor = parseToolDescriptor(descriptor),
                handler = explicitHandler?.let { QuickJsPluginV2CallbackHandle("tool", it) }
                    ?: extractCallbackHandle(descriptor, runtimeHandle, "handler", "registerTool"),
            ).value
        }
        bindHostCall(bridge, "registerToolLifecycleHook") { args ->
            val descriptor = requireObjectArg(args, 0, "registerToolLifecycleHook")
            hostApi.registerToolLifecycleHook(parseToolLifecycleDescriptor(descriptor, runtimeHandle)).value
        }
        bindHostCall(bridge, "onPluginLoaded") { args ->
            val descriptor = requireObjectArg(args, 0, "onPluginLoaded")
            hostApi.registerLifecycleHandler(
                parseLifecycleAliasDescriptor(
                    descriptor = descriptor,
                    hook = PluginLifecycleHookSurface.OnPluginLoaded.wireValue,
                    runtimeHandle = runtimeHandle,
                ),
            ).value
        }
        bindHostCall(bridge, "onPluginUnloaded") { args ->
            val descriptor = requireObjectArg(args, 0, "onPluginUnloaded")
            hostApi.registerLifecycleHandler(
                parseLifecycleAliasDescriptor(
                    descriptor = descriptor,
                    hook = PluginLifecycleHookSurface.OnPluginUnloaded.wireValue,
                    runtimeHandle = runtimeHandle,
                ),
            ).value
        }
        bindHostCall(bridge, "onPluginError") { args ->
            val descriptor = requireObjectArg(args, 0, "onPluginError")
            hostApi.registerLifecycleHandler(
                parseLifecycleAliasDescriptor(
                    descriptor = descriptor,
                    hook = PluginLifecycleHookSurface.OnPluginError.wireValue,
                    runtimeHandle = runtimeHandle,
                ),
            ).value
        }
        bindHostCall(bridge, "log") { args ->
            val level = args.getOrNull(0)?.toString().orEmpty()
            val message = args.getOrNull(1)?.toString().orEmpty()
            val metadata = parseStringMap(args.getOrNull(2))
            hostApi.log(level, message, metadata)
            null
        }
        bindHostCall(bridge, "getPluginMetadata") {
            createJsObject(
                runtimeHandle,
                linkedMapOf(
                    "pluginId" to hostApi.getPluginMetadata().pluginId,
                    "installedVersion" to hostApi.getPluginMetadata().installedVersion,
                    "runtimeKind" to hostApi.getPluginMetadata().runtimeKind,
                    "runtimeApiVersion" to hostApi.getPluginMetadata().runtimeApiVersion,
                    "runtimeBootstrap" to hostApi.getPluginMetadata().runtimeBootstrap,
                ),
            )
        }
        val settingsCallback = JSCallFunction {
            createJsObject(runtimeHandle, hostApi.getSettings())
        }
        bridge.setProperty("getSettings", settingsCallback)
        bridge.setProperty("getPluginSettings", settingsCallback)
        bridge.setProperty("readSettings", settingsCallback)
        bridge.setProperty("getConfig", settingsCallback)
        bridge.setProperty(
            "storage",
            createStorageBridge(
                runtimeHandle = runtimeHandle,
                hostApi = hostApi,
            ),
        )
        runtimeHandle.handleStore["global::$name"] = bridge
        return bridge
    }

    private fun createStorageBridge(
        runtimeHandle: QuickJsBootstrapRuntime,
        hostApi: PluginV2BootstrapHostApi,
    ): JSObject {
        val storageBridge = runtimeHandle.context.createNewJSObject()
        storageBridge.setProperty(
            "plugin",
            createStorageScopeBridge(
                runtimeHandle = runtimeHandle,
                getValue = { key, defaultValue -> hostApi.pluginStorageGet(key, defaultValue) },
                setValue = { key, value -> hostApi.pluginStorageSet(key, value) },
                removeValue = { key -> hostApi.pluginStorageRemove(key) },
                listKeys = { prefix -> hostApi.pluginStorageKeys(prefix) },
                clearScope = { prefix -> hostApi.pluginStorageClear(prefix) },
            ),
        )
        storageBridge.setProperty(
            "session",
            createStorageScopeBridge(
                runtimeHandle = runtimeHandle,
                getValue = { key, defaultValue -> hostApi.sessionStorageGet(key, defaultValue) },
                setValue = { key, value -> hostApi.sessionStorageSet(key, value) },
                removeValue = { key -> hostApi.sessionStorageRemove(key) },
                listKeys = { prefix -> hostApi.sessionStorageKeys(prefix) },
                clearScope = { prefix -> hostApi.sessionStorageClear(prefix) },
            ),
        )
        runtimeHandle.handleStore["storage::${runtimeHandle.handleStore.size}"] = storageBridge
        return storageBridge
    }

    private fun createStorageScopeBridge(
        runtimeHandle: QuickJsBootstrapRuntime,
        getValue: (String, Any?) -> Any?,
        setValue: (String, Any?) -> Boolean,
        removeValue: (String) -> Boolean,
        listKeys: (String) -> List<String>,
        clearScope: (String) -> Boolean,
    ): JSObject {
        val scopeBridge = runtimeHandle.context.createNewJSObject()
        scopeBridge.setProperty(
            "get",
            JSCallFunction { args ->
                createJsValue(
                    runtimeHandle,
                    getValue(
                        args.getOrNull(0)?.toString().orEmpty(),
                        coerceJsValue(args.getOrNull(1)),
                    ),
                )
            },
        )
        scopeBridge.setProperty(
            "set",
            JSCallFunction { args ->
                setValue(
                    args.getOrNull(0)?.toString().orEmpty(),
                    coerceJsValue(args.getOrNull(1)),
                )
            },
        )
        scopeBridge.setProperty(
            "remove",
            JSCallFunction { args ->
                removeValue(args.getOrNull(0)?.toString().orEmpty())
            },
        )
        scopeBridge.setProperty(
            "keys",
            JSCallFunction { args ->
                createJsArray(
                    runtimeHandle,
                    listKeys(args.getOrNull(0)?.toString().orEmpty()),
                )
            },
        )
        scopeBridge.setProperty(
            "clear",
            JSCallFunction { args ->
                clearScope(args.getOrNull(0)?.toString().orEmpty())
            },
        )
        runtimeHandle.handleStore["storage-scope::${runtimeHandle.handleStore.size}"] = scopeBridge
        return scopeBridge
    }

    private fun bindHostCall(
        bridge: JSObject,
        methodName: String,
        block: (Array<out Any?>) -> Any?,
    ) {
        bridge.setProperty(
            methodName,
            JSCallFunction { args ->
                block(args)
            },
        )
    }

    private fun parseMessageHandlerDescriptor(
        descriptor: Any,
        runtimeHandle: QuickJsBootstrapRuntime,
    ): MessageHandlerRegistrationInput {
        return MessageHandlerRegistrationInput(
            base = parseBaseDescriptor(descriptor),
            handler = extractCallbackHandle(descriptor, runtimeHandle, "handler", "registerMessageHandler"),
        )
    }

    private fun parseCommandHandlerDescriptor(
        descriptor: Any,
        runtimeHandle: QuickJsBootstrapRuntime,
    ): CommandHandlerRegistrationInput {
        return CommandHandlerRegistrationInput(
            base = parseBaseDescriptor(descriptor),
            command = requireStringProperty(descriptor, "registerCommandHandler", "command"),
            aliases = parseStringList(propertyValue(descriptor, "aliases")),
            groupPath = parseStringList(propertyValue(descriptor, "groupPath")),
            handler = extractCallbackHandle(descriptor, runtimeHandle, "handler", "registerCommandHandler"),
        )
    }

    private fun parseRegexHandlerDescriptor(
        descriptor: Any,
        runtimeHandle: QuickJsBootstrapRuntime,
    ): RegexHandlerRegistrationInput {
        return RegexHandlerRegistrationInput(
            base = parseBaseDescriptor(descriptor),
            pattern = requireStringProperty(descriptor, "registerRegexHandler", "pattern"),
            flags = parseStringList(propertyValue(descriptor, "flags")).toSet(),
            handler = extractCallbackHandle(descriptor, runtimeHandle, "handler", "registerRegexHandler"),
        )
    }

    private fun parseLifecycleHandlerDescriptor(
        descriptor: Any,
        runtimeHandle: QuickJsBootstrapRuntime,
    ): LifecycleHandlerRegistrationInput {
        return LifecycleHandlerRegistrationInput(
            registrationKey = parseRegistrationKey(descriptor),
            hook = requireStringProperty(descriptor, "registerLifecycleHandler", "hook"),
            priority = parsePriority(descriptor),
            metadata = BootstrapRegistrationMetadata(parseStringMap(propertyValue(descriptor, "metadata"))),
            handler = extractCallbackHandle(descriptor, runtimeHandle, "handler", "registerLifecycleHandler"),
        )
    }

    private fun parseLifecycleAliasDescriptor(
        descriptor: Any,
        hook: String,
        runtimeHandle: QuickJsBootstrapRuntime,
    ): LifecycleHandlerRegistrationInput {
        return LifecycleHandlerRegistrationInput(
            registrationKey = parseRegistrationKey(descriptor),
            hook = hook,
            priority = parsePriority(descriptor),
            metadata = BootstrapRegistrationMetadata(parseStringMap(propertyValue(descriptor, "metadata"))),
            handler = extractCallbackHandle(descriptor, runtimeHandle, "handler", hook),
        )
    }

    private fun parseLlmHookDescriptor(
        descriptor: Any,
        runtimeHandle: QuickJsBootstrapRuntime,
    ): LlmHookRegistrationInput {
        return LlmHookRegistrationInput(
            registrationKey = parseRegistrationKey(descriptor),
            hook = requireStringProperty(descriptor, "registerLlmHook", "hook"),
            priority = parsePriority(descriptor),
            metadata = BootstrapRegistrationMetadata(parseStringMap(propertyValue(descriptor, "metadata"))),
            handler = extractCallbackHandle(descriptor, runtimeHandle, "handler", "registerLlmHook"),
        )
    }

    private fun parseToolDescriptor(
        descriptor: Any,
    ): PluginToolDescriptor {
        val rawSourceKind = propertyValue(descriptor, "sourceKind")?.toString().orEmpty()
        val sourceKind = PluginToolSourceKind.entries.firstOrNull { entry ->
            entry.name.equals(rawSourceKind, ignoreCase = true)
        } ?: PluginToolSourceKind.PLUGIN_V2
        val rawVisibility = propertyValue(descriptor, "visibility")?.toString().orEmpty()
        val visibility = PluginToolVisibility.entries.firstOrNull { entry ->
            entry.name.equals(rawVisibility, ignoreCase = true)
        } ?: PluginToolVisibility.LLM_VISIBLE
        return PluginToolDescriptor(
            pluginId = propertyValue(descriptor, "pluginId")?.toString()?.trim().orEmpty().ifBlank {
                pluginId
            },
            name = requireStringProperty(descriptor, "registerTool", "name"),
            description = propertyValue(descriptor, "description")?.toString().orEmpty(),
            visibility = visibility,
            sourceKind = sourceKind,
            inputSchema = parseJsonLikeMap(propertyValue(descriptor, "inputSchema")),
            metadata = parseJsonLikeMap(propertyValue(descriptor, "metadata")).takeIf(Map<String, Any?>::isNotEmpty),
        )
    }

    private fun parseToolLifecycleDescriptor(
        descriptor: Any,
        runtimeHandle: QuickJsBootstrapRuntime,
    ): ToolLifecycleHookRegistrationInput {
        return ToolLifecycleHookRegistrationInput(
            registrationKey = parseRegistrationKey(descriptor),
            hook = requireStringProperty(descriptor, "registerToolLifecycleHook", "hook"),
            priority = parsePriority(descriptor),
            metadata = BootstrapRegistrationMetadata(parseStringMap(propertyValue(descriptor, "metadata"))),
            handler = extractCallbackHandle(descriptor, runtimeHandle, "handler", "registerToolLifecycleHook"),
        )
    }

    private fun parseBaseDescriptor(
        descriptor: Any,
    ): BaseHandlerRegistrationInput {
        val base = propertyValue(descriptor, "base")
        return BaseHandlerRegistrationInput(
            registrationKey = parseRegistrationKey(base ?: descriptor),
            declaredFilters = parseDeclaredFilters(
                propertyValue(base ?: descriptor, "declaredFilters")
                    ?: propertyValue(base ?: descriptor, "filters"),
            ),
            priority = parsePriority(base ?: descriptor),
            metadata = BootstrapRegistrationMetadata(parseStringMap(propertyValue(base ?: descriptor, "metadata"))),
        )
    }

    private fun parseRegistrationKey(
        descriptor: Any,
    ): String? {
        return propertyValue(descriptor, "registrationKey", "key")
            ?.toString()
            ?.trim()
            ?.takeIf(String::isNotBlank)
    }

    private fun parsePriority(
        descriptor: Any,
    ): Int {
        return when (val value = propertyValue(descriptor, "priority")) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull() ?: 0
            else -> 0
        }
    }

    private fun parseDeclaredFilters(value: Any?): List<BootstrapFilterDescriptor> {
        return parseList(value).mapNotNull { item ->
            when (item) {
                is String -> parseStringFilter(item)
                is JSObject, is Map<*, *> -> parseObjectFilter(item)
                else -> null
            }
        }
    }

    private fun parseStringFilter(value: String): BootstrapFilterDescriptor? {
        val normalized = value.trim()
        if (normalized.isBlank()) {
            return null
        }
        return when {
            normalized.startsWith("event_message_type:", ignoreCase = true) -> {
                BootstrapFilterDescriptor.message(normalized)
            }

            normalized.startsWith("platform_adapter_type:", ignoreCase = true) ||
                normalized.startsWith("custom_filter:", ignoreCase = true) -> {
                BootstrapFilterDescriptor.command(normalized)
            }

            normalized.startsWith("permission_type:", ignoreCase = true) -> {
                BootstrapFilterDescriptor.regex(normalized)
            }

            else -> BootstrapFilterDescriptor.message(normalized)
        }
    }

    private fun parseObjectFilter(value: Any): BootstrapFilterDescriptor? {
        val kind = propertyValue(value, "kind", "type")?.toString()?.trim().orEmpty()
        val rawValue = propertyValue(value, "value")?.toString()?.trim().orEmpty()
        if (rawValue.isBlank()) {
            return null
        }
        return when {
            kind.equals("message", ignoreCase = true) -> BootstrapFilterDescriptor.message(rawValue)
            kind.equals("command", ignoreCase = true) -> BootstrapFilterDescriptor.command(rawValue)
            kind.equals("regex", ignoreCase = true) -> BootstrapFilterDescriptor.regex(rawValue)
            else -> parseStringFilter(rawValue)
        }
    }

    private fun requireObjectArg(
        args: Array<out Any?>,
        index: Int,
        action: String,
    ): Any {
        return args.getOrNull(index)?.takeIf { candidate ->
            candidate is JSObject || candidate is Map<*, *>
        } ?: throw IllegalArgumentException("$action requires an object descriptor.")
    }

    private fun requireStringProperty(
        descriptor: Any,
        action: String,
        propertyName: String,
    ): String {
        return propertyValue(descriptor, propertyName)
            ?.toString()
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("$action requires non-blank $propertyName.")
    }

    private fun propertyValue(
        source: Any?,
        vararg names: String,
    ): Any? {
        names.forEach { name ->
            when (source) {
                is JSObject -> source.getProperty(name)?.let { return it }
                is Map<*, *> -> if (source.containsKey(name)) return source[name]
            }
        }
        return null
    }

    private fun parseStringMap(value: Any?): Map<String, String> {
        val rawMap = parseJsonLikeMap(value)
        return rawMap.entries.associate { (key, rawValue) ->
            key to rawValue?.toString().orEmpty()
        }
    }

    private fun parseJsonLikeMap(value: Any?): Map<String, Any?> {
        return when (value) {
            null -> emptyMap()
            is JSObject -> value.toMap().mapValues { (_, item) -> coerceJsValue(item) }
            is Map<*, *> -> value.entries.associate { (key, item) ->
                key.toString() to coerceJsValue(item)
            }

            else -> emptyMap()
        }
    }

    private fun parseStringList(value: Any?): List<String> {
        return parseList(value)
            .mapNotNull { item ->
                item?.toString()?.trim()?.takeIf(String::isNotBlank)
            }
    }

    private fun parseList(value: Any?): List<Any?> {
        return when (value) {
            null -> emptyList()
            is JSArray -> value.toArray().map(::coerceJsValue)
            is List<*> -> value.map(::coerceJsValue)
            is Array<*> -> value.map(::coerceJsValue)
            is JSObject -> value.toArray().map(::coerceJsValue)
            else -> emptyList()
        }
    }

    private fun coerceJsValue(value: Any?): Any? {
        return when (value) {
            null,
            is String,
            is Boolean,
            is Int,
            is Long,
            is Double,
            is ByteArray,
            -> value

            is Float -> value.toDouble()
            is Number -> value.toDouble()
            is JSArray -> value.toArray().map(::coerceJsValue)
            is JSObject -> value.toMap().mapValues { (_, item) -> coerceJsValue(item) }
            is Map<*, *> -> value.entries.associate { (key, item) -> key.toString() to coerceJsValue(item) }
            is List<*> -> value.map(::coerceJsValue)
            else -> value.toString()
        }
    }

    private fun extractCallbackHandle(
        descriptor: Any,
        runtimeHandle: QuickJsBootstrapRuntime,
        propertyName: String,
        label: String,
    ): PluginV2CallbackHandle {
        val jsFunction = propertyValue(descriptor, propertyName) as? JSFunction
        return if (jsFunction != null) {
            QuickJsPluginV2CallbackHandle(label = label, function = jsFunction).also { callbackHandle ->
                runtimeHandle.handleStore["callback::$label::${runtimeHandle.handleStore.size}"] = jsFunction
                callbackHandle.hold()
            }
        } else {
            NoOpBootstrapCallbackHandle
        }
    }

    private fun createJsObject(
        runtimeHandle: QuickJsBootstrapRuntime,
        values: Map<String, Any?>,
    ): JSObject {
        val jsObject = runtimeHandle.context.createNewJSObject()
        values.forEach { (key, value) ->
            setJsProperty(
                runtimeHandle = runtimeHandle,
                target = jsObject,
                key = key,
                value = value,
            )
        }
        return jsObject
    }

    private fun createJsArray(
        runtimeHandle: QuickJsBootstrapRuntime,
        values: List<Any?>,
    ): JSArray {
        val jsArray = runtimeHandle.context.createNewJSArray()
        values.forEachIndexed { index, value ->
            runtimeHandle.context.set(
                jsArray,
                createJsValue(runtimeHandle, value),
                index,
            )
        }
        return jsArray
    }

    private fun createJsValue(
        runtimeHandle: QuickJsBootstrapRuntime,
        value: Any?,
    ): Any? {
        return when (value) {
            null,
            is String,
            is Boolean,
            is Int,
            is Long,
            is Double,
            is ByteArray,
            -> value

            is JSObject -> value
            is JSCallFunction -> value
            is Float -> value.toDouble()
            is Number -> value.toDouble()
            is Map<*, *> -> createJsObject(
                runtimeHandle,
                value.entries.associate { (key, item) -> key.toString() to item },
            )

            is List<*> -> createJsArray(runtimeHandle, value)
            is PluginMessageEvent -> createMessageEventBridge(runtimeHandle, value)
            is PluginCommandEvent -> createCommandEventBridge(runtimeHandle, value)
            is PluginRegexEvent -> createRegexEventBridge(runtimeHandle, value)
            is PluginV2LlmWaitingPayload -> createLlmWaitingPayloadBridge(runtimeHandle, value)
            is PluginV2LlmRequestPayload -> createLlmRequestPayloadBridge(runtimeHandle, value)
            is PluginV2LlmResponsePayload -> createLlmResponsePayloadBridge(runtimeHandle, value)
            is PluginV2LlmResultDecoratingPayload -> createLlmResultDecoratingPayloadBridge(runtimeHandle, value)
            is PluginV2LlmAfterSentPayload -> createLlmAfterSentPayloadBridge(runtimeHandle, value)
            is PluginLifecycleMetadata -> createJsObject(
                runtimeHandle,
                linkedMapOf(
                    "pluginName" to value.pluginName,
                    "pluginVersion" to value.pluginVersion,
                ),
            )

            is PluginErrorHookArgs -> createJsObject(
                runtimeHandle,
                linkedMapOf(
                    "event" to value.event,
                    "plugin_name" to value.plugin_name,
                    "handler_name" to value.handler_name,
                    "error" to linkedMapOf(
                        "message" to (value.error.message ?: ""),
                        "type" to value.error.javaClass.simpleName,
                    ),
                    "traceback_text" to value.traceback_text,
                ),
            )

            is PluginV2CustomFilterRequest -> createJsObject(
                runtimeHandle,
                linkedMapOf(
                    "eventView" to linkedMapOf(
                        "stage" to value.eventView.stage,
                        "eventId" to value.eventView.eventId,
                        "platformAdapterType" to value.eventView.platformAdapterType,
                        "messageType" to value.eventView.messageType,
                        "conversationId" to value.eventView.conversationId,
                        "senderId" to value.eventView.senderId,
                        "workingText" to value.eventView.workingText,
                        "extrasSnapshot" to value.eventView.extrasSnapshot,
                        "commandPath" to value.eventView.commandPath,
                        "matchedAlias" to value.eventView.matchedAlias,
                        "patternKey" to value.eventView.patternKey,
                        "matchedText" to value.eventView.matchedText,
                    ),
                    "pluginContextView" to linkedMapOf(
                        "pluginId" to value.pluginContextView.pluginId,
                        "pluginVersion" to value.pluginContextView.pluginVersion,
                        "runtimeKind" to value.pluginContextView.runtimeKind,
                        "runtimeApiVersion" to value.pluginContextView.runtimeApiVersion,
                        "declaredPermissionIds" to value.pluginContextView.declaredPermissionIds,
                        "grantedPermissionIds" to value.pluginContextView.grantedPermissionIds,
                        "sourceType" to value.pluginContextView.sourceType,
                    ),
                    "filterArgs" to value.filterArgs,
                ),
            )

            else -> createJsObject(
                runtimeHandle,
                linkedMapOf("value" to value.toString()),
            )
        }
    }

    private fun setJsProperty(
        runtimeHandle: QuickJsBootstrapRuntime,
        target: JSObject,
        key: String,
        value: Any?,
    ) {
        when (val jsValue = createJsValue(runtimeHandle, value)) {
            null -> runtimeHandle.context.setProperty(target, key, null)
            is String -> target.setProperty(key, jsValue)
            is Boolean -> target.setProperty(key, jsValue)
            is Int -> target.setProperty(key, jsValue)
            is Long -> target.setProperty(key, jsValue)
            is Double -> target.setProperty(key, jsValue)
            is ByteArray -> target.setProperty(key, jsValue)
            is JSCallFunction -> target.setProperty(key, jsValue)
            is JSObject -> target.setProperty(key, jsValue)
            else -> runtimeHandle.context.setProperty(target, key, jsValue)
        }
    }

    private fun createMessageEventBridge(
        runtimeHandle: QuickJsBootstrapRuntime,
        event: PluginMessageEvent,
    ): JSObject {
        val jsObject = createJsObject(
            runtimeHandle,
            linkedMapOf(
                "eventId" to event.eventId,
                "platformAdapterType" to event.platformAdapterType,
                "messageType" to event.messageType.wireValue,
                "conversationId" to event.conversationId,
                "senderId" to event.senderId,
                "timestampEpochMillis" to event.timestampEpochMillis,
                "rawText" to event.rawText,
                "workingText" to event.workingText,
                "normalizedMentions" to event.normalizedMentions,
                "extras" to event.extras,
                "stage" to event.stage.name,
            ),
        )
        jsObject.setProperty("stopPropagation", JSCallFunction {
            event.stopPropagation()
            true
        })
        return jsObject
    }

    private fun createCommandEventBridge(
        runtimeHandle: QuickJsBootstrapRuntime,
        event: PluginCommandEvent,
    ): JSObject {
        val jsObject = createJsObject(
            runtimeHandle,
            linkedMapOf(
                "eventId" to event.eventId,
                "platformAdapterType" to event.platformAdapterType,
                "messageType" to event.messageType.wireValue,
                "conversationId" to event.conversationId,
                "senderId" to event.senderId,
                "timestampEpochMillis" to event.timestampEpochMillis,
                "rawText" to event.rawText,
                "workingText" to event.workingText,
                "normalizedMentions" to event.normalizedMentions,
                "extras" to event.extras,
                "stage" to event.stage.name,
                "commandPath" to event.commandPath,
                "args" to event.args,
                "matchedAlias" to event.matchedAlias,
                "remainingText" to event.remainingText,
                "argText" to event.remainingText,
                "invocationText" to event.invocationText,
                "triggerMetadata" to linkedMapOf(
                    "args" to event.args,
                    "matchedAlias" to event.matchedAlias,
                    "commandPath" to event.commandPath,
                ),
            ),
        )
        jsObject.setProperty("stopPropagation", JSCallFunction {
            event.stopPropagation()
            true
        })
        val replyHandler = JSCallFunction { args ->
            event.replyResult(coerceJsValue(args.getOrNull(0)))
        }
        jsObject.setProperty("replyResult", replyHandler)
        jsObject.setProperty("sendResult", replyHandler)
        jsObject.setProperty("reply", replyHandler)
        jsObject.setProperty("respond", replyHandler)
        val replyTextHandler = JSCallFunction { args ->
            event.replyText(args.getOrNull(0)?.toString().orEmpty())
        }
        jsObject.setProperty("replyText", replyTextHandler)
        jsObject.setProperty("sendText", replyTextHandler)
        jsObject.setProperty("respondText", replyTextHandler)
        return jsObject
    }

    private fun createRegexEventBridge(
        runtimeHandle: QuickJsBootstrapRuntime,
        event: PluginRegexEvent,
    ): JSObject {
        val jsObject = createJsObject(
            runtimeHandle,
            linkedMapOf(
                "eventId" to event.eventId,
                "platformAdapterType" to event.platformAdapterType,
                "messageType" to event.messageType.wireValue,
                "conversationId" to event.conversationId,
                "senderId" to event.senderId,
                "timestampEpochMillis" to event.timestampEpochMillis,
                "rawText" to event.rawText,
                "workingText" to event.workingText,
                "normalizedMentions" to event.normalizedMentions,
                "extras" to event.extras,
                "stage" to event.stage.name,
                "patternKey" to event.patternKey,
                "matchedText" to event.matchedText,
                "groups" to event.groups,
                "namedGroups" to event.namedGroups,
            ),
        )
        jsObject.setProperty("stopPropagation", JSCallFunction {
            event.stopPropagation()
            true
        })
        return jsObject
    }

    private fun createLlmWaitingPayloadBridge(
        runtimeHandle: QuickJsBootstrapRuntime,
        payload: PluginV2LlmWaitingPayload,
    ): JSObject {
        return createJsObject(
            runtimeHandle,
            linkedMapOf(
                "eventId" to payload.eventId,
                "platformAdapterType" to payload.platformAdapterType,
                "messageType" to payload.messageType,
                "conversationId" to payload.conversationId,
                "senderId" to payload.senderId,
                "timestampEpochMillis" to payload.timestampEpochMillis,
                "rawText" to payload.rawText,
                "workingText" to payload.workingText,
                "rawMentions" to payload.rawMentions,
                "normalizedMentions" to payload.normalizedMentions,
                "extras" to payload.extras,
                "event" to linkedMapOf(
                    "eventId" to payload.eventId,
                    "platformAdapterType" to payload.platformAdapterType,
                    "messageType" to payload.messageType,
                    "conversationId" to payload.conversationId,
                    "senderId" to payload.senderId,
                    "timestampEpochMillis" to payload.timestampEpochMillis,
                    "rawText" to payload.rawText,
                    "workingText" to payload.workingText,
                    "rawMentions" to payload.rawMentions,
                    "normalizedMentions" to payload.normalizedMentions,
                    "extras" to payload.extras,
                ),
            ),
        )
    }

    private fun createLlmRequestPayloadBridge(
        runtimeHandle: QuickJsBootstrapRuntime,
        payload: PluginV2LlmRequestPayload,
    ): JSObject {
        return createJsObject(
            runtimeHandle,
            linkedMapOf(
                "event" to payload.event,
                "request" to createPluginProviderRequestBridge(runtimeHandle, payload.request),
            ),
        )
    }

    private fun createLlmResponsePayloadBridge(
        runtimeHandle: QuickJsBootstrapRuntime,
        payload: PluginV2LlmResponsePayload,
    ): JSObject {
        return createJsObject(
            runtimeHandle,
            linkedMapOf(
                "event" to payload.event,
                "response" to createPluginLlmResponseBridge(runtimeHandle, payload.response),
            ),
        )
    }

    private fun createLlmResultDecoratingPayloadBridge(
        runtimeHandle: QuickJsBootstrapRuntime,
        payload: PluginV2LlmResultDecoratingPayload,
    ): JSObject {
        return createJsObject(
            runtimeHandle,
            linkedMapOf(
                "event" to payload.event,
                "result" to createPluginMessageEventResultBridge(runtimeHandle, payload.result),
            ),
        )
    }

    private fun createLlmAfterSentPayloadBridge(
        runtimeHandle: QuickJsBootstrapRuntime,
        payload: PluginV2LlmAfterSentPayload,
    ): JSObject {
        return createJsObject(
            runtimeHandle,
            linkedMapOf(
                "event" to payload.event,
                "view" to createPluginAfterSentViewBridge(
                    runtimeHandle,
                    payload.view,
                    payload.followupSender,
                ),
            ),
        )
    }

    private fun createPluginProviderRequestBridge(
        runtimeHandle: QuickJsBootstrapRuntime,
        request: PluginProviderRequest,
    ): JSObject {
        val jsObject = createJsObject(
            runtimeHandle,
            linkedMapOf(
                "requestId" to request.requestId,
                "availableProviderIds" to request.availableProviderIds,
                "availableModelIdsByProvider" to request.availableModelIdsByProvider,
                "conversationId" to request.conversationId,
                "messageIds" to request.messageIds,
                "llmInputSnapshot" to request.llmInputSnapshot,
                "selectedProviderId" to request.selectedProviderId,
                "selectedModelId" to request.selectedModelId,
                "systemPrompt" to request.systemPrompt,
                "messages" to request.messages.map { message ->
                    linkedMapOf(
                        "role" to message.role.wireValue,
                        "parts" to message.parts.map { part ->
                            when (part) {
                                is PluginProviderMessagePartDto.TextPart -> linkedMapOf(
                                    "partType" to "text",
                                    "text" to part.text,
                                )

                                is PluginProviderMessagePartDto.MediaRefPart -> linkedMapOf(
                                    "partType" to "media_ref",
                                    "uri" to part.uri,
                                    "mimeType" to part.mimeType,
                                )
                            }
                        },
                        "name" to message.name,
                        "metadata" to message.metadata,
                    )
                },
                "temperature" to request.temperature,
                "topP" to request.topP,
                "maxTokens" to request.maxTokens,
                "streamingEnabled" to request.streamingEnabled,
                "metadata" to request.metadata,
            ),
        )
        jsObject.setProperty("replaceSystemPrompt", JSCallFunction { args ->
            request.systemPrompt = args.getOrNull(0)?.toString()
            true
        })
        jsObject.setProperty("appendSystemPrompt", JSCallFunction { args ->
            val appendix = args.getOrNull(0)?.toString().orEmpty()
            if (appendix.isNotBlank()) {
                request.systemPrompt = buildString {
                    append(request.systemPrompt.orEmpty())
                    if (isNotEmpty()) {
                        append("\n\n")
                    }
                    append(appendix)
                }
            }
            true
        })
        return jsObject
    }

    private fun createPluginLlmResponseBridge(
        runtimeHandle: QuickJsBootstrapRuntime,
        response: PluginLlmResponse,
    ): JSObject {
        return createJsObject(
            runtimeHandle,
            linkedMapOf(
                "requestId" to response.requestId,
                "providerId" to response.providerId,
                "modelId" to response.modelId,
                "usage" to response.usage?.let { usage ->
                    linkedMapOf(
                        "promptTokens" to usage.promptTokens,
                        "completionTokens" to usage.completionTokens,
                        "totalTokens" to usage.totalTokens,
                        "inputCostMicros" to usage.inputCostMicros,
                        "outputCostMicros" to usage.outputCostMicros,
                        "currencyCode" to usage.normalizedCurrencyCode,
                    )
                },
                "finishReason" to response.finishReason,
                "text" to response.text,
                "markdown" to response.markdown,
                "toolCalls" to response.toolCalls.map { toolCall ->
                    linkedMapOf(
                        "toolName" to toolCall.normalizedToolName,
                        "arguments" to toolCall.normalizedArguments,
                        "metadata" to toolCall.normalizedMetadata,
                    )
                },
                "metadata" to response.metadata,
            ),
        )
    }

    private fun createPluginMessageEventResultBridge(
        runtimeHandle: QuickJsBootstrapRuntime,
        result: PluginMessageEventResult,
    ): JSObject {
        val jsObject = createJsObject(
            runtimeHandle,
            linkedMapOf(
                "requestId" to result.requestId,
                "conversationId" to result.conversationId,
                "text" to result.text,
                "markdown" to result.markdown,
                "attachments" to result.attachments.map { attachment ->
                    linkedMapOf(
                        "uri" to attachment.uri,
                        "mimeType" to attachment.mimeType,
                    )
                },
                "attachmentMutationIntent" to result.attachmentMutationIntent.name,
                "shouldSend" to result.shouldSend,
                "isStopped" to result.isStopped,
            ),
        )
        jsObject.setProperty("replaceText", JSCallFunction { args ->
            result.replaceText(args.getOrNull(0)?.toString().orEmpty())
            true
        })
        jsObject.setProperty("appendText", JSCallFunction { args ->
            result.appendText(args.getOrNull(0)?.toString().orEmpty())
            true
        })
        jsObject.setProperty("clearText", JSCallFunction {
            result.clearText()
            true
        })
        jsObject.setProperty("replaceAttachments", JSCallFunction { args ->
            val attachments = parsePluginMessageEventResultAttachments(args.getOrNull(0))
            result.replaceAttachments(attachments)
            true
        })
        jsObject.setProperty("appendAttachment", JSCallFunction { args ->
            parsePluginMessageEventResultAttachment(args.getOrNull(0))?.let(result::appendAttachment)
            true
        })
        jsObject.setProperty("clearAttachments", JSCallFunction {
            result.clearAttachments()
            true
        })
        jsObject.setProperty("setShouldSend", JSCallFunction { args ->
            result.setShouldSend(args.getOrNull(0) as? Boolean ?: false)
            true
        })
        jsObject.setProperty("stop", JSCallFunction {
            result.stop()
            true
        })
        return jsObject
    }

    private fun createPluginAfterSentViewBridge(
        runtimeHandle: QuickJsBootstrapRuntime,
        view: PluginV2AfterSentView,
        followupSender: PluginV2FollowupSender? = null,
    ): JSObject {
        val jsObject = createJsObject(
            runtimeHandle,
            linkedMapOf(
                "requestId" to view.requestId,
                "conversationId" to view.conversationId,
                "sendAttemptId" to view.sendAttemptId,
                "platformAdapterType" to view.platformAdapterType,
                "platformInstanceKey" to view.platformInstanceKey,
                "sentAtEpochMs" to view.sentAtEpochMs,
                "deliveryStatus" to view.deliveryStatus.wireValue,
                "receiptIds" to view.receiptIds,
                "deliveredEntries" to view.deliveredEntries.map { entry ->
                    linkedMapOf(
                        "entryId" to entry.entryId,
                        "entryType" to entry.entryType,
                        "textPreview" to entry.textPreview,
                        "attachmentCount" to entry.attachmentCount,
                    )
                },
                "usage" to view.usage?.let { usage ->
                    linkedMapOf(
                        "promptTokens" to usage.promptTokens,
                        "completionTokens" to usage.completionTokens,
                        "totalTokens" to usage.totalTokens,
                        "inputCostMicros" to usage.inputCostMicros,
                        "outputCostMicros" to usage.outputCostMicros,
                        "currencyCode" to usage.normalizedCurrencyCode,
                    )
                },
                "deliveredEntryCount" to view.deliveredEntryCount,
                "canSendFollowup" to (followupSender != null),
            ),
        )
        if (followupSender != null) {
            jsObject.setProperty("sendFollowup", JSCallFunction { args ->
                val text = args.getOrNull(0)?.toString().orEmpty()
                val attachments = parsePluginMessageEventResultAttachments(args.getOrNull(1))
                    .map { att ->
                        ConversationAttachment(
                            id = att.uri,
                            type = att.mimeType.substringBefore("/").ifBlank { "image" },
                            mimeType = att.mimeType,
                            remoteUrl = att.uri,
                        )
                    }
                val result = followupSender.send(text, attachments)
                createJsObject(
                    runtimeHandle,
                    linkedMapOf(
                        "success" to result.success,
                        "receiptIds" to result.receiptIds,
                        "errorSummary" to result.errorSummary,
                    ),
                )
            })
        }
        return jsObject
    }

    private fun parsePluginMessageEventResultAttachments(
        value: Any?,
    ): List<PluginMessageEventResult.Attachment> {
        return parseList(value).mapNotNull(::parsePluginMessageEventResultAttachment)
    }

    private fun parsePluginMessageEventResultAttachment(
        value: Any?,
    ): PluginMessageEventResult.Attachment? {
        val uri = propertyValue(value, "uri", "source", "path", "assetPath")?.toString()?.trim().orEmpty()
        if (uri.isBlank()) {
            return null
        }
        val resolvedUri = resolvePluginAssetUri(uri)
        return PluginMessageEventResult.Attachment(
            uri = resolvedUri,
            mimeType = propertyValue(value, "mimeType")?.toString().orEmpty(),
        )
    }

    private fun resolvePluginAssetUri(uri: String): String {
        if (uri.startsWith("http://", ignoreCase = true) ||
            uri.startsWith("https://", ignoreCase = true) ||
            uri.startsWith("file://", ignoreCase = true) ||
            uri.startsWith("base64://", ignoreCase = true) ||
            uri.startsWith("content://", ignoreCase = true) ||
            uri.startsWith("plugin://", ignoreCase = true)
        ) {
            return uri
        }
        val file = File(uri)
        if (file.isAbsolute) {
            return uri
        }
        val resolved = File(pluginRootDirectory, uri)
        return if (resolved.exists()) resolved.absolutePath else uri
    }

    private inner class QuickJsPluginV2CallbackHandle(
        private val label: String,
        private val function: JSFunction,
    ) : PluginV2EventAwareCallbackHandle, PluginV2CustomFilterAwareCallbackHandle {
        fun hold() {
            function.hold()
        }

        override fun invoke() {
            callFunction(label)
        }

        override suspend fun handleEvent(event: PluginErrorEventPayload) {
            callFunction(label, event)
        }

        override suspend fun evaluateCustomFilter(request: PluginV2CustomFilterRequest): Boolean {
            return when (val result = callFunction("$label.customFilter", request)) {
                is Boolean -> result
                is Number -> result.toInt() != 0
                is String -> result.equals("true", ignoreCase = true)
                else -> false
            }
        }

        private fun callFunction(
            actionName: String,
            vararg args: Any?,
        ): Any? {
            return executeWithTimeout("invoke QuickJS callback $actionName") {
                val runtimeHandle = checkNotNull(runtime) {
                    "QuickJS bootstrap session has already been disposed: $pluginId"
                }
                val previousSessionUnifiedOrigin = runtimeHandle.currentSessionUnifiedOrigin
                runtimeHandle.currentSessionUnifiedOrigin = resolveSessionUnifiedOrigin(args)
                val jsArgs = args.map { createJsValue(runtimeHandle, it) }
                try {
                    function.call(*jsArgs.toTypedArray())
                } finally {
                    runtimeHandle.currentSessionUnifiedOrigin = previousSessionUnifiedOrigin
                    jsArgs.forEach(::releaseQuickJsValueIfNeeded)
                }
            }
        }
    }

    private fun resolveSessionUnifiedOrigin(
        args: Array<out Any?>,
    ): String? {
        return args.asSequence()
            .mapNotNull(::extractSessionUnifiedOrigin)
            .firstOrNull()
    }

    private fun extractSessionUnifiedOrigin(
        value: Any?,
    ): String? {
        return when (value) {
            is PluginMessageEvent -> value.extras.sessionUnifiedOrigin()
            is PluginCommandEvent -> value.extras.sessionUnifiedOrigin()
            is PluginRegexEvent -> value.extras.sessionUnifiedOrigin()
            is PluginV2LlmWaitingPayload -> value.extras.sessionUnifiedOrigin()
            is PluginV2LlmRequestPayload -> value.event.extras.sessionUnifiedOrigin()
            is PluginV2LlmResponsePayload -> value.event.extras.sessionUnifiedOrigin()
            is PluginV2LlmResultDecoratingPayload -> value.event.extras.sessionUnifiedOrigin()
            is PluginV2LlmAfterSentPayload -> value.event.extras.sessionUnifiedOrigin()
            is PluginErrorHookArgs -> extractSessionUnifiedOrigin(value.event)
            is PluginV2CustomFilterRequest -> value.eventView.extrasSnapshot.sessionUnifiedOrigin()
            else -> null
        }
    }

    private fun Map<String, *>.sessionUnifiedOrigin(): String? {
        return this["sessionUnifiedOrigin"]
            ?.toString()
            ?.trim()
            ?.takeIf { candidate -> candidate.isNotBlank() }
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
    var currentSessionUnifiedOrigin: String? = null,
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
