package com.elymbot.android.feature.plugin.runtime

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginV2AgentInvocationTest {

    @Test
    fun agent_invoker_consumes_compiled_handler_and_routes_agent_run_to_runner() = runTest {
        val session = agentSession("com.example.agent.run")
        val handler = RecordingAgentHandler()
        val hostApi = PluginV2BootstrapHostApi(session = session, clock = { 1L })
        hostApi.registerAgent(
            AgentRegistrationInput(
                key = "research-agent",
                systemPrompt = "Use host LLM.",
                tools = emptyList(),
                model = AgentModelSelection(providerId = "provider-a", modelId = "model-a-1"),
                handler = handler,
            ),
        )
        val compiled = requireNotNull(PluginV2RegistryCompiler(clock = { 1L }).compile(session.requireBootstrapRawRegistry()).compiledRegistry)
        session.attachCompiledRegistry(compiled)
        session.transitionTo(PluginV2RuntimeSessionState.Active)

        val store = PluginV2ActiveRuntimeStore(clock = { 1L })
        store.commitLoadedRuntime(
            PluginV2ActiveRuntimeEntry(
                session = session,
                compiledRegistry = compiled,
                lastBootstrapSummary = PluginV2BootstrapSummary(
                    pluginId = session.pluginId,
                    sessionInstanceId = session.sessionInstanceId,
                    compiledAtEpochMillis = 1L,
                    handlerCount = compiled.handlerRegistry.totalHandlerCount,
                    warningCount = 0,
                    errorCount = 0,
                ),
                diagnostics = emptyList(),
                callbackTokens = session.snapshotCallbackTokens(),
            ),
        )

        val llmRequests = mutableListOf<PluginV2HostLlmPortRequest>()
        val invoker = PluginV2AgentInvoker(
            store = store,
            agentRunner = PluginV2AgentRunner(
                llmPort = PluginV2HostLlmPort { request ->
                    llmRequests += request
                    PluginV2HostLlmPortResult(
                        text = "agent-result-${request.messages.single().text}",
                        finishReason = "stop",
                        providerId = request.providerId,
                        modelId = request.modelId,
                    )
                },
                logBus = InMemoryPluginRuntimeLogBus(clock = { 1L }),
                clock = { 1L },
            ),
            clock = { 1L },
        )

        val result = invoker.invoke(
            PluginV2AgentInvocationRequest(
                context = hostContext(),
                pluginId = session.pluginId,
                agentKey = "research-agent",
                input = "agent question",
            ),
        )

        val success = result as PluginV2HostApiResult.Success
        val agentResult = success.value as AgentRunResult
        assertEquals("agent question", handler.invokedInput)
        assertEquals("agent-result-agent question", agentResult.text)
        assertEquals("provider-a", agentResult.providerId)
        assertEquals("model-a-1", agentResult.modelId)
        assertEquals(1, llmRequests.size)
        assertTrue(llmRequests.single().bypassPluginLlmHooks)
    }

    private class RecordingAgentHandler : PluginV2AgentCallbackHandle {
        var invokedInput: String = ""

        override fun invoke() = Unit

        override suspend fun handleAgent(event: PluginV2AgentInvocationEvent): Any? {
            invokedInput = event.input
            return event.agent.run(event.input)
        }
    }
}
