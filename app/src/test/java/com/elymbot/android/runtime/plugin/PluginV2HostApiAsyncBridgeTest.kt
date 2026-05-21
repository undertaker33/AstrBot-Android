package com.elymbot.android.feature.plugin.runtime

import com.elymbot.android.model.plugin.PluginPermissionGrant
import com.elymbot.android.model.plugin.PluginRiskLevel
import com.elymbot.android.model.plugin.PluginTriggerMetadata
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
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
    fun successful_suspend_block_returns_success_after_settle() = runTest {
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
    fun timeout_returns_timeout() = runTest {
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
        assertEquals("req-timeout", failure.requestId)
        assertEquals(PluginV2HostApiErrorCodes.TIMEOUT, failure.error.code)
    }

    @Test
    fun illegal_argument_exception_returns_invalid_payload() = runTest {
        val bridge = PluginV2HostApiAsyncBridge(
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        val result = bridge.await(
            context = requestContext(requestId = "req-invalid"),
            api = "hostApi.fake",
            timeoutMs = 1_000L,
        ) {
            throw IllegalArgumentException("bad payload")
        }

        val failure = result as PluginV2HostApiResult.Failure
        assertEquals("req-invalid", failure.requestId)
        assertEquals(PluginV2HostApiErrorCodes.INVALID_PAYLOAD, failure.error.code)
    }

    @Test
    fun generic_exception_returns_execution_failed_and_is_logged() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 20L })
        val facade = PluginV2HostApiFacade(
            permissionPolicy = PluginV2HostApiPermissionPolicy(),
            asyncBridge = PluginV2HostApiAsyncBridge(dispatcher = dispatcher),
            auditLogger = PluginV2HostApiAuditLogger(logBus = logBus, clock = { 20L }),
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
        assertEquals(PluginV2HostApiErrorCodes.EXECUTION_FAILED, record.metadata["failureCode"])
        val serializedRecord = record.message + record.metadata.values.joinToString(separator = "|")
        assertFalse(serializedRecord.contains("sk-test"))
        assertFalse(serializedRecord.contains("InternalProviderDao"))
    }

    @Test
    fun cancellation_is_preserved_without_raw_stack_log() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 30L })
        val facade = PluginV2HostApiFacade(
            permissionPolicy = PluginV2HostApiPermissionPolicy(),
            asyncBridge = PluginV2HostApiAsyncBridge(dispatcher = dispatcher),
            auditLogger = PluginV2HostApiAuditLogger(logBus = logBus, clock = { 30L }),
            clock = { 10L },
        )

        val thrown = try {
            facade.call(
                context = requestContext(requestId = "req-cancel"),
                api = "hostApi.fake",
                permissionId = PluginV2HostApiPermissions.NETWORK_REQUEST,
                timeoutMs = 1_000L,
            ) {
                throw CancellationException("cancelled with stack at HostDao.kt:12")
            }
            null
        } catch (error: CancellationException) {
            error
        }

        assertEquals("cancelled with stack at HostDao.kt:12", thrown?.message)
        assertTrue(logBus.snapshot(pluginId = "plugin.host-api-test").isEmpty())
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
