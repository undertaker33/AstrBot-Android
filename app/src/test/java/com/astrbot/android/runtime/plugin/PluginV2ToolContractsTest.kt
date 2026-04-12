package com.astrbot.android.runtime.plugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginV2ToolContractsTest {
    @Test
    fun pluginToolDescriptor_builds_phase5_tool_id_and_sanitizes_fields() {
        val descriptor = PluginToolDescriptor(
            pluginId = "com.example.tools",
            name = " summarize ",
            description = " summarize latest response ",
            visibility = PluginToolVisibility.LLM_VISIBLE,
            sourceKind = PluginToolSourceKind.PLUGIN_V2,
            inputSchema = linkedMapOf(
                "type" to "object",
                "properties" to linkedMapOf(
                    "text" to linkedMapOf("type" to "string"),
                ),
            ),
            metadata = linkedMapOf(
                "surface" to "bootstrap",
                "version" to 5,
            ),
        )

        assertEquals("com.example.tools:summarize", descriptor.toolId)
        assertEquals("summarize", descriptor.name)
        assertEquals("summarize latest response", descriptor.description)
        assertEquals(PluginToolVisibility.LLM_VISIBLE, descriptor.visibility)
        assertEquals(PluginToolSourceKind.PLUGIN_V2, descriptor.sourceKind)
        assertEquals("object", descriptor.inputSchema["type"])
        assertEquals("bootstrap", descriptor.metadata?.get("surface"))
        assertEquals(5, descriptor.metadata?.get("version"))
    }

    @Test
    fun pluginToolDescriptor_visibility_tracks_public_contract_not_activation_state() {
        val descriptor = PluginToolDescriptor(
            pluginId = "com.example.tools",
            name = "lookup",
            visibility = PluginToolVisibility.HOST_INTERNAL,
            sourceKind = PluginToolSourceKind.MCP,
            inputSchema = linkedMapOf("properties" to emptyMap<String, Any>()),
        )

        assertEquals("com.example.tools:lookup", descriptor.toolId)
        assertEquals(PluginToolVisibility.HOST_INTERNAL, descriptor.visibility)
        assertEquals(PluginToolSourceKind.MCP, descriptor.sourceKind)
    }

    @Test
    fun pluginToolArgs_and_result_reuse_json_like_whitelist() {
        val args = PluginToolArgs(
            toolCallId = "call-1",
            requestId = "req-1",
            toolId = "com.example.tools:summarize",
            attemptIndex = 1,
            payload = linkedMapOf(
                "text" to "hello",
                "limit" to 3,
                "nested" to linkedMapOf("enabled" to true),
            ),
            metadata = linkedMapOf("source" to "llm"),
        )
        val result = PluginToolResult(
            toolCallId = "call-1",
            requestId = "req-1",
            toolId = "com.example.tools:summarize",
            status = PluginToolResultStatus.SUCCESS,
            text = "done",
            structuredContent = linkedMapOf("summary" to "done"),
            metadata = linkedMapOf("normalized" to true),
        )

        assertEquals(1, args.attemptIndex)
        assertEquals("hello", args.payload["text"])
        assertEquals("llm", args.metadata?.get("source"))
        assertEquals(PluginToolResultStatus.SUCCESS, result.status)
        assertEquals("done", result.structuredContent?.get("summary"))
        assertEquals(true, result.metadata?.get("normalized"))
    }

    @Test
    fun pluginToolResult_rejects_blank_error_code_on_failure() {
        val failure = runCatching {
            PluginToolResult(
                toolCallId = "call-1",
                requestId = "req-1",
                toolId = "com.example.tools:summarize",
                status = PluginToolResultStatus.ERROR,
                errorCode = " ",
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message?.contains("errorCode") == true)
    }

    @Test
    fun pluginToolLifecycleHookRegistrationInput_exposes_phase5_contract_surface() {
        val handler = PluginV2CallbackHandle {}
        val input = ToolLifecycleHookRegistrationInput(
            registrationKey = "tool.before_execute",
            hook = "on_using_llm_tool",
            priority = 3,
            metadata = BootstrapRegistrationMetadata(values = mapOf("phase" to "5")),
            handler = handler,
        )

        assertEquals("tool.before_execute", input.registrationKey)
        assertEquals("on_using_llm_tool", input.hook)
        assertEquals(3, input.priority)
        assertEquals("5", input.metadata.values["phase"])
        assertSame(handler, input.handler)
    }
}
