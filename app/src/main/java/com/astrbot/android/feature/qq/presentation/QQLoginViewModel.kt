package com.astrbot.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astrbot.android.core.common.logging.RuntimeLogRepository
import com.astrbot.android.feature.qq.data.NapCatLoginService
import com.astrbot.android.feature.qq.data.NapCatLoginRepository
import com.astrbot.android.di.hilt.IoDispatcher
import com.astrbot.android.di.hilt.QqLoginState
import com.astrbot.android.model.NapCatLoginState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface QQLoginViewModelBindings {
    val loginState: StateFlow<NapCatLoginState>

    suspend fun refresh(manual: Boolean = false)

    suspend fun refreshQrCode()

    suspend fun quickLoginSavedAccount(uin: String? = null)

    suspend fun saveQuickLoginAccount(uin: String)

    suspend fun logoutCurrentAccount()

    suspend fun passwordLogin(uin: String, password: String)

    suspend fun captchaLogin(uin: String, password: String, ticket: String, randstr: String, sid: String)

    suspend fun newDeviceLogin(uin: String, password: String, verifiedToken: String?)

    suspend fun getNewDeviceQRCode(): NapCatLoginService.NewDeviceQrCodeResult

    suspend fun pollNewDeviceQRCode(bytesToken: String): NapCatLoginService.NewDeviceQrPollResult

    fun log(message: String)
}

internal class DefaultQQLoginViewModelBindings @Inject constructor(
    @QqLoginState override val loginState: StateFlow<NapCatLoginState>,
) : QQLoginViewModelBindings {
    override suspend fun refresh(manual: Boolean) {
        NapCatLoginRepository.refresh(manual)
    }

    override suspend fun refreshQrCode() {
        NapCatLoginRepository.refreshQrCode()
    }

    override suspend fun quickLoginSavedAccount(uin: String?) {
        NapCatLoginRepository.quickLoginSavedAccount(uin)
    }

    override suspend fun saveQuickLoginAccount(uin: String) {
        NapCatLoginRepository.saveQuickLoginAccount(uin)
    }

    override suspend fun logoutCurrentAccount() {
        NapCatLoginRepository.logoutCurrentAccount()
    }

    override suspend fun passwordLogin(uin: String, password: String) {
        NapCatLoginRepository.passwordLogin(uin, password)
    }

    override suspend fun captchaLogin(
        uin: String,
        password: String,
        ticket: String,
        randstr: String,
        sid: String,
    ) {
        NapCatLoginRepository.captchaLogin(uin, password, ticket, randstr, sid)
    }

    override suspend fun newDeviceLogin(uin: String, password: String, verifiedToken: String?) {
        NapCatLoginRepository.newDeviceLogin(uin, password, verifiedToken)
    }

    override suspend fun getNewDeviceQRCode(): NapCatLoginService.NewDeviceQrCodeResult {
        return NapCatLoginRepository.getNewDeviceQRCode()
    }

    override suspend fun pollNewDeviceQRCode(bytesToken: String): NapCatLoginService.NewDeviceQrPollResult {
        return NapCatLoginRepository.pollNewDeviceQRCode(bytesToken)
    }

    override fun log(message: String) {
        RuntimeLogRepository.append(message)
    }
}

