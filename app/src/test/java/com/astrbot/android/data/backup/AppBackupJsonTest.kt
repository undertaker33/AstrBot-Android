package com.astrbot.android.data.backup

import com.astrbot.android.model.ConversationMessage
import com.astrbot.android.model.ConversationSession
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppBackupJsonTest {
    @Test
    fun serialize_manifest_includes_required_schema_and_modules() {
        val manifest = AppBackupManifest(
            createdAt = 1234L,
            modules = AppBackupModules(
                bots = AppBackupModuleSnapshot(count = 1),
                providers = AppBackupModuleSnapshot(count = 2),
                personas = AppBackupModuleSnapshot(count = 3),
                configs = AppBackupModuleSnapshot(count = 4),
                conversations = AppBackupModuleSnapshot(count = 5),
                qqLogin = AppBackupModuleSnapshot(count = 6),
                ttsAssets = AppBackupModuleSnapshot(count = 7, hasFiles = true),
            ),
        )

        val json = manifest.toJson()

        assertEquals(AppBackupJson.FULL_BACKUP_SCHEMA, json.getString("schema"))
        assertEquals(1234L, json.getLong("createdAt"))
        val modules = json.getJSONObject("modules")
        assertEquals(1, modules.getJSONObject("bots").getInt("count"))
        assertEquals(7, modules.getJSONObject("ttsAssets").getInt("count"))
        assertTrue(modules.getJSONObject("ttsAssets").getBoolean("hasFiles"))
    }

    @Test
    fun deserialize_manifest_accepts_missing_optional_fields() {
        val json = JSONObject(
            """
            {
              "schema": "astrbot-android-full-backup-v1",
              "createdAt": 42,
              "modules": {
                "bots": {"count": 1},
                "providers": {"count": 0},
                "personas": {"count": 0},
                "configs": {"count": 0},
                "conversations": {"count": 1},
                "qqLogin": {"count": 0},
                "ttsAssets": {"count": 0}
              }
            }
            """.trimIndent(),
        )

        val manifest = AppBackupJson.parseManifest(json)

        assertEquals(AppBackupJson.FULL_BACKUP_SCHEMA, manifest.schema)
        assertEquals(42L, manifest.createdAt)
        assertEquals(1, manifest.modules.bots.count)
        assertEquals(1, manifest.modules.conversations.count)
        assertTrue(manifest.modules.ttsAssets.files.isEmpty())
    }

    @Test
    fun conversation_snapshot_round_trip_preserves_message_counts() {
        val manifest = AppBackupManifest(
            createdAt = 999L,
            modules = AppBackupModules(
                conversations = AppBackupModuleSnapshot(
                    count = 1,
                    records = listOf(
                        testSession(id = "session-a"),
                    ),
                ),
            ),
        )

        val reparsed = AppBackupJson.parseManifest(manifest.toJson())
        val restored = reparsed.modules.conversations.records.single() as ConversationSession

        assertEquals("session-a", restored.id)
        assertEquals(1, restored.messages.size)
        assertEquals("hello", restored.messages.single().content)
    }

    private fun testSession(id: String): ConversationSession {
        return ConversationSession(
            id = id,
            title = "Test Session",
            botId = "qq-main",
            personaId = "default",
            providerId = "openai-chat",
            maxContextMessages = 12,
            sessionSttEnabled = true,
            sessionTtsEnabled = false,
            pinned = false,
            titleCustomized = false,
            messages = listOf(
                ConversationMessage(
                    id = "message-1",
                    role = "user",
                    content = "hello",
                    timestamp = 1000L,
                ),
            ),
        )
    }
}
