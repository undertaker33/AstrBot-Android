package com.elymbot.android.feature.plugin.runtime

import com.elymbot.android.model.chat.MessageType
import com.elymbot.android.model.plugin.PluginRuntimeLogCategory
import com.elymbot.android.model.plugin.PluginRuntimeLogLevel
import com.elymbot.android.model.plugin.PluginRuntimeLogRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

private const val CUSTOM_FILTER_TIMEOUT_MS: Long = 2_000L
private const val DEFAULT_CUSTOM_FILTER_FAILURE_MESSAGE = "Plugin filter failed. Please try again later."

sealed interface PluginV2FilterEvaluationResult {
    data object Pass : PluginV2FilterEvaluationResult

    data class Reject(
        val reasonCode: String,
        val astPath: String = "$",
    ) : PluginV2FilterEvaluationResult

    data class ErrorStop(
        val logCode: String,
        val userVisibleMessage: String,
    ) : PluginV2FilterEvaluationResult
}

data class PluginV2CustomFilterRequest(
    val eventView: PluginV2CustomFilterEventView,
    val pluginContextView: PluginV2CustomFilterPluginContextView,
    val filterArgs: Map<String, String>,
)

data class PluginV2CustomFilterEventView(
    val stage: String,
    val eventId: String,
    val platformAdapterType: String,
    val messageType: String,
    val conversationId: String,
    val senderId: String,
    val workingText: String,
    val extrasSnapshot: Map<String, AllowedValue>,
    val commandPath: List<String> = emptyList(),
    val matchedAlias: String = "",
    val patternKey: String = "",
    val matchedText: String = "",
)

data class PluginV2CustomFilterPluginContextView(
    val pluginId: String,
    val pluginVersion: String,
    val runtimeKind: String,
    val runtimeApiVersion: Int,
    val declaredPermissionIds: List<String>,
    val grantedPermissionIds: List<String>,
    val sourceType: String,
)

interface PluginV2EventAwareCallbackHandle : PluginV2CallbackHandle {
    suspend fun handleEvent(event: PluginErrorEventPayload)
}

interface PluginV2CustomFilterAwareCallbackHandle : PluginV2CallbackHandle {
    suspend fun evaluateCustomFilter(request: PluginV2CustomFilterRequest): Boolean
}

