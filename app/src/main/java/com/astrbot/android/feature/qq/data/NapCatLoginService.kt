package com.astrbot.android.feature.qq.data

import com.astrbot.android.data.http.AstrBotHttpClient
import com.astrbot.android.data.http.AstrBotHttpException
import com.astrbot.android.data.http.HttpFailureCategory
import com.astrbot.android.data.http.HttpMethod
import com.astrbot.android.data.http.HttpRequestSpec
import com.astrbot.android.data.http.OkHttpAstrBotHttpClient
import com.astrbot.android.core.common.logging.AppLogger
import com.astrbot.android.core.common.logging.RuntimeLogRepository
import com.astrbot.android.core.runtime.secret.AppSecretStore
import com.astrbot.android.model.NapCatLoginState
import com.astrbot.android.model.SavedQqAccount
import org.json.JSONObject
import org.json.JSONArray
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.json.JSONException

object NapCatLoginService {
    private var credential: String = ""
    private var postJsonOverride: ((String, JSONObject, String?) -> JSONObject)? = null
    private var httpClient: AstrBotHttpClient = OkHttpAstrBotHttpClient()

    internal enum class LoginFailureCategory {
        AUTH_TOKEN_EXPIRED,
        AUTH_LOGIN_FAILED,
        API_BUSINESS_REJECTED,
        EMPTY_OR_MALFORMED_RESPONSE,
        NETWORK_FAILURE,
    }

    private class LoginServiceException(
        val category: LoginFailureCategory,
        override val message: String,
        cause: Throwable? = null,
    ) : IllegalStateException(message, cause)

    data class NewDeviceQrCodeResult(
        val qrUrl: String,
        val bytesToken: String,
    )

    data class NewDeviceQrPollResult(
        val guaranteeStatus: Int,
        val successToken: String,
    )

    data class LoginActionResult(
        val completed: Boolean,
        val needCaptcha: Boolean = false,
        val captchaUrl: String = "",
        val needNewDevice: Boolean = false,
        val newDeviceJumpUrl: String = "",
        val newDeviceSig: String = "",
    )

    private data class ApiEnvelope(
        val code: Int,
        val message: String,
        val data: Any?,
    )

    internal fun resetForTests() {
        credential = ""
        postJsonOverride = null
        httpClient = OkHttpAstrBotHttpClient()
    }

    internal fun setPostJsonOverrideForTests(override: ((String, JSONObject, String?) -> JSONObject)?) {
        postJsonOverride = override
    }

    internal fun setHttpClientOverrideForTests(override: AstrBotHttpClient?) {
        httpClient = override ?: OkHttpAstrBotHttpClient()
    }

    internal fun debugCredentialForTests(): String = credential

    internal fun classifyFailureForTests(
        message: String?,
        cause: Throwable?,
    ): LoginFailureCategory = classifyFailure(message, cause)

    internal fun maskSecretForTests(value: String): String = maskSecret(value)

    internal fun sanitizeDetailForLogsForTests(detail: String): String = sanitizeNapCatDetailForLogs(detail)

    fun clearCredential() {
        credential = ""
    }

    fun fetchLoginState(baseUrl: String): NapCatLoginState {
        val savedAccounts = runCatching { getQuickLoginAccounts(baseUrl) }.getOrDefault(emptyList())
        val quickLoginAccount = runCatching { getQuickLoginAccount(baseUrl) }.getOrDefault("")
        val statusData = requireObject(
            requireSuccess(
                authorizedPostResponse(baseUrl, "/QQLogin/CheckLoginStatus", JSONObject()),
            ),
        )
        val isLogin = statusData.optBoolean("isLogin", false)
        val isOffline = statusData.optBoolean("isOffline", false)
        val qrCodeUrl = statusData.optString("qrcodeurl")
        val loginError = statusData.optString("loginError")

        var uin = ""
        var nick = ""
        var avatarUrl = ""
        if (isLogin) {
            val infoData = requireObject(
                requireSuccess(
                    authorizedPostResponse(baseUrl, "/QQLogin/GetQQLoginInfo", JSONObject()),
                ),
            )
            uin = infoData.optString("uin")
            nick = infoData.optString("nick")
            avatarUrl = infoData.optString("avatarUrl")
        }

        val statusText = when {
            isLogin -> "QQ logged in"
            isOffline -> "QQ session is offline"
            qrCodeUrl.isNotBlank() -> "Scan the QR code with QQ to log in"
            loginError.isNotBlank() -> loginError
            else -> "Waiting for login QR code"
        }

        return NapCatLoginState(
            bridgeReady = true,
            authenticated = credential.isNotBlank(),
            isLogin = isLogin,
            isOffline = isOffline,
            qrCodeUrl = qrCodeUrl,
            quickLoginUin = quickLoginAccount,
            savedAccounts = savedAccounts,
            loginError = loginError,
            uin = uin,
            nick = nick,
            avatarUrl = avatarUrl,
            statusText = statusText,
            lastUpdated = System.currentTimeMillis(),
        )
    }

