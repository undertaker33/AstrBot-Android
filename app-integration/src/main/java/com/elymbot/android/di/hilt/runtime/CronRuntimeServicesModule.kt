package com.elymbot.android.di.hilt.runtime

import android.content.Context
import com.elymbot.android.core.runtime.context.RuntimeContextResolverPort
import com.elymbot.android.core.runtime.llm.LlmClientPort
import com.elymbot.android.feature.bot.domain.BotRepositoryPort
import com.elymbot.android.feature.conversation.domain.ConversationRepositoryPort
import com.elymbot.android.feature.cron.domain.ActiveCapabilityNaturalLanguageLexicon
import com.elymbot.android.feature.cron.domain.ActiveCapabilityNaturalLanguageParser
import com.elymbot.android.feature.cron.domain.ActiveCapabilityPromptStrings
import com.elymbot.android.feature.cron.domain.ActiveCapabilityTaskPort
import com.elymbot.android.feature.cron.domain.CronJobRepositoryPort
import com.elymbot.android.feature.cron.domain.CronJobRunNowPort
import com.elymbot.android.feature.cron.domain.CronSchedulerPort
import com.elymbot.android.feature.cron.runtime.AndroidActiveCapabilityPromptStrings
import com.elymbot.android.feature.cron.runtime.CoordinatorCronJobRunNowPort
import com.elymbot.android.feature.cron.runtime.CronJobRunCoordinator
import com.elymbot.android.feature.cron.runtime.CronRescheduler
import com.elymbot.android.feature.cron.runtime.CronRuntimeService
import com.elymbot.android.feature.cron.runtime.FeatureCronSchedulerPortAdapter
import com.elymbot.android.feature.cron.runtime.PluginScheduledTaskDispatchPort
import com.elymbot.android.feature.cron.runtime.ScheduledMessageDeliveryPort
import com.elymbot.android.feature.cron.runtime.ScheduledTaskExecutor
import com.elymbot.android.feature.cron.runtime.ScheduledTaskRuntimeDependencies
import com.elymbot.android.feature.cron.runtime.ScheduledTaskRuntimeExecutor
import com.elymbot.android.feature.cron.runtime.WorkManagerCronRescheduler
import com.elymbot.android.feature.cron.runtime.activeCapabilityNaturalLanguageLexiconFromResources
import com.elymbot.android.feature.plugin.runtime.AppChatLlmPipelineRuntime
import com.elymbot.android.feature.plugin.runtime.AppChatPluginRuntime
import com.elymbot.android.feature.plugin.runtime.PluginHostCapabilityGateway
import com.elymbot.android.feature.plugin.runtime.PluginV2ActiveRuntimeStore
import com.elymbot.android.feature.plugin.runtime.PluginV2ScheduledDispatchEngine
import com.elymbot.android.feature.plugin.runtime.RuntimeLlmOrchestratorPort
import com.elymbot.android.feature.qq.domain.QqScheduledMessageSender
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object CronRuntimeServicesModule {

    @Provides
    @Singleton
    fun provideScheduledTaskRuntimeDependencies(
        llmClientPort: LlmClientPort,
        botPort: BotRepositoryPort,
        orchestrator: RuntimeLlmOrchestratorPort,
        runtimeContextResolverPort: RuntimeContextResolverPort,
        deliveryPort: ScheduledMessageDeliveryPort,
        appChatPluginRuntime: AppChatPluginRuntime,
        hostCapabilityGateway: PluginHostCapabilityGateway,
    ): ScheduledTaskRuntimeDependencies {
        return ScheduledTaskRuntimeDependencies(
            llmClient = llmClientPort,
            botPort = botPort,
            orchestrator = orchestrator,
            runtimeContextResolverPort = runtimeContextResolverPort,
            deliveryPort = deliveryPort,
            appChatPluginRuntime = appChatPluginRuntime as AppChatLlmPipelineRuntime,
            hostCapabilityGateway = hostCapabilityGateway,
        )
    }

    @Provides
    @Singleton
    fun provideScheduledMessageDeliveryPort(
        conversationPort: ConversationRepositoryPort,
        qqScheduledMessageSender: QqScheduledMessageSender,
    ): ScheduledMessageDeliveryPort = DefaultScheduledMessageDeliveryPort(
        conversationPort = conversationPort,
        qqScheduledMessageSender = qqScheduledMessageSender,
    )

    @Provides
    @Singleton
    fun provideScheduledTaskExecutor(
        dependencies: ScheduledTaskRuntimeDependencies,
        executor: ScheduledTaskRuntimeExecutor,
    ): ScheduledTaskExecutor {
        return ScheduledTaskExecutor { context ->
            executor.execute(context, dependencies)
        }
    }

    @Provides
    @Singleton
    fun providePluginScheduledTaskDispatchPort(
        activeRuntimeStore: PluginV2ActiveRuntimeStore,
        dispatchEngine: PluginV2ScheduledDispatchEngine,
    ): PluginScheduledTaskDispatchPort = PluginV2ScheduledTaskDispatchPortAdapter(
        activeRuntimeStore = activeRuntimeStore,
        dispatchEngine = dispatchEngine,
    )

    @Provides
    @Singleton
    fun provideCronSchedulerPort(
        adapter: FeatureCronSchedulerPortAdapter,
    ): CronSchedulerPort = adapter

    @Provides
    @Singleton
    fun provideActiveCapabilityNaturalLanguageLexicon(
        @ApplicationContext appContext: Context,
    ): ActiveCapabilityNaturalLanguageLexicon =
        activeCapabilityNaturalLanguageLexiconFromResources(appContext)

    @Provides
    @Singleton
    fun provideActiveCapabilityNaturalLanguageParser(
        lexicon: ActiveCapabilityNaturalLanguageLexicon,
    ): ActiveCapabilityNaturalLanguageParser = ActiveCapabilityNaturalLanguageParser(lexicon)

    @Provides
    fun provideActiveCapabilityPromptStrings(
        strings: AndroidActiveCapabilityPromptStrings,
    ): ActiveCapabilityPromptStrings = strings

    @Provides
    @Singleton
    fun provideActiveCapabilityTaskPort(
        runtimeService: CronRuntimeService,
    ): ActiveCapabilityTaskPort = runtimeService

    @Provides
    fun provideCronJobRunNowPort(
        port: CoordinatorCronJobRunNowPort,
    ): CronJobRunNowPort = port

    @Provides
    fun provideCronRescheduler(
        rescheduler: WorkManagerCronRescheduler,
    ): CronRescheduler = rescheduler

    @Provides
    fun provideCronJobRunCoordinator(
        repository: CronJobRepositoryPort,
        scheduler: CronRescheduler,
        executor: ScheduledTaskExecutor,
        pluginScheduledTaskDispatchPort: PluginScheduledTaskDispatchPort,
    ): CronJobRunCoordinator = CronJobRunCoordinator(
        repository = repository,
        scheduler = scheduler,
        executor = executor,
        pluginScheduledTaskDispatchPort = pluginScheduledTaskDispatchPort,
    )
}
