package com.naki.skiff.ui.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.naki.skiff.R
import com.naki.skiff.fs.sftp.HostKeyPrompt

/**
 * Trust-on-first-use prompt. A changed fingerprint gets a distinctly louder treatment,
 * because that is the case where clicking through actually costs the user something.
 */
@Composable
fun HostKeyDialog(
    prompt: HostKeyPrompt,
    onTrust: () -> Unit,
    onReject: () -> Unit,
) {
    val changed = prompt.previousFingerprint != null
    val target = "${prompt.host}:${prompt.port}"

    AlertDialog(
        onDismissRequest = onReject,
        title = {
            Text(
                stringResource(
                    if (changed) R.string.hostkey_title_changed else R.string.hostkey_title_new,
                ),
                color = if (changed) MaterialTheme.colorScheme.error else Color.Unspecified,
            )
        },
        text = {
            Column {
                Text(
                    stringResource(
                        if (changed) R.string.hostkey_body_changed else R.string.hostkey_body_new,
                        target,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                SelectionContainer {
                    Column {
                        Text(prompt.keyType, style = MaterialTheme.typography.labelMedium)
                        Text(prompt.fingerprint, style = MaterialTheme.typography.bodySmall)
                        prompt.previousFingerprint?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.hostkey_previous),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onTrust) { Text(stringResource(R.string.action_trust)) }
        },
        dismissButton = {
            TextButton(onClick = onReject) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