    fun getQuickLoginAccount(baseUrl: String): String {
        val data = requireSuccess(
            authorizedPostResponse(baseUrl, "/QQLogin/GetQuickLoginQQ", JSONObject()),
        )
        return unwrapValue(data)
    }

    fun getQuickLoginAccounts(baseUrl: String): List<SavedQqAccount> {
        val data = requireSuccess(
            authorizedPostResponse(baseUrl, "/QQLogin/GetQuickLoginListNew", JSONObject()),
        )
        val parsed = parseQuickLoginAccountList(data)
        if (parsed.isNotEmpty()) {
            return parsed
        }

        val legacyData = requireSuccess(
            authorizedPostResponse(baseUrl, "/QQLogin/GetQuickLoginList", JSONObject()),
        )
        return parseQuickLoginAccountList(legacyData)
    }

    fun setQuickLoginAccount(baseUrl: String, uin: String) {
        requireSuccess(
            authorizedPostResponse(
                baseUrl = baseUrl,
                path = "/QQLogin/SetQuickLoginQQ",
                payload = JSONObject().apply {
                    put("uin", uin.trim())
                },
            ),
        )
    }

    fun quickLogin(baseUrl: String, uin: String) {
        requireSuccess(
            authorizedPostResponse(
                baseUrl = baseUrl,
                path = "/QQLogin/SetQuickLogin",
                payload = JSONObject().apply {
                    put("uin", uin.trim())
                },
            ),
        )
    }

    fun refreshQrCode(baseUrl: String) {
        requireSuccess(
            authorizedPostResponse(baseUrl, "/QQLogin/RefreshQRcode", JSONObject()),
        )
    }

    fun passwordLogin(baseUrl: String, uin: String, password: String): LoginActionResult {
        val response = authorizedPostResponse(
            baseUrl = baseUrl,
            path = "/QQLogin/PasswordLogin",
            payload = JSONObject().apply {
                put("uin", uin.trim())
                put("passwordMd5", md5Hex(password))
            },
        )
        return parseLoginActionResult(requireSuccess(response))
    }

    fun captchaLogin(
        baseUrl: String,
        uin: String,
        password: String,
        ticket: String,
        randstr: String,
        sid: String,
    ): LoginActionResult {
        val response = authorizedPostResponse(
            baseUrl = baseUrl,
            path = "/QQLogin/CaptchaLogin",
            payload = JSONObject().apply {
                put("uin", uin.trim())
                put("passwordMd5", md5Hex(password))
                put("ticket", ticket.trim())
                put("randstr", randstr.trim())
                put("sid", sid.trim())
            },
        )
        return parseLoginActionResult(requireSuccess(response))
    }

    fun newDeviceLogin(
        baseUrl: String,
        uin: String,
        password: String,
        newDeviceSig: String,
    ): LoginActionResult {
        val response = authorizedPostResponse(
            baseUrl = baseUrl,
            path = "/QQLogin/NewDeviceLogin",
            payload = JSONObject().apply {
                put("uin", uin.trim())
                put("passwordMd5", md5Hex(password))
                put("newDevicePullQrCodeSig", newDeviceSig)
            },
        )
        return parseLoginActionResult(requireSuccess(response))
    }

    fun getNewDeviceQRCode(
        baseUrl: String,
        uin: String,
        jumpUrl: String,
    ): NewDeviceQrCodeResult {
        val data = requireObject(
            requireSuccess(
                authorizedPostResponse(
                    baseUrl = baseUrl,
                    path = "/QQLogin/GetNewDeviceQRCode",
                    payload = JSONObject().apply {
                        put("uin", uin.trim())
                        put("jumpUrl", jumpUrl.trim())
                    },
                ),
            ),
        )
        return NewDeviceQrCodeResult(
            qrUrl = data.optString("str_url").trim(),
            bytesToken = data.optString("bytes_token").trim(),
        )
    }

