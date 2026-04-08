package com.astrbot.android.di

import android.net.Uri
import android.provider.OpenableColumns
import com.astrbot.android.data.BotRepository
import com.astrbot.android.data.ChatCompletionService
import com.astrbot.android.data.ConfigRepository
import com.astrbot.android.data.ConversationRepository
import com.astrbot.android.data.NapCatBridgeRepository
import com.astrbot.android.data.NapCatLoginRepository
import com.astrbot.android.data.NapCatLoginService
import com.astrbot.android.data.PersonaRepository
import com.astrbot.android.data.PluginRepository
import com.astrbot.android.data.ProviderRepository
import com.astrbot.android.data.RuntimeAssetRepository
import com.astrbot.android.data.SherpaOnnxBridge
import com.astrbot.android.data.TtsVoiceAssetRepository
import com.astrbot.android.data.plugin.PluginStoragePaths
import com.astrbot.android.download.AppDownloadManager
import com.astrbot.android.download.DownloadOwnerType
import com.astrbot.android.download.DownloadRequest
import com.astrbot.android.download.DownloadTaskRecord
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.NapCatBridgeConfig
import com.astrbot.android.model.NapCatLoginState
import com.astrbot.android.model.NapCatRuntimeState
import com.astrbot.android.model.PersonaProfile
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.RuntimeAssetState
import com.astrbot.android.model.TtsVoiceReferenceAsset
import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.chat.ConversationSession
import com.astrbot.android.model.plugin.ExternalPluginWorkspacePolicy
import com.astrbot.android.model.plugin.PluginDownloadProgress
import com.astrbot.android.model.plugin.PluginInstallIntent
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginInstallIntentResult
import com.astrbot.android.model.plugin.PluginCatalogEntryRecord
import com.astrbot.android.model.plugin.PluginCatalogSyncState
import com.astrbot.android.model.plugin.PluginCatalogSyncStatus
import com.astrbot.android.model.plugin.PluginConfigStorageBoundary
import com.astrbot.android.model.plugin.PluginConfigStoreSnapshot
import com.astrbot.android.model.plugin.PluginHostWorkspaceSnapshot
import com.astrbot.android.model.plugin.PluginRepositorySource
import com.astrbot.android.model.plugin.PluginStaticConfigSchema
import com.astrbot.android.model.plugin.PluginStaticConfigValue
import com.astrbot.android.model.plugin.PluginUpdateAvailability
import com.astrbot.android.model.plugin.PluginUninstallPolicy
import com.astrbot.android.runtime.ContainerBridgeController
import com.astrbot.android.runtime.ConversationSessionLockManager
import com.astrbot.android.runtime.RuntimeLogRepository
import com.astrbot.android.runtime.plugin.PluginInstaller
import com.astrbot.android.runtime.plugin.PluginPackageValidator
import com.astrbot.android.runtime.plugin.catalog.PluginCatalogSynchronizer
import com.astrbot.android.runtime.plugin.catalog.PluginInstallIntentHandler
import com.astrbot.android.runtime.plugin.catalog.PluginRepositorySubscriptionManager
import com.astrbot.android.runtime.plugin.catalog.UrlConnectionPluginCatalogFetcher
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

interface BridgeViewModelDependencies {
    val config: StateFlow<NapCatBridgeConfig>
    val runtimeState: StateFlow<NapCatRuntimeState>

    fun saveConfig(config: NapCatBridgeConfig)
}

object DefaultBridgeViewModelDependencies : BridgeViewModelDependencies {
    override val config: StateFlow<NapCatBridgeConfig> = NapCatBridgeRepository.config
    override val runtimeState: StateFlow<NapCatRuntimeState> = NapCatBridgeRepository.runtimeState

    override fun saveConfig(config: NapCatBridgeConfig) {
        NapCatBridgeRepository.updateConfig(config)
    }
}

interface BotViewModelDependencies {
    val botProfile: StateFlow<BotProfile>
    val botProfiles: StateFlow<List<BotProfile>>
    val selectedBotId: StateFlow<String>
    val providers: StateFlow<List<ProviderProfile>>
    val personas: StateFlow<List<PersonaProfile>>
    val configProfiles: StateFlow<List<ConfigProfile>>
    val loginState: StateFlow<NapCatLoginState>

    fun select(botId: String)

    fun save(profile: BotProfile)

    fun saveConfig(profile: ConfigProfile)

    fun create()

    fun delete(botId: String)

    fun resolveConfig(profileId: String): ConfigProfile
}

object DefaultBotViewModelDependencies : BotViewModelDependencies {
    override val botProfile: StateFlow<BotProfile> = BotRepository.botProfile
    override val botProfiles: StateFlow<List<BotProfile>> = BotRepository.botProfiles
    override val selectedBotId: StateFlow<String> = BotRepository.selectedBotId
    override val providers: StateFlow<List<ProviderProfile>> = ProviderRepository.providers
    override val personas: StateFlow<List<PersonaProfile>> = PersonaRepository.personas
    override val configProfiles: StateFlow<List<ConfigProfile>> = ConfigRepository.profiles
    override val loginState: StateFlow<NapCatLoginState> = NapCatLoginRepository.loginState

    override fun select(botId: String) {
        BotRepository.select(botId)
    }

    override fun save(profile: BotProfile) {
        BotRepository.save(profile)
    }

    override fun saveConfig(profile: ConfigProfile) {
        ConfigRepository.save(profile)
    }

