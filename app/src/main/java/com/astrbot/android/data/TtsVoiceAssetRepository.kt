package com.astrbot.android.data

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.astrbot.android.data.db.AstrBotDatabase
import com.astrbot.android.data.db.TtsVoiceAssetAggregate
import com.astrbot.android.data.db.TtsVoiceAssetAggregateDao
import com.astrbot.android.data.db.TtsVoiceAssetEntity
import com.astrbot.android.data.db.TtsVoiceAssetWriteModel
import com.astrbot.android.data.db.toModel
import com.astrbot.android.data.db.toWriteModel
import com.astrbot.android.model.ClonedVoiceBinding
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ProviderType
import com.astrbot.android.model.TtsVoiceReferenceAsset
import com.astrbot.android.model.TtsVoiceReferenceClip
import com.astrbot.android.runtime.RuntimeLogRepository
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

object TtsVoiceAssetRepository {
    private const val PREFS_NAME = "tts_voice_assets"
    private const val KEY_ASSETS_JSON = "assets_json"

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var legacyPreferences: SharedPreferences? = null
    private var assetDao: TtsVoiceAssetAggregateDao = TtsVoiceAssetAggregateDaoPlaceholder.instance
    private val _assets = MutableStateFlow<List<TtsVoiceReferenceAsset>>(emptyList())

    val assets: StateFlow<List<TtsVoiceReferenceAsset>> = _assets.asStateFlow()

    fun initialize(context: Context) {
        val database = AstrBotDatabase.get(context)
        assetDao = database.ttsVoiceAssetAggregateDao()
        legacyPreferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        runBlocking(Dispatchers.IO) {
            seedStorageIfNeeded()
        }
        repositoryScope.launch {
            assetDao.observeAssetAggregates().collect { aggregates ->
                val loaded = aggregates.map(TtsVoiceAssetAggregate::toModel)
                _assets.value = loaded
                RuntimeLogRepository.append("TTS voice assets loaded: count=${loaded.size}")
            }
        }
    }

    fun listVoiceChoicesFor(provider: ProviderProfile?): List<Pair<String, String>> {
        if (provider == null) return emptyList()
        return _assets.value
            .flatMap { asset ->
                asset.providerBindings.mapNotNull { binding ->
                    if (binding.providerId == provider.id) {
                        binding.voiceId to binding.displayName
                    } else {
                        null
                    }
                }
            }
            .distinctBy { it.first }
    }

