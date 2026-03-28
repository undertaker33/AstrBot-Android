package com.astrbot.android.ui.screen

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.astrbot.android.R
import com.astrbot.android.ui.MonochromeUi
import com.astrbot.android.ui.monochromeOutlinedButtonBorder
import com.astrbot.android.ui.monochromeOutlinedButtonColors
import com.astrbot.android.ui.monochromeOutlinedTextFieldColors

@Composable
internal fun EditableStringListField(
    title: String,
    values: List<String>,
    itemLabel: String,
    onValuesChange: (List<String>) -> Unit,
    helperText: String = "",
) {
    LabeledField(
        title = title,
        subtitle = helperText,
    ) {
        EditableStringListEditor(
            values = values,
            itemLabel = itemLabel,
            onValuesChange = onValuesChange,
        )
    }
}

@Composable
internal fun StringListManagerField(
    title: String,
    values: List<String>,
    itemLabel: String,
    onValuesChange: (List<String>) -> Unit,
    helperText: String = "",
) {
    var showDialog by remember { mutableStateOf(false) }
    val summary = remember(values) { StringListFieldSummary.from(values) }

    LabeledField(
        title = title,
        subtitle = helperText,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (summary.count == 0) {
                    stringResource(R.string.config_list_empty)
                } else {
                    buildString {
                        append(stringResource(R.string.config_list_count, summary.count))
                        if (summary.previewValues.isNotEmpty()) {
                            append(" · ")
                            append(summary.previewValues.joinToString(", "))
                        }
                        val remaining = summary.count - summary.previewValues.size
                        if (remaining > 0) {
                            append(" ")
                            append(stringResource(R.string.config_list_preview_more, remaining))
                        }
                    }
                },
                color = MonochromeUi.textSecondary,
            )
            OutlinedButton(
                onClick = { showDialog = true },
                colors = monochromeOutlinedButtonColors(),
                border = monochromeOutlinedButtonBorder(),
            ) {
                Text(stringResource(R.string.common_manage))
            }
        }
    }

    if (showDialog) {
        StringListEditorDialog(
            title = title,
            values = values,
            itemLabel = itemLabel,
            onDismiss = { showDialog = false },
            onSave = { next ->
                onValuesChange(next)
                showDialog = false
            },
        )
    }
}

@Composable
internal fun StringListEditorDialog(
    title: String,
    values: List<String>,
    itemLabel: String,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit,
) {
    var draftValues by remember(values) { mutableStateOf(values.ifEmpty { listOf("") }) }

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
                    .heightIn(max = stringListEditorDialogScrollableMaxHeightDp().dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EditableStringListEditor(
                    values = draftValues,
                    itemLabel = itemLabel,
                    onValuesChange = { draftValues = it },
                )
            }
        },
        confirmButton = {
            OutlinedButton(
                onClick = {
                    onSave(draftValues.map { it.trim() }.filter { it.isNotBlank() })
                },
                colors = monochromeOutlinedButtonColors(),
                border = monochromeOutlinedButtonBorder(),
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                colors = monochromeOutlinedButtonColors(),
                border = monochromeOutlinedButtonBorder(),
            ) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

@Composable
internal fun EditableStringListEditor(
    values: List<String>,
    itemLabel: String,
    onValuesChange: (List<String>) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        values.forEachIndexed { index, value ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { next ->
                        onValuesChange(values.toMutableList().apply { set(index, next) })
                    },
                    label = { Text(itemLabel) },
                    modifier = Modifier.weight(1f),
                    colors = monochromeOutlinedTextFieldColors(),
                )
                IconButton(
                    onClick = {
                        onValuesChange(values.toMutableList().apply { removeAt(index) })
                    },
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.common_remove))
                }
            }
        }
        OutlinedButton(
            onClick = { onValuesChange(values + "") },
            colors = monochromeOutlinedButtonColors(),
            border = monochromeOutlinedButtonBorder(),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Text(
                text = stringResource(R.string.common_add),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

internal fun stringListEditorDialogScrollableMaxHeightDp(): Int = 360

internal fun stringListManagerUsesMonochromeManageButton(): Boolean = true
