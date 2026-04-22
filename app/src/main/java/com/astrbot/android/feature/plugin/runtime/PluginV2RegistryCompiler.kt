package com.astrbot.android.feature.plugin.runtime

import com.astrbot.android.model.plugin.PluginRuntimeLogLevel

typealias DiagnosticSeverity = com.astrbot.android.model.plugin.DiagnosticSeverity
typealias PluginV2CompilerDiagnostic = com.astrbot.android.model.plugin.PluginV2CompilerDiagnostic

data class PluginV2RegistryCompileResult(
    val compiledRegistry: PluginV2CompiledRegistrySnapshot?,
    val diagnostics: List<PluginV2CompilerDiagnostic>,
)

data class PluginV2CommandRegistryMergeResult(
    val commandRegistry: PluginV2HandlerRegistry?,
    val diagnostics: List<PluginV2CompilerDiagnostic>,
)

class PluginV2RegistryCompiler(
    private val logBus: PluginRuntimeLogBus = InMemoryPluginRuntimeLogBus(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun compile(rawRegistry: PluginV2RawRegistry): PluginV2RegistryCompileResult {
        val diagnostics = mutableListOf<PluginV2CompilerDiagnostic>()
        val duplicateGuard = linkedSetOf<String>()
        val autoCounters = linkedMapOf<String, Int>()
        val stageBuckets = linkedMapOf<PluginV2InternalStage, MutableList<String>>()
        val commandCompilation = CommandCompilationState()

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
                    commandCompilation = commandCompilation,
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
        val llmHookHandlers = rawRegistry.llmHooks
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
        diagnostics += inactivePhaseDiagnostics(
            pluginId = rawRegistry.pluginId,
            registrations = rawRegistry.tools.map { registration ->
                InactivePhaseRegistration(
                    registrationKind = REGISTRATION_KIND_TOOL,
                    registrationKey = registration.registrationKey,
                )
            } + rawRegistry.toolLifecycleHooks.map { registration ->
                InactivePhaseRegistration(
                    registrationKind = REGISTRATION_KIND_TOOL_LIFECYCLE_HOOK,
                    registrationKey = registration.registrationKey,
                )
            },
        )

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
            commandBuckets = commandCompilation.buildBuckets(),
            commandAliasIndex = commandCompilation.buildAliasIndex(),
            regexHandlers = regexHandlers,
            lifecycleHandlers = lifecycleHandlers,
            llmHookHandlers = llmHookHandlers,
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
        commandCompilation: CommandCompilationState,
    ): PluginV2CompiledCommandHandler? {
        val identity = compileIdentity(
            pluginId = registration.pluginId,
            registrationKind = REGISTRATION_KIND_COMMAND,
            requestedRegistrationKey = registration.registrationKey,
            autoCounters = autoCounters,
            diagnostics = diagnostics,
            duplicateGuard = duplicateGuard,
        ) ?: return null
        val commandPath = registration.descriptor.groupPath + registration.descriptor.command
        val commandPathKey = commandPath.toCommandPathKey()
        if (commandCompilation.registerCanonicalPath(commandPathKey, commandPathKey, registration.pluginId, diagnostics).not()) {
            return null
        }

        val aliasPaths = registration.descriptor.aliases
            .map(String::toCommandPathTokensFromText)
            .filter(List<String>::isNotEmpty)
        for (aliasPath in aliasPaths) {
            val aliasPathKey = aliasPath.toCommandPathKey()
            if (commandCompilation.registerAliasPath(aliasPathKey, commandPathKey, registration.pluginId, diagnostics).not()) {
                return null
            }
        }
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
            commandPath = commandPath,
            aliasPaths = aliasPaths,
        )
        commandCompilation.appendBucket(commandPathKey, commandPath, aliasPaths, compiled)
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
        val compiledPattern = runCatching {
            Regex(registration.descriptor.pattern, regexOptionsFor(registration.descriptor.flags))
        }.getOrElse { error ->
            diagnostics += PluginV2CompilerDiagnostic(
                severity = DiagnosticSeverity.Error,
                code = "invalid_regex_pattern",
                message = "Invalid regex pattern: ${registration.descriptor.pattern}. ${error.message ?: error.javaClass.simpleName}",
                pluginId = registration.pluginId,
                registrationKind = REGISTRATION_KIND_REGEX,
                registrationKey = identity.registrationKey,
            )
            return null
        }
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
            compiledPattern = compiledPattern,
            namedGroupNames = extractNamedGroupNames(registration.descriptor.pattern),
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
    ): PluginV2CompiledLlmHookHandler? {
        val identity = compileIdentity(
            pluginId = registration.pluginId,
            registrationKind = REGISTRATION_KIND_LLM_HOOK,
            requestedRegistrationKey = registration.registrationKey,
            autoCounters = autoCounters,
            diagnostics = diagnostics,
            duplicateGuard = duplicateGuard,
        ) ?: return null
        val hook = registration.descriptor.hook.trim()
        val surface = PluginV2LlmHookSurface.fromWireValue(hook)
        if (surface == null) {
            diagnostics += PluginV2CompilerDiagnostic(
                severity = DiagnosticSeverity.Error,
                code = "invalid_llm_hook_surface",
                message = "Unsupported llm hook surface: $hook",
                pluginId = registration.pluginId,
                registrationKind = REGISTRATION_KIND_LLM_HOOK,
                registrationKey = identity.registrationKey,
            )
            return null
        }
        val compiled = PluginV2CompiledLlmHookHandler(
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
            hook = hook,
            surface = surface,
        )
        stageBuckets.getOrPut(surface.stage) { mutableListOf() }
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

    private data class InactivePhaseRegistration(
        val registrationKind: String,
        val registrationKey: String?,
    )

    private class CommandCompilationState {
        private val pathIndexByKey = linkedMapOf<String, String>()
        private val commandPathsByKey = linkedMapOf<String, List<String>>()
        private val commandAliasPathsByKey = linkedMapOf<String, LinkedHashSet<String>>()
        private val commandBucketsByKey = linkedMapOf<String, MutableList<PluginV2CompiledCommandHandler>>()

        fun registerCanonicalPath(
            commandPathKey: String,
            canonicalPathKey: String,
            pluginId: String,
            diagnostics: MutableList<PluginV2CompilerDiagnostic>,
        ): Boolean {
            val existingCanonicalKey = pathIndexByKey[commandPathKey]
            return when {
                existingCanonicalKey == null -> {
                    pathIndexByKey[commandPathKey] = canonicalPathKey
                    commandPathsByKey[commandPathKey] = commandPathKey.toCommandPathTokens()
                    true
                }

                existingCanonicalKey == canonicalPathKey -> {
                    diagnostics += PluginV2CompilerDiagnostic(
                        severity = DiagnosticSeverity.Error,
                        code = "duplicate_canonical_command_key",
                        message = "Duplicate canonical command key detected: $commandPathKey",
                        pluginId = pluginId,
                        registrationKind = REGISTRATION_KIND_COMMAND,
                        registrationKey = commandPathKey,
                    )
                    false
                }

                else -> {
                    diagnostics += PluginV2CompilerDiagnostic(
                        severity = DiagnosticSeverity.Error,
                        code = "alias_chain_conflict",
                        message = "Alias chain conflicts with canonical command key: $commandPathKey",
                        pluginId = pluginId,
                        registrationKind = REGISTRATION_KIND_COMMAND,
                        registrationKey = commandPathKey,
                    )
                    false
                }
            }
        }

        fun registerAliasPath(
            aliasPathKey: String,
            canonicalPathKey: String,
            pluginId: String,
            diagnostics: MutableList<PluginV2CompilerDiagnostic>,
        ): Boolean {
            val existingCanonicalKey = pathIndexByKey[aliasPathKey]
            return when {
                existingCanonicalKey == null -> {
                    pathIndexByKey[aliasPathKey] = canonicalPathKey
                    true
                }

                existingCanonicalKey == canonicalPathKey -> true

                else -> {
                    diagnostics += PluginV2CompilerDiagnostic(
                        severity = DiagnosticSeverity.Error,
                        code = "alias_chain_conflict",
                        message = "Alias chain conflicts with canonical command key: $aliasPathKey",
                        pluginId = pluginId,
                        registrationKind = REGISTRATION_KIND_COMMAND,
                        registrationKey = aliasPathKey,
                    )
                    false
                }
            }
        }

        fun appendBucket(
            commandPathKey: String,
            commandPath: List<String>,
            aliasPaths: List<List<String>>,
            handler: PluginV2CompiledCommandHandler,
        ) {
            commandPathsByKey[commandPathKey] = commandPath.toList()
            commandAliasPathsByKey.getOrPut(commandPathKey) { linkedSetOf() }
                .addAll(aliasPaths.map(List<String>::toCommandPathKey))
            commandBucketsByKey.getOrPut(commandPathKey) { mutableListOf() }
                .add(handler)
        }

        fun buildBuckets(): List<PluginV2CommandBucket> {
            return commandBucketsByKey.entries.map { (commandPathKey, handlers) ->
                PluginV2CommandBucket(
                    commandPath = commandPathsByKey[commandPathKey].orEmpty(),
                    commandPathKey = commandPathKey,
                    handlers = handlers.sortedForCommandDispatch(),
                    aliasPaths = commandAliasPathsByKey[commandPathKey]
                        .orEmpty()
                        .map(String::toCommandPathTokens)
                        .toList(),
                )
            }
        }

        fun buildAliasIndex(): Map<String, String> {
            return LinkedHashMap(pathIndexByKey).toMap()
        }
    }

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

    private fun inactivePhaseDiagnostics(
        pluginId: String,
        registrations: List<InactivePhaseRegistration>,
    ): List<PluginV2CompilerDiagnostic> {
        return registrations.map { registration ->
            PluginV2CompilerDiagnostic(
                severity = DiagnosticSeverity.Warning,
                code = "inactive_phase_registration_ignored",
                message = "Ignoring ${registration.registrationKind} registration until a later phase is enabled.",
                pluginId = pluginId,
                registrationKind = registration.registrationKind,
                registrationKey = registration.registrationKey,
            )
        }
    }

    private fun regexOptionsFor(flags: Set<String>): Set<RegexOption> {
        val options = linkedSetOf<RegexOption>()
        flags.forEach { flag ->
            when (flag.uppercase()) {
                "IGNORE_CASE" -> options += RegexOption.IGNORE_CASE
                "MULTILINE" -> options += RegexOption.MULTILINE
                "DOT_MATCHES_ALL" -> options += RegexOption.DOT_MATCHES_ALL
            }
        }
        return options
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

internal fun mergeCommandRegistries(
    registries: Collection<PluginV2HandlerRegistry>,
): PluginV2CommandRegistryMergeResult {
    val diagnostics = mutableListOf<PluginV2CompilerDiagnostic>()
    val pathIndexByKey = linkedMapOf<String, String>()
    val commandPathsByKey = linkedMapOf<String, List<String>>()
    val commandBucketsByKey = linkedMapOf<String, MutableList<PluginV2CompiledCommandHandler>>()
    val commandAliasPathsByKey = linkedMapOf<String, LinkedHashSet<String>>()

    registries.forEach { registry ->
        registry.commandBuckets.forEach { bucket ->
            val canonicalKey = bucket.commandPathKey
            val existingCanonicalTarget = pathIndexByKey[canonicalKey]
            when {
                existingCanonicalTarget == null -> {
                    pathIndexByKey[canonicalKey] = canonicalKey
                    commandPathsByKey[canonicalKey] = bucket.commandPath.toList()
                }

                existingCanonicalTarget == canonicalKey -> Unit

                else -> {
                    diagnostics += PluginV2CompilerDiagnostic(
                        severity = DiagnosticSeverity.Error,
                        code = "alias_chain_conflict",
                        message = "Alias chain conflicts with canonical command key: $canonicalKey",
                        pluginId = bucket.handlers.firstOrNull()?.pluginId.orEmpty(),
                        registrationKind = "command",
                        registrationKey = canonicalKey,
                    )
                }
            }

            commandBucketsByKey.getOrPut(canonicalKey) { mutableListOf() }
                .addAll(bucket.handlers)
            commandAliasPathsByKey.getOrPut(canonicalKey) { linkedSetOf() }
                .addAll(bucket.aliasPaths.map(List<String>::toCommandPathKey))
        }

        registry.commandAliasIndex.forEach { (pathKey, canonicalKey) ->
            val existingCanonicalTarget = pathIndexByKey[pathKey]
            when {
                existingCanonicalTarget == null -> {
                    pathIndexByKey[pathKey] = canonicalKey
                }

                existingCanonicalTarget == canonicalKey -> Unit

                else -> {
                    diagnostics += PluginV2CompilerDiagnostic(
                        severity = DiagnosticSeverity.Error,
                        code = "alias_chain_conflict",
                        message = "Alias chain conflicts with canonical command key: $pathKey",
                        pluginId = registry.commandBuckets.firstOrNull()?.handlers?.firstOrNull()?.pluginId.orEmpty(),
                        registrationKind = "command",
                        registrationKey = pathKey,
                    )
                }
            }
        }
    }

    if (diagnostics.any { it.severity == DiagnosticSeverity.Error }) {
        return PluginV2CommandRegistryMergeResult(
            commandRegistry = null,
            diagnostics = diagnostics.toList(),
        )
    }

    val mergedBuckets = commandBucketsByKey.entries.map { (canonicalKey, handlers) ->
        PluginV2CommandBucket(
            commandPath = commandPathsByKey[canonicalKey].orEmpty(),
            commandPathKey = canonicalKey,
            handlers = handlers.sortedForCommandDispatch(),
            aliasPaths = commandAliasPathsByKey[canonicalKey]
                .orEmpty()
                .map(String::toCommandPathTokens)
                .toList(),
        )
    }

    return PluginV2CommandRegistryMergeResult(
        commandRegistry = PluginV2HandlerRegistry(
            commandBuckets = mergedBuckets,
            commandAliasIndex = LinkedHashMap(pathIndexByKey).toMap(),
        ),
        diagnostics = diagnostics.toList(),
    )
}