    override fun create() {
        BotRepository.create()
    }

    override fun delete(botId: String) {
        BotRepository.delete(botId)
    }

    override fun resolveConfig(profileId: String): ConfigProfile {
        return ConfigRepository.resolve(profileId)
    }
}

interface ProviderViewModelDependencies {
    val providers: StateFlow<List<ProviderProfile>>
    val configProfiles: StateFlow<List<ConfigProfile>>
    val selectedConfigProfileId: StateFlow<String>

    fun save(profile: ProviderProfile)

    fun saveConfig(profile: ConfigProfile)

    fun toggleEnabled(id: String)

    fun delete(id: String)

    fun updateMultimodalProbeSupport(id: String, support: com.astrbot.android.model.FeatureSupportState)

    fun updateNativeStreamingProbeSupport(id: String, support: com.astrbot.android.model.FeatureSupportState)

    fun updateSttProbeSupport(id: String, support: com.astrbot.android.model.FeatureSupportState)

    fun updateTtsProbeSupport(id: String, support: com.astrbot.android.model.FeatureSupportState)

    fun fetchModels(provider: ProviderProfile): List<String>

    fun detectMultimodalRule(provider: ProviderProfile): com.astrbot.android.model.FeatureSupportState

    fun probeMultimodalSupport(provider: ProviderProfile): com.astrbot.android.model.FeatureSupportState

    fun detectNativeStreamingRule(provider: ProviderProfile): com.astrbot.android.model.FeatureSupportState

    fun probeNativeStreamingSupport(provider: ProviderProfile): com.astrbot.android.model.FeatureSupportState

    fun probeSttSupport(provider: ProviderProfile): ChatCompletionService.SttProbeResult

    fun probeTtsSupport(provider: ProviderProfile): com.astrbot.android.model.FeatureSupportState

    fun listVoiceChoicesFor(provider: ProviderProfile?): List<Pair<String, String>>

    fun ttsAssetState(context: android.content.Context): com.astrbot.android.data.SherpaOnnxAssetManager.TtsAssetState

    fun isSherpaFrameworkReady(): Boolean

    fun isSherpaSttReady(): Boolean

    fun synthesizeSpeech(
        provider: ProviderProfile,
        text: String,
        voiceId: String,
        readBracketedContent: Boolean,
    ): ConversationAttachment
}

object DefaultProviderViewModelDependencies : ProviderViewModelDependencies {
    override val providers: StateFlow<List<ProviderProfile>> = ProviderRepository.providers
    override val configProfiles: StateFlow<List<ConfigProfile>> = ConfigRepository.profiles
    override val selectedConfigProfileId: StateFlow<String> = ConfigRepository.selectedProfileId

    override fun save(profile: ProviderProfile) {
        ProviderRepository.save(
            id = profile.id,
            name = profile.name,
            baseUrl = profile.baseUrl,
            model = profile.model,
            providerType = profile.providerType,
            apiKey = profile.apiKey,
            capabilities = profile.capabilities,
            enabled = profile.enabled,
            multimodalRuleSupport = profile.multimodalRuleSupport,
            multimodalProbeSupport = profile.multimodalProbeSupport,
            nativeStreamingRuleSupport = profile.nativeStreamingRuleSupport,
            nativeStreamingProbeSupport = profile.nativeStreamingProbeSupport,
            sttProbeSupport = profile.sttProbeSupport,
            ttsProbeSupport = profile.ttsProbeSupport,
            ttsVoiceOptions = profile.ttsVoiceOptions,
        )
    }

    override fun saveConfig(profile: ConfigProfile) {
        ConfigRepository.save(profile)
    }

    override fun toggleEnabled(id: String) {
        ProviderRepository.toggleEnabled(id)
    }

    override fun delete(id: String) {
        ProviderRepository.delete(id)
    }

    override fun updateMultimodalProbeSupport(id: String, support: com.astrbot.android.model.FeatureSupportState) {
        ProviderRepository.updateMultimodalProbeSupport(id, support)
    }

    override fun updateNativeStreamingProbeSupport(id: String, support: com.astrbot.android.model.FeatureSupportState) {
        ProviderRepository.updateNativeStreamingProbeSupport(id, support)
    }

    override fun updateSttProbeSupport(id: String, support: com.astrbot.android.model.FeatureSupportState) {
        ProviderRepository.updateSttProbeSupport(id, support)
    }

    override fun updateTtsProbeSupport(id: String, support: com.astrbot.android.model.FeatureSupportState) {
        ProviderRepository.updateTtsProbeSupport(id, support)
    }

    override fun fetchModels(provider: ProviderProfile): List<String> {
        return ChatCompletionService.fetchModels(
            baseUrl = provider.baseUrl,
            apiKey = provider.apiKey,
            providerType = provider.providerType,
        )
    }

    override fun detectMultimodalRule(provider: ProviderProfile): com.astrbot.android.model.FeatureSupportState {
        return ChatCompletionService.detectMultimodalRule(provider)
    }

    override fun probeMultimodalSupport(provider: ProviderProfile): com.astrbot.android.model.FeatureSupportState {
        return ChatCompletionService.probeMultimodalSupport(provider)
    }

    override fun detectNativeStreamingRule(provider: ProviderProfile): com.astrbot.android.model.FeatureSupportState {
        return ChatCompletionService.detectNativeStreamingRule(provider)
    }

