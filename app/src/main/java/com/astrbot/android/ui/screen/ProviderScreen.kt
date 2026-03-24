package com.astrbot.android.ui.screen

import android.media.MediaPlayer
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
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
import androidx.compose.runtime.LaunchedEffect
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
import com.astrbot.android.data.ConfigRepository
import com.astrbot.android.data.RuntimeAssetRepository
import com.astrbot.android.data.SherpaOnnxBridge
import com.astrbot.android.data.TtsVoiceAssetRepository
import com.astrbot.android.data.TtsVoiceCatalog
import com.astrbot.android.model.FeatureSupportState
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ProviderType
import com.astrbot.android.model.defaultCapability
import com.astrbot.android.model.displayLabel
import com.astrbot.android.model.displayLabel as providerTypeDisplayLabel
import com.astrbot.android.model.inferVoiceCloningRuleSupport
import com.astrbot.android.model.inferNativeStreamingRuleSupport
import com.astrbot.android.model.inferMultimodalRuleSupport
import com.astrbot.android.model.isVisibleInCatalog
import com.astrbot.android.model.isLocalOnDeviceProvider
import com.astrbot.android.model.supportsMultimodalCheck
import com.astrbot.android.model.supportsNativeStreamingCheck
import com.astrbot.android.model.supportsPullModels
import com.astrbot.android.model.visibleProviderTypesFor
import com.astrbot.android.ui.MonochromeUi
import com.astrbot.android.ui.monochromeOutlinedTextFieldColors
import com.astrbot.android.ui.monochromeSwitchColors
import com.astrbot.android.ui.viewmodel.ProviderViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Base64

@Composable
fun ProviderScreen(
    providerViewModel: ProviderViewModel = viewModel(),
    onBack: (() -> Unit)? = null,
    onOpenOnDeviceTtsAssets: (() -> Unit)? = null,
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
            onOpenOnDeviceTtsAssets = onOpenOnDeviceTtsAssets,
        )
    }
}

