package com.astrbot.android.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.astrbot.android.data.db.core.migration8To9
import com.astrbot.android.data.db.core.migration9To10
import com.astrbot.android.data.db.core.migration10To11
import com.astrbot.android.data.db.core.migration11To12
import com.astrbot.android.data.db.core.migration12To13
import com.astrbot.android.data.db.core.migration13To14
import com.astrbot.android.data.db.core.migration14To15
import com.astrbot.android.data.db.core.migration15To16
import com.astrbot.android.data.db.core.migration16To17
import com.astrbot.android.data.db.core.migration17To18
import com.astrbot.android.data.db.core.migration18To19
import com.astrbot.android.data.db.core.migration19To20
import com.astrbot.android.data.db.core.migration20To21
import com.astrbot.android.data.db.core.migration21To22
import com.astrbot.android.data.db.resource.ConfigResourceProjectionEntity
import com.astrbot.android.data.db.resource.ResourceCenterDao
import com.astrbot.android.data.db.resource.ResourceCenterItemEntity

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
        ResourceCenterItemEntity::class,
        ConfigResourceProjectionEntity::class,
    ],
    version = 22,
    exportSchema = true,
)
abstract class AstrBotDatabase : RoomDatabase() {
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
    abstract fun cronJobDao(): com.astrbot.android.data.db.cron.CronJobDao
    abstract fun cronJobExecutionRecordDao(): com.astrbot.android.data.db.cron.CronJobExecutionRecordDao
    abstract fun resourceCenterDao(): ResourceCenterDao

    companion object {
        @Volatile
        private var instance: AstrBotDatabase? = null

        private val migration2To3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS conversations (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        botId TEXT NOT NULL,
                        personaId TEXT NOT NULL,
                        providerId TEXT NOT NULL,
                        maxContextMessages INTEGER NOT NULL,
                        sessionSttEnabled INTEGER NOT NULL,
                        sessionTtsEnabled INTEGER NOT NULL,
                        pinned INTEGER NOT NULL DEFAULT 0,
                        titleCustomized INTEGER NOT NULL DEFAULT 0,
                        messagesJson TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        private val migration3To4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE conversations ADD COLUMN titleCustomized INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val migration4To5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS conversations_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        botId TEXT NOT NULL,
                        personaId TEXT NOT NULL,
                        providerId TEXT NOT NULL,
                        platformId TEXT NOT NULL,
                        messageType TEXT NOT NULL,
                        originSessionId TEXT NOT NULL,
                        maxContextMessages INTEGER NOT NULL,
                        sessionSttEnabled INTEGER NOT NULL,
                        sessionTtsEnabled INTEGER NOT NULL,
                        pinned INTEGER NOT NULL,
                        titleCustomized INTEGER NOT NULL,
                        messagesJson TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO conversations_new (
                        id, title, botId, personaId, providerId,
                        platformId, messageType, originSessionId,
                        maxContextMessages, sessionSttEnabled, sessionTtsEnabled,
                        pinned, titleCustomized, messagesJson, updatedAt
                    )
                    SELECT
                        id, title, botId, personaId, providerId,
                        CASE
                            WHEN id LIKE 'qq-%-private-%' THEN 'qq'
                            WHEN id LIKE 'qq-%-group-%' THEN 'qq'
                            ELSE 'app'
                        END AS platformId,
                        CASE
                            WHEN id LIKE 'qq-%-private-%' THEN 'friend'
                            WHEN id LIKE 'qq-%-group-%' THEN 'group'
                            ELSE 'other'
                        END AS messageType,
                        CASE
                            WHEN id LIKE 'qq-%-private-%' THEN 'friend:' || substr(id, instr(id, '-private-') + 9)
                            WHEN id LIKE 'qq-%-group-%' THEN
                                CASE
                                    WHEN instr(substr(id, instr(id, '-group-') + 7), '-user-') > 0 THEN
                                        'group:' ||
                                        substr(
                                            substr(id, instr(id, '-group-') + 7),
                                            1,
                                            instr(substr(id, instr(id, '-group-') + 7), '-user-') - 1
                                        ) ||
                                        ':user:' ||
                                        substr(
                                            substr(id, instr(id, '-group-') + 7),
                                            instr(substr(id, instr(id, '-group-') + 7), '-user-') + 6
                                        )
                                    ELSE 'group:' || substr(id, instr(id, '-group-') + 7)
                                END
                            ELSE id
                        END AS originSessionId,
                        maxContextMessages, sessionSttEnabled, sessionTtsEnabled,
                        pinned, titleCustomized, messagesJson, updatedAt
                    FROM conversations
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE conversations")
                db.execSQL("ALTER TABLE conversations_new RENAME TO conversations")
            }
        }

