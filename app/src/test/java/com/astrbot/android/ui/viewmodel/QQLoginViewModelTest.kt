package com.astrbot.android.ui.viewmodel

import com.astrbot.android.MainDispatcherRule
import com.astrbot.android.model.NapCatLoginState
import com.astrbot.android.feature.qq.data.NapCatLoginService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QQLoginViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

    @Test
    fun refresh_now_uses_manual_refresh_flag() = runTest(dispatcher) {
        val deps = FakeQQLoginDependencies()
        val viewModel = QQLoginViewModel(deps, dispatcher)

        viewModel.refreshNow()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(true), deps.refreshCalls)
    }

    @Test
    fun visible_screen_starts_polling_and_hidden_screen_stops_it() = runTest(dispatcher) {
        val deps = FakeQQLoginDependencies()
        val viewModel = QQLoginViewModel(deps, dispatcher)

        viewModel.onScreenVisible("qq-login", "marker-v1")
        runCurrent()
        val refreshCountAfterStart = deps.refreshCalls.size

        assertTrue(refreshCountAfterStart >= 1)
        assertEquals(listOf("marker-v1"), deps.loggedMessages.filter { it == "marker-v1" })

        viewModel.onScreenHidden("qq-login")
        dispatcher.scheduler.advanceTimeBy(6000)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(refreshCountAfterStart, deps.refreshCalls.size)
    }

    @Test
    fun blank_qr_state_triggers_single_automatic_qr_refresh_when_screen_becomes_visible() = runTest(dispatcher) {
        val deps = FakeQQLoginDependencies(
            initialState = NapCatLoginState(
                bridgeReady = true,
                isLogin = false,
                qrCodeUrl = "",
                statusText = "Waiting for login QR code",
            ),
        )
        val viewModel = QQLoginViewModel(deps, dispatcher)

        try {
            viewModel.onScreenVisible("qq-login", "marker-v1")
            runCurrent()

            assertEquals(listOf(false), deps.refreshCalls)
            assertEquals(1, deps.refreshQrCodeCalls)
        } finally {
            viewModel.onScreenHidden("qq-login")
            runCurrent()
        }
    }

    @Test
    fun logged_out_polling_does_not_repeat_automatic_qr_refresh_every_interval() = runTest(dispatcher) {
        val deps = FakeQQLoginDependencies(
            initialState = NapCatLoginState(
                bridgeReady = true,
                isLogin = false,
                qrCodeUrl = "",
                statusText = "Waiting for login QR code",
            ),
        )
        val viewModel = QQLoginViewModel(deps, dispatcher)

        try {
            viewModel.onScreenVisible("qq-login", "marker-v1")
            runCurrent()
            dispatcher.scheduler.advanceTimeBy(9_000)
            runCurrent()

            assertTrue(deps.refreshCalls.size >= 3)
            assertEquals(1, deps.refreshQrCodeCalls)
        } finally {
            viewModel.onScreenHidden("qq-login")
            runCurrent()
        }
    }

    @Test
    fun qq_account_visibility_keeps_polling_without_automatic_qr_refresh() = runTest(dispatcher) {
        val deps = FakeQQLoginDependencies(
            initialState = NapCatLoginState(
                bridgeReady = true,
                isLogin = false,
                qrCodeUrl = "",
                statusText = "Waiting for login QR code",
            ),
        )
        val viewModel = QQLoginViewModel(deps, dispatcher)

        try {
            viewModel.onScreenVisible("qq-account", "marker-v1")
            runCurrent()
            dispatcher.scheduler.advanceTimeBy(6_000)
            runCurrent()

            assertTrue(deps.refreshCalls.size >= 2)
            assertEquals(0, deps.refreshQrCodeCalls)
        } finally {
            viewModel.onScreenHidden("qq-account")
            runCurrent()
        }
    }

    private class FakeQQLoginDependencies(
        initialState: NapCatLoginState = NapCatLoginState(),
    ) : QQLoginViewModelBindings {
        private val state = MutableStateFlow(initialState)
        override val loginState: StateFlow<NapCatLoginState> = state
        val refreshCalls = mutableListOf<Boolean>()
        var refreshQrCodeCalls = 0
        val loggedMessages = mutableListOf<String>()

        override suspend fun refresh(manual: Boolean) {
            refreshCalls += manual
        }

        override suspend fun refreshQrCode() {
            refreshQrCodeCalls += 1
        }

        override suspend fun quickLoginSavedAccount(uin: String?) = Unit

        override suspend fun saveQuickLoginAccount(uin: String) = Unit

        override suspend fun logoutCurrentAccount() = Unit

        override suspend fun passwordLogin(uin: String, password: String) = Unit

        override suspend fun captchaLogin(
            uin: String,
            password: String,
            ticket: String,
            randstr: String,
            sid: String,
        ) = Unit

        override suspend fun newDeviceLogin(uin: String, password: String, verifiedToken: String?) = Unit

        override suspend fun getNewDeviceQRCode(): NapCatLoginService.NewDeviceQrCodeResult {
            error("Not needed in test")
        }

        override suspend fun pollNewDeviceQRCode(bytesToken: String): NapCatLoginService.NewDeviceQrPollResult {
            error("Not needed in test")
        }

        override fun log(message: String) {
            loggedMessages += message
        }
    }
}
