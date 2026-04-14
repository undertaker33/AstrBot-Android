package com.astrbot.android.ui.bot
import com.astrbot.android.ui.common.showProfileDeletionFailureToast
import com.astrbot.android.ui.persona.PersonaCatalogContent
import com.astrbot.android.ui.provider.ProviderCatalogContent

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.SavedQqAccount
import com.astrbot.android.ui.app.FloatingBottomNavFabBottomPadding
import com.astrbot.android.ui.app.MonochromeUi
import com.astrbot.android.ui.app.monochromeOutlinedTextFieldColors
import com.astrbot.android.ui.app.monochromeSwitchColors
import com.astrbot.android.ui.viewmodel.BotViewModel
import com.astrbot.android.ui.viewmodel.ProviderViewModel
import java.util.UUID

@Composable
internal fun BotScreen(
    botViewModel: BotViewModel = astrBotViewModel(),
    providerViewModel: ProviderViewModel = astrBotViewModel(),
    workspaceTab: BotWorkspaceTab,
    onWorkspaceTabChange: (BotWorkspaceTab) -> Unit = {},
) {
    val botProfiles by botViewModel.botProfiles.collectAsState()
    val selectedBotId by botViewModel.selectedBotId.collectAsState()
    val providers by botViewModel.providers.collectAsState()
    val personas by botViewModel.personas.collectAsState()
    val configProfiles by botViewModel.configProfiles.collectAsState()
    val loginState by botViewModel.loginState.collectAsState()
    val context = LocalContext.current

    val chatProviders = providers.filter { it.enabled && ProviderCapability.CHAT in it.capabilities }
    val enabledPersonas = personas.filter { it.enabled }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MonochromeUi.pageBackground),
    ) {
        when (workspaceTab) {
            BotWorkspaceTab.BOTS -> {
                BotCatalogContent(
                    bots = botProfiles,
                    selectedBotId = selectedBotId,
                    providerOptions = chatProviders,
                    personaOptions = enabledPersonas,
                    configOptions = configProfiles,
                    qqAccountOptions = loginState.savedAccounts,
                    onSelectBot = { botViewModel.select(it) },
                    onToggleBot = { bot, enabled -> botViewModel.save(bot.copy(autoReplyEnabled = enabled)) },
                    onSaveBot = { bot, defaultChatProviderId ->
                        botViewModel.saveWithConfigModel(bot, defaultChatProviderId)
                        botViewModel.select(bot.id)
                        Toast.makeText(context, context.getString(R.string.common_saved), Toast.LENGTH_SHORT).show()
                    },
                    onDeleteBot = { bot ->
                        botViewModel.select(bot.id)
                        botViewModel.deleteSelected().onFailure { error ->
                            showProfileDeletionFailureToast(context, error)
                        }
                    },
                )
            }

            BotWorkspaceTab.MODELS -> {
                ProviderCatalogContent(
                    providerViewModel = providerViewModel,
                    onSwitchToBots = { onWorkspaceTabChange(BotWorkspaceTab.BOTS) },
                    showBack = false,
                    showHeader = false,
                )
            }

            BotWorkspaceTab.PERSONAS -> {
                PersonaCatalogContent()
            }
        }
    }
}

