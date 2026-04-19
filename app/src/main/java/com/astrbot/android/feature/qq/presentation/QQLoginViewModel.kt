package com.astrbot.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astrbot.android.feature.qq.data.NapCatLoginService
import com.astrbot.android.di.QQLoginViewModelDependencies
import com.astrbot.android.di.hilt.IoDispatcher
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

@HiltViewModel
class QQLoginViewModel @Inject constructor(
    private val dependencies: QQLoginViewModelDependencies,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    val loginState: StateFlow<NapCatLoginState> = dependencies.loginState
    private val pollingTracker = QQLoginPollingTracker()
    private var pollingJob: Job? = null
    private var buildMarkerLogged = false

    fun onScreenVisible(screenTag: String, versionMarker: String) {
        when (pollingTracker.onScreenVisible(screenTag)) {
            QQLoginPollingTransition.START -> {
                logBuildMarkerIfNeeded(versionMarker)
                dependencies.log(
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
                dependencies.log("QQ login polling stopped: no visible QQ login screens remain")
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
                        dependencies.refresh()
                    }
                }
                delay(if (loginState.value.isLogin) 5000 else 3000)
            }
        }
    }

    fun refreshNow() {
        launchIo { dependencies.refresh(manual = true) }
    }

    fun refreshQrCode() {
        launchIo { dependencies.refreshQrCode() }
    }

    fun quickLoginSavedAccount(uin: String? = null) {
        launchIo { dependencies.quickLoginSavedAccount(uin) }
    }

    fun saveQuickLoginAccount(uin: String) {
        launchIo { dependencies.saveQuickLoginAccount(uin) }
    }

    fun logoutCurrentAccount() {
        launchIo { dependencies.logoutCurrentAccount() }
    }

    fun passwordLogin(uin: String, password: String) {
        launchIo { dependencies.passwordLogin(uin, password) }
    }

    fun captchaLogin(
        uin: String,
        password: String,
        ticket: String,
        randstr: String,
        sid: String,
    ) {
        launchIo { dependencies.captchaLogin(uin, password, ticket, randstr, sid) }
    }

    fun newDeviceLogin(uin: String, password: String, verifiedToken: String? = null) {
        launchIo { dependencies.newDeviceLogin(uin, password, verifiedToken) }
    }

    suspend fun getNewDeviceQRCode(): NapCatLoginService.NewDeviceQrCodeResult {
        return withContext(ioDispatcher) {
            dependencies.getNewDeviceQRCode()
        }
    }

    suspend fun pollNewDeviceQRCode(bytesToken: String): NapCatLoginService.NewDeviceQrPollResult {
        return withContext(ioDispatcher) {
            dependencies.pollNewDeviceQRCode(bytesToken)
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
        dependencies.log(versionMarker)
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

    fun activeScreenSummary(): String = activeScreens.joinToString(separator = ",")
}

internal fun buildQqLoginVersionMarker(versionName: String, versionCode: Long): String {
    return "QQ login diagnostics build: versionName=$versionName versionCode=$versionCode marker=qq-login-diag-v3-poll-singleton"
}
