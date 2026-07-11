package com.elymbot.android.data.db.persona

import com.elymbot.android.data.db.PersonaAggregate
import com.elymbot.android.data.db.PersonaEnabledToolEntity
import com.elymbot.android.data.db.PersonaEntity
import com.elymbot.android.data.db.PersonaPromptEntity
import com.elymbot.android.data.db.PersonaTagEntity
import com.elymbot.android.data.db.toProfile
import com.elymbot.android.data.db.toWriteModel
import com.elymbot.android.model.PersonaProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class PersonaMappersTest {
    @Test
    fun aggregate_toProfile_restoresPromptAndTools() {
        val profile = PersonaAggregate(
            persona = PersonaEntity("default", "Default", "provider", 12, true, 0, 1L),
            prompts = listOf(PersonaPromptEntity("default", "prompt")),
            enabledTools = listOf(PersonaEnabledToolEntity("default", "search", 0)),
            tags = listOf(PersonaTagEntity("default", "tag", 0)),
        ).toProfile()

        assertEquals("prompt", profile.systemPrompt)
        assertEquals(setOf("search"), profile.enabledTools)
        assertEquals(listOf("tag"), profile.tags)
    }

    @Test
    fun profile_toWriteModel_flattensPromptAndTools() {
        val writeModel = PersonaProfile(id = "default", name = "Default", systemPrompt = "prompt", enabledTools = setOf("search")).toWriteModel(sortIndex = 0)
        assertEquals("prompt", writeModel.prompt.systemPrompt)
        assertEquals(1, writeModel.enabledTools.size)
    }
}
