package com.elymbot.android.ui.config
import com.elymbot.android.ui.bot.SelectionField
import com.elymbot.android.ui.config.detail.ConfigDetailDrawerContent
import com.elymbot.android.ui.config.detail.ConfigFieldGroup
import com.elymbot.android.ui.config.detail.ConfigSection
import com.elymbot.android.ui.config.detail.ConfigSectionCard
import com.elymbot.android.ui.config.detail.InlineConfigNotice
import com.elymbot.android.ui.config.detail.PlaceholderSectionCard
import com.elymbot.android.ui.config.detail.configDrawerGroups
import com.elymbot.android.ui.config.detail.fields.ConfigToggleField
import com.elymbot.android.ui.config.detail.sections.AdminSettingsSection
import com.elymbot.android.ui.config.detail.sections.IgnorePermissionSettingsSection
import com.elymbot.android.ui.config.detail.sections.KeywordSettingsSection
import com.elymbot.android.ui.config.detail.sections.RateLimitSettingsSection
import com.elymbot.android.ui.config.detail.sections.ReplySettingsSection
import com.elymbot.android.ui.config.detail.sections.SessionSettingsSection
import com.elymbot.android.ui.config.detail.sections.WakeSettingsSection
import com.elymbot.android.ui.config.detail.sections.WhitelistSettingsSection

import android.media.MediaPlayer
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.elymbot.android.core.ui.R
import com.elymbot.android.feature.config.domain.model.ConfigProfile
import com.elymbot.android.feature.geofence.domain.model.GeofenceActionType
import com.elymbot.android.ui.config.geofence.ConfigGeofenceBindingDraft
import com.elymbot.android.ui.config.geofence.ConfigGeofenceBindingListItem
import com.elymbot.android.ui.config.geofence.ConfigGeofenceBindingPresentation
import com.elymbot.android.ui.config.geofence.ConfigGeofenceBindingSummary
import com.elymbot.android.ui.config.geofence.ConfigGeofenceBindingViewModel
import com.elymbot.android.feature.provider.domain.model.FeatureSupportState
import com.elymbot.android.model.ConfigResourceProjection
import com.elymbot.android.model.McpServerEntry
import com.elymbot.android.model.ResourceCenterItem
import com.elymbot.android.model.ResourceCenterKind
import com.elymbot.android.feature.provider.domain.model.ProviderCapability
import com.elymbot.android.feature.provider.domain.model.ProviderProfile
import com.elymbot.android.feature.provider.domain.model.ProviderType
import com.elymbot.android.model.SkillEntry
import com.elymbot.android.model.TtsVoiceCatalog
import com.elymbot.android.feature.voiceasset.api.model.TtsVoiceReferenceAsset
import com.elymbot.android.ui.app.MonochromeUi
import com.elymbot.android.ui.app.ConfigDetailChromeBinding
import com.elymbot.android.ui.app.monochromeOutlinedTextFieldColors
import com.elymbot.android.ui.app.monochromeSwitchColors
import com.elymbot.android.ui.app.RegisterConfigDetailChromeBinding
import com.elymbot.android.ui.app.SecondaryTopBarPlaceholder
import com.elymbot.android.ui.viewmodel.ConfigViewModel
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.text.KeyboardOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Base64

