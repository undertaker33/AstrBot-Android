package com.astrbot.android.ui.screen

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.astrbot.android.R

@Composable
fun MeScreen(
    onOpenQqAccount: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenAssets: () -> Unit,
    onOpenBackup: () -> Unit,
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
            EntryCardState(
                title = stringResource(R.string.me_card_backup_title),
                subtitle = stringResource(R.string.me_card_backup_subtitle),
                icon = Icons.Outlined.CloudDownload,
                onClick = onOpenBackup,
            ),
        ),
    )
}
