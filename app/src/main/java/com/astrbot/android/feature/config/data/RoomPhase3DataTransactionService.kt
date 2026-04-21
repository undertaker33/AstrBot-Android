package com.astrbot.android.feature.config.data

import androidx.room.withTransaction
import com.astrbot.android.core.common.profile.ProfileCatalogKind
import com.astrbot.android.core.common.profile.ProfileDeletionGuard
import com.astrbot.android.data.db.AppPreferenceEntity
import com.astrbot.android.data.db.AstrBotDatabase
import com.astrbot.android.data.db.toConversationSession
import com.astrbot.android.data.db.toProfile
import com.astrbot.android.data.db.toWriteModel
import com.astrbot.android.feature.config.domain.Phase3DataTransactionService
import com.astrbot.android.model.chat.ConversationSession
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val SELECTED_CONFIG_PROFILE_PREF_KEY = "selected_config_profile_id"
private const val SELECTED_BOT_PREF_KEY = "selected_bot_id"
private const val DEFAULT_APP_CHAT_SESSION_ID = "chat-main"
private const val DEFAULT_APP_CHAT_SESSION_TITLE = "新对话"

internal class RoomPhase3DataTransactionService @Inject constructor(
    private val database: AstrBotDatabase,
) : Phase3DataTransactionService {
    override suspend fun deleteConfigProfile(profileId: String) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val configDao = database.configAggregateDao()
                val botDao = database.botAggregateDao()
                val appPreferenceDao = database.appPreferenceDao()
                val currentConfigs = configDao.listConfigAggregates().map { it.toProfile() }
                if (currentConfigs.size <= 1) return@withTransaction

                val remainingConfigs = currentConfigs.filterNot { it.id == profileId }
                if (remainingConfigs.size == currentConfigs.size) return@withTransaction

                val storedSelectedConfigId = appPreferenceDao.getValue(SELECTED_CONFIG_PROFILE_PREF_KEY)
                val resolvedSelectedConfigId = if (storedSelectedConfigId == profileId) {
                    remainingConfigs.first().id
                } else {
                    remainingConfigs.firstOrNull { it.id == storedSelectedConfigId }?.id ?: remainingConfigs.first().id
                }
                val fallbackConfig = remainingConfigs.first { it.id == resolvedSelectedConfigId }
                val updatedBots = botDao.listBotAggregates()
                    .map { it.toProfile() }
                    .map { profile ->
                        if (profile.configProfileId == profileId) {
                            profile.copy(
                                configProfileId = fallbackConfig.id,
                                defaultProviderId = fallbackConfig.defaultChatProviderId,
                            )
                        } else {
                            profile
                        }
                    }

                configDao.replaceAll(
                    remainingConfigs.mapIndexed { index, profile ->
                        profile.toWriteModel(sortIndex = index)
                    },
                )
                appPreferenceDao.upsert(
                    AppPreferenceEntity(
                        key = SELECTED_CONFIG_PROFILE_PREF_KEY,
                        value = resolvedSelectedConfigId,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                botDao.replaceAll(updatedBots.map { it.toWriteModel() })
            }
        }
    }

    override suspend fun deleteBotProfile(botId: String) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val botDao = database.botAggregateDao()
                val conversationDao = database.conversationAggregateDao()
                val appPreferenceDao = database.appPreferenceDao()
                val currentBots = botDao.listBotAggregates().map { it.toProfile() }
                currentBots.firstOrNull { it.id == botId } ?: return@withTransaction
                ProfileDeletionGuard.requireCanDelete(
                    remainingCount = currentBots.size,
                    kind = ProfileCatalogKind.BOT,
                )

                val updatedBots = currentBots.filterNot { it.id == botId }
                val storedSelectedBotId = appPreferenceDao.getValue(SELECTED_BOT_PREF_KEY)
                val resolvedSelectedBotId = if (storedSelectedBotId == botId) {
                    updatedBots.first().id
                } else {
                    updatedBots.firstOrNull { it.id == storedSelectedBotId }?.id ?: updatedBots.first().id
                }
                val updatedSessions = conversationDao.listConversationAggregates()
                    .map { it.toConversationSession() }
                    .filterNot { it.botId == botId }
                    .ifEmpty {
                        listOf(
                            ConversationSession(
                                id = DEFAULT_APP_CHAT_SESSION_ID,
                                title = DEFAULT_APP_CHAT_SESSION_TITLE,
                                botId = resolvedSelectedBotId,
                                personaId = "",
                                providerId = "",
                                maxContextMessages = 12,
                                sessionSttEnabled = true,
                                sessionTtsEnabled = true,
                                pinned = false,
                                titleCustomized = false,
                                messages = emptyList(),
                            ),
                        )
                    }

                botDao.replaceAll(updatedBots.map { it.toWriteModel() })
                appPreferenceDao.upsert(
                    AppPreferenceEntity(
                        key = SELECTED_BOT_PREF_KEY,
                        value = resolvedSelectedBotId,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                conversationDao.replaceAll(updatedSessions.map { it.toWriteModel() })
            }
        }
    }
}
