package com.elymbot.android.core.db.backup

import android.content.Context
import android.net.Uri
import android.graphics.BitmapFactory
import com.elymbot.android.core.backup.BackupParticipantRegistry
import com.elymbot.android.core.db.backup.AppBackupAppState
import com.elymbot.android.core.db.backup.AppBackupJson
import com.elymbot.android.core.db.backup.AppBackupConflictPreview
import com.elymbot.android.core.db.backup.AppBackupImportSource
import com.elymbot.android.core.db.backup.AppBackupImportPlan
import com.elymbot.android.core.db.backup.AppBackupImportMode
import com.elymbot.android.core.db.backup.AppBackupImportPlanner
import com.elymbot.android.core.db.backup.AppBackupItem
import com.elymbot.android.core.db.backup.AppBackupManifest
import com.elymbot.android.core.db.backup.AppBackupModuleConflict
import com.elymbot.android.core.db.backup.AppBackupModuleKind
import com.elymbot.android.core.db.backup.AppBackupModuleSnapshot
import com.elymbot.android.core.db.backup.AppBackupModules
import com.elymbot.android.core.db.backup.AppBackupRestoreResult
import com.elymbot.android.core.db.backup.AppBackupSnapshot
import com.elymbot.android.core.db.backup.AppBackupZipEntrySource
import com.elymbot.android.core.db.backup.BackupPayload
import com.elymbot.android.core.db.backup.ModuleBackupImportSource
import com.elymbot.android.core.db.backup.ModuleBackupItem
import com.elymbot.android.core.db.backup.buildTtsClipBackupPayload
import com.elymbot.android.core.db.backup.moduleCountFromRestoreResult
import com.elymbot.android.core.db.backup.moduleOnlyManifest
import com.elymbot.android.core.db.backup.moduleSnapshot
import com.elymbot.android.core.db.backup.readAppBackupZip
import com.elymbot.android.core.db.backup.readAppBackupPayloadFile
import com.elymbot.android.core.db.backup.moduleConflictFor
import com.elymbot.android.core.db.backup.moduleOnlyImportPlan
import com.elymbot.android.core.db.backup.ttsClipArchivePath
import com.elymbot.android.core.db.backup.toBotProfile
import com.elymbot.android.core.db.backup.toConfigProfile
import com.elymbot.android.core.db.backup.toJson
import com.elymbot.android.core.db.backup.toPersonaProfile
import com.elymbot.android.core.db.backup.toProviderProfile
import com.elymbot.android.core.db.backup.toSavedAccounts
import com.elymbot.android.core.db.backup.toTtsAsset
import com.elymbot.android.core.db.backup.writeAppBackupZip
import com.elymbot.android.feature.settings.api.backup.AppBackupDataPort
import com.elymbot.android.model.BotProfile
import com.elymbot.android.model.ConfigProfile
import com.elymbot.android.model.PersonaProfile
import com.elymbot.android.model.ProviderProfile
import com.elymbot.android.model.SavedQqAccount
import com.elymbot.android.feature.voiceasset.api.model.TtsVoiceReferenceAsset
import com.elymbot.android.feature.voiceasset.api.TtsVoiceAssetPort
import com.elymbot.android.model.chat.ConversationSession
import com.elymbot.android.core.common.logging.RuntimeLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.security.MessageDigest

data class AppBackupCreateOptions(
    val includeProviderApiKeys: Boolean = false,
)

@Singleton
class AppBackupService @Inject constructor(
    @ApplicationContext context: Context,
    dataPort: AppBackupDataPort,
    ttsVoiceAssetPort: TtsVoiceAssetPort,
    private val participantRegistry: BackupParticipantRegistry,
    runtimeLogger: RuntimeLogger,
) {
    private val repository = AppBackupRepository(
        context = context,
        dataPort = dataPort,
        ttsVoiceAssetPort = ttsVoiceAssetPort,
        runtimeLogger = runtimeLogger,
    )

    val backups: StateFlow<List<AppBackupItem>> = repository.backups

    fun backupsForModule(module: AppBackupModuleKind): StateFlow<List<ModuleBackupItem>> {
        return repository.backupsForModule(module)
    }

    fun participantKeys(): List<String> {
        return participantRegistry.supportedParticipants().map { participant -> participant.key }
    }

    suspend fun createBackup(
        trigger: String = "manual",
        options: AppBackupCreateOptions = AppBackupCreateOptions(),
    ): Result<AppBackupItem> {
        return repository.createBackup(trigger = trigger, options = options)
    }

    suspend fun createModuleBackup(
        module: AppBackupModuleKind,
        trigger: String = "manual",
        options: AppBackupCreateOptions = AppBackupCreateOptions(),
    ): Result<ModuleBackupItem> {
        return repository.createModuleBackup(module = module, trigger = trigger, options = options)
    }

    suspend fun deleteBackup(backupId: String): Result<Unit> {
        return repository.deleteBackup(backupId)
    }

    suspend fun deleteModuleBackup(
        module: AppBackupModuleKind,
        backupId: String,
    ): Result<Unit> {
        return repository.deleteModuleBackup(module, backupId)
    }

    suspend fun exportBackupToUri(
        context: Context,
        backupId: String,
        targetUri: Uri,
    ): Result<Unit> {
        return repository.exportBackupToUri(context, backupId, targetUri)
    }

    suspend fun exportModuleBackupToUri(
        context: Context,
        module: AppBackupModuleKind,
        backupId: String,
        targetUri: Uri,
    ): Result<Unit> {
        return repository.exportModuleBackupToUri(context, module, backupId, targetUri)
    }

    suspend fun prepareImportFromBackup(backupId: String): Result<AppBackupImportSource> {
        return repository.prepareImportFromBackup(backupId)
    }

    suspend fun prepareImportFromUri(context: Context, uri: Uri): Result<AppBackupImportSource> {
        return repository.prepareImportFromUri(context, uri)
    }

    suspend fun prepareModuleImportFromBackup(
        module: AppBackupModuleKind,
        backupId: String,
    ): Result<ModuleBackupImportSource> {
        return repository.prepareModuleImportFromBackup(module, backupId)
    }

    suspend fun prepareModuleImportFromUri(
        context: Context,
        module: AppBackupModuleKind,
        uri: Uri,
    ): Result<ModuleBackupImportSource> {
        return repository.prepareModuleImportFromUri(context, module, uri)
    }

    suspend fun importBackup(
        source: AppBackupImportSource,
        plan: AppBackupImportPlan,
    ): Result<AppBackupRestoreResult> {
        return repository.importBackup(source, plan)
    }

    suspend fun importModuleBackup(
        source: ModuleBackupImportSource,
        mode: AppBackupImportMode,
    ): Result<Int> {
        return repository.importModuleBackup(source, mode)
    }
}

