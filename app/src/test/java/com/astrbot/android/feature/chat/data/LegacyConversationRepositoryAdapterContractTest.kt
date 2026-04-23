package com.astrbot.android.feature.chat.data

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureConversationRepositoryPortAdapterContractTest {
    @Test
    fun semantic_adapter_file_exists_and_legacy_file_is_removed() {
        val semantic = listOf(
            File("src/main/java/com/astrbot/android/feature/chat/data/FeatureConversationRepositoryPortAdapter.kt"),
            File("app/src/main/java/com/astrbot/android/feature/chat/data/FeatureConversationRepositoryPortAdapter.kt"),
        ).first { it.exists() }

        val legacyCandidates = listOf(
            File("src/main/java/com/astrbot/android/feature/chat/data/LegacyConversationRepositoryAdapter.kt"),
            File("app/src/main/java/com/astrbot/android/feature/chat/data/LegacyConversationRepositoryAdapter.kt"),
        )

        assertTrue(semantic.exists())
        assertTrue(legacyCandidates.none { it.exists() })
    }

    @Test
    fun semantic_adapter_explicitly_wraps_conversation_repository_port() {
        val source = listOf(
            File("src/main/java/com/astrbot/android/feature/chat/data/FeatureConversationRepositoryPortAdapter.kt"),
            File("app/src/main/java/com/astrbot/android/feature/chat/data/FeatureConversationRepositoryPortAdapter.kt"),
        ).first { it.exists() }.readText()

        assertTrue(source.contains("ConversationRepositoryPort"))
        assertTrue(source.contains("FeatureConversationRepository"))
        assertFalse(source.contains("RuntimeOrchestrator"))
        assertFalse(source.contains("LegacyConversationRepositoryAdapter"))
    }
}
