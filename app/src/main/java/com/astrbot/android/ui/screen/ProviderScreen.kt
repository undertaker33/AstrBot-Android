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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Info
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astrbot.android.R
import com.astrbot.android.data.ChatCompletionService
import com.astrbot.android.model.FeatureSupportState
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ProviderType
import com.astrbot.android.model.defaultCapability
import com.astrbot.android.model.displayLabel
import com.astrbot.android.model.displayLabel as providerTypeDisplayLabel
import com.astrbot.android.model.inferMultimodalRuleSupport
import com.astrbot.android.model.isVisibleInCatalog
import com.astrbot.android.model.supportsMultimodalCheck
import com.astrbot.android.model.supportsPullModels
import com.astrbot.android.model.visibleProviderTypesFor
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
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.common_back), tint = MonochromeUi.textPrimary)
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
    var selectedCapability by remember { mutableStateOf(ProviderCapability.CHAT) }
    var editingProvider by remember { mutableStateOf<ProviderProfile?>(null) }
    var pendingNewCapability by remember { mutableStateOf<ProviderCapability?>(null) }
    var isFetchingModels by remember { mutableStateOf(false) }
    var fetchedModels by remember { mutableStateOf(emptyList<String>()) }
    var isCheckingMultimodal by remember { mutableStateOf(false) }

    val capabilityChips = ProviderCapability.entries.map { it.displayLabel() }
    val filteredProviders = providers.filter { provider ->
        val matchesSearch = searchQuery.isBlank() ||
            provider.name.contains(searchQuery, ignoreCase = true) ||
            provider.model.contains(searchQuery, ignoreCase = true) ||
            provider.baseUrl.contains(searchQuery, ignoreCase = true)
        val matchesCapability = selectedCapability in provider.capabilities
        matchesSearch && matchesCapability
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
                    placeholder = { Text(stringResource(R.string.provider_search_placeholder)) },
                    shape = RoundedCornerShape(28.dp),
                    colors = monochromeOutlinedTextFieldColors(),
                    singleLine = true,
                )
            }
            item {
                ScrollableAssistChipRow(
                    items = capabilityChips,
                    selectedItem = selectedCapability.displayLabel(),
                    onSelect = { selected ->
                        selectedCapability = ProviderCapability.entries.first { it.displayLabel() == selected }
                    },
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
            onClick = { pendingNewCapability = selectedCapability },
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

    pendingNewCapability?.let { capability ->
        ProviderTypePickerDialog(
            capability = capability,
            onDismiss = { pendingNewCapability = null },
            onSelect = { type ->
                editingProvider = createEmptyProvider(type)
                fetchedModels = emptyList()
                pendingNewCapability = null
            },
        )
    }

    editingProvider?.let { provider ->
        ProviderEditorDialog(
            initialProvider = provider,
            fetchedModels = fetchedModels,
            isFetchingModels = isFetchingModels,
            isCheckingMultimodal = isCheckingMultimodal,
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
            onCheckMultimodal = { current, onResult ->
                scope.launch {
                    isCheckingMultimodal = true
                    val ruleResult = ChatCompletionService.detectMultimodalRule(current)
                    val probeResult = runCatching {
                        withContext(Dispatchers.IO) {
                            ChatCompletionService.probeMultimodalSupport(current)
                        }
                    }.getOrElse {
                        Toast.makeText(context, it.message ?: it.javaClass.simpleName, Toast.LENGTH_LONG).show()
                        FeatureSupportState.UNKNOWN
                    }
                    onResult(ruleResult, probeResult)
                    isCheckingMultimodal = false
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
                    multimodalRuleSupport = profile.multimodalRuleSupport,
                    multimodalProbeSupport = profile.multimodalProbeSupport,
                )
                Toast.makeText(context, context.getString(R.string.common_saved), Toast.LENGTH_SHORT).show()
                editingProvider = null
            },
        )
    }
}

@Composable
private fun ProviderTypePickerDialog(
    capability: ProviderCapability,
    onDismiss: () -> Unit,
    onSelect: (ProviderType) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MonochromeUi.cardBackground,
        titleContentColor = MonochromeUi.textPrimary,
        textContentColor = MonochromeUi.textPrimary,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = MonochromeUi.textSecondary)) {
                Text(stringResource(R.string.common_close))
            }
        },
        title = { Text(stringResource(R.string.provider_choose)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                visibleProviderTypesFor(capability).forEach { type ->
                    Surface(
                        onClick = { onSelect(type) },
                        shape = RoundedCornerShape(18.dp),
                        color = MonochromeUi.inputBackground,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(type.providerTypeDisplayLabel(), fontWeight = FontWeight.SemiBold)
                                Text(capability.displayLabel(), color = MonochromeUi.textSecondary, style = MaterialTheme.typography.bodySmall)
                            }
                            Icon(Icons.Outlined.Info, contentDescription = null, tint = MonochromeUi.textSecondary)
                        }
                    }
                }
            }
        },
    )
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
                    text = "${provider.model} | ${provider.providerType.providerTypeDisplayLabel()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = provider.capabilities.joinToString(" | ") { it.displayLabel() },
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
    isCheckingMultimodal: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onFetchModels: (ProviderProfile) -> Unit,
    onCheckMultimodal: (ProviderProfile, (FeatureSupportState, FeatureSupportState) -> Unit) -> Unit,
    onSave: (ProviderProfile) -> Unit,
) {
    var name by remember(initialProvider.id) { mutableStateOf(initialProvider.name) }
    var baseUrl by remember(initialProvider.id) { mutableStateOf(initialProvider.baseUrl) }
    var model by remember(initialProvider.id) { mutableStateOf(initialProvider.model) }
    var apiKey by remember(initialProvider.id) { mutableStateOf(initialProvider.apiKey) }
    var providerType by remember(initialProvider.id) { mutableStateOf(initialProvider.providerType) }
    var enabled by remember(initialProvider.id) { mutableStateOf(initialProvider.enabled) }
    var multimodalRuleSupport by remember(initialProvider.id) { mutableStateOf(initialProvider.multimodalRuleSupport) }
    var multimodalProbeSupport by remember(initialProvider.id) { mutableStateOf(initialProvider.multimodalProbeSupport) }

    val capability = providerType.defaultCapability()
    val providerOptions = visibleProviderTypesFor(capability).map { it.name to it.providerTypeDisplayLabel() }

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
                            name = name.trim().ifBlank { providerType.providerTypeDisplayLabel() },
                            baseUrl = baseUrl.trim(),
                            model = model.trim(),
                            apiKey = apiKey.trim(),
                            providerType = providerType,
                            capabilities = setOf(providerType.defaultCapability()),
                            enabled = enabled,
                            multimodalRuleSupport = multimodalRuleSupport,
                            multimodalProbeSupport = multimodalProbeSupport,
                        ),
                    )
                },
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (initialProvider.id.isNotBlank()) {
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
        title = {
            Text(
                when (capability) {
                    ProviderCapability.CHAT -> stringResource(R.string.provider_edit_chat)
                    ProviderCapability.STT -> stringResource(R.string.provider_edit_stt)
                    ProviderCapability.TTS -> stringResource(R.string.provider_edit_tts)
                    ProviderCapability.AGENT_EXECUTOR -> stringResource(R.string.provider_edit_agent)
                },
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.provider_field_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = monochromeOutlinedTextFieldColors(),
                )
                SelectionField(
                    title = stringResource(R.string.provider_field_type),
                    options = providerOptions,
                    selectedId = providerType.name,
                    onSelect = { selected ->
                        providerType = ProviderType.valueOf(selected)
                        multimodalRuleSupport = inferMultimodalRuleSupport(providerType, model)
                        multimodalProbeSupport = FeatureSupportState.UNKNOWN
                    },
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(stringResource(R.string.provider_field_api_key)) },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    colors = monochromeOutlinedTextFieldColors(),
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text(stringResource(R.string.provider_field_base_url)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = monochromeOutlinedTextFieldColors(),
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = {
                        model = it
                        multimodalRuleSupport = inferMultimodalRuleSupport(providerType, it)
                    },
                    label = { Text(stringResource(R.string.provider_field_model)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = monochromeOutlinedTextFieldColors(),
                )
                if (providerType.supportsPullModels()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = {
                                onFetchModels(
                                    initialProvider.copy(
                                        baseUrl = baseUrl,
                                        apiKey = apiKey,
                                        model = model,
                                        providerType = providerType,
                                    ),
                                )
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MonochromeUi.textPrimary),
                        ) {
                            Text(stringResource(R.string.provider_pull_models))
                        }
                        if (isFetchingModels) {
                            CircularProgressIndicator(color = MonochromeUi.textPrimary)
                        }
                    }
                }
                if (fetchedModels.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        fetchedModels.take(10).forEach { item ->
                            AssistChip(
                                onClick = {
                                    model = item
                                    multimodalRuleSupport = inferMultimodalRuleSupport(providerType, item)
                                },
                                label = { Text(item) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MonochromeUi.chipBackground,
                                    labelColor = MonochromeUi.textSecondary,
                                ),
                            )
                        }
                    }
                }
                if (providerType.supportsMultimodalCheck()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = {
                                onCheckMultimodal(
                                    initialProvider.copy(
                                        baseUrl = baseUrl,
                                        apiKey = apiKey,
                                        model = model,
                                        providerType = providerType,
                                        capabilities = setOf(providerType.defaultCapability()),
                                    ),
                                ) { rule, probe ->
                                    multimodalRuleSupport = rule
                                    multimodalProbeSupport = probe
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MonochromeUi.textPrimary),
                        ) {
                            Text(stringResource(R.string.provider_check_multimodal))
                        }
                        if (isCheckingMultimodal) {
                            CircularProgressIndicator(color = MonochromeUi.textPrimary)
                        }
                    }
                    SupportStatusRow(title = stringResource(R.string.provider_support_rule), state = multimodalRuleSupport)
                    SupportStatusRow(title = stringResource(R.string.provider_support_probe), state = multimodalProbeSupport)
                } else {
                    Text(
                        text = when (capability) {
                            ProviderCapability.STT -> stringResource(R.string.provider_placeholder_stt)
                            ProviderCapability.TTS -> stringResource(R.string.provider_placeholder_tts)
                            ProviderCapability.AGENT_EXECUTOR -> stringResource(R.string.provider_placeholder_agent)
                            ProviderCapability.CHAT -> ""
                        },
                        color = MonochromeUi.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                CapabilitySwitch(stringResource(R.string.common_enabled), enabled) { enabled = it }
            }
        },
    )
}

