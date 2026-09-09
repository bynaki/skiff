package com.naki.skiff.ui.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.naki.skiff.R
import com.naki.skiff.fs.FileNode
import com.naki.skiff.ui.formatMode
import com.naki.skiff.ui.formatSize
import com.naki.skiff.ui.formatTimestamp

@Composable
fun PropertiesDialog(node: FileNode, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(node.name) },
        text = {
            SelectionContainer {
                Column {
                    Field(stringResource(R.string.properties_path), node.path)
                    if (!node.isDirectory) {
                        Field(stringResource(R.string.properties_size), formatSize(node.size))
                    }
                    Field(
                        stringResource(R.string.properties_modified),
                        formatTimestamp(node.modifiedEpochSeconds),
                    )
                    formatMode(node.mode).takeIf { it.isNotEmpty() }?.let {
                        Field(stringResource(R.string.properties_permissions), it)
                    }
                    if (node.isSymlink) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.properties_symlink),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

@Composable
private fun Field(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(6.dp))
}