        private val migration5To6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS app_preferences (
                        `key` TEXT NOT NULL PRIMARY KEY,
                        value TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS provider_profiles (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        baseUrl TEXT NOT NULL,
                        model TEXT NOT NULL,
                        providerType TEXT NOT NULL,
                        apiKey TEXT NOT NULL,
                        capabilitiesJson TEXT NOT NULL,
                        enabled INTEGER NOT NULL,
                        multimodalRuleSupport TEXT NOT NULL,
                        multimodalProbeSupport TEXT NOT NULL,
                        nativeStreamingRuleSupport TEXT NOT NULL,
                        nativeStreamingProbeSupport TEXT NOT NULL,
                        sttProbeSupport TEXT NOT NULL,
                        ttsProbeSupport TEXT NOT NULL,
                        ttsVoiceOptionsJson TEXT NOT NULL,
                        sortIndex INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS persona_profiles (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        tag TEXT NOT NULL,
                        systemPrompt TEXT NOT NULL,
                        enabledToolsJson TEXT NOT NULL,
                        defaultProviderId TEXT NOT NULL,
                        maxContextMessages INTEGER NOT NULL,
                        enabled INTEGER NOT NULL,
                        sortIndex INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS config_profiles (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        defaultChatProviderId TEXT NOT NULL,
                        defaultVisionProviderId TEXT NOT NULL,
                        defaultSttProviderId TEXT NOT NULL,
                        defaultTtsProviderId TEXT NOT NULL,
                        sttEnabled INTEGER NOT NULL,
                        ttsEnabled INTEGER NOT NULL,
                        alwaysTtsEnabled INTEGER NOT NULL,
                        ttsReadBracketedContent INTEGER NOT NULL,
                        textStreamingEnabled INTEGER NOT NULL,
                        voiceStreamingEnabled INTEGER NOT NULL,
                        streamingMessageIntervalMs INTEGER NOT NULL,
                        realWorldTimeAwarenessEnabled INTEGER NOT NULL,
                        imageCaptionTextEnabled INTEGER NOT NULL,
                        webSearchEnabled INTEGER NOT NULL,
                        proactiveEnabled INTEGER NOT NULL,
                        includeScheduledTaskConversationContext INTEGER NOT NULL,
                        ttsVoiceId TEXT NOT NULL,
                        imageCaptionPrompt TEXT NOT NULL,
                        adminUidsJson TEXT NOT NULL,
                        sessionIsolationEnabled INTEGER NOT NULL,
                        wakeWordsJson TEXT NOT NULL,
                        wakeWordsAdminOnlyEnabled INTEGER NOT NULL,
                        privateChatRequiresWakeWord INTEGER NOT NULL,
                        replyTextPrefix TEXT NOT NULL,
                        quoteSenderMessageEnabled INTEGER NOT NULL,
                        mentionSenderEnabled INTEGER NOT NULL,
                        replyOnAtOnlyEnabled INTEGER NOT NULL,
                        whitelistEnabled INTEGER NOT NULL,
                        whitelistEntriesJson TEXT NOT NULL,
                        logOnWhitelistMiss INTEGER NOT NULL,
                        adminGroupBypassWhitelistEnabled INTEGER NOT NULL,
                        adminPrivateBypassWhitelistEnabled INTEGER NOT NULL,
                        ignoreSelfMessageEnabled INTEGER NOT NULL,
                        ignoreAtAllEventEnabled INTEGER NOT NULL,
                        replyWhenPermissionDenied INTEGER NOT NULL,
                        rateLimitWindowSeconds INTEGER NOT NULL,
                        rateLimitMaxCount INTEGER NOT NULL,
                        rateLimitStrategy TEXT NOT NULL,
                        keywordDetectionEnabled INTEGER NOT NULL,
                        keywordPatternsJson TEXT NOT NULL,
                        sortIndex INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        private val migration6To7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE bots ADD COLUMN boundQqUinsJson TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE bots ADD COLUMN persistConversationLocally INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE bots ADD COLUMN configProfileId TEXT NOT NULL DEFAULT 'default'")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS saved_qq_accounts (
                        uin TEXT NOT NULL PRIMARY KEY,
                        nickName TEXT NOT NULL,
                        avatarUrl TEXT NOT NULL,
                        sortIndex INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        private val migration7To8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS tts_voice_assets (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        source TEXT NOT NULL,
                        localPath TEXT NOT NULL,
                        remoteUrl TEXT NOT NULL,
                        durationMs INTEGER NOT NULL,
                        sampleRateHz INTEGER NOT NULL,
                        clipsJson TEXT NOT NULL,
                        providerBindingsJson TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        internal val allMigrations: Array<Migration> = arrayOf(
            migration2To3,
            migration3To4,
            migration4To5,
            migration5To6,
            migration6To7,
            migration7To8,
            migration8To9,
            migration9To10,
            migration10To11,
            migration11To12,
            migration12To13,
            migration13To14,
            migration14To15,
            migration15To16,
            migration16To17,
            migration17To18,
            migration18To19,
        migration19To20,
        migration20To21,
        migration21To22,
    )

        fun get(context: Context): AstrBotDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AstrBotDatabase::class.java,
                    "astrbot-native.db",
                )
                    .addMigrations(*allMigrations)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
