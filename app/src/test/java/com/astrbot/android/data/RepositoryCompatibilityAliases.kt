package com.astrbot.android.data

import com.astrbot.android.feature.bot.data.FeatureBotRepository
import com.astrbot.android.feature.chat.data.FeatureConversationRepository
import com.astrbot.android.feature.config.data.FeatureConfigRepository
import com.astrbot.android.feature.cron.data.FeatureCronJobRepository
import com.astrbot.android.feature.persona.data.FeaturePersonaRepository
import com.astrbot.android.feature.plugin.data.FeaturePluginRepository
import com.astrbot.android.feature.provider.data.FeatureProviderRepository
import com.astrbot.android.feature.resource.data.FeatureResourceCenterRepository

val BotRepository = FeatureBotRepository
val ConfigRepository = FeatureConfigRepository
val PersonaRepository = FeaturePersonaRepository
val ProviderRepository = FeatureProviderRepository
val ConversationRepository = FeatureConversationRepository
val CronJobRepository = FeatureCronJobRepository
val PluginRepository = FeaturePluginRepository
val ResourceCenterRepository = FeatureResourceCenterRepository

val NoOpPluginDataRemover = com.astrbot.android.feature.plugin.data.NoOpPluginDataRemover

typealias PluginInstallStore = com.astrbot.android.feature.plugin.data.PluginInstallStore
typealias PluginDataRemover = com.astrbot.android.feature.plugin.data.PluginDataRemover
typealias PluginFileDataRemover = com.astrbot.android.feature.plugin.data.PluginFileDataRemover
typealias PluginUninstallResult = com.astrbot.android.feature.plugin.data.PluginUninstallResult
typealias PluginCatalogVersionGateResult = com.astrbot.android.feature.plugin.data.PluginCatalogVersionGateResult
typealias PluginPackageInstallBlockedException =
    com.astrbot.android.feature.plugin.data.PluginPackageInstallBlockedException

typealias AppBackupRepository = com.astrbot.android.core.db.backup.AppBackupRepository
typealias ChatCompletionService = com.astrbot.android.core.runtime.llm.ChatCompletionService
typealias LlmResponseSegmenter = com.astrbot.android.core.runtime.llm.LlmResponseSegmenter
typealias NapCatBridgeRepository = com.astrbot.android.feature.qq.data.NapCatBridgeRepository
internal typealias NapCatLoginDiagnostics = com.astrbot.android.feature.qq.data.NapCatLoginDiagnostics
typealias NapCatLoginRepository = com.astrbot.android.feature.qq.data.NapCatLoginRepository
typealias NapCatLoginService = com.astrbot.android.feature.qq.data.NapCatLoginService
typealias ProfileDeletionGuard = com.astrbot.android.core.common.profile.ProfileDeletionGuard
typealias LastProfileDeletionBlockedException =
    com.astrbot.android.core.common.profile.LastProfileDeletionBlockedException
typealias ProfileCatalogKind = com.astrbot.android.core.common.profile.ProfileCatalogKind
