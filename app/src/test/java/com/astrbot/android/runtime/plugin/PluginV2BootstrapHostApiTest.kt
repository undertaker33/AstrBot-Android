package com.astrbot.android.runtime.plugin

import com.astrbot.android.model.plugin.PluginRuntimeLogLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginV2BootstrapHostApiTest {
    @Test
    fun registerMessageHandler_returns_token_synchronously_and_collects_raw_registration() {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 123L })
        val session = bootstrapRunningSession()
        val hostApi = PluginV2BootstrapHostApi(
            session = session,
            logBus = logBus,
            clock = { 123L },
        )
        val handler = PluginV2CallbackHandle {}

        val token = hostApi.registerMessageHandler(
            MessageHandlerRegistrationInput(
                base = BaseHandlerRegistrationInput(
                    priority = 7,
                    declaredFilters = listOf(
                        BootstrapFilterDescriptor.message("group"),
                        BootstrapFilterDescriptor.command("/echo"),
                    ),
                    metadata = BootstrapRegistrationMetadata(
                        values = linkedMapOf("source" to "bootstrap"),
                    ),
                ),
                handler = handler,
            ),
        )

        assertTrue(session.hasCallbackToken(token))
        assertSame(handler, session.requireCallbackHandle(token))
        val rawRegistry = session.rawRegistry
        assertNotNull(rawRegistry)
        assertEquals(1, rawRegistry?.messageHandlers?.size)

        val registration = rawRegistry!!.messageHandlers.single()
        assertEquals(session.pluginId, registration.pluginId)
        assertNull(registration.registrationKey)
        assertSame(token, registration.callbackToken)
        assertEquals(7, registration.priority)
        assertEquals(0, registration.sourceOrder)
        assertEquals(listOf("Message", "Command"), registration.declaredFilters.map { it.kind.name })
        assertEquals("bootstrap", registration.metadata.values["source"])
    }

    @Test
    fun registerCommandHandler_and_registerRegexHandler_accept_fixed_descriptor_inputs() {
        val registerCommand = PluginV2BootstrapHostApi::class.java.getMethod(
            "registerCommandHandler",
            CommandHandlerRegistrationInput::class.java,
        )
        val registerRegex = PluginV2BootstrapHostApi::class.java.getMethod(
            "registerRegexHandler",
            RegexHandlerRegistrationInput::class.java,
        )

        assertEquals(CommandHandlerRegistrationInput::class.java, registerCommand.parameterTypes.single())
        assertEquals(RegexHandlerRegistrationInput::class.java, registerRegex.parameterTypes.single())
        assertEquals(
            listOf("aliases", "base", "command", "groupPath", "handler"),
            descriptorFieldNames(CommandHandlerRegistrationInput::class.java),
        )
        assertEquals(
            listOf("base", "flags", "handler", "pattern"),
            descriptorFieldNames(RegexHandlerRegistrationInput::class.java),
        )
    }

    @Test
    fun descriptor_contract_uses_flat_metadata_optional_registration_key_and_phase2_names() {
        assertEquals(
            listOf("values"),
            descriptorFieldNames(BootstrapRegistrationMetadata::class.java),
        )
        assertEquals(
            listOf("declaredFilters", "metadata", "priority", "registrationKey"),
            descriptorFieldNames(BaseHandlerRegistrationInput::class.java),
        )
        assertEquals(
            listOf("declaredFilters", "handler", "hook", "metadata", "priority", "registrationKey"),
            descriptorFieldNames(LifecycleHandlerRegistrationInput::class.java),
        )
        assertEquals(
            listOf("declaredFilters", "handler", "hook", "metadata", "priority", "registrationKey"),
            descriptorFieldNames(LlmHookRegistrationInput::class.java),
        )
    }

    @Test
    fun phase5_bootstrap_host_api_exposes_tool_registration_methods_with_fixed_contract_inputs() {
        val publicMethodNames = PluginV2BootstrapHostApi::class.java.methods
            .map { method -> method.name }
            .filterNot { methodName -> methodName.startsWith("wait") || methodName == "equals" || methodName == "hashCode" || methodName == "toString" || methodName == "getClass" || methodName == "notify" || methodName == "notifyAll" }

        assertTrue(publicMethodNames.contains("registerTool"))
        assertTrue(publicMethodNames.contains("registerToolLifecycleHook"))

        val registerTool = PluginV2BootstrapHostApi::class.java.getMethod(
            "registerTool",
            PluginToolDescriptor::class.java,
            PluginV2CallbackHandle::class.java,
        )
        val registerToolLifecycleHook = PluginV2BootstrapHostApi::class.java.getMethod(
            "registerToolLifecycleHook",
            ToolLifecycleHookRegistrationInput::class.java,
        )

        assertEquals(PluginV2CallbackToken::class.java, registerTool.returnType)
        assertEquals(PluginV2CallbackToken::class.java, registerToolLifecycleHook.returnType)
    }

    @Test
    fun registerTool_returns_token_and_persists_phase5_tool_surface() {
        val session = bootstrapRunningSession(sessionInstanceId = "session-tool")
        val hostApi = PluginV2BootstrapHostApi(
            session = session,
            logBus = InMemoryPluginRuntimeLogBus(clock = { 321L }),
            clock = { 321L },
        )
        val handler = PluginV2CallbackHandle {}

        val token = hostApi.registerTool(
            descriptor = PluginToolDescriptor(
                pluginId = session.pluginId,
                name = "summarize",
                description = "Summarize latest assistant response",
                visibility = PluginToolVisibility.LLM_VISIBLE,
                sourceKind = PluginToolSourceKind.PLUGIN_V2,
                inputSchema = linkedMapOf("type" to "object"),
                metadata = linkedMapOf("surface" to "bootstrap"),
            ),
            handler = handler,
        )

        assertTrue(session.hasCallbackToken(token))
        assertSame(handler, session.requireCallbackHandle(token))
        val registration = session.rawRegistry!!.tools.single()
        assertEquals("${session.pluginId}:summarize", registration.descriptor.toolId)
        assertEquals(PluginToolSourceKind.PLUGIN_V2, registration.descriptor.sourceKind)
        assertEquals(PluginToolVisibility.LLM_VISIBLE, registration.descriptor.visibility)
        assertEquals("bootstrap", registration.descriptor.metadata?.get("surface"))
    }

    @Test
    fun registerTool_rejects_invalid_phase5_descriptor_inputs_and_logs() {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 654L })
        val hostApi = PluginV2BootstrapHostApi(
            session = bootstrapRunningSession(sessionInstanceId = "session-tool-reject"),
            logBus = logBus,
            clock = { 654L },
        )

        val failures = listOf(
            runCatching {
                hostApi.registerTool(
                    descriptor = PluginToolDescriptor(
                        pluginId = "com.example.v2.demo",
                        name = " ",
                        visibility = PluginToolVisibility.LLM_VISIBLE,
                        sourceKind = PluginToolSourceKind.PLUGIN_V2,
                        inputSchema = linkedMapOf("type" to "object"),
                    ),
                    handler = PluginV2CallbackHandle {},
                )
            }.exceptionOrNull(),
            runCatching {
                hostApi.registerTool(
                    descriptor = PluginToolDescriptor(
                        pluginId = "com.example.v2.demo",
                        name = "lookup",
                        visibility = PluginToolVisibility.HOST_INTERNAL,
                        sourceKind = PluginToolSourceKind.SKILL,
                        inputSchema = linkedMapOf("type" to "object"),
                    ),
                    handler = PluginV2CallbackHandle {},
                )
            }.exceptionOrNull(),
            runCatching {
                hostApi.registerTool(
                    descriptor = PluginToolDescriptor(
                        pluginId = "com.example.v2.demo",
                        name = "lookup",
                        visibility = PluginToolVisibility.LLM_VISIBLE,
                        sourceKind = PluginToolSourceKind.PLUGIN_V2,
                        inputSchema = linkedMapOf("properties" to emptyMap<String, Any>()),
                    ),
                    handler = PluginV2CallbackHandle {},
                )
            }.exceptionOrNull(),
        )

        failures.forEach { failure ->
            assertTrue(failure is IllegalArgumentException)
        }
        assertEquals(3, logBus.snapshot().size)
        assertEquals(
            listOf("tool", "tool", "tool"),
            logBus.snapshot().map { it.metadata["registrationType"] },
        )
    }

    @Test
    fun registerToolLifecycleHook_rejects_declared_filters_and_persists_phase5_surface() {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 777L })
        val session = bootstrapRunningSession(sessionInstanceId = "session-tool-hook")
        val hostApi = PluginV2BootstrapHostApi(
            session = session,
            logBus = logBus,
            clock = { 777L },
        )

        val token = hostApi.registerToolLifecycleHook(
            ToolLifecycleHookRegistrationInput(
                registrationKey = "tool.before_execute",
                hook = "on_using_llm_tool",
                metadata = BootstrapRegistrationMetadata(values = mapOf("phase" to "5")),
                handler = PluginV2CallbackHandle {},
            ),
        )

        assertTrue(session.hasCallbackToken(token))
        assertEquals(1, session.rawRegistry!!.toolLifecycleHooks.size)
        assertEquals("on_using_llm_tool", session.rawRegistry!!.toolLifecycleHooks.single().descriptor.hook)

        val failure = runCatching {
            hostApi.registerToolLifecycleHook(
                ToolLifecycleHookRegistrationInput(
                    registrationKey = "tool.reject_filters",
                    hook = "on_llm_tool_respond",
                    declaredFilters = listOf(BootstrapFilterDescriptor.message("group")),
                    handler = PluginV2CallbackHandle {},
                ),
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message?.contains("declaredFilters") == true)
        assertEquals(1, logBus.snapshot().size)
    }

    @Test
    fun lifecycle_and_hook_registrations_reject_declared_filters() {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 456L })
        val hostApi = PluginV2BootstrapHostApi(
            session = bootstrapRunningSession(sessionInstanceId = "session-reject"),
            logBus = logBus,
            clock = { 456L },
        )
        val forbiddenFilters = listOf(BootstrapFilterDescriptor.regex("^/blocked$"))

        val failures = listOf(
            runCatching {
                hostApi.registerLifecycleHandler(
                    LifecycleHandlerRegistrationInput(
                        registrationKey = "lifecycle.ready",
                        hook = "bootstrap_ready",
                        declaredFilters = forbiddenFilters,
                        handler = PluginV2CallbackHandle {},
                    ),
                )
            }.exceptionOrNull(),
            runCatching {
                hostApi.registerLlmHook(
                    LlmHookRegistrationInput(
                        registrationKey = "llm.before_call",
                        hook = "before_model_call",
                        declaredFilters = forbiddenFilters,
                        handler = PluginV2CallbackHandle {},
                    ),
                )
            }.exceptionOrNull(),
        )

        failures.forEach { failure ->
            assertTrue(failure is IllegalArgumentException)
            assertTrue(failure?.message?.contains("declaredFilters") == true)
        }

        assertEquals(2, logBus.snapshot().size)
        assertEquals(
            listOf(
                "bootstrap_registration_rejected",
                "bootstrap_registration_rejected",
            ),
            logBus.snapshot().map { it.code },
        )
    }

    @Test
    fun registration_errors_are_thrown_immediately_and_logged() {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 789L })
        val session = bootstrapRunningSession(sessionInstanceId = "session-errors")
        val hostApi = PluginV2BootstrapHostApi(
            session = session,
            logBus = logBus,
            clock = { 789L },
        )

        val failure = runCatching {
            hostApi.registerCommandHandler(
                CommandHandlerRegistrationInput(
                    base = BaseHandlerRegistrationInput(),
                    command = " ",
                    handler = PluginV2CallbackHandle {},
                ),
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message?.contains("command") == true)
        assertTrue(session.rawRegistry?.isEmpty() != false)

        val rejectedLog = logBus.snapshot().single()
        assertEquals("bootstrap_registration_rejected", rejectedLog.code)
        assertEquals(PluginRuntimeLogLevel.Error, rejectedLog.level)
        assertEquals("registerCommandHandler", rejectedLog.metadata["operation"])
        assertEquals("command", rejectedLog.metadata["registrationType"])
    }

    @Test
    fun log_writes_to_runtime_log_bus() {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 999L })
        val session = bootstrapRunningSession(sessionInstanceId = "session-log")
        val hostApi = PluginV2BootstrapHostApi(
            session = session,
            logBus = logBus,
            clock = { 999L },
        )

        hostApi.log(
            level = PluginRuntimeLogLevel.Info,
            message = "bootstrap ready",
            metadata = linkedMapOf("phase" to "bootstrap"),
        )

        val record = logBus.snapshot().single()
        assertEquals("bootstrap_log", record.code)
        assertEquals(PluginRuntimeLogLevel.Info, record.level)
        assertEquals("bootstrap ready", record.message)
        assertEquals(session.pluginId, record.pluginId)
        assertEquals("bootstrap", record.metadata["phase"])
    }

    @Test
    fun registerMessageHandler_rejects_sessions_before_bootstrap_running() {
        val session = PluginV2RuntimeSession(
            installRecord = samplePluginV2InstallRecord(),
            sessionInstanceId = "session-not-bootstrap",
        )
        val hostApi = PluginV2BootstrapHostApi(
            session = session,
            logBus = InMemoryPluginRuntimeLogBus(clock = { 1_222L }),
            clock = { 1_222L },
        )

        val failure = runCatching {
            hostApi.registerMessageHandler(
                MessageHandlerRegistrationInput(
                    handler = PluginV2CallbackHandle {},
                ),
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message?.contains("BootstrapRunning") == true)
    }

    @Test
    fun getPluginMetadata_returns_install_record_backed_static_snapshot_even_after_dispose() {
        val installRecord = samplePluginV2InstallRecord(
            pluginId = "com.example.v2.metadata",
            version = "2.1.0",
        )
        val session = PluginV2RuntimeSession(
            installRecord = installRecord,
            sessionInstanceId = "session-metadata",
        )
        val hostApi = PluginV2BootstrapHostApi(
            session = session,
            logBus = InMemoryPluginRuntimeLogBus(clock = { 1_234L }),
            clock = { 1_234L },
        )

        session.transitionTo(PluginV2RuntimeSessionState.Loading)
        session.transitionTo(PluginV2RuntimeSessionState.BootstrapRunning)
        session.dispose()

        val metadata = invokeNoArg(hostApi, "getPluginMetadata")!!

        assertEquals(installRecord.pluginId, readProperty(metadata, "pluginId"))
        assertEquals(installRecord.installedVersion, readProperty(metadata, "installedVersion"))
        assertEquals(
            installRecord.packageContractSnapshot?.runtime?.kind,
            readProperty(metadata, "runtimeKind"),
        )
        assertEquals(
            installRecord.packageContractSnapshot?.runtime?.apiVersion,
            readProperty(metadata, "runtimeApiVersion"),
        )
        assertEquals(
            installRecord.packageContractSnapshot?.runtime?.bootstrap,
            readProperty(metadata, "runtimeBootstrap"),
        )
    }

    private fun bootstrapRunningSession(
        sessionInstanceId: String = "session-bootstrap",
    ): PluginV2RuntimeSession {
        return PluginV2RuntimeSession(
            installRecord = samplePluginV2InstallRecord(),
            sessionInstanceId = sessionInstanceId,
        ).also { session ->
            session.transitionTo(PluginV2RuntimeSessionState.Loading)
            session.transitionTo(PluginV2RuntimeSessionState.BootstrapRunning)
        }
    }

    private fun descriptorFieldNames(type: Class<*>): List<String> {
        return type.declaredFields
            .filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .map { it.name }
            .sorted()
    }
}
