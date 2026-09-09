package com.naki.skiff.ui.transfer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.naki.skiff.R
import com.naki.skiff.transfer.TransferJob
import com.naki.skiff.transfer.TransferStatus
import com.naki.skiff.ui.formatSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferSheet(
    jobs: List<TransferJob>,
    onCancel: (String) -> Unit,
    onClearFinished: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.transfer_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (jobs.any { it.finished }) {
                    TextButton(onClick = onClearFinished) {
                        Text(stringResource(R.string.transfer_clear_finished))
                    }
                }
            }

            if (jobs.isEmpty()) {
                Text(
                    stringResource(R.string.transfer_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(jobs, key = { it.id }) { job -> TransferRow(job, onCancel) }
                }
            }
        }
    }
}

@Composable
private fun TransferRow(job: TransferJob, onCancel: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = job.label,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
                Text(
                    text = job.statusLine(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (job.status == TransferStatus.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 2,
                )
            }
            if (!job.finished) {
                IconButton(onClick = { onCancel(job.id) }) {
                    Icon(Icons.Default.Close, stringResource(R.string.action_cancel))
                }
            }
        }
        if (!job.finished) {
            if (job.totalBytes > 0) {
                LinearProgressIndicator(
                    progress = { job.fraction },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            } else {
                // Still walking the tree: we do not know the total yet.
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun TransferJob.statusLine(): String = when (status) {
    TransferStatus.QUEUED -> stringResource(R.string.transfer_preparing)
    TransferStatus.RUNNING -> if (totalBytes > 0) {
        "${formatSize(transferredBytes)} / ${formatSize(totalBytes)}  ·  $currentFileName"
    } else {
        stringResource(R.string.transfer_preparing)
    }
    TransferStatus.DONE -> stringResource(R.string.transfer_done)
    TransferStatus.CANCELLED -> stringResource(R.string.transfer_cancelled)
    TransferStatus.FAILED -> error ?: stringResource(R.string.transfer_failed)
}
