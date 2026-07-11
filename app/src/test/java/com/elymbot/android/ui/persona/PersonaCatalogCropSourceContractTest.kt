package com.elymbot.android.ui.persona

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonaCatalogCropSourceContractTest {
    @Test fun `catalog applies crop zoom and grid card square viewport`() {
        val source = File("../feature/persona/presentation/src/main/java/com/elymbot/android/feature/persona/presentation/PersonaCatalogUi.kt").readText()
        assertTrue(source.contains("graphicsLayer"))
        assertTrue(source.contains("scaleX = policy.scale"))
        assertTrue(source.contains("scaleY = policy.scale"))
        assertTrue(source.contains("aspectRatio(1f)"))
        assertTrue(source.contains("PersonaCoverViewport.PORTRAIT"))
        assertTrue(source.contains("PersonaCoverViewport.SQUARE"))
    }
}
