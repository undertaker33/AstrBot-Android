package com.astrbot.android.feature.qq.data

import android.content.Context
import com.astrbot.android.model.NapCatLoginState
import com.astrbot.android.model.SavedQqAccount
import com.astrbot.android.model.RuntimeStatus
import com.astrbot.android.core.runtime.container.BridgeCommandRunner
import com.astrbot.android.core.runtime.container.NapCatContainerRuntime
import com.astrbot.android.core.common.logging.AppLogger
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay

object NapCatLoginRepository {
    private const val RUNTIME_DIAGNOSTIC_THROTTLE_MS = 20_000L
    private const val QR_REFRESH_COOLDOWN_MS = 10_000L

    private var appContext: Context? = null
    private var lastRuntimeDiagnosticAt: Long = 0L
    private val qrRefreshLock = Any()
    private var qrRefreshInFlight = false
    private var lastQrRefreshRequestedAt = 0L
    private var nowProvider: () -> Long = { System.currentTimeMillis() }
    private val _loginState = MutableStateFlow(NapCatLoginState())
    val loginState: StateFlow<NapCatLoginState> = _loginState.asStateFlow()

    internal fun buildRuntimeDiagnosticsLinesForTests(
        filesDir: File,
        trigger: String,
        detail: String,
    ): List<String> = NapCatLoginDiagnostics.buildRuntimeDiagnosticsLines(filesDir, trigger, detail)

    internal fun resetQrRefreshGuardsForTests(nowProviderOverride: (() -> Long)? = null) {
        synchronized(qrRefreshLock) {
            qrRefreshInFlight = false
            lastQrRefreshRequestedAt = 0L
            nowProvider = nowProviderOverride ?: { System.currentTimeMillis() }
        }
    }

    fun initialize(context: Context) {
        appContext = context.applicationContext
        NapCatLoginLocalStore.initialize(appContext ?: context.applicationContext)
        val savedQuickLoginUin = loadSavedQuickLoginUin()
        val savedAccounts = loadSavedAccounts()
        if (savedQuickLoginUin.isNotBlank()) {
            _loginState.value = _loginState.value.copy(
                quickLoginUin = savedQuickLoginUin,
                savedAccounts = savedAccounts,
            )
        } else if (savedAccounts.isNotEmpty()) {
            _loginState.value = _loginState.value.copy(savedAccounts = savedAccounts)
        }
    }

    fun markBridgeUnavailable(details: String) {
        val current = _loginState.value
        NapCatLoginService.clearCredential()
        _loginState.value = current.copy(
            bridgeReady = false,
            qrCodeUrl = "",
            quickLoginUin = current.quickLoginUin.ifBlank { loadSavedQuickLoginUin() },
            savedAccounts = current.savedAccounts.ifEmpty { loadSavedAccounts() },
            statusText = details,
            loginError = "",
            lastUpdated = System.currentTimeMillis(),
        )
    }

    fun refresh(manual: Boolean = false): NapCatLoginState {
        val healthUrl = requireBridgeHealthUrl() ?: return _loginState.value

        return try {
            var nextState = mergeWithPendingChallenge(
                NapCatLoginService.fetchLoginState(healthUrl),
            )
            if (nextState.isLogin && nextState.uin.isNotBlank() && nextState.quickLoginUin != nextState.uin) {
                runCatching {
                    NapCatLoginService.setQuickLoginAccount(healthUrl, nextState.uin)
                }.onSuccess {
                    saveQuickLoginUinLocally(nextState.uin)
                    nextState = nextState.copy(
                        quickLoginUin = nextState.uin,
                        savedAccounts = upsertSavedAccount(nextState.savedAccounts, SavedQqAccount(nextState.uin, nextState.nick, nextState.avatarUrl)),
                    )
                    AppLogger.append("QQ quick login account auto-saved: ${nextState.uin}")
                }.onFailure { error ->
                    AppLogger.append(
                        "QQ quick login account auto-save failed: ${error.message ?: error.javaClass.simpleName}",
                    )
                }
            } else if (nextState.isLogin && nextState.uin.isNotBlank()) {
                saveQuickLoginUinLocally(nextState.uin)
                nextState = nextState.copy(
                    quickLoginUin = nextState.quickLoginUin.ifBlank { nextState.uin },
                    savedAccounts = upsertSavedAccount(nextState.savedAccounts, SavedQqAccount(nextState.uin, nextState.nick, nextState.avatarUrl)),
                )
            }
            persistSavedAccounts(nextState.savedAccounts)
            _loginState.value = nextState
            if (manual) {
                AppLogger.append(
                    "QQ login state refreshed: login=${nextState.isLogin} offline=${nextState.isOffline} quick=${nextState.quickLoginUin.ifBlank { "-" }}",
                )
            }
            nextState
        } catch (error: Exception) {
            val message = error.message ?: error.javaClass.simpleName
            _loginState.value = _loginState.value.copy(
                bridgeReady = true,
                authenticated = false,
                statusText = message,
                loginError = message,
                lastUpdated = System.currentTimeMillis(),
            )
            maybeLogRuntimeDiagnostics(trigger = "refresh", detail = message)
            if (manual) {
                AppLogger.append("QQ login refresh error: $message")
            }
            _loginState.value
        }
    }

