package com.astrbot.android.ui.screen

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.text.format.DateFormat
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astrbot.android.R
import com.astrbot.android.data.ConversationRepository
import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.chat.ConversationSession
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.ui.AppMotionTokens
import com.astrbot.android.ui.FloatingBottomNavFabBottomPadding
import com.astrbot.android.ui.MonochromeUi
import com.astrbot.android.ui.animateToItemWithAppMotion
import com.astrbot.android.ui.monochromeSwitchColors
import com.astrbot.android.ui.rememberPulsingAlpha
import com.astrbot.android.ui.rememberPulsingScale
import com.astrbot.android.ui.chat.canDeleteConversation
import com.astrbot.android.ui.chat.canRenameConversation
import com.astrbot.android.ui.chat.isQqConversation
import com.astrbot.android.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatViewModel: ChatViewModel = viewModel(),
    drawerState: DrawerState? = null,
    floatingBottomNavPadding: Dp = 0.dp,
) {
    val context = LocalContext.current
    val providers by chatViewModel.providers.collectAsState()
    val sessions by chatViewModel.sessions.collectAsState()
    val uiState by chatViewModel.uiState.collectAsState()

    val resolvedDrawerState = drawerState ?: rememberDrawerState(initialValue = androidx.compose.material3.DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val currentBot = chatViewModel.selectedBot()
    val messages = chatViewModel.sessionMessages(uiState.selectedSessionId)
    val latestMessageScrollKey = messages.lastOrNull()?.let { message ->
        "${message.id}:${message.content.length}:${message.attachments.size}"
    }
    val chatProviders = providers.filter { it.enabled && ProviderCapability.CHAT in it.capabilities }

    var showQqConversations by remember { mutableStateOf(true) }
    val visibleSessions = sessions.filter { session -> showQqConversations || !session.isQqConversation() }

    var input by remember(uiState.selectedSessionId) { mutableStateOf("") }
    var pendingAttachments by remember(uiState.selectedSessionId) { mutableStateOf(emptyList<ConversationAttachment>()) }
    var selectedSessionIds by remember { mutableStateOf(emptySet<String>()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var sessionPendingDelete by remember { mutableStateOf<ConversationSession?>(null) }
    var sessionPendingRename by remember { mutableStateOf<ConversationSession?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var expandedSwipeSessionId by remember { mutableStateOf<String?>(null) }
    val selectionMode = selectedSessionIds.isNotEmpty()

    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val attachment = uri?.let { loadConversationAttachment(context, it) }
        if (attachment != null) {
            pendingAttachments = pendingAttachments + attachment
        }
    }

    LaunchedEffect(messages.size, latestMessageScrollKey) {
        if (messages.isNotEmpty()) {
            listState.animateToItemWithAppMotion(messages.lastIndex)
        }
    }

    BackHandler(enabled = selectionMode) {
        selectedSessionIds = emptySet()
    }

    LaunchedEffect(selectionMode) {
        if (selectionMode) {
            expandedSwipeSessionId = null
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = MonochromeUi.cardBackground,
            titleContentColor = MonochromeUi.textPrimary,
            textContentColor = MonochromeUi.textSecondary,
            title = { Text(stringResource(R.string.chat_delete_conversation_title)) },
            text = { Text(stringResource(R.string.config_delete_confirm_message, selectedSessionIds.size)) },
            confirmButton = {
                TextButton(
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFB42318)),
                    onClick = {
                        selectedSessionIds.forEach(chatViewModel::deleteSession)
                        selectedSessionIds = emptySet()
                        showDeleteConfirm = false
                    },
                ) {
                    Text(stringResource(R.string.common_delete))
                }
            },
            dismissButton = {
                TextButton(
                    colors = ButtonDefaults.textButtonColors(contentColor = MonochromeUi.textSecondary),
                    onClick = { showDeleteConfirm = false },
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    sessionPendingDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { sessionPendingDelete = null },
            containerColor = MonochromeUi.cardBackground,
            titleContentColor = MonochromeUi.textPrimary,
            textContentColor = MonochromeUi.textSecondary,
            title = { Text(stringResource(R.string.chat_delete_conversation_title)) },
            text = { Text(stringResource(R.string.chat_delete_conversation_message, session.title)) },
            confirmButton = {
                TextButton(
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFB42318)),
                    onClick = {
                        chatViewModel.deleteSession(session.id)
                        sessionPendingDelete = null
                    },
                ) {
                    Text(stringResource(R.string.common_delete))
                }
            },
            dismissButton = {
                TextButton(
                    colors = ButtonDefaults.textButtonColors(contentColor = MonochromeUi.textSecondary),
                    onClick = { sessionPendingDelete = null },
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    sessionPendingRename?.let { session ->
        AlertDialog(
            onDismissRequest = { sessionPendingRename = null },
            containerColor = MonochromeUi.cardBackground,
            titleContentColor = MonochromeUi.textPrimary,
            textContentColor = MonochromeUi.textSecondary,
            title = { Text(stringResource(R.string.chat_rename_title)) },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.chat_rename_field_label)) },
                )
            },
            confirmButton = {
                TextButton(
                    colors = ButtonDefaults.textButtonColors(contentColor = MonochromeUi.textPrimary),
                    onClick = {
                        chatViewModel.renameSession(session.id, renameInput)
                        sessionPendingRename = null
                    },
                ) {
                    Text(stringResource(R.string.common_save))
                }
            },
            dismissButton = {
                TextButton(
                    colors = ButtonDefaults.textButtonColors(contentColor = MonochromeUi.textSecondary),
                    onClick = { sessionPendingRename = null },
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    ModalNavigationDrawer(
        drawerState = resolvedDrawerState,
        drawerContent = {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .background(MonochromeUi.drawerSurface),
            ) {
                ModalDrawerSheet(
                    modifier = Modifier.fillMaxWidth(),
                    drawerContainerColor = MonochromeUi.drawerSurface,
                    windowInsets = WindowInsets(0, 0, 0, 0),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MonochromeUi.drawerSurface)
                            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SessionDrawerTopToggle(
                            showQqConversations = showQqConversations,
                            onShowQqChange = { showQqConversations = it },
                        )
                        SessionDrawerHeader(
                            onCreateConversation = {
                                if (!selectionMode) {
                                    chatViewModel.createSession()
                                    scope.launch { resolvedDrawerState.close() }
                                }
                            },
                        )
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(visibleSessions, key = { it.id }) { session ->
                                val selected = if (selectionMode) {
                                    session.id in selectedSessionIds
                                } else {
                                    session.id == uiState.selectedSessionId
                                }
                                SessionDrawerItem(
                                    session = session,
                                    selected = selected,
                                    selectionMode = selectionMode,
                                    onClick = {
                                        if (selectionMode) {
                                            selectedSessionIds = selectedSessionIds.toggle(session.id)
                                        } else {
                                            chatViewModel.selectSession(session.id)
                                            scope.launch { resolvedDrawerState.close() }
                                        }
                                    },
                                    onLongPress = {
                                        if (session.canDeleteConversation()) {
                                            selectedSessionIds = selectedSessionIds.toggle(session.id)
                                        }
                                    },
                                    onTogglePinned = {
                                        chatViewModel.toggleSessionPinned(session.id)
                                    },
                                    onRename = {
                                        sessionPendingRename = session
                                        renameInput = session.title
                                    },
                                    onDelete = {
                                        sessionPendingDelete = session
                                    },
                                    swipeExpanded = expandedSwipeSessionId == session.id,
                                    onSwipeExpandedChange = { expanded ->
                                        expandedSwipeSessionId = if (expanded) session.id else null
                                    },
                                )
                            }
                        }
                    }
                }

                if (selectionMode) {
                    FloatingActionButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .navigationBarsPadding()
                            .padding(start = 20.dp, end = 20.dp, bottom = FloatingBottomNavFabBottomPadding),
                        containerColor = MonochromeUi.fabBackground,
                        contentColor = MonochromeUi.fabContent,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.config_delete_selected))
                    }
                }
            }
        },
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(MonochromeUi.pageBackground),
            containerColor = MonochromeUi.pageBackground,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                ChatInputBar(
                    input = input,
                    pendingAttachments = pendingAttachments,
                    isSending = uiState.isSending,
                    canSend = (input.isNotBlank() || pendingAttachments.isNotEmpty()) && chatProviders.isNotEmpty(),
                    floatingBottomNavPadding = floatingBottomNavPadding,
                    onInputChange = { input = it },
                    onRemoveAttachment = { attachmentId ->
                        pendingAttachments = pendingAttachments.filterNot { it.id == attachmentId }
                    },
                    onAddImage = { pickImageLauncher.launch("image/*") },
                    onSend = {
                        chatViewModel.sendMessage(input, pendingAttachments)
                        input = ""
                        pendingAttachments = emptyList()
                    },
                )
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (messages.isEmpty()) {
                    Spacer(modifier = Modifier.fillMaxSize())
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(top = 20.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(messages, key = { it.id }) { message ->
                            MessageBubble(
                                message = message,
                                assistantName = currentBot?.displayName ?: stringResource(R.string.chat_role_assistant),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionDrawerHeader(
    onCreateConversation: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            onClick = onCreateConversation,
            shape = RoundedCornerShape(16.dp),
            color = MonochromeUi.cardAltBackground,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 58.dp)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.chat_new_conversation),
                    color = MonochromeUi.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall,
                )
                androidx.compose.material3.Icon(
                    imageVector = Icons.Outlined.PlayArrow,
                    contentDescription = null,
                    tint = MonochromeUi.textPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun SessionDrawerTopToggle(
    showQqConversations: Boolean,
    onShowQqChange: (Boolean) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MonochromeUi.cardBackground,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 46.dp)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.chat_show_qq_title),
                color = MonochromeUi.textPrimary,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium,
            )
            Switch(
                checked = showQqConversations,
                onCheckedChange = onShowQqChange,
                colors = monochromeSwitchColors(),
                modifier = Modifier.scale(0.72f),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionDrawerItem(
    session: ConversationSession,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onTogglePinned: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    swipeExpanded: Boolean,
    onSwipeExpandedChange: (Boolean) -> Unit,
) {
    if (selectionMode) {
        SessionDrawerCard(
            session = session,
            selected = selected,
            selectionMode = true,
            onClick = onClick,
            onLongPress = onLongPress,
        )
        return
    }

    val actionCount = buildList {
        add("pin")
        if (session.canRenameConversation()) add("rename")
        if (session.canDeleteConversation()) add("delete")
    }.size
    val revealWidth = SessionSwipeSizing.revealWidthDp(actionCount).dp
    val density = LocalDensity.current
    val revealWidthPx = with(density) { revealWidth.toPx() }
    var rawOffsetX by remember(session.id, session.pinned, selectionMode) { mutableFloatStateOf(0f) }
    val animatedOffsetX by animateFloatAsState(targetValue = rawOffsetX, label = "sessionDrawerOffset")

    LaunchedEffect(swipeExpanded, revealWidthPx) {
        rawOffsetX = if (swipeExpanded) revealWidthPx else 0f
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(session.id, selectionMode, revealWidthPx) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        rawOffsetX = SessionSwipeMotion.applyDrag(
                            currentOffset = rawOffsetX,
                            dragAmount = dragAmount,
                            revealWidth = revealWidthPx,
                        )
                    },
                    onDragEnd = {
                        rawOffsetX = SessionSwipeMotion.settleOffset(
                            currentOffset = rawOffsetX,
                            revealWidth = revealWidthPx,
                        )
                        onSwipeExpandedChange(rawOffsetX > 0f)
                    },
                    onDragCancel = {
                        rawOffsetX = 0f
                        onSwipeExpandedChange(false)
                    },
                )
            },
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .widthIn(min = revealWidth)
                .heightIn(min = 76.dp),
            horizontalArrangement = Arrangement.spacedBy(SessionSwipeSizing.actionSpacingDp.dp, Alignment.Start),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SessionSwipeAction(
                label = stringResource(
                    if (session.pinned) R.string.chat_unpin_action else R.string.chat_pin_action,
                ),
                destructive = false,
                onClick = {
                    rawOffsetX = 0f
                    onTogglePinned()
                },
            )
            if (session.canRenameConversation()) {
                SessionSwipeAction(
                    label = stringResource(R.string.chat_rename_action),
                    destructive = false,
                    onClick = {
                        rawOffsetX = 0f
                        onRename()
                    },
                )
            }
            if (session.canDeleteConversation()) {
                SessionSwipeAction(
                    label = stringResource(R.string.common_delete),
                    destructive = true,
                    onClick = {
                        rawOffsetX = 0f
                        onDelete()
                    },
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = animatedOffsetX },
        ) {
            SessionDrawerCard(
                session = session,
                selected = selected,
                selectionMode = false,
                onClick = {
                    if (rawOffsetX != 0f) {
                        rawOffsetX = 0f
                        onSwipeExpandedChange(false)
                    } else {
                        onClick()
                    }
                },
                onLongPress = {
                    if (rawOffsetX != 0f) {
                        rawOffsetX = 0f
                        onSwipeExpandedChange(false)
                    } else {
                        onLongPress()
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionDrawerCard(
    session: ConversationSession,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MonochromeUi.cardAltBackground else MonochromeUi.cardBackground,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongPress,
                )
                .heightIn(min = 76.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = session.title,
                        color = MonochromeUi.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (session.pinned) {
                        SessionPill(label = stringResource(R.string.chat_pinned_label), selected = true)
                    }
                    if (session.isQqConversation()) {
                        SessionPill(label = stringResource(R.string.chat_message_source_qq), selected = false)
                    }
                }
                Text(
                    stringResource(R.string.chat_message_count, session.messages.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MonochromeUi.textSecondary,
                )
            }

            if (selectionMode && session.canDeleteConversation()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (selected) MonochromeUi.fabBackground else Color.Transparent,
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.5.dp,
                            color = if (selected) MonochromeUi.fabBackground else MonochromeUi.textSecondary.copy(alpha = 0.35f),
                        ),
                    ) {
                        Box(
                            modifier = Modifier.size(28.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) {
                                Icon(
                                    Icons.Outlined.Check,
                                    contentDescription = null,
                                    tint = MonochromeUi.fabContent,
                                )
                            }
                        }
                    }
                    Text(
                        text = if (selected) stringResource(R.string.common_selected) else stringResource(R.string.common_select),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) MonochromeUi.textPrimary else MonochromeUi.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionSwipeAction(
    label: String,
    destructive: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (destructive) Color(0xFFB42318) else MonochromeUi.iconButtonSurface,
    ) {
        Box(
            modifier = Modifier
                .widthIn(min = SessionSwipeSizing.actionWidthDp.dp)
                .heightIn(min = 58.dp)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                color = if (destructive) Color.White else MonochromeUi.textPrimary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ChatContextCard(
    session: ConversationSession?,
    botName: String,
    providerName: String,
    error: String,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        color = MonochromeUi.cardBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = session?.title ?: stringResource(R.string.chat_new_conversation),
                color = MonochromeUi.textPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SessionPill(label = botName.ifBlank { stringResource(R.string.chat_role_assistant) }, selected = true)
                SessionPill(label = providerName, selected = false)
                session?.takeIf { it.sessionSttEnabled }?.let {
                    SessionPill(label = stringResource(R.string.chat_stt_enabled_label), selected = false)
                }
                session?.takeIf { it.sessionTtsEnabled }?.let {
                    SessionPill(label = stringResource(R.string.chat_tts_enabled_label), selected = false)
                }
            }
            Text(
                text = if (error.isBlank()) {
                    stringResource(R.string.chat_context_hint)
                } else {
                    stringResource(R.string.chat_error_prefix, error)
                },
                color = if (error.isBlank()) MonochromeUi.textSecondary else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ChatEmptyState(
    providerName: String?,
    botName: String?,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MonochromeUi.cardBackground,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.chat_empty_hint),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MonochromeUi.textPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(
                        R.string.chat_empty_subtitle,
                        botName ?: stringResource(R.string.chat_role_assistant),
                        providerName ?: stringResource(R.string.chat_choose_model),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MonochromeUi.textSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SessionPill(label = stringResource(R.string.chat_stt_entry_label), selected = false)
                    SessionPill(label = stringResource(R.string.chat_tts_entry_label), selected = false)
                    SessionPill(label = stringResource(R.string.chat_image_entry_label), selected = false)
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: ConversationMessage,
    assistantName: String,
) {
    val isUser = message.role == "user"
    val bubbleColor = if (isUser) MonochromeUi.cardAltBackground else MonochromeUi.cardBackground

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.widthIn(max = 320.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (isUser) stringResource(R.string.chat_role_you) else assistantName,
                color = MonochromeUi.textSecondary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = DateFormat.format("HH:mm", message.timestamp).toString(),
                color = MonochromeUi.textSecondary,
                style = MaterialTheme.typography.labelSmall,
            )
        }

        Surface(
            modifier = Modifier.widthIn(max = 340.dp),
            shape = RoundedCornerShape(
                topStart = 24.dp,
                topEnd = 24.dp,
                bottomStart = if (isUser) 24.dp else 10.dp,
                bottomEnd = if (isUser) 10.dp else 24.dp,
            ),
            color = bubbleColor,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (message.content.isNotBlank()) {
                    Text(
                        text = message.content,
                        color = MonochromeUi.textPrimary,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }

                if (message.attachments.isNotEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        message.attachments.forEach { attachment ->
                            SessionPill(
                                label = attachment.fileName.ifBlank {
                                    if (attachment.type == "audio") {
                                        stringResource(R.string.chat_audio_attachment)
                                    } else {
                                        stringResource(R.string.chat_image_attachment)
                                    }
                                },
                                selected = false,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatInputBar(
    input: String,
    pendingAttachments: List<ConversationAttachment>,
    isSending: Boolean,
    canSend: Boolean,
    floatingBottomNavPadding: Dp,
    onInputChange: (String) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onAddImage: () -> Unit,
    onSend: () -> Unit,
) {
    val viewConfiguration = LocalViewConfiguration.current
    var isVoiceRecording by remember { mutableStateOf(false) }
    var recordingDurationMs by remember { mutableLongStateOf(0L) }
    val hasTypedContent = input.isNotBlank() || pendingAttachments.isNotEmpty()

    LaunchedEffect(isVoiceRecording) {
        if (!isVoiceRecording) {
            recordingDurationMs = 0L
            return@LaunchedEffect
        }
        val startAt = System.currentTimeMillis()
        while (isVoiceRecording) {
            recordingDurationMs = System.currentTimeMillis() - startAt
            delay(100L)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp + floatingBottomNavPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (isVoiceRecording) {
            VoiceRecordingIndicator(durationMs = recordingDurationMs)
        }

        if (pendingAttachments.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                pendingAttachments.forEach { attachment ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MonochromeUi.cardAltBackground,
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = attachment.fileName.ifBlank {
                                    if (attachment.type == "audio") {
                                        stringResource(R.string.chat_audio_attachment)
                                    } else {
                                        stringResource(R.string.chat_image_attachment)
                                    }
                                },
                                color = MonochromeUi.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Surface(
                                onClick = { onRemoveAttachment(attachment.id) },
                                shape = CircleShape,
                                color = Color.Transparent,
                            ) {
                                androidx.compose.material3.Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = stringResource(R.string.common_close),
                                    tint = MonochromeUi.textSecondary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            CompactCircleButton(
                icon = Icons.Outlined.Add,
                contentDescription = stringResource(R.string.chat_add_image),
                onClick = onAddImage,
                dark = false,
                size = 48.dp,
                bordered = false,
            )
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                color = MonochromeUi.elevatedSurface,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ChatPromptField(
                        value = input,
                        onValueChange = onInputChange,
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 2.dp, bottom = 2.dp),
                    )
                    if (isSending) {
                        Box(
                            modifier = Modifier.size(36.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MonochromeUi.textPrimary,
                            )
                        }
                    } else {
                        if (hasTypedContent) {
                            CompactCircleButton(
                                icon = Icons.AutoMirrored.Outlined.Send,
                                contentDescription = stringResource(R.string.chat_send),
                                onClick = onSend,
                                dark = true,
                                enabled = canSend,
                                size = 30.dp,
                                bordered = false,
                            )
                        } else {
                            VoiceRecordButton(
                                isRecording = isVoiceRecording,
                                durationMs = recordingDurationMs,
                                size = 30.dp,
                                onPressAndHold = {
                                    val releasedBeforeLongPress = try {
                                        kotlinx.coroutines.withTimeout(viewConfiguration.longPressTimeoutMillis.toLong()) {
                                            tryAwaitRelease()
                                        }
                                    } catch (_: TimeoutCancellationException) {
                                        null
                                    }
                                    if (releasedBeforeLongPress == true) return@VoiceRecordButton
                                    isVoiceRecording = true
                                    tryAwaitRelease()
                                    isVoiceRecording = false
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatPromptField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholderText: String? = null,
    placeholderStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    textStyle: TextStyle = TextStyle(
        color = MonochromeUi.textPrimary,
        fontSize = 18.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Normal,
    ),
) {
    val interactionSource = remember { MutableInteractionSource() }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        textStyle = textStyle,
        interactionSource = interactionSource,
        singleLine = false,
        maxLines = 4,
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 4.dp),
            ) {
                if (value.isBlank() && !placeholderText.isNullOrEmpty()) {
                    Text(
                        text = placeholderText,
                        color = MonochromeUi.textSecondary,
                        style = placeholderStyle,
                    )
                }
                innerTextField()
            }
        },
    )
}

@Composable
private fun VoiceRecordingIndicator(
    durationMs: Long,
) {
    val pulse = rememberPulsingAlpha(
        durationMillis = AppMotionTokens.recordingPulseMillis,
        label = "recordingPulse",
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MonochromeUi.strong,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .graphicsLayer { alpha = pulse }
                        .background(Color(0xFFFF5A5F), CircleShape),
                )
                Text(
                    text = stringResource(R.string.chat_recording_active),
                    color = MonochromeUi.strongText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = formatRecordingDuration(durationMs),
                color = MonochromeUi.strongText,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun VoiceRecordButton(
    isRecording: Boolean,
    durationMs: Long,
    size: androidx.compose.ui.unit.Dp = 56.dp,
    onPressAndHold: suspend androidx.compose.foundation.gestures.PressGestureScope.(Offset) -> Unit,
) {
    val pulseScale = rememberPulsingScale(
        durationMillis = AppMotionTokens.voiceButtonPulseMillis,
        label = "voiceButtonScale",
    )
    val haloAlpha = rememberPulsingAlpha(
        initialValue = 0.12f,
        targetValue = 0.35f,
        durationMillis = AppMotionTokens.voiceButtonPulseMillis,
        label = "voiceButtonHalo",
    )

    Box(
        modifier = Modifier
            .graphicsLayer {
                val scale = if (isRecording) pulseScale else 1f
                scaleX = scale
                scaleY = scale
            }
            .pointerInput(isRecording) {
                detectTapGestures(
                    onPress = onPressAndHold,
                )
            },
    ) {
        if (isRecording) {
            Box(
                modifier = Modifier
                    .size(size + 10.dp)
                    .background(Color(0xFF111111).copy(alpha = haloAlpha), CircleShape),
            )
        }
        Surface(
            shape = CircleShape,
            color = if (isRecording) MonochromeUi.strong else MonochromeUi.cardAltBackground,
        ) {
            Box(
                modifier = Modifier.size(size),
                contentAlignment = Alignment.Center,
            ) {
                if (isRecording) {
                    Text(
                        text = formatRecordingDuration(durationMs),
                        color = MonochromeUi.strongText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                } else {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Outlined.Mic,
                        contentDescription = stringResource(R.string.chat_add_audio),
                        tint = MonochromeUi.textPrimary,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    dark: Boolean,
    enabled: Boolean = true,
    size: androidx.compose.ui.unit.Dp = 52.dp,
    bordered: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = if (dark) MonochromeUi.strong else MonochromeUi.cardBackground,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .then(
                    if (dark || !bordered) Modifier
                    else Modifier.border(1.dp, MonochromeUi.border, CircleShape),
                ),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (dark) MonochromeUi.strongText else MonochromeUi.textPrimary,
                modifier = Modifier.size(if (size > 60.dp) 28.dp else 24.dp),
            )
        }
    }
}

@Composable
private fun EntryButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MonochromeUi.iconButtonSurface,
    ) {
        Row(
            modifier = Modifier
                .border(1.dp, MonochromeUi.border, RoundedCornerShape(18.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MonochromeUi.textPrimary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = label,
                color = MonochromeUi.textPrimary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun VoiceToggleChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (checked) MonochromeUi.strong else MonochromeUi.iconButtonSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (checked) MonochromeUi.strongText else MonochromeUi.textPrimary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = label,
                color = if (checked) MonochromeUi.strongText else MonochromeUi.textPrimary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun SessionPill(
    label: String,
    selected: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (selected) MonochromeUi.cardAltBackground else MonochromeUi.iconButtonSurface,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = MonochromeUi.textPrimary,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun Set<String>.toggle(id: String): Set<String> {
    return if (contains(id)) this - id else this + id
}

private fun loadConversationAttachment(
    context: Context,
    uri: Uri,
): ConversationAttachment? {
    val mimeType = context.contentResolver.getType(uri).orEmpty().ifBlank { "image/jpeg" }
    val attachmentType = if (mimeType.startsWith("audio/")) "audio" else "image"
    val fileName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            } else {
                null
            }
        }.orEmpty()
    val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() ?: return null

    return ConversationAttachment(
        id = UUID.randomUUID().toString(),
        type = attachmentType,
        mimeType = mimeType,
        fileName = fileName,
        base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP),
    )
}

private fun formatRecordingDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d".format(minutes, seconds)
}
