package com.astrbot.android.ui.screen

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astrbot.android.R
import com.astrbot.android.data.ChatCompletionService
import com.astrbot.android.data.TtsVoiceCatalog
import com.astrbot.android.data.TtsVoiceAssetRepository
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.FeatureSupportState
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.ui.MonochromeUi
import com.astrbot.android.ui.monochromeOutlinedTextFieldColors
import com.astrbot.android.ui.monochromeSwitchColors
import com.astrbot.android.ui.viewmodel.ConfigViewModel
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.Switch
import androidx.compose.foundation.text.KeyboardOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ConfigDetailScreen(
    profileId: String,
    onBack: () -> Unit,
    configViewModel: ConfigViewModel = viewModel(),
) {
    val profiles by configViewModel.configProfiles.collectAsState()
    val providers by configViewModel.providers.collectAsState()
    val ttsVoiceAssets by configViewModel.ttsVoiceAssets.collectAsState()
    val profile = profiles.firstOrNull { it.id == profileId }
    val context = LocalContext.current

    LaunchedEffect(profile) {
        if (profile == null) onBack()
    }

    if (profile == null) return

    val chatProviderOptions = providers
        .filter { it.enabled && ProviderCapability.CHAT in it.capabilities }
        .map { it.id to it.name }
    val sttProviderOptions = providers
        .filter { it.enabled && ProviderCapability.STT in it.capabilities }
        .map { it.id to it.name }
    val ttsProviderOptions = providers
        .filter { it.enabled && ProviderCapability.TTS in it.capabilities }
        .map { it.id to it.name }
    val captionProviderOptions = providers
        .filter { it.enabled && ProviderCapability.CHAT in it.capabilities && it.hasMultimodalSupport() }
        .map { it.id to it.name }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val sections = remember { ConfigSection.entries }
    val currentSection by remember(listState, sections) {
        derivedStateOf {
            val visibleSectionKeys = listState.layoutInfo.visibleItemsInfo.mapNotNull { item ->
                val key = item.key as? String ?: return@mapNotNull null
                val section = sections.firstOrNull { it.name == key } ?: return@mapNotNull null
                section to item.offset
            }

            visibleSectionKeys
                .minByOrNull { (_, offset) -> kotlin.math.abs(offset) }
                ?.first
                ?: sections.first()
        }
    }
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.widthIn(max = screenWidth * 0.5f),
                drawerContainerColor = MonochromeUi.drawerSurface,
                drawerContentColor = MonochromeUi.textPrimary,
            ) {
                Text(
                    text = stringResource(R.string.config_detail_nav_title),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MonochromeUi.textPrimary,
                )
                sections.forEach { section ->
                    val selected = section == currentSection
                    Surface(
                        onClick = {
                            scope.launch {
                                listState.animateScrollToItem(section.ordinal)
                                drawerState.close()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = if (selected) MonochromeUi.cardAltBackground else MonochromeUi.drawerSurface,
                    ) {
                        Text(
                            text = stringResource(section.titleRes),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            color = if (selected) MonochromeUi.textPrimary else MonochromeUi.textSecondary,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        )
                    }
                }
            }
        },
    ) {
        Scaffold(
            containerColor = MonochromeUi.pageBackground,
            topBar = {
                Surface(color = MonochromeUi.pageBackground) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = stringResource(R.string.common_back),
                                tint = MonochromeUi.textPrimary,
                            )
                        }
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                Icons.Outlined.Menu,
                                contentDescription = stringResource(R.string.config_detail_open_sections),
                                tint = MonochromeUi.textPrimary,
                            )
                        }
                        Text(
                            text = profile.name,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 6.dp, end = 12.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MonochromeUi.textPrimary,
                        )
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MonochromeUi.inputBackground,
                        ) {
                            Text(
                                text = stringResource(currentSection.titleRes),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MonochromeUi.textSecondary,
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            ConfigDetailContent(
                profile = profile,
                chatModelOptions = chatProviderOptions,
                sttModelOptions = sttProviderOptions,
                ttsModelOptions = ttsProviderOptions,
                captionModelOptions = captionProviderOptions,
                providers = providers,
                ttsVoiceAssets = ttsVoiceAssets,
                listState = listState,
                modifier = Modifier.padding(innerPadding),
                onSave = { updated ->
                    configViewModel.save(updated)
                    configViewModel.select(updated.id)
                    Toast.makeText(context, context.getString(R.string.common_saved), Toast.LENGTH_SHORT).show()
                },
            )
        }
    }
}

