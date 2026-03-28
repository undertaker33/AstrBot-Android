package com.astrbot.android.data.backup

import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.chat.ConversationSession
import com.astrbot.android.model.PersonaProfile
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ProviderType
import com.astrbot.android.model.SavedQqAccount
import com.astrbot.android.model.TtsVoiceReferenceAsset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppBackupImportPlannerTest {
    @Test
    fun preview_reports_duplicates_per_module() {
        val current = snapshot(
            bots = listOf(bot("bot-a")),
            providers = listOf(provider("provider-a")),
            personas = listOf(persona("persona-a")),
            configs = listOf(config("config-a")),
            conversations = listOf(conversation("qq-main", "group", "1001", "user-a")),
            savedAccounts = listOf(savedAccount("10000")),
            ttsAssets = listOf(ttsAsset("tts-a")),
        )
        val incoming = snapshot(
            bots = listOf(bot("bot-a"), bot("bot-b")),
            providers = listOf(provider("provider-a"), provider("provider-b")),
            personas = listOf(persona("persona-a")),
            configs = listOf(config("config-a"), config("config-b")),
            conversations = listOf(
                conversation("qq-main", "group", "1001", "user-b"),
                conversation("qq-main", "private", "2002"),
            ),
            savedAccounts = listOf(savedAccount("10000"), savedAccount("20000")),
            ttsAssets = listOf(ttsAsset("tts-a"), ttsAsset("tts-b")),
        )

        val preview = AppBackupImportPlanner.preview(current, incoming)

        assertEquals(1, preview.bots.duplicateCount)
        assertEquals(1, preview.providers.duplicateCount)
        assertEquals(1, preview.personas.duplicateCount)
        assertEquals(1, preview.configs.duplicateCount)
        assertEquals(0, preview.conversations.duplicateCount)
        assertEquals(1, preview.qqAccounts.duplicateCount)
        assertEquals(1, preview.ttsAssets.duplicateCount)
        assertEquals(1, preview.bots.newCount)
        assertEquals(2, preview.conversations.newCount)
    }

    @Test
    fun merge_skip_duplicates_keeps_existing_records_and_adds_new_ones() {
        val currentConversation = conversation("qq-main", "group", "1001", "user-a", title = "Current")
        val incomingConversation = conversation("qq-main", "group", "1001", "user-b", title = "Incoming")
        val current = snapshot(
            bots = listOf(bot("bot-a", displayName = "Current Bot")),
            conversations = listOf(currentConversation),
            savedAccounts = listOf(savedAccount("10000", "Current Nick")),
            ttsAssets = listOf(ttsAsset("tts-a", name = "Current TTS")),
        )
        val incoming = snapshot(
            bots = listOf(bot("bot-a", displayName = "Incoming Bot"), bot("bot-b", displayName = "New Bot")),
            conversations = listOf(incomingConversation, conversation("qq-main", "private", "3003")),
            savedAccounts = listOf(savedAccount("10000", "Incoming Nick"), savedAccount("20000", "New Nick")),
            ttsAssets = listOf(ttsAsset("tts-a", name = "Incoming TTS"), ttsAsset("tts-b", name = "New TTS")),
        )

        val merged = AppBackupImportPlanner.merge(current, incoming, AppBackupImportMode.MERGE_SKIP_DUPLICATES)

        assertEquals(2, merged.bots.size)
        assertEquals("Current Bot", merged.bots.first { it.id == "bot-a" }.displayName)
        assertEquals(3, merged.conversations.size)
        assertEquals("Current", merged.conversations.first { it.id == currentConversation.id }.title)
        assertEquals("Current Nick", merged.savedAccounts.first { it.uin == "10000" }.nickName)
        assertEquals("Current TTS", merged.ttsAssets.first { it.id == "tts-a" }.name)
    }

    @Test
    fun merge_overwrite_duplicates_replaces_existing_records_but_preserves_conversation_id() {
        val currentConversation = conversation("qq-main", "private", "2002", title = "Current")
        val incomingConversation = conversation("qq-main", "private", "2002", title = "Incoming")
        val current = snapshot(
            bots = listOf(bot("bot-a", displayName = "Current Bot")),
            conversations = listOf(currentConversation),
            savedAccounts = listOf(savedAccount("10000", "Current Nick")),
            ttsAssets = listOf(ttsAsset("tts-a", name = "Current TTS")),
        )
        val incoming = snapshot(
            bots = listOf(bot("bot-a", displayName = "Incoming Bot")),
            conversations = listOf(incomingConversation),
            savedAccounts = listOf(savedAccount("10000", "Incoming Nick")),
            ttsAssets = listOf(ttsAsset("tts-a", name = "Incoming TTS")),
        )

        val merged = AppBackupImportPlanner.merge(current, incoming, AppBackupImportMode.MERGE_OVERWRITE_DUPLICATES)

        assertEquals("Incoming Bot", merged.bots.single().displayName)
        assertEquals(currentConversation.id, merged.conversations.single().id)
        assertEquals("Incoming", merged.conversations.single().title)
        assertEquals("Incoming Nick", merged.savedAccounts.single().nickName)
        assertEquals("Incoming TTS", merged.ttsAssets.single().name)
    }

    @Test
    fun replace_all_uses_incoming_snapshot_directly() {
        val current = snapshot(bots = listOf(bot("bot-a")))
        val incoming = snapshot(bots = listOf(bot("bot-b")), savedAccounts = listOf(savedAccount("20000")))

        val replaced = AppBackupImportPlanner.merge(current, incoming, AppBackupImportMode.REPLACE_ALL)

        assertEquals(listOf("bot-b"), replaced.bots.map { it.id })
        assertEquals(listOf("20000"), replaced.savedAccounts.map { it.uin })
        assertTrue(replaced.providers.isEmpty())
    }

    @Test
    fun merge_with_per_module_modes_applies_each_strategy_independently() {
        val current = snapshot(
            bots = listOf(bot("bot-a", displayName = "Current Bot")),
            providers = listOf(provider("provider-a")),
            conversations = listOf(conversation("qq-main", "private", "2002", title = "Current Chat")),
            savedAccounts = listOf(savedAccount("10000", "Current Nick")),
        )
        val incoming = snapshot(
            bots = listOf(bot("bot-a", displayName = "Incoming Bot"), bot("bot-b", displayName = "New Bot")),
            providers = listOf(provider("provider-a"), provider("provider-b")),
            conversations = listOf(conversation("qq-main", "private", "2002", title = "Incoming Chat")),
            savedAccounts = listOf(savedAccount("10000", "Incoming Nick"), savedAccount("20000", "New Nick")),
        )

        val merged = AppBackupImportPlanner.merge(
            current = current,
            incoming = incoming,
            mode = AppBackupImportPlan(
                bots = AppBackupImportMode.MERGE_OVERWRITE_DUPLICATES,
                providers = AppBackupImportMode.MERGE_SKIP_DUPLICATES,
                conversations = AppBackupImportMode.REPLACE_ALL,
                qqAccounts = AppBackupImportMode.MERGE_SKIP_DUPLICATES,
            ),
        )

        assertEquals("Incoming Bot", merged.bots.first { it.id == "bot-a" }.displayName)
        assertEquals(2, merged.providers.size)
        assertEquals(1, merged.conversations.size)
        assertEquals("Incoming Chat", merged.conversations.single().title)
        assertEquals("Current Nick", merged.savedAccounts.first { it.uin == "10000" }.nickName)
        assertEquals("20000", merged.savedAccounts.last().uin)
    }

    private fun snapshot(
        bots: List<BotProfile> = emptyList(),
        providers: List<ProviderProfile> = emptyList(),
        personas: List<PersonaProfile> = emptyList(),
        configs: List<ConfigProfile> = emptyList(),
        conversations: List<ConversationSession> = emptyList(),
        savedAccounts: List<SavedQqAccount> = emptyList(),
        ttsAssets: List<TtsVoiceReferenceAsset> = emptyList(),
    ) = AppBackupSnapshot(
        bots = bots,
        providers = providers,
        personas = personas,
        configs = configs,
        conversations = conversations,
        quickLoginUin = savedAccounts.firstOrNull()?.uin.orEmpty(),
        savedAccounts = savedAccounts,
        ttsAssets = ttsAssets,
    )

    private fun bot(id: String, displayName: String = id) = BotProfile(
        id = id,
        displayName = displayName,
        configProfileId = "config-a",
    )

    private fun provider(id: String) = ProviderProfile(
        id = id,
        name = id,
        baseUrl = "https://example.com",
        model = "model",
        providerType = ProviderType.OPENAI_COMPATIBLE,
        apiKey = "",
        capabilities = setOf(ProviderCapability.CHAT),
    )

    private fun persona(id: String) = PersonaProfile(
        id = id,
        name = id,
        systemPrompt = "prompt",
        enabledTools = emptySet(),
    )

    private fun config(id: String) = ConfigProfile(id = id, name = id)

    private fun conversation(
        botId: String,
        type: String,
        peerId: String,
        userId: String? = null,
        title: String = peerId,
        id: String = buildString {
            append("qq-")
            append(botId)
            append("-")
            append(type)
            append("-")
            append(peerId)
            if (type == "group" && userId != null) {
                append("-user-")
                append(userId)
            }
        },
    ) = ConversationSession(
        id = id,
        title = title,
        botId = botId,
        personaId = "",
        providerId = "",
        maxContextMessages = 12,
        sessionSttEnabled = true,
        sessionTtsEnabled = true,
        pinned = false,
        titleCustomized = false,
        messages = listOf(
            ConversationMessage(
                id = "message-$peerId",
                role = "user",
                content = "hello",
                timestamp = 1000L,
            ),
        ),
    )

    private fun savedAccount(uin: String, nickName: String = uin) = SavedQqAccount(
        uin = uin,
        nickName = nickName,
    )

    private fun ttsAsset(id: String, name: String = id) = TtsVoiceReferenceAsset(
        id = id,
        name = name,
    )
}
