package com.elymbot.android.feature.plugin.runtime

import com.elymbot.android.model.plugin.PluginPermissionGrant
import com.elymbot.android.model.plugin.PluginRuntimeLogCategory
import com.elymbot.android.model.plugin.PluginRuntimeLogLevel
import com.elymbot.android.model.plugin.PluginRuntimeLogRecord
import com.elymbot.android.model.plugin.PluginTriggerMetadata
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

object PluginV2HostApiPermissions {
    const val NETWORK_REQUEST = "network_request"
    const val CALL_MODEL = "call_model"
    const val PROVIDER_READ = "provider_read"
    const val SEND_MESSAGE = "send_message"
    const val CONVERSATION_READ = "conversation_read"
    const val SCHEDULE_MANAGE = "schedule_manage"
    const val MESSAGE_STREAM = "message_stream"
    const val RICH_MESSAGE_SEND = "rich_message_send"
    const val CONTEXT_COMPRESS = "context_compress"
    const val AGENT_RUN = "agent_run"

    val WELL_KNOWN: Set<String> = setOf(
        NETWORK_REQUEST,
        CALL_MODEL,
        PROVIDER_READ,
        SEND_MESSAGE,
        CONVERSATION_READ,
        SCHEDULE_MANAGE,
        MESSAGE_STREAM,
        RICH_MESSAGE_SEND,
        CONTEXT_COMPRESS,
        AGENT_RUN,
    )
}

object PluginV2HostApiErrorCodes {
    const val PERMISSION_DENIED = "permission_denied"
    const val TIMEOUT = "timeout"
    const val INVALID_PAYLOAD = "invalid_payload"
    const val HOST_UNAVAILABLE = "host_unavailable"
    const val EXECUTION_FAILED = "execution_failed"
    const val CANCELLED = "cancelled"
}

data class PluginV2HostApiError(
    val code: String,
    val message: String,
    val details: Map<String, String> = emptyMap(),
)

class PluginV2HostApiException(
    val error: PluginV2HostApiError,
) : IllegalStateException(error.message)

data class PluginV2HostApiRequestContext(
    val pluginId: String,
    val pluginVersion: String = "",
    val requestId: String,
    val conversationId: String = "",
    val platformAdapterType: String = "",
    val manifestPermissionIds: Set<String> = emptySet(),
    val permissionSnapshot: List<PluginPermissionGrant> = emptyList(),
    val triggerPermissionWhitelist: Set<String> = emptySet(),
    val triggerMetadata: PluginTriggerMetadata = PluginTriggerMetadata(),
    val networkAllowedDomains: Set<String> = emptySet(),
)

sealed interface PluginV2HostApiResult {
    val requestId: String
    val api: String

    data class Success(
        override val requestId: String,
        override val api: String,
        val value: Any?,
    ) : PluginV2HostApiResult

    data class Failure(
        override val requestId: String,
        override val api: String,
        val error: PluginV2HostApiError,
    ) : PluginV2HostApiResult
}

data class PluginV2HostApiPermissionDecision(
    val allowed: Boolean,
    val error: PluginV2HostApiError? = null,
)

data class PluginV2HostApiRuntimePolicyDecision(
    val allowed: Boolean,
    val reason: String = "",
) {
    companion object {
        val Allowed = PluginV2HostApiRuntimePolicyDecision(allowed = true)
    }
}

fun interface PluginV2HostApiRuntimePolicy {
    fun evaluate(
        context: PluginV2HostApiRequestContext,
        api: String,
        permissionId: String,
    ): PluginV2HostApiRuntimePolicyDecision

    companion object {
        val AllowAll = PluginV2HostApiRuntimePolicy { _, _, _ ->
            PluginV2HostApiRuntimePolicyDecision.Allowed
        }
    }
}

class PluginV2HostApiPermissionPolicy(
    private val runtimePolicy: PluginV2HostApiRuntimePolicy = PluginV2HostApiRuntimePolicy.AllowAll,
) {
    fun evaluate(
        context: PluginV2HostApiRequestContext,
        api: String,
        permissionId: String,
    ): PluginV2HostApiPermissionDecision {
        val normalizedPermissionId = permissionId.trim()
        if (normalizedPermissionId.isBlank()) {
            return denied(
                api = api,
                permissionId = normalizedPermissionId,
                gate = "invalid_permission",
            )
        }
        if (normalizedPermissionId !in context.manifestPermissionIds) {
            return denied(
                api = api,
                permissionId = normalizedPermissionId,
                gate = "manifest_declaration",
            )
        }
        val granted = context.permissionSnapshot.any { permission ->
            permission.permissionId == normalizedPermissionId && permission.granted
        }
        if (!granted) {
            return denied(
                api = api,
                permissionId = normalizedPermissionId,
                gate = "user_grant",
            )
        }
        if (normalizedPermissionId !in context.triggerPermissionWhitelist) {
            return denied(
                api = api,
                permissionId = normalizedPermissionId,
                gate = "trigger_whitelist",
            )
        }
        val runtimeDecision = runtimePolicy.evaluate(
            context = context,
            api = api,
            permissionId = normalizedPermissionId,
        )
        if (!runtimeDecision.allowed) {
            return denied(
                api = api,
                permissionId = normalizedPermissionId,
                gate = "runtime_policy",
                reason = runtimeDecision.reason,
            )
        }
        return PluginV2HostApiPermissionDecision(allowed = true)
    }

    private fun denied(
        api: String,
        permissionId: String,
        gate: String,
        reason: String = "",
    ): PluginV2HostApiPermissionDecision {
        return PluginV2HostApiPermissionDecision(
            allowed = false,
            error = PluginV2HostApiError(
                code = PluginV2HostApiErrorCodes.PERMISSION_DENIED,
                message = "Permission denied for host API.",
                details = linkedMapOf(
                    "api" to api,
                    "permissionId" to permissionId,
                    "gate" to gate,
                ).also { details ->
                    if (reason.isNotBlank()) {
                        details["reason"] = reason
                    }
                },
            ),
        )
    }
}

