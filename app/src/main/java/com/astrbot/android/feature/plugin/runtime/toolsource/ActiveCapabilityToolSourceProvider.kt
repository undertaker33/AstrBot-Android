package com.astrbot.android.feature.plugin.runtime.toolsource

import com.astrbot.android.feature.config.data.FeatureConfigRepository
import com.astrbot.android.core.common.logging.AppLogger
import com.astrbot.android.feature.plugin.runtime.PluginToolDescriptor
import com.astrbot.android.feature.plugin.runtime.PluginToolResult
import com.astrbot.android.feature.plugin.runtime.PluginToolResultStatus
import com.astrbot.android.feature.plugin.runtime.PluginToolSourceKind
import com.astrbot.android.feature.plugin.runtime.PluginToolVisibility

/**
 * Active capability tool source provider.
 *
 * The provider owns tool exposure and invocation formatting only. Task shaping,
 * target resolution, persistence, and WorkManager scheduling are delegated to
 * [ActiveCapabilityRuntimeFacade].
 */
class ActiveCapabilityToolSourceProvider(
    private val facadeOverride: ActiveCapabilityRuntimeFacade? = null,
) : FutureToolSourceProvider {
    override val sourceKind: PluginToolSourceKind = PluginToolSourceKind.ACTIVE_CAPABILITY

    var appContext: android.content.Context? = globalAppContext
        set(value) {
            field = value ?: globalAppContext
        }

    override suspend fun listBindings(
        context: ToolSourceRegistryIngestContext,
    ): List<ToolSourceDescriptorBinding> {
        val configProfile = FeatureConfigRepository.resolve(context.configProfileId)
        if (!configProfile.proactiveEnabled) return emptyList()

        return listOf(
            buildCreateTaskBinding(),
            buildDeleteTaskBinding(),
            buildListTasksBinding(),
        )
    }

    override suspend fun availabilityOf(
        identity: ToolSourceIdentity,
        context: ToolSourceAvailabilityContext,
    ): ToolSourceAvailability {
        val configProfile = FeatureConfigRepository.resolve(context.configProfileId)
        return if (configProfile.proactiveEnabled) {
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
                detailCode = "proactive_disabled",
                detailMessage = "Proactive capability is disabled in this config profile.",
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

    private fun facade(): ActiveCapabilityRuntimeFacade {
        return facadeOverride ?: ActiveCapabilityRuntimeFacade(
            scheduler = WorkManagerActiveCapabilityScheduler { appContext },
        )
    }

    private suspend fun handleCreateFutureTask(
        request: ToolSourceInvokeRequest,
    ): ActiveCapabilityToolInvocation {
        val result = facade().createFutureTask(
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
            text = facade().creationToJson(result).toString(2),
        )
    }

    private suspend fun handleDeleteFutureTask(payload: Map<String, Any?>): ActiveCapabilityToolInvocation {
        val json = facade().deleteFutureTask((payload["job_id"] as? String).orEmpty())
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
            text = facade().listFutureTasks().toString(2),
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

    companion object {
        internal var request_sessionId: String? = null

        @Volatile
        private var globalAppContext: android.content.Context? = null

        fun initialize(context: android.content.Context) {
            globalAppContext = context.applicationContext
        }
    }
}

private data class ActiveCapabilityToolInvocation(
    val status: PluginToolResultStatus,
    val errorCode: String? = null,
    val text: String,
)

