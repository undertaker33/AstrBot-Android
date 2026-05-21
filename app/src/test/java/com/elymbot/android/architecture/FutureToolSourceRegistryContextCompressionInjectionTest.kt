package com.elymbot.android.architecture

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FutureToolSourceRegistryContextCompressionInjectionTest {

    private val projectRoot = detectProjectRoot()

    @Test
    fun production_context_strategy_provider_injection_requires_context_compressor() {
        val providerSource = projectRoot.resolve(
            "feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/toolsource/ContextStrategyToolSourceProvider.kt",
        ).readText()
        val registrySource = projectRoot.resolve(
            "feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/toolsource/FutureToolSourceRegistry.kt",
        ).readText()

        assertTrue(
            "ContextStrategyToolSourceProvider production @Inject constructor must require PluginV2ContextCompressApi.",
            providerSource.contains("class ContextStrategyToolSourceProvider @Inject constructor(") &&
                providerSource.contains("private val contextCompressor: PluginV2ContextCompressApi,"),
        )
        assertFalse(
            "ContextStrategyToolSourceProvider production compressor dependency must not be nullable.",
            providerSource.contains("PluginV2ContextCompressApi?"),
        )
        assertTrue(
            "FutureToolSourceRegistry production constructor must receive ContextStrategyToolSourceProvider from Hilt.",
            Regex("""class\s+FutureToolSourceRegistry\s+@Inject\s+constructor\([\s\S]*contextStrategyToolSourceProvider:\s*ContextStrategyToolSourceProvider""")
                .containsMatchIn(registrySource),
        )
    }

    private fun detectProjectRoot(): Path {
        var current = Path.of("").toAbsolutePath()
        while (!current.resolve("settings.gradle.kts").exists()) {
            current = checkNotNull(current.parent) { "Unable to locate project root." }
        }
        return current
    }
}
