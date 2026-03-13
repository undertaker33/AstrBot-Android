package com.astrbot.android.ui.screen

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.ui.MonochromeUi
import com.astrbot.android.ui.monochromeOutlinedTextFieldColors
import com.astrbot.android.ui.monochromeSwitchColors
import com.astrbot.android.ui.viewmodel.BotViewModel
import com.astrbot.android.ui.viewmodel.ProviderViewModel
import java.util.UUID

@Composable
fun BotScreen(
    botViewModel: BotViewModel = viewModel(),
    providerViewModel: ProviderViewModel = viewModel(),
    showModels: Boolean,
    onShowBots: () -> Unit,
) {
    val botProfiles by botViewModel.botProfiles.collectAsState()
    val selectedBotId by botViewModel.selectedBotId.collectAsState()
    val providers by botViewModel.providers.collectAsState()
    val personas by botViewModel.personas.collectAsState()
    val context = LocalContext.current

    val chatProviders = providers.filter { it.enabled && ProviderCapability.CHAT in it.capabilities }
    val enabledPersonas = personas.filter { it.enabled }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF3F3F1)),
    ) {
        if (!showModels) {
            BotCatalogContent(
                bots = botProfiles,
                selectedBotId = selectedBotId,
                providerOptions = chatProviders,
                personaOptions = enabledPersonas,
                onSelectBot = { botViewModel.select(it) },
                onSaveBot = { bot ->
                    botViewModel.save(bot)
                    botViewModel.select(bot.id)
                    Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                },
                onDeleteBot = { bot ->
                    botViewModel.select(bot.id)
                    botViewModel.deleteSelected()
                },
            )
        } else {
            ProviderCatalogContent(
                providerViewModel = providerViewModel,
                onSwitchToBots = onShowBots,
                showBack = false,
                showHeader = false,
            )
        }
    }
}

@Composable
private fun BotCatalogContent(
    bots: List<BotProfile>,
    selectedBotId: String,
    providerOptions: List<com.astrbot.android.model.ProviderProfile>,
    personaOptions: List<com.astrbot.android.model.PersonaProfile>,
    onSelectBot: (String) -> Unit,
    onSaveBot: (BotProfile) -> Unit,
    onDeleteBot: (BotProfile) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf("全部") }
    var editingBot by remember { mutableStateOf<BotProfile?>(null) }

    val tags = listOf("全部") + bots.mapNotNull { it.tag.takeIf(String::isNotBlank) }.distinct().sorted()
    val filteredBots = bots.filter { bot ->
        val matchesSearch = searchQuery.isBlank() ||
            bot.displayName.contains(searchQuery, ignoreCase = true) ||
            bot.accountHint.contains(searchQuery, ignoreCase = true) ||
            bot.tag.contains(searchQuery, ignoreCase = true)
        val matchesTag = selectedTag == "全部" || bot.tag == selectedTag
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
                    placeholder = { Text("搜索机器人") },
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
                    providerName = providerOptions.firstOrNull { it.id == bot.defaultProviderId }?.name.orEmpty(),
                    personaName = personaOptions.firstOrNull { it.id == bot.defaultPersonaId }?.name.orEmpty(),
                    onClick = {
                        onSelectBot(bot.id)
                        editingBot = bot
                    },
                )
            }
        }

        FloatingActionButton(
            onClick = {
                editingBot = BotProfile(
                    id = "bot-${UUID.randomUUID()}",
                    displayName = "新机器人",
                    tag = "",
                    accountHint = "",
                    defaultProviderId = providerOptions.firstOrNull()?.id.orEmpty(),
                    defaultPersonaId = personaOptions.firstOrNull()?.id.orEmpty(),
                )
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            containerColor = Color(0xFF151515),
            contentColor = Color.White,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = "新建机器人")
        }
    }

    editingBot?.let { profile ->
        BotEditorDialog(
            initialBot = profile,
            providerOptions = providerOptions.map { it.id to it.name },
            personaOptions = personaOptions.map { it.id to it.name },
            onDismiss = { editingBot = null },
            onDelete = {
                if (bots.size > 1) {
                    onDeleteBot(profile)
                }
                editingBot = null
            },
            onSave = {
                onSaveBot(it)
                editingBot = null
            },
        )
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
                color = if (leftSelected) Color(0xFF0F172A) else Color(0xFF94A3B8),
            )
        }
        Text(
            "|",
            style = MaterialTheme.typography.titleMedium,
            color = Color(0xFF94A3B8),
        )
        TextButton(onClick = onSelectRight, contentPadding = PaddingValues(0.dp)) {
            Text(
                rightLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (leftSelected) FontWeight.Medium else FontWeight.Bold,
                color = if (leftSelected) Color(0xFF94A3B8) else Color(0xFF0F172A),
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
            )
        }
    }
}

