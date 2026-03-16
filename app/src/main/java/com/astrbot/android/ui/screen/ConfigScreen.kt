package com.astrbot.android.ui.screen

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.ui.MonochromeUi
import com.astrbot.android.ui.monochromeOutlinedTextFieldColors
import com.astrbot.android.ui.monochromeSwitchColors
import com.astrbot.android.ui.viewmodel.ConfigViewModel

@Composable
fun ConfigScreen(
    configViewModel: ConfigViewModel = viewModel(),
) {
    val configProfiles by configViewModel.configProfiles.collectAsState()
    val selectedConfigId by configViewModel.selectedConfigProfileId.collectAsState()
    val providers by configViewModel.providers.collectAsState()
    val bots by configViewModel.bots.collectAsState()
    val context = LocalContext.current

    var searchQuery by remember { mutableStateOf("") }
    var editingProfile by remember { mutableStateOf<ConfigProfile?>(null) }

    val modelOptions = providers
        .filter { it.enabled && ProviderCapability.CHAT in it.capabilities }
        .map { it.id to it.name }
    val allProviderOptions = providers
        .filter { it.enabled }
        .map { it.id to it.name }

    val filteredProfiles = configProfiles.filter { profile ->
        searchQuery.isBlank() ||
            profile.name.contains(searchQuery, ignoreCase = true) ||
            profile.imageCaptionPrompt.contains(searchQuery, ignoreCase = true)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MonochromeUi.pageBackground),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            contentPadding = PaddingValues(top = 14.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    placeholder = { Text("搜索配置") },
                    shape = RoundedCornerShape(28.dp),
                    colors = monochromeOutlinedTextFieldColors(),
                    singleLine = true,
                )
            }
            items(filteredProfiles, key = { it.id }) { profile ->
                ConfigProfileCard(
                    profile = profile,
                    selected = profile.id == selectedConfigId,
                    assignedBotCount = bots.count { it.configProfileId == profile.id },
                    defaultModelName = modelOptions.firstOrNull { it.first == profile.defaultChatProviderId }?.second.orEmpty(),
                    onSelect = { configViewModel.select(profile.id) },
                    onEdit = { editingProfile = profile },
                )
            }
        }

        FloatingActionButton(
            onClick = {
                editingProfile = ConfigProfile(
                    name = "新配置",
                )
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            containerColor = MonochromeUi.fabBackground,
            contentColor = MonochromeUi.fabContent,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = "新增配置")
        }
    }

    editingProfile?.let { profile ->
        ConfigProfileEditorDialog(
            initialProfile = profile,
            chatModelOptions = modelOptions,
            visionModelOptions = allProviderOptions,
            onDismiss = { editingProfile = null },
            onDelete = {
                configViewModel.delete(profile.id)
                Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                editingProfile = null
            },
            onSave = { nextProfile ->
                val saved = nextProfile.copy(
                    id = nextProfile.id.ifBlank { profile.id },
                )
                configViewModel.save(saved)
                configViewModel.select(saved.id)
                Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                editingProfile = null
            },
        )
    }
}

