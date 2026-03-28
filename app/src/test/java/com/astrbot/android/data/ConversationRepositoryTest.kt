package com.astrbot.android.data

import com.astrbot.android.model.chat.ConversationSession
import com.astrbot.android.model.chat.MessageType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConversationRepositoryTest {
    private lateinit var snapshot: List<ConversationSession>

    @Before
    fun captureSnapshot() {
        snapshot = ConversationRepository.snapshotSessions()
    }

    @After
    fun restoreSnapshot() {
        ConversationRepository.restoreSessions(snapshot)
    }

    @Test
    fun isolated_group_sessions_from_different_users_are_not_treated_as_duplicates() {
        val existing = testSession(
            id = "qq-qq-main-group-20001-user-10001",
            personaId = "persona-a",
        )
        val incoming = testSession(
            id = "qq-qq-main-group-20001-user-10002",
            personaId = "persona-b",
        )

        ConversationRepository.restoreSessions(listOf(existing))

        val preview = ConversationRepository.previewImportedSessions(listOf(incoming))
        val result = ConversationRepository.importSessions(
            importedSessions = listOf(incoming),
            overwriteDuplicates = true,
        )
        val sessions = ConversationRepository.snapshotSessions()

        assertTrue(preview.duplicateSessions.isEmpty())
        assertEquals(1, preview.newSessions.size)
        assertEquals(1, result.importedCount)
        assertEquals(0, result.overwrittenCount)
        assertEquals(
            setOf("persona-a", "persona-b"),
            sessions
                .filter { it.id.startsWith("qq-qq-main-group-20001-user-") }
                .map { it.personaId }
                .toSet(),
        )
    }

    @Test
    fun shared_group_sessions_still_deduplicate_to_a_single_persona_slot() {
        val existing = testSession(
            id = "qq-qq-main-group-20001",
            personaId = "persona-a",
        )
        val incoming = testSession(
            id = "qq-qq-main-group-20001",
            personaId = "persona-b",
        )

        ConversationRepository.restoreSessions(listOf(existing))

        val preview = ConversationRepository.previewImportedSessions(listOf(incoming))
        val result = ConversationRepository.importSessions(
            importedSessions = listOf(incoming),
            overwriteDuplicates = false,
        )
        val sessions = ConversationRepository.snapshotSessions()

        assertEquals(1, preview.duplicateSessions.size)
        assertTrue(preview.newSessions.isEmpty())
        assertEquals(0, result.importedCount)
        assertEquals(0, result.overwrittenCount)
        assertEquals(1, result.skippedCount)
        assertEquals(
            listOf("persona-a"),
            sessions
                .filter { it.id == "qq-qq-main-group-20001" }
                .map { it.personaId },
        )
    }

    @Test
    fun explicit_session_identity_drives_qq_dedup_even_when_ids_change() {
        val existing = testSession(
            id = "local-a",
            personaId = "persona-a",
            platformId = "qq",
            messageType = MessageType.FriendMessage,
            originSessionId = "friend:10001",
        )
        val incoming = testSession(
            id = "local-b",
            personaId = "persona-b",
            platformId = "qq",
            messageType = MessageType.FriendMessage,
            originSessionId = "friend:10001",
        )

        ConversationRepository.restoreSessions(listOf(existing))

        val preview = ConversationRepository.previewImportedSessions(listOf(incoming))

        assertEquals(1, preview.duplicateSessions.size)
        assertTrue(preview.newSessions.isEmpty())
    }

    @Test
    fun pinned_sessions_are_sorted_ahead_of_recent_unpinned_sessions() {
        val unpinnedRecent = testSession(
            id = "recent",
            personaId = "",
            pinned = false,
            latestTimestamp = 300L,
        )
        val pinnedOlder = testSession(
            id = "pinned",
            personaId = "",
            pinned = true,
            latestTimestamp = 100L,
        )

        ConversationRepository.restoreSessions(listOf(unpinnedRecent, pinnedOlder))

        val sessions = ConversationRepository.snapshotSessions()

        assertEquals(listOf("pinned", "recent"), sessions.map { it.id })
    }

    @Test
    fun sync_system_session_title_does_not_override_a_user_renamed_private_session() {
        val sessionId = "qq-qq-main-private-10001"
        ConversationRepository.restoreSessions(
            listOf(
                testSession(
                    id = sessionId,
                    personaId = "",
                    title = "QQP 小明",
                ),
            ),
        )

        ConversationRepository.renameSession(sessionId, "和小明的私聊")
        ConversationRepository.syncSystemSessionTitle(sessionId, "QQP 新昵称")

        val updated = ConversationRepository.session(sessionId)
        assertEquals("和小明的私聊", updated.title)
        assertTrue(updated.titleCustomized)
    }

    private fun testSession(
        id: String,
        personaId: String,
        title: String = id,
        pinned: Boolean = false,
        latestTimestamp: Long = 0L,
        platformId: String = "app",
        messageType: MessageType = MessageType.OtherMessage,
        originSessionId: String = id,
    ): ConversationSession {
        return ConversationSession(
            id = id,
            title = title,
            botId = "qq-main",
            personaId = personaId,
            providerId = "",
            platformId = platformId,
            messageType = messageType,
            originSessionId = originSessionId,
            maxContextMessages = 12,
            sessionSttEnabled = true,
            sessionTtsEnabled = true,
            pinned = pinned,
            titleCustomized = false,
            messages = if (latestTimestamp > 0L) {
                listOf(
                    com.astrbot.android.model.chat.ConversationMessage(
                        id = "$id-message",
                        role = "assistant",
                        content = "seed",
                        timestamp = latestTimestamp,
                    ),
                )
            } else {
                emptyList()
            },
        )
    }
}
