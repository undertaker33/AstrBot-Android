package com.astrbot.android.data.db

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AstrBotDatabaseSchemaContractTest {
    @Test
    fun migrations_include8To9Step() {
        assertTrue(
            AstrBotDatabase.allMigrations.any { migration ->
                migration.startVersion == 8 && migration.endVersion == 9
            },
        )
    }

    @Test
    fun latestMigration_targetsVersion9() {
        assertTrue(AstrBotDatabase.allMigrations.maxOf { it.endVersion } == 9)
    }

    @Test
    fun version9Schema_removesLegacyJsonColumns() {
        val schemaFile = listOf(
            File("schemas/com.astrbot.android.data.db.AstrBotDatabase/9.json"),
            File("app/schemas/com.astrbot.android.data.db.AstrBotDatabase/9.json"),
        ).firstOrNull { it.exists() } ?: error("Room schema file for v9 was not found")
        val schema = schemaFile.readText()

        val legacyColumns = listOf(
            "messagesJson",
            "clipsJson",
            "providerBindingsJson",
            "boundQqUinsJson",
            "triggerWordsCsv",
            "capabilitiesJson",
            "ttsVoiceOptionsJson",
            "enabledToolsJson",
            "adminUidsJson",
            "wakeWordsJson",
            "whitelistEntriesJson",
            "keywordPatternsJson",
        )

        legacyColumns.forEach { column ->
            assertTrue("Expected $column to be absent from v9 schema", column !in schema)
        }
    }
}
