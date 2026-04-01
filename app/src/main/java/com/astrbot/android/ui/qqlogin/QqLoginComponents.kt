package com.astrbot.android.ui.qqlogin

import android.content.Context
import android.graphics.Bitmap
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.astrbot.android.model.NapCatLoginState
import com.astrbot.android.model.SavedQqAccount
import com.astrbot.android.ui.MonochromeUi
import com.astrbot.android.ui.monochromeOutlinedTextFieldColors
import com.astrbot.android.ui.viewmodel.QQLoginViewModel
import com.astrbot.android.ui.viewmodel.buildQqLoginVersionMarker
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LoginModeToggle(
    qrSelected: Boolean,
    onSelectQr: () -> Unit,
    onSelectPassword: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color(0xFFE7E7E4),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QQModeChip(
                label = "扫码登录",
                selected = qrSelected,
                modifier = Modifier.weight(1f),
                onClick = onSelectQr,
            )
            QQModeChip(
                label = "账号密码登录",
                selected = !qrSelected,
                modifier = Modifier.weight(1f),
                onClick = onSelectPassword,
            )
        }
    }
}

@Composable
private fun QQModeChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = if (selected) Color.White else Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                label,
                color = if (selected) Color(0xFF202020) else Color(0xFF777777),
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            )
        }
    }
}

@Composable
fun QQCaptchaCard(
    onSubmit: (String, String, String) -> Unit,
) {
    var ticketInput by remember { mutableStateOf("") }
    var randstrInput by remember { mutableStateOf("") }
    var sidInput by remember { mutableStateOf("") }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MonochromeUi.cardBackground,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("验证码校验", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = ticketInput,
                onValueChange = { ticketInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Ticket") },
                colors = monochromeOutlinedTextFieldColors(),
            )
            OutlinedTextField(
                value = randstrInput,
                onValueChange = { randstrInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Randstr") },
                colors = monochromeOutlinedTextFieldColors(),
            )
            OutlinedTextField(
                value = sidInput,
                onValueChange = { sidInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Sid（可选）") },
                colors = monochromeOutlinedTextFieldColors(),
            )
            Button(
                onClick = { onSubmit(ticketInput, randstrInput, sidInput) },
                enabled = ticketInput.isNotBlank() && randstrInput.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                colors = qqPrimaryButtonColors(),
            ) {
                Text("提交验证码")
            }
        }
    }
}

@Composable
fun qqPrimaryButtonColors() = ButtonDefaults.buttonColors(
    containerColor = Color(0xFF1B1B1B),
    contentColor = Color.White,
    disabledContainerColor = Color(0xFFD8D8D4),
    disabledContentColor = Color.White.copy(alpha = 0.75f),
)

@Composable
fun qqDarkOutlinedButtonColors() = ButtonDefaults.outlinedButtonColors(
    containerColor = Color.Transparent,
    contentColor = Color.White,
    disabledContainerColor = Color.Transparent,
    disabledContentColor = Color.White.copy(alpha = 0.28f),
)

fun qqDarkOutlinedButtonBorder(enabled: Boolean): BorderStroke {
    return BorderStroke(
        width = 1.5.dp,
        color = if (enabled) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.18f),
    )
}

fun NapCatLoginState.canQuickLogin(): Boolean {
    return bridgeReady && savedAccounts.isNotEmpty() && !isLogin
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedAccountDropdown(
    accounts: List<SavedQqAccount>,
    selectedUin: String,
    enabled: Boolean,
    onSelect: (SavedQqAccount) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedAccount = accounts.firstOrNull { it.uin == selectedUin }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("saved-account-dropdown-container"),
    ) {
        OutlinedTextField(
            value = selectedAccount.displayLabel().ifBlank { selectedUin.ifBlank { "请选择QQ" } },
            onValueChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .testTag("saved-account-dropdown-field"),
            readOnly = true,
            enabled = enabled,
            label = { Text("请选择QQ") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled && accounts.isNotEmpty())
            },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                disabledTextColor = Color.White.copy(alpha = 0.55f),
                focusedBorderColor = Color.White.copy(alpha = 0.8f),
                unfocusedBorderColor = Color.White.copy(alpha = 0.35f),
                disabledBorderColor = Color.White.copy(alpha = 0.18f),
                focusedLabelColor = Color.White.copy(alpha = 0.8f),
                unfocusedLabelColor = Color.White.copy(alpha = 0.65f),
                disabledLabelColor = Color.White.copy(alpha = 0.4f),
                focusedTrailingIconColor = Color.White,
                unfocusedTrailingIconColor = Color.White,
                disabledTrailingIconColor = Color.White.copy(alpha = 0.4f),
            ),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clickable(enabled = enabled && accounts.isNotEmpty()) {
                    expanded = !expanded
                }
                .testTag("saved-account-dropdown-overlay"),
        )
        DropdownMenu(
            expanded = expanded && enabled && accounts.isNotEmpty(),
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(),
        ) {
            accounts.forEach { account ->
                DropdownMenuItem(
                    text = { Text(account.displayLabel()) },
                    onClick = {
                        expanded = false
                        onSelect(account)
                    },
                )
            }
        }
    }
}

