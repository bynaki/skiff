package com.naki.skiff.ui.workspace

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.naki.skiff.data.SourceDescriptor
import com.naki.skiff.data.SourceRegistry
import com.naki.skiff.fs.FileNode
import com.naki.skiff.fs.FileSystem
import com.naki.skiff.fs.FsPath
import com.naki.skiff.fs.SourceId
import com.naki.skiff.fs.childNames
import com.naki.skiff.fs.local.LocalStorageAccess
import com.naki.skiff.ui.describe
import com.naki.skiff.ui.pane.PaneController
import com.naki.skiff.ui.pane.SortOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PaneSide { A, B }

enum class SplitDirection { HORIZONTAL, VERTICAL }

data class WorkspaceUiState(
    val storageGranted: Boolean = false,
    val splitEnabled: Boolean = false,
    val splitDirection: SplitDirection = SplitDirection.HORIZONTAL,
    val splitRatio: Float = 0.5f,
    val activeSide: PaneSide = PaneSide.A,
    val sources: List<SourceDescriptor> = emptyList(),
    /** One-shot message for the snackbar. */
    val message: String? = null,
)

class WorkspaceViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application
    private val registry = SourceRegistry(application)

    private val _state = MutableStateFlow(WorkspaceUiState())
    val state: StateFlow<WorkspaceUiState> = _state.asStateFlow()

    val paneA = PaneController(viewModelScope) { context.describe(it) }
    val paneB = PaneController(viewModelScope) { context.describe(it) }

    init {
        _state.update { it.copy(sources = registry.descriptors()) }
        refreshStorageGrant()
    }

    fun controller(side: PaneSide): PaneController = if (side == PaneSide.A) paneA else paneB

    /**
     * Called on start and every time we come back from the Settings screen, since
     * "all files access" can only be granted out there.
     */
    fun refreshStorageGrant() {
        val granted = LocalStorageAccess.isGranted()
        val wasGranted = _state.value.storageGranted
        _state.update { it.copy(storageGranted = granted) }
        if (granted && !wasGranted) {
            paneA.setSource(registry.get(SourceId.Local))
            paneB.setSource(registry.get(SourceId.Local))
        }
    }

    fun setActiveSide(side: PaneSide) = _state.update { it.copy(activeSide = side) }

    fun toggleSplit() = _state.update { it.copy(splitEnabled = !it.splitEnabled) }

    fun toggleSplitDirection() = _state.update {
        it.copy(
            splitDirection = if (it.splitDirection == SplitDirection.HORIZONTAL) {
                SplitDirection.VERTICAL
            } else {
                SplitDirection.HORIZONTAL
            },
        )
    }

    fun setSplitRatio(ratio: Float) =
        _state.update { it.copy(splitRatio = ratio.coerceIn(0.2f, 0.8f)) }

    fun selectSource(side: PaneSide, id: SourceId) {
        controller(side).setSource(registry.get(id))
    }

    fun setSort(side: PaneSide, sort: SortOrder) = controller(side).setSort(sort)

    fun setShowHidden(side: PaneSide, show: Boolean) = controller(side).setShowHidden(show)

    // ---- file operations -------------------------------------------------

    fun createFolder(side: PaneSide, name: String) = mutate(side) { fs, directory ->
        fs.mkdir(FsPath.join(directory, name))
    }

    fun createFile(side: PaneSide, name: String) = mutate(side) { fs, directory ->
        fs.createFile(FsPath.join(directory, name))
    }

    fun rename(side: PaneSide, node: FileNode, newName: String) = mutate(side) { fs, directory ->
        fs.rename(node.path, FsPath.join(directory, newName))
    }

    fun deleteSelected(side: PaneSide) = mutate(side) { fs, _ ->
        for (node in controller(side).state.value.selectedNodes) {
            fs.delete(node.path, recursive = true)
        }
    }

    /** Default name for a new folder that will not collide with what is already there. */
    suspend fun suggestName(side: PaneSide, desired: String): String {
        val controller = controller(side)
        val fs = controller.currentFileSystem() ?: return desired
        return FsPath.uniqueName(desired, fs.childNames(controller.state.value.path))
    }

    private fun mutate(side: PaneSide, block: suspend (FileSystem, String) -> Unit) {
        val controller = controller(side)
        val fs = controller.currentFileSystem() ?: return
        viewModelScope.launch {
            runCatching { block(fs, controller.state.value.path) }
                .onFailure { throwable ->
                    _state.update { it.copy(message = context.describe(throwable)) }
                }
            controller.clearSelection()
            controller.refresh()
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    override fun onCleared() {
        registry.closeAll()
        super.onCleared()
    }
}
