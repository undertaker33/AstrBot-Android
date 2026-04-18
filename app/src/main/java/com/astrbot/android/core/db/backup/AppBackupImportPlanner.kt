package com.astrbot.android.core.db.backup

import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.chat.ConversationSession
import com.astrbot.android.model.PersonaProfile
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.SavedQqAccount
import com.astrbot.android.model.TtsVoiceReferenceAsset
import com.astrbot.android.model.chat.importDedupKey

enum class AppBackupModuleKind {
    BOTS,
    PROVIDERS,
    PERSONAS,
    CONFIGS,
    CONVERSATIONS,
    QQ_ACCOUNTS,
    TTS_ASSETS,
}

enum class AppBackupImportMode {
    REPLACE_ALL,
    MERGE_SKIP_DUPLICATES,
    MERGE_OVERWRITE_DUPLICATES,
}

data class AppBackupImportPlan(
    val bots: AppBackupImportMode = AppBackupImportMode.REPLACE_ALL,
    val providers: AppBackupImportMode = AppBackupImportMode.REPLACE_ALL,
    val personas: AppBackupImportMode = AppBackupImportMode.REPLACE_ALL,
    val configs: AppBackupImportMode = AppBackupImportMode.REPLACE_ALL,
    val conversations: AppBackupImportMode = AppBackupImportMode.REPLACE_ALL,
    val qqAccounts: AppBackupImportMode = AppBackupImportMode.REPLACE_ALL,
    val ttsAssets: AppBackupImportMode = AppBackupImportMode.REPLACE_ALL,
)

data class AppBackupSnapshot(
    val bots: List<BotProfile> = emptyList(),
    val providers: List<ProviderProfile> = emptyList(),
    val personas: List<PersonaProfile> = emptyList(),
    val configs: List<ConfigProfile> = emptyList(),
    val conversations: List<ConversationSession> = emptyList(),
    val quickLoginUin: String = "",
    val savedAccounts: List<SavedQqAccount> = emptyList(),
    val ttsAssets: List<TtsVoiceReferenceAsset> = emptyList(),
    val appState: AppBackupAppState = AppBackupAppState(),
)

data class AppBackupModuleConflict(
    val duplicateCount: Int = 0,
    val newCount: Int = 0,
)

data class AppBackupConflictPreview(
    val bots: AppBackupModuleConflict = AppBackupModuleConflict(),
    val providers: AppBackupModuleConflict = AppBackupModuleConflict(),
    val personas: AppBackupModuleConflict = AppBackupModuleConflict(),
    val configs: AppBackupModuleConflict = AppBackupModuleConflict(),
    val conversations: AppBackupModuleConflict = AppBackupModuleConflict(),
    val qqAccounts: AppBackupModuleConflict = AppBackupModuleConflict(),
    val ttsAssets: AppBackupModuleConflict = AppBackupModuleConflict(),
)

object AppBackupImportPlanner {
    fun preview(
        current: AppBackupSnapshot,
        incoming: AppBackupSnapshot,
    ): AppBackupConflictPreview {
        return AppBackupConflictPreview(
            bots = conflict(current.bots.map { it.id }.toSet(), incoming.bots.map { it.id }),
            providers = conflict(current.providers.map { it.id }.toSet(), incoming.providers.map { it.id }),
            personas = conflict(current.personas.map { it.id }.toSet(), incoming.personas.map { it.id }),
            configs = conflict(current.configs.map { it.id }.toSet(), incoming.configs.map { it.id }),
            conversations = conflict(
                current.conversations.map { it.importDedupKey() }.toSet(),
                incoming.conversations.map { it.importDedupKey() },
            ),
            qqAccounts = conflict(current.savedAccounts.map { it.uin }.toSet(), incoming.savedAccounts.map { it.uin }),
            ttsAssets = conflict(current.ttsAssets.map { it.id }.toSet(), incoming.ttsAssets.map { it.id }),
        )
    }

    fun merge(
        current: AppBackupSnapshot,
        incoming: AppBackupSnapshot,
        mode: AppBackupImportMode,
    ): AppBackupSnapshot {
        return merge(
            current = current,
            incoming = incoming,
            mode = AppBackupImportPlan(
                bots = mode,
                providers = mode,
                personas = mode,
                configs = mode,
                conversations = mode,
                qqAccounts = mode,
                ttsAssets = mode,
            ),
        )
    }