@HiltViewModel
class QQLoginViewModel @Inject constructor(
    private val bindings: QQLoginViewModelBindings,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    val loginState: StateFlow<NapCatLoginState> = bindings.loginState

    private val pollingTracker = QQLoginPollingTracker()
    private var pollingJob: Job? = null
    private var buildMarkerLogged = false
    private var autoQrRefreshIssuedForBlankState = false

    fun onScreenVisible(screenTag: String, versionMarker: String) {
        when (pollingTracker.onScreenVisible(screenTag)) {
            QQLoginPollingTransition.START -> {
                logBuildMarkerIfNeeded(versionMarker)
                log(
                    "QQ login polling started: screens=${pollingTracker.activeScreenSummary()} intervalLoggedOutMs=3000 intervalLoggedInMs=5000",
                )
                startPollingLoop()
            }

            QQLoginPollingTransition.KEEP_RUNNING,
            QQLoginPollingTransition.NO_CHANGE,
            QQLoginPollingTransition.STOP,
            -> Unit
        }
    }

    fun onScreenHidden(screenTag: String) {
        when (pollingTracker.onScreenHidden(screenTag)) {
            QQLoginPollingTransition.STOP -> {
                pollingJob?.cancel()
                pollingJob = null
                autoQrRefreshIssuedForBlankState = false
                log("QQ login polling stopped: no visible QQ login screens remain")
            }

            QQLoginPollingTransition.START,
            QQLoginPollingTransition.KEEP_RUNNING,
            QQLoginPollingTransition.NO_CHANGE,
            -> Unit
        }
    }

    override fun onCleared() {
        pollingJob?.cancel()
        pollingJob = null
        super.onCleared()
    }

    private fun startPollingLoop() {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch {
            while (isActive) {
                runCatching {
                    withContext(ioDispatcher) {
                        bindings.refresh()
                        maybeRequestQrCodeAutomatically()
                    }
                }
                delay(if (loginState.value.isLogin) 5000 else 3000)
            }
        }
    }

    fun refreshNow() {
        launchIo { bindings.refresh(manual = true) }
    }

    fun refreshQrCode() {
        launchIo { bindings.refreshQrCode() }
    }

    fun quickLoginSavedAccount(uin: String? = null) {
        launchIo { bindings.quickLoginSavedAccount(uin) }
    }

    fun saveQuickLoginAccount(uin: String) {
        launchIo { bindings.saveQuickLoginAccount(uin) }
    }

    fun logoutCurrentAccount() {
        launchIo { bindings.logoutCurrentAccount() }
    }

    fun passwordLogin(uin: String, password: String) {
        launchIo { bindings.passwordLogin(uin, password) }
    }

    fun captchaLogin(
        uin: String,
        password: String,
        ticket: String,
        randstr: String,
        sid: String,
    ) {
        launchIo { bindings.captchaLogin(uin, password, ticket, randstr, sid) }
    }

    fun newDeviceLogin(uin: String, password: String, verifiedToken: String? = null) {
        launchIo { bindings.newDeviceLogin(uin, password, verifiedToken) }
    }

    suspend fun getNewDeviceQRCode(): NapCatLoginService.NewDeviceQrCodeResult {
        return withContext(ioDispatcher) {
            bindings.getNewDeviceQRCode()
        }
    }

    suspend fun pollNewDeviceQRCode(bytesToken: String): NapCatLoginService.NewDeviceQrPollResult {
        return withContext(ioDispatcher) {
            bindings.pollNewDeviceQRCode(bytesToken)
        }
    }

    private fun launchIo(block: suspend () -> Unit) {
        viewModelScope.launch(ioDispatcher) {
            runCatching { block() }
        }
    }

    private fun logBuildMarkerIfNeeded(versionMarker: String) {
        if (buildMarkerLogged) return
        buildMarkerLogged = true
        log(versionMarker)
    }

    private suspend fun maybeRequestQrCodeAutomatically() {
        if (!pollingTracker.isScreenVisible("qq-login")) {
            autoQrRefreshIssuedForBlankState = false
            return
        }
        val state = loginState.value
        val needsQrBootstrap = state.bridgeReady && !state.isLogin && state.qrCodeUrl.isBlank()
        if (!needsQrBootstrap) {
            autoQrRefreshIssuedForBlankState = false
            return
        }
        if (autoQrRefreshIssuedForBlankState) {
            return
        }
        autoQrRefreshIssuedForBlankState = true
        bindings.refreshQrCode()
    }

    private fun log(message: String) {
        bindings.log(message)
    }
}

internal enum class QQLoginPollingTransition {
    START,
    KEEP_RUNNING,
    STOP,
    NO_CHANGE,
}

internal class QQLoginPollingTracker {
    private val activeScreens = linkedSetOf<String>()

    fun onScreenVisible(screenTag: String): QQLoginPollingTransition {
        val normalizedTag = screenTag.trim()
        if (normalizedTag.isBlank()) return QQLoginPollingTransition.NO_CHANGE
        if (!activeScreens.add(normalizedTag)) return QQLoginPollingTransition.NO_CHANGE
        return if (activeScreens.size == 1) {
            QQLoginPollingTransition.START
        } else {
            QQLoginPollingTransition.KEEP_RUNNING
        }
    }

    fun onScreenHidden(screenTag: String): QQLoginPollingTransition {
        val normalizedTag = screenTag.trim()
        if (normalizedTag.isBlank()) return QQLoginPollingTransition.NO_CHANGE
        if (!activeScreens.remove(normalizedTag)) return QQLoginPollingTransition.NO_CHANGE
        return if (activeScreens.isEmpty()) {
            QQLoginPollingTransition.STOP
        } else {
            QQLoginPollingTransition.KEEP_RUNNING
        }
    }

    fun activeScreenCount(): Int = activeScreens.size

    fun isScreenVisible(screenTag: String): Boolean = activeScreens.contains(screenTag.trim())

    fun activeScreenSummary(): String = activeScreens.joinToString(separator = ",")
}

internal fun buildQqLoginVersionMarker(versionName: String, versionCode: Long): String {
    return "QQ login diagnostics build: versionName=$versionName versionCode=$versionCode marker=qq-login-diag-v3-poll-singleton"
}