fun SavedQqAccount?.displayLabel(): String {
    val account = this ?: return ""
    return when {
        account.nickName.isNotBlank() -> "${account.nickName} (${account.uin})"
        else -> account.uin
    }
}

@Composable
fun QQTencentCaptchaCard(
    proofWaterUrl: String,
    onSuccess: (ticket: String, randstr: String, sid: String) -> Unit,
    onCancel: () -> Unit,
) {
    var statusText by remember(proofWaterUrl) { mutableStateOf("Loading captcha...") }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MonochromeUi.cardBackground,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("验证码验证", fontWeight = FontWeight.SemiBold)
            Text(
                text = statusText,
                color = Color(0xFF6B6B6B),
            )
            TencentCaptchaWebView(
                proofWaterUrl = proofWaterUrl,
                onStatus = { statusText = it },
                onSuccess = onSuccess,
                onCancel = onCancel,
            )
        }
    }
}

@Composable
fun QQNewDeviceVerifyCard(
    qqLoginViewModel: QQLoginViewModel,
    onVerified: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var qrUrl by remember { mutableStateOf("") }
    var bytesToken by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("Loading verification QR...") }
    var loading by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf("") }

    suspend fun refreshQr() {
        loading = true
        errorText = ""
        statusText = "Loading verification QR..."
        runCatching {
            qqLoginViewModel.getNewDeviceQRCode()
        }.onSuccess { result ->
            qrUrl = result.qrUrl
            bytesToken = result.bytesToken
            statusText = "Scan with mobile QQ to verify this device"
            loading = false
        }.onFailure { error ->
            errorText = error.message ?: "Failed to load verification QR"
            statusText = errorText
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshQr()
    }

    LaunchedEffect(bytesToken) {
        if (bytesToken.isBlank()) return@LaunchedEffect
        while (true) {
            delay(2500)
            val result = runCatching {
                qqLoginViewModel.pollNewDeviceQRCode(bytesToken)
            }.getOrNull() ?: continue
            when (result.guaranteeStatus) {
                3 -> statusText = "Scanned. Confirm on mobile QQ."
                1 -> {
                    statusText = "Verified. Continuing login..."
                    onVerified(result.successToken)
                    return@LaunchedEffect
                }
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MonochromeUi.cardBackground,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("新设备验证", fontWeight = FontWeight.SemiBold)
            Text(statusText, color = Color(0xFF6B6B6B))
            when {
                loading -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                errorText.isNotBlank() -> {
                    Text(errorText, color = Color(0xFFB3261E))
                    OutlinedButton(
                        onClick = { scope.launch { refreshQr() } },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Retry")
                    }
                }

                qrUrl.isNotBlank() -> {
                    Image(
                        bitmap = buildLoginQrBitmap(qrUrl, 720).asImageBitmap(),
                        contentDescription = "New device verification QR",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(
                        onClick = { scope.launch { refreshQr() } },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Refresh verification QR")
                    }
                }
            }
        }
    }
}

@Composable
private fun TencentCaptchaWebView(
    proofWaterUrl: String,
    onStatus: (String) -> Unit,
    onSuccess: (ticket: String, randstr: String, sid: String) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val html = remember(proofWaterUrl) { buildTencentCaptchaHtml(proofWaterUrl) }
    val callbackHolder = remember {
        object {
            var handled = false
        }
    }

    DisposableEffect(Unit) {
        onDispose { callbackHolder.handled = false }
    }

    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .background(Color.White, RoundedCornerShape(16.dp)),
        factory = {
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                webChromeClient = WebChromeClient()
                webViewClient = WebViewClient()
                addJavascriptInterface(
                    TencentCaptchaBridge(
                        onStatus = onStatus,
                        onSuccess = { ticket, randstr, sid ->
                            if (!callbackHolder.handled) {
                                callbackHolder.handled = true
                                onSuccess(ticket, randstr, sid)
                            }
                        },
                        onCancel = {
                            if (!callbackHolder.handled) {
                                callbackHolder.handled = true
                                onCancel()
                            }
                        },
                    ),
                    "AstrBotCaptchaBridge",
                )
                loadDataWithBaseURL("https://captcha.gtimg.com/", html, "text/html", "utf-8", null)
            }
        },
        update = { webView ->
            callbackHolder.handled = false
            webView.loadDataWithBaseURL("https://captcha.gtimg.com/", html, "text/html", "utf-8", null)
        },
    )
}

