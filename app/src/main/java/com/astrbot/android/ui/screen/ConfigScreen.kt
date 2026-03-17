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
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astrbot.android.R
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
    val newConfigLabel = stringResource(R.string.config_new)

    val chatProviderOptions = providers
        .filter { it.enabled && ProviderCapability.CHAT in it.capabilities }
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
                    placeholder = { Text(stringResource(R.string.config_search_placeholder)) },
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
                    defaultModelName = chatProviderOptions.firstOrNull { it.first == profile.defaultChatProviderId }?.second.orEmpty(),
                    onEdit = { editingProfile = profile },
                )
            }
        }

        FloatingActionButton(
            onClick = {
                editingProfile = ConfigProfile(name = newConfigLabel)
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            containerColor = MonochromeUi.fabBackground,
            contentColor = MonochromeUi.fabContent,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.provider_add))
        }
    }

    editingProfile?.let { profile ->
        ConfigProfileEditorDialog(
            initialProfile = profile,
            chatModelOptions = chatProviderOptions,
            visionModelOptions = chatProviderOptions,
            onDismiss = { editingProfile = null },
            onDelete = {
                configViewModel.delete(profile.id)
                Toast.makeText(context, context.getString(R.string.config_deleted), Toast.LENGTH_SHORT).show()
                editingProfile = null
            },
            onSave = { nextProfile ->
                val saved = nextProfile.copy(id = nextProfile.id.ifBlank { profile.id })
                configViewModel.save(saved)
                configViewModel.select(saved.id)
                Toast.makeText(context, context.getString(R.string.common_saved), Toast.LENGTH_SHORT).show()
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
                        add(stringResource(R.string.config_summary_bots, assignedBotCount))
                        add(stringResource(R.string.config_summary_chat_model, defaultModelName.ifBlank { stringResource(R.string.bot_not_set) }))
                        if (profile.sttEnabled) add(stringResource(R.string.config_summary_stt_on))
                        if (profile.ttsEnabled) add(stringResource(R.string.config_summary_tts_on))
                        if (profile.realWorldTimeAwarenessEnabled) add(stringResource(R.string.config_summary_time_on))
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
    val unnamedConfigLabel = stringResource(R.string.config_unnamed)
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
                            name = name.trim().ifBlank { initialProfile.name.ifBlank { unnamedConfigLabel } },
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
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (initialProfile.id.isNotBlank() && initialProfile.id != "default") {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MonochromeUi.textSecondary),
                    ) {
                        Text(stringResource(R.string.common_delete))
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(contentColor = MonochromeUi.textSecondary),
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        },
        title = { Text(stringResource(R.string.config_title)) },
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
                    label = { Text(stringResource(R.string.config_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = monochromeOutlinedTextFieldColors(),
                )
                SelectionField(
                    title = stringResource(R.string.config_default_chat_model),
                    options = chatModelOptions,
                    selectedId = defaultChatProviderId,
                    onSelect = { defaultChatProviderId = it },
                )
                SelectionField(
                    title = stringResource(R.string.config_default_caption_model),
                    options = visionModelOptions,
                    selectedId = defaultVisionProviderId,
                    onSelect = { defaultVisionProviderId = it },
                )
                ConfigSwitch(
                    title = stringResource(R.string.config_enable_stt),
                    subtitle = stringResource(R.string.config_enable_stt_desc),
                    checked = sttEnabled,
                    onCheckedChange = { sttEnabled = it },
                )
                ConfigSwitch(
                    title = stringResource(R.string.config_enable_tts),
                    subtitle = stringResource(R.string.config_enable_tts_desc),
                    checked = ttsEnabled,
                    onCheckedChange = { ttsEnabled = it },
                )
                ConfigSwitch(
                    title = stringResource(R.string.config_time_awareness),
                    subtitle = stringResource(R.string.config_time_awareness_desc),
                    checked = realWorldTimeAwarenessEnabled,
                    onCheckedChange = { realWorldTimeAwarenessEnabled = it },
                )
                OutlinedTextField(
                    value = imageCaptionPrompt,
                    onValueChange = { imageCaptionPrompt = it },
                    label = { Text(stringResource(R.string.config_caption_prompt)) },
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
