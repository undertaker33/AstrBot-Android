package com.astrbot.android.ui.screen

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astrbot.android.R
import com.astrbot.android.data.AppLanguage
import com.astrbot.android.data.AppPreferencesRepository
import com.astrbot.android.data.ProviderRepository
import com.astrbot.android.data.RuntimeAssetRepository
import com.astrbot.android.data.ThemeMode
import com.astrbot.android.data.TtsVoiceAssetRepository
import com.astrbot.android.data.VoiceCloneService
import com.astrbot.android.model.RuntimeAssetEntryState
import com.astrbot.android.model.RuntimeAssetId
import com.astrbot.android.model.ProviderType
import com.astrbot.android.ui.AppUiTransitionState
import com.astrbot.android.ui.MonochromeUi
import com.astrbot.android.ui.monochromeOutlinedTextFieldColors
import com.astrbot.android.ui.viewmodel.RuntimeAssetViewModel
import com.astrbot.android.ui.viewmodel.QQLoginViewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MeScreen(
    onOpenQqAccount: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenAssets: () -> Unit,
) {
    EntryListPage(
        entries = listOf(
            EntryCardState(
                title = stringResource(R.string.me_card_qq_title),
                subtitle = stringResource(R.string.me_card_qq_subtitle),
                icon = Icons.Outlined.PersonOutline,
                onClick = onOpenQqAccount,
            ),
            EntryCardState(
                title = stringResource(R.string.me_card_settings_title),
                subtitle = stringResource(R.string.me_card_settings_subtitle),
                icon = Icons.Outlined.Settings,
                onClick = onOpenSettings,
            ),
            EntryCardState(
                title = stringResource(R.string.me_card_logs_title),
                subtitle = stringResource(R.string.me_card_logs_subtitle),
                icon = Icons.Outlined.Refresh,
                onClick = onOpenLogs,
            ),
            EntryCardState(
                title = stringResource(R.string.me_card_assets_title),
                subtitle = stringResource(R.string.me_card_assets_subtitle),
                icon = Icons.Outlined.CloudDownload,
                onClick = onOpenAssets,
            ),
        ),
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun QQAccountScreen(
    onBack: () -> Unit,
    qqLoginViewModel: QQLoginViewModel = viewModel(),
) {
    val loginState by qqLoginViewModel.loginState.collectAsState()
    val uriHandler = LocalUriHandler.current
    val qrBitmap = remember(loginState.qrCodeUrl) {
        loginState.qrCodeUrl.takeIf { it.isNotBlank() }?.let { buildAccountQrBitmap(it, 720) }
    }
    val scope = rememberCoroutineScope()
    var uinInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    val uinBringIntoViewRequester = remember { BringIntoViewRequester() }
    val passwordBringIntoViewRequester = remember { BringIntoViewRequester() }

    LaunchedEffect(loginState.quickLoginUin, loginState.uin) {
        if (uinInput.isBlank()) {
            uinInput = loginState.quickLoginUin.ifBlank { loginState.uin }
        }
    }

    Scaffold(
        topBar = { SubPageHeader(title = stringResource(R.string.nav_qq_account), onBack = onBack) },
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MonochromeUi.pageBackground,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = Color(0xFF161616),
                    tonalElevation = 4.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            stringResource(R.string.qq_account_center_title),
                            color = Color.White,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(loginState.statusText, color = Color.White.copy(alpha = 0.78f))
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                QuickActionChip(stringResource(R.string.qq_login_refresh_status), Icons.Outlined.Refresh) { qqLoginViewModel.refreshNow() }
                                QuickActionChip(stringResource(R.string.qq_login_refresh_qr), Icons.Outlined.QrCode2) { qqLoginViewModel.refreshQrCode() }
                            }
                            if (loginState.quickLoginUin.isNotBlank() && !loginState.isLogin) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    QuickActionChip(stringResource(R.string.qq_login_quick_login), Icons.Outlined.Bolt) {
                                        qqLoginViewModel.quickLoginSavedAccount()
                                    }
                                }
                            }
                            if (loginState.isLogin) {
                                Text(
                                    stringResource(
                                        R.string.qq_login_current_account,
                                        loginState.nick.ifBlank { loginState.uin.ifBlank { stringResource(R.string.qq_login_unknown_account) } },
                                    ),
                                    color = Color.White.copy(alpha = 0.78f),
                                )
                                Text(
                                    stringResource(R.string.qq_login_center_no_logout),
                                    color = Color.White.copy(alpha = 0.58f),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
            item {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MonochromeUi.cardBackground,
                    tonalElevation = 2.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(stringResource(R.string.qq_login_qr_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        if (loginState.quickLoginUin.isNotBlank()) {
                            Text(
                                stringResource(R.string.qq_login_saved_account, loginState.quickLoginUin),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                            )
                            if (!loginState.isLogin) {
                                Button(
                                    onClick = { qqLoginViewModel.quickLoginSavedAccount() },
                                    enabled = loginState.quickLoginUin.isNotBlank(),
                                    colors = monochromeButtonColors(),
                                ) {
                                    Text(stringResource(R.string.qq_login_quick_login))
                                }
                            }
                        }
                        if (qrBitmap != null && !loginState.isLogin) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.qq_login_qr_title),
                            modifier = Modifier.fillMaxWidth(),
                        )
                            Text(loginState.qrCodeUrl, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f))
                        } else if (loginState.isLogin) {
                            Text(
                                stringResource(
                                    R.string.qq_login_current_account,
                                    loginState.nick.ifBlank { loginState.uin.ifBlank { stringResource(R.string.qq_login_unknown_account) } },
                                ),
                            )
                        } else {
                            Text(stringResource(R.string.qq_login_qr_waiting))
                        }
                        if (loginState.captchaUrl.isNotBlank()) {
                            OutlinedButton(onClick = { uriHandler.openUri(loginState.captchaUrl) }) {
                                Text(stringResource(R.string.qq_login_open_captcha))
                            }
                        }
                        if (loginState.newDeviceJumpUrl.isNotBlank()) {
                            OutlinedButton(onClick = { uriHandler.openUri(loginState.newDeviceJumpUrl) }) {
                                Text(stringResource(R.string.qq_login_open_device_verify))
                            }
                        }
                    }
                }
            }
            item {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = Color.White,
                    tonalElevation = 2.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(stringResource(R.string.qq_login_password_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = uinInput,
                            onValueChange = { uinInput = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .bringIntoViewRequester(uinBringIntoViewRequester)
                                .onFocusChanged { state ->
                                    if (state.isFocused) {
                                        scope.launch {
                                            delay(250)
                                            uinBringIntoViewRequester.bringIntoView()
                                        }
                                    }
                                },
                            label = { Text(stringResource(R.string.qq_login_account_label)) },
                            singleLine = true,
                            colors = monochromeOutlinedTextFieldColors(),
                        )
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .bringIntoViewRequester(passwordBringIntoViewRequester)
                                .onFocusChanged { state ->
                                    if (state.isFocused) {
                                        scope.launch {
                                            delay(250)
                                            passwordBringIntoViewRequester.bringIntoView()
                                        }
                                    }
                                },
                            label = { Text(stringResource(R.string.qq_login_password_label)) },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            colors = monochromeOutlinedTextFieldColors(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { qqLoginViewModel.passwordLogin(uinInput, passwordInput) },
                                enabled = loginState.bridgeReady && uinInput.isNotBlank() && passwordInput.isNotBlank(),
                                colors = monochromeButtonColors(),
                            ) {
                                Text(stringResource(R.string.qq_login_password_action))
                            }
                            OutlinedButton(
                                onClick = { qqLoginViewModel.saveQuickLoginAccount(uinInput) },
                                enabled = loginState.bridgeReady && uinInput.isNotBlank(),
                            ) {
                                Text(stringResource(R.string.qq_login_save_account))
                            }
                        }
                        if (loginState.needNewDevice) {
                            Button(
                                onClick = { qqLoginViewModel.newDeviceLogin(uinInput, passwordInput) },
                                enabled = loginState.bridgeReady && uinInput.isNotBlank() && passwordInput.isNotBlank(),
                                colors = monochromeButtonColors(),
                            ) {
                                Text(stringResource(R.string.qq_login_continue_device))
                            }
                        }
                        if (loginState.loginError.isNotBlank()) {
                            Text(loginState.loginError, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            if (loginState.needCaptcha) {
                item {
                    CaptchaCard(
                        onSubmit = { ticket, randstr, sid ->
                            qqLoginViewModel.captchaLogin(uinInput, passwordInput, ticket, randstr, sid)
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsHubScreen(
    onBack: () -> Unit,
    onOpenRuntime: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    LocalConfiguration.current
    val repository = remember(context) { AppPreferencesRepository(context.applicationContext) }
    val settings by repository.settings.collectAsState(
        initial = com.astrbot.android.data.AppSettings(
            qqEnabled = true,
            napCatContainerEnabled = true,
            preferredChatProvider = "",
        ),
    )
    val scope = rememberCoroutineScope()
    val currentLanguage = currentApplicationLanguage()

    SubPageScaffold(
        title = stringResource(R.string.nav_settings),
        onBack = onBack,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SettingSelectionCard(
                    title = stringResource(R.string.settings_language_title),
                    subtitle = stringResource(R.string.settings_language_subtitle),
                    icon = Icons.Outlined.Language,
                    selectedLabel = when (currentLanguage) {
                        AppLanguage.CHINESE -> stringResource(R.string.settings_language_zh)
                        AppLanguage.ENGLISH -> stringResource(R.string.settings_language_en)
                    },
                    options = listOf(
                        AppLanguage.CHINESE.value to stringResource(R.string.settings_language_zh),
                        AppLanguage.ENGLISH.value to stringResource(R.string.settings_language_en),
                    ),
                    onSelect = { value ->
                        scope.launch {
                            AppUiTransitionState.requestTransition(MonochromeUi.pageBackground.toArgb())
                            delay(50)
                            val locales = LocaleListCompat.forLanguageTags(value)
                            if (AppCompatDelegate.getApplicationLocales() != locales) {
                                AppCompatDelegate.setApplicationLocales(locales)
                            }
                        }
                    },
                )
            }
            item {
                SettingSelectionCard(
                    title = stringResource(R.string.settings_theme_title),
                    subtitle = stringResource(R.string.settings_theme_subtitle),
                    icon = Icons.Outlined.Palette,
                    selectedLabel = when (settings.themeMode) {
                        ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
                        ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
                        ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
                    },
                    options = listOf(
                        ThemeMode.LIGHT.value to stringResource(R.string.settings_theme_light),
                        ThemeMode.DARK.value to stringResource(R.string.settings_theme_dark),
                        ThemeMode.SYSTEM.value to stringResource(R.string.settings_theme_system),
                    ),
                    onSelect = { value ->
                        scope.launch {
                            AppUiTransitionState.requestTransition(MonochromeUi.pageBackground.toArgb())
                            delay(50)
                            repository.setThemeMode(ThemeMode.fromValue(value))
                        }
                    },
                )
            }
            item {
                EntryCardState(
                    title = stringResource(R.string.settings_runtime_entry_title),
                    subtitle = stringResource(R.string.settings_runtime_entry_subtitle),
                    icon = Icons.Outlined.Settings,
                    onClick = onOpenRuntime,
                ).also { entry ->
                    AccountEntryCard(entry)
                }
            }
        }
    }
}

@Composable
fun AssetManagementScreen(
    onBack: () -> Unit,
    onOpenAsset: (String) -> Unit,
    assetViewModel: RuntimeAssetViewModel = viewModel(),
) {
    val assetState by assetViewModel.state.collectAsState()
    val assetItems = assetState.assets

    SubPageScaffold(
        title = stringResource(R.string.nav_asset_management),
        onBack = onBack,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MonochromeUi.cardBackground,
                    tonalElevation = 2.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.asset_list_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.asset_list_desc),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                        )
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    assetItems.forEach { item ->
                        RuntimeAssetEntryCard(
                            item = item,
                            onClick = { onOpenAsset(item.catalog.id.value) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AssetDetailScreen(
    assetId: String,
    onBack: () -> Unit,
    assetViewModel: RuntimeAssetViewModel = viewModel(),
) {
    val assetState by assetViewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val providerOptions by ProviderRepository.providers.collectAsState()
    val voiceAssets by TtsVoiceAssetRepository.assets.collectAsState()
    val resolvedAsset = assetState.assets.firstOrNull { it.catalog.id.value == assetId }
        ?: assetState.assets.firstOrNull()
        ?: return
    val ttsAssetState = remember(assetState.assets, assetId) {
        if (resolvedAsset.catalog.id == RuntimeAssetId.ON_DEVICE_TTS) {
            RuntimeAssetRepository.ttsAssetState(context)
        } else {
            null
        }
    }
    val cloneProviders = providerOptions.filter { it.providerType == ProviderType.BAILIAN_TTS || it.providerType == ProviderType.MINIMAX_TTS }
    var referenceName by remember { mutableStateOf("") }
    var referenceLocalPath by remember { mutableStateOf("") }
    var referenceRemoteUrl by remember { mutableStateOf("") }
    var selectedReferenceAssetId by remember(voiceAssets) { mutableStateOf(voiceAssets.firstOrNull()?.id.orEmpty()) }
    var selectedCloneProviderId by remember(cloneProviders) { mutableStateOf(cloneProviders.firstOrNull()?.id.orEmpty()) }
    var cloneDisplayName by remember { mutableStateOf("") }

    SubPageScaffold(
        title = stringResource(resolvedAsset.catalog.titleRes),
        onBack = onBack,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MonochromeUi.cardBackground,
                    tonalElevation = 2.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = stringResource(resolvedAsset.catalog.titleRes),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(resolvedAsset.catalog.descriptionRes),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                        )
                        Text(
                            text = assetStatusLabel(resolvedAsset),
                            fontWeight = FontWeight.Medium,
                        )
                        if (resolvedAsset.lastAction.isNotBlank()) {
                            Text(
                                text = stringResource(R.string.asset_last_action, resolvedAsset.lastAction),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                            )
                        }
                        Text(resolvedAsset.details, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f))
                        if (resolvedAsset.busy) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.width(18.dp),
                                    strokeWidth = 2.dp,
                                )
                                Text(
                                    text = stringResource(R.string.asset_busy_hint),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                                )
                            }
                        }
                    }
                }
            }
            if (resolvedAsset.catalog.id == RuntimeAssetId.TTS_VOICE_ASSETS) {
                item {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MonochromeUi.cardBackground,
                        tonalElevation = 2.dp,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = "Import Reference Audio",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            OutlinedTextField(
                                value = referenceName,
                                onValueChange = { referenceName = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Name") },
                                colors = monochromeOutlinedTextFieldColors(),
                            )
                            OutlinedTextField(
                                value = referenceLocalPath,
                                onValueChange = { referenceLocalPath = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Local path (optional)") },
                                colors = monochromeOutlinedTextFieldColors(),
                            )
                            OutlinedTextField(
                                value = referenceRemoteUrl,
                                onValueChange = { referenceRemoteUrl = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Remote URL (optional)") },
                                colors = monochromeOutlinedTextFieldColors(),
                            )
                            Button(
                                onClick = {
                                    val asset = TtsVoiceAssetRepository.upsertReferenceAsset(
                                        name = referenceName,
                                        localPath = referenceLocalPath,
                                        remoteUrl = referenceRemoteUrl,
                                        source = if (referenceLocalPath.isNotBlank()) "local" else "remote",
                                    )
                                    selectedReferenceAssetId = asset.id
                                    referenceName = ""
                                    referenceLocalPath = ""
                                    referenceRemoteUrl = ""
                                },
                                enabled = referenceName.isNotBlank() && (referenceLocalPath.isNotBlank() || referenceRemoteUrl.isNotBlank()),
                                colors = monochromeButtonColors(),
                            ) {
                                Text("Save Reference Audio")
                            }
                        }
                    }
                }
                item {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MonochromeUi.cardBackground,
                        tonalElevation = 2.dp,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = "Clone Voice",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            SelectionField(
                                title = "Reference Audio",
                                options = voiceAssets.map { it.id to it.name },
                                selectedId = selectedReferenceAssetId,
                                onSelect = { selectedReferenceAssetId = it },
                            )
                            SelectionField(
                                title = "Provider Model",
                                options = cloneProviders.map { it.id to "${it.name} (${it.model})" },
                                selectedId = selectedCloneProviderId,
                                onSelect = { selectedCloneProviderId = it },
                            )
                            OutlinedTextField(
                                value = cloneDisplayName,
                                onValueChange = { cloneDisplayName = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Cloned voice name") },
                                colors = monochromeOutlinedTextFieldColors(),
                            )
                            Button(
                                onClick = {
                                    val asset = voiceAssets.firstOrNull { it.id == selectedReferenceAssetId }
                                    val provider = cloneProviders.firstOrNull { it.id == selectedCloneProviderId }
                                    if (asset == null || provider == null) {
                                        Toast.makeText(context, "Select a reference audio and a provider model first.", Toast.LENGTH_LONG).show()
                                    } else {
                                        scope.launch {
                                            runCatching {
                                                VoiceCloneService.cloneVoice(
                                                    provider = provider,
                                                    asset = asset,
                                                    displayName = cloneDisplayName.ifBlank { asset.name },
                                                )
                                            }.onSuccess { voiceId ->
                                                TtsVoiceAssetRepository.saveProviderBinding(
                                                    assetId = asset.id,
                                                    providerId = provider.id,
                                                    providerType = provider.providerType,
                                                    model = provider.model,
                                                    voiceId = voiceId,
                                                    displayName = cloneDisplayName.ifBlank { asset.name },
                                                )
                                                Toast.makeText(context, "Voice cloned successfully.", Toast.LENGTH_LONG).show()
                                            }.onFailure { error ->
                                                Toast.makeText(
                                                    context,
                                                    error.message ?: error.javaClass.simpleName,
                                                    Toast.LENGTH_LONG,
                                                ).show()
                                            }
                                        }
                                    }
                                },
                                enabled = voiceAssets.isNotEmpty() && cloneProviders.isNotEmpty() && cloneDisplayName.isNotBlank(),
                                colors = monochromeButtonColors(),
                            ) {
                                Text("Start Voice Clone")
                            }
                            if (cloneProviders.isEmpty()) {
                                Text(
                                    text = "Add a Qwen or MiniMax TTS model first, then return here to clone voices.",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                                )
                            }
                        }
                    }
                }
                items(items = voiceAssets, key = { asset -> asset.id }) { asset ->
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MonochromeUi.cardBackground,
                        tonalElevation = 2.dp,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(asset.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            asset.localPath.takeIf { it.isNotBlank() }?.let {
                                Text("Local: $it", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f))
                            }
                            asset.remoteUrl.takeIf { it.isNotBlank() }?.let {
                                Text("Remote: $it", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f))
                            }
                            if (asset.providerBindings.isEmpty()) {
                                Text("No cloned voices yet.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f))
                            } else {
                                asset.providerBindings.forEach { binding ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(binding.displayName, fontWeight = FontWeight.Medium)
                                            Text(
                                                "${binding.providerType.name} / ${binding.model} / ${binding.voiceId}",
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                                            )
                                        }
                                        TextButton(onClick = { TtsVoiceAssetRepository.deleteBinding(asset.id, binding.id) }) {
                                            Text("Delete")
                                        }
                                    }
                                }
                            }
                            OutlinedButton(onClick = { TtsVoiceAssetRepository.deleteReferenceAsset(asset.id) }) {
                                Text("Remove Reference Audio")
                            }
                        }
                    }
                }
            }
            if (resolvedAsset.catalog.id == RuntimeAssetId.ON_DEVICE_TTS && ttsAssetState != null) {
                item {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MonochromeUi.cardBackground,
                        tonalElevation = 2.dp,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.asset_actions_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = stringResource(R.string.asset_manual_only_desc),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                            )
                            TtsModelAssetSection(
                                title = "kokoro",
                                description = ttsAssetState.kokoro.details,
                                installed = ttsAssetState.kokoro.installed,
                                enabled = !resolvedAsset.busy && ttsAssetState.framework.installed,
                                onDownload = { assetViewModel.downloadOnDeviceTtsModel("kokoro") },
                                onClear = { assetViewModel.clearOnDeviceTtsModel("kokoro") },
                            )
                            TtsModelAssetSection(
                                title = "matcha",
                                description = ttsAssetState.matcha.details,
                                installed = ttsAssetState.matcha.installed,
                                enabled = !resolvedAsset.busy && ttsAssetState.framework.installed,
                                onDownload = { assetViewModel.downloadOnDeviceTtsModel("matcha") },
                                onClear = { assetViewModel.clearOnDeviceTtsModel("matcha") },
                            )
                            if (!ttsAssetState.framework.installed) {
                                Text(
                                    text = stringResource(R.string.asset_framework_required_hint),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            } else if (resolvedAsset.catalog.id != RuntimeAssetId.TTS_VOICE_ASSETS) {
                item {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MonochromeUi.cardBackground,
                        tonalElevation = 2.dp,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.asset_actions_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = stringResource(R.string.asset_manual_only_desc),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                            )
                            Text(
                                text = stringResource(R.string.asset_auto_detect_desc),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                            )
                            if (!resolvedAsset.catalog.actionsEnabled) {
                                Text(
                                    text = stringResource(R.string.asset_actions_coming_next_step),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Button(
                                    onClick = { assetViewModel.downloadAsset(resolvedAsset.catalog.id.value) },
                                    modifier = Modifier.weight(1f),
                                    enabled = !resolvedAsset.busy && resolvedAsset.catalog.actionsEnabled && !resolvedAsset.installed,
                                    colors = monochromeButtonColors(),
                                ) {
                                    Text(stringResource(R.string.asset_download_action))
                                }
                                OutlinedButton(
                                    onClick = { assetViewModel.clearAsset(resolvedAsset.catalog.id.value) },
                                    modifier = Modifier.weight(1f),
                                    enabled = !resolvedAsset.busy && resolvedAsset.catalog.actionsEnabled && resolvedAsset.installed,
                                    border = BorderStroke(1.dp, monochromeClearButtonBorderColor()),
                                    colors = monochromeClearButtonColors(),
                                ) {
                                    Text(stringResource(R.string.asset_clear_action))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TtsModelAssetSection(
    title: String,
    description: String,
    installed: Boolean,
    enabled: Boolean,
    onDownload: () -> Unit,
    onClear: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(
            text = description,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
        )
        Text(
            text = if (installed) stringResource(R.string.asset_status_ready) else stringResource(R.string.asset_status_not_ready),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onDownload,
                modifier = Modifier.weight(1f),
                enabled = enabled && !installed,
                colors = monochromeButtonColors(),
            ) {
                Text(stringResource(R.string.asset_download_action))
            }
            OutlinedButton(
                onClick = onClear,
                modifier = Modifier.weight(1f),
                enabled = enabled && installed,
                border = BorderStroke(1.dp, monochromeClearButtonBorderColor()),
                colors = monochromeClearButtonColors(),
            ) {
                Text(stringResource(R.string.asset_clear_action))
            }
        }
    }
}

@Composable
internal fun SubPageScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = { SubPageHeader(title = title, onBack = onBack) },
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MonochromeUi.pageBackground,
        content = content,
    )
}

@Composable
internal fun SubPageHeader(
    title: String,
    onBack: () -> Unit,
) {
    Surface(
        color = MonochromeUi.pageBackground,
        shadowElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 12.dp),
        ) {
            Surface(
                onClick = onBack,
                shape = CircleShape,
                color = MonochromeUi.iconButtonSurface,
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                Box(
                    modifier = Modifier
                        .background(MonochromeUi.iconButtonSurface, CircleShape)
                        .border(1.dp, MonochromeUi.border, CircleShape)
                        .padding(9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.common_back),
                        tint = MonochromeUi.textPrimary,
                    )
                }
            }
            Text(
                text = title,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
                color = MonochromeUi.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun EntryListPage(
    entries: List<EntryCardState>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .background(MonochromeUi.pageBackground)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                entries.forEach { entry ->
                    AccountEntryCard(entry)
                }
            }
        }
    }
}

