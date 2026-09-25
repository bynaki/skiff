package com.naki.skiff.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.naki.skiff.R
import com.naki.skiff.transfer.ConflictAnswer
import com.naki.skiff.transfer.ConflictPolicy
import com.naki.skiff.transfer.ConflictPrompt

/**
 * Asks what to do with one file whose name is taken at the destination. The transfer waits
 * on the answer, so the dialog does not go away on a stray tap or back press: leaving it
 * means choosing, or cancelling the transfer outright.
 */
@Composable
fun ConflictDialog(
    prompt: ConflictPrompt,
    onAnswer: (ConflictAnswer) -> Unit,
    onCancelTransfer: () -> Unit,
) {
    // Keyed on the prompt so the checkbox starts clear for each new question.
    var applyToRest by rememberSaveable(prompt) { mutableStateOf(false) }
    val answer = { policy: ConflictPolicy -> onAnswer(ConflictAnswer(policy, applyToRest)) }

    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.conflict_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.conflict_body, prompt.fileName, prompt.destinationDir),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = applyToRest,
                            role = Role.Checkbox,
                            onValueChange = { applyToRest = it },
                        ),
                ) {
                    Checkbox(checked = applyToRest, onCheckedChange = null)
                    Text(stringResource(R.string.conflict_apply_to_rest))
                }
            }
        },
        confirmButton = {
            // Three choices do not fit one row on a narrow screen, so they wrap.
            FlowRow(horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { answer(ConflictPolicy.SKIP) }) {
                    Text(stringResource(R.string.action_skip))
                }
                TextButton(onClick = { answer(ConflictPolicy.KEEP_BOTH) }) {
                    Text(stringResource(R.string.action_keep_both))
                }
                TextButton(onClick = { answer(ConflictPolicy.OVERWRITE) }) {
                    Text(stringResource(R.string.action_overwrite))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onCancelTransfer) {
                Text(stringResource(R.string.action_cancel_transfer))
            }
        },
    )
}
