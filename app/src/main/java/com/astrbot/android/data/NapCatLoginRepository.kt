package com.astrbot.android.data

import android.content.Context
import android.content.SharedPreferences
import com.astrbot.android.model.NapCatLoginState
import com.astrbot.android.model.SavedQqAccount
import com.astrbot.android.runtime.BridgeCommandRunner
import com.astrbot.android.runtime.ContainerRuntimeInstaller
import com.astrbot.android.runtime.ContainerBridgeController
import com.astrbot.android.runtime.RuntimeLogRepository
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import org.json.JSONArray

object NapCatLoginRepository {
    private const val PREFS_NAME = "napcat_login_state"
    private const val KEY_LAST_QUICK_LOGIN_UIN = "last_quick_login_uin"
    private const val KEY_SAVED_ACCOUNTS = "saved_accounts"

    private var preferences: SharedPreferences? = null
    private var appContext: Context? = null
    private val _loginState = MutableStateFlow(NapCatLoginState())
    val loginState: StateFlow<NapCatLoginState> = _loginState.asStateFlow()

    fun initialize(context: Context) {
        if (preferences != null) return
        appContext = context.applicationContext
        preferences = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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
        NapCatLoginService.clearCredential()
        _loginState.value = _loginState.value.copy(
            bridgeReady = false,
            authenticated = false,
            isLogin = false,
            isOffline = false,
            qrCodeUrl = "",
            quickLoginUin = _loginState.value.quickLoginUin.ifBlank { loadSavedQuickLoginUin() },
            savedAccounts = _loginState.value.savedAccounts.ifEmpty { loadSavedAccounts() },
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
                    RuntimeLogRepository.append("QQ quick login account auto-saved: ${nextState.uin}")
                }.onFailure { error ->
                    RuntimeLogRepository.append(
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
                RuntimeLogRepository.append(
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
            if (manual) {
                RuntimeLogRepository.append("QQ login refresh error: $message")
            }
            _loginState.value
        }
    }

    fun refreshQrCode() {
        val healthUrl = requireBridgeHealthUrl() ?: return
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
            RuntimeLogRepository.append("QQ login QR refresh requested")
        } catch (error: Exception) {
            applyActionError("QQ login QR refresh error", error)
            return
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
            RuntimeLogRepository.append("QQ quick login requested: $targetUin")
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
            RuntimeLogRepository.append("QQ quick login account saved: $cleanedUin")
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

        ContainerRuntimeInstaller.ensureInstalled(context)
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

        RuntimeLogRepository.append("QQ logout command completed")
        RuntimeLogRepository.append("QQ logout requesting bridge restart")
        ContainerBridgeController.stop(context)
        delay(1_500)
        ContainerBridgeController.start(context)
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
        RuntimeLogRepository.append("QQ login backup restored: accounts=${normalizedAccounts.size} quick=${resolvedQuickLoginUin.ifBlank { "-" }}")
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
                RuntimeLogRepository.append("$actionLabel requires verification, redirected to QR login")
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
                RuntimeLogRepository.append("$actionLabel requires first-time verification, redirected to QR login")
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
                RuntimeLogRepository.append("$actionLabel requested: $uin")
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
        RuntimeLogRepository.append("$prefix: $message")
    }

    private fun saveQuickLoginUinLocally(uin: String) {
        val cleanedUin = uin.trim()
        if (cleanedUin.isBlank()) return
        preferences
            ?.edit()
            ?.putString(KEY_LAST_QUICK_LOGIN_UIN, cleanedUin)
            ?.apply()
    }

    private fun loadSavedQuickLoginUin(): String {
        return preferences
            ?.getString(KEY_LAST_QUICK_LOGIN_UIN, "")
            .orEmpty()
            .trim()
    }

    private fun loadSavedAccounts(): List<SavedQqAccount> {
        val raw = preferences?.getString(KEY_SAVED_ACCOUNTS, "").orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val uin = item.optString("uin").trim()
                    if (uin.isBlank()) continue
                    add(
                        SavedQqAccount(
                            uin = uin,
                            nickName = item.optString("nickName").trim(),
                            avatarUrl = item.optString("avatarUrl").trim(),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun persistSavedAccounts(accounts: List<SavedQqAccount>) {
        val normalized = accounts
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
        val array = JSONArray()
        normalized.forEach { account ->
            array.put(
                org.json.JSONObject().apply {
                    put("uin", account.uin)
                    put("nickName", account.nickName)
                    put("avatarUrl", account.avatarUrl)
                },
            )
        }
        preferences?.edit()?.putString(KEY_SAVED_ACCOUNTS, array.toString())?.apply()
    }

    private fun mergeSavedAccounts(
        localAccounts: List<SavedQqAccount>,
        remoteAccounts: List<SavedQqAccount>,
    ): List<SavedQqAccount> {
        val merged = localAccounts.toMutableList()
        remoteAccounts.forEach { mergedAccount ->
            val existingIndex = merged.indexOfFirst { it.uin == mergedAccount.uin }
            if (existingIndex >= 0) {
                val existing = merged[existingIndex]
                merged[existingIndex] = existing.copy(
                    nickName = mergedAccount.nickName.ifBlank { existing.nickName },
                    avatarUrl = mergedAccount.avatarUrl.ifBlank { existing.avatarUrl },
                )
            } else {
                merged += mergedAccount
            }
        }
        return merged.distinctBy { it.uin }
    }

    private fun upsertSavedAccount(
        accounts: List<SavedQqAccount>,
        account: SavedQqAccount,
    ): List<SavedQqAccount> {
        val cleanedUin = account.uin.trim()
        if (cleanedUin.isBlank()) return accounts
        val updated = mutableListOf(
            SavedQqAccount(
                uin = cleanedUin,
                nickName = account.nickName.trim(),
                avatarUrl = account.avatarUrl.trim(),
            ),
        )
        accounts.forEach { existing ->
            if (existing.uin != cleanedUin) {
                updated += existing
            }
        }
        persistSavedAccounts(updated)
        return updated
    }

    private fun requireBridgeHealthUrl(): String? {
        val bridgeState = NapCatBridgeRepository.runtimeState.value
        val healthUrl = NapCatBridgeRepository.config.value.healthUrl
        if (bridgeState.status != "Running") {
            val reason = if (bridgeState.details.isBlank()) "Waiting for NapCat bridge" else bridgeState.details
            markBridgeUnavailable(reason)
            return null
        }
        return healthUrl
    }
}
