package com.astrbot.android.data

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.astrbot.android.model.ClonedVoiceBinding
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ProviderType
import com.astrbot.android.model.TtsVoiceReferenceClip
import com.astrbot.android.model.TtsVoiceReferenceAsset
import com.astrbot.android.runtime.RuntimeLogRepository
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

object TtsVoiceAssetRepository {
    private const val PREFS_NAME = "tts_voice_assets"
    private const val KEY_ASSETS_JSON = "assets_json"

    private var preferences: SharedPreferences? = null
    private val _assets = MutableStateFlow<List<TtsVoiceReferenceAsset>>(emptyList())

    val assets: StateFlow<List<TtsVoiceReferenceAsset>> = _assets.asStateFlow()

    fun initialize(context: Context) {
        if (preferences != null) return
        preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _assets.value = loadAssets().orEmpty()
        RuntimeLogRepository.append("TTS voice assets loaded: count=${_assets.value.size}")
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
            durationMs = primaryClip?.durationMs ?: 0L,
            sampleRateHz = primaryClip?.sampleRateHz ?: 0,
        ).copy(clips = allClips)
        _assets.value = _assets.value.filterNot { it.id == asset.id } + asset
        _assets.value = _assets.value.sortedByDescending { it.createdAt }
        persist()
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
        persist()
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
        persist()
    }

    fun renameBinding(assetId: String, bindingId: String, displayName: String) {
        _assets.value = _assets.value.map { asset ->
            if (asset.id != assetId) {
                asset
            } else {
                asset.copy(
                    providerBindings = asset.providerBindings.map { binding ->
                        if (binding.id == bindingId) binding.copy(displayName = displayName.trim().ifBlank { binding.voiceId }) else binding
                    },
                )
            }
        }
        persist()
    }

    fun deleteReferenceAsset(assetId: String) {
        _assets.value = _assets.value.filterNot { it.id == assetId }
        persist()
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
        persist()
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
        persist()
    }

    fun deleteBinding(assetId: String, bindingId: String) {
        _assets.value = _assets.value.map { asset ->
            if (asset.id != assetId) {
                asset
            } else {
                asset.copy(providerBindings = asset.providerBindings.filterNot { it.id == bindingId })
            }
        }
        persist()
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
        persist()
        RuntimeLogRepository.append("TTS voice assets restored: count=${restored.size}")
    }

    private fun loadAssets(): List<TtsVoiceReferenceAsset>? {
        val raw = preferences?.getString(KEY_ASSETS_JSON, null)?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        TtsVoiceReferenceAsset(
                            id = item.optString("id"),
                            name = item.optString("name"),
                            source = item.optString("source"),
                            localPath = item.optString("localPath"),
                            remoteUrl = item.optString("remoteUrl"),
                            durationMs = item.optLong("durationMs"),
                            sampleRateHz = item.optInt("sampleRateHz"),
                            createdAt = item.optLong("createdAt"),
                            clips = buildList {
                                val clipsArray = item.optJSONArray("clips")
                                if (clipsArray != null && clipsArray.length() > 0) {
                                    for (clipIndex in 0 until clipsArray.length()) {
                                        val clip = clipsArray.optJSONObject(clipIndex) ?: continue
                                        add(
                                            TtsVoiceReferenceClip(
                                                id = clip.optString("id"),
                                                localPath = clip.optString("localPath"),
                                                durationMs = clip.optLong("durationMs"),
                                                sampleRateHz = clip.optInt("sampleRateHz"),
                                                createdAt = clip.optLong("createdAt"),
                                            ),
                                        )
                                    }
                                } else if (item.optString("localPath").isNotBlank()) {
                                    add(
                                        TtsVoiceReferenceClip(
                                            id = "${item.optString("id")}-legacy",
                                            localPath = item.optString("localPath"),
                                            durationMs = item.optLong("durationMs"),
                                            sampleRateHz = item.optInt("sampleRateHz"),
                                            createdAt = item.optLong("createdAt"),
                                        ),
                                    )
                                }
                            },
                            providerBindings = buildList {
                                val bindings = item.optJSONArray("providerBindings") ?: JSONArray()
                                for (bindingIndex in 0 until bindings.length()) {
                                    val binding = bindings.optJSONObject(bindingIndex) ?: continue
                                    val providerType = runCatching {
                                        ProviderType.valueOf(binding.optString("providerType"))
                                    }.getOrDefault(ProviderType.OPENAI_TTS)
                                    add(
                                        ClonedVoiceBinding(
                                            id = binding.optString("id"),
                                            providerId = binding.optString("providerId"),
                                            providerType = providerType,
                                            model = binding.optString("model"),
                                            voiceId = binding.optString("voiceId"),
                                            displayName = binding.optString("displayName"),
                                            createdAt = binding.optLong("createdAt"),
                                            lastVerifiedAt = binding.optLong("lastVerifiedAt"),
                                            status = binding.optString("status").ifBlank { "ready" },
                                        ),
                                    )
                                }
                            },
                        ),
                    )
                }
            }
        }.onFailure { error ->
            RuntimeLogRepository.append("TTS voice assets load failed: ${error.message ?: error.javaClass.simpleName}")
        }.getOrNull()
    }

    private fun persist() {
        val json = JSONArray().apply {
            _assets.value.forEach { asset ->
                put(
                    JSONObject().apply {
                        put("id", asset.id)
                        put("name", asset.name)
                        put("source", asset.source)
                        put("localPath", asset.localPath)
                        put("remoteUrl", asset.remoteUrl)
                        put("durationMs", asset.durationMs)
                        put("sampleRateHz", asset.sampleRateHz)
                        put("createdAt", asset.createdAt)
                        put(
                            "clips",
                            JSONArray().apply {
                                asset.clips.forEach { clip ->
                                    put(
                                        JSONObject().apply {
                                            put("id", clip.id)
                                            put("localPath", clip.localPath)
                                            put("durationMs", clip.durationMs)
                                            put("sampleRateHz", clip.sampleRateHz)
                                            put("createdAt", clip.createdAt)
                                        },
                                    )
                                }
                            },
                        )
                        put(
                            "providerBindings",
                            JSONArray().apply {
                                asset.providerBindings.forEach { binding ->
                                    put(
                                        JSONObject().apply {
                                            put("id", binding.id)
                                            put("providerId", binding.providerId)
                                            put("providerType", binding.providerType.name)
                                            put("model", binding.model)
                                            put("voiceId", binding.voiceId)
                                            put("displayName", binding.displayName)
                                            put("createdAt", binding.createdAt)
                                            put("lastVerifiedAt", binding.lastVerifiedAt)
                                            put("status", binding.status)
                                        },
                                    )
                                }
                            },
                        )
                    },
                )
            }
        }
        preferences?.edit()?.putString(KEY_ASSETS_JSON, json.toString())?.apply()
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
            ReferenceAudioMetadata(
                durationMs = durationMs,
                sampleRateHz = sampleRateHz,
            )
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
