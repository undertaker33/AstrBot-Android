package com.elymbot.android.feature.plugin.runtime

sealed interface PluginMessageSegment {
    val type: String
}

data class PluginTextSegment(
    val text: String,
) : PluginMessageSegment {
    override val type: String = "text"
}

data class PluginImageSegment(
    val uri: String,
    val alt: String = "",
    val mimeType: String = "image/*",
) : PluginMessageSegment {
    override val type: String = "image"
}

data class PluginFileSegment(
    val uri: String,
    val name: String = "",
    val mimeType: String = "application/octet-stream",
) : PluginMessageSegment {
    override val type: String = "file"
}

data class PluginMentionSegment(
    val userId: String,
    val label: String = "",
) : PluginMessageSegment {
    override val type: String = "mention"
}

data class PluginReplySegment(
    val messageId: String,
) : PluginMessageSegment {
    override val type: String = "reply"
}

data class PluginCardSegment(
    val title: String,
    val text: String = "",
    val url: String = "",
) : PluginMessageSegment {
    override val type: String = "card"
}

data class PluginMessageSendWarning(
    val code: String,
    val message: String,
    val segmentType: String = "",
)

data class PluginMessagePlatformMapping(
    val text: String,
    val attachments: List<PluginV2MessageAttachmentRef> = emptyList(),
    val warnings: List<PluginMessageSendWarning> = emptyList(),
)

object PluginMessageSegmentParser {
    const val UNSUPPORTED_SEGMENT_TYPE = "unsupported_segment_type"
    const val UNSAFE_MEDIA_REF = "unsafe_media_ref"

    fun parseChain(value: Any?): List<PluginMessageSegment> {
        val items = when (value) {
            null -> emptyList<Any?>()
            is List<*> -> value
            else -> throw PluginV2HostApiException(
                PluginV2HostApiError(
                    code = PluginV2HostApiErrorCodes.INVALID_PAYLOAD,
                    message = "message chain must be a list.",
                ),
            )
        }
        return items.mapIndexed { index, item ->
            parseSegment(item, index)
        }
    }

    fun validateMediaRef(uri: String): String {
        val normalized = uri.trim()
        if (normalized.isBlank() || normalized.contains("..")) {
            throw unsafeMediaRef(normalized)
        }
        val allowed = normalized.startsWith("plugin://package/") ||
            normalized.startsWith("plugin://workspace/") ||
            normalized.startsWith(HOST_FILE_PROVIDER_CONTENT_PREFIX, ignoreCase = true) ||
            normalized.startsWith("https://", ignoreCase = true)
        if (!allowed) {
            throw unsafeMediaRef(normalized)
        }
        return normalized
    }

    private fun parseSegment(item: Any?, index: Int): PluginMessageSegment {
        val type = propertyValue(item, "type")
            ?.toString()
            ?.trim()
            ?.lowercase()
            .orEmpty()
        return when (type) {
            "text" -> PluginTextSegment(text = requireText(item, "text", index))
            "image" -> PluginImageSegment(
                uri = validateMediaRef(requireText(item, "uri", index)),
                alt = propertyValue(item, "alt")?.toString().orEmpty(),
                mimeType = propertyValue(item, "mimeType")?.toString().orEmpty().ifBlank { "image/*" },
            )
            "file" -> PluginFileSegment(
                uri = validateMediaRef(requireText(item, "uri", index)),
                name = propertyValue(item, "name")?.toString().orEmpty(),
                mimeType = propertyValue(item, "mimeType")?.toString().orEmpty().ifBlank { "application/octet-stream" },
            )
            "mention" -> PluginMentionSegment(
                userId = requireText(item, "userId", index),
                label = propertyValue(item, "label")?.toString().orEmpty(),
            )
            "reply" -> PluginReplySegment(messageId = requireText(item, "messageId", index))
            "card" -> PluginCardSegment(
                title = requireText(item, "title", index),
                text = propertyValue(item, "text")?.toString().orEmpty(),
                url = propertyValue(item, "url")?.toString().orEmpty(),
            )
            else -> throw PluginV2HostApiException(
                PluginV2HostApiError(
                    code = UNSUPPORTED_SEGMENT_TYPE,
                    message = "Unsupported message segment type.",
                    details = mapOf("type" to type, "index" to index.toString()),
                ),
            )
        }
    }

    private fun requireText(item: Any?, field: String, index: Int): String {
        return propertyValue(item, field)
            ?.toString()
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: throw PluginV2HostApiException(
                PluginV2HostApiError(
                    code = PluginV2HostApiErrorCodes.INVALID_PAYLOAD,
                    message = "message segment requires non-blank $field.",
                    details = mapOf("index" to index.toString(), "field" to field),
                ),
            )
    }

