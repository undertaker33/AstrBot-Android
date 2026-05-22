package com.elymbot.android.feature.plugin.runtime

import com.elymbot.android.model.plugin.PluginPermissionGrant
import com.elymbot.android.model.plugin.PluginRiskLevel
import com.elymbot.android.model.plugin.PluginRuntimeLogLevel
import com.elymbot.android.model.plugin.PluginTriggerMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PluginV2HostApiAuditLoggerTest {

    @Test
    fun success_and_failure_include_required_audit_fields() {
        var now = 100L
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 100L })
        val logger = PluginV2HostApiAuditLogger(
            logBus = logBus,
            clock = { now++ },
        )
        val context = requestContext()

        logger.record(
            context = context,
            api = "hostApi.fake",
            permissionId = PluginV2HostApiPermissions.NETWORK_REQUEST,
            result = PluginV2HostApiResult.Success(
                requestId = context.requestId,
                api = "hostApi.fake",
                value = "ok",
            ),
            durationMs = 12L,
        )
        logger.record(
            context = context.copy(requestId = "req-failure"),
            api = "hostApi.fake",
            permissionId = PluginV2HostApiPermissions.NETWORK_REQUEST,
            result = PluginV2HostApiResult.Failure(
                requestId = "req-failure",
                api = "hostApi.fake",
                error = PluginV2HostApiError(
                    code = PluginV2HostApiErrorCodes.PERMISSION_DENIED,
                    message = "Denied",
                ),
            ),
            durationMs = 21L,
        )

        val records = logBus.snapshot(pluginId = context.pluginId).sortedBy { it.occurredAtEpochMillis }
        val success = records[0]
        val failure = records[1]

        assertEquals("plugin_v2_host_api_succeeded", success.code)
        assertEquals(PluginRuntimeLogLevel.Info, success.level)
        assertEquals(true, success.succeeded)
        assertEquals(12L, success.durationMillis)
        assertRequiredAuditFields(success.metadata, context.requestId, "")

        assertEquals("plugin_v2_host_api_failed", failure.code)
        assertEquals(PluginRuntimeLogLevel.Warning, failure.level)
        assertEquals(false, failure.succeeded)
        assertEquals(21L, failure.durationMillis)
        assertRequiredAuditFields(
            metadata = failure.metadata,
            requestId = "req-failure",
            failureCode = PluginV2HostApiErrorCodes.PERMISSION_DENIED,
        )
    }

    @Test
    fun provider_secret_raw_stack_and_internal_dao_details_are_not_logged() {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 200L })
        val logger = PluginV2HostApiAuditLogger(
            logBus = logBus,
            clock = { 200L },
        )
        val context = requestContext()

        logger.record(
            context = context,
            api = "hostApi.callLlm",
            permissionId = PluginV2HostApiPermissions.CALL_MODEL,
            result = PluginV2HostApiResult.Failure(
                requestId = context.requestId,
                api = "hostApi.callLlm",
                error = PluginV2HostApiError(
                    code = PluginV2HostApiErrorCodes.EXECUTION_FAILED,
                    message = "provider secret sk-live leaked\nat InternalProviderDao.query(ProviderDao.kt:42)",
                    details = linkedMapOf(
                        "providerSecret" to "sk-live",
                        "rawStack" to "InternalProviderDao.query",
                        "internalDao" to "ProviderDao",
                    ),
                ),
            ),
            durationMs = 5L,
        )

        val record = logBus.snapshot(pluginId = context.pluginId).single()
        val serializedRecord = record.message + "|" + record.metadata.entries.joinToString(separator = "|")
        assertFalse(serializedRecord.contains("sk-live"))
        assertFalse(serializedRecord.contains("InternalProviderDao"))
        assertFalse(serializedRecord.contains("ProviderDao"))
        assertFalse(serializedRecord.contains("rawStack"))
        assertEquals(PluginV2HostApiErrorCodes.EXECUTION_FAILED, record.metadata["failureCode"])
    }

    @Test
    fun details_cannot_override_required_audit_fields() {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 300L })
        val logger = PluginV2HostApiAuditLogger(
            logBus = logBus,
            clock = { 300L },
        )
        val context = requestContext()

        logger.record(
            context = context,
            api = "hostApi.fake",
            permissionId = PluginV2HostApiPermissions.NETWORK_REQUEST,
            result = PluginV2HostApiResult.Success(
                requestId = context.requestId,
                api = "hostApi.fake",
                value = "ok",
            ),
            durationMs = 7L,
            details = linkedMapOf(
                "api" to "hostApi.spoofed",
                "permissionId" to "permission.spoofed",
                "requestId" to "req-spoofed",
                "durationMs" to "999",
                "providerId" to "provider-main",
                "rawStack" to "InternalProviderDao.query",
            ),
        )

        val metadata = logBus.snapshot(pluginId = context.pluginId).single().metadata
        assertEquals("hostApi.fake", metadata["api"])
        assertEquals(PluginV2HostApiPermissions.NETWORK_REQUEST, metadata["permissionId"])
        assertEquals(context.requestId, metadata["requestId"])
        assertEquals("7", metadata["durationMs"])
        assertEquals("provider-main", metadata["providerId"])
        assertFalse(metadata.containsKey("rawStack"))
    }

    private fun assertRequiredAuditFields(
        metadata: Map<String, String>,
        requestId: String,
        failureCode: String,
    ) {
        assertEquals("PluginV2HostApi", metadata["stage"])
        assertEquals("hostApi.fake", metadata["api"])
        assertEquals(PluginV2HostApiPermissions.NETWORK_REQUEST, metadata["permissionId"])
        assertEquals("conversation-1", metadata["conversationId"])
        assertEquals("app_chat", metadata["platformAdapterType"])
        assertEquals(requestId, metadata["requestId"])
        assertEquals(failureCode, metadata["failureCode"].orEmpty())
    }

    private fun requestContext(): PluginV2HostApiRequestContext {
        return PluginV2HostApiRequestContext(
            pluginId = "plugin.host-api-test",
            pluginVersion = "1.0.0",
            requestId = "req-audit",
            conversationId = "conversation-1",
            platformAdapterType = "app_chat",
            manifestPermissionIds = setOf(
                PluginV2HostApiPermissions.NETWORK_REQUEST,
                PluginV2HostApiPermissions.CALL_MODEL,
            ),
            permissionSnapshot = listOf(
                PluginPermissionGrant(
                    permissionId = PluginV2HostApiPermissions.NETWORK_REQUEST,
                    title = "Network access",
                    granted = true,
                    riskLevel = PluginRiskLevel.MEDIUM,
                ),
                PluginPermissionGrant(
                    permissionId = PluginV2HostApiPermissions.CALL_MODEL,
                    title = "Call model",
                    granted = true,
                    riskLevel = PluginRiskLevel.HIGH,
                ),
            ),
            triggerPermissionWhitelist = setOf(
                PluginV2HostApiPermissions.NETWORK_REQUEST,
                PluginV2HostApiPermissions.CALL_MODEL,
            ),
            triggerMetadata = PluginTriggerMetadata(eventId = "event-audit"),
        )
    }
}