class AppBackupRepository(
    context: Context,
    private val dataPort: AppBackupDataPort,
    private val ttsVoiceAssetPort: TtsVoiceAssetPort,
    private val runtimeLogger: RuntimeLogger = RuntimeLogger.noop(),
) {
    private val timestampFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    private val appContext: Context = context.applicationContext
    private val backupDirectory: File = File(appContext.filesDir, "app-backups").apply { mkdirs() }
    private val moduleBackupRootDirectory: File = File(appContext.filesDir, "module-backups").apply { mkdirs() }

    private val _backups = MutableStateFlow<List<AppBackupItem>>(emptyList())
    val backups: StateFlow<List<AppBackupItem>> = _backups.asStateFlow()
    private val moduleBackupFlows = AppBackupModuleKind.entries.associateWith {
        MutableStateFlow<List<ModuleBackupItem>>(emptyList())
    }

    init {
        refreshBackups()
        refreshModuleBackups()
    }

    fun backupsForModule(module: AppBackupModuleKind): StateFlow<List<ModuleBackupItem>> {
        return moduleBackupFlows.getValue(module).asStateFlow()
    }

    suspend fun createBackup(
        trigger: String = "manual",
        options: AppBackupCreateOptions = AppBackupCreateOptions(),
    ): Result<AppBackupItem> = withContext(Dispatchers.IO) {
        runCatching {
            val manifest = buildManifest(trigger = trigger, options = options)
            val fileName = "full-backup-${timestampFormatter.format(LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(manifest.createdAt), ZoneId.systemDefault()))}-$trigger.zip"
            val file = File(backupDirectory, fileName)
            writeBackupPayload(file, manifest)
            refreshBackups()
            buildBackupItem(file) ?: error("Backup file was created but could not be parsed")
        }.onFailure { error ->
            runtimeLogger.append("Full backup create failed: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    suspend fun createModuleBackup(
        module: AppBackupModuleKind,
        trigger: String = "manual",
        options: AppBackupCreateOptions = AppBackupCreateOptions(),
    ): Result<ModuleBackupItem> = withContext(Dispatchers.IO) {
        runCatching {
            val manifest = buildModuleManifest(module = module, trigger = trigger, options = options)
            val fileName =
                "${module.filePrefix}-backup-${timestampFormatter.format(LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(manifest.createdAt), ZoneId.systemDefault()))}-$trigger.zip"
            val file = File(resolveModuleBackupDirectory(module), fileName)
            writeBackupPayload(file, manifest)
            refreshModuleBackups(module)
            buildModuleBackupItem(module, file) ?: error("Module backup file was created but could not be parsed")
        }.onFailure { error ->
            runtimeLogger.append("${module.storageLabel} backup create failed: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    suspend fun restoreBackup(
        backupId: String,
        mode: AppBackupImportMode = AppBackupImportMode.REPLACE_ALL,
    ): Result<AppBackupRestoreResult> = withContext(Dispatchers.IO) {
        runCatching {
            val file = resolveBackupFile(backupId) ?: error("Backup not found")
            restoreFromManifest(
                loadBackupPayload(file).manifest,
                AppBackupImportPlan(
                    bots = mode,
                    providers = mode,
                    personas = mode,
                    configs = mode,
                    conversations = mode,
                    qqAccounts = mode,
                    ttsAssets = mode,
                ),
            )
        }.onFailure { error ->
            runtimeLogger.append("Full backup restore failed: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    suspend fun deleteBackup(backupId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val file = resolveBackupFile(backupId) ?: error("Backup not found")
            if (!file.delete()) error("Delete failed")
            refreshBackups()
        }.onFailure { error ->
            runtimeLogger.append("Full backup delete failed: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    suspend fun deleteModuleBackup(
        module: AppBackupModuleKind,
        backupId: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val file = resolveModuleBackupFile(module, backupId) ?: error("Backup not found")
            if (!file.delete()) error("Delete failed")
            refreshModuleBackups(module)
        }.onFailure { error ->
            runtimeLogger.append("${module.storageLabel} backup delete failed: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    suspend fun exportBackupToUri(
        context: Context,
        backupId: String,
        targetUri: Uri,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val file = resolveBackupFile(backupId) ?: error("Backup not found")
            context.contentResolver.openOutputStream(targetUri, "w")?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
                output.flush()
            } ?: error("Unable to open export target")
        }.onFailure { error ->
            runtimeLogger.append("Full backup export failed: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    suspend fun exportModuleBackupToUri(
        context: Context,
        module: AppBackupModuleKind,
        backupId: String,
        targetUri: Uri,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val file = resolveModuleBackupFile(module, backupId) ?: error("Backup not found")
            context.contentResolver.openOutputStream(targetUri, "w")?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
                output.flush()
            } ?: error("Unable to open export target")
        }.onFailure { error ->
            runtimeLogger.append("${module.storageLabel} backup export failed: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    suspend fun prepareImportFromBackup(backupId: String): Result<AppBackupImportSource> = withContext(Dispatchers.IO) {
        runCatching {
            val file = resolveBackupFile(backupId) ?: error("Backup not found")
            val payload = loadBackupPayload(file)
            val manifest = payload.manifest
            AppBackupImportSource(
                label = file.nameWithoutExtension,
                manifest = manifest,
                preview = AppBackupImportPlanner.preview(buildCurrentSnapshot(), manifestToSnapshot(manifest, materializeTtsFiles = false)),
                extractedFiles = payload.extractedFiles,
            )
        }
    }

    suspend fun prepareImportFromUri(context: Context, uri: Uri): Result<AppBackupImportSource> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = loadBackupPayloadFromUri(context, uri)
            val manifest = payload.manifest
            AppBackupImportSource(
                label = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { "external-full-backup" } ?: "external-full-backup",
                manifest = manifest,
                preview = AppBackupImportPlanner.preview(
                    buildCurrentSnapshot(),
                    manifestToSnapshot(manifest, materializeTtsFiles = false, extractedFiles = payload.extractedFiles),
                ),
                extractedFiles = payload.extractedFiles,
            )
        }.onFailure { error ->
            runtimeLogger.append("Full backup external import failed: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    suspend fun prepareModuleImportFromBackup(
        module: AppBackupModuleKind,
        backupId: String,
    ): Result<ModuleBackupImportSource> = withContext(Dispatchers.IO) {
        runCatching {
            val file = resolveModuleBackupFile(module, backupId) ?: error("Backup not found")
            val payload = loadBackupPayload(file)
            val manifest = payload.manifest
            buildModuleImportSource(
                module = module,
                label = file.nameWithoutExtension,
                manifest = manifest,
                extractedFiles = payload.extractedFiles,
            )
        }
    }

    suspend fun prepareModuleImportFromUri(
        context: Context,
        module: AppBackupModuleKind,
        uri: Uri,
    ): Result<ModuleBackupImportSource> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = loadBackupPayloadFromUri(context, uri)
            val manifest = payload.manifest
            buildModuleImportSource(
                module = module,
                label = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { "external-${module.filePrefix}-backup" }
                    ?: "external-${module.filePrefix}-backup",
                manifest = manifest,
                extractedFiles = payload.extractedFiles,
            )
        }.onFailure { error ->
            runtimeLogger.append("${module.storageLabel} external import failed: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    suspend fun importBackup(
        manifest: AppBackupManifest,
        mode: AppBackupImportMode = AppBackupImportMode.REPLACE_ALL,
    ): Result<AppBackupRestoreResult> = importBackup(
        manifest = manifest,
        plan = AppBackupImportPlan(
            bots = mode,
            providers = mode,
            personas = mode,
            configs = mode,
            conversations = mode,
            qqAccounts = mode,
            ttsAssets = mode,
        ),
    )

    suspend fun importBackup(
        manifest: AppBackupManifest,
        plan: AppBackupImportPlan,
    ): Result<AppBackupRestoreResult> = withContext(Dispatchers.IO) {
        runCatching {
            restoreFromManifest(manifest, plan)
        }.onFailure { error ->
            runtimeLogger.append("Full backup import failed: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    suspend fun importBackup(
        source: AppBackupImportSource,
        plan: AppBackupImportPlan,
    ): Result<AppBackupRestoreResult> = withContext(Dispatchers.IO) {
        runCatching {
            restoreFromManifest(source.manifest, plan, source.extractedFiles)
        }.onFailure { error ->
            runtimeLogger.append("Full backup import failed: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    suspend fun importModuleBackup(
        module: AppBackupModuleKind,
        manifest: AppBackupManifest,
        mode: AppBackupImportMode,
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val result = restoreFromManifest(
                manifest = moduleOnlyManifest(module, manifest),
                plan = moduleOnlyImportPlan(module, mode),
            )
            refreshModuleBackups(module)
            moduleCountFromRestoreResult(module, result)
        }.onFailure { error ->
            runtimeLogger.append("${module.storageLabel} import failed: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    suspend fun importModuleBackup(
        source: ModuleBackupImportSource,
        mode: AppBackupImportMode,
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val result = restoreFromManifest(
                manifest = source.manifest,
                plan = moduleOnlyImportPlan(source.module, mode),
                extractedFiles = source.extractedFiles,
            )
            refreshModuleBackups(source.module)
            moduleCountFromRestoreResult(source.module, result)
        }.onFailure { error ->
            runtimeLogger.append("${source.module.storageLabel} import failed: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    private fun refreshBackups() {
        _backups.value = backupDirectory
            .listFiles { file ->
                file.isFile && (
                    file.extension.equals("json", ignoreCase = true) ||
                        file.extension.equals("zip", ignoreCase = true)
                    )
            }
            .orEmpty()
            .mapNotNull(::buildBackupItem)
            .sortedByDescending { it.createdAt }
    }

    private fun refreshModuleBackups(module: AppBackupModuleKind? = null) {
        val modules = module?.let(::listOf) ?: AppBackupModuleKind.entries
        modules.forEach { kind ->
            moduleBackupFlows.getValue(kind).value = resolveModuleBackupDirectory(kind)
                .listFiles { file ->
                    file.isFile && (
                        file.extension.equals("json", ignoreCase = true) ||
                            file.extension.equals("zip", ignoreCase = true)
                        )
                }
                .orEmpty()
                .mapNotNull { buildModuleBackupItem(kind, it) }
                .sortedByDescending { it.createdAt }
        }
    }

    private fun resolveBackupFile(backupId: String): File? {
        return File(backupDirectory, backupId).takeIf { it.exists() && it.isFile }
    }

    private fun resolveModuleBackupDirectory(module: AppBackupModuleKind): File {
        return File(moduleBackupRootDirectory, module.directoryName).apply { mkdirs() }
    }

    private fun resolveModuleBackupFile(
        module: AppBackupModuleKind,
        backupId: String,
    ): File? {
        return File(resolveModuleBackupDirectory(module), backupId).takeIf { it.exists() && it.isFile }
    }

    private fun buildBackupItem(file: File): AppBackupItem? {
        return runCatching {
            val manifest = loadBackupPayload(file).manifest
            AppBackupItem(
                id = file.name,
                fileName = file.nameWithoutExtension,
                createdAt = manifest.createdAt,
                trigger = manifest.trigger,
                moduleCounts = mapOf(
                    "bots" to manifest.modules.bots.count,
                    "providers" to manifest.modules.providers.count,
                    "personas" to manifest.modules.personas.count,
                    "configs" to manifest.modules.configs.count,
                    "conversations" to manifest.modules.conversations.count,
                    "qqLogin" to manifest.modules.qqLogin.count,
                    "ttsAssets" to manifest.modules.ttsAssets.count,
                ),
            )
        }.getOrNull()
    }

    private fun buildModuleBackupItem(
        module: AppBackupModuleKind,
        file: File,
    ): ModuleBackupItem? {
        return runCatching {
            val manifest = loadBackupPayload(file).manifest
            val snapshot = moduleSnapshot(module, manifest.modules)
            ModuleBackupItem(
                id = file.name,
                fileName = file.nameWithoutExtension,
                createdAt = manifest.createdAt,
                trigger = manifest.trigger,
                module = module,
                recordCount = snapshot.count,
                hasFiles = snapshot.hasFiles,
            )
        }.getOrNull()
    }

    private fun buildManifest(
        trigger: String,
        options: AppBackupCreateOptions,
    ): AppBackupManifest {
        val now = System.currentTimeMillis()
        val dataPort = resolveDataPort()
        val botProfiles = dataPort.snapshotBots()
        val providerProfiles = dataPort.snapshotProviders()
        val personaProfiles = dataPort.snapshotPersonas()
        val configProfiles = dataPort.snapshotConfigs()
        val conversations = dataPort.snapshotConversations()
        val externalState = dataPort.snapshotExternalState()
        val ttsAssets = resolveTtsVoiceAssetPort().snapshotAssets()
        return AppBackupManifest(
            createdAt = now,
            trigger = trigger,
            modules = AppBackupModules(
                bots = AppBackupModuleSnapshot(
                    count = botProfiles.size,
                    records = botProfiles.map(::botToJson),
                ),
                providers = AppBackupModuleSnapshot(
                    count = providerProfiles.size,
                    records = providerProfiles.map { profile ->
                        providerToJson(profile, includeApiKey = options.includeProviderApiKeys)
                    },
                ),
                personas = AppBackupModuleSnapshot(
                    count = personaProfiles.size,
                    records = personaProfiles.map(::personaToJson),
                ),
                configs = AppBackupModuleSnapshot(
                    count = configProfiles.size,
                    records = configProfiles.map(::configToJson),
                ),
                conversations = AppBackupModuleSnapshot(
                    count = conversations.size,
                    records = conversations,
                ),
                qqLogin = AppBackupModuleSnapshot(
                    count = externalState.savedAccounts.size,
                    records = listOf(loginStateToJson(externalState.quickLoginUin, externalState.savedAccounts)),
                ),
                ttsAssets = AppBackupModuleSnapshot(
                    count = ttsAssets.size,
                    hasFiles = ttsAssets.any { asset -> asset.clips.any { it.localPath.isNotBlank() } },
                    files = ttsAssets.flatMap { asset -> asset.clips.map { clip -> "${asset.id}/${File(clip.localPath).name}" } },
                    records = ttsAssets.map(::ttsAssetToJson),
                ),
            ),
            appState = AppBackupAppState(
                selectedBotId = externalState.selectedBotId,
                selectedConfigId = externalState.selectedConfigId,
            ),
        )
    }

    private fun buildModuleManifest(
        module: AppBackupModuleKind,
        trigger: String,
        options: AppBackupCreateOptions,
    ): AppBackupManifest {
        return moduleOnlyManifest(module, buildManifest(trigger = trigger, options = options))
    }

    private suspend fun restoreFromManifest(
        manifest: AppBackupManifest,
        plan: AppBackupImportPlan,
        extractedFiles: Map<String, File> = emptyMap(),
    ): AppBackupRestoreResult {
        val incoming = manifestToSnapshot(manifest, materializeTtsFiles = true, materializePersonaFiles = true, extractedFiles = extractedFiles)
        val current = buildCurrentSnapshot()
        val resolved = AppBackupImportPlanner.merge(current, incoming, plan)
        val incomingCoverRefs = incoming.personas.mapNotNull { it.cover?.assetRef }.toSet()
        val resolvedCoverRefs = resolved.personas.mapNotNull { it.cover?.assetRef }.toSet()
        val dataPort = resolveDataPort()
        val appliedStages = mutableListOf<AppBackupRestoreStage>()
        val stages = listOf(
            AppBackupRestoreStage(
                name = "providers",
                shouldApply = current.providers != resolved.providers,
                apply = { dataPort.restoreProviders(resolved.providers) },
                rollback = { dataPort.restoreProviders(current.providers) },
            ),
            AppBackupRestoreStage(
                name = "personas",
                shouldApply = current.personas != resolved.personas,
                apply = { dataPort.restorePersonas(resolved.personas) },
                rollback = { dataPort.restorePersonas(current.personas) },
            ),
            AppBackupRestoreStage(
                name = "configs",
                shouldApply = current.configs != resolved.configs ||
                    current.appState.selectedConfigId != resolved.appState.selectedConfigId,
                apply = { dataPort.restoreConfigs(resolved.configs, resolved.appState.selectedConfigId) },
                rollback = { dataPort.restoreConfigs(current.configs, current.appState.selectedConfigId) },
            ),
            AppBackupRestoreStage(
                name = "bots",
                shouldApply = current.bots != resolved.bots ||
                    current.appState.selectedBotId != resolved.appState.selectedBotId,
                apply = { dataPort.restoreBots(resolved.bots, resolved.appState.selectedBotId) },
                rollback = { dataPort.restoreBots(current.bots, current.appState.selectedBotId) },
            ),
            AppBackupRestoreStage(
                name = "conversations",
                shouldApply = current.conversations != resolved.conversations,
                apply = { dataPort.restoreConversations(resolved.conversations) },
                rollback = { dataPort.restoreConversations(current.conversations) },
            ),
            AppBackupRestoreStage(
                name = "qqAccounts",
                shouldApply = current.quickLoginUin != resolved.quickLoginUin ||
                    current.savedAccounts != resolved.savedAccounts,
                apply = {
                    dataPort.restoreQqLoginState(
                        quickLoginUin = resolved.quickLoginUin,
                        savedAccounts = resolved.savedAccounts,
                    )
                },
                rollback = {
                    dataPort.restoreQqLoginState(
                        quickLoginUin = current.quickLoginUin,
                        savedAccounts = current.savedAccounts,
                    )
                },
            ),
            AppBackupRestoreStage(
                name = "ttsAssets",
                shouldApply = current.ttsAssets != resolved.ttsAssets,
                apply = { resolveTtsVoiceAssetPort().restoreAssets(resolved.ttsAssets) },
                rollback = { resolveTtsVoiceAssetPort().restoreAssets(current.ttsAssets) },
            ),
        )

        try {
            stages.forEach { stage ->
                if (!stage.shouldApply) return@forEach
                stage.apply()
                appliedStages += stage
            }
        } catch (restoreError: Throwable) {
            rollbackAppliedRestoreStages(
                appliedStages = appliedStages,
                restoreError = restoreError,
            )
            cleanupPersonaCoverRefs(personaCoverCleanupPlan(emptySet(), incomingCoverRefs, emptySet(), true))
            throw restoreError
        }

        cleanupPersonaCoverRefs(
            personaCoverCleanupPlan(
                current.personas.mapNotNull { it.cover?.assetRef }.toSet(),
                incomingCoverRefs,
                resolvedCoverRefs,
                false,
            ),
        )

        return AppBackupRestoreResult(
            botCount = resolved.bots.size,
            providerCount = resolved.providers.size,
            personaCount = resolved.personas.size,
            configCount = resolved.configs.size,
            conversationCount = resolved.conversations.size,
            qqAccountCount = resolved.savedAccounts.size,
            ttsAssetCount = resolved.ttsAssets.size,
        )
    }

    private fun buildModuleImportSource(
        module: AppBackupModuleKind,
        label: String,
        manifest: AppBackupManifest,
        extractedFiles: Map<String, File> = emptyMap(),
    ): ModuleBackupImportSource {
        val trimmedManifest = moduleOnlyManifest(module, manifest)
        val preview = AppBackupImportPlanner.preview(
            buildCurrentSnapshot(),
            manifestToSnapshot(trimmedManifest, materializeTtsFiles = false, extractedFiles = extractedFiles),
        )
        return ModuleBackupImportSource(
            label = label,
            module = module,
            manifest = trimmedManifest,
            preview = moduleConflictFor(module, preview),
            extractedFiles = extractedFiles,
        )
    }

    private suspend fun rollbackAppliedRestoreStages(
        appliedStages: List<AppBackupRestoreStage>,
        restoreError: Throwable,
    ) {
        appliedStages.asReversed().forEach { stage ->
            runCatching {
                stage.rollback()
            }.onFailure { rollbackError ->
                restoreError.addSuppressed(rollbackError)
                runtimeLogger.append(
                    "Full backup rollback failed at ${stage.name}: ${rollbackError.message ?: rollbackError.javaClass.simpleName}",
                )
            }
        }
    }

    private fun parseManifest(json: String): AppBackupManifest {
        return AppBackupJson.parseManifest(JSONObject(json))
    }

    private fun writeBackupPayload(
        file: File,
        manifest: AppBackupManifest,
    ) {
        if (file.extension.equals("zip", ignoreCase = true)) {
            writeAppBackupZip(
                targetZip = file,
                manifestJson = manifest.toJson(),
                files = resolveZipEntries(manifest),
            )
        } else {
            file.writeText(manifest.toJson().toString(2), Charsets.UTF_8)
        }
    }

    private fun loadBackupPayload(file: File): BackupPayload {
        val parsed = readAppBackupPayloadFile(
            file = file,
            extractionDirectory = if (file.extension.equals("zip", ignoreCase = true)) {
                createTempImportDirectory("local")
            } else {
                null
            },
        )
        return BackupPayload(
            manifest = AppBackupJson.parseManifest(parsed.manifestJson),
            extractedFiles = parsed.extractedFiles,
        )
    }

    private fun loadBackupPayloadFromUri(
        context: Context,
        uri: Uri,
    ): BackupPayload {
        val tempFile = File(createTempImportDirectory("uri"), uri.lastPathSegment?.substringAfterLast('/') ?: "import.bin")
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Unable to open import file")
        return if (looksLikeZip(tempFile)) {
            val unzipDirectory = createTempImportDirectory("unzipped")
            val zip = readAppBackupZip(tempFile, unzipDirectory)
            BackupPayload(
                manifest = AppBackupJson.parseManifest(zip.manifestJson),
                extractedFiles = zip.files,
            )
        } else {
            BackupPayload(manifest = parseManifest(tempFile.readText(Charsets.UTF_8)))
        }
    }

    private fun createTempImportDirectory(label: String): File {
        return File(appContext.cacheDir, "app-backup-import/$label-${UUID.randomUUID()}").apply { mkdirs() }
    }

    private fun looksLikeZip(file: File): Boolean {
        if (file.extension.equals("zip", ignoreCase = true)) return true
        file.inputStream().buffered().use { input ->
            return looksLikeZip(input)
        }
    }

    private fun resolveZipEntries(manifest: AppBackupManifest): List<AppBackupZipEntrySource> {
        return buildList {
            manifest.modules.personas.records.mapNotNull { it as? JSONObject }.forEach { persona ->
                val cover = persona.optJSONObject("cover") ?: return@forEach
                val assetRef = cover.optString("assetRef")
                val archivePath = cover.optString("archivePath", assetRef)
                val source = File(appContext.filesDir, assetRef)
                if (assetRef.isNotBlank() && source.isFile) add(AppBackupZipEntrySource(archivePath, source))
            }
            manifest.modules.ttsAssets.records.mapNotNull { it as? JSONObject }.forEach { assetObject ->
                val assetId = assetObject.optString("id")
                val clips = assetObject.optJSONArray("clips") ?: JSONArray()
                for (index in 0 until clips.length()) {
                    val clip = clips.optJSONObject(index) ?: continue
                    val localPath = clip.optString("localPath").trim()
                    if (localPath.isBlank()) continue
                    val source = File(localPath)
                    if (!source.exists() || !source.isFile) continue
                    val archivePath = clip.optString("archivePath").ifBlank {
                        ttsClipArchivePath(assetId, source.name)
                    }
                    add(
                        AppBackupZipEntrySource(
                            archivePath = archivePath,
                            sourceFile = source,
                        ),
                    )
                }
            }
        }
    }

    private fun buildCurrentSnapshot(): AppBackupSnapshot {
        val dataPort = resolveDataPort()
        val externalState = dataPort.snapshotExternalState()
        return AppBackupSnapshot(
            bots = dataPort.snapshotBots(),
            providers = dataPort.snapshotProviders(),
            personas = dataPort.snapshotPersonas(),
            configs = dataPort.snapshotConfigs(),
            conversations = dataPort.snapshotConversations(),
            quickLoginUin = externalState.quickLoginUin,
            savedAccounts = externalState.savedAccounts,
            ttsAssets = resolveTtsVoiceAssetPort().snapshotAssets(),
            appState = AppBackupAppState(
                selectedBotId = externalState.selectedBotId,
                selectedConfigId = externalState.selectedConfigId,
            ),
        )
    }

    private fun manifestToSnapshot(
        manifest: AppBackupManifest,
        materializeTtsFiles: Boolean,
        materializePersonaFiles: Boolean = false,
        extractedFiles: Map<String, File> = emptyMap(),
    ): AppBackupSnapshot {
        val qqLogin = manifest.modules.qqLogin.records.firstOrNull() as? JSONObject
        return AppBackupSnapshot(
            bots = manifest.modules.bots.records.mapNotNull { (it as? JSONObject)?.toBotProfile() },
            providers = manifest.modules.providers.records.mapNotNull { (it as? JSONObject)?.toProviderProfile() },
            personas = manifest.modules.personas.records.mapNotNull { record ->
                val json = record as? JSONObject ?: return@mapNotNull null
                (if (materializePersonaFiles) materializePersonaCover(json, extractedFiles) else json)?.toPersonaProfile()
            },
            configs = manifest.modules.configs.records.mapNotNull { (it as? JSONObject)?.toConfigProfile() },
            conversations = manifest.modules.conversations.records.filterIsInstance<ConversationSession>(),
            quickLoginUin = qqLogin?.optString("quickLoginUin").orEmpty(),
            savedAccounts = qqLogin?.optJSONArray("savedAccounts").toSavedAccounts(),
            ttsAssets = manifest.modules.ttsAssets.records.mapNotNull {
                (it as? JSONObject)?.toTtsAsset(appContext, materializeTtsFiles, extractedFiles)
            },
            appState = manifest.appState,
        )
    }

    private fun botToJson(profile: BotProfile): JSONObject {
        return JSONObject()
            .put("id", profile.id)
            .put("platformName", profile.platformName)
            .put("displayName", profile.displayName)
            .put("tag", profile.tag)
            .put("accountHint", profile.accountHint)
            .put("boundQqUins", JSONArray(profile.boundQqUins))
            .put("triggerWords", JSONArray(profile.triggerWords))
            .put("autoReplyEnabled", profile.autoReplyEnabled)
            .put("persistConversationLocally", profile.persistConversationLocally)
            .put("bridgeMode", profile.bridgeMode)
            .put("bridgeEndpoint", profile.bridgeEndpoint)
            .put("defaultProviderId", profile.defaultProviderId)
            .put("defaultPersonaId", profile.defaultPersonaId)
            .put("configProfileId", profile.configProfileId)
            .put("status", profile.status)
    }

    private fun providerToJson(
        profile: ProviderProfile,
        includeApiKey: Boolean,
    ): JSONObject {
        return JSONObject()
            .put("id", profile.id)
            .put("name", profile.name)
            .put("baseUrl", profile.baseUrl)
            .put("model", profile.model)
            .put("providerType", profile.providerType.name)
            .put("apiKey", if (includeApiKey) profile.apiKey else "")
            .put("enabled", profile.enabled)
            .put("multimodalRuleSupport", profile.multimodalRuleSupport.name)
            .put("multimodalProbeSupport", profile.multimodalProbeSupport.name)
            .put("nativeStreamingRuleSupport", profile.nativeStreamingRuleSupport.name)
            .put("nativeStreamingProbeSupport", profile.nativeStreamingProbeSupport.name)
            .put("sttProbeSupport", profile.sttProbeSupport.name)
            .put("ttsProbeSupport", profile.ttsProbeSupport.name)
            .put("capabilities", JSONArray(profile.capabilities.map { it.name }))
            .put("ttsVoiceOptions", JSONArray(profile.ttsVoiceOptions))
    }

    private fun personaToJson(profile: PersonaProfile): JSONObject {
        return JSONObject()
            .put("id", profile.id)
            .put("name", profile.name)
            .put("tags", JSONArray(profile.tags))
            .put("systemPrompt", profile.systemPrompt)
            .put("enabledTools", JSONArray(profile.enabledTools.toList()))
            .put("defaultProviderId", profile.defaultProviderId)
            .put("maxContextMessages", profile.maxContextMessages)
            .put("enabled", profile.enabled)
            .apply {
                profile.cover?.let { cover ->
                    val source = File(appContext.filesDir, cover.assetRef)
                    put("cover", JSONObject()
                    .put("assetRef", cover.assetRef)
                    .put("archivePath", cover.assetRef)
                    .put("length", source.takeIf(File::isFile)?.length() ?: 0L)
                    .put("contentSha256", cover.contentSha256)
                    .put("pixelWidth", cover.pixelWidth).put("pixelHeight", cover.pixelHeight)
                    .put("updatedAt", cover.updatedAt)
                    .put("portraitCrop", JSONObject().put("centerX", cover.portraitCrop.centerX).put("centerY", cover.portraitCrop.centerY).put("zoom", cover.portraitCrop.zoom))
                    .put("squareCrop", JSONObject().put("centerX", cover.squareCrop.centerX).put("centerY", cover.squareCrop.centerY).put("zoom", cover.squareCrop.zoom))) }
            }
    }

    private fun materializePersonaCover(json: JSONObject, extractedFiles: Map<String, File>): JSONObject? {
        val cover = json.optJSONObject("cover") ?: return json
        val archivePath = cover.optString("archivePath", cover.optString("assetRef"))
        val staged = extractedFiles[archivePath] ?: return json.apply { remove("cover") }
        val expectedLength = cover.optLong("length", staged.length())
        val expectedHash = cover.optString("contentSha256")
        val valid = staged.length() == expectedLength &&
            (expectedHash.isBlank() || staged.sha256().equals(expectedHash, ignoreCase = true)) &&
            BitmapFactory.decodeFile(staged.absolutePath) != null
        if (!valid) {
            runtimeLogger.append("Persona cover restore warning: invalid asset for ${json.optString("id")}")
            return json.apply { remove("cover") }
        }
        val originalRef = cover.optString("assetRef")
        val extension = File(originalRef).extension.ifBlank { "img" }
        val assetRef = "assets/persona-covers/${json.optString("id")}/import-${UUID.randomUUID()}.$extension"
        val destination = File(appContext.filesDir, assetRef)
        destination.parentFile?.mkdirs()
        staged.copyTo(destination, overwrite = true)
        cover.put("assetRef", assetRef)
        return json
    }

    private fun File.sha256(): String = inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) { val read = input.read(buffer); if (read < 0) break; digest.update(buffer, 0, read) }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun cleanupPersonaCoverRefs(refs: Set<String>) {
        refs.filter { it.startsWith("assets/persona-covers/") }.forEach { ref ->
            runCatching { File(appContext.filesDir, ref).delete() }
        }
    }
    private fun configToJson(profile: ConfigProfile): JSONObject {
        return JSONObject()
            .put("id", profile.id)
            .put("name", profile.name)
            .put("defaultChatProviderId", profile.defaultChatProviderId)
            .put("defaultVisionProviderId", profile.defaultVisionProviderId)
            .put("defaultSttProviderId", profile.defaultSttProviderId)
            .put("defaultTtsProviderId", profile.defaultTtsProviderId)
            .put("sttEnabled", profile.sttEnabled)
            .put("ttsEnabled", profile.ttsEnabled)
            .put("alwaysTtsEnabled", profile.alwaysTtsEnabled)
            .put("ttsReadBracketedContent", profile.ttsReadBracketedContent)
            .put("textStreamingEnabled", profile.textStreamingEnabled)
            .put("voiceStreamingEnabled", profile.voiceStreamingEnabled)
            .put("streamingMessageIntervalMs", profile.streamingMessageIntervalMs)
            .put("realWorldTimeAwarenessEnabled", profile.realWorldTimeAwarenessEnabled)
            .put("imageCaptionTextEnabled", profile.imageCaptionTextEnabled)
            .put("webSearchEnabled", profile.webSearchEnabled)
            .put("proactiveEnabled", profile.proactiveEnabled)
            .put("includeScheduledTaskConversationContext", profile.includeScheduledTaskConversationContext)
            .put("ttsVoiceId", profile.ttsVoiceId)
            .put("imageCaptionPrompt", profile.imageCaptionPrompt)
            .put("adminUids", JSONArray(profile.adminUids))
            .put("pluginCommandsAdminOnlyEnabled", profile.pluginCommandsAdminOnlyEnabled)
            .put("sessionIsolationEnabled", profile.sessionIsolationEnabled)
            .put("wakeWords", JSONArray(profile.wakeWords))
            .put("wakeWordsAdminOnlyEnabled", profile.wakeWordsAdminOnlyEnabled)
            .put("privateChatRequiresWakeWord", profile.privateChatRequiresWakeWord)
            .put("replyTextPrefix", profile.replyTextPrefix)
            .put("quoteSenderMessageEnabled", profile.quoteSenderMessageEnabled)
            .put("mentionSenderEnabled", profile.mentionSenderEnabled)
            .put("replyOnAtOnlyEnabled", profile.replyOnAtOnlyEnabled)
            .put("whitelistEnabled", profile.whitelistEnabled)
            .put("whitelistEntries", JSONArray(profile.whitelistEntries))
            .put("logOnWhitelistMiss", profile.logOnWhitelistMiss)
            .put("adminGroupBypassWhitelistEnabled", profile.adminGroupBypassWhitelistEnabled)
            .put("adminPrivateBypassWhitelistEnabled", profile.adminPrivateBypassWhitelistEnabled)
            .put("ignoreSelfMessageEnabled", profile.ignoreSelfMessageEnabled)
            .put("ignoreAtAllEventEnabled", profile.ignoreAtAllEventEnabled)
            .put("replyWhenPermissionDenied", profile.replyWhenPermissionDenied)
            .put("rateLimitWindowSeconds", profile.rateLimitWindowSeconds)
            .put("rateLimitMaxCount", profile.rateLimitMaxCount)
            .put("rateLimitStrategy", profile.rateLimitStrategy)
            .put("keywordDetectionEnabled", profile.keywordDetectionEnabled)
            .put("keywordPatterns", JSONArray(profile.keywordPatterns))
    }

    private fun loginStateToJson(
        quickLoginUin: String,
        savedAccounts: List<SavedQqAccount>,
    ): JSONObject {
        return JSONObject()
            .put("quickLoginUin", quickLoginUin)
            .put(
                "savedAccounts",
                JSONArray().apply {
                    savedAccounts.forEach { account ->
                        put(
                            JSONObject()
                                .put("uin", account.uin)
                                .put("nickName", account.nickName)
                                .put("avatarUrl", account.avatarUrl),
                        )
                    }
                },
            )
    }

    private fun resolveDataPort(): AppBackupDataPort {
        return dataPort
    }

    private fun resolveTtsVoiceAssetPort(): TtsVoiceAssetPort {
        return ttsVoiceAssetPort
    }

    private fun ttsAssetToJson(asset: TtsVoiceReferenceAsset): JSONObject {
        return JSONObject()
            .put("id", asset.id)
            .put("name", asset.name)
            .put("source", asset.source)
            .put("remoteUrl", asset.remoteUrl)
            .put("durationMs", asset.durationMs)
            .put("sampleRateHz", asset.sampleRateHz)
            .put("createdAt", asset.createdAt)
            .put(
                "clips",
                JSONArray().apply {
                    asset.clips.forEach { clip ->
                        val backupPayload = buildTtsClipBackupPayload(
                            assetId = asset.id,
                            clipId = clip.id,
                            localPath = clip.localPath,
                            includeEmbeddedData = false,
                        )
                        put(
                            JSONObject()
                                .put("id", clip.id)
                                .put("localPath", clip.localPath)
                                .put("archivePath", backupPayload.archivePath)
                                .put("durationMs", clip.durationMs)
                                .put("sampleRateHz", clip.sampleRateHz)
                                .put("createdAt", clip.createdAt)
                                .put("embeddedFileName", backupPayload.embeddedFileName)
                                .put("embeddedDataBase64", backupPayload.embeddedDataBase64),
                        )
                    }
                },
            )
            .put(
                "providerBindings",
                JSONArray().apply {
                    asset.providerBindings.forEach { binding ->
                        put(
                            JSONObject()
                                .put("id", binding.id)
                                .put("providerId", binding.providerId)
                                .put("providerType", binding.providerType.name)
                                .put("model", binding.model)
                                .put("voiceId", binding.voiceId)
                                .put("displayName", binding.displayName)
                                .put("createdAt", binding.createdAt)
                                .put("lastVerifiedAt", binding.lastVerifiedAt)
                                .put("status", binding.status),
                        )
                    }
                },
            )
    }
}

internal fun personaCoverCleanupPlan(
    currentRefs: Set<String>,
    incomingRefs: Set<String>,
    resolvedRefs: Set<String>,
    restoreFailed: Boolean,
): Set<String> = if (restoreFailed) incomingRefs else (currentRefs + incomingRefs) - resolvedRefs

private data class AppBackupRestoreStage(
    val name: String,
    val shouldApply: Boolean,
    val apply: suspend () -> Unit,
    val rollback: suspend () -> Unit,
)

private val AppBackupModuleKind.directoryName: String
    get() = when (this) {
        AppBackupModuleKind.BOTS -> "bots"
        AppBackupModuleKind.PROVIDERS -> "providers"
        AppBackupModuleKind.PERSONAS -> "personas"
        AppBackupModuleKind.CONFIGS -> "configs"
        AppBackupModuleKind.CONVERSATIONS -> "conversations"
        AppBackupModuleKind.QQ_ACCOUNTS -> "qq-accounts"
        AppBackupModuleKind.TTS_ASSETS -> "tts-assets"
    }

private val AppBackupModuleKind.filePrefix: String
    get() = when (this) {
        AppBackupModuleKind.BOTS -> "bot"
        AppBackupModuleKind.PROVIDERS -> "model"
        AppBackupModuleKind.PERSONAS -> "persona"
        AppBackupModuleKind.CONFIGS -> "config"
        AppBackupModuleKind.CONVERSATIONS -> "conversation"
        AppBackupModuleKind.QQ_ACCOUNTS -> "qq-account"
        AppBackupModuleKind.TTS_ASSETS -> "tts"
    }

private val AppBackupModuleKind.storageLabel: String
    get() = when (this) {
        AppBackupModuleKind.BOTS -> "Bot"
        AppBackupModuleKind.PROVIDERS -> "Model"
        AppBackupModuleKind.PERSONAS -> "Persona"
        AppBackupModuleKind.CONFIGS -> "Config"
        AppBackupModuleKind.CONVERSATIONS -> "Conversation"
        AppBackupModuleKind.QQ_ACCOUNTS -> "QQ account"
        AppBackupModuleKind.TTS_ASSETS -> "TTS"
    }

private fun looksLikeZip(input: InputStream): Boolean {
    val header = ByteArray(4)
    val bytesRead = input.read(header)
    return bytesRead == 4 &&
        header[0] == 'P'.code.toByte() &&
        header[1] == 'K'.code.toByte() &&
        header[2] == 3.toByte() &&
        header[3] == 4.toByte()
}

