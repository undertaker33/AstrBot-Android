package com.astrbot.android.di.hilt

import android.content.Context
import com.astrbot.android.feature.chat.domain.AppChatRuntimePort
import com.astrbot.android.core.runtime.llm.LlmClientPort
import com.astrbot.android.feature.bot.domain.BotRepositoryPort
import com.astrbot.android.feature.chat.domain.ConversationRepositoryPort
import com.astrbot.android.feature.config.domain.ConfigRepositoryPort
import com.astrbot.android.feature.cron.runtime.CronJobRunCoordinator
import com.astrbot.android.feature.cron.runtime.CronRescheduler
import com.astrbot.android.feature.cron.runtime.ScheduledTaskRuntimeDependencies
import com.astrbot.android.feature.cron.runtime.WorkManagerCronRescheduler
import com.astrbot.android.feature.persona.domain.PersonaRepositoryPort
import com.astrbot.android.feature.plugin.runtime.AppChatPluginRuntime
import com.astrbot.android.feature.plugin.runtime.DefaultAppChatPluginRuntime
import com.astrbot.android.feature.plugin.runtime.DefaultRuntimeLlmOrchestrator
import com.astrbot.android.feature.plugin.runtime.RuntimeLlmOrchestratorPort
import com.astrbot.android.feature.provider.domain.ProviderRepositoryPort
import com.astrbot.android.feature.qq.domain.QqConversationPort
import com.astrbot.android.feature.qq.domain.QqPlatformConfigPort
import com.astrbot.android.feature.qq.runtime.DefaultQqProviderInvoker
import com.astrbot.android.feature.qq.runtime.QqOneBotRuntimeDependencies
import com.astrbot.android.runtime.llm.LegacyChatCompletionServiceAdapter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object RuntimeServicesModule {

    @Provides
    @Singleton
    fun provideLlmClientPort(): LlmClientPort = LegacyChatCompletionServiceAdapter()

    @Provides
    @Singleton
    fun provideRuntimeLlmOrchestratorPort(): RuntimeLlmOrchestratorPort = DefaultRuntimeLlmOrchestrator()

    @Provides
    @Singleton
    fun provideAppChatPluginRuntime(): AppChatPluginRuntime = DefaultAppChatPluginRuntime

    @Provides
    @Singleton
    fun provideQqProviderInvoker(
        llmClientPort: LlmClientPort,
    ): DefaultQqProviderInvoker = DefaultQqProviderInvoker(llmClientPort)

    @Provides
    @Singleton
    fun provideScheduledTaskRuntimeDependencies(
        botPort: BotRepositoryPort,
        conversationPort: ConversationRepositoryPort,
        orchestrator: RuntimeLlmOrchestratorPort,
    ): ScheduledTaskRuntimeDependencies {
        return ScheduledTaskRuntimeDependencies(
            botPort = botPort,
            conversationPort = conversationPort,
            orchestrator = orchestrator,
        )
    }

    @Provides
    fun provideCronRescheduler(
        @ApplicationContext appContext: Context,
    ): CronRescheduler = WorkManagerCronRescheduler(appContext)

    @Provides
    fun provideCronJobRunCoordinator(
        scheduler: CronRescheduler,
    ): CronJobRunCoordinator = CronJobRunCoordinator(scheduler = scheduler)

    @Provides
    @Singleton
    fun provideQqOneBotRuntimeDependencies(
        botPort: BotRepositoryPort,
        configPort: ConfigRepositoryPort,
        personaPort: PersonaRepositoryPort,
        providerPort: ProviderRepositoryPort,
        conversationPort: QqConversationPort,
        platformConfigPort: QqPlatformConfigPort,
        orchestrator: RuntimeLlmOrchestratorPort,
        providerInvoker: DefaultQqProviderInvoker,
    ): QqOneBotRuntimeDependencies {
        return QqOneBotRuntimeDependencies(
            botPort = botPort,
            configPort = configPort,
            personaPort = personaPort,
            providerPort = providerPort,
            conversationPort = conversationPort,
            platformConfigPort = platformConfigPort,
            orchestrator = orchestrator,
            providerInvoker = providerInvoker,
        )
    }
}