@Composable
internal fun ProviderCatalogContent(
    providerViewModel: ProviderViewModel,
    onSwitchToBots: () -> Unit,
    showBack: Boolean,
    showHeader: Boolean = true,
    onOpenOnDeviceTtsAssets: (() -> Unit)? = null,
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
    var isCheckingNativeStreaming by remember { mutableStateOf(false) }
    var isCheckingStt by remember { mutableStateOf(false) }
    var isCheckingTts by remember { mutableStateOf(false) }
    var showImageHelp by remember { mutableStateOf(false) }

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

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.End,
        ) {
            FloatingActionButton(
                onClick = { showImageHelp = true },
                modifier = Modifier.size(50.dp),
                containerColor = MonochromeUi.cardBackground,
                contentColor = MonochromeUi.textPrimary,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.HelpOutline,
                    contentDescription = stringResource(R.string.provider_image_help_title),
                )
            }
            FloatingActionButton(
                onClick = { pendingNewCapability = selectedCapability },
                containerColor = MonochromeUi.fabBackground,
                contentColor = MonochromeUi.fabContent,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.provider_add))
            }
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
            isCheckingNativeStreaming = isCheckingNativeStreaming,
            isCheckingStt = isCheckingStt,
            isCheckingTts = isCheckingTts,
            onDismiss = { editingProvider = null },
            onDelete = {
                if (provider.id.isNotBlank()) {
                    providerViewModel.delete(provider.id)
                }
                editingProvider = null
            },
            onPersistProbeSupport = { id, probeSupport ->
                providerViewModel.updateMultimodalProbeSupport(id, probeSupport)
            },
            onPersistNativeStreamingProbeSupport = { id, probeSupport ->
                providerViewModel.updateNativeStreamingProbeSupport(id, probeSupport)
            },
            onPersistSttProbeSupport = { id, probeSupport ->
                providerViewModel.updateSttProbeSupport(id, probeSupport)
            },
            onPersistTtsProbeSupport = { id, probeSupport ->
                providerViewModel.updateTtsProbeSupport(id, probeSupport)
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
            onCheckNativeStreaming = { current, onResult ->
                scope.launch {
                    isCheckingNativeStreaming = true
                    val ruleResult = ChatCompletionService.detectNativeStreamingRule(current)
                    val probeResult = runCatching {
                        withContext(Dispatchers.IO) {
                            ChatCompletionService.probeNativeStreamingSupport(current)
                        }
                    }.getOrElse {
                        Toast.makeText(context, it.message ?: it.javaClass.simpleName, Toast.LENGTH_LONG).show()
                        FeatureSupportState.UNKNOWN
                    }
                    onResult(ruleResult, probeResult)
                    isCheckingNativeStreaming = false
                }
            },
            onCheckStt = { current, onResult ->
                scope.launch {
                    isCheckingStt = true
                    val probeResult = runCatching {
                        withContext(Dispatchers.IO) {
                            ChatCompletionService.probeSttSupport(current)
                        }
                    }.getOrElse {
                        Toast.makeText(context, it.message ?: it.javaClass.simpleName, Toast.LENGTH_LONG).show()
                        ChatCompletionService.SttProbeResult(
                            state = FeatureSupportState.UNKNOWN,
                            transcript = "",
                        )
                    }
                    onResult(probeResult.state, probeResult.transcript)
                    isCheckingStt = false
                }
            },
            onCheckTts = { current, onResult ->
                scope.launch {
                    isCheckingTts = true
                    val probeResult = runCatching {
                        withContext(Dispatchers.IO) {
                            ChatCompletionService.probeTtsSupport(current)
                        }
                    }.getOrElse {
                        Toast.makeText(context, it.message ?: it.javaClass.simpleName, Toast.LENGTH_LONG).show()
                        FeatureSupportState.UNKNOWN
                    }
                    onResult(probeResult)
                    isCheckingTts = false
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
                    nativeStreamingRuleSupport = profile.nativeStreamingRuleSupport,
                    nativeStreamingProbeSupport = profile.nativeStreamingProbeSupport,
                    sttProbeSupport = profile.sttProbeSupport,
                    ttsProbeSupport = profile.ttsProbeSupport,
                    ttsVoiceOptions = profile.ttsVoiceOptions,
                )
                Toast.makeText(context, context.getString(R.string.common_saved), Toast.LENGTH_SHORT).show()
                editingProvider = null
            },
            onOpenOnDeviceTtsAssets = onOpenOnDeviceTtsAssets,
        )
    }

    if (showImageHelp) {
        AlertDialog(
            onDismissRequest = { showImageHelp = false },
            containerColor = MonochromeUi.cardBackground,
            titleContentColor = MonochromeUi.textPrimary,
            textContentColor = MonochromeUi.textPrimary,
            confirmButton = {
                TextButton(
                    onClick = { showImageHelp = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MonochromeUi.textPrimary),
                ) {
                    Text(stringResource(R.string.common_close))
                }
            },
            title = { Text(stringResource(R.string.provider_image_help_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProviderHelpBlock(
                        title = stringResource(R.string.provider_image_help_estimated_title),
                        body = stringResource(R.string.provider_image_help_estimated_body),
                    )
                    ProviderHelpBlock(
                        title = stringResource(R.string.provider_image_help_tested_title),
                        body = stringResource(R.string.provider_image_help_tested_body),
                    )
                    ProviderHelpBlock(
                        title = stringResource(R.string.provider_image_help_fallback_title),
                        body = stringResource(R.string.provider_image_help_fallback_body),
                    )
                }
            },
        )
    }
}

