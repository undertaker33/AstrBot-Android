package com.astrbot.android.feature.provider

import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderType
import com.astrbot.android.model.defaultCapability
import com.astrbot.android.model.visibleProviderTypesFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderCatalogSearchCapabilityTest {
    @Test
    fun search_provider_types_default_to_search_capability() {
        assertEquals(ProviderCapability.SEARCH, ProviderType.TAVILY_SEARCH.defaultCapability())
        assertEquals(ProviderCapability.SEARCH, ProviderType.BRAVE_SEARCH.defaultCapability())
        assertEquals(ProviderCapability.SEARCH, ProviderType.BOCHA_SEARCH.defaultCapability())
        assertEquals(ProviderCapability.SEARCH, ProviderType.BAIDU_AI_SEARCH.defaultCapability())
    }

    @Test
    fun search_provider_types_are_visible_only_in_search_catalog() {
        val searchTypes = visibleProviderTypesFor(ProviderCapability.SEARCH)

        assertTrue(ProviderType.TAVILY_SEARCH in searchTypes)
        assertTrue(ProviderType.BRAVE_SEARCH in searchTypes)
        assertTrue(ProviderType.BOCHA_SEARCH in searchTypes)
        assertTrue(ProviderType.BAIDU_AI_SEARCH in searchTypes)
        assertTrue(ProviderType.TAVILY_SEARCH !in visibleProviderTypesFor(ProviderCapability.CHAT))
    }
}
