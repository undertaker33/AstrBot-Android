package com.astrbot.android.data.db.core

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val migration8To9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.resetSchemaToV9()
    }
}

internal val migration9To10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.createPluginTablesV10()
    }
}

internal val migration10To11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            ALTER TABLE plugin_install_records
            ADD COLUMN consecutiveFailureCount INTEGER NOT NULL DEFAULT 0
            """.trimIndent(),
        )
        db.execSQL(
            """
            ALTER TABLE plugin_install_records
            ADD COLUMN lastFailureAtEpochMillis INTEGER
            """.trimIndent(),
        )
        db.execSQL(
            """
            ALTER TABLE plugin_install_records
            ADD COLUMN lastErrorSummary TEXT NOT NULL DEFAULT ''
            """.trimIndent(),
        )
        db.execSQL(
            """
            ALTER TABLE plugin_install_records
            ADD COLUMN suspendedUntilEpochMillis INTEGER
            """.trimIndent(),
        )
    }
}

internal val migration11To12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            ALTER TABLE plugin_install_records
            ADD COLUMN catalogSourceId TEXT
            """.trimIndent(),
        )
        db.execSQL(
            """
            ALTER TABLE plugin_install_records
            ADD COLUMN installedPackageUrl TEXT NOT NULL DEFAULT ''
            """.trimIndent(),
        )
        db.execSQL(
            """
            ALTER TABLE plugin_install_records
            ADD COLUMN lastCatalogCheckAtEpochMillis INTEGER
            """.trimIndent(),
        )
        db.createPluginCatalogTablesV12()
    }
}

internal val migration12To13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            ALTER TABLE plugin_catalog_sources
            ADD COLUMN lastSyncAtEpochMillis INTEGER
            """.trimIndent(),
        )
        db.execSQL(
            """
            ALTER TABLE plugin_catalog_sources
            ADD COLUMN lastSyncStatus TEXT NOT NULL DEFAULT 'NEVER_SYNCED'
            """.trimIndent(),
        )
        db.execSQL(
            """
            ALTER TABLE plugin_catalog_sources
            ADD COLUMN lastSyncErrorSummary TEXT NOT NULL DEFAULT ''
            """.trimIndent(),
        )
    }
}

internal val migration13To14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.createPluginConfigTablesV14()
    }
}
