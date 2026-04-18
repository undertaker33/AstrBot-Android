package com.astrbot.android.core.db.backup

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
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
}
