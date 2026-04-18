package com.astrbot.android.core.runtime.container

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeSecretRepositoryTest {
    @Test
    fun load_or_create_secrets_persists_same_values_across_reads() {
        val rootDir = createTempDirectory("runtime-secrets").toFile()
        val first = RuntimeSecretRepository.loadOrCreateSecretsForTests(
            rootDir = rootDir,
            randomBytes = { size -> ByteArray(size) { index -> (index + 1).toByte() } },
        )

        val second = RuntimeSecretRepository.loadOrCreateSecretsForTests(
            rootDir = rootDir,
            randomBytes = { size -> ByteArray(size) { index -> (index + 99).toByte() } },
        )

        assertEquals(first, second)
        assertEquals(64, first.webUiToken.length)
        assertEquals(64, first.webUiJwtSecret.length)
        assertTrue(first.webUiToken.all { it in '0'..'9' || it in 'a'..'f' })
        assertTrue(first.webUiJwtSecret.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun load_or_create_secrets_writes_shell_consumable_env_file() {
        val rootDir = createTempDirectory("runtime-secrets-env").toFile()

        val secrets = RuntimeSecretRepository.loadOrCreateSecretsForTests(
            rootDir = rootDir,
            randomBytes = { size -> ByteArray(size) { index -> (size - index).toByte() } },
        )

        val envFile = File(rootDir, "config/runtime-secrets.env")
        val envText = envFile.readText()

        assertTrue(envText.contains("NAPCAT_WEBUI_SECRET_KEY=${secrets.webUiToken}"))
        assertTrue(envText.contains("NAPCAT_WEBUI_JWT_SECRET_KEY=${secrets.webUiJwtSecret}"))
        assertTrue(envText.contains("ASTRBOT_RUNTIME_SECRET_VERSION=1"))
    }
}
