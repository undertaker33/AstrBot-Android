package com.astrbot.android.ui.qqlogin

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.astrbot.android.ui.app.MonochromeUi
import com.astrbot.android.ui.app.RegisterSecondaryTopBar
import com.astrbot.android.ui.app.SecondaryTopBarPlaceholder
import com.astrbot.android.ui.app.SecondaryTopBarSpec
import com.astrbot.android.ui.app.monochromeOutlinedTextFieldColors
import com.astrbot.android.ui.qqlogin.LoginModeToggle
import com.astrbot.android.ui.qqlogin.QQCaptchaCard
import com.astrbot.android.ui.qqlogin.SavedAccountDropdown
import com.astrbot.android.ui.qqlogin.buildLoginQrBitmap
import com.astrbot.android.ui.qqlogin.canQuickLogin
import com.astrbot.android.ui.qqlogin.qqDarkOutlinedButtonBorder
import com.astrbot.android.ui.qqlogin.qqDarkOutlinedButtonColors
import com.astrbot.android.ui.qqlogin.qqPrimaryButtonColors
import com.astrbot.android.ui.qqlogin.resolveQqLoginVersionMarker
import com.astrbot.android.ui.viewmodel.QQLoginViewModel

@Composable
fun QQAccountCenterScreen(
    onBack: () -> Unit,
    onOpenLogin: () -> Unit,
    qqLoginViewModel: QQLoginViewModel,
) {
    val context = LocalContext.current
    val loginState by qqLoginViewModel.loginState.collectAsState()
    val versionMarker = remember(context) { resolveQqLoginVersionMarker(context) }
    DisposableEffect(qqLoginViewModel, versionMarker) {
        qqLoginViewModel.onScreenVisible("qq-account", versionMarker)
        onDispose { qqLoginViewModel.onScreenHidden("qq-account") }
    }
    RegisterSecondaryTopBar(SecondaryTopBarSpec.SubPage(title = "QQ 账号", onBack = onBack))

    Scaffold(
        topBar = { SecondaryTopBarPlaceholder() },
        containerColor = MonochromeUi.pageBackground,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF171717),
                tonalElevation = 4.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = "QQ 登录中心",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (loginState.isLogin) {
                            "当前已登录：${loginState.nick.ifBlank { loginState.uin.ifBlank { "Unknown account" } }}"
                        } else {
                            loginState.statusText
                        },
                        color = Color.White.copy(alpha = 0.78f),
                    )
                    SavedAccountDropdown(
                        accounts = loginState.savedAccounts,
                        selectedUin = loginState.quickLoginUin.ifBlank { loginState.uin },
                        enabled = !loginState.isLogin,
                        onSelect = { account ->
                            qqLoginViewModel.quickLoginSavedAccount(account.uin)
                            Toast.makeText(context, "Quick login: ${account.uin}", Toast.LENGTH_SHORT).show()
                        },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onOpenLogin,
                            modifier = Modifier.weight(1f),
                            colors = qqPrimaryButtonColors(),
                        ) {
                            Text("去登录")
                        }
                        OutlinedButton(
                            onClick = { qqLoginViewModel.logoutCurrentAccount() },
                            modifier = Modifier.weight(1f),
                            enabled = loginState.bridgeReady && loginState.isLogin,
                            colors = qqDarkOutlinedButtonColors(),
                            border = qqDarkOutlinedButtonBorder(enabled = loginState.bridgeReady && loginState.isLogin),
                        ) {
                            Text("退出登录")
                        }
                    }
                    if (loginState.loginError.isNotBlank()) {
                        Text(
                            text = loginState.loginError,
                            color = Color(0xFFFFB4AB),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun QQLoginScreen(
    onBack: () -> Unit,
    qqLoginViewModel: QQLoginViewModel,
) {
    val context = LocalContext.current
    val loginState by qqLoginViewModel.loginState.collectAsState()
    val quickLoginEnabled = loginState.canQuickLogin()
    val qrBitmap = remember(loginState.qrCodeUrl) {
        loginState.qrCodeUrl.takeIf { it.isNotBlank() }?.let { buildLoginQrBitmap(it, 720) }
    }
    var loginMode by remember { mutableStateOf(QqLoginMode.Qr) }
    var uinInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    val versionMarker = remember(context) { resolveQqLoginVersionMarker(context) }
    DisposableEffect(qqLoginViewModel, versionMarker) {
        qqLoginViewModel.onScreenVisible("qq-login", versionMarker)
        onDispose { qqLoginViewModel.onScreenHidden("qq-login") }
    }

    LaunchedEffect(loginState.quickLoginUin, loginState.uin) {
        if (uinInput.isBlank()) {
            uinInput = loginState.quickLoginUin.ifBlank { loginState.uin }
        }
    }
    RegisterSecondaryTopBar(SecondaryTopBarSpec.SubPage(title = "去登录", onBack = onBack))

    Scaffold(
        topBar = { SecondaryTopBarPlaceholder() },
        containerColor = MonochromeUi.pageBackground,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 18.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                LoginModeToggle(
                    qrSelected = loginMode == QqLoginMode.Qr,
                    onSelectQr = { loginMode = QqLoginMode.Qr },
                    onSelectPassword = { loginMode = QqLoginMode.Password },
                )
            }

            if (loginMode == QqLoginMode.Qr) {
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MonochromeUi.cardBackground,
                        tonalElevation = 2.dp,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Text("扫码登录", fontWeight = FontWeight.SemiBold)
                            if (qrBitmap != null && !loginState.isLogin) {
                                Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = "QQ 登录二维码",
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else if (loginState.isLogin) {
                                Text("当前已登录：${loginState.nick.ifBlank { loginState.uin.ifBlank { "未知账号" } }}")
                            } else {
                                Text("NapCat WebUI 就绪后，这里会显示二维码。")
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { qqLoginViewModel.refreshQrCode() },
                                    enabled = loginState.bridgeReady && !loginState.isLogin,
                                    colors = qqPrimaryButtonColors(),
                                ) {
                                    Text("刷新二维码")
                                }
                                OutlinedButton(
                                    onClick = { qqLoginViewModel.quickLoginSavedAccount() },
                                    enabled = quickLoginEnabled,
                                ) {
                                    Text("快捷登录")
                                }
                            }
                        }
                    }
                }
            } else {
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MonochromeUi.cardBackground,
                        tonalElevation = 2.dp,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Text("账号密码登录", fontWeight = FontWeight.SemiBold)
                            OutlinedTextField(
                                value = uinInput,
                                onValueChange = { uinInput = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("输入 QQ 号") },
                                singleLine = true,
                                colors = monochromeOutlinedTextFieldColors(),
                            )
                            OutlinedTextField(
                                value = passwordInput,
                                onValueChange = { passwordInput = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("输入 QQ 密码") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                colors = monochromeOutlinedTextFieldColors(),
                            )
                            Button(
                                onClick = { qqLoginViewModel.passwordLogin(uinInput, passwordInput) },
                                enabled = loginState.bridgeReady && uinInput.isNotBlank() && passwordInput.isNotBlank(),
                                modifier = Modifier.fillMaxWidth(),
                                colors = qqPrimaryButtonColors(),
                            ) {
                                Text("登录")
                            }
                            if (loginState.needNewDevice) {
                                Button(
                                    onClick = { qqLoginViewModel.newDeviceLogin(uinInput, passwordInput) },
                                    enabled = loginState.bridgeReady && uinInput.isNotBlank() && passwordInput.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = qqPrimaryButtonColors(),
                                ) {
                                    Text("继续设备验证")
                                }
                            }
                            if (loginState.loginError.isNotBlank()) {
                                Text(loginState.loginError, color = Color(0xFFB3261E))
                            }
                        }
                    }
                }

                if (loginState.needCaptcha) {
                    item {
                        QQCaptchaCard(
                            onSubmit = { ticket, randstr, sid ->
                                qqLoginViewModel.captchaLogin(uinInput, passwordInput, ticket, randstr, sid)
                            },
                        )
                    }
                }
            }
        }
    }
}

private enum class QqLoginMode {
    Qr,
    Password,
}
