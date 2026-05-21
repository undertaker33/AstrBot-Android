package com.elymbot.android.feature.plugin.runtime

import com.elymbot.android.feature.cron.domain.CronJobRepositoryPort
import com.elymbot.android.feature.cron.domain.CronSchedulerPort
import com.elymbot.android.feature.cron.domain.model.CronJob
import kotlin.coroutines.cancellation.CancellationException
import org.json.JSONObject

data class PluginV2SchedulePayload(
    val pluginId: String,
    val pluginVersion: String,
    val handlerKey: String,
    val conversationId: String,
    val platformAdapterType: String = "",
    val triggerSource: String,
) {
    fun toJsonString(): String {
        return JSONObject()
            .put("pluginId", pluginId)
            .put("pluginVersion", pluginVersion)
            .put("handlerKey", handlerKey)
            .put("conversationId", conversationId)
            .put("platformAdapterType", platformAdapterType)
            .put("triggerSource", triggerSource)
            .toString()
    }

    companion object {
        fun fromJsonString(value: String): PluginV2SchedulePayload {
            val json = JSONObject(value.ifBlank { "{}" })
            return PluginV2SchedulePayload(
                pluginId = json.optString("pluginId"),
                pluginVersion = json.optString("pluginVersion"),
                handlerKey = json.optString("handlerKey"),
                conversationId = json.optString("conversationId"),
                platformAdapterType = json.optString("platformAdapterType"),
                triggerSource = json.optString("triggerSource"),
            )
        }
    }
}

fun CronJob.pluginSchedulePayload(): PluginV2SchedulePayload {
    return PluginV2SchedulePayload.fromJsonString(payloadJson)
}

class PluginV2ScheduledHandlerLifecycle(
    private val repository: CronJobRepositoryPort,
    private val scheduler: CronSchedulerPort,
    private val clock: () -> Long = System::currentTimeMillis,
    private val nextFireTime: (String, Long?, Long) -> Long = { _, runAt, now -> runAt ?: now },
) {
    suspend fun reconcile(
        pluginId: String,
        pluginVersion: String,
        schedules: List<PluginV2CompiledScheduledHandler>,
    ) {
        val normalizedPluginId = pluginId.trim()
        if (normalizedPluginId.isBlank()) return

        val now = clock()
        val existingByHandlerKey = pluginV2JobsForPlugin(normalizedPluginId)
            .associateBy { job -> job.pluginSchedulePayload().handlerKey }
        val desiredKeys = schedules.mapTo(linkedSetOf()) { it.handlerKey }

        schedules.forEach { schedule ->
            val payload = PluginV2SchedulePayload(
                pluginId = normalizedPluginId,
                pluginVersion = pluginVersion,
                handlerKey = schedule.handlerKey,
                conversationId = schedule.conversationId,
                platformAdapterType = schedule.platformAdapterType,
                triggerSource = TRIGGER_SOURCE,
            )
            val existing = existingByHandlerKey[schedule.handlerKey]
            val nextRun = nextFireTime(schedule.cron.orEmpty(), schedule.runAtEpochMillis, now)
            val job = (existing ?: CronJob(
                jobId = scheduleJobId(normalizedPluginId, schedule.handlerKey),
                createdAt = now,
            )).copy(
                name = "Plugin schedule ${schedule.handlerKey}",
                description = "Plugin V2 scheduled handler for $normalizedPluginId/${schedule.handlerKey}",
                jobType = PLUGIN_V2_SCHEDULE_JOB_TYPE,
                cronExpression = schedule.cron.orEmpty(),
                payloadJson = payload.toJsonString(),
                enabled = true,
                runOnce = schedule.runAtEpochMillis != null,
                platform = schedule.platformAdapterType,
                conversationId = schedule.conversationId,
                origin = normalizedPluginId,
                nextRunTime = nextRun,
                updatedAt = now,
            )
            val persisted = if (existing == null) {
                repository.create(job)
            } else {
                repository.update(job)
            }
            scheduler.schedule(persisted)
        }

        existingByHandlerKey
            .filterKeys { handlerKey -> handlerKey !in desiredKeys }
            .values
            .forEach { staleJob ->
                scheduler.cancel(staleJob.jobId)
                repository.delete(staleJob.jobId)
            }
    }

    suspend fun pausePlugin(pluginId: String) {
        pluginV2JobsForPlugin(pluginId).forEach { job ->
            scheduler.cancel(job.jobId)
            repository.update(
                job.copy(
                    enabled = false,
                    updatedAt = clock(),
                ),
            )
        }
    }

    suspend fun deletePlugin(pluginId: String) {
        pluginV2JobsForPlugin(pluginId).forEach { job ->
            if (job.enabled) {
                scheduler.cancel(job.jobId)
            }
            repository.delete(job.jobId)
        }
    }

    private suspend fun pluginV2JobsForPlugin(pluginId: String): List<CronJob> {
        val normalizedPluginId = pluginId.trim()
        return repository.listAll().filter { job ->
            job.jobType == PLUGIN_V2_SCHEDULE_JOB_TYPE &&
                runCatching { job.pluginSchedulePayload().pluginId == normalizedPluginId }.getOrDefault(false)
        }
    }

    companion object {
        const val PLUGIN_V2_SCHEDULE_JOB_TYPE = "plugin_v2_schedule"
        const val TRIGGER_SOURCE = "plugin_v2_schedule"

        fun scheduleJobId(pluginId: String, handlerKey: String): String {
            return "plugin-v2-schedule:${pluginId.trim()}:${handlerKey.trim()}"
        }
    }
}