@Composable
private fun SupportStatusRow(
    title: String,
    state: FeatureSupportState,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title)
        Text(
            text = when (state) {
                FeatureSupportState.UNKNOWN -> stringResource(R.string.provider_support_unknown)
                FeatureSupportState.SUPPORTED -> stringResource(R.string.provider_support_yes)
                FeatureSupportState.UNSUPPORTED -> stringResource(R.string.provider_support_no)
            },
            color = when (state) {
                FeatureSupportState.SUPPORTED -> Color(0xFF16A34A)
                FeatureSupportState.UNSUPPORTED -> Color(0xFFDC2626)
                FeatureSupportState.UNKNOWN -> MonochromeUi.textSecondary
            },
        )
    }
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

private fun createEmptyProvider(type: ProviderType): ProviderProfile {
    val defaultBaseUrl = when (type) {
        ProviderType.OPENAI_COMPATIBLE -> "https://api.openai.com/v1"
        ProviderType.DEEPSEEK -> "https://api.deepseek.com/v1"
        ProviderType.GEMINI -> "https://generativelanguage.googleapis.com/v1beta"
        ProviderType.OLLAMA -> "http://127.0.0.1:11434"
        ProviderType.QWEN -> "https://dashscope.aliyuncs.com/compatible-mode/v1"
        ProviderType.ZHIPU -> "https://open.bigmodel.cn/api/paas/v4"
        ProviderType.XAI -> "https://api.x.ai/v1"
        ProviderType.WHISPER_API -> "https://api.openai.com/v1"
        ProviderType.XINFERENCE_STT -> "http://127.0.0.1:9997/v1"
        ProviderType.OPENAI_TTS -> "https://api.openai.com/v1"
        ProviderType.BAILIAN_TTS -> "https://dashscope.aliyuncs.com/api/v1"
        ProviderType.MINIMAX_TTS -> "https://api.minimax.chat/v1"
        ProviderType.DIFY -> "https://api.dify.ai/v1"
        ProviderType.BAILIAN_APP -> "https://dashscope.aliyuncs.com/api/v1"
        ProviderType.ANTHROPIC,
        ProviderType.CUSTOM,
        -> ""
    }
    val defaultModel = when (type) {
        ProviderType.OPENAI_COMPATIBLE -> "gpt-4.1-mini"
        ProviderType.DEEPSEEK -> "deepseek-chat"
        ProviderType.GEMINI -> "gemini-2.5-flash"
        ProviderType.OLLAMA -> "llama3.2"
        ProviderType.QWEN -> "qwen-plus"
        ProviderType.ZHIPU -> "glm-4.5"
        ProviderType.XAI -> "grok-3-mini"
        ProviderType.WHISPER_API -> "whisper-1"
        ProviderType.XINFERENCE_STT -> "whisper-large-v3"
        ProviderType.OPENAI_TTS -> "gpt-4o-mini-tts"
        ProviderType.BAILIAN_TTS -> "cosyvoice-v1"
        ProviderType.MINIMAX_TTS -> "speech-01"
        ProviderType.DIFY -> "chat-app"
        ProviderType.BAILIAN_APP -> "application-id"
        ProviderType.ANTHROPIC,
        ProviderType.CUSTOM,
        -> ""
    }
    return ProviderProfile(
        id = "",
        name = type.providerTypeDisplayLabel(),
        baseUrl = defaultBaseUrl,
        model = defaultModel,
        providerType = type,
        apiKey = "",
        capabilities = setOf(type.defaultCapability()),
        enabled = true,
        multimodalRuleSupport = inferMultimodalRuleSupport(type, defaultModel),
    )
}
