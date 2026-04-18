package com.astrbot.android.feature.chat.data

import android.content.Context
import com.astrbot.android.data.db.ConversationAggregateDao
import com.astrbot.android.data.db.toConversationSession
import com.astrbot.android.data.db.toWriteModel
import com.astrbot.android.data.db.AstrBotDatabase
import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.chat.ConversationSession
import com.astrbot.android.model.chat.MessageType
import com.astrbot.android.model.chat.defaultSessionRefFor
import com.astrbot.android.model.chat.importDedupKey
import com.astrbot.android.core.common.logging.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

data class ConversationImportPreview(
    val totalSessions: Int,
    val duplicateSessions: List<ConversationSession>,
    val newSessions: List<ConversationSession>,
)

data class ConversationImportResult(
    val importedCount: Int,
    val overwrittenCount: Int,
    val skippedCount: Int,
)

@Deprecated("Use ConversationRepositoryPort from feature/chat/domain. Direct access will be removed.")
object FeatureConversationRepository {
    const val DEFAULT_SESSION_ID = "chat-main"
    const val DEFAULT_SESSION_TITLE = "新对话"

    private const val LEGACY_STORAGE_FILE_NAME = "persistent_conversations.json"
    private const val PERSIST_DEBOUNCE_MS = 300L

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initialized = AtomicBoolean(false)
    private val initializationLoaded = AtomicBoolean(false)
    private val persistenceMutex = Mutex()
    @Volatile
    private var selectedBotIdProvider: () -> String = { "qq-main" }

    private var legacyStorageFile: File? = null
    private var conversationAggregateDao: ConversationAggregateDao = ConversationAggregateDaoPlaceholder.instance

    private val _sessions = MutableStateFlow(defaultSessions())
    val sessions: StateFlow<List<ConversationSession>> = _sessions.asStateFlow()
    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    @Volatile
    private var debouncedPersistJob: Job? = null

    fun setSelectedBotIdProvider(provider: () -> String) {
        selectedBotIdProvider = provider
    }

    fun initialize(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        initializationLoaded.set(false)

        legacyStorageFile = File(context.filesDir, LEGACY_STORAGE_FILE_NAME)
        conversationAggregateDao = AstrBotDatabase.get(context).conversationAggregateDao()

        repositoryScope.launch {
            runCatching {
                val importedLegacy = importLegacySessionsIfNeeded()
                if (conversationAggregateDao.count() == 0 && !importedLegacy) {
                    conversationAggregateDao.replaceAll(defaultSessions().map { it.toWriteModel() })
                }
                val sessionsFromDb = conversationAggregateDao.listConversationAggregates().mapNotNull { aggregate ->
                    runCatching { aggregate.toConversationSession() }
                        .onFailure { error ->
                            AppLogger.append(
                                "Conversation row skipped: id=${aggregate.session.id} reason=${error.message ?: error.javaClass.simpleName}",
                            )
                        }
                        .getOrNull()
                }
                _sessions.value = if (sessionsFromDb.isEmpty()) defaultSessions() else sessionsFromDb
                initializationLoaded.set(true)
                _isReady.value = true
                AppLogger.append("Conversation database initialized: sessions=${_sessions.value.size}")
            }.onFailure { error ->
                _sessions.value = defaultSessions()
                initializationLoaded.set(true)
                _isReady.value = true
                AppLogger.append(
                    "Conversation database init failed, fallback to defaults: ${error.message ?: error.javaClass.simpleName}",
                )
            }
        }
    }

    fun session(sessionId: String = DEFAULT_SESSION_ID): ConversationSession {
        return _sessions.value.firstOrNull { it.id == sessionId } ?: createMissingSession(sessionId)
    }

    fun createSession(
        title: String = DEFAULT_SESSION_TITLE,
        botId: String = currentSelectedBotId(),
    ): ConversationSession {
        val created = ConversationSession(
            id = UUID.randomUUID().toString(),
            title = title,
            botId = botId,
            personaId = "",
            providerId = "",
            maxContextMessages = 12,
            sessionSttEnabled = true,
            sessionTtsEnabled = true,
            pinned = false,
            titleCustomized = false,
            messages = emptyList(),
        )
        _sessions.update { current -> sortConversationSessions(listOf(created) + current) }
        persistSessions()
        AppLogger.append("Conversation created: session=${created.id} bot=${created.botId}")
        return created
    }

