package com.elymbot.android.feature.plugin.runtime.toolsource

import com.elymbot.android.core.common.logging.RuntimeLogger
import com.elymbot.android.core.runtime.tool.DefaultToolDescriptorCachePolicy
import com.elymbot.android.core.runtime.tool.ToolDescriptor
import com.elymbot.android.core.runtime.tool.ToolDescriptorCachePolicy
import com.elymbot.android.core.runtime.tool.ToolSourceRequestContext
import com.elymbot.android.feature.plugin.runtime.PluginToolSourceBridge
import com.elymbot.android.feature.plugin.runtime.PluginToolDescriptor
import com.elymbot.android.feature.plugin.runtime.PluginToolSourceKind
import com.elymbot.android.feature.plugin.runtime.PluginV2ContextCompressApi
import com.elymbot.android.feature.plugin.runtime.PluginV2ConversationHistoryReadPort
import com.elymbot.android.feature.plugin.runtime.PluginV2HostApiAsyncBridge
import com.elymbot.android.feature.plugin.runtime.PluginV2HostApiAuditLogger
import com.elymbot.android.feature.plugin.runtime.PluginV2HostApiFacade
import com.elymbot.android.feature.plugin.runtime.PluginV2HostApiPermissionPolicy
import com.elymbot.android.feature.plugin.runtime.PluginV2HostLlmPort
import com.elymbot.android.feature.plugin.runtime.PluginV2HostLlmPortRequest
import com.elymbot.android.core.runtime.context.RuntimePlatform
import com.elymbot.android.feature.cron.domain.EmptyActiveCapabilityPromptStrings
import com.elymbot.android.feature.plugin.runtime.InMemoryPluginRuntimeLogBus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Aggregates all [FutureToolSourceProvider] instances and produces a unified list
 * of [PluginToolDescriptor]s for the centralized tool registry compiler.
 *
 * This is the single gateway that [PluginV2ToolSourceGateway] and the host capability
 * system delegate to when resolving non-PLUGIN_V2/HOST_BUILTIN source kinds.
 */
