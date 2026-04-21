@file:Suppress("DEPRECATION")

package com.astrbot.android.di.startup

import android.app.Application
import com.astrbot.android.core.db.backup.AppBackupRepository
import com.astrbot.android.core.db.backup.ConversationBackupRepository
import com.astrbot.android.core.di.InitializationCoordinator
import com.astrbot.android.feature.bot.data.BotRepositoryInitializer
import com.astrbot.android.feature.chat.data.FeatureConversationRepository as ConversationRepository
import com.astrbot.android.feature.config.data.ConfigRepositoryInitializer
import com.astrbot.android.feature.cron.data.FeatureCronJobRepository as CronJobRepository
import com.astrbot.android.feature.cron.runtime.CronJobScheduler
import com.astrbot.android.feature.persona.data.PersonaRepositoryInitializer
import com.astrbot.android.feature.plugin.data.FeaturePluginRepository as PluginRepository
import com.astrbot.android.feature.plugin.runtime.PluginRuntimeLogCleanupRepository
import com.astrbot.android.feature.plugin.runtime.toolsource.ActiveCapabilityToolSourceProvider
import com.astrbot.android.feature.provider.data.ProviderRepositoryWarmup
import com.astrbot.android.feature.resource.data.FeatureResourceCenterRepository as ResourceCenterRepository
import javax.inject.Inject

internal class RepositoryInitializationStartupChain @Inject constructor(
    private val application: Application,
    private val providerRepositoryWarmup: ProviderRepositoryWarmup,
) : AppStartupChain {

    override fun run() {
        InitializationCoordinator(
            listOf(
                ConfigRepositoryInitializer(),
                BotRepositoryInitializer(),
            ),
        ).initializeAll(application)
        ActiveCapabilityToolSourceProvider.initialize(application)
        CronJobRepository.initialize(application)
        ResourceCenterRepository.initialize(application)
        providerRepositoryWarmup.warmUp()
        InitializationCoordinator(
            listOf(
                PersonaRepositoryInitializer(),
            ),
        ).initializeAll(application)
        ConversationRepository.initialize(application)
        PluginRepository.initialize(application)
        CronJobScheduler.initialize(application)
        PluginRuntimeLogCleanupRepository.initialize(application)
        ConversationBackupRepository.initialize(application)
        AppBackupRepository.initialize(application)
    }
}
