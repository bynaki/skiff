package com.naki.skiff.ui.workspace

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.naki.skiff.data.SourceDescriptor
import com.naki.skiff.data.store.ServerProfile
import com.naki.skiff.skiff
import com.naki.skiff.transfer.TransferJob
import com.naki.skiff.transfer.TransferService
import com.naki.skiff.fs.FileNode
import com.naki.skiff.fs.FileSystem
import com.naki.skiff.fs.FsPath
import com.naki.skiff.fs.SourceId
import com.naki.skiff.fs.childNames
import com.naki.skiff.R
import com.naki.skiff.fs.local.LocalStorageAccess
import com.naki.skiff.fs.sftp.HostKeyDecision
import com.naki.skiff.fs.sftp.HostKeyPrompt
import com.naki.skiff.ui.describe
import com.naki.skiff.ui.pane.PaneController
import com.naki.skiff.ui.pane.SortOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
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
    val profiles: List<ServerProfile> = emptyList(),
    /** Non-null while a server form is open; the inner value is null for "new server". */
    val editingProfile: EditingProfile? = null,
    /** Non-null while a connection is waiting on the user to accept a host key. */
    val hostKeyPrompt: HostKeyPrompt? = null,
    /** One-shot message for the snackbar. */
    val message: String? = null,
)

/** Wrapper so "editing nothing" and "creating a new server" stay distinguishable. */
data class EditingProfile(val profile: ServerProfile?)

class WorkspaceViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application
    private val container = application.skiff
    private val store = container.store
    private val registry = container.registry

    val transfers = container.transferQueue.jobs

    private val _state = MutableStateFlow(WorkspaceUiState())
    val state: StateFlow<WorkspaceUiState> = _state.asStateFlow()

    val paneA = PaneController(viewModelScope) { context.describe(it) }
    val paneB = PaneController(viewModelScope) { context.describe(it) }

    init {
        viewModelScope.launch {
            store.profiles.collectLatest { profiles ->
                _state.update {
                    it.copy(profiles = profiles, sources = registry.descriptors())
                }
            }
        }
        // The prompt is owned by the process, not this screen, so a transfer that meets an
        // unknown host key in the background still gets answered when the UI comes back.
        viewModelScope.launch {
            container.hostKeyPrompter.pending.collectLatest { prompt ->
                _state.update { it.copy(hostKeyPrompt = prompt) }
            }
        }
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
        val fs = runCatching { registry.get(id) }.getOrElse { throwable ->
            _state.update { it.copy(message = context.describe(throwable)) }
            return
        }
        controller(side).setSource(fs)
        if (id is SourceId.Remote) {
            viewModelScope.launch {
                store.touchProfile(id.profileId, System.currentTimeMillis() / 1000)
            }
        }
    }

    // ---- server profiles -------------------------------------------------

    fun startAddServer() = _state.update { it.copy(editingProfile = EditingProfile(null)) }

    fun startEditServer(profile: ServerProfile) =
        _state.update { it.copy(editingProfile = EditingProfile(profile)) }

    fun cancelEditServer() = _state.update { it.copy(editingProfile = null) }

    fun saveProfile(profile: ServerProfile) {
        viewModelScope.launch {
            store.upsertProfile(profile)
            // Credentials or host may have changed, so the cached connection is stale.
            registry.invalidate(SourceId.Remote(profile.id))
            _state.update { it.copy(editingProfile = null) }
        }
    }

    fun deleteProfile(profile: ServerProfile) {
        viewModelScope.launch {
            registry.invalidate(SourceId.Remote(profile.id))
            store.deleteProfile(profile.id)
            _state.update { it.copy(editingProfile = null) }
            // Any pane still showing the deleted server falls back to the device.
            for (side in PaneSide.entries) {
                if (controller(side).state.value.sourceId == SourceId.Remote(profile.id)) {
                    selectSource(side, SourceId.Local)
                }
            }
        }
    }

    fun respondToHostKey(decision: HostKeyDecision) {
        container.hostKeyPrompter.respond(decision)
    }

    // ---- transfers -------------------------------------------------------

    /**
     * Sends the active pane's selection to the other pane. This is the payoff of the
     * FileSystem abstraction: the same call covers device to server, server to device and
     * server to server without branching.
     */
    fun transferToOtherPane(move: Boolean) {
        if (!_state.value.splitEnabled) {
            _state.update { it.copy(message = context.getString(R.string.error_split_required)) }
            return
        }
        val fromSide = _state.value.activeSide
        val toSide = if (fromSide == PaneSide.A) PaneSide.B else PaneSide.A
        val from = controller(fromSide).state.value
        val to = controller(toSide).state.value
        val paths = from.selection.toList()
        if (paths.isEmpty()) return

        container.transferQueue.enqueue(
            TransferJob(
                sourceId = from.sourceId,
                destinationId = to.sourceId,
                sourcePaths = paths,
                destinationDir = to.path,
                move = move,
            ),
        )
        TransferService.start(context)
        controller(fromSide).clearSelection()

        // Refresh both sides once the job leaves the queue, so the result is visible.
        viewModelScope.launch {
            container.transferQueue.jobs.collectLatest { jobs ->
                if (jobs.none { !it.finished }) {
                    controller(toSide).refresh()
                    if (move) controller(fromSide).refresh()
                }
            }
        }
    }

    fun cancelTransfer(jobId: String) = container.transferQueue.cancel(jobId)

    fun clearFinishedTransfers() = container.transferQueue.clearFinished()

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

    // The registry is process-scoped now: closing it here would kill a running transfer.

}
