package com.elymbot.android.ui.persona

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.elymbot.android.feature.persona.domain.PersonaBrowseMode
import com.elymbot.android.feature.persona.domain.model.PersonaProfile
import com.elymbot.android.feature.persona.presentation.*
import com.elymbot.android.ui.app.MonochromeUi
import com.elymbot.android.ui.viewmodel.PersonaViewModel

@Composable
internal fun PersonaCatalogRoute(viewModel: PersonaViewModel, onAdd: () -> Unit, onEdit: (String) -> Unit) {
    val personas by viewModel.personas.collectAsState()
    val mode by viewModel.browseMode.collectAsState()
    var state by remember { mutableStateOf(PersonaPageUiState()) }
    LaunchedEffect(personas, mode) { state = state.copy(personas = personas, browseMode = mode) }
    val dispatch: (PersonaPageAction) -> Unit = { state = reducePersonaPage(state, it) }
    PersonaCatalogPage(state, dispatch, viewModel::setBrowseMode, onAdd, onEdit, viewModel::resolveCover)
}

@Composable
private fun PersonaCatalogPage(state: PersonaPageUiState, dispatch: (PersonaPageAction) -> Unit, onMode: (PersonaBrowseMode) -> Unit, onAdd: () -> Unit, onEdit: (String) -> Unit, resolveCover: (String) -> String?) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val filtered = filterPersonas(state.personas, state.searchQuery, state.appliedTagFilters)
    BackHandler(state.topOverlay != PersonaTopOverlay.NONE) { focus.clearFocus(); keyboard?.hide(); dispatch(PersonaPageAction.DismissOverlay) }
    Box(Modifier.fillMaxSize().background(MonochromeUi.pageBackground).clickable(enabled = state.topOverlay == PersonaTopOverlay.SEARCH) { focus.clearFocus(); keyboard?.hide(); dispatch(PersonaPageAction.DismissOverlay) }) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            PersonaTopBar(state, dispatch, onMode, onAdd)
            if (filtered.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("没有匹配的人格", color = MonochromeUi.textSecondary) }
            else if (state.browseMode == PersonaBrowseMode.IMMERSIVE_CARD) ImmersiveCatalog(filtered, state.visiblePersonaId, onEdit, resolveCover)
            else StaggeredCatalog(filtered, onEdit, resolveCover)
        }
        if (state.topOverlay == PersonaTopOverlay.FILTER) FilterDialog(state, dispatch)
    }
}

@Composable
private fun PersonaTopBar(state: PersonaPageUiState, dispatch: (PersonaPageAction) -> Unit, onMode: (PersonaBrowseMode) -> Unit, onAdd: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
        if (state.topOverlay == PersonaTopOverlay.SEARCH) {
            val requester = androidx.compose.ui.focus.FocusRequester()
            OutlinedTextField(value = state.searchQuery, onValueChange = { dispatch(PersonaPageAction.Search(it)) }, modifier = Modifier.fillMaxWidth(.75f).height(52.dp).focusRequester(requester), singleLine = true, leadingIcon = { Icon(Icons.Outlined.Search, null) }, trailingIcon = { IconButton({ dispatch(PersonaPageAction.Search("")) }) { Icon(Icons.Outlined.Close, "清空") } }, placeholder = { Text("搜索人格") }, shape = RoundedCornerShape(16.dp))
            LaunchedEffect(Unit) { requester.requestFocus() }
        } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("人格", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton({ dispatch(PersonaPageAction.OpenOverlay(PersonaTopOverlay.SEARCH)) }) { Icon(Icons.Outlined.Search, "搜索人格") }
            IconButton({ dispatch(PersonaPageAction.OpenOverlay(PersonaTopOverlay.FILTER)) }) { Icon(Icons.Outlined.Menu, "筛选人格") }
            Box { IconButton({ dispatch(PersonaPageAction.OpenOverlay(PersonaTopOverlay.MORE)) }) { Icon(Icons.Outlined.MoreVert, "更多") }
                DropdownMenu(expanded = state.topOverlay == PersonaTopOverlay.MORE, onDismissRequest = { dispatch(PersonaPageAction.DismissOverlay) }) {
                    DropdownMenuItem(text = { Text("新增人格") }, leadingIcon = { Icon(Icons.Outlined.Add, null) }, onClick = { dispatch(PersonaPageAction.DismissOverlay); onAdd() })
                    DropdownMenuItem(text = { Text(if (state.browseMode == PersonaBrowseMode.IMMERSIVE_CARD) "切换为列表模式" else "切换为沉浸模式") }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.List, null) }, onClick = { onMode(if (state.browseMode == PersonaBrowseMode.IMMERSIVE_CARD) PersonaBrowseMode.STAGGERED_GRID else PersonaBrowseMode.IMMERSIVE_CARD); dispatch(PersonaPageAction.DismissOverlay) })
                }
            }
        }
    }
}

