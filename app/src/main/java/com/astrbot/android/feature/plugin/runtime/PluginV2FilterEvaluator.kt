package com.astrbot.android.feature.plugin.runtime

import com.astrbot.android.model.chat.MessageType
import com.astrbot.android.model.plugin.PluginRuntimeLogCategory
import com.astrbot.android.model.plugin.PluginRuntimeLogLevel
import com.astrbot.android.model.plugin.PluginRuntimeLogRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

private const val CUSTOM_FILTER_TIMEOUT_MS: Long = 2_000L
private const val DEFAULT_CUSTOM_FILTER_FAILURE_MESSAGE = "Plugin filter failed. Please try again later."

sealed interface PluginV2FilterEvaluationResult {
    data object Pass : PluginV2FilterEvaluationResult

    data class Reject(
        val reasonCode: String,
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
    private val logBus: PluginRuntimeLogBus = PluginRuntimeLogBusProvider.bus(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun evaluate(
        session: PluginV2RuntimeSession,
        descriptor: PluginV2CompiledHandlerDescriptor,
        event: PluginErrorEventPayload,
    ): PluginV2FilterEvaluationResult {
        val filters = descriptor.filterAttachments.mapNotNull(::parseFilter)
        val builtInFilters = filters.filter { it.kind != ParsedFilterKind.CustomFilter }
            .sortedBy { it.kind.ordinal }
        val customFilters = filters.filter { it.kind == ParsedFilterKind.CustomFilter }

        builtInFilters.forEach { filter ->
            val passed = when (filter.kind) {
                ParsedFilterKind.EventMessageType -> matchesMessageType(event, filter.value)
                ParsedFilterKind.PlatformAdapterType -> matchesPlatformAdapterType(event, filter.value)
                ParsedFilterKind.PermissionType -> matchesPermission(session, filter.value)
                ParsedFilterKind.CustomFilter -> true
            }
            if (!passed) {
                publishFilterRejected(
                    session = session,
                    descriptor = descriptor,
                    reasonCode = filter.kind.reasonCode,
                    event = event,
                )
                return PluginV2FilterEvaluationResult.Reject(filter.kind.reasonCode)
            }
        }

        val callbackHandle = session.requireCallbackHandle(descriptor.callbackToken)
        for (filter in customFilters) {
            if (callbackHandle !is PluginV2CustomFilterAwareCallbackHandle) {
                return publishCustomFilterStop(
                    session = session,
                    descriptor = descriptor,
                    event = event,
                    code = "custom_filter_failed",
                )
            }

            val passed = try {
                withTimeout(CUSTOM_FILTER_TIMEOUT_MS) {
                    session.runSerializedCallback {
                        callbackHandle.evaluateCustomFilter(
                            PluginV2CustomFilterRequest(
                                eventView = event.toCustomFilterEventView(),
                                pluginContextView = session.toPluginContextView(),
                                filterArgs = filter.arguments,
                            ),
                        )
                    }
                }
            } catch (_: TimeoutCancellationException) {
                return publishCustomFilterStop(
                    session = session,
                    descriptor = descriptor,
                    event = event,
                    code = "custom_filter_timeout",
                )
            } catch (error: Throwable) {
                error.rethrowIfCancellation()
                return publishCustomFilterStop(
                    session = session,
                    descriptor = descriptor,
                    event = event,
                    code = "custom_filter_failed",
                )
            }

            if (!passed) {
                publishFilterRejected(
                    session = session,
                    descriptor = descriptor,
                    reasonCode = "custom_filter",
                    event = event,
                )
                return PluginV2FilterEvaluationResult.Reject("custom_filter")
            }
        }

        return PluginV2FilterEvaluationResult.Pass
    }

    private fun publishFilterRejected(
        session: PluginV2RuntimeSession,
        descriptor: PluginV2CompiledHandlerDescriptor,
        reasonCode: String,
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

    private fun parseFilter(
        attachment: PluginV2CompiledFilterAttachment,
    ): ParsedFilter? {
        val rawValue = attachment.arguments["value"]?.trim().orEmpty()
        if (rawValue.isBlank()) {
            return null
        }

        val prefixed = PREFIX_TO_FILTER_KIND.entries.firstOrNull { (prefix, _) ->
            rawValue.startsWith(prefix)
        }?.let { (prefix, kind) ->
            ParsedFilter(
                kind = kind,
                value = rawValue.removePrefix(prefix).trim(),
                arguments = attachment.arguments,
            )
        }
        if (prefixed != null) {
            return prefixed
        }

        return when (attachment.kind) {
            BootstrapFilterKind.Message -> ParsedFilter(
                kind = ParsedFilterKind.EventMessageType,
                value = rawValue,
                arguments = attachment.arguments,
            )

            BootstrapFilterKind.Command -> ParsedFilter(
                kind = ParsedFilterKind.PlatformAdapterType,
                value = rawValue,
                arguments = attachment.arguments,
            )

            BootstrapFilterKind.Regex -> ParsedFilter(
                kind = ParsedFilterKind.PermissionType,
                value = rawValue,
                arguments = attachment.arguments,
            )
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

    private data class ParsedFilter(
        val kind: ParsedFilterKind,
        val value: String,
        val arguments: Map<String, String>,
    )

    private enum class ParsedFilterKind(
        val reasonCode: String,
    ) {
        EventMessageType("event_message_type"),
        PlatformAdapterType("platform_adapter_type"),
        PermissionType("permission_type"),
        CustomFilter("custom_filter"),
    }

    private companion object {
        private val PREFIX_TO_FILTER_KIND = linkedMapOf(
            "event_message_type:" to ParsedFilterKind.EventMessageType,
            "platform_adapter_type:" to ParsedFilterKind.PlatformAdapterType,
            "permission_type:" to ParsedFilterKind.PermissionType,
            "custom_filter:" to ParsedFilterKind.CustomFilter,
        )
    }
}
