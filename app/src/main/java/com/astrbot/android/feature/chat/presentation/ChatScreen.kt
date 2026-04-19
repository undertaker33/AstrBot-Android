package com.astrbot.android.ui.chat

import androidx.compose.ui.unit.dp

import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Menu
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.astrbot.android.R
import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.chat.ConversationSession
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.ui.navigation.AppMotionTokens
import com.astrbot.android.ui.app.FloatingBottomNavFabBottomPadding
import com.astrbot.android.ui.app.MonochromeUi
import com.astrbot.android.ui.common.animateToItemWithAppMotion
import com.astrbot.android.ui.app.chatDrawerContentTopPadding
import com.astrbot.android.ui.app.monochromeSwitchColors
import com.astrbot.android.ui.chat.ChatInputBar
import com.astrbot.android.ui.chat.EntryButton
import com.astrbot.android.ui.chat.SessionDrawerHeader
import com.astrbot.android.ui.chat.SessionDrawerItem
import com.astrbot.android.ui.chat.SessionDrawerTopToggle
import com.astrbot.android.ui.chat.SessionPill
import com.astrbot.android.ui.chat.VoiceToggleChip
import com.astrbot.android.ui.chat.loadConversationAttachment
import com.astrbot.android.ui.chat.canDeleteConversation
import com.astrbot.android.ui.chat.canRenameConversation
import com.astrbot.android.ui.chat.isQqConversation
import com.astrbot.android.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatViewModel: ChatViewModel = hiltViewModel(),
    drawerState: DrawerState? = null,
    floatingBottomNavPadding: Dp = 0.dp,
    showDrawer: Boolean = true,
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
    val latestMessageId = messages.lastOrNull()?.id
    val chatProviders = providers.filter { it.enabled && ProviderCapability.CHAT in it.capabilities }

    var showQqConversations by remember { mutableStateOf(false) }
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

    LaunchedEffect(messages.size, latestMessageId) {
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

    val content: @Composable () -> Unit = {
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

    if (!showDrawer) {
        content()
        return
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
                                    isQqConversation = session.isQqConversation(),
                                    canRename = session.canRenameConversation(),
                                    canDelete = session.canDeleteConversation(),
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
        content()
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
        shape = RoundedCornerShape(14.dp),
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
            shape = RoundedCornerShape(18.dp),
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

private fun Set<String>.toggle(id: String): Set<String> {
    return if (contains(id)) this - id else this + id
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatDrawerContent(
    chatViewModel: ChatViewModel = hiltViewModel(),
    drawerState: DrawerState,
) {
    val sessions by chatViewModel.sessions.collectAsState()
    val uiState by chatViewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    var showQqConversations by remember { mutableStateOf(false) }
    val visibleSessions = sessions.filter { session -> showQqConversations || !session.isQqConversation() }
    var selectedSessionIds by remember { mutableStateOf(emptySet<String>()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var sessionPendingDelete by remember { mutableStateOf<ConversationSession?>(null) }
    var sessionPendingRename by remember { mutableStateOf<ConversationSession?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var expandedSwipeSessionId by remember { mutableStateOf<String?>(null) }
    val selectionMode = selectedSessionIds.isNotEmpty()
    val safeDrawingPadding = WindowInsets.safeDrawing.asPaddingValues()
    val drawerTopPadding = chatDrawerContentTopPadding(safeDrawingPadding.calculateTopPadding())

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
                    .padding(start = 16.dp, end = 16.dp, top = drawerTopPadding, bottom = 14.dp),
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
                            scope.launch { drawerState.close() }
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
                            isQqConversation = session.isQqConversation(),
                            canRename = session.canRenameConversation(),
                            canDelete = session.canDeleteConversation(),
                            onClick = {
                                if (selectionMode) {
                                    selectedSessionIds = selectedSessionIds.toggle(session.id)
                                } else {
                                    chatViewModel.selectSession(session.id)
                                    scope.launch { drawerState.close() }
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
}