@Composable
fun ConfigDetailScreen(
    profileId: String,
    onBack: () -> Unit,
    onOpenGeofenceRules: () -> Unit,
    onOpenResourceCenter: () -> Unit,
    configViewModel: ConfigViewModel = hiltViewModel(),
    geofenceBindingViewModel: ConfigGeofenceBindingViewModel = hiltViewModel(),
) {
    val profiles by configViewModel.configProfiles.collectAsState()
    val providers by configViewModel.providers.collectAsState()
    val ttsVoiceAssets by configViewModel.ttsVoiceAssets.collectAsState()
    val resourceCenterResources by configViewModel.resourceCenterResources.collectAsState()
    val resourceCenterProjections by configViewModel.resourceCenterProjections.collectAsState()
    val geofenceRules by geofenceBindingViewModel.rules.collectAsState()
    val geofenceBindings by geofenceBindingViewModel.bindings.collectAsState()
    val profile = profiles.firstOrNull { it.id == profileId }
    val context = LocalContext.current

    LaunchedEffect(profile) {
        if (profile == null) onBack()
    }

    if (profile == null) return

    val geofenceBindingPresentation = remember(profile.id, geofenceRules, geofenceBindings) {
        geofenceBindingViewModel.buildPresentation(
            configId = profile.id,
            rules = geofenceRules,
            bindings = geofenceBindings,
        )
    }

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
                resourceCenterResources = resourceCenterResources,
                resourceCenterProjections = resourceCenterProjections,
                geofenceBindingPresentation = geofenceBindingPresentation,
                currentSectionTitle = context.getString(currentSection.titleRes),
                onOpenSections = { scope.launch { drawerState.open() } },
                listState = listState,
                modifier = Modifier.padding(innerPadding),
                onBack = onBack,
                onOpenGeofenceRules = onOpenGeofenceRules,
                onOpenResourceCenter = onOpenResourceCenter,
                onSaveGeofenceBindingDraft = { draft ->
                    geofenceBindingViewModel.saveDraft(profile.id, draft)
                },
                onSave = { updated, projections ->
                    configViewModel.saveWithResourceProjections(updated, projections)
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
// skipcq: KT-R1006
private fun ConfigDetailContent(
    profile: ConfigProfile,
    chatModelOptions: List<Pair<String, String>>,
    sttModelOptions: List<Pair<String, String>>,
    ttsModelOptions: List<Pair<String, String>>,
    captionModelOptions: List<Pair<String, String>>,
    providers: List<ProviderProfile>,
    ttsVoiceAssets: List<TtsVoiceReferenceAsset>,
    resourceCenterResources: List<ResourceCenterItem>,
    resourceCenterProjections: List<ConfigResourceProjection>,
    geofenceBindingPresentation: ConfigGeofenceBindingPresentation,
    currentSectionTitle: String,
    onOpenSections: () -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onOpenGeofenceRules: () -> Unit,
    onOpenResourceCenter: () -> Unit,
    onSaveGeofenceBindingDraft: suspend (ConfigGeofenceBindingDraft) -> Result<Unit>,
    onSave: (ConfigProfile, List<ConfigResourceProjection>) -> Unit,
    onPreviewVoice: (ProviderProfile, String, String, Boolean) -> com.elymbot.android.model.chat.ConversationAttachment,
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
    var includeScheduledTaskConversationContext by remember(profile.id) {
        mutableStateOf(profile.includeScheduledTaskConversationContext)
    }
    var ttsVoiceId by remember(profile.id) { mutableStateOf(profile.ttsVoiceId) }
    var isPreviewingVoice by remember(profile.id) { mutableStateOf(false) }
    var imageCaptionPrompt by remember(profile.id) { mutableStateOf(profile.imageCaptionPrompt) }
    var adminUids by remember(profile.id) { mutableStateOf(profile.adminUids) }
    var pluginCommandsAdminOnlyEnabled by remember(profile.id) {
        mutableStateOf(profile.pluginCommandsAdminOnlyEnabled)
    }
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

    // MCP / Skill selections are projections of Resource Center resources.
    var mcpSelections by remember(profile.id, resourceCenterResources, resourceCenterProjections) {
        mutableStateOf(
            initialResourceSelections(
                profile = profile,
                resources = resourceCenterResources,
                projections = resourceCenterProjections,
                kind = ResourceCenterKind.MCP_SERVER,
            ),
        )
    }
    var skillSelections by remember(profile.id, resourceCenterResources, resourceCenterProjections) {
        mutableStateOf(
            initialResourceSelections(
                profile = profile,
                resources = resourceCenterResources,
                projections = resourceCenterProjections,
                kind = ResourceCenterKind.SKILL,
            ),
        )
    }
    var pendingResourceDialogKind by remember { mutableStateOf<ResourceCenterKind?>(null) }
    var pendingExit by remember { mutableStateOf<ConfigPendingExit?>(null) }
    var showGeofenceBindingDialog by remember(profile.id) { mutableStateOf(false) }
    var geofenceBindingDialogSession by remember(profile.id) { mutableStateOf(0) }
    var geofenceBindingSaving by remember(profile.id) { mutableStateOf(false) }
    var geofenceBindingSaveError by remember(profile.id) { mutableStateOf<String?>(null) }
    val unnamedConfigLabel = stringResource(R.string.config_unnamed)
    val defaultChatProvider = providers.firstOrNull { it.id == defaultChatProviderId }
    val defaultTtsProvider = providers.firstOrNull { it.id == defaultTtsProviderId }
    val sttModelReady = defaultSttProviderId.isNotBlank()
    val ttsModelReady = defaultTtsProviderId.isNotBlank()
    val ttsVoiceOptions = remember(defaultTtsProvider?.id, defaultTtsProvider?.model, ttsVoiceAssets) {
        val providerOptions = TtsVoiceCatalog.optionsFor(defaultTtsProvider)
        val clonedOptions = ttsVoiceAssets.listVoiceChoicesFor(defaultTtsProvider)
        buildList {
            val preferClonedVoicesFirst = defaultTtsProvider?.providerType == ProviderType.BAILIAN_TTS &&
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
    fun draftProfile(): ConfigProfile {
        return profile.copy(
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
            includeScheduledTaskConversationContext = includeScheduledTaskConversationContext,
            ttsVoiceId = ttsVoiceId.trim(),
            imageCaptionPrompt = imageCaptionPrompt.trim(),
            adminUids = adminUids,
            pluginCommandsAdminOnlyEnabled = pluginCommandsAdminOnlyEnabled,
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
            mcpServers = mcpSelections.mapNotNull { selection ->
                resourceCenterResources.firstOrNull {
                    it.resourceId == selection.resourceId && it.kind == ResourceCenterKind.MCP_SERVER
                }?.toMcpServerEntry(selection)
            },
            skills = skillSelections.mapNotNull { selection ->
                resourceCenterResources.firstOrNull {
                    it.resourceId == selection.resourceId && it.kind == ResourceCenterKind.SKILL
                }?.toSkillEntry(selection)
            },
        )
    }
    fun projectionUpdates(): List<ConfigResourceProjection> {
        return buildProjectionUpdates(
            configId = profile.id,
            mcpSelections = mcpSelections,
            skillSelections = skillSelections,
            existingProjections = resourceCenterProjections,
        )
    }
    fun saveDraft() {
        onSave(draftProfile(), projectionUpdates())
    }
    val hasUnsavedChanges = draftProfile() != profile
    fun requestExit(exit: ConfigPendingExit) {
        if (hasUnsavedChanges) {
            pendingExit = exit
        } else {
            when (exit) {
                ConfigPendingExit.Back -> onBack()
                ConfigPendingExit.GeofenceRules -> onOpenGeofenceRules()
                ConfigPendingExit.ResourceCenter -> onOpenResourceCenter()
            }
        }
    }

    BackHandler {
        requestExit(ConfigPendingExit.Back)
    }

    RegisterConfigDetailChromeBinding(
        ConfigDetailChromeBinding(
            currentSectionTitle = currentSectionTitle,
            onBack = { requestExit(ConfigPendingExit.Back) },
            onOpenSections = onOpenSections,
        ),
    )

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
                        ResourceSelectionSummary(
                            title = stringResource(R.string.config_mcp_servers_title),
                            emptyText = stringResource(R.string.config_resource_selection_empty),
                            selections = mcpSelections,
                            resources = resourceCenterResources.filter { it.kind == ResourceCenterKind.MCP_SERVER },
                            onManage = { pendingResourceDialogKind = ResourceCenterKind.MCP_SERVER },
                        )
                        ResourceSelectionSummary(
                            title = stringResource(R.string.config_skills_title),
                            emptyText = stringResource(R.string.config_resource_selection_empty),
                            selections = skillSelections,
                            resources = resourceCenterResources.filter { it.kind == ResourceCenterKind.SKILL },
                            onManage = { pendingResourceDialogKind = ResourceCenterKind.SKILL },
                        )
                        OutlinedButton(
                            onClick = { requestExit(ConfigPendingExit.ResourceCenter) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Outlined.Settings, contentDescription = null)
                            Text(stringResource(R.string.config_resource_center_manage))
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
                        ConfigToggleField(
                            title = stringResource(R.string.config_scheduled_task_context_title),
                            subtitle = stringResource(R.string.config_scheduled_task_context_desc),
                            checked = includeScheduledTaskConversationContext,
                            onCheckedChange = { includeScheduledTaskConversationContext = it },
                        )
                    }
                }
            }
            item(key = ConfigSection.Admin.name) {
                AdminSettingsSection(
                    adminUids = adminUids,
                    onAdminUidsChange = { adminUids = it },
                    pluginCommandsAdminOnlyEnabled = pluginCommandsAdminOnlyEnabled,
                    onPluginCommandsAdminOnlyEnabledChange = { pluginCommandsAdminOnlyEnabled = it },
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
            item(key = ConfigSection.Geofence.name) {
                GeofenceBindingSettingsSection(
                    presentation = geofenceBindingPresentation,
                    onManage = {
                        geofenceBindingDialogSession += 1
                        geofenceBindingSaveError = null
                        showGeofenceBindingDialog = true
                    },
                    onOpenRules = { requestExit(ConfigPendingExit.GeofenceRules) },
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
                saveDraft()
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

        pendingResourceDialogKind?.let { kind ->
            ResourceSelectionDialog(
                kind = kind,
                resources = resourceCenterResources.filter { it.kind == kind },
                selections = if (kind == ResourceCenterKind.MCP_SERVER) mcpSelections else skillSelections,
                onDismiss = { pendingResourceDialogKind = null },
                onSave = { next ->
                    if (kind == ResourceCenterKind.MCP_SERVER) {
                        mcpSelections = next
                    } else {
                        skillSelections = next
                    }
                    pendingResourceDialogKind = null
                },
            )
        }

        if (showGeofenceBindingDialog) {
            GeofenceBindingDialog(
                draftSessionKey = "${profile.id}:$geofenceBindingDialogSession",
                presentation = geofenceBindingPresentation,
                saving = geofenceBindingSaving,
                errorMessage = geofenceBindingSaveError,
                onOpenRules = { requestExit(ConfigPendingExit.GeofenceRules) },
                onDismiss = {
                    if (!geofenceBindingSaving) {
                        showGeofenceBindingDialog = false
                        geofenceBindingSaveError = null
                    }
                },
                onSave = { draft ->
                    scope.launch {
                        geofenceBindingSaving = true
                        geofenceBindingSaveError = null
                        val result = onSaveGeofenceBindingDraft(draft)
                        geofenceBindingSaving = false
                        result.onSuccess {
                            showGeofenceBindingDialog = false
                            geofenceBindingSaveError = null
                            Toast.makeText(context, context.getString(R.string.common_saved), Toast.LENGTH_SHORT).show()
                        }.onFailure { error ->
                            geofenceBindingSaveError = error.message ?: error.javaClass.simpleName
                        }
                    }
                },
            )
        }

        pendingExit?.let { exit ->
            UnsavedConfigExitDialog(
                onDismiss = { pendingExit = null },
                onSaveAndContinue = {
                    saveDraft()
                    pendingExit = null
                    when (exit) {
                        ConfigPendingExit.Back -> onBack()
                        ConfigPendingExit.GeofenceRules -> onOpenGeofenceRules()
                        ConfigPendingExit.ResourceCenter -> onOpenResourceCenter()
                    }
                },
                onDiscardAndContinue = {
                    pendingExit = null
                    when (exit) {
                        ConfigPendingExit.Back -> onBack()
                        ConfigPendingExit.GeofenceRules -> onOpenGeofenceRules()
                        ConfigPendingExit.ResourceCenter -> onOpenResourceCenter()
                    }
                },
            )
        }
    }
}

private fun ProviderProfile.hasMultimodalSupport(): Boolean {
    return multimodalProbeSupport == FeatureSupportState.SUPPORTED ||
        multimodalRuleSupport == FeatureSupportState.SUPPORTED
}

@Composable
private fun GeofenceBindingSettingsSection(
    presentation: ConfigGeofenceBindingPresentation,
    onManage: () -> Unit,
    onOpenRules: () -> Unit,
) {
    ConfigSectionCard(
        title = stringResource(R.string.config_section_geofence),
        subtitle = stringResource(R.string.config_section_geofence_desc),
    ) {
        ConfigFieldGroup {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("config-geofence-section"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.config_geofence_binding_count, presentation.loadedCount),
                    color = MonochromeUi.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                if (presentation.summaryItems.isEmpty()) {
                    Text(
                        text = stringResource(R.string.config_geofence_binding_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MonochromeUi.textSecondary,
                    )
                } else {
                    presentation.summaryItems.forEach { summary ->
                        Text(
                            text = localizedGeofenceBindingSummary(summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MonochromeUi.textSecondary,
                        )
                    }
                    val remaining = presentation.loadedCount - presentation.summaryItems.size
                    if (remaining > 0) {
                        Text(
                            text = stringResource(R.string.config_list_preview_more, remaining),
                            style = MaterialTheme.typography.bodySmall,
                            color = MonochromeUi.textSecondary,
                        )
                    }
                }
                if (presentation.emptyRules) {
                    InlineConfigNotice(text = stringResource(R.string.config_geofence_binding_no_rules))
                    OutlinedButton(
                        onClick = onOpenRules,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("config-geofence-open-rules"),
                    ) {
                        Text(stringResource(R.string.config_geofence_open_rules))
                    }
                }
                OutlinedButton(
                    onClick = onManage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("config-geofence-manage"),
                ) {
                    Text(stringResource(R.string.common_manage))
                }
            }
        }
    }
}

@Composable
private fun GeofenceBindingDialog(
    draftSessionKey: String,
    presentation: ConfigGeofenceBindingPresentation,
    saving: Boolean,
    errorMessage: String?,
    onOpenRules: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (ConfigGeofenceBindingDraft) -> Unit,
) {
    val availableRuleIds = remember(presentation.items) {
        presentation.items.mapTo(linkedSetOf()) { item -> item.ruleId }
    }
    var draft by remember(draftSessionKey) {
        mutableStateOf(ConfigGeofenceBindingDraft.fromPresentation(presentation))
    }
    LaunchedEffect(draftSessionKey, availableRuleIds) {
        draft = draft.mergeAvailableRules(availableRuleIds)
    }
    val draftByRuleId = draft.entries.associateBy { entry -> entry.ruleId }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MonochromeUi.cardBackground,
        titleContentColor = MonochromeUi.textPrimary,
        textContentColor = MonochromeUi.textSecondary,
        title = { Text(stringResource(R.string.config_geofence_binding_dialog_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
                    .testTag("config-geofence-dialog"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (presentation.emptyRules) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("config-geofence-empty"),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(stringResource(R.string.config_geofence_binding_no_rules))
                        OutlinedButton(
                            onClick = onOpenRules,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("config-geofence-open-rules"),
                        ) {
                            Text(stringResource(R.string.config_geofence_open_rules))
                        }
                    }
                } else {
                    presentation.items.forEachIndexed { index, item ->
                        val entry = draftByRuleId[item.ruleId]
                        GeofenceBindingDialogRuleRow(
                            item = item,
                            selected = entry != null,
                            bindingEnabled = entry?.enabled ?: true,
                            onSelectedChange = { selected ->
                                draft = draft.withRuleSelected(item.ruleId, selected)
                            },
                            onBindingEnabledChange = { enabled ->
                                draft = draft.withBindingEnabled(item.ruleId, enabled)
                            },
                        )
                        if (index != presentation.items.lastIndex) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp),
                                color = MonochromeUi.divider,
                            ) {}
                        }
                    }
                }
                if (presentation.staleBindingRuleIds.isNotEmpty()) {
                    InlineConfigNotice(
                        text = stringResource(
                            R.string.config_geofence_stale_bindings_notice,
                            presentation.staleBindingRuleIds.joinToString(", "),
                        ),
                    )
                }
                if (saving) {
                    Text(
                        text = stringResource(R.string.config_geofence_saving),
                        style = MaterialTheme.typography.bodySmall,
                        color = MonochromeUi.textSecondary,
                    )
                }
                errorMessage?.let { message ->
                    InlineConfigNotice(
                        text = stringResource(R.string.config_geofence_save_failed, message),
                    )
                }
            }
        },
        confirmButton = {
            OutlinedButton(
                onClick = { onSave(draft) },
                enabled = !saving,
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !saving,
            ) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

@Composable
private fun GeofenceBindingDialogRuleRow(
    item: ConfigGeofenceBindingListItem,
    selected: Boolean,
    bindingEnabled: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    onBindingEnabledChange: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = onSelectedChange,
                modifier = Modifier.testTag("config-geofence-rule-checkbox-${item.ruleId}"),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = item.name,
                    color = MonochromeUi.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = localizedGeofenceRuleDetail(item.summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MonochromeUi.textSecondary,
                )
            }
        }
        if (selected) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.config_geofence_binding_enabled),
                        color = MonochromeUi.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.config_geofence_binding_enabled_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MonochromeUi.textSecondary,
                    )
                }
                Switch(
                    checked = bindingEnabled,
                    onCheckedChange = onBindingEnabledChange,
                    modifier = Modifier.testTag("config-geofence-binding-enabled-${item.ruleId}"),
                    colors = monochromeSwitchColors(),
                )
            }
        }
    }
}

