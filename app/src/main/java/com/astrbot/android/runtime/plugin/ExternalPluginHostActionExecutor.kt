package com.astrbot.android.runtime.plugin

import com.astrbot.android.model.plugin.ExternalPluginHostActionPolicy
import com.astrbot.android.model.plugin.HostActionRequest
import com.astrbot.android.model.plugin.PluginExecutionContext
import com.astrbot.android.model.plugin.PluginHostAction

data class ExternalPluginHostActionExecutionResult(
    val action: PluginHostAction,
    val succeeded: Boolean,
    val code: String = "",
    val message: String = "",
    val failureSnapshot: PluginFailureSnapshot,
)

class ExternalPluginHostActionExecutor(
    private val failureGuard: PluginFailureGuard = PluginFailureGuard(
        store = PluginRuntimeFailureStateStoreProvider.store(),
    ),
    private val sendMessageHandler: (String) -> Unit = {},
    private val sendNotificationHandler: (String, String) -> Unit = { _, _ -> },
    private val openHostPageHandler: (String) -> Unit = {},
) {
    fun execute(
        pluginId: String,
        request: HostActionRequest,
        context: PluginExecutionContext,
    ): ExternalPluginHostActionExecutionResult {
        val suspendedSnapshot = failureGuard.snapshot(pluginId)
        if (suspendedSnapshot.isSuspended) {
            return ExternalPluginHostActionExecutionResult(
                action = request.action,
                succeeded = false,
                code = "plugin_suspended",
                message = "Plugin is suspended because recent host actions failed.",
                failureSnapshot = suspendedSnapshot,
            )
        }
        return runCatching {
            validate(request = request, context = context)
            val message = perform(request)
            ExternalPluginHostActionExecutionResult(
                action = request.action,
                succeeded = true,
                message = message,
                failureSnapshot = failureGuard.recordSuccess(pluginId),
            )
        }.getOrElse { error ->
            val message = error.message ?: "Host action execution failed."
            val failureSnapshot = failureGuard.recordFailure(
                pluginId = pluginId,
                errorSummary = message,
            )
            ExternalPluginHostActionExecutionResult(
                action = request.action,
                succeeded = false,
                code = failureCode(request.action, message),
                message = message,
                failureSnapshot = failureSnapshot,
            )
        }
    }

    private fun validate(
        request: HostActionRequest,
        context: PluginExecutionContext,
    ) {
        require(ExternalPluginHostActionPolicy.isOpen(request.action)) {
            "Host action ${request.action.name} is not open for v1."
        }
        require(request.action in context.hostActionWhitelist) {
            "Host action ${request.action.name} is not in the trigger whitelist."
        }
        ExternalPluginHostActionPolicy.requiredPermissionId(request.action)?.let { permissionId ->
            val granted = context.permissionSnapshot.any { permission ->
                permission.permissionId == permissionId && permission.granted
            }
            require(granted) {
                "Host action ${request.action.name} requires granted permission: $permissionId"
            }
        }
    }

    private fun perform(request: HostActionRequest): String {
        return when (request.action) {
            PluginHostAction.SendMessage -> {
                val text = request.payload["text"].orEmpty().trim()
                require(text.isNotBlank()) { "Host action SendMessage requires payload.text" }
                sendMessageHandler(text)
                text
            }

            PluginHostAction.SendNotification -> {
                val message = request.payload["message"]
                    ?.takeIf { it.isNotBlank() }
                    ?: request.payload["text"]
                    ?.takeIf { it.isNotBlank() }
                    ?: throw IllegalArgumentException("Host action SendNotification requires payload.message")
                val title = request.payload["title"].orEmpty().ifBlank { "AstrBot" }
                sendNotificationHandler(title, message)
                "$title: $message"
            }

            PluginHostAction.OpenHostPage -> {
                val route = request.payload["route"].orEmpty().trim()
                require(route.isNotBlank()) { "Host action OpenHostPage requires payload.route" }
                openHostPageHandler(route)
                route
            }

            else -> throw IllegalArgumentException("Host action ${request.action.name} is not open for v1.")
        }
    }

    private fun failureCode(
        action: PluginHostAction,
        message: String,
    ): String {
        return when {
            message.contains("not open for v1", ignoreCase = true) -> "host_action_not_open"
            message.contains("whitelist", ignoreCase = true) -> "host_action_not_whitelisted"
            message.contains("permission", ignoreCase = true) -> "host_action_permission_denied"
            message.contains("payload.", ignoreCase = true) -> "host_action_invalid_payload"
            else -> "host_action_failed"
        }
    }
}