@Composable
private fun AccountEntryCard(entry: EntryCardState) {
    Surface(
        onClick = entry.onClick,
        shape = RoundedCornerShape(20.dp),
        color = MonochromeUi.cardBackground,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .background(MonochromeUi.mutedSurface, RoundedCornerShape(14.dp))
                    .padding(10.dp),
            ) {
                Icon(entry.icon, contentDescription = null, tint = MonochromeUi.textPrimary)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(entry.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    entry.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
            Box(
                modifier = Modifier.width(24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MonochromeUi.textPrimary,
                )
            }
        }
    }
}

@Composable
private fun SettingSelectionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selectedLabel: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MonochromeUi.cardBackground,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .background(MonochromeUi.mutedSurface, RoundedCornerShape(14.dp))
                        .padding(10.dp),
                ) {
                    Icon(icon, contentDescription = null, tint = MonochromeUi.textPrimary)
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            }
            SelectionField(
                title = "",
                options = options,
                selectedId = options.firstOrNull { it.second == selectedLabel }?.first.orEmpty(),
                onSelect = onSelect,
            )
        }
    }
}

@Composable
private fun QuickActionChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = MonochromeUi.mutedSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MonochromeUi.textPrimary)
            Text(label, color = MonochromeUi.textPrimary)
        }
    }
}

