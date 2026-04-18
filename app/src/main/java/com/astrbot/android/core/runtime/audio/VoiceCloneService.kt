package com.astrbot.android.core.runtime.audio

import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ProviderType
import com.astrbot.android.model.TtsVoiceReferenceAsset
import com.astrbot.android.core.common.logging.RuntimeLogRepository
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID
import org.json.JSONObject

object VoiceCloneService {
    private const val CLIP_GAP_MS = 300

    fun cloneVoice(
        provider: ProviderProfile,
        asset: TtsVoiceReferenceAsset,
        displayName: String,
    ): String {
        return when (provider.providerType) {
            ProviderType.BAILIAN_TTS -> cloneQwenVoice(provider, asset, displayName)
            ProviderType.MINIMAX_TTS -> cloneMiniMaxVoice(provider, asset, displayName)
            else -> throw IllegalStateException("Voice cloning is not supported for ${provider.providerType.name}.")
        }
    }

    private fun cloneQwenVoice(
        provider: ProviderProfile,
        asset: TtsVoiceReferenceAsset,
        displayName: String,
    ): String {
        require(provider.apiKey.isNotBlank()) { "Provider API key is empty." }
        require(provider.model.trim().startsWith("qwen3-tts-vc")) {
            "Qwen voice cloning requires a qwen3-tts-vc-* model."
        }
        val referenceAudio = resolveReferenceAudio(asset)
        val resolvedTargetModel = normalizeQwenVoiceCloneTargetModel(provider.model)
        val payload = JSONObject().apply {
            put("model", "qwen-voice-enrollment")
            put(
                "input",
                JSONObject().apply {
                    put("action", "create")
                    put("target_model", resolvedTargetModel)
                    put("preferred_name", sanitizeQwenPreferredName(displayName, asset.id))
                    put(
                        "audio",
                        JSONObject().apply {
                            put("data", "data:${referenceAudio.mimeType};base64,${Base64.getEncoder().encodeToString(referenceAudio.bytes)}")
                        },
                    )
                    inferReferenceLanguage(referenceAudio.fileName)?.let { put("language", it) }
                },
            )
        }
        val endpoint = provider.baseUrl.trimEnd('/') + "/services/audio/tts/customization"
        val connection = openJsonConnection(endpoint).apply {
            setRequestProperty("Authorization", "Bearer ${provider.apiKey}")
        }
        return try {
            executeJsonRequest(connection, payload) { body ->
                val json = JSONObject(body)
                val output = json.optJSONObject("output") ?: json
                output.optString("voice_id")
                    .ifBlank { output.optString("voice") }
                    .ifBlank { output.optString("id") }
                    .ifBlank {
                        throw IllegalStateException("Qwen voice clone response does not contain a voice_id.")
                    }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun cloneMiniMaxVoice(
        provider: ProviderProfile,
        asset: TtsVoiceReferenceAsset,
        displayName: String,
    ): String {
        require(provider.apiKey.isNotBlank()) { "Provider API key is empty." }
        val referenceAudio = resolveReferenceAudio(asset)
        val fileId = uploadMiniMaxFile(
            baseUrl = provider.baseUrl,
            apiKey = provider.apiKey,
            purpose = "voice_clone",
            fileName = referenceAudio.fileName,
            mimeType = referenceAudio.mimeType,
            bytes = referenceAudio.bytes,
        )
        val voiceId = sanitizeMiniMaxVoiceId(displayName, asset.id)
        val normalizedFileId: Any = fileId.toLongOrNull() ?: fileId
        val payload = JSONObject().apply {
            put("file_id", normalizedFileId)
            put("voice_id", voiceId)
            put("text", "感谢试用音色克隆。")
            put("model", provider.model.trim())
            put("need_noise_reduction", false)
            put("need_volume_normalization", false)
        }
        val endpoint = normalizeMiniMaxApiBase(provider.baseUrl) + "/voice_clone"
        val connection = openJsonConnection(endpoint).apply {
            setRequestProperty("Authorization", "Bearer ${provider.apiKey}")
        }
        return try {
            executeJsonRequest(connection, payload) { body ->
                RuntimeLogRepository.append("MiniMax voice clone response: ${body.ifBlank { "-" }}")
                val json = JSONObject(body)
                val baseResp = json.optJSONObject("base_resp")
                val statusCode = baseResp?.optInt("status_code", 0) ?: 0
                if (statusCode != 0) {
                    throw IllegalStateException(
                        "MiniMax voice clone failed: ${baseResp?.optString("status_msg").orEmpty().ifBlank { "status=$statusCode" }}",
                    )
                }
                json.optJSONObject("data")
                    ?.optString("voice_id")
                    ?.takeIf { it.isNotBlank() }
                    ?: voiceId
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun uploadMiniMaxFile(
        baseUrl: String,
        apiKey: String,
        purpose: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
    ): String {
        val boundary = "AstrBotBoundary${System.currentTimeMillis()}"
        val connection = openMultipartConnection("${normalizeMiniMaxApiBase(baseUrl)}/files/upload", boundary).apply {
            setRequestProperty("Authorization", "Bearer $apiKey")
        }
        return try {
            connection.outputStream.use { output ->
                writeMultipartText(output, boundary, "purpose", purpose)
                writeMultipartFile(output, boundary, "file", fileName, mimeType, bytes)
                finishMultipart(output, boundary)
            }
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IllegalStateException("HTTP $responseCode while uploading reference audio to MiniMax.")
            }
            val body = connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            RuntimeLogRepository.append("MiniMax file upload response: ${body.ifBlank { "-" }}")
            val json = JSONObject(body)
            val baseResp = json.optJSONObject("base_resp")
            val statusCode = baseResp?.optInt("status_code", 0) ?: 0
            if (statusCode != 0) {
                throw IllegalStateException(
                    "MiniMax file upload failed: ${baseResp?.optString("status_msg").orEmpty().ifBlank { "status=$statusCode" }}",
                )
            }
            extractMiniMaxFileId(json)
                ?: throw IllegalStateException("MiniMax file upload did not return file_id. body=$body")
        } finally {
            connection.disconnect()
        }
    }

    private fun extractMiniMaxFileId(json: JSONObject): String? {
        val directCandidates = listOf(
            json.opt("file_id"),
            json.optJSONObject("file")?.opt("file_id"),
            json.optJSONObject("data")?.opt("file_id"),
            json.optJSONObject("data")?.optJSONObject("file")?.opt("file_id"),
        )
        directCandidates.forEach { candidate ->
            candidate?.toString()?.takeIf { it.isNotBlank() && it != "null" }?.let { return it }
        }
        val filesArray = json.optJSONArray("files")
        if (filesArray != null) {
            for (index in 0 until filesArray.length()) {
                val fileId = filesArray.optJSONObject(index)
                    ?.opt("file_id")
                    ?.toString()
                    ?.takeIf { it.isNotBlank() && it != "null" }
                if (fileId != null) return fileId
            }
        }
        return null
    }

    private fun resolveReferenceAudio(asset: TtsVoiceReferenceAsset): ReferenceAudioData {
        mergeLocalClipsToWav(asset)?.let { return it }
        val preferredLocalPath = asset.clips
            .filter { it.localPath.isNotBlank() }
            .maxByOrNull { it.durationMs }
            ?.localPath
            ?: asset.localPath.takeIf { it.isNotBlank() }
        val bytes = when {
            preferredLocalPath != null -> resolveLocalBytes(preferredLocalPath)
            asset.remoteUrl.isNotBlank() -> downloadBytes(asset.remoteUrl)
            else -> throw IllegalStateException("Reference audio is missing.")
        }
        val fileName = preferredLocalPath?.let { File(it).name }
            ?: asset.remoteUrl.substringAfterLast('/').substringBefore('?').ifBlank { "${asset.name}.wav" }
        val mimeType = inferAudioMimeType(fileName)
        return ReferenceAudioData(
            fileName = fileName,
            mimeType = mimeType,
            bytes = bytes,
        )
    }

    private fun mergeLocalClipsToWav(asset: TtsVoiceReferenceAsset): ReferenceAudioData? {
        val localClips = asset.clips
            .mapNotNull { it.localPath.takeIf(String::isNotBlank) }
            .distinct()
        if (localClips.size < 2) return null
        val wavClips = localClips.mapNotNull { path ->
            val normalized = normalizeLocalFile(path)
            if (!normalized.exists()) return null
            parseWavClip(normalized)
        }
        if (wavClips.size != localClips.size) return null
        val first = wavClips.first()
        if (wavClips.any { it.sampleRate != first.sampleRate || it.channels != first.channels || it.bitsPerSample != first.bitsPerSample }) {
            return null
        }
        val bytesPerFrame = (first.channels * first.bitsPerSample) / 8
        if (bytesPerFrame <= 0) return null
        val gapFrames = ((first.sampleRate * CLIP_GAP_MS) / 1000.0).toInt().coerceAtLeast(0)
        val gapBytes = ByteArray(gapFrames * bytesPerFrame)
        val mergedPcm = buildList<ByteArray> {
            wavClips.forEachIndexed { index, clip ->
                add(clip.pcmData)
                if (index != wavClips.lastIndex && gapBytes.isNotEmpty()) {
                    add(gapBytes)
                }
            }
        }
        val totalPcmSize = mergedPcm.sumOf { it.size }
        val mergedBytes = buildWavFile(
            sampleRate = first.sampleRate,
            channels = first.channels,
            bitsPerSample = first.bitsPerSample,
            pcmChunks = mergedPcm,
            totalPcmSize = totalPcmSize,
        )
        return ReferenceAudioData(
            fileName = "${asset.name.ifBlank { asset.id }}-merged.wav",
            mimeType = "audio/wav",
            bytes = mergedBytes,
        )
    }

    private fun resolveLocalBytes(path: String): ByteArray {
        return normalizeLocalFile(path).takeIf { it.exists() }?.readBytes()
            ?: throw IllegalStateException("Reference audio file does not exist.")
    }

    private fun normalizeLocalFile(path: String): File {
        val normalized = path.trim()
        return if (normalized.startsWith("file://", ignoreCase = true)) {
            File(URI(normalized))
        } else {
            File(normalized)
        }
    }

    private fun downloadBytes(url: String): ByteArray {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 120_000
        }
        return try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IllegalStateException("HTTP $responseCode while downloading reference audio.")
            }
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private fun inferAudioMimeType(fileName: String): String {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "wav" -> "audio/wav"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            else -> "audio/wav"
        }
    }

    private fun sanitizeVoiceId(
        displayName: String,
        assetId: String,
    ): String {
        val base = displayName.trim().ifBlank { assetId }.lowercase()
            .replace(Regex("[^a-z0-9_-]+"), "-")
            .trim('-')
            .ifBlank { "voice-${UUID.randomUUID()}" }
        return if (base.length <= 48) base else base.take(48)
    }

    private fun sanitizeMiniMaxVoiceId(
        displayName: String,
        assetId: String,
    ): String {
        var base = sanitizeVoiceId(displayName, assetId)
        if (base.firstOrNull()?.isLetter() != true) {
            base = "voice-$base"
        }
        if (base.length < 8) {
            base = (base + "-sample").take(8)
        }
        return base.trimEnd('-', '_').take(256)
    }

    private fun sanitizeQwenPreferredName(
        displayName: String,
        assetId: String,
    ): String {
        val base = displayName.trim().ifBlank { assetId }
            .replace(Regex("[^A-Za-z0-9_]+"), "_")
            .trim('_')
            .ifBlank { "voice_${UUID.randomUUID().toString().take(8)}" }
        return if (base.length <= 16) base else base.take(16)
    }

    private fun parseWavClip(file: File): ParsedWavClip? {
        val bytes = file.readBytes()
        if (bytes.size < 44) return null
        if (bytes.copyOfRange(0, 4).toAsciiString() != "RIFF") return null
        if (bytes.copyOfRange(8, 12).toAsciiString() != "WAVE") return null
        var offset = 12
        var channels = 0
        var sampleRate = 0
        var bitsPerSample = 0
        var pcmData: ByteArray? = null
        while (offset + 8 <= bytes.size) {
            val chunkId = bytes.copyOfRange(offset, offset + 4).toAsciiString()
            val chunkSize = readLittleEndianInt(bytes, offset + 4)
            val chunkDataStart = offset + 8
            val nextOffset = chunkDataStart + chunkSize + (chunkSize % 2)
            if (nextOffset > bytes.size) return null
            when (chunkId) {
                "fmt " -> {
                    if (chunkSize < 16) return null
                    val audioFormat = readLittleEndianShort(bytes, chunkDataStart)
                    if (audioFormat != 1) return null
                    channels = readLittleEndianShort(bytes, chunkDataStart + 2)
                    sampleRate = readLittleEndianInt(bytes, chunkDataStart + 4)
                    bitsPerSample = readLittleEndianShort(bytes, chunkDataStart + 14)
                }
                "data" -> {
                    pcmData = bytes.copyOfRange(chunkDataStart, chunkDataStart + chunkSize)
                }
            }
            offset = nextOffset
        }
        val data = pcmData ?: return null
        if (channels <= 0 || sampleRate <= 0 || bitsPerSample <= 0) return null
        return ParsedWavClip(
            sampleRate = sampleRate,
            channels = channels,
            bitsPerSample = bitsPerSample,
            pcmData = data,
        )
    }

    private fun buildWavFile(
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int,
        pcmChunks: List<ByteArray>,
        totalPcmSize: Int,
    ): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val totalSize = 44 + totalPcmSize
        val out = ByteArray(totalSize)
        writeAscii(out, 0, "RIFF")
        writeLittleEndianInt(out, 4, totalSize - 8)
        writeAscii(out, 8, "WAVE")
        writeAscii(out, 12, "fmt ")
        writeLittleEndianInt(out, 16, 16)
        writeLittleEndianShort(out, 20, 1)
        writeLittleEndianShort(out, 22, channels)
        writeLittleEndianInt(out, 24, sampleRate)
        writeLittleEndianInt(out, 28, byteRate)
        writeLittleEndianShort(out, 32, blockAlign)
        writeLittleEndianShort(out, 34, bitsPerSample)
        writeAscii(out, 36, "data")
        writeLittleEndianInt(out, 40, totalPcmSize)
        var offset = 44
        pcmChunks.forEach { chunk ->
            chunk.copyInto(out, offset)
            offset += chunk.size
        }
        return out
    }

    private fun ByteArray.toAsciiString(): String = String(this, StandardCharsets.US_ASCII)

    private fun readLittleEndianInt(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)
    }

    private fun readLittleEndianShort(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)
    }

