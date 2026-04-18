package com.astrbot.android.core.runtime.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.core.common.logging.RuntimeLogRepository
import java.io.File
import java.util.Base64
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object AndroidSystemTtsBridge {
    fun synthesize(
        context: Context,
        text: String,
        preferredVoiceId: String,
    ): ConversationAttachment {
        val sanitized = text.trim().ifBlank { "收到" }
        val outputFile = File(context.cacheDir, "system-tts-${System.currentTimeMillis()}.wav")
        val utteranceId = UUID.randomUUID().toString()
        val tts = createEngine(context)
        try {
            configureVoice(tts, preferredVoiceId)
            val doneLatch = CountDownLatch(1)
            var errorMessage: String? = null
            Handler(Looper.getMainLooper()).post {
                tts.setOnUtteranceProgressListener(
                    object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) = Unit

                        override fun onDone(utteranceId: String?) {
                            doneLatch.countDown()
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            errorMessage = "Android system TTS synthesis failed."
                            doneLatch.countDown()
                        }

                        override fun onError(utteranceId: String?, errorCode: Int) {
                            errorMessage = "Android system TTS synthesis failed: code=$errorCode"
                            doneLatch.countDown()
                        }
                    },
                )
                val result = tts.synthesizeToFile(sanitized, Bundle(), outputFile, utteranceId)
                if (result != TextToSpeech.SUCCESS) {
                    errorMessage = "Android system TTS rejected synthesis request: code=$result"
                    doneLatch.countDown()
                }
            }
            check(doneLatch.await(20, TimeUnit.SECONDS)) { "Android system TTS timed out." }
            check(errorMessage == null) { errorMessage!! }
            check(outputFile.exists() && outputFile.length() > 0L) { "Android system TTS did not produce audio." }
            RuntimeLogRepository.append("Android system TTS generated audio: bytes=${outputFile.length()}")
            return ConversationAttachment(
                id = UUID.randomUUID().toString(),
                type = "audio",
                mimeType = "audio/wav",
                fileName = outputFile.name,
                base64Data = Base64.getEncoder().encodeToString(outputFile.readBytes()),
            )
        } finally {
            runCatching { tts.stop() }
            runCatching { tts.shutdown() }
            runCatching { outputFile.delete() }
        }
    }

    private fun createEngine(context: Context): TextToSpeech {
        val candidateEngines = buildList {
            add(null)
            val discovered = context.packageManager
                .queryIntentServices(Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0)
                .mapNotNull { it.serviceInfo?.packageName }
                .distinct()
            addAll(discovered)
        }
        var lastError = "Android system TTS initialization failed: no engine responded."
        candidateEngines.forEach { enginePackage ->
            val latch = CountDownLatch(1)
            var initStatus = TextToSpeech.ERROR
            var engine: TextToSpeech? = null
            Handler(Looper.getMainLooper()).post {
                engine = if (enginePackage == null) {
                    TextToSpeech(context.applicationContext) { status ->
                        initStatus = status
                        latch.countDown()
                    }
                } else {
                    TextToSpeech(context.applicationContext, { status ->
                        initStatus = status
                        latch.countDown()
                    }, enginePackage)
                }
            }
            if (!latch.await(10, TimeUnit.SECONDS)) {
                lastError = "Android system TTS initialization timed out."
                runCatching { engine?.shutdown() }
                return@forEach
            }
            if (initStatus == TextToSpeech.SUCCESS && engine != null) {
                RuntimeLogRepository.append(
                    "Android system TTS engine selected: ${enginePackage ?: "default"}",
                )
                return engine!!
            }
            lastError = "Android system TTS initialization failed: code=$initStatus engine=${enginePackage ?: "default"}"
            runCatching { engine?.shutdown() }
        }
        throw IllegalStateException(lastError)
    }

    private fun configureVoice(
        tts: TextToSpeech,
        preferredVoiceId: String,
    ) {
        val chineseLocale = Locale.SIMPLIFIED_CHINESE
        val localeResult = tts.setLanguage(chineseLocale)
        check(localeResult != TextToSpeech.LANG_MISSING_DATA && localeResult != TextToSpeech.LANG_NOT_SUPPORTED) {
            "Android system TTS does not support Simplified Chinese."
        }
        val normalizedVoiceId = preferredVoiceId.trim().lowercase()
        val candidate = tts.voices
            ?.filter { voice ->
                val locale = voice.locale
                locale != null &&
                    locale.language.equals(chineseLocale.language, ignoreCase = true) &&
                    !voice.isNetworkConnectionRequired
            }
            ?.sortedBy { voice ->
                when {
                    normalizedVoiceId == "male" && voice.name.contains("male", ignoreCase = true) -> 0
                    normalizedVoiceId == "female" && voice.name.contains("female", ignoreCase = true) -> 0
                    else -> 1
                }
            }
            ?.firstOrNull()
        if (candidate != null) {
            tts.voice = candidate
        }
        tts.setSpeechRate(1.0f)
        tts.setPitch(1.0f)
    }
}
