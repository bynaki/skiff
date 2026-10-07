package com.naki.skiff.code.ui

/**
 * What the palette's Reset Data offers to clear, in the order its dialog lists them. Each one goes
 * whole: there is no picking single entries out of it (2026-10-07 사용자 결정).
 */
enum class ResetItem {
    Profiles,
    HostKeys,
    Projects,
    OpenFiles,
    RecentFiles,
    Settings,
    Themes,
    PaletteRecents,
}

/** What an open file is, as far as which reset closes it goes. */
enum class OpenFileKind { Remote, Settings, Theme, Other }

object ResetData {

    /**
     * What has to go with [item]. A project names its server by profile id, so one left behind its
     * profile could not be opened again; nothing else in the store points at anything else.
     */
    fun implied(item: ResetItem): Set<ResetItem> =
        if (item == ResetItem.Profiles) setOf(ResetItem.Projects) else emptySet()

    /** [chosen] with everything it implies. */
    fun complete(chosen: Set<ResetItem>): Set<ResetItem> = chosen + chosen.flatMap(::implied)

    /**
     * Which of [files] [chosen] closes: every one for Open Files; a server's files for Profiles,
     * whose sessions go with them and leave nothing to save through; and the settings or a theme of
     * the person's own when that file is what is being reset. The projects' files stay open, as
     * removing one project from the sidebar leaves them.
     */
    fun <T> closing(chosen: Set<ResetItem>, files: List<T>, kind: (T) -> OpenFileKind): List<T> {
        if (ResetItem.OpenFiles in chosen) return files
        return files.filter {
            when (kind(it)) {
                OpenFileKind.Remote -> ResetItem.Profiles in chosen
                OpenFileKind.Settings -> ResetItem.Settings in chosen
                OpenFileKind.Theme -> ResetItem.Themes in chosen
                OpenFileKind.Other -> false
            }
        }
    }
}
