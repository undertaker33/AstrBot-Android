package com.astrbot.android.ui.screen

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class PluginDetailResourceTest {
    @Test
    fun plugin_detail_manage_configuration_action_is_localized() {
        val defaultStrings = readStringResources("values/strings.xml")
        val zhStrings = readStringResources("values-zh/strings.xml")

        assertEquals("Open configuration", defaultStrings["plugin_action_open_config"])
        assertEquals("打开配置", zhStrings["plugin_action_open_config"])
    }

    private fun readStringResources(relativePath: String): Map<String, String> {
        val file = listOf(
            File("app/src/main/res", relativePath),
            File("src/main/res", relativePath),
        ).firstOrNull { it.exists() } ?: error("Missing resource file: $relativePath")
        return Regex("""<string\s+name="([^"]+)">([^<]*)</string>""")
            .findAll(file.readText())
            .associate { match -> match.groupValues[1] to match.groupValues[2] }
    }
}
