package com.astrbot.android.feature.qq.data

import com.astrbot.android.data.parseLegacySavedQqAccounts

import android.content.Context
import android.content.SharedPreferences
import com.astrbot.android.data.db.AppPreferenceDao
import com.astrbot.android.data.db.AppPreferenceEntity
import com.astrbot.android.data.db.AstrBotDatabase
import com.astrbot.android.data.db.SavedQqAccountDao
import com.astrbot.android.data.db.SavedQqAccountEntity
import com.astrbot.android.data.db.toEntity
import com.astrbot.android.data.db.toModel
import com.astrbot.android.model.SavedQqAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

internal object NapCatLoginLocalStore {
    private const val PREFS_NAME = "napcat_login_state"
    private const val KEY_LAST_QUICK_LOGIN_UIN = "last_quick_login_uin"
    private const val KEY_SAVED_ACCOUNTS = "saved_accounts"
    private const val PREF_QUICK_LOGIN_UIN = "qq_login_quick_uin"
    private const val PREF_LEGACY_QQ_LOGIN_MIGRATED = "legacy_qq_login_migrated"

    private var appPreferenceDao: AppPreferenceDao = LoginStoreAppPreferenceDaoPlaceholder.instance
    private var savedQqAccountDao: SavedQqAccountDao = SavedQqAccountDaoPlaceholder.instance
    private var legacyPreferences: SharedPreferences? = null
    private var cachedQuickLoginUin: String = ""
    private var cachedAccounts: List<SavedQqAccount> = emptyList()

    fun initialize(context: Context) {
        val database = AstrBotDatabase.get(context)
        appPreferenceDao = database.appPreferenceDao()
        savedQqAccountDao = database.savedQqAccountDao()
        legacyPreferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        runBlocking(Dispatchers.IO) {
            migrateLegacyStateIfNeeded()
            cachedQuickLoginUin = appPreferenceDao.getValue(PREF_QUICK_LOGIN_UIN).orEmpty().trim()
            cachedAccounts = savedQqAccountDao.listAccounts().map(SavedQqAccountEntity::toModel)
        }
    }

    fun saveQuickLoginUin(uin: String) {
        val cleanedUin = uin.trim()
        if (cleanedUin.isBlank()) return
        cachedQuickLoginUin = cleanedUin
        runBlocking(Dispatchers.IO) {
            appPreferenceDao.upsert(
                AppPreferenceEntity(
                    key = PREF_QUICK_LOGIN_UIN,
                    value = cleanedUin,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun loadSavedQuickLoginUin(): String {
        return cachedQuickLoginUin
    }

    fun loadSavedAccounts(): List<SavedQqAccount> {
        return cachedAccounts
    }

    fun persistSavedAccounts(accounts: List<SavedQqAccount>) {
        val normalized = normalizeAccounts(accounts)
        cachedAccounts = normalized
        runBlocking(Dispatchers.IO) {
            if (normalized.isEmpty()) {
                savedQqAccountDao.clearAll()
            } else {
                val entities = normalized.mapIndexed { index, account -> account.toEntity(sortIndex = index) }
                savedQqAccountDao.upsertAll(entities)
                savedQqAccountDao.deleteMissing(entities.map { it.uin })
            }
        }
    }

    fun mergeSavedAccounts(
        localAccounts: List<SavedQqAccount>,
        remoteAccounts: List<SavedQqAccount>,
    ): List<SavedQqAccount> {
        val merged = localAccounts.toMutableList()
        remoteAccounts.forEach { mergedAccount ->
            val existingIndex = merged.indexOfFirst { it.uin == mergedAccount.uin }
            if (existingIndex >= 0) {
                val existing = merged[existingIndex]
                merged[existingIndex] = existing.copy(
                    nickName = mergedAccount.nickName.ifBlank { existing.nickName },
                    avatarUrl = mergedAccount.avatarUrl.ifBlank { existing.avatarUrl },
                )
            } else {
                merged += mergedAccount
            }
        }
        return normalizeAccounts(merged)
    }

    fun upsertSavedAccount(
        accounts: List<SavedQqAccount>,
        account: SavedQqAccount,
    ): List<SavedQqAccount> {
        val cleanedUin = account.uin.trim()
        if (cleanedUin.isBlank()) return accounts
        val updated = mutableListOf(
            SavedQqAccount(
                uin = cleanedUin,
                nickName = account.nickName.trim(),
                avatarUrl = account.avatarUrl.trim(),
            ),
        )
        accounts.forEach { existing ->
            if (existing.uin != cleanedUin) {
                updated += existing
            }
        }
        val normalized = normalizeAccounts(updated)
        persistSavedAccounts(normalized)
        return normalized
    }

    private suspend fun migrateLegacyStateIfNeeded() {
        if (appPreferenceDao.getValue(PREF_LEGACY_QQ_LOGIN_MIGRATED) == "true") return
        val quickLoginUin = legacyPreferences?.getString(KEY_LAST_QUICK_LOGIN_UIN, null).orEmpty().trim()
        val savedAccounts = parseLegacySavedQqAccounts(
            legacyPreferences?.getString(KEY_SAVED_ACCOUNTS, null),
        )
        if (quickLoginUin.isNotBlank()) {
            appPreferenceDao.upsert(
                AppPreferenceEntity(
                    key = PREF_QUICK_LOGIN_UIN,
                    value = quickLoginUin,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
        if (savedAccounts.isNotEmpty()) {
            val entities = savedAccounts.mapIndexed { index, account -> account.toEntity(sortIndex = index) }
            savedQqAccountDao.upsertAll(entities)
            savedQqAccountDao.deleteMissing(entities.map { it.uin })
        }
        appPreferenceDao.upsert(
            AppPreferenceEntity(
                key = PREF_LEGACY_QQ_LOGIN_MIGRATED,
                value = "true",
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    private fun normalizeAccounts(accounts: List<SavedQqAccount>): List<SavedQqAccount> {
        return accounts
            .mapNotNull { account ->
                val uin = account.uin.trim()
                if (uin.isBlank()) {
                    null
                } else {
                    SavedQqAccount(
                        uin = uin,
                        nickName = account.nickName.trim(),
                        avatarUrl = account.avatarUrl.trim(),
                    )
                }
            }
            .distinctBy { it.uin }
    }
}

private object LoginStoreAppPreferenceDaoPlaceholder {
    val instance = object : AppPreferenceDao {
        override fun observeValue(key: String) = flowOf<String?>(null)
        override suspend fun getValue(key: String): String? = null
        override suspend fun upsert(entity: AppPreferenceEntity) = Unit
    }
}

private object SavedQqAccountDaoPlaceholder {
    val instance = object : SavedQqAccountDao {
        override fun observeAccounts() = flowOf(emptyList<SavedQqAccountEntity>())
        override suspend fun listAccounts(): List<SavedQqAccountEntity> = emptyList()
        override suspend fun upsertAll(entities: List<SavedQqAccountEntity>) = Unit
        override suspend fun deleteMissing(uins: List<String>) = Unit
        override suspend fun clearAll() = Unit
    }
}
