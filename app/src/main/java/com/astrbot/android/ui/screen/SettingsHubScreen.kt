package com.astrbot.android.ui.screen

import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.astrbot.android.R
import com.astrbot.android.data.AppLanguage
import com.astrbot.android.data.AppPreferencesRepository
import com.astrbot.android.data.RuntimeCacheRepository
import com.astrbot.android.data.ThemeMode
import com.astrbot.android.ui.MonochromeUi
import com.astrbot.android.ui.runWithAppUiTransition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsHubScreen(
    onBack: () -> Unit,
    onOpenRuntime: () -> Unit,
) {
    val context = LocalContext.current
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
    val cacheCleanupState by RuntimeCacheRepository.state.collectAsState()
    var showCacheCleanupConfirm by remember { mutableStateOf(false) }

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
                            runWithAppUiTransition(MonochromeUi.pageBackground.toArgb()) {
                                val locales = LocaleListCompat.forLanguageTags(value)
                                if (AppCompatDelegate.getApplicationLocales() != locales) {
                                    AppCompatDelegate.setApplicationLocales(locales)
                                }
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
                            runWithAppUiTransition(MonochromeUi.pageBackground.toArgb()) {
                                repository.setThemeMode(ThemeMode.fromValue(value))
                            }
                        }
                    },
                )
            }
            item {
                AccountEntryCard(
                    EntryCardState(
                        title = stringResource(R.string.settings_runtime_entry_title),
                        subtitle = stringResource(R.string.settings_runtime_entry_subtitle),
                        icon = Icons.Outlined.Settings,
                        onClick = onOpenRuntime,
                    ),
                )
            }
            item {
                RuntimeCacheCleanupCard(
                    state = cacheCleanupState,
                    onClearClick = { showCacheCleanupConfirm = true },
                )
            }
        }
    }

    if (showCacheCleanupConfirm) {
        AlertDialog(
            onDismissRequest = { showCacheCleanupConfirm = false },
            containerColor = MonochromeUi.cardBackground,
            titleContentColor = MonochromeUi.textPrimary,
            textContentColor = MonochromeUi.textSecondary,
            title = { Text(stringResource(R.string.me_cache_cleanup_confirm_title)) },
            text = { Text(stringResource(R.string.me_cache_cleanup_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCacheCleanupConfirm = false
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    RuntimeCacheRepository.clearSafeRuntimeCaches(context)
                                }
                            }.onSuccess { summary ->
                                Toast.makeText(context, summary, Toast.LENGTH_LONG).show()
                            }.onFailure { error ->
                                Toast.makeText(
                                    context,
                                    error.message ?: error.javaClass.simpleName,
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MonochromeUi.textPrimary),
                ) {
                    Text(stringResource(R.string.me_cache_cleanup_action))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCacheCleanupConfirm = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MonochromeUi.textSecondary),
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun SettingSelectionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selectedLabel: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
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
                        .background(MonochromeUi.mutedSurface, androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
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

private fun currentApplicationLanguage(): AppLanguage {
    val currentTag = AppCompatDelegate.getApplicationLocales()[0]?.toLanguageTag().orEmpty()
    return when {
        currentTag.startsWith("en", ignoreCase = true) -> AppLanguage.ENGLISH
        currentTag.startsWith("zh", ignoreCase = true) -> AppLanguage.CHINESE
        else -> AppLanguage.CHINESE
    }
}
