package com.elymbot.android.feature.plugin.runtime

import com.elymbot.android.model.plugin.PluginPermissionGrant
import com.elymbot.android.model.plugin.PluginRiskLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginV2HostLlmApiTest {

    @Test
    fun call_llm_returns_text_usage_and_selected_provider_model() = runTest {
        val port = RecordingHostLlmPort(
            response = PluginV2HostLlmPortResult(
                text = "compressed answer",
                finishReason = "stop",
                providerId = "provider-main",
                modelId = "model-main",
                usage = PluginLlmUsageSnapshot(promptTokens = 10, completionTokens = 3, totalTokens = 13),
            ),
        )
        val api = hostLlmApi(port = port)

        val result = api.callLlm(
            context = allowedContext(),
            request = PluginV2HostLlmRequest(
                providerId = "provider-main",
                modelId = "model-main",
                messages = listOf(PluginV2HostLlmMessage(role = "user", text = "Summarize")),
                temperature = 0.2,
                topP = 0.9,
                maxTokens = 800,
            ),
        )

        val success = result as PluginV2HostApiResult.Success
        val value = success.value as PluginV2HostLlmResult
        assertEquals(PluginV2HostLlmApi.HOST_API_CALL_LLM, success.api)
        assertEquals("compressed answer", value.text)
        assertEquals("stop", value.finishReason)
        assertEquals("provider-main", value.providerId)
        assertEquals("model-main", value.modelId)
        assertEquals(13, value.usage?.totalTokens)
        assertTrue(port.requests.single().bypassPluginLlmHooks)
    }

    @Test
    fun llm_generate_uses_distinct_audit_api_name_with_call_model_permission() = runTest {
        val logBus = InMemoryPluginRuntimeLogBus(capacity = 16, clock = { 1L })
        val api = hostLlmApi(logBus = logBus)

        val result = api.generate(
            context = allowedContext(),
            request = validRequest(),
        )

        val success = result as PluginV2HostApiResult.Success
        assertEquals(PluginV2HostLlmApi.HOST_API_LLM_GENERATE, success.api)
        val audit = logBus.snapshot(pluginId = "plugin.llm").single { record ->
            record.metadata["stage"] == "PluginV2HostApi"
        }
        assertEquals(PluginV2HostLlmApi.HOST_API_LLM_GENERATE, audit.metadata["api"])
        assertEquals(PluginV2HostApiPermissions.CALL_MODEL, audit.metadata["permissionId"])
    }

    @Test
    fun call_llm_audit_includes_provider_model_message_and_usage_details() = runTest {
        val logBus = InMemoryPluginRuntimeLogBus(capacity = 16, clock = { 1L })
        val api = hostLlmApi(
            logBus = logBus,
            port = RecordingHostLlmPort(
                response = PluginV2HostLlmPortResult(
                    text = "ok",
                    finishReason = "stop",
                    providerId = "provider-main",
                    modelId = "model-main",
                    usage = PluginLlmUsageSnapshot(
                        promptTokens = 11,
                        completionTokens = 7,
                        totalTokens = 18,
                    ),
                ),
            ),
        )

        val result = api.callLlm(
            context = allowedContext(),
            request = validRequest(
                messages = listOf(
                    PluginV2HostLlmMessage(role = "system", text = "Be concise"),
                    PluginV2HostLlmMessage(role = "user", text = "Hello"),
                ),
                maxTokens = 256,
            ),
        )

        assertTrue(result is PluginV2HostApiResult.Success)
        val audit = logBus.snapshot(pluginId = "plugin.llm").single { record ->
            record.metadata["api"] == PluginV2HostLlmApi.HOST_API_CALL_LLM
        }
        assertEquals("provider-main", audit.metadata["requestedProviderId"])
        assertEquals("model-main", audit.metadata["requestedModelId"])
        assertEquals("provider-main", audit.metadata["selectedProviderId"])
        assertEquals("model-main", audit.metadata["selectedModelId"])
        assertEquals("2", audit.metadata["messageCount"])
        assertEquals("256", audit.metadata["maxTokens"])
        assertEquals("11", audit.metadata["promptTokens"])
        assertEquals("7", audit.metadata["completionTokens"])
        assertEquals("18", audit.metadata["totalTokens"])
        assertEquals("0", audit.metadata["toolCount"])
        assertEquals("stop", audit.metadata["finishReason"])
        assertEquals("0", audit.metadata["durationMs"])
    }

    @Test
    fun missing_permission_returns_permission_denied() = runTest {
        val result = hostLlmApi().callLlm(
            context = allowedContext(granted = false),
            request = validRequest(),
        )

        assertFailureCode(result, PluginV2HostApiErrorCodes.PERMISSION_DENIED)
    }

    @Test
    fun unknown_provider_or_model_returns_structured_error() = runTest {
        val providerFailure = hostLlmApi(
            providers = listOf(provider(providerId = "provider-main", modelId = "model-main")),
        ).callLlm(
            context = allowedContext(),
            request = validRequest(providerId = "missing", modelId = "model-main"),
        )
        val modelFailure = hostLlmApi(
            providers = listOf(provider(providerId = "provider-main", modelId = "model-main")),
        ).callLlm(
            context = allowedContext(),
            request = validRequest(providerId = "provider-main", modelId = "missing"),
        )

        assertFailureCode(providerFailure, PluginV2HostLlmApi.PROVIDER_NOT_FOUND)
        assertFailureCode(modelFailure, PluginV2HostLlmApi.MODEL_NOT_FOUND)
    }

    @Test
    fun invalid_parameters_and_tool_role_are_rejected() = runTest {
        val temperatureFailure = hostLlmApi().callLlm(
            context = allowedContext(),
            request = validRequest(temperature = 3.0),
        )
        val topPFailure = hostLlmApi().callLlm(
            context = allowedContext(),
            request = validRequest(topP = 1.5),
        )
        val maxTokensFailure = hostLlmApi().callLlm(
            context = allowedContext(),
            request = validRequest(maxTokens = 0),
        )
        val toolRoleFailure = hostLlmApi().callLlm(
            context = allowedContext(),
            request = validRequest(
                messages = listOf(PluginV2HostLlmMessage(role = "tool", text = "not allowed")),
            ),
        )

        assertFailureCode(temperatureFailure, PluginV2HostApiErrorCodes.INVALID_PAYLOAD)
        assertFailureCode(topPFailure, PluginV2HostApiErrorCodes.INVALID_PAYLOAD)
        assertFailureCode(maxTokensFailure, PluginV2HostApiErrorCodes.INVALID_PAYLOAD)
        assertFailureCode(toolRoleFailure, PluginV2HostLlmApi.UNSUPPORTED_MESSAGE_ROLE)
    }

    @Test
    fun provider_secret_does_not_enter_result_or_audit_log() = runTest {
        val logBus = InMemoryPluginRuntimeLogBus(capacity = 16, clock = { 1L })
        val api = hostLlmApi(
            logBus = logBus,
            providers = listOf(
                provider(
                    providerId = "provider-main",
                    modelId = "model-main",
                    displayName = "Provider apiKey baseUrl credential",
                ),
            ),
        )

        val result = api.callLlm(
            context = allowedContext(),
            request = validRequest(),
        )

        assertTrue(result is PluginV2HostApiResult.Success)
        val serializedResult = result.toString()
        val serializedLogs = logBus.snapshot().joinToString("\n") { it.toString() }
        listOf("apiKey", "baseUrl", "credential", "secret").forEach { forbidden ->
            assertFalse("Result leaked $forbidden", serializedResult.contains(forbidden, ignoreCase = true))
            assertFalse("Log leaked $forbidden", serializedLogs.contains(forbidden, ignoreCase = true))
        }
    }

    private fun hostLlmApi(
        port: PluginV2HostLlmPort = RecordingHostLlmPort(),
        providers: List<PluginV2ProviderReadProvider> = listOf(provider()),
        logBus: PluginRuntimeLogBus = InMemoryPluginRuntimeLogBus(clock = { 1L }),
    ): PluginV2HostLlmApi {
        return PluginV2HostLlmApi(
            facade = PluginV2HostApiFacade(
                permissionPolicy = PluginV2HostApiPermissionPolicy(),
                asyncBridge = PluginV2HostApiAsyncBridge(dispatcher = Dispatchers.Unconfined),
                auditLogger = PluginV2HostApiAuditLogger(logBus = logBus, clock = { 10L }),
                clock = { 10L },
            ),
            providerReader = object : PluginV2ProviderReadPort {
                override suspend fun providers(): List<PluginV2ProviderReadProvider> = providers
            },
            llmPort = port,
        )
    }

    private fun allowedContext(granted: Boolean = true): PluginV2HostApiRequestContext {
        return PluginV2HostApiRequestContext(
            pluginId = "plugin.llm",
            pluginVersion = "1.0.0",
            requestId = "request-llm",
            conversationId = "conversation-current",
            platformAdapterType = "app_chat",
            manifestPermissionIds = setOf(PluginV2HostApiPermissions.CALL_MODEL),
            permissionSnapshot = listOf(
                PluginPermissionGrant(
                    permissionId = PluginV2HostApiPermissions.CALL_MODEL,
                    title = "Call model",
                    granted = granted,
                    riskLevel = PluginRiskLevel.HIGH,
                ),
            ),
            triggerPermissionWhitelist = setOf(PluginV2HostApiPermissions.CALL_MODEL),
        )
    }

    private fun validRequest(
        providerId: String = "provider-main",
        modelId: String = "model-main",
        messages: List<PluginV2HostLlmMessage> = listOf(PluginV2HostLlmMessage(role = "user", text = "Hello")),
        temperature: Double? = 0.2,
        topP: Double? = 0.9,
        maxTokens: Int? = 800,
    ): PluginV2HostLlmRequest = PluginV2HostLlmRequest(
        providerId = providerId,
        modelId = modelId,
        messages = messages,
        temperature = temperature,
        topP = topP,
        maxTokens = maxTokens,
    )

    private fun provider(
        providerId: String = "provider-main",
        modelId: String = "model-main",
        displayName: String = "Provider Main",
    ): PluginV2ProviderReadProvider = PluginV2ProviderReadProvider(
        providerId = providerId,
        displayName = displayName,
        enabled = true,
        capabilities = setOf("chat"),
        defaultModelId = modelId,
        models = listOf(
            PluginV2ProviderReadModel(
                modelId = modelId,
                displayName = modelId,
                capabilities = setOf("chat"),
                contextWindow = 8192,
                supportsToolCalling = true,
                supportsStreaming = false,
            ),
        ),
    )

    private fun assertFailureCode(result: PluginV2HostApiResult, code: String) {
        val failure = result as PluginV2HostApiResult.Failure
        assertEquals(code, failure.error.code)
    }

    private class RecordingHostLlmPort(
        private val response: PluginV2HostLlmPortResult = PluginV2HostLlmPortResult(
            text = "ok",
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
}
