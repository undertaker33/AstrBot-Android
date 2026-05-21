package com.elymbot.android.feature.plugin.runtime

import com.elymbot.android.model.plugin.PluginPermissionGrant
import com.elymbot.android.model.plugin.PluginRiskLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginV2ProviderReadApiTest {

    @Test
    fun list_returns_sanitized_provider_summaries() = runTest {
        val api = providerReadApi(
            providerReader = FakeProviderReadPort(
                providers = listOf(
                    PluginV2ProviderReadProvider(
                        providerId = "provider-openai",
                        displayName = "OpenAI",
                        enabled = true,
                        capabilities = setOf("chat", "tool_calling", "apiKey"),
                        defaultModelId = "gpt-4.1-mini",
                        models = listOf(
                            PluginV2ProviderReadModel(
                                modelId = "gpt-4.1-mini",
                                displayName = "GPT 4.1 Mini",
                                capabilities = setOf("chat"),
                                contextWindow = 128_000,
                                supportsToolCalling = true,
                                supportsStreaming = true,
                            ),
                            PluginV2ProviderReadModel(
                                modelId = "gpt-4.1",
                                displayName = "GPT 4.1",
                                capabilities = setOf("chat"),
                                contextWindow = 128_000,
                                supportsToolCalling = true,
                                supportsStreaming = true,
                            ),
                        ),
                    ),
                ),
            ),
        )

        val result = api.list(context = allowedContext())

        val success = result as PluginV2HostApiResult.Success
        assertEquals(PluginV2ProviderReadApi.HOST_API_PROVIDERS_LIST, success.api)
        val providers = success.value as List<*>
        val summary = providers.single() as PluginV2ProviderSummary
        assertEquals("provider-openai", summary.providerId)
        assertEquals("OpenAI", summary.displayName)
        assertTrue(summary.enabled)
        assertEquals(setOf("chat", "tool_calling"), summary.capabilities)
        assertEquals("gpt-4.1-mini", summary.defaultModelId)
        assertEquals(2, summary.modelCount)
        assertNoSensitiveTokens(success)
    }

    @Test
    fun models_returns_sanitized_model_summaries_for_selected_provider() = runTest {
        val api = providerReadApi(
            providerReader = FakeProviderReadPort(
                providers = listOf(
                    PluginV2ProviderReadProvider(
                        providerId = "provider-openai",
                        displayName = "OpenAI",
                        enabled = true,
                        capabilities = setOf("chat"),
                        defaultModelId = "gpt-4.1-mini",
                        models = listOf(
                            PluginV2ProviderReadModel(
                                modelId = "gpt-4.1-mini",
                                displayName = "GPT 4.1 Mini",
                                capabilities = setOf("chat", "streaming", "headers"),
                                contextWindow = 128_000,
                                supportsToolCalling = true,
                                supportsStreaming = true,
                            ),
                        ),
                    ),
                ),
            ),
        )

        val result = api.models(
            context = allowedContext(),
            request = PluginV2ProviderModelsRequest(providerId = "provider-openai"),
        )

        val success = result as PluginV2HostApiResult.Success
        assertEquals(PluginV2ProviderReadApi.HOST_API_PROVIDERS_MODELS, success.api)
        val models = success.value as List<*>
        val summary = models.single() as PluginV2ProviderModelSummary
        assertEquals("gpt-4.1-mini", summary.modelId)
        assertEquals("GPT 4.1 Mini", summary.displayName)
        assertEquals(setOf("chat", "streaming"), summary.capabilities)
        assertEquals(128_000, summary.contextWindow)
        assertTrue(summary.supportsToolCalling)
        assertTrue(summary.supportsStreaming)
        assertNoSensitiveTokens(success)
    }

    @Test
    fun permission_denial_returns_structured_permission_denied() = runTest {
        val result = providerReadApi(
            providerReader = FakeProviderReadPort(),
        ).list(context = allowedContext(granted = false))

        val failure = result as PluginV2HostApiResult.Failure
        assertEquals(PluginV2HostApiErrorCodes.PERMISSION_DENIED, failure.error.code)
        assertEquals(PluginV2ProviderReadApi.HOST_API_PROVIDERS_LIST, failure.error.details["api"])
        assertNoSensitiveTokens(failure)
    }

    @Test
    fun missing_provider_returns_structured_provider_not_found() = runTest {
        val result = providerReadApi(
            providerReader = FakeProviderReadPort(
                providers = listOf(
                    PluginV2ProviderReadProvider(
                        providerId = "provider-openai",
                        displayName = "OpenAI",
                        enabled = true,
                        capabilities = setOf("chat"),
                        defaultModelId = "gpt-4.1-mini",
                        models = emptyList(),
                    ),
                ),
            ),
        ).models(
            context = allowedContext(),
            request = PluginV2ProviderModelsRequest(providerId = "missing-provider"),
        )

        val failure = result as PluginV2HostApiResult.Failure
        assertEquals(PluginV2ProviderReadApi.PROVIDER_NOT_FOUND, failure.error.code)
        assertEquals("missing-provider", failure.error.details["providerId"])
        assertNoSensitiveTokens(failure)
    }

    @Test
    fun returned_objects_and_errors_do_not_contain_sensitive_or_internal_names() = runTest {
        val api = providerReadApi(
            providerReader = FakeProviderReadPort(
                providers = listOf(
                    PluginV2ProviderReadProvider(
                        providerId = "provider-openai",
                        displayName = "baseUrl ProviderDao credential",
                        enabled = true,
                        capabilities = setOf("chat", "credential", "ProviderDao"),
                        defaultModelId = "apiKey-model",
                        models = listOf(
                            PluginV2ProviderReadModel(
                                modelId = "headers-model",
                                displayName = "ProviderDao model",
                                capabilities = setOf("chat", "baseUrl"),
                                contextWindow = 4_096,
                                supportsToolCalling = false,
                                supportsStreaming = false,
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertNoSensitiveTokens(api.list(context = allowedContext()))
        assertNoSensitiveTokens(
            api.models(
                context = allowedContext(),
                request = PluginV2ProviderModelsRequest(providerId = "provider-openai"),
            ),
        )
        assertNoSensitiveTokens(
            api.models(
                context = allowedContext(),
                request = PluginV2ProviderModelsRequest(providerId = "missing-provider"),
            ),
        )
    }

    private fun providerReadApi(
        providerReader: PluginV2ProviderReadPort,
        logBus: PluginRuntimeLogBus = InMemoryPluginRuntimeLogBus(clock = { 1L }),
    ): PluginV2ProviderReadApi {
        return PluginV2ProviderReadApi(
            facade = PluginV2HostApiFacade(
                permissionPolicy = PluginV2HostApiPermissionPolicy(),
                asyncBridge = PluginV2HostApiAsyncBridge(dispatcher = Dispatchers.Unconfined),
                auditLogger = PluginV2HostApiAuditLogger(logBus = logBus, clock = { 10L }),
                clock = { 10L },
            ),
            providerReader = providerReader,
        )
    }

    private fun allowedContext(granted: Boolean = true): PluginV2HostApiRequestContext {
        return PluginV2HostApiRequestContext(
            pluginId = "plugin.provider",
            pluginVersion = "1.0.0",
            requestId = "request-provider",
            manifestPermissionIds = setOf(PluginV2HostApiPermissions.PROVIDER_READ),
            permissionSnapshot = listOf(
                PluginPermissionGrant(
                    permissionId = PluginV2HostApiPermissions.PROVIDER_READ,
                    title = "Provider read",
                    granted = granted,
                    riskLevel = PluginRiskLevel.LOW,
                ),
            ),
            triggerPermissionWhitelist = setOf(PluginV2HostApiPermissions.PROVIDER_READ),
        )
    }

    private fun assertNoSensitiveTokens(value: Any) {
        val text = value.toString()
        SENSITIVE_TOKENS.forEach { token ->
            assertFalse("Sensitive token leaked: $token in $text", text.contains(token, ignoreCase = true))
        }
    }

    private class FakeProviderReadPort(
        private val providers: List<PluginV2ProviderReadProvider> = emptyList(),
    ) : PluginV2ProviderReadPort {
        override suspend fun providers(): List<PluginV2ProviderReadProvider> = providers
    }

    companion object {
        private val SENSITIVE_TOKENS = listOf(
            "apiKey",
            "baseUrl",
            "headers",
            "credential",
            "ProviderDao",
        )
    }
}
