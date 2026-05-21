package com.elymbot.android

import com.elymbot.android.model.plugin.PluginInstallIntent
import kotlin.io.path.readText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityPluginDeepLinkTest {
    @Test
    fun `repository deep link parses into pending repository request`() {
        val request = parsePluginDeepLinkInstallRequest(
            "elymbot://plugin/repository?url=https%3A%2F%2Frepo.example.com%2Fcatalog.json",
        )

        assertEquals(
            PluginDeepLinkInstallRequest(
                action = PluginDeepLinkAction.Repository,
                url = "https://repo.example.com/catalog.json",
                intent = PluginInstallIntent.repositoryUrl("https://repo.example.com/catalog.json"),
            ),
            request,
        )
    }

    @Test
    fun `install deep link parses into pending direct package request`() {
        val request = parsePluginDeepLinkInstallRequest(
            "elymbot://plugin/install?url=https%3A%2F%2Fplugins.example.com%2Fdemo.zip",
        )

        assertEquals(
            PluginDeepLinkInstallRequest(
                action = PluginDeepLinkAction.DirectPackage,
                url = "https://plugins.example.com/demo.zip",
                intent = PluginInstallIntent.directPackageUrl("https://plugins.example.com/demo.zip"),
            ),
            request,
        )
    }

    @Test
    fun `plugin deep link rejects non https urls`() {
        assertNull(
            parsePluginDeepLinkInstallRequest(
                "elymbot://plugin/install?url=http%3A%2F%2Fplugins.example.com%2Fdemo.zip",
            ),
        )
    }

    @Test
    fun `unsupported deep link returns null`() {
        assertNull(parsePluginDeepLinkInstallRequest("https://example.com/plugin/install?url=x"))
        assertNull(parsePluginDeepLinkInstallRequest("elymbot://plugin/update?url=https://example.com/x.zip"))
    }

    @Test
    fun `legacy parsePluginInstallIntentFromDeepLink still works for backward compatibility`() {
        val intent = parsePluginInstallIntentFromDeepLink(
            "elymbot://plugin/repository?url=https%3A%2F%2Frepo.example.com%2Fcatalog.json",
        )
        assertEquals(
            PluginInstallIntent.repositoryUrl("https://repo.example.com/catalog.json"),
            intent,
        )
    }

    @Test
    fun `legacy parsePluginInstallIntentFromDeepLink rejects http urls`() {
        assertNull(
            parsePluginInstallIntentFromDeepLink(
                "elymbot://plugin/install?url=http%3A%2F%2Fplugins.example.com%2Fdemo.zip",
            ),
        )
    }

    @Test
    fun `handle plugin deep link must only enqueue pending confirmation`() {
        val source = java.nio.file.Path.of("app/src/main/java/com/elymbot/android/MainActivity.kt")
            .takeIf { java.nio.file.Files.exists(it) }
            ?: java.nio.file.Path.of("src/main/java/com/elymbot/android/MainActivity.kt")
        val mainActivity = source.readText()
        val handleFunction = mainActivity.substringAfter("private fun handlePluginDeepLink")
            .substringBefore("\n    private fun ")

        assertTrue(
            "handlePluginDeepLink must not directly execute PluginInstallIntentHandler.handle; confirmation dialog should do it",
            !handleFunction.contains("pluginInstallIntentHandler.handle"),
        )
    }

    @Test
    fun `main activity wires one update check after compose content initialization`() {
        val mainActivity = mainActivitySource()
        val contentBlock = mainActivity.substringAfter("setContent {")

        assertTrue(
            "MainActivity must trigger one app update check from the Compose content lifecycle",
            contentBlock.contains("LaunchedEffect(Unit)") &&
                contentBlock.contains("appUpdateViewModel.checkForUpdateOnce()"),
        )
    }

    @Test
    fun `plugin deep link confirmation must take priority over update dialog host`() {
        val mainActivity = mainActivitySource()
        val updateGate = mainActivity.substringAfter("if (pendingPluginRequest == null)")
            .substringBefore("pendingPluginRequest?.let")

        assertTrue(
            "AppUpdateDialogHost must only be shown when no plugin deep link confirmation is pending",
            updateGate.contains("AppUpdateDialogHost("),
        )
        assertTrue(
            "AppUpdateDialogHost must be wired to the Hilt AppUpdateViewModel actions",
            updateGate.contains("uiState = appUpdateUiState") &&
                updateGate.contains("onUpdateNow = appUpdateViewModel::updateNow") &&
                updateGate.contains("onSnooze = appUpdateViewModel::snooze") &&
                updateGate.contains("onIgnore = appUpdateViewModel::ignore") &&
                updateGate.contains("onInstall = appUpdateViewModel::install"),
        )
    }

    private fun mainActivitySource(): String {
        val source = java.nio.file.Path.of("app/src/main/java/com/elymbot/android/MainActivity.kt")
            .takeIf { java.nio.file.Files.exists(it) }
            ?: java.nio.file.Path.of("src/main/java/com/elymbot/android/MainActivity.kt")
        return source.readText()
    }
}
