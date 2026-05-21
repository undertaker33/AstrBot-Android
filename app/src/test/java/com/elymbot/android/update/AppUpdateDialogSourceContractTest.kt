package com.elymbot.android.update

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateDialogSourceContractTest {

    @Test
    fun `all app update dialogs must block outside dismiss`() {
        val source = appUpdateDialogSource()

        assertEquals(
            "Available, downloading, ready, and failed dialogs must all disable dismiss requests",
            4,
            Regex("""onDismissRequest\s*=\s*\{\}""").findAll(source).count(),
        )
    }

    @Test
    fun `failed dialog must keep retry snooze and ignore decisions`() {
        val failedDialog = appUpdateDialogSource()
            .substringAfter("private fun AppUpdateFailedDialog")

        assertTrue(failedDialog.contains("onRetry(failed.candidate)"))
        assertTrue(failedDialog.contains("onSnooze(failed.candidate)"))
        assertTrue(failedDialog.contains("onIgnore(failed.candidate)"))
        assertTrue(
            "Failed app update dialog must not fall back to a close-only decision",
            !failedDialog.contains("app_update_action_close"),
        )
    }

    private fun appUpdateDialogSource(): String {
        val source = Path.of("app/src/main/java/com/elymbot/android/update/AppUpdateDialog.kt")
            .takeIf { Files.exists(it) }
            ?: Path.of("src/main/java/com/elymbot/android/update/AppUpdateDialog.kt")
        return source.readText()
    }
}
