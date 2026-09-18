package com.naki.skiff.fs

import okio.Sink
import okio.Source

/**
 * The one abstraction the whole app is built on. Panes render it, the transfer engine
 * copies between two of them, and the future preview viewer will read through it — none
 * of them know whether they are talking to the phone's storage or to an SSH server.
 *
 * Every path is an absolute POSIX path (see [FsPath]). Implementations throw [FsError].
 */
interface FileSystem {

    val id: SourceId

    val displayName: String

    /** Where a pane opens when it first switches to this source. */
    suspend fun startPath(): String

    suspend fun list(path: String): List<FileNode>

    suspend fun stat(path: String): FileNode?

    suspend fun mkdir(path: String)

    suspend fun createFile(path: String)

    /** Rename, and therefore also same-filesystem move. */
    suspend fun rename(from: String, to: String)

    suspend fun delete(path: String, recursive: Boolean)

    suspend fun openRead(path: String): Source

    suspend fun openWrite(path: String, append: Boolean = false): Sink

    /**
     * Resolves [path] through any symlinks to its real location. The transfer engine uses
     * this to detect a link that points back into the tree it is already walking.
     */
    suspend fun canonicalize(path: String): String

    /** Bytes free on the volume holding [path], or null when unknown. */
    suspend fun freeSpace(path: String): Long?

    fun close()
}

/** Convenience used by the transfer engine and by "does this name already exist?" checks. */
suspend fun FileSystem.exists(path: String): Boolean = stat(path) != null

suspend fun FileSystem.childNames(path: String): Set<String> =
    runCatching { list(path).map { it.name }.toSet() }.getOrDefault(emptySet())