@Composable
private fun ProviderHelpBlock(
    title: String,
    body: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MonochromeUi.textPrimary,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MonochromeUi.textSecondary,
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
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(provider.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    text = provider.model.ifBlank { stringResource(R.string.bot_not_set) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildList {
                        add(provider.providerType.providerTypeDisplayLabel())
                        add(provider.capabilities.joinToString(" | ") { it.displayLabel() })
                    }.joinToString(" | "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MonochromeUi.textSecondary,
                    maxLines = 2,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProviderEditorDialog(
    initialProvider: ProviderProfile,
    fetchedModels: List<String>,
    isFetchingModels: Boolean,
    isCheckingMultimodal: Boolean,
    isCheckingNativeStreaming: Boolean,
    isCheckingStt: Boolean,
    isCheckingTts: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onPersistProbeSupport: (String, FeatureSupportState) -> Unit,
    onPersistNativeStreamingProbeSupport: (String, FeatureSupportState) -> Unit,
    onPersistSttProbeSupport: (String, FeatureSupportState) -> Unit,
    onPersistTtsProbeSupport: (String, FeatureSupportState) -> Unit,
    onFetchModels: (ProviderProfile) -> Unit,
    onCheckMultimodal: (ProviderProfile, (FeatureSupportState, FeatureSupportState) -> Unit) -> Unit,
    onCheckNativeStreaming: (ProviderProfile, (FeatureSupportState, FeatureSupportState) -> Unit) -> Unit,
    onCheckStt: (ProviderProfile, (FeatureSupportState, String) -> Unit) -> Unit,
    onCheckTts: (ProviderProfile, (FeatureSupportState) -> Unit) -> Unit,
    onSave: (ProviderProfile) -> Unit,
    onOpenOnDeviceTtsAssets: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configProfiles by ConfigRepository.profiles.collectAsState()
    val selectedConfigId by ConfigRepository.selectedProfileId.collectAsState()
    var showDeleteConfirm by remember(initialProvider.id) { mutableStateOf(false) }
    var name by remember(initialProvider.id) { mutableStateOf(initialProvider.name) }
    var baseUrl by remember(initialProvider.id) { mutableStateOf(initialProvider.baseUrl) }
    var model by remember(initialProvider.id) { mutableStateOf(initialProvider.model) }
    var apiKey by remember(initialProvider.id) { mutableStateOf(initialProvider.apiKey) }
    var providerType by remember(initialProvider.id) { mutableStateOf(initialProvider.providerType) }
    var enabled by remember(initialProvider.id) { mutableStateOf(initialProvider.enabled) }
    var multimodalRuleSupport by remember(initialProvider.id) { mutableStateOf(initialProvider.multimodalRuleSupport) }
    var multimodalProbeSupport by remember(initialProvider.id) { mutableStateOf(initialProvider.multimodalProbeSupport) }
    var nativeStreamingRuleSupport by remember(initialProvider.id) { mutableStateOf(initialProvider.nativeStreamingRuleSupport) }
    var nativeStreamingProbeSupport by remember(initialProvider.id) { mutableStateOf(initialProvider.nativeStreamingProbeSupport) }
    var sttProbeSupport by remember(initialProvider.id) { mutableStateOf(initialProvider.sttProbeSupport) }
    var ttsProbeSupport by remember(initialProvider.id) { mutableStateOf(initialProvider.ttsProbeSupport) }
    var ttsVoiceOptionsText by remember(initialProvider.id) { mutableStateOf(initialProvider.ttsVoiceOptions.joinToString("\n")) }
    var draftLocalTtsVoiceId by remember(initialProvider.id) {
        mutableStateOf(initialProvider.ttsVoiceOptions.firstOrNull().orEmpty())
    }
    var sttProbePreview by remember(initialProvider.id) { mutableStateOf("") }
    var isPreviewingVoice by remember(initialProvider.id) { mutableStateOf(false) }
    val initialProbeFingerprint = remember(initialProvider.id) { probeFingerprint(initialProvider) }
    var lastProbeFingerprint by remember(initialProvider.id) { mutableStateOf(initialProbeFingerprint) }
    val currentProbeFingerprint = remember(providerType, baseUrl, model, apiKey) {
        probeFingerprint(
            providerType = providerType,
            baseUrl = baseUrl,
            model = model,
            apiKey = apiKey,
        )
    }
    val displayedProbeSupport = if (currentProbeFingerprint == lastProbeFingerprint) {
        multimodalProbeSupport
    } else {
        FeatureSupportState.UNKNOWN
    }
    val displayedNativeStreamingProbeSupport = if (currentProbeFingerprint == lastProbeFingerprint) {
        nativeStreamingProbeSupport
    } else {
        FeatureSupportState.UNKNOWN
    }
    val displayedSttProbeSupport = if (currentProbeFingerprint == lastProbeFingerprint) {
        sttProbeSupport
    } else {
        FeatureSupportState.UNKNOWN
    }
    val displayedSttProbePreview = if (currentProbeFingerprint == lastProbeFingerprint) {
        sttProbePreview
    } else {
        ""
    }
    val displayedTtsProbeSupport = if (currentProbeFingerprint == lastProbeFingerprint) {
        ttsProbeSupport
    } else {
        FeatureSupportState.UNKNOWN
    }
    val ttsAssetState = remember(providerType) {
        if (providerType == ProviderType.SHERPA_ONNX_TTS) {
            RuntimeAssetRepository.ttsAssetState(context)
        } else {
            null
        }
    }
    val downloadedLocalTtsModelOptions = remember(ttsAssetState) {
        buildList {
            if (ttsAssetState?.kokoro?.installed == true) add("kokoro" to "kokoro")
        }
    }
    val suggestedTtsVoiceOptions = remember(providerType, model) {
        TtsVoiceCatalog.optionsFor(
            ProviderProfile(
                id = initialProvider.id,
                name = name,
                baseUrl = baseUrl,
                model = model,
                providerType = providerType,
                apiKey = apiKey,
                capabilities = setOf(providerType.defaultCapability()),
            ),
        ).filter { it.first.isNotBlank() }
    }

    val capability = providerType.defaultCapability()
    val providerOptions = visibleProviderTypesFor(capability).map { it.name to it.providerTypeDisplayLabel() }
    val isLocalProvider = providerType.isLocalOnDeviceProvider()
    val isLocalTtsProvider = providerType == ProviderType.SHERPA_ONNX_TTS
    val isLocalSttProvider = providerType == ProviderType.SHERPA_ONNX_STT
    val selectedConfig = remember(configProfiles, selectedConfigId) {
        configProfiles.firstOrNull { it.id == selectedConfigId }
    }
    val currentProviderProjection = remember(
        initialProvider.id,
        name,
        baseUrl,
        model,
        providerType,
        apiKey,
        enabled,
    ) {
        initialProvider.copy(
            id = initialProvider.id,
            name = name.trim().ifBlank { providerType.providerTypeDisplayLabel() },
            baseUrl = baseUrl.trim(),
            model = model.trim(),
            providerType = providerType,
            apiKey = apiKey.trim(),
            capabilities = setOf(providerType.defaultCapability()),
            enabled = enabled,
        )
    }
    val cloudVoiceOptions = remember(currentProviderProjection, selectedConfig?.id) {
        TtsVoiceAssetRepository.listVoiceChoicesFor(currentProviderProjection)
    }
    val allTtsVoiceOptions = remember(currentProviderProjection, cloudVoiceOptions) {
        buildList<Pair<String, String>> {
            val builtInVoiceOptions = TtsVoiceCatalog.optionsFor(currentProviderProjection)
            val preferClonedVoicesFirst = currentProviderProjection.providerType == ProviderType.BAILIAN_TTS &&
                currentProviderProjection.model.trim().lowercase().contains("-vc")
            val primaryOptions = if (preferClonedVoicesFirst) cloudVoiceOptions else builtInVoiceOptions
            val secondaryOptions = if (preferClonedVoicesFirst) builtInVoiceOptions else cloudVoiceOptions
            addAll(primaryOptions)
            val seen = mutableSetOf<String>().apply {
                this@buildList.forEach { (voiceId, _) -> add(voiceId) }
            }
            secondaryOptions.forEach { option ->
                if (seen.add(option.first)) add(option)
            }
        }
    }
    val canSyncTtsVoice = capability == ProviderCapability.TTS &&
        initialProvider.id.isNotBlank() &&
        selectedConfig != null
    val projectedVoiceId = remember(selectedConfig?.id, selectedConfig?.ttsVoiceId, allTtsVoiceOptions) {
        val configVoice = selectedConfig?.ttsVoiceId.orEmpty()
        when {
            !canSyncTtsVoice -> allTtsVoiceOptions.firstOrNull()?.first.orEmpty()
            configVoice.isBlank() -> allTtsVoiceOptions.firstOrNull()?.first.orEmpty()
            allTtsVoiceOptions.any { it.first == configVoice } -> configVoice
            else -> allTtsVoiceOptions.firstOrNull()?.first.orEmpty()
        }
    }
    val editorTtsVoiceId = remember(canSyncTtsVoice, projectedVoiceId, draftLocalTtsVoiceId, allTtsVoiceOptions) {
        when {
            canSyncTtsVoice -> projectedVoiceId
            draftLocalTtsVoiceId.isNotBlank() && allTtsVoiceOptions.any { it.first == draftLocalTtsVoiceId } -> draftLocalTtsVoiceId
            else -> allTtsVoiceOptions.firstOrNull()?.first.orEmpty()
        }
    }
    val localModelOptions = when (providerType) {
        ProviderType.SHERPA_ONNX_TTS -> downloadedLocalTtsModelOptions
        ProviderType.SHERPA_ONNX_STT -> listOf(
            "sherpa-stt" to "sherpa-onnx 端侧 STT",
        )
        else -> emptyList()
    }
    val localSttModelReady = isLocalSttProvider &&
        model in localModelOptions.map { it.first } &&
        SherpaOnnxBridge.isFrameworkReady() &&
        SherpaOnnxBridge.isSttReady()
    val localTtsModelMissing = isLocalTtsProvider && model !in localModelOptions.map { it.first }
    val displayedVoiceCloneRuleSupport = remember(providerType, model) {
        inferVoiceCloningRuleSupport(providerType, model)
    }
    LaunchedEffect(providerType, model, suggestedTtsVoiceOptions) {
        if (isLocalTtsProvider && ttsVoiceOptionsText.isBlank()) {
            ttsVoiceOptionsText = suggestedTtsVoiceOptions.firstOrNull()?.first.orEmpty()
        }
    }
    LaunchedEffect(isLocalTtsProvider, canSyncTtsVoice, allTtsVoiceOptions) {
        if (!isLocalTtsProvider || canSyncTtsVoice) return@LaunchedEffect
        val firstAvailableVoice = allTtsVoiceOptions.firstOrNull()?.first.orEmpty()
        if (draftLocalTtsVoiceId.isBlank() || allTtsVoiceOptions.none { it.first == draftLocalTtsVoiceId }) {
            draftLocalTtsVoiceId = firstAvailableVoice
        }
        if (ttsVoiceOptionsText.isBlank()) {
            ttsVoiceOptionsText = firstAvailableVoice
        }
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
                if (initialProvider.id.isNotBlank()) {
                    TextButton(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFB42318)),
                    ) {
                        Text(stringResource(R.string.common_delete))
                    }
                } else {
                    Spacer(modifier = Modifier.size(1.dp))
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
                                    multimodalProbeSupport = displayedProbeSupport,
                                    nativeStreamingRuleSupport = nativeStreamingRuleSupport,
                                    nativeStreamingProbeSupport = displayedNativeStreamingProbeSupport,
                                    sttProbeSupport = displayedSttProbeSupport,
                                    ttsProbeSupport = displayedTtsProbeSupport,
                                    ttsVoiceOptions = if (providerType == ProviderType.SHERPA_ONNX_TTS) {
                                        draftLocalTtsVoiceId.takeIf { it.isNotBlank() }?.let(::listOf).orEmpty()
                                    } else {
                                        emptyList()
                                    },
                                ),
                            )
                        },
                    ) {
                        Text(stringResource(R.string.common_save))
                    }
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
                    .heightIn(max = 420.dp)
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
                        if (providerType == ProviderType.SHERPA_ONNX_TTS && model !in downloadedLocalTtsModelOptions.map { it.first }) {
                            model = downloadedLocalTtsModelOptions.firstOrNull()?.first.orEmpty()
                        }
                        multimodalRuleSupport = inferMultimodalRuleSupport(providerType, model)
                        nativeStreamingRuleSupport = inferNativeStreamingRuleSupport(providerType, model)
                    },
                )
                if (!isLocalProvider) {
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
                            nativeStreamingRuleSupport = inferNativeStreamingRuleSupport(providerType, it)
                        },
                        label = { Text(stringResource(R.string.provider_field_model)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = monochromeOutlinedTextFieldColors(),
                    )
                } else {
                    if (isLocalTtsProvider && localModelOptions.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MonochromeUi.inputBackground,
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.provider_field_model),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MonochromeUi.textPrimary,
                                )
                                Text(
                                    text = stringResource(R.string.provider_local_tts_asset_required),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MonochromeUi.textSecondary,
                                )
                                OutlinedButton(
                                    onClick = { onOpenOnDeviceTtsAssets?.invoke() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MonochromeUi.textPrimary),
                                ) {
                                    Text(stringResource(R.string.provider_open_on_device_tts_assets))
                                }
                            }
                        }
                    } else {
                        SelectionField(
                            title = stringResource(R.string.provider_field_model),
                            options = localModelOptions,
                            selectedId = model,
                            onSelect = {
                                model = it
                                if (isLocalTtsProvider) {
                                    val firstVoice = TtsVoiceCatalog.optionsFor(
                                        initialProvider.copy(
                                            model = it,
                                            providerType = providerType,
                                        ),
                                    ).firstOrNull { option -> option.first.isNotBlank() }?.first.orEmpty()
                                    ttsVoiceOptionsText = firstVoice
                                    draftLocalTtsVoiceId = firstVoice
                                }
                            },
                        )
                        if (isLocalSttProvider) {
                            Text(
                                text = stringResource(R.string.provider_local_stt_model_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MonochromeUi.textSecondary,
                            )
                        }
                    }
                }
                if (providerType.supportsPullModels()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MonochromeUi.textPrimary),
                        ) {
                            Text(stringResource(R.string.provider_pull_models))
                        }
                        if (isFetchingModels) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                CircularProgressIndicator(color = MonochromeUi.textPrimary)
                            }
                        }
                    }
                }
                if (fetchedModels.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        fetchedModels.take(10).forEach { item ->
                            AssistChip(
                                onClick = {
                                    model = item
                                    multimodalRuleSupport = inferMultimodalRuleSupport(providerType, item)
                                    nativeStreamingRuleSupport = inferNativeStreamingRuleSupport(providerType, item)
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
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MonochromeUi.inputBackground,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
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
                                        lastProbeFingerprint = currentProbeFingerprint
                                        if (initialProvider.id.isNotBlank() && currentProbeFingerprint == initialProbeFingerprint) {
                                            onPersistProbeSupport(initialProvider.id, probe)
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MonochromeUi.textPrimary),
                            ) {
                                Text(stringResource(R.string.provider_check_multimodal))
                            }
                            if (isCheckingMultimodal) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    CircularProgressIndicator(color = MonochromeUi.textPrimary)
                                }
                            }
                            SupportStatusRow(title = stringResource(R.string.provider_support_rule), state = multimodalRuleSupport)
                            SupportStatusRow(title = stringResource(R.string.provider_support_probe), state = displayedProbeSupport)
                            if (providerType.supportsNativeStreamingCheck()) {
                                OutlinedButton(
                                    onClick = {
                                        onCheckNativeStreaming(
                                            initialProvider.copy(
                                                baseUrl = baseUrl,
                                                apiKey = apiKey,
                                                model = model,
                                                providerType = providerType,
                                                capabilities = setOf(providerType.defaultCapability()),
                                            ),
                                        ) { rule, probe ->
                                            nativeStreamingRuleSupport = rule
                                            nativeStreamingProbeSupport = probe
                                            lastProbeFingerprint = currentProbeFingerprint
                                            if (initialProvider.id.isNotBlank() && currentProbeFingerprint == initialProbeFingerprint) {
                                                onPersistNativeStreamingProbeSupport(initialProvider.id, probe)
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MonochromeUi.textPrimary),
                                ) {
                                    Text(stringResource(R.string.provider_check_native_streaming))
                                }
                                if (isCheckingNativeStreaming) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                    ) {
                                        CircularProgressIndicator(color = MonochromeUi.textPrimary)
                                    }
                                }
                                SupportStatusRow(
                                    title = stringResource(R.string.provider_support_native_streaming_rule),
                                    state = nativeStreamingRuleSupport,
                                )
                                SupportStatusRow(
                                    title = stringResource(R.string.provider_support_native_streaming_probe),
                                    state = displayedNativeStreamingProbeSupport,
                                )
                            }
                        }
                    }
                } else if (capability == ProviderCapability.STT) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MonochromeUi.inputBackground,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                stringResource(R.string.provider_capability_check_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MonochromeUi.textPrimary,
                            )
                            if (!isLocalSttProvider || localSttModelReady) {
                                OutlinedButton(
                                    onClick = {
                                        onCheckStt(
                                            initialProvider.copy(
                                                baseUrl = baseUrl,
                                                apiKey = apiKey,
                                                model = model,
                                                providerType = providerType,
                                                capabilities = setOf(providerType.defaultCapability()),
                                            ),
                                        ) { probe, transcript ->
                                            sttProbeSupport = probe
                                            sttProbePreview = transcript
                                            lastProbeFingerprint = currentProbeFingerprint
                                            if (initialProvider.id.isNotBlank() && currentProbeFingerprint == initialProbeFingerprint) {
                                                onPersistSttProbeSupport(initialProvider.id, probe)
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MonochromeUi.textPrimary),
                                ) {
                                    Text(stringResource(R.string.provider_check_stt))
                                }
                                if (isCheckingStt) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                    ) {
                                        CircularProgressIndicator(color = MonochromeUi.textPrimary)
                                    }
                                }
                            }
                            val sttDescription = if (isLocalProvider) {
                                if (localSttModelReady) {
                                    ""
                                } else {
                                    stringResource(R.string.provider_local_stt_asset_required)
                                }
                            } else {
                                stringResource(R.string.provider_placeholder_stt)
                            }
                            if (sttDescription.isNotBlank()) {
                                Text(
                                    text = sttDescription,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MonochromeUi.textSecondary,
                                )
                            }
                            SupportStatusRow(title = stringResource(R.string.provider_support_stt_probe), state = displayedSttProbeSupport)
                            if (displayedSttProbePreview.isNotBlank()) {
                                Text(
                                    text = stringResource(R.string.provider_stt_preview, displayedSttProbePreview),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MonochromeUi.textSecondary,
                                )
                            }
                        }
                    }
                } else if (capability == ProviderCapability.TTS) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MonochromeUi.inputBackground,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                stringResource(
                                    if (isLocalTtsProvider) {
                                        R.string.provider_voice_preview_title
                                    } else {
                                        R.string.provider_capability_check_title
                                    },
                                ),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MonochromeUi.textPrimary,
                            )
                            if (!isLocalProvider) {
                                OutlinedButton(
                                    onClick = {
                                        onCheckTts(
                                            initialProvider.copy(
                                                baseUrl = baseUrl,
                                                apiKey = apiKey,
                                                model = model,
                                                providerType = providerType,
                                                capabilities = setOf(providerType.defaultCapability()),
                                            ),
                                        ) { probe ->
                                            ttsProbeSupport = probe
                                            lastProbeFingerprint = currentProbeFingerprint
                                            if (initialProvider.id.isNotBlank() && currentProbeFingerprint == initialProbeFingerprint) {
                                                onPersistTtsProbeSupport(initialProvider.id, probe)
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MonochromeUi.textPrimary),
                                ) {
                                    Text(stringResource(R.string.provider_check_tts))
                                }
                                if (isCheckingTts) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                    ) {
                                        CircularProgressIndicator(color = MonochromeUi.textPrimary)
                                    }
                                }
                            }
                            if (isLocalProvider) {
                                if (!localTtsModelMissing && allTtsVoiceOptions.isNotEmpty()) {
                                    if (canSyncTtsVoice) {
                                        SelectionField(
                                            title = stringResource(R.string.provider_tts_voice_selection_field),
                                            options = allTtsVoiceOptions,
                                            selectedId = editorTtsVoiceId,
                                            onSelect = { selection ->
                                                val config = selectedConfig ?: return@SelectionField
                                                ConfigRepository.save(
                                                    config.copy(
                                                        defaultTtsProviderId = initialProvider.id,
                                                        ttsVoiceId = selection,
                                                    ),
                                                )
                                            },
                                        )
                                    } else {
                                        SelectionField(
                                            title = stringResource(R.string.provider_tts_voice_selection_field),
                                            options = allTtsVoiceOptions,
                                            selectedId = editorTtsVoiceId,
                                            onSelect = { selection ->
                                                draftLocalTtsVoiceId = selection
                                                ttsVoiceOptionsText = selection
                                            },
                                        )
                                    }
                                }
                                Text(
                                    text = stringResource(R.string.provider_voice_preview_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MonochromeUi.textSecondary,
                                )
                                OutlinedButton(
                                    onClick = {
                                        val selectedVoiceId = editorTtsVoiceId
                                        val previewProvider = initialProvider.copy(
                                            name = name.trim().ifBlank { providerType.providerTypeDisplayLabel() },
                                            baseUrl = baseUrl.trim(),
                                            model = model.trim(),
                                            apiKey = apiKey.trim(),
                                            providerType = providerType,
                                            capabilities = setOf(providerType.defaultCapability()),
                                            enabled = enabled,
                                            ttsVoiceOptions = selectedVoiceId.takeIf { it.isNotBlank() }?.let(::listOf).orEmpty(),
                                        )
                                        scope.launch {
                                            isPreviewingVoice = true
                                            val result = runCatching {
                                                withContext(Dispatchers.IO) {
                                                    ChatCompletionService.synthesizeSpeech(
                                                        provider = previewProvider,
                                                        text = "你好世界",
                                                        voiceId = selectedVoiceId,
                                                        readBracketedContent = true,
                                                    )
                                                }
                                            }
                                            result.onSuccess { attachment ->
                                                runCatching { playPreviewAttachment(context, attachment) }
                                                    .onFailure { error ->
                                                        Toast.makeText(
                                                            context,
                                                            error.message ?: error.javaClass.simpleName,
                                                            Toast.LENGTH_LONG,
                                                        ).show()
                                                    }
                                            }.onFailure { error ->
                                                Toast.makeText(
                                                    context,
                                                    error.message ?: error.javaClass.simpleName,
                                                    Toast.LENGTH_LONG,
                                                ).show()
                                            }
                                            isPreviewingVoice = false
                                        }
                                    },
                                    enabled = !isPreviewingVoice && !localTtsModelMissing && editorTtsVoiceId.isNotBlank(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MonochromeUi.textPrimary),
                                ) {
                                    Text(stringResource(R.string.provider_voice_preview_action))
                                }
                                if (isPreviewingVoice) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                    ) {
                                        CircularProgressIndicator(color = MonochromeUi.textPrimary)
                                    }
                                }
                            } else {
                                SupportStatusRow(title = stringResource(R.string.provider_support_tts_probe), state = displayedTtsProbeSupport)
                                SupportStatusRow(
                                    title = stringResource(R.string.provider_support_voice_clone_rule),
                                    state = displayedVoiceCloneRuleSupport,
                                )
                                if (allTtsVoiceOptions.isNotEmpty()) {
                                    if (canSyncTtsVoice) {
                                        SelectionField(
                                            title = stringResource(R.string.provider_tts_voice_selection_field),
                                            options = allTtsVoiceOptions,
                                            selectedId = projectedVoiceId,
                                            onSelect = { selection ->
                                                val config = selectedConfig ?: return@SelectionField
                                                ConfigRepository.save(
                                                    config.copy(
                                                        defaultTtsProviderId = initialProvider.id,
                                                        ttsVoiceId = selection,
                                                    ),
                                                )
                                            },
                                        )
                                    } else {
                                        ReadOnlySelectionField(
                                            title = stringResource(R.string.provider_tts_voice_selection_field),
                                            options = allTtsVoiceOptions,
                                            selectedId = projectedVoiceId,
                                        )
                                    }
                                }
                                if (!canSyncTtsVoice) {
                                    InlineProviderNotice(
                                        text = stringResource(R.string.provider_cloud_tts_voice_projection_notice),
                                    )
                                }
                                if (displayedVoiceCloneRuleSupport != FeatureSupportState.SUPPORTED) {
                                    Text(
                                        text = stringResource(R.string.provider_tts_voice_clone_unsupported_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MonochromeUi.textSecondary,
                                    )
                                }
                            }
                        }
                    }
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

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = MonochromeUi.cardBackground,
            titleContentColor = MonochromeUi.textPrimary,
            textContentColor = MonochromeUi.textSecondary,
            title = { Text(stringResource(R.string.common_delete)) },
            text = { Text(stringResource(R.string.provider_delete_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFB42318)),
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

@Composable
private fun InlineProviderNotice(
    text: String,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MonochromeUi.inputBackground,
    ) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MonochromeUi.textSecondary,
        )
    }
}

