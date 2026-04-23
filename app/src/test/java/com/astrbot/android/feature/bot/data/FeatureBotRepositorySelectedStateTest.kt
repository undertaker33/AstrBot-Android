package com.astrbot.android.feature.bot.data

import android.content.SharedPreferences
import com.astrbot.android.data.db.AppPreferenceDao
import com.astrbot.android.data.db.AppPreferenceEntity
import com.astrbot.android.data.db.BotAggregate
import com.astrbot.android.data.db.BotAggregateDao
import com.astrbot.android.data.db.BotBoundQqUinEntity
import com.astrbot.android.data.db.BotEntity
import com.astrbot.android.data.db.BotTriggerWordEntity
import com.astrbot.android.data.db.ConfigAdminUidEntity
import com.astrbot.android.data.db.ConfigAggregate
import com.astrbot.android.data.db.ConfigAggregateDao
import com.astrbot.android.data.db.ConfigKeywordPatternEntity
import com.astrbot.android.data.db.ConfigMcpServerEntity
import com.astrbot.android.data.db.ConfigProfileEntity
import com.astrbot.android.data.db.ConfigSkillEntity
import com.astrbot.android.data.db.ConfigTextRuleEntity
import com.astrbot.android.data.db.ConfigWakeWordEntity
import com.astrbot.android.data.db.ConfigWhitelistEntryEntity
import com.astrbot.android.data.db.ConfigWriteModel
import com.astrbot.android.data.db.toWriteModel
import com.astrbot.android.feature.config.data.FeatureConfigRepository
import com.astrbot.android.feature.config.data.FeatureConfigRepositoryStore
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ConfigProfile
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Provider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class FeatureBotRepositorySelectedStateTest {
    @Test
    fun select_waits_for_persisted_selected_bot_flow_before_switching_state() {
        val defaultConfig = configProfile(id = FeatureConfigRepository.DEFAULT_CONFIG_ID, name = "Default")
        val configStore = FeatureConfigRepositoryStore(
            configProfileDao = FakeConfigAggregateDao(listOf(defaultConfig)),
            appPreferenceDao = FakeConfigPreferenceDao(defaultConfig.id),
            preferences = InMemorySharedPreferences(),
        )
        val alpha = botProfile(id = "bot-alpha", displayName = "Alpha")
        val beta = botProfile(id = "bot-beta", displayName = "Beta")
        val botDao = FakeBotAggregateDao(listOf(alpha, beta))
        val appPreferenceDao = FakeBotPreferenceDao(alpha.id)

        FeatureBotRepositoryStore(
            botDao = botDao,
            appPreferenceDao = appPreferenceDao,
            bindingsPreferences = InMemorySharedPreferences(),
            configRepositoryProvider = Provider { configStore },
        )

        waitUntil("initial bot state should sync from fake persistence") {
            FeatureBotRepository.selectedBotId.value == alpha.id &&
                FeatureBotRepository.botProfile.value.id == alpha.id
        }

        FeatureBotRepository.select(beta.id)

        waitUntil("bot selection should be persisted asynchronously") {
            appPreferenceDao.upsertedValues.contains(beta.id)
        }
        assertEquals(alpha.id, FeatureBotRepository.selectedBotId.value)
        assertEquals(alpha.id, FeatureBotRepository.botProfile.value.id)

        appPreferenceDao.emit(beta.id)

        waitUntil("selected bot should switch only after persisted preference flow emits") {
            FeatureBotRepository.selectedBotId.value == beta.id &&
                FeatureBotRepository.botProfile.value.id == beta.id
        }
    }

    private fun botProfile(id: String, displayName: String): BotProfile {
        return BotProfile(
            id = id,
            displayName = displayName,
            tag = displayName,
            accountHint = "QQ account not linked",
            triggerWords = listOf("astrbot"),
            configProfileId = FeatureConfigRepository.DEFAULT_CONFIG_ID,
        )
    }

    private fun configProfile(id: String, name: String): ConfigProfile {
        return ConfigProfile(
            id = id,
            name = name,
        )
    }

    private fun waitUntil(message: String, timeoutMs: Long = 2_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) {
                return
            }
            Thread.sleep(10)
        }
        fail(message)
    }
}

private class FakeBotAggregateDao(initialProfiles: List<BotProfile>) : BotAggregateDao() {
    private val aggregates = MutableStateFlow(initialProfiles.map(::toAggregate))

    override fun observeBotAggregates(): Flow<List<BotAggregate>> = aggregates

    override suspend fun listBotAggregates(): List<BotAggregate> = aggregates.value

    override suspend fun upsertBots(entities: List<BotEntity>) = Unit

    override suspend fun upsertBoundQqUins(entities: List<BotBoundQqUinEntity>) = Unit

    override suspend fun upsertTriggerWords(entities: List<BotTriggerWordEntity>) = Unit

    override suspend fun deleteMissingBots(ids: List<String>) = Unit

    override suspend fun clearBots() = Unit

