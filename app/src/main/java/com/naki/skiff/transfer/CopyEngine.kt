package com.naki.skiff.transfer

import com.naki.skiff.fs.FileNode
import com.naki.skiff.fs.FileSystem
import com.naki.skiff.fs.FsError
import com.naki.skiff.fs.FsPath
import com.naki.skiff.fs.childNames
import com.naki.skiff.fs.exists
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okio.Buffer
import okio.buffer

/** One file to move, already resolved to absolute source and destination paths. */
data class PlannedFile(val from: String, val to: String, val size: Long)

data class TransferPlan(
    val directories: List<String>,
    val files: List<PlannedFile>,
    val totalBytes: Long,
) {
    val fileCount: Int get() = files.size
}

/**
 * Copies between any two [FileSystem]s — device to server, server to device, server to
 * server, or within one. Because it only speaks the interface, there is exactly one
 * implementation of the tree walk, the conflict handling and the progress accounting.
 *
 * Directory recursion happens here rather than by asking the server to run `cp -r`, which
 * is what keeps the app working against sftp-only accounts with no shell.
 */
class CopyEngine {

    /**
     * Walks the source tree to learn what has to move and how big it is. Doing this up front
     * costs a pass but buys an honest progress bar instead of a spinner of unknown length.
     */
    suspend fun plan(
        source: FileSystem,
        sourcePaths: List<String>,
        destination: FileSystem,
        destinationDir: String,
        conflictPolicy: ConflictPolicy,
    ): TransferPlan {
        val directories = ArrayList<String>()
        val files = ArrayList<PlannedFile>()
        val takenAtRoot = destination.childNames(destinationDir).toMutableSet()
        val visited = HashSet<String>()

        for (path in sourcePaths) {
            currentCoroutineContext().ensureActive()
            val node = source.stat(path) ?: throw FsError.NotFound(path)

            val targetName = when (conflictPolicy) {
                ConflictPolicy.KEEP_BOTH -> FsPath.uniqueName(node.name, takenAtRoot)
                else -> node.name
            }
            takenAtRoot.add(targetName)
            val target = FsPath.join(destinationDir, targetName)

            if (node.isDirectory) {
                // Copying a directory into itself would recurse forever.
                if (source.id == destination.id && FsPath.isAncestorOrSame(path, target)) {
                    throw FsError.Unknown("Cannot copy a folder into itself")
                }
                visited.add(source.canonicalize(path))
                directories.add(target)
                walk(source, path, target, directories, files, visited)
            } else {
                files.add(PlannedFile(path, target, node.size))
            }
        }

        val totalBytes = files.sumOf { it.size }
        // Fail before a byte moves rather than at 90%. Only a local destination can answer:
        // SFTP has no call for free space, so a server returns null and is not checked.
        val free = destination.freeSpace(destinationDir)
        if (free != null && totalBytes > free) throw FsError.NoSpace(destinationDir)

        return TransferPlan(directories, files, totalBytes)
    }

    private suspend fun walk(
        source: FileSystem,
        from: String,
        to: String,
        directories: MutableList<String>,
        files: MutableList<PlannedFile>,
        visited: MutableSet<String>,
    ) {
        currentCoroutineContext().ensureActive()
        for (child: FileNode in source.list(from)) {
            val childTo = FsPath.join(to, child.name)

            if (child.isSymlink) {
                // Symlinks are dereferenced: we copy what they point at. Skipping them
                // instead would be silent data loss, because a move deletes the source
                // tree afterwards. stat() follows the link, so it reports the target.
                val target = source.stat(child.path) ?: continue
                val real = source.canonicalize(child.path)
                if (target.isDirectory) {
                    // A link pointing back up its own tree would otherwise recurse forever.
                    if (!visited.add(real)) continue
                    directories.add(childTo)
                    walk(source, child.path, childTo, directories, files, visited)
                } else {
                    files.add(PlannedFile(child.path, childTo, target.size))
                }
                continue
            }

            if (child.isDirectory) {
                visited.add(source.canonicalize(child.path))
                directories.add(childTo)
                walk(source, child.path, childTo, directories, files, visited)
            } else {
                files.add(PlannedFile(child.path, childTo, child.size))
            }
        }
    }

