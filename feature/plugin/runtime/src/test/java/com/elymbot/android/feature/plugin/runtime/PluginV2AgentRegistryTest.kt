package com.elymbot.android.feature.plugin.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginV2AgentRegistryTest {

    @Test
    fun register_agent_compiles_into_v2_registry_snapshot() {
        val session = agentSession("com.example.agent.registry")
        val hostApi = PluginV2BootstrapHostApi(session = session, clock = { 1L })

        val token = hostApi.registerAgent(
            AgentRegistrationInput(
                key = "research-agent",
                systemPrompt = "Use tools before answering.",
                tools = listOf("plugin.search", "web.search"),
                model = AgentModelSelection(providerId = "provider-a", modelId = "model-a-1"),
                handler = PluginV2CallbackHandle {},
            ),
        )

        val result = PluginV2RegistryCompiler(clock = { 1L }).compile(session.requireBootstrapRawRegistry())
        val compiled = requireNotNull(result.compiledRegistry)
        val agent = compiled.handlerRegistry.agentHandlers.single()

        assertEquals(token, agent.callbackToken)
        assertEquals("research-agent", agent.agentKey)
        assertEquals("Use tools before answering.", agent.systemPrompt)
        assertEquals(listOf("plugin.search", "web.search"), agent.tools)
        assertEquals("provider-a", agent.model.providerId)
        assertEquals("model-a-1", agent.model.modelId)
        assertEquals(0, result.diagnostics.count { it.severity == DiagnosticSeverity.Error })
    }

    @Test
    fun duplicate_agent_key_is_rejected_during_compile() {
        val session = agentSession("com.example.agent.duplicate")
        val hostApi = PluginV2BootstrapHostApi(session = session, clock = { 1L })

        hostApi.registerAgent(agentInput(key = "same"))
        hostApi.registerAgent(agentInput(key = "same"))

        val result = PluginV2RegistryCompiler(clock = { 1L }).compile(session.requireBootstrapRawRegistry())

        assertNull(result.compiledRegistry)
        assertTrue(result.diagnostics.any { it.code == "duplicate_agent_key" })
    }

    @Test
    fun session_dispose_clears_agent_runtime_state() {
        val session = agentSession("com.example.agent.dispose")
        val hostApi = PluginV2BootstrapHostApi(session = session, clock = { 1L })
        hostApi.registerAgent(agentInput(key = "temporary"))
        val compiled = requireNotNull(PluginV2RegistryCompiler(clock = { 1L }).compile(session.requireBootstrapRawRegistry()).compiledRegistry)
        session.attachCompiledRegistry(compiled)
        session.transitionTo(PluginV2RuntimeSessionState.Active)

        assertNotNull(session.rawRegistry)
        assertNotNull(session.compiledRegistry)
        assertFalse(session.snapshotCallbackTokens().isEmpty())

        session.dispose()

        assertNull(session.rawRegistry)
        assertNull(session.compiledRegistry)
        assertTrue(session.snapshotCallbackTokens().isEmpty())
    }

    private fun agentInput(key: String): AgentRegistrationInput {
        return AgentRegistrationInput(
            key = key,
            systemPrompt = "You are an agent.",
            tools = emptyList(),
            model = AgentModelSelection(providerId = "provider-a", modelId = "model-a-1"),
            handler = PluginV2CallbackHandle {},
        )
    }
}