    fun deleteSession(sessionId: String) {
        var shouldPersist = false
        _sessions.update { current ->
            val filtered = current.filterNot { it.id == sessionId }
            shouldPersist = filtered.size != current.size || current.size == 1
            sortConversationSessions(if (filtered.isEmpty()) defaultSessions() else filtered)
        }
        if (shouldPersist) {
            persistSessions()
            AppLogger.append("Conversation deleted: session=$sessionId")
        }
    }

    fun deleteSessionsForBot(botId: String) {
        var shouldPersist = false
        _sessions.update { current ->
            val filtered = current.filterNot { it.botId == botId }
            shouldPersist = filtered.size != current.size
            sortConversationSessions(if (filtered.isEmpty()) defaultSessions() else filtered)
        }
        if (shouldPersist) {
            persistSessions()
            AppLogger.append("Conversation deleted for bot: $botId")
        }
    }

    fun renameSession(sessionId: String, title: String) {
        val cleaned = title.trim().ifBlank { DEFAULT_SESSION_TITLE }
        _sessions.update { current ->
            current.map { item ->
                if (item.id == sessionId) item.copy(title = cleaned, titleCustomized = true) else item
            }
        }
        persistSessions()
        AppLogger.append("Conversation renamed: session=$sessionId title=$cleaned")
    }

    fun syncSystemSessionTitle(sessionId: String, title: String) {
        var updated = false
        _sessions.update { current ->
            current.map { item ->
                val synced = if (item.id == sessionId) {
                    applySystemSessionTitle(
                        session = item,
                        incomingTitle = title,
                        defaultTitle = DEFAULT_SESSION_TITLE,
                    )
                } else {
                    null
                }
                if (synced != null) {
                    updated = true
                    synced
                } else {
                    item
                }
            }
        }
        if (updated) {
            persistSessions()
            AppLogger.append(
                "Conversation system title synced: session=$sessionId title=${title.trim().ifBlank { DEFAULT_SESSION_TITLE }}",
            )
        }
    }

    fun toggleSessionPinned(sessionId: String) {
        var pinned: Boolean? = null
        _sessions.update { current ->
            sortConversationSessions(
                current.map { item ->
                    if (item.id == sessionId) {
                        val next = !item.pinned
                        pinned = next
                        item.copy(pinned = next)
                    } else {
                        item
                    }
                },
            )
        }
        if (pinned != null) {
            persistSessions()
            AppLogger.append("Conversation pin toggled: session=$sessionId pinned=$pinned")
        }
    }

    fun buildContextPreview(sessionId: String): String {
        val currentSession = session(sessionId)
        return currentSession.messages
            .takeLast(currentSession.maxContextMessages)
            .joinToString(separator = "\n") { "${it.role}: ${it.content}" }
    }

    fun appendMessage(
        sessionId: String,
        role: String,
        content: String,
        attachments: List<ConversationAttachment> = emptyList(),
    ): String {
        val currentSession = session(sessionId)
        val message = ConversationMessage(
            id = UUID.randomUUID().toString(),
            role = role,
            content = content,
            timestamp = System.currentTimeMillis(),
            attachments = attachments,
        )
        _sessions.update { current ->
            current.map { item ->
                if (item.id == currentSession.id) item.copy(messages = item.messages + message) else item
            }
        }.also {
            _sessions.value = sortConversationSessions(_sessions.value)
        }
        persistSessions()
        AppLogger.append(
            "Conversation message appended: session=$sessionId role=$role chars=${content.length} attachments=${attachments.size}",
        )
        return message.id
    }

    fun updateMessage(
        sessionId: String,
        messageId: String,
        content: String? = null,
        attachments: List<ConversationAttachment>? = null,
    ) {
        val currentSession = session(sessionId)
        var updated = false
        _sessions.update { current ->
            current.map { item ->
                if (item.id == currentSession.id) {
                    item.copy(
                        messages = item.messages.map { message ->
                            if (message.id == messageId) {
                                updated = true
                                message.copy(
                                    content = content ?: message.content,
                                    attachments = attachments ?: message.attachments,
                                )
                            } else {
                                message
                            }
                        },
                    )
                } else {
                    item
                }
            }
        }
        if (!updated) {
            AppLogger.append(
                "Conversation message update skipped: session=$sessionId message=$messageId not found",
            )
            return
        }
        persistSessions()
    }

