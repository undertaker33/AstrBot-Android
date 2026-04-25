package com.astrbot.android.feature.plugin.runtime.toolsource

import com.astrbot.android.core.runtime.tool.DefaultToolDescriptorCachePolicy
import com.astrbot.android.core.runtime.tool.ToolDescriptor
import com.astrbot.android.core.runtime.tool.ToolDescriptorCachePolicy
import com.astrbot.android.core.runtime.tool.ToolSourceRequestContext
import com.astrbot.android.feature.plugin.runtime.PluginToolSourceBridge
import com.astrbot.android.feature.plugin.runtime.PluginToolDescriptor
import com.astrbot.android.feature.plugin.runtime.PluginToolSourceKind
import com.astrbot.android.core.runtime.context.RuntimePlatform
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
                mcpToolSourceProvider = McpToolSourceProvider(contextResolver = resolver),
                skillToolSourceProvider = SkillToolSourceProvider(contextResolver = resolver),
                activeCapabilityToolSourceProvider = ActiveCapabilityToolSourceProvider(
                    facade = ActiveCapabilityRuntimeFacade(
                        repository = EmptyCronJobRepositoryPort(),
                        scheduler = EmptyCronSchedulerPort,
                    ),
                    contextResolver = resolver,
                ),
                contextStrategyToolSourceProvider = ContextStrategyToolSourceProvider(contextResolver = resolver),
                webSearchToolSourceProvider = WebSearchToolSourceProvider(
                    searchPort = EmptyUnifiedSearchPort,
                    contextResolver = resolver,
                ),
            )
        }
    }
}

private object EmptyCronSchedulerPort : com.astrbot.android.feature.cron.domain.CronSchedulerPort {
    override fun schedule(job: com.astrbot.android.model.CronJob) = Unit

    override fun cancel(jobId: String) = Unit

    override fun cancelAll() = Unit
}

private class EmptyCronJobRepositoryPort : com.astrbot.android.feature.cron.domain.CronJobRepositoryPort {
    override val jobs = kotlinx.coroutines.flow.MutableStateFlow(emptyList<com.astrbot.android.model.CronJob>())

    override suspend fun create(job: com.astrbot.android.model.CronJob): com.astrbot.android.model.CronJob = job

    override suspend fun update(job: com.astrbot.android.model.CronJob): com.astrbot.android.model.CronJob = job

    override suspend fun delete(jobId: String) = Unit

    override suspend fun getByJobId(jobId: String): com.astrbot.android.model.CronJob? = null

    override suspend fun listAll(): List<com.astrbot.android.model.CronJob> = emptyList()

    override suspend fun listEnabled(): List<com.astrbot.android.model.CronJob> = emptyList()

    override suspend fun updateStatus(jobId: String, status: String, lastRunAt: Long?, lastError: String?) = Unit

    override suspend fun recordExecutionStarted(
        record: com.astrbot.android.model.CronJobExecutionRecord,
    ): com.astrbot.android.model.CronJobExecutionRecord = record

    override suspend fun updateExecutionRecord(
        record: com.astrbot.android.model.CronJobExecutionRecord,
    ): com.astrbot.android.model.CronJobExecutionRecord = record

    override suspend fun listRecentExecutionRecords(
        jobId: String,
        limit: Int,
    ): List<com.astrbot.android.model.CronJobExecutionRecord> = emptyList()

    override suspend fun latestExecutionRecord(
        jobId: String,
    ): com.astrbot.android.model.CronJobExecutionRecord? = null
}

private object EmptyUnifiedSearchPort : com.astrbot.android.core.runtime.search.UnifiedSearchPort {
    override suspend fun search(
        request: com.astrbot.android.core.runtime.search.UnifiedSearchRequest,
    ): com.astrbot.android.core.runtime.search.UnifiedSearchResponse {
        throw UnsupportedOperationException("Empty FutureToolSourceRegistry does not support web search")
    }
}

