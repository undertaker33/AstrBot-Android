package com.elymbot.android.ui.persona

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.elymbot.android.feature.persona.domain.model.PersonaCropSpec
import com.elymbot.android.feature.persona.presentation.*
import com.elymbot.android.ui.app.MonochromeUi
import com.elymbot.android.ui.viewmodel.PersonaViewModel

@Composable
internal fun PersonaEditorRoute(personaId: String, viewModel: PersonaViewModel, onBack: () -> Unit) {
    val profiles by viewModel.personas.collectAsState()
    val profile = profiles.firstOrNull { it.id == personaId } ?: return
    var form by remember(profile) { mutableStateOf(PersonaEditorForm(profile.name, profile.tags.joinToString(","), profile.systemPrompt, profile.maxContextMessages, profile.enabled)) }
    var panel by remember { mutableStateOf(PersonaEditorPanelState.EXPANDED) }
    var crop by remember { mutableStateOf<PersonaCoverCropFlow>(PersonaCoverCropFlow.Idle) }
    var error by remember { mutableStateOf<String?>(null) }
    val leave = { crop.draftIdOrNull()?.let(viewModel::discardCover); onBack() }
    BackHandler { leave() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) runCatching { viewModel.stageCover(personaId, uri.toString()) }.onSuccess { crop = PersonaCoverCropFlow.Portrait(it) }.onFailure { error = it.message }
    }
    PersonaEditorPage(profile.cover?.assetRef?.let(viewModel::resolveCover), profile.cover?.portraitCrop, form, { form = it }, panel, { panel = it }, { picker.launch("image/*") }, leave) {
        validatePersonaEditor(form).onSuccess { valid ->
            viewModel.update(profile.copy(name = valid.name, tags = valid.tags, systemPrompt = valid.systemPrompt, maxContextMessages = valid.maxContextMessages, enabled = valid.enabled)); onBack()
        }.onFailure { error = it.message }
    }
    when (val step = crop) {
        is PersonaCoverCropFlow.Portrait -> CropDialog("裁切纵向封面", step.draft.previewAssetRef, false, step.cropSpec, { crop = step.copy(cropSpec = it) }, { viewModel.discardCover(step.draft.draftId); crop = PersonaCoverCropFlow.Idle }) { crop = step.confirmPortrait() }
        is PersonaCoverCropFlow.Square -> CropDialog("裁切方形封面", step.draft.previewAssetRef, true, step.squareCrop, { crop = step.updateSquare(it) }, { crop = PersonaCoverCropFlow.Portrait(step.draft, step.portraitCrop) }) {
            runCatching { viewModel.commitCover(step.draft.draftId, step.portraitCrop, step.squareCrop) }.onSuccess { crop = PersonaCoverCropFlow.Idle }.onFailure { crop = PersonaCoverCropFlow.Failure(step.draft, step.portraitCrop, step.squareCrop, it.message ?: "封面保存失败") }
        }
        is PersonaCoverCropFlow.Failure -> AlertDialog(
            onDismissRequest = { viewModel.discardCover(step.draftIdToDiscard); crop = PersonaCoverCropFlow.Idle },
            title = { Text("封面保存失败") }, text = { Text(step.message) },
            dismissButton = { TextButton({ viewModel.discardCover(step.draftIdToDiscard); crop = PersonaCoverCropFlow.Idle }) { Text("取消") } },
            confirmButton = { Button({
                runCatching { viewModel.commitCover(step.draft.draftId, step.portraitCrop, step.squareCrop) }
                    .onSuccess { crop = PersonaCoverCropFlow.Idle }
                    .onFailure { crop = step.copy(message = it.message ?: "封面保存失败") }
            }) { Text("重试") } },
        )
        else -> Unit
    }
    error?.let { AlertDialog(onDismissRequest = { error = null }, confirmButton = { TextButton({ error = null }) { Text("确定") } }, text = { Text(it) }) }
}

