package com.naki.skiff.code.project

import com.naki.skiff.fs.FileSystem
import com.naki.skiff.fs.FsError
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

    /** The files a walk found, relative to the root, and whether it stopped at its limit before the end. */
    data class Walk(val paths: List<String>, val truncated: Boolean)

    /**
     * Every file under [root], relative to it, for the palette's file mode in a project without git —
     * where `git ls-files` cannot be asked (docs/skiffcode.spec.md "커맨드 버튼과 팔레트"). A folder named
     * in [skip] is not gone into, nor is `.git`; a link to a folder is not followed, which is also what
     * keeps a link back up the tree from going round forever. A link to a file is listed, as the
     * single file mode lists one; one that reaches nothing is not. A folder below the root that
     * cannot be read (no permission, gone since it was listed) is passed over rather than failing the
     * rest. It stops once [max] files are in, saying so.
     */
    suspend fun walk(fs: FileSystem, root: String, skip: Collection<String>, max: Int): Walk {
        val skipped = skip.toSet() + ".git"
        val paths = ArrayList<String>()
        val pending = ArrayDeque(listOf(""))
        while (pending.isNotEmpty()) {
            val relative = pending.removeFirst()
            val nodes = try {
                fs.list(FsPath.join(root, relative))
            } catch (e: FsError) {
                // Only a folder below the root, and only one that is not there to read: a lost link
                // would pass over every folder after it and call what is left the whole project.
                if (relative.isEmpty() || (e !is FsError.PermissionDenied && e !is FsError.NotFound)) throw e
                continue
            }
            for (node in nodes.sortedBy { it.name }) {
                val path = if (relative.isEmpty()) node.name else "$relative/${node.name}"
                when {
                    node.isDirectory && !node.isSymlink -> if (node.name !in skipped) pending.addLast(path)
                    node.isSymlink && !linksToFile(fs, FsPath.join(root, path)) -> Unit
                    paths.size == max -> return Walk(paths, truncated = true)
                    else -> paths += path
                }
            }
        }
        return Walk(paths, truncated = false)
    }

    /**
     * Whether the link at [path] reaches a file. A listing reports a link as itself, never what it
     * points at (one round trip a row), so each one is asked here — links are few.
     */
    private suspend fun linksToFile(fs: FileSystem, path: String): Boolean = try {
        fs.stat(path)?.isDirectory == false
    } catch (_: FsError) {
        false
    }
}