    fun refreshQrCode() {
        val healthUrl = requireBridgeHealthUrl() ?: return
        val requestStartedAt = nowProvider()
        synchronized(qrRefreshLock) {
            if (qrRefreshInFlight) {
                AppLogger.append("QQ login QR refresh skipped: request already in flight")
                return
            }
            if (requestStartedAt - lastQrRefreshRequestedAt < QR_REFRESH_COOLDOWN_MS) {
                AppLogger.append("QQ login QR refresh skipped: cooldown active")
                return
            }
            qrRefreshInFlight = true
            lastQrRefreshRequestedAt = requestStartedAt
        }
        try {
            NapCatLoginService.refreshQrCode(healthUrl)
            _loginState.value = clearedChallengeState(
                _loginState.value.copy(
                    bridgeReady = true,
                    statusText = "QQ login QR refresh requested",
                    loginError = "",
                    lastUpdated = System.currentTimeMillis(),
                ),
            )
            AppLogger.append("QQ login QR refresh requested")
        } catch (error: Exception) {
            applyActionError("QQ login QR refresh error", error)
            return
        } finally {
            synchronized(qrRefreshLock) {
                qrRefreshInFlight = false
            }
        }
        refresh(manual = true)
    }

    fun quickLoginSavedAccount(uin: String? = null) {
        val healthUrl = requireBridgeHealthUrl() ?: return
        val targetUin = uin?.trim().orEmpty().ifBlank { _loginState.value.quickLoginUin }.ifBlank {
            _loginState.value = _loginState.value.copy(
                bridgeReady = true,
                statusText = "No quick login account is saved yet",
                loginError = "No quick login account is saved yet",
                lastUpdated = System.currentTimeMillis(),
            )
            return
        }
        try {
            NapCatLoginService.quickLogin(healthUrl, targetUin)
            _loginState.value = clearedChallengeState(
                _loginState.value.copy(
                    bridgeReady = true,
                    quickLoginUin = targetUin,
                    savedAccounts = upsertSavedAccount(_loginState.value.savedAccounts, SavedQqAccount(targetUin)),
                    statusText = "Quick login requested for $targetUin",
                    loginError = "",
                    lastUpdated = System.currentTimeMillis(),
                ),
            )
            saveQuickLoginUinLocally(targetUin)
            AppLogger.append("QQ quick login requested: $targetUin")
        } catch (error: Exception) {
            applyActionError("QQ quick login failed", error)
            return
        }
        refresh(manual = true)
    }

    fun saveQuickLoginAccount(uin: String) {
        val healthUrl = requireBridgeHealthUrl() ?: return
        val cleanedUin = uin.trim()
        if (cleanedUin.isBlank()) {
            _loginState.value = _loginState.value.copy(
                bridgeReady = true,
                statusText = "QQ account is required",
                loginError = "QQ account is required",
                lastUpdated = System.currentTimeMillis(),
            )
            return
        }

        try {
            NapCatLoginService.setQuickLoginAccount(healthUrl, cleanedUin)
            saveQuickLoginUinLocally(cleanedUin)
            val mergedAccounts = upsertSavedAccount(_loginState.value.savedAccounts, SavedQqAccount(cleanedUin))
            persistSavedAccounts(mergedAccounts)
            _loginState.value = _loginState.value.copy(
                bridgeReady = true,
                quickLoginUin = cleanedUin,
                savedAccounts = mergedAccounts,
                statusText = "Quick login account saved: $cleanedUin",
                loginError = "",
                lastUpdated = System.currentTimeMillis(),
            )
            AppLogger.append("QQ quick login account saved: $cleanedUin")
        } catch (error: Exception) {
            applyActionError("QQ quick login account save failed", error)
        }
    }

