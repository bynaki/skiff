package com.naki.skiff.ui.pane

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.naki.skiff.R
import com.naki.skiff.data.SourceDescriptor
import com.naki.skiff.fs.FsPath
import com.naki.skiff.fs.SourceId

/**
 * Source dropdown + breadcrumb. This is the pane's identity strip: which filesystem
 * it is showing and where inside it, both switchable without leaving the pane.
 */
@Composable
fun PaneHeader(
    state: PaneUiState,
    sources: List<SourceDescriptor>,
    onSelectSource: (SourceId) -> Unit,
    onAddServer: () -> Unit,
    onEditServer: (String) -> Unit,
    onNavigate: (String) -> Unit,
    onGoUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SourceDropdown(
                currentName = state.sourceName,
                currentId = state.sourceId,
                sources = sources,
                onSelectSource = onSelectSource,
                onAddServer = onAddServer,
                onEditServer = onEditServer,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onGoUp, enabled = state.canGoUp) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_up),
                )
            }
        }
        Breadcrumb(path = state.path, onNavigate = onNavigate)
        HorizontalDivider()
    }
}

@Composable
private fun SourceDropdown(
    currentName: String,
    currentId: SourceId,
    sources: List<SourceDescriptor>,
    onSelectSource: (SourceId) -> Unit,
    onAddServer: () -> Unit,
    onEditServer: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(modifier) {
        TextButton(onClick = { expanded = true }) {
            Text(currentName, maxLines = 1, style = MaterialTheme.typography.titleSmall)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            sources.forEach { source ->
                DropdownMenuItem(
                    text = { Text(source.name) },
                    leadingIcon = {
                        if (source.id == currentId) {
                            Icon(Icons.Default.Check, contentDescription = null)
                        }
                    },
                    trailingIcon = {
                        val remote = source.id as? SourceId.Remote
                        if (remote != null) {
                            IconButton(onClick = { expanded = false; onEditServer(remote.profileId) }) {
                                Icon(Icons.Default.Edit, contentDescription = null)
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelectSource(source.id)
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.source_add_server)) },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                onClick = {
                    expanded = false
                    onAddServer()
                },
            )
        }
    }
}

@Composable
private fun Breadcrumb(path: String, onNavigate: (String) -> Unit) {
    val crumbs = FsPath.crumbs(path)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            Icons.Default.Home,
            contentDescription = FsPath.ROOT,
            modifier = Modifier
                .size(18.dp)
                .clickable { onNavigate(FsPath.ROOT) },
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        crumbs.forEach { (segment, target) ->
            Text("/", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = segment,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .clickable { onNavigate(target) }
                    .padding(horizontal = 2.dp, vertical = 4.dp),
            )
        }
    }
}