    fun merge(
        current: AppBackupSnapshot,
        incoming: AppBackupSnapshot,
        mode: AppBackupImportPlan,
    ): AppBackupSnapshot {
        return AppBackupSnapshot(
            bots = mergeByMode(
                mode = mode.bots,
                replaceAll = { incoming.bots },
                mergeSkip = { mergeById(current.bots, incoming.bots, overwriteDuplicates = false) },
                mergeOverwrite = { mergeById(current.bots, incoming.bots, overwriteDuplicates = true) },
            ),
            providers = mergeByMode(
                mode = mode.providers,
                replaceAll = { incoming.providers },
                mergeSkip = { mergeById(current.providers, incoming.providers, overwriteDuplicates = false) },
                mergeOverwrite = { mergeById(current.providers, incoming.providers, overwriteDuplicates = true) },
            ),
            personas = mergeByMode(
                mode = mode.personas,
                replaceAll = { incoming.personas },
                mergeSkip = { mergeById(current.personas, incoming.personas, overwriteDuplicates = false) },
                mergeOverwrite = { mergeById(current.personas, incoming.personas, overwriteDuplicates = true) },
            ),
            configs = mergeByMode(
                mode = mode.configs,
                replaceAll = { incoming.configs },
                mergeSkip = { mergeById(current.configs, incoming.configs, overwriteDuplicates = false) },
                mergeOverwrite = { mergeById(current.configs, incoming.configs, overwriteDuplicates = true) },
            ),
            conversations = mergeByMode(
                mode = mode.conversations,
                replaceAll = { incoming.conversations },
                mergeSkip = { mergeConversations(current.conversations, incoming.conversations, overwriteDuplicates = false) },
                mergeOverwrite = { mergeConversations(current.conversations, incoming.conversations, overwriteDuplicates = true) },
            ),
            quickLoginUin = when (mode.qqAccounts) {
                AppBackupImportMode.REPLACE_ALL,
                AppBackupImportMode.MERGE_OVERWRITE_DUPLICATES,
                -> incoming.quickLoginUin.ifBlank { current.quickLoginUin }
                AppBackupImportMode.MERGE_SKIP_DUPLICATES -> current.quickLoginUin.ifBlank { incoming.quickLoginUin }
            },
            savedAccounts = mergeByMode(
                mode = mode.qqAccounts,
                replaceAll = { incoming.savedAccounts },
                mergeSkip = { mergeSavedAccounts(current.savedAccounts, incoming.savedAccounts, overwriteDuplicates = false) },
                mergeOverwrite = { mergeSavedAccounts(current.savedAccounts, incoming.savedAccounts, overwriteDuplicates = true) },
            ),
            ttsAssets = mergeByMode(
                mode = mode.ttsAssets,
                replaceAll = { incoming.ttsAssets },
                mergeSkip = { mergeById(current.ttsAssets, incoming.ttsAssets, overwriteDuplicates = false) },
                mergeOverwrite = { mergeById(current.ttsAssets, incoming.ttsAssets, overwriteDuplicates = true) },
            ),
            appState = mergeAppState(current.appState, incoming.appState, mode),
        )
    }

    private fun conflict(
        currentKeys: Set<String>,
        incomingKeys: List<String>,
    ): AppBackupModuleConflict {
        val duplicateCount = incomingKeys.count { it in currentKeys }
        return AppBackupModuleConflict(
            duplicateCount = duplicateCount,
            newCount = incomingKeys.size - duplicateCount,
        )
    }

    private fun <T> mergeById(
        current: List<T>,
        incoming: List<T>,
        overwriteDuplicates: Boolean,
    ): List<T> where T : Any {
        val idAccessor: (T) -> String = when (current.firstOrNull() ?: incoming.firstOrNull()) {
            is BotProfile -> { item -> (item as BotProfile).id }
            is ProviderProfile -> { item -> (item as ProviderProfile).id }
            is PersonaProfile -> { item -> (item as PersonaProfile).id }
            is ConfigProfile -> { item -> (item as ConfigProfile).id }
            is TtsVoiceReferenceAsset -> { item -> (item as TtsVoiceReferenceAsset).id }
            else -> return current
        }
        val merged = LinkedHashMap<String, T>()
        current.forEach { item -> merged[idAccessor(item)] = item }
        incoming.forEach { item ->
            val id = idAccessor(item)
            if (overwriteDuplicates || id !in merged) {
                merged[id] = item
            }
        }
        return merged.values.toList()
    }

    private fun <T> mergeByMode(
        mode: AppBackupImportMode,
        replaceAll: () -> List<T>,
        mergeSkip: () -> List<T>,
        mergeOverwrite: () -> List<T>,
    ): List<T> {
        return when (mode) {
            AppBackupImportMode.REPLACE_ALL -> replaceAll()
            AppBackupImportMode.MERGE_SKIP_DUPLICATES -> mergeSkip()
            AppBackupImportMode.MERGE_OVERWRITE_DUPLICATES -> mergeOverwrite()
        }
    }

