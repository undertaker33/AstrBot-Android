package com.elymbot.android.feature.persona.presentation

import com.elymbot.android.feature.persona.domain.model.PersonaProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonaCatalogStateTest {
    @Test fun `opening overlays is mutually exclusive and closing search preserves query`() {
        var state = PersonaPageUiState(searchQuery = "灵感")
        state = reducePersonaPage(state, PersonaPageAction.OpenOverlay(PersonaTopOverlay.SEARCH))
        state = reducePersonaPage(state, PersonaPageAction.OpenOverlay(PersonaTopOverlay.FILTER))
        assertEquals(PersonaTopOverlay.FILTER, state.topOverlay)
        assertEquals("灵感", state.searchQuery)
        state = reducePersonaPage(state, PersonaPageAction.DismissOverlay)
        assertEquals(PersonaTopOverlay.NONE, state.topOverlay)
        assertEquals("灵感", state.searchQuery)
    }

    @Test fun `filter reset changes pending and apply commits while dismiss discards`() {
        val initial = PersonaPageUiState(appliedTagFilters = setOf("写作"))
        val opened = reducePersonaPage(initial, PersonaPageAction.OpenOverlay(PersonaTopOverlay.FILTER))
        assertEquals(setOf("写作"), opened.pendingTagFilters)
        val reset = reducePersonaPage(opened, PersonaPageAction.ResetPendingFilters)
        assertTrue(reset.pendingTagFilters.isEmpty())
        assertEquals(setOf("写作"), reset.appliedTagFilters)
        val dismissed = reducePersonaPage(reset, PersonaPageAction.DismissOverlay)
        assertEquals(setOf("写作"), dismissed.pendingTagFilters)
        val applied = reducePersonaPage(reset, PersonaPageAction.ApplyPendingFilters)
        assertTrue(applied.appliedTagFilters.isEmpty())
    }

    @Test fun `search matches name or any tag and multi tag filter uses OR`() {
        val personas = listOf(
            profile("a", "Atlas", listOf("效率", "分析")),
            profile("b", "Nova", listOf("创意", "写作")),
            profile("c", "Orion", listOf("沟通")),
        )
        assertEquals(listOf("a"), filterPersonas(personas, "分析", emptySet()).map { it.id })
        assertEquals(listOf("b"), filterPersonas(personas, "nov", emptySet()).map { it.id })
        assertEquals(listOf("a", "c"), filterPersonas(personas, "", setOf("效率", "沟通")).map { it.id })
    }

    @Test fun `paging exposes correct first last and single arrow states`() {
        assertFalse(personaPagerArrows(1, 0).previousVisible)
        assertFalse(personaPagerArrows(1, 0).nextVisible)
        assertFalse(personaPagerArrows(3, 0).previousVisible)
        assertTrue(personaPagerArrows(3, 0).nextVisible)
        assertTrue(personaPagerArrows(3, 2).previousVisible)
        assertFalse(personaPagerArrows(3, 2).nextVisible)
    }

    private fun profile(id: String, name: String, tags: List<String>) = PersonaProfile(
        id = id, name = name, tags = tags, systemPrompt = "", enabledTools = emptySet(),
    )
}
