package com.astrbot.android.ui.settings
import com.astrbot.android.ui.bot.SelectionField
import com.astrbot.android.ui.common.SubPageScaffold
import com.astrbot.android.ui.voiceasset.VoiceAssetBindingCard
import com.astrbot.android.ui.voiceasset.VoiceAssetCloneCard
import com.astrbot.android.ui.voiceasset.VoiceAssetClonedVoicesHeader
import com.astrbot.android.ui.voiceasset.VoiceAssetEmptyCard
import com.astrbot.android.ui.voiceasset.VoiceAssetFaceSwitcherCard
import com.astrbot.android.ui.voiceasset.VoiceAssetImportCard
import com.astrbot.android.ui.voiceasset.VoiceAssetOverviewCard
import com.astrbot.android.ui.voiceasset.VoiceAssetReferenceAssetCard
import com.astrbot.android.ui.voiceasset.VoiceAssetReferenceLibraryHeader

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.astrbot.android.R
import com.astrbot.android.data.AppLanguage
import com.astrbot.android.data.AppPreferencesRepository
import com.astrbot.android.data.RuntimeCacheRepository
import com.astrbot.android.data.ThemeMode
import com.astrbot.android.core.runtime.audio.VoiceCloneService
import com.astrbot.android.feature.provider.runtime.ProviderRuntimePort
import com.astrbot.android.model.displayLabel
import com.astrbot.android.model.ClonedVoiceBinding
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.RuntimeAssetEntryState
import com.astrbot.android.model.RuntimeAssetId
import com.astrbot.android.model.ProviderType
import com.astrbot.android.model.TtsVoiceReferenceAsset
import com.astrbot.android.model.hasVoiceCloningSupport
import com.astrbot.android.ui.navigation.AppUiTransitionState
import com.astrbot.android.ui.app.MonochromeUi
import com.astrbot.android.ui.app.monochromeOutlinedTextFieldColors
import com.astrbot.android.ui.viewmodel.RuntimeAssetViewModel
import com.astrbot.android.ui.viewmodel.QQLoginViewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding

@HiltViewModel
class AssetScreenBindingsViewModel @Inject constructor(
    private val providerRuntimePort: ProviderRuntimePort,
) : ViewModel() {
    val providerOptions: StateFlow<List<ProviderProfile>> = providerRuntimePort.providers
    val voiceAssets: StateFlow<List<TtsVoiceReferenceAsset>> = providerRuntimePort.voiceAssets

    fun ttsAssetState(context: android.content.Context) = providerRuntimePort.ttsAssetState(context)

    fun importReferenceAudio(
        context: android.content.Context,
        sourceUri: Uri,
        name: String = "",
        assetId: String? = null,
    ) = providerRuntimePort.importReferenceAudio(
        context = context,
        sourceUri = sourceUri,
        name = name,
        assetId = assetId,
    )

    fun saveVoiceBinding(
        assetId: String,
        providerId: String,
        providerType: ProviderType,
        model: String,
        voiceId: String,
        displayName: String,
    ) {
        providerRuntimePort.saveVoiceBinding(
            assetId = assetId,
            providerId = providerId,
            providerType = providerType,
            model = model,
            voiceId = voiceId,
            displayName = displayName,
        )
    }

    fun renameVoiceBinding(assetId: String, bindingId: String, displayName: String) {
        providerRuntimePort.renameVoiceBinding(
            assetId = assetId,
            bindingId = bindingId,
            displayName = displayName,
        )
    }

    fun clearReferenceAudio(assetId: String) {
        providerRuntimePort.clearReferenceAudio(assetId)
    }

    fun deleteReferenceClip(assetId: String, clipId: String) {
        providerRuntimePort.deleteReferenceClip(assetId, clipId)
    }

    fun deleteVoiceBinding(assetId: String, bindingId: String) {
        providerRuntimePort.deleteVoiceBinding(assetId, bindingId)
    }
}