    fun pollNewDeviceQRCode(
        baseUrl: String,
        uin: String,
        bytesToken: String,
    ): NewDeviceQrPollResult {
        val data = requireObject(
            requireSuccess(
                authorizedPostResponse(
                    baseUrl = baseUrl,
                    path = "/QQLogin/PollNewDeviceQR",
                    payload = JSONObject().apply {
                        put("uin", uin.trim())
                        put("bytesToken", bytesToken.trim())
                    },
                ),
            ),
        )
        return NewDeviceQrPollResult(
            guaranteeStatus = data.optInt("uint32_guarantee_status", 0),
            successToken = data.optString("str_nt_succ_token").trim(),
        )
    }

    private fun parseLoginActionResult(data: Any?): LoginActionResult {
        val payload = requireObject(data)
        val needCaptcha = payload.optBoolean("needCaptcha", false)
        val needNewDevice = payload.optBoolean("needNewDevice", false)

        return when {
            needCaptcha -> LoginActionResult(
                completed = false,
                needCaptcha = true,
                captchaUrl = payload.optString("proofWaterUrl"),
            )

            needNewDevice -> LoginActionResult(
                completed = false,
                needNewDevice = true,
                newDeviceJumpUrl = payload.optString("jumpUrl"),
                newDeviceSig = payload.optString("newDevicePullQrCodeSig"),
            )

            else -> LoginActionResult(completed = true)
        }
    }

    private fun authorizedPostResponse(
        baseUrl: String,
        path: String,
        payload: JSONObject,
        retryUnauthorized: Boolean = true,
    ): ApiEnvelope {
        val endpoint = normalizeApiBaseUrl(baseUrl) + path
        val authorization = ensureCredential(baseUrl, phase = "request", clearedCredential = false)
        val envelope = executeApiRequest(
            endpoint = endpoint,
            path = path,
            payload = payload,
            authorization = authorization,
            phase = "request",
            clearedCredential = false,
        )
        if (envelope.code == 0) {
            return envelope
        }

        val failureCategory = classifyFailure(envelope.message, null)
        logApiFailure(
            path = path,
            phase = "request",
            category = failureCategory,
            detail = envelope.message,
            clearedCredential = false,
        )

        if (failureCategory != LoginFailureCategory.AUTH_TOKEN_EXPIRED || !retryUnauthorized) {
            return envelope
        }

        clearCredential()
        appendLoginLog("QQ login auth recovery: path=$path phase=auth-relogin detail=credential cache cleared")
        val retryAuthorization = ensureCredential(baseUrl, phase = "auth-relogin", clearedCredential = true)
        val retryEnvelope = executeApiRequest(
            endpoint = endpoint,
            path = path,
            payload = payload,
            authorization = retryAuthorization,
            phase = "retry",
            clearedCredential = true,
        )
        if (retryEnvelope.code != 0) {
            logApiFailure(
                path = path,
                phase = "retry",
                category = classifyFailure(retryEnvelope.message, null),
                detail = retryEnvelope.message,
                clearedCredential = true,
            )
        } else {
            appendLoginLog("QQ login api recovered: path=$path phase=retry clearedCredential=true")
        }
        return retryEnvelope
    }

