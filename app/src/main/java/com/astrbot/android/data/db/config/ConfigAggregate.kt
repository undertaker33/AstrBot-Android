package com.astrbot.android.data.db

import androidx.room.Embedded
import androidx.room.Relation

data class ConfigAggregate(
    @Embedded val config: ConfigProfileEntity,
    @Relation(parentColumn = "id", entityColumn = "configId")
    val adminUids: List<ConfigAdminUidEntity>,
    @Relation(parentColumn = "id", entityColumn = "configId")
    val wakeWords: List<ConfigWakeWordEntity>,
    @Relation(parentColumn = "id", entityColumn = "configId")
    val whitelistEntries: List<ConfigWhitelistEntryEntity>,
    @Relation(parentColumn = "id", entityColumn = "configId")
    val keywordPatterns: List<ConfigKeywordPatternEntity>,
    @Relation(parentColumn = "id", entityColumn = "configId")
    val textRules: List<ConfigTextRuleEntity>,
)

data class ConfigWriteModel(
    val config: ConfigProfileEntity,
    val adminUids: List<ConfigAdminUidEntity>,
    val wakeWords: List<ConfigWakeWordEntity>,
    val whitelistEntries: List<ConfigWhitelistEntryEntity>,
    val keywordPatterns: List<ConfigKeywordPatternEntity>,
    val textRule: ConfigTextRuleEntity,
)