@Singleton
class FutureToolSourceRegistry @Inject constructor(
    private val contextResolver: FutureToolSourceContextResolver,
    mcpToolSourceProvider: McpToolSourceProvider,
    skillToolSourceProvider: SkillToolSourceProvider,
    activeCapabilityToolSourceProvider: ActiveCapabilityToolSourceProvider,
    contextStrategyToolSourceProvider: ContextStrategyToolSourceProvider,
    webSearchToolSourceProvider: WebSearchToolSourceProvider,
) {
    private val cachePolicy: ToolDescriptorCachePolicy = DefaultToolDescriptorCachePolicy()
    private val providers: List<FutureToolSourceProvider> = listOf(
        mcpToolSourceProvider,
        skillToolSourceProvider,
        activeCapabilityToolSourceProvider,
        contextStrategyToolSourceProvider,
        webSearchToolSourceProvider,
    )

    private val providersByKind: Map<PluginToolSourceKind, FutureToolSourceProvider> =
        providers.associateBy { it.sourceKind }
    private val bridgesByKind: Map<PluginToolSourceKind, PluginToolSourceBridge> =
        providers.associate { provider ->
            provider.sourceKind to PluginToolSourceBridge(
                provider = provider,
                cachePolicy = cachePolicy,
            )
        }

    suspend fun collectToolDescriptors(
        configProfileId: String,
    ): List<PluginToolDescriptor> {
        return collectContractDescriptors(configProfileId).map(ToolDescriptor::toPluginToolDescriptor)
    }

    suspend fun collectToolDescriptors(
        toolSourceContext: ToolSourceContext,
    ): List<PluginToolDescriptor> {
        return collectContractDescriptors(toolSourceContext).map(ToolDescriptor::toPluginToolDescriptor)
    }

    suspend fun collectContractDescriptors(
        configProfileId: String,
    ): List<ToolDescriptor> {
        val requestContext = ToolSourceRequestContext(
            botId = "",
            configId = configProfileId,
            personaId = "",
            conversationId = "",
        )
        return providers.flatMap { provider ->
            bridgesByKind.getValue(provider.sourceKind).descriptors(requestContext)
        }
    }

    suspend fun collectContractDescriptors(
        toolSourceContext: ToolSourceContext,
    ): List<ToolDescriptor> {
        return providers.flatMap { provider ->
            provider.listBindings(
                context = ToolSourceRegistryIngestContext(toolSourceContext = toolSourceContext),
            ).map(ToolSourceDescriptorBinding::toContractDescriptor)
        }
    }

    suspend fun isAvailable(
        sourceKind: PluginToolSourceKind,
        ownerId: String,
        configProfileId: String,
    ): Boolean {
        val provider = providersByKind[sourceKind] ?: return false
        val identity = ToolSourceIdentity(
            sourceKind = sourceKind,
            ownerId = ownerId,
            sourceRef = "",
            displayName = "",
        )
        val availability = provider.availabilityOf(
            identity = identity,
            context = ToolSourceAvailabilityContext(toolSourceContext = contextForConfig(configProfileId)),
        )
        return availability.providerReachable && availability.capabilityAllowed
    }

    suspend fun isAvailable(
        sourceKind: PluginToolSourceKind,
        ownerId: String,
        toolSourceContext: ToolSourceContext,
    ): Boolean {
        val provider = providersByKind[sourceKind] ?: return false
        val identity = ToolSourceIdentity(
            sourceKind = sourceKind,
            ownerId = ownerId,
            sourceRef = "",
            displayName = "",
        )
        val availability = provider.availabilityOf(
            identity = identity,
            context = ToolSourceAvailabilityContext(toolSourceContext = toolSourceContext),
        )
        return availability.providerReachable && availability.capabilityAllowed
    }

    suspend fun invoke(
        request: ToolSourceInvokeRequest,
    ): ToolSourceInvokeResult? {
        val provider = providersByKind[request.identity.sourceKind] ?: return null
        return provider.invoke(request)
    }

    fun providerFor(sourceKind: PluginToolSourceKind): FutureToolSourceProvider? {
        return providersByKind[sourceKind]
    }

    fun contextForConfig(configProfileId: String): ToolSourceContext {
        return contextResolver.resolveForConfig(configProfileId)
    }

    fun contextForRequest(requestContext: ToolSourceRequestContext): ToolSourceContext {
        return contextResolver.resolveForRequest(requestContext)
    }

    companion object {
        fun empty(): FutureToolSourceRegistry {
            val runtimeLogger = RuntimeLogger.noop()
            val resolver = object : FutureToolSourceContextResolver {
                override fun resolveForConfig(configProfileId: String): ToolSourceContext {
                    return ToolSourceContext(
                        requestId = "",
                        platform = RuntimePlatform.APP_CHAT,
                        configProfileId = configProfileId,
                        webSearchEnabled = false,
                        activeCapabilityEnabled = false,
                        mcpServers = emptyList(),
                        promptSkills = emptyList(),
                        toolSkills = emptyList(),
                        conversationId = "",
                    )
                }
            }
            return FutureToolSourceRegistry(
                contextResolver = resolver,
                mcpToolSourceProvider = McpToolSourceProvider(
                    contextResolver = resolver,
                    runtimeLogger = runtimeLogger,
                ),
                skillToolSourceProvider = SkillToolSourceProvider(contextResolver = resolver),
                activeCapabilityToolSourceProvider = ActiveCapabilityToolSourceProvider(
                    facade = ActiveCapabilityRuntimeFacade(
                        repository = EmptyCronJobRepositoryPort(),
                        scheduler = EmptyCronSchedulerPort,
                        promptStrings = EmptyActiveCapabilityPromptStrings(),
                        runtimeLogger = runtimeLogger,
                    ),
                    promptStrings = EmptyActiveCapabilityPromptStrings(),
                    contextResolver = resolver,
                    runtimeLogger = runtimeLogger,
                ),
                contextStrategyToolSourceProvider = ContextStrategyToolSourceProvider(
                    contextResolver = resolver,
                    contextCompressor = emptyContextCompressApi(),
                ),
                webSearchToolSourceProvider = WebSearchToolSourceProvider(
                    searchPort = EmptyUnifiedSearchPort,
                    contextResolver = resolver,
                    runtimeLogger = runtimeLogger,
                ),
            )
        }

        private fun emptyContextCompressApi(): PluginV2ContextCompressApi {
            val logBus = InMemoryPluginRuntimeLogBus()
            return PluginV2ContextCompressApi(
                facade = PluginV2HostApiFacade(
                    permissionPolicy = PluginV2HostApiPermissionPolicy(),
                    asyncBridge = PluginV2HostApiAsyncBridge(),
                    auditLogger = PluginV2HostApiAuditLogger(logBus = logBus),
                ),
                historyReader = PluginV2ConversationHistoryReadPort { emptyList() },
                llmPort = PluginV2HostLlmPort { request: PluginV2HostLlmPortRequest ->
                    throw UnsupportedOperationException(
                        "Empty FutureToolSourceRegistry does not support context compression for ${request.pluginId}",
                    )
                },
            )
        }
    }
}