@Composable
private fun localizedGeofenceBindingSummary(summary: ConfigGeofenceBindingSummary): String {
    return listOf(
        summary.ruleName,
        if (summary.bindingEnabled) {
            stringResource(R.string.config_geofence_binding_state_enabled)
        } else {
            stringResource(R.string.config_geofence_binding_state_disabled)
        },
        localizedGeofenceRegionSummary(summary),
        localizedGeofenceTriggerSummary(summary),
        localizedGeofenceActionSummary(summary),
    ).filter { part -> part.isNotBlank() }.joinToString(" · ")
}

@Composable
private fun localizedGeofenceRuleDetail(summary: ConfigGeofenceBindingSummary): String {
    return listOf(
        localizedGeofenceRegionSummary(summary),
        localizedGeofenceTriggerSummary(summary),
        localizedGeofenceActionSummary(summary),
    ).filter { part -> part.isNotBlank() }
        .joinToString(" · ")
        .ifBlank { stringResource(R.string.config_geofence_summary_empty) }
}

@Composable
private fun localizedGeofenceRegionSummary(summary: ConfigGeofenceBindingSummary): String {
    val radiusMeters = summary.radiusMeters ?: return ""
    val label = summary.regionLabel ?: stringResource(R.string.config_geofence_region_fallback)
    return if (summary.additionalRegionCount > 0) {
        stringResource(
            R.string.config_geofence_region_summary_more,
            label,
            radiusMeters,
            summary.additionalRegionCount,
        )
    } else {
        stringResource(R.string.config_geofence_region_summary, label, radiusMeters)
    }
}

