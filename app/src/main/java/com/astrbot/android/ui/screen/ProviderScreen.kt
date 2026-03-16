package com.astrbot.android.ui.screen

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astrbot.android.data.ChatCompletionService
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ProviderType
import com.astrbot.android.ui.MonochromeUi
import com.astrbot.android.ui.monochromeOutlinedTextFieldColors
import com.astrbot.android.ui.monochromeSwitchColors
import com.astrbot.android.ui.viewmodel.ProviderViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProviderScreen(
    providerViewModel: ProviderViewModel = viewModel(),
    onBack: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MonochromeUi.pageBackground),
    ) {
        if (onBack != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 12.dp, top = 16.dp),
                onClick = onBack,
                shape = CircleShape,
                color = MonochromeUi.iconButtonSurface,
            ) {
                Box(
                    modifier = Modifier.padding(9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = MonochromeUi.textPrimary)
                }
            }
        }
        ProviderCatalogContent(
            providerViewModel = providerViewModel,
            onSwitchToBots = {},
            showBack = false,
        )
    }
}

@Composable
internal fun ProviderCatalogContent(
    providerViewModel: ProviderViewModel,
    onSwitchToBots: () -> Unit,
    showBack: Boolean,
    showHeader: Boolean = true,
) {
    val providers by providerViewModel.providers.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("All") }
    var editingProvider by remember { mutableStateOf<ProviderProfile?>(null) }
    var isFetchingModels by remember { mutableStateOf(false) }
    var fetchedModels by remember { mutableStateOf(emptyList<String>()) }

    val typeChips = listOf("All") + ProviderType.entries.map(::providerTypeLabel)
    val filteredProviders = providers.filter { provider ->
        val matchesSearch = searchQuery.isBlank() ||
            provider.name.contains(searchQuery, ignoreCase = true) ||
            provider.model.contains(searchQuery, ignoreCase = true) ||
            provider.baseUrl.contains(searchQuery, ignoreCase = true)
        val matchesType = selectedType == "All" || providerTypeLabel(provider.providerType) == selectedType
        matchesSearch && matchesType
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            contentPadding = PaddingValues(top = if (showBack) 72.dp else 14.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (showHeader) {
                item {
                    CatalogToggleHeader(
                        leftLabel = "Bots",
                        rightLabel = "Models",
                        leftSelected = false,
                        onSelectLeft = onSwitchToBots,
                        onSelectRight = {},
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    placeholder = { Text("搜索模型") },
                    shape = RoundedCornerShape(28.dp),
                    colors = monochromeOutlinedTextFieldColors(),
                    singleLine = true,
                )
            }
            item {
                ScrollableAssistChipRow(
                    items = typeChips,
                    selectedItem = selectedType,
                    onSelect = { selectedType = it },
                )
            }
            items(filteredProviders, key = { it.id }) { provider ->
                ProviderCard(
                    provider = provider,
                    onClick = {
                        editingProvider = provider
                        fetchedModels = emptyList()
                    },
                    onToggleEnabled = { providerViewModel.toggleEnabled(provider.id) },
                )
            }
        }

        FloatingActionButton(
            onClick = {
                editingProvider = ProviderProfile(
                    id = "",
                    name = "新模型",
                    baseUrl = "https://api.openai.com/v1",
                    model = "gpt-4.1-mini",
                    providerType = ProviderType.OPENAI_COMPATIBLE,
                    apiKey = "",
                    capabilities = setOf(ProviderCapability.CHAT),
                    enabled = true,
                )
                fetchedModels = emptyList()
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            containerColor = MonochromeUi.fabBackground,
            contentColor = MonochromeUi.fabContent,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = "新增模型")
        }
    }

    editingProvider?.let { provider ->
        ProviderEditorDialog(
            initialProvider = provider,
            fetchedModels = fetchedModels,
            isFetchingModels = isFetchingModels,
            onDismiss = { editingProvider = null },
            onDelete = {
                if (provider.id.isNotBlank()) {
                    providerViewModel.delete(provider.id)
                }
                editingProvider = null
            },
            onFetchModels = { current ->
                scope.launch {
                    isFetchingModels = true
                    fetchedModels = runCatching {
                        withContext(Dispatchers.IO) {
                            ChatCompletionService.fetchModels(
                                baseUrl = current.baseUrl,
                                apiKey = current.apiKey,
                                providerType = current.providerType,
                            )
                        }
                    }.getOrElse {
                        Toast.makeText(context, it.message ?: it.javaClass.simpleName, Toast.LENGTH_LONG).show()
                        emptyList()
                    }
                    isFetchingModels = false
                }
            },
            onSave = { profile ->
                providerViewModel.save(
                    id = profile.id.takeIf { it.isNotBlank() },
                    name = profile.name,
                    baseUrl = profile.baseUrl,
                    model = profile.model,
                    providerType = profile.providerType,
                    apiKey = profile.apiKey,
                    capabilities = profile.capabilities,
                    enabled = profile.enabled,
                )
                Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                editingProvider = null
            },
        )
    }
}

