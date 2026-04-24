package com.astrbot.android.feature.plugin.runtime.toolsource

import com.astrbot.android.core.common.logging.AppLogger
import com.astrbot.android.core.runtime.context.IngressTrigger
import com.astrbot.android.feature.plugin.runtime.PluginToolDescriptor
import com.astrbot.android.feature.plugin.runtime.PluginToolResult
import com.astrbot.android.feature.plugin.runtime.PluginToolResultStatus
import com.astrbot.android.feature.plugin.runtime.PluginToolSourceKind
import com.astrbot.android.feature.plugin.runtime.PluginToolVisibility
import javax.inject.Inject

/**
 * Active capability tool source provider.
 *
 * The provider owns tool exposure and invocation formatting only. Task shaping,
 * target resolution, persistence, and WorkManager scheduling are delegated to
 * [ActiveCapabilityRuntimeFacade].
 */
class ActiveCapabilityToolSourceProvider @Inject constructor(
    private val facade: ActiveCapabilityRuntimeFacade,
    override val contextResolver: FutureToolSourceContextResolver,
) : FutureToolSourceProvider {
    override val sourceKind: PluginToolSourceKind = PluginToolSourceKind.ACTIVE_CAPABILITY

    override suspend fun listBindings(
        context: ToolSourceRegistryIngestContext,
    ): List<ToolSourceDescriptorBinding> {
        if (!context.toolSourceContext.activeCapabilityEnabled) return emptyList()
        if (context.toolSourceContext.ingressTrigger == IngressTrigger.SCHEDULED_TASK) return emptyList()

        return listOf(
            buildCreateTaskBinding(),
            buildDeleteTaskBinding(),
            buildListTasksBinding(),
            buildPauseTaskBinding(),
            buildResumeTaskBinding(),
            buildListTaskRunsBinding(),
            buildUpdateTaskBinding(),
            buildRunTaskNowBinding(),
        )
    }

    override suspend fun availabilityOf(
        identity: ToolSourceIdentity,
        context: ToolSourceAvailabilityContext,
    ): ToolSourceAvailability {
        return if (
            context.toolSourceContext.activeCapabilityEnabled &&
            context.toolSourceContext.ingressTrigger != IngressTrigger.SCHEDULED_TASK
        ) {
            ToolSourceAvailability(
                providerReachable = true,
                permissionGranted = true,
                capabilityAllowed = true,
            )
        } else {
            ToolSourceAvailability(
                providerReachable = false,
                permissionGranted = true,
                capabilityAllowed = false,
                detailCode = if (context.toolSourceContext.ingressTrigger == IngressTrigger.SCHEDULED_TASK) {
                    "scheduled_task_wakeup"
                } else {
                    "proactive_disabled"
                },
                detailMessage = if (context.toolSourceContext.ingressTrigger == IngressTrigger.SCHEDULED_TASK) {
                    "Active capability tools are hidden while a scheduled task is being delivered."
                } else {
                    "Proactive capability is disabled in this config profile."
                },
            )
        }
    }

    override suspend fun invoke(
        request: ToolSourceInvokeRequest,
    ): ToolSourceInvokeResult {
        val toolName = request.args.toolId.substringAfter(":")
        return try {
            val invocation = when (toolName) {
                "create_future_task" -> handleCreateFutureTask(request)
                "delete_future_task" -> handleDeleteFutureTask(request.args.payload)
                "list_future_tasks" -> handleListFutureTasks()
                "pause_future_task" -> handlePauseFutureTask(request.args.payload)
                "resume_future_task" -> handleResumeFutureTask(request.args.payload)
                "list_future_task_runs" -> handleListFutureTaskRuns(request.args.payload)
                "update_future_task" -> handleUpdateFutureTask(request.args.payload)
                "run_future_task_now" -> handleRunFutureTaskNow(request.args.payload)
                else -> throw IllegalArgumentException("Unknown active capability tool: $toolName")
            }
            ToolSourceInvokeResult(
                result = PluginToolResult(
                    toolCallId = request.args.toolCallId,
                    requestId = request.args.requestId,
                    toolId = request.args.toolId,
                    status = invocation.status,
                    errorCode = invocation.errorCode,
                    text = invocation.text,
                ),
            )
        } catch (e: Exception) {
            AppLogger.append("ActiveCapability invoke error: ${e.message}")
            ToolSourceInvokeResult(
                result = PluginToolResult(
                    toolCallId = request.args.toolCallId,
                    requestId = request.args.requestId,
                    toolId = request.args.toolId,
                    status = PluginToolResultStatus.ERROR,
                    errorCode = "active_capability_error",
                    text = "Error: ${e.message}",
                ),
            )
        }
    }

    private suspend fun handleCreateFutureTask(
        request: ToolSourceInvokeRequest,
    ): ActiveCapabilityToolInvocation {
        val result = facade.createFutureTask(
            ActiveCapabilityCreateTaskRequest(
                payload = request.args.payload,
                metadata = request.args.metadata,
                toolSourceContext = request.toolSourceContext,
            ),
        )
        val failed = result as? ActiveCapabilityTaskCreation.Failed
        return ActiveCapabilityToolInvocation(
            status = if (failed == null) PluginToolResultStatus.SUCCESS else PluginToolResultStatus.ERROR,
            errorCode = failed?.error?.code,
            text = facade.creationToJson(result).toString(2),
        )
    }

    private suspend fun handleDeleteFutureTask(payload: Map<String, Any?>): ActiveCapabilityToolInvocation {
        val json = facade.deleteFutureTask((payload["job_id"] as? String).orEmpty())
        val success = json.optBoolean("success", false)
        return ActiveCapabilityToolInvocation(
            status = if (success) PluginToolResultStatus.SUCCESS else PluginToolResultStatus.ERROR,
            errorCode = if (success) null else json.optString("error_code", "active_capability_error"),
            text = json.toString(2),
        )
    }

    private suspend fun handleListFutureTasks(): ActiveCapabilityToolInvocation {
        return ActiveCapabilityToolInvocation(
            status = PluginToolResultStatus.SUCCESS,
            text = facade.listFutureTasks().toString(2),
        )
    }

    private suspend fun handlePauseFutureTask(payload: Map<String, Any?>): ActiveCapabilityToolInvocation {
        return managementInvocation(facade.pauseFutureTask((payload["job_id"] as? String).orEmpty()))
    }

    private suspend fun handleResumeFutureTask(payload: Map<String, Any?>): ActiveCapabilityToolInvocation {
        return managementInvocation(facade.resumeFutureTask((payload["job_id"] as? String).orEmpty()))
    }

    private suspend fun handleListFutureTaskRuns(payload: Map<String, Any?>): ActiveCapabilityToolInvocation {
        val limit = (payload["limit"] as? Number)?.toInt() ?: 5
        return managementInvocation(
            facade.listFutureTaskRuns(
                jobId = (payload["job_id"] as? String).orEmpty(),
                limit = limit,
            ),
        )
    }

    private suspend fun handleUpdateFutureTask(payload: Map<String, Any?>): ActiveCapabilityToolInvocation {
        return managementInvocation(facade.updateFutureTask(payload))
    }

    private suspend fun handleRunFutureTaskNow(payload: Map<String, Any?>): ActiveCapabilityToolInvocation {
        return managementInvocation(facade.runFutureTaskNow((payload["job_id"] as? String).orEmpty()))
    }

    private fun managementInvocation(json: org.json.JSONObject): ActiveCapabilityToolInvocation {
        val success = json.optBoolean("success", false)
        return ActiveCapabilityToolInvocation(
            status = if (success) PluginToolResultStatus.SUCCESS else PluginToolResultStatus.ERROR,
            errorCode = if (success) null else json.optString("error_code", "active_capability_error"),
            text = json.toString(2),
        )
    }

    private fun buildCreateTaskBinding(): ToolSourceDescriptorBinding {
        val ownerId = "cap.schedule"
        return ToolSourceDescriptorBinding(
            identity = ToolSourceIdentity(
                sourceKind = PluginToolSourceKind.ACTIVE_CAPABILITY,
                ownerId = ownerId,
                sourceRef = "create_future_task",
                displayName = "Schedule Future Task",
            ),
            descriptor = PluginToolDescriptor(
                pluginId = ownerId,
                name = "create_future_task",
                description = "Schedule reminders, timed follow-ups, and future proactive actions. Provide note as the core instruction, with cron_expression for recurring or run_at (ISO 8601) for one-time tasks.",
                visibility = PluginToolVisibility.LLM_VISIBLE,
                sourceKind = PluginToolSourceKind.ACTIVE_CAPABILITY,
                inputSchema = ActiveCapabilityToolSchemas.createFutureTaskSchema(),
            ),
        )
    }

    private fun buildDeleteTaskBinding(): ToolSourceDescriptorBinding {
        val ownerId = "cap.schedule"
        return ToolSourceDescriptorBinding(
            identity = ToolSourceIdentity(
                sourceKind = PluginToolSourceKind.ACTIVE_CAPABILITY,
                ownerId = ownerId,
                sourceRef = "delete_future_task",
                displayName = "Cancel Future Task",
            ),
            descriptor = PluginToolDescriptor(
                pluginId = ownerId,
                name = "delete_future_task",
                description = "Cancel and delete a previously scheduled future task.",
                visibility = PluginToolVisibility.LLM_VISIBLE,
                sourceKind = PluginToolSourceKind.ACTIVE_CAPABILITY,
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "job_id" to mapOf("type" to "string", "description" to "The job_id of the task to cancel"),
                    ),
                    "required" to listOf("job_id"),
                ),
            ),
        )
    }

    private fun buildListTasksBinding(): ToolSourceDescriptorBinding {
        val ownerId = "cap.schedule"
        return ToolSourceDescriptorBinding(
            identity = ToolSourceIdentity(
                sourceKind = PluginToolSourceKind.ACTIVE_CAPABILITY,
                ownerId = ownerId,
                sourceRef = "list_future_tasks",
                displayName = "List Future Tasks",
            ),
            descriptor = PluginToolDescriptor(
                pluginId = ownerId,
                name = "list_future_tasks",
                description = "List all scheduled future tasks with status, next run time, and execution summary.",
                visibility = PluginToolVisibility.LLM_VISIBLE,
                sourceKind = PluginToolSourceKind.ACTIVE_CAPABILITY,
                inputSchema = mapOf("type" to "object"),
            ),
        )
    }

    private fun buildPauseTaskBinding(): ToolSourceDescriptorBinding {
        return taskByIdBinding(
            sourceRef = "pause_future_task",
            displayName = "Pause Future Task",
            description = "Pause a scheduled future task without deleting it.",
        )
    }

    private fun buildResumeTaskBinding(): ToolSourceDescriptorBinding {
        return taskByIdBinding(
            sourceRef = "resume_future_task",
            displayName = "Resume Future Task",
            description = "Resume a paused scheduled future task.",
        )
    }

    private fun buildListTaskRunsBinding(): ToolSourceDescriptorBinding {
        val ownerId = "cap.schedule"
        return ToolSourceDescriptorBinding(
            identity = ToolSourceIdentity(
                sourceKind = PluginToolSourceKind.ACTIVE_CAPABILITY,
                ownerId = ownerId,
                sourceRef = "list_future_task_runs",
                displayName = "List Future Task Runs",
            ),
            descriptor = PluginToolDescriptor(
                pluginId = ownerId,
                name = "list_future_task_runs",
                description = "List recent execution records for a scheduled future task.",
                visibility = PluginToolVisibility.LLM_VISIBLE,
                sourceKind = PluginToolSourceKind.ACTIVE_CAPABILITY,
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "job_id" to mapOf("type" to "string", "description" to "The job_id of the task."),
                        "limit" to mapOf("type" to "number", "description" to "Maximum number of recent runs to return."),
                    ),
                    "required" to listOf("job_id"),
                ),
            ),
        )
    }

    private fun buildUpdateTaskBinding(): ToolSourceDescriptorBinding {
        val ownerId = "cap.schedule"
        return ToolSourceDescriptorBinding(
            identity = ToolSourceIdentity(
                sourceKind = PluginToolSourceKind.ACTIVE_CAPABILITY,
                ownerId = ownerId,
                sourceRef = "update_future_task",
                displayName = "Update Future Task",
            ),
            descriptor = PluginToolDescriptor(
                pluginId = ownerId,
                name = "update_future_task",
                description = "Update conservative fields on a scheduled future task without changing its target context.",
                visibility = PluginToolVisibility.LLM_VISIBLE,
                sourceKind = PluginToolSourceKind.ACTIVE_CAPABILITY,
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "job_id" to mapOf("type" to "string", "description" to "The job_id of the task."),
                        "name" to mapOf("type" to "string", "description" to "Updated short title."),
                        "note" to mapOf("type" to "string", "description" to "Updated task instruction."),
                        "enabled" to mapOf("type" to "boolean", "description" to "Whether the task is enabled."),
                        "status" to mapOf("type" to "string", "description" to "Updated task status, for example scheduled or paused."),
                        "run_at" to mapOf("type" to "string", "description" to "Updated ISO 8601 one-time run time."),
                        "cron_expression" to mapOf("type" to "string", "description" to "Updated cron expression for recurring tasks."),
                        "timezone" to mapOf("type" to "string", "description" to "Updated IANA timezone."),
                    ),
                    "required" to listOf("job_id"),
                ),
            ),
        )
    }

    private fun buildRunTaskNowBinding(): ToolSourceDescriptorBinding {
        return taskByIdBinding(
            sourceRef = "run_future_task_now",
            displayName = "Run Future Task Now",
            description = "Run a scheduled future task immediately through the injected scheduled task runner.",
        )
    }

    private fun taskByIdBinding(
        sourceRef: String,
        displayName: String,
        description: String,
    ): ToolSourceDescriptorBinding {
        val ownerId = "cap.schedule"
        return ToolSourceDescriptorBinding(
            identity = ToolSourceIdentity(
                sourceKind = PluginToolSourceKind.ACTIVE_CAPABILITY,
                ownerId = ownerId,
                sourceRef = sourceRef,
                displayName = displayName,
            ),
            descriptor = PluginToolDescriptor(
                pluginId = ownerId,
                name = sourceRef,
                description = description,
                visibility = PluginToolVisibility.LLM_VISIBLE,
                sourceKind = PluginToolSourceKind.ACTIVE_CAPABILITY,
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "job_id" to mapOf("type" to "string", "description" to "The job_id of the task."),
                    ),
                    "required" to listOf("job_id"),
                ),
            ),
        )
    }
}

private data class ActiveCapabilityToolInvocation(
    val status: PluginToolResultStatus,
    val errorCode: String? = null,
    val text: String,
)