@Composable
private fun BotCatalogContent(
    bots: List<BotProfile>,
    selectedBotId: String,
    providerOptions: List<com.astrbot.android.model.ProviderProfile>,
    personaOptions: List<com.astrbot.android.model.PersonaProfile>,
    configOptions: List<com.astrbot.android.model.ConfigProfile>,
    qqAccountOptions: List<SavedQqAccount>,
    onSelectBot: (String) -> Unit,
    onToggleBot: (BotProfile, Boolean) -> Unit,
    onSaveBot: (BotProfile, String) -> Unit,
    onDeleteBot: (BotProfile) -> Result<Unit>,
) {
    var searchQuery by remember { mutableStateOf("") }
    val allTagLabel = stringResource(R.string.bot_tag_all)
    val newBotLabel = stringResource(R.string.bot_new)
    var selectedTag by remember { mutableStateOf(allTagLabel) }
    var editingBot by remember { mutableStateOf<BotProfile?>(null) }

    val tags = listOf(allTagLabel) + bots.mapNotNull { it.tag.takeIf(String::isNotBlank) }.distinct().sorted()
    val filteredBots = bots.filter { bot ->
        val matchesSearch = searchQuery.isBlank() ||
            bot.displayName.contains(searchQuery, ignoreCase = true) ||
            bot.accountHint.contains(searchQuery, ignoreCase = true) ||
            bot.tag.contains(searchQuery, ignoreCase = true)
        val matchesTag = selectedTag == allTagLabel || bot.tag == selectedTag
        matchesSearch && matchesTag
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                    placeholder = { Text(stringResource(R.string.bot_search_placeholder)) },
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
            items(filteredBots, key = { it.id }) { bot ->
                BotListCard(
                    bot = bot,
                    active = bot.id == selectedBotId,
                    providerName = configProviderName(configOptions, providerOptions, bot.configProfileId),
                    personaName = personaOptions.firstOrNull { it.id == bot.defaultPersonaId }?.name.orEmpty(),
                    qqAccountsLabel = bot.boundQqUins.ifEmpty { listOf("No QQ bound") }.joinToString(", "),
                    onClick = {
                        onSelectBot(bot.id)
                        editingBot = bot
                    },
                    onToggle = { enabled -> onToggleBot(bot, enabled) },
                )
            }
        }

        FloatingActionButton(
            onClick = {
                editingBot = BotProfile(
                    id = "bot-${UUID.randomUUID()}",
                    displayName = newBotLabel,
                    tag = "",
                    accountHint = "",
                    defaultPersonaId = personaOptions.firstOrNull()?.id.orEmpty(),
                    configProfileId = configOptions.firstOrNull()?.id ?: "default",
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
            Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.provider_add_bot))
        }
    }

    editingBot?.let { profile ->
        BotEditorDialog(
            initialBot = profile,
            providerOptions = providerOptions.map { it.id to it.name },
            configProfiles = configOptions,
            personaOptions = personaOptions.map { it.id to it.name },
            configOptions = configOptions.map { it.id to it.name },
            qqAccountOptions = qqAccountOptions,
            onDismiss = { editingBot = null },
            onDelete = {
                val result = onDeleteBot(profile)
                if (result.isSuccess) {
                    editingBot = null
                }
            },
            onSave = { bot, defaultChatProviderId ->
                onSaveBot(bot, defaultChatProviderId)
                editingBot = null
            },
        )
    }
}

