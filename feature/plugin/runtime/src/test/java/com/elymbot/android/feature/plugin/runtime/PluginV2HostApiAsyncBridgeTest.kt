package com.elymbot.android.feature.plugin.runtime

import com.elymbot.android.model.plugin.PluginPermissionGrant
import com.elymbot.android.model.plugin.PluginRiskLevel
import com.elymbot.android.model.plugin.PluginTriggerMetadata
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PluginV2HostApiAsyncBridgeTest {

    @Test
    fun fake_host_api_can_be_awaited_and_settled() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val bridge = PluginV2HostApiAsyncBridge(dispatcher = dispatcher)
        val hostResult = CompletableDeferred<String>()

        val result = async {
            bridge.await(
                context = requestContext(),
                api = "hostApi.fake",
                timeoutMs = 1_000L,
            ) {
                hostResult.await()
            }
        }
        runCurrent()

        assertFalse(result.isCompleted)

        hostResult.complete("settled")
        advanceUntilIdle()

        val success = result.await() as PluginV2HostApiResult.Success
        assertEquals("settled", success.value)
    }

    @Test
    fun timeout_returns_structured_error() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val bridge = PluginV2HostApiAsyncBridge(dispatcher = dispatcher)

        val result = bridge.await(
            context = requestContext(requestId = "req-timeout"),
            api = "hostApi.slow",
            timeoutMs = 50L,
        ) {
            delay(1_000L)
            "late"
        }

        val failure = result as PluginV2HostApiResult.Failure
        assertEquals(PluginV2HostApiErrorCodes.TIMEOUT, failure.error.code)
        assertEquals("req-timeout", failure.requestId)
    }

    @Test
    fun thrown_host_failure_is_logged_and_converted_to_structured_error() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 20L })
        val facade = PluginV2HostApiFacade(
            permissionPolicy = PluginV2HostApiPermissionPolicy(),
            asyncBridge = PluginV2HostApiAsyncBridge(dispatcher = dispatcher),
            auditLogger = PluginV2HostApiAuditLogger(
                logBus = logBus,
                clock = { 20L },
            ),
            clock = { 10L },
        )

        val result = facade.call(
            context = requestContext(requestId = "req-throw"),
            api = "hostApi.fake",
            permissionId = PluginV2HostApiPermissions.NETWORK_REQUEST,
            timeoutMs = 1_000L,
        ) {
            error("provider secret sk-test should not leak from InternalProviderDao")
        }

        val failure = result as PluginV2HostApiResult.Failure
        assertEquals(PluginV2HostApiErrorCodes.EXECUTION_FAILED, failure.error.code)

        val record = logBus.snapshot(pluginId = "plugin.host-api-test").single()
        assertEquals("plugin_v2_host_api_failed", record.code)
        assertEquals("execution_failed", record.metadata["failureCode"])
        val serializedRecord = record.message + record.metadata.values.joinToString(separator = "|")
        assertFalse(serializedRecord.contains("sk-test"))
        assertFalse(serializedRecord.contains("InternalProviderDao"))
    }

    @Test
    fun continuation_after_settle_is_observable_at_bridge_level() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val bridge = PluginV2HostApiAsyncBridge(dispatcher = dispatcher)
        val events = mutableListOf<String>()

        val result = bridge.await(
            context = requestContext(requestId = "req-continuation"),
            api = "hostApi.fake",
            timeoutMs = 1_000L,
        ) {
            events += "host-settled"
            "payload"
        }
        if (result is PluginV2HostApiResult.Success) {
            events += "continuation:${result.value}"
        }

        assertEquals(listOf("host-settled", "continuation:payload"), events)
    }

    private fun requestContext(
        requestId: String = "req-async",
    ): PluginV2HostApiRequestContext {
        return PluginV2HostApiRequestContext(
            pluginId = "plugin.host-api-test",
            pluginVersion = "1.0.0",
            requestId = requestId,
            conversationId = "conversation-1",
            platformAdapterType = "app_chat",
            manifestPermissionIds = setOf(PluginV2HostApiPermissions.NETWORK_REQUEST),
            permissionSnapshot = listOf(
                PluginPermissionGrant(
                    permissionId = PluginV2HostApiPermissions.NETWORK_REQUEST,
                    title = "Network access",
                    granted = true,
                    riskLevel = PluginRiskLevel.MEDIUM,
                ),
            ),
            triggerPermissionWhitelist = setOf(PluginV2HostApiPermissions.NETWORK_REQUEST),
            triggerMetadata = PluginTriggerMetadata(eventId = "event-async"),
        )
    }
}