@Composable private fun ImmersiveCatalog(personas: List<PersonaProfile>, visibleId: String?, onEdit: (String) -> Unit, resolve: (String) -> String?) {
    var index by remember(personas.map { it.id }) { mutableIntStateOf(personas.indexOfFirst { it.id == visibleId }.coerceAtLeast(0)) }
    val arrows = personaPagerArrows(personas.size, index)
    Box(Modifier.fillMaxSize().padding(bottom = 12.dp).clip(RoundedCornerShape(24.dp))) {
        Cover(personas[index], resolve, Modifier.fillMaxSize(), PersonaCoverViewport.PORTRAIT)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color.Black.copy(.86f)))))
        if (arrows.previousVisible) Arrow(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "上一个人格", Alignment.CenterStart) { index-- }
        if (arrows.nextVisible) Arrow(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "下一个人格", Alignment.CenterEnd) { index++ }
        Column(Modifier.align(Alignment.BottomStart).padding(18.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(personas[index].name, color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            TagRow(personas[index].tags, dark = true)
            OutlinedButton(onClick = { onEdit(personas[index].id) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Edit, null); Spacer(Modifier.width(8.dp)); Text("编辑人格") }
            if (personas.size > 1) Text("${index + 1} / ${personas.size}", color = Color.White.copy(.72f), modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable private fun Arrow(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, alignment: Alignment, onClick: () -> Unit) { Box(Modifier.fillMaxSize(), contentAlignment = alignment) { FilledIconButton(onClick, modifier = Modifier.padding(8.dp).size(48.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(.4f))) { Icon(icon, description, tint = Color.White) } } }

@Composable private fun StaggeredCatalog(personas: List<PersonaProfile>, onEdit: (String) -> Unit, resolve: (String) -> String?) {
    val rows = personas.chunked(2)
    LazyColumn(contentPadding = PaddingValues(bottom = 80.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { itemsIndexed(rows) { _, row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
            GridCard(row[0], Modifier.weight(1f), onEdit, resolve)
            if (row.size > 1) GridCard(row[1], Modifier.weight(1f).padding(top = 68.dp), onEdit, resolve) else Spacer(Modifier.weight(1f))
        }
    } }
}

@Composable private fun GridCard(persona: PersonaProfile, modifier: Modifier, onEdit: (String) -> Unit, resolve: (String) -> String?) { Surface(modifier.aspectRatio(1f), shape = RoundedCornerShape(20.dp), color = MonochromeUi.cardBackground) { Column { Box(Modifier.fillMaxWidth().weight(.64f)) { Cover(persona, resolve, Modifier.fillMaxSize(), PersonaCoverViewport.SQUARE); FilledIconButton({ onEdit(persona.id) }, Modifier.align(Alignment.TopEnd).padding(8.dp).size(40.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(.55f))) { Icon(Icons.Outlined.Edit, "编辑人格", tint = Color.White) } }; Column(Modifier.fillMaxWidth().weight(.36f).padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text(persona.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); TagRow(persona.tags) } } } }

@Composable private fun Cover(persona: PersonaProfile, resolve: (String) -> String?, modifier: Modifier, viewport: PersonaCoverViewport) { val path = persona.cover?.assetRef?.let(resolve); val bitmap = remember(path) { path?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() } }; val policy = personaCoverRenderPolicy(persona.cover, viewport); val alignment = BiasAlignment(policy.centerX * 2f - 1f, policy.centerY * 2f - 1f); if (bitmap != null) Image(bitmap, null, modifier.graphicsLayer { scaleX = policy.scale; scaleY = policy.scale }, contentScale = ContentScale.Crop, alignment = alignment) else Box(modifier.background(Brush.linearGradient(listOf(Color(0xFF8B83B8), Color(0xFF4D5E7A)))), contentAlignment = Alignment.Center) { Text(persona.name.take(1).uppercase(), color = Color.White, style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold) } }

@Composable private fun TagRow(tags: List<String>, dark: Boolean = false) { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { tags.take(3).forEach { Surface(shape = RoundedCornerShape(8.dp), color = if (dark) Color.White.copy(.16f) else MonochromeUi.mutedSurface) { Text(it, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (dark) Color.White else MonochromeUi.textSecondary, style = MaterialTheme.typography.labelSmall) } } } }

@Composable private fun FilterDialog(state: PersonaPageUiState, dispatch: (PersonaPageAction) -> Unit) { val tags = state.personas.flatMap { it.tags }.filter { it.isNotBlank() }.distinct(); AlertDialog(onDismissRequest = { dispatch(PersonaPageAction.DismissOverlay) }, title = { Text("筛选标签") }, text = { Column(Modifier.fillMaxWidth().height(280.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) { tags.forEach { tag -> FilterChip(selected = tag in state.pendingTagFilters, onClick = { dispatch(PersonaPageAction.TogglePendingFilter(tag)) }, label = { Text(tag) }) } } }, dismissButton = { TextButton({ dispatch(PersonaPageAction.ResetPendingFilters) }) { Text("重置") } }, confirmButton = { Button({ dispatch(PersonaPageAction.ApplyPendingFilters) }) { Text("确定") } }, shape = RoundedCornerShape(22.dp)) }