private class TencentCaptchaBridge(
    private val onStatus: (String) -> Unit,
    private val onSuccess: (String, String, String) -> Unit,
    private val onCancel: () -> Unit,
) {
    @JavascriptInterface
    fun onStatus(message: String) {
        onStatus(message)
    }

    @JavascriptInterface
    fun onSuccess(ticket: String, randstr: String, sid: String) {
        onSuccess(ticket, randstr, sid)
    }

    @JavascriptInterface
    fun onCancel() {
        onCancel()
    }
}

private fun buildTencentCaptchaHtml(proofWaterUrl: String): String {
    val escapedProofWaterUrl = proofWaterUrl
        .replace("\\", "\\\\")
        .replace("'", "\\'")
    return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="utf-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1" />
          <style>
            body { margin: 0; font-family: sans-serif; background: #ffffff; color: #111111; }
            .wrap { min-height: 100vh; display: flex; align-items: center; justify-content: center; padding: 16px; box-sizing: border-box; text-align: center; }
          </style>
        </head>
        <body>
          <div class="wrap">Loading Tencent captcha...</div>
          <script>
            const proofWaterUrl = '$escapedProofWaterUrl';
            function parseParams(url) {
              const params = {};
              try {
                const parsed = new URL(url);
                parsed.searchParams.forEach((value, key) => params[key] = value);
              } catch (error) {}
              return params;
            }
            function setStatus(message) {
              if (window.AstrBotCaptchaBridge) {
                window.AstrBotCaptchaBridge.onStatus(message);
              }
            }
            function finishSuccess(ticket, randstr, sid) {
              if (window.AstrBotCaptchaBridge) {
                window.AstrBotCaptchaBridge.onSuccess(ticket, randstr, sid || '');
              }
            }
            function finishCancel() {
              if (window.AstrBotCaptchaBridge) {
                window.AstrBotCaptchaBridge.onCancel();
              }
            }
            function fallback(appid, sid) {
              finishSuccess('terror_1001_' + appid + '_' + Math.floor(Date.now() / 1000), '@' + Math.random().toString(36).substring(2), sid);
            }
            function loadScript(src) {
              return new Promise((resolve, reject) => {
                const script = document.createElement('script');
                script.src = src;
                script.onload = resolve;
                script.onerror = reject;
                document.head.appendChild(script);
              });
            }
            async function run() {
              const params = parseParams(proofWaterUrl);
              const appid = params.aid || '2081081773';
              const sid = params.sid || '';
              setStatus('Loading captcha...');
              try {
                await loadScript('https://captcha.gtimg.com/TCaptcha.js');
              } catch (firstError) {
                try {
                  await loadScript('https://ssl.captcha.qq.com/TCaptcha.js');
                } catch (secondError) {
                  setStatus('Captcha script failed to load. Using fallback token.');
                  fallback(appid, sid);
                  return;
                }
              }
              if (!window.TencentCaptcha) {
                setStatus('Captcha init failed. Using fallback token.');
                fallback(appid, sid);
                return;
              }
              setStatus('Waiting for captcha verification...');
              try {
                const captcha = new window.TencentCaptcha(appid, function(result) {
                  if (result && result.ret === 0 && result.ticket && result.randstr) {
                    finishSuccess(result.ticket, result.randstr, sid);
                  } else {
                    finishCancel();
                  }
                }, {
                  type: 'popup',
                  showHeader: false,
                  login_appid: params.login_appid,
                  uin: params.uin,
                  sid: params.sid,
                  enableAged: true,
                });
                captcha.show();
              } catch (error) {
                setStatus('Captcha popup failed to start. Using fallback token.');
                fallback(appid, sid);
              }
            }
            run();
          </script>
        </body>
        </html>
    """.trimIndent()
}

fun buildLoginQrBitmap(content: String, sizePx: Int): Bitmap {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    for (x in 0 until sizePx) {
        for (y in 0 until sizePx) {
            bitmap.setPixel(
                x,
                y,
                if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE,
            )
        }
    }
    return bitmap
}

fun resolveQqLoginVersionMarker(context: Context): String {
    return runCatching {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        buildQqLoginVersionMarker(
            versionName = packageInfo.versionName.orEmpty().ifBlank { "unknown" },
            versionCode = packageInfo.longVersionCode,
        )
    }.getOrElse { error ->
        "QQ login diagnostics build: versionName=unknown versionCode=unknown marker=qq-login-diag-v3-poll-singleton detail=${error.javaClass.simpleName}"
    }
}