@Composable
private fun localizedGeofenceTriggerSummary(summary: ConfigGeofenceBindingSummary): String {
    val triggers = mutableListOf<String>()
    if (summary.triggerEnter) {
        triggers += stringResource(R.string.config_geofence_trigger_enter)
    }
    if (summary.triggerExit) {
        triggers += stringResource(R.string.config_geofence_trigger_exit)
    }
    if (summary.triggerDwell) {
        triggers += stringResource(R.string.config_geofence_trigger_dwell)
    }
    return triggers.joinToString(", ")
}

@Composable
private fun localizedGeofenceActionSummary(summary: ConfigGeofenceBindingSummary): String {
    val actionLabel = when (summary.actionType) {
        GeofenceActionType.AGENT_PROMPT -> stringResource(R.string.config_geofence_action_agent_prompt)
        GeofenceActionType.SEND_MESSAGE -> stringResource(R.string.config_geofence_action_send_message)
        GeofenceActionType.WEATHER_FORECAST -> stringResource(R.string.config_geofence_action_weather_forecast)
        GeofenceActionType.NEWS_DIGEST -> stringResource(R.string.config_geofence_action_news_digest)
        GeofenceActionType.HOST_CAPABILITY -> stringResource(R.string.config_geofence_action_host_capability)
    }
    return if (summary.actionPromptPreview.isBlank()) {
        actionLabel
    } else {
        stringResource(R.string.config_geofence_action_with_prompt, actionLabel, summary.actionPromptPreview)
    }
}

