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
            "core/common",
            "core/db",
            "core/di",
            "core/network",
            "core/runtime",
            "feature/chat/presentation",
            "feature/chat/domain",
            "feature/chat/data",
            "feature/chat/runtime",
            "feature/qq/presentation",
            "feature/qq/domain",
            "feature/qq/data",
            "feature/qq/runtime",
            "feature/plugin/presentation",
            "feature/plugin/domain",
            "feature/plugin/data",
            "feature/plugin/runtime",
            "feature/resource/presentation",
            "feature/resource/domain",
            "feature/resource/data",
            "feature/resource/runtime",
            "feature/cron/presentation",
            "feature/cron/domain",
            "feature/cron/data",
            "feature/cron/runtime",
            "feature/provider/presentation",
            "feature/provider/domain",
            "feature/provider/data",
            "feature/provider/runtime",
            "feature/config/presentation",
            "feature/config/domain",
            "feature/config/data",
            "feature/config/runtime",
            "feature/bot/presentation",
            "feature/bot/domain",
            "feature/bot/data",
            "feature/bot/runtime",
            "feature/persona/presentation",
            "feature/persona/domain",
            "feature/persona/data",
            "feature/persona/runtime",
        )

        val missing = required.filterNot { mainRoot.resolve(it).exists() }
        assertTrue("Missing architecture anchors: $missing", missing.isEmpty())
    }

    @Test
    fun migrated_presentation_does_not_import_legacy_data_or_runtime() {
        val violations = kotlinFilesUnder("feature")
            .filter { it.toString().replace('\\', '/').contains("/presentation/") }
            .flatMap { file ->
                val text = file.readText()
                forbiddenPresentationImports.mapNotNull { forbidden ->
                    if (text.contains(forbidden)) "${mainRoot.relativize(file)} imports $forbidden" else null
                }
            }

        assertTrue("Presentation boundary violations: $violations", violations.isEmpty())
    }

    @Test
    fun migrated_domain_does_not_import_android_ui_or_legacy_singletons() {
        val violations = kotlinFilesUnder("feature")
            .filter { it.toString().replace('\\', '/').contains("/domain/") }
            .flatMap { file ->
                val text = file.readText()
                forbiddenDomainImports.mapNotNull { forbidden ->
                    if (text.contains(forbidden)) "${mainRoot.relativize(file)} imports $forbidden" else null
                }
            }

        assertTrue("Domain boundary violations: $violations", violations.isEmpty())
    }

    @Test
    fun app_chat_presentation_exposes_real_send_handler_bound_to_domain_use_case() {
        val handlerFile = mainRoot.resolve("feature/chat/presentation/AppChatSendHandler.kt")
        assertTrue("AppChatSendHandler.kt must exist", handlerFile.exists())
        val text = handlerFile.readText()
        assertTrue(
            "AppChatSendHandler must delegate to SendAppMessageUseCase",
            text.contains("SendAppMessageUseCase"),
        )
        assertTrue(
            "AppChatSendHandler must not depend on legacy data singletons",
            !text.contains("import com.astrbot.android.data."),
        )
        assertTrue(
            "AppChatSendHandler must not depend on runtime/plugin objects",
            !text.contains("import com.astrbot.android.runtime."),
        )
    }

    @Test
    fun chat_view_model_send_path_delegates_to_feature_chat_without_direct_runtime_send() {
        val viewModelFile = mainRoot.resolve("ui/viewmodel/ChatViewModel.kt")
        assertTrue("ChatViewModel.kt must exist", viewModelFile.exists())
        val text = viewModelFile.readText()
        assertTrue(
            "ChatViewModel must delegate app chat sending through presentation handler",
            text.contains("AppChatSendHandler"),
        )
        assertTrue(
            "ChatViewModel must not keep deliverViaRuntimePort after use case migration",
            !text.contains("deliverViaRuntimePort"),
        )
        assertTrue(
            "ChatViewModel must not directly invoke AppChatRuntimePort.send",
            !text.contains(".send(\n            AppChatRequest(") &&
                !text.contains("localRuntimePort.send"),
        )
    }

    private fun kotlinFilesUnder(relative: String): List<Path> {
        val root = mainRoot.resolve(relative)
        if (!root.exists()) return emptyList()
        return Files.walk(root).use { stream ->
            stream
                .filter { it.isRegularFile() && it.fileName.toString().endsWith(".kt") }
                .toList()
        }
    }

    private companion object {
        val forbiddenPresentationImports = listOf(
            "import com.astrbot.android.data.",
            "import com.astrbot.android.runtime.",
        )

        val forbiddenDomainImports = listOf(
            "import android.",
            "import androidx.compose.",
            "import com.astrbot.android.data.",
            "import com.astrbot.android.runtime.",
        )
    }
}
