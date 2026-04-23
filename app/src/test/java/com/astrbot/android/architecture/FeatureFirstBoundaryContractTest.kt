package com.astrbot.android.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureFirstBoundaryContractTest {
    private val mainRoot: Path = listOf(
        Path.of("src/main/java/com/astrbot/android"),
        Path.of("app/src/main/java/com/astrbot/android"),
    ).first { it.exists() }

    @Test
    fun feature_first_anchor_directories_exist() {
        val required = listOf(
            "feature/bot/data",
            "feature/chat/data",
            "feature/config/data",
            "feature/cron/data",
            "feature/persona/data",
            "feature/plugin/runtime",
            "feature/provider/data",
            "feature/qq/data",
            "feature/resource/data",
        )
        val missing = required.filterNot { mainRoot.resolve(it).exists() }
        assertTrue("Missing feature-first anchors: $missing", missing.isEmpty())
    }

    @Test
    fun feature_presentation_and_domain_stay_off_root_singletons() {
        val violations = kotlinFilesUnder("feature")
            .flatMap { file ->
                val relative = mainRoot.relativize(file).toString().replace('\\', '/')
                val text = file.readText()
                when {
                    "/presentation/" in relative -> presentationForbiddenImports.mapNotNull { forbidden ->
                        if (text.contains(forbidden)) "$relative imports $forbidden" else null
                    }

                    "/domain/" in relative -> domainForbiddenImports.mapNotNull { forbidden ->
                        if (text.contains(forbidden)) "$relative imports $forbidden" else null
                    }

                    else -> emptyList()
                }
            }

        assertTrue(
            "Feature presentation/domain files must stay off root singleton imports: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun feature_tree_must_not_reference_legacy_adapter_types() {
        val legacyAdapterPattern = Regex("""\bLegacy[A-Za-z0-9_]*Adapter\b""")
        val violations = kotlinFilesUnder("feature")
            .flatMap { file ->
                val relative = mainRoot.relativize(file).toString().replace('\\', '/')
                legacyAdapterPattern.findAll(file.readText())
                    .map { match -> "$relative references ${match.value}" }
                    .toList()
            }

        assertTrue(
            "Feature production code must not reference legacy adapter types after phase 5: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun plugin_v1_boundary_uses_semantic_dispatch_adapter_name() {
        val legacyFile = mainRoot.resolve("feature/plugin/runtime/PluginV1LegacyAdapter.kt")
        val semanticFile = mainRoot.resolve("feature/plugin/runtime/PluginV1DispatchAdapter.kt")
        val facadeFile = mainRoot.resolve("feature/plugin/runtime/PluginRuntimeFacade.kt")

        assertTrue(
            "PluginV1LegacyAdapter.kt must be removed from production after phase 5 cleanup",
            !legacyFile.exists(),
        )
        assertTrue(
            "PluginV1DispatchAdapter.kt must exist as the semantic V1 frozen boundary",
            semanticFile.exists(),
        )

        val boundaryText = semanticFile.readText()
        assertTrue(
            "PluginV1DispatchAdapter must still document the frozen V1 boundary",
            boundaryText.contains("frozen boundary", ignoreCase = true) ||
                boundaryText.contains("freeze", ignoreCase = true),
        )
        assertTrue(
            "PluginRuntimeFacade must not expose legacy adapter names",
            !facadeFile.readText().contains("PluginV1LegacyAdapter"),
        )
    }

    private fun kotlinFilesUnder(relativeRoot: String): List<Path> {
        val root = mainRoot.resolve(relativeRoot)
        return Files.walk(root).use { stream ->
            stream
                .filter { it.isRegularFile() && it.fileName.toString().endsWith(".kt") }
                .toList()
        }
    }

    private val presentationForbiddenImports = listOf(
        "import com.astrbot.android.data.",
        "import com.astrbot.android.runtime.",
    )

    private val domainForbiddenImports = listOf(
        "import android.",
        "import androidx.compose.",
        "import com.astrbot.android.data.",
        "import com.astrbot.android.runtime.",
    )
}