@Composable
private fun ResourceSelectionSummary(
    title: String,
    emptyText: String,
    selections: List<ResourceSelectionState>,
    resources: List<ResourceCenterItem>,
    onManage: () -> Unit,
) {
    val resourcesById = remember(resources) { resources.associateBy { it.resourceId } }
    val selectedLabels = selections.mapNotNull { selection ->
        resourcesById[selection.resourceId]?.name?.ifBlank { selection.resourceId }
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MonochromeUi.textPrimary,
        )
        Text(
            text = if (selectedLabels.isEmpty()) {
                emptyText
            } else {
                buildString {
                    append(stringResource(R.string.config_list_count, selectedLabels.size))
                    append(": ")
                    append(selectedLabels.take(3).joinToString(", "))
                    val remaining = selectedLabels.size - 3
                    if (remaining > 0) {
                        append(" ")
                        append(stringResource(R.string.config_list_preview_more, remaining))
                    }
                }
            },
            color = MonochromeUi.textSecondary,
        )
        OutlinedButton(
            onClick = onManage,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.common_manage))
        }
    }
}

@Composable
private fun ResourceSelectionDialog(
    kind: ResourceCenterKind,
    resources: List<ResourceCenterItem>,
    selections: List<ResourceSelectionState>,
    onDismiss: () -> Unit,
    onSave: (List<ResourceSelectionState>) -> Unit,
) {
    var draftSelections by remember(kind, resources, selections) { mutableStateOf(selections) }
    val title = stringResource(
        if (kind == ResourceCenterKind.MCP_SERVER) {
            R.string.config_mcp_servers_title
        } else {
            R.string.config_skills_title
        },
    )
    val draftById = draftSelections.associateBy { it.resourceId }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MonochromeUi.cardBackground,
        titleContentColor = MonochromeUi.textPrimary,
        textContentColor = MonochromeUi.textSecondary,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (resources.isEmpty()) {
                    Text(stringResource(R.string.config_resource_selection_none_available))
                }
                resources.sortedWith(compareBy<ResourceCenterItem> { it.name.lowercase() }.thenBy { it.resourceId })
                    .forEachIndexed { index, resource ->
                        val selection = draftById[resource.resourceId]
                        val checked = selection != null
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { nextChecked ->
                                        draftSelections = if (nextChecked) {
                                            val nextSortIndex = draftSelections.size
                                            draftSelections + ResourceSelectionState(
                                                resourceId = resource.resourceId,
                                                active = resource.enabled,
                                                sortIndex = nextSortIndex,
                                            )
                                        } else {
                                            draftSelections.filterNot { it.resourceId == resource.resourceId }
                                        }
                                    },
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = resource.name.ifBlank { resource.resourceId },
                                        color = MonochromeUi.textPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = resource.description.ifBlank { resource.source },
                                        color = MonochromeUi.textSecondary,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                            selection?.let { selectedSelection ->
                                ConfigToggleField(
                                    title = stringResource(R.string.config_resource_projection_active),
                                    subtitle = "",
                                    checked = selectedSelection.active,
                                    onCheckedChange = { active ->
                                        draftSelections = draftSelections.replaceSelection(
                                            selectedSelection.copy(active = active),
                                        )
                                    },
                                )
                                if (kind == ResourceCenterKind.SKILL) {
                                    OutlinedTextField(
                                        value = selectedSelection.priority.toString(),
                                        onValueChange = { value ->
                                            draftSelections = draftSelections.replaceSelection(
                                                selectedSelection.copy(priority = value.filter { it.isDigit() || it == '-' }.take(4).toIntOrNull() ?: 0),
                                            )
                                        },
                                        label = { Text(stringResource(R.string.config_skill_priority)) },
                                        modifier = Modifier.fillMaxWidth(),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        colors = monochromeOutlinedTextFieldColors(),
                                    )
                                }
                            }
                        }
                        if (index != resources.lastIndex) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp),
                                color = MonochromeUi.divider,
                            ) {}
                        }
                    }
            }
        },
        confirmButton = {
            OutlinedButton(
                onClick = {
                    onSave(draftSelections.mapIndexed { index, selection -> selection.copy(sortIndex = index) })
                },
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

@Composable
private fun UnsavedConfigExitDialog(
    onDismiss: () -> Unit,
    onSaveAndContinue: () -> Unit,
    onDiscardAndContinue: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MonochromeUi.cardBackground,
        titleContentColor = MonochromeUi.textPrimary,
        textContentColor = MonochromeUi.textSecondary,
        title = { Text(stringResource(R.string.config_unsaved_exit_title)) },
        text = { Text(stringResource(R.string.config_unsaved_exit_message)) },
        confirmButton = {
            OutlinedButton(onClick = onSaveAndContinue) {
                Text(stringResource(R.string.config_unsaved_exit_save))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDiscardAndContinue) {
                    Text(stringResource(R.string.config_unsaved_exit_discard))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        },
    )
}

private enum class ConfigPendingExit {
    Back,
    GeofenceRules,
    ResourceCenter,
}

private data class ResourceSelectionState(
    val resourceId: String,
    val active: Boolean,
    val priority: Int = 0,
    val sortIndex: Int = 0,
)

private fun List<ResourceSelectionState>.replaceSelection(
    next: ResourceSelectionState,
): List<ResourceSelectionState> {
    return map { selection ->
        if (selection.resourceId == next.resourceId) next else selection
    }
}

private fun initialResourceSelections(
    profile: ConfigProfile,
    resources: List<ResourceCenterItem>,
    projections: List<ConfigResourceProjection>,
    kind: ResourceCenterKind,
): List<ResourceSelectionState> {
    val resourceIds = resources.asSequence()
        .filter { it.kind == kind }
        .map { it.resourceId }
        .toSet()
    val stored = projections
        .filter { it.configId == profile.id && it.kind == kind && it.resourceId in resourceIds }
        .sortedWith(compareBy<ConfigResourceProjection> { it.sortIndex }.thenBy { it.resourceId })
    if (stored.isNotEmpty()) {
        return stored
            .filterNot { it.isRemovedProjection() }
            .map { projection ->
                ResourceSelectionState(
                    resourceId = projection.resourceId,
                    active = projection.active,
                    priority = projection.priority,
                    sortIndex = projection.sortIndex,
                )
            }
    }
    val legacy = when (kind) {
        ResourceCenterKind.MCP_SERVER -> profile.mcpServers.mapIndexedNotNull { index, entry ->
            entry.serverId.takeIf { it in resourceIds }?.let { resourceId ->
                ResourceSelectionState(
                    resourceId = resourceId,
                    active = entry.active,
                    sortIndex = index,
                )
            }
        }
        ResourceCenterKind.SKILL -> profile.skills.mapIndexedNotNull { index, entry ->
            entry.skillId.takeIf { it in resourceIds }?.let { resourceId ->
                ResourceSelectionState(
                    resourceId = resourceId,
                    active = entry.active,
                    priority = entry.priority,
                    sortIndex = index,
                )
            }
        }
        ResourceCenterKind.TOOL -> emptyList()
    }
    return legacy
}

private fun buildProjectionUpdates(
    configId: String,
    mcpSelections: List<ResourceSelectionState>,
    skillSelections: List<ResourceSelectionState>,
    existingProjections: List<ConfigResourceProjection>,
): List<ConfigResourceProjection> {
    val selected = buildList {
        mcpSelections.forEachIndexed { index, selection ->
            add(
                ConfigResourceProjection(
                    configId = configId,
                    resourceId = selection.resourceId,
                    kind = ResourceCenterKind.MCP_SERVER,
                    active = selection.active,
                    priority = 0,
                    sortIndex = index,
                ),
            )
        }
        skillSelections.forEachIndexed { index, selection ->
            add(
                ConfigResourceProjection(
                    configId = configId,
                    resourceId = selection.resourceId,
                    kind = ResourceCenterKind.SKILL,
                    active = selection.active,
                    priority = selection.priority,
                    sortIndex = index,
                ),
            )
        }
    }
    val selectedKeys = selected.map { it.kind to it.resourceId }.toSet()
    val inactiveRemoved = existingProjections
        .filter { it.configId == configId && (it.kind == ResourceCenterKind.MCP_SERVER || it.kind == ResourceCenterKind.SKILL) }
        .filterNot { it.kind to it.resourceId in selectedKeys }
        .filterNot { it.isRemovedProjection() }
        .map { it.copy(active = false, configJson = it.configJson.markProjectionRemoved()) }
    return selected + inactiveRemoved
}

private fun ConfigResourceProjection.isRemovedProjection(): Boolean {
    return parseJsonObject(configJson).optBoolean("removed", false)
}

private fun String.markProjectionRemoved(): String {
    val json = parseJsonObject(this)
    json.put("removed", true)
    return json.toString()
}

private fun ResourceCenterItem.toMcpServerEntry(selection: ResourceSelectionState): McpServerEntry? {
    if (kind != ResourceCenterKind.MCP_SERVER) return null
    val payload = parseJsonObject(payloadJson)
    val url = payload.optString("url", "").trim()
    if (url.isBlank()) return null
    return McpServerEntry(
        serverId = resourceId,
        name = name.ifBlank { resourceId },
        url = url,
        transport = payload.optString("transport", "streamable_http").ifBlank { "streamable_http" },
        command = payload.optString("command", ""),
        args = payload.optJSONArray("args").toStringList(),
        headers = payload.optJSONObject("headers").toStringMap(),
        timeoutSeconds = payload.optInt("timeoutSeconds", 30).coerceAtLeast(1),
        active = enabled && selection.active,
    )
}

private fun ResourceCenterItem.toSkillEntry(selection: ResourceSelectionState): SkillEntry? {
    if (kind != ResourceCenterKind.SKILL) return null
    return SkillEntry(
        skillId = resourceId,
        name = name.ifBlank { resourceId },
        description = description,
        content = content,
        priority = selection.priority,
        active = enabled && selection.active,
    )
}

private fun parseJsonObject(raw: String): JSONObject {
    return runCatching {
        raw.takeIf { it.isNotBlank() }?.let(::JSONObject) ?: JSONObject()
    }.getOrDefault(JSONObject())
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index -> optString(index).takeIf { it.isNotBlank() } }
}

private fun JSONObject?.toStringMap(): Map<String, String> {
    if (this == null) return emptyMap()
    return keys().asSequence().associateWith { key -> optString(key, "") }
}

private fun List<TtsVoiceReferenceAsset>.listVoiceChoicesFor(
    provider: ProviderProfile?,
): List<Pair<String, String>> {
    if (provider == null) return emptyList()
    return flatMap { asset ->
        asset.providerBindings.mapNotNull { binding ->
            if (binding.providerId == provider.id) {
                binding.voiceId to binding.displayName
            } else {
                null
            }
        }
    }.distinctBy { it.first }
}

private fun playPreviewAttachment(
    context: android.content.Context,
    attachment: com.elymbot.android.model.chat.ConversationAttachment,
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

