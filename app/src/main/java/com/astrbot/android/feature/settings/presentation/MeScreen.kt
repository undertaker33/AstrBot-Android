package com.astrbot.android.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Notifications
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
    onOpenCronJobs: () -> Unit,
    onOpenResourceCenter: () -> Unit = {},
) {
    EntryListPage(
        entries = buildMeEntryKinds().map { entryKind ->
            entryKind.toEntryCardState(
                onOpenQqAccount = onOpenQqAccount,
                onOpenSettings = onOpenSettings,
                onOpenLogs = onOpenLogs,
                onOpenAssets = onOpenAssets,
                onOpenBackup = onOpenBackup,
                onOpenCronJobs = onOpenCronJobs,
                onOpenResourceCenter = onOpenResourceCenter,
            )
        },
    )
}

@Composable
private fun MeEntryKind.toEntryCardState(
    onOpenQqAccount: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenAssets: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenCronJobs: () -> Unit,
    onOpenResourceCenter: () -> Unit,
): EntryCardState {
    return when (this) {
        MeEntryKind.QqAccount -> EntryCardState(
            title = stringResource(R.string.me_card_qq_title),
            subtitle = stringResource(R.string.me_card_qq_subtitle),
            icon = Icons.Outlined.PersonOutline,
            onClick = onOpenQqAccount,
        )
        MeEntryKind.Settings -> EntryCardState(
            title = stringResource(R.string.me_card_settings_title),
            subtitle = stringResource(R.string.me_card_settings_subtitle),
            icon = Icons.Outlined.Settings,
            onClick = onOpenSettings,
        )
        MeEntryKind.CronJobs -> EntryCardState(
            title = stringResource(R.string.cron_jobs_title),
            subtitle = stringResource(R.string.cron_jobs_subtitle),
            icon = Icons.Outlined.Notifications,
            onClick = onOpenCronJobs,
        )
        MeEntryKind.ResourceCenter -> EntryCardState(
            title = stringResource(R.string.me_card_resource_center_title),
            subtitle = stringResource(R.string.me_card_resource_center_subtitle),
            icon = Icons.Outlined.Memory,
            onClick = onOpenResourceCenter,
        )
        MeEntryKind.Logs -> EntryCardState(
            title = stringResource(R.string.me_card_logs_title),
            subtitle = stringResource(R.string.me_card_logs_subtitle),
            icon = Icons.Outlined.Refresh,
            onClick = onOpenLogs,
        )
        MeEntryKind.Assets -> EntryCardState(
            title = stringResource(R.string.me_card_assets_title),
            subtitle = stringResource(R.string.me_card_assets_subtitle),
            icon = Icons.Outlined.CloudDownload,
            onClick = onOpenAssets,
        )
        MeEntryKind.Backup -> EntryCardState(
            title = stringResource(R.string.me_card_backup_title),
            subtitle = stringResource(R.string.me_card_backup_subtitle),
            icon = Icons.Outlined.CloudDownload,
            onClick = onOpenBackup,
        )
    }
}