@Composable
private fun ReadOnlySelectionField(
    title: String,
    options: List<Pair<String, String>>,
    selectedId: String,
) {
    val selectedLabel = options.firstOrNull { it.first == selectedId }?.second
        ?: options.firstOrNull()?.second
        ?: stringResource(R.string.common_not_selected)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelSmall)
        Surface(
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
                Text(
                    text = selectedLabel,
                    color = MonochromeUi.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MonochromeUi.textSecondary,
                )
            }
        }
    }
}

internal fun playPreviewAttachment(
    context: android.content.Context,
    attachment: com.astrbot.android.model.ConversationAttachment,
) {
    val base64 = attachment.base64Data.takeIf { it.isNotBlank() }
        ?: throw IllegalStateException("Preview audio is empty.")
    val audioFile = File(context.cacheDir, "tts-preview-${System.currentTimeMillis()}.wav")
    audioFile.writeBytes(Base64.getDecoder().decode(base64))
    val player = MediaPlayer().apply {
        setDataSource(audioFile.absolutePath)
        prepare()
    }
    player.setOnCompletionListener {
        it.release()
        audioFile.delete()
    }
    player.setOnErrorListener { mp, _, _ ->
        mp.release()
        audioFile.delete()
        false
    }
    player.start()
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
        ProviderType.BAILIAN_STT -> "https://dashscope.aliyuncs.com/api/v1"
        ProviderType.SHERPA_ONNX_STT -> ""
        ProviderType.OPENAI_TTS -> "https://api.openai.com/v1"
        ProviderType.BAILIAN_TTS -> "https://dashscope.aliyuncs.com/api/v1"
        ProviderType.MINIMAX_TTS -> "https://api.minimax.chat/v1"
        ProviderType.SHERPA_ONNX_TTS -> ""
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
        ProviderType.BAILIAN_STT -> "qwen3-asr-flash"
        ProviderType.SHERPA_ONNX_STT -> "sherpa-stt"
        ProviderType.OPENAI_TTS -> "gpt-4o-mini-tts"
        ProviderType.BAILIAN_TTS -> "cosyvoice-v1"
        ProviderType.MINIMAX_TTS -> "speech-01"
        ProviderType.SHERPA_ONNX_TTS -> "kokoro"
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
        nativeStreamingRuleSupport = inferNativeStreamingRuleSupport(type, defaultModel),
    )
}

private fun probeFingerprint(provider: ProviderProfile): String {
    return probeFingerprint(
        providerType = provider.providerType,
        baseUrl = provider.baseUrl,
        model = provider.model,
        apiKey = provider.apiKey,
    )
}

private fun probeFingerprint(
    providerType: ProviderType,
    baseUrl: String,
    model: String,
    apiKey: String,
): String {
    return listOf(
        providerType.name,
        baseUrl.trim(),
        model.trim(),
        apiKey.trim(),
    ).joinToString(separator = "|")
}
