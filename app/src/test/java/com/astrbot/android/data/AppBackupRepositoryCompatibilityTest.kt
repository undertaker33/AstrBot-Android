package com.astrbot.android.data

import com.astrbot.android.core.db.backup.AppBackupImportMode
import com.astrbot.android.core.db.backup.AppBackupJson
import com.astrbot.android.model.chat.MessageType
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppBackupRepositoryCompatibilityTest {
    private lateinit var botSnapshot: List<com.astrbot.android.model.BotProfile>
    private lateinit var providerSnapshot: List<com.astrbot.android.model.ProviderProfile>
    private lateinit var personaSnapshot: List<com.astrbot.android.model.PersonaProfile>
    private lateinit var configSnapshot: List<com.astrbot.android.model.ConfigProfile>
    private lateinit var conversationSnapshot: List<com.astrbot.android.model.chat.ConversationSession>
    private lateinit var selectedBotId: String
    private lateinit var selectedConfigId: String

    @Before
    fun captureSnapshot() {
        botSnapshot = BotRepository.snapshotProfiles()
        providerSnapshot = ProviderRepository.snapshotProfiles()
        personaSnapshot = PersonaRepository.snapshotProfiles()
        configSnapshot = ConfigRepository.snapshotProfiles()
        conversationSnapshot = ConversationRepository.snapshotSessions()
        selectedBotId = BotRepository.selectedBotId.value
        selectedConfigId = ConfigRepository.selectedProfileId.value
    }

    @After
    fun restoreSnapshot() {
        runBlocking {
            ProviderRepository.restoreProfiles(providerSnapshot)
            PersonaRepository.restoreProfiles(personaSnapshot)
            ConfigRepository.restoreProfiles(configSnapshot, selectedConfigId)
            BotRepository.restoreProfiles(botSnapshot, selectedBotId)
            ConversationRepository.restoreSessions(conversationSnapshot)
        }
    }

    @Test
    fun importLegacyManifest_restoresAggregateBackedRepositories() = runBlocking {
        val manifest = AppBackupJson.parseManifest(legacyManifestJson())

        val result = AppBackupRepository.importBackup(
            manifest = manifest,
            mode = AppBackupImportMode.REPLACE_ALL,
        )

        assertTrue(result.isSuccess)
        assertEquals(listOf("provider-legacy"), ProviderRepository.snapshotProfiles().map { it.id })
        assertEquals(listOf("persona-legacy"), PersonaRepository.snapshotProfiles().map { it.id })
        assertEquals(listOf("config-legacy"), ConfigRepository.snapshotProfiles().map { it.id })
        val restoredBot = BotRepository.snapshotProfiles().single()
        assertEquals("bot-legacy", restoredBot.id)
        assertEquals(listOf("10001", "10002"), restoredBot.boundQqUins)
        assertEquals(listOf("astrbot", "assistant"), restoredBot.triggerWords)
        val restoredSession = ConversationRepository.snapshotSessions().single()
        assertEquals("session-legacy", restoredSession.id)
        assertEquals("qq", restoredSession.platformId)
        assertEquals(MessageType.GroupMessage, restoredSession.messageType)
        assertEquals("group:20001:user:10001", restoredSession.originSessionId)
        assertEquals("hello from backup", restoredSession.messages.single().content)
        assertEquals("bot-legacy", BotRepository.selectedBotId.value)
        assertEquals("config-legacy", ConfigRepository.selectedProfileId.value)
    }

    private fun legacyManifestJson(): JSONObject {
        return JSONObject(
            """
            {
              "schema": "astrbot-android-full-backup-v1",
              "createdAt": 1710000000000,
              "trigger": "legacy-import-test",
              "modules": {
                "bots": {
                  "count": 1,
                  "records": [
                    {
                      "id": "bot-legacy",
                      "platformName": "QQ",
                      "displayName": "Legacy Bot",
                      "tag": "Imported",
                      "accountHint": "10001",
                      "boundQqUins": ["10001", "10002"],
                      "triggerWords": ["astrbot", "assistant"],
                      "autoReplyEnabled": true,
                      "persistConversationLocally": true,
                      "bridgeMode": "NapCat local bridge",
                      "bridgeEndpoint": "ws://127.0.0.1:6199/ws",
                      "defaultProviderId": "provider-legacy",
                      "defaultPersonaId": "persona-legacy",
                      "configProfileId": "config-legacy",
                      "status": "Idle"
                    }
                  ]
                },
                "providers": {
                  "count": 1,
                  "records": [
                    {
                      "id": "provider-legacy",
                      "name": "Legacy Provider",
                      "baseUrl": "https://example.com/v1",
                      "model": "gpt-4.1-mini",
                      "providerType": "OPENAI_COMPATIBLE",
                      "apiKey": "secret",
                      "enabled": true,
                      "multimodalRuleSupport": "SUPPORTED",
                      "multimodalProbeSupport": "UNKNOWN",
                      "nativeStreamingRuleSupport": "SUPPORTED",
                      "nativeStreamingProbeSupport": "UNKNOWN",
                      "sttProbeSupport": "UNKNOWN",
                      "ttsProbeSupport": "UNKNOWN",
                      "capabilities": ["CHAT", "TTS"],
                      "ttsVoiceOptions": ["alloy", "verse"]
                    }
                  ]
                },
                "personas": {
                  "count": 1,
                  "records": [
                    {
                      "id": "persona-legacy",
                      "name": "Legacy Persona",
                      "tag": "Legacy",
                      "systemPrompt": "Keep answers concise.",
                      "enabledTools": ["search_web", "qq_send"],
                      "defaultProviderId": "provider-legacy",
                      "maxContextMessages": 16,
                      "enabled": true
                    }
                  ]
                },
                "configs": {
                  "count": 1,
                  "records": [
                    {
                      "id": "config-legacy",
                      "name": "Legacy Config",
                      "defaultChatProviderId": "provider-legacy",
                      "defaultVisionProviderId": "",
                      "defaultSttProviderId": "",
                      "defaultTtsProviderId": "provider-legacy",
                      "sttEnabled": false,
                      "ttsEnabled": true,
                      "alwaysTtsEnabled": false,
                      "ttsReadBracketedContent": true,
                      "textStreamingEnabled": true,
                      "voiceStreamingEnabled": false,
                      "streamingMessageIntervalMs": 120,
                      "realWorldTimeAwarenessEnabled": false,
                      "imageCaptionTextEnabled": true,
                      "webSearchEnabled": true,
                      "proactiveEnabled": false,
                      "ttsVoiceId": "alloy",
                      "imageCaptionPrompt": "Describe images naturally.",
                      "adminUids": ["10001"],
                      "sessionIsolationEnabled": true,
                      "wakeWords": ["astrbot"],
                      "wakeWordsAdminOnlyEnabled": false,
                      "privateChatRequiresWakeWord": false,
                      "replyTextPrefix": "[bot]",
                      "quoteSenderMessageEnabled": true,
                      "mentionSenderEnabled": true,
                      "replyOnAtOnlyEnabled": false,
                      "whitelistEnabled": true,
                      "whitelistEntries": ["group:20001"],
                      "logOnWhitelistMiss": true,
                      "adminGroupBypassWhitelistEnabled": true,
                      "adminPrivateBypassWhitelistEnabled": true,
                      "ignoreSelfMessageEnabled": true,
                      "ignoreAtAllEventEnabled": true,
                      "replyWhenPermissionDenied": true,
                      "rateLimitWindowSeconds": 30,
                      "rateLimitMaxCount": 5,
                      "rateLimitStrategy": "drop",
                      "keywordDetectionEnabled": true,
                      "keywordPatterns": ["ping", "status"]
                    }
                  ]
                },
                "conversations": {
                  "count": 1,
                  "records": [
                    {
                      "id": "session-legacy",
                      "title": "Legacy Session",
                      "botId": "bot-legacy",
                      "personaId": "persona-legacy",
                      "providerId": "provider-legacy",
                      "platformId": "qq",
                      "messageType": "group",
                      "originSessionId": "group:20001:user:10001",
                      "maxContextMessages": 16,
                      "sessionSttEnabled": false,
                      "sessionTtsEnabled": true,
                      "pinned": true,
                      "titleCustomized": true,
                      "messages": [
                        {
                          "id": "message-legacy",
                          "role": "user",
                          "content": "hello from backup",
                          "timestamp": 1710000000123,
                          "attachments": []
                        }
                      ]
                    }
                  ]
                },
                "qqLogin": {
                  "count": 0,
                  "records": []
                },
                "ttsAssets": {
                  "count": 0,
                  "records": []
                }
              },
              "appState": {
                "selectedBotId": "bot-legacy",
                "selectedConfigId": "config-legacy"
              }
            }
            """.trimIndent(),
        )
    }
}
