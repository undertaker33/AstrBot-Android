package com.astrbot.android.feature.plugin.runtime

import com.astrbot.android.model.chat.MessageType
import com.astrbot.android.model.plugin.PluginExecutionProtocolJson
import com.astrbot.android.model.plugin.PluginV2StreamingMode
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

class PluginV2ToolLoopCoordinatorTest {

    @Test
    fun multiple_tool_calls_execute_serially_in_normalized_tool_call_order() = runBlocking {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 1L })
        val executed = CopyOnWriteArrayList<String>()
        val providerCalls = AtomicInteger(0)

        val coordinator = PluginV2LlmPipelineCoordinator(
            dispatchEngine = PluginV2DispatchEngine(logBus = logBus, clock = { 1L }),
            toolExecutor = PluginV2ToolExecutor { args ->
                val toolName = args.toolId.substringAfterLast(":")
                executed += toolName
                assertEquals(0, args.attemptIndex)
                PluginToolResult(
                    toolCallId = args.toolCallId,
                    requestId = args.requestId,
                    toolId = args.toolId,
                    status = PluginToolResultStatus.SUCCESS,
                    text = "tool-$toolName",
                    structuredContent = linkedMapOf("tool" to toolName),
                )
            },
            logBus = logBus,
            clock = { 1L },
            requestIdFactory = { "req-tool-loop-001" },
        )
        val event = sampleMessageEvent(rawText = "hello tool loop")

        val result = coordinator.runPreSendStages(
            input = pipelineInput(
                event = event,
                streamingMode = PluginV2StreamingMode.NON_STREAM,
            ) { request, _ ->
                when (providerCalls.incrementAndGet()) {
                    1 -> PluginV2ProviderInvocationResult.NonStreaming(
                        PluginLlmResponse(
                            requestId = request.requestId,
                            providerId = request.selectedProviderId,
                            modelId = request.selectedModelId,
                            text = "",
                            toolCalls = listOf(
                                PluginLlmToolCall(
                                    toolName = "alpha",
                                    arguments = linkedMapOf("q" to "a"),
                                ),
                                PluginLlmToolCall(
                                    toolName = "beta",
                                    arguments = linkedMapOf("q" to "b"),
                                ),
                            ),
                        ),
                    )

                    else -> PluginV2ProviderInvocationResult.NonStreaming(
                        PluginLlmResponse(
                            requestId = request.requestId,
                            providerId = request.selectedProviderId,
                            modelId = request.selectedModelId,
                            text = "final-assistant",
                            toolCalls = emptyList(),
                        ),
                    )
                }
            },
            snapshot = snapshotOf(
                llmFixture(
                    pluginId = "com.example.tool.loop.order",
                    logBus = logBus,
                ) { hostApi ->
                    hostApi.registerTool(
                        descriptor = PluginToolDescriptor(
                            pluginId = "com.example.tool.loop.order",
                            name = "alpha",
                            description = "alpha tool",
                            visibility = PluginToolVisibility.LLM_VISIBLE,
                            sourceKind = PluginToolSourceKind.PLUGIN_V2,
                            inputSchema = linkedMapOf("type" to "object"),
                        ),
                        handler = PluginV2CallbackHandle {},
                    )
                    hostApi.registerTool(
                        descriptor = PluginToolDescriptor(
                            pluginId = "com.example.tool.loop.order",
                            name = "beta",
                            description = "beta tool",
                            visibility = PluginToolVisibility.LLM_VISIBLE,
                            sourceKind = PluginToolSourceKind.PLUGIN_V2,
                            inputSchema = linkedMapOf("type" to "object"),
                        ),
                        handler = PluginV2CallbackHandle {},
                    )
                },
            ),
        )

        assertEquals(listOf("alpha", "beta"), executed.toList())
        assertEquals(listOf("alpha", "beta"), result.executedToolNames)
        assertEquals("final-assistant", result.sendableResult.text)
    }

    @Test
    fun web_search_tool_round_reinjects_empty_assistant_tool_calls_before_tool_result() = runBlocking {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 1L })
        val seenRequests = CopyOnWriteArrayList<PluginProviderRequest>()
        val providerCalls = AtomicInteger(0)

        val coordinator = PluginV2LlmPipelineCoordinator(
            dispatchEngine = PluginV2DispatchEngine(logBus = logBus, clock = { 1L }),
            toolExecutor = PluginV2ToolExecutor { args ->
                PluginToolResult(
                    toolCallId = args.toolCallId,
                    requestId = args.requestId,
                    toolId = args.toolId,
                    status = PluginToolResultStatus.SUCCESS,
                    text = "web search result",
                )
            },
            logBus = logBus,
            clock = { 1L },
            requestIdFactory = { "req-web-search-loop" },
        )

        coordinator.runPreSendStages(
            input = pipelineInput(
                event = sampleMessageEvent(rawText = "search AstrBot news"),
                streamingMode = PluginV2StreamingMode.NON_STREAM,
                systemPrompt = "Base persona instruction.",
            ) { request, _ ->
                seenRequests += request
                when (providerCalls.incrementAndGet()) {
                    1 -> PluginV2ProviderInvocationResult.NonStreaming(
                        PluginLlmResponse(
                            requestId = request.requestId,
                            providerId = request.selectedProviderId,
                            modelId = request.selectedModelId,
                            text = "",
                            toolCalls = listOf(
                                PluginLlmToolCall(
                                    toolCallId = "call_web_search",
                                    toolName = "web_search",
                                    arguments = linkedMapOf("query" to "AstrBot"),
                                ),
                            ),
                        ),
                    )

                    else -> PluginV2ProviderInvocationResult.NonStreaming(
                        PluginLlmResponse(
                            requestId = request.requestId,
                            providerId = request.selectedProviderId,
                            modelId = request.selectedModelId,
                            text = "final answer",
                        ),
                    )
                }
            },
            snapshot = snapshotOf(
                llmFixture(
                    pluginId = "com.example.tool.loop.web.search",
                    logBus = logBus,
                ) { hostApi ->
                    hostApi.registerTool(
                        descriptor = PluginToolDescriptor(
                            pluginId = "com.example.tool.loop.web.search",
                            name = "web_search",
                            description = "search the web",
                            visibility = PluginToolVisibility.LLM_VISIBLE,
                            sourceKind = PluginToolSourceKind.PLUGIN_V2,
                            inputSchema = linkedMapOf("type" to "object"),
                        ),
                        handler = PluginV2CallbackHandle {},
                    )
                },
            ),
        )

        val second = seenRequests[1]
        assertEquals("Base persona instruction.", second.systemPrompt)
        val assistant = second.messages[second.messages.size - 2]
        assertEquals(PluginProviderMessageRole.ASSISTANT, assistant.role)
        assertTrue(assistant.parts.isEmpty())
        assertEquals(listOf("call_web_search"), assistant.toolCalls.map { it.normalizedId })
        assertEquals(listOf("web_search"), assistant.toolCalls.map { it.normalizedToolName })

        val tool = second.messages.last()
        assertEquals(PluginProviderMessageRole.TOOL, tool.role)
        assertEquals("web_search", tool.name)
        val hostMetadata = tool.metadata?.get("__host") as? Map<*, *>
        assertEquals("call_web_search", hostMetadata?.get("toolCallId"))
        assertEquals(
            "web search result",
            tool.parts.filterIsInstance<PluginProviderMessagePartDto.TextPart>().single().text,
        )
    }

    @Test
    fun tool_result_delivery_handler_can_replace_tool_message_before_next_provider_call() = runBlocking {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 1L })
        val seenRequests = CopyOnWriteArrayList<PluginProviderRequest>()
        val providerCalls = AtomicInteger(0)
        val deliveredResults = CopyOnWriteArrayList<String>()

        val coordinator = PluginV2LlmPipelineCoordinator(
            dispatchEngine = PluginV2DispatchEngine(logBus = logBus, clock = { 1L }),
            toolExecutor = PluginV2ToolExecutor { args ->
                PluginToolResult(
                    toolCallId = args.toolCallId,
                    requestId = args.requestId,
                    toolId = args.toolId,
                    status = PluginToolResultStatus.SUCCESS,
                    text = "raw web search facts",
                    structuredContent = linkedMapOf(
                        "query" to "today news",
                        "results" to listOf(
                            linkedMapOf(
                                "title" to "Headline",
                                "snippet" to "Confirmed fact.",
                                "source" to "Example News",
                                "index" to 1,
                            ),
                        ),
                    ),
                )
            },
            logBus = logBus,
            clock = { 1L },
            requestIdFactory = { "req-web-search-direct-news" },
        )

        coordinator.runPreSendStages(
            input = pipelineInput(
                event = sampleMessageEvent(rawText = "today news"),
                streamingMode = PluginV2StreamingMode.NON_STREAM,
            ) { request, _ ->
                seenRequests += request
                when (providerCalls.incrementAndGet()) {
                    1 -> PluginV2ProviderInvocationResult.NonStreaming(
                        PluginLlmResponse(
                            requestId = request.requestId,
                            providerId = request.selectedProviderId,
                            modelId = request.selectedModelId,
                            toolCalls = listOf(
                                PluginLlmToolCall(
                                    toolCallId = "call_web_search",
                                    toolName = "web_search",
                                    arguments = linkedMapOf("query" to "today news"),
                                ),
                            ),
                        ),
                    )

                    else -> {
                        val toolText = request.messages
                            .last { it.role == PluginProviderMessageRole.TOOL }
                            .parts
                            .filterIsInstance<PluginProviderMessagePartDto.TextPart>()
                            .joinToString("\n") { it.text }
                        assertTrue(toolText.contains("raw web search facts"))
                        assertTrue(toolText.contains("Do not repeat the news items"))
                        assertTrue(toolText.contains("Only provide a brief evaluation"))
                        PluginV2ProviderInvocationResult.NonStreaming(
                            PluginLlmResponse(
                                requestId = request.requestId,
                                providerId = request.selectedProviderId,
                                modelId = request.selectedModelId,
                                text = "commentary only",
                            ),
                        )
                    }
                }
            },
            snapshot = snapshotOf(
                llmFixture(
                    pluginId = "com.example.tool.loop.direct.news",
                    logBus = logBus,
                ) { hostApi ->
                    hostApi.registerTool(
                        descriptor = PluginToolDescriptor(
                            pluginId = "com.example.tool.loop.direct.news",
                            name = "web_search",
                            description = "search the web",
                            visibility = PluginToolVisibility.LLM_VISIBLE,
                            sourceKind = PluginToolSourceKind.PLUGIN_V2,
                            inputSchema = linkedMapOf("type" to "object"),
                        ),
                        handler = PluginV2CallbackHandle {},
                    )
                },
            ),
            toolResultDeliveryHandler = PluginV2ToolResultDeliveryHandler { request ->
                deliveredResults += request.result.text.orEmpty()
                PluginToolResult(
                    toolCallId = request.result.toolCallId,
                    requestId = request.result.requestId,
                    toolId = request.result.toolId,
                    status = request.result.status,
                    text = request.result.text + "\n\nDo not repeat the news items. Only provide a brief evaluation.",
                    structuredContent = request.result.structuredContent,
                    metadata = request.result.metadata,
                )
            },
        )

        assertEquals(listOf("raw web search facts"), deliveredResults.toList())
    }

    @Test
    fun repeated_web_search_tool_rounds_preserve_assistant_tool_adjacency_across_reinjection() = runBlocking {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 1L })
        val seenRequests = CopyOnWriteArrayList<PluginProviderRequest>()
        val providerCalls = AtomicInteger(0)

        val coordinator = PluginV2LlmPipelineCoordinator(
            dispatchEngine = PluginV2DispatchEngine(logBus = logBus, clock = { 1L }),
            toolExecutor = PluginV2ToolExecutor { args ->
                PluginToolResult(
                    toolCallId = args.toolCallId,
                    requestId = args.requestId,
                    toolId = args.toolId,
                    status = PluginToolResultStatus.SUCCESS,
                    text = "result for ${args.payload["query"]}",
                )
            },
            logBus = logBus,
            clock = { 1L },
            requestIdFactory = { "req-web-search-multi-round" },
        )

        val result = coordinator.runPreSendStages(
            input = pipelineInput(
                event = sampleMessageEvent(rawText = "search 异环"),
                streamingMode = PluginV2StreamingMode.NON_STREAM,
            ) { request, _ ->
                seenRequests += request
                when (providerCalls.incrementAndGet()) {
                    1 -> PluginV2ProviderInvocationResult.NonStreaming(
                        PluginLlmResponse(
                            requestId = request.requestId,
                            providerId = request.selectedProviderId,
                            modelId = request.selectedModelId,
                            text = "",
                            toolCalls = listOf(
                                PluginLlmToolCall(
                                    toolCallId = "call_web_search_1",
                                    toolName = "web_search",
                                    arguments = linkedMapOf("query" to "异环 开服时间 游戏 上线"),
                                ),
                            ),
                        ),
                    )

                    2 -> PluginV2ProviderInvocationResult.NonStreaming(
                        PluginLlmResponse(
                            requestId = request.requestId,
                            providerId = request.selectedProviderId,
                            modelId = request.selectedModelId,
                            text = "",
                            toolCalls = listOf(
                                PluginLlmToolCall(
                                    toolCallId = "call_web_search_2",
                                    toolName = "web_search",
                                    arguments = linkedMapOf("query" to "异环游戏 什么时候开服 上线时间"),
                                ),
                            ),
                        ),
                    )

                    else -> PluginV2ProviderInvocationResult.NonStreaming(
                        PluginLlmResponse(
                            requestId = request.requestId,
                            providerId = request.selectedProviderId,
                            modelId = request.selectedModelId,
                            text = "final answer",
                            toolCalls = emptyList(),
                        ),
                    )
                }
            },
            snapshot = snapshotOf(
                llmFixture(
                    pluginId = "com.example.tool.loop.web.search.multi",
                    logBus = logBus,
                ) { hostApi ->
                    hostApi.registerTool(
                        descriptor = PluginToolDescriptor(
                            pluginId = "com.example.tool.loop.web.search.multi",
                            name = "web_search",
                            description = "search the web",
                            visibility = PluginToolVisibility.LLM_VISIBLE,
                            sourceKind = PluginToolSourceKind.PLUGIN_V2,
                            inputSchema = linkedMapOf("type" to "object"),
                        ),
                        handler = PluginV2CallbackHandle {},
                    )
                },
            ),
        )

        assertEquals("final answer", result.sendableResult.text)
        assertEquals(3, seenRequests.size)
        assertEquals(
            listOf(
                PluginProviderMessageRole.USER,
                PluginProviderMessageRole.ASSISTANT,
                PluginProviderMessageRole.TOOL,
                PluginProviderMessageRole.ASSISTANT,
                PluginProviderMessageRole.TOOL,
            ),
            seenRequests[2].messages.map { it.role },
        )
        assertEquals(
            listOf(null, null, "call_web_search_1", null, "call_web_search_2"),
            seenRequests[2].messages.map { message ->
                ((message.metadata?.get("__host") as? Map<*, *>)?.get("toolCallId") as? String)
            },
        )
    }

    @Test
    fun tool_loop_resolves_descriptor_from_centralized_registry_not_raw_registry_scan() = runBlocking {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 1L })
        val executedToolIds = CopyOnWriteArrayList<String>()
        val providerCalls = AtomicInteger(0)
        val rawOnlyFixture = llmFixture(
            pluginId = "com.example.tool.loop.raw.only",
            logBus = logBus,
        ) { hostApi ->
            hostApi.registerTool(
                descriptor = PluginToolDescriptor(
                    pluginId = "com.example.tool.loop.raw.only",
                    name = "raw_only",
                    description = "raw only tool",
                    visibility = PluginToolVisibility.LLM_VISIBLE,
                    sourceKind = PluginToolSourceKind.PLUGIN_V2,
                    inputSchema = linkedMapOf("type" to "object"),
                ),
                handler = PluginV2CallbackHandle {},
            )
        }
        val registryDescriptor = PluginToolDescriptor(
            pluginId = "com.example.tool.loop.centralized",
            name = "centralized_only",
            description = "centralized only tool",
            visibility = PluginToolVisibility.LLM_VISIBLE,
            sourceKind = PluginToolSourceKind.PLUGIN_V2,
            inputSchema = linkedMapOf("type" to "object"),
        )
        val centralized = PluginV2ToolRegistry().compileCandidates(listOf(registryDescriptor))
        val registrySnapshot = requireNotNull(centralized.activeRegistry)

        val coordinator = PluginV2LlmPipelineCoordinator(
            dispatchEngine = PluginV2DispatchEngine(logBus = logBus, clock = { 1L }),
            toolExecutor = PluginV2ToolExecutor { args ->
                executedToolIds += args.toolId
                PluginToolResult(
                    toolCallId = args.toolCallId,
                    requestId = args.requestId,
                    toolId = args.toolId,
                    status = PluginToolResultStatus.SUCCESS,
                    text = "centralized-ok",
                )
            },
            logBus = logBus,
            clock = { 1L },
            requestIdFactory = { "req-tool-loop-centralized-registry" },
        )

        val result = coordinator.runPreSendStages(
            input = pipelineInput(
                event = sampleMessageEvent(rawText = "hello centralized registry"),
                streamingMode = PluginV2StreamingMode.NON_STREAM,
            ) { request, _ ->
                when (providerCalls.incrementAndGet()) {
                    1 -> PluginV2ProviderInvocationResult.NonStreaming(
                        PluginLlmResponse(
                            requestId = request.requestId,
                            providerId = request.selectedProviderId,
                            modelId = request.selectedModelId,
                            toolCalls = listOf(
                                PluginLlmToolCall(
                                    toolName = "centralized_only",
                                    arguments = linkedMapOf("q" to "centralized"),
                                ),
                            ),
                        ),
                    )

                    else -> PluginV2ProviderInvocationResult.NonStreaming(
                        PluginLlmResponse(
                            requestId = request.requestId,
                            providerId = request.selectedProviderId,
                            modelId = request.selectedModelId,
                            text = "assistant",
                            toolCalls = emptyList(),
                        ),
                    )
                }
            },
            snapshot = snapshotOf(rawOnlyFixture).copy(
                toolRegistrySnapshot = registrySnapshot,
                toolAvailabilityByName = mapOf(
                    "centralized_only" to PluginV2ToolAvailabilitySnapshot(
                        toolName = "centralized_only",
                        toolId = registryDescriptor.toolId,
                        pluginId = registryDescriptor.pluginId,
                        sourceKind = PluginToolSourceKind.PLUGIN_V2,
                        registryActive = true,
                        personaEnabled = true,
                        capabilityAllowed = true,
                        sourceProviderAvailable = true,
                        available = true,
                    ),
                ),
            ),
        )

        assertEquals(listOf(registryDescriptor.toolId), executedToolIds.toList())
        assertEquals("assistant", result.sendableResult.text)
    }

    @Test
    fun on_using_llm_tool_runs_before_executor_and_on_llm_tool_respond_runs_after_executor() = runBlocking {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 1L })
        val order = CopyOnWriteArrayList<String>()
        val providerCalls = AtomicInteger(0)

        val fixture = llmFixture(
            pluginId = "com.example.tool.loop.hooks",
            logBus = logBus,
        ) { hostApi ->
            hostApi.registerTool(
                descriptor = PluginToolDescriptor(
                    pluginId = "com.example.tool.loop.hooks",
                    name = "alpha",
                    description = "alpha tool",
                    visibility = PluginToolVisibility.LLM_VISIBLE,
                    sourceKind = PluginToolSourceKind.PLUGIN_V2,
                    inputSchema = linkedMapOf("type" to "object"),
                ),
                handler = PluginV2CallbackHandle {},
            )
            hostApi.registerLlmHook(
                LlmHookRegistrationInput(
                    registrationKey = "tool.using",
                    hook = "on_using_llm_tool",
                    priority = 100,
                    handler = EventAwareHandle { payload ->
                        val toolPayload = payload as PluginV2UsingLlmToolPayload
                        assertEquals("alpha", toolPayload.descriptor.name)
                        order += "using"
                    },
                ),
            )
            hostApi.registerLlmHook(
                LlmHookRegistrationInput(
                    registrationKey = "tool.respond",
                    hook = "on_llm_tool_respond",
                    priority = 100,
                    handler = EventAwareHandle { payload ->
                        val toolPayload = payload as PluginV2LlmToolRespondPayload
                        assertEquals("alpha", toolPayload.descriptor.name)
                        assertEquals("ok", toolPayload.result.text)
                        order += "respond"
                    },
                ),
            )
        }

        val coordinator = PluginV2LlmPipelineCoordinator(
            dispatchEngine = PluginV2DispatchEngine(logBus = logBus, clock = { 1L }),
            toolExecutor = PluginV2ToolExecutor { args ->
                order += "execute"
                PluginToolResult(
                    toolCallId = args.toolCallId,
                    requestId = args.requestId,
                    toolId = args.toolId,
                    status = PluginToolResultStatus.SUCCESS,
                    text = "ok",
                )
            },
            logBus = logBus,
            clock = { 1L },
            requestIdFactory = { "req-tool-loop-002" },
        )

        val event = sampleMessageEvent(rawText = "hello tool hook")

        val result = coordinator.runPreSendStages(
            input = pipelineInput(
                event = event,
                streamingMode = PluginV2StreamingMode.NON_STREAM,
            ) { request, _ ->
                when (providerCalls.incrementAndGet()) {
                    1 -> PluginV2ProviderInvocationResult.NonStreaming(
                        PluginLlmResponse(
                            requestId = request.requestId,
                            providerId = request.selectedProviderId,
                            modelId = request.selectedModelId,
                            toolCalls = listOf(
                                PluginLlmToolCall(
                                    toolName = "alpha",
                                    arguments = linkedMapOf("q" to "a"),
                                ),
                            ),
                        ),
                    )

                    else -> PluginV2ProviderInvocationResult.NonStreaming(
                        PluginLlmResponse(
                            requestId = request.requestId,
                            providerId = request.selectedProviderId,
                            modelId = request.selectedModelId,
                            text = "assistant",
                            toolCalls = emptyList(),
                        ),
                    )
                }
            },
            snapshot = snapshotOf(fixture),
        )

        assertEquals(listOf("using", "execute", "respond"), order.toList())
        assertEquals("assistant", result.sendableResult.text)
    }

    @Test
    fun tool_args_sanitizer_freezes_next_visible_args_and_last_write_wins_across_pre_call_hooks() = runBlocking {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 1L })
        val providerCalls = AtomicInteger(0)

        val fixture = llmFixture(
            pluginId = "com.example.tool.loop.args.sanitize",
            logBus = logBus,
        ) { hostApi ->
            hostApi.registerTool(
                descriptor = PluginToolDescriptor(
                    pluginId = "com.example.tool.loop.args.sanitize",
                    name = "alpha",
                    description = "alpha tool",
                    visibility = PluginToolVisibility.LLM_VISIBLE,
                    sourceKind = PluginToolSourceKind.PLUGIN_V2,
                    inputSchema = linkedMapOf("type" to "object"),
                ),
                handler = PluginV2CallbackHandle {},
            )
            hostApi.registerLlmHook(
                LlmHookRegistrationInput(
                    registrationKey = "tool.args.first",
                    hook = "on_using_llm_tool",
                    priority = 100,
                    handler = EventAwareHandle { payload ->
                        val toolPayload = payload as PluginV2UsingLlmToolPayload
                        toolPayload.replaceArgsPayload(linkedMapOf("q" to "a"))
                    },
                ),
            )
            hostApi.registerLlmHook(
                LlmHookRegistrationInput(
                    registrationKey = "tool.args.second",
                    hook = "on_using_llm_tool",
                    priority = 90,
                    handler = EventAwareHandle { payload ->
                        val toolPayload = payload as PluginV2UsingLlmToolPayload
                        assertEquals("a", toolPayload.args.payload["q"])

                        val frozenAttempt = runCatching {
                            @Suppress("UNCHECKED_CAST")
                            (toolPayload.args.payload as MutableMap<String, AllowedValue>)["q"] = "illegal"
                        }.exceptionOrNull()
                        assertTrue(
                            frozenAttempt is UnsupportedOperationException ||
                                frozenAttempt is ClassCastException,
                        )

                        val next = LinkedHashMap<String, AllowedValue>(toolPayload.args.payload)
                        next["q"] = "b"
                        toolPayload.replaceArgsPayload(next)
                    },
                ),
            )
        }

        val coordinator = PluginV2LlmPipelineCoordinator(
            dispatchEngine = PluginV2DispatchEngine(logBus = logBus, clock = { 1L }),
            toolExecutor = PluginV2ToolExecutor { args ->
                assertEquals("b", args.payload["q"])
                PluginToolResult(
                    toolCallId = args.toolCallId,
                    requestId = args.requestId,
                    toolId = args.toolId,
                    status = PluginToolResultStatus.SUCCESS,
                    text = "ok",
                )
            },
            logBus = logBus,
            clock = { 1L },
            requestIdFactory = { "req-tool-loop-003" },
        )

        val event = sampleMessageEvent(rawText = "hello tool args")

        val result = coordinator.runPreSendStages(
            input = pipelineInput(
                event = event,
                streamingMode = PluginV2StreamingMode.NON_STREAM,
            ) { request, _ ->
                when (providerCalls.incrementAndGet()) {
                    1 -> PluginV2ProviderInvocationResult.NonStreaming(
                        PluginLlmResponse(
                            requestId = request.requestId,
                            providerId = request.selectedProviderId,
                            modelId = request.selectedModelId,
                            toolCalls = listOf(
                                PluginLlmToolCall(
                                    toolName = "alpha",
                                    arguments = linkedMapOf("q" to "seed"),
                                ),
                            ),
                        ),
                    )

                    else -> PluginV2ProviderInvocationResult.NonStreaming(
                        PluginLlmResponse(
                            requestId = request.requestId,
                            providerId = request.selectedProviderId,
                            modelId = request.selectedModelId,
                            text = "assistant",
                            toolCalls = emptyList(),
                        ),
                    )
                }
            },
            snapshot = snapshotOf(fixture),
        )

        assertEquals("assistant", result.sendableResult.text)
    }

    @Test
    fun on_using_llm_tool_cannot_create_or_replace_host_metadata_with_non_map_value() = runBlocking {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 1L })
        val providerCalls = AtomicInteger(0)
        val executorCalls = AtomicInteger(0)

        val fixture = llmFixture(
            pluginId = "com.example.tool.loop.host.metadata.guard",
            logBus = logBus,
        ) { hostApi ->
            hostApi.registerTool(
                descriptor = PluginToolDescriptor(
                    pluginId = "com.example.tool.loop.host.metadata.guard",
                    name = "alpha",
                    description = "alpha tool",
                    visibility = PluginToolVisibility.LLM_VISIBLE,
                    sourceKind = PluginToolSourceKind.PLUGIN_V2,
                    inputSchema = linkedMapOf("type" to "object"),
                ),
                handler = PluginV2CallbackHandle {},
            )
            hostApi.registerLlmHook(
                LlmHookRegistrationInput(
                    registrationKey = "tool.args.host.tamper",
                    hook = "on_using_llm_tool",
                    priority = 100,
                    handler = EventAwareHandle { payload ->
                        val toolPayload = payload as PluginV2UsingLlmToolPayload
                        toolPayload.replaceArgs(
                            PluginToolArgs(
                                toolCallId = toolPayload.args.toolCallId,
                                requestId = toolPayload.args.requestId,
                                toolId = toolPayload.args.toolId,
                                attemptIndex = 0,
                                payload = toolPayload.args.payload,
                                metadata = linkedMapOf("__host" to "tamper"),
                            ),
                        )
                    },
                ),
            )
        }
        val coordinator = PluginV2LlmPipelineCoordinator(
            dispatchEngine = PluginV2DispatchEngine(logBus = logBus, clock = { 1L }),
            toolExecutor = PluginV2ToolExecutor { args ->
                executorCalls.incrementAndGet()
                PluginToolResult(
                    toolCallId = args.toolCallId,
                    requestId = args.requestId,
                    toolId = args.toolId,
                    status = PluginToolResultStatus.SUCCESS,
                    text = "ok",
                )
            },
            logBus = logBus,
            clock = { 1L },
            requestIdFactory = { "req-tool-loop-host-metadata-guard" },
        )

        val failure = runCatching {
            coordinator.runPreSendStages(
                input = pipelineInput(
                    event = sampleMessageEvent(rawText = "hello host metadata guard"),
                    streamingMode = PluginV2StreamingMode.NON_STREAM,
                ) { request, _ ->
                    when (providerCalls.incrementAndGet()) {
                        1 -> PluginV2ProviderInvocationResult.NonStreaming(
                            PluginLlmResponse(
                                requestId = request.requestId,
                                providerId = request.selectedProviderId,
                                modelId = request.selectedModelId,
                                toolCalls = listOf(
                                    PluginLlmToolCall(
                                        toolName = "alpha",
                                        arguments = linkedMapOf("q" to "seed"),
                                    ),
                                ),
                            ),
                        )

                        else -> PluginV2ProviderInvocationResult.NonStreaming(
                            PluginLlmResponse(
                                requestId = request.requestId,
                                providerId = request.selectedProviderId,
                                modelId = request.selectedModelId,
                                text = "assistant",
                                toolCalls = emptyList(),
                            ),
                        )
                    }
                },
                snapshot = snapshotOf(fixture),
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message?.contains("metadata.__host") == true)
        assertEquals(0, executorCalls.get())
    }

    @Test
    fun tool_results_are_reinjected_as_tool_role_messages_with_text_and_canonical_json_parts() = runBlocking {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 1L })
        val providerCalls = AtomicInteger(0)
        val seenRequests = CopyOnWriteArrayList<PluginProviderRequest>()

        val coordinator = PluginV2LlmPipelineCoordinator(
            dispatchEngine = PluginV2DispatchEngine(logBus = logBus, clock = { 1L }),
            toolExecutor = PluginV2ToolExecutor { args ->
                PluginToolResult(
                    toolCallId = args.toolCallId,
                    requestId = args.requestId,
                    toolId = args.toolId,
                    status = PluginToolResultStatus.SUCCESS,
                    text = "tool-text",
                    structuredContent = linkedMapOf("ok" to true),
                )
            },
            logBus = logBus,
            clock = { 1L },
            requestIdFactory = { "req-tool-loop-004" },
        )
        val event = sampleMessageEvent(rawText = "hello reinject")

        coordinator.runPreSendStages(
            input = pipelineInput(
                event = event,
                streamingMode = PluginV2StreamingMode.NON_STREAM,
            ) { request, _ ->
                seenRequests += request
                when (providerCalls.incrementAndGet()) {
                    1 -> PluginV2ProviderInvocationResult.NonStreaming(
                        PluginLlmResponse(
                            requestId = request.requestId,
                            providerId = request.selectedProviderId,
                            modelId = request.selectedModelId,
                            toolCalls = listOf(
                                PluginLlmToolCall(
                                    toolName = "alpha",
                                    arguments = linkedMapOf("q" to "a"),
                                ),
                            ),
                        ),
                    )

                    else -> PluginV2ProviderInvocationResult.NonStreaming(
                        PluginLlmResponse(
                            requestId = request.requestId,
                            providerId = request.selectedProviderId,
                            modelId = request.selectedModelId,
                            text = "assistant",
                            toolCalls = emptyList(),
                        ),
                    )
                }
            },
            snapshot = snapshotOf(
                llmFixture(
                    pluginId = "com.example.tool.loop.reinject",
                    logBus = logBus,
                ) { hostApi ->
                    hostApi.registerTool(
                        descriptor = PluginToolDescriptor(
                            pluginId = "com.example.tool.loop.reinject",
                            name = "alpha",
                            description = "alpha tool",
                            visibility = PluginToolVisibility.LLM_VISIBLE,
                            sourceKind = PluginToolSourceKind.PLUGIN_V2,
                            inputSchema = linkedMapOf("type" to "object"),
                        ),
                        handler = PluginV2CallbackHandle {},
                    )
                },
            ),
        )

        assertTrue(seenRequests.size >= 2)
        val second = seenRequests[1]
        val assistantMessage = second.messages[second.messages.size - 2]
        assertEquals(PluginProviderMessageRole.ASSISTANT, assistantMessage.role)
        assertEquals(1, assistantMessage.toolCalls.size)
        assertEquals("alpha", assistantMessage.toolCalls.single().normalizedToolName)
        val lastMessage = second.messages.last()
        assertEquals(PluginProviderMessageRole.TOOL, lastMessage.role)
        assertEquals("alpha", lastMessage.name)
        val hostMetadata = lastMessage.metadata?.get("__host") as? Map<*, *>
        assertNotNull(hostMetadata)
        val toolCallId = hostMetadata?.get("toolCallId") as? String
        assertTrue(!toolCallId.isNullOrBlank())
        assertEquals(toolCallId, assistantMessage.toolCalls.single().normalizedId)
        val parts = lastMessage.parts.filterIsInstance<PluginProviderMessagePartDto.TextPart>()
        assertEquals(2, parts.size)
        assertEquals("tool-text", parts[0].text)
        assertEquals("{\"ok\":true}", parts[1].text)
    }

    @Test
    fun multiple_tool_results_are_reinjected_after_single_assistant_tool_call_round_with_original_ids() = runBlocking {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 1L })
        val providerCalls = AtomicInteger(0)
        val seenRequests = CopyOnWriteArrayList<PluginProviderRequest>()

        val coordinator = PluginV2LlmPipelineCoordinator(
            dispatchEngine = PluginV2DispatchEngine(logBus = logBus, clock = { 1L }),
            toolExecutor = PluginV2ToolExecutor { args ->
                PluginToolResult(
                    toolCallId = args.toolCallId,
                    requestId = args.requestId,
                    toolId = args.toolId,
                    status = PluginToolResultStatus.SUCCESS,
                    text = "result-${args.toolId.substringAfterLast(':')}",
                )
            },
            logBus = logBus,
            clock = { 1L },
            requestIdFactory = { "req-tool-loop-original-id" },
        )

        coordinator.runPreSendStages(
            input = pipelineInput(
                event = sampleMessageEvent(rawText = "hello reinject order"),
                streamingMode = PluginV2StreamingMode.NON_STREAM,
            ) { request, _ ->
                seenRequests += request
                when (providerCalls.incrementAndGet()) {
                    1 -> PluginV2ProviderInvocationResult.NonStreaming(
                        PluginLlmResponse(
                            requestId = request.requestId,
                            providerId = request.selectedProviderId,
                            modelId = request.selectedModelId,
                            text = "Need two tools",
                            toolCalls = listOf(
                                PluginLlmToolCall(
                                    toolCallId = "call_alpha",
                                    toolName = "alpha",
                                    arguments = linkedMapOf("q" to "a"),
                                ),
                                PluginLlmToolCall(
                                    toolCallId = "call_beta",
                                    toolName = "beta",
                                    arguments = linkedMapOf("q" to "b"),
                                ),
                            ),
                        ),
                    )

                    else -> PluginV2ProviderInvocationResult.NonStreaming(
                        PluginLlmResponse(
                            requestId = request.requestId,
                            providerId = request.selectedProviderId,
                            modelId = request.selectedModelId,
                            text = "assistant",
                        ),
                    )
                }
            },
            snapshot = snapshotOf(
                llmFixture(
                    pluginId = "com.example.tool.loop.reinject.multi",
                    logBus = logBus,
                ) { hostApi ->
                    listOf("alpha", "beta").forEach { toolName ->
                        hostApi.registerTool(
                            descriptor = PluginToolDescriptor(
                                pluginId = "com.example.tool.loop.reinject.multi",
                                name = toolName,
                                description = "$toolName tool",
                                visibility = PluginToolVisibility.LLM_VISIBLE,
                                sourceKind = PluginToolSourceKind.PLUGIN_V2,
                                inputSchema = linkedMapOf("type" to "object"),
                            ),
                            handler = PluginV2CallbackHandle {},
                        )
                    }
                },
            ),
        )

        val second = seenRequests[1]
        assertEquals(4, second.messages.size)
        val assistant = second.messages[1]
        assertEquals(PluginProviderMessageRole.ASSISTANT, assistant.role)
        assertEquals("Need two tools", assistant.parts.filterIsInstance<PluginProviderMessagePartDto.TextPart>().single().text)
        assertEquals(listOf("call_alpha", "call_beta"), assistant.toolCalls.map { it.normalizedId })
        assertEquals(listOf(PluginProviderMessageRole.TOOL, PluginProviderMessageRole.TOOL), second.messages.takeLast(2).map { it.role })
        assertEquals(listOf("call_alpha", "call_beta"), second.messages.takeLast(2).mapNotNull { message ->
            ((message.metadata?.get("__host") as? Map<*, *>)?.get("toolCallId") as? String)
        })
    }

    @Test
    fun streaming_tool_call_delta_preserves_original_tool_call_id() = runBlocking {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 1L })
        val seenToolCallIds = CopyOnWriteArrayList<String>()
        val providerCalls = AtomicInteger(0)

        val coordinator = PluginV2LlmPipelineCoordinator(
            dispatchEngine = PluginV2DispatchEngine(logBus = logBus, clock = { 1L }),
            toolExecutor = PluginV2ToolExecutor { args ->
                seenToolCallIds += args.toolCallId
                PluginToolResult(
                    toolCallId = args.toolCallId,
                    requestId = args.requestId,
                    toolId = args.toolId,
                    status = PluginToolResultStatus.SUCCESS,
                    text = "ok",
                )
            },
            logBus = logBus,
            clock = { 1L },
            requestIdFactory = { "req-tool-loop-stream-id" },
        )

        coordinator.runPreSendStages(
            input = pipelineInput(
                event = sampleMessageEvent(rawText = "hello stream id"),
                streamingMode = PluginV2StreamingMode.NATIVE_STREAM,
            ) { request, _ ->
                when (providerCalls.incrementAndGet()) {
                    1 -> PluginV2ProviderInvocationResult.Streaming(
                        events = listOf(
                            PluginV2ProviderStreamChunk(
                                toolCallDeltas = listOf(
                                    PluginLlmToolCallDelta(
                                        index = 0,
                                        toolCallId = "call_stream_alpha",
                                        toolName = "alpha",
                                        arguments = linkedMapOf("q" to "a"),
                                    ),
                                ),
                            ),
                            PluginV2ProviderStreamChunk(isCompletion = true, finishReason = "tool_calls"),
                        ),
                    )

                    else -> PluginV2ProviderInvocationResult.NonStreaming(
                        PluginLlmResponse(
                            requestId = request.requestId,
                            providerId = request.selectedProviderId,
                            modelId = request.selectedModelId,
                            text = "assistant",
                        ),
                    )
                }
            },
            snapshot = snapshotOf(
                llmFixture(
                    pluginId = "com.example.tool.loop.streaming.id",
                    logBus = logBus,
                ) { hostApi ->
                    hostApi.registerTool(
                        descriptor = PluginToolDescriptor(
                            pluginId = "com.example.tool.loop.streaming.id",
                            name = "alpha",
                            description = "alpha tool",
                            visibility = PluginToolVisibility.LLM_VISIBLE,
                            sourceKind = PluginToolSourceKind.PLUGIN_V2,
                            inputSchema = linkedMapOf("type" to "object"),
                        ),
                        handler = PluginV2CallbackHandle {},
                    )
                },
            ),
        )

        assertEquals(listOf("call_stream_alpha"), seenToolCallIds.toList())
    }

    @Test
    fun reinjection_adds_empty_text_part_when_both_text_and_structured_content_are_empty() = runBlocking {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 1L })
        val providerCalls = AtomicInteger(0)
        val seenRequests = CopyOnWriteArrayList<PluginProviderRequest>()

        val coordinator = PluginV2LlmPipelineCoordinator(
            dispatchEngine = PluginV2DispatchEngine(logBus = logBus, clock = { 1L }),
            toolExecutor = PluginV2ToolExecutor { args ->
                PluginToolResult(
                    toolCallId = args.toolCallId,
                    requestId = args.requestId,
                    toolId = args.toolId,
                    status = PluginToolResultStatus.SUCCESS,
                    text = null,
                    structuredContent = null,
                )
            },
            logBus = logBus,
            clock = { 1L },
            requestIdFactory = { "req-tool-loop-005" },
        )
        val event = sampleMessageEvent(rawText = "hello empty reinject")

        coordinator.runPreSendStages(
            input = pipelineInput(
                event = event,
                streamingMode = PluginV2StreamingMode.NON_STREAM,
            ) { request, _ ->
                seenRequests += request
                when (providerCalls.incrementAndGet()) {
                    1 -> PluginV2ProviderInvocationResult.NonStreaming(
                        PluginLlmResponse(
                            requestId = request.requestId,
                            providerId = request.selectedProviderId,
                            modelId = request.selectedModelId,
                            toolCalls = listOf(
                                PluginLlmToolCall(
                                    toolName = "alpha",
                                    arguments = emptyMap(),
                                ),
                            ),
                        ),
                    )

                    else -> PluginV2ProviderInvocationResult.NonStreaming(
                        PluginLlmResponse(
                            requestId = request.requestId,
                            providerId = request.selectedProviderId,
                            modelId = request.selectedModelId,
                            text = "assistant",
                            toolCalls = emptyList(),
                        ),
                    )
                }
            },
            snapshot = snapshotOf(
                llmFixture(
                    pluginId = "com.example.tool.loop.reinject.empty",
                    logBus = logBus,
                ) { hostApi ->
                    hostApi.registerTool(
                        descriptor = PluginToolDescriptor(
                            pluginId = "com.example.tool.loop.reinject.empty",
                            name = "alpha",
                            description = "alpha tool",
                            visibility = PluginToolVisibility.LLM_VISIBLE,
                            sourceKind = PluginToolSourceKind.PLUGIN_V2,
                            inputSchema = linkedMapOf("type" to "object"),
                        ),
                        handler = PluginV2CallbackHandle {},
                    )
                },
            ),
        )

        assertTrue(seenRequests.size >= 2)
        val second = seenRequests[1]
        val lastMessage = second.messages.last()
        assertEquals(PluginProviderMessageRole.TOOL, lastMessage.role)
        val parts = lastMessage.parts.filterIsInstance<PluginProviderMessagePartDto.TextPart>()
        assertEquals(1, parts.size)
        assertEquals("", parts[0].text)
    }

    @Test
    fun reinjection_uses_only_canonical_json_text_part_when_text_is_empty_but_structured_content_exists() = runBlocking {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 1L })
        val providerCalls = AtomicInteger(0)
        val seenRequests = CopyOnWriteArrayList<PluginProviderRequest>()

        val coordinator = PluginV2LlmPipelineCoordinator(
            dispatchEngine = PluginV2DispatchEngine(logBus = logBus, clock = { 1L }),
            toolExecutor = PluginV2ToolExecutor { args ->
                PluginToolResult(
                    toolCallId = args.toolCallId,
                    requestId = args.requestId,
                    toolId = args.toolId,
                    status = PluginToolResultStatus.SUCCESS,
                    text = "",
                    structuredContent = linkedMapOf("ok" to true),
                )
            },
            logBus = logBus,
            clock = { 1L },
            requestIdFactory = { "req-tool-loop-005b" },
        )
        val event = sampleMessageEvent(rawText = "hello structured reinject")

        coordinator.runPreSendStages(
            input = pipelineInput(
                event = event,
                streamingMode = PluginV2StreamingMode.NON_STREAM,
            ) { request, _ ->
                seenRequests += request
                when (providerCalls.incrementAndGet()) {
                    1 -> PluginV2ProviderInvocationResult.NonStreaming(
                        PluginLlmResponse(
                            requestId = request.requestId,
                            providerId = request.selectedProviderId,
                            modelId = request.selectedModelId,
                            toolCalls = listOf(
                                PluginLlmToolCall(
                                    toolName = "alpha",
                                    arguments = emptyMap(),
                                ),
                            ),
                        ),
                    )

                    else -> PluginV2ProviderInvocationResult.NonStreaming(
                        PluginLlmResponse(
                            requestId = request.requestId,
                            providerId = request.selectedProviderId,
                            modelId = request.selectedModelId,
                            text = "assistant",
                            toolCalls = emptyList(),
                        ),
                    )
                }
            },
            snapshot = snapshotOf(
                llmFixture(
                    pluginId = "com.example.tool.loop.reinject.structured",
                    logBus = logBus,
                ) { hostApi ->
                    hostApi.registerTool(
                        descriptor = PluginToolDescriptor(
                            pluginId = "com.example.tool.loop.reinject.structured",
                            name = "alpha",
                            description = "alpha tool",
                            visibility = PluginToolVisibility.LLM_VISIBLE,
                            sourceKind = PluginToolSourceKind.PLUGIN_V2,
                            inputSchema = linkedMapOf("type" to "object"),
                        ),
                        handler = PluginV2CallbackHandle {},
                    )
                },
            ),
        )

        assertTrue(seenRequests.size >= 2)
        val second = seenRequests[1]
        val lastMessage = second.messages.last()
        assertEquals(PluginProviderMessageRole.TOOL, lastMessage.role)
        val parts = lastMessage.parts.filterIsInstance<PluginProviderMessagePartDto.TextPart>()
        assertEquals(1, parts.size)
        assertEquals("{\"ok\":true}", parts[0].text)
    }

    @Test
    fun plugin_request_paths_cannot_author_tool_messages_but_host_reinjection_survives_following_request_hooks() = runBlocking {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 1L })
        val providerCalls = AtomicInteger(0)
        val seenRequests = CopyOnWriteArrayList<PluginProviderRequest>()

        val toolAuthoredByPlugin = PluginProviderMessageDto(
            role = PluginProviderMessageRole.TOOL,
            name = "fake-tool",
            parts = listOf(PluginProviderMessagePartDto.TextPart("plugin-authored")),
            metadata = mapOf("__host" to mapOf("toolCallId" to "fake-call")),
        )
        val hookFixture = llmFixture(
            pluginId = "com.example.tool.loop.request.guard",
            logBus = logBus,
        ) { hostApi ->
            hostApi.registerTool(
                descriptor = PluginToolDescriptor(
                    pluginId = "com.example.tool.loop.request.guard",
                    name = "alpha",
                    description = "alpha tool",
                    visibility = PluginToolVisibility.LLM_VISIBLE,
                    sourceKind = PluginToolSourceKind.PLUGIN_V2,
                    inputSchema = linkedMapOf("type" to "object"),
                ),
                handler = PluginV2CallbackHandle {},
            )
            hostApi.registerLlmHook(
                LlmHookRegistrationInput(
                    registrationKey = "request.inject.tool",
                    hook = "on_llm_request",
                    priority = 100,
                    handler = EventAwareHandle { payload ->
                        val requestPayload = payload as PluginV2LlmRequestPayload
                        requestPayload.request.messages = requestPayload.request.messages + toolAuthoredByPlugin
                    },
                ),
            )
        }

        PluginV2LlmPipelineCoordinator(
            dispatchEngine = PluginV2DispatchEngine(logBus = logBus, clock = { 1L }),
            toolExecutor = PluginV2ToolExecutor { args ->
                PluginToolResult(
                    toolCallId = args.toolCallId,
                    requestId = args.requestId,
                    toolId = args.toolId,
                    status = PluginToolResultStatus.SUCCESS,
                    text = "host-tool",
                )
            },
            logBus = logBus,
            clock = { 1L },
            requestIdFactory = { "req-tool-loop-guard-hook" },
        ).runPreSendStages(
            input = pipelineInput(
                event = sampleMessageEvent(rawText = "hello request guard"),
                streamingMode = PluginV2StreamingMode.NON_STREAM,
            ) { request, _ ->
                seenRequests += request
                when (providerCalls.incrementAndGet()) {
                    1 -> PluginV2ProviderInvocationResult.NonStreaming(
                        PluginLlmResponse(
                            requestId = request.requestId,
                            providerId = request.selectedProviderId,
                            modelId = request.selectedModelId,
                            toolCalls = listOf(
                                PluginLlmToolCall(
                                    toolName = "alpha",
                                    arguments = linkedMapOf("q" to "a"),
                                ),
                            ),
                        ),
                    )

                    else -> {
                        val toolMessages = request.messages.filter { it.role == PluginProviderMessageRole.TOOL }
                        assertEquals(1, toolMessages.size)
                        val toolMessage = toolMessages.single()
                        assertEquals("alpha", toolMessage.name)
                        val textParts = toolMessage.parts.filterIsInstance<PluginProviderMessagePartDto.TextPart>()
                        assertEquals(1, textParts.size)
                        assertEquals("host-tool", textParts.single().text)
                        assertEquals(
                            0,
                            toolMessages.count { message ->
                                message.name == "fake-tool" ||
                                    message.parts.filterIsInstance<PluginProviderMessagePartDto.TextPart>()
                                        .any { it.text == "plugin-authored" }
                            },
                        )
                        PluginV2ProviderInvocationResult.NonStreaming(
                            PluginLlmResponse(
                                requestId = request.requestId,
                                providerId = request.selectedProviderId,
                                modelId = request.selectedModelId,
                                text = "assistant",
                                toolCalls = emptyList(),
                            ),
                        )
                    }
                }
            },
            snapshot = snapshotOf(hookFixture),
        )
        assertEquals(2, providerCalls.get())
        assertTrue(seenRequests.size >= 2)
        assertEquals(1, seenRequests[1].messages.count { it.role == PluginProviderMessageRole.TOOL })

        val decodeFailure = runCatching {
            PluginExecutionProtocolJson.decodePluginProviderRequest(
                JSONObject().apply {
                    put("requestId", "req-tool-loop-guard-decode")
                    put("availableProviderIds", JSONArray().put("provider-a"))
                    put(
                        "availableModelIdsByProvider",
                        JSONObject().put("provider-a", JSONArray().put("model-a-1")),
                    )
                    put("conversationId", "conv-guard")
                    put("messageIds", JSONArray().put("msg-guard"))
                    put("llmInputSnapshot", "snapshot")
                    put("selectedProviderId", "provider-a")
                    put("selectedModelId", "model-a-1")
                    put(
                        "messages",
                        JSONArray().put(
                            JSONObject().apply {
                                put("role", PluginProviderMessageRole.TOOL.wireValue)
                                put("name", "alpha")
                                put(
                                    "metadata",
                                    JSONObject().apply {
                                        put(
                                            "__host",
                                            JSONObject().apply {
                                                put("toolCallId", "decode-tool-call")
                                            },
                                        )
                                    },
                                )
                                put(
                                    "parts",
                                    JSONArray().put(
                                        JSONObject().apply {
                                            put("partType", "text")
                                            put("text", "plugin-authored")
                                        },
                                    ),
                                )
                            },
                        ),
                    )
                    put("streamingEnabled", false)
                },
            )
        }.exceptionOrNull()
        assertTrue(decodeFailure is IllegalArgumentException)
        assertTrue(decodeFailure?.message?.contains("role=TOOL") == true)

        val sanitizedRequest = PluginProviderRequest(
            requestId = "req-tool-loop-guard-construct",
            availableProviderIds = listOf("provider-a"),
            availableModelIdsByProvider = linkedMapOf("provider-a" to listOf("model-a-1")),
            conversationId = "conv-guard",
            messageIds = listOf("msg-guard"),
            llmInputSnapshot = "snapshot",
            selectedProviderId = "provider-a",
            selectedModelId = "model-a-1",
            messages = listOf(toolAuthoredByPlugin),
        )
        assertTrue(sanitizedRequest.messages.none { it.role == PluginProviderMessageRole.TOOL })
    }

    @Test
    fun repeated_tool_across_rounds_generates_new_tool_call_id_and_attempt_index_is_zero() = runBlocking {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 1L })
        val providerCalls = AtomicInteger(0)
        val toolCallIds = CopyOnWriteArrayList<String>()

        val coordinator = PluginV2LlmPipelineCoordinator(
            dispatchEngine = PluginV2DispatchEngine(logBus = logBus, clock = { 1L }),
            toolExecutor = PluginV2ToolExecutor { args ->
                assertEquals(0, args.attemptIndex)
                toolCallIds += args.toolCallId
                PluginToolResult(
                    toolCallId = args.toolCallId,
                    requestId = args.requestId,
                    toolId = args.toolId,
                    status = PluginToolResultStatus.SUCCESS,
                    text = "ok",
                )
            },
            logBus = logBus,
            clock = { 1L },
            requestIdFactory = { "req-tool-loop-006" },
        )
        val event = sampleMessageEvent(rawText = "hello repeated tool")

        val result = coordinator.runPreSendStages(
            input = pipelineInput(
                event = event,
                streamingMode = PluginV2StreamingMode.NON_STREAM,
            ) { request, _ ->
                when (providerCalls.incrementAndGet()) {
                    1, 2 -> PluginV2ProviderInvocationResult.NonStreaming(
                        PluginLlmResponse(
                            requestId = request.requestId,
                            providerId = request.selectedProviderId,
                            modelId = request.selectedModelId,
                            toolCalls = listOf(
                                PluginLlmToolCall(
                                    toolName = "alpha",
                                    arguments = linkedMapOf("q" to providerCalls.get()),
                                ),
                            ),
                        ),
                    )

                    else -> PluginV2ProviderInvocationResult.NonStreaming(
                        PluginLlmResponse(
                            requestId = request.requestId,
                            providerId = request.selectedProviderId,
                            modelId = request.selectedModelId,
                            text = "assistant",
                            toolCalls = emptyList(),
                        ),
                    )
                }
            },
            snapshot = snapshotOf(
                llmFixture(
                    pluginId = "com.example.tool.loop.tool_call_id",
                    logBus = logBus,
                ) { hostApi ->
                    hostApi.registerTool(
                        descriptor = PluginToolDescriptor(
                            pluginId = "com.example.tool.loop.tool_call_id",
                            name = "alpha",
                            description = "alpha tool",
                            visibility = PluginToolVisibility.LLM_VISIBLE,
                            sourceKind = PluginToolSourceKind.PLUGIN_V2,
                            inputSchema = linkedMapOf("type" to "object"),
                        ),
                        handler = PluginV2CallbackHandle {},
                    )
                },
            ),
        )

        assertEquals("assistant", result.sendableResult.text)
        assertEquals(2, toolCallIds.size)
        assertNotEquals(toolCallIds[0], toolCallIds[1])
    }

    @Test
    fun streaming_tool_call_freezes_only_after_completion_and_response_hooks_can_still_drop_tool_calls() = runBlocking {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 1L })
        val providerCalls = AtomicInteger(0)
        val executorCalls = AtomicInteger(0)

        val fixture = llmFixture(
            pluginId = "com.example.tool.loop.streaming.freeze",
            logBus = logBus,
        ) { hostApi ->
            hostApi.registerTool(
                descriptor = PluginToolDescriptor(
                    pluginId = "com.example.tool.loop.streaming.freeze",
                    name = "alpha",
                    description = "alpha tool",
                    visibility = PluginToolVisibility.LLM_VISIBLE,
                    sourceKind = PluginToolSourceKind.PLUGIN_V2,
                    inputSchema = linkedMapOf("type" to "object"),
                ),
                handler = PluginV2CallbackHandle {},
            )
            hostApi.registerLlmHook(
                LlmHookRegistrationInput(
                    registrationKey = "response.drop.tools",
                    hook = "on_llm_response",
                    priority = 100,
                    handler = EventAwareHandle { payload ->
                        val responsePayload = payload as PluginV2LlmResponsePayload
                        responsePayload.response.toolCalls = emptyList()
                    },
                ),
            )
        }

        val coordinator = PluginV2LlmPipelineCoordinator(
            dispatchEngine = PluginV2DispatchEngine(logBus = logBus, clock = { 1L }),
            toolExecutor = PluginV2ToolExecutor { args ->
                executorCalls.incrementAndGet()
                PluginToolResult(
                    toolCallId = args.toolCallId,
                    requestId = args.requestId,
                    toolId = args.toolId,
                    status = PluginToolResultStatus.SUCCESS,
                    text = "ok",
                )
            },
            logBus = logBus,
            clock = { 1L },
            requestIdFactory = { "req-tool-loop-007" },
        )
        val event = sampleMessageEvent(rawText = "hello streaming tool")

        val result = coordinator.runPreSendStages(
            input = pipelineInput(
                event = event,
                streamingMode = PluginV2StreamingMode.NATIVE_STREAM,
            ) { request, mode ->
                assertEquals(PluginV2StreamingMode.NATIVE_STREAM, mode)
                when (providerCalls.incrementAndGet()) {
                    1 -> PluginV2ProviderInvocationResult.Streaming(
                        events = listOf(
                            PluginV2ProviderStreamChunk(
                                deltaText = "assistant",
                                toolCallDeltas = listOf(
                                    PluginLlmToolCallDelta(
                                        index = 0,
                                        toolName = "alpha",
                                        arguments = linkedMapOf("q" to "a"),
                                    ),
                                ),
                            ),
                            PluginV2ProviderStreamChunk(isCompletion = true, finishReason = "tool_calls"),
                        ),
                    )

                    else -> PluginV2ProviderInvocationResult.NonStreaming(
                        PluginLlmResponse(
                            requestId = request.requestId,
                            providerId = request.selectedProviderId,
                            modelId = request.selectedModelId,
                            text = "assistant",
                            toolCalls = emptyList(),
                        ),
                    )
                }
            },
            snapshot = snapshotOf(fixture),
        )

        assertEquals("assistant", result.sendableResult.text)
        assertEquals(0, executorCalls.get())
    }

    private fun pipelineInput(
        event: PluginMessageEvent,
        streamingMode: PluginV2StreamingMode,
        systemPrompt: String? = null,
        invokeProvider: suspend (PluginProviderRequest, PluginV2StreamingMode) -> PluginV2ProviderInvocationResult,
    ): PluginV2LlmPipelineInput {
        return PluginV2LlmPipelineInput(
            event = event,
            messageIds = listOf("msg-001"),
            streamingMode = streamingMode,
            availableProviderIds = listOf("provider-a", "provider-b"),
            availableModelIdsByProvider = linkedMapOf(
                "provider-a" to listOf("model-a-1"),
                "provider-b" to listOf("model-b-1", "model-b-2"),
            ),
            selectedProviderId = "provider-a",
            selectedModelId = "model-a-1",
            systemPrompt = systemPrompt,
            messages = listOf(
                PluginProviderMessageDto(
                    role = PluginProviderMessageRole.USER,
                    parts = listOf(
                        PluginProviderMessagePartDto.TextPart(event.rawText),
                    ),
                ),
            ),
            invokeProvider = invokeProvider,
        )
    }

    private fun llmFixture(
        pluginId: String,
        logBus: PluginRuntimeLogBus,
        register: (PluginV2BootstrapHostApi) -> Unit,
    ): RuntimeFixture {
        val session = PluginV2RuntimeSession(
            installRecord = samplePluginV2InstallRecord(pluginId = pluginId),
            sessionInstanceId = "session-$pluginId",
        )
        session.transitionTo(PluginV2RuntimeSessionState.Loading)
        session.transitionTo(PluginV2RuntimeSessionState.BootstrapRunning)
        session.requireBootstrapRawRegistry()

        val hostApi = PluginV2BootstrapHostApi(session, logBus = logBus, clock = { 1L })
        register(hostApi)

        val compileResult = PluginV2RegistryCompiler(logBus = logBus, clock = { 1L })
            .compile(requireNotNull(session.rawRegistry))
        val compiledRegistry = requireNotNull(compileResult.compiledRegistry)
        session.attachCompiledRegistry(compiledRegistry)
        session.transitionTo(PluginV2RuntimeSessionState.Active)

        return RuntimeFixture(
            session = session,
            compiledRegistry = compiledRegistry,
            entry = PluginV2ActiveRuntimeEntry(
                session = session,
                compiledRegistry = compiledRegistry,
                lastBootstrapSummary = PluginV2BootstrapSummary(
                    pluginId = pluginId,
                    sessionInstanceId = session.sessionInstanceId,
                    compiledAtEpochMillis = 0L,
                    handlerCount = compiledRegistry.handlerRegistry.totalHandlerCount,
                    warningCount = compileResult.diagnostics.count { it.severity == DiagnosticSeverity.Warning },
                    errorCount = compileResult.diagnostics.count { it.severity == DiagnosticSeverity.Error },
                ),
                diagnostics = compileResult.diagnostics,
                callbackTokens = session.snapshotCallbackTokens(),
            ),
        )
    }

    private fun snapshotOf(vararg fixtures: RuntimeFixture): PluginV2ActiveRuntimeSnapshot {
        val sessionsByPluginId = fixtures.associateBy { it.session.pluginId }.mapValues { it.value.session }
        val toolState = compileCentralizedToolState(sessionsByPluginId)
        return PluginV2ActiveRuntimeSnapshot(
            activeRuntimeEntriesByPluginId = fixtures.associateBy { it.session.pluginId }.mapValues { it.value.entry },
            activeSessionsByPluginId = sessionsByPluginId,
            compiledRegistriesByPluginId = fixtures.associateBy { it.session.pluginId }.mapValues { it.value.compiledRegistry },
            toolRegistrySnapshot = toolState.activeRegistry,
            toolRegistryDiagnostics = toolState.diagnostics,
            toolAvailabilityByName = toolState.availabilityByName,
        )
    }

    private fun sampleMessageEvent(rawText: String): PluginMessageEvent {
        return PluginMessageEvent(
            eventId = "evt-${rawText.hashCode()}",
            platformAdapterType = "onebot",
            messageType = MessageType.GroupMessage,
            conversationId = "group-llm",
            senderId = "user-llm",
            timestampEpochMillis = 1_710_000_100_000L,
            rawText = rawText,
            initialWorkingText = rawText,
            rawMentions = emptyList(),
            normalizedMentions = emptyList(),
            extras = emptyMap(),
        )
    }

    private data class RuntimeFixture(
        val session: PluginV2RuntimeSession,
        val compiledRegistry: PluginV2CompiledRegistrySnapshot,
        val entry: PluginV2ActiveRuntimeEntry,
    )

    private class EventAwareHandle(
        private val onEvent: suspend (PluginErrorEventPayload) -> Unit,
    ) : PluginV2EventAwareCallbackHandle {
        override fun invoke() = Unit

        override suspend fun handleEvent(event: PluginErrorEventPayload) {
            onEvent(event)
        }
    }
}