@Composable
fun AssetManagementScreen(
    onBack: () -> Unit,
    onOpenAsset: (String) -> Unit,
    assetViewModel: RuntimeAssetViewModel = hiltViewModel(),
) {
    val assetState by assetViewModel.state.collectAsState()
    val assetItems = assetState.assets

    SubPageScaffold(
        title = stringResource(R.string.nav_asset_management),
        onBack = onBack,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MonochromeUi.cardBackground,
                    tonalElevation = 0.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.asset_list_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.asset_list_desc),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                        )
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    assetItems.forEach { item ->
                        RuntimeAssetEntryCard(
                            item = item,
                            onClick = { onOpenAsset(item.catalog.id.value) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AssetDetailScreen(
    assetId: String,
    onBack: () -> Unit,
    assetViewModel: RuntimeAssetViewModel = hiltViewModel(),
    assetBindingsViewModel: AssetScreenBindingsViewModel = hiltViewModel(),
) {
    val assetState by assetViewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val providerOptions by assetBindingsViewModel.providerOptions.collectAsState()
    val voiceAssets by assetBindingsViewModel.voiceAssets.collectAsState()
    val resolvedAsset = assetState.assets.firstOrNull { it.catalog.id.value == assetId }
        ?: assetState.assets.firstOrNull()
        ?: return
    val ttsAssetState = remember(assetState.assets, assetId) {
        if (resolvedAsset.catalog.id == RuntimeAssetId.ON_DEVICE_TTS) {
            assetBindingsViewModel.ttsAssetState(context)
        } else {
            null
        }
    }
    val cloneProviders = providerOptions.filter { it.enabled && it.hasVoiceCloningSupport() }
    val referenceAssets = voiceAssets.filter { it.clips.isNotEmpty() || it.localPath.isNotBlank() }
    val clonedVoiceItems = voiceAssets.flatMap { asset ->
        asset.providerBindings.map { binding -> asset to binding }
    }
    val totalReferenceClipCount = referenceAssets.sumOf { asset -> asset.clips.size }
    val totalReferenceDurationMs = referenceAssets.sumOf { asset -> asset.clips.sumOf { clip -> clip.durationMs } }
    var referenceName by remember { mutableStateOf("") }
    var selectedImportUri by remember { mutableStateOf<Uri?>(null) }
    var selectedImportFileName by remember { mutableStateOf("") }
    var pendingImportAssetId by remember { mutableStateOf("") }
    var selectedReferenceAssetId by remember(referenceAssets) { mutableStateOf(referenceAssets.firstOrNull()?.id.orEmpty()) }
    var selectedCloneProviderId by remember(cloneProviders) { mutableStateOf(cloneProviders.firstOrNull()?.id.orEmpty()) }
    var cloneDisplayName by remember { mutableStateOf("") }
    var isImportingReferenceAudio by remember { mutableStateOf(false) }
    var isCloningVoice by remember { mutableStateOf(false) }
    var expandedReferenceAssetId by remember { mutableStateOf("") }
    var voiceAssetFace by remember { mutableStateOf("overview") }
    var renamingBindingId by remember { mutableStateOf("") }
    var renamingBindingName by remember { mutableStateOf("") }
    var lastVoiceCloneMessage by remember { mutableStateOf("") }
    var showVoiceCloneMessageDialog by remember { mutableStateOf(false) }
    val selectedReferenceAsset = referenceAssets.firstOrNull { it.id == selectedReferenceAssetId }
    val selectedCloneProvider = cloneProviders.firstOrNull { it.id == selectedCloneProviderId }
    val referenceAudioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val resolvedUri = uri ?: return@rememberLauncherForActivityResult
        val fileName = resolvedUri.lastPathSegment?.substringAfterLast('/')?.ifBlank { "selected-audio" }.orEmpty()
        if (pendingImportAssetId.isNotBlank()) {
            val targetAssetId = pendingImportAssetId
            pendingImportAssetId = ""
            scope.launch {
                isImportingReferenceAudio = true
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        assetBindingsViewModel.importReferenceAudio(
                            context = context,
                            sourceUri = resolvedUri,
                            assetId = targetAssetId,
                        )
                    }
                }
                result.onSuccess { importResult ->
                    selectedReferenceAssetId = importResult.asset.id
                    Toast.makeText(context, context.getString(R.string.voice_asset_append_success), Toast.LENGTH_LONG).show()
                }.onFailure { error ->
                    Toast.makeText(
                        context,
                        error.message ?: error.javaClass.simpleName,
                        Toast.LENGTH_LONG,
                    ).show()
                }
                isImportingReferenceAudio = false
            }
        } else {
            selectedImportUri = resolvedUri
            selectedImportFileName = fileName
            if (referenceName.isBlank()) {
                referenceName = selectedImportFileName.substringBeforeLast('.')
            }
        }
    }

    SubPageScaffold(
        title = stringResource(resolvedAsset.catalog.titleRes),
        onBack = onBack,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(innerPadding)
                .imePadding(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = innerPadding.calculateTopPadding() + 20.dp,
                end = 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 20.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (resolvedAsset.catalog.id == RuntimeAssetId.TTS_VOICE_ASSETS) {
                voiceAssetManagementItems(
                    referenceAssets = referenceAssets,
                    cloneProviders = cloneProviders,
                    clonedVoiceItems = clonedVoiceItems,
                    totalReferenceClipCount = totalReferenceClipCount,
                    totalReferenceDurationMs = totalReferenceDurationMs,
                    selectedImportFileName = selectedImportFileName,
                    referenceName = referenceName,
                    onReferenceNameChange = { referenceName = it },
                    selectedImportUri = selectedImportUri,
                    onLaunchImport = { referenceAudioPicker.launch(arrayOf("audio/*")) },
                    selectedReferenceAssetId = selectedReferenceAssetId,
                    onSelectReferenceAsset = { selectedReferenceAssetId = it },
                    selectedCloneProviderId = selectedCloneProviderId,
                    onSelectCloneProvider = { selectedCloneProviderId = it },
                    selectedReferenceAsset = selectedReferenceAsset,
                    selectedCloneProvider = selectedCloneProvider,
                    cloneDisplayName = cloneDisplayName,
                    onCloneDisplayNameChange = { cloneDisplayName = it },
                    isImportingReferenceAudio = isImportingReferenceAudio,
                    isCloningVoice = isCloningVoice,
                    expandedReferenceAssetId = expandedReferenceAssetId,
                    onToggleExpandedReferenceAsset = { assetId ->
                        expandedReferenceAssetId = if (expandedReferenceAssetId == assetId) "" else assetId
                    },
                    voiceAssetFace = voiceAssetFace,
                    onVoiceAssetFaceChange = { voiceAssetFace = it },
                    renamingBindingId = renamingBindingId,
                    renamingBindingName = renamingBindingName,
                    onRenamingBindingNameChange = { renamingBindingName = it },
                    lastVoiceCloneMessage = lastVoiceCloneMessage,
                    onImportReference = {
                        val importUri = selectedImportUri ?: return@voiceAssetManagementItems
                        scope.launch {
                            isImportingReferenceAudio = true
                            val result = runCatching {
                                withContext(Dispatchers.IO) {
                                    assetBindingsViewModel.importReferenceAudio(
                                        context = context,
                                        sourceUri = importUri,
                                        name = referenceName,
                                        assetId = null,
                                    )
                                }
                            }
                            result.onSuccess { importResult ->
                                selectedReferenceAssetId = importResult.asset.id
                                referenceName = ""
                                selectedImportUri = null
                                selectedImportFileName = ""
                                lastVoiceCloneMessage = importResult.warning ?: context.getString(R.string.voice_asset_import_success)
                                Toast.makeText(context, lastVoiceCloneMessage, Toast.LENGTH_LONG).show()
                            }.onFailure { error ->
                                lastVoiceCloneMessage = error.message ?: error.javaClass.simpleName
                                showVoiceCloneMessageDialog = true
                                Toast.makeText(
                                    context,
                                    error.message ?: error.javaClass.simpleName,
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                            isImportingReferenceAudio = false
                        }
                    },
                    onCloneVoice = {
                        val asset = selectedReferenceAsset
                        val provider = selectedCloneProvider
                        if (asset == null || provider == null) {
                            Toast.makeText(context, context.getString(R.string.voice_asset_clone_missing_selection), Toast.LENGTH_LONG).show()
                        } else {
                            scope.launch {
                                isCloningVoice = true
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        VoiceCloneService.cloneVoice(
                                            provider = provider,
                                            asset = asset,
                                            displayName = cloneDisplayName.ifBlank { asset.name },
                                        )
                                    }
                                }.onSuccess { voiceId ->
                                    assetBindingsViewModel.saveVoiceBinding(
                                        assetId = asset.id,
                                        providerId = provider.id,
                                        providerType = provider.providerType,
                                        model = provider.model,
                                        voiceId = voiceId,
                                        displayName = cloneDisplayName.ifBlank { asset.name },
                                    )
                                    cloneDisplayName = ""
                                    lastVoiceCloneMessage = context.getString(R.string.voice_asset_clone_success)
                                    Toast.makeText(context, context.getString(R.string.voice_asset_clone_success), Toast.LENGTH_LONG).show()
                                }.onFailure { error ->
                                    lastVoiceCloneMessage = error.message ?: error.javaClass.simpleName
                                    showVoiceCloneMessageDialog = true
                                    Toast.makeText(
                                        context,
                                        error.message ?: error.javaClass.simpleName,
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                                isCloningVoice = false
                            }
                        }
                    },
                    onAddClipToAsset = { assetId ->
                        pendingImportAssetId = assetId
                        referenceAudioPicker.launch(arrayOf("audio/*"))
                    },
                    onClearReferenceAudio = { assetId -> assetBindingsViewModel.clearReferenceAudio(assetId) },
                    onDeleteReferenceClip = { assetId, clipId -> assetBindingsViewModel.deleteReferenceClip(assetId, clipId) },
                    onStartRenameBinding = { bindingId, displayName ->
                        renamingBindingId = bindingId
                        renamingBindingName = displayName
                    },
                    onCancelRenameBinding = {
                        renamingBindingId = ""
                        renamingBindingName = ""
                    },
                    onSaveRenameBinding = { assetId, bindingId ->
                        assetBindingsViewModel.renameVoiceBinding(
                            assetId = assetId,
                            bindingId = bindingId,
                            displayName = renamingBindingName,
                        )
                        renamingBindingId = ""
                        renamingBindingName = ""
                    },
                    onDeleteBinding = { assetId, bindingId -> assetBindingsViewModel.deleteVoiceBinding(assetId, bindingId) },
                )
            }
            if (false && resolvedAsset.catalog.id == RuntimeAssetId.TTS_VOICE_ASSETS) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MonochromeUi.cardBackground,
                        tonalElevation = 0.dp,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.voice_asset_import_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = stringResource(R.string.voice_asset_import_desc),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                            )
                            OutlinedButton(
                                onClick = { referenceAudioPicker.launch(arrayOf("audio/*")) },
                                enabled = !isImportingReferenceAudio,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MonochromeUi.textPrimary),
                            ) {
                                Text(stringResource(R.string.voice_asset_pick_audio_action))
                            }
                            if (selectedImportFileName.isNotBlank()) {
                                Text(
                                    text = stringResource(R.string.voice_asset_selected_audio, selectedImportFileName),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                                )
                            }
                            OutlinedTextField(
                                value = referenceName,
                                onValueChange = { referenceName = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.voice_asset_name_field)) },
                                colors = monochromeOutlinedTextFieldColors(),
                            )
                            Button(
                                onClick = {
                                    val importUri = selectedImportUri ?: return@Button
                                    scope.launch {
                                        isImportingReferenceAudio = true
                                        val result = runCatching {
                                            withContext(Dispatchers.IO) {
                                                assetBindingsViewModel.importReferenceAudio(
                                                    context = context,
                                                    sourceUri = importUri,
                                                    name = referenceName,
                                                    assetId = null,
                                                )
                                            }
                                        }
                                        result.onSuccess { importResult ->
                                            selectedReferenceAssetId = importResult.asset.id
                                            referenceName = ""
                                            selectedImportUri = null
                                            selectedImportFileName = ""
                                            lastVoiceCloneMessage = ""
                                            Toast.makeText(
                                                context,
                                                importResult.warning ?: context.getString(R.string.voice_asset_import_success),
                                                Toast.LENGTH_LONG,
                                            ).show()
                                        }.onFailure { error ->
                                            lastVoiceCloneMessage = error.message ?: error.javaClass.simpleName
                                            showVoiceCloneMessageDialog = true
                                            Toast.makeText(
                                                context,
                                                error.message ?: error.javaClass.simpleName,
                                                Toast.LENGTH_LONG,
                                            ).show()
                                        }
                                        isImportingReferenceAudio = false
                                    }
                                },
                                enabled = !isImportingReferenceAudio && selectedImportUri != null,
                                colors = monochromeButtonColors(),
                            ) {
                                Text(stringResource(R.string.voice_asset_import_action))
                            }
                            if (isImportingReferenceAudio) {
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
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MonochromeUi.cardBackground,
                        tonalElevation = 0.dp,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.voice_asset_clone_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = stringResource(R.string.voice_asset_clone_desc),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                            )
                            SelectionField(
                                title = stringResource(R.string.voice_asset_reference_audio_field),
                                options = referenceAssets.map { it.id to it.name },
                                selectedId = selectedReferenceAssetId,
                                onSelect = { selectedReferenceAssetId = it },
                            )
                            SelectionField(
                                title = stringResource(R.string.voice_asset_provider_model_field),
                                options = cloneProviders.map { it.id to "${it.name} (${it.model})" },
                                selectedId = selectedCloneProviderId,
                                onSelect = { selectedCloneProviderId = it },
                            )
                            OutlinedTextField(
                                value = cloneDisplayName,
                                onValueChange = { cloneDisplayName = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.voice_asset_clone_name_field)) },
                                colors = monochromeOutlinedTextFieldColors(),
                            )
                            Button(
                                onClick = {
                                    val asset = referenceAssets.firstOrNull { it.id == selectedReferenceAssetId }
                                    val provider = cloneProviders.firstOrNull { it.id == selectedCloneProviderId }
                                    if (asset == null || provider == null) {
                                        Toast.makeText(context, context.getString(R.string.voice_asset_clone_missing_selection), Toast.LENGTH_LONG).show()
                                    } else {
                                        scope.launch {
                                            isCloningVoice = true
                                            runCatching {
                                                withContext(Dispatchers.IO) {
                                                    VoiceCloneService.cloneVoice(
                                                        provider = provider,
                                                        asset = asset,
                                                        displayName = cloneDisplayName.ifBlank { asset.name },
                                                    )
                                                }
                                            }.onSuccess { voiceId ->
                                                assetBindingsViewModel.saveVoiceBinding(
                                                    assetId = asset.id,
                                                    providerId = provider.id,
                                                    providerType = provider.providerType,
                                                    model = provider.model,
                                                    voiceId = voiceId,
                                                    displayName = cloneDisplayName.ifBlank { asset.name },
                                                )
                                                cloneDisplayName = ""
                                                lastVoiceCloneMessage = context.getString(R.string.voice_asset_clone_success)
                                                Toast.makeText(context, context.getString(R.string.voice_asset_clone_success), Toast.LENGTH_LONG).show()
                                            }.onFailure { error ->
                                                lastVoiceCloneMessage = error.message ?: error.javaClass.simpleName
                                                showVoiceCloneMessageDialog = true
                                                Toast.makeText(
                                                    context,
                                                    error.message ?: error.javaClass.simpleName,
                                                    Toast.LENGTH_LONG,
                                                ).show()
                                            }
                                            isCloningVoice = false
                                        }
                                    }
                                },
                                enabled = !isCloningVoice &&
                                    referenceAssets.isNotEmpty() &&
                                    cloneProviders.isNotEmpty() &&
                                    cloneDisplayName.isNotBlank(),
                                colors = monochromeButtonColors(),
                            ) {
                                Text(stringResource(R.string.voice_asset_clone_action))
                            }
                            if (cloneProviders.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.voice_asset_clone_provider_hint),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                                )
                            }
                            if (referenceAssets.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.voice_asset_clone_reference_hint),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                                )
                            }
                            if (isCloningVoice) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    CircularProgressIndicator(color = MonochromeUi.textPrimary)
                                }
                            }
                            if (lastVoiceCloneMessage.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = MonochromeUi.inputBackground,
                                ) {
                                    Text(
                                        text = lastVoiceCloneMessage,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        color = MonochromeUi.textSecondary,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                }
                items(items = voiceAssets, key = { asset -> asset.id }) { asset ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MonochromeUi.cardBackground,
                        tonalElevation = 0.dp,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(asset.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = stringResource(R.string.voice_asset_clip_count_value, asset.clips.size),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                            )
                            if (asset.clips.isEmpty() && asset.localPath.isBlank()) {
                                Text(
                                    text = stringResource(R.string.voice_asset_reference_removed),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                                )
                            } else if (asset.clips.isEmpty()) {
                                Text(
                                    stringResource(R.string.voice_asset_local_path_value, asset.localPath),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                                )
                            }
                            asset.clips.forEachIndexed { index, clip ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = stringResource(R.string.voice_asset_clip_title, index + 1),
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(
                                        stringResource(R.string.voice_asset_local_path_value, clip.localPath),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                                    )
                                    if (clip.durationMs > 0L) {
                                        Text(
                                            stringResource(R.string.voice_asset_duration_value, formatDuration(clip.durationMs)),
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                                        )
                                    }
                                    if (clip.sampleRateHz > 0) {
                                        Text(
                                            stringResource(R.string.voice_asset_sample_rate_value, clip.sampleRateHz),
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                                        )
                                    }
                                    TextButton(onClick = { assetBindingsViewModel.deleteReferenceClip(asset.id, clip.id) }) {
                                        Text(stringResource(R.string.voice_asset_remove_clip_action))
                                    }
                                }
                            }
                            if (asset.providerBindings.isEmpty()) {
                                Text(
                                    stringResource(R.string.voice_asset_no_cloned_voices),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                                )
                            } else {
                                asset.providerBindings.forEach { binding ->
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        if (renamingBindingId == binding.id) {
                                            OutlinedTextField(
                                                value = renamingBindingName,
                                                onValueChange = { renamingBindingName = it },
                                                modifier = Modifier.fillMaxWidth(),
                                                label = { Text(stringResource(R.string.voice_asset_clone_name_field)) },
                                                colors = monochromeOutlinedTextFieldColors(),
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                Button(
                                                    onClick = {
                                                        assetBindingsViewModel.renameVoiceBinding(
                                                            assetId = asset.id,
                                                            bindingId = binding.id,
                                                            displayName = renamingBindingName,
                                                        )
                                                        renamingBindingId = ""
                                                        renamingBindingName = ""
                                                    },
                                                    colors = monochromeButtonColors(),
                                                ) {
                                                    Text(stringResource(R.string.common_save))
                                                }
                                                OutlinedButton(
                                                    onClick = {
                                                        renamingBindingId = ""
                                                        renamingBindingName = ""
                                                    },
                                                ) {
                                                    Text(stringResource(R.string.common_cancel))
                                                }
                                            }
                                        } else {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(binding.displayName, fontWeight = FontWeight.Medium)
                                                    Text(
                                                        "${binding.providerType.displayLabel()} / ${binding.model}",
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                                                    )
                                                }
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    TextButton(
                                                        onClick = {
                                                            renamingBindingId = binding.id
                                                            renamingBindingName = binding.displayName
                                                        },
                                                    ) {
                                                        Text(stringResource(R.string.voice_asset_rename_action))
                                                    }
                                                    TextButton(onClick = { assetBindingsViewModel.deleteVoiceBinding(asset.id, binding.id) }) {
                                                        Text(stringResource(R.string.common_delete))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            OutlinedButton(
                                onClick = {
                                    pendingImportAssetId = asset.id
                                    referenceAudioPicker.launch(arrayOf("audio/*"))
                                },
                                enabled = !isImportingReferenceAudio,
                            ) {
                                Text(stringResource(R.string.voice_asset_add_clip_action))
                            }
                            OutlinedButton(onClick = { assetBindingsViewModel.clearReferenceAudio(asset.id) }) {
                                Text(stringResource(R.string.voice_asset_clear_reference_action))
                            }
                        }
                    }
                }
            }
            val resolvedTtsAssetState = ttsAssetState
            if (resolvedAsset.catalog.id == RuntimeAssetId.ON_DEVICE_TTS && resolvedTtsAssetState != null) {
                item {
                    AssetDetailSummaryCard(resolvedAsset = resolvedAsset)
                }
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MonochromeUi.cardBackground,
                        tonalElevation = 0.dp,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.asset_actions_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            TtsModelAssetSection(
                                title = "kokoro",
                                description = resolvedTtsAssetState.kokoro.details,
                                installed = resolvedTtsAssetState.kokoro.installed,
                                enabled = !resolvedAsset.busy && resolvedTtsAssetState.framework.installed,
                                onDownload = { assetViewModel.downloadOnDeviceTtsModel("kokoro") },
                                onClear = { assetViewModel.clearOnDeviceTtsModel("kokoro") },
                            )
                            if (!resolvedTtsAssetState.framework.installed) {
                                Text(
                                    text = stringResource(R.string.asset_framework_required_hint),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            } else if (resolvedAsset.catalog.id != RuntimeAssetId.TTS_VOICE_ASSETS) {
                item {
                    AssetDetailSummaryCard(resolvedAsset = resolvedAsset)
                }
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MonochromeUi.cardBackground,
                        tonalElevation = 0.dp,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.asset_actions_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = stringResource(R.string.asset_auto_detect_desc),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                            )
                            if (!resolvedAsset.catalog.actionsEnabled) {
                                Text(
                                    text = stringResource(R.string.asset_actions_coming_next_step),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Button(
                                    onClick = { assetViewModel.downloadAsset(resolvedAsset.catalog.id.value) },
                                    modifier = Modifier.weight(1f),
                                    enabled = !resolvedAsset.busy && resolvedAsset.catalog.actionsEnabled && !resolvedAsset.installed,
                                    colors = monochromeButtonColors(),
                                ) {
                                    Text(stringResource(R.string.asset_download_action))
                                }
                                OutlinedButton(
                                    onClick = { assetViewModel.clearAsset(resolvedAsset.catalog.id.value) },
                                    modifier = Modifier.weight(1f),
                                    enabled = !resolvedAsset.busy && resolvedAsset.catalog.actionsEnabled && resolvedAsset.installed,
                                    border = BorderStroke(1.dp, monochromeClearButtonBorderColor()),
                                    colors = monochromeClearButtonColors(),
                                ) {
                                    Text(stringResource(R.string.asset_clear_action))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showVoiceCloneMessageDialog && lastVoiceCloneMessage.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { showVoiceCloneMessageDialog = false },
            containerColor = MonochromeUi.cardBackground,
            titleContentColor = MonochromeUi.textPrimary,
            textContentColor = MonochromeUi.textPrimary,
            confirmButton = {
                TextButton(onClick = { showVoiceCloneMessageDialog = false }) {
                    Text(stringResource(R.string.common_close))
                }
            },
            title = { Text(stringResource(R.string.voice_asset_error_dialog_title)) },
            text = {
                Text(
                    text = lastVoiceCloneMessage,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
        )
    }
}

private fun LazyListScope.voiceAssetManagementItems(
    referenceAssets: List<TtsVoiceReferenceAsset>,
    cloneProviders: List<ProviderProfile>,
    clonedVoiceItems: List<Pair<TtsVoiceReferenceAsset, ClonedVoiceBinding>>,
    totalReferenceClipCount: Int,
    totalReferenceDurationMs: Long,
    selectedImportFileName: String,
    referenceName: String,
    onReferenceNameChange: (String) -> Unit,
    selectedImportUri: Uri?,
    onLaunchImport: () -> Unit,
    selectedReferenceAssetId: String,
    onSelectReferenceAsset: (String) -> Unit,
    selectedCloneProviderId: String,
    onSelectCloneProvider: (String) -> Unit,
    selectedReferenceAsset: TtsVoiceReferenceAsset?,
    selectedCloneProvider: ProviderProfile?,
    cloneDisplayName: String,
    onCloneDisplayNameChange: (String) -> Unit,
    isImportingReferenceAudio: Boolean,
    isCloningVoice: Boolean,
    expandedReferenceAssetId: String,
    onToggleExpandedReferenceAsset: (String) -> Unit,
    voiceAssetFace: String,
    onVoiceAssetFaceChange: (String) -> Unit,
    renamingBindingId: String,
    renamingBindingName: String,
    onRenamingBindingNameChange: (String) -> Unit,
    lastVoiceCloneMessage: String,
    onImportReference: () -> Unit,
    onCloneVoice: () -> Unit,
    onAddClipToAsset: (String) -> Unit,
    onClearReferenceAudio: (String) -> Unit,
    onDeleteReferenceClip: (String, String) -> Unit,
    onStartRenameBinding: (String, String) -> Unit,
    onCancelRenameBinding: () -> Unit,
    onSaveRenameBinding: (String, String) -> Unit,
    onDeleteBinding: (String, String) -> Unit,
) {
    item {
        VoiceAssetFaceSwitcherCard(
            currentFace = voiceAssetFace,
            onFaceChange = onVoiceAssetFaceChange,
        )
    }
    if (voiceAssetFace == "overview") item {
        VoiceAssetOverviewCard(
            referenceAssetCount = referenceAssets.size,
            totalReferenceClipCount = totalReferenceClipCount,
            clonedVoiceCount = clonedVoiceItems.size,
            totalReferenceDurationMs = totalReferenceDurationMs,
            lastVoiceCloneMessage = lastVoiceCloneMessage,
        )
    }
    if (voiceAssetFace == "import") item {
        VoiceAssetImportCard(
            selectedImportFileName = selectedImportFileName,
            referenceName = referenceName,
            onReferenceNameChange = onReferenceNameChange,
            canImport = !isImportingReferenceAudio && selectedImportUri != null,
            isImportingReferenceAudio = isImportingReferenceAudio,
            onLaunchImport = onLaunchImport,
            onImportReference = onImportReference,
        )
    }
    if (voiceAssetFace == "clone") item {
        VoiceAssetCloneCard(
            selectedReferenceAsset = selectedReferenceAsset,
            selectedCloneProvider = selectedCloneProvider,
            referenceOptions = referenceAssets.map { it.id to it.name },
            selectedReferenceAssetId = selectedReferenceAssetId,
            onSelectReferenceAsset = onSelectReferenceAsset,
            providerOptions = cloneProviders.map { it.id to "${it.name} (${it.model})" },
            selectedCloneProviderId = selectedCloneProviderId,
            onSelectCloneProvider = onSelectCloneProvider,
            cloneDisplayName = cloneDisplayName,
            onCloneDisplayNameChange = onCloneDisplayNameChange,
            canClone = !isCloningVoice &&
                referenceAssets.isNotEmpty() &&
                cloneProviders.isNotEmpty() &&
                cloneDisplayName.isNotBlank(),
            isCloningVoice = isCloningVoice,
            onCloneVoice = onCloneVoice,
        )
    }
    if (voiceAssetFace == "manage") item {
        VoiceAssetReferenceLibraryHeader()
    }
    if (voiceAssetFace == "manage" && referenceAssets.isEmpty()) {
        item {
            VoiceAssetEmptyCard(
                title = stringResource(R.string.voice_asset_empty_reference_title),
                description = stringResource(R.string.voice_asset_empty_reference_desc),
            )
        }
    } else if (voiceAssetFace == "manage") {
        items(items = referenceAssets, key = { asset -> asset.id }) { asset ->
            VoiceAssetReferenceAssetCard(
                asset = asset,
                expanded = expandedReferenceAssetId == asset.id,
                isImportingReferenceAudio = isImportingReferenceAudio,
                onToggleExpanded = { onToggleExpandedReferenceAsset(asset.id) },
                onAddClip = { onAddClipToAsset(asset.id) },
                onClearReferenceAudio = { onClearReferenceAudio(asset.id) },
                onDeleteReferenceClip = { clipId -> onDeleteReferenceClip(asset.id, clipId) },
            )
        }
    }
    item {
        VoiceAssetClonedVoicesHeader()
    }
    if (clonedVoiceItems.isEmpty()) {
        item {
            VoiceAssetEmptyCard(
                title = stringResource(R.string.voice_asset_empty_binding_title),
                description = stringResource(R.string.voice_asset_empty_binding_desc),
            )
        }
    } else {
        items(items = clonedVoiceItems, key = { (asset, binding) -> "${asset.id}-${binding.id}" }) { (asset, binding) ->
            VoiceAssetBindingCard(
                asset = asset,
                binding = binding,
                renamingBindingId = renamingBindingId,
                renamingBindingName = renamingBindingName,
                onRenamingBindingNameChange = onRenamingBindingNameChange,
                onStartRename = { onStartRenameBinding(binding.id, binding.displayName) },
                onCancelRename = onCancelRenameBinding,
                onSaveRename = { onSaveRenameBinding(asset.id, binding.id) },
                onDelete = { onDeleteBinding(asset.id, binding.id) },
            )
        }
    }
}

@Composable
private fun AssetDetailSummaryCard(
    resolvedAsset: RuntimeAssetEntryState,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MonochromeUi.cardBackground,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(resolvedAsset.catalog.titleRes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(resolvedAsset.catalog.descriptionRes),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            )
            Text(
                text = assetStatusLabel(resolvedAsset),
                fontWeight = FontWeight.Medium,
            )
            if (resolvedAsset.lastAction.isNotBlank()) {
                Text(
                    text = stringResource(R.string.asset_last_action, resolvedAsset.lastAction),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                )
            }
            Text(resolvedAsset.details, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f))
            if (resolvedAsset.busy) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.width(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = stringResource(R.string.asset_busy_hint),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                    )
                }
            }
        }
    }
}

