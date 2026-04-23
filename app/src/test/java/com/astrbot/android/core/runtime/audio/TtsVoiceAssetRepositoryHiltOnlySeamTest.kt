package com.astrbot.android.core.runtime.audio

import java.nio.file.Path
import kotlin.io.path.readText
import org.junit.Assert.assertFalse
import org.junit.Test

class TtsVoiceAssetRepositoryHiltOnlySeamTest {

    private val projectRoot: Path = detectProjectRoot()
    private val sourceFile: Path = projectRoot.resolve(
        "app/src/main/java/com/astrbot/android/core/runtime/audio/TtsVoiceAssetRepository.kt",
    )

    @Test
    fun tts_voice_asset_repository_must_not_self_bootstrap_from_room_or_static_object_state() {
        val source = sourceFile.readText()

        assertFalse(
            "TtsVoiceAssetRepository must not self-bootstrap through AstrBotDatabase.get(context).",
            source.contains("AstrBotDatabase.get(context)"),
        )
        assertFalse(
            "TtsVoiceAssetRepository should no longer be the state-owning Kotlin object.",
            source.contains("object TtsVoiceAssetRepository"),
        )
    }

    private fun detectProjectRoot(): Path {
        var current = Path.of("").toAbsolutePath()
        while (current.parent != null) {
            if (current.resolve("settings.gradle.kts").toFile().isFile ||
                current.resolve("settings.gradle").toFile().isFile
            ) {
                return current
            }
            current = current.parent
        }
        error("Unable to locate project root from ${Path.of("").toAbsolutePath()}")
    }
}
