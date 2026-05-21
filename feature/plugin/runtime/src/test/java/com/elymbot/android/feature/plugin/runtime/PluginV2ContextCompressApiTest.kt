package com.elymbot.android.feature.plugin.runtime

import com.elymbot.android.model.plugin.PluginPermissionGrant
import com.elymbot.android.model.plugin.PluginRiskLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginV2ContextCompressApiTest {

    @Test
    fun current_conversation_compresses_history_with_host_llm() = runTest {
        val llmPort = RecordingHostLlmPort(
            response = PluginV2HostLlmPortResult(
                text = "short summary",
                finishReason = "stop",
                providerId = "provider-main",
                modelId = "model-main",
                usage = PluginLlmUsageSnapshot(promptTokens = 20, completionTokens = 5, totalTokens = 25),
            ),
        )
        val api = contextCompressApi(llmPort = llmPort)

        val result = api.compress(
            context = allowedContext(),
            request = PluginV2ContextCompressRequest(
                conversationId = "conversation-current",
                providerId = "provider-main",
                modelId = "model-main",
                maxTokens = 512,
            ),
        )

        val success = result as PluginV2HostApiResult.Success
        val value = success.value as PluginV2ContextCompressResult
        assertEquals(PluginV2ContextCompressApi.HOST_API_CONTEXT_COMPRESS, success.api)
        assertEquals("short summary", value.summary)
        assertEquals(2, value.sourceMessageCount)
        assertEquals(25, value.usage?.totalTokens)
        val llmRequest = llmPort.requests.single()
        assertTrue(llmRequest.bypassPluginLlmHooks)
        assertEquals("provider-main", llmRequest.providerId)
        assertEquals("model-main", llmRequest.modelId)
        assertTrue(llmRequest.systemPrompt?.contains("conversation context", ignoreCase = true) == true)
    }

    @Test
    fun missing_permission_returns_permission_denied() = runTest {
        val result = contextCompressApi().compress(
            context = allowedContext(granted = false),
            request = validRequest(),
        )

        assertFailureCode(result, PluginV2HostApiErrorCodes.PERMISSION_DENIED)
    }

    @Test
    fun non_current_conversation_is_rejected() = runTest {
        val result = contextCompressApi().compress(
            context = allowedContext(conversationId = "conversation-current"),
            request = validRequest(conversationId = "conversation-other"),
        )

        assertFailureCode(result, PluginV2ContextCompressApi.CONVERSATION_SCOPE_VIOLATION)
    }

    @Test
    fun host_llm_failure_maps_to_structured_error() = runTest {
        val result = contextCompressApi(llmPort = FailingHostLlmPort()).compress(
            context = allowedContext(),
            request = validRequest(),
        )

        assertFailureCode(result, PluginV2ContextCompressApi.CONTEXT_COMPRESS_FAILED)
    }

    private fun contextCompressApi(
        llmPort: PluginV2HostLlmPort = RecordingHostLlmPort(),
    ): PluginV2ContextCompressApi {
        return PluginV2ContextCompressApi(
            facade = PluginV2HostApiFacade(
                permissionPolicy = PluginV2HostApiPermissionPolicy(),
                asyncBridge = PluginV2HostApiAsyncBridge(dispatcher = Dispatchers.Unconfined),
                auditLogger = PluginV2HostApiAuditLogger(
                    logBus = InMemoryPluginRuntimeLogBus(clock = { 1L }),
                    clock = { 10L },
                ),
                clock = { 10L },
            ),
            historyReader = PluginV2ConversationHistoryReadPort { request ->
                assertEquals("conversation-current", request.conversationId)
                listOf(
                    PluginV2ConversationHistoryMessage(
                        messageId = "message-1",
                        role = "user",
                        senderId = "user",
                        messageType = "text",
                        text = "Long context from the user.",
                        timestampEpochMillis = 1L,
                    ),
                    PluginV2ConversationHistoryMessage(
                        messageId = "message-2",
                        role = "assistant",
                        senderId = "assistant",
                        messageType = "text",
                        text = "Long assistant response.",
                        timestampEpochMillis = 2L,
                    ),
                )
            },
            llmPort = llmPort,
        )
    }

    private fun allowedContext(
        conversationId: String = "conversation-current",
        granted: Boolean = true,
    ): PluginV2HostApiRequestContext {
        return PluginV2HostApiRequestContext(
            pluginId = "plugin.compress",
            pluginVersion = "1.0.0",
            requestId = "request-compress",
            conversationId = conversationId,
            platformAdapterType = "app_chat",
            manifestPermissionIds = setOf(PluginV2HostApiPermissions.CONTEXT_COMPRESS),
            permissionSnapshot = listOf(
                PluginPermissionGrant(
                    permissionId = PluginV2HostApiPermissions.CONTEXT_COMPRESS,
                    title = "Compress context",
                    granted = granted,
                    riskLevel = PluginRiskLevel.MEDIUM,
                ),
            ),
            triggerPermissionWhitelist = setOf(PluginV2HostApiPermissions.CONTEXT_COMPRESS),
        )
    }

    private fun validRequest(
        conversationId: String = "conversation-current",
    ): PluginV2ContextCompressRequest = PluginV2ContextCompressRequest(
        conversationId = conversationId,
        providerId = "provider-main",
        modelId = "model-main",
        maxTokens = 512,
    )

    private fun assertFailureCode(result: PluginV2HostApiResult, code: String) {
        val failure = result as PluginV2HostApiResult.Failure
        assertEquals(code, failure.error.code)
    }

    private class RecordingHostLlmPort(
        private val response: PluginV2HostLlmPortResult = PluginV2HostLlmPortResult(
            text = "summary",
            finishReason = "stop",
            providerId = "provider-main",
            modelId = "model-main",
        ),
    ) : PluginV2HostLlmPort {
        val requests = mutableListOf<PluginV2HostLlmPortRequest>()

        override suspend fun generate(request: PluginV2HostLlmPortRequest): PluginV2HostLlmPortResult {
            requests += request
            return response
        }
    }

    private class FailingHostLlmPort : PluginV2HostLlmPort {
        override suspend fun generate(request: PluginV2HostLlmPortRequest): PluginV2HostLlmPortResult {
            throw IllegalStateException("provider unavailable")
        }
    }
}