    override fun probeNativeStreamingSupport(provider: ProviderProfile): com.astrbot.android.model.FeatureSupportState {
        return ChatCompletionService.probeNativeStreamingSupport(provider)
    }

    override fun probeSttSupport(provider: ProviderProfile): ChatCompletionService.SttProbeResult {
        return ChatCompletionService.probeSttSupport(provider)
    }

    override fun probeTtsSupport(provider: ProviderProfile): com.astrbot.android.model.FeatureSupportState {
        return ChatCompletionService.probeTtsSupport(provider)
    }

    override fun listVoiceChoicesFor(provider: ProviderProfile?): List<Pair<String, String>> {
        return TtsVoiceAssetRepository.listVoiceChoicesFor(provider)
    }

    override fun ttsAssetState(context: android.content.Context): com.astrbot.android.data.SherpaOnnxAssetManager.TtsAssetState {
        return RuntimeAssetRepository.ttsAssetState(context)
    }

    override fun isSherpaFrameworkReady(): Boolean {
        return SherpaOnnxBridge.isFrameworkReady()
    }

    override fun isSherpaSttReady(): Boolean {
        return SherpaOnnxBridge.isSttReady()
    }

    override fun synthesizeSpeech(
        provider: ProviderProfile,
        text: String,
        voiceId: String,
        readBracketedContent: Boolean,
    ): ConversationAttachment {
        return ChatCompletionService.synthesizeSpeech(provider, text, voiceId, readBracketedContent)
    }
}

interface ConfigViewModelDependencies {
    val configProfiles: StateFlow<List<ConfigProfile>>
    val selectedConfigProfileId: StateFlow<String>
    val providers: StateFlow<List<ProviderProfile>>
    val bots: StateFlow<List<BotProfile>>
    val ttsVoiceAssets: StateFlow<List<TtsVoiceReferenceAsset>>

    fun select(profileId: String)

    fun save(profile: ConfigProfile)

    fun create(): ConfigProfile

    fun delete(profileId: String): String

    fun replaceConfigBinding(deletedConfigId: String, fallbackConfigId: String)

    fun resolve(profileId: String): ConfigProfile
}

object DefaultConfigViewModelDependencies : ConfigViewModelDependencies {
    override val configProfiles: StateFlow<List<ConfigProfile>> = ConfigRepository.profiles
    override val selectedConfigProfileId: StateFlow<String> = ConfigRepository.selectedProfileId
    override val providers: StateFlow<List<ProviderProfile>> = ProviderRepository.providers
    override val bots: StateFlow<List<BotProfile>> = BotRepository.botProfiles
    override val ttsVoiceAssets: StateFlow<List<TtsVoiceReferenceAsset>> = TtsVoiceAssetRepository.assets

    override fun select(profileId: String) {
        ConfigRepository.select(profileId)
    }

    override fun save(profile: ConfigProfile) {
        ConfigRepository.save(profile)
    }

    override fun create(): ConfigProfile {
        return ConfigRepository.create()
    }

    override fun delete(profileId: String): String {
        return ConfigRepository.delete(profileId)
    }

    override fun replaceConfigBinding(deletedConfigId: String, fallbackConfigId: String) {
        BotRepository.replaceConfigBinding(deletedConfigId, fallbackConfigId)
    }

    override fun resolve(profileId: String): ConfigProfile {
        return ConfigRepository.resolve(profileId)
    }
}

interface ConversationViewModelDependencies {
    val defaultSessionId: String
    val sessions: StateFlow<List<ConversationSession>>

    fun contextPreview(sessionId: String): String

    fun session(sessionId: String): ConversationSession

    fun appendMessage(sessionId: String, role: String, content: String)

    fun replaceMessages(sessionId: String, messages: List<ConversationMessage>)
}

object DefaultConversationViewModelDependencies : ConversationViewModelDependencies {
    override val defaultSessionId: String = ConversationRepository.DEFAULT_SESSION_ID
    override val sessions: StateFlow<List<ConversationSession>> = ConversationRepository.sessions

    override fun contextPreview(sessionId: String): String {
        return ConversationRepository.buildContextPreview(sessionId)
    }

    override fun session(sessionId: String): ConversationSession {
        return ConversationRepository.session(sessionId)
    }

    override fun appendMessage(sessionId: String, role: String, content: String) {
        ConversationRepository.appendMessage(sessionId, role, content)
    }

    override fun replaceMessages(sessionId: String, messages: List<ConversationMessage>) {
        ConversationRepository.replaceMessages(sessionId, messages)
    }
}

interface PersonaViewModelDependencies {
    val personas: StateFlow<List<PersonaProfile>>

    fun add(
        name: String,
        tag: String,
        systemPrompt: String,
        enabledTools: Set<String>,
        defaultProviderId: String,
        maxContextMessages: Int,
    )

    fun update(profile: PersonaProfile)

    fun toggleEnabled(id: String)

    fun delete(id: String)
}

object DefaultPersonaViewModelDependencies : PersonaViewModelDependencies {
    override val personas: StateFlow<List<PersonaProfile>> = PersonaRepository.personas

    override fun add(
        name: String,
        tag: String,
        systemPrompt: String,
        enabledTools: Set<String>,
        defaultProviderId: String,
        maxContextMessages: Int,
    ) {
        PersonaRepository.add(name, tag, systemPrompt, enabledTools, defaultProviderId, maxContextMessages)
    }