    private fun ensureCredential(
        baseUrl: String,
        phase: String,
        clearedCredential: Boolean,
    ): String {
        if (credential.isNotBlank()) return credential

        val path = "/auth/login"
        val webUiToken = AppSecretStore.getOrCreateWebUiToken()
        appendLoginLog(
            "QQ login auth request: path=$path phase=$phase strategy=sha256(token.napcat) baseUrl=${normalizeBaseUrl(baseUrl)} configuredToken=${maskSecret(webUiToken)}",
        )
        val loginResponse = try {
            postJson(
                endpoint = normalizeApiBaseUrl(baseUrl) + path,
                payload = JSONObject().apply {
                    put("hash", sha256("$webUiToken.napcat"))
                },
                authorization = null,
            )
        } catch (error: Exception) {
            val category = if (classifyFailure(null, error) == LoginFailureCategory.NETWORK_FAILURE) {
                LoginFailureCategory.NETWORK_FAILURE
            } else {
                LoginFailureCategory.AUTH_LOGIN_FAILED
            }
            logApiFailure(
                path = path,
                phase = phase,
                category = category,
                detail = error.message ?: error.javaClass.simpleName,
                clearedCredential = clearedCredential,
            )
            throw LoginServiceException(
                category = category,
                message = error.message ?: "WebUI login failed",
                cause = error,
            )
        }
        if (loginResponse.optInt("code", 1) != 0) {
            val detail = loginResponse.optString("message").ifBlank { "WebUI login failed" }
            logApiFailure(
                path = path,
                phase = phase,
                category = LoginFailureCategory.AUTH_LOGIN_FAILED,
                detail = detail,
                clearedCredential = clearedCredential,
            )
            throw LoginServiceException(
                category = LoginFailureCategory.AUTH_LOGIN_FAILED,
                message = detail,
            )
        }

        credential = loginResponse
            .optJSONObject("data")
            ?.optString("Credential")
            .orEmpty()
        if (credential.isBlank()) {
            val detail = "Missing WebUI credential"
            logApiFailure(
                path = path,
                phase = phase,
                category = LoginFailureCategory.EMPTY_OR_MALFORMED_RESPONSE,
                detail = detail,
                clearedCredential = clearedCredential,
            )
            throw LoginServiceException(
                category = LoginFailureCategory.EMPTY_OR_MALFORMED_RESPONSE,
                message = detail,
            )
        }
        appendLoginLog("QQ login auth success: path=$path phase=$phase clearedCredential=$clearedCredential")
        return credential
    }

    private fun executeApiRequest(
        endpoint: String,
        path: String,
        payload: JSONObject,
        authorization: String,
        phase: String,
        clearedCredential: Boolean,
    ): ApiEnvelope {
        val response = try {
            postJson(
                endpoint = endpoint,
                payload = payload,
                authorization = authorization,
            )
        } catch (error: Exception) {
            val category = classifyFailure(null, error)
            logApiFailure(
                path = path,
                phase = phase,
                category = category,
                detail = error.message ?: error.javaClass.simpleName,
                clearedCredential = clearedCredential,
            )
            throw LoginServiceException(
                category = category,
                message = error.message ?: "QQ login request failed",
                cause = error,
            )
        }
        return toApiEnvelope(response)
    }

    private fun toApiEnvelope(response: JSONObject): ApiEnvelope {
        val code = response.optInt("code", 1)
        val message = response.optString("message").ifBlank {
            if (code == 0) "success" else "Unknown error"
        }
        return ApiEnvelope(
            code = code,
            message = message,
            data = response.opt("data").takeUnless { it == null || it == JSONObject.NULL },
        )
    }

    private fun logApiFailure(
        path: String,
        phase: String,
        category: LoginFailureCategory,
        detail: String,
        clearedCredential: Boolean,
    ) {
        appendLoginLog(
            "QQ login api error: path=$path phase=$phase category=$category clearedCredential=$clearedCredential detail=${sanitizeNapCatDetailForLogs(detail)}",
        )
    }

    private fun appendLoginLog(message: String) {
        AppLogger.append(message)
        RuntimeLogRepository.flush()
    }

    private fun postJson(
        endpoint: String,
        payload: JSONObject,
        authorization: String?,
    ): JSONObject {
        postJsonOverride?.let { override ->
            return override(endpoint, payload, authorization)
        }
        return try {
            val body = httpClient.execute(
                HttpRequestSpec(
                    method = HttpMethod.POST,
                    url = endpoint,
                    headers = buildMap {
                        put("Content-Type", "application/json")
                        if (!authorization.isNullOrBlank()) {
                            put("Authorization", "Bearer $authorization")
                        }
                    },
                    body = payload.toString(),
                    contentType = "application/json",
                    connectTimeoutMs = 10_000,
                    readTimeoutMs = 15_000,
                ),
            ).body
            if (body.isBlank()) {
                throw LoginServiceException(
                    category = LoginFailureCategory.EMPTY_OR_MALFORMED_RESPONSE,
                    message = "Empty response",
                )
            }
            JSONObject(body)
        } catch (error: LoginServiceException) {
            throw error
        } catch (error: JSONException) {
            throw LoginServiceException(
                category = LoginFailureCategory.EMPTY_OR_MALFORMED_RESPONSE,
                message = error.message ?: "Malformed response",
                cause = error,
            )
        }
    }

