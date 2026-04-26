package com.astrbot.android.feature.cron.runtime

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CronResourceContractTest {
    @Test
    fun cron_and_runtime_prompt_resource_files_exist_and_have_matching_keys() {
        listOf("cron_strings.xml", "runtime_prompt_strings.xml").forEach { fileName ->
            val defaultKeys = resourceKeys(File("src/main/res/values/$fileName"))
            val zhKeys = resourceKeys(File("src/main/res/values-zh/$fileName"))

            assertTrue("Expected $fileName to declare strings.", defaultKeys.isNotEmpty())
            assertEquals("values and values-zh keys must match for $fileName", defaultKeys, zhKeys)
        }
    }

    @Test
    fun active_capability_runtime_code_does_not_embed_large_user_facing_prompts() {
        val provider = File(
            "src/main/java/com/astrbot/android/feature/plugin/runtime/toolsource/ActiveCapabilityToolSourceProvider.kt",
        ).readText()
        val fallbackResponder = File(
            "src/main/java/com/astrbot/android/feature/cron/runtime/ScheduledTaskIntentFallbackResponder.kt",
        ).readText()

        assertFalse(provider.contains("Required for creating reminders, timers, timed follow-ups"))
        assertFalse(provider.contains("Run a scheduled future task immediately through the injected scheduled task runner."))
        assertFalse(fallbackResponder.contains("Tell the user naturally that the reminder was created."))
        assertFalse(fallbackResponder.contains("Do not call create_future_task again."))
    }

    private fun resourceKeys(file: File): Set<String> {
        assertTrue("Missing resource file: ${file.path}", file.isFile)
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val keys = linkedSetOf<String>()
        listOf("string", "string-array").forEach { tag ->
            val nodes = document.getElementsByTagName(tag)
            for (index in 0 until nodes.length) {
                val node = nodes.item(index)
                val name = node.attributes?.getNamedItem("name")?.nodeValue
                if (!name.isNullOrBlank()) keys += name
            }
        }
        return keys
    }
}
