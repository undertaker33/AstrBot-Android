package com.elymbot.android.feature.persona.data

import com.elymbot.android.data.db.PersonaAggregate
import com.elymbot.android.data.db.PersonaCoverAssetEntity
import com.elymbot.android.data.db.PersonaEnabledToolEntity
import com.elymbot.android.data.db.PersonaEntity
import com.elymbot.android.data.db.PersonaPromptEntity
import com.elymbot.android.data.db.PersonaTagEntity
import com.elymbot.android.data.db.toProfile
import com.elymbot.android.data.db.toWriteModel
import com.elymbot.android.feature.persona.domain.model.PersonaCoverMetadata
import com.elymbot.android.feature.persona.domain.model.PersonaCropSpec
import com.elymbot.android.feature.persona.domain.model.PersonaProfile
import com.elymbot.android.feature.persona.domain.model.normalizePersonaTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PersonaMetadataContractTest {
    @Test fun tags_are_trimmed_deduplicated_and_limited_to_three() {
        assertEquals(listOf("A", "B", "C"), normalizePersonaTags(listOf(" A ", "", "A", "B", "C", "D")))
        assertEquals(listOf("A", "B", "C"), normalizePersonaTags(" A，B,, C,D"))
    }

    @Test fun crop_rejects_non_finite_out_of_range_or_non_positive_values() {
        assertThrows(IllegalArgumentException::class.java) { PersonaCropSpec(Float.NaN, .5f, 1f) }
        assertThrows(IllegalArgumentException::class.java) { PersonaCropSpec(-.1f, .5f, 1f) }
        assertThrows(IllegalArgumentException::class.java) { PersonaCropSpec(.5f, .5f, 0f) }
    }

    @Test fun mapper_round_trip_preserves_ordered_tags_and_cover() {
        val cover = PersonaCoverMetadata("persona-covers/p/a.webp", "abc", 1200, 1600, PersonaCropSpec(.4f, .6f, 1.2f), PersonaCropSpec(.5f, .5f, 1f), 9L)
        val profile = PersonaProfile("p", "P", listOf("one", "two", "three"), "prompt", setOf("tool"), cover = cover)
        val write = profile.toWriteModel(0)
        val aggregate = PersonaAggregate(write.persona, listOf(write.prompt), write.enabledTools, write.tags.reversed(), write.cover?.let(::listOf).orEmpty())
        assertEquals(profile, aggregate.toProfile())
    }
}
