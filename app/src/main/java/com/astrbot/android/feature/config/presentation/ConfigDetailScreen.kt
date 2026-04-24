package com.astrbot.android.ui.config
import com.astrbot.android.ui.bot.SelectionField
import com.astrbot.android.ui.config.detail.ConfigDetailDrawerContent
import com.astrbot.android.ui.config.detail.ConfigFieldGroup
import com.astrbot.android.ui.config.detail.ConfigSection
import com.astrbot.android.ui.config.detail.ConfigSectionCard
import com.astrbot.android.ui.config.detail.InlineConfigNotice
import com.astrbot.android.ui.config.detail.PlaceholderSectionCard
import com.astrbot.android.ui.config.detail.configDrawerGroups
import com.astrbot.android.ui.config.detail.fields.ConfigToggleField
import com.astrbot.android.ui.config.detail.sections.AdminSettingsSection
import com.astrbot.android.ui.config.detail.sections.IgnorePermissionSettingsSection
import com.astrbot.android.ui.config.detail.sections.KeywordSettingsSection
import com.astrbot.android.ui.config.detail.sections.RateLimitSettingsSection
import com.astrbot.android.ui.config.detail.sections.ReplySettingsSection
import com.astrbot.android.ui.config.detail.sections.SessionSettingsSection
import com.astrbot.android.ui.config.detail.sections.WakeSettingsSection
import com.astrbot.android.ui.config.detail.sections.WhitelistSettingsSection

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
import androidx.hilt.navigation.compose.hiltViewModel
import com.astrbot.android.R
import com.astrbot.android.core.runtime.audio.TtsVoiceCatalog
import com.astrbot.android.core.runtime.audio.TtsVoiceAssetRepository
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.FeatureSupportState
import com.astrbot.android.model.McpServerEntry
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.SkillEntry
import com.astrbot.android.ui.common.animateToItemWithAppMotion
import com.astrbot.android.ui.app.MonochromeUi
import com.astrbot.android.ui.app.ConfigDetailChromeBinding
import com.astrbot.android.ui.app.monochromeOutlinedTextFieldColors
import com.astrbot.android.ui.app.RegisterConfigDetailChromeBinding
import com.astrbot.android.ui.app.SecondaryTopBarPlaceholder
import com.astrbot.android.ui.provider.playPreviewAttachment
import com.astrbot.android.ui.viewmodel.ConfigViewModel
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.text.KeyboardOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable
fun ConfigDetailScreen(
    profileId: String,
    onBack: () -> Unit,
    configViewModel: ConfigViewModel = hiltViewModel(),
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
    val groups = remember { configDrawerGroups() }
    val sections = remember(groups) { groups.flatMap { it.children } }
    var expandedGroupTitles by remember { mutableStateOf(emptySet<Int>()) }
    val currentSection by remember(listState, sections) {
        derivedStateOf {
            currentSectionFor(
                visibleSectionOffsets = listState.layoutInfo.visibleItemsInfo.mapNotNull { item ->
                    val key = item.key as? String ?: return@mapNotNull null
                    key to item.offset
                },
                sections = sections,
            )
        }
    }
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp

    RegisterConfigDetailChromeBinding(
        ConfigDetailChromeBinding(
            currentSectionTitle = context.getString(currentSection.titleRes),
            onOpenSections = { scope.launch { drawerState.open() } },
        ),
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ConfigDetailDrawerContent(
                screenWidth = screenWidth,
                groups = groups,
                currentSection = currentSection,
                expandedGroupTitles = expandedGroupTitles,
                onToggleGroup = { titleRes ->
                    expandedGroupTitles = toggleExpandedGroup(expandedGroupTitles, titleRes)
                },
                onSelectSection = { section ->
                    scope.launch {
                        listState.animateToItemWithAppMotion(sections.indexOf(section))
                        drawerState.close()
                    }
                },
            )
        },
    ) {
        Scaffold(
            containerColor = MonochromeUi.pageBackground,
            topBar = { SecondaryTopBarPlaceholder() },
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
                onPreviewVoice = { provider, text, voiceId, readBracketedContent ->
                    configViewModel.synthesizeSpeech(
                        provider = provider,
                        text = text,
                        voiceId = voiceId,
                        readBracketedContent = readBracketedContent,
                    )
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
    onPreviewVoice: (ProviderProfile, String, String, Boolean) -> com.astrbot.android.model.chat.ConversationAttachment,
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
    var adminUids by remember(profile.id) { mutableStateOf(profile.adminUids) }
    var sessionIsolationEnabled by remember(profile.id) { mutableStateOf(profile.sessionIsolationEnabled) }
    var wakeWords by remember(profile.id) { mutableStateOf(profile.wakeWords) }
    var wakeWordsAdminOnlyEnabled by remember(profile.id) { mutableStateOf(profile.wakeWordsAdminOnlyEnabled) }
    var privateChatRequiresWakeWord by remember(profile.id) { mutableStateOf(profile.privateChatRequiresWakeWord) }
    var replyTextPrefix by remember(profile.id) { mutableStateOf(profile.replyTextPrefix) }
    var quoteSenderMessageEnabled by remember(profile.id) { mutableStateOf(profile.quoteSenderMessageEnabled) }
    var mentionSenderEnabled by remember(profile.id) { mutableStateOf(profile.mentionSenderEnabled) }
    var replyOnAtOnlyEnabled by remember(profile.id) { mutableStateOf(profile.replyOnAtOnlyEnabled) }
    var whitelistEnabled by remember(profile.id) { mutableStateOf(profile.whitelistEnabled) }
    var whitelistEntries by remember(profile.id) { mutableStateOf(profile.whitelistEntries) }
    var logOnWhitelistMiss by remember(profile.id) { mutableStateOf(profile.logOnWhitelistMiss) }
    var adminGroupBypassWhitelistEnabled by remember(profile.id) { mutableStateOf(profile.adminGroupBypassWhitelistEnabled) }
    var adminPrivateBypassWhitelistEnabled by remember(profile.id) { mutableStateOf(profile.adminPrivateBypassWhitelistEnabled) }
    var ignoreSelfMessageEnabled by remember(profile.id) { mutableStateOf(profile.ignoreSelfMessageEnabled) }
    var ignoreAtAllEventEnabled by remember(profile.id) { mutableStateOf(profile.ignoreAtAllEventEnabled) }
    var replyWhenPermissionDenied by remember(profile.id) { mutableStateOf(profile.replyWhenPermissionDenied) }
    var rateLimitWindowSeconds by remember(profile.id) { mutableStateOf(profile.rateLimitWindowSeconds.toString()) }
    var rateLimitMaxCount by remember(profile.id) { mutableStateOf(profile.rateLimitMaxCount.toString()) }
    var rateLimitStrategy by remember(profile.id) { mutableStateOf(profile.rateLimitStrategy) }
    var keywordDetectionEnabled by remember(profile.id) { mutableStateOf(profile.keywordDetectionEnabled) }
    var keywordPatterns by remember(profile.id) { mutableStateOf(profile.keywordPatterns) }

    // Context Strategy
    var contextLimitStrategy by remember(profile.id) { mutableStateOf(profile.contextLimitStrategy) }
    var maxContextTurns by remember(profile.id) { mutableStateOf(profile.maxContextTurns.toString()) }
    var dequeueContextTurns by remember(profile.id) { mutableStateOf(profile.dequeueContextTurns.toString()) }
    var llmCompressInstruction by remember(profile.id) { mutableStateOf(profile.llmCompressInstruction) }
    var llmCompressKeepRecent by remember(profile.id) { mutableStateOf(profile.llmCompressKeepRecent.toString()) }
    var llmCompressProviderId by remember(profile.id) { mutableStateOf(profile.llmCompressProviderId) }

    // MCP / Skill
    var mcpServers by remember(profile.id) { mutableStateOf(profile.mcpServers) }
    var skills by remember(profile.id) { mutableStateOf(profile.skills) }
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
                            ConfigToggleField(
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
            }
            item(key = ConfigSection.SpeechSettings.name) {
                ConfigSectionCard(
                        title = stringResource(R.string.config_section_speech_settings),
                        subtitle = stringResource(R.string.config_section_speech_settings_desc),
                    ) {
                        ConfigFieldGroup {
                            ConfigToggleField(
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
                            ConfigToggleField(
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
                            ConfigToggleField(
                                title = stringResource(R.string.config_always_tts_title),
                                subtitle = "",
                                checked = alwaysTtsEnabled,
                                enabled = ttsModelReady,
                                onCheckedChange = { alwaysTtsEnabled = it },
                            )
                            ConfigToggleField(
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
                                                onPreviewVoice(
                                                    previewProvider,
                                                    "你好，世界",
                                                    ttsVoiceId,
                                                    true,
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
            }
            item(key = ConfigSection.StreamingSettings.name) {
                ConfigSectionCard(
                        title = stringResource(R.string.config_section_streaming_settings),
                        subtitle = stringResource(R.string.config_section_streaming_settings_desc),
                    ) {
                        ConfigFieldGroup {
                            ConfigToggleField(
                                title = stringResource(R.string.config_text_streaming_title),
                                subtitle = stringResource(R.string.config_text_streaming_desc),
                                checked = textStreamingEnabled,
                                onCheckedChange = { textStreamingEnabled = it },
                            )
                            ConfigToggleField(
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
            }
            item(key = ConfigSection.RuntimeHelpers.name) {
                ConfigSectionCard(
                        title = stringResource(R.string.config_section_runtime_helpers),
                        subtitle = stringResource(R.string.config_section_runtime_helpers_desc),
                    ) {
                        ConfigFieldGroup {
                            ConfigToggleField(
                                title = stringResource(R.string.config_time_awareness),
                                subtitle = stringResource(R.string.config_time_awareness_desc),
                                checked = realWorldTimeAwarenessEnabled,
                                onCheckedChange = { realWorldTimeAwarenessEnabled = it },
                            )
                            ConfigToggleField(
                                title = stringResource(R.string.config_web_search_title),
                                subtitle = stringResource(R.string.config_web_search_desc),
                                checked = webSearchEnabled,
                                onCheckedChange = { webSearchEnabled = it },
                            )
                        }
                    }
            }
            item(key = ConfigSection.KnowledgeBase.name) {
                ConfigSectionCard(
                    title = stringResource(R.string.config_section_knowledge_base),
                    subtitle = stringResource(R.string.config_knowledge_base_desc),
                ) {
                    ConfigFieldGroup {
                        Text(
                            text = stringResource(R.string.config_mcp_servers_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MonochromeUi.textPrimary,
                        )
                        mcpServers.forEachIndexed { index, server ->
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                OutlinedTextField(
                                    value = server.name,
                                    onValueChange = { value ->
                                        mcpServers = mcpServers.toMutableList().also { it[index] = server.copy(name = value) }
                                    },
                                    label = { Text(stringResource(R.string.config_mcp_server_name)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = monochromeOutlinedTextFieldColors(),
                                )
                                OutlinedTextField(
                                    value = server.url,
                                    onValueChange = { value ->
                                        mcpServers = mcpServers.toMutableList().also { it[index] = server.copy(url = value) }
                                    },
                                    label = { Text(stringResource(R.string.config_mcp_server_url)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = monochromeOutlinedTextFieldColors(),
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    ConfigToggleField(
                                        title = stringResource(R.string.config_mcp_server_active),
                                        subtitle = "",
                                        checked = server.active,
                                        onCheckedChange = { value ->
                                            mcpServers = mcpServers.toMutableList().also { it[index] = server.copy(active = value) }
                                        },
                                    )
                                    OutlinedButton(onClick = {
                                        mcpServers = mcpServers.toMutableList().also { it.removeAt(index) }
                                    }) {
                                        Text(stringResource(R.string.config_mcp_server_remove))
                                    }
                                }
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                mcpServers = mcpServers + McpServerEntry(serverId = UUID.randomUUID().toString().take(8))
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.config_mcp_server_add))
                        }

                        Text(
                            text = stringResource(R.string.config_skills_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MonochromeUi.textPrimary,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        skills.forEachIndexed { index, skill ->
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                OutlinedTextField(
                                    value = skill.name,
                                    onValueChange = { value ->
                                        skills = skills.toMutableList().also { it[index] = skill.copy(name = value) }
                                    },
                                    label = { Text(stringResource(R.string.config_skill_name)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = monochromeOutlinedTextFieldColors(),
                                )
                                OutlinedTextField(
                                    value = skill.description,
                                    onValueChange = { value ->
                                        skills = skills.toMutableList().also { it[index] = skill.copy(description = value) }
                                    },
                                    label = { Text(stringResource(R.string.config_skill_description)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = monochromeOutlinedTextFieldColors(),
                                )
                                OutlinedTextField(
                                    value = skill.content,
                                    onValueChange = { value ->
                                        skills = skills.toMutableList().also { it[index] = skill.copy(content = value) }
                                    },
                                    label = { Text(stringResource(R.string.config_skill_content)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 3,
                                    maxLines = 8,
                                    colors = monochromeOutlinedTextFieldColors(),
                                )
                                OutlinedTextField(
                                    value = skill.priority.toString(),
                                    onValueChange = { value ->
                                        val parsed = value.toIntOrNull() ?: 0
                                        skills = skills.toMutableList().also { it[index] = skill.copy(priority = parsed) }
                                    },
                                    label = { Text(stringResource(R.string.config_skill_priority)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = monochromeOutlinedTextFieldColors(),
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    ConfigToggleField(
                                        title = stringResource(R.string.config_skill_active),
                                        subtitle = "",
                                        checked = skill.active,
                                        onCheckedChange = { value ->
                                            skills = skills.toMutableList().also { it[index] = skill.copy(active = value) }
                                        },
                                    )
                                    OutlinedButton(onClick = {
                                        skills = skills.toMutableList().also { it.removeAt(index) }
                                    }) {
                                        Text(stringResource(R.string.config_skill_remove))
                                    }
                                }
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                skills = skills + SkillEntry(skillId = UUID.randomUUID().toString().take(8))
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.config_skill_add))
                        }
                    }
                }
            }
            item(key = ConfigSection.ContextStrategy.name) {
                ConfigSectionCard(
                    title = stringResource(R.string.config_section_context_strategy),
                    subtitle = stringResource(R.string.config_context_strategy_desc),
                ) {
                    ConfigFieldGroup {
                        SelectionField(
                            title = stringResource(R.string.config_context_limit_strategy_title),
                            selectedId = contextLimitStrategy,
                            options = listOf(
                                "truncate_by_turns" to stringResource(R.string.config_context_limit_strategy_truncate),
                                "llm_compress" to stringResource(R.string.config_context_limit_strategy_compress),
                            ),
                            onSelect = { contextLimitStrategy = it },
                        )
                        OutlinedTextField(
                            value = maxContextTurns,
                            onValueChange = { value ->
                                maxContextTurns = value.filter { it.isDigit() || it == '-' }.take(5)
                            },
                            label = { Text(stringResource(R.string.config_max_context_turns_title)) },
                            supportingText = { Text(stringResource(R.string.config_max_context_turns_desc)) },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = monochromeOutlinedTextFieldColors(),
                        )
                        if (contextLimitStrategy == "truncate_by_turns") {
                            OutlinedTextField(
                                value = dequeueContextTurns,
                                onValueChange = { value ->
                                    dequeueContextTurns = value.filter { it.isDigit() }.take(3)
                                },
                                label = { Text(stringResource(R.string.config_dequeue_context_turns_title)) },
                                supportingText = { Text(stringResource(R.string.config_dequeue_context_turns_desc)) },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = monochromeOutlinedTextFieldColors(),
                            )
                        }
                        if (contextLimitStrategy == "llm_compress") {
                            OutlinedTextField(
                                value = llmCompressInstruction,
                                onValueChange = { llmCompressInstruction = it },
                                label = { Text(stringResource(R.string.config_llm_compress_instruction_title)) },
                                supportingText = { Text(stringResource(R.string.config_llm_compress_instruction_desc)) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = monochromeOutlinedTextFieldColors(),
                            )
                            OutlinedTextField(
                                value = llmCompressKeepRecent,
                                onValueChange = { value ->
                                    llmCompressKeepRecent = value.filter { it.isDigit() }.take(3)
                                },
                                label = { Text(stringResource(R.string.config_llm_compress_keep_recent_title)) },
                                supportingText = { Text(stringResource(R.string.config_llm_compress_keep_recent_desc)) },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = monochromeOutlinedTextFieldColors(),
                            )
                            OutlinedTextField(
                                value = llmCompressProviderId,
                                onValueChange = { llmCompressProviderId = it },
                                label = { Text(stringResource(R.string.config_llm_compress_provider_title)) },
                                supportingText = { Text(stringResource(R.string.config_llm_compress_provider_desc)) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = monochromeOutlinedTextFieldColors(),
                            )
                        }
                    }
                }
            }
            item(key = ConfigSection.Automation.name) {
                ConfigSectionCard(
                    title = stringResource(R.string.config_section_automation),
                    subtitle = stringResource(R.string.config_automation_desc),
                ) {
                    ConfigFieldGroup {
                        ConfigToggleField(
                            title = stringResource(R.string.config_proactive_title),
                            subtitle = stringResource(R.string.config_proactive_desc),
                            checked = proactiveEnabled,
                            onCheckedChange = { proactiveEnabled = it },
                        )
                    }
                }
            }
            item(key = ConfigSection.Admin.name) {
                AdminSettingsSection(
                    adminUids = adminUids,
                    onAdminUidsChange = { adminUids = it },
                )
            }
            item(key = ConfigSection.Session.name) {
                SessionSettingsSection(
                    sessionIsolationEnabled = sessionIsolationEnabled,
                    onSessionIsolationEnabledChange = { sessionIsolationEnabled = it },
                )
            }
            item(key = ConfigSection.Wake.name) {
                WakeSettingsSection(
                    wakeWords = wakeWords,
                    onWakeWordsChange = { wakeWords = it },
                    wakeWordsAdminOnlyEnabled = wakeWordsAdminOnlyEnabled,
                    onWakeWordsAdminOnlyEnabledChange = { wakeWordsAdminOnlyEnabled = it },
                    privateChatRequiresWakeWord = privateChatRequiresWakeWord,
                    onPrivateChatRequiresWakeWordChange = { privateChatRequiresWakeWord = it },
                )
            }
            item(key = ConfigSection.Reply.name) {
                ReplySettingsSection(
                    replyTextPrefix = replyTextPrefix,
                    onReplyTextPrefixChange = { replyTextPrefix = it },
                    quoteSenderMessageEnabled = quoteSenderMessageEnabled,
                    onQuoteSenderMessageEnabledChange = { quoteSenderMessageEnabled = it },
                    mentionSenderEnabled = mentionSenderEnabled,
                    onMentionSenderEnabledChange = { mentionSenderEnabled = it },
                    replyOnAtOnlyEnabled = replyOnAtOnlyEnabled,
                    onReplyOnAtOnlyEnabledChange = { replyOnAtOnlyEnabled = it },
                )
            }
            item(key = ConfigSection.Whitelist.name) {
                WhitelistSettingsSection(
                    whitelistEnabled = whitelistEnabled,
                    onWhitelistEnabledChange = { whitelistEnabled = it },
                    whitelistEntries = whitelistEntries,
                    onWhitelistEntriesChange = { whitelistEntries = it },
                    logOnWhitelistMiss = logOnWhitelistMiss,
                    onLogOnWhitelistMissChange = { logOnWhitelistMiss = it },
                    adminGroupBypassWhitelistEnabled = adminGroupBypassWhitelistEnabled,
                    onAdminGroupBypassWhitelistEnabledChange = { adminGroupBypassWhitelistEnabled = it },
                    adminPrivateBypassWhitelistEnabled = adminPrivateBypassWhitelistEnabled,
                    onAdminPrivateBypassWhitelistEnabledChange = { adminPrivateBypassWhitelistEnabled = it },
                )
            }
            item(key = ConfigSection.IgnorePermission.name) {
                IgnorePermissionSettingsSection(
                    ignoreSelfMessageEnabled = ignoreSelfMessageEnabled,
                    onIgnoreSelfMessageEnabledChange = { ignoreSelfMessageEnabled = it },
                    ignoreAtAllEventEnabled = ignoreAtAllEventEnabled,
                    onIgnoreAtAllEventEnabledChange = { ignoreAtAllEventEnabled = it },
                    replyWhenPermissionDenied = replyWhenPermissionDenied,
                    onReplyWhenPermissionDeniedChange = { replyWhenPermissionDenied = it },
                )
            }
            item(key = ConfigSection.RateLimit.name) {
                RateLimitSettingsSection(
                    rateLimitWindowSeconds = rateLimitWindowSeconds,
                    onRateLimitWindowSecondsChange = { rateLimitWindowSeconds = it },
                    rateLimitMaxCount = rateLimitMaxCount,
                    onRateLimitMaxCountChange = { rateLimitMaxCount = it },
                    rateLimitStrategy = rateLimitStrategy,
                    onRateLimitStrategyChange = { rateLimitStrategy = it },
                )
            }
            item(key = ConfigSection.Keyword.name) {
                KeywordSettingsSection(
                    keywordDetectionEnabled = keywordDetectionEnabled,
                    onKeywordDetectionEnabledChange = { keywordDetectionEnabled = it },
                    keywordPatterns = keywordPatterns,
                    onKeywordPatternsChange = { keywordPatterns = it },
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
                        adminUids = adminUids,
                        sessionIsolationEnabled = sessionIsolationEnabled,
                        wakeWords = wakeWords,
                        wakeWordsAdminOnlyEnabled = wakeWordsAdminOnlyEnabled,
                        privateChatRequiresWakeWord = privateChatRequiresWakeWord,
                        replyTextPrefix = replyTextPrefix.trim(),
                        quoteSenderMessageEnabled = quoteSenderMessageEnabled,
                        mentionSenderEnabled = mentionSenderEnabled,
                        replyOnAtOnlyEnabled = replyOnAtOnlyEnabled,
                        whitelistEnabled = whitelistEnabled,
                        whitelistEntries = whitelistEntries,
                        logOnWhitelistMiss = logOnWhitelistMiss,
                        adminGroupBypassWhitelistEnabled = adminGroupBypassWhitelistEnabled,
                        adminPrivateBypassWhitelistEnabled = adminPrivateBypassWhitelistEnabled,
                        ignoreSelfMessageEnabled = ignoreSelfMessageEnabled,
                        ignoreAtAllEventEnabled = ignoreAtAllEventEnabled,
                        replyWhenPermissionDenied = replyWhenPermissionDenied,
                        rateLimitWindowSeconds = rateLimitWindowSeconds.toIntOrNull() ?: profile.rateLimitWindowSeconds,
                        rateLimitMaxCount = rateLimitMaxCount.toIntOrNull() ?: profile.rateLimitMaxCount,
                        rateLimitStrategy = rateLimitStrategy,
                        keywordDetectionEnabled = keywordDetectionEnabled,
                        keywordPatterns = keywordPatterns,
                        contextLimitStrategy = contextLimitStrategy,
                        maxContextTurns = maxContextTurns.toIntOrNull() ?: profile.maxContextTurns,
                        dequeueContextTurns = dequeueContextTurns.toIntOrNull()?.coerceAtLeast(1) ?: profile.dequeueContextTurns,
                        llmCompressInstruction = llmCompressInstruction.trim(),
                        llmCompressKeepRecent = llmCompressKeepRecent.toIntOrNull()?.coerceAtLeast(0) ?: profile.llmCompressKeepRecent,
                        llmCompressProviderId = llmCompressProviderId.trim(),
                        mcpServers = mcpServers,
                        skills = skills,
                    ),
                )
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 28.dp),
            containerColor = MonochromeUi.actionFabBackground,
            contentColor = MonochromeUi.actionFabContent,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
        ) {
            Icon(Icons.Outlined.Done, contentDescription = stringResource(R.string.common_save))
        }
    }
}

private fun ProviderProfile.hasMultimodalSupport(): Boolean {
    return multimodalProbeSupport == FeatureSupportState.SUPPORTED ||
        multimodalRuleSupport == FeatureSupportState.SUPPORTED
}
