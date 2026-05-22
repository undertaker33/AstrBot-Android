package com.elymbot.android.feature.plugin.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginV2FilterAstCompilerTest {
    @Test
    fun legacy_declared_filters_compile_to_all_of_expression() {
        val session = filterSession("com.example.filter.compiler.legacy")
        val hostApi = PluginV2BootstrapHostApi(session = session, clock = { 1L })

        hostApi.registerMessageHandler(
            MessageHandlerRegistrationInput(
                base = BaseHandlerRegistrationInput(
                    registrationKey = "legacy.filters",
                    declaredFilters = listOf(
                        BootstrapFilterDescriptor.message("event_message_type:group"),
                        BootstrapFilterDescriptor.command("platform_adapter_type:onebot"),
                        BootstrapFilterDescriptor.regex("permission_type:agent_run"),
                    ),
                ),
                handler = PluginV2CallbackHandle {},
            ),
        )

        val result = PluginV2RegistryCompiler(clock = { 1L }).compile(session.requireBootstrapRawRegistry())
        val expression = result.compiledRegistry!!.handlerRegistry.messageHandlers.single().filterExpression

        val allOf = expression as PluginV2CompiledFilterExpression.AllOf
        assertEquals(3, allOf.children.size)
        assertEquals(
            listOf("event_message_type", "platform_adapter_type", "permission_type"),
            allOf.children.map { child -> (child as PluginV2CompiledFilterExpression.Builtin).reasonCode },
        )
    }

    @Test
    fun legacy_declared_filters_compile_builtin_before_custom_for_compatibility() {
        val session = filterSession("com.example.filter.compiler.legacy-order")
        val hostApi = PluginV2BootstrapHostApi(session = session, clock = { 1L })

        hostApi.registerMessageHandler(
            MessageHandlerRegistrationInput(
                base = BaseHandlerRegistrationInput(
                    registrationKey = "legacy.filters.order",
                    declaredFilters = listOf(
                        BootstrapFilterDescriptor.message("custom_filter:gate"),
                        BootstrapFilterDescriptor.message("event_message_type:group"),
                    ),
                ),
                handler = PluginV2CallbackHandle {},
            ),
        )

        val result = PluginV2RegistryCompiler(clock = { 1L }).compile(session.requireBootstrapRawRegistry())
        val expression = result.compiledRegistry!!.handlerRegistry.messageHandlers.single().filterExpression

        val allOf = expression as PluginV2CompiledFilterExpression.AllOf
        assertTrue(allOf.children[0] is PluginV2CompiledFilterExpression.Builtin)
        assertTrue(allOf.children[1] is PluginV2CompiledFilterExpression.Custom)
    }

    @Test
    fun allOf_anyOf_not_and_custom_filters_compile_into_snapshot_expression() {
        val session = filterSession("com.example.filter.compiler.ast")
        val hostApi = PluginV2BootstrapHostApi(session = session, clock = { 1L })

        hostApi.registerMessageHandler(
            MessageHandlerRegistrationInput(
                base = BaseHandlerRegistrationInput(
                    registrationKey = "ast.filters",
                    filterExpression = PluginV2FilterExpression.AllOf(
                        listOf(
                            PluginV2FilterExpression.Builtin(
                                kind = PluginV2BuiltinFilterKind.EventMessageType,
                                value = "group",
                            ),
                            PluginV2FilterExpression.AnyOf(
                                listOf(
                                    PluginV2FilterExpression.Builtin(
                                        kind = PluginV2BuiltinFilterKind.PlatformAdapterType,
                                        value = "onebot",
                                    ),
                                    PluginV2FilterExpression.Custom(name = "custom-gate"),
                                ),
                            ),
                            PluginV2FilterExpression.Not(
                                PluginV2FilterExpression.Builtin(
                                    kind = PluginV2BuiltinFilterKind.PermissionType,
                                    value = "blocked",
                                ),
                            ),
                        ),
                    ),
                ),
                handler = PluginV2CallbackHandle {},
            ),
        )

        val result = PluginV2RegistryCompiler(clock = { 1L }).compile(session.requireBootstrapRawRegistry())
        val expression = result.compiledRegistry!!.handlerRegistry.messageHandlers.single().filterExpression

        val allOf = expression as PluginV2CompiledFilterExpression.AllOf
        assertEquals(3, allOf.children.size)
        assertTrue(allOf.children[1] is PluginV2CompiledFilterExpression.AnyOf)
        assertTrue(allOf.children[2] is PluginV2CompiledFilterExpression.Not)
        assertTrue(result.diagnostics.none { it.severity == DiagnosticSeverity.Error })
    }

    @Test
    fun explicit_filter_ast_preserves_declared_order() {
        val session = filterSession("com.example.filter.compiler.ast-order")
        val hostApi = PluginV2BootstrapHostApi(session = session, clock = { 1L })

        hostApi.registerMessageHandler(
            MessageHandlerRegistrationInput(
                base = BaseHandlerRegistrationInput(
                    registrationKey = "ast.filters.order",
                    filterExpression = PluginV2FilterExpression.AllOf(
                        listOf(
                            PluginV2FilterExpression.Custom(name = "gate"),
                            PluginV2FilterExpression.Builtin(
                                kind = PluginV2BuiltinFilterKind.EventMessageType,
                                value = "group",
                            ),
                        ),
                    ),
                ),
                handler = PluginV2CallbackHandle {},
            ),
        )

        val result = PluginV2RegistryCompiler(clock = { 1L }).compile(session.requireBootstrapRawRegistry())
        val expression = result.compiledRegistry!!.handlerRegistry.messageHandlers.single().filterExpression

        val allOf = expression as PluginV2CompiledFilterExpression.AllOf
        assertTrue(allOf.children[0] is PluginV2CompiledFilterExpression.Custom)
        assertTrue(allOf.children[1] is PluginV2CompiledFilterExpression.Builtin)
    }

    @Test
    fun simultaneous_declaredFilters_and_filter_ast_are_rejected() {
        val session = filterSession("com.example.filter.compiler.ambiguous")
        val rawRegistry = PluginV2RawRegistry(session.pluginId)
        rawRegistry.appendMessageHandler(
            callbackToken = session.allocateCallbackToken(PluginV2CallbackHandle {}),
            descriptor = MessageHandlerRegistrationInput(
                base = BaseHandlerRegistrationInput(
                    registrationKey = "ambiguous.filters",
                    declaredFilters = listOf(BootstrapFilterDescriptor.message("event_message_type:group")),
                    filterExpression = PluginV2FilterExpression.Builtin(
                        kind = PluginV2BuiltinFilterKind.PlatformAdapterType,
                        value = "onebot",
                    ),
                ),
                handler = PluginV2CallbackHandle {},
            ),
        )

        val result = PluginV2RegistryCompiler(clock = { 1L }).compile(rawRegistry)

        assertNull(result.compiledRegistry)
        assertTrue(result.diagnostics.any { it.code == "ambiguous_filter_sources" })
        assertTrue(result.diagnostics.any { it.severity == DiagnosticSeverity.Error })
    }

    private fun filterSession(pluginId: String): PluginV2RuntimeSession {
        return agentSession(pluginId)
    }
}