class PluginV2HostApiAsyncBridge(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun await(
        context: PluginV2HostApiRequestContext,
        api: String,
        timeoutMs: Long,
        call: suspend () -> Any?,
    ): PluginV2HostApiResult {
        if (timeoutMs <= 0L) {
            return failure(
                context = context,
                api = api,
                code = PluginV2HostApiErrorCodes.INVALID_PAYLOAD,
                message = "Host API timeoutMs must be greater than zero.",
            )
        }
        return try {
            val value = withContext(dispatcher) {
                withTimeout(timeoutMs) {
                    call()
                }
            }
            PluginV2HostApiResult.Success(
                requestId = context.requestId,
                api = api,
                value = value,
            )
        } catch (error: TimeoutCancellationException) {
            failure(
                context = context,
                api = api,
                code = PluginV2HostApiErrorCodes.TIMEOUT,
                message = "Host API call timed out.",
            )
        } catch (error: PluginV2HostApiException) {
            PluginV2HostApiResult.Failure(
                requestId = context.requestId,
                api = api,
                error = error.error,
            )
        } catch (_: IllegalArgumentException) {
            failure(
                context = context,
                api = api,
                code = PluginV2HostApiErrorCodes.INVALID_PAYLOAD,
                message = "Host API payload is invalid.",
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            failure(
                context = context,
                api = api,
                code = PluginV2HostApiErrorCodes.EXECUTION_FAILED,
                message = "Host API execution failed.",
            )
        }
    }

    private fun failure(
        context: PluginV2HostApiRequestContext,
        api: String,
        code: String,
        message: String,
    ): PluginV2HostApiResult.Failure {
        return PluginV2HostApiResult.Failure(
            requestId = context.requestId,
            api = api,
            error = PluginV2HostApiError(
                code = code,
                message = message,
            ),
        )
    }
}

class PluginV2HostApiFacade(
    private val permissionPolicy: PluginV2HostApiPermissionPolicy,
    private val asyncBridge: PluginV2HostApiAsyncBridge,
    private val auditLogger: PluginV2HostApiAuditLogger,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun call(
        context: PluginV2HostApiRequestContext,
        api: String,
        permissionId: String,
        timeoutMs: Long,
        call: suspend () -> Any?,
    ): PluginV2HostApiResult {
        val startedAt = clock()
        val permissionDecision = permissionPolicy.evaluate(
            context = context,
            api = api,
            permissionId = permissionId,
        )
        if (!permissionDecision.allowed) {
            val result = PluginV2HostApiResult.Failure(
                requestId = context.requestId,
                api = api,
                error = checkNotNull(permissionDecision.error),
            )
            auditLogger.record(
                context = context,
                api = api,
                permissionId = permissionId,
                result = result,
                durationMs = durationSince(startedAt),
            )
            return result
        }
        val result = asyncBridge.await(
            context = context,
            api = api,
            timeoutMs = timeoutMs,
            call = call,
        )
        auditLogger.record(
            context = context,
            api = api,
            permissionId = permissionId,
            result = result,
            durationMs = durationSince(startedAt),
        )
        return result
    }

    private fun durationSince(startedAt: Long): Long = (clock() - startedAt).coerceAtLeast(0L)
}

class PluginV2HostApiAuditLogger(
    private val logBus: PluginRuntimeLogBus,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun record(
        context: PluginV2HostApiRequestContext,
        api: String,
        permissionId: String,
        result: PluginV2HostApiResult,
        durationMs: Long,
    ) {
        val failureCode = (result as? PluginV2HostApiResult.Failure)?.error?.code.orEmpty()
        val succeeded = result is PluginV2HostApiResult.Success
        logBus.publish(
            PluginRuntimeLogRecord(
                occurredAtEpochMillis = clock(),
                pluginId = context.pluginId,
                pluginVersion = context.pluginVersion,
                category = PluginRuntimeLogCategory.HostAction,
                level = resolveLevel(failureCode),
                code = if (succeeded) {
                    "plugin_v2_host_api_succeeded"
                } else {
                    "plugin_v2_host_api_failed"
                },
                message = if (succeeded) {
                    "Plugin V2 host API call succeeded."
                } else {
                    "Plugin V2 host API call failed."
                },
                succeeded = succeeded,
                durationMillis = durationMs,
                metadata = linkedMapOf(
                    "code" to if (succeeded) {
                        "plugin_v2_host_api_succeeded"
                    } else {
                        "plugin_v2_host_api_failed"
                    },
                    "stage" to "PluginV2HostApi",
                    "outcome" to if (succeeded) "SUCCEEDED" else "FAILED",
                    "api" to api,
                    "permissionId" to permissionId,
                    "conversationId" to context.conversationId,
                    "platformAdapterType" to context.platformAdapterType,
                    "requestId" to context.requestId,
                    "failureCode" to failureCode,
                    "triggerEventId" to context.triggerMetadata.eventId,
                ).filterValues { value -> value.isNotBlank() },
            ),
        )
    }

    private fun resolveLevel(failureCode: String): PluginRuntimeLogLevel {
        return when (failureCode) {
            "" -> PluginRuntimeLogLevel.Info
            PluginV2HostApiErrorCodes.EXECUTION_FAILED,
            PluginV2HostApiErrorCodes.HOST_UNAVAILABLE -> PluginRuntimeLogLevel.Error

            else -> PluginRuntimeLogLevel.Warning
        }
    }
}
