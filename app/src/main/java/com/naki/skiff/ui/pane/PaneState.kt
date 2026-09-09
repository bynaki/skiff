package com.naki.skiff.ui.pane

import com.naki.skiff.fs.FileNode
import com.naki.skiff.fs.SourceId

enum class SortBy { NAME, SIZE, MODIFIED }

data class SortOrder(val by: SortBy = SortBy.NAME, val ascending: Boolean = true)

data class PaneUiState(
    val sourceId: SourceId = SourceId.Local,
    val sourceName: String = "",
    val path: String = "/",
    val entries: List<FileNode> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    /** Selected entry paths. Non-empty means the pane is in selection mode. */
    val selection: Set<String> = emptySet(),
    val sort: SortOrder = SortOrder(),
    val showHidden: Boolean = false,
    val canGoUp: Boolean = false,
) {
    val inSelectionMode: Boolean get() = selection.isNotEmpty()

    val selectedNodes: List<FileNode> get() = entries.filter { it.path in selection }
}
