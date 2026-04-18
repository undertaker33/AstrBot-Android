package com.astrbot.android.feature.chat.data

import com.astrbot.android.data.db.ConversationAggregate
import com.astrbot.android.data.db.ConversationAggregateDao
import com.astrbot.android.data.db.ConversationAttachmentEntity
import com.astrbot.android.data.db.ConversationEntity
import com.astrbot.android.data.db.ConversationMessageEntity
import com.astrbot.android.model.chat.ConversationSession
import java.io.File
import kotlinx.coroutines.flow.flowOf
import org.json.JSONArray

internal fun sortConversationSessions(sessions: List<ConversationSession>): List<ConversationSession> {
    return sessions.sortedWith(
        compareByDescending<ConversationSession> { it.pinned }
            .thenByDescending { it.messages.maxOfOrNull { message -> message.timestamp } ?: 0L },
    )
}

internal fun loadLegacyConversationSessions(
    file: File,
    defaultTitle: String,
    onFailure: (Throwable) -> Unit,
): List<ConversationSession> {
    return runCatching {
        val array = JSONArray(file.readText(Charsets.UTF_8))
        buildList {
            for (index in 0 until array.length()) {
                add(
                    array.optJSONObject(index)?.toConversationSession(
                        defaultTitle = defaultTitle,
                    ) ?: continue,
                )
            }
        }
    }.onFailure(onFailure).getOrDefault(emptyList())
}

internal fun mergeImportedConversationSessions(
    defaultSessionId: String,
    existingSessions: List<ConversationSession>,
    legacySessions: List<ConversationSession>,
    defaultSessionsProvider: () -> List<ConversationSession>,
): List<ConversationSession> {
    val merged = LinkedHashMap<String, ConversationSession>()
    mergeDefaultConversationSession(defaultSessionId, existingSessions, defaultSessionsProvider).forEach { session ->
        merged[session.id] = session
    }
    legacySessions.forEach { session ->
        val current = merged[session.id]
        merged[session.id] = when {
            current == null -> session
            session.messages.size > current.messages.size -> session
            session.messages.size == current.messages.size &&
                (session.messages.maxOfOrNull { it.timestamp } ?: 0L) >
                (current.messages.maxOfOrNull { it.timestamp } ?: 0L) -> session
            else -> current
        }
    }
    return mergeDefaultConversationSession(
        defaultSessionId = defaultSessionId,
        loadedSessions = merged.values.filterNot { it.id == defaultSessionId }.let(::sortConversationSessions),
        defaultSessionsProvider = defaultSessionsProvider,
    )
}

internal fun mergeDefaultConversationSession(
    defaultSessionId: String,
    loadedSessions: List<ConversationSession>,
    defaultSessionsProvider: () -> List<ConversationSession>,
): List<ConversationSession> {
    val withoutDefault = loadedSessions.filterNot { it.id == defaultSessionId }
    return defaultSessionsProvider() + withoutDefault
}

internal object ConversationAggregateDaoPlaceholder {
    val instance = object : ConversationAggregateDao() {
        override fun observeConversationAggregates() = flowOf(emptyList<ConversationAggregate>())

        override suspend fun listConversationAggregates(): List<ConversationAggregate> = emptyList()

        override suspend fun upsertSessions(entities: List<ConversationEntity>) = Unit

        override suspend fun upsertMessages(entities: List<ConversationMessageEntity>) = Unit

        override suspend fun upsertAttachments(entities: List<ConversationAttachmentEntity>) = Unit

        override suspend fun deleteMissingSessions(ids: List<String>) = Unit

        override suspend fun clearSessions() = Unit

        override suspend fun deleteMessagesForSessions(sessionIds: List<String>) = Unit

        override suspend fun count(): Int = 0
    }
}
