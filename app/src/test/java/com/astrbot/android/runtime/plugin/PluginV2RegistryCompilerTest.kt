package com.astrbot.android.runtime.plugin

import com.astrbot.android.model.plugin.projectRegisteredLlmHooks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginV2RegistryCompilerTest {
    @Test
    fun compiler_publishes_bootstrap_compiled_hook_on_success() {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 100L })
        val compiler = PluginV2RegistryCompiler(
            logBus = logBus,
            clock = { 100L },
        )
        val rawRegistry = rawRegistryWithMessageHandlers(
            messageHandlers = listOf(
                messageHandler(
                    registrationKey = "compiled.ok",
                ),
            ),
        )

        val result = compiler.compile(rawRegistry)

        assertTrue(result.compiledRegistry != null)
        val record = logBus.snapshot(limit = 10).firstOrNull { it.code == "bootstrap_compiled" }
        assertTrue(record != null)
        assertEquals("com.example.v2.compiler", record?.pluginId)
    }

    @Test
    fun compiler_publishes_bootstrap_compile_failed_hook_when_errors_exist() {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 101L })
        val compiler = PluginV2RegistryCompiler(
            logBus = logBus,
            clock = { 101L },
        )
        val rawRegistry = rawRegistryWithMessageHandlers(
            messageHandlers = listOf(
                messageHandler(registrationKey = "dup"),
                messageHandler(registrationKey = "dup"),
            ),
        )

        val result = compiler.compile(rawRegistry)

        assertNull(result.compiledRegistry)
        val record = logBus.snapshot(limit = 10).firstOrNull { it.code == "bootstrap_compile_failed" }
        assertTrue(record != null)
        assertEquals("com.example.v2.compiler", record?.pluginId)
        assertTrue(result.diagnostics.any { it.severity == DiagnosticSeverity.Error })
    }

    @Test
    fun duplicate_normalized_registration_key_produces_compiler_error() {
        val compiler = PluginV2RegistryCompiler()
        val rawRegistry = rawRegistryWithMessageHandlers(
            messageHandlers = listOf(
                messageHandler(
                    registrationKey = "shared.key",
                    priority = 10,
                ),
                messageHandler(
                    registrationKey = "shared.key",
                    priority = 20,
                ),
            ),
        )

        val result = compiler.compile(rawRegistry)

        assertNull(result.compiledRegistry)
        assertTrue(result.diagnostics.any { it.code == "duplicate_normalized_registration_key" })
        assertTrue(result.diagnostics.any { it.severity == DiagnosticSeverity.Error })
    }

    @Test
    fun same_short_key_across_different_registration_kinds_is_allowed() {
        val compiler = PluginV2RegistryCompiler()
        val rawRegistry = rawRegistryWithMessageHandlers(
            messageHandlers = listOf(
                messageHandler(
                    registrationKey = "shared.key",
                    priority = 10,
                ),
            ),
            commandHandlers = listOf(
                commandHandler(
                    registrationKey = "shared.key",
                    command = "/echo",
                    priority = 10,
                ),
            ),
        )

        val result = compiler.compile(rawRegistry)

        assertTrue(result.diagnostics.none { it.severity == DiagnosticSeverity.Error })
        assertTrue(result.compiledRegistry != null)
        val compiledRegistry = result.compiledRegistry!!

        assertEquals(
            listOf("com.example.v2.compiler/message/shared.key"),
            compiledRegistry.handlerRegistry.messageHandlers.map { it.normalizedRegistrationKey },
        )
        assertEquals(
            listOf("com.example.v2.compiler/command/shared.key"),
            compiledRegistry.handlerRegistry.commandHandlers.map { it.normalizedRegistrationKey },
        )
    }

    @Test
    fun handler_id_assignment_rule_is_stable_for_explicit_and_auto_generated_keys() {
        val compiler = PluginV2RegistryCompiler()
        val rawRegistry = rawRegistryWithMessageHandlers(
            messageHandlers = listOf(
                messageHandler(
                    registrationKey = null,
                    priority = 30,
                    declaredFilters = listOf(
                        BootstrapFilterDescriptor.message("  adapter-message  "),
                        BootstrapFilterDescriptor.command(" /echo "),
                    ),
                ),
                messageHandler(
                    registrationKey = "explicit.alpha",
                    priority = 10,
                ),
            ),
            commandHandlers = listOf(
                commandHandler(
                    registrationKey = null,
                    command = "/echo",
                    priority = 5,
                ),
            ),
        )

        val result = compiler.compile(rawRegistry)

        assertTrue(result.diagnostics.none { it.severity == DiagnosticSeverity.Error })
        val compiledRegistry = result.compiledRegistry!!

        assertEquals(
            listOf(
                "hdl::com.example.v2.compiler::message::auto-message-0001",
                "hdl::com.example.v2.compiler::message::explicit.alpha",
            ),
            compiledRegistry.handlerRegistry.messageHandlers.map { it.handlerId },
        )
        assertEquals(
            "com.example.v2.compiler/message/auto-message-0001",
            compiledRegistry.handlerRegistry.messageHandlers.first().normalizedRegistrationKey,
        )
        assertEquals(
            "auto-message-0001",
            compiledRegistry.handlerRegistry.messageHandlers.first().registrationKey,
        )
        assertEquals(
            listOf("hdl::com.example.v2.compiler::command::auto-command-0001"),
            compiledRegistry.handlerRegistry.commandHandlers.map { it.handlerId },
        )
        assertEquals(
            listOf(
                "hdl::com.example.v2.compiler::message::auto-message-0001",
                "hdl::com.example.v2.compiler::message::explicit.alpha",
            ),
            compiledRegistry.dispatchIndex.handlerIdsByStage[PluginV2InternalStage.AdapterMessage],
        )
        assertEquals(
            listOf("hdl::com.example.v2.compiler::command::auto-command-0001"),
            compiledRegistry.dispatchIndex.handlerIdsByStage[PluginV2InternalStage.Command],
        )
    }

    @Test
    fun compiler_preserves_structured_filter_attachments_without_executing_them() {
        val compiler = PluginV2RegistryCompiler()
        val rawRegistry = rawRegistryWithMessageHandlers(
            messageHandlers = listOf(
                messageHandler(
                    registrationKey = "filters.keep",
                    declaredFilters = listOf(
                        BootstrapFilterDescriptor.message("  adapter-message  "),
                        BootstrapFilterDescriptor.command(" /echo "),
                    ),
                ),
            ),
        )

        val result = compiler.compile(rawRegistry)

        val compiledHandler = result.compiledRegistry!!.handlerRegistry.messageHandlers.single()
        assertEquals(2, compiledHandler.filterAttachments.size)
        assertEquals(BootstrapFilterKind.Message, compiledHandler.filterAttachments[0].kind)
        assertEquals(mapOf("value" to "adapter-message"), compiledHandler.filterAttachments[0].arguments)
        assertEquals(BootstrapFilterKind.Command, compiledHandler.filterAttachments[1].kind)
        assertEquals(mapOf("value" to "/echo"), compiledHandler.filterAttachments[1].arguments)
        assertFalse(result.diagnostics.any { it.code == "filter_execution" })
    }

    @Test
    fun invalid_regex_pattern_is_rejected_during_compilation() {
        val compiler = PluginV2RegistryCompiler()
        val rawRegistry = rawRegistryWithRegexHandlers(
            regexHandlers = listOf(
                regexHandler(
                    registrationKey = "regex.invalid",
                    pattern = "(?<label>broken",
                ),
            ),
        )

        val result = compiler.compile(rawRegistry)

        assertNull(result.compiledRegistry)
        assertTrue(result.diagnostics.any { it.code == "invalid_regex_pattern" })
        assertTrue(result.diagnostics.any { it.severity == DiagnosticSeverity.Error })
    }

    @Test
    fun compiler_materializes_full_path_alias_chains_as_token_sequences() {
        val compiler = PluginV2RegistryCompiler()
        val rawRegistry = rawRegistryWithCommandHandlers(
            pluginId = "com.example.v2.alias",
            commandHandlers = listOf(
                commandHandler(
                    registrationKey = "alias.full",
                    command = "list",
                    aliases = listOf("astrbot plugin ls"),
                    groupPath = listOf("astrbot", "plugin"),
                ),
            ),
        )

        val result = compiler.compile(rawRegistry)

        assertTrue(result.diagnostics.none { it.severity == DiagnosticSeverity.Error })
        val compiledHandler = result.compiledRegistry!!.handlerRegistry.commandHandlers.single()
        val bucket = result.compiledRegistry!!.handlerRegistry.commandBuckets.single()

        assertEquals(listOf(listOf("astrbot", "plugin", "ls")), compiledHandler.aliasPaths)
        assertEquals(listOf(listOf("astrbot", "plugin", "ls")), bucket.aliasPaths)
        assertEquals(
            listOf("astrbot", "plugin", "list"),
            bucket.commandPath,
        )
        assertEquals(
            bucket.commandPathKey,
            result.compiledRegistry!!.handlerRegistry.commandAliasIndex[listOf("astrbot", "plugin", "ls").toCommandPathKey()],
        )
    }

    @Test
    fun duplicate_canonical_command_path_is_a_compile_error() {
        val compiler = PluginV2RegistryCompiler()
        val rawRegistry = rawRegistryWithCommandHandlers(
            pluginId = "com.example.v2.dup",
            commandHandlers = listOf(
                commandHandler(
                    registrationKey = "dup.one",
                    command = "list",
                    groupPath = listOf("astrbot", "plugin"),
                ),
                commandHandler(
                    registrationKey = "dup.two",
                    command = "list",
                    groupPath = listOf("astrbot", "plugin"),
                ),
            ),
        )

        val result = compiler.compile(rawRegistry)

        assertNull(result.compiledRegistry)
        assertTrue(result.diagnostics.any { it.code == "duplicate_canonical_command_key" })
        assertTrue(result.diagnostics.any { it.severity == DiagnosticSeverity.Error })
    }

    @Test
    fun shared_canonical_command_path_from_different_plugins_merges_into_one_bucket() {
        val compiler = PluginV2RegistryCompiler()
        val alpha = compiler.compile(
            rawRegistryWithCommandHandlers(
                pluginId = "com.example.v2.alpha",
                commandHandlers = listOf(
                    commandHandler(
                        registrationKey = "zeta.shared",
                        command = "list",
                        groupPath = listOf("astrbot", "plugin"),
                        priority = 5,
                    ),
                ),
            ),
        ).compiledRegistry as PluginV2CompiledRegistrySnapshot
        val beta = compiler.compile(
            rawRegistryWithCommandHandlers(
                pluginId = "com.example.v2.beta",
                commandHandlers = listOf(
                    commandHandler(
                        registrationKey = "alpha.shared",
                        command = "list",
                        groupPath = listOf("astrbot", "plugin"),
                        priority = 5,
                    ),
                ),
            ),
        ).compiledRegistry as PluginV2CompiledRegistrySnapshot

        val mergeResult = mergeCommandRegistries(listOf(beta.handlerRegistry, alpha.handlerRegistry))

        assertTrue(mergeResult.diagnostics.none { it.severity == DiagnosticSeverity.Error })
        val bucket = mergeResult.commandRegistry!!.commandBuckets.single()

        assertEquals(listOf("astrbot", "plugin", "list"), bucket.commandPath)
        assertEquals(2, bucket.handlers.size)
        assertEquals(
            listOf(
                "hdl::com.example.v2.alpha::command::zeta.shared",
                "hdl::com.example.v2.beta::command::alpha.shared",
            ),
            bucket.handlers.map { it.handlerId },
        )
    }

    @Test
    fun conflicting_alias_chain_is_rejected_when_command_registries_are_merged() {
        val compiler = PluginV2RegistryCompiler()
        val alpha = compiler.compile(
            rawRegistryWithCommandHandlers(
                pluginId = "com.example.v2.alpha",
                commandHandlers = listOf(
                    commandHandler(
                        registrationKey = "alias.alpha",
                        command = "list",
                        aliases = listOf("astrbot plugin ls"),
                        groupPath = listOf("astrbot", "plugin"),
                    ),
                ),
            ),
        ).compiledRegistry as PluginV2CompiledRegistrySnapshot
        val beta = compiler.compile(
            rawRegistryWithCommandHandlers(
                pluginId = "com.example.v2.beta",
                commandHandlers = listOf(
                    commandHandler(
                        registrationKey = "alias.beta",
                        command = "install",
                        aliases = listOf("astrbot plugin ls"),
                        groupPath = listOf("astrbot", "plugin"),
                    ),
                ),
            ),
        ).compiledRegistry as PluginV2CompiledRegistrySnapshot

        val mergeResult = mergeCommandRegistries(listOf(alpha.handlerRegistry, beta.handlerRegistry))

        assertNull(mergeResult.commandRegistry)
        assertTrue(mergeResult.diagnostics.any { it.code == "alias_chain_conflict" })
        assertTrue(mergeResult.diagnostics.any { it.severity == DiagnosticSeverity.Error })
    }

    @Test
    fun compiler_compiles_llm_hooks_into_registry_and_keeps_tool_surfaces_inactive() {
        val session = bootstrappedSession()
        val rawRegistry = PluginV2RawRegistry(session.pluginId)
        rawRegistry.appendLlmHook(
            callbackToken = session.allocateCallbackToken(PluginV2CallbackHandle {}),
            descriptor = LlmHookRegistrationInput(
                registrationKey = "llm.future",
                hook = "on_llm_request",
                handler = PluginV2CallbackHandle {},
            ),
        )
        rawRegistry.appendTool(
            callbackToken = session.allocateCallbackToken(PluginV2CallbackHandle {}),
            descriptor = ToolRegistrationInput(
                registrationKey = "tool.future",
                toolDescriptor = PluginV2ToolDescriptor(name = "futureTool"),
                handler = PluginV2CallbackHandle {},
            ),
        )
        rawRegistry.appendToolLifecycleHook(
            callbackToken = session.allocateCallbackToken(PluginV2CallbackHandle {}),
            descriptor = ToolLifecycleHookRegistrationInput(
                registrationKey = "tool.lifecycle.future",
                hook = "after_tool",
                handler = PluginV2CallbackHandle {},
            ),
        )

        val result = PluginV2RegistryCompiler().compile(rawRegistry)

        assertTrue(result.compiledRegistry != null)
        assertEquals(1, result.compiledRegistry!!.handlerRegistry.totalHandlerCount)
        assertEquals(1, result.compiledRegistry!!.handlerRegistry.llmHookHandlers.size)
        assertEquals(
            listOf("hdl::com.example.v2.compiler::llm_hook::llm.future"),
            result.compiledRegistry!!.dispatchIndex.handlerIdsByStage[PluginV2InternalStage.LlmRequest],
        )
        assertEquals(
            listOf(
                "inactive_phase_registration_ignored",
                "inactive_phase_registration_ignored",
            ),
            result.diagnostics.map { it.code },
        )
        assertTrue(result.diagnostics.all { it.severity == DiagnosticSeverity.Warning })
    }

    @Test
    fun plugin_v2_internal_stage_and_dispatch_index_include_all_phase4_llm_stages() {
        val session = bootstrappedSession()
        val rawRegistry = PluginV2RawRegistry(session.pluginId)
        rawRegistry.appendLlmHook(
            callbackToken = session.allocateCallbackToken(PluginV2CallbackHandle {}),
            descriptor = LlmHookRegistrationInput(
                registrationKey = "wait",
                hook = "on_waiting_llm_request",
                handler = PluginV2CallbackHandle {},
            ),
        )
        rawRegistry.appendLlmHook(
            callbackToken = session.allocateCallbackToken(PluginV2CallbackHandle {}),
            descriptor = LlmHookRegistrationInput(
                registrationKey = "request",
                hook = "on_llm_request",
                handler = PluginV2CallbackHandle {},
            ),
        )
        rawRegistry.appendLlmHook(
            callbackToken = session.allocateCallbackToken(PluginV2CallbackHandle {}),
            descriptor = LlmHookRegistrationInput(
                registrationKey = "response",
                hook = "on_llm_response",
                handler = PluginV2CallbackHandle {},
            ),
        )
        rawRegistry.appendLlmHook(
            callbackToken = session.allocateCallbackToken(PluginV2CallbackHandle {}),
            descriptor = LlmHookRegistrationInput(
                registrationKey = "decorating",
                hook = "on_decorating_result",
                handler = PluginV2CallbackHandle {},
            ),
        )
        rawRegistry.appendLlmHook(
            callbackToken = session.allocateCallbackToken(PluginV2CallbackHandle {}),
            descriptor = LlmHookRegistrationInput(
                registrationKey = "afterSent",
                hook = "after_message_sent",
                handler = PluginV2CallbackHandle {},
            ),
        )

        val result = PluginV2RegistryCompiler().compile(rawRegistry)
        val compiledRegistry = result.compiledRegistry!!

        assertTrue(result.diagnostics.none { it.severity == DiagnosticSeverity.Error })
        assertEquals(5, compiledRegistry.handlerRegistry.llmHookHandlers.size)
        assertEquals(
            setOf(
                PluginV2InternalStage.LlmWaiting,
                PluginV2InternalStage.LlmRequest,
                PluginV2InternalStage.LlmResponse,
                PluginV2InternalStage.ResultDecorating,
                PluginV2InternalStage.AfterMessageSent,
            ),
            compiledRegistry.dispatchIndex.handlerIdsByStage.keys,
        )
    }

    @Test
    fun governance_projection_reports_registered_llm_hooks_from_compiled_registry() {
        val session = bootstrappedSession()
        val rawRegistry = PluginV2RawRegistry(session.pluginId)
        rawRegistry.appendLlmHook(
            callbackToken = session.allocateCallbackToken(PluginV2CallbackHandle {}),
            descriptor = LlmHookRegistrationInput(
                registrationKey = "request.one",
                hook = "on_llm_request",
                handler = PluginV2CallbackHandle {},
            ),
        )
        rawRegistry.appendLlmHook(
            callbackToken = session.allocateCallbackToken(PluginV2CallbackHandle {}),
            descriptor = LlmHookRegistrationInput(
                registrationKey = "request.two",
                hook = "on_llm_request",
                handler = PluginV2CallbackHandle {},
            ),
        )
        rawRegistry.appendLlmHook(
            callbackToken = session.allocateCallbackToken(PluginV2CallbackHandle {}),
            descriptor = LlmHookRegistrationInput(
                registrationKey = "decorating.one",
                hook = "on_decorating_result",
                handler = PluginV2CallbackHandle {},
            ),
        )

        val compiledRegistry = PluginV2RegistryCompiler()
            .compile(rawRegistry)
            .compiledRegistry!!
        val projection = compiledRegistry.projectRegisteredLlmHooks()

        assertEquals(3, projection.totalCount)
        assertEquals(2, projection.byStage[PluginV2InternalStage.LlmRequest])
        assertEquals(1, projection.byStage[PluginV2InternalStage.ResultDecorating])
        assertTrue(
            projection.handlerIds.containsAll(
                compiledRegistry.handlerRegistry.llmHookHandlers.map { it.handlerId },
            ),
        )
    }

    private fun rawRegistryWithMessageHandlers(
        messageHandlers: List<MessageHandlerRegistrationInput> = emptyList(),
        commandHandlers: List<CommandHandlerRegistrationInput> = emptyList(),
    ): PluginV2RawRegistry {
        val session = bootstrappedSession()
        val rawRegistry = PluginV2RawRegistry(session.pluginId)

        messageHandlers.forEach { descriptor ->
            rawRegistry.appendMessageHandler(
                callbackToken = session.allocateCallbackToken(PluginV2CallbackHandle {}),
                descriptor = descriptor,
            )
        }
        commandHandlers.forEach { descriptor ->
            rawRegistry.appendCommandHandler(
                callbackToken = session.allocateCallbackToken(PluginV2CallbackHandle {}),
                descriptor = descriptor,
            )
        }
        return rawRegistry
    }

    private fun rawRegistryWithRegexHandlers(
        pluginId: String = "com.example.v2.compiler",
        regexHandlers: List<RegexHandlerRegistrationInput> = emptyList(),
    ): PluginV2RawRegistry {
        val session = PluginV2RuntimeSession(
            installRecord = samplePluginV2InstallRecord(
                pluginId = pluginId,
            ),
            sessionInstanceId = "session-$pluginId",
        ).also { runtimeSession ->
            runtimeSession.transitionTo(PluginV2RuntimeSessionState.Loading)
            runtimeSession.transitionTo(PluginV2RuntimeSessionState.BootstrapRunning)
        }
        val rawRegistry = PluginV2RawRegistry(session.pluginId)

        regexHandlers.forEach { descriptor ->
            rawRegistry.appendRegexHandler(
                callbackToken = session.allocateCallbackToken(PluginV2CallbackHandle {}),
                descriptor = descriptor,
            )
        }

        return rawRegistry
    }

    private fun rawRegistryWithCommandHandlers(
        pluginId: String,
        commandHandlers: List<CommandHandlerRegistrationInput> = emptyList(),
    ): PluginV2RawRegistry {
        val session = PluginV2RuntimeSession(
            installRecord = samplePluginV2InstallRecord(
                pluginId = pluginId,
            ),
            sessionInstanceId = "session-$pluginId",
        ).also { runtimeSession ->
            runtimeSession.transitionTo(PluginV2RuntimeSessionState.Loading)
            runtimeSession.transitionTo(PluginV2RuntimeSessionState.BootstrapRunning)
        }
        val rawRegistry = PluginV2RawRegistry(session.pluginId)

        commandHandlers.forEach { descriptor ->
            rawRegistry.appendCommandHandler(
                callbackToken = session.allocateCallbackToken(PluginV2CallbackHandle {}),
                descriptor = descriptor,
            )
        }

        return rawRegistry
    }

    private fun messageHandler(
        registrationKey: String?,
        priority: Int = 0,
        declaredFilters: List<BootstrapFilterDescriptor> = emptyList(),
    ): MessageHandlerRegistrationInput {
        return MessageHandlerRegistrationInput(
            base = BaseHandlerRegistrationInput(
                registrationKey = registrationKey,
                priority = priority,
                declaredFilters = declaredFilters,
            ),
            handler = PluginV2CallbackHandle {},
        )
    }

    private fun commandHandler(
        registrationKey: String?,
        command: String,
        priority: Int = 0,
        aliases: List<String> = emptyList(),
        groupPath: List<String> = emptyList(),
    ): CommandHandlerRegistrationInput {
        return CommandHandlerRegistrationInput(
            base = BaseHandlerRegistrationInput(
                registrationKey = registrationKey,
                priority = priority,
            ),
            command = command,
            aliases = aliases,
            groupPath = groupPath,
            handler = PluginV2CallbackHandle {},
        )
    }

    private fun regexHandler(
        registrationKey: String?,
        pattern: String,
        flags: Set<String> = emptySet(),
        priority: Int = 0,
    ): RegexHandlerRegistrationInput {
        return RegexHandlerRegistrationInput(
            base = BaseHandlerRegistrationInput(
                registrationKey = registrationKey,
                priority = priority,
            ),
            pattern = pattern,
            flags = flags,
            handler = PluginV2CallbackHandle {},
        )
    }

    private fun bootstrappedSession(): PluginV2RuntimeSession {
        return PluginV2RuntimeSession(
            installRecord = samplePluginV2InstallRecord(
                pluginId = "com.example.v2.compiler",
            ),
            sessionInstanceId = "session-compiler",
        ).also { session ->
            session.transitionTo(PluginV2RuntimeSessionState.Loading)
            session.transitionTo(PluginV2RuntimeSessionState.BootstrapRunning)
        }
    }
}
