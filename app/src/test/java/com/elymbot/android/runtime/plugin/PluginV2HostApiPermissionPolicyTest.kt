package com.elymbot.android.feature.plugin.runtime

import com.elymbot.android.model.plugin.PluginPermissionGrant
import com.elymbot.android.model.plugin.PluginRiskLevel
import com.elymbot.android.model.plugin.PluginTriggerMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginV2HostApiPermissionPolicyTest {

    @Test
    fun well_known_permission_ids_cover_t01a_host_api_surface() {
        assertEquals(
            setOf(
                "network_request",
                "call_model",
                "provider_read",
                "send_message",
                "conversation_read",
                "schedule_manage",
                "message_stream",
                "rich_message_send",
                "context_compress",
                "agent_run",
            ),
            PluginV2HostApiPermissions.WELL_KNOWN,
        )
    }

    @Test
    fun manifest_undeclared_denies_before_other_gates() {
        val decision = allowAllPolicy().evaluate(
            context = requestContext(
                manifestPermissionIds = emptySet(),
                grantedPermissionIds = setOf(PluginV2HostApiPermissions.NETWORK_REQUEST),
                triggerPermissionWhitelist = setOf(PluginV2HostApiPermissions.NETWORK_REQUEST),
            ),
            api = "hostApi.fetch",
            permissionId = PluginV2HostApiPermissions.NETWORK_REQUEST,
        )

        assertDeniedAt(decision, "manifest_declaration")
    }

    @Test
    fun user_grant_missing_denies_after_manifest_gate() {
        val decision = allowAllPolicy().evaluate(
            context = requestContext(
                manifestPermissionIds = setOf(PluginV2HostApiPermissions.NETWORK_REQUEST),
                grantedPermissionIds = emptySet(),
                triggerPermissionWhitelist = setOf(PluginV2HostApiPermissions.NETWORK_REQUEST),
            ),
            api = "hostApi.fetch",
            permissionId = PluginV2HostApiPermissions.NETWORK_REQUEST,
        )

        assertDeniedAt(decision, "user_grant")
    }

    @Test
    fun trigger_whitelist_missing_denies_after_user_grant_gate() {
        val decision = allowAllPolicy().evaluate(
            context = requestContext(
                manifestPermissionIds = setOf(PluginV2HostApiPermissions.NETWORK_REQUEST),
                grantedPermissionIds = setOf(PluginV2HostApiPermissions.NETWORK_REQUEST),
                triggerPermissionWhitelist = emptySet(),
            ),
            api = "hostApi.fetch",
            permissionId = PluginV2HostApiPermissions.NETWORK_REQUEST,
        )

        assertDeniedAt(decision, "trigger_whitelist")
    }

    @Test
    fun runtime_policy_denies_after_static_gates() {
        val policy = PluginV2HostApiPermissionPolicy(
            runtimePolicy = PluginV2HostApiRuntimePolicy { _, _, _ ->
                PluginV2HostApiRuntimePolicyDecision(
                    allowed = false,
                    reason = "rate_limited",
                )
            },
        )

        val decision = policy.evaluate(
            context = requestContext(
                manifestPermissionIds = setOf(PluginV2HostApiPermissions.NETWORK_REQUEST),
                grantedPermissionIds = setOf(PluginV2HostApiPermissions.NETWORK_REQUEST),
                triggerPermissionWhitelist = setOf(PluginV2HostApiPermissions.NETWORK_REQUEST),
            ),
            api = "hostApi.fetch",
            permissionId = PluginV2HostApiPermissions.NETWORK_REQUEST,
        )

        assertDeniedAt(decision, "runtime_policy")
        assertEquals("rate_limited", decision.error?.details?.get("reason"))
    }

    @Test
    fun all_gates_satisfied_allows() {
        val decision = allowAllPolicy().evaluate(
            context = requestContext(
                manifestPermissionIds = setOf(PluginV2HostApiPermissions.NETWORK_REQUEST),
                grantedPermissionIds = setOf(PluginV2HostApiPermissions.NETWORK_REQUEST),
                triggerPermissionWhitelist = setOf(PluginV2HostApiPermissions.NETWORK_REQUEST),
            ),
            api = "hostApi.fetch",
            permissionId = PluginV2HostApiPermissions.NETWORK_REQUEST,
        )

        assertTrue(decision.allowed)
        assertNull(decision.error)
    }

    private fun allowAllPolicy(): PluginV2HostApiPermissionPolicy = PluginV2HostApiPermissionPolicy()

    private fun assertDeniedAt(
        decision: PluginV2HostApiPermissionDecision,
        gate: String,
    ) {
        assertFalse(decision.allowed)
        assertEquals(PluginV2HostApiErrorCodes.PERMISSION_DENIED, decision.error?.code)
        assertEquals(gate, decision.error?.details?.get("gate"))
    }

    private fun requestContext(
        manifestPermissionIds: Set<String>,
        grantedPermissionIds: Set<String>,
        triggerPermissionWhitelist: Set<String>,
    ): PluginV2HostApiRequestContext {
        return PluginV2HostApiRequestContext(
            pluginId = "plugin.host-api-test",
            pluginVersion = "1.0.0",
            requestId = "req-permission",
            conversationId = "conversation-1",
            platformAdapterType = "app_chat",
            manifestPermissionIds = manifestPermissionIds,
            permissionSnapshot = grantedPermissionIds.map { permissionId ->
                PluginPermissionGrant(
                    permissionId = permissionId,
                    title = permissionId,
                    granted = true,
                    riskLevel = PluginRiskLevel.MEDIUM,
                )
            },
            triggerPermissionWhitelist = triggerPermissionWhitelist,
            triggerMetadata = PluginTriggerMetadata(eventId = "event-1"),
        )
    }
}
