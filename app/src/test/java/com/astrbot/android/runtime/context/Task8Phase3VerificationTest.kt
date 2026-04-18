package com.astrbot.android.core.runtime.context

import com.astrbot.android.data.ConfigRepository
import com.astrbot.android.data.ProviderRepository
import com.astrbot.android.data.ResourceCenterRepository
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.ConfigResourceProjection
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ProviderType
import com.astrbot.android.model.ResourceCenterItem
import com.astrbot.android.model.ResourceCenterKind
import com.astrbot.android.model.SkillResourceKind
import com.astrbot.android.model.chat.MessageType
import com.astrbot.android.feature.plugin.runtime.PluginV2LlmPipelineInput
import com.astrbot.android.feature.plugin.runtime.toolsource.ToolSourceAvailabilityContext
import com.astrbot.android.feature.plugin.runtime.toolsource.ToolSourceContext
import com.astrbot.android.feature.plugin.runtime.toolsource.ToolSourceRegistryIngestContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 3 architectural verification tests.
 *
 * These tests pin the resource projection and unified tool-source context
 * contracts required by Task8 phase 3.
 */
class Task8Phase3VerificationTest {

    @Test
    fun resolved_runtime_context_carries_phase3_resource_projection_fields() {
        val fields = ResolvedRuntimeContext::class.java.declaredFields.map { it.name }.toSet()
        val required = listOf(
            "promptSkills",
            "toolSkills",
            "toolSourceContext",
        )

        required.forEach { field ->
            assertTrue(
                "ResolvedRuntimeContext must have phase3 field '$field'",
                fields.contains(field),
            )
        }
    }

    @Test
    fun tool_source_context_carries_unified_tool_input_fields() {
        val fields = ToolSourceContext::class.java.declaredFields.map { it.name }.toSet()
        val required = listOf(
            "requestId",
            "platform",
            "configProfileId",
            "webSearchEnabled",
            "activeCapabilityEnabled",
            "mcpServers",
            "promptSkills",
            "toolSkills",
            "conversationId",
            "runtimePermissions",
            "networkPolicy",
        )

        required.forEach { field ->
            assertTrue(
                "ToolSourceContext must have field '$field'",
                fields.contains(field),
            )
        }
    }

    @Test
    fun tool_source_ingest_and_availability_contexts_wrap_tool_source_context() {
        val ingestCtor = ToolSourceRegistryIngestContext::class.java.constructors.single()
        val availabilityCtor = ToolSourceAvailabilityContext::class.java.constructors.single()

        assertEquals(1, ingestCtor.parameterTypes.size)
        assertEquals(ToolSourceContext::class.java, ingestCtor.parameterTypes[0])
        assertEquals(1, availabilityCtor.parameterTypes.size)
        assertEquals(ToolSourceContext::class.java, availabilityCtor.parameterTypes[0])
    }

    @Test
    fun llm_pipeline_input_carries_tool_source_context() {
        val field = PluginV2LlmPipelineInput::class.java.declaredFields
            .firstOrNull { it.name == "toolSourceContext" }

        assertNotNull("PluginV2LlmPipelineInput must carry ToolSourceContext", field)
        assertEquals(ToolSourceContext::class.java, field!!.type)
    }