@Composable
private fun ConfigProfileCard(
    profile: ConfigProfile,
    selected: Boolean,
    assignedBotCount: Int,
    defaultModelName: String,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
) {
    Surface(
        onClick = onEdit,
        shape = RoundedCornerShape(26.dp),
        color = if (selected) MonochromeUi.cardAltBackground else MonochromeUi.cardBackground,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .background(MonochromeUi.mutedSurface, CircleShape)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(profile.name.take(1).uppercase(), color = MonochromeUi.textPrimary, fontWeight = FontWeight.Bold)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(profile.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    text = buildList {
                        add("机器人 $assignedBotCount")
                        add("对话模型 ${defaultModelName.ifBlank { "未设置" }}")
                        if (profile.sttEnabled) add("语音转文字 开")
                        if (profile.ttsEnabled) add("文字转语音 开")
                        if (profile.realWorldTimeAwarenessEnabled) add("现实时间感知 开")
                    }.joinToString(" | "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = profile.imageCaptionPrompt,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            AssistChip(
                onClick = onSelect,
                label = { Text(if (selected) "已选择" else "选择") },
                leadingIcon = if (selected) {
                    { Icon(Icons.Outlined.Check, contentDescription = null) }
                } else {
                    null
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (selected) MonochromeUi.chipSelectedBackground else MonochromeUi.chipBackground,
                    labelColor = if (selected) MonochromeUi.textPrimary else MonochromeUi.textSecondary,
                    leadingIconContentColor = MonochromeUi.textPrimary,
                ),
            )
        }
    }
}

@Composable
private fun ConfigProfileEditorDialog(
    initialProfile: ConfigProfile,
    chatModelOptions: List<Pair<String, String>>,
    visionModelOptions: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: (ConfigProfile) -> Unit,
) {
    var name by remember(initialProfile.id) { mutableStateOf(initialProfile.name) }
    var defaultChatProviderId by remember(initialProfile.id) { mutableStateOf(initialProfile.defaultChatProviderId) }
    var defaultVisionProviderId by remember(initialProfile.id) { mutableStateOf(initialProfile.defaultVisionProviderId) }
    var sttEnabled by remember(initialProfile.id) { mutableStateOf(initialProfile.sttEnabled) }
    var ttsEnabled by remember(initialProfile.id) { mutableStateOf(initialProfile.ttsEnabled) }
    var realWorldTimeAwarenessEnabled by remember(initialProfile.id) { mutableStateOf(initialProfile.realWorldTimeAwarenessEnabled) }
    var imageCaptionPrompt by remember(initialProfile.id) { mutableStateOf(initialProfile.imageCaptionPrompt) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MonochromeUi.cardBackground,
        titleContentColor = MonochromeUi.textPrimary,
        textContentColor = MonochromeUi.textPrimary,
        confirmButton = {
            TextButton(
                colors = ButtonDefaults.textButtonColors(contentColor = MonochromeUi.textPrimary),
                onClick = {
                    onSave(
                        initialProfile.copy(
                            name = name.trim().ifBlank { initialProfile.name.ifBlank { "Unnamed Config" } },
                            defaultChatProviderId = defaultChatProviderId,
                            defaultVisionProviderId = defaultVisionProviderId,
                            sttEnabled = sttEnabled,
                            ttsEnabled = ttsEnabled,
                            realWorldTimeAwarenessEnabled = realWorldTimeAwarenessEnabled,
                            imageCaptionPrompt = imageCaptionPrompt.trim(),
                        ),
                    )
                },
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (initialProfile.id.isNotBlank() && initialProfile.id != "default") {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MonochromeUi.textSecondary),
                    ) {
                        Text("删除")
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(contentColor = MonochromeUi.textSecondary),
                ) {
                    Text("取消")
                }
            }
        },
        title = { Text("配置文件") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("配置名称") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = monochromeOutlinedTextFieldColors(),
                )
                SelectionField(
                    title = "默认对话模型",
                    options = chatModelOptions,
                    selectedId = defaultChatProviderId,
                    onSelect = { defaultChatProviderId = it },
                )
                SelectionField(
                    title = "默认图片转述模型",
                    options = visionModelOptions,
                    selectedId = defaultVisionProviderId,
                    onSelect = { defaultVisionProviderId = it },
                )
                ConfigSwitch(
                    title = "启用语音转文本",
                    subtitle = "占位开关，后续接入 STT 模型配置后生效。",
                    checked = sttEnabled,
                    onCheckedChange = { sttEnabled = it },
                )
                ConfigSwitch(
                    title = "启用文本转语音",
                    subtitle = "占位开关，后续接入 TTS 模型配置后生效。",
                    checked = ttsEnabled,
                    onCheckedChange = { ttsEnabled = it },
                )
                ConfigSwitch(
                    title = "现实世界时间感知",
                    subtitle = "开启后会将当前本地时间附加到 system prompt。",
                    checked = realWorldTimeAwarenessEnabled,
                    onCheckedChange = { realWorldTimeAwarenessEnabled = it },
                )
                OutlinedTextField(
                    value = imageCaptionPrompt,
                    onValueChange = { imageCaptionPrompt = it },
                    label = { Text("图片转述提示词") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 8,
                    colors = monochromeOutlinedTextFieldColors(),
                )
            }
        },
    )
}

@Composable
private fun ConfigSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = monochromeSwitchColors(),
        )
    }
}
