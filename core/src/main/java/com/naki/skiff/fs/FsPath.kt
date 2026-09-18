package com.naki.skiff.fs

/**
 * POSIX path helpers. Both the local and the SFTP filesystem speak "/"-separated
 * absolute paths, so every path in the app goes through here and the rest of the
 * code never has to care which side it came from.
 */
object FsPath {

    const val SEPARATOR = '/'
    const val ROOT = "/"

    /** Collapses "//", resolves "." and "..", and strips the trailing slash (except for root). */
    fun normalize(path: String): String {
        val absolute = path.startsWith(SEPARATOR)
        val stack = ArrayList<String>()
        for (segment in path.split(SEPARATOR)) {
            when (segment) {
                "", "." -> Unit
                ".." -> if (stack.isNotEmpty() && stack.last() != "..") {
                    stack.removeAt(stack.lastIndex)
                } else if (!absolute) {
                    stack.add("..")
                }
                else -> stack.add(segment)
            }
        }
        val joined = stack.joinToString(SEPARATOR.toString())
        return when {
            absolute -> ROOT + joined
            joined.isEmpty() -> "."
            else -> joined
        }
    }

    fun join(parent: String, child: String): String {
        if (child.startsWith(SEPARATOR)) return normalize(child)
        return normalize(parent.trimEnd(SEPARATOR) + SEPARATOR + child)
    }

    fun parent(path: String): String {
        val normalized = normalize(path)
        if (normalized == ROOT) return ROOT
        val cut = normalized.lastIndexOf(SEPARATOR)
        return if (cut <= 0) ROOT else normalized.substring(0, cut)
    }

    fun name(path: String): String {
        val normalized = normalize(path)
        if (normalized == ROOT) return ROOT
        return normalized.substringAfterLast(SEPARATOR)
    }

    fun isRoot(path: String): Boolean = normalize(path) == ROOT

    /** "/a/b/c" -> [("a","/a"), ("b","/a/b"), ("c","/a/b/c")], for the breadcrumb bar. */
    fun crumbs(path: String): List<Pair<String, String>> {
        val normalized = normalize(path)
        if (normalized == ROOT) return emptyList()
        val out = ArrayList<Pair<String, String>>()
        val builder = StringBuilder()
        for (segment in normalized.trimStart(SEPARATOR).split(SEPARATOR)) {
            builder.append(SEPARATOR).append(segment)
            out.add(segment to builder.toString())
        }
        return out
    }

    /** "archive.tar.gz" -> "gz"; dotfiles with no other dot have no extension. */
    fun extension(fileName: String): String {
        val dot = fileName.lastIndexOf('.')
        return if (dot <= 0) "" else fileName.substring(dot + 1).lowercase()
    }

    fun stem(fileName: String): String {
        val dot = fileName.lastIndexOf('.')
        return if (dot <= 0) fileName else fileName.substring(0, dot)
    }

    /**
     * Picks a name that does not collide with [taken]: "notes.md" -> "notes (1).md".
     * Used by the "keep both" conflict resolution and by "new folder" defaults.
     */
    fun uniqueName(desired: String, taken: Set<String>): String {
        if (desired !in taken) return desired
        val stem = stem(desired)
        val extension = extension(desired)
        val suffix = if (extension.isEmpty()) "" else ".$extension"
        var counter = 1
        while (true) {
            val candidate = "$stem ($counter)$suffix"
            if (candidate !in taken) return candidate
            counter++
        }
    }

    /** True when [path] is [ancestor] itself or sits underneath it. Guards "move a dir into itself". */
    fun isAncestorOrSame(ancestor: String, path: String): Boolean {
        val a = normalize(ancestor)
        val p = normalize(path)
        if (a == p) return true
        val prefix = if (a == ROOT) ROOT else a + SEPARATOR
        return p.startsWith(prefix)
    }
}
