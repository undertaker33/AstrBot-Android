package com.astrbot.android.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.Check
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astrbot.android.R
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.ui.FloatingBottomNavFabBottomPadding
import com.astrbot.android.ui.MonochromeUi
import com.astrbot.android.ui.monochromeOutlinedTextFieldColors
import com.astrbot.android.ui.viewmodel.ConfigViewModel
import kotlinx.coroutines.launch

@Composable
fun ConfigScreen(
    selectedConfigIds: Set<String>,
    onSelectedConfigIdsChange: (Set<String>) -> Unit,
    onOpenProfile: (String) -> Unit,
    configViewModel: ConfigViewModel = viewModel(),
) {
    val configProfiles by configViewModel.configProfiles.collectAsState()
    val selectedConfigId by configViewModel.selectedConfigProfileId.collectAsState()
    val providers by configViewModel.providers.collectAsState()
    val bots by configViewModel.bots.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val selectionMode = selectedConfigIds.isNotEmpty()

    BackHandler(enabled = selectionMode) {
        onSelectedConfigIdsChange(emptySet())
    }

    val chatProviderOptions = providers
        .filter { it.enabled && ProviderCapability.CHAT in it.capabilities }
        .associateBy({ it.id }, { it.name })

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
                    selected = if (selectionMode) {
                        profile.id in selectedConfigIds
                    } else {
                        profile.id == selectedConfigId
                    },
                    selectionMode = selectionMode,
                    assignedBotCount = bots.count { it.configProfileId == profile.id },
                    defaultModelName = chatProviderOptions[profile.defaultChatProviderId].orEmpty(),
                    onOpen = {
                        if (selectionMode) {
                            onSelectedConfigIdsChange(selectedConfigIds.toggle(profile.id))
                        } else {
                            configViewModel.select(profile.id)
                            onOpenProfile(profile.id)
                        }
                    },
                    onLongPress = {
                        onSelectedConfigIdsChange(selectedConfigIds.toggle(profile.id))
                    },
                )
            }
        }

        if (selectionMode) {
            FloatingActionButton(
                onClick = {
                    showDeleteConfirm = true
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, bottom = FloatingBottomNavFabBottomPadding),
                containerColor = MonochromeUi.fabBackground,
                contentColor = MonochromeUi.fabContent,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.config_delete_selected))
            }
        } else {
            FloatingActionButton(
                onClick = {
                    val created = configViewModel.create()
                    onOpenProfile(created.id)
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, bottom = FloatingBottomNavFabBottomPadding),
                containerColor = MonochromeUi.fabBackground,
                contentColor = MonochromeUi.fabContent,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.provider_add))
            }
        }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                containerColor = MonochromeUi.cardBackground,
                titleContentColor = MonochromeUi.textPrimary,
                textContentColor = MonochromeUi.textSecondary,
                title = { Text(stringResource(R.string.config_delete_confirm_title)) },
                text = { Text(stringResource(R.string.config_delete_confirm_message, selectedConfigIds.size)) },
                confirmButton = {
                    TextButton(
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFB42318)),
                        onClick = {
                            scope.launch {
                                selectedConfigIds.forEach { configViewModel.delete(it) }
                                onSelectedConfigIdsChange(emptySet())
                                showDeleteConfirm = false
                            }
                        },
                    ) {
                        Text(stringResource(R.string.common_delete))
                    }
                },
                dismissButton = {
                    TextButton(
                        colors = ButtonDefaults.textButtonColors(contentColor = MonochromeUi.textSecondary),
                        onClick = { showDeleteConfirm = false },
                    ) {
                        Text(stringResource(R.string.common_cancel))
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConfigProfileCard(
    profile: ConfigProfile,
    selected: Boolean,
    selectionMode: Boolean,
    assignedBotCount: Int,
    defaultModelName: String,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = when {
            selectionMode && selected -> MonochromeUi.cardAltBackground
            selected -> MonochromeUi.cardAltBackground
            else -> MonochromeUi.cardBackground
        },
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onOpen,
                    onLongClick = onLongPress,
                )
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .background(
                        if (selectionMode && selected) MonochromeUi.fabBackground.copy(alpha = 0.14f) else MonochromeUi.mutedSurface,
                        CircleShape,
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(profile.name.take(1).uppercase(), color = MonochromeUi.textPrimary, fontWeight = FontWeight.Bold)
            }
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(profile.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    text = buildList {
                        add(stringResource(R.string.config_summary_bots, assignedBotCount))
                        add(stringResource(R.string.config_summary_chat_model, defaultModelName.ifBlank { stringResource(R.string.bot_not_set) }))
                    }.joinToString(" | "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val stateSummary = buildList {
                    if (profile.sttEnabled) add(stringResource(R.string.config_summary_stt_on))
                    if (profile.ttsEnabled) add(stringResource(R.string.config_summary_tts_on))
                    if (profile.realWorldTimeAwarenessEnabled) add(stringResource(R.string.config_summary_time_on))
                    if (profile.webSearchEnabled) add(stringResource(R.string.config_summary_search_on))
                }.joinToString(" | ")
                if (stateSummary.isNotBlank()) {
                    Text(
                        text = stateSummary,
                        style = MaterialTheme.typography.labelMedium,
                        color = MonochromeUi.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = profile.imageCaptionPrompt,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (selectionMode) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (selected) MonochromeUi.fabBackground else Color.Transparent,
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.5.dp,
                            color = if (selected) MonochromeUi.fabBackground else MonochromeUi.textSecondary.copy(alpha = 0.35f),
                        ),
                    ) {
                        Box(
                            modifier = Modifier.size(28.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) {
                                Icon(
                                    Icons.Outlined.Check,
                                    contentDescription = null,
                                    tint = MonochromeUi.fabContent,
                                )
                            }
                        }
                    }
                    Text(
                        text = if (selected) stringResource(R.string.common_selected) else stringResource(R.string.common_select),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) MonochromeUi.textPrimary else MonochromeUi.textSecondary,
                    )
                }
            }
        }
    }
}

private fun Set<String>.toggle(id: String): Set<String> {
    return if (contains(id)) this - id else this + id
}
