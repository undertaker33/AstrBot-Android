package com.elymbot.android.app.integration.persona

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonaCoverSourceSafetyContractTest {
    private val source = File("../feature/persona/data/src/main/java/com/elymbot/android/feature/persona/data/AndroidPersonaCoverSourceImporter.kt").readText()

    @Test fun `source URI integration is read only`() {
        assertTrue(source.contains("contentResolver.openInputStream"))
        assertFalse(source.contains("contentResolver.delete"))
        assertFalse(source.contains("ContentResolver.delete"))
        assertFalse(source.contains("DocumentFile"))
        assertFalse(source.contains("openOutputStream"))
    }

    @Test fun `all EXIF orientation transforms and edge normalization are handled`() {
        listOf("ORIENTATION_FLIP_HORIZONTAL", "ORIENTATION_ROTATE_180", "ORIENTATION_FLIP_VERTICAL",
            "ORIENTATION_TRANSPOSE", "ORIENTATION_ROTATE_90", "ORIENTATION_TRANSVERSE", "ORIENTATION_ROTATE_270")
            .forEach { assertTrue("missing $it", source.contains(it)) }
        assertTrue(source.contains("MAX_EDGE = 2048"))
        assertTrue(source.contains("Bitmap.createScaledBitmap"))
        assertTrue(source.contains("normalized.compress"))
    }
}
