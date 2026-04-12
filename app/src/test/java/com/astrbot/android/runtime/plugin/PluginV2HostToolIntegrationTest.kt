package com.astrbot.android.runtime.plugin

import com.astrbot.android.model.chat.MessageType
import com.astrbot.android.model.plugin.PluginV2StreamingMode
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginV2HostToolIntegrationTest {
    @Test
    fun host_builtin_tools_are_exposed_through_centralized_registry_snapshot_not_host_session() {
        val gateway = DefaultPluginHostCapabilityGateway(
            hostToolHandlers = PluginExecutionHostToolHandlers(
                sendMessageHandler = {},
                sendNotificationHandler = { _, _ -> },
                openHostPageHandler = {},
            ),
        )

        val snapshot = gateway.registerHostBuiltinTools(PluginV2ActiveRuntimeSnapshot())
        val hostSession = snapshot.activeSessionsByPluginId[PluginExecutionHostApi.HostBuiltinPluginId]

        assertNull(hostSession)
        val descriptors = snapshot.toolRegistrySnapshot!!.activeEntries
        assertEquals(
            listOf(
                PluginExecutionHostApi.HostSendMessageToolName,
                PluginExecutionHostApi.HostSendNotificationToolName,
                PluginExecutionHostApi.HostOpenHostPageToolName,
            ),
            descriptors.map(PluginV2ToolRegistryEntry::name),
        )
        assertTrue(descriptors.all { it.sourceKind == PluginToolSourceKind.HOST_BUILTIN })
        assertTrue(
            snapshot.toolAvailabilityByName.values
                .filter { it.sourceKind == PluginToolSourceKind.HOST_BUILTIN }
                .all { it.available },
        )
    }

    @Test
    fun host_builtin_name_conflict_is_reported_by_centralized_registry() {
        val pluginSession = sessionWithTool(
            pluginId = "com.example.conflict",
            toolName = PluginExecutionHostApi.HostSendNotificationToolName,
        )
        val gateway = DefaultPluginHostCapabilityGateway(
            hostToolHandlers = PluginExecutionHostToolHandlers(
                sendNotificationHandler = { _, _ -> },
            ),
        )

        val snapshot = gateway.registerHostBuiltinTools(
            PluginV2ActiveRuntimeSnapshot(
                activeSessionsByPluginId = mapOf(pluginSession.pluginId to pluginSession),
            ),
        )

        assertNull(snapshot.activeSessionsByPluginId[PluginExecutionHostApi.HostBuiltinPluginId])
        assertNull(snapshot.toolRegistrySnapshot)
        assertTrue(snapshot.toolRegistryDiagnostics.any { it.code == "duplicate_public_tool_name" })
    }

    @Test
    fun host_builtin_tool_call_needs_registered_host_registry_entry_before_it_can_execute() = runBlocking {
        val notifications = CopyOnWriteArrayList<String>()
        val gateway = DefaultPluginHostCapabilityGateway(
            hostToolHandlers = PluginExecutionHostToolHandlers(
                sendNotificationHandler = { title, message ->
                    notifications += "$title:$message"
                },
            ),
        )
        val providerCalls = AtomicInteger(0)
        val requestsWithoutRegistry = CopyOnWriteArrayList<PluginProviderRequest>()
        val requestsWithRegistry = CopyOnWriteArrayList<PluginProviderRequest>()

        val input = pipelineInput { request, _ ->
            when (providerCalls.incrementAndGet()) {
                1, 3 -> PluginV2ProviderInvocationResult.NonStreaming(
                    response = PluginLlmResponse(
                        requestId = request.requestId,
                        providerId = request.selectedProviderId,
                        modelId = request.selectedModelId,
                        toolCalls = listOf(
                            PluginLlmToolCall(
                                toolName = PluginExecutionHostApi.HostSendNotificationToolName,
                                arguments = linkedMapOf(
                                    "title" to "AstrBot",
                                    "message" to "host integration",
                                ),
                            ),
                        ),
                    ),
                )

                else -> PluginV2ProviderInvocationResult.NonStreaming(
                    response = PluginLlmResponse(
                        requestId = request.requestId,
                        providerId = request.selectedProviderId,
                        modelId = request.selectedModelId,
                        text = "assistant-finished",
                    ),
                )
            }
        }

        val withoutRegistryCoordinator = PluginV2LlmPipelineCoordinator(
            toolExecutor = PluginV2ToolExecutor { args ->
                gateway.executeHostBuiltinTool(args) ?: PluginToolResult(
                    toolCallId = args.toolCallId,
                    requestId = args.requestId,
                    toolId = args.toolId,
                    status = PluginToolResultStatus.ERROR,
                    errorCode = "tool_executor_unavailable",
                    text = "Tool executor is not wired yet.",
                )
            },
            requestIdFactory = { "req-host-tool-without-registry" },
        )
        withoutRegistryCoordinator.runPreSendStages(
            input = input.copy(
                invokeProvider = { request, mode ->
                    requestsWithoutRegistry += request
                    input.invokeProvider(request, mode)
                },
            ),
            snapshot = PluginV2ActiveRuntimeSnapshot(),
        )

        val withoutRegistryToolMessage = requestsWithoutRegistry.last().messages.last()
        val withoutRegistryParts = withoutRegistryToolMessage.parts.filterIsInstance<PluginProviderMessagePartDto.TextPart>()
        assertEquals(1, withoutRegistryParts.size)
        assertTrue(withoutRegistryParts.single().text.contains("Tool descriptor not found"))
        assertTrue(notifications.isEmpty())

        val withRegistryCoordinator = PluginV2LlmPipelineCoordinator(
            toolExecutor = PluginV2ToolExecutor { args ->
                gateway.executeHostBuiltinTool(args) ?: PluginToolResult(
                    toolCallId = args.toolCallId,
                    requestId = args.requestId,
                    toolId = args.toolId,
                    status = PluginToolResultStatus.ERROR,
                    errorCode = "tool_executor_unavailable",
                    text = "Tool executor is not wired yet.",
                )
            },
            requestIdFactory = { "req-host-tool-with-registry" },
        )
        withRegistryCoordinator.runPreSendStages(
            input = input.copy(
                invokeProvider = { request, mode ->
                    requestsWithRegistry += request
                    input.invokeProvider(request, mode)
                },
            ),
            snapshot = gateway.registerHostBuiltinTools(PluginV2ActiveRuntimeSnapshot()),
        )

        val withRegistryToolMessage = requestsWithRegistry.last().messages.last()
        val withRegistryParts = withRegistryToolMessage.parts.filterIsInstance<PluginProviderMessagePartDto.TextPart>()
        assertEquals(1, withRegistryParts.size)
        assertEquals("AstrBot: host integration", withRegistryParts.single().text)
        assertEquals(listOf("AstrBot:host integration"), notifications.toList())
    }

    @Test
    fun tool_loop_coordinator_source_does_not_reference_external_host_action_executor() {
        val sourceFile = sequenceOf(
            File("app/src/main/java/com/astrbot/android/runtime/plugin/PluginV2ToolLoopCoordinator.kt"),
            File("../app/src/main/java/com/astrbot/android/runtime/plugin/PluginV2ToolLoopCoordinator.kt"),
        ).firstOrNull(File::exists)
            ?: error("Unable to locate PluginV2ToolLoopCoordinator.kt from ${System.getProperty("user.dir")}")
        val source = sourceFile
            .readText()

        assertFalse(source.contains("ExternalPluginHostActionExecutor"))
    }

    private fun pipelineInput(
        invokeProvider: suspend (PluginProviderRequest, PluginV2StreamingMode) -> PluginV2ProviderInvocationResult,
    ): PluginV2LlmPipelineInput {
        return PluginV2LlmPipelineInput(
            event = PluginMessageEvent(
                eventId = "evt-host-tool",
                platformAdapterType = "app_chat",
                messageType = MessageType.FriendMessage,
                conversationId = "conv-host-tool",
                senderId = "sender-host-tool",
                timestampEpochMillis = 1L,
                rawText = "hello host tool",
                initialWorkingText = "hello host tool",
                extras = emptyMap(),
            ),
            messageIds = listOf("msg-host-tool"),
            streamingMode = PluginV2StreamingMode.NON_STREAM,
            availableProviderIds = listOf("provider-a"),
            availableModelIdsByProvider = mapOf("provider-a" to listOf("model-a")),
            selectedProviderId = "provider-a",
            selectedModelId = "model-a",
            messages = listOf(
                PluginProviderMessageDto(
                    role = PluginProviderMessageRole.USER,
                    parts = listOf(PluginProviderMessagePartDto.TextPart("hello host tool")),
                ),
            ),
            invokeProvider = invokeProvider,
        )
    }

    private fun sessionWithTool(
        pluginId: String,
        toolName: String,
    ): PluginV2RuntimeSession {
        val session = PluginV2RuntimeSession(
            installRecord = samplePluginV2InstallRecord(pluginId = pluginId),
            sessionInstanceId = "session-$pluginId",
        )
        session.transitionTo(PluginV2RuntimeSessionState.Loading)
        session.transitionTo(PluginV2RuntimeSessionState.BootstrapRunning)
        session.requireBootstrapRawRegistry().appendTool(
            callbackToken = session.allocateCallbackToken(PluginV2CallbackHandle {}),
            descriptor = PluginToolDescriptor(
                pluginId = pluginId,
                name = toolName,
                inputSchema = linkedMapOf("type" to "object"),
            ),
        )
        session.transitionTo(PluginV2RuntimeSessionState.Active)
        return session
    }
}
