package com.astrbot.android.data

import android.content.Context
import android.content.res.AssetManager
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Base64
import com.astrbot.android.model.ConversationAttachment
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.runtime.RuntimeLogRepository
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineParaformerModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.getOfflineTtsConfig
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object SherpaOnnxBridge {
    private const val TARGET_STT_SAMPLE_RATE = 16_000
    private const val TARGET_STT_FEATURE_DIM = 80
    private const val TTS_LEADING_SILENCE_MS = 1200
    private const val TTS_FADE_IN_MS = 10
    private const val TTS_FADE_OUT_MS = 14
    private const val MATCHA_MALE_PITCH_SCALE = 0.84f
    private const val MATCHA_FEMALE_PITCH_SCALE = 1.08f

    private val recognizerCache = ConcurrentHashMap<String, OfflineRecognizer>()
    private val ttsCache = ConcurrentHashMap<String, OfflineTts>()

    private val bracketPatterns = listOf(
        Regex("\\(([^()]*)\\)"),
        Regex("（[^（）]*）"),
        Regex("\\[([^\\[\\]]*)\\]"),
        Regex("【[^【】]*】"),
    )

    @Volatile
    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun transcribeAudio(
        provider: ProviderProfile,
        attachment: ConversationAttachment,
    ): String {
        ensureInitialized()
        ensureFrameworkReady()
        ensureSttReady()
        val context = requireContext()
        val pcm = decodeAttachmentToMonoPcm(context, attachment)
        val recognizer = recognizerCache.getOrPut("stt") { buildRecognizer(context) }
        val stream = recognizer.createStream()
        return try {
            stream.acceptWaveform(pcm.samples, pcm.sampleRate)
            recognizer.decode(stream)
            recognizer.getResult(stream).text.trim()
        } finally {
            stream.release()
        }
    }

    fun synthesizeSpeech(
        provider: ProviderProfile,
        text: String,
        voiceId: String,
        readBracketedContent: Boolean,
    ): ConversationAttachment {
        ensureInitialized()
        ensureFrameworkReady()
        ensureTtsReady(provider.model)

        val context = requireContext()
        val normalizedModel = provider.model.trim().lowercase()
        val preparedText = prepareLocalTtsText(text, readBracketedContent)
        check(preparedText.isNotBlank()) { "TTS text is empty after normalization." }

        val runtimeConfig = buildTtsRuntimeConfig(context, normalizedModel)
        val speakerId = resolveSpeakerId(normalizedModel, voiceId)
        RuntimeLogRepository.append(
            "Sherpa ONNX TTS generate: model=$normalizedModel speakerId=$speakerId chars=${preparedText.length}",
        )

        val tts = getOrCreateTts(normalizedModel, runtimeConfig)
        return try {
            synthesizeWithCallback(
                model = normalizedModel,
                tts = tts,
                text = preparedText,
                speakerId = speakerId,
                voiceId = voiceId,
            )
        } catch (error: Throwable) {
            resetTts(model = normalizedModel, tts = tts)
            throw error
        }
    }

    fun isFrameworkReady(): Boolean {
        val context = appContext ?: return false
        return SherpaOnnxAssetManager.frameworkState(context).installed
    }

    fun isSttReady(): Boolean {
        val context = appContext ?: return false
        return SherpaOnnxAssetManager.sttState(context).installed
    }

    fun isTtsReady(model: String): Boolean {
        val context = appContext ?: return false
        val state = SherpaOnnxAssetManager.ttsState(context)
        return when (model.trim().lowercase()) {
            "kokoro" -> state.kokoro.installed
            "matcha" -> state.matcha.installed
            else -> false
        }
    }

    private fun buildRecognizer(context: Context): OfflineRecognizer {
        val sttDir = SherpaOnnxAssetManager.sttDir(context)
        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(TARGET_STT_SAMPLE_RATE, TARGET_STT_FEATURE_DIM, 0.0f),
            modelConfig = OfflineModelConfig(
                paraformer = OfflineParaformerModelConfig(
                    model = File(sttDir, "model.int8.onnx").absolutePath,
                ),
                numThreads = 2,
                debug = false,
                provider = "cpu",
                modelType = "paraformer",
                tokens = File(sttDir, "tokens.txt").absolutePath,
            ),
            decodingMethod = "greedy_search",
            maxActivePaths = 4,
        )
        return OfflineRecognizer(null, config)
    }

    private fun buildTtsRuntimeConfig(
        context: Context,
        model: String,
    ): TtsRuntimeConfig {
        return when (model) {
            "kokoro" -> TtsRuntimeConfig(
                config = buildKokoroConfig(context),
                assetManager = null,
            )
            "matcha" -> TtsRuntimeConfig(
                config = buildMatchaConfig(context),
                assetManager = null,
            )
            else -> throw IllegalStateException("Unsupported Sherpa ONNX TTS model: $model")
        }
    }

    private fun getOrCreateTts(
        model: String,
        runtimeConfig: TtsRuntimeConfig,
    ): OfflineTts {
        return ttsCache[model] ?: synchronized(ttsCache) {
            ttsCache[model] ?: run {
                RuntimeLogRepository.append("Sherpa ONNX TTS init start: model=$model")
                val created = OfflineTts(runtimeConfig.assetManager, runtimeConfig.config)
                RuntimeLogRepository.append("Sherpa ONNX TTS init done: model=$model")
                ttsCache[model] = created
                created
            }
        }
    }

    private fun buildKokoroConfig(context: Context): OfflineTtsConfig {
        val kokoroDir = SherpaOnnxAssetManager.kokoroDir(context)
        return getOfflineTtsConfig(
            modelDir = kokoroDir.absolutePath,
            modelName = "model.int8.onnx",
            acousticModelName = "",
            vocoder = "",
            voices = "voices.bin",
            lexicon = listOf(
                File(kokoroDir, "lexicon-us-en.txt").absolutePath,
                File(kokoroDir, "lexicon-zh.txt").absolutePath,
            ).joinToString(","),
            dataDir = File(kokoroDir, "espeak-ng-data").absolutePath,
            dictDir = File(kokoroDir, "dict").absolutePath,
            ruleFsts = listOf(
                File(kokoroDir, "phone-zh.fst").absolutePath,
                File(kokoroDir, "date-zh.fst").absolutePath,
                File(kokoroDir, "number-zh.fst").absolutePath,
            ).joinToString(","),
            ruleFars = "",
            numThreads = 2,
            isKitten = false,
        )
    }

    private fun buildMatchaConfig(context: Context): OfflineTtsConfig {
        val matchaDir = SherpaOnnxAssetManager.matchaDir(context)
        return getOfflineTtsConfig(
            modelDir = matchaDir.absolutePath,
            modelName = "",
            acousticModelName = "model-steps-3.onnx",
            vocoder = File(matchaDir, "vocos-22khz-univ.onnx").absolutePath,
            voices = "",
            lexicon = File(matchaDir, "lexicon.txt").absolutePath,
            dataDir = "",
            dictDir = File(matchaDir, "dict").absolutePath,
            ruleFsts = listOf(
                File(matchaDir, "phone.fst").absolutePath,
                File(matchaDir, "date.fst").absolutePath,
                File(matchaDir, "number.fst").absolutePath,
            ).joinToString(","),
            ruleFars = "",
            numThreads = 2,
            isKitten = false,
        )
    }

    private fun resolveSpeakerId(
        model: String,
        voiceId: String,
    ): Int {
        val normalizedModel = model.trim().lowercase()
        val resolvedVoice = OnDeviceTtsCatalog.voice(normalizedModel, voiceId)
        if (resolvedVoice?.speakerId != null) {
            return resolvedVoice.speakerId
        }
        return OnDeviceTtsCatalog.defaultVoice(normalizedModel)?.speakerId ?: 0
    }

    private fun resetTts(
        model: String,
        tts: OfflineTts,
    ) {
        ttsCache.remove(model)
        runCatching { tts.release() }
    }

    private fun buildAudioAttachment(generated: GeneratedAudio): ConversationAttachment {
        val wavBytes = generated.toWavBytes()
        return ConversationAttachment(
            id = UUID.randomUUID().toString(),
            type = "audio",
            mimeType = "audio/wav",
            fileName = "tts-${System.currentTimeMillis()}.wav",
            base64Data = java.util.Base64.getEncoder().encodeToString(wavBytes),
        )
    }

    private fun synthesizeWithCallback(
        model: String,
        tts: OfflineTts,
        text: String,
        speakerId: Int,
        voiceId: String,
    ): ConversationAttachment {
        val sampleRate = tts.sampleRate()
        val pcmBytes = ByteArrayOutputStream()
        var callbackChunks = 0
        RuntimeLogRepository.append(
            "Sherpa ONNX TTS callback start: sampleRate=$sampleRate speakerId=$speakerId chars=${text.length}",
        )
        tts.generateWithCallback(
            text = text,
            sid = speakerId,
            speed = 1.0f,
        ) { samples ->
            callbackChunks += 1
            if (callbackChunks == 1) {
                RuntimeLogRepository.append(
                    "Sherpa ONNX TTS callback chunk: samples=${samples.size}",
                )
            }
            pcmBytes.write(floatArrayToPcmBytes(samples))
            1
        }
        RuntimeLogRepository.append(
            "Sherpa ONNX TTS callback done: chunks=$callbackChunks bytes=${pcmBytes.size()}",
        )
        check(pcmBytes.size() > 0) { "Sherpa ONNX TTS generated empty audio." }
        val processedPcm = applyVoiceProfile(
            model = model,
            voiceId = voiceId,
            sampleRate = sampleRate,
            pcmBytes = pcmBytes.toByteArray(),
        )
        return buildAudioAttachment(sampleRate, processedPcm)
    }

    private fun applyVoiceProfile(
        model: String,
        voiceId: String,
        sampleRate: Int,
        pcmBytes: ByteArray,
    ): ByteArray {
        if (model != "matcha") return pcmBytes
        val pitchScale = when (voiceId.trim()) {
            TtsVoiceCatalog.MATCHA_MALE_VOICE_ID -> MATCHA_MALE_PITCH_SCALE
            TtsVoiceCatalog.MATCHA_FEMALE_VOICE_ID,
            "",
            -> MATCHA_FEMALE_PITCH_SCALE
            else -> MATCHA_FEMALE_PITCH_SCALE
        }
        return shiftMonoPcmPitch(
            pcmBytes = pcmBytes,
            sampleRate = sampleRate,
            pitchScale = pitchScale,
        )
    }

    private fun shiftMonoPcmPitch(
        pcmBytes: ByteArray,
        sampleRate: Int,
        pitchScale: Float,
    ): ByteArray {
        if (pcmBytes.size < 4 || pitchScale <= 0f || pitchScale == 1f) return pcmBytes
        val source = ShortArray(pcmBytes.size / 2)
        ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(source)
        val pitchedLength = (source.size / pitchScale).toInt().coerceAtLeast(1)
        val pitched = ShortArray(pitchedLength)
        for (index in pitched.indices) {
            pitched[index] = interpolateSample(source, index * pitchScale)
        }
        val restored = ShortArray(source.size)
        val restoreRatio = if (restored.size <= 1) 1f else (pitched.size - 1).toFloat() / (restored.size - 1).toFloat()
        for (index in restored.indices) {
            restored[index] = interpolateSample(pitched, index * restoreRatio)
        }
        val adjusted = if (pitchScale < 1f) {
            applyLowShelf(restored, sampleRate)
        } else {
            restored
        }
        return shortArrayToPcmBytes(adjusted)
    }

    private fun interpolateSample(
        samples: ShortArray,
        position: Float,
    ): Short {
        if (samples.isEmpty()) return 0
        val safePosition = position.coerceIn(0f, (samples.lastIndex).toFloat())
        val leftIndex = safePosition.toInt()
        val rightIndex = (leftIndex + 1).coerceAtMost(samples.lastIndex)
        if (leftIndex == rightIndex) return samples[leftIndex]
        val fraction = safePosition - leftIndex
        val interpolated = samples[leftIndex] * (1f - fraction) + samples[rightIndex] * fraction
        return interpolated.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }

    private fun applyLowShelf(
        samples: ShortArray,
        sampleRate: Int,
    ): ShortArray {
        if (samples.isEmpty()) return samples
        val blend = (140f / sampleRate.coerceAtLeast(1)).coerceIn(0.03f, 0.18f)
        var previous = samples.first().toFloat()
        return ShortArray(samples.size) { index ->
            previous += (samples[index] - previous) * blend
            previous.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    private fun shortArrayToPcmBytes(samples: ShortArray): ByteArray {
        val bytes = ByteArray(samples.size * 2)
        for (index in samples.indices) {
            val sample = samples[index]
            bytes[index * 2] = sample.toInt().toByte()
            bytes[index * 2 + 1] = (sample.toInt() shr 8).toByte()
        }
        return bytes
    }

    private fun floatArrayToPcmBytes(samples: FloatArray): ByteArray {
        val pcmBytes = ByteArray(samples.size * 2)
        for (index in samples.indices) {
            val clamped = samples[index].coerceIn(-1.0f, 1.0f)
            val sample = (clamped * Short.MAX_VALUE).toInt().toShort()
            pcmBytes[index * 2] = sample.toInt().toByte()
            pcmBytes[index * 2 + 1] = (sample.toInt() shr 8).toByte()
        }
        return pcmBytes
    }

    private fun buildAudioAttachment(
        sampleRate: Int,
        pcmBytes: ByteArray,
    ): ConversationAttachment {
        val wavBytes = pcmToWavBytes(sampleRate, smoothTtsPcm(sampleRate, pcmBytes))
        return ConversationAttachment(
            id = UUID.randomUUID().toString(),
            type = "audio",
            mimeType = "audio/wav",
            fileName = "tts-${System.currentTimeMillis()}.wav",
            base64Data = java.util.Base64.getEncoder().encodeToString(wavBytes),
        )
    }

    private fun prepareLocalTtsText(
        text: String,
        readBracketedContent: Boolean,
    ): String {
        var current = text
        if (!readBracketedContent) {
            bracketPatterns.forEach { pattern ->
                current = pattern.replace(current, " ")
            }
        }

        current = current
            .replace(Regex("```[\\s\\S]*?```"), " ")
            .replace(Regex("`[^`]*`"), " ")
            .replace(Regex("https?://\\S+"), " ")
            .replace(Regex("www\\.\\S+"), " ")
            .replace(Regex("[\\uD800-\\uDBFF][\\uDC00-\\uDFFF]"), " ")
            .replace(Regex("[A-Za-z_#@*/\\\\|<>{}\\[\\]^~=+$]+"), " ")
            .replace(Regex("[0-9]{5,}"), " ")
            .replace('-', ' ')

        return normalizeTtsText(current).ifBlank { "收到" }
    }

    private fun normalizeTtsText(text: String): String {
        return text
            .replace(Regex("[\\t\\r\\n ]+"), " ")
            .trim()
    }

    private fun decodeAttachmentToMonoPcm(
        context: Context,
        attachment: ConversationAttachment,
    ): DecodedPcm {
        val bytes = resolveAttachmentBytes(attachment)
        val extension = inferAudioExtension(attachment)
        val tempInput = File(context.cacheDir, "stt-input-${System.currentTimeMillis()}.$extension")
        tempInput.writeBytes(bytes)
        return try {
            decodeAudioFile(tempInput)
        } finally {
            tempInput.delete()
        }
    }

    private fun resolveAttachmentBytes(attachment: ConversationAttachment): ByteArray {
        if (attachment.base64Data.isNotBlank()) {
            return java.util.Base64.getDecoder().decode(attachment.base64Data)
        }

        val location = attachment.remoteUrl.takeIf { it.isNotBlank() }
            ?: attachment.fileName.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Attachment data is missing.")

        return when {
            location.startsWith("data:", ignoreCase = true) -> decodeDataUrl(location)
            location.startsWith("file://", ignoreCase = true) -> File(URI(location)).readBytes()
            location.startsWith("http://", ignoreCase = true) || location.startsWith("https://", ignoreCase = true) ->
                downloadBytes(location)
            else -> File(location).takeIf { it.exists() }?.readBytes()
                ?: throw IllegalStateException("Attachment data is missing.")
        }
    }

    private fun inferAudioExtension(attachment: ConversationAttachment): String {
        return when {
            attachment.fileName.endsWith(".wav", ignoreCase = true) -> "wav"
            attachment.fileName.endsWith(".mp3", ignoreCase = true) -> "mp3"
            attachment.fileName.endsWith(".m4a", ignoreCase = true) -> "m4a"
            attachment.fileName.endsWith(".aac", ignoreCase = true) -> "aac"
            attachment.fileName.endsWith(".ogg", ignoreCase = true) -> "ogg"
            attachment.fileName.endsWith(".opus", ignoreCase = true) -> "opus"
            attachment.fileName.endsWith(".amr", ignoreCase = true) -> "amr"
            attachment.mimeType.contains("wav", ignoreCase = true) -> "wav"
            attachment.mimeType.contains("mpeg", ignoreCase = true) -> "mp3"
            attachment.mimeType.contains("aac", ignoreCase = true) -> "aac"
            attachment.mimeType.contains("ogg", ignoreCase = true) -> "ogg"
            attachment.mimeType.contains("amr", ignoreCase = true) -> "amr"
            else -> "tmp"
        }
    }

    private fun decodeAudioFile(file: File): DecodedPcm {
        val header = file.inputStream().buffered().use { input ->
            ByteArray(12).also { input.read(it) }
        }
        return if (
            header.size >= 12 &&
            header.copyOfRange(0, 4).toString(StandardCharsets.US_ASCII) == "RIFF" &&
            header.copyOfRange(8, 12).toString(StandardCharsets.US_ASCII) == "WAVE"
        ) {
            decodeWavFile(file)
        } else {
            decodeCompressedAudioFile(file)
        }
    }

    private fun decodeWavFile(file: File): DecodedPcm {
        val data = file.readBytes()
        var cursor = 12
        var sampleRate = TARGET_STT_SAMPLE_RATE
        var channels = 1
        var bitsPerSample = 16
        var pcmStart = -1
        var pcmSize = 0

        while (cursor + 8 <= data.size) {
            val chunkId = data.copyOfRange(cursor, cursor + 4).toString(StandardCharsets.US_ASCII)
            val chunkSize = ByteBuffer.wrap(data, cursor + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val chunkDataStart = cursor + 8
            if (chunkDataStart + chunkSize > data.size) break

            when (chunkId) {
                "fmt " -> {
                    channels = ByteBuffer.wrap(data, chunkDataStart + 2, 2)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .short
                        .toInt()
                    sampleRate = ByteBuffer.wrap(data, chunkDataStart + 4, 4)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .int
                    bitsPerSample = ByteBuffer.wrap(data, chunkDataStart + 14, 2)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .short
                        .toInt()
                }

                "data" -> {
                    pcmStart = chunkDataStart
                    pcmSize = chunkSize
                    break
                }
            }

            cursor = chunkDataStart + chunkSize + (chunkSize and 1)
        }

        check(pcmStart >= 0 && pcmSize > 0) { "Unsupported WAV file." }

        val floatSamples = when (bitsPerSample) {
            16 -> {
                val buffer = ByteBuffer.wrap(data, pcmStart, pcmSize).order(ByteOrder.LITTLE_ENDIAN)
                FloatArray(pcmSize / 2) { buffer.short.toFloat() / Short.MAX_VALUE.toFloat() }
            }

            32 -> {
                val buffer = ByteBuffer.wrap(data, pcmStart, pcmSize).order(ByteOrder.LITTLE_ENDIAN)
                FloatArray(pcmSize / 4) { buffer.float }
            }

            else -> throw IllegalStateException("Unsupported WAV sample size: $bitsPerSample")
        }

        return normalizePcm(floatSamples, sampleRate, channels)
    }

    private fun decodeCompressedAudioFile(file: File): DecodedPcm {
        val extractor = MediaExtractor()
        extractor.setDataSource(file.absolutePath)
        val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
            extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: throw IllegalStateException("No audio track found.")

        extractor.selectTrack(trackIndex)
        val format = extractor.getTrackFormat(trackIndex)
        val mimeType = format.getString(MediaFormat.KEY_MIME)
            ?: throw IllegalStateException("Audio MIME type is missing.")

        val codec = MediaCodec.createDecoderByType(mimeType)
        codec.configure(format, null, null, 0)
        codec.start()

        val outputBytes = ByteArrayOutputStream()
        val bufferInfo = MediaCodec.BufferInfo()
        var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var inputDone = false
        var outputDone = false

        try {
            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex) ?: continue
                        inputBuffer.clear()
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outputFormat = codec.outputFormat
                        sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }

                    else -> if (outputIndex >= 0) {
                        val outputBuffer = codec.getOutputBuffer(outputIndex)
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            val chunk = ByteArray(bufferInfo.size)
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            outputBuffer.get(chunk)
                            outputBytes.write(chunk)
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            extractor.release()
        }

        val pcmBytes = outputBytes.toByteArray()
        val pcmBuffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        val sampleCount = pcmBytes.size / 2
        val floatSamples = FloatArray(sampleCount) { pcmBuffer.short.toFloat() / Short.MAX_VALUE.toFloat() }
        return normalizePcm(floatSamples, sampleRate, channels)
    }

    private fun normalizePcm(
        interleavedSamples: FloatArray,
        sampleRate: Int,
        channels: Int,
    ): DecodedPcm {
        val mono = if (channels <= 1) {
            interleavedSamples
        } else {
            val frameCount = interleavedSamples.size / channels
            FloatArray(frameCount) { frameIndex ->
                var sum = 0.0f
                val start = frameIndex * channels
                for (channelIndex in 0 until channels) {
                    sum += interleavedSamples[start + channelIndex]
                }
                sum / channels.toFloat()
            }
        }

        return if (sampleRate == TARGET_STT_SAMPLE_RATE) {
            DecodedPcm(mono, sampleRate)
        } else {
            DecodedPcm(resampleMono(mono, sampleRate, TARGET_STT_SAMPLE_RATE), TARGET_STT_SAMPLE_RATE)
        }
    }

    private fun resampleMono(
        samples: FloatArray,
        fromRate: Int,
        toRate: Int,
    ): FloatArray {
        if (samples.isEmpty() || fromRate == toRate) return samples

        val ratio = toRate.toDouble() / fromRate.toDouble()
        val outputLength = (samples.size * ratio).toInt().coerceAtLeast(1)
        return FloatArray(outputLength) { index ->
            val sourceIndex = index / ratio
            val left = sourceIndex.toInt().coerceIn(0, samples.lastIndex)
            val right = (left + 1).coerceAtMost(samples.lastIndex)
            val fraction = (sourceIndex - left).toFloat()
            samples[left] * (1.0f - fraction) + samples[right] * fraction
        }
    }

    private fun GeneratedAudio.toWavBytes(): ByteArray {
        return pcmToWavBytes(
            sampleRate = sampleRate,
            pcmBytes = ByteBuffer.allocate(samples.size * 2)
                .order(ByteOrder.LITTLE_ENDIAN)
                .apply {
                    samples.forEach { sample ->
                        val clamped = sample.coerceIn(-1.0f, 1.0f)
                        putShort((clamped * Short.MAX_VALUE).toInt().toShort())
                    }
                }
                .array(),
        )
    }

    private fun pcmToWavBytes(
        sampleRate: Int,
        pcmBytes: ByteArray,
    ): ByteArray {
        return ByteBuffer.allocate(44 + pcmBytes.size)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                put("RIFF".toByteArray(StandardCharsets.US_ASCII))
                putInt(36 + pcmBytes.size)
                put("WAVE".toByteArray(StandardCharsets.US_ASCII))
                put("fmt ".toByteArray(StandardCharsets.US_ASCII))
                putInt(16)
                putShort(1.toShort())
                putShort(1.toShort())
                putInt(sampleRate)
                putInt(sampleRate * 2)
                putShort(2.toShort())
                putShort(16.toShort())
                put("data".toByteArray(StandardCharsets.US_ASCII))
                putInt(pcmBytes.size)
                put(pcmBytes)
            }
            .array()
    }

    private fun smoothTtsPcm(
        sampleRate: Int,
        pcmBytes: ByteArray,
    ): ByteArray {
        if (pcmBytes.size < 2) return pcmBytes

        val sampleCount = pcmBytes.size / 2
        val source = ShortArray(sampleCount)
        ByteBuffer.wrap(pcmBytes)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()
            .get(source)

        val leadingSilenceSamples = ((sampleRate * TTS_LEADING_SILENCE_MS) / 1000).coerceAtLeast(1)
        val fadeInSamples = ((sampleRate * TTS_FADE_IN_MS) / 1000).coerceAtLeast(1).coerceAtMost(sampleCount)
        val fadeOutSamples = ((sampleRate * TTS_FADE_OUT_MS) / 1000).coerceAtLeast(1).coerceAtMost(sampleCount)

        val smoothed = ShortArray(leadingSilenceSamples + sampleCount)
        System.arraycopy(source, 0, smoothed, leadingSilenceSamples, sampleCount)

        for (index in 0 until fadeInSamples) {
            val targetIndex = leadingSilenceSamples + index
            val gain = (index + 1).toFloat() / fadeInSamples.toFloat()
            smoothed[targetIndex] = (smoothed[targetIndex] * gain).toInt().toShort()
        }

        val fadeOutStart = smoothed.size - fadeOutSamples
        for (index in 0 until fadeOutSamples) {
            val targetIndex = fadeOutStart + index
            val gain = (fadeOutSamples - index).toFloat() / fadeOutSamples.toFloat()
            smoothed[targetIndex] = (smoothed[targetIndex] * gain).toInt().toShort()
        }

        return ByteBuffer.allocate(smoothed.size * 2)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                smoothed.forEach { putShort(it) }
            }
            .array()
    }

    private fun downloadBytes(url: String): ByteArray {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 120_000
            instanceFollowRedirects = true
        }

        return try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IllegalStateException("HTTP $responseCode while downloading audio content.")
            }
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private fun decodeDataUrl(dataUrl: String): ByteArray {
        val encoded = dataUrl.substringAfter("base64,", "")
        check(encoded.isNotBlank()) { "Attachment data is missing." }
        return Base64.decode(encoded, Base64.DEFAULT)
    }

    private fun ensureInitialized() {
        check(appContext != null) { "SherpaOnnxBridge is not initialized." }
    }

    private fun ensureFrameworkReady() {
        check(isFrameworkReady()) {
            "Sherpa ONNX framework assets are missing. Open Asset Management and activate the on-device framework first."
        }
    }

    private fun ensureSttReady() {
        check(isSttReady()) {
            "Sherpa ONNX STT assets are missing. Open Asset Management and download the on-device STT assets first."
        }
    }

    private fun ensureTtsReady(model: String) {
        val normalized = model.trim().lowercase()
        check(normalized.isNotBlank()) { "No local TTS model is selected." }
        check(isTtsReady(normalized)) {
            when (normalized) {
                "kokoro" -> "Kokoro on-device TTS assets are missing. Open Asset Management and download kokoro first."
                "matcha" -> "Matcha on-device TTS assets are missing or incomplete. Re-download matcha assets first."
                else -> "Unknown local TTS model: $model"
            }
        }
    }

    private fun requireContext(): Context {
        return requireNotNull(appContext) { "SherpaOnnxBridge is not initialized." }
    }

    private data class DecodedPcm(
        val samples: FloatArray,
        val sampleRate: Int,
    )

    private data class TtsRuntimeConfig(
        val config: OfflineTtsConfig,
        val assetManager: AssetManager?,
    )
}
