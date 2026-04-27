package com.astrbot.android.di.hilt.runtime

import android.content.Context
import com.astrbot.android.core.runtime.llm.ChatCompletionServiceLlmClient
import com.astrbot.android.core.runtime.llm.HiltLlmProviderProbePort
import com.astrbot.android.core.runtime.llm.LlmClientPort
import com.astrbot.android.core.runtime.llm.LlmProviderProbePort
import com.astrbot.android.feature.plugin.runtime.DefaultRuntimeLlmOrchestrator
import com.astrbot.android.feature.plugin.runtime.RuntimeLlmOrchestratorPort
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object LlmRuntimeModule {

    @Provides
    @Singleton
    fun provideLlmClientPort(
        @ApplicationContext appContext: Context,
    ): LlmClientPort = ChatCompletionServiceLlmClient(appContext)

    @Provides
    @Singleton
    fun provideLlmProviderProbePort(
        @ApplicationContext appContext: Context,
    ): LlmProviderProbePort = HiltLlmProviderProbePort(appContext)

    @Provides
    @Singleton
    fun provideRuntimeLlmOrchestratorPort(): RuntimeLlmOrchestratorPort = DefaultRuntimeLlmOrchestrator()
}
