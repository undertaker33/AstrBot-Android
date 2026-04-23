package com.astrbot.android.core.runtime.context

import com.astrbot.android.model.ConfigResourceProjection
import com.astrbot.android.model.ResourceCenterCompatibilitySnapshot
import com.astrbot.android.model.ResourceCenterItem
import com.astrbot.android.model.ResourceCenterKind
import com.astrbot.android.model.SkillResourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeResourceProjectionResolverTest {

    @Test
    fun `non streamable mcp resources are filtered out of runtime projection`() {
        val snapshot = ResourceCenterCompatibilitySnapshot(
            resources = listOf(
                ResourceCenterItem(
                    resourceId = "legacy-sse",
                    kind = ResourceCenterKind.MCP_SERVER,
                    name = "Legacy SSE",
                    payloadJson = """{"url":"https://mcp.example.com/sse","transport":"legacy_sse"}""",
                ),
            ),
            projections = listOf(
                ConfigResourceProjection(
                    configId = "config-a",
                    resourceId = "legacy-sse",
                    kind = ResourceCenterKind.MCP_SERVER,
                    active = true,
                ),
            ),
        )

        val projection = RuntimeSkillProjectionResolver.fromResourceCenterSnapshot(
            snapshot = snapshot,
            platform = RuntimePlatform.APP_CHAT,
            trigger = IngressTrigger.USER_MESSAGE,
        )

        assertTrue(projection.mcpServers.isEmpty())
    }

    @Test
    fun `blank transport defaults to streamable http when url is present`() {
        val snapshot = ResourceCenterCompatibilitySnapshot(
            resources = listOf(
                ResourceCenterItem(
                    resourceId = "remote-http",
                    kind = ResourceCenterKind.MCP_SERVER,
                    name = "Remote HTTP",
                    payloadJson = """{"url":"https://mcp.example.com/http","transport":""}""",
                ),
            ),
            projections = listOf(
                ConfigResourceProjection(
                    configId = "config-a",
                    resourceId = "remote-http",
                    kind = ResourceCenterKind.MCP_SERVER,
                    active = true,
                ),
            ),
        )

        val projection = RuntimeSkillProjectionResolver.fromResourceCenterSnapshot(
            snapshot = snapshot,
            platform = RuntimePlatform.APP_CHAT,
            trigger = IngressTrigger.USER_MESSAGE,
        )

        assertEquals(1, projection.mcpServers.size)
        assertEquals("streamable_http", projection.mcpServers.single().transport)
    }

    @Test
    fun `tool skill input schema preserves nested objects arrays and nulls`() {
        val snapshot = ResourceCenterCompatibilitySnapshot(
            resources = listOf(
                ResourceCenterItem(
                    resourceId = "tool-skill",
                    kind = ResourceCenterKind.SKILL,
                    skillKind = SkillResourceKind.TOOL,
                    name = "Tool Skill",
                    description = "Nested schema tool",
                    payloadJson = """
                        {
                          "inputSchema": {
                            "type": "object",
                            "properties": {
                              "config": {
                                "type": "object",
                                "properties": {
                                  "tags": {
                                    "type": "array",
                                    "items": { "type": "string" }
                                  },
                                  "nullable": null
                                }
                              }
                            }
                          }
                        }
                    """.trimIndent(),
                ),
            ),
            projections = listOf(
                ConfigResourceProjection(
                    configId = "config-a",
                    resourceId = "tool-skill",
                    kind = ResourceCenterKind.SKILL,
                    active = true,
                ),
            ),
        )

        val projection = RuntimeSkillProjectionResolver.fromResourceCenterSnapshot(
            snapshot = snapshot,
            platform = RuntimePlatform.APP_CHAT,
            trigger = IngressTrigger.USER_MESSAGE,
        )

        val inputSchema = projection.toolSkills.single().inputSchema
        assertEquals("object", inputSchema["type"])
        val properties = inputSchema["properties"] as Map<*, *>
        val config = properties["config"] as Map<*, *>
        val configProperties = config["properties"] as Map<*, *>
        val tags = configProperties["tags"] as Map<*, *>
        assertEquals("array", tags["type"])
        assertEquals(mapOf("type" to "string"), tags["items"])
        assertEquals(null, configProperties["nullable"])
    }
}