    fun replaceMessages(sessionId: String, messages: List<ConversationMessage>) {
        val currentSession = session(sessionId)
        _sessions.update { current ->
            current.map { item ->
                if (item.id == currentSession.id) item.copy(messages = messages) else item
            }
        }.also {
            _sessions.value = sortConversationSessions(_sessions.value)
        }
        persistSessions()
        AppLogger.append("Conversation replaced: session=$sessionId messages=${messages.size}")
    }

    fun updateSessionBindings(
        sessionId: String,
        providerId: String,
        personaId: String,
        botId: String,
    ) {
        val currentSession = session(sessionId)
        _sessions.update { current ->
            current.map { item ->
                if (item.id == currentSession.id) {
                    item.copy(
                        botId = botId,
                        providerId = providerId,
                        personaId = personaId,
                    )
                } else {
                    item
                }
            }
        }
        persistSessions()
        AppLogger.append(
            "Conversation binding updated: session=$sessionId bot=$botId provider=${providerId.ifBlank { "none" }} persona=${personaId.ifBlank { "none" }}",
        )
    }

    fun updateSessionServiceFlags(
        sessionId: String,
        sessionSttEnabled: Boolean? = null,
        sessionTtsEnabled: Boolean? = null,
    ) {
        val currentSession = session(sessionId)
        _sessions.update { current ->
            current.map { item ->
                if (item.id == currentSession.id) {
                    item.copy(
                        sessionSttEnabled = sessionSttEnabled ?: item.sessionSttEnabled,
                        sessionTtsEnabled = sessionTtsEnabled ?: item.sessionTtsEnabled,
                    )
                } else {
                    item
                }
            }
        }
        persistSessions()
        AppLogger.append(
            "Conversation service flags updated: session=$sessionId stt=${sessionSttEnabled ?: currentSession.sessionSttEnabled} tts=${sessionTtsEnabled ?: currentSession.sessionTtsEnabled}",
        )
    }

    fun syncPersistenceForBot(botId: String, persistConversationLocally: Boolean) {
        persistSessions()
        AppLogger.append(
            "Conversation persistence synced to database for bot=$botId legacyFlag=$persistConversationLocally",
        )
    }

    fun snapshotSessions(): List<ConversationSession> {
        return _sessions.value.map { session ->
            session.copy(
                messages = session.messages.map { message ->
                    message.copy(
                        attachments = message.attachments.map { attachment -> attachment.copy() },
                    )
                },
            )
        }
    }

    fun restoreSessions(restoredSessions: List<ConversationSession>) {
        val normalized = restoredSessions
            .map { session ->
                session.copy(
                    title = session.title.ifBlank { DEFAULT_SESSION_TITLE },
                    botId = session.botId.ifBlank { currentSelectedBotId() },
                    messages = session.messages.sortedBy { it.timestamp },
                )
            }
            .distinctBy { it.id }
            .ifEmpty { defaultSessions() }
            .let(::sortConversationSessions)

        _sessions.value = normalized
        if (initializationLoaded.get()) {
            persistSessions()
        }
        AppLogger.append("Conversation sessions restored: sessions=${normalized.size}")
    }

    fun previewImportedSessions(importedSessions: List<ConversationSession>): ConversationImportPreview {
        val existingKeys = _sessions.value.map { it.importDedupKey() }.toSet()
        val duplicates = importedSessions.filter { it.importDedupKey() in existingKeys }
        val newSessions = importedSessions.filterNot { it.importDedupKey() in existingKeys }
        return ConversationImportPreview(
            totalSessions = importedSessions.size,
            duplicateSessions = duplicates,
            newSessions = newSessions,
        )
    }

    fun importSessions(
        importedSessions: List<ConversationSession>,
        overwriteDuplicates: Boolean,
    ): ConversationImportResult {
        val incoming = importedSessions
            .map { session ->
                session.copy(
                    title = session.title.ifBlank { DEFAULT_SESSION_TITLE },
                        botId = session.botId.ifBlank { currentSelectedBotId() },
                    messages = session.messages.sortedBy { it.timestamp },
                )
            }
            .distinctBy { it.id }

        val currentByKey = _sessions.value.associateBy { it.importDedupKey() }.toMutableMap()
        var importedCount = 0
        var overwrittenCount = 0
        var skippedCount = 0

        incoming.forEach { session ->
            val dedupKey = session.importDedupKey()
            val existingSession = currentByKey[dedupKey]
            when {
                existingSession == null -> {
                    currentByKey[dedupKey] = session
                    importedCount += 1
                }
                overwriteDuplicates -> {
                    currentByKey[dedupKey] = session.copy(id = existingSession.id)
                    overwrittenCount += 1
                }
                else -> skippedCount += 1
            }
        }

        val merged = currentByKey.values
            .toList()
            .ifEmpty { defaultSessions() }
            .let(::sortConversationSessions)

        _sessions.value = merged
        if (initializationLoaded.get()) {
            persistSessions()
        }
        AppLogger.append(
            "Conversation sessions imported: new=$importedCount overwritten=$overwrittenCount skipped=$skippedCount",
        )
        return ConversationImportResult(
            importedCount = importedCount,
            overwrittenCount = overwrittenCount,
            skippedCount = skippedCount,
        )
    }

