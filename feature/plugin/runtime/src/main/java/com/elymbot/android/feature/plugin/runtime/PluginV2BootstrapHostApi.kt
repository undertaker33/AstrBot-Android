package com.elymbot.android.feature.plugin.runtime

import com.elymbot.android.feature.plugin.data.state.InMemoryPluginStateStore
import com.elymbot.android.feature.plugin.data.state.PluginStateScope
import com.elymbot.android.feature.plugin.data.state.PluginStateStore
import com.elymbot.android.feature.plugin.data.state.PluginStateValueCodec
import com.elymbot.android.model.plugin.PluginPermissionGrant
import com.elymbot.android.model.plugin.PluginRuntimeLogLevel
import com.elymbot.android.model.plugin.PluginTriggerMetadata
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

data class PluginV2BootstrapPluginMetadata(
    val pluginId: String,
    val installedVersion: String,
    val runtimeKind: String,
    val runtimeApiVersion: Int,
    val runtimeBootstrap: String,
)

data class PluginV2HostApiEventContext(
    val eventId: String = "",
    val conversationId: String = "",
    val platformAdapterType: String = "",
    val messageType: String = "",
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
    private val hostOperations: PluginExecutionHostOperations = DefaultPluginExecutionHostOperations(),
    private val hostNetworkApi: PluginV2HostNetworkApi? = null,
    private val providerReadApi: PluginV2ProviderReadApi? = null,
    private val messageSendApi: PluginV2MessageSendApi? = null,
    private val messageStreamApi: PluginV2MessageStreamApi? = null,
    private val conversationHistoryApi: PluginV2ConversationHistoryApi? = null,
    private val hostLlmApi: PluginV2HostLlmApi? = null,
    private val contextCompressApi: PluginV2ContextCompressApi? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private var sessionUnifiedOriginProvider: () -> String? = { null },
    private var hostApiEventContextProvider: () -> PluginV2HostApiEventContext? = { null },
) {
    private val networkAllowedDomains: Set<String> by lazy(::loadNetworkAllowedDomains)

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

    internal fun fetch(request: PluginV2HostNetworkRequest): PluginV2HostApiResult {
        val networkApi = hostNetworkApi
            ?: throw PluginV2HostApiException(
                PluginV2HostApiError(
                    code = PluginV2HostApiErrorCodes.HOST_UNAVAILABLE,
                    message = "Plugin host network API is unavailable.",
                ),
            )
        return runBlocking {
            networkApi.fetch(
                context = createHostApiRequestContext(),
                request = request,
            )
        }
    }

    fun registerScheduledHandler(
        descriptor: ScheduledHandlerRegistrationInput,
    ): PluginV2CallbackToken {
        requireRegistrationPermission(PluginV2HostApiPermissions.SCHEDULE_MANAGE)
        return register(
            operation = "registerScheduledHandler",
            registrationType = "schedule",
            normalizeDescriptor = { validateScheduledHandler(descriptor) },
            extractHandler = { it.handler },
        ) { rawRegistry, callbackToken, normalizedDescriptor ->
            rawRegistry.appendScheduledHandler(
                callbackToken = callbackToken,
                descriptor = normalizedDescriptor,
            )
        }
    }

    internal fun networkRequest(request: PluginV2HostNetworkRequest): PluginV2HostApiResult = fetch(request)

    internal fun providersList(): PluginV2HostApiResult {
        val api = providerReadApi
            ?: throw hostApiUnavailable("Plugin provider read API is unavailable.")
        return runBlocking {
            api.list(context = createHostApiRequestContext())
        }
    }

    internal fun providerModels(request: PluginV2ProviderModelsRequest): PluginV2HostApiResult {
        val api = providerReadApi
            ?: throw hostApiUnavailable("Plugin provider model read API is unavailable.")
        return runBlocking {
            api.models(
                context = createHostApiRequestContext(),
                request = request,
            )
        }
    }

    internal fun messageSend(request: PluginV2MessageSendRequest): PluginV2HostApiResult {
        val api = messageSendApi
            ?: throw hostApiUnavailable("Plugin message send API is unavailable.")
        return runBlocking {
            api.send(
                context = createHostApiRequestContext(),
                request = request,
            )
        }
    }

    internal fun messageOpenStream(request: PluginV2MessageStreamOpenRequest): PluginV2HostApiResult {
        val api = messageStreamApi
            ?: throw hostApiUnavailable("Plugin message stream API is unavailable.")
        return runBlocking {
            api.openStream(
                context = createHostApiRequestContext(),
                request = request,
            )
        }
    }

    internal fun messageStreamAppend(streamId: String, text: String): PluginV2HostApiResult {
        val api = messageStreamApi
            ?: throw hostApiUnavailable("Plugin message stream API is unavailable.")
        return runBlocking {
            api.append(streamId, text)
        }
    }

    internal fun messageStreamReplace(streamId: String, text: String): PluginV2HostApiResult {
        val api = messageStreamApi
            ?: throw hostApiUnavailable("Plugin message stream API is unavailable.")
        return runBlocking {
            api.replace(streamId, text)
        }
    }

    internal fun messageStreamClose(streamId: String): PluginV2HostApiResult {
        val api = messageStreamApi
            ?: throw hostApiUnavailable("Plugin message stream API is unavailable.")
        return runBlocking {
            api.close(streamId)
        }
    }

    internal fun messageStreamFail(streamId: String, message: String): PluginV2HostApiResult {
        val api = messageStreamApi
            ?: throw hostApiUnavailable("Plugin message stream API is unavailable.")
        return runBlocking {
            api.fail(streamId, message)
        }
    }

    internal fun conversationHistory(request: PluginV2ConversationHistoryRequest): PluginV2HostApiResult {
        val api = conversationHistoryApi
            ?: throw hostApiUnavailable("Plugin conversation history API is unavailable.")
        return runBlocking {
            api.history(
                context = createHostApiRequestContext(),
                request = request,
            )
        }
    }

    internal fun callLlm(request: PluginV2HostLlmRequest): PluginV2HostApiResult {
        val api = hostLlmApi
            ?: throw hostApiUnavailable("Plugin host LLM API is unavailable.")
        return runBlocking {
            api.callLlm(
                context = createHostApiRequestContext(),
                request = request,
            )
        }
    }

    internal fun llmGenerate(request: PluginV2HostLlmRequest): PluginV2HostApiResult {
        val api = hostLlmApi
            ?: throw hostApiUnavailable("Plugin host LLM API is unavailable.")
        return runBlocking {
            api.generate(
                context = createHostApiRequestContext(),
                request = request,
            )
        }
    }

    internal fun contextCompress(request: PluginV2ContextCompressRequest): PluginV2HostApiResult {
        val api = contextCompressApi
            ?: throw hostApiUnavailable("Plugin context compress API is unavailable.")
        return runBlocking {
            api.compress(
                context = createHostApiRequestContext(),
                request = request,
            )
        }
    }

    internal fun attachSessionUnifiedOriginProvider(
        provider: () -> String?,
    ) {
        sessionUnifiedOriginProvider = provider
    }

    internal fun attachHostApiEventContextProvider(
        provider: () -> PluginV2HostApiEventContext?,
    ) {
        hostApiEventContextProvider = provider
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
        return hostOperations.resolve(session.installRecord.pluginId).mergedSettings
    }

    private fun createHostApiRequestContext(): PluginV2HostApiRequestContext {
        val declaredPermissions = session.installRecord.manifestSnapshot.permissions
        val grantedPermissions = session.installRecord.permissionSnapshot
        val grantedPermissionIds = grantedPermissions.mapTo(linkedSetOf()) { it.permissionId }
        val eventContext = hostApiEventContextProvider()
        return PluginV2HostApiRequestContext(
            pluginId = session.pluginId,
            pluginVersion = session.installRecord.installedVersion,
            requestId = "${session.sessionInstanceId}:host-api:${clock()}",
            conversationId = eventContext?.conversationId.orEmpty(),
            platformAdapterType = eventContext?.platformAdapterType.orEmpty(),
            manifestPermissionIds = declaredPermissions.mapTo(linkedSetOf()) { it.permissionId },
            permissionSnapshot = grantedPermissions.map { permission ->
                PluginPermissionGrant(
                    permissionId = permission.permissionId,
                    title = permission.title,
                    granted = permission.permissionId in grantedPermissionIds,
                    required = permission.required,
                    riskLevel = permission.riskLevel,
                )
            },
            triggerPermissionWhitelist = grantedPermissionIds,
            triggerMetadata = PluginTriggerMetadata(
                eventId = eventContext?.eventId.orEmpty(),
                extras = buildMap {
                    eventContext?.messageType
                        ?.takeIf(String::isNotBlank)
                        ?.let { put("messageType", it) }
                },
            ),
            networkAllowedDomains = networkAllowedDomains,
        )
    }

    private fun hostApiUnavailable(message: String): PluginV2HostApiException {
        return PluginV2HostApiException(
            PluginV2HostApiError(
                code = PluginV2HostApiErrorCodes.HOST_UNAVAILABLE,
                message = message,
            ),
        )
    }

    private fun loadNetworkAllowedDomains(): Set<String> {
        val packageContractDomains = session.packageContractSnapshot.network.allowedDomains
        val domains = linkedSetOf<String>()
        domains += packageContractDomains
        val manifestFile = File(session.installRecord.extractedDir, "manifest.json")
        if (!manifestFile.isFile) {
            return domains.normalizedDomains()
        }
        val json = runCatching {
            JSONObject(manifestFile.readText(Charsets.UTF_8))
        }.getOrNull() ?: return domains.normalizedDomains()
        domains.appendStringArray(json.optJSONArray("networkAllowedDomains"))
        domains.appendStringArray(json.optJSONObject("network")?.optJSONArray("allowedDomains"))
        return domains.normalizedDomains()
    }

    private fun MutableSet<String>.appendStringArray(array: JSONArray?) {
        if (array == null) return
        for (index in 0 until array.length()) {
            val value = array.optString(index).trim()
            if (value.isNotBlank()) {
                add(value)
            }
        }
    }

    private fun Set<String>.normalizedDomains(): Set<String> {
        return mapNotNullTo(linkedSetOf()) { domain ->
            domain.trim().lowercase().removeSuffix(".").takeIf(String::isNotBlank)
        }
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

    private fun validateScheduledHandler(
        descriptor: ScheduledHandlerRegistrationInput,
    ): ScheduledHandlerRegistrationInput {
        val key = requireTrimmedValue(
            value = descriptor.key,
            fieldName = "key",
        )
        val cron = descriptor.cron?.trim()?.takeIf(String::isNotBlank)
        val runAt = descriptor.runAt
        require((cron != null) xor (runAt != null)) {
            "registerScheduledHandler requires exactly one of cron or runAt."
        }
        val targetContext = requireScheduledTargetContext(
            conversationId = descriptor.conversationId.trim(),
            platformAdapterType = descriptor.platformAdapterType.trim(),
        )
        return descriptor.copy(
            key = key,
            cron = cron,
            runAt = runAt,
            conversationId = targetContext.conversationId,
            platformAdapterType = targetContext.platformAdapterType,
            metadata = normalizeMetadata(descriptor.metadata),
        )
    }

    private fun requireScheduledTargetContext(
        conversationId: String,
        platformAdapterType: String,
    ): ScheduledTargetContext {
        val eventContext = hostApiEventContextProvider()
        val boundConversationId = eventContext?.conversationId?.trim().orEmpty()
        if (boundConversationId.isBlank()) {
            require(conversationId.isBlank() && platformAdapterType.isBlank()) {
                "registerScheduledHandler explicit target requires current event context or host-authorized schedule target."
            }
            return ScheduledTargetContext(
                conversationId = "",
                platformAdapterType = "",
            )
        }
        val targetConversationId = conversationId.ifBlank { boundConversationId }
        require(targetConversationId == boundConversationId) {
            "registerScheduledHandler target conversationId must match current event context."
        }

        val boundPlatformAdapterType = eventContext?.platformAdapterType?.trim().orEmpty()
        require(platformAdapterType.isBlank() || boundPlatformAdapterType.isNotBlank()) {
            "registerScheduledHandler target platformAdapterType must match current event context."
        }
        val targetPlatformAdapterType = platformAdapterType.ifBlank { boundPlatformAdapterType }
        require(boundPlatformAdapterType.isBlank() || targetPlatformAdapterType == boundPlatformAdapterType) {
            "registerScheduledHandler target platformAdapterType must match current event context."
        }

        return ScheduledTargetContext(
            conversationId = targetConversationId,
            platformAdapterType = targetPlatformAdapterType,
        )
    }

    private data class ScheduledTargetContext(
        val conversationId: String,
        val platformAdapterType: String,
    )

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

    private fun requireRegistrationPermission(permissionId: String) {
        val manifestPermissions = session.installRecord.manifestSnapshot.permissions.mapTo(linkedSetOf()) { it.permissionId }
        val grantedPermissions = session.installRecord.permissionSnapshot.mapTo(linkedSetOf()) { it.permissionId }
        check(permissionId in manifestPermissions && permissionId in grantedPermissions) {
            "Permission $permissionId is required for this registration."
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