    override fun update(profile: PersonaProfile) {
        PersonaRepository.update(profile)
    }

    override fun toggleEnabled(id: String) {
        PersonaRepository.toggleEnabled(id)
    }

    override fun delete(id: String) {
        PersonaRepository.delete(id)
    }
}

interface PluginViewModelDependencies {
    val records: StateFlow<List<PluginInstallRecord>>
    val repositorySources: StateFlow<List<PluginRepositorySource>>
    val catalogEntries: StateFlow<List<PluginCatalogEntryRecord>>

    suspend fun handleInstallIntent(
        intent: PluginInstallIntent,
        onDownloadProgress: (PluginDownloadProgress) -> Unit = {},
    ): PluginInstallIntentResult

    suspend fun installFromLocalPackageUri(uri: String): PluginInstallIntentResult

    suspend fun ensureOfficialMarketCatalogSubscribed(): PluginCatalogSyncState

    suspend fun refreshMarketCatalog(): List<PluginCatalogSyncState>

    fun getHostVersion(): String = "0.0.0"

    fun getUpdateAvailability(pluginId: String): PluginUpdateAvailability?

    suspend fun upgradePlugin(update: PluginUpdateAvailability): PluginInstallRecord

    fun getPluginStaticConfigSchema(pluginId: String): PluginStaticConfigSchema?

    fun resolvePluginConfigSnapshot(
        pluginId: String,
        boundary: PluginConfigStorageBoundary,
    ): PluginConfigStoreSnapshot

    fun savePluginCoreConfig(
        pluginId: String,
        boundary: PluginConfigStorageBoundary,
        coreValues: Map<String, PluginStaticConfigValue>,
    ): PluginConfigStoreSnapshot

    fun savePluginExtensionConfig(
        pluginId: String,
        boundary: PluginConfigStorageBoundary,
        extensionValues: Map<String, PluginStaticConfigValue>,
    ): PluginConfigStoreSnapshot

    fun resolvePluginWorkspaceSnapshot(pluginId: String): PluginHostWorkspaceSnapshot

    suspend fun importPluginWorkspaceFile(
        pluginId: String,
        uri: String,
    ): PluginHostWorkspaceSnapshot

    fun deletePluginWorkspaceFile(
        pluginId: String,
        relativePath: String,
    ): PluginHostWorkspaceSnapshot

    fun clearPluginFailureState(pluginId: String): PluginInstallRecord

    fun setPluginEnabled(pluginId: String, enabled: Boolean): PluginInstallRecord

    fun uninstallPlugin(pluginId: String, policy: PluginUninstallPolicy): com.astrbot.android.data.PluginUninstallResult
}

object DefaultPluginViewModelDependencies : PluginViewModelDependencies {
    override val records: StateFlow<List<PluginInstallRecord>> = PluginRepository.records
    override val repositorySources: StateFlow<List<PluginRepositorySource>> = PluginRepository.repositorySources
    override val catalogEntries: StateFlow<List<PluginCatalogEntryRecord>> = PluginRepository.catalogEntries

    override suspend fun handleInstallIntent(
        intent: PluginInstallIntent,
        onDownloadProgress: (PluginDownloadProgress) -> Unit,
    ): PluginInstallIntentResult {
        return withContext(Dispatchers.IO) {
            defaultPluginInstallIntentHandler().handle(
                intent = intent,
                onDownloadProgress = onDownloadProgress,
            )
        }
    }