data class PluginV2ScheduledHandlerEvent(
    val pluginId: String,
    val handlerKey: String,
    val jobId: String,
    val conversationId: String,
    val platformAdapterType: String = "",
    val scheduledAtEpochMillis: Long,
    val triggerSource: String,
) : PluginErrorEventPayload

data class PluginV2ScheduledDispatchResult(
    val succeeded: Boolean,
    val errorCode: String = "",
)

class PluginV2ScheduledDispatchEngine(
    private val messageStreamFinalizerProvider: () -> PluginV2MessageStreamFinalizer? = { null },
) {
    suspend fun dispatch(
        event: PluginV2ScheduledHandlerEvent,
        snapshot: PluginV2ActiveRuntimeSnapshot,
    ): PluginV2ScheduledDispatchResult {
        val session = snapshot.activeSessionsByPluginId[event.pluginId]
            ?: return PluginV2ScheduledDispatchResult(false, "missing_plugin_session")
        if (session.state != PluginV2RuntimeSessionState.Active) {
            return PluginV2ScheduledDispatchResult(false, "plugin_session_inactive")
        }
        val registry = snapshot.compiledRegistriesByPluginId[event.pluginId]
            ?: return PluginV2ScheduledDispatchResult(false, "missing_compiled_registry")
        val handler = registry.handlerRegistry.scheduledHandlers.firstOrNull { candidate ->
            candidate.handlerKey == event.handlerKey
        } ?: return PluginV2ScheduledDispatchResult(false, "missing_schedule_handler")
        if (event.conversationId.isBlank() || event.platformAdapterType.isBlank()) {
            return PluginV2ScheduledDispatchResult(false, "missing_context")
        }
        val handle = session.requireCallbackHandle(handler.callbackToken)
        var streamFailureMessage = ""
        try {
            session.runSerializedCallback {
                if (handle is PluginV2EventAwareCallbackHandle) {
                    handle.handleEvent(event)
                } else {
                    handle.invoke()
                }
            }
        } catch (error: Throwable) {
            streamFailureMessage = if (error is CancellationException) {
                "Plugin scheduled handler cancelled."
            } else {
                "Plugin scheduled handler failed: ${error.message ?: error.javaClass.simpleName}"
            }
            throw error
        } finally {
            messageStreamFinalizerProvider()?.closeOpenStreamsForPlugin(
                pluginId = session.pluginId,
                failureMessage = streamFailureMessage,
            )
        }
        return PluginV2ScheduledDispatchResult(succeeded = true)
    }
}
