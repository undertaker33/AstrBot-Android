package com.elymbot.android.feature.persona.domain.model

data class PersonaProfile(
    val id: String,
    val name: String,
    val tags: List<String> = emptyList(),
    val systemPrompt: String,
    val enabledTools: Set<String>,
    val defaultProviderId: String = "",
    val maxContextMessages: Int = 12,
    val enabled: Boolean = true,
    val cover: PersonaCoverMetadata? = null,
) {
    val tag: String get() = tags.firstOrNull().orEmpty()

    constructor(id: String, name: String, tag: String, systemPrompt: String, enabledTools: Set<String>, defaultProviderId: String = "", maxContextMessages: Int = 12, enabled: Boolean = true) :
        this(id, name, normalizePersonaTags(tag), systemPrompt, enabledTools, defaultProviderId, maxContextMessages, enabled)

    fun copy(
        name: String = this.name,
        tag: String,
        systemPrompt: String = this.systemPrompt,
        enabledTools: Set<String> = this.enabledTools,
        defaultProviderId: String = this.defaultProviderId,
        maxContextMessages: Int = this.maxContextMessages,
        enabled: Boolean = this.enabled,
    ): PersonaProfile = copy(
        name = name,
        tags = normalizePersonaTags(tag),
        systemPrompt = systemPrompt,
        enabledTools = enabledTools,
        defaultProviderId = defaultProviderId,
        maxContextMessages = maxContextMessages,
        enabled = enabled,
    )
}

data class PersonaCropSpec(val centerX: Float = .5f, val centerY: Float = .5f, val zoom: Float = 1f) {
    init {
        require(centerX.isFinite() && centerX in 0f..1f)
        require(centerY.isFinite() && centerY in 0f..1f)
        require(zoom.isFinite() && zoom in 0.1f..10f)
    }
}

data class PersonaCoverMetadata(
    val assetRef: String,
    val contentSha256: String,
    val pixelWidth: Int,
    val pixelHeight: Int,
    val portraitCrop: PersonaCropSpec,
    val squareCrop: PersonaCropSpec,
    val updatedAt: Long,
) {
    init {
        require(assetRef.isNotBlank() && !assetRef.startsWith('/') && !assetRef.contains(".."))
        require(pixelWidth > 0 && pixelHeight > 0)
    }
}

fun normalizePersonaTags(tags: Iterable<String>): List<String> = tags
    .map(String::trim).filter(String::isNotBlank).distinct().take(3)

fun normalizePersonaTags(value: String): List<String> =
    normalizePersonaTags(value.replace('，', ',').split(','))
