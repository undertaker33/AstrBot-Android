package com.elymbot.android.runtime.plugin.toolsource

import com.elymbot.android.core.runtime.context.IngressTrigger
import com.elymbot.android.core.runtime.context.RuntimePlatform
import com.elymbot.android.core.runtime.context.ToolSourceContext
import com.elymbot.android.feature.plugin.runtime.InMemoryPluginRuntimeLogBus
import com.elymbot.android.feature.plugin.runtime.PluginLlmUsageSnapshot
import com.elymbot.android.feature.plugin.runtime.PluginToolArgs
import com.elymbot.android.feature.plugin.runtime.PluginToolResultStatus
import com.elymbot.android.feature.plugin.runtime.PluginV2ContextCompressApi
import com.elymbot.android.feature.plugin.runtime.PluginV2ContextCompressRequest
import com.elymbot.android.feature.plugin.runtime.PluginV2ContextCompressResult
import com.elymbot.android.feature.plugin.runtime.PluginV2HostApiAsyncBridge
import com.elymbot.android.feature.plugin.runtime.PluginV2HostApiAuditLogger
import com.elymbot.android.feature.plugin.runtime.PluginV2HostApiFacade
import com.elymbot.android.feature.plugin.runtime.PluginV2HostApiPermissionPolicy
import com.elymbot.android.feature.plugin.runtime.PluginV2HostApiResult
import com.elymbot.android.feature.plugin.runtime.PluginV2HostLlmPort
import com.elymbot.android.feature.plugin.runtime.PluginV2HostLlmPortRequest
import com.elymbot.android.feature.plugin.runtime.PluginV2HostLlmPortResult
import com.elymbot.android.feature.plugin.runtime.PluginV2ConversationHistoryMessage
import com.elymbot.android.feature.plugin.runtime.PluginV2ConversationHistoryReadPort
import com.elymbot.android.feature.plugin.runtime.PluginToolSourceKind
import com.elymbot.android.feature.plugin.runtime.toolsource.ContextStrategyToolSourceProvider
import com.elymbot.android.feature.plugin.runtime.toolsource.FutureToolSourceContextResolver
import com.elymbot.android.feature.plugin.runtime.toolsource.ToolSourceAvailabilityContext
import com.elymbot.android.feature.plugin.runtime.toolsource.ToolSourceIdentity
import com.elymbot.android.feature.plugin.runtime.toolsource.ToolSourceInvokeRequest
import com.elymbot.android.feature.plugin.runtime.toolsource.ToolSourceRegistryIngestContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextStrategyToolSourceProviderTest {

    @Test
    fun llm_compress_active_exposes_and_invokes_compress_context() = runTest {
        val provider = provider(context = toolContext(contextLimitStrategy = "llm_compress"))

        val bindings = provider.listBindings(
            ToolSourceRegistryIngestContext(toolSourceContext = toolContext(contextLimitStrategy = "llm_compress")),
        )
        val result = provider.invoke(invokeRequest(contextLimitStrategy = "llm_compress"))

        assertEquals("compress_context", bindings.single().descriptor.name)
        assertEquals(PluginToolResultStatus.SUCCESS, result.result.status)
        assertEquals("summary", result.result.structuredContent?.get("summary"))
        assertEquals(2, result.result.structuredContent?.get("sourceMessageCount"))
    }

    @Test
    fun not_llm_compress_keeps_unavailable_and_denied_semantics() = runTest {
        val provider = provider(context = toolContext(contextLimitStrategy = "truncate_by_turns"))

        val bindings = provider.listBindings(
            ToolSourceRegistryIngestContext(toolSourceContext = toolContext(contextLimitStrategy = "truncate_by_turns")),
        )
        val availability = provider.availabilityOf(
            identity = identity(),
            context = ToolSourceAvailabilityContext(toolSourceContext = toolContext(contextLimitStrategy = "truncate_by_turns")),
        )
        val result = provider.invoke(invokeRequest(contextLimitStrategy = "truncate_by_turns"))

        assertTrue(bindings.isEmpty())
        assertEquals(false, availability.capabilityAllowed)
        assertEquals(PluginToolResultStatus.ERROR, result.result.status)
        assertEquals("context_strategy_not_llm_compress", result.result.errorCode)
    }

    @Test
    fun fake_llm_failure_maps_structured_error() = runTest {
        val provider = provider(
            context = toolContext(contextLimitStrategy = "llm_compress"),
            llmPort = object : PluginV2HostLlmPort {
                override suspend fun generate(request: PluginV2HostLlmPortRequest): PluginV2HostLlmPortResult {
                    throw IllegalStateException("failed")
                }
            },
        )

        val result = provider.invoke(invokeRequest(contextLimitStrategy = "llm_compress"))

        assertEquals(PluginToolResultStatus.ERROR, result.result.status)
        assertEquals(PluginV2ContextCompressApi.CONTEXT_COMPRESS_FAILED, result.result.errorCode)
    }

    private fun provider(
        context: ToolSourceContext,
        llmPort: PluginV2HostLlmPort = object : PluginV2HostLlmPort {
            override suspend fun generate(request: PluginV2HostLlmPortRequest): PluginV2HostLlmPortResult =
                PluginV2HostLlmPortResult(
                    text = "summary",
                    finishReason = "stop",
                    providerId = request.providerId,
                    modelId = request.modelId,
                    usage = PluginLlmUsageSnapshot(totalTokens = 12),
                )
        },
    ): ContextStrategyToolSourceProvider {
        val resolver = object : FutureToolSourceContextResolver {
            override fun resolveForConfig(configProfileId: String): ToolSourceContext = context
        }
        return ContextStrategyToolSourceProvider(
            contextResolver = resolver,
            contextCompressor = PluginV2ContextCompressApi(
                facade = PluginV2HostApiFacade(
                    permissionPolicy = PluginV2HostApiPermissionPolicy(),
                    asyncBridge = PluginV2HostApiAsyncBridge(dispatcher = Dispatchers.Unconfined),
                    auditLogger = PluginV2HostApiAuditLogger(
                        logBus = InMemoryPluginRuntimeLogBus(clock = { 1L }),
                        clock = { 10L },
                    ),
                    clock = { 10L },
                ),
                historyReader = PluginV2ConversationHistoryReadPort {
                    listOf(
                        PluginV2ConversationHistoryMessage(
                            messageId = "m1",
                            role = "user",
                            senderId = "user",
                            messageType = "text",
                            text = "hello",
                            timestampEpochMillis = 1L,
                        ),
                        PluginV2ConversationHistoryMessage(
                            messageId = "m2",
                            role = "assistant",
                            senderId = "assistant",
                            messageType = "text",
                            text = "world",
                            timestampEpochMillis = 2L,
                        ),
                    )
                },
                llmPort = llmPort,
            ),
        )
    }

    private fun invokeRequest(contextLimitStrategy: String): ToolSourceInvokeRequest =
        ToolSourceInvokeRequest(
            identity = identity(),
            args = PluginToolArgs(
                toolCallId = "tool-call-1",
                requestId = "request-1",
                toolId = "ctx.compress:compress_context",
                payload = mapOf(
                    "conversationId" to "conversation-current",
                    "providerId" to "provider-main",
                    "modelId" to "model-main",
                    "maxTokens" to 512,
                ),
            ),
            timeoutMs = 5_000L,
            configProfileId = "config-main",
            toolSourceContext = toolContext(contextLimitStrategy = contextLimitStrategy),
        )

    private fun identity(): ToolSourceIdentity =
        ToolSourceIdentity(
            sourceKind = PluginToolSourceKind.CONTEXT_STRATEGY,
            ownerId = "ctx.compress",
            sourceRef = "compress_context",
            displayName = "Compress Context",
        )

    private fun toolContext(contextLimitStrategy: String): ToolSourceContext =
        ToolSourceContext(
            requestId = "request-ctx",
            platform = RuntimePlatform.APP_CHAT,
            configProfileId = "config-main",
            webSearchEnabled = false,
            activeCapabilityEnabled = false,
            mcpServers = emptyList(),
            promptSkills = emptyList(),
            toolSkills = emptyList(),
            conversationId = "conversation-current",
            contextLimitStrategy = contextLimitStrategy,
            runtimePermissions = mapOf(
                "context_compress" to true,
            ),
            ingressTrigger = IngressTrigger.USER_MESSAGE,
        )
}
