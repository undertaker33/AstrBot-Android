package com.astrbot.android.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [BotEntity::class, ConversationEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class AstrBotDatabase : RoomDatabase() {
    abstract fun botDao(): BotDao
    abstract fun conversationDao(): ConversationDao

    companion object {
        @Volatile
        private var instance: AstrBotDatabase? = null

        private val migration2To3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
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
                        messagesJson TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        fun get(context: Context): AstrBotDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AstrBotDatabase::class.java,
                    "astrbot-native.db",
                )
                    .addMigrations(migration2To3)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