@Composable
private fun monochromeButtonColors() = ButtonDefaults.buttonColors(
    containerColor = MonochromeUi.strong,
    contentColor = MonochromeUi.strongText,
    disabledContainerColor = MonochromeUi.border,
    disabledContentColor = MonochromeUi.textSecondary,
)

@Composable
private fun monochromeClearButtonColors() = ButtonDefaults.outlinedButtonColors(
    contentColor = if (MonochromeUi.isDarkTheme) Color(0xFFE8EDF7) else MonochromeUi.textPrimary,
    disabledContentColor = if (MonochromeUi.isDarkTheme) Color(0xFF5F6876) else MonochromeUi.textSecondary,
)

@Composable
private fun monochromeClearButtonBorderColor(): Color {
    return if (MonochromeUi.isDarkTheme) Color(0xFF546074) else MonochromeUi.border
}

@Composable
private fun CaptchaCard(
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
            Text(stringResource(R.string.qq_captcha_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.qq_captcha_subtitle))
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
                label = { Text(stringResource(R.string.qq_captcha_sid_optional)) },
                colors = monochromeOutlinedTextFieldColors(),
            )
            Button(
                onClick = { onSubmit(ticketInput, randstrInput, sidInput) },
                enabled = ticketInput.isNotBlank() && randstrInput.isNotBlank(),
                colors = monochromeButtonColors(),
            ) {
                Text(stringResource(R.string.qq_captcha_submit))
            }
        }
    }
}

