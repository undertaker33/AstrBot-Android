package com.elymbot.android.data.db

import androidx.room.migration.Migration
import com.elymbot.android.data.db.core.migration10To11
import com.elymbot.android.data.db.core.migration11To12
import com.elymbot.android.data.db.core.migration12To13
import com.elymbot.android.data.db.core.migration13To14
import com.elymbot.android.data.db.core.migration14To15
import com.elymbot.android.data.db.core.migration15To16
import com.elymbot.android.data.db.core.migration16To17
import com.elymbot.android.data.db.core.migration17To18
import com.elymbot.android.data.db.core.migration18To19
import com.elymbot.android.data.db.core.migration19To20
import com.elymbot.android.data.db.core.migration20To21
import com.elymbot.android.data.db.core.migration21To22
import com.elymbot.android.data.db.core.migration22To23
import com.elymbot.android.data.db.core.migration23To24
import com.elymbot.android.data.db.core.migration24To25
import com.elymbot.android.data.db.core.migration2To3
import com.elymbot.android.data.db.core.migration3To4
import com.elymbot.android.data.db.core.migration4To5
import com.elymbot.android.data.db.core.migration5To6
import com.elymbot.android.data.db.core.migration6To7
import com.elymbot.android.data.db.core.migration7To8
import com.elymbot.android.data.db.core.migration8To9
import com.elymbot.android.data.db.core.migration9To10

val astrBotDatabaseMigrations: Array<Migration>
    get() = roomMigrations.copyOf()

private val roomMigrations: Array<Migration> = arrayOf(
    migration2To3,
    migration3To4,
    migration4To5,
    migration5To6,
    migration6To7,
    migration7To8,
    migration8To9,
    migration9To10,
    migration10To11,
    migration11To12,
    migration12To13,
    migration13To14,
    migration14To15,
    migration15To16,
    migration16To17,
    migration17To18,
    migration18To19,
    migration19To20,
    migration20To21,
    migration21To22,
    migration22To23,
    migration23To24,
    migration24To25,
)
