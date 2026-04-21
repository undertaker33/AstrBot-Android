package com.astrbot.android.data

import com.astrbot.android.feature.qq.data.NapCatLoginService
import com.astrbot.android.model.NapCatLoginState
import com.astrbot.android.core.runtime.secret.RuntimeSecretRepository
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NapCatLoginRepositoryTest {
    @After
    fun tearDown() {
        NapCatBridgeRepository.resetRuntimeStateForTests()
        NapCatLoginRepository.resetQrRefreshGuardsForTests()
        NapCatLoginService.resetForTests()
        RuntimeSecretRepository.setSecretsOverrideForTests(null)
        setLoginState(NapCatLoginState())
    }

    @Test
    fun refresh_preserves_logged_in_state_when_bridge_runtime_is_temporarily_unavailable() {
        NapCatBridgeRepository.resetRuntimeStateForTests()
        setLoginState(
            NapCatLoginState(
                bridgeReady = true,
                authenticated = true,
                isLogin = true,
                uin = "123456",
                nick = "Test QQ",
                statusText = "QQ login ready",
            ),
        )

        val state = NapCatLoginRepository.refresh()

        assertTrue(state.isLogin)
        assertFalse(state.bridgeReady)
        assertTrue(state.authenticated)
        assertEquals("123456", state.uin)
        assertEquals("Test QQ", state.nick)
        assertEquals("Bridge is not connected", state.statusText)
    }

    @Test
    fun refresh_qr_code_ignores_rapid_repeat_requests() {
        NapCatBridgeRepository.resetRuntimeStateForTests()
        NapCatBridgeRepository.markRunning()
        setLoginState(
            NapCatLoginState(
                bridgeReady = true,
                isLogin = false,
                qrCodeUrl = "",
                statusText = "Waiting for login QR code",
            ),
        )
        RuntimeSecretRepository.setSecretsOverrideForTests(
            RuntimeSecretRepository.RuntimeSecrets(
                webUiToken = "runtime-webui-token",
                webUiJwtSecret = "runtime-jwt-secret",
            ),
        )

        var refreshQrCodeCalls = 0
        NapCatLoginService.setPostJsonOverrideForTests { endpoint, _, _ ->
            when {
                endpoint.endsWith("/auth/login") -> JSONObject().apply {
                    put("code", 0)
                    put("data", JSONObject().put("Credential", "stable-token"))
                }

                endpoint.endsWith("/QQLogin/RefreshQRcode") -> JSONObject().apply {
                    refreshQrCodeCalls += 1
                    put("code", 0)
                    put("message", "success")
                    put("data", JSONObject())
                }

                endpoint.endsWith("/QQLogin/CheckLoginStatus") -> JSONObject().apply {
                    put("code", 0)
                    put(
                        "data",
                        JSONObject().apply {
                            put("isLogin", false)
                            put("isOffline", false)
                            put("qrcodeurl", "")
                        },
                    )
                }

                else -> error("Unexpected endpoint: $endpoint")
            }
        }

        NapCatLoginRepository.refreshQrCode()
        NapCatLoginRepository.refreshQrCode()

        assertEquals(1, refreshQrCodeCalls)
    }

    @Suppress("UNCHECKED_CAST")
    private fun setLoginState(state: NapCatLoginState) {
        val field = NapCatLoginRepository::class.java.getDeclaredField("_loginState").apply {
            isAccessible = true
        }
        val flow = field.get(NapCatLoginRepository) as MutableStateFlow<NapCatLoginState>
        flow.value = state
    }
}
