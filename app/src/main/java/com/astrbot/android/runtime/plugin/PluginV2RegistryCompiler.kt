package com.astrbot.android.runtime.plugin

import com.astrbot.android.model.plugin.PluginRuntimeLogLevel

typealias DiagnosticSeverity = com.astrbot.android.model.plugin.DiagnosticSeverity
typealias PluginV2CompilerDiagnostic = com.astrbot.android.model.plugin.PluginV2CompilerDiagnostic

data class PluginV2RegistryCompileResult(
    val compiledRegistry: PluginV2CompiledRegistrySnapshot?,
    val diagnostics: List<PluginV2CompilerDiagnostic>,
)

class PluginV2RegistryCompiler(
    private val logBus: PluginRuntimeLogBus = PluginRuntimeLogBusProvider.bus(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun compile(rawRegistry: PluginV2RawRegistry): PluginV2RegistryCompileResult {
        val diagnostics = mutableListOf<PluginV2CompilerDiagnostic>()
        val duplicateGuard = linkedSetOf<String>()
        val autoCounters = linkedMapOf<String, Int>()
        val stageBuckets = linkedMapOf<PluginV2InternalStage, MutableList<String>>()

        val messageHandlers = rawRegistry.messageHandlers
            .sortedBy(MessageHandlerRawRegistration::sourceOrder)
            .mapNotNull { registration ->
                compileMessage(
                    registration = registration,
                    diagnostics = diagnostics,
                    duplicateGuard = duplicateGuard,
                    autoCounters = autoCounters,
                    stageBuckets = stageBuckets,
                )
            }
        val commandHandlers = rawRegistry.commandHandlers
            .sortedBy(CommandHandlerRawRegistration::sourceOrder)
            .mapNotNull { registration ->
                compileCommand(
                    registration = registration,
                    diagnostics = diagnostics,
                    duplicateGuard = duplicateGuard,
                    autoCounters = autoCounters,
                    stageBuckets = stageBuckets,
                )
            }
        val regexHandlers = rawRegistry.regexHandlers
            .sortedBy(RegexHandlerRawRegistration::sourceOrder)
            .mapNotNull { registration ->
                compileRegex(
                    registration = registration,
                    diagnostics = diagnostics,
                    duplicateGuard = duplicateGuard,
                    autoCounters = autoCounters,
                    stageBuckets = stageBuckets,
                )
            }
        val lifecycleHandlers = rawRegistry.lifecycleHandlers
            .sortedBy(LifecycleHandlerRawRegistration::sourceOrder)
            .mapNotNull { registration ->
                compileLifecycle(
                    registration = registration,
                    diagnostics = diagnostics,
                    duplicateGuard = duplicateGuard,
                    autoCounters = autoCounters,
                    stageBuckets = stageBuckets,
                )
            }
        val llmHooks = rawRegistry.llmHooks
            .sortedBy(LlmHookRawRegistration::sourceOrder)
            .mapNotNull { registration ->
                compileLlmHook(
                    registration = registration,
                    diagnostics = diagnostics,
                    duplicateGuard = duplicateGuard,
                    autoCounters = autoCounters,
                    stageBuckets = stageBuckets,
                )
            }
        val tools = rawRegistry.tools
            .sortedBy(ToolRawRegistration::sourceOrder)
            .mapNotNull { registration ->
                compileTool(
                    registration = registration,
                    diagnostics = diagnostics,
                    duplicateGuard = duplicateGuard,
                    autoCounters = autoCounters,
                    stageBuckets = stageBuckets,
                )
            }
        val toolLifecycleHooks = rawRegistry.toolLifecycleHooks
            .sortedBy(ToolLifecycleHookRawRegistration::sourceOrder)
            .mapNotNull { registration ->
                compileToolLifecycleHook(
                    registration = registration,
                    diagnostics = diagnostics,
                    duplicateGuard = duplicateGuard,
                    autoCounters = autoCounters,
                    stageBuckets = stageBuckets,
                )
            }

        val hasError = diagnostics.any { diagnostic ->
            diagnostic.severity == DiagnosticSeverity.Error
        }
        if (hasError) {
            publishCompileFailed(
                pluginId = rawRegistry.pluginId,
                diagnostics = diagnostics,
            )
            return PluginV2RegistryCompileResult(
                compiledRegistry = null,
                diagnostics = diagnostics.toList(),
            )
        }

        val handlerRegistry = PluginV2HandlerRegistry(
            messageHandlers = messageHandlers,
            commandHandlers = commandHandlers,
            regexHandlers = regexHandlers,
            lifecycleHandlers = lifecycleHandlers,
            llmHooks = llmHooks,
            tools = tools,
            toolLifecycleHooks = toolLifecycleHooks,
        )
        val dispatchIndex = PluginV2StageIndex(
            handlerIdsByStage = stageBuckets.mapValues { (_, handlerIds) ->
                handlerIds.toList()
            },
        )

        return PluginV2CompiledRegistrySnapshot(
            handlerRegistry = handlerRegistry,
            dispatchIndex = dispatchIndex,
        ).let { compiledRegistry ->
            publishCompiled(
                pluginId = rawRegistry.pluginId,
                compiledRegistry = compiledRegistry,
                diagnostics = diagnostics,
            )
            PluginV2RegistryCompileResult(
                compiledRegistry = compiledRegistry,
                diagnostics = diagnostics.toList(),
            )
        }
    }

    private fun compileMessage(
        registration: MessageHandlerRawRegistration,
        diagnostics: MutableList<PluginV2CompilerDiagnostic>,
        duplicateGuard: MutableSet<String>,
        autoCounters: MutableMap<String, Int>,
        stageBuckets: MutableMap<PluginV2InternalStage, MutableList<String>>,
    ): PluginV2CompiledMessageHandler? {
        val identity = compileIdentity(
            pluginId = registration.pluginId,
            registrationKind = REGISTRATION_KIND_MESSAGE,
            requestedRegistrationKey = registration.registrationKey,
            autoCounters = autoCounters,
            diagnostics = diagnostics,
            duplicateGuard = duplicateGuard,
        ) ?: return null
        val compiled = PluginV2CompiledMessageHandler(
            pluginId = registration.pluginId,
            registrationKind = REGISTRATION_KIND_MESSAGE,
            registrationKey = identity.registrationKey,
            normalizedRegistrationKey = identity.normalizedRegistrationKey,
            handlerId = identity.handlerId,
            callbackToken = registration.callbackToken,
            priority = registration.priority,
            filterAttachments = compileFilterAttachments(
                declaredFilters = registration.declaredFilters,
                normalizedRegistrationKey = identity.normalizedRegistrationKey,
            ),
            metadata = registration.metadata,
            sourceOrder = registration.sourceOrder,
        )
        stageBuckets.getOrPut(PluginV2InternalStage.AdapterMessage) { mutableListOf() }
            .add(compiled.handlerId)
        return compiled
    }

    private fun compileCommand(
        registration: CommandHandlerRawRegistration,
        diagnostics: MutableList<PluginV2CompilerDiagnostic>,
        duplicateGuard: MutableSet<String>,
        autoCounters: MutableMap<String, Int>,
        stageBuckets: MutableMap<PluginV2InternalStage, MutableList<String>>,
    ): PluginV2CompiledCommandHandler? {
        val identity = compileIdentity(
            pluginId = registration.pluginId,
            registrationKind = REGISTRATION_KIND_COMMAND,
            requestedRegistrationKey = registration.registrationKey,
            autoCounters = autoCounters,
            diagnostics = diagnostics,
            duplicateGuard = duplicateGuard,
        ) ?: return null
        val compiled = PluginV2CompiledCommandHandler(
            pluginId = registration.pluginId,
            registrationKind = REGISTRATION_KIND_COMMAND,
            registrationKey = identity.registrationKey,
            normalizedRegistrationKey = identity.normalizedRegistrationKey,
            handlerId = identity.handlerId,
            callbackToken = registration.callbackToken,
            priority = registration.priority,
            filterAttachments = compileFilterAttachments(
                declaredFilters = registration.declaredFilters,
                normalizedRegistrationKey = identity.normalizedRegistrationKey,
            ),
            metadata = registration.metadata,
            sourceOrder = registration.sourceOrder,
            command = registration.descriptor.command,
            aliases = registration.descriptor.aliases,
            groupPath = registration.descriptor.groupPath,
        )
        stageBuckets.getOrPut(PluginV2InternalStage.Command) { mutableListOf() }
            .add(compiled.handlerId)
        return compiled
    }

    private fun compileRegex(
        registration: RegexHandlerRawRegistration,
        diagnostics: MutableList<PluginV2CompilerDiagnostic>,
        duplicateGuard: MutableSet<String>,
        autoCounters: MutableMap<String, Int>,
        stageBuckets: MutableMap<PluginV2InternalStage, MutableList<String>>,
    ): PluginV2CompiledRegexHandler? {
        val identity = compileIdentity(
            pluginId = registration.pluginId,
            registrationKind = REGISTRATION_KIND_REGEX,
            requestedRegistrationKey = registration.registrationKey,
            autoCounters = autoCounters,
            diagnostics = diagnostics,
            duplicateGuard = duplicateGuard,
        ) ?: return null
        val compiled = PluginV2CompiledRegexHandler(
            pluginId = registration.pluginId,
            registrationKind = REGISTRATION_KIND_REGEX,
            registrationKey = identity.registrationKey,
            normalizedRegistrationKey = identity.normalizedRegistrationKey,
            handlerId = identity.handlerId,
            callbackToken = registration.callbackToken,
            priority = registration.priority,
            filterAttachments = compileFilterAttachments(
                declaredFilters = registration.declaredFilters,
                normalizedRegistrationKey = identity.normalizedRegistrationKey,
            ),
            metadata = registration.metadata,
            sourceOrder = registration.sourceOrder,
            pattern = registration.descriptor.pattern,
            flags = registration.descriptor.flags,
        )
        stageBuckets.getOrPut(PluginV2InternalStage.Regex) { mutableListOf() }
            .add(compiled.handlerId)
        return compiled
    }

    private fun compileLifecycle(
        registration: LifecycleHandlerRawRegistration,
        diagnostics: MutableList<PluginV2CompilerDiagnostic>,
        duplicateGuard: MutableSet<String>,
        autoCounters: MutableMap<String, Int>,
        stageBuckets: MutableMap<PluginV2InternalStage, MutableList<String>>,
    ): PluginV2CompiledLifecycleHandler? {
        val identity = compileIdentity(
            pluginId = registration.pluginId,
            registrationKind = REGISTRATION_KIND_LIFECYCLE,
            requestedRegistrationKey = registration.registrationKey,
            autoCounters = autoCounters,
            diagnostics = diagnostics,
            duplicateGuard = duplicateGuard,
        ) ?: return null
        val compiled = PluginV2CompiledLifecycleHandler(
            pluginId = registration.pluginId,
            registrationKind = REGISTRATION_KIND_LIFECYCLE,
            registrationKey = identity.registrationKey,
            normalizedRegistrationKey = identity.normalizedRegistrationKey,
            handlerId = identity.handlerId,
            callbackToken = registration.callbackToken,
            priority = registration.priority,
            filterAttachments = compileFilterAttachments(
                declaredFilters = registration.declaredFilters,
                normalizedRegistrationKey = identity.normalizedRegistrationKey,
            ),
            metadata = registration.metadata,
            sourceOrder = registration.sourceOrder,
            hook = registration.descriptor.hook,
        )
        stageBuckets.getOrPut(PluginV2InternalStage.Lifecycle) { mutableListOf() }
            .add(compiled.handlerId)
        return compiled
    }

    private fun compileLlmHook(
        registration: LlmHookRawRegistration,
        diagnostics: MutableList<PluginV2CompilerDiagnostic>,
        duplicateGuard: MutableSet<String>,
        autoCounters: MutableMap<String, Int>,
        stageBuckets: MutableMap<PluginV2InternalStage, MutableList<String>>,
    ): PluginV2CompiledLlmHook? {
        val identity = compileIdentity(
            pluginId = registration.pluginId,
            registrationKind = REGISTRATION_KIND_LLM_HOOK,
            requestedRegistrationKey = registration.registrationKey,
            autoCounters = autoCounters,
            diagnostics = diagnostics,
            duplicateGuard = duplicateGuard,
        ) ?: return null
        val compiled = PluginV2CompiledLlmHook(
            pluginId = registration.pluginId,
            registrationKind = REGISTRATION_KIND_LLM_HOOK,
            registrationKey = identity.registrationKey,
            normalizedRegistrationKey = identity.normalizedRegistrationKey,
            handlerId = identity.handlerId,
            callbackToken = registration.callbackToken,
            priority = registration.priority,
            filterAttachments = compileFilterAttachments(
                declaredFilters = registration.declaredFilters,
                normalizedRegistrationKey = identity.normalizedRegistrationKey,
            ),
            metadata = registration.metadata,
            sourceOrder = registration.sourceOrder,
            hook = registration.descriptor.hook,
        )
        stageBuckets.getOrPut(PluginV2InternalStage.LlmRequest) { mutableListOf() }
            .add(compiled.handlerId)
        return compiled
    }

    private fun compileTool(
        registration: ToolRawRegistration,
        diagnostics: MutableList<PluginV2CompilerDiagnostic>,
        duplicateGuard: MutableSet<String>,
        autoCounters: MutableMap<String, Int>,
        stageBuckets: MutableMap<PluginV2InternalStage, MutableList<String>>,
    ): PluginV2CompiledTool? {
        val identity = compileIdentity(
            pluginId = registration.pluginId,
            registrationKind = REGISTRATION_KIND_TOOL,
            requestedRegistrationKey = registration.registrationKey,
            autoCounters = autoCounters,
            diagnostics = diagnostics,
            duplicateGuard = duplicateGuard,
        ) ?: return null
        val compiled = PluginV2CompiledTool(
            pluginId = registration.pluginId,
            registrationKind = REGISTRATION_KIND_TOOL,
            registrationKey = identity.registrationKey,
            normalizedRegistrationKey = identity.normalizedRegistrationKey,
            handlerId = identity.handlerId,
            callbackToken = registration.callbackToken,
            priority = registration.priority,
            filterAttachments = compileFilterAttachments(
                declaredFilters = registration.declaredFilters,
                normalizedRegistrationKey = identity.normalizedRegistrationKey,
            ),
            metadata = registration.metadata,
            sourceOrder = registration.sourceOrder,
            toolDescriptor = registration.descriptor.toolDescriptor,
        )
        stageBuckets.getOrPut(PluginV2InternalStage.ToolUse) { mutableListOf() }
            .add(compiled.handlerId)
        return compiled
    }

    private fun compileToolLifecycleHook(
        registration: ToolLifecycleHookRawRegistration,
        diagnostics: MutableList<PluginV2CompilerDiagnostic>,
        duplicateGuard: MutableSet<String>,
        autoCounters: MutableMap<String, Int>,
        stageBuckets: MutableMap<PluginV2InternalStage, MutableList<String>>,
    ): PluginV2CompiledToolLifecycleHook? {
        val identity = compileIdentity(
            pluginId = registration.pluginId,
            registrationKind = REGISTRATION_KIND_TOOL_LIFECYCLE_HOOK,
            requestedRegistrationKey = registration.registrationKey,
            autoCounters = autoCounters,
            diagnostics = diagnostics,
            duplicateGuard = duplicateGuard,
        ) ?: return null
        val compiled = PluginV2CompiledToolLifecycleHook(
            pluginId = registration.pluginId,
            registrationKind = REGISTRATION_KIND_TOOL_LIFECYCLE_HOOK,
            registrationKey = identity.registrationKey,
            normalizedRegistrationKey = identity.normalizedRegistrationKey,
            handlerId = identity.handlerId,
            callbackToken = registration.callbackToken,
            priority = registration.priority,
            filterAttachments = compileFilterAttachments(
                declaredFilters = registration.declaredFilters,
                normalizedRegistrationKey = identity.normalizedRegistrationKey,
            ),
            metadata = registration.metadata,
            sourceOrder = registration.sourceOrder,
            hook = registration.descriptor.hook,
        )
        stageBuckets.getOrPut(PluginV2InternalStage.ToolRespond) { mutableListOf() }
            .add(compiled.handlerId)
        return compiled
    }

    private fun compileIdentity(
        pluginId: String,
        registrationKind: String,
        requestedRegistrationKey: String?,
        autoCounters: MutableMap<String, Int>,
        diagnostics: MutableList<PluginV2CompilerDiagnostic>,
        duplicateGuard: MutableSet<String>,
    ): CompiledIdentity? {
        val normalizedRequestedKey = requestedRegistrationKey?.trim()
        val registrationKey = when {
            normalizedRequestedKey == null -> nextAutoRegistrationKey(registrationKind, autoCounters)
            normalizedRequestedKey.isEmpty() -> {
                diagnostics += PluginV2CompilerDiagnostic(
                    severity = DiagnosticSeverity.Error,
                    code = "invalid_registration_key",
                    message = "registrationKey must not be blank.",
                    pluginId = pluginId,
                    registrationKind = registrationKind,
                    registrationKey = requestedRegistrationKey,
                )
                return null
            }

            REGISTRATION_KEY_PATTERN.matches(normalizedRequestedKey).not() -> {
                diagnostics += PluginV2CompilerDiagnostic(
                    severity = DiagnosticSeverity.Error,
                    code = "invalid_registration_key",
                    message = "registrationKey contains unsupported characters: $normalizedRequestedKey",
                    pluginId = pluginId,
                    registrationKind = registrationKind,
                    registrationKey = normalizedRequestedKey,
                )
                return null
            }

            else -> normalizedRequestedKey
        }

        val normalizedRegistrationKey = "$pluginId/$registrationKind/$registrationKey"
        if (duplicateGuard.add(normalizedRegistrationKey).not()) {
            diagnostics += PluginV2CompilerDiagnostic(
                severity = DiagnosticSeverity.Error,
                code = "duplicate_normalized_registration_key",
                message = "Duplicate normalized registration key detected: $normalizedRegistrationKey",
                pluginId = pluginId,
                registrationKind = registrationKind,
                registrationKey = registrationKey,
            )
            return null
        }

        return CompiledIdentity(
            registrationKey = registrationKey,
            normalizedRegistrationKey = normalizedRegistrationKey,
            handlerId = "hdl::$pluginId::$registrationKind::$registrationKey",
        )
    }

    private fun compileFilterAttachments(
        declaredFilters: List<BootstrapFilterDescriptor>,
        normalizedRegistrationKey: String,
    ): List<PluginV2CompiledFilterAttachment> {
        return declaredFilters.map { filter ->
            PluginV2CompiledFilterAttachment(
                kind = filter.kind,
                arguments = mapOf("value" to filter.value.trim()),
                sourceRegistrationKey = normalizedRegistrationKey,
            )
        }
    }

    private fun nextAutoRegistrationKey(
        registrationKind: String,
        autoCounters: MutableMap<String, Int>,
    ): String {
        val next = (autoCounters[registrationKind] ?: 0) + 1
        autoCounters[registrationKind] = next
        val prefix = AUTO_KEY_PREFIX_BY_KIND[registrationKind] ?: "auto-$registrationKind"
        return "%s-%04d".format(prefix, next)
    }

    private data class CompiledIdentity(
        val registrationKey: String,
        val normalizedRegistrationKey: String,
        val handlerId: String,
    )

    private companion object {
        private const val REGISTRATION_KIND_MESSAGE = "message"
        private const val REGISTRATION_KIND_COMMAND = "command"
        private const val REGISTRATION_KIND_REGEX = "regex"
        private const val REGISTRATION_KIND_LIFECYCLE = "lifecycle"
        private const val REGISTRATION_KIND_LLM_HOOK = "llm_hook"
        private const val REGISTRATION_KIND_TOOL = "tool"
        private const val REGISTRATION_KIND_TOOL_LIFECYCLE_HOOK = "tool_lifecycle_hook"

        private val REGISTRATION_KEY_PATTERN = Regex("^[A-Za-z0-9._-]+$")

        private val AUTO_KEY_PREFIX_BY_KIND = mapOf(
            REGISTRATION_KIND_MESSAGE to "auto-message",
            REGISTRATION_KIND_COMMAND to "auto-command",
            REGISTRATION_KIND_REGEX to "auto-regex",
            REGISTRATION_KIND_LIFECYCLE to "auto-lifecycle",
            REGISTRATION_KIND_LLM_HOOK to "auto-llm-hook",
            REGISTRATION_KIND_TOOL to "auto-tool",
            REGISTRATION_KIND_TOOL_LIFECYCLE_HOOK to "auto-tool-lifecycle-hook",
        )
    }

    private fun publishCompiled(
        pluginId: String,
        compiledRegistry: PluginV2CompiledRegistrySnapshot,
        diagnostics: List<PluginV2CompilerDiagnostic>,
    ) {
        logBus.publishBootstrapRecord(
            pluginId = pluginId,
            pluginVersion = "",
            occurredAtEpochMillis = clock(),
            level = PluginRuntimeLogLevel.Info,
            code = "bootstrap_compiled",
            message = "Plugin v2 registry compiled.",
            metadata = linkedMapOf(
                "handlerCount" to compiledRegistry.handlerRegistry.totalHandlerCount.toString(),
                "warningCount" to diagnostics.count { it.severity == DiagnosticSeverity.Warning }.toString(),
            ),
        )
    }

    private fun publishCompileFailed(
        pluginId: String,
        diagnostics: List<PluginV2CompilerDiagnostic>,
    ) {
        diagnostics.forEach { diagnostic ->
            logBus.publishBootstrapRecord(
                pluginId = pluginId,
                pluginVersion = "",
                occurredAtEpochMillis = clock(),
                level = when (diagnostic.severity) {
                    DiagnosticSeverity.Error -> PluginRuntimeLogLevel.Error
                    DiagnosticSeverity.Warning -> PluginRuntimeLogLevel.Warning
                },
                code = "runtime_diagnostic_feedback",
                message = diagnostic.message,
                metadata = linkedMapOf(
                    "diagnosticCode" to diagnostic.code,
                    "severity" to diagnostic.severity.name.lowercase(),
                ).also { metadata ->
                    diagnostic.registrationKind?.let { metadata["registrationKind"] = it }
                    diagnostic.registrationKey?.let { metadata["registrationKey"] = it }
                },
            )
        }

        logBus.publishBootstrapRecord(
            pluginId = pluginId,
            pluginVersion = "",
            occurredAtEpochMillis = clock(),
            level = PluginRuntimeLogLevel.Error,
            code = "bootstrap_compile_failed",
            message = "Plugin v2 registry compilation failed.",
            metadata = linkedMapOf(
                "errorCount" to diagnostics.count { it.severity == DiagnosticSeverity.Error }.toString(),
                "warningCount" to diagnostics.count { it.severity == DiagnosticSeverity.Warning }.toString(),
            ),
        )
    }
}
