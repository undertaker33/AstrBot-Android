package com.astrbot.android.runtime.plugin

import com.astrbot.android.model.plugin.PluginRuntimeLogCategory
import com.astrbot.android.model.plugin.PluginRuntimeLogLevel
import com.astrbot.android.model.plugin.PluginRuntimeLogRecord
import kotlinx.coroutines.CancellationException

enum class PluginV2DispatchObservationKind {
    Skip,
    Missing,
    Inactive,
}

data class PluginV2DispatchObservation(
    val pluginId: String,
    val stage: PluginV2InternalStage,
    val kind: PluginV2DispatchObservationKind,
    val reason: String,
    val handlerId: String? = null,
)

data class PluginV2DispatchPlan(
    val stage: PluginV2InternalStage,
    val envelopes: List<PluginV2DispatchEnvelope>,
    val observations: List<PluginV2DispatchObservation> = emptyList(),
)

data class PluginV2MessageDispatchResult(
    val propagationStopped: Boolean = false,
    val terminatedByCustomFilterFailure: Boolean = false,
    val userVisibleFailureMessage: String? = null,
)

object PluginV2DispatchEngineProvider {
    @Volatile
    private var engineOverrideForTests: PluginV2DispatchEngine? = null

    private val sharedEngine: PluginV2DispatchEngine by lazy {
        PluginV2DispatchEngine()
    }

    fun engine(): PluginV2DispatchEngine = engineOverrideForTests ?: sharedEngine

    internal fun setEngineOverrideForTests(engine: PluginV2DispatchEngine?) {
        engineOverrideForTests = engine
    }
}

