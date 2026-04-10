package com.astrbot.android.runtime.plugin

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
    ): CommandHandlerRegistrationInput {
        return CommandHandlerRegistrationInput(
            base = BaseHandlerRegistrationInput(
                registrationKey = registrationKey,
                priority = priority,
            ),
            command = command,
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
