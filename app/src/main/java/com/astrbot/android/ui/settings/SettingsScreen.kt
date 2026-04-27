
package com.astrbot.android.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.astrbot.android.R
import com.astrbot.android.model.NapCatBridgeConfig
import com.astrbot.android.core.runtime.container.ContainerBridgeController
import com.astrbot.android.ui.app.MonochromeUi
import com.astrbot.android.ui.app.RegisterSecondaryTopBar
import com.astrbot.android.ui.app.SecondaryTopBarPlaceholder
import com.astrbot.android.ui.app.SecondaryTopBarSpec
import com.astrbot.android.ui.app.monochromeOutlinedTextFieldColors
import com.astrbot.android.ui.app.monochromeSwitchColors
import com.astrbot.android.ui.viewmodel.BridgeViewModel

@Composable
fun SettingsScreen(
    bridgeViewModel: BridgeViewModel = hiltViewModel(),
    onBack: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val bridgeConfig by bridgeViewModel.config.collectAsState()
    val runtimeState by bridgeViewModel.runtimeState.collectAsState()

    var endpoint by remember(bridgeConfig.endpoint) { mutableStateOf(bridgeConfig.endpoint) }
    var healthUrl by remember(bridgeConfig.healthUrl) { mutableStateOf(bridgeConfig.healthUrl) }
    var startCommand by remember(bridgeConfig.startCommand) { mutableStateOf(bridgeConfig.startCommand) }
    var stopCommand by remember(bridgeConfig.stopCommand) { mutableStateOf(bridgeConfig.stopCommand) }
    var statusCommand by remember(bridgeConfig.statusCommand) { mutableStateOf(bridgeConfig.statusCommand) }
    var commandPreview by remember(bridgeConfig.commandPreview) { mutableStateOf(bridgeConfig.commandPreview) }
    var autoStart by remember(bridgeConfig.autoStart) { mutableStateOf(bridgeConfig.autoStart) }
    if (onBack != null) {
        RegisterSecondaryTopBar(
            SecondaryTopBarSpec.SubPage(
                title = context.getString(R.string.settings_runtime_title),
                onBack = onBack,
            ),
        )
    }

    Scaffold(
        topBar = {
            if (onBack != null) {
                SecondaryTopBarPlaceholder()
            }
        },
        containerColor = MonochromeUi.pageBackground,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MonochromeUi.pageBackground)
                .padding(innerPadding)
                .padding(horizontal = 14.dp),
            contentPadding = PaddingValues(top = 14.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(stringResource(R.string.settings_runtime_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.settings_runtime_subtitle),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                    )
                }
            }
            item {
                RuntimeConfigCard(
                    title = stringResource(R.string.settings_runtime_card_endpoints_title),
                    description = stringResource(R.string.settings_runtime_card_endpoints_desc),
                ) {
                    OutlinedTextField(
                        value = endpoint,
                        onValueChange = { endpoint = it },
                        label = { Text(stringResource(R.string.settings_runtime_endpoint_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = monochromeOutlinedTextFieldColors(),
                    )
                    OutlinedTextField(
                        value = healthUrl,
                        onValueChange = { healthUrl = it },
                        label = { Text(stringResource(R.string.settings_runtime_health_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = monochromeOutlinedTextFieldColors(),
                    )
                    OutlinedTextField(
                        value = commandPreview,
                        onValueChange = { commandPreview = it },
                        label = { Text(stringResource(R.string.settings_runtime_preview_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = monochromeOutlinedTextFieldColors(),
                    )
                }
            }
            item {
                RuntimeConfigCard(
                    title = stringResource(R.string.settings_runtime_card_commands_title),
                    description = stringResource(R.string.settings_runtime_card_commands_desc),
                ) {
                    OutlinedTextField(
                        value = startCommand,
                        onValueChange = { startCommand = it },
                        label = { Text(stringResource(R.string.settings_runtime_start_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                        colors = monochromeOutlinedTextFieldColors(),
                    )
                    OutlinedTextField(
                        value = stopCommand,
                        onValueChange = { stopCommand = it },
                        label = { Text(stringResource(R.string.settings_runtime_stop_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                        colors = monochromeOutlinedTextFieldColors(),
                    )
                    OutlinedTextField(
                        value = statusCommand,
                        onValueChange = { statusCommand = it },
                        label = { Text(stringResource(R.string.settings_runtime_status_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                        colors = monochromeOutlinedTextFieldColors(),
                    )
                    RuntimeToggleRow(
                        title = stringResource(R.string.settings_runtime_auto_start_title),
                        subtitle = stringResource(R.string.settings_runtime_auto_start_desc),
                        checked = autoStart,
                        onCheckedChange = { autoStart = it },
                    )
                    Button(
                        onClick = {
                            bridgeViewModel.saveConfig(
                                NapCatBridgeConfig(
                                    endpoint = endpoint,
                                    healthUrl = healthUrl,
                                    startCommand = startCommand,
                                    stopCommand = stopCommand,
                                    statusCommand = statusCommand,
                                    commandPreview = commandPreview,
                                    autoStart = autoStart,
                                ),
                            )
                            Toast.makeText(context, context.getString(R.string.common_saved), Toast.LENGTH_SHORT).show()
                        },
                        colors = monochromeButtonColors(),
                    ) {
                        Icon(Icons.Outlined.Save, contentDescription = null)
                        Text(stringResource(R.string.settings_runtime_save_action))
                    }
                }
            }
            item {
                RuntimeConfigCard(
                    title = stringResource(R.string.settings_runtime_status_title),
                    description = stringResource(R.string.settings_runtime_status_desc),
                ) {
                    Text(stringResource(R.string.settings_runtime_status_value, runtimeState.status))
                    Text(stringResource(R.string.settings_runtime_last_action_value, runtimeState.lastAction))
                    Text(stringResource(R.string.settings_runtime_details_value, runtimeState.details))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { ContainerBridgeController.start(context) },
                            colors = monochromeButtonColors(),
                        ) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                            Text(stringResource(R.string.runtime_start))
                        }
                        OutlinedButton(
                            onClick = { ContainerBridgeController.stop(context) },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MonochromeUi.textPrimary),
                        ) {
                            Icon(Icons.Outlined.Stop, contentDescription = null)
                            Text(stringResource(R.string.runtime_stop))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RuntimeToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, fontWeight = FontWeight.SemiBold, color = MonochromeUi.textPrimary)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MonochromeUi.textSecondary,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = monochromeSwitchColors(),
        )
    }
}

@Composable
private fun RuntimeConfigCard(
    title: String,
    description: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MonochromeUi.cardBackground,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(description, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f))
                content()
            },
        )
    }
}

@Composable
private fun monochromeButtonColors() = ButtonDefaults.buttonColors(
    containerColor = MonochromeUi.strong,
    contentColor = MonochromeUi.strongText,
)
