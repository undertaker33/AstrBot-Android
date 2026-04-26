package com.astrbot.android.core.db.backup

import android.content.Context
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ClonedVoiceBinding
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.FeatureSupportState
import com.astrbot.android.model.PersonaProfile
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ProviderType
import com.astrbot.android.model.SavedQqAccount
import com.astrbot.android.model.TtsVoiceReferenceAsset
import com.astrbot.android.model.TtsVoiceReferenceClip
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

internal fun moduleSnapshot(
    module: AppBackupModuleKind,
    modules: AppBackupModules,
): AppBackupModuleSnapshot {
    return when (module) {
        AppBackupModuleKind.BOTS -> modules.bots
        AppBackupModuleKind.PROVIDERS -> modules.providers
        AppBackupModuleKind.PERSONAS -> modules.personas
        AppBackupModuleKind.CONFIGS -> modules.configs
        AppBackupModuleKind.CONVERSATIONS -> modules.conversations
        AppBackupModuleKind.QQ_ACCOUNTS -> modules.qqLogin
        AppBackupModuleKind.TTS_ASSETS -> modules.ttsAssets
    }
}

internal fun moduleOnlyManifest(
    module: AppBackupModuleKind,
    manifest: AppBackupManifest,
): AppBackupManifest {
    val selected = moduleSnapshot(module, manifest.modules)
    return manifest.copy(
        modules = when (module) {
            AppBackupModuleKind.BOTS -> AppBackupModules(bots = selected)
            AppBackupModuleKind.PROVIDERS -> AppBackupModules(providers = selected)
            AppBackupModuleKind.PERSONAS -> AppBackupModules(personas = selected)
            AppBackupModuleKind.CONFIGS -> AppBackupModules(configs = selected)
            AppBackupModuleKind.CONVERSATIONS -> AppBackupModules(conversations = selected)
            AppBackupModuleKind.QQ_ACCOUNTS -> AppBackupModules(qqLogin = selected)
            AppBackupModuleKind.TTS_ASSETS -> AppBackupModules(ttsAssets = selected)
        },
    )
}

internal fun moduleCountFromRestoreResult(
    module: AppBackupModuleKind,
    result: AppBackupRestoreResult,
): Int {
    return when (module) {
        AppBackupModuleKind.BOTS -> result.botCount
        AppBackupModuleKind.PROVIDERS -> result.providerCount
        AppBackupModuleKind.PERSONAS -> result.personaCount
        AppBackupModuleKind.CONFIGS -> result.configCount
        AppBackupModuleKind.CONVERSATIONS -> result.conversationCount
        AppBackupModuleKind.QQ_ACCOUNTS -> result.qqAccountCount
        AppBackupModuleKind.TTS_ASSETS -> result.ttsAssetCount
    }
}

internal fun JSONObject.toBotProfile(): BotProfile {
    return BotProfile(
        id = optString("id"),
        platformName = optString("platformName", "QQ"),
        displayName = optString("displayName"),
        tag = optString("tag"),
        accountHint = optString("accountHint"),
        boundQqUins = optJSONArray("boundQqUins").jsonStringList(),
        triggerWords = optJSONArray("triggerWords").jsonStringList(),
        autoReplyEnabled = optBoolean("autoReplyEnabled", true),
        persistConversationLocally = optBoolean("persistConversationLocally", false),
        bridgeMode = optString("bridgeMode"),
        bridgeEndpoint = optString("bridgeEndpoint"),
        defaultProviderId = optString("defaultProviderId"),
        defaultPersonaId = optString("defaultPersonaId"),
        configProfileId = optString("configProfileId"),
        status = optString("status"),
    )
}

internal fun JSONObject.toProviderProfile(): ProviderProfile {
    val providerType = runCatching { ProviderType.valueOf(optString("providerType")) }.getOrDefault(ProviderType.OPENAI_COMPATIBLE)
    return ProviderProfile(
        id = optString("id"),
        name = optString("name"),
        baseUrl = optString("baseUrl"),
        model = optString("model"),
        providerType = providerType,
        apiKey = optString("apiKey"),
        capabilities = optJSONArray("capabilities").jsonStringList()
            .mapNotNull { value -> runCatching { ProviderCapability.valueOf(value) }.getOrNull() }
            .toSet(),
        enabled = optBoolean("enabled", true),
        multimodalRuleSupport = optString("multimodalRuleSupport").toFeatureSupportState(),
        multimodalProbeSupport = optString("multimodalProbeSupport").toFeatureSupportState(),
        nativeStreamingRuleSupport = optString("nativeStreamingRuleSupport").toFeatureSupportState(),
        nativeStreamingProbeSupport = optString("nativeStreamingProbeSupport").toFeatureSupportState(),
        sttProbeSupport = optString("sttProbeSupport").toFeatureSupportState(),
        ttsProbeSupport = optString("ttsProbeSupport").toFeatureSupportState(),
        ttsVoiceOptions = optJSONArray("ttsVoiceOptions").jsonStringList(),
    )
}

