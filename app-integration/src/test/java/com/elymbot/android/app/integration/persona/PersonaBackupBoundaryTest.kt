package com.elymbot.android.app.integration.persona

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonaBackupBoundaryTest {
    @Test
    fun `backup adapter snapshots and restores through persona repository store`() {
        val source = File("src/main/java/com/elymbot/android/di/BackupDataPortAdapter.kt").readText()

        assertTrue(source.contains("override fun snapshotPersonas(): List<PersonaProfile> = personaRepository.snapshotProfiles()"))
        assertTrue(source.contains("override fun restorePersonas(profiles: List<PersonaProfile>)"))
        assertTrue(source.contains("personaRepository.restoreProfiles(profiles)"))
    }
}
