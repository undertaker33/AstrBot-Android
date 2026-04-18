package com.astrbot.android.feature.plugin.runtime

import com.astrbot.android.core.runtime.tool.ToolDescriptor
import com.astrbot.android.core.runtime.tool.ToolDescriptorCachePolicy
import com.astrbot.android.core.runtime.tool.ToolDescriptorVisibility
import com.astrbot.android.core.runtime.tool.ToolSourceKind
import com.astrbot.android.core.runtime.tool.ToolSourceProviderPort
import com.astrbot.android.core.runtime.tool.ToolSourceRequestContext
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PluginToolSourceBridgeTest {

    @Test
    fun descriptors_reads_provider_once_for_same_context_when_cacheable() = runTest {
        val provider = FakeToolSourceProvider(ToolSourceKind.SKILL)
        val bridge = PluginToolSourceBridge(
            provider = provider,
            cachePolicy = FakeCachePolicy(cacheableKinds = setOf(ToolSourceKind.SKILL)),
        )
        val context = requestContext()

        val first = bridge.descriptors(context)
        val second = bridge.descriptors(context)

        assertEquals(first, second)
        assertEquals(1, provider.requestCount)
    }

    @Test
    fun descriptors_reload_when_cache_key_changes() = runTest {
        val provider = FakeToolSourceProvider(ToolSourceKind.MCP)
        val bridge = PluginToolSourceBridge(
            provider = provider,
            cachePolicy = FakeCachePolicy(cacheableKinds = setOf(ToolSourceKind.MCP)),
        )

        bridge.descriptors(requestContext(configId = "config-a", personaId = "persona-a"))
        bridge.descriptors(requestContext(configId = "config-b", personaId = "persona-a"))
        bridge.descriptors(requestContext(configId = "config-b", personaId = "persona-b"))

        assertEquals(3, provider.requestCount)
    }

    @Test
    fun descriptors_bypass_cache_when_source_is_not_cacheable() = runTest {
        val provider = FakeToolSourceProvider(ToolSourceKind.WEB_SEARCH)
        val bridge = PluginToolSourceBridge(
            provider = provider,
            cachePolicy = FakeCachePolicy(cacheableKinds = emptySet()),
        )
        val context = requestContext()

        bridge.descriptors(context)
        bridge.descriptors(context)

        assertEquals(2, provider.requestCount)
    }

    private fun requestContext(
        configId: String = "config-1",
        personaId: String = "persona-1",
    ) = ToolSourceRequestContext(
        botId = "bot-1",
        configId = configId,
        personaId = personaId,
        conversationId = "conversation-1",
    )

    private class FakeToolSourceProvider(
        override val kind: ToolSourceKind,
    ) : ToolSourceProviderPort {
        var requestCount: Int = 0

        override suspend fun descriptors(context: ToolSourceRequestContext): List<ToolDescriptor> {
            requestCount += 1
            return listOf(
                ToolDescriptor(
                    id = "${kind.name.lowercase()}-$requestCount",
                    ownerId = "owner-$requestCount",
                    name = "tool-$requestCount",
                    description = "descriptor-$requestCount",
                    inputSchemaJson = """{"type":"object"}""",
                    source = kind,
                    visibility = ToolDescriptorVisibility.LLM_VISIBLE,
                ),
            )
        }
    }

    private class FakeCachePolicy(
        private val cacheableKinds: Set<ToolSourceKind>,
    ) : ToolDescriptorCachePolicy {
        override fun isCacheable(kind: ToolSourceKind): Boolean = kind in cacheableKinds

        override fun ttlMillis(kind: ToolSourceKind): Long = 60_000L
    }
}
