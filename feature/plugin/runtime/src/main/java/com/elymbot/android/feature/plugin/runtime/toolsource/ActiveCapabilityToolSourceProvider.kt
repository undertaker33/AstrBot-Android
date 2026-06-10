package com.elymbot.android.feature.plugin.runtime.toolsource

import com.elymbot.android.core.common.logging.RuntimeLogger
import com.elymbot.android.core.runtime.context.IngressTrigger
import com.elymbot.android.feature.cron.domain.ActiveCapabilityPromptStrings
import com.elymbot.android.feature.plugin.domain.runtime.DisabledGeofenceActiveCapabilityFacade
import com.elymbot.android.feature.plugin.domain.runtime.GeofenceActiveCapabilityFacade
import com.elymbot.android.feature.plugin.runtime.PluginToolDescriptor
import com.elymbot.android.feature.plugin.runtime.PluginToolResult
import com.elymbot.android.feature.plugin.runtime.PluginToolResultStatus
import com.elymbot.android.feature.plugin.runtime.PluginToolSourceKind
import com.elymbot.android.feature.plugin.runtime.PluginToolVisibility
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
    private val geofenceFacade: GeofenceActiveCapabilityFacade = DisabledGeofenceActiveCapabilityFacade,
    private val promptStrings: ActiveCapabilityPromptStrings,
    override val contextResolver: FutureToolSourceContextResolver,
    private val runtimeLogger: RuntimeLogger,
) : FutureToolSourceProvider {
    override val sourceKind: PluginToolSourceKind = PluginToolSourceKind.ACTIVE_CAPABILITY

    override suspend fun listBindings(
        context: ToolSourceRegistryIngestContext,
    ): List<ToolSourceDescriptorBinding> {
        if (!context.toolSourceContext.activeCapabilityEnabled) return emptyList()
        if (context.toolSourceContext.ingressTrigger == IngressTrigger.SCHEDULED_TASK) return emptyList()

        val scheduledTaskBindings = listOf(
            buildCreateTaskBinding(),
            buildDeleteTaskBinding(),
            buildListTasksBinding(),
            buildPauseTaskBinding(),
            buildResumeTaskBinding(),
            buildListTaskRunsBinding(),
            buildUpdateTaskBinding(),
            buildRunTaskNowBinding(),
        )
        val geofenceBindings = if (context.toolSourceContext.ingressTrigger == IngressTrigger.GEOFENCE_EVENT) {
            listOf(buildListGeofenceRulesBinding())
        } else {
            listOf(
                buildCreateGeofenceRuleBinding(),
                buildUpdateGeofenceRuleBinding(),
                buildListGeofenceRulesBinding(),
                buildDeleteGeofenceRuleBinding(),
                buildPauseGeofenceRuleBinding(),
                buildResumeGeofenceRuleBinding(),
            )
        }
        return scheduledTaskBindings + geofenceBindings
    }

    override suspend fun availabilityOf(
        identity: ToolSourceIdentity,
        context: ToolSourceAvailabilityContext,
    ): ToolSourceAvailability {
        val geofenceMutationHidden = context.toolSourceContext.ingressTrigger == IngressTrigger.GEOFENCE_EVENT &&
            identity.sourceRef in GEOFENCE_MUTATING_TOOL_NAMES
        return when {
            !context.toolSourceContext.activeCapabilityEnabled ||
                context.toolSourceContext.ingressTrigger == IngressTrigger.SCHEDULED_TASK -> ToolSourceAvailability(
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
            geofenceMutationHidden -> ToolSourceAvailability(
                providerReachable = true,
                permissionGranted = true,
                capabilityAllowed = false,
                detailCode = GEOFENCE_MUTATION_HIDDEN_DURING_EVENT,
                detailMessage = "Geofence mutation tools are hidden during geofence event ingress.",
            )
            else -> ToolSourceAvailability(
                providerReachable = true,
                permissionGranted = true,
                capabilityAllowed = true,
            )
        }
    }

    // skipcq: KT-R1006
    override suspend fun invoke(
        request: ToolSourceInvokeRequest,
    ): ToolSourceInvokeResult {
        val toolName = request.args.toolId.substringAfter(":")
        if (
            request.toolSourceContext?.ingressTrigger == IngressTrigger.GEOFENCE_EVENT &&
            toolName in GEOFENCE_MUTATING_TOOL_NAMES
        ) {
            return ToolSourceInvokeResult(
                result = PluginToolResult(
                    toolCallId = request.args.toolCallId,
                    requestId = request.args.requestId,
                    toolId = request.args.toolId,
                    status = PluginToolResultStatus.ERROR,
                    errorCode = GEOFENCE_MUTATION_HIDDEN_DURING_EVENT,
                    text = org.json.JSONObject().apply {
                        // skipcq: KT-W1042
                        put("success", false)
                        // skipcq: KT-W1042
                        put("error_code", GEOFENCE_MUTATION_HIDDEN_DURING_EVENT)
                        put("message", "Geofence mutation tools are hidden during geofence event ingress.")
                    }.toString(2),
                ),
            )
        }
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
                // skipcq: KT-W1042
                "create_geofence_rule" -> handleCreateGeofenceRule(request)
                // skipcq: KT-W1042
                "update_geofence_rule" -> handleUpdateGeofenceRule(request)
                // skipcq: KT-W1042
                "list_geofence_rules" -> handleGeofenceManagement(geofenceFacade.listRules())
                // skipcq: KT-W1042
                "delete_geofence_rule" -> handleGeofenceManagement(
                    // skipcq: KT-W1042
                    geofenceFacade.deleteRule(request.args.payload.stringValue("rule_id")),
                )
                // skipcq: KT-W1042
                "pause_geofence_rule" -> handleGeofenceManagement(
                    geofenceFacade.pauseRule(request.args.payload.stringValue("rule_id")),
                )
                // skipcq: KT-W1042
                "resume_geofence_rule" -> handleGeofenceManagement(
                    geofenceFacade.resumeRule(request.args.payload.stringValue("rule_id")),
                )
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
            runtimeLogger.append("ActiveCapability invoke error: ${e.message}")
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

    private suspend fun handleCreateGeofenceRule(request: ToolSourceInvokeRequest): ActiveCapabilityToolInvocation {
        return handleGeofenceManagement(
            geofenceFacade.createRule(
                payload = request.args.payload,
                metadata = request.args.metadata,
                toolSourceContext = request.toolSourceContext,
            ),
        )
    }

    private suspend fun handleUpdateGeofenceRule(request: ToolSourceInvokeRequest): ActiveCapabilityToolInvocation {
        return handleGeofenceManagement(
            geofenceFacade.updateRule(
                payload = request.args.payload,
                metadata = request.args.metadata,
                toolSourceContext = request.toolSourceContext,
            ),
        )
    }

    private fun handleGeofenceManagement(json: org.json.JSONObject): ActiveCapabilityToolInvocation {
        val success = json.optBoolean("success", false)
        return ActiveCapabilityToolInvocation(
            status = if (success) PluginToolResultStatus.SUCCESS else PluginToolResultStatus.ERROR,
            errorCode = if (success) null else json.optString("error_code", "geofence_error"),
            text = json.toString(2),
        )
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
                        // skipcq: KT-W1042
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
                        // skipcq: KT-W1042
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

    private fun buildCreateGeofenceRuleBinding(): ToolSourceDescriptorBinding {
        val ownerId = GEOFENCE_OWNER_ID
        return ToolSourceDescriptorBinding(
            identity = ToolSourceIdentity(
                sourceKind = PluginToolSourceKind.ACTIVE_CAPABILITY,
                ownerId = ownerId,
                sourceRef = "create_geofence_rule",
                displayName = "Create geofence rule",
            ),
            descriptor = PluginToolDescriptor(
                pluginId = ownerId,
                name = "create_geofence_rule",
                description = "Create a host-owned geofence rule from explicit coordinates or a permitted current-location request.",
                visibility = PluginToolVisibility.LLM_VISIBLE,
                sourceKind = PluginToolSourceKind.ACTIVE_CAPABILITY,
                inputSchema = GeofenceActiveCapabilityToolSchemas.createOrUpdateRuleSchema(requiredForCreate = true),
            ),
        )
    }

    private fun buildUpdateGeofenceRuleBinding(): ToolSourceDescriptorBinding {
        val ownerId = GEOFENCE_OWNER_ID
        return ToolSourceDescriptorBinding(
            identity = ToolSourceIdentity(
                sourceKind = PluginToolSourceKind.ACTIVE_CAPABILITY,
                ownerId = ownerId,
                sourceRef = "update_geofence_rule",
                displayName = "Update geofence rule",
            ),
            descriptor = PluginToolDescriptor(
                pluginId = ownerId,
                name = "update_geofence_rule",
                description = "Update an existing host-owned geofence rule and reconcile runtime registration.",
                visibility = PluginToolVisibility.LLM_VISIBLE,
                sourceKind = PluginToolSourceKind.ACTIVE_CAPABILITY,
                inputSchema = GeofenceActiveCapabilityToolSchemas.createOrUpdateRuleSchema(requiredForCreate = false),
            ),
        )
    }

    private fun buildListGeofenceRulesBinding(): ToolSourceDescriptorBinding {
        val ownerId = GEOFENCE_OWNER_ID
        return ToolSourceDescriptorBinding(
            identity = ToolSourceIdentity(
                sourceKind = PluginToolSourceKind.ACTIVE_CAPABILITY,
                ownerId = ownerId,
                sourceRef = "list_geofence_rules",
                displayName = "List geofence rules",
            ),
            descriptor = PluginToolDescriptor(
                pluginId = ownerId,
                name = "list_geofence_rules",
                description = "List host-owned geofence rule summaries visible to the current Agent context.",
                visibility = PluginToolVisibility.LLM_VISIBLE,
                sourceKind = PluginToolSourceKind.ACTIVE_CAPABILITY,
                inputSchema = mapOf("type" to "object"),
            ),
        )
    }

    private fun buildDeleteGeofenceRuleBinding(): ToolSourceDescriptorBinding =
        geofenceRuleByIdBinding(
            sourceRef = "delete_geofence_rule",
            displayName = "Delete geofence rule",
            description = "Delete a host-owned geofence rule and reconcile runtime registration.",
        )

    private fun buildPauseGeofenceRuleBinding(): ToolSourceDescriptorBinding =
        geofenceRuleByIdBinding(
            sourceRef = "pause_geofence_rule",
            displayName = "Pause geofence rule",
            description = "Pause a host-owned geofence rule and reconcile runtime registration.",
        )

    private fun buildResumeGeofenceRuleBinding(): ToolSourceDescriptorBinding =
        geofenceRuleByIdBinding(
            sourceRef = "resume_geofence_rule",
            displayName = "Resume geofence rule",
            description = "Resume a host-owned geofence rule and reconcile runtime registration.",
        )

    private fun geofenceRuleByIdBinding(
        sourceRef: String,
        displayName: String,
        description: String,
    ): ToolSourceDescriptorBinding {
        val ownerId = GEOFENCE_OWNER_ID
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
                        "rule_id" to mapOf("type" to "string", "description" to "Host geofence rule id."),
                    ),
                    "required" to listOf("rule_id"),
                ),
            ),
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

private object GeofenceActiveCapabilityToolSchemas {
    fun createOrUpdateRuleSchema(requiredForCreate: Boolean): Map<String, Any?> {
        val properties = mapOf(
            "rule_id" to mapOf("type" to "string", "description" to "Required when updating an existing rule."),
            "name" to mapOf("type" to "string", "description" to "Rule display name."),
            "description" to mapOf("type" to "string", "description" to "Optional rule description."),
            "latitude" to mapOf("type" to "number", "description" to "Geofence center latitude. Do not guess."),
            "longitude" to mapOf("type" to "number", "description" to "Geofence center longitude. Do not guess."),
            "use_current_location" to mapOf("type" to "boolean", "description" to "Use current location only when the host has permission."),
            "radius_meters" to mapOf("type" to "number", "description" to "Geofence radius in meters, minimum 50."),
            "region_label" to mapOf("type" to "string", "description" to "Human-readable region label."),
            "trigger" to mapOf(
                "type" to "string",
                "enum" to listOf("enter", "exit", "dwell", "enter_exit"),
                "description" to "Transition that triggers the rule.",
            ),
            "dwell_delay_millis" to mapOf("type" to "number", "description" to "Required for dwell trigger."),
            "action_type" to mapOf(
                "type" to "string",
                "enum" to listOf("agent_prompt", "send_message", "weather_forecast", "news_digest", "host_capability"),
                "description" to "Geofence action type.",
            ),
            "action_prompt" to mapOf("type" to "string", "description" to "Prompt or message to run when triggered."),
            "target_platform" to mapOf("type" to "string", "description" to "app_chat or qq_onebot."),
            "conversation_id" to mapOf("type" to "string", "description" to "Target host conversation id."),
            "bot_id" to mapOf("type" to "string", "description" to "Target host bot id."),
            "minimum_trigger_interval_millis" to mapOf("type" to "number", "description" to "Minimum interval between executions."),
            "enabled" to mapOf("type" to "boolean", "description" to "Whether the rule should be enabled."),
        )
        return mapOf(
            "type" to "object",
            "properties" to properties,
            "required" to if (requiredForCreate) {
                listOf("name", "radius_meters", "trigger", "action_type", "action_prompt")
            } else {
                listOf("rule_id")
            },
        )
    }
}

private const val GEOFENCE_OWNER_ID = "cap.geofence"
private const val GEOFENCE_MUTATION_HIDDEN_DURING_EVENT = "geofence_tools_hidden_during_geofence_event"
private val GEOFENCE_MUTATING_TOOL_NAMES = setOf(
    "create_geofence_rule",
    "update_geofence_rule",
    "delete_geofence_rule",
    "pause_geofence_rule",
    "resume_geofence_rule",
)

private fun Map<String, Any?>.stringValue(key: String): String =
    (this[key] as? String)?.trim().orEmpty()

