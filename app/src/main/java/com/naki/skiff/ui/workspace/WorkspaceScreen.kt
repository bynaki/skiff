package com.naki.skiff.ui.workspace

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.HorizontalSplit
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naki.skiff.R
import com.naki.skiff.fs.FileNode
import com.naki.skiff.ui.dialog.ConfirmDeleteDialog
import com.naki.skiff.ui.dialog.NameDialog
import com.naki.skiff.ui.dialog.PropertiesDialog
import com.naki.skiff.ui.pane.PaneScreen
import com.naki.skiff.ui.pane.SortBy
import com.naki.skiff.ui.pane.SortOrder
import kotlinx.coroutines.launch

private sealed interface WorkspaceDialog {
    data object NewFolder : WorkspaceDialog
    data object NewFile : WorkspaceDialog
    data class Rename(val node: FileNode) : WorkspaceDialog
    data object Delete : WorkspaceDialog
    data class Properties(val node: FileNode) : WorkspaceDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(viewModel: WorkspaceViewModel, onOpenExternally: (FileNode) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val stateA by viewModel.paneA.state.collectAsStateWithLifecycle()
    val stateB by viewModel.paneB.state.collectAsStateWithLifecycle()

    val activeSide = state.activeSide
    val activeState = if (activeSide == PaneSide.A) stateA else stateB
    val activeController = viewModel.controller(activeSide)

    var dialog by remember { mutableStateOf<WorkspaceDialog?>(null) }
    var seedName by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (activeState.inSelectionMode) {
                SelectionTopBar(
                    count = activeState.selection.size,
                    canRename = activeState.selection.size == 1,
                    onClear = { activeController.clearSelection() },
                    onSelectAll = { activeController.selectAll() },
                    onRename = {
                        activeState.selectedNodes.firstOrNull()?.let {
                            seedName = it.name
                            dialog = WorkspaceDialog.Rename(it)
                        }
                    },
                    onDelete = { dialog = WorkspaceDialog.Delete },
                    onProperties = {
                        activeState.selectedNodes.firstOrNull()?.let {
                            dialog = WorkspaceDialog.Properties(it)
                        }
                    },
                )
            } else {
                BrowseTopBar(
                    state = state,
                    showHidden = activeState.showHidden,
                    sort = activeState.sort,
                    onToggleSplit = viewModel::toggleSplit,
                    onToggleDirection = viewModel::toggleSplitDirection,
                    onRefresh = { activeController.refresh() },
                    onNewFolder = {
                        scope.launch {
                            seedName = viewModel.suggestName(activeSide, "New folder")
                            dialog = WorkspaceDialog.NewFolder
                        }
                    },
                    onNewFile = {
                        scope.launch {
                            seedName = viewModel.suggestName(activeSide, "New file.txt")
                            dialog = WorkspaceDialog.NewFile
                        }
                    },
                    onSetShowHidden = { viewModel.setShowHidden(activeSide, it) },
                    onSetSort = { viewModel.setSort(activeSide, it) },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val paneA: @Composable () -> Unit = {
                Pane(viewModel, PaneSide.A, state, stateA, onOpenExternally)
            }
            val paneB: @Composable () -> Unit = {
                Pane(viewModel, PaneSide.B, state, stateB, onOpenExternally)
            }
            if (state.splitEnabled) {
                SplitContainer(
                    direction = state.splitDirection,
                    ratio = state.splitRatio,
                    onRatioChange = viewModel::setSplitRatio,
                    first = paneA,
                    second = paneB,
                )
            } else {
                paneA()
            }
        }
    }

    when (val current = dialog) {
        null -> Unit
        WorkspaceDialog.NewFolder -> NameDialog(
            title = stringResource(R.string.dialog_new_folder_title),
            initialName = seedName,
            confirmLabel = stringResource(R.string.action_create),
            onConfirm = { dialog = null; viewModel.createFolder(activeSide, it) },
            onDismiss = { dialog = null },
        )
        WorkspaceDialog.NewFile -> NameDialog(
            title = stringResource(R.string.dialog_new_file_title),
            initialName = seedName,
            confirmLabel = stringResource(R.string.action_create),
            selectStemOnly = true,
            onConfirm = { dialog = null; viewModel.createFile(activeSide, it) },
            onDismiss = { dialog = null },
        )
        is WorkspaceDialog.Rename -> NameDialog(
            title = stringResource(R.string.dialog_rename_title),
            initialName = seedName,
            confirmLabel = stringResource(R.string.action_rename),
            selectStemOnly = true,
            onConfirm = { dialog = null; viewModel.rename(activeSide, current.node, it) },
            onDismiss = { dialog = null },
        )
        WorkspaceDialog.Delete -> ConfirmDeleteDialog(
            nodes = activeState.selectedNodes,
            onConfirm = { dialog = null; viewModel.deleteSelected(activeSide) },
            onDismiss = { dialog = null },
        )
        is WorkspaceDialog.Properties -> PropertiesDialog(
            node = current.node,
            onDismiss = { dialog = null },
        )
    }
}

@Composable
private fun Pane(
    viewModel: WorkspaceViewModel,
    side: PaneSide,
    workspace: WorkspaceUiState,
    paneState: com.naki.skiff.ui.pane.PaneUiState,
    onOpenExternally: (FileNode) -> Unit,
) {
    val controller = viewModel.controller(side)
    PaneScreen(
        state = paneState,
        sources = workspace.sources,
        // A single full-screen pane is always "active"; the border only means something when split.
        isActive = workspace.splitEnabled && workspace.activeSide == side,
        onActivate = { viewModel.setActiveSide(side) },
        onSelectSource = { viewModel.selectSource(side, it) },
        onAddServer = { /* wired up with server profiles */ },
        onNavigate = controller::navigateTo,
        onGoUp = controller::goUp,
        onOpen = { node -> if (node.navigable) controller.open(node) else onOpenExternally(node) },
        onToggleSelection = controller::toggleSelection,
        scrollIndexFor = controller::scrollIndexFor,
        onScrollChanged = controller::rememberScroll,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowseTopBar(
    state: WorkspaceUiState,
    showHidden: Boolean,
    sort: SortOrder,
    onToggleSplit: () -> Unit,
    onToggleDirection: () -> Unit,
    onRefresh: () -> Unit,
    onNewFolder: () -> Unit,
    onNewFile: () -> Unit,
    onSetShowHidden: (Boolean) -> Unit,
    onSetSort: (SortOrder) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    TopAppBar(
        title = { Text(stringResource(R.string.app_name)) },
        actions = {
            if (state.splitEnabled) {
                IconButton(onClick = onToggleDirection) {
                    Icon(
                        imageVector = if (state.splitDirection == SplitDirection.HORIZONTAL) {
                            Icons.Default.VerticalSplit
                        } else {
                            Icons.Default.HorizontalSplit
                        },
                        contentDescription = null,
                    )
                }
            }
            IconButton(onClick = onToggleSplit) {
                Icon(
                    imageVector = Icons.Default.VerticalSplit,
                    contentDescription = null,
                    tint = if (state.splitEnabled) {
                        androidx.compose.material3.MaterialTheme.colorScheme.primary
                    } else {
                        androidx.compose.material3.LocalContentColor.current
                    },
                )
            }
            IconButton(onClick = onNewFolder) {
                Icon(Icons.Default.CreateNewFolder, stringResource(R.string.action_new_folder))
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, stringResource(R.string.action_refresh))
            }
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.MoreVert, stringResource(R.string.action_more))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_new_file)) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.NoteAdd, null) },
                    onClick = { menuOpen = false; onNewFile() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_show_hidden)) },
                    leadingIcon = { if (showHidden) Icon(Icons.Default.Check, null) },
                    onClick = { onSetShowHidden(!showHidden) },
                )
                HorizontalDivider()
                Text(
                    text = stringResource(R.string.menu_sort_by),
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                SortBy.entries.forEach { by ->
                    val label = when (by) {
                        SortBy.NAME -> R.string.sort_name
                        SortBy.SIZE -> R.string.sort_size
                        SortBy.MODIFIED -> R.string.sort_modified
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(label)) },
                        leadingIcon = { if (sort.by == by) Icon(Icons.Default.Check, null) },
                        onClick = {
                            // Tapping the active key flips the direction, like a table header.
                            onSetSort(
                                if (sort.by == by) sort.copy(ascending = !sort.ascending)
                                else SortOrder(by, ascending = true),
                            )
                        },
                    )
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    count: Int,
    canRename: Boolean,
    onClear: () -> Unit,
    onSelectAll: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onProperties: () -> Unit,
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClear) {
                Icon(Icons.Default.Close, stringResource(R.string.action_clear_selection))
            }
        },
        title = { Text(stringResource(R.string.items_selected, count)) },
        actions = {
            IconButton(onClick = onSelectAll) {
                Icon(Icons.Default.SelectAll, stringResource(R.string.action_select_all))
            }
            IconButton(onClick = onRename, enabled = canRename) {
                Icon(Icons.Default.DriveFileRenameOutline, stringResource(R.string.action_rename))
            }
            IconButton(onClick = onProperties, enabled = canRename) {
                Icon(Icons.Default.Info, stringResource(R.string.action_properties))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, stringResource(R.string.action_delete))
            }
        },
    )
}
