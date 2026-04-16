package com.astrbot.android.ui.persona

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.astrbot.android.di.astrBotViewModel
import com.astrbot.android.R
import com.astrbot.android.model.PersonaProfile
import com.astrbot.android.ui.bot.ScrollableAssistChipRow
import com.astrbot.android.ui.app.FloatingBottomNavFabBottomPadding
import com.astrbot.android.ui.app.MonochromeUi
import com.astrbot.android.ui.app.monochromeOutlinedTextFieldColors
import com.astrbot.android.ui.app.monochromeSwitchColors
import com.astrbot.android.ui.common.showProfileDeletionFailureToast
import com.astrbot.android.ui.viewmodel.PersonaViewModel
import java.util.UUID

@Composable
fun PersonaScreen(
    personaViewModel: PersonaViewModel = astrBotViewModel(),
) {
    PersonaCatalogContent(personaViewModel = personaViewModel)
}

@Composable
internal fun PersonaCatalogContent(
    personaViewModel: PersonaViewModel = astrBotViewModel(),
) {
    val personas by personaViewModel.personas.collectAsState()
    val context = LocalContext.current

    var searchQuery by remember { mutableStateOf("") }
    val allTagLabel = stringResource(R.string.bot_tag_all)
    val newPersonaLabel = stringResource(R.string.persona_new)
    var selectedTag by remember { mutableStateOf(allTagLabel) }
    var editingPersona by remember { mutableStateOf<PersonaProfile?>(null) }

    val tags = listOf(allTagLabel) + personas.mapNotNull { it.tag.takeIf(String::isNotBlank) }.distinct().sorted()
    val filteredPersonas = personas.filter { persona ->
        val matchesSearch = searchQuery.isBlank() ||
            persona.name.contains(searchQuery, ignoreCase = true) ||
            persona.systemPrompt.contains(searchQuery, ignoreCase = true) ||
            persona.tag.contains(searchQuery, ignoreCase = true)
        val matchesTag = selectedTag == allTagLabel || persona.tag == selectedTag
        matchesSearch && matchesTag
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
                    placeholder = { Text(stringResource(R.string.persona_search_placeholder)) },
                    shape = RoundedCornerShape(18.dp),
                    colors = monochromeOutlinedTextFieldColors(),
                    singleLine = true,
                )
            }
            item {
                ScrollableAssistChipRow(
                    items = tags,
                    selectedItem = selectedTag,
                    onSelect = { selectedTag = it },
                )
            }
            items(filteredPersonas, key = { it.id }) { persona ->
                PersonaCard(
                    persona = persona,
                    onClick = { editingPersona = persona },
                    onToggleEnabled = { personaViewModel.toggleEnabled(persona.id) },
                )
            }
        }

        FloatingActionButton(
            onClick = {
                editingPersona = PersonaProfile(
                    id = UUID.randomUUID().toString(),
                    name = newPersonaLabel,
                    tag = "",
                    systemPrompt = "",
                    enabledTools = emptySet(),
                )
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

    editingPersona?.let { profile ->
        PersonaEditorDialog(
            initialPersona = profile,
            onDismiss = { editingPersona = null },
            onDelete = {
                val result = personaViewModel.delete(profile.id)
                result.onFailure { error ->
                    showProfileDeletionFailureToast(context, error)
                }
                if (result.isSuccess) {
                    editingPersona = null
                }
            },
            onSave = { persona ->
                if (personas.any { it.id == persona.id }) {
                    personaViewModel.update(persona)
                } else {
                    personaViewModel.add(
                        name = persona.name,
                        tag = persona.tag,
                        systemPrompt = persona.systemPrompt,
                        enabledTools = persona.enabledTools,
                        defaultProviderId = persona.defaultProviderId,
                        maxContextMessages = persona.maxContextMessages,
                    )
                }
                Toast.makeText(context, context.getString(R.string.common_saved), Toast.LENGTH_SHORT).show()
                editingPersona = null
            },
        )
    }
}

@Composable
private fun PersonaCard(
    persona: PersonaProfile,
    onClick: () -> Unit,
    onToggleEnabled: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MonochromeUi.cardBackground,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .background(MonochromeUi.mutedSurface, CircleShape)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(persona.name.take(1).uppercase(), fontWeight = FontWeight.Bold, color = MonochromeUi.textPrimary)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(persona.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    text = buildList {
                        if (persona.tag.isNotBlank()) add(persona.tag)
                        add(stringResource(R.string.persona_context_count, persona.maxContextMessages))
                    }.joinToString(" | "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = persona.systemPrompt,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Switch(
                checked = persona.enabled,
                onCheckedChange = { onToggleEnabled() },
                colors = monochromeSwitchColors(),
            )
        }
    }
}

@Composable
private fun PersonaEditorDialog(
    initialPersona: PersonaProfile,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: (PersonaProfile) -> Unit,
) {
    var showDeleteConfirm by remember(initialPersona.id) { mutableStateOf(false) }
    var name by remember(initialPersona.id) { mutableStateOf(initialPersona.name) }
    var tag by remember(initialPersona.id) { mutableStateOf(initialPersona.tag) }
    var systemPrompt by remember(initialPersona.id) { mutableStateOf(initialPersona.systemPrompt) }
    var maxContextMessages by remember(initialPersona.id) { mutableStateOf(initialPersona.maxContextMessages.toString()) }
    var enabled by remember(initialPersona.id) { mutableStateOf(initialPersona.enabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MonochromeUi.cardBackground,
        titleContentColor = MonochromeUi.textPrimary,
        textContentColor = MonochromeUi.textPrimary,
        confirmButton = {},
        dismissButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (initialPersona.id != "default") {
                    TextButton(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = androidx.compose.ui.graphics.Color(0xFFB42318)),
                    ) {
                        Text(stringResource(R.string.common_delete))
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(contentColor = MonochromeUi.textSecondary),
                    ) {
                        Text(stringResource(R.string.common_cancel))
                    }
                    TextButton(
                        colors = ButtonDefaults.textButtonColors(contentColor = MonochromeUi.textPrimary),
                        onClick = {
                            onSave(
                                initialPersona.copy(
                                    name = name.trim().ifBlank { initialPersona.name },
                                    tag = tag.trim(),
                                    systemPrompt = systemPrompt.trim(),
                                    defaultProviderId = "",
                                    maxContextMessages = maxContextMessages.toIntOrNull()?.coerceAtLeast(1) ?: 12,
                                    enabledTools = emptySet(),
                                    enabled = enabled,
                                ),
                            )
                        },
                    ) {
                        Text(stringResource(R.string.common_save))
                    }
                }
            }
        },
        title = { Text(stringResource(R.string.persona_edit_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PersonaGroupCard {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.persona_field_name)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = monochromeOutlinedTextFieldColors(),
                    )
                    OutlinedTextField(
                        value = tag,
                        onValueChange = { tag = it },
                        label = { Text(stringResource(R.string.persona_field_tag)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = monochromeOutlinedTextFieldColors(),
                    )
                    OutlinedTextField(
                        value = maxContextMessages,
                        onValueChange = { maxContextMessages = it.filter(Char::isDigit) },
                        label = { Text(stringResource(R.string.persona_field_max_context)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = monochromeOutlinedTextFieldColors(),
                    )
                }
                PersonaGroupCard {
                    OutlinedTextField(
                        value = systemPrompt,
                        onValueChange = { systemPrompt = it },
                        label = { Text(stringResource(R.string.persona_field_system_prompt)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 6,
                        maxLines = 10,
                        colors = monochromeOutlinedTextFieldColors(),
                    )
                }
                PersonaGroupCard {
                    ToolSwitch(stringResource(R.string.common_enabled), enabled) { enabled = it }
                }
            }
        },
    )

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = MonochromeUi.cardBackground,
            titleContentColor = MonochromeUi.textPrimary,
            textContentColor = MonochromeUi.textSecondary,
            title = { Text(stringResource(R.string.common_delete)) },
            text = { Text(stringResource(R.string.persona_delete_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = androidx.compose.ui.graphics.Color(0xFFB42318)),
                ) {
                    Text(stringResource(R.string.common_delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirm = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MonochromeUi.textSecondary),
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun PersonaGroupCard(
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MonochromeUi.inputBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun ToolSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            fontWeight = FontWeight.SemiBold,
            color = MonochromeUi.textPrimary,
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = monochromeSwitchColors(),
        )
    }
}
