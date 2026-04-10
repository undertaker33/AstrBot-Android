package com.astrbot.android.runtime.plugin

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
) {
    fun dispatch(
        stage: PluginV2InternalStage,
        snapshot: PluginV2ActiveRuntimeSnapshot = store.snapshot(),
    ): PluginV2DispatchPlan {
        val observations = mutableListOf<PluginV2DispatchObservation>()
        val candidates = mutableListOf<DispatchCandidate>()

        snapshot.activeRuntimeEntriesByPluginId.keys
            .sorted()
            .forEach { pluginId ->
                val session = snapshot.activeSessionsByPluginId[pluginId]
                if (session == null) {
                    observations += PluginV2DispatchObservation(
                        pluginId = pluginId,
                        stage = stage,
                        kind = PluginV2DispatchObservationKind.Missing,
                        reason = "missing_session",
                    )
                    return@forEach
                }
                if (session.state != PluginV2RuntimeSessionState.Active) {
                    observations += PluginV2DispatchObservation(
                        pluginId = pluginId,
                        stage = stage,
                        kind = PluginV2DispatchObservationKind.Inactive,
                        reason = "session_state_${session.state.name.lowercase()}",
                    )
                    return@forEach
                }

                val compiledRegistry = snapshot.compiledRegistriesByPluginId[pluginId]
                if (compiledRegistry == null) {
                    observations += PluginV2DispatchObservation(
                        pluginId = pluginId,
                        stage = stage,
                        kind = PluginV2DispatchObservationKind.Missing,
                        reason = "missing_compiled_registry",
                    )
                    return@forEach
                }

                val handlerIds = compiledRegistry.dispatchIndex.handlerIdsByStage[stage].orEmpty()
                if (handlerIds.isEmpty()) {
                    observations += PluginV2DispatchObservation(
                        pluginId = pluginId,
                        stage = stage,
                        kind = PluginV2DispatchObservationKind.Skip,
                        reason = "no_handlers_for_stage",
                    )
                    return@forEach
                }

                handlerIds.forEach { handlerId ->
                    val descriptor = compiledRegistry.handlerRegistry.findHandler(handlerId)
                    if (descriptor == null) {
                        observations += PluginV2DispatchObservation(
                            pluginId = pluginId,
                            stage = stage,
                            kind = PluginV2DispatchObservationKind.Missing,
                            reason = "missing_handler_descriptor",
                            handlerId = handlerId,
                        )
                        return@forEach
                    }
                    candidates += DispatchCandidate(
                        pluginId = pluginId,
                        handlerId = descriptor.handlerId,
                        priority = descriptor.priority,
                        sourceOrder = descriptor.sourceOrder,
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
                .thenBy { it.sourceOrder }
                .thenBy { it.pluginId }
                .thenBy { it.handlerId },
        ).map { candidate -> candidate.envelope }

        return PluginV2DispatchPlan(
            stage = stage,
            envelopes = envelopes,
            observations = observations,
        )
    }

    private data class DispatchCandidate(
        val pluginId: String,
        val handlerId: String,
        val priority: Int,
        val sourceOrder: Int,
        val envelope: PluginV2DispatchEnvelope,
    )
}

private fun PluginV2HandlerRegistry.findHandler(handlerId: String): PluginV2CompiledHandlerDescriptor? {
    return messageHandlers.firstOrNull { it.handlerId == handlerId }
        ?: commandHandlers.firstOrNull { it.handlerId == handlerId }
        ?: regexHandlers.firstOrNull { it.handlerId == handlerId }
        ?: lifecycleHandlers.firstOrNull { it.handlerId == handlerId }
        ?: llmHooks.firstOrNull { it.handlerId == handlerId }
        ?: tools.firstOrNull { it.handlerId == handlerId }
        ?: toolLifecycleHooks.firstOrNull { it.handlerId == handlerId }
}
