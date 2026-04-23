package com.astrbot.android.feature.plugin.runtime

import com.astrbot.android.feature.plugin.data.state.InMemoryPluginStateStore
import com.astrbot.android.feature.plugin.data.state.PluginStateScope
import com.astrbot.android.feature.plugin.data.state.PluginStateStore
import com.astrbot.android.feature.plugin.data.state.PluginStateValueCodec
import com.astrbot.android.model.plugin.PluginRuntimeLogLevel
import org.json.JSONObject

data class PluginV2BootstrapPluginMetadata(
    val pluginId: String,
    val installedVersion: String,
    val runtimeKind: String,
    val runtimeApiVersion: Int,
    val runtimeBootstrap: String,
)

data class PluginV2StructuredError(
    val code: String,
    val message: String,
    val details: Map<String, String> = emptyMap(),
)

class PluginV2StorageAccessException(
    val error: PluginV2StructuredError,
) : IllegalStateException(
    JSONObject().apply {
        put("code", error.code)
        put("message", error.message)
        put("details", error.details)
    }.toString(),
)

class PluginV2BootstrapHostApi(
    private val session: PluginV2RuntimeSession,
    private val logBus: PluginRuntimeLogBus = InMemoryPluginRuntimeLogBus(),
    private val stateStore: PluginStateStore = InMemoryPluginStateStore(),
    private val clock: () -> Long = System::currentTimeMillis,
    private var sessionUnifiedOriginProvider: () -> String? = { null },
) {
    fun registerMessageHandler(
        descriptor: MessageHandlerRegistrationInput,
    ): PluginV2CallbackToken {
        return register(
            operation = "registerMessageHandler",
            registrationType = "message",
            normalizeDescriptor = { validateMessageHandler(descriptor) },
            extractHandler = { it.handler },
        ) { rawRegistry, callbackToken, normalizedDescriptor ->
            rawRegistry.appendMessageHandler(
                callbackToken = callbackToken,
                descriptor = normalizedDescriptor,
            )
        }
    }

    fun registerCommandHandler(
        descriptor: CommandHandlerRegistrationInput,
    ): PluginV2CallbackToken {
        return register(
            operation = "registerCommandHandler",
            registrationType = "command",
            normalizeDescriptor = { validateCommandHandler(descriptor) },
            extractHandler = { it.handler },
        ) { rawRegistry, callbackToken, normalizedDescriptor ->
            rawRegistry.appendCommandHandler(
                callbackToken = callbackToken,
                descriptor = normalizedDescriptor,
            )
        }
    }

    fun registerRegexHandler(
        descriptor: RegexHandlerRegistrationInput,
    ): PluginV2CallbackToken {
        return register(
            operation = "registerRegexHandler",
            registrationType = "regex",
            normalizeDescriptor = { validateRegexHandler(descriptor) },
            extractHandler = { it.handler },
        ) { rawRegistry, callbackToken, normalizedDescriptor ->
            rawRegistry.appendRegexHandler(
                callbackToken = callbackToken,
                descriptor = normalizedDescriptor,
            )
        }
    }

    fun registerLifecycleHandler(
        descriptor: LifecycleHandlerRegistrationInput,
    ): PluginV2CallbackToken {
        return register(
            operation = "registerLifecycleHandler",
            registrationType = "lifecycle",
            normalizeDescriptor = { validateLifecycleHandler(descriptor) },
            extractHandler = { it.handler },
        ) { rawRegistry, callbackToken, normalizedDescriptor ->
            rawRegistry.appendLifecycleHandler(
                callbackToken = callbackToken,
                descriptor = normalizedDescriptor,
            )
        }
    }

    fun registerLlmHook(
        descriptor: LlmHookRegistrationInput,
    ): PluginV2CallbackToken {
        return register(
            operation = "registerLlmHook",
            registrationType = "llm_hook",
            normalizeDescriptor = { validateLlmHook(descriptor) },
            extractHandler = { it.handler },
        ) { rawRegistry, callbackToken, normalizedDescriptor ->
            rawRegistry.appendLlmHook(
                callbackToken = callbackToken,
                descriptor = normalizedDescriptor,
            )
        }
    }

    fun registerTool(
        descriptor: PluginToolDescriptor,
        handler: PluginV2CallbackHandle,
    ): PluginV2CallbackToken {
        return register(
            operation = "registerTool",
            registrationType = "tool",
            normalizeDescriptor = { validateToolDescriptor(descriptor) },
            extractHandler = { handler },
        ) { rawRegistry, callbackToken, normalizedDescriptor ->
            rawRegistry.appendTool(
                callbackToken = callbackToken,
                descriptor = normalizedDescriptor,
            )
        }
    }

    fun registerToolLifecycleHook(
        descriptor: ToolLifecycleHookRegistrationInput,
    ): PluginV2CallbackToken {
        return register(
            operation = "registerToolLifecycleHook",
            registrationType = "tool_lifecycle",
            normalizeDescriptor = { validateToolLifecycleHook(descriptor) },
            extractHandler = { it.handler },
        ) { rawRegistry, callbackToken, normalizedDescriptor ->
            rawRegistry.appendToolLifecycleHook(
                callbackToken = callbackToken,
                descriptor = normalizedDescriptor,
            )
        }
    }

    fun log(
        level: PluginRuntimeLogLevel,
        message: String,
        metadata: Map<String, String> = emptyMap(),
    ) {
        require(message.isNotBlank()) { "message must not be blank." }
        val normalizedMetadata = normalizeMetadata(metadata)
        publishBootstrapLog(
            level = level,
            code = "bootstrap_log",
            message = message.trim(),
            metadata = normalizedMetadata,
        )
    }

    fun log(
        level: String,
        message: String,
        metadata: Map<String, String> = emptyMap(),
    ) {
        val resolvedLevel = PluginRuntimeLogLevel.entries.firstOrNull { candidate ->
            candidate.wireValue.equals(level.trim(), ignoreCase = true)
        } ?: throw IllegalArgumentException("Unsupported log level: $level")
        log(
            level = resolvedLevel,
            message = message,
            metadata = metadata,
        )
    }

    fun getPluginMetadata(): PluginV2BootstrapPluginMetadata {
        val installRecord = session.installRecord
        val runtimeSnapshot = session.packageContractSnapshot.runtime
        return PluginV2BootstrapPluginMetadata(
            pluginId = installRecord.pluginId,
            installedVersion = installRecord.installedVersion,
            runtimeKind = runtimeSnapshot.kind,
            runtimeApiVersion = runtimeSnapshot.apiVersion,
            runtimeBootstrap = runtimeSnapshot.bootstrap,
        )
    }

    fun getSettings(): Map<String, Any?> {
        return try {
            loadMergedSettings()
        } catch (error: Throwable) {
            publishBootstrapLog(
                level = PluginRuntimeLogLevel.Warning,
                code = "bootstrap_settings_load_failed",
                message = "Failed to load plugin settings: ${error.message ?: error.javaClass.simpleName}",
                metadata = emptyMap(),
            )
            emptyMap()
        }
    }

    internal fun attachSessionUnifiedOriginProvider(
        provider: () -> String?,
    ) {
        sessionUnifiedOriginProvider = provider
    }

    internal fun pluginStorageGet(
        key: String,
        defaultValue: Any? = null,
    ): Any? = readStorageValue(
        scope = PluginStateScope.plugin(),
        key = key,
        defaultValue = defaultValue,
    )

    internal fun pluginStorageSet(
        key: String,
        value: Any?,
    ): Boolean {
        writeStorageValue(
            scope = PluginStateScope.plugin(),
            key = key,
            value = value,
        )
        return true
    }

    internal fun pluginStorageRemove(
        key: String,
    ): Boolean {
        removeStorageValue(
            scope = PluginStateScope.plugin(),
            key = key,
        )
        return true
    }

    internal fun pluginStorageKeys(
        prefix: String = "",
    ): List<String> = listStorageKeys(
        scope = PluginStateScope.plugin(),
        prefix = prefix,
    )

    internal fun pluginStorageClear(
        prefix: String = "",
    ): Boolean {
        clearStorageScope(
            scope = PluginStateScope.plugin(),
            prefix = prefix,
        )
        return true
    }

    internal fun sessionStorageGet(
        key: String,
        defaultValue: Any? = null,
    ): Any? = readStorageValue(
        scope = requireSessionStorageScope(),
        key = key,
        defaultValue = defaultValue,
    )

    internal fun sessionStorageSet(
        key: String,
        value: Any?,
    ): Boolean {
        writeStorageValue(
            scope = requireSessionStorageScope(),
            key = key,
            value = value,
        )
        return true
    }

    internal fun sessionStorageRemove(
        key: String,
    ): Boolean {
        removeStorageValue(
            scope = requireSessionStorageScope(),
            key = key,
        )
        return true
    }

    internal fun sessionStorageKeys(
        prefix: String = "",
    ): List<String> = listStorageKeys(
        scope = requireSessionStorageScope(),
        prefix = prefix,
    )

    internal fun sessionStorageClear(
        prefix: String = "",
    ): Boolean {
        clearStorageScope(
            scope = requireSessionStorageScope(),
            prefix = prefix,
        )
        return true
    }

    private fun loadMergedSettings(): Map<String, Any?> {
        return PluginExecutionHostApi.resolve(session.installRecord.pluginId).mergedSettings
    }

    private fun requireSessionStorageScope(): PluginStateScope {
        val sessionUnifiedOrigin = sessionUnifiedOriginProvider()
            ?.trim()
            .orEmpty()
        if (sessionUnifiedOrigin.isBlank()) {
            throw PluginV2StorageAccessException(
                PluginV2StructuredError(
                    code = "missing_session_scope",
                    message = "storage.session requires a current message session context.",
                    details = linkedMapOf("scope" to "session"),
                ),
            )
        }
        return PluginStateScope.session(sessionUnifiedOrigin)
    }

    private fun readStorageValue(
        scope: PluginStateScope,
        key: String,
        defaultValue: Any?,
    ): Any? {
        val normalizedKey = normalizeStorageKey(key)
        val storedValueJson = runStorageOperation(
            code = "storage_read_failed",
            fallbackMessage = "Failed to read storage value.",
        ) {
            stateStore.getValueJson(
                pluginId = session.pluginId,
                scope = scope,
                key = normalizedKey,
            )
        }
        if (storedValueJson == null) {
            return defaultValue
        }
        return runStorageOperation(
            code = "storage_decode_failed",
            fallbackMessage = "Failed to decode stored value.",
        ) {
            PluginStateValueCodec.decode(storedValueJson)
        }
    }

    private fun writeStorageValue(
        scope: PluginStateScope,
        key: String,
        value: Any?,
    ) {
        val normalizedKey = normalizeStorageKey(key)
        val valueJson = runStorageOperation(
            code = "invalid_storage_value",
            fallbackMessage = "Storage value must be JSON-serializable.",
        ) {
            PluginStateValueCodec.encode(value)
        }
        runStorageOperation(
            code = "storage_write_failed",
            fallbackMessage = "Failed to persist storage value.",
        ) {
            stateStore.putValueJson(
                pluginId = session.pluginId,
                scope = scope,
                key = normalizedKey,
                valueJson = valueJson,
            )
        }
    }

    private fun removeStorageValue(
        scope: PluginStateScope,
        key: String,
    ) {
        val normalizedKey = normalizeStorageKey(key)
        runStorageOperation(
            code = "storage_remove_failed",
            fallbackMessage = "Failed to remove storage value.",
        ) {
            stateStore.remove(
                pluginId = session.pluginId,
                scope = scope,
                key = normalizedKey,
            )
        }
    }

    private fun listStorageKeys(
        scope: PluginStateScope,
        prefix: String,
    ): List<String> {
        return runStorageOperation(
            code = "storage_list_failed",
            fallbackMessage = "Failed to list storage keys.",
        ) {
            stateStore.listKeys(
                pluginId = session.pluginId,
                scope = scope,
                prefix = prefix.trim(),
            )
        }
    }

    private fun clearStorageScope(
        scope: PluginStateScope,
        prefix: String,
    ) {
        runStorageOperation(
            code = "storage_clear_failed",
            fallbackMessage = "Failed to clear storage scope.",
        ) {
            stateStore.clearScope(
                pluginId = session.pluginId,
                scope = scope,
                prefix = prefix.trim(),
            )
        }
    }

    private fun normalizeStorageKey(
        key: String,
    ): String {
        val normalized = key.trim()
        if (normalized.isEmpty()) {
            throw PluginV2StorageAccessException(
                PluginV2StructuredError(
                    code = "invalid_storage_key",
                    message = "storage key must not be blank.",
                ),
            )
        }
        if (normalized.length > 128) {
            throw PluginV2StorageAccessException(
                PluginV2StructuredError(
                    code = "invalid_storage_key",
                    message = "storage key must be <= 128 characters.",
                ),
            )
        }
        return normalized
    }

    private fun <T> runStorageOperation(
        code: String,
        fallbackMessage: String,
        block: () -> T,
    ): T {
        return try {
            block()
        } catch (error: PluginV2StorageAccessException) {
            throw error
        } catch (error: IllegalArgumentException) {
            throw PluginV2StorageAccessException(
                PluginV2StructuredError(
                    code = code,
                    message = error.message ?: fallbackMessage,
                ),
            )
        } catch (error: IllegalStateException) {
            throw PluginV2StorageAccessException(
                PluginV2StructuredError(
                    code = code,
                    message = error.message ?: fallbackMessage,
                ),
            )
        }
    }

    private fun <T> register(
        operation: String,
        registrationType: String,
        normalizeDescriptor: () -> T,
        extractHandler: (T) -> PluginV2CallbackHandle,
        appendRegistration: (PluginV2RawRegistry, PluginV2CallbackToken, T) -> Unit,
    ): PluginV2CallbackToken {
        return try {
            val normalizedDescriptor = normalizeDescriptor()
            val rawRegistry = session.requireBootstrapRawRegistry()
            val callbackToken = session.allocateCallbackToken(extractHandler(normalizedDescriptor))
            appendRegistration(rawRegistry, callbackToken, normalizedDescriptor)
            callbackToken
        } catch (error: IllegalArgumentException) {
            logRegistrationRejected(
                operation = operation,
                registrationType = registrationType,
                message = error.message ?: "Invalid registration input.",
                exception = error,
            )
        } catch (error: IllegalStateException) {
            logRegistrationRejected(
                operation = operation,
                registrationType = registrationType,
                message = error.message ?: "Registration attempted in invalid runtime state.",
                exception = error,
            )
        }
    }

    private fun validateMessageHandler(
        descriptor: MessageHandlerRegistrationInput,
    ): MessageHandlerRegistrationInput {
        return descriptor.copy(
            base = normalizeBase(descriptor.base),
        )
    }

    private fun validateCommandHandler(
        descriptor: CommandHandlerRegistrationInput,
    ): CommandHandlerRegistrationInput {
        return descriptor.copy(
            base = normalizeBase(descriptor.base),
            command = requireTrimmedValue(
                value = descriptor.command,
                fieldName = "command",
            ),
            aliases = normalizeStringList(
                values = descriptor.aliases,
                fieldName = "aliases",
            ),
            groupPath = normalizeStringList(
                values = descriptor.groupPath,
                fieldName = "groupPath",
            ),
        )
    }

    private fun validateRegexHandler(
        descriptor: RegexHandlerRegistrationInput,
    ): RegexHandlerRegistrationInput {
        return descriptor.copy(
            base = normalizeBase(descriptor.base),
            pattern = requireTrimmedValue(
                value = descriptor.pattern,
                fieldName = "pattern",
            ),
            flags = normalizeStringSet(
                values = descriptor.flags,
                fieldName = "flags",
            ),
        )
    }

    private fun validateLifecycleHandler(
        descriptor: LifecycleHandlerRegistrationInput,
    ): LifecycleHandlerRegistrationInput {
        rejectFiltersIfPresent(descriptor.declaredFilters)
        val lifecycleHook = PluginLifecycleHookSurface.fromWireValue(
            requireTrimmedValue(
                value = descriptor.hook,
                fieldName = "hook",
            ),
        ) ?: throw IllegalArgumentException(
            "Unsupported lifecycle hook: ${descriptor.hook}",
        )
        return descriptor.copy(
            registrationKey = normalizeRegistrationKey(descriptor.registrationKey),
            hook = lifecycleHook.wireValue,
            metadata = normalizeMetadata(descriptor.metadata),
        )
    }

    private fun validateLlmHook(
        descriptor: LlmHookRegistrationInput,
    ): LlmHookRegistrationInput {
        rejectFiltersIfPresent(descriptor.declaredFilters)
        return descriptor.copy(
            registrationKey = normalizeRegistrationKey(descriptor.registrationKey),
            hook = requireTrimmedValue(
                value = descriptor.hook,
                fieldName = "hook",
            ),
            metadata = normalizeMetadata(descriptor.metadata),
        )
    }

    private fun validateToolDescriptor(
        descriptor: PluginToolDescriptor,
    ): PluginToolDescriptor {
        require(descriptor.pluginId.isNotBlank()) { "pluginId must not be blank." }
        require(descriptor.pluginId == session.pluginId) {
            "tool descriptor pluginId must match bootstrap session pluginId."
        }
        require(descriptor.name.isNotBlank()) { "name must not be blank." }
        require(!descriptor.sourceKind.reservedOnly) {
            "reserved source kind cannot be registered through registerTool."
        }
        requireToolSchema(descriptor.inputSchema)
        return PluginToolDescriptor(
            pluginId = descriptor.pluginId.trim(),
            name = descriptor.name,
            description = descriptor.description,
            visibility = descriptor.visibility,
            sourceKind = descriptor.sourceKind,
            inputSchema = descriptor.inputSchema,
            metadata = descriptor.metadata,
        )
    }

    private fun validateToolLifecycleHook(
        descriptor: ToolLifecycleHookRegistrationInput,
    ): ToolLifecycleHookRegistrationInput {
        rejectFiltersIfPresent(descriptor.declaredFilters)
        return descriptor.copy(
            registrationKey = normalizeRegistrationKey(descriptor.registrationKey),
            hook = requireTrimmedValue(
                value = descriptor.hook,
                fieldName = "hook",
            ),
            metadata = normalizeMetadata(descriptor.metadata),
        )
    }

    private fun normalizeBase(
        descriptor: BaseHandlerRegistrationInput,
    ): BaseHandlerRegistrationInput {
        return descriptor.copy(
            registrationKey = normalizeRegistrationKey(descriptor.registrationKey),
            declaredFilters = normalizeDeclaredFilters(descriptor.declaredFilters),
            metadata = normalizeMetadata(descriptor.metadata),
        )
    }

    private fun normalizeRegistrationKey(value: String?): String? {
        return value?.let {
            requireTrimmedValue(
                value = it,
                fieldName = "registrationKey",
            )
        }
    }

    private fun normalizeDeclaredFilters(
        declaredFilters: List<BootstrapFilterDescriptor>,
    ): List<BootstrapFilterDescriptor> {
        return declaredFilters.map { filter ->
            BootstrapFilterDescriptor(
                kind = filter.kind,
                value = requireTrimmedValue(
                    value = filter.value,
                    fieldName = "declaredFilters.value",
                ),
            )
        }
    }

    private fun normalizeStringList(
        values: List<String>,
        fieldName: String,
    ): List<String> {
        return values.mapIndexed { index, value ->
            requireTrimmedValue(
                value = value,
                fieldName = "$fieldName[$index]",
            )
        }
    }

    private fun normalizeStringSet(
        values: Set<String>,
        fieldName: String,
    ): Set<String> {
        return values.mapIndexed { index, value ->
            requireTrimmedValue(
                value = value,
                fieldName = "$fieldName[$index]",
            )
        }.toSet()
    }

    private fun rejectFiltersIfPresent(
        declaredFilters: List<BootstrapFilterDescriptor>,
    ) {
        require(declaredFilters.isEmpty()) {
            "declaredFilters are only allowed on message/command/regex registrations."
        }
    }

    private fun normalizeMetadata(
        metadata: BootstrapRegistrationMetadata,
    ): BootstrapRegistrationMetadata {
        return metadata.copy(
            values = normalizeMetadata(metadata.values),
        )
    }

    private fun normalizeMetadata(
        metadata: Map<String, String>,
    ): Map<String, String> {
        return metadata.entries.associate { (key, value) ->
            require(key.isNotBlank()) { "metadata keys must not be blank." }
            key.trim() to value.trim()
        }
    }

    private fun requireTrimmedValue(
        value: String,
        fieldName: String,
    ): String {
        return value.trim().also { trimmed ->
            require(trimmed.isNotEmpty()) { "$fieldName must not be blank." }
        }
    }

    private fun logRegistrationRejected(
        operation: String,
        registrationType: String,
        message: String,
        exception: RuntimeException,
    ): Nothing {
        publishBootstrapLog(
            level = PluginRuntimeLogLevel.Error,
            code = "bootstrap_registration_rejected",
            message = message,
            metadata = linkedMapOf(
                "operation" to operation,
                "registrationType" to registrationType,
            ),
        )
        throw exception
    }

    private fun publishBootstrapLog(
        level: PluginRuntimeLogLevel,
        code: String,
        message: String,
        metadata: Map<String, String>,
    ) {
        logBus.publishBootstrapRecord(
            pluginId = session.pluginId,
            pluginVersion = session.installRecord.installedVersion,
            occurredAtEpochMillis = clock(),
            level = level,
            code = code,
            message = message,
            metadata = metadata,
        )
    }
}