    override suspend fun installFromLocalPackageUri(uri: String): PluginInstallIntentResult {
        val appContext = PluginRepository.requireAppContext()
        return withContext(Dispatchers.IO) {
            val contentUri = Uri.parse(uri)
            val importDir = File(appContext.cacheDir, "plugin-import").apply { mkdirs() }
            val tempFile = File.createTempFile("plugin-import-", ".zip", importDir)
            try {
                appContext.contentResolver.openInputStream(contentUri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: error("Unable to open selected file.")

                val record = defaultPluginInstaller().installFromLocalPackage(tempFile)
                PluginInstallIntentResult.Installed(record = record)
            } finally {
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            }
        }
    }

    override suspend fun ensureOfficialMarketCatalogSubscribed(): PluginCatalogSyncState {
        return withContext(Dispatchers.IO) {
            val existing = repositorySources.value.firstOrNull { source ->
                source.catalogUrl == OFFICIAL_MARKET_CATALOG_URL
            }
            RuntimeLogRepository.append(
                "Plugin market ensure-official start: " +
                    "existing=${existing != null} " +
                    "sourceCount=${repositorySources.value.size} " +
                    "url=$OFFICIAL_MARKET_CATALOG_URL",
            )
            val syncState = if (existing != null) {
                defaultPluginCatalogSynchronizer().sync(existing.sourceId)
            } else {
                defaultPluginRepositorySubscriptionManager()
                    .subscribeAndSync(OFFICIAL_MARKET_CATALOG_URL)
                    .syncState
            }
            RuntimeLogRepository.append(
                "Plugin market ensure-official finished: " +
                    "sourceId=${syncState.sourceId} " +
                    "status=${syncState.lastSyncStatus.name}",
            )
            syncState
        }
    }

    override suspend fun refreshMarketCatalog(): List<PluginCatalogSyncState> {
        return withContext(Dispatchers.IO) {
            val existingSources = repositorySources.value
            RuntimeLogRepository.append(
                "Plugin market refresh flow start: sourceCount=${existingSources.size}",
            )
            val states = if (existingSources.isEmpty()) {
                listOf(ensureOfficialMarketCatalogSubscribed())
            } else {
                val synchronizer = defaultPluginCatalogSynchronizer()
                existingSources.map { source ->
                    synchronizer.sync(source.sourceId)
                }
            }
            RuntimeLogRepository.append(
                "Plugin market refresh flow finished: " +
                    "resultCount=${states.size} " +
                    "statuses=${states.joinToString(separator = ",") { "${it.sourceId}:${it.lastSyncStatus.name}" }}",
            )
            if (states.any { it.lastSyncStatus == PluginCatalogSyncStatus.FAILED }) {
                RuntimeLogRepository.append("Plugin market refresh flow failed: at least one source sync failed")
                error("Market catalog refresh failed.")
            }
            states
        }
    }

    override fun getUpdateAvailability(pluginId: String): PluginUpdateAvailability? {
        val hostVersion = getHostVersion()
        return PluginRepository.getUpdateAvailability(
            pluginId = pluginId,
            hostVersion = hostVersion,
            supportedProtocolVersion = 1,
        )
    }

    override fun getHostVersion(): String {
        val appContext = PluginRepository.requireAppContext()
        return runCatching {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
        }.getOrNull().orEmpty().ifBlank { "0.0.0" }
    }

    override suspend fun upgradePlugin(update: PluginUpdateAvailability): PluginInstallRecord {
        return withContext(Dispatchers.IO) {
            defaultPluginInstaller().upgrade(update)
        }
    }

    override fun getPluginStaticConfigSchema(pluginId: String): PluginStaticConfigSchema? {
        return PluginRepository.getInstalledStaticConfigSchema(pluginId)
    }

    override fun resolvePluginConfigSnapshot(
        pluginId: String,
        boundary: PluginConfigStorageBoundary,
    ): PluginConfigStoreSnapshot {
        return PluginRepository.resolveConfigSnapshot(
            pluginId = pluginId,
            boundary = boundary,
        )
    }

    override fun savePluginCoreConfig(
        pluginId: String,
        boundary: PluginConfigStorageBoundary,
        coreValues: Map<String, PluginStaticConfigValue>,
    ): PluginConfigStoreSnapshot {
        return PluginRepository.saveCoreConfig(
            pluginId = pluginId,
            boundary = boundary,
            coreValues = coreValues,
        )
    }

    override fun savePluginExtensionConfig(
        pluginId: String,
        boundary: PluginConfigStorageBoundary,
        extensionValues: Map<String, PluginStaticConfigValue>,
    ): PluginConfigStoreSnapshot {
        return PluginRepository.saveExtensionConfig(
            pluginId = pluginId,
            boundary = boundary,
            extensionValues = extensionValues,
        )
    }

    override fun resolvePluginWorkspaceSnapshot(pluginId: String): PluginHostWorkspaceSnapshot {
        val appContext = PluginRepository.requireAppContext()
        return ExternalPluginWorkspacePolicy.snapshot(
            storagePaths = PluginStoragePaths.fromFilesDir(appContext.filesDir),
            pluginId = pluginId,
        )
    }

    override suspend fun importPluginWorkspaceFile(
        pluginId: String,
        uri: String,
    ): PluginHostWorkspaceSnapshot {
        val appContext = PluginRepository.requireAppContext()
        return withContext(Dispatchers.IO) {
            val storagePaths = PluginStoragePaths.fromFilesDir(appContext.filesDir)
            val paths = ExternalPluginWorkspacePolicy.ensureWorkspace(
                storagePaths = storagePaths,
                pluginId = pluginId,
            )
            val contentUri = Uri.parse(uri)
            val targetFile = ExternalPluginWorkspacePolicy.buildImportTarget(
                importsDirPath = paths.importsDir.absolutePath,
                displayName = queryDisplayName(appContext, contentUri)
                    ?: contentUri.lastPathSegment
                    ?: "imported-file",
            )
            targetFile.parentFile?.mkdirs()
            appContext.contentResolver.openInputStream(contentUri)?.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: error("Unable to open selected workspace file.")
            ExternalPluginWorkspacePolicy.snapshot(
                storagePaths = storagePaths,
                pluginId = pluginId,
            )
        }
    }

    override fun deletePluginWorkspaceFile(
        pluginId: String,
        relativePath: String,
    ): PluginHostWorkspaceSnapshot {
        val appContext = PluginRepository.requireAppContext()
        val storagePaths = PluginStoragePaths.fromFilesDir(appContext.filesDir)
        val snapshot = ExternalPluginWorkspacePolicy.snapshot(
            storagePaths = storagePaths,
            pluginId = pluginId,
        )
        val targetFile = ExternalPluginWorkspacePolicy.resolveWorkspaceFile(
            privateRootPath = snapshot.privateRootPath,
            relativePath = relativePath,
        )
        if (targetFile.exists()) {
            targetFile.deleteRecursively()
        }
        return ExternalPluginWorkspacePolicy.snapshot(
            storagePaths = storagePaths,
            pluginId = pluginId,
        )
    }

    override fun clearPluginFailureState(pluginId: String): PluginInstallRecord {
        return PluginRepository.clearFailureState(pluginId)
    }

    override fun setPluginEnabled(pluginId: String, enabled: Boolean): PluginInstallRecord {
        return PluginRepository.setEnabled(pluginId, enabled)
    }

    override fun uninstallPlugin(
        pluginId: String,
        policy: PluginUninstallPolicy,
    ): com.astrbot.android.data.PluginUninstallResult {
        return PluginRepository.uninstall(pluginId, policy)
    }
}

private const val OFFICIAL_MARKET_CATALOG_URL =
    "https://raw.githubusercontent.com/undertaker33/astrbot-android-plugin-market/main/catalog.json"

private fun queryDisplayName(
    appContext: android.content.Context,
    uri: Uri,
): String? {
    return appContext.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (columnIndex < 0) return@use null
        cursor.getString(columnIndex)
    }
}

