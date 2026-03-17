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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astrbot.android.R
import com.astrbot.android.model.PersonaProfile
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.ui.MonochromeUi
import com.astrbot.android.ui.monochromeOutlinedTextFieldColors
import com.astrbot.android.ui.monochromeSwitchColors
import com.astrbot.android.ui.viewmodel.PersonaViewModel
import com.astrbot.android.ui.viewmodel.ProviderViewModel
import java.util.UUID

@Composable
fun PersonaScreen(
    personaViewModel: PersonaViewModel = viewModel(),
    providerViewModel: ProviderViewModel = viewModel(),
) {
    val personas by personaViewModel.personas.collectAsState()
    val providers by providerViewModel.providers.collectAsState()
    val context = LocalContext.current

    var searchQuery by remember { mutableStateOf("") }
    val allTagLabel = stringResource(R.string.bot_tag_all)
    val newPersonaLabel = stringResource(R.string.persona_new)
    var selectedTag by remember { mutableStateOf(allTagLabel) }
    var editingPersona by remember { mutableStateOf<PersonaProfile?>(null) }

    val chatProviders = providers.filter { it.enabled && ProviderCapability.CHAT in it.capabilities }
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
                    shape = RoundedCornerShape(28.dp),
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
                    providerName = chatProviders.firstOrNull { it.id == persona.defaultProviderId }?.name.orEmpty(),
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
                    defaultProviderId = chatProviders.firstOrNull()?.id.orEmpty(),
                )
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

    editingPersona?.let { profile ->
        PersonaEditorDialog(
            initialPersona = profile,
            providerOptions = chatProviders.map { it.id to it.name },
            onDismiss = { editingPersona = null },
            onDelete = {
                personaViewModel.delete(profile.id)
                editingPersona = null
            },
            onSave = { persona ->
                if (personas.any { it.id == persona.id }) {
                    personaViewModel.update(persona)
                } else {
                    personaViewModel.add(
                        name = persona.name,
                        tag = persona.tag,
                        systemPrompt = persona.systemPrompt,
                        enabledTools = emptySet(),
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
    providerName: String,
    onClick: () -> Unit,
    onToggleEnabled: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(26.dp),
        color = MonochromeUi.cardBackground,
        tonalElevation = 2.dp,
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
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(persona.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    text = buildList {
                        if (persona.tag.isNotBlank()) add(persona.tag)
                        add(stringResource(R.string.persona_context_count, persona.maxContextMessages))
                        if (providerName.isNotBlank()) add(providerName)
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
    providerOptions: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: (PersonaProfile) -> Unit,
) {
    var name by remember(initialPersona.id) { mutableStateOf(initialPersona.name) }
    var tag by remember(initialPersona.id) { mutableStateOf(initialPersona.tag) }
    var systemPrompt by remember(initialPersona.id) { mutableStateOf(initialPersona.systemPrompt) }
    var defaultProviderId by remember(initialPersona.id) { mutableStateOf(initialPersona.defaultProviderId) }
    var maxContextMessages by remember(initialPersona.id) { mutableStateOf(initialPersona.maxContextMessages.toString()) }
    var enabled by remember(initialPersona.id) { mutableStateOf(initialPersona.enabled) }

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
                        initialPersona.copy(
                            name = name.trim().ifBlank { initialPersona.name },
                            tag = tag.trim(),
                            systemPrompt = systemPrompt.trim(),
                            defaultProviderId = defaultProviderId,
                            maxContextMessages = maxContextMessages.toIntOrNull()?.coerceAtLeast(1) ?: 12,
                            enabledTools = emptySet(),
                            enabled = enabled,
                        ),
                    )
                },
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (initialPersona.id != "default") {
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
        title = { Text(stringResource(R.string.persona_edit_title)) },
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
                SelectionField(
                    title = stringResource(R.string.persona_field_default_model),
                    options = providerOptions,
                    selectedId = defaultProviderId,
                    onSelect = { defaultProviderId = it },
                )
                OutlinedTextField(
                    value = maxContextMessages,
                    onValueChange = { maxContextMessages = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.persona_field_max_context)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = monochromeOutlinedTextFieldColors(),
                )
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text(stringResource(R.string.persona_field_system_prompt)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 6,
                    maxLines = 10,
                    colors = monochromeOutlinedTextFieldColors(),
                )
                ToolSwitch(stringResource(R.string.common_enabled), enabled) { enabled = it }
            }
        },
    )
}

@Composable
private fun ToolSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = monochromeSwitchColors())
    }
}
