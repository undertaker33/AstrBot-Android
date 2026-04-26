package com.astrbot.android.data.db.core

import androidx.sqlite.db.SupportSQLiteDatabase

internal fun SupportSQLiteDatabase.resetSchemaToV9() {
    execSQL("DROP TABLE IF EXISTS conversation_attachments")
    execSQL("DROP TABLE IF EXISTS conversation_messages")
    execSQL("DROP TABLE IF EXISTS conversations")
    execSQL("DROP TABLE IF EXISTS tts_voice_provider_bindings")
    execSQL("DROP TABLE IF EXISTS tts_voice_clips")
    execSQL("DROP TABLE IF EXISTS tts_voice_assets")
    execSQL("DROP TABLE IF EXISTS bot_trigger_words")
    execSQL("DROP TABLE IF EXISTS bot_bound_qq_uins")
    execSQL("DROP TABLE IF EXISTS bots")
    execSQL("DROP TABLE IF EXISTS provider_tts_voice_options")
    execSQL("DROP TABLE IF EXISTS provider_capabilities")
    execSQL("DROP TABLE IF EXISTS provider_profiles")
    execSQL("DROP TABLE IF EXISTS config_text_rules")
    execSQL("DROP TABLE IF EXISTS config_keyword_patterns")
    execSQL("DROP TABLE IF EXISTS config_whitelist_entries")
    execSQL("DROP TABLE IF EXISTS config_wake_words")
    execSQL("DROP TABLE IF EXISTS config_admin_uids")
    execSQL("DROP TABLE IF EXISTS config_profiles")
    execSQL("DROP TABLE IF EXISTS persona_enabled_tools")
    execSQL("DROP TABLE IF EXISTS persona_prompts")
    execSQL("DROP TABLE IF EXISTS persona_profiles")
    execSQL("DROP TABLE IF EXISTS saved_qq_accounts")
    execSQL("DROP TABLE IF EXISTS app_preferences")

    execSQL(
        """
        CREATE TABLE IF NOT EXISTS app_preferences (
            `key` TEXT NOT NULL PRIMARY KEY,
            value TEXT NOT NULL,
            updatedAt INTEGER NOT NULL
        )
        """.trimIndent(),
    )
    execSQL(
        """
        CREATE TABLE IF NOT EXISTS bots (
            id TEXT NOT NULL PRIMARY KEY,
            platformName TEXT NOT NULL,
            displayName TEXT NOT NULL,
            tag TEXT NOT NULL,
            accountHint TEXT NOT NULL,
            autoReplyEnabled INTEGER NOT NULL,
            persistConversationLocally INTEGER NOT NULL,
            bridgeMode TEXT NOT NULL,
            bridgeEndpoint TEXT NOT NULL,
            defaultProviderId TEXT NOT NULL,
            defaultPersonaId TEXT NOT NULL,
            configProfileId TEXT NOT NULL,
            status TEXT NOT NULL,
            updatedAt INTEGER NOT NULL
        )
        """.trimIndent(),
    )
    execSQL(
        """
        CREATE TABLE IF NOT EXISTS bot_bound_qq_uins (
            botId TEXT NOT NULL,
            uin TEXT NOT NULL,
            sortIndex INTEGER NOT NULL,
            PRIMARY KEY(botId, uin),
            FOREIGN KEY(botId) REFERENCES bots(id) ON DELETE CASCADE
        )
        """.trimIndent(),
    )
    execSQL("CREATE INDEX IF NOT EXISTS index_bot_bound_qq_uins_botId_sortIndex ON bot_bound_qq_uins(botId, sortIndex)")
    execSQL(
        """
        CREATE TABLE IF NOT EXISTS bot_trigger_words (
            botId TEXT NOT NULL,
            word TEXT NOT NULL,
            sortIndex INTEGER NOT NULL,
            PRIMARY KEY(botId, word),
            FOREIGN KEY(botId) REFERENCES bots(id) ON DELETE CASCADE
        )
        """.trimIndent(),
    )
    execSQL("CREATE INDEX IF NOT EXISTS index_bot_trigger_words_botId_sortIndex ON bot_trigger_words(botId, sortIndex)")
    execSQL(
        """
        CREATE TABLE IF NOT EXISTS provider_profiles (
            id TEXT NOT NULL PRIMARY KEY,
            name TEXT NOT NULL,
            baseUrl TEXT NOT NULL,
            model TEXT NOT NULL,
            providerType TEXT NOT NULL,
            apiKey TEXT NOT NULL,
            enabled INTEGER NOT NULL,
            multimodalRuleSupport TEXT NOT NULL,
            multimodalProbeSupport TEXT NOT NULL,
            nativeStreamingRuleSupport TEXT NOT NULL,
            nativeStreamingProbeSupport TEXT NOT NULL,
            sttProbeSupport TEXT NOT NULL,
            ttsProbeSupport TEXT NOT NULL,
            sortIndex INTEGER NOT NULL,
            updatedAt INTEGER NOT NULL
        )
        """.trimIndent(),
    )
    execSQL(
        """
        CREATE TABLE IF NOT EXISTS provider_capabilities (
            providerId TEXT NOT NULL,
            capability TEXT NOT NULL,
            PRIMARY KEY(providerId, capability),
            FOREIGN KEY(providerId) REFERENCES provider_profiles(id) ON DELETE CASCADE
        )
        """.trimIndent(),
    )
    execSQL(
        """
        CREATE TABLE IF NOT EXISTS provider_tts_voice_options (
            providerId TEXT NOT NULL,
            voiceOption TEXT NOT NULL,
            sortIndex INTEGER NOT NULL,
            PRIMARY KEY(providerId, voiceOption),
            FOREIGN KEY(providerId) REFERENCES provider_profiles(id) ON DELETE CASCADE
        )
        """.trimIndent(),
    )
    execSQL("CREATE INDEX IF NOT EXISTS index_provider_tts_voice_options_providerId_sortIndex ON provider_tts_voice_options(providerId, sortIndex)")
    execSQL(
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
            sessionIsolationEnabled INTEGER NOT NULL,
            wakeWordsAdminOnlyEnabled INTEGER NOT NULL,
            privateChatRequiresWakeWord INTEGER NOT NULL,
            replyTextPrefix TEXT NOT NULL,
            quoteSenderMessageEnabled INTEGER NOT NULL,
            mentionSenderEnabled INTEGER NOT NULL,
            replyOnAtOnlyEnabled INTEGER NOT NULL,
            whitelistEnabled INTEGER NOT NULL,
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
            sortIndex INTEGER NOT NULL,
            updatedAt INTEGER NOT NULL
        )
        """.trimIndent(),
    )
    execSQL("CREATE TABLE IF NOT EXISTS config_admin_uids (configId TEXT NOT NULL, uid TEXT NOT NULL, sortIndex INTEGER NOT NULL, PRIMARY KEY(configId, uid), FOREIGN KEY(configId) REFERENCES config_profiles(id) ON DELETE CASCADE)")
    execSQL("CREATE INDEX IF NOT EXISTS index_config_admin_uids_configId_sortIndex ON config_admin_uids(configId, sortIndex)")
    execSQL("CREATE TABLE IF NOT EXISTS config_wake_words (configId TEXT NOT NULL, word TEXT NOT NULL, sortIndex INTEGER NOT NULL, PRIMARY KEY(configId, word), FOREIGN KEY(configId) REFERENCES config_profiles(id) ON DELETE CASCADE)")
    execSQL("CREATE INDEX IF NOT EXISTS index_config_wake_words_configId_sortIndex ON config_wake_words(configId, sortIndex)")
    execSQL("CREATE TABLE IF NOT EXISTS config_whitelist_entries (configId TEXT NOT NULL, entry TEXT NOT NULL, sortIndex INTEGER NOT NULL, PRIMARY KEY(configId, entry), FOREIGN KEY(configId) REFERENCES config_profiles(id) ON DELETE CASCADE)")
    execSQL("CREATE INDEX IF NOT EXISTS index_config_whitelist_entries_configId_sortIndex ON config_whitelist_entries(configId, sortIndex)")
    execSQL("CREATE TABLE IF NOT EXISTS config_keyword_patterns (configId TEXT NOT NULL, pattern TEXT NOT NULL, sortIndex INTEGER NOT NULL, PRIMARY KEY(configId, pattern), FOREIGN KEY(configId) REFERENCES config_profiles(id) ON DELETE CASCADE)")
    execSQL("CREATE INDEX IF NOT EXISTS index_config_keyword_patterns_configId_sortIndex ON config_keyword_patterns(configId, sortIndex)")
    execSQL("CREATE TABLE IF NOT EXISTS config_text_rules (configId TEXT NOT NULL PRIMARY KEY, imageCaptionPrompt TEXT NOT NULL, FOREIGN KEY(configId) REFERENCES config_profiles(id) ON DELETE CASCADE)")
    execSQL(
        """
        CREATE TABLE IF NOT EXISTS persona_profiles (
            id TEXT NOT NULL PRIMARY KEY,
            name TEXT NOT NULL,
            tag TEXT NOT NULL,
            defaultProviderId TEXT NOT NULL,
            maxContextMessages INTEGER NOT NULL,
            enabled INTEGER NOT NULL,
            sortIndex INTEGER NOT NULL,
            updatedAt INTEGER NOT NULL
        )
        """.trimIndent(),
    )
    execSQL("CREATE TABLE IF NOT EXISTS persona_prompts (personaId TEXT NOT NULL PRIMARY KEY, systemPrompt TEXT NOT NULL, FOREIGN KEY(personaId) REFERENCES persona_profiles(id) ON DELETE CASCADE)")
    execSQL("CREATE TABLE IF NOT EXISTS persona_enabled_tools (personaId TEXT NOT NULL, toolName TEXT NOT NULL, sortIndex INTEGER NOT NULL, PRIMARY KEY(personaId, toolName), FOREIGN KEY(personaId) REFERENCES persona_profiles(id) ON DELETE CASCADE)")
    execSQL("CREATE INDEX IF NOT EXISTS index_persona_enabled_tools_personaId_sortIndex ON persona_enabled_tools(personaId, sortIndex)")
    execSQL(
        """
        CREATE TABLE IF NOT EXISTS conversations (
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
            updatedAt INTEGER NOT NULL
        )
        """.trimIndent(),
    )
    execSQL(
        """
        CREATE TABLE IF NOT EXISTS conversation_messages (
            id TEXT NOT NULL PRIMARY KEY,
            sessionId TEXT NOT NULL,
            role TEXT NOT NULL,
            content TEXT NOT NULL,
            timestamp INTEGER NOT NULL,
            sortIndex INTEGER NOT NULL,
            FOREIGN KEY(sessionId) REFERENCES conversations(id) ON DELETE CASCADE
        )
        """.trimIndent(),
    )
    execSQL("CREATE INDEX IF NOT EXISTS index_conversation_messages_sessionId_timestamp ON conversation_messages(sessionId, timestamp)")
    execSQL("CREATE INDEX IF NOT EXISTS index_conversation_messages_sessionId_sortIndex ON conversation_messages(sessionId, sortIndex)")
    execSQL(
        """
        CREATE TABLE IF NOT EXISTS conversation_attachments (
            id TEXT NOT NULL PRIMARY KEY,
            messageId TEXT NOT NULL,
            type TEXT NOT NULL,
            mimeType TEXT NOT NULL,
            fileName TEXT NOT NULL,
            base64Data TEXT NOT NULL,
            remoteUrl TEXT NOT NULL,
            sortIndex INTEGER NOT NULL,
            FOREIGN KEY(messageId) REFERENCES conversation_messages(id) ON DELETE CASCADE
        )
        """.trimIndent(),
    )
    execSQL("CREATE INDEX IF NOT EXISTS index_conversation_attachments_messageId_sortIndex ON conversation_attachments(messageId, sortIndex)")
    execSQL(
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
    execSQL(
        """
        CREATE TABLE IF NOT EXISTS tts_voice_assets (
            id TEXT NOT NULL PRIMARY KEY,
            name TEXT NOT NULL,
            source TEXT NOT NULL,
            localPath TEXT NOT NULL,
            remoteUrl TEXT NOT NULL,
            durationMs INTEGER NOT NULL,
            sampleRateHz INTEGER NOT NULL,
            createdAt INTEGER NOT NULL,
            updatedAt INTEGER NOT NULL
        )
        """.trimIndent(),
    )
    execSQL(
        """
        CREATE TABLE IF NOT EXISTS tts_voice_clips (
            id TEXT NOT NULL PRIMARY KEY,
            assetId TEXT NOT NULL,
            localPath TEXT NOT NULL,
            durationMs INTEGER NOT NULL,
            sampleRateHz INTEGER NOT NULL,
            createdAt INTEGER NOT NULL,
            sortIndex INTEGER NOT NULL,
            FOREIGN KEY(assetId) REFERENCES tts_voice_assets(id) ON DELETE CASCADE
        )
        """.trimIndent(),
    )
    execSQL("CREATE INDEX IF NOT EXISTS index_tts_voice_clips_assetId_sortIndex ON tts_voice_clips(assetId, sortIndex)")
    execSQL(
        """
        CREATE TABLE IF NOT EXISTS tts_voice_provider_bindings (
            id TEXT NOT NULL PRIMARY KEY,
            assetId TEXT NOT NULL,
            providerId TEXT NOT NULL,
            providerType TEXT NOT NULL,
            model TEXT NOT NULL,
            voiceId TEXT NOT NULL,
            displayName TEXT NOT NULL,
            createdAt INTEGER NOT NULL,
            lastVerifiedAt INTEGER NOT NULL,
            status TEXT NOT NULL,
            sortIndex INTEGER NOT NULL,
            FOREIGN KEY(assetId) REFERENCES tts_voice_assets(id) ON DELETE CASCADE
        )
        """.trimIndent(),
    )
    execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_tts_voice_provider_bindings_assetId_providerId_model_voiceId ON tts_voice_provider_bindings(assetId, providerId, model, voiceId)")
    execSQL("CREATE INDEX IF NOT EXISTS index_tts_voice_provider_bindings_assetId_sortIndex ON tts_voice_provider_bindings(assetId, sortIndex)")
}
