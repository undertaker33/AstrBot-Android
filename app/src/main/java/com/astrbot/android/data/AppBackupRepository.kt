package com.astrbot.android.data

import android.content.Context
import android.net.Uri
import com.astrbot.android.data.backup.AppBackupAppState
import com.astrbot.android.data.backup.AppBackupJson
import com.astrbot.android.data.backup.AppBackupConflictPreview
import com.astrbot.android.data.backup.AppBackupImportSource
import com.astrbot.android.data.backup.AppBackupImportPlan
import com.astrbot.android.data.backup.AppBackupImportMode
import com.astrbot.android.data.backup.AppBackupImportPlanner
import com.astrbot.android.data.backup.AppBackupItem
import com.astrbot.android.data.backup.AppBackupManifest
import com.astrbot.android.data.backup.AppBackupModuleConflict
import com.astrbot.android.data.backup.AppBackupModuleKind
import com.astrbot.android.data.backup.AppBackupModuleSnapshot
import com.astrbot.android.data.backup.AppBackupModules
import com.astrbot.android.data.backup.AppBackupRestoreResult
import com.astrbot.android.data.backup.AppBackupSnapshot
import com.astrbot.android.data.backup.AppBackupZipEntrySource
import com.astrbot.android.data.backup.BackupPayload
import com.astrbot.android.data.backup.ModuleBackupImportSource
import com.astrbot.android.data.backup.ModuleBackupItem
import com.astrbot.android.data.backup.buildTtsClipBackupPayload
import com.astrbot.android.data.backup.moduleCountFromRestoreResult
import com.astrbot.android.data.backup.moduleOnlyManifest
import com.astrbot.android.data.backup.moduleSnapshot
import com.astrbot.android.data.backup.readAppBackupZip
import com.astrbot.android.data.backup.readAppBackupPayloadFile
import com.astrbot.android.data.backup.moduleConflictFor
import com.astrbot.android.data.backup.moduleOnlyImportPlan
import com.astrbot.android.data.backup.ttsClipArchivePath
import com.astrbot.android.data.backup.toBotProfile
import com.astrbot.android.data.backup.toConfigProfile
import com.astrbot.android.data.backup.toJson
import com.astrbot.android.data.backup.toPersonaProfile
import com.astrbot.android.data.backup.toProviderProfile
import com.astrbot.android.data.backup.toSavedAccounts
import com.astrbot.android.data.backup.toTtsAsset
import com.astrbot.android.data.backup.writeAppBackupZip
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.ConversationSession
import com.astrbot.android.model.PersonaProfile
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.SavedQqAccount
import com.astrbot.android.model.TtsVoiceReferenceAsset
import com.astrbot.android.runtime.RuntimeLogRepository
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
import java.util.concurrent.atomic.AtomicBoolean

object AppBackupRepository {
    private val initialized = AtomicBoolean(false)
    private val timestampFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    private lateinit var appContext: Context
    private lateinit var backupDirectory: File
    private lateinit var moduleBackupRootDirectory: File

    private val _backups = MutableStateFlow<List<AppBackupItem>>(emptyList())
    val backups: StateFlow<List<AppBackupItem>> = _backups.asStateFlow()
    private val moduleBackupFlows = AppBackupModuleKind.entries.associateWith {
        MutableStateFlow<List<ModuleBackupItem>>(emptyList())
    }

    fun initialize(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        appContext = context.applicationContext
        backupDirectory = File(appContext.filesDir, "app-backups").apply { mkdirs() }
        moduleBackupRootDirectory = File(appContext.filesDir, "module-backups").apply { mkdirs() }
        refreshBackups()
        refreshModuleBackups()
    }

    fun backupsForModule(module: AppBackupModuleKind): StateFlow<List<ModuleBackupItem>> {
        return moduleBackupFlows.getValue(module).asStateFlow()
    }

