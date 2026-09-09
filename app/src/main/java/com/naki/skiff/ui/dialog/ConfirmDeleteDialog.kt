package com.naki.skiff.ui.dialog

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.naki.skiff.R
import com.naki.skiff.fs.FileNode

@Composable
fun ConfirmDeleteDialog(
    nodes: List<FileNode>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val title = if (nodes.size == 1) {
        stringResource(R.string.dialog_delete_one_title, nodes.first().name)
    } else {
        stringResource(R.string.dialog_delete_title, nodes.size)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(stringResource(R.string.dialog_delete_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(R.string.action_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