@Composable
private fun BotListCard(
    bot: BotProfile,
    active: Boolean,
    providerName: String,
    personaName: String,
    qqAccountsLabel: String,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(26.dp),
        color = if (active) MonochromeUi.cardAltBackground else MonochromeUi.cardBackground,
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
                Text(bot.displayName.take(1).uppercase(), color = MonochromeUi.textPrimary, fontWeight = FontWeight.Bold)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(bot.displayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    text = stringResource(
                        R.string.bot_model_persona_summary,
                        providerName.ifBlank { stringResource(R.string.bot_not_set) },
                        personaName.ifBlank { stringResource(R.string.bot_not_set) },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildList {
                        add(qqAccountsLabel)
                        if (bot.tag.isNotBlank()) add(bot.tag)
                    }.joinToString(" | "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MonochromeUi.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Switch(
                checked = bot.autoReplyEnabled,
                onCheckedChange = onToggle,
                colors = monochromeSwitchColors(),
            )
        }
    }
}

@Composable
private fun BotEditorDialog(
    initialBot: BotProfile,
    providerOptions: List<Pair<String, String>>,
    configProfiles: List<ConfigProfile>,
    personaOptions: List<Pair<String, String>>,
    configOptions: List<Pair<String, String>>,
    qqAccountOptions: List<SavedQqAccount>,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: (BotProfile, String) -> Unit,
) {
    var showDeleteConfirm by remember(initialBot.id) { mutableStateOf(false) }
    val noQqBoundLabel = stringResource(R.string.bot_no_qq_bound)
    var displayName by remember(initialBot.id) { mutableStateOf(initialBot.displayName) }
    var tag by remember(initialBot.id) { mutableStateOf(initialBot.tag) }
    var boundQqUins by remember(initialBot.id) { mutableStateOf(initialBot.boundQqUins) }
    var triggerWords by remember(initialBot.id) { mutableStateOf(initialBot.triggerWords.joinToString(", ")) }
    var autoReplyEnabled by remember(initialBot.id) { mutableStateOf(initialBot.autoReplyEnabled) }
    var persistConversationLocally by remember(initialBot.id) { mutableStateOf(initialBot.persistConversationLocally) }
    var defaultPersonaId by remember(initialBot.id) { mutableStateOf(initialBot.defaultPersonaId) }
    var configProfileId by remember(initialBot.id) { mutableStateOf(initialBot.configProfileId) }
    var defaultProviderId by remember(initialBot.id) {
        mutableStateOf(
            configProfiles.firstOrNull { it.id == initialBot.configProfileId }?.defaultChatProviderId
                ?: initialBot.defaultProviderId,
        )
    }

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
                if (initialBot.id != "qq-main") {
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
                                initialBot.copy(
                                    displayName = displayName.trim().ifBlank { initialBot.displayName },
                                    tag = tag.trim(),
                                    accountHint = boundQqUins.joinToString(", ").ifBlank { noQqBoundLabel },
                                    boundQqUins = boundQqUins,
                                    triggerWords = triggerWords.split(",").map { it.trim() }.filter { it.isNotBlank() },
                                    autoReplyEnabled = autoReplyEnabled,
                                    persistConversationLocally = persistConversationLocally,
                                    defaultPersonaId = defaultPersonaId,
                                    configProfileId = configProfileId,
                                ),
                                defaultProviderId,
                            )
                        },
                    ) {
                        Text(stringResource(R.string.common_save))
                    }
                }
            }
        },
        title = { Text(stringResource(R.string.bot_edit_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                EditorGroupCard {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text(stringResource(R.string.bot_field_name)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = monochromeOutlinedTextFieldColors(),
                    )
                    OutlinedTextField(
                        value = tag,
                        onValueChange = { tag = it },
                        label = { Text(stringResource(R.string.bot_field_tag)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = monochromeOutlinedTextFieldColors(),
                    )
                    OutlinedTextField(
                        value = triggerWords,
                        onValueChange = { triggerWords = it },
                        label = { Text(stringResource(R.string.bot_field_trigger_words)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = monochromeOutlinedTextFieldColors(),
                    )
                }
                EditorGroupCard {
                    MultiSelectionField(
                        title = stringResource(R.string.bot_field_bound_qq),
                        options = qqAccountOptions,
                        selectedIds = boundQqUins,
                        onSelectionChange = { boundQqUins = it },
                    )
                    SelectionField(
                        title = stringResource(R.string.bot_field_default_model),
                        options = providerOptions,
                        selectedId = defaultProviderId,
                        onSelect = { defaultProviderId = it },
                    )
                    SelectionField(
                        title = stringResource(R.string.bot_field_default_persona),
                        options = personaOptions,
                        selectedId = defaultPersonaId,
                        onSelect = { defaultPersonaId = it },
                    )
                    SelectionField(
                        title = stringResource(R.string.bot_field_config_profile),
                        options = configOptions,
                        selectedId = configProfileId,
                        onSelect = {
                            configProfileId = it
                            defaultProviderId = configProfiles.firstOrNull { profile -> profile.id == it }?.defaultChatProviderId.orEmpty()
                        },
                    )
                }
                EditorGroupCard {
                    PreferenceToggleRow(
                        title = stringResource(R.string.bot_auto_reply_title),
                        subtitle = stringResource(R.string.bot_auto_reply_desc),
                        checked = autoReplyEnabled,
                        onCheckedChange = { autoReplyEnabled = it },
                    )
                    PreferenceToggleRow(
                        title = stringResource(R.string.bot_persist_title),
                        subtitle = stringResource(R.string.bot_persist_desc),
                        checked = persistConversationLocally,
                        onCheckedChange = { persistConversationLocally = it },
                    )
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
            text = { Text(stringResource(R.string.bot_delete_confirm_message)) },
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
private fun EditorGroupCard(
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
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
private fun PreferenceToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            )
        }
        Box(
            modifier = Modifier
                .width(76.dp)
                .padding(top = 4.dp),
            contentAlignment = Alignment.TopEnd,
        ) {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = monochromeSwitchColors(),
            )
        }
    }
}

@Composable
private fun MultiSelectionField(
    title: String,
    options: List<SavedQqAccount>,
    selectedIds: List<String>,
    onSelectionChange: (List<String>) -> Unit,
) {
    var expanded by remember(selectedIds, options) { mutableStateOf(false) }
    val summary = when {
        selectedIds.isEmpty() -> stringResource(R.string.bot_no_qq_selected)
        else -> options
            .filter { it.uin in selectedIds }
            .map { it.nickName.ifBlank { it.uin } }
            .ifEmpty { selectedIds }
            .joinToString(", ")
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelSmall)
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(18.dp),
            color = MonochromeUi.inputBackground,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(summary, style = MaterialTheme.typography.bodySmall)
                Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, tint = MonochromeUi.textSecondary)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (options.isEmpty()) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.bot_no_saved_qq)) },
                    onClick = { expanded = false },
                )
            } else {
                options.forEach { account ->
                    val checked = selectedIds.contains(account.uin)
                    DropdownMenuItem(
                        text = { Text(account.nickName.ifBlank { account.uin }) },
                        leadingIcon = {
                            Icon(
                                imageVector = if (checked) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            onSelectionChange(
                                if (checked) selectedIds - account.uin else selectedIds + account.uin,
                            )
                        },
                    )
                }
            }
        }
    }
}