    suspend fun logoutCurrentAccount() {
        val context = appContext ?: run {
            _loginState.value = _loginState.value.copy(
                statusText = "Runtime is not initialized yet",
                loginError = "Runtime is not initialized yet",
                lastUpdated = System.currentTimeMillis(),
            )
            return
        }

        NapCatContainerRuntime.ensureInstalled(context)
        val scriptFile = File(context.filesDir, "runtime/scripts/logout_qq.sh")
        if (!scriptFile.exists()) {
            _loginState.value = _loginState.value.copy(
                statusText = "Logout script is missing",
                loginError = "Logout script is missing",
                lastUpdated = System.currentTimeMillis(),
            )
            return
        }

        val command = "/system/bin/sh ${scriptFile.absolutePath} ${context.filesDir.absolutePath} ${context.applicationInfo.nativeLibraryDir}"
        _loginState.value = _loginState.value.copy(
            bridgeReady = false,
            statusText = "Logging out current QQ account",
            loginError = "",
            lastUpdated = System.currentTimeMillis(),
        )

        val result = BridgeCommandRunner.execute(command)
        if (result.exitCode != 0) {
            applyActionError("QQ logout failed", IllegalStateException(result.stderr.ifBlank { result.stdout.ifBlank { "Unknown error" } }))
            return
        }

        AppLogger.append("QQ logout command completed")
        AppLogger.append("QQ logout requesting bridge restart")
        NapCatContainerRuntime.stopBridge(context)
        delay(1_500)
        NapCatContainerRuntime.startBridge(context)
        repeat(12) { attempt ->
            delay(3_000)
            val state = refresh(manual = attempt == 5)
            if (state.bridgeReady && !state.isLogin) {
                _loginState.value = state.copy(
                    statusText = "Logged out. QR login is ready.",
                    loginError = "",
                    lastUpdated = System.currentTimeMillis(),
                )
                return
            }
        }

        _loginState.value = _loginState.value.copy(
            statusText = "Logout requested. NapCat is restarting.",
            loginError = "",
            lastUpdated = System.currentTimeMillis(),
        )
    }

    fun passwordLogin(uin: String, password: String) {
        performLoginAction(
            actionLabel = "QQ password login",
            uin = uin,
            password = password,
        ) { healthUrl, cleanedUin, cleanedPassword ->
            NapCatLoginService.passwordLogin(healthUrl, cleanedUin, cleanedPassword)
        }
    }

    fun captchaLogin(
        uin: String,
        password: String,
        ticket: String,
        randstr: String,
        sid: String,
    ) {
        performLoginAction(
            actionLabel = "QQ captcha login",
            uin = uin,
            password = password,
        ) { healthUrl, cleanedUin, cleanedPassword ->
            NapCatLoginService.captchaLogin(
                baseUrl = healthUrl,
                uin = cleanedUin,
                password = cleanedPassword,
                ticket = ticket.trim(),
                randstr = randstr.trim(),
                sid = sid.trim(),
            )
        }
    }

    fun newDeviceLogin(uin: String, password: String, verifiedToken: String? = null) {
        val sig = verifiedToken?.trim().orEmpty().ifBlank { _loginState.value.newDeviceSig }
        if (sig.isBlank()) {
            _loginState.value = _loginState.value.copy(
                bridgeReady = true,
                statusText = "No pending new device verification request",
                loginError = "No pending new device verification request",
                lastUpdated = System.currentTimeMillis(),
            )
            return
        }

        performLoginAction(
            actionLabel = "QQ new device login",
            uin = uin,
            password = password,
        ) { healthUrl, cleanedUin, cleanedPassword ->
            NapCatLoginService.newDeviceLogin(
                baseUrl = healthUrl,
                uin = cleanedUin,
                password = cleanedPassword,
                newDeviceSig = sig,
            )
        }
    }

    fun getNewDeviceQRCode(): NapCatLoginService.NewDeviceQrCodeResult {
        val healthUrl = requireBridgeHealthUrl()
            ?: throw IllegalStateException("Waiting for NapCat bridge")
        val state = _loginState.value
        if (!state.needNewDevice || state.newDeviceJumpUrl.isBlank() || state.uin.isBlank()) {
            throw IllegalStateException("No pending new device verification request")
        }
        return NapCatLoginService.getNewDeviceQRCode(
            baseUrl = healthUrl,
            uin = state.uin,
            jumpUrl = state.newDeviceJumpUrl,
        )
    }

