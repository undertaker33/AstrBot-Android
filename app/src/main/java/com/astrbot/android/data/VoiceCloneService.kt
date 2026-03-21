package com.astrbot.android.data

import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ProviderType
import com.astrbot.android.model.TtsVoiceReferenceAsset
import com.astrbot.android.runtime.RuntimeLogRepository
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID
import org.json.JSONObject

object VoiceCloneService {
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
            apiKey = provider.apiKey,
            purpose = "voice_clone",
            fileName = referenceAudio.fileName,
            mimeType = referenceAudio.mimeType,
            bytes = referenceAudio.bytes,
        )
        val voiceId = sanitizeVoiceId(displayName, asset.id)
        val payload = JSONObject().apply {
            put("file_id", fileId)
            put("voice_id", voiceId)
            put("text", "你好，这是一段用于校验音色克隆链路的测试语音。")
            put("model", provider.model.trim())
        }
        val endpoint = normalizeMiniMaxApiBase(provider.baseUrl) + "/voice_clone"
        val connection = openJsonConnection(endpoint).apply {
            setRequestProperty("Authorization", "Bearer ${provider.apiKey}")
        }
        return try {
            executeJsonRequest(connection, payload) { body ->
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
        apiKey: String,
        purpose: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
    ): String {
        val boundary = "AstrBotBoundary${System.currentTimeMillis()}"
        val connection = openMultipartConnection("https://api.minimax.io/v1/files/upload", boundary).apply {
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
            JSONObject(body).optJSONObject("file")
                ?.optString("file_id")
                ?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("MiniMax file upload did not return file_id.")
        } finally {
            connection.disconnect()
        }
    }

    private fun resolveReferenceAudio(asset: TtsVoiceReferenceAsset): ReferenceAudioData {
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

    private fun resolveLocalBytes(path: String): ByteArray {
        val normalized = path.trim()
        if (normalized.startsWith("file://", ignoreCase = true)) {
            return File(URI(normalized)).readBytes()
        }
        return File(normalized).takeIf { it.exists() }?.readBytes()
            ?: throw IllegalStateException("Reference audio file does not exist.")
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
}