    fun importReferenceAudio(
        context: Context,
        sourceUri: Uri,
        name: String = "",
        assetId: String? = null,
    ): ImportReferenceAudioResult {
        val appContext = context.applicationContext
        val resolver = appContext.contentResolver
        val sourceDisplayName = queryDisplayName(appContext, sourceUri)
        val fileExtension = inferSupportedExtension(
            sourceDisplayName = sourceDisplayName,
            mimeType = resolver.getType(sourceUri),
        )
        val resolvedId = assetId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val outputDirectory = File(appContext.filesDir, "assets/tts-reference-audio").apply { mkdirs() }
        val clipId = UUID.randomUUID().toString()
        val destinationFile = File(outputDirectory, "$clipId.$fileExtension")
        resolver.openInputStream(sourceUri)?.use { input ->
            FileOutputStream(destinationFile).use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("Unable to read the selected reference audio file.")

        val metadata = readAudioMetadata(appContext, sourceUri)
        val clip = TtsVoiceReferenceClip(
            id = clipId,
            localPath = destinationFile.absolutePath,
            durationMs = metadata.durationMs,
            sampleRateHz = metadata.sampleRateHz,
        )
        val current = _assets.value.firstOrNull { it.id == resolvedId }
        val resolvedName = when {
            current != null -> current.name
            else -> name.trim().ifBlank { sourceDisplayName.substringBeforeLast('.').ifBlank { "Reference Audio" } }
        }
        val existingClips = current?.clips.orEmpty().ifEmpty {
            current?.localPath
                ?.takeIf { it.isNotBlank() }
                ?.let {
                    listOf(
                        TtsVoiceReferenceClip(
                            id = "${current.id}-legacy",
                            localPath = it,
                            durationMs = current.durationMs,
                            sampleRateHz = current.sampleRateHz,
                            createdAt = current.createdAt,
                        ),
                    )
                }
                .orEmpty()
        }
        val allClips = (existingClips + clip).sortedByDescending { it.createdAt }
        val primaryClip = allClips.maxByOrNull { it.durationMs }
        val asset = upsertReferenceAsset(
            id = resolvedId,
            name = resolvedName,
            source = "imported",
            localPath = primaryClip?.localPath.orEmpty(),
            remoteUrl = current?.remoteUrl.orEmpty(),
            durationMs = primaryClip?.durationMs ?: 0L,
            sampleRateHz = primaryClip?.sampleRateHz ?: 0,
        ).copy(clips = allClips)
        val updated = _assets.value.filterNot { it.id == asset.id } + asset
        _assets.value = updated.sortedByDescending { it.createdAt }
        persist(_assets.value)
        RuntimeLogRepository.append(
            "Reference audio clip imported: name=${asset.name} clips=${asset.clips.size} durationMs=${clip.durationMs}",
        )
        return ImportReferenceAudioResult(asset = asset, warning = null)
    }

    fun upsertReferenceAsset(
        id: String? = null,
        name: String,
        source: String = "",
        localPath: String = "",
        remoteUrl: String = "",
        durationMs: Long = 0L,
        sampleRateHz: Int = 0,
    ): TtsVoiceReferenceAsset {
        val resolvedId = id?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val current = _assets.value.firstOrNull { it.id == resolvedId }
        val updated = (current ?: TtsVoiceReferenceAsset(id = resolvedId, name = name)).copy(
            name = name.trim().ifBlank { "Unnamed voice asset" },
            source = source.trim(),
            localPath = localPath.trim(),
            remoteUrl = remoteUrl.trim(),
            durationMs = durationMs.coerceAtLeast(0L),
            sampleRateHz = sampleRateHz.coerceAtLeast(0),
        )
        _assets.value = _assets.value
            .filterNot { it.id == resolvedId }
            .plus(updated)
            .sortedByDescending { it.createdAt }
        persist(_assets.value)
        return updated
    }

    fun saveProviderBinding(
        assetId: String,
        providerId: String,
        providerType: ProviderType,
        model: String,
        voiceId: String,
        displayName: String,
    ) {
        if (assetId.isBlank() || providerId.isBlank() || voiceId.isBlank()) return
        _assets.value = _assets.value.map { asset ->
            if (asset.id != assetId) {
                asset
            } else {
                val bindingId = "${providerId}:${model.trim()}:${voiceId.trim()}"
                val newBinding = ClonedVoiceBinding(
                    id = bindingId,
                    providerId = providerId,
                    providerType = providerType,
                    model = model.trim(),
                    voiceId = voiceId.trim(),
                    displayName = displayName.trim().ifBlank { voiceId.trim() },
                    createdAt = System.currentTimeMillis(),
                    lastVerifiedAt = System.currentTimeMillis(),
                )
                asset.copy(
                    providerBindings = asset.providerBindings
                        .filterNot { it.id == bindingId }
                        .plus(newBinding)
                        .sortedByDescending { it.createdAt },
                )
            }
        }
        persist(_assets.value)
    }

    fun renameBinding(assetId: String, bindingId: String, displayName: String) {
        _assets.value = _assets.value.map { asset ->
            if (asset.id != assetId) asset else asset.copy(
                providerBindings = asset.providerBindings.map { binding ->
                    if (binding.id == bindingId) binding.copy(displayName = displayName.trim().ifBlank { binding.voiceId }) else binding
                },
            )
        }
        persist(_assets.value)
    }

    fun deleteReferenceAsset(assetId: String) {
        _assets.value = _assets.value.filterNot { it.id == assetId }
        persist(_assets.value)
    }

    fun clearReferenceAudio(assetId: String) {
        _assets.value = _assets.value.map { asset ->
            if (asset.id != assetId) {
                asset
            } else {
                asset.clips.forEach { clip ->
                    runCatching { File(clip.localPath).takeIf(File::exists)?.delete() }
                }
                asset.localPath.takeIf { it.isNotBlank() }?.let { path ->
                    runCatching { File(path).takeIf(File::exists)?.delete() }
                }
                asset.copy(
                    source = "cleared",
                    localPath = "",
                    remoteUrl = "",
                    durationMs = 0L,
                    sampleRateHz = 0,
                    clips = emptyList(),
                )
            }
        }
        persist(_assets.value)
    }

    fun deleteReferenceClip(assetId: String, clipId: String) {
        _assets.value = _assets.value.map { asset ->
            if (asset.id != assetId) {
                asset
            } else {
                val remainingClips = asset.clips.filterNot { clip ->
                    if (clip.id == clipId) {
                        runCatching { File(clip.localPath).takeIf(File::exists)?.delete() }
                        true
                    } else {
                        false
                    }
                }
                val primaryClip = remainingClips.maxByOrNull { it.durationMs }
                asset.copy(
                    localPath = primaryClip?.localPath.orEmpty(),
                    durationMs = primaryClip?.durationMs ?: 0L,
                    sampleRateHz = primaryClip?.sampleRateHz ?: 0,
                    clips = remainingClips,
                    source = if (remainingClips.isEmpty()) "cleared" else asset.source,
                )
            }
        }
        persist(_assets.value)
    }

    fun deleteBinding(assetId: String, bindingId: String) {
        _assets.value = _assets.value.map { asset ->
            if (asset.id != assetId) asset else asset.copy(
                providerBindings = asset.providerBindings.filterNot { it.id == bindingId },
            )
        }
        persist(_assets.value)
    }

    fun snapshotAssets(): List<TtsVoiceReferenceAsset> {
        return _assets.value.map { asset ->
            asset.copy(
                clips = asset.clips.map { clip -> clip.copy() },
                providerBindings = asset.providerBindings.map { binding -> binding.copy() },
            )
        }
    }

    fun restoreAssets(assets: List<TtsVoiceReferenceAsset>) {
        val restored = assets
            .map { asset ->
                asset.copy(
                    name = asset.name.trim().ifBlank { "Unnamed voice asset" },
                    source = asset.source.trim(),
                    localPath = asset.localPath.trim(),
                    remoteUrl = asset.remoteUrl.trim(),
                    clips = asset.clips.map { clip -> clip.copy(localPath = clip.localPath.trim()) },
                    providerBindings = asset.providerBindings.map { binding ->
                        binding.copy(
                            model = binding.model.trim(),
                            voiceId = binding.voiceId.trim(),
                            displayName = binding.displayName.trim().ifBlank { binding.voiceId.trim() },
                        )
                    },
                )
            }
            .distinctBy { it.id }
            .sortedByDescending { it.createdAt }
        _assets.value = restored
        persist(restored)
        RuntimeLogRepository.append("TTS voice assets restored: count=${restored.size}")
    }

    private fun persist(assets: List<TtsVoiceReferenceAsset>) {
        runBlocking(Dispatchers.IO) {
            assetDao.replaceAll(assets.map(TtsVoiceReferenceAsset::toWriteModel))
        }
    }

    private suspend fun seedStorageIfNeeded() {
        if (assetDao.count() > 0) return
        val imported = runCatching {
            parseLegacyTtsVoiceAssets(legacyPreferences?.getString(KEY_ASSETS_JSON, null))
        }.onFailure { error ->
            RuntimeLogRepository.append("TTS voice assets legacy import failed: ${error.message ?: error.javaClass.simpleName}")
        }.getOrDefault(emptyList())
        if (imported.isNotEmpty()) {
            assetDao.replaceAll(imported.map(TtsVoiceReferenceAsset::toWriteModel))
            RuntimeLogRepository.append("TTS voice assets migrated from SharedPreferences: count=${imported.size}")
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String {
        val fallback = uri.lastPathSegment?.substringAfterLast('/') ?: "reference-audio"
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)?.takeIf { it.isNotBlank() }
                } else {
                    null
                }
            }
        }.getOrNull().orEmpty().ifBlank { fallback }
    }

    private fun inferSupportedExtension(sourceDisplayName: String, mimeType: String?): String {
        val normalizedMime = mimeType.orEmpty().lowercase()
        val fromMime = when (normalizedMime) {
            "audio/wav", "audio/x-wav", "audio/wave" -> "wav"
            "audio/mpeg", "audio/mp3" -> "mp3"
            "audio/mp4", "audio/x-m4a" -> "m4a"
            "audio/aac" -> "aac"
            else -> ""
        }
        val fromName = sourceDisplayName.substringAfterLast('.', "").lowercase()
        val resolved = when {
            fromMime.isNotBlank() -> fromMime
            fromName in setOf("wav", "mp3", "m4a", "aac") -> fromName
            else -> ""
        }
        return resolved.ifBlank {
            throw IllegalStateException("Unsupported audio format. Import WAV, MP3, M4A, or AAC reference audio.")
        }
    }

    private fun readAudioMetadata(context: Context, uri: Uri): ReferenceAudioMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.coerceAtLeast(0L)
                ?: 0L
            val sampleRateHz = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
                ?.toIntOrNull()
                ?.coerceAtLeast(0)
                ?: 0
            ReferenceAudioMetadata(durationMs = durationMs, sampleRateHz = sampleRateHz)
        } finally {
            runCatching { retriever.release() }
        }
    }

    data class ImportReferenceAudioResult(
        val asset: TtsVoiceReferenceAsset,
        val warning: String?,
    )

    private data class ReferenceAudioMetadata(
        val durationMs: Long,
        val sampleRateHz: Int,
    )
}

private object TtsVoiceAssetAggregateDaoPlaceholder {
    val instance = object : TtsVoiceAssetAggregateDao() {
        override fun observeAssetAggregates() = flowOf(emptyList<TtsVoiceAssetAggregate>())
        override suspend fun listAssetAggregates(): List<TtsVoiceAssetAggregate> = emptyList()
        override suspend fun upsertAssets(entities: List<TtsVoiceAssetEntity>) = Unit
        override suspend fun upsertClips(entities: List<com.astrbot.android.data.db.TtsVoiceClipEntity>) = Unit
        override suspend fun upsertProviderBindings(entities: List<com.astrbot.android.data.db.TtsVoiceProviderBindingEntity>) = Unit
        override suspend fun deleteMissingAssets(ids: List<String>) = Unit
        override suspend fun clearAssets() = Unit
        override suspend fun deleteClipsForAssets(assetIds: List<String>) = Unit
        override suspend fun deleteBindingsForAssets(assetIds: List<String>) = Unit
        override suspend fun count(): Int = 0
    }
}
