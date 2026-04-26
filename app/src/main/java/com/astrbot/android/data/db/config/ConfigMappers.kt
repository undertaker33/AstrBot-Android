package com.astrbot.android.data.db

import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.McpServerEntry
import com.astrbot.android.model.SkillEntry
import org.json.JSONArray
import org.json.JSONObject

fun ConfigAggregate.toProfile(): ConfigProfile {
    return ConfigProfile(
        id = config.id,
        name = config.name,
        defaultChatProviderId = config.defaultChatProviderId,
        defaultVisionProviderId = config.defaultVisionProviderId,
        defaultSttProviderId = config.defaultSttProviderId,
        defaultTtsProviderId = config.defaultTtsProviderId,
        sttEnabled = config.sttEnabled,
        ttsEnabled = config.ttsEnabled,
        alwaysTtsEnabled = config.alwaysTtsEnabled,
        ttsReadBracketedContent = config.ttsReadBracketedContent,
        textStreamingEnabled = config.textStreamingEnabled,
        voiceStreamingEnabled = config.voiceStreamingEnabled,
        streamingMessageIntervalMs = config.streamingMessageIntervalMs,
        realWorldTimeAwarenessEnabled = config.realWorldTimeAwarenessEnabled,
        imageCaptionTextEnabled = config.imageCaptionTextEnabled,
        webSearchEnabled = config.webSearchEnabled,
        proactiveEnabled = config.proactiveEnabled,
        includeScheduledTaskConversationContext = config.includeScheduledTaskConversationContext,
        ttsVoiceId = config.ttsVoiceId,
        imageCaptionPrompt = textRules.firstOrNull()?.imageCaptionPrompt.orEmpty(),
        adminUids = adminUids.sortedBy { it.sortIndex }.map { it.uid },
        sessionIsolationEnabled = config.sessionIsolationEnabled,
        wakeWords = wakeWords.sortedBy { it.sortIndex }.map { it.word },
        wakeWordsAdminOnlyEnabled = config.wakeWordsAdminOnlyEnabled,
        privateChatRequiresWakeWord = config.privateChatRequiresWakeWord,
        replyTextPrefix = config.replyTextPrefix,
        quoteSenderMessageEnabled = config.quoteSenderMessageEnabled,
        mentionSenderEnabled = config.mentionSenderEnabled,
        replyOnAtOnlyEnabled = config.replyOnAtOnlyEnabled,
        whitelistEnabled = config.whitelistEnabled,
        whitelistEntries = whitelistEntries.sortedBy { it.sortIndex }.map { it.entry },
        logOnWhitelistMiss = config.logOnWhitelistMiss,
        adminGroupBypassWhitelistEnabled = config.adminGroupBypassWhitelistEnabled,
        adminPrivateBypassWhitelistEnabled = config.adminPrivateBypassWhitelistEnabled,
        ignoreSelfMessageEnabled = config.ignoreSelfMessageEnabled,
        ignoreAtAllEventEnabled = config.ignoreAtAllEventEnabled,
        replyWhenPermissionDenied = config.replyWhenPermissionDenied,
        rateLimitWindowSeconds = config.rateLimitWindowSeconds,
        rateLimitMaxCount = config.rateLimitMaxCount,
        rateLimitStrategy = config.rateLimitStrategy,
        keywordDetectionEnabled = config.keywordDetectionEnabled,
        keywordPatterns = keywordPatterns.sortedBy { it.sortIndex }.map { it.pattern },
        contextLimitStrategy = config.contextLimitStrategy,
        maxContextTurns = config.maxContextTurns,
        dequeueContextTurns = config.dequeueContextTurns,
        llmCompressInstruction = config.llmCompressInstruction,
        llmCompressKeepRecent = config.llmCompressKeepRecent,
        llmCompressProviderId = config.llmCompressProviderId,
        mcpServers = mcpServers.sortedBy { it.sortIndex }.map { it.toEntry() },
        skills = skills.sortedBy { it.sortIndex }.map { it.toEntry() },
    )
}