private data class EntryCardState(
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val onClick: () -> Unit,
)

@Composable
private fun RuntimeAssetEntryCard(
    item: RuntimeAssetEntryState,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MonochromeUi.cardBackground,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .background(MonochromeUi.mutedSurface, RoundedCornerShape(14.dp))
                    .padding(10.dp),
            ) {
                Icon(Icons.Outlined.CloudDownload, contentDescription = null, tint = MonochromeUi.textPrimary)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    stringResource(item.catalog.titleRes),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(item.catalog.subtitleRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                Text(
                    assetStatusLabel(item),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
            }
            Box(
                modifier = Modifier.width(24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MonochromeUi.textPrimary,
                )
            }
        }
    }
}

@Composable
private fun assetStatusLabel(item: RuntimeAssetEntryState): String {
    return if (item.installed) {
        stringResource(R.string.asset_status_ready)
    } else {
        stringResource(R.string.asset_status_not_ready)
    }
}

private fun buildAccountQrBitmap(content: String, sizePx: Int): Bitmap {
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

private fun currentApplicationLanguage(): AppLanguage {
    val currentTag = AppCompatDelegate.getApplicationLocales()[0]?.toLanguageTag().orEmpty()
    return when {
        currentTag.startsWith("en", ignoreCase = true) -> AppLanguage.ENGLISH
        currentTag.startsWith("zh", ignoreCase = true) -> AppLanguage.CHINESE
        else -> AppLanguage.CHINESE
    }
}
