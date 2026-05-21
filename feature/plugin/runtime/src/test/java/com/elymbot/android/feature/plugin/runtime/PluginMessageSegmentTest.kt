package com.elymbot.android.feature.plugin.runtime

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginMessageSegmentTest {

    @Test
    fun parses_supported_segment_types_from_json_serializable_maps() {
        val segments = PluginMessageSegmentParser.parseChain(
            listOf(
                mapOf("type" to "text", "text" to "hello"),
                mapOf("type" to "image", "uri" to "plugin://workspace/out/a.png", "alt" to "image"),
                mapOf("type" to "file", "uri" to "https://example.com/report.pdf", "name" to "report.pdf"),
                mapOf("type" to "mention", "userId" to "123"),
                mapOf("type" to "reply", "messageId" to "msg-1"),
                mapOf("type" to "card", "title" to "Result", "text" to "ok"),
            ),
        )

        assertEquals(
            listOf("text", "image", "file", "mention", "reply", "card"),
            segments.map(PluginMessageSegment::type),
        )
    }

    @Test
    fun rejects_unsupported_segment_type_with_structured_error_code() {
        val error = assertThrows(PluginV2HostApiException::class.java) {
            PluginMessageSegmentParser.parseChain(listOf(mapOf("type" to "audio", "uri" to "plugin://workspace/a.wav")))
        }

        assertEquals(PluginMessageSegmentParser.UNSUPPORTED_SEGMENT_TYPE, error.error.code)
        assertEquals("audio", error.error.details["type"])
    }

    @Test
    fun rejects_media_path_escape_and_arbitrary_absolute_file_path() {
        val escape = assertThrows(PluginV2HostApiException::class.java) {
            PluginMessageSegmentParser.parseChain(listOf(mapOf("type" to "image", "uri" to "plugin://workspace/../secret.png")))
        }
        val file = assertThrows(PluginV2HostApiException::class.java) {
            PluginMessageSegmentParser.parseChain(listOf(mapOf("type" to "file", "uri" to "file:///sdcard/secret.txt")))
        }

        assertEquals(PluginMessageSegmentParser.UNSAFE_MEDIA_REF, escape.error.code)
        assertEquals(PluginMessageSegmentParser.UNSAFE_MEDIA_REF, file.error.code)
    }

    @Test
    fun rejects_unapproved_content_uri_but_allows_host_fileprovider_ref() {
        val unapproved = assertThrows(PluginV2HostApiException::class.java) {
            PluginMessageSegmentParser.parseChain(
                listOf(mapOf("type" to "image", "uri" to "content://com.android.providers.downloads/document/42")),
            )
        }

        val approved = PluginMessageSegmentParser.parseChain(
            listOf(mapOf("type" to "image", "uri" to "content://com.elymbot.android.fileprovider/plugin/out.png")),
        )

        assertEquals(PluginMessageSegmentParser.UNSAFE_MEDIA_REF, unapproved.error.code)
        assertEquals("image", approved.single().type)
    }

    @Test
    fun external_event_attachment_resolver_rejects_unsafe_refs_and_keeps_safe_refs() {
        val pluginRoot = Files.createTempDirectory("plugin-event-attachment").toFile()
        try {
            val mediaFile = File(pluginRoot, "resources/out.png").apply {
                requireNotNull(parentFile).mkdirs()
                writeBytes(byteArrayOf(1, 2, 3))
            }

            val safeRefs = listOf(
                "plugin://package/resources/out.png",
                "plugin://workspace/out.png",
                "https://example.com/out.png",
                "content://com.elymbot.android.fileprovider/plugin/out.png",
            )
            safeRefs.forEach { ref ->
                assertEquals(ref, resolvePluginEventAttachmentUri(ref, pluginRoot))
            }
            assertEquals(
                mediaFile.canonicalFile.absolutePath,
                resolvePluginEventAttachmentUri("resources/out.png", pluginRoot),
            )

            val unsafeRefs = listOf(
                "content://com.android.providers.downloads/document/42",
                "http://example.com/out.png",
                "file:///sdcard/secret.png",
                "base64://abcd",
                "plugin://other/out.png",
                "../secret.png",
            )
            unsafeRefs.forEach { ref ->
                val error = assertThrows(PluginV2HostApiException::class.java) {
                    resolvePluginEventAttachmentUri(ref, pluginRoot)
                }
                assertEquals(PluginMessageSegmentParser.UNSAFE_MEDIA_REF, error.error.code)
            }
        } finally {
            pluginRoot.deleteRecursively()
        }
    }

    @Test
    fun app_chat_mapping_merges_text_and_creates_visible_fallbacks() {
        val mapped = PluginMessagePlatformMapper.mapForAppChat(
            listOf(
                PluginTextSegment(text = "hello"),
                PluginMentionSegment(userId = "123", label = "alice"),
                PluginReplySegment(messageId = "msg-1"),
                PluginCardSegment(title = "Card", text = "body"),
            ),
        )

        assertTrue(mapped.text.contains("hello"))
        assertTrue(mapped.text.contains("@alice"))
        assertTrue(mapped.text.contains("> reply: msg-1"))
        assertTrue(mapped.text.contains("Card"))
        assertEquals(0, mapped.attachments.size)
        assertTrue(mapped.warnings.any { it.code == "app_chat_segment_fallback" })
    }
}