    fun pollNewDeviceQRCode(bytesToken: String): NapCatLoginService.NewDeviceQrPollResult {
        val healthUrl = requireBridgeHealthUrl()
            ?: throw IllegalStateException("Waiting for NapCat bridge")
        val uin = _loginState.value.uin
        if (uin.isBlank()) {
            throw IllegalStateException("Missing QQ account for new device verification")
        }
        return NapCatLoginService.pollNewDeviceQRCode(
            baseUrl = healthUrl,
            uin = uin,
            bytesToken = bytesToken,
        )
    }

    fun restoreSavedLoginState(
        quickLoginUin: String,
        savedAccounts: List<SavedQqAccount>,
    ) {
        val normalizedAccounts = savedAccounts
            .mapNotNull { account ->
                val uin = account.uin.trim()
                if (uin.isBlank()) {
                    null
                } else {
                    SavedQqAccount(
                        uin = uin,
                        nickName = account.nickName.trim(),
                        avatarUrl = account.avatarUrl.trim(),
                    )
                }
            }
            .distinctBy { it.uin }
        val resolvedQuickLoginUin = quickLoginUin.trim().ifBlank { normalizedAccounts.firstOrNull()?.uin.orEmpty() }
        saveQuickLoginUinLocally(resolvedQuickLoginUin)
        persistSavedAccounts(normalizedAccounts)
        _loginState.value = _loginState.value.copy(
            quickLoginUin = resolvedQuickLoginUin,
            savedAccounts = normalizedAccounts,
            lastUpdated = System.currentTimeMillis(),
        )
        AppLogger.append("QQ login backup restored: accounts=${normalizedAccounts.size} quick=${resolvedQuickLoginUin.ifBlank { "-" }}")
    }

    private fun performLoginAction(
        actionLabel: String,
        uin: String,
        password: String,
        block: (String, String, String) -> NapCatLoginService.LoginActionResult,
    ) {
        val healthUrl = requireBridgeHealthUrl() ?: return
        val cleanedUin = uin.trim()
        val cleanedPassword = password.trim()
        if (cleanedUin.isBlank() || cleanedPassword.isBlank()) {
            _loginState.value = _loginState.value.copy(
                bridgeReady = true,
                statusText = "QQ account and password are required",
                loginError = "QQ account and password are required",
                lastUpdated = System.currentTimeMillis(),
            )
            return
        }

        try {
            val result = block(healthUrl, cleanedUin, cleanedPassword)
            applyLoginActionResult(actionLabel, cleanedUin, result)
        } catch (error: Exception) {
            applyActionError("$actionLabel failed", error)
        }
    }

    private fun applyLoginActionResult(
        actionLabel: String,
        uin: String,
        result: NapCatLoginService.LoginActionResult,
    ) {
        when {
            result.needCaptcha -> {
                _loginState.value = _loginState.value.copy(
                    bridgeReady = true,
                    quickLoginUin = _loginState.value.quickLoginUin.ifBlank { uin },
                    needCaptcha = false,
                    captchaUrl = "",
                    needNewDevice = false,
                    newDeviceJumpUrl = "",
                    newDeviceSig = "",
                    statusText = "First-time login requires QR login",
                    loginError = "首次登录请使用扫码登录",
                    lastUpdated = System.currentTimeMillis(),
                )
                AppLogger.append("$actionLabel requires verification, redirected to QR login")
            }

            result.needNewDevice -> {
                _loginState.value = _loginState.value.copy(
                    bridgeReady = true,
                    quickLoginUin = _loginState.value.quickLoginUin.ifBlank { uin },
                    needCaptcha = false,
                    captchaUrl = "",
                    needNewDevice = false,
                    newDeviceJumpUrl = "",
                    newDeviceSig = "",
                    statusText = "First-time login requires QR login",
                    loginError = "首次登录请使用扫码登录",
                    lastUpdated = System.currentTimeMillis(),
                )
                AppLogger.append("$actionLabel requires first-time verification, redirected to QR login")
            }

            result.completed -> {
                _loginState.value = clearedChallengeState(
                    _loginState.value.copy(
                        bridgeReady = true,
                        quickLoginUin = _loginState.value.quickLoginUin.ifBlank { uin },
                        statusText = "$actionLabel requested",
                        loginError = "",
                        lastUpdated = System.currentTimeMillis(),
                    ),
                )
                AppLogger.append("$actionLabel requested: $uin")
                refresh(manual = true)
            }
        }
    }

