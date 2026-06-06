package com.elymbot.android.architecture

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

    private val networkSecurityConfigPath: Path = listOf(
        Path.of("app/src/main/res/xml/network_security_config.xml"),
        Path.of("src/main/res/xml/network_security_config.xml"),
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

    @Test
    fun manifest_must_keep_runtime_foreground_service_data_sync_type() {
        val manifest = manifestPath.readText()

        assertTrue(
            "Runtime container service must keep FOREGROUND_SERVICE permission",
            manifest.contains("""<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />"""),
        )
        assertTrue(
            "Runtime container service must keep Android 14+ dataSync foreground service permission",
            manifest.contains("""<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />"""),
        )
        assertTrue(
            "Runtime container service must declare foregroundServiceType=dataSync",
            Regex(
                """<service\s+[\s\S]*android:name="\.core\.runtime\.container\.ContainerBridgeService"[\s\S]*android:foregroundServiceType="dataSync"[\s\S]*/>""",
            ).containsMatchIn(manifest),
        )
    }

    @Test
    fun manifest_must_register_app_update_install_boundaries() {
        val manifest = manifestPath.readText()
        val updateFilePaths = listOf(
            Path.of("app/src/main/res/xml/update_file_paths.xml"),
            Path.of("src/main/res/xml/update_file_paths.xml"),
        ).first { it.exists() }.readText()

        assertTrue(
            "App update install flow must declare REQUEST_INSTALL_PACKAGES",
            manifest.contains("""<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />"""),
        )
        assertTrue(
            "App update APK sharing must use the app FileProvider authority",
            manifest.contains("""android:name="androidx.core.content.FileProvider"""") &&
                manifest.contains("""android:authorities="${'$'}{applicationId}.fileprovider""""),
        )
        assertTrue(
            "App update FileProvider must not be exported and must grant uri permissions",
            manifest.contains("""android:exported="false"""") &&
                manifest.contains("""android:grantUriPermissions="true""""),
        )
        assertTrue(
            "App update FileProvider must point at @xml/update_file_paths",
            manifest.contains("""android:resource="@xml/update_file_paths""""),
        )
        assertTrue(
            "App update cleanup receiver must handle MY_PACKAGE_REPLACED",
            manifest.contains("""android:name=".update.AppUpdateCleanupReceiver"""") &&
                manifest.contains("""android.intent.action.MY_PACKAGE_REPLACED"""),
        )
        assertTrue(
            "App update FileProvider must expose only the app-updates filesDir subtree",
            updateFilePaths.contains("""<files-path""") &&
                updateFilePaths.contains("""path="app-updates/""""),
        )
    }

    @Test
    fun manifest_must_disable_global_cleartext_and_backup() {
        val manifest = manifestPath.readText()

        assertTrue(
            "AndroidManifest.xml must not globally allow cleartext traffic",
            !manifest.contains("""android:usesCleartextTraffic="true""""),
        )
        assertTrue(
            "AndroidManifest.xml must explicitly disable cleartext traffic",
            manifest.contains("""android:usesCleartextTraffic="false""""),
        )
        assertTrue(
            "AndroidManifest.xml must disable Android system backup because runtime secrets live under filesDir",
            manifest.contains("""android:allowBackup="false""""),
        )
    }

    @Test
    fun network_security_config_must_only_allow_loopback_cleartext() {
        val networkConfig = networkSecurityConfigPath.readText()

        assertTrue(
            "network_security_config.xml must keep loopback cleartext for local runtime integrations",
            networkConfig.contains("127.0.0.1") && networkConfig.contains("localhost"),
        )
        assertTrue(
            "network_security_config.xml must not allow external OSS cleartext traffic",
            !networkConfig.contains("dashscope-result-bj.oss-cn-beijing.aliyuncs.com"),
        )
        assertTrue(
            "network_security_config.xml must not define multiple cleartext=true domain-config blocks",
            Regex("""<domain-config\s+cleartextTrafficPermitted="true"""")
                .findAll(networkConfig)
                .count() == 1,
        )
    }
}
