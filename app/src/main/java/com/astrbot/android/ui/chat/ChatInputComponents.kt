package com.astrbot.android.ui.chat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astrbot.android.R
import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.ui.navigation.AppMotionTokens
import com.astrbot.android.ui.app.MonochromeUi
import com.astrbot.android.ui.common.rememberPulsingAlpha
import com.astrbot.android.ui.common.rememberPulsingScale
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import java.util.UUID

@Composable
fun ChatInputBar(
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
                                Icon(
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
                    } else if (hasTypedContent) {
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

@Composable
fun ChatPromptField(
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
fun VoiceRecordingIndicator(
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
fun VoiceRecordButton(
    isRecording: Boolean,
    durationMs: Long,
    size: Dp = 56.dp,
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
                    Icon(
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
fun CompactCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    dark: Boolean,
    enabled: Boolean = true,
    size: Dp = 52.dp,
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
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (dark) MonochromeUi.strongText else MonochromeUi.textPrimary,
                modifier = Modifier.size(if (size > 60.dp) 28.dp else 24.dp),
            )
        }
    }
}

@Composable
fun EntryButton(
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
            Icon(
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
fun VoiceToggleChip(
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
            Icon(
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
fun SessionPill(
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

fun loadConversationAttachment(
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

fun formatRecordingDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d".format(minutes, seconds)
}
