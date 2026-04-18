package com.astrbot.android.data

import com.astrbot.android.feature.qq.data.NapCatLoginService
import com.astrbot.android.data.http.AstrBotHttpClient
import com.astrbot.android.data.http.AstrBotHttpException
import com.astrbot.android.data.http.HttpFailureCategory
import com.astrbot.android.data.http.MultipartPartSpec
import com.astrbot.android.data.http.HttpRequestSpec
import com.astrbot.android.data.http.HttpResponsePayload
import com.astrbot.android.core.common.logging.RuntimeLogRepository
import com.astrbot.android.core.runtime.secret.RuntimeSecretRepository
import java.net.SocketTimeoutException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Test

class NapCatLoginServiceTest {
    @After
    fun tearDown() {
        RuntimeSecretRepository.setSecretsOverrideForTests(null)
        NapCatLoginService.resetForTests()
    }

    @Test
    fun classifies_unauthorized_message_as_expired_token() {
        assertEquals(
            NapCatLoginService.LoginFailureCategory.AUTH_TOKEN_EXPIRED,
            NapCatLoginService.classifyFailureForTests(
                message = "Unauthorized",
                cause = null,
            ),
        )
    }

    @Test
    fun classifies_token_is_invalid_message_as_expired_token() {
        assertEquals(
            NapCatLoginService.LoginFailureCategory.AUTH_TOKEN_EXPIRED,
            NapCatLoginService.classifyFailureForTests(
                message = "token is invalid",
                cause = null,
            ),
        )
    }

    @Test
    fun classifies_socket_timeout_as_network_failure() {
        assertEquals(
            NapCatLoginService.LoginFailureCategory.NETWORK_FAILURE,
            NapCatLoginService.classifyFailureForTests(
                message = null,
                cause = SocketTimeoutException("timeout"),
            ),
        )
    }

    @Test
    fun refresh_qr_code_maps_shared_http_client_timeout_to_network_failure() {
        NapCatLoginService.resetForTests()
        RuntimeLogRepository.clear()
        RuntimeSecretRepository.setSecretsOverrideForTests(
            RuntimeSecretRepository.RuntimeSecrets(
                webUiToken = "runtime-webui-token",
                webUiJwtSecret = "runtime-jwt-secret",
            ),
        )
        NapCatLoginService.setHttpClientOverrideForTests(
            object : AstrBotHttpClient {
                override fun execute(requestSpec: HttpRequestSpec): HttpResponsePayload {
                    throw AstrBotHttpException(
                        category = HttpFailureCategory.TIMEOUT,
                        message = "timeout",
                    )
                }

                override fun executeBytes(requestSpec: HttpRequestSpec): ByteArray {
                    throw UnsupportedOperationException("Not used in this test")
                }

                override suspend fun executeStream(
                    requestSpec: HttpRequestSpec,
                    onLine: suspend (String) -> Unit,
                ) {
                    throw UnsupportedOperationException("Not used in this test")
                }

                override fun executeMultipart(
                    requestSpec: HttpRequestSpec,
                    parts: List<MultipartPartSpec>,
                ): HttpResponsePayload {
                    throw UnsupportedOperationException("Not used in this test")
                }
            },
        )

        val error = runCatching {
            NapCatLoginService.refreshQrCode("http://127.0.0.1:6099")
        }.exceptionOrNull()

        requireNotNull(error)
        assertTrue(error.message.orEmpty().contains("timeout"))
        assertTrue(RuntimeLogRepository.logs.value.any { it.contains("category=NETWORK_FAILURE") })
    }

    @Test
    fun refresh_qr_code_clears_cached_credential_and_retries_when_token_expires() {
        NapCatLoginService.resetForTests()
        RuntimeLogRepository.clear()
        RuntimeSecretRepository.setSecretsOverrideForTests(
            RuntimeSecretRepository.RuntimeSecrets(
                webUiToken = "runtime-webui-token",
                webUiJwtSecret = "runtime-jwt-secret",
            ),
        )

        var authLoginCalls = 0
        var refreshCalls = 0
        NapCatLoginService.setPostJsonOverrideForTests { endpoint, _, authorization ->
            when {
                endpoint.endsWith("/auth/login") -> {
                    authLoginCalls += 1
                    assertEquals(null, authorization)
                    JSONObject().apply {
                        put("code", 0)
                        put("data", JSONObject().put("Credential", if (authLoginCalls == 1) "stale-token" else "fresh-token"))
                    }
                }

                endpoint.endsWith("/QQLogin/RefreshQRcode") -> {
                    refreshCalls += 1
                    JSONObject().apply {
                        if (refreshCalls == 1) {
                            put("code", 1)
                            put("message", "token is invalid")
                        } else {
                            put("code", 0)
                            put("message", "success")
                            put("data", JSONObject())
                        }
                    }
                }

                else -> error("Unexpected endpoint: $endpoint")
            }
        }

        NapCatLoginService.refreshQrCode("http://127.0.0.1:6099")

        assertEquals(2, authLoginCalls)
        assertEquals(2, refreshCalls)
        assertEquals("fresh-token", NapCatLoginService.debugCredentialForTests())
        assertTrue(RuntimeLogRepository.logs.value.any { it.contains("phase=request") && it.contains("category=AUTH_TOKEN_EXPIRED") })
        assertTrue(RuntimeLogRepository.logs.value.any { it.contains("phase=auth-relogin") && it.contains("/auth/login") })
        assertTrue(RuntimeLogRepository.logs.value.any { it.contains("phase=retry") && it.contains("/QQLogin/RefreshQRcode") })
        assertTrue(
            RuntimeLogRepository.logs.value.any {
                it.contains("QQ login auth request:") &&
                    it.contains("configuredToken=<redacted:19>")
            },
        )
    }