@Composable
private fun TtsModelAssetSection(
    title: String,
    description: String,
    installed: Boolean,
    enabled: Boolean,
    onDownload: () -> Unit,
    onClear: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(
            text = description,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
        )
        Text(
            text = if (installed) stringResource(R.string.asset_status_ready) else stringResource(R.string.asset_status_not_ready),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onDownload,
                modifier = Modifier.weight(1f),
                enabled = enabled && !installed,
                colors = monochromeButtonColors(),
            ) {
                Text(stringResource(R.string.asset_download_action))
            }
            OutlinedButton(
                onClick = onClear,
                modifier = Modifier.weight(1f),
                enabled = enabled && installed,
                border = BorderStroke(1.dp, monochromeClearButtonBorderColor()),
                colors = monochromeClearButtonColors(),
            ) {
                Text(stringResource(R.string.asset_clear_action))
            }
        }
    }
}

@Composable

private fun monochromeButtonColors() = ButtonDefaults.buttonColors(
    containerColor = MonochromeUi.strong,
    contentColor = MonochromeUi.strongText,
    disabledContainerColor = MonochromeUi.border,
    disabledContentColor = MonochromeUi.textSecondary,
)

@Composable
private fun monochromeClearButtonColors() = ButtonDefaults.outlinedButtonColors(
    contentColor = if (MonochromeUi.isDarkTheme) Color(0xFFE8EDF7) else MonochromeUi.textPrimary,
    disabledContentColor = if (MonochromeUi.isDarkTheme) Color(0xFF5F6876) else MonochromeUi.textSecondary,
)

@Composable
private fun monochromeClearButtonBorderColor(): Color {
    return if (MonochromeUi.isDarkTheme) Color(0xFF546074) else MonochromeUi.border
}

@Composable

private fun RuntimeAssetEntryCard(
    item: RuntimeAssetEntryState,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MonochromeUi.cardBackground,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .background(MonochromeUi.mutedSurface, RoundedCornerShape(14.dp))
                    .padding(10.dp),
            ) {
                Icon(Icons.Outlined.CloudDownload, contentDescription = null, tint = MonochromeUi.textPrimary)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    stringResource(item.catalog.titleRes),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(item.catalog.subtitleRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                Text(
                    assetStatusLabel(item),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
            }
            Box(
                modifier = Modifier.width(24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MonochromeUi.textPrimary,
                )
            }
        }
    }
}

@Composable
private fun assetStatusLabel(item: RuntimeAssetEntryState): String {
    return if (item.installed) {
        stringResource(R.string.asset_status_ready)
    } else {
        stringResource(R.string.asset_status_not_ready)
    }
}

internal fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}

