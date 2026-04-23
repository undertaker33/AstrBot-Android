package com.astrbot.android.feature.plugin.runtime.toolsource

import com.astrbot.android.core.runtime.context.PromptSkillProjection
import com.astrbot.android.core.runtime.context.RuntimePlatform
import com.astrbot.android.core.runtime.context.ToolSkillProjection
import com.astrbot.android.feature.plugin.runtime.PluginToolArgs
import com.astrbot.android.feature.plugin.runtime.PluginToolResultStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillToolSourceProviderTest {

    @Test
    fun list_bindings_ignores_prompt_skills_and_registers_tool_skills_only() = runBlocking {
        val provider = SkillToolSourceProvider(contextResolver = noopContextResolver)
        val context = ToolSourceRegistryIngestContext(
            toolSourceContext = toolSourceContext(
                promptSkills = listOf(
                    PromptSkillProjection(
                        skillId = "style",
                        name = "Style",
                        content = "Answer warmly.",
                        priority = 10,
                    ),
                ),
                toolSkills = listOf(
                    ToolSkillProjection(
                        skillId = "format_weather",
                        name = "format_weather",
                        description = "Format weather payload",
                        resultTemplate = "weather={{weather}}",
                    ),
                ),
            ),
        )

        val bindings = provider.listBindings(context)

        assertEquals(1, bindings.size)
        assertEquals("skill.format_weather", bindings.single().identity.ownerId)
        assertEquals("format_weather", bindings.single().descriptor.name)
    }

    @Test
    fun invoke_executes_tool_skill_template_with_payload() = runBlocking {
        val provider = SkillToolSourceProvider(contextResolver = noopContextResolver)
        val context = toolSourceContext(
            toolSkills = listOf(
                ToolSkillProjection(
                    skillId = "format_weather",
                    name = "format_weather",
                    description = "Format weather payload",
                    resultTemplate = "weather={{weather}}, city={{city}}",
                ),
            ),
        )

        val result = provider.invoke(
            ToolSourceInvokeRequest(
                identity = ToolSourceIdentity(
                    sourceKind = provider.sourceKind,
                    ownerId = "skill.format_weather",
                    sourceRef = "format_weather",
                    displayName = "format_weather",
                ),
                args = PluginToolArgs(
                    toolCallId = "call-1",
                    requestId = "req-1",
                    toolId = "skill.format_weather:format_weather",
                    payload = mapOf("weather" to "rain", "city" to "Fuzhou"),
                ),
                timeoutMs = 1_000L,
                toolSourceContext = context,
            ),
        ).result

        assertEquals(PluginToolResultStatus.SUCCESS, result.status)
        assertTrue(result.text.orEmpty().contains("weather=rain"))
        assertTrue(result.text.orEmpty().contains("city=Fuzhou"))
    }

    private fun toolSourceContext(
        promptSkills: List<PromptSkillProjection> = emptyList(),
        toolSkills: List<ToolSkillProjection> = emptyList(),
    ): ToolSourceContext {
        return ToolSourceContext(
            requestId = "req-1",
            platform = RuntimePlatform.APP_CHAT,
            configProfileId = "config-1",
            webSearchEnabled = false,
            activeCapabilityEnabled = false,
            mcpServers = emptyList(),
            promptSkills = promptSkills,
            toolSkills = toolSkills,
            conversationId = "conversation-1",
        )
    }
}

private val noopContextResolver = object : FutureToolSourceContextResolver {
    override fun resolveForConfig(configProfileId: String): ToolSourceContext {
        return ToolSourceContext(
            requestId = "",
            platform = RuntimePlatform.APP_CHAT,
            configProfileId = configProfileId,
            webSearchEnabled = false,
            activeCapabilityEnabled = false,
            mcpServers = emptyList(),
            promptSkills = emptyList(),
            toolSkills = emptyList(),
            conversationId = "",
        )
    }
}
