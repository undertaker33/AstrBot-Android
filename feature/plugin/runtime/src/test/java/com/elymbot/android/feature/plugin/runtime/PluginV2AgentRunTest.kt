package com.elymbot.android.feature.plugin.runtime

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginV2AgentRunTest {

    @Test
    fun agent_run_executes_fake_llm_and_fake_tool_loop() = runBlocking {
        val llmCalls = AtomicInteger(0)
        val runner = agentRunner(
            llmPort = PluginV2HostLlmPort { request ->
                when (llmCalls.incrementAndGet()) {
                    1 -> PluginV2HostLlmPortResult(
                        text = "",
                        finishReason = "tool_calls",
                        providerId = request.providerId,
                        modelId = request.modelId,
                        usage = PluginLlmUsageSnapshot(promptTokens = 4, completionTokens = 3, totalTokens = 7),
                        toolCalls = listOf(
                            PluginLlmToolCall(
                                toolCallId = "call-search",
                                toolName = "search",
                                arguments = linkedMapOf("q" to "elymbot"),
                            ),
                        ),
                    )

                    else -> {
                        assertTrue(request.messages.any { it.role == "tool" && it.text.contains("tool-result") })
                        PluginV2HostLlmPortResult(
                            text = "final answer",
                            finishReason = "stop",
                            providerId = request.providerId,
                            modelId = request.modelId,
                            usage = PluginLlmUsageSnapshot(promptTokens = 5, completionTokens = 2, totalTokens = 7),
                        )
                    }
                }
            },
            toolExecutor = PluginV2ToolExecutor { args ->
                PluginToolResult(
                    toolCallId = args.toolCallId,
                    requestId = args.requestId,
                    toolId = args.toolId,
                    status = PluginToolResultStatus.SUCCESS,
                    text = "tool-result",
                )
            },
        )

        val result = runner.run(
            AgentRunRequest(
                context = hostContext(),
                agent = compiledAgent(tools = listOf("search")),
                input = "Find status",
                snapshot = snapshotWithTools(pluginTool("com.example.agent.run", "search")),
            ),
        )

        assertEquals("final answer", result.text)
        assertEquals(1, result.toolCallCount)
        assertEquals("provider-a", result.providerId)
        assertEquals("model-a-1", result.modelId)
        assertEquals(14, result.usage?.totalTokens)
        assertTrue(result.succeeded)
        assertEquals(2, llmCalls.get())
    }

    @Test
    fun max_tool_calls_stops_agent_run() = runBlocking {
        val runner = agentRunner(
            llmPort = PluginV2HostLlmPort { request ->
                PluginV2HostLlmPortResult(
                    text = "",
                    finishReason = "tool_calls",
                    providerId = request.providerId,
                    modelId = request.modelId,
                    toolCalls = listOf(PluginLlmToolCall(toolName = "search", arguments = emptyMap())),
                )
            },
            toolExecutor = PluginV2ToolExecutor { args ->
                PluginToolResult(
                    toolCallId = args.toolCallId,
                    requestId = args.requestId,
                    toolId = args.toolId,
                    status = PluginToolResultStatus.SUCCESS,
                    text = "loop",
                )
            },
        )

        val result = runner.run(
            AgentRunRequest(
                context = hostContext(),
                agent = compiledAgent(tools = listOf("search")),
                input = "loop",
                snapshot = snapshotWithTools(pluginTool("com.example.agent.run", "search")),
                limits = AgentRunLimits(maxToolCalls = 1, maxDepth = 8),
            ),
        )

        assertFalse(result.succeeded)
        assertEquals("agent_max_tool_calls_exceeded", result.failureCode)
        assertEquals(1, result.toolCallCount)
    }

    @Test
    fun timeout_stops_agent_run() = runBlocking {
        val runner = agentRunner(
            llmPort = PluginV2HostLlmPort {
                delay(200)
                PluginV2HostLlmPortResult(
                    text = "late",
                    finishReason = "stop",
                    providerId = "provider-a",
                    modelId = "model-a-1",
                )
            },
        )

        val result = runner.run(
            AgentRunRequest(
                context = hostContext(),
                agent = compiledAgent(),
                input = "slow",
                snapshot = snapshotWithTools(),
                limits = AgentRunLimits(timeoutMs = 25),
            ),
        )

        assertFalse(result.succeeded)
        assertEquals("agent_timeout", result.failureCode)
    }

    @Test
    fun agent_run_rejects_unauthorized_toolsource() = runBlocking {
        val runner = agentRunner(
            llmPort = PluginV2HostLlmPort { request ->
                PluginV2HostLlmPortResult(
                    text = "",
                    finishReason = "tool_calls",
                    providerId = request.providerId,
                    modelId = request.modelId,
                    toolCalls = listOf(PluginLlmToolCall(toolName = "web.search", arguments = emptyMap())),
                )
            },
        )

        val result = runner.run(
            AgentRunRequest(
                context = hostContext(),
                agent = compiledAgent(tools = listOf("web.search")),
                input = "search",
                snapshot = snapshotWithTools(
                    tool("web", "web.search", PluginToolSourceKind.WEB_SEARCH),
                    availability = mapOf(
                        "web.search" to PluginV2ToolAvailabilitySnapshot(
                            toolName = "web.search",
                            toolId = "web:web.search",
                            pluginId = "web",
                            sourceKind = PluginToolSourceKind.WEB_SEARCH,
                            registryActive = true,
                            personaEnabled = true,
                            capabilityAllowed = true,
                            sourceProviderAvailable = false,
                            available = false,
                            firstFailureReason = PluginV2ToolAvailabilityFailureReason.SourceUnavailable,
                        ),
                    ),
                ),
            ),
        )

        assertFalse(result.succeeded)
        assertEquals("agent_tool_unavailable", result.failureCode)
    }

    @Test
    fun agent_run_bypasses_plugin_llm_hooks() = runBlocking {
        var bypassFlag = false
        val runner = agentRunner(
            llmPort = PluginV2HostLlmPort { request ->
                bypassFlag = request.bypassPluginLlmHooks
                PluginV2HostLlmPortResult(
                    text = "ok",
                    finishReason = "stop",
                    providerId = request.providerId,
                    modelId = request.modelId,
                )
            },
        )

        val result = runner.run(
            AgentRunRequest(
                context = hostContext(),
                agent = compiledAgent(),
                input = "hello",
                snapshot = snapshotWithTools(),
            ),
        )

        assertTrue(result.succeeded)
        assertTrue(bypassFlag)
    }
}
