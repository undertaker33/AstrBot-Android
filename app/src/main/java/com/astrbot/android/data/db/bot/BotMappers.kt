package com.astrbot.android.data.db

import com.astrbot.android.model.BotProfile

fun BotAggregate.toProfile(): BotProfile {
    return BotProfile(
        id = bot.id,
        platformName = bot.platformName,
        displayName = bot.displayName,
        tag = bot.tag,
        accountHint = bot.accountHint,
        boundQqUins = boundQqUins.sortedBy { it.sortIndex }.map { it.uin },
        triggerWords = triggerWords.sortedBy { it.sortIndex }.map { it.word },
        autoReplyEnabled = bot.autoReplyEnabled,
        persistConversationLocally = bot.persistConversationLocally,
        bridgeMode = bot.bridgeMode,
        bridgeEndpoint = bot.bridgeEndpoint,
        defaultProviderId = bot.defaultProviderId,
        defaultPersonaId = bot.defaultPersonaId,
        configProfileId = bot.configProfileId,
        status = bot.status,
    )
}

fun BotProfile.toWriteModel(): BotWriteModel {
    return BotWriteModel(
        bot = BotEntity(
            id = id,
            platformName = platformName,
            displayName = displayName,
            tag = tag,
            accountHint = accountHint,
            autoReplyEnabled = autoReplyEnabled,
            persistConversationLocally = persistConversationLocally,
            bridgeMode = bridgeMode,
            bridgeEndpoint = bridgeEndpoint,
            defaultProviderId = defaultProviderId,
            defaultPersonaId = defaultPersonaId,
            configProfileId = configProfileId,
            status = status,
            updatedAt = System.currentTimeMillis(),
        ),
        boundQqUins = boundQqUins.mapIndexed { index, uin -> BotBoundQqUinEntity(id, uin, index) },
        triggerWords = triggerWords.mapIndexed { index, word -> BotTriggerWordEntity(id, word, index) },
    )
}