interface QQLoginViewModelDependencies {
    val loginState: StateFlow<NapCatLoginState>

    suspend fun refresh(manual: Boolean = false)

    suspend fun refreshQrCode()

    suspend fun quickLoginSavedAccount(uin: String? = null)

    suspend fun saveQuickLoginAccount(uin: String)

    suspend fun logoutCurrentAccount()

    suspend fun passwordLogin(uin: String, password: String)

    suspend fun captchaLogin(uin: String, password: String, ticket: String, randstr: String, sid: String)

    suspend fun newDeviceLogin(uin: String, password: String, verifiedToken: String?)

    suspend fun getNewDeviceQRCode(): NapCatLoginService.NewDeviceQrCodeResult

    suspend fun pollNewDeviceQRCode(bytesToken: String): NapCatLoginService.NewDeviceQrPollResult

    fun log(message: String)
}

object DefaultQQLoginViewModelDependencies : QQLoginViewModelDependencies {
    override val loginState: StateFlow<NapCatLoginState> = NapCatLoginRepository.loginState

    override suspend fun refresh(manual: Boolean) {
        NapCatLoginRepository.refresh(manual)
    }

    override suspend fun refreshQrCode() {
        NapCatLoginRepository.refreshQrCode()
    }

    override suspend fun quickLoginSavedAccount(uin: String?) {
        NapCatLoginRepository.quickLoginSavedAccount(uin)
    }

    override suspend fun saveQuickLoginAccount(uin: String) {
        NapCatLoginRepository.saveQuickLoginAccount(uin)
    }

    override suspend fun logoutCurrentAccount() {
        NapCatLoginRepository.logoutCurrentAccount()
    }

    override suspend fun passwordLogin(uin: String, password: String) {
        NapCatLoginRepository.passwordLogin(uin, password)
    }

    override suspend fun captchaLogin(uin: String, password: String, ticket: String, randstr: String, sid: String) {
        NapCatLoginRepository.captchaLogin(uin, password, ticket, randstr, sid)
    }

    override suspend fun newDeviceLogin(uin: String, password: String, verifiedToken: String?) {
        NapCatLoginRepository.newDeviceLogin(uin, password, verifiedToken)
    }

    override suspend fun getNewDeviceQRCode(): NapCatLoginService.NewDeviceQrCodeResult {
        return NapCatLoginRepository.getNewDeviceQRCode()
    }

    override suspend fun pollNewDeviceQRCode(bytesToken: String): NapCatLoginService.NewDeviceQrPollResult {
        return NapCatLoginRepository.pollNewDeviceQRCode(bytesToken)
    }

    override fun log(message: String) {
        RuntimeLogRepository.append(message)
    }
}

interface RuntimeAssetViewModelDependencies {
    val state: StateFlow<RuntimeAssetState>

    fun refresh()

    suspend fun downloadAsset(assetId: String)

    suspend fun clearAsset(assetId: String)

    suspend fun downloadOnDeviceTtsModel(modelId: String)

    suspend fun clearOnDeviceTtsModel(modelId: String)
}

class DefaultRuntimeAssetViewModelDependencies(
    private val appContext: android.content.Context,
) : RuntimeAssetViewModelDependencies {
    override val state: StateFlow<RuntimeAssetState> = RuntimeAssetRepository.state

    override fun refresh() {
        RuntimeAssetRepository.refresh(appContext)
    }

    override suspend fun downloadAsset(assetId: String) {
        RuntimeAssetRepository.downloadAsset(appContext, assetId)
    }

    override suspend fun clearAsset(assetId: String) {
        RuntimeAssetRepository.clearAsset(appContext, assetId)
    }

    override suspend fun downloadOnDeviceTtsModel(modelId: String) {
        RuntimeAssetRepository.downloadOnDeviceTtsModel(appContext, modelId)
    }

    override suspend fun clearOnDeviceTtsModel(modelId: String) {
        RuntimeAssetRepository.clearOnDeviceTtsModel(appContext, modelId)
    }
}

interface ChatViewModelDependencies {
    val defaultSessionId: String
    val defaultSessionTitle: String
    val bots: StateFlow<List<BotProfile>>
    val selectedBotId: StateFlow<String>
    val providers: StateFlow<List<ProviderProfile>>
    val configProfiles: StateFlow<List<ConfigProfile>>
    val sessions: StateFlow<List<ConversationSession>>
    val personas: StateFlow<List<PersonaProfile>>

    fun session(sessionId: String): ConversationSession

    fun createSession(botId: String): ConversationSession

    fun deleteSession(sessionId: String)

    fun renameSession(sessionId: String, title: String)

    fun toggleSessionPinned(sessionId: String)

    fun updateSessionServiceFlags(sessionId: String, sessionSttEnabled: Boolean? = null, sessionTtsEnabled: Boolean? = null)