    private fun requireSuccess(response: ApiEnvelope): Any? {
        if (response.code != 0) {
            throw LoginServiceException(
                category = classifyFailure(response.message, null),
                message = response.message.ifBlank { "Unknown error" },
            )
        }
        return response.data
    }

    private fun requireObject(value: Any?): JSONObject {
        return value as? JSONObject ?: JSONObject()
    }

    private fun unwrapValue(value: Any?): String {
        return when (value) {
            null -> ""
            is String -> value
            else -> value.toString()
        }.trim()
    }

    private fun parseQuickLoginAccountList(value: Any?): List<SavedQqAccount> {
        val accounts = mutableListOf<SavedQqAccount>()
        when (value) {
            is JSONArray -> {
                for (index in 0 until value.length()) {
                    when (val item = value.opt(index)) {
                        is JSONObject -> {
                            val uin = item.optString("uin").trim()
                            if (uin.isNotBlank()) {
                                accounts += SavedQqAccount(
                                    uin = uin,
                                    nickName = item.optString("nickName").trim(),
                                    avatarUrl = item.optString("faceUrl").trim(),
                                )
                            }
                        }

                        is String -> {
                            val uin = item.trim()
                            if (uin.isNotBlank()) {
                                accounts += SavedQqAccount(uin = uin)
                            }
                        }
                    }
                }
            }

            is JSONObject -> {
                val array = value.optJSONArray("list")
                if (array != null) {
                    return parseQuickLoginAccountList(array)
                }
            }
        }
        return accounts.distinctBy { it.uin }
    }

    private fun normalizeBaseUrl(baseUrl: String): String {
        return baseUrl.trim().removeSuffix("/")
    }

    private fun normalizeApiBaseUrl(baseUrl: String): String {
        val normalized = normalizeBaseUrl(baseUrl)
        return if (normalized.endsWith("/api")) normalized else "$normalized/api"
    }

    private fun classifyFailure(
        message: String?,
        cause: Throwable?,
    ): LoginFailureCategory {
        val normalizedMessage = message.orEmpty().trim().lowercase()
        return when {
            normalizedMessage == "unauthorized" ||
                normalizedMessage.contains("token is invalid") ||
                normalizedMessage.contains("invalid token") -> LoginFailureCategory.AUTH_TOKEN_EXPIRED

            cause is AstrBotHttpException && cause.category == HttpFailureCategory.TIMEOUT -> LoginFailureCategory.NETWORK_FAILURE
            cause is AstrBotHttpException && cause.category == HttpFailureCategory.NETWORK -> LoginFailureCategory.NETWORK_FAILURE

            cause is SocketTimeoutException ||
                cause is ConnectException ||
                cause is UnknownHostException ||
                (cause is java.io.IOException && cause !is JSONException) -> LoginFailureCategory.NETWORK_FAILURE

            cause is JSONException ||
                normalizedMessage == "empty response" ||
                normalizedMessage.contains("missing webui credential") ||
                normalizedMessage.contains("malformed response") -> LoginFailureCategory.EMPTY_OR_MALFORMED_RESPONSE

            else -> LoginFailureCategory.API_BUSINESS_REJECTED
        }
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private fun md5Hex(value: String): String {
        // NapCat WebUI currently requires MD5 for QQ password submission.
        val digest = MessageDigest.getInstance("MD5").digest(value.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private fun maskSecret(value: String): String {
        return if (value.isBlank()) {
            "<empty>"
        } else {
            "<redacted:${value.length}>"
        }
    }

    private fun sanitizeNapCatDetailForLogs(detail: String): String {
        if (detail.isBlank()) return detail
        return detail
            .replace(Regex("""(configuredToken=)([^,\s]+)""")) { match ->
                match.groupValues[1] + maskSecret(match.groupValues[2])
            }
            .replace(Regex("""(credential=)([^,\s]+)""")) { match ->
                match.groupValues[1] + maskSecret(match.groupValues[2])
            }
            .replace(Regex("""(Authorization:\s*Bearer\s+)([^,\s]+)""", RegexOption.IGNORE_CASE)) { match ->
                match.groupValues[1] + maskSecret(match.groupValues[2])
            }
    }
}
