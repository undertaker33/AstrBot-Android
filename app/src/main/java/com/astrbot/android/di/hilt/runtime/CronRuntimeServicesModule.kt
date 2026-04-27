package com.astrbot.android.di.hilt.runtime

import android.content.Context
import com.astrbot.android.core.runtime.context.RuntimeContextResolverPort
import com.astrbot.android.core.runtime.llm.LlmClientPort
import com.astrbot.android.feature.bot.domain.BotRepositoryPort
import com.astrbot.android.feature.chat.domain.ConversationRepositoryPort
import com.astrbot.android.feature.cron.data.FeatureCronSchedulerPortAdapter
import com.astrbot.android.feature.cron.domain.ActiveCapabilityTaskPort
import com.astrbot.android.feature.cron.domain.CronJobRepositoryPort
import com.astrbot.android.feature.cron.domain.CronJobRunNowPort
import com.astrbot.android.feature.cron.domain.CronSchedulerPort
import com.astrbot.android.feature.cron.runtime.ActiveCapabilityPromptStrings
import com.astrbot.android.feature.cron.runtime.AndroidActiveCapabilityPromptStrings
import com.astrbot.android.feature.cron.runtime.CoordinatorCronJobRunNowPort
import com.astrbot.android.feature.cron.runtime.CronJobRunCoordinator
import com.astrbot.android.feature.cron.runtime.CronRescheduler
import com.astrbot.android.feature.cron.runtime.CronRuntimeService
import com.astrbot.android.feature.cron.runtime.DefaultScheduledMessageDeliveryPort
import com.astrbot.android.feature.cron.runtime.ScheduledMessageDeliveryPort
import com.astrbot.android.feature.cron.runtime.ScheduledTaskExecutor
import com.astrbot.android.feature.cron.runtime.ScheduledTaskRuntimeDependencies
import com.astrbot.android.feature.cron.runtime.ScheduledTaskRuntimeExecutor
import com.astrbot.android.feature.cron.runtime.WorkManagerCronRescheduler
import com.astrbot.android.feature.plugin.runtime.AppChatLlmPipelineRuntime
import com.astrbot.android.feature.plugin.runtime.AppChatPluginRuntime
import com.astrbot.android.feature.plugin.runtime.PluginHostCapabilityGateway
import com.astrbot.android.feature.plugin.runtime.RuntimeLlmOrchestratorPort
import com.astrbot.android.feature.plugin.runtime.toolsource.ActiveCapabilityNaturalLanguageLexicon
import com.astrbot.android.feature.plugin.runtime.toolsource.ActiveCapabilityNaturalLanguageParser
import com.astrbot.android.feature.qq.runtime.QqScheduledMessageSender
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
    ): ScheduledTaskExecutor {
        return ScheduledTaskExecutor { context ->
            ScheduledTaskRuntimeExecutor.execute(context, dependencies)
        }
    }

    @Provides
    @Singleton
    fun provideCronSchedulerPort(
        @ApplicationContext appContext: Context,
    ): CronSchedulerPort = FeatureCronSchedulerPortAdapter(appContext)

    @Provides
    @Singleton
    fun provideActiveCapabilityNaturalLanguageLexicon(
        @ApplicationContext appContext: Context,
    ): ActiveCapabilityNaturalLanguageLexicon =
        ActiveCapabilityNaturalLanguageLexicon.fromResources(appContext)

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
        @ApplicationContext appContext: Context,
    ): CronRescheduler = WorkManagerCronRescheduler(appContext)

    @Provides
    fun provideCronJobRunCoordinator(
        repository: CronJobRepositoryPort,
        scheduler: CronRescheduler,
        executor: ScheduledTaskExecutor,
    ): CronJobRunCoordinator = CronJobRunCoordinator(
        repository = repository,
        scheduler = scheduler,
        executor = executor,
    )
}
