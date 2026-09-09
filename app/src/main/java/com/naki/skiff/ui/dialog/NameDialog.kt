package com.naki.skiff.ui.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import com.naki.skiff.R

/** Shared by "new folder", "new file" and "rename" — they differ only in title and seed. */
@Composable
fun NameDialog(
    title: String,
    initialName: String,
    confirmLabel: String,
    /** Selects the stem only, so renaming "notes.md" does not force retyping ".md". */
    selectStemOnly: Boolean = false,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val stemEnd = initialName.lastIndexOf('.').let { if (it <= 0) initialName.length else it }
    var value by remember {
        mutableStateOf(
            TextFieldValue(
                text = initialName,
                selection = if (selectStemOnly) TextRange(0, stemEnd) else TextRange(initialName.length),
            ),
        )
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val trimmed = value.text.trim()
    val error = when {
        trimmed.isEmpty() -> stringResource(R.string.error_name_empty)
        trimmed.contains('/') -> stringResource(R.string.error_name_separator)
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(stringResource(R.string.dialog_name_label)) },
                    singleLine = true,
                    isError = error != null,
                    modifier = androidx.compose.ui.Modifier.focusRequester(focusRequester),
                )
                if (error != null) {
                    SelectionContainer { Text(error) }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = error == null,
                onClick = { onConfirm(trimmed) },
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
