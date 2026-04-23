package com.astrbot.android.feature.plugin.runtime.toolsource

import com.astrbot.android.core.runtime.tool.ToolDescriptor
import com.astrbot.android.core.runtime.tool.ToolSourceKind
import org.junit.Assert.assertEquals
import org.junit.Test

class FutureToolSourceContractsTest {

    @Test
    fun toPluginToolDescriptor_preserves_nested_schema_arrays_objects_and_nulls() {
        val descriptor = ToolDescriptor(
            id = "skill:tool",
            ownerId = "skill",
            name = "tool",
            description = "nested schema",
            inputSchemaJson = """
                {
                  "type": "object",
                  "properties": {
                    "config": {
                      "type": "object",
                      "properties": {
                        "flags": {
                          "type": "array",
                          "items": { "type": "boolean" }
                        },
                        "nullable": null
                      }
                    }
                  }
                }
            """.trimIndent(),
            source = ToolSourceKind.SKILL,
        )

        val pluginDescriptor = descriptor.toPluginToolDescriptor()

        assertEquals("object", pluginDescriptor.inputSchema["type"])
        val properties = pluginDescriptor.inputSchema["properties"] as Map<*, *>
        val config = properties["config"] as Map<*, *>
        val configProperties = config["properties"] as Map<*, *>
        val flags = configProperties["flags"] as Map<*, *>
        assertEquals("array", flags["type"])
        assertEquals(mapOf("type" to "boolean"), flags["items"])
        assertEquals(null, configProperties["nullable"])
    }
}
