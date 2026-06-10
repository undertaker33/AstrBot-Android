package com.elymbot.android.architecture

import java.io.File
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeofenceMapsApiKeyContractTest {
    private val manifestPath: Path = listOf(
        Path.of("app/src/main/AndroidManifest.xml"),
        Path.of("src/main/AndroidManifest.xml"),
    ).first { it.exists() }

    @Test
    fun manifest_does_not_require_maps_api_key_placeholders_for_map_picking() {
        val manifest = manifestPath.readText()

        assertFalse(manifest.contains("com.google.android.geo.API_KEY"))
        assertFalse(manifest.contains("\${MAPS_API_KEY}"))
        assertFalse(manifest.contains("com.elymbot.android.geofence.AMAP_WEB_SERVICE_KEY"))
        assertFalse(manifest.contains("\${AMAP_WEB_SERVICE_KEY}"))
        assertFalse(manifest.contains("YOUR_API_KEY"))
    }

    @Test
    fun production_sources_do_not_contain_literal_google_maps_api_key() {
        val keyPattern = Regex("AIza[0-9A-Za-z_-]{10,}")
        val productionFiles = listOf("app/src/main", "feature/geofence")
            .flatMap(::candidateRoots)
            .flatMap { root ->
                root.walkTopDown()
                    .filter { it.isFile }
                    .filter { it.extension in setOf("kt", "kts", "xml", "properties") }
                    .toList()
            }

        val offenders = productionFiles.filter { file -> keyPattern.containsMatchIn(file.readText()) }

        assertTrue("Hard-coded Maps API key found in: ${offenders.joinToString { it.path }}", offenders.isEmpty())
    }

    private fun candidateRoots(path: String): List<File> {
        // skipcq: KT-W1051
        return listOf(File(path), File("../$path")).filter { it.exists() }
    }
}
