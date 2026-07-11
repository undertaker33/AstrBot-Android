package com.elymbot.android.feature.persona

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonaCoverArchitectureContractTest {
    @Test fun `cover API is Android free and production binding is Hilt owned`() {
        val api = File("../feature/persona/api/src/main/java/com/elymbot/android/feature/persona/domain/PersonaCoverAssetPort.kt").readText()
        assertFalse(api.contains("import android."))
        assertFalse(api.contains("java.io.File"))
        val binding = File("../app-integration/src/main/java/com/elymbot/android/app/integration/persona/PersonaRepositoryBindings.kt").readText()
        assertTrue(binding.contains("@InstallIn(SingletonComponent::class)"))
        assertTrue(binding.contains("bindCoverPort"))
        assertFalse(binding.contains("install("))
        assertFalse(binding.contains("configure("))
    }
}
