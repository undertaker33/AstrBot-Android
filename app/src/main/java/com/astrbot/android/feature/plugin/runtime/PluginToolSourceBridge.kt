package com.astrbot.android.feature.plugin.runtime

import com.astrbot.android.core.runtime.tool.ToolDescriptor
import com.astrbot.android.core.runtime.tool.ToolDescriptorCacheKey
import com.astrbot.android.core.runtime.tool.ToolDescriptorCachePolicy
import com.astrbot.android.core.runtime.tool.ToolSourceKind
import com.astrbot.android.core.runtime.tool.ToolSourceProviderPort
import com.astrbot.android.core.runtime.tool.ToolSourceRequestContext
import java.util.concurrent.ConcurrentHashMap

class PluginToolSourceBridge(
    private val provider: ToolSourceProviderPort,
    private val cachePolicy: ToolDescriptorCachePolicy,
) : ToolSourceProviderPort {

    override val kind: ToolSourceKind = provider.kind

    private val cache = ConcurrentHashMap<ToolDescriptorCacheKey, CacheEntry>()

    override suspend fun descriptors(context: ToolSourceRequestContext): List<ToolDescriptor> {
        if (!cachePolicy.isCacheable(kind)) {
            return provider.descriptors(context)
        }
        val key = ToolDescriptorCacheKey(
            kind = kind,
            botId = context.botId,
            configId = context.configId,
            personaId = context.personaId,
        )
        val now = System.currentTimeMillis()
        val entry = cache[key]
        if (entry != null && now - entry.createdAtMillis < cachePolicy.ttlMillis(kind)) {
            return entry.descriptors
        }
        val result = provider.descriptors(context)
        cache[key] = CacheEntry(result, now)
        return result
    }

    fun invalidate() {
        cache.clear()
    }

    private data class CacheEntry(
        val descriptors: List<ToolDescriptor>,
        val createdAtMillis: Long,
    )
}