    @Test
    fun refresh_qr_code_surfaces_auth_login_failed_when_relogin_fails() {
        NapCatLoginService.resetForTests()
        RuntimeLogRepository.clear()
        RuntimeSecretRepository.setSecretsOverrideForTests(
            RuntimeSecretRepository.RuntimeSecrets(
                webUiToken = "runtime-webui-token",
                webUiJwtSecret = "runtime-jwt-secret",
            ),
        )

        var authLoginCalls = 0
        NapCatLoginService.setPostJsonOverrideForTests { endpoint, _, _ ->
            when {
                endpoint.endsWith("/auth/login") -> {
                    authLoginCalls += 1
                    if (authLoginCalls == 1) {
                        JSONObject().apply {
                            put("code", 0)
                            put("data", JSONObject().put("Credential", "stale-token"))
                        }
                    } else {
                        JSONObject().apply {
                            put("code", 1)
                            put("message", "login down")
                        }
                    }
                }

                endpoint.endsWith("/QQLogin/RefreshQRcode") -> JSONObject().apply {
                    put("code", 1)
                    put("message", "token is invalid")
                }

                else -> error("Unexpected endpoint: $endpoint")
            }
        }

        val error = runCatching {
            NapCatLoginService.refreshQrCode("http://127.0.0.1:6099")
        }.exceptionOrNull()

        requireNotNull(error)
        assertTrue(error.message.orEmpty().contains("login down"))
        assertEquals("", NapCatLoginService.debugCredentialForTests())
        assertTrue(
            RuntimeLogRepository.logs.value.any {
                it.contains("QQ login auth request:") &&
                    it.contains("strategy=sha256(token.napcat)") &&
                    it.contains("path=/auth/login") &&
                    it.contains("configuredToken=<redacted:19>")
            },
        )
        assertTrue(RuntimeLogRepository.logs.value.any { it.contains("phase=auth-relogin") && it.contains("category=AUTH_LOGIN_FAILED") })
    }

    @Test
    fun refresh_qr_code_does_not_retry_for_non_auth_api_failure() {
        NapCatLoginService.resetForTests()
        RuntimeLogRepository.clear()
        RuntimeSecretRepository.setSecretsOverrideForTests(
            RuntimeSecretRepository.RuntimeSecrets(
                webUiToken = "runtime-webui-token",
                webUiJwtSecret = "runtime-jwt-secret",
            ),
        )

        var authLoginCalls = 0
        var refreshCalls = 0
        NapCatLoginService.setPostJsonOverrideForTests { endpoint, _, _ ->
            when {
                endpoint.endsWith("/auth/login") -> {
                    authLoginCalls += 1
                    JSONObject().apply {
                        put("code", 0)
                        put("data", JSONObject().put("Credential", "stable-token"))
                    }
                }

                endpoint.endsWith("/QQLogin/RefreshQRcode") -> {
                    refreshCalls += 1
                    JSONObject().apply {
                        put("code", 1)
                        put("message", "qq offline")
                    }
                }

                else -> error("Unexpected endpoint: $endpoint")
            }
        }

        val error = runCatching {
            NapCatLoginService.refreshQrCode("http://127.0.0.1:6099")
        }.exceptionOrNull()

        requireNotNull(error)
        assertTrue(error.message.orEmpty().contains("qq offline"))
        assertEquals(1, authLoginCalls)
        assertEquals(1, refreshCalls)
        assertEquals("stable-token", NapCatLoginService.debugCredentialForTests())
        assertTrue(RuntimeLogRepository.logs.value.any { it.contains("phase=request") && it.contains("category=API_BUSINESS_REJECTED") })
    }
}