private fun configProviderName(
    configOptions: List<ConfigProfile>,
    providerOptions: List<com.astrbot.android.model.ProviderProfile>,
    configProfileId: String,
): String {
    val providerId = configOptions.firstOrNull { it.id == configProfileId }?.defaultChatProviderId.orEmpty()
    return providerOptions.firstOrNull { it.id == providerId }?.name.orEmpty()
}

@Composable
internal fun SelectionField(
    title: String,
    options: List<Pair<String, String>>,
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember(selectedId, options) { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selectedId }?.second ?: stringResource(R.string.common_not_selected)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelSmall)
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(18.dp),
            color = MonochromeUi.inputBackground,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(selectedLabel, style = MaterialTheme.typography.bodySmall)
                Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, tint = MonochromeUi.textSecondary)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.second) },
                    onClick = {
                        onSelect(option.first)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
internal fun CatalogToggleHeader(
    leftLabel: String,
    rightLabel: String,
    leftSelected: Boolean,
    onSelectLeft: () -> Unit,
    onSelectRight: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onSelectLeft, contentPadding = PaddingValues(0.dp)) {
            Text(
                leftLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (leftSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (leftSelected) MonochromeUi.textPrimary else MonochromeUi.textSecondary,
            )
        }
        Text("|", style = MaterialTheme.typography.titleMedium, color = MonochromeUi.textSecondary)
        TextButton(onClick = onSelectRight, contentPadding = PaddingValues(0.dp)) {
            Text(
                rightLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (leftSelected) FontWeight.Medium else FontWeight.Bold,
                color = if (leftSelected) MonochromeUi.textSecondary else MonochromeUi.textPrimary,
            )
        }
    }
}

@Composable
internal fun ScrollableAssistChipRow(
    items: List<String>,
    selectedItem: String,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            AssistChip(
                onClick = { onSelect(item) },
                label = { Text(item) },
                leadingIcon = if (selectedItem == item) {
                    { Icon(Icons.Outlined.Check, contentDescription = null) }
                } else {
                    null
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (selectedItem == item) MonochromeUi.chipSelectedBackground else MonochromeUi.chipBackground,
                    labelColor = if (selectedItem == item) MonochromeUi.textPrimary else MonochromeUi.textSecondary,
                    leadingIconContentColor = MonochromeUi.textPrimary,
                ),
            )
        }
    }
}
