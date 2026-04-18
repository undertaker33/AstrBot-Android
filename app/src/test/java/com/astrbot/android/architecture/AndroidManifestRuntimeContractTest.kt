package com.astrbot.android.architecture

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidManifestRuntimeContractTest {

    private val manifestPath: Path = listOf(
        Path.of("app/src/main/AndroidManifest.xml"),
        Path.of("src/main/AndroidManifest.xml"),
    ).first { it.exists() }

    @Test
    fun manifest_must_register_core_container_bridge_service() {
        val manifest = manifestPath.readText()
        assertTrue("AndroidManifest.xml must exist", manifest.isNotBlank())
        assertTrue(
            "Bridge service must be registered at .core.runtime.container.ContainerBridgeService after phase 7 migration",
            manifest.contains("""android:name=".core.runtime.container.ContainerBridgeService""""),
        )
        assertTrue(
            "AndroidManifest.xml must not keep the legacy .runtime.ContainerBridgeService entry",
            !manifest.contains("""android:name=".runtime.ContainerBridgeService""""),
        )
    }
}