    /**
     * Executes [plan]. [onProgress] is called with cumulative bytes and the current file so
     * the caller can drive both the notification and the in-app sheet from one source.
     *
     * Under [ConflictPolicy.ASK], [onConflict] is called for each file whose destination is
     * taken, and suspends until the user answers; an answer that applies to the rest replaces
     * the policy for the files after it.
     *
     * Returns the source paths of the files that were skipped, which a move must not delete.
     */
    suspend fun execute(
        plan: TransferPlan,
        source: FileSystem,
        destination: FileSystem,
        conflictPolicy: ConflictPolicy,
        onConflict: suspend (PlannedFile) -> ConflictAnswer = { error("No one to ask about ${it.to}") },
        onProgress: suspend (transferredBytes: Long, completedFiles: Int, currentName: String) -> Unit,
    ): Set<String> {
        for (directory in plan.directories) {
            currentCoroutineContext().ensureActive()
            if (!destination.exists(directory)) destination.mkdir(directory)
        }

        var policy = conflictPolicy
        var transferred = 0L
        var completed = 0
        val skipped = HashSet<String>()

        for (file in plan.files) {
            currentCoroutineContext().ensureActive()

            val resolution = when {
                !destination.exists(file.to) -> null
                policy == ConflictPolicy.ASK -> {
                    val answer = onConflict(file)
                    if (answer.applyToRest) policy = answer.policy
                    answer.policy
                }
                else -> policy
            }
            val target = when (resolution) {
                null, ConflictPolicy.OVERWRITE -> file.to
                ConflictPolicy.SKIP -> {
                    skipped.add(file.from)
                    completed++
                    transferred += file.size
                    onProgress(transferred, completed, FsPath.name(file.to))
                    continue
                }
                ConflictPolicy.KEEP_BOTH -> {
                    val parent = FsPath.parent(file.to)
                    FsPath.join(parent, FsPath.uniqueName(FsPath.name(file.to), destination.childNames(parent)))
                }
                ConflictPolicy.ASK -> error("An answer has to pick a policy")
            }

            onProgress(transferred, completed, FsPath.name(target))
            transferred += copyOne(source, file.from, destination, target) { chunk ->
                onProgress(transferred + chunk, completed, FsPath.name(target))
            }
            completed++
            onProgress(transferred, completed, FsPath.name(target))
        }
        return skipped
    }

    /**
     * The delete half of a move. A file in [skipped] was never copied, so it stays where it
     * is, and so does every folder holding one; everything else under [sourcePaths] goes.
     */
    suspend fun removeSources(source: FileSystem, sourcePaths: List<String>, skipped: Set<String>) {
        for (path in sourcePaths) {
            if (skipped.none { FsPath.isAncestorOrSame(path, it) }) {
                source.delete(path, recursive = true)
                continue
            }
            // Looked up in its parent's listing rather than by stat, which follows a link and
            // would report the target's type.
            val node = source.list(FsPath.parent(path)).firstOrNull { it.name == FsPath.name(path) }
            if (node != null) removeMoved(source, node, skipped)
        }
    }

    private suspend fun removeMoved(source: FileSystem, node: FileNode, skipped: Set<String>) {
        currentCoroutineContext().ensureActive()
        if (skipped.none { FsPath.isAncestorOrSame(node.path, it) }) {
            source.delete(node.path, recursive = true)
            return
        }
        // Only a real directory is opened. A link's target lies outside what was moved, so a
        // link holding a skipped file stays whole rather than having its target emptied.
        if (node.isDirectory && !node.isSymlink) {
            for (child in source.list(node.path)) removeMoved(source, child, skipped)
        }
    }

    /** Streams one file, reporting bytes as they land. Returns the number of bytes copied. */
    private suspend fun copyOne(
        source: FileSystem,
        from: String,
        destination: FileSystem,
        to: String,
        onChunk: suspend (Long) -> Unit,
    ): Long {
        val input = source.openRead(from)
        try {
            val output = destination.openWrite(to, append = false)
            try {
                val sink = output.buffer()
                val buffer = Buffer()
                var copied = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer, BUFFER_BYTES)
                    if (read == -1L) break
                    sink.write(buffer, read)
                    copied += read
                    onChunk(copied)
                }
                sink.flush()
                return copied
            } finally {
                output.close()
            }
        } finally {
            input.close()
        }
    }

    private companion object {
        // 32 KiB matches the SFTP protocol's practical per-packet payload; larger reads
        // just get split up again, smaller ones waste round trips.
        const val BUFFER_BYTES = 32L * 1024
    }
}