@Composable
private fun BotListCard(
    bot: BotProfile,
    active: Boolean,
    providerName: String,
    personaName: String,
    onClick: () -> Unit,
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
                    .background(Color(0xFF1B1B1B), CircleShape)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(bot.displayName.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(bot.displayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    text = buildList {
                        add(bot.accountHint.ifBlank { bot.platformName })
                        if (bot.tag.isNotBlank()) add(bot.tag)
                    }.joinToString(" | "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "模型：${providerName.ifBlank { "未选择" }}  |  人格：${personaName.ifBlank { "未选择" }}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            Switch(
                checked = bot.autoReplyEnabled,
                onCheckedChange = null,
                colors = monochromeSwitchColors(),
            )
        }
    }
}

@Composable
private fun BotEditorDialog(
    initialBot: BotProfile,
    providerOptions: List<Pair<String, String>>,
    personaOptions: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: (BotProfile) -> Unit,
) {
    var displayName by remember(initialBot.id) { mutableStateOf(initialBot.displayName) }
    var tag by remember(initialBot.id) { mutableStateOf(initialBot.tag) }
    var accountHint by remember(initialBot.id) { mutableStateOf(initialBot.accountHint) }
    var triggerWords by remember(initialBot.id) { mutableStateOf(initialBot.triggerWords.joinToString(", ")) }
    var autoReplyEnabled by remember(initialBot.id) { mutableStateOf(initialBot.autoReplyEnabled) }
    var defaultProviderId by remember(initialBot.id) { mutableStateOf(initialBot.defaultProviderId) }
    var defaultPersonaId by remember(initialBot.id) { mutableStateOf(initialBot.defaultPersonaId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        initialBot.copy(
                            displayName = displayName.trim().ifBlank { initialBot.displayName },
                            tag = tag.trim(),
                            accountHint = accountHint.trim(),
                            triggerWords = triggerWords.split(",").map { it.trim() }.filter { it.isNotBlank() },
                            autoReplyEnabled = autoReplyEnabled,
                            defaultProviderId = defaultProviderId,
                            defaultPersonaId = defaultPersonaId,
                        ),
                    )
                },
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (initialBot.id != "qq-main") {
                    TextButton(onClick = onDelete) {
                        Text("删除")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        },
        title = { Text("编辑机器人") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("名称") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = monochromeOutlinedTextFieldColors(),
                )
                OutlinedTextField(
                    value = tag,
                    onValueChange = { tag = it },
                    label = { Text("标签") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = monochromeOutlinedTextFieldColors(),
                )
                OutlinedTextField(
                    value = accountHint,
                    onValueChange = { accountHint = it },
                    label = { Text("账号提示") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = monochromeOutlinedTextFieldColors(),
                )
                OutlinedTextField(
                    value = triggerWords,
                    onValueChange = { triggerWords = it },
                    label = { Text("触发词") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = monochromeOutlinedTextFieldColors(),
                )
                SelectionField(
                    title = "默认模型",
                    options = providerOptions,
                    selectedId = defaultProviderId,
                    onSelect = { defaultProviderId = it },
                )
                SelectionField(
                    title = "默认人格",
                    options = personaOptions,
                    selectedId = defaultPersonaId,
                    onSelect = { defaultPersonaId = it },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("自动回复", fontWeight = FontWeight.SemiBold)
                        Text(
                            "启用后可用于 QQ 自动回复。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        )
                    }
                    Switch(
                        checked = autoReplyEnabled,
                        onCheckedChange = { autoReplyEnabled = it },
                        colors = monochromeSwitchColors(),
                    )
                }
            }
        },
    )
}

@Composable
internal fun SelectionField(
    title: String,
    options: List<Pair<String, String>>,
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember(selectedId, options) { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selectedId }?.second ?: "未选择"

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelSmall)
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(18.dp),
            color = CardDefaults.outlinedCardColors().containerColor,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(selectedLabel, style = MaterialTheme.typography.bodySmall)
                Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
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