class PluginV2FilterEvaluator(
    private val logBus: PluginRuntimeLogBus = InMemoryPluginRuntimeLogBus(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun evaluate(
        session: PluginV2RuntimeSession,
        descriptor: PluginV2CompiledHandlerDescriptor,
        event: PluginErrorEventPayload,
    ): PluginV2FilterEvaluationResult {
        return when (val result = evaluateFilterExpression(session, descriptor, event, descriptor.filterExpression, "$")) {
            is InternalFilterResult.Pass -> PluginV2FilterEvaluationResult.Pass
            is InternalFilterResult.Reject -> {
                publishFilterRejected(
                    session = session,
                    descriptor = descriptor,
                    reasonCode = result.reasonCode,
                    astPath = result.astPath,
                    event = event,
                )
                PluginV2FilterEvaluationResult.Reject(
                    reasonCode = result.reasonCode,
                    astPath = result.astPath,
                )
            }

            is InternalFilterResult.ErrorStop -> result.stop
        }
    }

    private suspend fun evaluateFilterExpression(
        session: PluginV2RuntimeSession,
        descriptor: PluginV2CompiledHandlerDescriptor,
        event: PluginErrorEventPayload,
        expression: PluginV2CompiledFilterExpression,
        path: String,
    ): InternalFilterResult {
        return when (expression) {
            is PluginV2CompiledFilterExpression.AllOf -> {
                expression.children.forEachIndexed { index, child ->
                    when (val result = evaluateFilterExpression(session, descriptor, event, child, "$path.allOf[$index]")) {
                        is InternalFilterResult.Pass -> Unit
                        is InternalFilterResult.Reject -> return result
                        is InternalFilterResult.ErrorStop -> return result
                    }
                }
                InternalFilterResult.Pass
            }

            is PluginV2CompiledFilterExpression.AnyOf -> {
                var lastReject: InternalFilterResult.Reject? = null
                expression.children.forEachIndexed { index, child ->
                    when (val result = evaluateFilterExpression(session, descriptor, event, child, "$path.anyOf[$index]")) {
                        is InternalFilterResult.Pass -> return InternalFilterResult.Pass
                        is InternalFilterResult.Reject -> lastReject = result
                        is InternalFilterResult.ErrorStop -> return result
                    }
                }
                lastReject ?: InternalFilterResult.Reject(
                    reasonCode = "any_of",
                    astPath = "$path.anyOf",
                )
            }

            is PluginV2CompiledFilterExpression.Not -> {
                when (val result = evaluateFilterExpression(session, descriptor, event, expression.child, "$path.not")) {
                    is InternalFilterResult.Pass -> InternalFilterResult.Reject(
                        reasonCode = "not",
                        astPath = "$path.not",
                    )

                    is InternalFilterResult.Reject -> InternalFilterResult.Pass
                    is InternalFilterResult.ErrorStop -> result
                }
            }

            is PluginV2CompiledFilterExpression.Builtin -> {
                val passed = when (expression.kind) {
                    PluginV2BuiltinFilterKind.EventMessageType -> matchesMessageType(event, expression.value)
                    PluginV2BuiltinFilterKind.PlatformAdapterType -> matchesPlatformAdapterType(event, expression.value)
                    PluginV2BuiltinFilterKind.PermissionType -> matchesPermission(session, expression.value)
                }
                if (passed) {
                    InternalFilterResult.Pass
                } else {
                    InternalFilterResult.Reject(
                        reasonCode = expression.kind.reasonCode,
                        astPath = path,
                    )
                }
            }

            is PluginV2CompiledFilterExpression.Custom -> evaluateCustomFilter(
                session = session,
                descriptor = descriptor,
                event = event,
                expression = expression,
                path = path,
            )
        }
    }

    private suspend fun evaluateCustomFilter(
        session: PluginV2RuntimeSession,
        descriptor: PluginV2CompiledHandlerDescriptor,
        event: PluginErrorEventPayload,
        expression: PluginV2CompiledFilterExpression.Custom,
        path: String,
    ): InternalFilterResult {
        val callbackHandle = session.requireCallbackHandle(descriptor.callbackToken)
        if (callbackHandle !is PluginV2CustomFilterAwareCallbackHandle) {
            return InternalFilterResult.ErrorStop(
                publishCustomFilterStop(
                    session = session,
                    descriptor = descriptor,
                    event = event,
                    code = "custom_filter_failed",
                ),
            )
        }

        val passed = try {
            withTimeout(CUSTOM_FILTER_TIMEOUT_MS) {
                session.runSerializedCallback {
                    callbackHandle.evaluateCustomFilter(
                        PluginV2CustomFilterRequest(
                            eventView = event.toCustomFilterEventView(),
                            pluginContextView = session.toPluginContextView(),
                            filterArgs = expression.arguments,
                        ),
                    )
                }
            }
        } catch (_: TimeoutCancellationException) {
            return InternalFilterResult.ErrorStop(
                publishCustomFilterStop(
                    session = session,
                    descriptor = descriptor,
                    event = event,
                    code = "custom_filter_timeout",
                ),
            )
        } catch (error: Throwable) {
            error.rethrowIfCancellation()
            return InternalFilterResult.ErrorStop(
                publishCustomFilterStop(
                    session = session,
                    descriptor = descriptor,
                    event = event,
                    code = "custom_filter_failed",
                ),
            )
        }

        return if (passed) {
            InternalFilterResult.Pass
        } else {
            InternalFilterResult.Reject(
                reasonCode = "custom_filter",
                astPath = path,
            )
        }
    }

    private fun publishFilterRejected(
        session: PluginV2RuntimeSession,
        descriptor: PluginV2CompiledHandlerDescriptor,
        reasonCode: String,
        astPath: String,
        event: PluginErrorEventPayload,
    ) {
        logBus.publish(
            PluginRuntimeLogRecord(
                occurredAtEpochMillis = clock(),
                pluginId = session.pluginId,
                pluginVersion = session.installRecord.installedVersion,
                category = PluginRuntimeLogCategory.Dispatcher,
                level = PluginRuntimeLogLevel.Info,
                code = "filter_rejected",
                message = "Plugin v2 handler rejected by filter.",
                succeeded = true,
                metadata = linkedMapOf(
                    "sessionInstanceId" to session.sessionInstanceId,
                    "handlerId" to descriptor.handlerId,
                    "stage" to event.stageName(),
                    "reasonCode" to reasonCode,
                    "filterAstPath" to astPath,
                    "traceId" to buildTraceId(session, descriptor, event),
                ),
            ),
        )
    }

    private fun publishCustomFilterStop(
        session: PluginV2RuntimeSession,
        descriptor: PluginV2CompiledHandlerDescriptor,
        event: PluginErrorEventPayload,
        code: String,
    ): PluginV2FilterEvaluationResult.ErrorStop {
        logBus.publish(
            PluginRuntimeLogRecord(
                occurredAtEpochMillis = clock(),
                pluginId = session.pluginId,
                pluginVersion = session.installRecord.installedVersion,
                category = PluginRuntimeLogCategory.Dispatcher,
                level = PluginRuntimeLogLevel.Error,
                code = code,
                message = "Plugin v2 custom filter failed.",
                succeeded = false,
                metadata = linkedMapOf(
                    "sessionInstanceId" to session.sessionInstanceId,
                    "handlerId" to descriptor.handlerId,
                    "stage" to event.stageName(),
                    "traceId" to buildTraceId(session, descriptor, event),
                ),
            ),
        )
        return PluginV2FilterEvaluationResult.ErrorStop(
            logCode = code,
            userVisibleMessage = DEFAULT_CUSTOM_FILTER_FAILURE_MESSAGE,
        )
    }

    private fun matchesMessageType(
        event: PluginErrorEventPayload,
        expected: String,
    ): Boolean {
        val actual = when (event) {
            is PluginMessageEvent -> event.messageType
            is PluginCommandEvent -> event.messageType
            is PluginRegexEvent -> event.messageType
            else -> return false
        }
        return actual.wireValue.equals(expected, ignoreCase = true) ||
            actual.name.equals(expected, ignoreCase = true)
    }

    private fun matchesPlatformAdapterType(
        event: PluginErrorEventPayload,
        expected: String,
    ): Boolean {
        val actual = when (event) {
            is PluginMessageEvent -> event.platformAdapterType
            is PluginCommandEvent -> event.platformAdapterType
            is PluginRegexEvent -> event.platformAdapterType
            else -> return false
        }
        return actual.equals(expected, ignoreCase = true)
    }

    private fun matchesPermission(
        session: PluginV2RuntimeSession,
        expectedPermissionId: String,
    ): Boolean {
        return session.installRecord.permissionSnapshot.any { permission ->
            permission.permissionId == expectedPermissionId
        }
    }

    private fun PluginErrorEventPayload.toCustomFilterEventView(): PluginV2CustomFilterEventView {
        return when (this) {
            is PluginMessageEvent -> PluginV2CustomFilterEventView(
                stage = stage.name,
                eventId = eventId,
                platformAdapterType = platformAdapterType,
                messageType = messageType.wireValue,
                conversationId = conversationId,
                senderId = senderId,
                workingText = workingText,
                extrasSnapshot = PluginV2ValueSanitizer.requireAllowedMap(extras),
            )

            is PluginCommandEvent -> PluginV2CustomFilterEventView(
                stage = stage.name,
                eventId = eventId,
                platformAdapterType = platformAdapterType,
                messageType = messageType.wireValue,
                conversationId = conversationId,
                senderId = senderId,
                workingText = workingText,
                extrasSnapshot = PluginV2ValueSanitizer.requireAllowedMap(extras),
                commandPath = commandPath.toList(),
                matchedAlias = matchedAlias,
            )

            is PluginRegexEvent -> PluginV2CustomFilterEventView(
                stage = stage.name,
                eventId = eventId,
                platformAdapterType = platformAdapterType,
                messageType = messageType.wireValue,
                conversationId = conversationId,
                senderId = senderId,
                workingText = workingText,
                extrasSnapshot = PluginV2ValueSanitizer.requireAllowedMap(extras),
                patternKey = patternKey,
                matchedText = matchedText,
            )

            else -> PluginV2CustomFilterEventView(
                stage = "Unknown",
                eventId = "",
                platformAdapterType = "",
                messageType = MessageType.OtherMessage.wireValue,
                conversationId = "",
                senderId = "",
                workingText = "",
                extrasSnapshot = emptyMap(),
            )
        }
    }

    private fun PluginV2RuntimeSession.toPluginContextView(): PluginV2CustomFilterPluginContextView {
        return PluginV2CustomFilterPluginContextView(
            pluginId = pluginId,
            pluginVersion = installRecord.installedVersion,
            runtimeKind = packageContractSnapshot.runtime.kind,
            runtimeApiVersion = packageContractSnapshot.runtime.apiVersion,
            declaredPermissionIds = installRecord.manifestSnapshot.permissions.map { it.permissionId },
            grantedPermissionIds = installRecord.permissionSnapshot.map { it.permissionId },
            sourceType = installRecord.source.sourceType.name,
        )
    }

    private fun PluginErrorEventPayload.stageName(): String {
        return when (this) {
            is PluginMessageEvent -> stage.name
            is PluginCommandEvent -> stage.name
            is PluginRegexEvent -> stage.name
            else -> "Unknown"
        }
    }

    private fun buildTraceId(
        session: PluginV2RuntimeSession,
        descriptor: PluginV2CompiledHandlerDescriptor,
        event: PluginErrorEventPayload,
    ): String {
        return "trace::${session.pluginId}::${event.stageName()}::${descriptor.handlerId}"
    }

    private fun Throwable.rethrowIfCancellation() {
        if (this is CancellationException) {
            throw this
        }
    }

    private sealed interface InternalFilterResult {
        data object Pass : InternalFilterResult

        data class Reject(
            val reasonCode: String,
            val astPath: String,
        ) : InternalFilterResult

        data class ErrorStop(
            val stop: PluginV2FilterEvaluationResult.ErrorStop,
        ) : InternalFilterResult
    }
}
