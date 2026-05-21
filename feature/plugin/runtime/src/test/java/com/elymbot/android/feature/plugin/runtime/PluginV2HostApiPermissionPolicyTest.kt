package com.elymbot.android.feature.plugin.runtime

import com.elymbot.android.model.plugin.PluginPermissionGrant
import com.elymbot.android.model.plugin.PluginRiskLevel
import com.elymbot.android.model.plugin.PluginTriggerMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginV2HostApiPermissionPolicyTest {

    private val policy = PluginV2HostApiPermissionPolicy()

    @Test
    fun manifest_undeclared_denies() {
        val decision = policy.evaluate(
            context = requestContext(
                manifestPermissionIds = emptySet(),
                grantedPermissionIds = setOf(PluginV2HostApiPermissions.NETWORK_REQUEST),
                triggerPermissionWhitelist = setOf(PluginV2HostApiPermissions.NETWORK_REQUEST),
            ),
            api = "hostApi.fetch",
            permissionId = PluginV2HostApiPermissions.NETWORK_REQUEST,
        )

        assertFalse(decision.allowed)
        assertEquals(PluginV2HostApiErrorCodes.PERMISSION_DENIED, decision.error?.code)
        assertEquals("manifest_declaration", decision.error?.details?.get("gate"))
    }

    @Test
    fun user_grant_missing_denies() {
        val decision = policy.evaluate(
            context = requestContext(
                manifestPermissionIds = setOf(PluginV2HostApiPermissions.NETWORK_REQUEST),
                grantedPermissionIds = emptySet(),
                triggerPermissionWhitelist = setOf(PluginV2HostApiPermissions.NETWORK_REQUEST),
            ),
            api = "hostApi.fetch",
            permissionId = PluginV2HostApiPermissions.NETWORK_REQUEST,
        )

        assertFalse(decision.allowed)
        assertEquals(PluginV2HostApiErrorCodes.PERMISSION_DENIED, decision.error?.code)
        assertEquals("user_grant", decision.error?.details?.get("gate"))
    }

    @Test
    fun trigger_whitelist_missing_denies() {
        val decision = policy.evaluate(
            context = requestContext(
                manifestPermissionIds = setOf(PluginV2HostApiPermissions.NETWORK_REQUEST),
                grantedPermissionIds = setOf(PluginV2HostApiPermissions.NETWORK_REQUEST),
                triggerPermissionWhitelist = emptySet(),
            ),
            api = "hostApi.fetch",
            permissionId = PluginV2HostApiPermissions.NETWORK_REQUEST,
        )

        assertFalse(decision.allowed)
        assertEquals(PluginV2HostApiErrorCodes.PERMISSION_DENIED, decision.error?.code)
        assertEquals("trigger_whitelist", decision.error?.details?.get("gate"))
    }

    @Test
    fun all_gates_satisfied_allows() {
        val decision = policy.evaluate(
            context = requestContext(
                manifestPermissionIds = setOf(PluginV2HostApiPermissions.NETWORK_REQUEST),
                grantedPermissionIds = setOf(PluginV2HostApiPermissions.NETWORK_REQUEST),
                triggerPermissionWhitelist = setOf(PluginV2HostApiPermissions.NETWORK_REQUEST),
            ),
            api = "hostApi.fetch",
            permissionId = PluginV2HostApiPermissions.NETWORK_REQUEST,
        )

        assertTrue(decision.allowed)
        assertEquals(null, decision.error)
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