@Composable private fun PersonaEditorPage(path: String?, portraitCrop: PersonaCropSpec?, form: PersonaEditorForm, onForm: (PersonaEditorForm) -> Unit, panel: PersonaEditorPanelState, onPanel: (PersonaEditorPanelState) -> Unit, onPick: () -> Unit, onBack: () -> Unit, onSave: () -> Unit) {
    val coverHeight by animateDpAsState(if (panel == PersonaEditorPanelState.EXPANDED) 230.dp else 430.dp, label = "cover")
    Column(Modifier.fillMaxSize().background(MonochromeUi.pageBackground)) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }; Text("编辑人格", Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge); TextButton(onSave) { Text("保存") } }
        Box(Modifier.fillMaxWidth().height(coverHeight)) { EditorCover(path, portraitCrop, Modifier.fillMaxSize()); FilledIconButton(onPick, Modifier.align(Alignment.TopEnd).padding(18.dp).size(52.dp)) { Icon(Icons.Outlined.Edit, "更换封面") } }
        Surface(Modifier.fillMaxSize(), shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), color = MonochromeUi.pageBackground) {
            Column(Modifier.fillMaxSize()) {
                var drag by remember { mutableFloatStateOf(0f) }
                Box(Modifier.fillMaxWidth().height(48.dp).pointerInput(panel) { detectDragGestures(onDragEnd = { onPanel(settlePersonaPanel(panel, if (panel == PersonaEditorPanelState.EXPANDED) (drag / 120f).coerceIn(0f, 1f) else (1f + drag / 120f).coerceIn(0f, 1f), 0f)); drag = 0f }) { change, amount -> change.consume(); drag += amount.y } }, contentAlignment = Alignment.Center) { Box(Modifier.width(52.dp).height(5.dp).clip(RoundedCornerShape(9.dp)).background(Color.Gray.copy(.55f))) }
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(form.name, { onForm(form.copy(name = it)) }, Modifier.fillMaxWidth(), label = { Text("人格名称") }, singleLine = true)
                    OutlinedTextField(form.tagsInput, { onForm(form.copy(tagsInput = it)) }, Modifier.fillMaxWidth(), label = { Text("标签（最多三项）") }, supportingText = { Text("${com.elymbot.android.feature.persona.domain.model.normalizePersonaTags(form.tagsInput).size} / 3") }, singleLine = true)
                    OutlinedTextField(form.systemPrompt, { onForm(form.copy(systemPrompt = it)) }, Modifier.fillMaxWidth().height(142.dp), label = { Text("系统提示词") })
                    Text("上下文消息数：${form.maxContextMessages}", fontWeight = FontWeight.SemiBold); Slider(form.maxContextMessages.toFloat(), { onForm(form.copy(maxContextMessages = it.toInt())) }, valueRange = 1f..200f, steps = 198)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("启用人格", Modifier.weight(1f), fontWeight = FontWeight.SemiBold); Switch(form.enabled, { onForm(form.copy(enabled = it)) }) }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable private fun EditorCover(path: String?, crop: PersonaCropSpec?, modifier: Modifier) { val bitmap = remember(path) { path?.let(BitmapFactory::decodeFile)?.asImageBitmap() }; val render = personaCoverRenderSpec(crop); if (bitmap != null) Image(bitmap, null, modifier.graphicsLayer { scaleX = render.zoom; scaleY = render.zoom }, contentScale = ContentScale.Crop, alignment = BiasAlignment(render.biasX, render.biasY)) else Box(modifier.background(Brush.linearGradient(listOf(Color(0xFF9690CB), Color(0xFF526786))))) }

private fun PersonaCoverCropFlow.draftIdOrNull(): String? = when (this) {
    is PersonaCoverCropFlow.Portrait -> draft.draftId
    is PersonaCoverCropFlow.Square -> draft.draftId
    is PersonaCoverCropFlow.Failure -> draftIdToDiscard
    else -> null
}

@Composable private fun CropDialog(title: String, path: String, square: Boolean, spec: PersonaCropSpec, onSpec: (PersonaCropSpec) -> Unit, onCancel: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onCancel, title = { Text(title) }, text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { val bitmap = remember(path) { BitmapFactory.decodeFile(path)?.asImageBitmap() }; Box(Modifier.fillMaxWidth().then(if (square) Modifier.aspectRatio(1f) else Modifier.aspectRatio(.72f)).clip(RoundedCornerShape(16.dp)).background(Color.DarkGray).pointerInput(spec) { detectTransformGestures { _, pan, zoom, _ -> onSpec(applyCropGesture(spec, pan.x, pan.y, size.width.toFloat(), size.height.toFloat(), zoom)) } }) { if (bitmap != null) Image(bitmap, null, Modifier.fillMaxSize().graphicsLayer { scaleX = spec.zoom; scaleY = spec.zoom }, contentScale = ContentScale.Crop, alignment = BiasAlignment(spec.centerX * 2 - 1, spec.centerY * 2 - 1)) }; Text("拖动调整位置，双指捏合缩放", style = MaterialTheme.typography.bodySmall); TextButton({ onSpec(PersonaCropSpec()) }) { Text("重置居中") } } }, dismissButton = { TextButton(onCancel) { Text(if (square) "返回" else "取消") } }, confirmButton = { Button(onConfirm) { Text(if (square) "完成" else "下一步") } })
}