    private fun mergeAppState(
        current: AppBackupAppState,
        incoming: AppBackupAppState,
        mode: AppBackupImportPlan,
    ): AppBackupAppState {
        val selectedBotId = when (mode.bots) {
            AppBackupImportMode.REPLACE_ALL,
            AppBackupImportMode.MERGE_OVERWRITE_DUPLICATES,
            -> incoming.selectedBotId.ifBlank { current.selectedBotId }
            AppBackupImportMode.MERGE_SKIP_DUPLICATES -> current.selectedBotId.ifBlank { incoming.selectedBotId }
        }
        val selectedConfigId = when (mode.configs) {
            AppBackupImportMode.REPLACE_ALL,
            AppBackupImportMode.MERGE_OVERWRITE_DUPLICATES,
            -> incoming.selectedConfigId.ifBlank { current.selectedConfigId }
            AppBackupImportMode.MERGE_SKIP_DUPLICATES -> current.selectedConfigId.ifBlank { incoming.selectedConfigId }
        }
        return current.copy(
            selectedBotId = selectedBotId,
            selectedConfigId = selectedConfigId,
            preferredChatProvider = incoming.preferredChatProvider.ifBlank { current.preferredChatProvider },
            themeMode = incoming.themeMode.ifBlank { current.themeMode },
        )
    }

    private fun mergeSavedAccounts(
        current: List<SavedQqAccount>,
        incoming: List<SavedQqAccount>,
        overwriteDuplicates: Boolean,
    ): List<SavedQqAccount> {
        val merged = LinkedHashMap<String, SavedQqAccount>()
        current.forEach { account -> merged[account.uin] = account }
        incoming.forEach { account ->
            if (overwriteDuplicates || account.uin !in merged) {
                merged[account.uin] = account
            }
        }
        return merged.values.toList()
    }

    private fun mergeConversations(
        current: List<ConversationSession>,
        incoming: List<ConversationSession>,
        overwriteDuplicates: Boolean,
    ): List<ConversationSession> {
        val merged = LinkedHashMap<String, ConversationSession>()
        current.forEach { session -> merged[session.importDedupKey()] = session }
        incoming.forEach { session ->
            val dedupKey = session.importDedupKey()
            val existing = merged[dedupKey]
            when {
                existing == null -> merged[dedupKey] = session
                overwriteDuplicates -> merged[dedupKey] = session.copy(id = existing.id)
            }
        }
        return merged.values.toList()
    }
}

internal fun moduleOnlyImportPlan(
    module: AppBackupModuleKind,
    mode: AppBackupImportMode,
): AppBackupImportPlan {
    return AppBackupImportPlan(
        bots = if (module == AppBackupModuleKind.BOTS) mode else AppBackupImportMode.MERGE_SKIP_DUPLICATES,
        providers = if (module == AppBackupModuleKind.PROVIDERS) mode else AppBackupImportMode.MERGE_SKIP_DUPLICATES,
        personas = if (module == AppBackupModuleKind.PERSONAS) mode else AppBackupImportMode.MERGE_SKIP_DUPLICATES,
        configs = if (module == AppBackupModuleKind.CONFIGS) mode else AppBackupImportMode.MERGE_SKIP_DUPLICATES,
        conversations = if (module == AppBackupModuleKind.CONVERSATIONS) mode else AppBackupImportMode.MERGE_SKIP_DUPLICATES,
        qqAccounts = if (module == AppBackupModuleKind.QQ_ACCOUNTS) mode else AppBackupImportMode.MERGE_SKIP_DUPLICATES,
        ttsAssets = if (module == AppBackupModuleKind.TTS_ASSETS) mode else AppBackupImportMode.MERGE_SKIP_DUPLICATES,
    )
}

internal fun moduleConflictFor(
    module: AppBackupModuleKind,
    preview: AppBackupConflictPreview,
): AppBackupModuleConflict {
    return when (module) {
        AppBackupModuleKind.BOTS -> preview.bots
        AppBackupModuleKind.PROVIDERS -> preview.providers
        AppBackupModuleKind.PERSONAS -> preview.personas
        AppBackupModuleKind.CONFIGS -> preview.configs
        AppBackupModuleKind.CONVERSATIONS -> preview.conversations
        AppBackupModuleKind.QQ_ACCOUNTS -> preview.qqAccounts
        AppBackupModuleKind.TTS_ASSETS -> preview.ttsAssets
    }
}

