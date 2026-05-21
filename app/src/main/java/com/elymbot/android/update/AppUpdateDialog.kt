package com.elymbot.android.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.elymbot.android.R

@Composable
internal fun AppUpdateDialogHost(
    uiState: AppUpdateUiState,
    onUpdateNow: (AppUpdateCandidate) -> Unit,
    onSnooze: (AppUpdateCandidate) -> Unit,
    onIgnore: (AppUpdateCandidate) -> Unit,
    onInstall: (AppUpdateDownloadedPackage) -> Unit,
    onRetry: (AppUpdateCandidate) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (val dialog = uiState.dialog) {
        is AppUpdateDialogState.Available -> AppUpdateAvailableDialog(
            candidate = dialog.candidate,
            onUpdateNow = onUpdateNow,
            onSnooze = onSnooze,
            onIgnore = onIgnore,
            modifier = modifier,
        )

        is AppUpdateDialogState.Downloading -> AppUpdateDownloadingDialog(
            candidate = dialog.candidate,
            progress = dialog.progress,
            modifier = modifier,
        )

        is AppUpdateDialogState.ReadyToInstall -> AppUpdateReadyToInstallDialog(
            packageFile = dialog.packageFile,
            onInstall = onInstall,
            modifier = modifier,
        )

        is AppUpdateDialogState.Failed -> AppUpdateFailedDialog(
            failed = dialog,
            onRetry = onRetry,
            onSnooze = onSnooze,
            onIgnore = onIgnore,
            modifier = modifier,
        )

        null -> Unit
    }
}

@Composable
private fun AppUpdateAvailableDialog(
    candidate: AppUpdateCandidate,
    onUpdateNow: (AppUpdateCandidate) -> Unit,
    onSnooze: (AppUpdateCandidate) -> Unit,
    onIgnore: (AppUpdateCandidate) -> Unit,
    modifier: Modifier,
) {
    AlertDialog(
        onDismissRequest = {},
        modifier = modifier.testTag("app-update-available-dialog"),
        title = { Text(text = stringResource(R.string.app_update_available_title)) },
        text = {
            Text(
                text = stringResource(
                    R.string.app_update_available_message,
                    candidate.currentVersionName,
                    candidate.currentVersionCode,
                    candidate.displayVersion,
                    candidate.asset.name,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { onUpdateNow(candidate) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                    Text(text = stringResource(R.string.app_update_action_update))
                }
                OutlinedButton(
                    onClick = { onSnooze(candidate) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Schedule, contentDescription = null)
                    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                    Text(text = stringResource(R.string.app_update_action_later))
                }
                TextButton(
                    onClick = { onIgnore(candidate) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Block, contentDescription = null)
                    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                    Text(text = stringResource(R.string.app_update_action_ignore))
                }
            }
        },
    )
}

@Composable
private fun AppUpdateDownloadingDialog(
    candidate: AppUpdateCandidate,
    progress: AppUpdateDownloadProgress,
    modifier: Modifier,
) {
    AlertDialog(
        onDismissRequest = {},
        modifier = modifier.testTag("app-update-downloading-dialog"),
        title = { Text(text = stringResource(R.string.app_update_downloading_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.app_update_downloading_message, candidate.asset.name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (progress.isIndeterminate) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(
                        progress = { progress.progressFraction ?: 0f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.app_update_progress_speed_label, progress.speedLabel),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(
                            R.string.app_update_progress_size_label,
                            progress.downloadedLabel,
                            progress.totalLabel,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun AppUpdateReadyToInstallDialog(
    packageFile: AppUpdateDownloadedPackage,
    onInstall: (AppUpdateDownloadedPackage) -> Unit,
    modifier: Modifier,
) {
    AlertDialog(
        onDismissRequest = {},
        modifier = modifier.testTag("app-update-ready-dialog"),
        title = { Text(text = stringResource(R.string.app_update_ready_title)) },
        text = {
            Text(
                text = stringResource(
                    R.string.app_update_ready_message,
                    packageFile.candidate.displayVersion,
                    packageFile.file.name,
                ),
            )
        },
        confirmButton = {
            Button(
                onClick = { onInstall(packageFile) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.InstallMobile, contentDescription = null)
                Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                Text(text = stringResource(R.string.app_update_action_install))
            }
        },
    )
}

@Composable
private fun AppUpdateFailedDialog(
    failed: AppUpdateDialogState.Failed,
    onRetry: (AppUpdateCandidate) -> Unit,
    onSnooze: (AppUpdateCandidate) -> Unit,
    onIgnore: (AppUpdateCandidate) -> Unit,
    modifier: Modifier,
) {
    AlertDialog(
        onDismissRequest = {},
        modifier = modifier.testTag("app-update-failed-dialog"),
        title = { Text(text = stringResource(R.string.app_update_failed_title)) },
        text = {
            Text(
                text = stringResource(R.string.app_update_failed_message, failed.message),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { onRetry(failed.candidate) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                    Text(text = stringResource(R.string.app_update_action_retry))
                }
                OutlinedButton(
                    onClick = { onSnooze(failed.candidate) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Schedule, contentDescription = null)
                    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                    Text(text = stringResource(R.string.app_update_action_later))
                }
                TextButton(
                    onClick = { onIgnore(failed.candidate) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Block, contentDescription = null)
                    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                    Text(text = stringResource(R.string.app_update_action_ignore))
                }
            }
        },
    )
}
