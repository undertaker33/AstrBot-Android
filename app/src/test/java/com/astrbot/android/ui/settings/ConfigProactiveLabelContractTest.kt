package com.astrbot.android.ui.settings

import java.nio.file.Files
import java.nio.file.Paths
import kotlin.text.Charsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigProactiveLabelContractTest {

    @Test
    fun `proactive switch is labeled as scheduled tasks in default and zh resources`() {
        val defaultStrings = readUtf8("src/main/res/values/strings.xml")
        val zhStrings = readUtf8("src/main/res/values-zh/strings.xml")

        assertTrue(defaultStrings.contains("""name="config_proactive_title">Scheduled tasks<"""))
        assertTrue(zhStrings.contains("""name="config_proactive_title">定时任务<"""))
    }

    @Test
    fun `config detail exposes only one proactive switch in automation section`() {
        val source = readUtf8("src/main/java/com/astrbot/android/feature/config/presentation/ConfigDetailScreen.kt")
        val occurrences = Regex("R\\.string\\.config_proactive_title").findAll(source).count()

        assertEquals(1, occurrences)
        assertFalse(source.contains("config_section_runtime_helpers") && occurrences > 1)
    }

    private fun readUtf8(path: String): String {
        return Files.readAllBytes(Paths.get(path)).toString(Charsets.UTF_8)
    }
}