    private fun createMissingSession(sessionId: String): ConversationSession {
        val created = ConversationSession(
            id = sessionId,
            title = DEFAULT_SESSION_TITLE,
            botId = currentSelectedBotId(),
            personaId = "",
            providerId = "",
            maxContextMessages = 12,
            sessionSttEnabled = true,
            sessionTtsEnabled = true,
            pinned = false,
            titleCustomized = false,
            messages = emptyList(),
        )
        _sessions.update { current -> sortConversationSessions(listOf(created) + current) }
        persistSessions()
        AppLogger.append("Conversation created: session=$sessionId")
        return created
    }

    private fun persistSessions() {
        if (!initializationLoaded.get()) {
            AppLogger.append("Conversation persist skipped: repository is still loading initial sessions")
            return
        }
        repositoryScope.launch {
            persistenceMutex.withLock {
                runCatching {
                    val snapshot = _sessions.value
                    conversationAggregateDao.replaceAll(snapshot.map { it.toWriteModel() })
                }.onFailure { error ->
                    AppLogger.append(
                        "Conversation database persist failed: ${error.message ?: error.javaClass.simpleName}",
                    )
                }
            }
        }
    }

    /**
     * Debounced persistence: coalesces rapid-fire updates (e.g. streaming segments)
     * into a single DB write after [PERSIST_DEBOUNCE_MS] of quiet time.
     */
    private fun debouncedPersist() {
        debouncedPersistJob?.cancel()
        debouncedPersistJob = repositoryScope.launch {
            kotlinx.coroutines.delay(PERSIST_DEBOUNCE_MS)
            persistSessions()
        }
    }

    private fun currentSelectedBotId(): String {
        return selectedBotIdProvider().ifBlank { "qq-main" }
    }

    private suspend fun importLegacySessionsIfNeeded(): Boolean {
        val file = legacyStorageFile ?: return false
        if (!file.exists()) return false
        val legacySessions = loadLegacyConversationSessions(
            file = file,
            defaultTitle = DEFAULT_SESSION_TITLE,
            onFailure = { error ->
                AppLogger.append(
                    "Conversation legacy migration failed: ${error.message ?: error.javaClass.simpleName}",
                )
            },
        )
        if (legacySessions.isEmpty()) return false
        val existingSessions = conversationAggregateDao.listConversationAggregates().mapNotNull { aggregate ->
            runCatching { aggregate.toConversationSession() }.getOrNull()
        }
        val mergedSessions = mergeImportedConversationSessions(
            defaultSessionId = DEFAULT_SESSION_ID,
            existingSessions = existingSessions,
            legacySessions = legacySessions,
            defaultSessionsProvider = ::defaultSessions,
        )
        conversationAggregateDao.replaceAll(mergedSessions.map { it.toWriteModel() })
        AppLogger.append(
            "Conversation legacy JSON imported into database: legacy=${legacySessions.size} merged=${mergedSessions.size}",
        )
        return true
    }

    private fun defaultSessions(): List<ConversationSession> {
        return listOf(
            ConversationSession(
                id = DEFAULT_SESSION_ID,
                title = DEFAULT_SESSION_TITLE,
                botId = currentSelectedBotId(),
                personaId = "",
                providerId = "",
                maxContextMessages = 12,
                sessionSttEnabled = true,
                sessionTtsEnabled = true,
                pinned = false,
                titleCustomized = false,
                messages = listOf(
                    ConversationMessage(
                        id = UUID.randomUUID().toString(),
                        role = "assistant",
                        content = "对话已就绪。配置好模型后就可以开始聊天。",
                        timestamp = System.currentTimeMillis(),
                    ),
                ),
            ),
        )
    }
}