@Composable
private fun ConfigDetailContent(
    profile: ConfigProfile,
    chatModelOptions: List<Pair<String, String>>,
    sttModelOptions: List<Pair<String, String>>,
    ttsModelOptions: List<Pair<String, String>>,
    captionModelOptions: List<Pair<String, String>>,
    providers: List<ProviderProfile>,
    ttsVoiceAssets: List<com.astrbot.android.model.TtsVoiceReferenceAsset>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier,
    onSave: (ConfigProfile) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember(profile.id) { mutableStateOf(profile.name) }
    var defaultChatProviderId by remember(profile.id) { mutableStateOf(profile.defaultChatProviderId) }
    var defaultVisionProviderId by remember(profile.id) { mutableStateOf(profile.defaultVisionProviderId) }
    var defaultSttProviderId by remember(profile.id) { mutableStateOf(profile.defaultSttProviderId) }
    var defaultTtsProviderId by remember(profile.id) { mutableStateOf(profile.defaultTtsProviderId) }
    var sttEnabled by remember(profile.id) { mutableStateOf(profile.sttEnabled) }
    var ttsEnabled by remember(profile.id) { mutableStateOf(profile.ttsEnabled) }
    var alwaysTtsEnabled by remember(profile.id) { mutableStateOf(profile.alwaysTtsEnabled) }
    var ttsReadBracketedContent by remember(profile.id) { mutableStateOf(profile.ttsReadBracketedContent) }
    var textStreamingEnabled by remember(profile.id) { mutableStateOf(profile.textStreamingEnabled) }
    var voiceStreamingEnabled by remember(profile.id) { mutableStateOf(profile.voiceStreamingEnabled) }
    var streamingMessageIntervalMs by remember(profile.id) { mutableStateOf(profile.streamingMessageIntervalMs.toString()) }
    var realWorldTimeAwarenessEnabled by remember(profile.id) { mutableStateOf(profile.realWorldTimeAwarenessEnabled) }
    var imageCaptionTextEnabled by remember(profile.id) { mutableStateOf(profile.imageCaptionTextEnabled) }
    var webSearchEnabled by remember(profile.id) { mutableStateOf(profile.webSearchEnabled) }
    var proactiveEnabled by remember(profile.id) { mutableStateOf(profile.proactiveEnabled) }
    var ttsVoiceId by remember(profile.id) { mutableStateOf(profile.ttsVoiceId) }
    var isPreviewingVoice by remember(profile.id) { mutableStateOf(false) }
    var imageCaptionPrompt by remember(profile.id) { mutableStateOf(profile.imageCaptionPrompt) }
    val unnamedConfigLabel = stringResource(R.string.config_unnamed)
    val defaultChatProvider = providers.firstOrNull { it.id == defaultChatProviderId }
    val defaultTtsProvider = providers.firstOrNull { it.id == defaultTtsProviderId }
    val sttModelReady = defaultSttProviderId.isNotBlank()
    val ttsModelReady = defaultTtsProviderId.isNotBlank()
    val ttsVoiceOptions = remember(defaultTtsProvider?.id, defaultTtsProvider?.model, ttsVoiceAssets) {
        val providerOptions = TtsVoiceCatalog.optionsFor(defaultTtsProvider)
        val clonedOptions = TtsVoiceAssetRepository.listVoiceChoicesFor(defaultTtsProvider)
        buildList {
            val preferClonedVoicesFirst = defaultTtsProvider?.providerType == com.astrbot.android.model.ProviderType.BAILIAN_TTS &&
                defaultTtsProvider.model.trim().lowercase().contains("-vc")
            val primaryOptions = if (preferClonedVoicesFirst) clonedOptions else providerOptions
            val secondaryOptions = if (preferClonedVoicesFirst) providerOptions else clonedOptions
            addAll(primaryOptions)
            val existingIds = primaryOptions.map { entry -> entry.first }.toMutableSet()
            secondaryOptions.forEach { option ->
                if (existingIds.add(option.first)) {
                    add(option)
                }
            }
        }
    }
    val chatModelSupportsImages = defaultChatProvider?.hasMultimodalSupport() == true
    val needsCaptionModel = imageCaptionTextEnabled && !chatModelSupportsImages
    val missingCaptionModel = needsCaptionModel && defaultVisionProviderId.isBlank()

    LaunchedEffect(defaultTtsProvider?.id, ttsVoiceOptions) {
        if (defaultTtsProvider == null) {
            ttsVoiceId = ""
        } else if (ttsVoiceOptions.any { it.first == ttsVoiceId }) {
            Unit
        } else {
            ttsVoiceId = ttsVoiceOptions.firstOrNull()?.first.orEmpty()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MonochromeUi.pageBackground),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = ConfigSection.ModelSettings.name) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    ConfigSectionCard(
                        title = stringResource(R.string.config_section_model_settings),
                        subtitle = stringResource(R.string.config_section_model_settings_desc),
                    ) {
                        ConfigFieldGroup {
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
                                options = captionModelOptions,
                                selectedId = defaultVisionProviderId,
                                onSelect = { defaultVisionProviderId = it },
                            )
                            Text(
                                text = if (captionModelOptions.isEmpty()) {
                                    stringResource(R.string.config_caption_model_empty)
                                } else {
                                    stringResource(R.string.config_caption_model_hint)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MonochromeUi.textSecondary,
                            )
                            SelectionField(
                                title = stringResource(R.string.config_default_stt_model),
                                options = sttModelOptions,
                                selectedId = defaultSttProviderId,
                                onSelect = { defaultSttProviderId = it },
                            )
                            SelectionField(
                                title = stringResource(R.string.config_default_tts_model),
                                options = ttsModelOptions,
                                selectedId = defaultTtsProviderId,
                                onSelect = { defaultTtsProviderId = it },
                            )
                        }
                        ConfigFieldGroup {
                            ConfigToggleRow(
                                title = stringResource(R.string.config_image_caption_text_title),
                                subtitle = stringResource(R.string.config_image_caption_text_desc),
                                checked = imageCaptionTextEnabled,
                                onCheckedChange = { imageCaptionTextEnabled = it },
                            )
                            if (missingCaptionModel) {
                                InlineConfigNotice(
                                    text = stringResource(R.string.config_caption_model_required_notice),
                                )
                            } else if (imageCaptionTextEnabled && chatModelSupportsImages) {
                                InlineConfigNotice(
                                    text = stringResource(R.string.config_caption_model_multimodal_notice),
                                )
                            }
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
                    }
                    ConfigSectionCard(
                        title = stringResource(R.string.config_section_speech_settings),
                        subtitle = stringResource(R.string.config_section_speech_settings_desc),
                    ) {
                        ConfigFieldGroup {
                            ConfigToggleRow(
                                title = stringResource(R.string.config_enable_stt),
                                subtitle = stringResource(R.string.config_enable_stt_desc),
                                checked = sttEnabled,
                                enabled = sttModelReady,
                                onCheckedChange = { sttEnabled = it },
                            )
                            if (!sttModelReady) {
                                InlineConfigNotice(
                                    text = stringResource(R.string.config_stt_model_required_notice),
                                )
                            }
                            ConfigToggleRow(
                                title = stringResource(R.string.config_enable_tts),
                                subtitle = stringResource(R.string.config_enable_tts_desc),
                                checked = ttsEnabled,
                                enabled = ttsModelReady,
                                onCheckedChange = { ttsEnabled = it },
                            )
                            if (!ttsModelReady) {
                                InlineConfigNotice(
                                    text = stringResource(R.string.config_tts_model_required_notice),
                                )
                            }
                            ConfigToggleRow(
                                title = stringResource(R.string.config_always_tts_title),
                                subtitle = "",
                                checked = alwaysTtsEnabled,
                                enabled = ttsModelReady,
                                onCheckedChange = { alwaysTtsEnabled = it },
                            )
                            ConfigToggleRow(
                                title = stringResource(R.string.config_tts_read_brackets_title),
                                subtitle = stringResource(R.string.config_tts_read_brackets_desc),
                                checked = ttsReadBracketedContent,
                                enabled = ttsModelReady,
                                onCheckedChange = { ttsReadBracketedContent = it },
                            )
                            SelectionField(
                                title = stringResource(R.string.config_tts_voice_title),
                                options = ttsVoiceOptions,
                                selectedId = ttsVoiceId,
                                onSelect = { ttsVoiceId = it },
                            )
                            Text(
                                text = when {
                                    defaultTtsProvider == null -> stringResource(R.string.config_tts_voice_no_provider)
                                    ttsVoiceOptions.isEmpty() -> stringResource(R.string.config_tts_voice_no_options)
                                    else -> stringResource(R.string.config_tts_voice_hint)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MonochromeUi.textSecondary,
                            )
                            OutlinedButton(
                                onClick = {
                                    val previewProvider = defaultTtsProvider ?: return@OutlinedButton
                                    scope.launch {
                                        isPreviewingVoice = true
                                        val result = runCatching {
                                            withContext(Dispatchers.IO) {
                                                ChatCompletionService.synthesizeSpeech(
                                                    provider = previewProvider,
                                                    text = "你好世界",
                                                    voiceId = ttsVoiceId,
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
                                enabled = !isPreviewingVoice && ttsModelReady && ttsVoiceId.isNotBlank(),
                                modifier = Modifier.fillMaxWidth(),
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
                        }
                    }
                    ConfigSectionCard(
                        title = stringResource(R.string.config_section_streaming_settings),
                        subtitle = stringResource(R.string.config_section_streaming_settings_desc),
                    ) {
                        ConfigFieldGroup {
                            ConfigToggleRow(
                                title = stringResource(R.string.config_text_streaming_title),
                                subtitle = stringResource(R.string.config_text_streaming_desc),
                                checked = textStreamingEnabled,
                                onCheckedChange = { textStreamingEnabled = it },
                            )
                            ConfigToggleRow(
                                title = stringResource(R.string.config_voice_streaming_title),
                                subtitle = stringResource(R.string.config_voice_streaming_desc),
                                checked = voiceStreamingEnabled,
                                onCheckedChange = { voiceStreamingEnabled = it },
                            )
                            OutlinedTextField(
                                value = streamingMessageIntervalMs,
                                onValueChange = { value ->
                                    streamingMessageIntervalMs = value.filter { it.isDigit() }.take(4)
                                },
                                label = { Text(stringResource(R.string.config_streaming_interval_title)) },
                                supportingText = {
                                    Text(stringResource(R.string.config_streaming_interval_desc))
                                },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = monochromeOutlinedTextFieldColors(),
                            )
                        }
                    }
                    ConfigSectionCard(
                        title = stringResource(R.string.config_section_runtime_helpers),
                        subtitle = stringResource(R.string.config_section_runtime_helpers_desc),
                    ) {
                        ConfigFieldGroup {
                            ConfigToggleRow(
                                title = stringResource(R.string.config_time_awareness),
                                subtitle = stringResource(R.string.config_time_awareness_desc),
                                checked = realWorldTimeAwarenessEnabled,
                                onCheckedChange = { realWorldTimeAwarenessEnabled = it },
                            )
                            ConfigToggleRow(
                                title = stringResource(R.string.config_web_search_title),
                                subtitle = stringResource(R.string.config_web_search_desc),
                                checked = webSearchEnabled,
                                onCheckedChange = { webSearchEnabled = it },
                            )
                            ConfigToggleRow(
                                title = stringResource(R.string.config_proactive_title),
                                subtitle = stringResource(R.string.config_proactive_desc),
                                checked = proactiveEnabled,
                                onCheckedChange = { proactiveEnabled = it },
                            )
                        }
                    }
                }
            }
            item(key = ConfigSection.KnowledgeBase.name) {
                PlaceholderSectionCard(
                    title = stringResource(R.string.config_section_knowledge_base),
                    subtitle = stringResource(R.string.config_placeholder_round_three),
                )
            }
            item(key = ConfigSection.ContextStrategy.name) {
                PlaceholderSectionCard(
                    title = stringResource(R.string.config_section_context_strategy),
                    subtitle = stringResource(R.string.config_placeholder_round_four),
                )
            }
            item(key = ConfigSection.Search.name) {
                PlaceholderSectionCard(
                    title = stringResource(R.string.config_section_search),
                    subtitle = stringResource(R.string.config_placeholder_search),
                )
            }
            item(key = ConfigSection.Automation.name) {
                PlaceholderSectionCard(
                    title = stringResource(R.string.config_section_automation),
                    subtitle = stringResource(R.string.config_placeholder_automation),
                )
            }
            item(key = ConfigSection.Advanced.name) {
                PlaceholderSectionCard(
                    title = stringResource(R.string.config_section_advanced),
                    subtitle = stringResource(R.string.config_placeholder_advanced),
                )
            }
        }

        FloatingActionButton(
            onClick = {
                onSave(
                    profile.copy(
                        name = name.trim().ifBlank { unnamedConfigLabel },
                        defaultChatProviderId = defaultChatProviderId,
                        defaultVisionProviderId = defaultVisionProviderId,
                        defaultSttProviderId = defaultSttProviderId,
                        defaultTtsProviderId = defaultTtsProviderId,
                        sttEnabled = sttEnabled && sttModelReady,
                        ttsEnabled = ttsEnabled && ttsModelReady,
                        alwaysTtsEnabled = alwaysTtsEnabled && ttsModelReady,
                        ttsReadBracketedContent = ttsReadBracketedContent && ttsModelReady,
                        textStreamingEnabled = textStreamingEnabled,
                        voiceStreamingEnabled = voiceStreamingEnabled,
                        streamingMessageIntervalMs = streamingMessageIntervalMs.toIntOrNull()?.coerceIn(0, 5000) ?: profile.streamingMessageIntervalMs,
                        realWorldTimeAwarenessEnabled = realWorldTimeAwarenessEnabled,
                        imageCaptionTextEnabled = imageCaptionTextEnabled,
                        webSearchEnabled = webSearchEnabled,
                        proactiveEnabled = proactiveEnabled,
                        ttsVoiceId = ttsVoiceId.trim(),
                        imageCaptionPrompt = imageCaptionPrompt.trim(),
                    ),
                )
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 28.dp),
            containerColor = MonochromeUi.fabBackground,
            contentColor = MonochromeUi.fabContent,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
        ) {
            Icon(Icons.Outlined.Done, contentDescription = stringResource(R.string.common_save))
        }
    }
}

@Composable
private fun ConfigFieldGroup(
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MonochromeUi.inputBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun ConfigSectionCard(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MonochromeUi.cardBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MonochromeUi.textPrimary)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MonochromeUi.textSecondary,
                )
                content()
            },
        )
    }
}

@Composable
private fun PlaceholderSectionCard(
    title: String,
    subtitle: String,
) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MonochromeUi.cardBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MonochromeUi.textPrimary)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MonochromeUi.textSecondary,
            )
        }
    }
}

@Composable
private fun InlineConfigNotice(
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
private fun ConfigToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(title, fontWeight = FontWeight.SemiBold, color = MonochromeUi.textPrimary)
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MonochromeUi.textSecondary,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = monochromeSwitchColors(),
        )
    }
}

private enum class ConfigSection(val titleRes: Int) {
    ModelSettings(R.string.config_section_model_settings),
    KnowledgeBase(R.string.config_section_knowledge_base),
    ContextStrategy(R.string.config_section_context_strategy),
    Search(R.string.config_section_search),
    Automation(R.string.config_section_automation),
    Advanced(R.string.config_section_advanced),
}

private fun ProviderProfile.hasMultimodalSupport(): Boolean {
    return multimodalProbeSupport == FeatureSupportState.SUPPORTED ||
        multimodalRuleSupport == FeatureSupportState.SUPPORTED
}