    suspend fun createBackup(trigger: String = "manual"): Result<AppBackupItem> = withContext(Dispatchers.IO) {
        runCatching {
            val manifest = buildManifest(trigger)
            val fileName = "full-backup-${timestampFormatter.format(LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(manifest.createdAt), ZoneId.systemDefault()))}-$trigger.zip"
            val file = File(backupDirectory, fileName)
            writeBackupPayload(file, manifest)
            refreshBackups()
            buildBackupItem(file) ?: error("Backup file was created but could not be parsed")
        }.onFailure { error ->
            RuntimeLogRepository.append("Full backup create failed: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    suspend fun createModuleBackup(
        module: AppBackupModuleKind,
        trigger: String = "manual",
    ): Result<ModuleBackupItem> = withContext(Dispatchers.IO) {
        runCatching {
            val manifest = buildModuleManifest(module, trigger)
            val fileName =
                "${module.filePrefix}-backup-${timestampFormatter.format(LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(manifest.createdAt), ZoneId.systemDefault()))}-$trigger.zip"
            val file = File(resolveModuleBackupDirectory(module), fileName)
            writeBackupPayload(file, manifest)
            refreshModuleBackups(module)
            buildModuleBackupItem(module, file) ?: error("Module backup file was created but could not be parsed")
        }.onFailure { error ->
            RuntimeLogRepository.append("${module.storageLabel} backup create failed: ${error.message ?: error.javaClass.simpleName}")
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
            RuntimeLogRepository.append("Full backup restore failed: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    suspend fun deleteBackup(backupId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val file = resolveBackupFile(backupId) ?: error("Backup not found")
            if (!file.delete()) error("Delete failed")
            refreshBackups()
        }.onFailure { error ->
            RuntimeLogRepository.append("Full backup delete failed: ${error.message ?: error.javaClass.simpleName}")
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
            RuntimeLogRepository.append("${module.storageLabel} backup delete failed: ${error.message ?: error.javaClass.simpleName}")
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
            RuntimeLogRepository.append("Full backup export failed: ${error.message ?: error.javaClass.simpleName}")
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
            RuntimeLogRepository.append("${module.storageLabel} backup export failed: ${error.message ?: error.javaClass.simpleName}")
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
            RuntimeLogRepository.append("Full backup external import failed: ${error.message ?: error.javaClass.simpleName}")
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
            RuntimeLogRepository.append("${module.storageLabel} external import failed: ${error.message ?: error.javaClass.simpleName}")
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
            RuntimeLogRepository.append("Full backup import failed: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    suspend fun importBackup(
        source: AppBackupImportSource,
        plan: AppBackupImportPlan,
    ): Result<AppBackupRestoreResult> = withContext(Dispatchers.IO) {
        runCatching {
            restoreFromManifest(source.manifest, plan, source.extractedFiles)
        }.onFailure { error ->
            RuntimeLogRepository.append("Full backup import failed: ${error.message ?: error.javaClass.simpleName}")
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
            RuntimeLogRepository.append("${module.storageLabel} import failed: ${error.message ?: error.javaClass.simpleName}")
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
            RuntimeLogRepository.append("${source.module.storageLabel} import failed: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    private fun refreshBackups() {
        if (!initialized.get()) return
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
        if (!initialized.get()) return
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

    private fun buildManifest(trigger: String): AppBackupManifest {
        val now = System.currentTimeMillis()
        val botProfiles = BotRepository.snapshotProfiles()
        val providerProfiles = ProviderRepository.snapshotProfiles()
        val personaProfiles = PersonaRepository.snapshotProfiles()
        val configProfiles = ConfigRepository.snapshotProfiles()
        val conversations = ConversationRepository.snapshotSessions()
        val loginState = NapCatLoginRepository.loginState.value
        val ttsAssets = TtsVoiceAssetRepository.snapshotAssets()
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
                    records = providerProfiles.map(::providerToJson),
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
                    count = loginState.savedAccounts.size,
                    records = listOf(loginStateToJson(loginState.quickLoginUin, loginState.savedAccounts)),
                ),
                ttsAssets = AppBackupModuleSnapshot(
                    count = ttsAssets.size,
                    hasFiles = ttsAssets.any { asset -> asset.clips.any { it.localPath.isNotBlank() } },
                    files = ttsAssets.flatMap { asset -> asset.clips.map { clip -> "${asset.id}/${File(clip.localPath).name}" } },
                    records = ttsAssets.map(::ttsAssetToJson),
                ),
            ),
            appState = AppBackupAppState(
                selectedBotId = BotRepository.selectedBotId.value,
                selectedConfigId = ConfigRepository.selectedProfileId.value,
            ),
        )
    }

    private fun buildModuleManifest(
        module: AppBackupModuleKind,
        trigger: String,
    ): AppBackupManifest {
        return moduleOnlyManifest(module, buildManifest(trigger))
    }

    private suspend fun restoreFromManifest(
        manifest: AppBackupManifest,
        plan: AppBackupImportPlan,
        extractedFiles: Map<String, File> = emptyMap(),
    ): AppBackupRestoreResult {
        val incoming = manifestToSnapshot(manifest, materializeTtsFiles = true, extractedFiles = extractedFiles)
        val current = buildCurrentSnapshot()
        val resolved = AppBackupImportPlanner.merge(current, incoming, plan)

        ProviderRepository.restoreProfiles(resolved.providers)
        PersonaRepository.restoreProfiles(resolved.personas)
        ConfigRepository.restoreProfiles(resolved.configs, resolved.appState.selectedConfigId)
        BotRepository.restoreProfiles(resolved.bots, resolved.appState.selectedBotId)
        ConversationRepository.restoreSessions(resolved.conversations)
        NapCatLoginRepository.restoreSavedLoginState(
            quickLoginUin = resolved.quickLoginUin,
            savedAccounts = resolved.savedAccounts,
        )
        TtsVoiceAssetRepository.restoreAssets(resolved.ttsAssets)

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
        val loginState = NapCatLoginRepository.loginState.value
        return AppBackupSnapshot(
            bots = BotRepository.snapshotProfiles(),
            providers = ProviderRepository.snapshotProfiles(),
            personas = PersonaRepository.snapshotProfiles(),
            configs = ConfigRepository.snapshotProfiles(),
            conversations = ConversationRepository.snapshotSessions(),
            quickLoginUin = loginState.quickLoginUin,
            savedAccounts = loginState.savedAccounts,
            ttsAssets = TtsVoiceAssetRepository.snapshotAssets(),
            appState = AppBackupAppState(
                selectedBotId = BotRepository.selectedBotId.value,
                selectedConfigId = ConfigRepository.selectedProfileId.value,
            ),
        )
    }

    private fun manifestToSnapshot(
        manifest: AppBackupManifest,
        materializeTtsFiles: Boolean,
        extractedFiles: Map<String, File> = emptyMap(),
    ): AppBackupSnapshot {
        val qqLogin = manifest.modules.qqLogin.records.firstOrNull() as? JSONObject
        return AppBackupSnapshot(
            bots = manifest.modules.bots.records.mapNotNull { (it as? JSONObject)?.toBotProfile() },
            providers = manifest.modules.providers.records.mapNotNull { (it as? JSONObject)?.toProviderProfile() },
            personas = manifest.modules.personas.records.mapNotNull { (it as? JSONObject)?.toPersonaProfile() },
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

    private fun providerToJson(profile: ProviderProfile): JSONObject {
        return JSONObject()
            .put("id", profile.id)
            .put("name", profile.name)
            .put("baseUrl", profile.baseUrl)
            .put("model", profile.model)
            .put("providerType", profile.providerType.name)
            .put("apiKey", profile.apiKey)
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
            .put("tag", profile.tag)
            .put("systemPrompt", profile.systemPrompt)
            .put("enabledTools", JSONArray(profile.enabledTools.toList()))
            .put("defaultProviderId", profile.defaultProviderId)
            .put("maxContextMessages", profile.maxContextMessages)
            .put("enabled", profile.enabled)
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
            .put("ttsVoiceId", profile.ttsVoiceId)
            .put("imageCaptionPrompt", profile.imageCaptionPrompt)
            .put("adminUids", JSONArray(profile.adminUids))
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
