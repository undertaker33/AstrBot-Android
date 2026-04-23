package com.astrbot.android.feature.plugin.runtime

import com.astrbot.android.feature.plugin.data.FeaturePluginRepository
import com.astrbot.android.model.plugin.PluginRuntimeLogLevel
import com.astrbot.android.model.plugin.PluginStaticConfigJson
import com.astrbot.android.model.plugin.PluginStaticConfigValue
import com.astrbot.android.model.plugin.toStorageBoundary
import java.io.File
import org.json.JSONObject

data class PluginV2BootstrapPluginMetadata(
    val pluginId: String,
    val installedVersion: String,
    val runtimeKind: String,
    val runtimeApiVersion: Int,
    val runtimeBootstrap: String,
)

class PluginV2BootstrapHostApi(
    private val session: PluginV2RuntimeSession,
    private val logBus: PluginRuntimeLogBus = InMemoryPluginRuntimeLogBus(),
    private val clock: () -> Long = System::currentTimeMillis,
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

    private fun loadMergedSettings(): Map<String, Any?> {
        val installRecord = session.installRecord
        val staticSchemaRelPath = session.packageContractSnapshot.config.staticSchema
        if (staticSchemaRelPath.isBlank()) return emptyMap()

        val extractedDir = installRecord.extractedDir
        if (extractedDir.isBlank()) return emptyMap()

        val schemaFile = File(extractedDir, staticSchemaRelPath)
        if (!schemaFile.isFile) return emptyMap()

        val rawJson = JSONObject(schemaFile.readText(Charsets.UTF_8))

        val schema = try {
            PluginStaticConfigJson.decodeSchema(rawJson)
        } catch (_: Throwable) {
            null
        }

        val defaults: Map<String, Any?> = if (schema != null && schema.fields.isNotEmpty()) {
            schema.fields.associate { field ->
                field.fieldKey to unwrapConfigValue(field.defaultValue)
            }
        } else {
            extractDefaultsFromRawJson(rawJson)
        }

        val persisted = try {
            if (schema != null && schema.fields.isNotEmpty()) {
                val boundary = schema.toStorageBoundary()
        val snapshot = FeaturePluginRepository.resolveConfigSnapshot(
                    pluginId = installRecord.pluginId,
                    boundary = boundary,
                )
                snapshot.coreValues.mapValues { (_, v) -> unwrapConfigValue(v) }
            } else {
                emptyMap()
            }
        } catch (_: Throwable) {
            emptyMap()
        }

        return defaults + persisted
    }

    private fun extractDefaultsFromRawJson(json: JSONObject): Map<String, Any?> {
        val result = linkedMapOf<String, Any?>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val fieldObj = json.optJSONObject(key) ?: continue
            if (fieldObj.has("default")) {
                val default = fieldObj.get("default")
                result[key] = when (default) {
                    is String, is Boolean, is Int, is Long, is Double, is Float -> default
                    JSONObject.NULL -> null
                    else -> default.toString()
                }
            }
        }
        return result
    }

    private fun unwrapConfigValue(value: Any?): Any? {
        return when (value) {
            is PluginStaticConfigValue.StringValue -> value.value
            is PluginStaticConfigValue.IntValue -> value.value
            is PluginStaticConfigValue.FloatValue -> value.value
            is PluginStaticConfigValue.BoolValue -> value.value
            else -> null
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