    @Test
    fun runtime_context_uses_resource_center_projection_as_runtime_resource_source() {
        val providerSnapshot = ProviderRepository.snapshotProfiles()
        val configSnapshot = ConfigRepository.snapshotProfiles()
        val selectedConfigId = ConfigRepository.selectedProfileId.value
        val config = ConfigProfile(
            id = "phase3-resource-config",
            name = "Phase3 Resource Config",
            defaultChatProviderId = "phase3-provider",
            webSearchEnabled = true,
            proactiveEnabled = true,
        )
        val provider = ProviderProfile(
            id = "phase3-provider",
            name = "Phase3 Provider",
            baseUrl = "https://example.invalid/v1",
            model = "phase3-model",
            providerType = ProviderType.OPENAI_COMPATIBLE,
            apiKey = "",
            capabilities = setOf(ProviderCapability.CHAT),
        )
        val resourceIds = listOf("phase3-mcp", "phase3-prompt", "phase3-tool")

        try {
            ProviderRepository.restoreProfiles(listOf(provider))
            ConfigRepository.restoreProfiles(listOf(config), config.id)
            resourceIds.forEach(ResourceCenterRepository::deleteResource)
            ResourceCenterRepository.saveResource(
                ResourceCenterItem(
                    resourceId = "phase3-mcp",
                    kind = ResourceCenterKind.MCP_SERVER,
                    name = "Phase3 MCP",
                    payloadJson = """{"url":"https://mcp.example.invalid/http","timeoutSeconds":45}""",
                ),
            )
            ResourceCenterRepository.setProjection(
                ConfigResourceProjection(
                    configId = config.id,
                    resourceId = "phase3-mcp",
                    kind = ResourceCenterKind.MCP_SERVER,
                    active = true,
                    sortIndex = 0,
                ),
            )
            ResourceCenterRepository.saveResource(
                ResourceCenterItem(
                    resourceId = "phase3-prompt",
                    kind = ResourceCenterKind.SKILL,
                    skillKind = SkillResourceKind.PROMPT,
                    name = "Phase3 Prompt",
                    description = "Prompt projection",
                    content = "Use the resource center prompt.",
                ),
            )
            ResourceCenterRepository.setProjection(
                ConfigResourceProjection(
                    configId = config.id,
                    resourceId = "phase3-prompt",
                    kind = ResourceCenterKind.SKILL,
                    active = true,
                    priority = 7,
                    sortIndex = 1,
                ),
            )
            ResourceCenterRepository.saveResource(
                ResourceCenterItem(
                    resourceId = "phase3-tool",
                    kind = ResourceCenterKind.TOOL,
                    name = "format_weather",
                    description = "Format weather payload",
                    payloadJson = """{"resultTemplate":"weather={{weather}}","inputSchema":{"type":"object","properties":{"weather":{"type":"string"}}}}""",
                ),
            )
            ResourceCenterRepository.setProjection(
                ConfigResourceProjection(
                    configId = config.id,
                    resourceId = "phase3-tool",
                    kind = ResourceCenterKind.TOOL,
                    active = true,
                    sortIndex = 2,
                ),
            )

            val ctx = RuntimeContextResolver.resolve(
                event = RuntimeIngressEvent(
                    platform = RuntimePlatform.APP_CHAT,
                    conversationId = "phase3-conversation",
                    messageId = "msg-1",
                    sender = SenderInfo(userId = "user-1"),
                    messageType = MessageType.OtherMessage,
                    text = "hello",
                ),
                bot = BotProfile(
                    id = "phase3-bot",
                    defaultProviderId = provider.id,
                    configProfileId = config.id,
                    defaultPersonaId = "",
                ),
            )

            assertEquals("phase3-mcp", ctx.mcpServers.single().serverId)
            assertEquals("https://mcp.example.invalid/http", ctx.toolSourceContext.mcpServers.single().url)
            assertEquals("streamable_http", ctx.toolSourceContext.mcpServers.single().transport)
            assertEquals("Use the resource center prompt.", ctx.promptSkills.single().content)
            assertEquals(7, ctx.promptSkills.single().priority)
            assertEquals("phase3-tool", ctx.toolSkills.single().skillId)
            assertEquals("weather={{weather}}", ctx.toolSkills.single().resultTemplate)
            assertTrue(ctx.toolSkills.single().inputSchema.containsKey("properties"))
        } finally {
            resourceIds.forEach(ResourceCenterRepository::deleteResource)
            ProviderRepository.restoreProfiles(providerSnapshot)
            ConfigRepository.restoreProfiles(configSnapshot, selectedConfigId)
        }
    }
}
