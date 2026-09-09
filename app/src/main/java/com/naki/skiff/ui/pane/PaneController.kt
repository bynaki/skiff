package com.naki.skiff.ui.pane

import com.naki.skiff.fs.FileNode
import com.naki.skiff.fs.FileSystem
import com.naki.skiff.fs.FsPath
import com.naki.skiff.ui.logFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives one pane. The workspace owns two of these; because it only talks to
 * [FileSystem], the same controller serves the local pane and any remote pane.
 */
class PaneController(
    private val scope: CoroutineScope,
    private val errorText: (Throwable) -> String,
) {

    private val _state = MutableStateFlow(PaneUiState())
    val state: StateFlow<PaneUiState> = _state.asStateFlow()

    private var fileSystem: FileSystem? = null
    private var loadJob: Job? = null

    /** Remembered scroll position per directory, so going back lands where you left. */
    private val scrollMemory = HashMap<String, Int>()

    fun scrollIndexFor(path: String): Int = scrollMemory[path] ?: 0

    fun rememberScroll(path: String, index: Int) {
        scrollMemory[path] = index
    }

    /** Swaps this pane to another filesystem and opens its start directory. */
    fun setSource(fs: FileSystem, path: String? = null) {
        fileSystem = fs
        _state.update {
            it.copy(
                sourceId = fs.id,
                sourceName = fs.displayName,
                entries = emptyList(),
                selection = emptySet(),
                error = null,
                loading = true,
            )
        }
        loadJob?.cancel()
        loadJob = scope.launch {
            val target = if (path != null) {
                path
            } else {
                try {
                    fs.startPath()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    // Connecting is what usually fails here. Falling back to "/" and letting
                    // the listing fail again would hide the real reason, so report this one.
                    logFailure("startPath on ${fs.displayName}", e)
                    _state.update { it.copy(loading = false, error = errorText(e)) }
                    return@launch
                }
            }
            loadInto(fs, target)
        }
    }

    fun navigateTo(path: String) {
        val fs = fileSystem ?: return
        val target = FsPath.normalize(path)
        loadJob?.cancel()
        loadJob = scope.launch { loadInto(fs, target) }
    }

    /**
     * Opens a directory, or a symlink that turns out to point at one. Remote listings come
     * from readdir, which reports the link rather than its target, so a symlink has to be
     * resolved here — otherwise tapping one does nothing at all.
     */
    fun open(node: FileNode, onFile: (FileNode) -> Unit) {
        when {
            node.navigable -> navigateTo(node.path)
            node.isSymlink -> {
                val fs = fileSystem ?: return
                scope.launch {
                    val target = try {
                        fs.stat(node.path)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        logFailure("stat ${node.path}", e)
                        null
                    }
                    if (target?.isDirectory == true) navigateTo(node.path) else onFile(node)
                }
            }
            else -> onFile(node)
        }
    }

    fun goUp() {
        val current = _state.value.path
        if (!FsPath.isRoot(current)) navigateTo(FsPath.parent(current))
    }

    fun refresh() {
        val fs = fileSystem ?: return
        loadJob?.cancel()
        loadJob = scope.launch { loadInto(fs, _state.value.path, keepSelection = true) }
    }

    private suspend fun loadInto(fs: FileSystem, path: String, keepSelection: Boolean = false) {
        _state.update { it.copy(loading = true, error = null) }
        val result = try {
            Result.success(fs.list(path))
        } catch (e: CancellationException) {
            // A cancelled load is not an error the user needs to see; a newer one is running.
            throw e
        } catch (e: Throwable) {
            logFailure("list $path on ${fs.displayName}", e)
            Result.failure(e)
        }
        _state.update { current ->
            result.fold(
                onSuccess = { entries ->
                    val keptSelection =
                        if (keepSelection) current.selection.intersect(entries.map { it.path }.toSet())
                        else emptySet()
                    current.copy(
                        path = path,
                        entries = entries,
                        loading = false,
                        error = null,
                        selection = keptSelection,
                        canGoUp = !FsPath.isRoot(path),
                    )
                },
                onFailure = { throwable ->
                    // Show the path we failed on, not the one still listed underneath;
                    // otherwise the breadcrumb and the error describe different folders.
                    current.copy(
                        path = path,
                        entries = emptyList(),
                        selection = emptySet(),
                        canGoUp = !FsPath.isRoot(path),
                        loading = false,
                        error = errorText(throwable),
                    )
                },
            )
        }
    }

    fun toggleSelection(node: FileNode) {
        _state.update {
            val selection = it.selection.toMutableSet()
            if (!selection.add(node.path)) selection.remove(node.path)
            it.copy(selection = selection)
        }
    }

    fun selectAll() {
        _state.update { it.copy(selection = it.visibleEntries().map { node -> node.path }.toSet()) }
    }

    fun clearSelection() {
        _state.update { it.copy(selection = emptySet()) }
    }

    fun setSort(sort: SortOrder) = _state.update { it.copy(sort = sort) }

    fun setShowHidden(show: Boolean) = _state.update { it.copy(showHidden = show) }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun currentFileSystem(): FileSystem? = fileSystem
}

/** Directories first, then the chosen sort key — the ordering every file manager uses. */
fun PaneUiState.visibleEntries(): List<FileNode> {
    val filtered = if (showHidden) entries else entries.filter { !it.isHidden }
    val comparator = when (sort.by) {
        SortBy.NAME -> compareBy<FileNode> { it.name.lowercase() }
        SortBy.SIZE -> compareBy<FileNode> { it.size }
        SortBy.MODIFIED -> compareBy<FileNode> { it.modifiedEpochSeconds }
    }
    val directed = if (sort.ascending) comparator else comparator.reversed()
    return filtered.sortedWith(
        compareByDescending<FileNode> { it.navigable }.then(directed),
    )
}
