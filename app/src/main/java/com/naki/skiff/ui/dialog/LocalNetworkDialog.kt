package com.naki.skiff.ui.dialog

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.naki.skiff.R
import com.naki.skiff.fs.LocalNetworkAccess

/**
 * Explains why we are about to ask for local network access before the system prompt shows,
 * because "Skiff wants to find devices on your network" on its own reads like tracking.
 */
@Composable
fun LocalNetworkDialog(onAnswered: () -> Unit, onDismiss: () -> Unit) {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { onAnswered() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.local_network_title)) },
        text = { Text(stringResource(R.string.local_network_body)) },
        confirmButton = {
            TextButton(onClick = {
                val permission = LocalNetworkAccess.permission
                if (permission == null) onAnswered() else launcher.launch(permission)
            }) { Text(stringResource(R.string.action_allow)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