fun ConfigProfile.toWriteModel(sortIndex: Int): ConfigWriteModel {
    return ConfigWriteModel(
        config = ConfigProfileEntity(
            id = id,
            name = name,
            defaultChatProviderId = defaultChatProviderId,
            defaultVisionProviderId = defaultVisionProviderId,
            defaultSttProviderId = defaultSttProviderId,
            defaultTtsProviderId = defaultTtsProviderId,
            sttEnabled = sttEnabled,
            ttsEnabled = ttsEnabled,
            alwaysTtsEnabled = alwaysTtsEnabled,
            ttsReadBracketedContent = ttsReadBracketedContent,
            textStreamingEnabled = textStreamingEnabled,
            voiceStreamingEnabled = voiceStreamingEnabled,
            streamingMessageIntervalMs = streamingMessageIntervalMs,
            realWorldTimeAwarenessEnabled = realWorldTimeAwarenessEnabled,
            imageCaptionTextEnabled = imageCaptionTextEnabled,
            webSearchEnabled = webSearchEnabled,
            proactiveEnabled = proactiveEnabled,
            includeScheduledTaskConversationContext = includeScheduledTaskConversationContext,
            ttsVoiceId = ttsVoiceId,
            sessionIsolationEnabled = sessionIsolationEnabled,
            wakeWordsAdminOnlyEnabled = wakeWordsAdminOnlyEnabled,
            privateChatRequiresWakeWord = privateChatRequiresWakeWord,
            replyTextPrefix = replyTextPrefix,
            quoteSenderMessageEnabled = quoteSenderMessageEnabled,
            mentionSenderEnabled = mentionSenderEnabled,
            replyOnAtOnlyEnabled = replyOnAtOnlyEnabled,
            whitelistEnabled = whitelistEnabled,
            logOnWhitelistMiss = logOnWhitelistMiss,
            adminGroupBypassWhitelistEnabled = adminGroupBypassWhitelistEnabled,
            adminPrivateBypassWhitelistEnabled = adminPrivateBypassWhitelistEnabled,
            ignoreSelfMessageEnabled = ignoreSelfMessageEnabled,
            ignoreAtAllEventEnabled = ignoreAtAllEventEnabled,
            replyWhenPermissionDenied = replyWhenPermissionDenied,
            rateLimitWindowSeconds = rateLimitWindowSeconds,
            rateLimitMaxCount = rateLimitMaxCount,
            rateLimitStrategy = rateLimitStrategy,
            keywordDetectionEnabled = keywordDetectionEnabled,
            contextLimitStrategy = contextLimitStrategy,
            maxContextTurns = maxContextTurns,
            dequeueContextTurns = dequeueContextTurns,
            llmCompressInstruction = llmCompressInstruction,
            llmCompressKeepRecent = llmCompressKeepRecent,
            llmCompressProviderId = llmCompressProviderId,
            sortIndex = sortIndex,
            updatedAt = System.currentTimeMillis(),
        ),
        adminUids = adminUids.mapIndexed { index, uid -> ConfigAdminUidEntity(id, uid, index) },
        wakeWords = wakeWords.mapIndexed { index, word -> ConfigWakeWordEntity(id, word, index) },
        whitelistEntries = whitelistEntries.mapIndexed { index, entry -> ConfigWhitelistEntryEntity(id, entry, index) },
        keywordPatterns = keywordPatterns.mapIndexed { index, pattern -> ConfigKeywordPatternEntity(id, pattern, index) },
        textRule = ConfigTextRuleEntity(id, imageCaptionPrompt),
        mcpServers = mcpServers.mapIndexed { index, entry -> entry.toEntity(id, index) },
        skills = skills.mapIndexed { index, entry -> entry.toEntity(id, index) },
    )
}

private fun ConfigMcpServerEntity.toEntry(): McpServerEntry {
    return McpServerEntry(
        serverId = serverId,
        name = name,
        url = url,
        transport = transport,
        command = command,
        args = runCatching {
            val arr = JSONArray(argsJson)
            (0 until arr.length()).map { arr.getString(it) }
        }.getOrDefault(emptyList()),
        headers = runCatching {
            val obj = JSONObject(headersJson)
            obj.keys().asSequence().associateWith { key -> obj.getString(key) }
        }.getOrDefault(emptyMap()),
        timeoutSeconds = timeoutSeconds,
        active = active,
    )
}

private fun McpServerEntry.toEntity(configId: String, sortIndex: Int): ConfigMcpServerEntity {
    return ConfigMcpServerEntity(
        configId = configId,
        serverId = serverId,
        name = name,
        url = url,
        transport = transport,
        command = command,
        argsJson = JSONArray(args).toString(),
        headersJson = JSONObject(headers).toString(),
        timeoutSeconds = timeoutSeconds,
        active = active,
        sortIndex = sortIndex,
    )
}

private fun ConfigSkillEntity.toEntry(): SkillEntry {
    return SkillEntry(
        skillId = skillId,
        name = name,
        description = description,
        content = content,
        priority = priority,
        active = active,
    )
}

private fun SkillEntry.toEntity(configId: String, sortIndex: Int): ConfigSkillEntity {
    return ConfigSkillEntity(
        configId = configId,
        skillId = skillId,
        name = name,
        description = description,
        content = content,
        priority = priority,
        active = active,
        sortIndex = sortIndex,
    )
}
