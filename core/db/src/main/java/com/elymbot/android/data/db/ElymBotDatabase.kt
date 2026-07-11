package com.elymbot.android.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.elymbot.android.data.db.geofence.ConfigGeofenceBindingEntity
import com.elymbot.android.data.db.geofence.GeofenceExecutionRecordEntity
import com.elymbot.android.data.db.geofence.GeofenceRegionEntity
import com.elymbot.android.data.db.geofence.GeofenceRuleDao
import com.elymbot.android.data.db.geofence.GeofenceRuleEntity
import com.elymbot.android.data.db.resource.ConfigResourceProjectionEntity
import com.elymbot.android.data.db.resource.ResourceCenterDao
import com.elymbot.android.data.db.resource.ResourceCenterItemEntity

@Database(
    entities = [
        AppPreferenceEntity::class,
        BotEntity::class,
        BotBoundQqUinEntity::class,
        BotTriggerWordEntity::class,
        ConfigProfileEntity::class,
        ConfigAdminUidEntity::class,
        ConfigWakeWordEntity::class,
        ConfigWhitelistEntryEntity::class,
        ConfigKeywordPatternEntity::class,
        ConfigTextRuleEntity::class,
        ConfigMcpServerEntity::class,
        ConfigSkillEntity::class,
        ConversationEntity::class,
        ConversationMessageEntity::class,
        ConversationAttachmentEntity::class,
        PersonaEntity::class,
        PersonaPromptEntity::class,
        PersonaEnabledToolEntity::class,
        PersonaTagEntity::class,
        PersonaCoverAssetEntity::class,
        ProviderEntity::class,
        ProviderCapabilityEntity::class,
        ProviderTtsVoiceOptionEntity::class,
        PluginInstallRecordEntity::class,
        PluginCatalogSourceEntity::class,
        PluginCatalogEntryEntity::class,
        PluginCatalogVersionEntity::class,
        PluginManifestSnapshotEntity::class,
        PluginPackageContractSnapshotEntity::class,
        PluginManifestPermissionEntity::class,
        PluginPermissionSnapshotEntity::class,
        PluginConfigSnapshotEntity::class,
        PluginStateEntryEntity::class,
        DownloadTaskEntity::class,
        SavedQqAccountEntity::class,
        TtsVoiceAssetEntity::class,
        TtsVoiceClipEntity::class,
        TtsVoiceProviderBindingEntity::class,
        CronJobEntity::class,
        CronJobExecutionRecordEntity::class,
        GeofenceRuleEntity::class,
        GeofenceRegionEntity::class,
        ConfigGeofenceBindingEntity::class,
        GeofenceExecutionRecordEntity::class,
        ResourceCenterItemEntity::class,
        ConfigResourceProjectionEntity::class,
    ],
    version = 25,
    exportSchema = true,
)
abstract class ElymBotDatabase : RoomDatabase() {
    abstract fun appPreferenceDao(): AppPreferenceDao
    abstract fun botAggregateDao(): BotAggregateDao
    abstract fun configAggregateDao(): ConfigAggregateDao
    abstract fun conversationAggregateDao(): ConversationAggregateDao
    abstract fun personaAggregateDao(): PersonaAggregateDao
    abstract fun providerAggregateDao(): ProviderAggregateDao
    abstract fun pluginInstallAggregateDao(): PluginInstallAggregateDao
    abstract fun pluginCatalogDao(): PluginCatalogDao
    abstract fun pluginConfigSnapshotDao(): PluginConfigSnapshotDao
    abstract fun pluginStateEntryDao(): PluginStateEntryDao
    abstract fun downloadTaskDao(): DownloadTaskDao
    abstract fun savedQqAccountDao(): SavedQqAccountDao
    abstract fun ttsVoiceAssetAggregateDao(): TtsVoiceAssetAggregateDao
    abstract fun cronJobDao(): com.elymbot.android.data.db.cron.CronJobDao
    abstract fun cronJobExecutionRecordDao(): com.elymbot.android.data.db.cron.CronJobExecutionRecordDao
    abstract fun geofenceRuleDao(): GeofenceRuleDao
    abstract fun resourceCenterDao(): ResourceCenterDao
}