    private fun propertyValue(source: Any?, name: String): Any? {
        return when (source) {
            is Map<*, *> -> source[name]
            else -> source?.javaClass?.methods
                ?.firstOrNull { method -> method.name == "getProperty" && method.parameterTypes.size == 1 }
                ?.let { method -> runCatching { method.invoke(source, name) }.getOrNull() }
        }
    }

    private fun unsafeMediaRef(uri: String): PluginV2HostApiException {
        return PluginV2HostApiException(
            PluginV2HostApiError(
                code = UNSAFE_MEDIA_REF,
                message = "Media reference is not allowed.",
                details = mapOf("uri" to uri),
            ),
        )
    }

    private const val HOST_FILE_PROVIDER_CONTENT_PREFIX = "content://com.elymbot.android.fileprovider/"
}

object PluginMessagePlatformMapper {
    fun mapForAppChat(chain: List<PluginMessageSegment>): PluginMessagePlatformMapping {
        val textParts = mutableListOf<String>()
        val attachments = mutableListOf<PluginV2MessageAttachmentRef>()
        val warnings = mutableListOf<PluginMessageSendWarning>()
        chain.forEach { segment ->
            when (segment) {
                is PluginTextSegment -> textParts += segment.text
                is PluginImageSegment -> attachments += PluginV2MessageAttachmentRef(
                    uri = PluginMessageSegmentParser.validateMediaRef(segment.uri),
                    mimeType = segment.mimeType.ifBlank { "image/*" },
                )
                is PluginFileSegment -> attachments += PluginV2MessageAttachmentRef(
                    uri = PluginMessageSegmentParser.validateMediaRef(segment.uri),
                    mimeType = segment.mimeType.ifBlank { "application/octet-stream" },
                )
                is PluginMentionSegment -> {
                    textParts += "@${segment.label.ifBlank { segment.userId }}"
                    warnings += fallback("app_chat_segment_fallback", segment.type)
                }
                is PluginReplySegment -> {
                    textParts += "> reply: ${segment.messageId}"
                    warnings += fallback("app_chat_segment_fallback", segment.type)
                }
                is PluginCardSegment -> {
                    textParts += listOf(segment.title, segment.text, segment.url)
                        .map(String::trim)
                        .filter(String::isNotBlank)
                        .joinToString(" ")
                    warnings += fallback("app_chat_segment_fallback", segment.type)
                }
            }
        }
        return PluginMessagePlatformMapping(
            text = textParts.filter(String::isNotBlank).joinToString("\n"),
            attachments = attachments,
            warnings = warnings,
        )
    }

    fun mapForQq(chain: List<PluginMessageSegment>): PluginMessagePlatformMapping {
        val textParts = mutableListOf<String>()
        val attachments = mutableListOf<PluginV2MessageAttachmentRef>()
        val warnings = mutableListOf<PluginMessageSendWarning>()
        chain.forEach { segment ->
            when (segment) {
                is PluginTextSegment -> textParts += segment.text
                is PluginMentionSegment -> {
                    textParts += "[CQ:at,qq=${segment.userId}]"
                    warnings += fallback("qq_segment_fallback", segment.type)
                }
                is PluginReplySegment -> {
                    textParts += "[CQ:reply,id=${segment.messageId}]"
                    warnings += fallback("qq_segment_fallback", segment.type)
                }
                is PluginImageSegment -> attachments += PluginV2MessageAttachmentRef(
                    uri = PluginMessageSegmentParser.validateMediaRef(segment.uri),
                    mimeType = segment.mimeType.ifBlank { "image/*" },
                )
                is PluginFileSegment -> {
                    attachments += PluginV2MessageAttachmentRef(
                        uri = PluginMessageSegmentParser.validateMediaRef(segment.uri),
                        mimeType = segment.mimeType.ifBlank { "application/octet-stream" },
                    )
                    warnings += fallback("qq_segment_fallback", segment.type)
                }
                is PluginCardSegment -> {
                    textParts += listOf(segment.title, segment.text, segment.url)
                        .map(String::trim)
                        .filter(String::isNotBlank)
                        .joinToString(" ")
                    warnings += fallback("qq_segment_fallback", segment.type)
                }
            }
        }
        return PluginMessagePlatformMapping(
            text = textParts.filter(String::isNotBlank).joinToString(" "),
            attachments = attachments,
            warnings = warnings,
        )
    }

    private fun fallback(code: String, segmentType: String): PluginMessageSendWarning {
        return PluginMessageSendWarning(
            code = code,
            message = "Message segment was represented with platform fallback.",
            segmentType = segmentType,
        )
    }
}