@Composable
private fun ProviderCard(
    provider: ProviderProfile,
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
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .background(MonochromeUi.mutedSurface, CircleShape)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(provider.name.take(1).uppercase(), color = MonochromeUi.textPrimary, fontWeight = FontWeight.Bold)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(provider.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "${provider.model} | ${providerTypeLabel(provider.providerType)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = provider.baseUrl,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Switch(
                checked = provider.enabled,
                onCheckedChange = { onToggleEnabled() },
                colors = monochromeSwitchColors(),
            )
        }
    }
}

@Composable
private fun ProviderEditorDialog(
    initialProvider: ProviderProfile,
    fetchedModels: List<String>,
    isFetchingModels: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onFetchModels: (ProviderProfile) -> Unit,
    onSave: (ProviderProfile) -> Unit,
) {
    var name by remember(initialProvider.id) { mutableStateOf(initialProvider.name) }
    var baseUrl by remember(initialProvider.id) { mutableStateOf(initialProvider.baseUrl) }
    var model by remember(initialProvider.id) { mutableStateOf(initialProvider.model) }
    var apiKey by remember(initialProvider.id) { mutableStateOf(initialProvider.apiKey) }
    var providerType by remember(initialProvider.id) { mutableStateOf(initialProvider.providerType) }
    var chatEnabled by remember(initialProvider.id) { mutableStateOf(ProviderCapability.CHAT in initialProvider.capabilities) }
    var enabled by remember(initialProvider.id) { mutableStateOf(initialProvider.enabled) }

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
                        initialProvider.copy(
                            id = initialProvider.id.ifBlank { java.util.UUID.randomUUID().toString() },
                            name = name.trim(),
                            baseUrl = baseUrl.trim(),
                            model = model.trim(),
                            apiKey = apiKey.trim(),
                            providerType = providerType,
                            capabilities = if (chatEnabled) setOf(ProviderCapability.CHAT) else emptySet(),
                            enabled = enabled,
                        ),
                    )
                },
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (initialProvider.id.isNotBlank()) {
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
        title = { Text("编辑模型") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = monochromeOutlinedTextFieldColors(),
                )
                SelectionField(
                    title = "提供商类型",
                    options = ProviderType.entries.map { it.name to providerTypeLabel(it) },
                    selectedId = providerType.name,
                    onSelect = { selected -> providerType = ProviderType.valueOf(selected) },
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    colors = monochromeOutlinedTextFieldColors(),
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = monochromeOutlinedTextFieldColors(),
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("模型") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = monochromeOutlinedTextFieldColors(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = {
                            onFetchModels(
                                initialProvider.copy(
                                    baseUrl = baseUrl,
                                    apiKey = apiKey,
                                    providerType = providerType,
                                ),
                            )
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MonochromeUi.textPrimary),
                    ) {
                        Text("拉取模型")
                    }
                    if (isFetchingModels) {
                        CircularProgressIndicator(color = MonochromeUi.textPrimary)
                    }
                }
                if (fetchedModels.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        fetchedModels.take(8).forEach { item ->
                            AssistChip(
                                onClick = { model = item },
                                label = { Text(item) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MonochromeUi.chipBackground,
                                    labelColor = MonochromeUi.textSecondary,
                                ),
                            )
                        }
                    }
                }
                CapabilitySwitch("聊天能力", chatEnabled) { chatEnabled = it }
                CapabilitySwitch("启用", enabled) { enabled = it }
            }
        },
    )
}

@Composable
private fun CapabilitySwitch(
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

internal fun providerTypeLabel(type: ProviderType): String {
    return when (type) {
        ProviderType.OPENAI_COMPATIBLE -> "OpenAI Compatible"
        ProviderType.DEEPSEEK -> "DeepSeek"
        ProviderType.GEMINI -> "Gemini"
        ProviderType.ANTHROPIC -> "Anthropic"
        ProviderType.CUSTOM -> "Custom"
    }
}