class PluginV2DispatchEngine(
    private val store: PluginV2ActiveRuntimeStore = PluginV2ActiveRuntimeStoreProvider.store(),
    private val logBus: PluginRuntimeLogBus = PluginRuntimeLogBusProvider.bus(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val lifecycleManager: PluginV2LifecycleManager = PluginV2LifecycleManager(
        store = store,
        logBus = logBus,
        clock = clock,
    ),
    private val filterEvaluator: PluginV2FilterEvaluator = PluginV2FilterEvaluator(
        logBus = logBus,
        clock = clock,
    ),
) {
    fun dispatch(
        stage: PluginV2InternalStage,
        snapshot: PluginV2ActiveRuntimeSnapshot = store.snapshot(),
    ): PluginV2DispatchPlan {
        val observations = mutableListOf<PluginV2DispatchObservation>()
        val candidates = mutableListOf<DispatchCandidate>()

        snapshot.activeRuntimeEntriesByPluginId.keys
            .sorted()
            .forEach pluginLoop@{ pluginId ->
                val session = snapshot.activeSessionsByPluginId[pluginId]
                if (session == null) {
                    observations += PluginV2DispatchObservation(
                        pluginId = pluginId,
                        stage = stage,
                        kind = PluginV2DispatchObservationKind.Missing,
                        reason = "missing_session",
                    )
                    return@pluginLoop
                }
                if (session.state != PluginV2RuntimeSessionState.Active) {
                    observations += PluginV2DispatchObservation(
                        pluginId = pluginId,
                        stage = stage,
                        kind = PluginV2DispatchObservationKind.Inactive,
                        reason = "session_state_${session.state.name.lowercase()}",
                    )
                    return@pluginLoop
                }

                val compiledRegistry = snapshot.compiledRegistriesByPluginId[pluginId]
                if (compiledRegistry == null) {
                    observations += PluginV2DispatchObservation(
                        pluginId = pluginId,
                        stage = stage,
                        kind = PluginV2DispatchObservationKind.Missing,
                        reason = "missing_compiled_registry",
                    )
                    return@pluginLoop
                }

                val handlerIds = compiledRegistry.dispatchIndex.handlerIdsByStage[stage].orEmpty()
                if (handlerIds.isEmpty()) {
                    observations += PluginV2DispatchObservation(
                        pluginId = pluginId,
                        stage = stage,
                        kind = PluginV2DispatchObservationKind.Skip,
                        reason = "no_handlers_for_stage",
                    )
                    return@pluginLoop
                }

                handlerIds.forEach handlerLoop@{ handlerId ->
                    val descriptor = compiledRegistry.handlerRegistry.findHandler(handlerId)
                    if (descriptor == null) {
                        observations += PluginV2DispatchObservation(
                            pluginId = pluginId,
                            stage = stage,
                            kind = PluginV2DispatchObservationKind.Missing,
                            reason = "missing_handler_descriptor",
                            handlerId = handlerId,
                        )
                        return@handlerLoop
                    }
                    candidates += DispatchCandidate(
                        pluginId = pluginId,
                        handlerId = descriptor.handlerId,
                        priority = descriptor.priority,
                        envelope = PluginV2DispatchEnvelope(
                            stage = PluginV2DispatchStage.Skeleton,
                            callbackToken = descriptor.callbackToken,
                            payloadRef = PluginV2DispatchPayloadRef(
                                kind = PluginV2PayloadKind.OpaqueRef,
                                refId = "dispatch::$pluginId::$stage::${descriptor.handlerId}",
                                attributes = mapOf(
                                    "pluginId" to pluginId,
                                    "internalStage" to stage.name,
                                    "registrationKind" to descriptor.registrationKind,
                                    "registrationKey" to descriptor.registrationKey,
                                ),
                            ),
                            traceId = "trace::$pluginId::$stage::${descriptor.handlerId}",
                        ),
                    )
                }
            }

        val envelopes = candidates.sortedWith(
            compareByDescending<DispatchCandidate> { it.priority }
                .thenBy { it.handlerId },
                ).map { candidate -> candidate.envelope }

        return PluginV2DispatchPlan(
            stage = stage,
            envelopes = envelopes,
            observations = observations,
        )
    }

    suspend fun dispatchMessage(
        event: PluginMessageEvent,
        snapshot: PluginV2ActiveRuntimeSnapshot = store.snapshot(),
    ): PluginV2MessageDispatchResult {
        val activeSessions = snapshot.activeSessionsByPluginId.values
            .filter { it.state == PluginV2RuntimeSessionState.Active }
            .sortedBy(PluginV2RuntimeSession::pluginId)
        publishDispatchLifecycle(
            code = "message_dispatch_started",
            sessions = activeSessions,
            stage = PluginMessageStage.AdapterMessage,
            event = event,
            succeeded = true,
        )

        dispatchStage(
            stage = PluginMessageStage.AdapterMessage,
            candidates = buildMessageCandidates(snapshot),
            event = event,
        )?.let { result ->
            publishDispatchLifecycle(
                code = "message_dispatch_completed",
                sessions = activeSessions,
                stage = PluginMessageStage.AdapterMessage,
                event = event,
                succeeded = result.userVisibleFailureMessage == null,
            )
            return result
        }

        val commandResolution = PluginV2CommandResolver(
            snapshot.compiledRegistriesByPluginId.values.map { it.handlerRegistry },
        ).resolve(commandStageWorkingText(event.workingText))
        if (commandResolution == null) {
            publishStageSkipped(
                sessions = activeSessions,
                stage = PluginMessageStage.Command,
                event = event,
                reason = "command_not_matched",
            )
        } else {
            dispatchStage(
                stage = PluginMessageStage.Command,
                candidates = buildCommandCandidates(snapshot, event, commandResolution),
                event = event,
            )?.let { result ->
                publishDispatchLifecycle(
                    code = "message_dispatch_completed",
                    sessions = activeSessions,
                    stage = PluginMessageStage.Command,
                    event = event,
                    succeeded = result.userVisibleFailureMessage == null,
                )
                return result
            }
        }

        val regexCandidates = buildRegexCandidates(snapshot, event)
        if (regexCandidates.isEmpty()) {
            publishStageSkipped(
                sessions = activeSessions,
                stage = PluginMessageStage.Regex,
                event = event,
                reason = "regex_not_matched",
            )
        } else {
            dispatchStage(
                stage = PluginMessageStage.Regex,
                candidates = regexCandidates,
                event = event,
            )?.let { result ->
                publishDispatchLifecycle(
                    code = "message_dispatch_completed",
                    sessions = activeSessions,
                    stage = PluginMessageStage.Regex,
                    event = event,
                    succeeded = result.userVisibleFailureMessage == null,
                )
                return result
            }
        }

        val result = PluginV2MessageDispatchResult(
            propagationStopped = event.isPropagationStopped,
        )
        publishDispatchLifecycle(
            code = "message_dispatch_completed",
            sessions = activeSessions,
            stage = PluginMessageStage.Regex,
            event = event,
            succeeded = true,
        )
        return result
    }

    private suspend fun dispatchStage(
        stage: PluginMessageStage,
        candidates: List<StageCandidate>,
        event: PluginMessageEvent,
    ): PluginV2MessageDispatchResult? {
        if (event.isPropagationStopped) {
            return PluginV2MessageDispatchResult(propagationStopped = true)
        }

        for ((index, candidate) in candidates.withIndex()) {
            if (event.isPropagationStopped) {
                publishPropagationStopped(candidates = candidates.drop(index), stage = stage, event = event)
                return PluginV2MessageDispatchResult(propagationStopped = true)
            }

            val stageEvent = candidate.materialize(event)
            when (val filterResult = filterEvaluator.evaluate(candidate.session, candidate.descriptor, stageEvent)) {
                is PluginV2FilterEvaluationResult.Pass -> {
                    invokeHandler(candidate.session, candidate.descriptor, stageEvent)
                }

                is PluginV2FilterEvaluationResult.Reject -> continue

                is PluginV2FilterEvaluationResult.ErrorStop -> {
                    return PluginV2MessageDispatchResult(
                        propagationStopped = event.isPropagationStopped,
                        terminatedByCustomFilterFailure = true,
                        userVisibleFailureMessage = filterResult.userVisibleMessage,
                    )
                }
            }
        }

        return if (event.isPropagationStopped) {
            PluginV2MessageDispatchResult(propagationStopped = true)
        } else {
            null
        }
    }

    private suspend fun invokeHandler(
        session: PluginV2RuntimeSession,
        descriptor: PluginV2CompiledHandlerDescriptor,
        event: PluginErrorEventPayload,
    ) {
        val handle = session.requireCallbackHandle(descriptor.callbackToken)
        try {
            session.runSerializedCallback {
                if (handle is PluginV2EventAwareCallbackHandle) {
                    handle.handleEvent(event)
                } else {
                    handle.invoke()
                }
            }
        } catch (error: Throwable) {
            error.rethrowIfCancellation()
            lifecycleManager.emitPluginError(
                event = event,
                pluginName = session.pluginId,
                handlerName = descriptor.handlerId,
                error = error,
                tracebackText = error.stackTraceToString(),
            )
        }
    }

    private fun buildMessageCandidates(
        snapshot: PluginV2ActiveRuntimeSnapshot,
    ): List<StageCandidate> {
        return snapshot.compiledRegistriesByPluginId.entries
            .mapNotNull { (pluginId, registry) ->
                val session = snapshot.activeSessionsByPluginId[pluginId]
                if (session == null || session.state != PluginV2RuntimeSessionState.Active) {
                    null
                } else {
                    registry.handlerRegistry.messageHandlers.map { descriptor ->
                        StageCandidate(
                            session = session,
                            descriptor = descriptor,
                            materialize = { currentEvent -> currentEvent },
                        )
                    }
                }
            }
            .flatten()
            .let(::sortedStageCandidates)
    }

    private fun buildCommandCandidates(
        snapshot: PluginV2ActiveRuntimeSnapshot,
        event: PluginMessageEvent,
        resolution: PluginV2CommandResolution,
    ): List<StageCandidate> {
        return resolution.bucket.handlers.mapNotNull { descriptor ->
            val session = snapshot.activeSessionsByPluginId[descriptor.pluginId]
            if (session == null || session.state != PluginV2RuntimeSessionState.Active) {
                null
            } else {
                StageCandidate(
                    session = session,
                    descriptor = descriptor,
                    materialize = { _ ->
                        PluginCommandEvent(
                            baseEvent = event,
                            commandPath = resolution.commandPath,
                            matchedAlias = resolution.matchedAlias,
                            args = tokenizeArguments(resolution.remainingText),
                            remainingText = resolution.remainingText,
                            invocationText = listOf(resolution.matchedAlias, resolution.remainingText)
                                .filter(String::isNotBlank)
                                .joinToString(" "),
                        )
                    },
                )
            }
        }.let(::sortedStageCandidates)
    }

    private fun buildRegexCandidates(
        snapshot: PluginV2ActiveRuntimeSnapshot,
        event: PluginMessageEvent,
    ): List<StageCandidate> {
        return snapshot.compiledRegistriesByPluginId.entries
            .mapNotNull { (pluginId, registry) ->
                val session = snapshot.activeSessionsByPluginId[pluginId]
                if (session == null || session.state != PluginV2RuntimeSessionState.Active) {
                    null
                } else {
                    registry.handlerRegistry.regexHandlers.mapNotNull regexHandlerLoop@{ descriptor ->
                        val match = descriptor.compiledPattern.find(event.workingText) ?: return@regexHandlerLoop null
                        StageCandidate(
                            session = session,
                            descriptor = descriptor,
                            materialize = { _ ->
                                PluginRegexEvent(
                                    baseEvent = event,
                                    patternKey = descriptor.registrationKey,
                                    matchedText = match.value,
                                    groups = match.groupValues.drop(1),
                                    namedGroups = descriptor.namedGroups(match),
                                )
                            },
                        )
                    }
                }
            }
            .flatten()
            .let(::sortedStageCandidates)
    }

    private fun sortedStageCandidates(candidates: List<StageCandidate>): List<StageCandidate> {
        return candidates.sortedWith(
            compareByDescending<StageCandidate> { it.descriptor.priority }
                .thenBy { it.descriptor.handlerId },
        )
    }

    private fun publishPropagationStopped(
        candidates: List<StageCandidate>,
        stage: PluginMessageStage,
        event: PluginMessageEvent,
    ) {
        candidates.forEach { candidate ->
            publishHandlerSkipped(
                session = candidate.session,
                handlerId = candidate.descriptor.handlerId,
                stage = stage,
                event = event,
                reason = "propagation_stopped",
            )
        }
    }

    private fun publishDispatchLifecycle(
        code: String,
        sessions: List<PluginV2RuntimeSession>,
        stage: PluginMessageStage,
        event: PluginMessageEvent,
        succeeded: Boolean,
    ) {
        sessions.forEach { session ->
            logBus.publish(
                PluginRuntimeLogRecord(
                    occurredAtEpochMillis = clock(),
                    pluginId = session.pluginId,
                    pluginVersion = session.installRecord.installedVersion,
                    category = PluginRuntimeLogCategory.Dispatcher,
                    level = PluginRuntimeLogLevel.Info,
                    code = code,
                    message = "Plugin v2 message dispatch observation.",
                    succeeded = succeeded,
                    metadata = linkedMapOf(
                        "sessionInstanceId" to session.sessionInstanceId,
                        "stage" to stage.name,
                        "traceId" to "trace::${event.eventId}::${stage.name}",
                        "messageType" to event.messageType.wireValue,
                        "platformAdapterType" to event.platformAdapterType,
                    ),
                ),
            )
        }
    }

    private fun publishStageSkipped(
        sessions: List<PluginV2RuntimeSession>,
        stage: PluginMessageStage,
        event: PluginMessageEvent,
        reason: String,
    ) {
        sessions.forEach { session ->
            publishHandlerSkipped(
                session = session,
                handlerId = null,
                stage = stage,
                event = event,
                reason = reason,
            )
        }
    }

    private fun publishHandlerSkipped(
        session: PluginV2RuntimeSession,
        handlerId: String?,
        stage: PluginMessageStage,
        event: PluginMessageEvent,
        reason: String,
    ) {
        logBus.publish(
            PluginRuntimeLogRecord(
                occurredAtEpochMillis = clock(),
                pluginId = session.pluginId,
                pluginVersion = session.installRecord.installedVersion,
                category = PluginRuntimeLogCategory.Dispatcher,
                level = PluginRuntimeLogLevel.Info,
                code = "message_handler_skipped",
                message = "Plugin v2 handler skipped.",
                succeeded = true,
                metadata = linkedMapOf(
                    "sessionInstanceId" to session.sessionInstanceId,
                    "stage" to stage.name,
                    "traceId" to "trace::${event.eventId}::${stage.name}",
                    "reason" to reason,
                ).also { metadata ->
                    handlerId?.let { metadata["handlerId"] = it }
                },
            ),
        )
    }

    private data class DispatchCandidate(
        val pluginId: String,
        val handlerId: String,
        val priority: Int,
        val envelope: PluginV2DispatchEnvelope,
    )

    private data class StageCandidate(
        val session: PluginV2RuntimeSession,
        val descriptor: PluginV2CompiledHandlerDescriptor,
        val materialize: (PluginMessageEvent) -> PluginErrorEventPayload,
    )
}

private fun Throwable.rethrowIfCancellation() {
    if (this is CancellationException) {
        throw this
    }
}

private fun PluginV2HandlerRegistry.findHandler(handlerId: String): PluginV2CompiledHandlerDescriptor? {
    return messageHandlers.firstOrNull { it.handlerId == handlerId }
        ?: commandHandlers.firstOrNull { it.handlerId == handlerId }
        ?: regexHandlers.firstOrNull { it.handlerId == handlerId }
        ?: lifecycleHandlers.firstOrNull { it.handlerId == handlerId }
}

private fun tokenizeArguments(
    remainingText: String,
): List<String> {
    return remainingText.trim()
        .split(Regex("\\s+"))
        .map(String::trim)
        .filter(String::isNotBlank)
}

private fun commandStageWorkingText(
    workingText: String,
): String {
    val trimmed = workingText.trimStart()
    return if (trimmed.startsWith("/")) {
        trimmed.removePrefix("/")
    } else {
        workingText
    }
}
