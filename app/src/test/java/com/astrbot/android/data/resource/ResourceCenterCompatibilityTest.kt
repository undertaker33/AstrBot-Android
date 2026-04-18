package com.astrbot.android.feature.resource.data

import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.McpServerEntry
import com.astrbot.android.model.ResourceCenterKind
import com.astrbot.android.model.SkillEntry
import com.astrbot.android.model.SkillResourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceCenterCompatibilityTest {
    @Test
    fun mcpServerEntry_defaults_to_streamable_http() {
        assertEquals("streamable_http", McpServerEntry().transport)
    }

    @Test
    fun projectionsFromConfigProfile_preserveLegacyMcpAndPromptSkills() {
        val profile = ConfigProfile(
            id = "config-a",
            mcpServers = listOf(
                McpServerEntry(
                    serverId = "mcp-weather",
                    name = "Weather MCP",
                    url = "https://mcp.example.com/http",
                    transport = "streamable_http",
                    command = "",
                    args = listOf("--verbose"),
                    headers = mapOf("Authorization" to "Bearer demo"),
                    timeoutSeconds = 45,
                    active = true,
                ),
            ),
            skills = listOf(
                SkillEntry(
                    skillId = "skill-summary",
                    name = "Summary",
                    description = "Summarize context",
                    content = "Be concise.",
                    priority = 3,
                    active = false,
                ),
            ),
        )

        val snapshot = ResourceCenterCompatibility.projectionsFromConfigProfile(profile)

        assertEquals(2, snapshot.resources.size)
        assertEquals(2, snapshot.projections.size)

        val mcpResource = snapshot.resources.single { it.kind == ResourceCenterKind.MCP_SERVER }
        assertEquals("mcp-weather", mcpResource.resourceId)
        assertEquals("Weather MCP", mcpResource.name)
        assertTrue("\"timeoutSeconds\":45" in mcpResource.payloadJson)

        val skillResource = snapshot.resources.single { it.kind == ResourceCenterKind.SKILL }
        assertEquals("skill-summary", skillResource.resourceId)
        assertEquals(SkillResourceKind.PROMPT, skillResource.skillKind)
        assertEquals("Be concise.", skillResource.content)

        val mcpProjection = snapshot.projections.single { it.kind == ResourceCenterKind.MCP_SERVER }
        assertEquals("config-a", mcpProjection.configId)
        assertEquals("mcp-weather", mcpProjection.resourceId)
        assertEquals(0, mcpProjection.sortIndex)
        assertTrue(mcpProjection.active)

        val skillProjection = snapshot.projections.single { it.kind == ResourceCenterKind.SKILL }
        assertEquals("skill-summary", skillProjection.resourceId)
        assertEquals(3, skillProjection.priority)
        assertEquals(0, skillProjection.sortIndex)
        assertTrue(!skillProjection.active)
    }
}
