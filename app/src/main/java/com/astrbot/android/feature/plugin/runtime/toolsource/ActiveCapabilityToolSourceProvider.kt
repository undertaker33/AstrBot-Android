package com.astrbot.android.feature.plugin.runtime.toolsource

import com.astrbot.android.core.common.logging.AppLogger
import com.astrbot.android.core.runtime.context.IngressTrigger
import com.astrbot.android.feature.plugin.runtime.PluginToolDescriptor
import com.astrbot.android.feature.plugin.runtime.PluginToolResult
import com.astrbot.android.feature.plugin.runtime.PluginToolResultStatus
import com.astrbot.android.feature.plugin.runtime.PluginToolSourceKind
import com.astrbot.android.feature.plugin.runtime.PluginToolVisibility
import com.astrbot.android.feature.cron.runtime.ActiveCapabilityPromptStrings
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
    private val promptStrings: ActiveCapabilityPromptStrings,
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
                    promptStrings.activeCapabilityHiddenDuringScheduledTask
                } else {
                    promptStrings.proactiveCapabilityDisabled
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
                    text = promptStrings.activeCapabilityToolError(e.message.orEmpty()),
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
                displayName = promptStrings.createFutureTaskDisplayName,
            ),
            descriptor = PluginToolDescriptor(
                pluginId = ownerId,
                name = "create_future_task",
                description = promptStrings.createFutureTaskDescription,
                visibility = PluginToolVisibility.LLM_VISIBLE,
                sourceKind = PluginToolSourceKind.ACTIVE_CAPABILITY,
                inputSchema = ActiveCapabilityToolSchemas.createFutureTaskSchema(promptStrings),
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
                displayName = promptStrings.deleteFutureTaskDisplayName,
            ),
            descriptor = PluginToolDescriptor(
                pluginId = ownerId,
                name = "delete_future_task",
                description = promptStrings.deleteFutureTaskDescription,
                visibility = PluginToolVisibility.LLM_VISIBLE,
                sourceKind = PluginToolSourceKind.ACTIVE_CAPABILITY,
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "job_id" to mapOf("type" to "string", "description" to promptStrings.schemaJobIdCancelDescription),
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
                displayName = promptStrings.listFutureTasksDisplayName,
            ),
            descriptor = PluginToolDescriptor(
                pluginId = ownerId,
                name = "list_future_tasks",
                description = promptStrings.listFutureTasksDescription,
                visibility = PluginToolVisibility.LLM_VISIBLE,
                sourceKind = PluginToolSourceKind.ACTIVE_CAPABILITY,
                inputSchema = mapOf("type" to "object"),
            ),
        )
    }

    private fun buildPauseTaskBinding(): ToolSourceDescriptorBinding {
        return taskByIdBinding(
            sourceRef = "pause_future_task",
            displayName = promptStrings.pauseFutureTaskDisplayName,
            description = promptStrings.pauseFutureTaskDescription,
        )
    }

    private fun buildResumeTaskBinding(): ToolSourceDescriptorBinding {
        return taskByIdBinding(
            sourceRef = "resume_future_task",
            displayName = promptStrings.resumeFutureTaskDisplayName,
            description = promptStrings.resumeFutureTaskDescription,
        )
    }

    private fun buildListTaskRunsBinding(): ToolSourceDescriptorBinding {
        val ownerId = "cap.schedule"
        return ToolSourceDescriptorBinding(
            identity = ToolSourceIdentity(
                sourceKind = PluginToolSourceKind.ACTIVE_CAPABILITY,
                ownerId = ownerId,
                sourceRef = "list_future_task_runs",
                displayName = promptStrings.listFutureTaskRunsDisplayName,
            ),
            descriptor = PluginToolDescriptor(
                pluginId = ownerId,
                name = "list_future_task_runs",
                description = promptStrings.listFutureTaskRunsDescription,
                visibility = PluginToolVisibility.LLM_VISIBLE,
                sourceKind = PluginToolSourceKind.ACTIVE_CAPABILITY,
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "job_id" to mapOf("type" to "string", "description" to promptStrings.schemaJobIdDescription),
                        "limit" to mapOf("type" to "number", "description" to promptStrings.schemaRunsLimitDescription),
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
                displayName = promptStrings.updateFutureTaskDisplayName,
            ),
            descriptor = PluginToolDescriptor(
                pluginId = ownerId,
                name = "update_future_task",
                description = promptStrings.updateFutureTaskDescription,
                visibility = PluginToolVisibility.LLM_VISIBLE,
                sourceKind = PluginToolSourceKind.ACTIVE_CAPABILITY,
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "job_id" to mapOf("type" to "string", "description" to promptStrings.schemaJobIdDescription),
                        "name" to mapOf("type" to "string", "description" to promptStrings.schemaUpdatedShortTitleDescription),
                        "note" to mapOf("type" to "string", "description" to promptStrings.schemaUpdatedTaskInstructionDescription),
                        "enabled" to mapOf("type" to "boolean", "description" to promptStrings.schemaTaskEnabledDescription),
                        "status" to mapOf("type" to "string", "description" to promptStrings.schemaUpdatedTaskStatusDescription),
                        "run_at" to mapOf("type" to "string", "description" to promptStrings.schemaUpdatedRunAtDescription),
                        "cron_expression" to mapOf("type" to "string", "description" to promptStrings.schemaUpdatedCronExpressionDescription),
                        "timezone" to mapOf("type" to "string", "description" to promptStrings.schemaUpdatedTimezoneDescription),
                    ),
                    "required" to listOf("job_id"),
                ),
            ),
        )
    }

    private fun buildRunTaskNowBinding(): ToolSourceDescriptorBinding {
        return taskByIdBinding(
            sourceRef = "run_future_task_now",
            displayName = promptStrings.runFutureTaskNowDisplayName,
            description = promptStrings.runFutureTaskNowDescription,
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
                        "job_id" to mapOf("type" to "string", "description" to promptStrings.schemaJobIdDescription),
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

