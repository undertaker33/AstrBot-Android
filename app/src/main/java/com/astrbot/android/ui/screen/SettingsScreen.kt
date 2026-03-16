package com.astrbot.android.ui.screen

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astrbot.android.model.NapCatBridgeConfig
import com.astrbot.android.runtime.ContainerBridgeController
import com.astrbot.android.ui.MonochromeUi
import com.astrbot.android.ui.monochromeOutlinedTextFieldColors
import com.astrbot.android.ui.monochromeSwitchColors
import com.astrbot.android.ui.viewmodel.BridgeViewModel

@Composable
fun SettingsScreen(
    bridgeViewModel: BridgeViewModel = viewModel(),
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

    Scaffold(
        topBar = {
            if (onBack != null) {
                SubPageHeader(title = "运行设置", onBack = onBack)
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
                    Text("运行设置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "这里配置 Android 侧桥接，用来启动 NapCat 并检查本地健康状态。",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                    )
                }
            }
            item {
                RuntimeConfigCard(
                    title = "桥接地址",
                    description = "这些是应用侧桥接地址，不是 NapCat WebUI 自己的网络面板。",
                ) {
                    OutlinedTextField(
                        value = endpoint,
                        onValueChange = { endpoint = it },
                        label = { Text("反向 WS 地址") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = monochromeOutlinedTextFieldColors(),
                    )
                    OutlinedTextField(
                        value = healthUrl,
                        onValueChange = { healthUrl = it },
                        label = { Text("NapCat 健康检查地址") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = monochromeOutlinedTextFieldColors(),
                    )
                    OutlinedTextField(
                        value = commandPreview,
                        onValueChange = { commandPreview = it },
                        label = { Text("命令预览") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = monochromeOutlinedTextFieldColors(),
                    )
                }
            }
            item {
                RuntimeConfigCard(
                    title = "桥接命令",
                    description = "这些 shell 命令由 Android 应用调用，用于启动、停止和检查容器运行状态。",
                ) {
                    OutlinedTextField(
                        value = startCommand,
                        onValueChange = { startCommand = it },
                        label = { Text("启动命令") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                        colors = monochromeOutlinedTextFieldColors(),
                    )
                    OutlinedTextField(
                        value = stopCommand,
                        onValueChange = { stopCommand = it },
                        label = { Text("停止命令") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                        colors = monochromeOutlinedTextFieldColors(),
                    )
                    OutlinedTextField(
                        value = statusCommand,
                        onValueChange = { statusCommand = it },
                        label = { Text("状态命令") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                        colors = monochromeOutlinedTextFieldColors(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("自动启动", fontWeight = FontWeight.SemiBold)
                            Text(
                                "应用请求时自动尝试启动 NapCat。",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                            )
                        }
                        Switch(
                            checked = autoStart,
                            onCheckedChange = { autoStart = it },
                            colors = monochromeSwitchColors(),
                        )
                    }
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
                            Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                        },
                        colors = monochromeButtonColors(),
                    ) {
                        Icon(Icons.Outlined.Save, contentDescription = null)
                        Text("保存运行设置")
                    }
                }
            }
            item {
                RuntimeConfigCard(
                    title = "运行状态",
                    description = "本地 NapCat 桥接的快速控制与状态查看。",
                ) {
                    Text("状态：${runtimeState.status}")
                    Text("最近动作：${runtimeState.lastAction}")
                    Text("详情：${runtimeState.details}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { ContainerBridgeController.start(context) },
                            colors = monochromeButtonColors(),
                        ) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                            Text("启动")
                        }
                        OutlinedButton(
                            onClick = { ContainerBridgeController.stop(context) },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MonochromeUi.textPrimary,
                            ),
                        ) {
                            Icon(Icons.Outlined.Stop, contentDescription = null)
                            Text("停止")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RuntimeConfigCard(
    title: String,
    description: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(26.dp),
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