private object EmptyCronSchedulerPort : com.elymbot.android.feature.cron.domain.CronSchedulerPort {
    override fun schedule(job: com.elymbot.android.feature.cron.domain.model.CronJob) = Unit

    override fun cancel(jobId: String) = Unit

    override fun cancelAll() = Unit
}

private class EmptyCronJobRepositoryPort : com.elymbot.android.feature.cron.domain.CronJobRepositoryPort {
    override val jobs = kotlinx.coroutines.flow.MutableStateFlow(emptyList<com.elymbot.android.feature.cron.domain.model.CronJob>())

    override suspend fun create(job: com.elymbot.android.feature.cron.domain.model.CronJob): com.elymbot.android.feature.cron.domain.model.CronJob = job

    override suspend fun update(job: com.elymbot.android.feature.cron.domain.model.CronJob): com.elymbot.android.feature.cron.domain.model.CronJob = job

    override suspend fun delete(jobId: String) = Unit

    override suspend fun getByJobId(jobId: String): com.elymbot.android.feature.cron.domain.model.CronJob? = null

    override suspend fun listAll(): List<com.elymbot.android.feature.cron.domain.model.CronJob> = emptyList()

    override suspend fun listEnabled(): List<com.elymbot.android.feature.cron.domain.model.CronJob> = emptyList()

    override suspend fun updateStatus(jobId: String, status: String, lastRunAt: Long?, lastError: String?) = Unit

    override suspend fun recordExecutionStarted(
        record: com.elymbot.android.feature.cron.domain.model.CronJobExecutionRecord,
    ): com.elymbot.android.feature.cron.domain.model.CronJobExecutionRecord = record

    override suspend fun updateExecutionRecord(
        record: com.elymbot.android.feature.cron.domain.model.CronJobExecutionRecord,
    ): com.elymbot.android.feature.cron.domain.model.CronJobExecutionRecord = record

    override suspend fun listRecentExecutionRecords(
        jobId: String,
        limit: Int,
    ): List<com.elymbot.android.feature.cron.domain.model.CronJobExecutionRecord> = emptyList()

    override suspend fun latestExecutionRecord(
        jobId: String,
    ): com.elymbot.android.feature.cron.domain.model.CronJobExecutionRecord? = null
}

private object EmptyUnifiedSearchPort : com.elymbot.android.core.runtime.search.UnifiedSearchPort {
    override suspend fun search(
        request: com.elymbot.android.core.runtime.search.UnifiedSearchRequest,
    ): com.elymbot.android.core.runtime.search.UnifiedSearchResponse {
        throw UnsupportedOperationException("Empty FutureToolSourceRegistry does not support web search")
    }
}

