package com.elymbot.android.di.hilt.runtime

import com.elymbot.android.feature.cron.runtime.CronJobDeliverySummary
import com.elymbot.android.feature.cron.runtime.CronJobExecutionContext
import com.elymbot.android.feature.cron.runtime.CronJobExecutionFailure
import com.elymbot.android.feature.cron.runtime.PluginScheduledTaskDispatchPort
import com.elymbot.android.feature.plugin.runtime.PluginV2ActiveRuntimeStore
import com.elymbot.android.feature.plugin.runtime.PluginV2SchedulePayload
import com.elymbot.android.feature.plugin.runtime.PluginV2ScheduledDispatchEngine
import com.elymbot.android.feature.plugin.runtime.PluginV2ScheduledHandlerEvent
import com.elymbot.android.feature.plugin.runtime.PluginV2ScheduledHandlerLifecycle

internal class PluginV2ScheduledTaskDispatchPortAdapter(
    private val activeRuntimeStore: PluginV2ActiveRuntimeStore,
    private val dispatchEngine: PluginV2ScheduledDispatchEngine,
) : PluginScheduledTaskDispatchPort {

    override suspend fun dispatch(context: CronJobExecutionContext): CronJobDeliverySummary? {
        if (context.jobType != PluginV2ScheduledHandlerLifecycle.PLUGIN_V2_SCHEDULE_JOB_TYPE) {
            return null
        }
        val payload = parsePayload(context)
        val pluginId = payload.pluginId.trim()
        val handlerKey = payload.handlerKey.trim()
        if (pluginId.isBlank() || handlerKey.isBlank()) {
            throw CronJobExecutionFailure(
                code = "invalid_plugin_schedule_payload",
                retryable = false,
                message = "Plugin V2 schedule payload requires pluginId and handlerKey for job=${context.jobId}.",
            )
        }
        val conversationId = payload.conversationId.ifBlank { context.conversationId.ifBlank { context.sessionId } }
        val dispatchResult = dispatchEngine.dispatch(
            event = PluginV2ScheduledHandlerEvent(
                pluginId = pluginId,
                handlerKey = handlerKey,
                jobId = context.jobId,
                conversationId = conversationId,
                platformAdapterType = payload.platformAdapterType.ifBlank { context.platform },
                scheduledAtEpochMillis = context.scheduledAtEpochMillis,
                triggerSource = payload.triggerSource.ifBlank { PluginV2ScheduledHandlerLifecycle.TRIGGER_SOURCE },
            ),
            snapshot = activeRuntimeStore.snapshot(),
        )
        if (!dispatchResult.succeeded) {
            throw CronJobExecutionFailure(
                code = dispatchResult.errorCode.ifBlank { "plugin_schedule_dispatch_failed" },
                retryable = false,
                message = "Plugin V2 scheduled handler dispatch failed for job=${context.jobId}: ${dispatchResult.errorCode}",
            )
        }
        return CronJobDeliverySummary(
            platform = PluginV2ScheduledHandlerLifecycle.TRIGGER_SOURCE,
            conversationId = conversationId,
            deliveredMessageCount = 1,
            receiptIds = listOf("${context.jobId}:$handlerKey"),
            textPreview = "Plugin V2 scheduled handler dispatched: $handlerKey",
        )
    }

    private fun parsePayload(context: CronJobExecutionContext): PluginV2SchedulePayload {
        return runCatching {
            PluginV2SchedulePayload.fromJsonString(context.payloadJson)
        }.getOrElse { error ->
            throw CronJobExecutionFailure(
                code = "invalid_plugin_schedule_payload",
                retryable = false,
                message = "Invalid Plugin V2 schedule payload for job=${context.jobId}: ${error.message ?: error.javaClass.simpleName}",
                cause = error,
            )
        }
    }
}