    private fun mergeWithPendingChallenge(nextState: NapCatLoginState): NapCatLoginState {
        val current = _loginState.value
        val quickLoginUin = nextState.quickLoginUin
            .ifBlank { current.quickLoginUin }
            .ifBlank { loadSavedQuickLoginUin() }
        val savedAccounts = mergeSavedAccounts(current.savedAccounts, nextState.savedAccounts)

        if (nextState.isLogin) {
            return clearedChallengeState(
                nextState.copy(
                    quickLoginUin = quickLoginUin,
                    savedAccounts = upsertSavedAccount(savedAccounts, SavedQqAccount(nextState.uin, nextState.nick, nextState.avatarUrl)),
                ),
            )
        }

        val hasPendingChallenge = current.needCaptcha || current.needNewDevice
        if (!hasPendingChallenge) {
            return nextState.copy(
                quickLoginUin = quickLoginUin,
                savedAccounts = savedAccounts,
            )
        }

        return nextState.copy(
            quickLoginUin = quickLoginUin,
            savedAccounts = savedAccounts,
            needCaptcha = current.needCaptcha,
            captchaUrl = current.captchaUrl,
            needNewDevice = current.needNewDevice,
            newDeviceJumpUrl = current.newDeviceJumpUrl,
            newDeviceSig = current.newDeviceSig,
            statusText = current.statusText,
            loginError = current.loginError,
        )
    }

    private fun clearedChallengeState(state: NapCatLoginState): NapCatLoginState {
        return state.copy(
            needCaptcha = false,
            captchaUrl = "",
            needNewDevice = false,
            newDeviceJumpUrl = "",
            newDeviceSig = "",
        )
    }

    private fun applyActionError(prefix: String, error: Exception) {
        val message = error.message ?: error.javaClass.simpleName
        _loginState.value = _loginState.value.copy(
            bridgeReady = true,
            statusText = message,
            loginError = message,
            lastUpdated = System.currentTimeMillis(),
        )
        maybeLogRuntimeDiagnostics(trigger = prefix, detail = message)
        AppLogger.append("$prefix: $message")
    }

    private fun maybeLogRuntimeDiagnostics(trigger: String, detail: String) {
        val normalizedDetail = detail.trim()
        if (!shouldLogRuntimeDiagnostics(normalizedDetail)) {
            return
        }
        val context = appContext ?: return
        val now = System.currentTimeMillis()
        if (now - lastRuntimeDiagnosticAt < RUNTIME_DIAGNOSTIC_THROTTLE_MS) {
            return
        }
        lastRuntimeDiagnosticAt = now
        NapCatLoginDiagnostics.buildRuntimeDiagnosticsLines(context.filesDir, trigger, normalizedDetail)
            .forEach(AppLogger::append)
    }

    private fun shouldLogRuntimeDiagnostics(detail: String): Boolean {
        val normalized = detail.lowercase()
        return normalized.contains("token is invalid") ||
            normalized.contains("login rate limit") ||
            normalized.contains("missing webui credential") ||
            normalized.contains("webui login failed")
    }

    private fun saveQuickLoginUinLocally(uin: String) {
        NapCatLoginLocalStore.saveQuickLoginUin(uin)
    }

    private fun loadSavedQuickLoginUin(): String {
        return NapCatLoginLocalStore.loadSavedQuickLoginUin()
    }

    private fun loadSavedAccounts(): List<SavedQqAccount> {
        return NapCatLoginLocalStore.loadSavedAccounts()
    }

    private fun persistSavedAccounts(accounts: List<SavedQqAccount>) {
        NapCatLoginLocalStore.persistSavedAccounts(accounts)
    }

    private fun mergeSavedAccounts(
        localAccounts: List<SavedQqAccount>,
        remoteAccounts: List<SavedQqAccount>,
    ): List<SavedQqAccount> {
        return NapCatLoginLocalStore.mergeSavedAccounts(localAccounts, remoteAccounts)
    }

    private fun upsertSavedAccount(
        accounts: List<SavedQqAccount>,
        account: SavedQqAccount,
    ): List<SavedQqAccount> {
        return NapCatLoginLocalStore.upsertSavedAccount(accounts, account)
    }

    private fun requireBridgeHealthUrl(): String? {
        val bridgeState = NapCatBridgeRepository.runtimeState.value
        val healthUrl = NapCatBridgeRepository.config.value.healthUrl
        if (bridgeState.statusType != RuntimeStatus.RUNNING) {
            val reason = if (bridgeState.details.isBlank()) "Waiting for NapCat bridge" else bridgeState.details
            markBridgeUnavailable(reason)
            return null
        }
        return healthUrl
    }
}