    private fun writeAscii(bytes: ByteArray, offset: Int, value: String) {
        value.toByteArray(StandardCharsets.US_ASCII).copyInto(bytes, offset)
    }

    private fun writeLittleEndianInt(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xff).toByte()
        bytes[offset + 1] = ((value shr 8) and 0xff).toByte()
        bytes[offset + 2] = ((value shr 16) and 0xff).toByte()
        bytes[offset + 3] = ((value shr 24) and 0xff).toByte()
    }

    private fun writeLittleEndianShort(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xff).toByte()
        bytes[offset + 1] = ((value shr 8) and 0xff).toByte()
    }

    private fun normalizeQwenVoiceCloneTargetModel(model: String): String {
        return when (model.trim()) {
            "qwen3-tts-vc" -> "qwen3-tts-vc-2026-01-22"
            "qwen3-tts-vc-realtime" -> "qwen3-tts-vc-realtime-2026-01-15"
            else -> model.trim()
        }
    }

    private fun inferReferenceLanguage(fileName: String): String? {
        val normalized = fileName.lowercase()
        return when {
            normalized.endsWith(".wav") || normalized.endsWith(".mp3") || normalized.endsWith(".m4a") || normalized.endsWith(".aac") -> "zh"
            else -> null
        }
    }

    private fun normalizeMiniMaxApiBase(baseUrl: String): String {
        val normalized = baseUrl.trimEnd('/')
        return if (normalized.endsWith("/v1")) normalized else normalized.substringBefore("/t2a_v2").trimEnd('/') + "/v1"
    }

    private fun openJsonConnection(endpoint: String): HttpURLConnection {
        RuntimeLogRepository.append("Voice clone request: endpoint=$endpoint")
        return (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 120_000
            doInput = true
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
    }

    private fun executeJsonRequest(
        connection: HttpURLConnection,
        payload: JSONObject,
        parser: (String) -> String,
    ): String {
        connection.outputStream.use { output ->
            output.write(payload.toString().toByteArray(StandardCharsets.UTF_8))
        }
        val responseCode = connection.responseCode
        val body = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader(StandardCharsets.UTF_8)
            ?.use { it.readText() }
            .orEmpty()
        if (responseCode !in 200..299) {
            RuntimeLogRepository.append(
                "Voice clone HTTP error: code=$responseCode body=${body.ifBlank { "-" }}",
            )
            throw IllegalStateException("HTTP $responseCode: $body")
        }
        return parser(body)
    }

    private fun openMultipartConnection(
        endpoint: String,
        boundary: String,
    ): HttpURLConnection {
        return (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 120_000
            doInput = true
            doOutput = true
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setRequestProperty("Accept", "application/json")
        }
    }

    private fun writeMultipartText(
        output: java.io.OutputStream,
        boundary: String,
        fieldName: String,
        value: String,
    ) {
        output.write("--$boundary\r\n".toByteArray(StandardCharsets.UTF_8))
        output.write("Content-Disposition: form-data; name=\"$fieldName\"\r\n\r\n".toByteArray(StandardCharsets.UTF_8))
        output.write(value.toByteArray(StandardCharsets.UTF_8))
        output.write("\r\n".toByteArray(StandardCharsets.UTF_8))
    }

    private fun writeMultipartFile(
        output: java.io.OutputStream,
        boundary: String,
        fieldName: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
    ) {
        output.write("--$boundary\r\n".toByteArray(StandardCharsets.UTF_8))
        output.write(
            "Content-Disposition: form-data; name=\"$fieldName\"; filename=\"$fileName\"\r\n".toByteArray(StandardCharsets.UTF_8),
        )
        output.write("Content-Type: $mimeType\r\n\r\n".toByteArray(StandardCharsets.UTF_8))
        output.write(bytes)
        output.write("\r\n".toByteArray(StandardCharsets.UTF_8))
    }

    private fun finishMultipart(
        output: java.io.OutputStream,
        boundary: String,
    ) {
        output.write("--$boundary--\r\n".toByteArray(StandardCharsets.UTF_8))
    }

    private data class ReferenceAudioData(
        val fileName: String,
        val mimeType: String,
        val bytes: ByteArray,
    )

    private data class ParsedWavClip(
        val sampleRate: Int,
        val channels: Int,
        val bitsPerSample: Int,
        val pcmData: ByteArray,
    )
}