    override suspend fun deleteBoundQqUins(botIds: List<String>) = Unit

    override suspend fun deleteTriggerWords(botIds: List<String>) = Unit

    override suspend fun count(): Int = aggregates.value.size

    private fun toAggregate(profile: BotProfile): BotAggregate {
        val writeModel = profile.toWriteModel()
        return BotAggregate(
            bot = writeModel.bot,
            boundQqUins = writeModel.boundQqUins,
            triggerWords = writeModel.triggerWords,
        )
    }
}

private class FakeBotPreferenceDao(initialValue: String?) : AppPreferenceDao {
    private val values = MutableStateFlow(initialValue)
    val upsertedValues = CopyOnWriteArrayList<String?>()

    override fun observeValue(key: String): Flow<String?> = values

    override suspend fun getValue(key: String): String? = values.value

    override suspend fun upsert(entity: AppPreferenceEntity) {
        upsertedValues += entity.value
    }

    fun emit(value: String?) {
        values.value = value
    }
}

private class FakeConfigAggregateDao(initialProfiles: List<ConfigProfile>) : ConfigAggregateDao() {
    private val aggregates = MutableStateFlow(initialProfiles.map(::toAggregate))

    override fun observeConfigAggregates(): Flow<List<ConfigAggregate>> = aggregates

    override suspend fun listConfigAggregates(): List<ConfigAggregate> = aggregates.value

    override suspend fun replaceAll(writeModels: List<ConfigWriteModel>) {
        aggregates.value = writeModels.map(::toAggregate)
    }

    override suspend fun upsertConfigs(entities: List<ConfigProfileEntity>) = Unit

    override suspend fun upsertAdminUids(entities: List<ConfigAdminUidEntity>) = Unit

    override suspend fun upsertWakeWords(entities: List<ConfigWakeWordEntity>) = Unit

    override suspend fun upsertWhitelistEntries(entities: List<ConfigWhitelistEntryEntity>) = Unit

    override suspend fun upsertKeywordPatterns(entities: List<ConfigKeywordPatternEntity>) = Unit

    override suspend fun upsertTextRules(entities: List<ConfigTextRuleEntity>) = Unit

    override suspend fun upsertMcpServers(entities: List<ConfigMcpServerEntity>) = Unit

    override suspend fun upsertSkills(entities: List<ConfigSkillEntity>) = Unit

    override suspend fun deleteMissingConfigs(ids: List<String>) = Unit

    override suspend fun clearConfigs() = Unit

    override suspend fun deleteAdminUids(configIds: List<String>) = Unit

    override suspend fun deleteWakeWords(configIds: List<String>) = Unit

    override suspend fun deleteWhitelistEntries(configIds: List<String>) = Unit

    override suspend fun deleteKeywordPatterns(configIds: List<String>) = Unit

    override suspend fun deleteTextRules(configIds: List<String>) = Unit

    override suspend fun deleteMcpServers(configIds: List<String>) = Unit

    override suspend fun deleteSkills(configIds: List<String>) = Unit

    override suspend fun count(): Int = aggregates.value.size

    private fun toAggregate(profile: ConfigProfile): ConfigAggregate = toAggregate(profile.toWriteModel(sortIndex = 0))

    private fun toAggregate(writeModel: ConfigWriteModel): ConfigAggregate {
        return ConfigAggregate(
            config = writeModel.config,
            adminUids = writeModel.adminUids,
            wakeWords = writeModel.wakeWords,
            whitelistEntries = writeModel.whitelistEntries,
            keywordPatterns = writeModel.keywordPatterns,
            textRules = listOf(writeModel.textRule),
            mcpServers = writeModel.mcpServers,
            skills = writeModel.skills,
        )
    }
}

private class FakeConfigPreferenceDao(initialValue: String?) : AppPreferenceDao {
    private val values = MutableStateFlow(initialValue)

    override fun observeValue(key: String): Flow<String?> = values

    override suspend fun getValue(key: String): String? = values.value

    override suspend fun upsert(entity: AppPreferenceEntity) {
        values.value = entity.value
    }
}

private class InMemorySharedPreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()

    override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        @Suppress("UNCHECKED_CAST")
        ((values[key] as? Set<String>)?.toMutableSet() ?: defValues)

    override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue

    override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue

    override fun contains(key: String?): Boolean = values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor(values)

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    private class Editor(
        private val values: MutableMap<String, Any?>,
    ) : SharedPreferences.Editor {
        override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
            if (key != null) values[key] = value
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply {
            if (key != null) this.values[key] = values?.toSet()
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
            if (key != null) values[key] = value
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
            if (key != null) values[key] = value
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
            if (key != null) values[key] = value
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
            if (key != null) values[key] = value
        }

        override fun remove(key: String?): SharedPreferences.Editor = apply {
            if (key != null) values.remove(key)
        }

        override fun clear(): SharedPreferences.Editor = apply {
            values.clear()
        }

        override fun commit(): Boolean = true

        override fun apply() = Unit
    }
}
