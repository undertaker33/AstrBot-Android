package com.astrbot.android.ui.screen

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astrbot.android.runtime.RuntimeLogRepository
import com.astrbot.android.ui.MonochromeUi
import com.astrbot.android.ui.monochromeOutlinedTextFieldColors
import com.astrbot.android.ui.viewmodel.ChatViewModel
import com.astrbot.android.ui.viewmodel.ConversationViewModel

@Composable
fun LogScreen(
    conversationViewModel: ConversationViewModel = viewModel(),
    chatViewModel: ChatViewModel = viewModel(),
    showContext: Boolean,
) {
    val logs by RuntimeLogRepository.logs.collectAsState()
    val sessions by conversationViewModel.sessions.collectAsState()
    val uiState by chatViewModel.uiState.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val joinedLogs = logs.joinToString(separator = "\n")
    val contextPreview = if (sessions.isEmpty()) "" else conversationViewModel.contextPreview(uiState.selectedSessionId)
    val displayTitle = if (!showContext) "Logs" else "Context"
    val displayText = if (!showContext) {
        if (joinedLogs.isBlank()) "No logs yet." else joinedLogs
    } else {
        if (contextPreview.isBlank()) "No context preview yet." else contextPreview
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MonochromeUi.pageBackground)
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    clipboardManager.setText(AnnotatedString(displayText))
                    Toast.makeText(context, if (!showContext) "日志已复制" else "上下文已复制", Toast.LENGTH_SHORT).show()
                },
                enabled = displayText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MonochromeUi.strong,
                    contentColor = MonochromeUi.strongText,
                ),
            ) {
                Text("复制全部")
            }
            OutlinedButton(
                onClick = {
                    if (!showContext) {
                        RuntimeLogRepository.clear()
                        Toast.makeText(context, "日志已清空", Toast.LENGTH_SHORT).show()
                    } else {
                        conversationViewModel.replaceMessages(uiState.selectedSessionId, emptyList())
                        Toast.makeText(context, "上下文已清空", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = if (!showContext) logs.isNotEmpty() else contextPreview.isNotBlank(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MonochromeUi.textPrimary,
                    disabledContentColor = MonochromeUi.textSecondary,
                ),
            ) {
                Text("清空")
            }
        }
        LogPanel(
            title = displayTitle,
            content = displayText,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LogPanel(
    title: String,
    content: String,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
        tonalElevation = 2.dp,
        color = MonochromeUi.cardBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MonochromeUi.textPrimary)
            SelectionContainer {
                OutlinedTextField(
                    value = content,
                    onValueChange = {},
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState),
                    readOnly = true,
                    colors = monochromeOutlinedTextFieldColors(),
                )
            }
        }
    }
}
