package com.elymbot.android.core.db.backup

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AppBackupManifestAdaptersTest {
    @Test
    fun `module only manifest keeps requested module and clears others`() {
        val snapshot = AppBackupModuleSnapshot(
            count = 1,
            records = listOf(JSONObject().put("id", "bot-1")),
        )
        val manifest = AppBackupManifest(
            createdAt = 1L,
            trigger = "manual",
            modules = AppBackupModules(
                bots = snapshot,
                providers = AppBackupModuleSnapshot(
                    count = 1,
                    records = listOf(JSONObject().put("id", "provider-1")),
                ),
            ),
        )

        val result = moduleOnlyManifest(AppBackupModuleKind.BOTS, manifest)

        assertEquals(1, result.modules.bots.records.size)
        assertEquals(0, result.modules.providers.records.size)
        assertEquals(0, result.modules.personas.records.size)
    }

    @Test
    fun `module count from restore result uses matching module bucket`() {
        val result = AppBackupRestoreResult(
            botCount = 1,
            providerCount = 2,
            personaCount = 3,
            configCount = 4,
            conversationCount = 5,
            qqAccountCount = 6,
            ttsAssetCount = 7,
        )

        assertEquals(1, moduleCountFromRestoreResult(AppBackupModuleKind.BOTS, result))
        assertEquals(5, moduleCountFromRestoreResult(AppBackupModuleKind.CONVERSATIONS, result))
        assertEquals(7, moduleCountFromRestoreResult(AppBackupModuleKind.TTS_ASSETS, result))
    }

    @Test
    fun `json array to string list preserves entries order`() {
        val values = JSONArray().put("a").put("b").put("c")

        assertEquals(listOf("a", "b", "c"), values.jsonStringList())
    }

    @Test
    fun `persona adapter reads tags and cover metadata`() {
        val json = JSONObject()
            .put("id", "persona-1")
            .put("name", "Helper")
            .put("tags", JSONArray(listOf("warm", "concise", "safe", "ignored")))
            .put("systemPrompt", "Help")
            .put("enabledTools", JSONArray())
            .put("cover", JSONObject()
                .put("assetRef", "assets/persona-covers/persona-1/cover.png")
                .put("contentSha256", "abc")
                .put("pixelWidth", 800)
                .put("pixelHeight", 1200)
                .put("updatedAt", 9L)
                .put("portraitCrop", JSONObject().put("centerX", .4).put("centerY", .6).put("zoom", 1.5))
                .put("squareCrop", JSONObject().put("centerX", .5).put("centerY", .5).put("zoom", 2.0)))

        val result = json.toPersonaProfile()

        assertEquals(listOf("warm", "concise", "safe"), result.tags)
        assertNotNull(result.cover)
        assertEquals("assets/persona-covers/persona-1/cover.png", result.cover?.assetRef)
        assertEquals(.6f, result.cover?.portraitCrop?.centerY)
    }

    @Test
    fun `persona adapter falls back to legacy single tag and missing cover`() {
        val result = JSONObject().put("id", "legacy").put("name", "Legacy")
            .put("tag", " old，friend ").put("systemPrompt", "Hi")
            .put("enabledTools", JSONArray()).toPersonaProfile()

        assertEquals(listOf("old", "friend"), result.tags)
        assertNull(result.cover)
    }
}