    fun updateSessionBindings(sessionId: String, providerId: String, personaId: String, botId: String)

    fun appendMessage(
        sessionId: String,
        role: String,
        content: String,
        attachments: List<ConversationAttachment> = emptyList(),
    ): String

    fun replaceMessages(sessionId: String, messages: List<ConversationMessage>)

    fun updateMessage(
        sessionId: String,
        messageId: String,
        content: String? = null,
        attachments: List<ConversationAttachment>? = null,
    )

    fun syncSystemSessionTitle(sessionId: String, title: String)

    fun resolveConfig(profileId: String): ConfigProfile

    fun saveConfig(profile: ConfigProfile)

    fun saveBot(profile: BotProfile)

    fun saveProvider(profile: ProviderProfile)

    suspend fun transcribeAudio(provider: ProviderProfile, attachment: ConversationAttachment): String

    suspend fun sendConfiguredChat(
        provider: ProviderProfile,
        messages: List<ConversationMessage>,
        systemPrompt: String?,
        config: ConfigProfile?,
        availableProviders: List<ProviderProfile>,
    ): String

    suspend fun sendConfiguredChatStream(
        provider: ProviderProfile,
        messages: List<ConversationMessage>,
        systemPrompt: String?,
        config: ConfigProfile,
        availableProviders: List<ProviderProfile>,
        onDelta: suspend (String) -> Unit,
    ): String

    suspend fun synthesizeSpeech(
        provider: ProviderProfile,
        text: String,
        voiceId: String,
        readBracketedContent: Boolean,
    ): ConversationAttachment

    suspend fun <T> withSessionLock(sessionId: String, block: suspend () -> T): T

    fun log(message: String)
}

object DefaultChatViewModelDependencies : ChatViewModelDependencies {
    override val defaultSessionId: String = ConversationRepository.DEFAULT_SESSION_ID
    override val defaultSessionTitle: String = ConversationRepository.DEFAULT_SESSION_TITLE
    override val bots: StateFlow<List<BotProfile>> = BotRepository.botProfiles
    override val selectedBotId: StateFlow<String> = BotRepository.selectedBotId
    override val providers: StateFlow<List<ProviderProfile>> = ProviderRepository.providers
    override val configProfiles: StateFlow<List<ConfigProfile>> = ConfigRepository.profiles
    override val sessions: StateFlow<List<ConversationSession>> = ConversationRepository.sessions
    override val personas: StateFlow<List<PersonaProfile>> = PersonaRepository.personas

    override fun session(sessionId: String): ConversationSession {
        return ConversationRepository.session(sessionId)
    }

    override fun createSession(botId: String): ConversationSession {
        return ConversationRepository.createSession(botId = botId)
    }

    override fun deleteSession(sessionId: String) {
        ConversationRepository.deleteSession(sessionId)
    }

    override fun renameSession(sessionId: String, title: String) {
        ConversationRepository.renameSession(sessionId, title)
    }

    override fun toggleSessionPinned(sessionId: String) {
        ConversationRepository.toggleSessionPinned(sessionId)
    }

    override fun updateSessionServiceFlags(sessionId: String, sessionSttEnabled: Boolean?, sessionTtsEnabled: Boolean?) {
        ConversationRepository.updateSessionServiceFlags(sessionId, sessionSttEnabled, sessionTtsEnabled)
    }

    override fun updateSessionBindings(sessionId: String, providerId: String, personaId: String, botId: String) {
        ConversationRepository.updateSessionBindings(sessionId, providerId, personaId, botId)
    }

    override fun appendMessage(
        sessionId: String,
        role: String,
        content: String,
        attachments: List<ConversationAttachment>,
    ): String {
        return ConversationRepository.appendMessage(sessionId, role, content, attachments)
    }

    override fun replaceMessages(sessionId: String, messages: List<ConversationMessage>) {
        ConversationRepository.replaceMessages(sessionId, messages)
    }

    override fun updateMessage(
        sessionId: String,
        messageId: String,
        content: String?,
        attachments: List<ConversationAttachment>?,
    ) {
        ConversationRepository.updateMessage(sessionId, messageId, content, attachments)
    }

    override fun syncSystemSessionTitle(sessionId: String, title: String) {
        ConversationRepository.syncSystemSessionTitle(sessionId, title)
    }

    override fun resolveConfig(profileId: String): ConfigProfile {
        return ConfigRepository.resolve(profileId)
    }

    override fun saveConfig(profile: ConfigProfile) {
        ConfigRepository.save(profile)
    }

    override fun saveBot(profile: BotProfile) {
        BotRepository.save(profile)
    }

    override fun saveProvider(profile: ProviderProfile) {
        ProviderRepository.save(
            id = profile.id,
            name = profile.name,
            baseUrl = profile.baseUrl,
            model = profile.model,
            providerType = profile.providerType,
            apiKey = profile.apiKey,
            capabilities = profile.capabilities,
            enabled = profile.enabled,
            multimodalRuleSupport = profile.multimodalRuleSupport,
            multimodalProbeSupport = profile.multimodalProbeSupport,
            nativeStreamingRuleSupport = profile.nativeStreamingRuleSupport,
            nativeStreamingProbeSupport = profile.nativeStreamingProbeSupport,
            sttProbeSupport = profile.sttProbeSupport,
            ttsProbeSupport = profile.ttsProbeSupport,
            ttsVoiceOptions = profile.ttsVoiceOptions,
        )
    }

