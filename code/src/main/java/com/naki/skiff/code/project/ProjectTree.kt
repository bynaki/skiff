package com.naki.skiff.code.project

import com.naki.skiff.fs.FileSystem
import com.naki.skiff.fs.FsPath

/**
 * Where an open file is in a project: what the sidebar unfolds to and marks when it opens. [path] is
 * relative to the root, as the tree names it.
 */
data class ProjectFile(val project: Project, val path: String)

/**
 * What the sidebar shows of a project: one directory at a time, listed when it is opened
 * (docs/skiffcode.spec.md "화면 메뉴").
 *
 * The page names a place in the tree by the project and a path relative to its root, never by an
 * absolute path. [resolve] is what keeps that inside the root: the page cannot send anything that
 * reaches a directory the user did not make a project of, the way [com.naki.skiff.code.ui.Folder]
 * keeps the palette's file mode to one directory.
 */
object ProjectTree {

    /** One line in a listed directory. [directory] counts a link to one, which opens like one. */
    data class Entry(val name: String, val directory: Boolean)

    /**
     * The absolute path of [relative] under [root], or null when it is not a plain relative path:
     * empty segments (so a leading `/` too), `.`, `..` and NUL are refused rather than resolved. The
     * empty string is the root itself.
     */
    fun resolve(root: String, relative: String): String? {
        if (relative.isEmpty()) return root
        val segments = relative.split(FsPath.SEPARATOR)
        if (segments.any { it.isEmpty() || it == "." || it == ".." || '\u0000' in it }) return null
        return FsPath.join(root, relative)
    }

    /** Where [path] is under [root], relative to it, or null when it is not under it. */
    fun relative(root: String, path: String): String? {
        val r = FsPath.normalize(root)
        val p = FsPath.normalize(path)
        if (!FsPath.isAncestorOrSame(r, p)) return null
        return p.removePrefix(r).trimStart(FsPath.SEPARATOR)
    }

    /**
     * The directory [relative] in the project at [root]: directories first, then files, each by name.
     * `.git` is left out (2026-10-02 사용자 결정) — it is the repository's own, never something to open
     * from here — and every other dot file stays, since `.gitignore` and `.github` are.
     */
    suspend fun list(fs: FileSystem, root: String, relative: String): List<Entry> {
        val dir = requireNotNull(resolve(root, relative)) { "not a path in the project: $relative" }
        return fs.list(dir)
            .filter { it.name != ".git" }
            .map { Entry(it.name, it.navigable) }
            .sortedWith(compareBy<Entry> { !it.directory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.name })
    }
}
