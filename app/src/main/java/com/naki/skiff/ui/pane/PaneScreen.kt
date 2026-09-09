package com.naki.skiff.ui.pane

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.naki.skiff.R
import com.naki.skiff.data.SourceDescriptor
import com.naki.skiff.fs.FileNode
import com.naki.skiff.fs.SourceId

/**
 * One filesystem pane, whole. The workspace shows one of these full-screen, or two of
 * them side by side; nothing in here knows which of those it is.
 */
@Composable
fun PaneScreen(
    state: PaneUiState,
    sources: List<SourceDescriptor>,
    isActive: Boolean,
    onActivate: () -> Unit,
    onSelectSource: (SourceId) -> Unit,
    onAddServer: () -> Unit,
    onEditServer: (String) -> Unit,
    onNavigate: (String) -> Unit,
    onGoUp: () -> Unit,
    onOpen: (FileNode) -> Unit,
    onToggleSelection: (FileNode) -> Unit,
    scrollIndexFor: (String) -> Int,
    onScrollChanged: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val entries = remember(state.entries, state.sort, state.showHidden) { state.visibleEntries() }

    // Restore the scroll position we left this directory at.
    LaunchedEffect(state.path, state.entries) {
        val remembered = scrollIndexFor(state.path)
        if (remembered > 0 && remembered < entries.size) listState.scrollToItem(remembered)
    }
    LaunchedEffect(listState, state.path) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { index -> onScrollChanged(state.path, index) }
    }

    val activeBorder = if (isActive) {
        Modifier.border(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .then(activeBorder)
            .background(MaterialTheme.colorScheme.surface),
    ) {
        PaneHeader(
            state = state,
            sources = sources,
            onSelectSource = { onActivate(); onSelectSource(it) },
            onAddServer = onAddServer,
            onEditServer = onEditServer,
            onNavigate = { onActivate(); onNavigate(it) },
            onGoUp = { onActivate(); onGoUp() },
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.error != null -> PaneMessage(state.error)
                entries.isEmpty() && !state.loading ->
                    PaneMessage(stringResource(R.string.empty_folder))
                else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(entries, key = { it.path }) { node ->
                        FileRow(
                            node = node,
                            selected = node.path in state.selection,
                            onClick = {
                                onActivate()
                                if (state.inSelectionMode) onToggleSelection(node) else onOpen(node)
                            },
                            onLongClick = {
                                onActivate()
                                onToggleSelection(node)
                            },
                        )
                    }
                }
            }
            if (state.loading) {
                CircularProgressIndicator(
                    Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                )
            }
        }

        HorizontalDivider()
        Text(
            text = if (state.inSelectionMode) {
                stringResource(R.string.items_selected, state.selection.size)
            } else {
                stringResource(R.string.item_count, entries.size)
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun PaneMessage(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(32.dp),
        )
    }
}