    override suspend fun transcribeAudio(provider: ProviderProfile, attachment: ConversationAttachment): String {
        return ChatCompletionService.transcribeAudio(provider, attachment)
    }

    override suspend fun sendConfiguredChat(
        provider: ProviderProfile,
        messages: List<ConversationMessage>,
        systemPrompt: String?,
        config: ConfigProfile?,
        availableProviders: List<ProviderProfile>,
    ): String {
        return ChatCompletionService.sendConfiguredChat(provider, messages, systemPrompt, config, availableProviders)
    }

    override suspend fun sendConfiguredChatStream(
        provider: ProviderProfile,
        messages: List<ConversationMessage>,
        systemPrompt: String?,
        config: ConfigProfile,
        availableProviders: List<ProviderProfile>,
        onDelta: suspend (String) -> Unit,
    ): String {
        return ChatCompletionService.sendConfiguredChatStream(
            provider = provider,
            messages = messages,
            systemPrompt = systemPrompt,
            config = config,
            availableProviders = availableProviders,
            onDelta = onDelta,
        )
    }

    override suspend fun synthesizeSpeech(
        provider: ProviderProfile,
        text: String,
        voiceId: String,
        readBracketedContent: Boolean,
    ): ConversationAttachment {
        return ChatCompletionService.synthesizeSpeech(provider, text, voiceId, readBracketedContent)
    }

    override suspend fun <T> withSessionLock(sessionId: String, block: suspend () -> T): T {
        return ConversationSessionLockManager.withLock(sessionId, block)
    }

    override fun log(message: String) {
        RuntimeLogRepository.append(message)
    }
}

interface MainActivityDependencies {
    val autoStartEnabled: Boolean
    val runtimeState: NapCatRuntimeState

    suspend fun handlePluginInstallIntent(intent: PluginInstallIntent)

    fun log(message: String)

    fun startBridge(context: android.content.Context)
}

object DefaultMainActivityDependencies : MainActivityDependencies {
    override val autoStartEnabled: Boolean
        get() = NapCatBridgeRepository.config.value.autoStart
    override val runtimeState: NapCatRuntimeState
        get() = NapCatBridgeRepository.runtimeState.value

    override suspend fun handlePluginInstallIntent(intent: PluginInstallIntent) {
        withContext(Dispatchers.IO) {
            defaultPluginInstallIntentHandler().handle(intent)
        }
    }

    override fun log(message: String) {
        RuntimeLogRepository.append(message)
    }

    override fun startBridge(context: android.content.Context) {
        ContainerBridgeController.start(context)
    }
}

private fun defaultPluginInstallIntentHandler(): PluginInstallIntentHandler {
    return PluginInstallIntentHandler(
        installer = defaultPluginInstaller(),
        repositorySubscriptionManager = defaultPluginRepositorySubscriptionManager(),
    )
}

private fun defaultPluginCatalogSynchronizer(): PluginCatalogSynchronizer {
    return PluginCatalogSynchronizer(
        store = PluginRepository,
        fetcher = UrlConnectionPluginCatalogFetcher(),
    )
}

private fun defaultPluginRepositorySubscriptionManager(): PluginRepositorySubscriptionManager {
    return PluginRepositorySubscriptionManager(
        store = PluginRepository,
        synchronizer = defaultPluginCatalogSynchronizer(),
    )
}

private fun defaultPluginInstaller(): PluginInstaller {
    val appContext = PluginRepository.requireAppContext()
    val hostVersion = runCatching {
        appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
    }.getOrNull().orEmpty().ifBlank { "0.0.0" }
    return PluginInstaller(
        validator = PluginPackageValidator(
            hostVersion = hostVersion,
            supportedProtocolVersion = 1,
        ),
        storagePaths = PluginStoragePaths.fromFilesDir(appContext.filesDir),
        installStore = PluginRepository,
        remotePackageDownloader = com.astrbot.android.runtime.plugin.RemotePluginPackageDownloader { packageUrl, destinationFile, onProgress ->
            AppDownloadManager.initialize(appContext)
            val taskKey = "plugin:${packageUrl.sha256Hex()}"
            AppDownloadManager.enqueue(
                DownloadRequest(
                    taskKey = taskKey,
                    url = packageUrl,
                    targetFilePath = destinationFile.absolutePath,
                    displayName = destinationFile.name,
                    ownerType = DownloadOwnerType.PLUGIN_PACKAGE,
                    ownerId = destinationFile.nameWithoutExtension,
                ),
            )
            AppDownloadManager.awaitCompletion(taskKey) { task ->
                task.toPluginDownloadProgress()?.let(onProgress)
            }
        },
    )
}

private fun DownloadTaskRecord.toPluginDownloadProgress(): PluginDownloadProgress? {
    return when (status) {
        com.astrbot.android.download.DownloadTaskStatus.QUEUED,
        com.astrbot.android.download.DownloadTaskStatus.RUNNING,
        com.astrbot.android.download.DownloadTaskStatus.PAUSED,
        -> PluginDownloadProgress.downloading(
            bytesDownloaded = downloadedBytes,
            totalBytes = totalBytes ?: -1L,
            bytesPerSecond = bytesPerSecond,
        )
        else -> null
    }
}

private fun String.sha256Hex(): String {
    return MessageDigest.getInstance("SHA-256")
        .digest(toByteArray())
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
}
