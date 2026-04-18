package com.astrbot.android.core.runtime.tool

data class ToolDescriptorCacheKey(
    val kind: ToolSourceKind,
    val botId: String,
    val configId: String,
    val personaId: String,
)

interface ToolDescriptorCachePolicy {
    fun isCacheable(kind: ToolSourceKind): Boolean
    fun ttlMillis(kind: ToolSourceKind): Long
}

class DefaultToolDescriptorCachePolicy(
    private val ttlMillisByKind: Map<ToolSourceKind, Long> = defaultTtls(),
    private val nonCacheableKinds: Set<ToolSourceKind> = setOf(ToolSourceKind.WEB_SEARCH),
) : ToolDescriptorCachePolicy {

    override fun isCacheable(kind: ToolSourceKind): Boolean = kind !in nonCacheableKinds

    override fun ttlMillis(kind: ToolSourceKind): Long = ttlMillisByKind[kind] ?: DEFAULT_TTL_MILLIS

    private companion object {
        const val DEFAULT_TTL_MILLIS: Long = 30_000L

        fun defaultTtls(): Map<ToolSourceKind, Long> = ToolSourceKind.entries.associateWith { DEFAULT_TTL_MILLIS }
    }
}
