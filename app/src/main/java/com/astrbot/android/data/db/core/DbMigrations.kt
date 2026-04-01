package com.astrbot.android.data.db.core

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val migration8To9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.resetSchemaToV9()
    }
}
