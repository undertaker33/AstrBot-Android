package com.astrbot.android.runtime.plugin

import com.astrbot.android.model.PersonaToolEnablementSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginV2ToolAvailabilityTest {
    @Test
    fun available_when_all_gates_pass() {
        val registry = compiledToolRegistry()

        val snapshot = registry.evaluateAvailability(
            toolName = "summarize",
            activeRegistry = registry.compileCandidates(
                listOf(
                    PluginToolDescriptor(
                        pluginId = "com.example.alpha",
                        name = "summarize",
                        sourceKind = PluginToolSourceKind.PLUGIN_V2,
                        inputSchema = linkedMapOf("type" to "object"),
                    ),
                ),
            ).activeRegistry,
            personaSnapshot = PersonaToolEnablementSnapshot(
                personaId = "persona-1",
                enabled = true,
                enabledTools = setOf("summarize"),
            ),
            capabilityGateway = PluginV2ToolCapabilityGateway { true },
        )

        assertTrue(snapshot.available)
        assertNull(snapshot.firstFailureReason)
        assertEquals("summarize", snapshot.toolName)
    }

    @Test
    fun registry_inactive_is_reported_first() {
        val registry = compiledToolRegistry()

        val snapshot = registry.evaluateAvailability(
            toolName = "missing",
            activeRegistry = registry.compileCandidates(
                listOf(
                    PluginToolDescriptor(
                        pluginId = "com.example.alpha",
                        name = "summarize",
                        sourceKind = PluginToolSourceKind.PLUGIN_V2,
                        inputSchema = linkedMapOf("type" to "object"),
                    ),
                ),
            ).activeRegistry,
            personaSnapshot = PersonaToolEnablementSnapshot(
                personaId = "persona-1",
                enabled = true,
                enabledTools = setOf("missing"),
            ),
            capabilityGateway = PluginV2ToolCapabilityGateway { true },
        )

        assertTrue(!snapshot.available)
        assertEquals(PluginV2ToolAvailabilityFailureReason.RegistryInactive, snapshot.firstFailureReason)
        assertEquals("registry_inactive", snapshot.firstFailureReason?.code)
    }

    @Test
    fun persona_disabled_is_reported_after_registry_passes() {
        val registry = compiledToolRegistry()

        val snapshot = registry.evaluateAvailability(
            toolName = "summarize",
            activeRegistry = registry.compileCandidates(
                listOf(
                    PluginToolDescriptor(
                        pluginId = "com.example.alpha",
                        name = "summarize",
                        sourceKind = PluginToolSourceKind.PLUGIN_V2,
                        inputSchema = linkedMapOf("type" to "object"),
                    ),
                ),
            ).activeRegistry,
            personaSnapshot = PersonaToolEnablementSnapshot(
                personaId = "persona-1",
                enabled = false,
                enabledTools = setOf("summarize"),
            ),
            capabilityGateway = PluginV2ToolCapabilityGateway { true },
        )

        assertTrue(!snapshot.available)
        assertEquals(PluginV2ToolAvailabilityFailureReason.PersonaDisabled, snapshot.firstFailureReason)
        assertEquals("persona_disabled", snapshot.firstFailureReason?.code)
    }

    @Test
    fun capability_denied_is_reported_after_persona_passes() {
        val registry = compiledToolRegistry()

        val snapshot = registry.evaluateAvailability(
            toolName = "summarize",
            activeRegistry = registry.compileCandidates(
                listOf(
                    PluginToolDescriptor(
                        pluginId = "com.example.alpha",
                        name = "summarize",
                        sourceKind = PluginToolSourceKind.PLUGIN_V2,
                        inputSchema = linkedMapOf("type" to "object"),
                    ),
                ),
            ).activeRegistry,
            personaSnapshot = PersonaToolEnablementSnapshot(
                personaId = "persona-1",
                enabled = true,
                enabledTools = setOf("summarize"),
            ),
            capabilityGateway = PluginV2ToolCapabilityGateway { false },
        )

        assertTrue(!snapshot.available)
        assertEquals(PluginV2ToolAvailabilityFailureReason.CapabilityDenied, snapshot.firstFailureReason)
        assertEquals("capability_denied", snapshot.firstFailureReason?.code)
    }

    @Test
    fun source_unavailable_is_reported_last() {
        val registry = compiledToolRegistry(
            sourceGateway = PluginV2ToolSourceGateway { PluginV2ToolSourceGatewayResult.SourceUnavailable },
        )

        val snapshot = registry.evaluateAvailability(
            toolName = "summarize",
            activeRegistry = registry.compileCandidates(
                listOf(
                    PluginToolDescriptor(
                        pluginId = "com.example.alpha",
                        name = "summarize",
                        sourceKind = PluginToolSourceKind.PLUGIN_V2,
                        inputSchema = linkedMapOf("type" to "object"),
                    ),
                ),
            ).activeRegistry,
            personaSnapshot = PersonaToolEnablementSnapshot(
                personaId = "persona-1",
                enabled = true,
                enabledTools = setOf("summarize"),
            ),
            capabilityGateway = PluginV2ToolCapabilityGateway { true },
        )

        assertTrue(!snapshot.available)
        assertEquals(PluginV2ToolAvailabilityFailureReason.SourceUnavailable, snapshot.firstFailureReason)
        assertEquals("source_unavailable", snapshot.firstFailureReason?.code)
    }

    @Test
    fun reserved_source_kinds_stay_unavailable_in_the_gateway() {
        val gateway = PluginV2ToolSourceGateway()
        val entry = PluginV2ToolRegistryEntry(
            pluginId = "com.example.alpha",
            name = "future_skill",
            toolId = "com.example.alpha:future_skill",
            description = "",
            visibility = PluginToolVisibility.LLM_VISIBLE,
            sourceKind = PluginToolSourceKind.SKILL,
            inputSchema = linkedMapOf("type" to "object"),
            metadata = null,
            sourceOrder = 0,
        )

        val resolved = gateway.resolve(entry)

        assertTrue(resolved is PluginV2ToolSourceGatewayResult.ReservedSourceUnavailable)
    }

    @Test
    fun centralized_runtime_tool_state_uses_real_persona_snapshot_instead_of_default_enablement() {
        val session = bootstrappedToolSession(
            pluginId = "com.example.runtime.persona",
            toolName = "persona_guarded",
        )
        val toolState = compileCentralizedToolState(
            sessionsByPluginId = mapOf(session.pluginId to session),
            personaSnapshot = PersonaToolEnablementSnapshot(
                personaId = "persona-disabled-tool",
                enabled = true,
                enabledTools = emptySet(),
            ),
        )

        val availability = toolState.availabilityByName.getValue("persona_guarded")
        assertTrue(!availability.available)
        assertEquals(PluginV2ToolAvailabilityFailureReason.PersonaDisabled, availability.firstFailureReason)
    }

    private fun compiledToolRegistry(
        sourceGateway: PluginV2ToolSourceGateway = PluginV2ToolSourceGateway(),
    ): PluginV2ToolRegistry {
        return PluginV2ToolRegistry(sourceGateway = sourceGateway)
    }

    private fun bootstrappedToolSession(
        pluginId: String,
        toolName: String,
    ): PluginV2RuntimeSession {
        val session = PluginV2RuntimeSession(
            installRecord = samplePluginV2InstallRecord(pluginId = pluginId),
            sessionInstanceId = "session-$pluginId",
        )
        session.transitionTo(PluginV2RuntimeSessionState.Loading)
        session.transitionTo(PluginV2RuntimeSessionState.BootstrapRunning)
        val rawRegistry = PluginV2RawRegistry(pluginId)
        rawRegistry.appendTool(
            callbackToken = session.allocateCallbackToken(PluginV2CallbackHandle {}),
            descriptor = ToolRegistrationInput(
                registrationKey = "tool.$toolName",
                toolDescriptor = PluginV2ToolDescriptor(name = toolName),
                handler = PluginV2CallbackHandle {},
            ),
        )
        session.attachRawRegistry(rawRegistry)
        session.transitionTo(PluginV2RuntimeSessionState.Active)
        return session
    }
}