internal fun JSONObject.toPersonaProfile(): PersonaProfile {
    return PersonaProfile(
        id = optString("id"),
        name = optString("name"),
        tag = optString("tag"),
        systemPrompt = optString("systemPrompt"),
        enabledTools = optJSONArray("enabledTools").jsonStringList().toSet(),
        defaultProviderId = optString("defaultProviderId"),
        maxContextMessages = optInt("maxContextMessages", 12),
        enabled = optBoolean("enabled", true),
    )
}

internal fun JSONObject.toConfigProfile(): ConfigProfile {
    return ConfigProfile(
        id = optString("id"),
        name = optString("name"),
        defaultChatProviderId = optString("defaultChatProviderId"),
        defaultVisionProviderId = optString("defaultVisionProviderId"),
        defaultSttProviderId = optString("defaultSttProviderId"),
        defaultTtsProviderId = optString("defaultTtsProviderId"),
        sttEnabled = optBoolean("sttEnabled", false),
        ttsEnabled = optBoolean("ttsEnabled", false),
        alwaysTtsEnabled = optBoolean("alwaysTtsEnabled", false),
        ttsReadBracketedContent = optBoolean("ttsReadBracketedContent", true),
        textStreamingEnabled = optBoolean("textStreamingEnabled", false),
        voiceStreamingEnabled = optBoolean("voiceStreamingEnabled", false),
        streamingMessageIntervalMs = optInt("streamingMessageIntervalMs", 120),
        realWorldTimeAwarenessEnabled = optBoolean("realWorldTimeAwarenessEnabled", false),
        imageCaptionTextEnabled = optBoolean("imageCaptionTextEnabled", false),
        webSearchEnabled = optBoolean("webSearchEnabled", false),
        proactiveEnabled = optBoolean("proactiveEnabled", false),
        includeScheduledTaskConversationContext = optBoolean("includeScheduledTaskConversationContext", false),
        ttsVoiceId = optString("ttsVoiceId"),
        imageCaptionPrompt = optString("imageCaptionPrompt"),
        adminUids = optJSONArray("adminUids").jsonStringList(),
        sessionIsolationEnabled = optBoolean("sessionIsolationEnabled", false),
        wakeWords = optJSONArray("wakeWords").jsonStringList(),
        wakeWordsAdminOnlyEnabled = optBoolean("wakeWordsAdminOnlyEnabled", false),
        privateChatRequiresWakeWord = optBoolean("privateChatRequiresWakeWord", false),
        replyTextPrefix = optString("replyTextPrefix"),
        quoteSenderMessageEnabled = optBoolean("quoteSenderMessageEnabled", false),
        mentionSenderEnabled = optBoolean("mentionSenderEnabled", false),
        replyOnAtOnlyEnabled = optBoolean("replyOnAtOnlyEnabled", true),
        whitelistEnabled = optBoolean("whitelistEnabled", false),
        whitelistEntries = optJSONArray("whitelistEntries").jsonStringList(),
        logOnWhitelistMiss = optBoolean("logOnWhitelistMiss", false),
        adminGroupBypassWhitelistEnabled = optBoolean("adminGroupBypassWhitelistEnabled", true),
        adminPrivateBypassWhitelistEnabled = optBoolean("adminPrivateBypassWhitelistEnabled", true),
        ignoreSelfMessageEnabled = optBoolean("ignoreSelfMessageEnabled", true),
        ignoreAtAllEventEnabled = optBoolean("ignoreAtAllEventEnabled", true),
        replyWhenPermissionDenied = optBoolean("replyWhenPermissionDenied", false),
        rateLimitWindowSeconds = optInt("rateLimitWindowSeconds", 0),
        rateLimitMaxCount = optInt("rateLimitMaxCount", 0),
        rateLimitStrategy = optString("rateLimitStrategy", "drop"),
        keywordDetectionEnabled = optBoolean("keywordDetectionEnabled", false),
        keywordPatterns = optJSONArray("keywordPatterns").jsonStringList(),
    )
}

