package com.astrbot.android.ui.chat

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.astrbot.android.R
import com.astrbot.android.model.chat.ConversationSession
import com.astrbot.android.ui.app.MonochromeUi
import com.astrbot.android.ui.app.monochromeSwitchColors
import com.astrbot.android.ui.chat.SessionSwipeMotion
import com.astrbot.android.ui.chat.SessionSwipeSizing

@Composable
fun SessionDrawerHeader(
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
                Icon(
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
fun SessionDrawerTopToggle(
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
                modifier = Modifier.graphicsLayer {
                    scaleX = 0.72f
                    scaleY = 0.72f
                },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SessionDrawerItem(
    session: ConversationSession,
    selected: Boolean,
    selectionMode: Boolean,
    isQqConversation: Boolean,
    canRename: Boolean,
    canDelete: Boolean,
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
            isQqConversation = isQqConversation,
            canDelete = canDelete,
            onClick = onClick,
            onLongPress = onLongPress,
        )
        return
    }

    val actionCount = buildList {
        add("pin")
        if (canRename) add("rename")
        if (canDelete) add("delete")
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
            if (canRename) {
                SessionSwipeAction(
                    label = stringResource(R.string.chat_rename_action),
                    destructive = false,
                    onClick = {
                        rawOffsetX = 0f
                        onRename()
                    },
                )
            }
            if (canDelete) {
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
                isQqConversation = isQqConversation,
                canDelete = canDelete,
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
    isQqConversation: Boolean,
    canDelete: Boolean,
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
                    if (isQqConversation) {
                        SessionPill(label = stringResource(R.string.chat_message_source_qq), selected = false)
                    }
                }
                Text(
                    stringResource(R.string.chat_message_count, session.messages.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MonochromeUi.textSecondary,
                )
            }

            if (selectionMode && canDelete) {
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
