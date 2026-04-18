package com.astrbot.android.feature.chat.data

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyConversationRepositoryAdapterContractTest {
    @Test
    fun adapter_is_the_only_chat_data_file_allowed_to_import_legacy_repository() {
        val root = listOf(
            File("src/main/java/com/astrbot/android/feature/chat"),
            File("app/src/main/java/com/astrbot/android/feature/chat"),
        ).first { it.exists() }

        val offenders = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.name != "LegacyConversationRepositoryAdapter.kt" }
            .filter { it.readText().contains("import com.astrbot.android.data.ConversationRepository") }
            .map { it.relativeTo(root).path }
            .toList()

        assertTrue("Only the legacy adapter may import ConversationRepository: $offenders", offenders.isEmpty())
    }

    @Test
    fun adapter_explicitly_wraps_conversation_repository_port() {
        val source = listOf(
            File("src/main/java/com/astrbot/android/feature/chat/data/LegacyConversationRepositoryAdapter.kt"),
            File("app/src/main/java/com/astrbot/android/feature/chat/data/LegacyConversationRepositoryAdapter.kt"),
        ).first { it.exists() }.readText()

        assertTrue(source.contains("ConversationRepositoryPort"))
        assertTrue(source.contains("ConversationRepository"))
        assertFalse(source.contains("RuntimeOrchestrator"))
    }
}