internal fun JSONObject.toTtsAsset(
    context: Context,
    materializeFiles: Boolean,
    extractedFiles: Map<String, File> = emptyMap(),
): TtsVoiceReferenceAsset {
    val clipsArray = optJSONArray("clips") ?: JSONArray()
    val restoredClips = buildList {
        for (index in 0 until clipsArray.length()) {
            val clipObject = clipsArray.optJSONObject(index) ?: continue
            val fileName = clipObject.optString("embeddedFileName").ifBlank { "${clipObject.optString("id")}.bin" }
            val archivePath = clipObject.optString("archivePath").ifBlank {
                clipObject.optString("embeddedFilePath")
            }
            val localPath = when {
                materializeFiles && archivePath.isNotBlank() && archivePath in extractedFiles -> {
                    materializeImportedTtsFile(
                        context = context,
                        fileName = fileName,
                        source = extractedFiles.getValue(archivePath),
                    )
                }
                else -> clipObject.optString("embeddedDataBase64")
                    .takeIf { it.isNotBlank() && materializeFiles }
                    ?.let { encoded ->
                        val directory = File(context.filesDir, "assets/tts-reference-audio").apply { mkdirs() }
                        val destination = File(directory, "${UUID.randomUUID()}-${fileName}")
                        destination.writeBytes(java.util.Base64.getDecoder().decode(encoded))
                        destination.absolutePath
                    }
                    .orEmpty()
            }
            add(
                TtsVoiceReferenceClip(
                    id = clipObject.optString("id").ifBlank { UUID.randomUUID().toString() },
                    localPath = localPath,
                    durationMs = clipObject.optLong("durationMs", 0L),
                    sampleRateHz = clipObject.optInt("sampleRateHz", 0),
                    createdAt = clipObject.optLong("createdAt", System.currentTimeMillis()),
                ),
            )
        }
    }
    val primaryClip = restoredClips.maxByOrNull { it.durationMs }
    return TtsVoiceReferenceAsset(
        id = optString("id"),
        name = optString("name"),
        source = optString("source"),
        localPath = primaryClip?.localPath.orEmpty(),
        remoteUrl = optString("remoteUrl"),
        durationMs = optLong("durationMs", primaryClip?.durationMs ?: 0L),
        sampleRateHz = optInt("sampleRateHz", primaryClip?.sampleRateHz ?: 0),
        clips = restoredClips,
        providerBindings = optJSONArray("providerBindings").toVoiceBindings(),
        createdAt = optLong("createdAt", System.currentTimeMillis()),
    )
}

private fun materializeImportedTtsFile(
    context: Context,
    fileName: String,
    source: File,
): String {
    val directory = File(context.filesDir, "assets/tts-reference-audio").apply { mkdirs() }
    val destination = File(directory, "${UUID.randomUUID()}-${fileName.ifBlank { source.name }}")
    source.inputStream().use { input ->
        destination.outputStream().use { output -> input.copyTo(output) }
    }
    return destination.absolutePath
}

internal fun JSONArray?.toSavedAccounts(): List<SavedQqAccount> {
    val array = this ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            add(
                SavedQqAccount(
                    uin = item.optString("uin"),
                    nickName = item.optString("nickName"),
                    avatarUrl = item.optString("avatarUrl"),
                ),
            )
        }
    }
}

private fun JSONArray?.toVoiceBindings(): List<ClonedVoiceBinding> {
    val array = this ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            add(
                ClonedVoiceBinding(
                    id = item.optString("id"),
                    providerId = item.optString("providerId"),
                    providerType = runCatching { ProviderType.valueOf(item.optString("providerType")) }.getOrDefault(ProviderType.OPENAI_TTS),
                    model = item.optString("model"),
                    voiceId = item.optString("voiceId"),
                    displayName = item.optString("displayName"),
                    createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                    lastVerifiedAt = item.optLong("lastVerifiedAt", 0L),
                    status = item.optString("status").ifBlank { "ready" },
                ),
            )
        }
    }
}

internal fun JSONArray?.jsonStringList(): List<String> {
    val array = this ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            add(array.optString(index))
        }
    }
}

private fun String.toFeatureSupportState(): FeatureSupportState {
    return runCatching { FeatureSupportState.valueOf(this) }.getOrDefault(FeatureSupportState.UNKNOWN)
}
