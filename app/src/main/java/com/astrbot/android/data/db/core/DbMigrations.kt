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
