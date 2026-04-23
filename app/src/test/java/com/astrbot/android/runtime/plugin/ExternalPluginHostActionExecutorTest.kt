package com.astrbot.android.feature.plugin.runtime

import com.astrbot.android.model.chat.MessageSessionRef
import com.astrbot.android.model.chat.MessageType
import com.astrbot.android.model.plugin.HostActionRequest
import com.astrbot.android.model.plugin.PluginBotSummary
import com.astrbot.android.model.plugin.PluginConfigSummary
import com.astrbot.android.model.plugin.PluginExecutionContext
import com.astrbot.android.model.plugin.PluginHostAction
import com.astrbot.android.model.plugin.PluginMessageSummary
import com.astrbot.android.model.plugin.PluginPermissionGrant
import com.astrbot.android.model.plugin.PluginRiskLevel
import com.astrbot.android.model.plugin.PluginTriggerMetadata
import com.astrbot.android.model.plugin.PluginTriggerSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalPluginHostActionExecutorTest {

    @Test
    fun executor_executes_send_message_when_action_is_open_whitelisted_and_granted() {
        var sentMessage = ""
        val executor = testExternalPluginHostActionExecutor(
            failureGuard = testPluginFailureGuard(store = InMemoryPluginFailureStateStore()),
        )

        val result = executor.execute(
            pluginId = "plugin.message",
            request = HostActionRequest(
                action = PluginHostAction.SendMessage,
                payload = mapOf("text" to "hello from host action"),
            ),
            context = context(
                whitelist = listOf(PluginHostAction.SendMessage),
                permissions = listOf(
                    permission(
                        permissionId = "send_message",
                        granted = true,
                    ),
                ),
            ),
            handlers = ExternalPluginHostActionHandlers(
                sendMessage = { text -> sentMessage = text },
            ),
        )

        assertTrue(result.succeeded)
        assertEquals("hello from host action", sentMessage)
        assertEquals(0, result.failureSnapshot.consecutiveFailureCount)
    }

    @Test
    fun executor_rejects_action_when_it_is_not_open_for_v1() {
        val executor = testExternalPluginHostActionExecutor(
            failureGuard = testPluginFailureGuard(store = InMemoryPluginFailureStateStore()),
        )

        val result = executor.execute(
            pluginId = "plugin.network",
            request = HostActionRequest(
                action = PluginHostAction.NetworkRequest,
                payload = mapOf("url" to "https://example.com"),
            ),
            context = context(
                whitelist = listOf(PluginHostAction.NetworkRequest),
                permissions = emptyList(),
            ),
        )

        assertTrue(!result.succeeded)
        assertEquals("host_action_not_open", result.code)
        assertTrue(result.message.contains("NetworkRequest"))
    }

    @Test
    fun executor_suspends_plugin_after_repeated_permission_failures() {
        val failureGuard = testPluginFailureGuard(
            store = InMemoryPluginFailureStateStore(),
        )
        val executor = testExternalPluginHostActionExecutor(
            failureGuard = failureGuard,
        )

        repeat(3) {
            executor.execute(
                pluginId = "plugin.denied",
                request = HostActionRequest(
                    action = PluginHostAction.SendNotification,
                    payload = mapOf("message" to "notify"),
                ),
                context = context(
                    whitelist = listOf(PluginHostAction.SendNotification),
                    permissions = listOf(
                        permission(
                            permissionId = "send_notification",
                            granted = false,
                        ),
                    ),
                ),
            )
        }

        val suspended = executor.execute(
            pluginId = "plugin.denied",
            request = HostActionRequest(
                action = PluginHostAction.SendNotification,
                payload = mapOf("message" to "notify"),
            ),
            context = context(
                whitelist = listOf(PluginHostAction.SendNotification),
                permissions = listOf(
                    permission(
                        permissionId = "send_notification",
                        granted = false,
                    ),
                ),
            ),
        )

        assertTrue(suspended.failureSnapshot.isSuspended)
        assertEquals("plugin_suspended", suspended.code)
    }

    @Test
    fun executor_explicitly_rejects_v2_tool_executor_usage() = runBlocking {
        val executor = testExternalPluginHostActionExecutor(
            failureGuard = testPluginFailureGuard(store = InMemoryPluginFailureStateStore()),
        )

        val result = executor.asV2ToolExecutor().execute(
            PluginToolArgs(
                toolCallId = "tool-call-legacy-guard",
                requestId = "request-legacy-guard",
                toolId = "__host_builtin__:send_message",
                payload = linkedMapOf("text" to "hello"),
            ),
        )

        assertEquals(PluginToolResultStatus.ERROR, result.status)
        assertEquals("legacy_host_action_only", result.errorCode)
        assertTrue(result.text.orEmpty().contains("legacy/internal"))
    }

    private fun context(
        whitelist: List<PluginHostAction>,
        permissions: List<PluginPermissionGrant>,
    ): PluginExecutionContext {
        return PluginExecutionContext(
            trigger = PluginTriggerSource.OnCommand,
            pluginId = "plugin.test",
            pluginVersion = "1.0.0",
            sessionRef = MessageSessionRef(
                platformId = "host",
                messageType = MessageType.OtherMessage,
                originSessionId = "session",
            ),
            message = PluginMessageSummary(
                messageId = "message",
                contentPreview = "/plugin",
            ),
            bot = PluginBotSummary(
                botId = "host",
                displayName = "AstrBot Host",
                platformId = "host",
            ),
            config = PluginConfigSummary(),
            permissionSnapshot = permissions,
            hostActionWhitelist = whitelist,
            triggerMetadata = PluginTriggerMetadata(
                command = "/plugin",
            ),
        )
    }

    private fun permission(
        permissionId: String,
        granted: Boolean,
    ): PluginPermissionGrant {
        return PluginPermissionGrant(
            permissionId = permissionId,
            title = permissionId,
            granted = granted,
            riskLevel = PluginRiskLevel.LOW,
        )
    }
}
