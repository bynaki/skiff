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
                directories.add(target)
                walk(source, path, target, directories, files)
            } else {
                files.add(PlannedFile(path, target, node.size))
            }
        }

        return TransferPlan(directories, files, files.sumOf { it.size })
    }

    private suspend fun walk(
        source: FileSystem,
        from: String,
        to: String,
        directories: MutableList<String>,
        files: MutableList<PlannedFile>,
    ) {
        currentCoroutineContext().ensureActive()
        for (child: FileNode in source.list(from)) {
            val childTo = FsPath.join(to, child.name)
            // Symlinks are copied as whatever they point at; following them into a cycle
            // is the one way a plain tree walk can hang forever.
            if (child.isDirectory && !child.isSymlink) {
                directories.add(childTo)
                walk(source, child.path, childTo, directories, files)
            } else if (!child.isSymlink) {
                files.add(PlannedFile(child.path, childTo, child.size))
            }
        }
    }

    /**
     * Executes [plan]. [onProgress] is called with cumulative bytes and the current file so
     * the caller can drive both the notification and the in-app sheet from one source.
     */
    suspend fun execute(
        plan: TransferPlan,
        source: FileSystem,
        destination: FileSystem,
        conflictPolicy: ConflictPolicy,
        onProgress: suspend (transferredBytes: Long, completedFiles: Int, currentName: String) -> Unit,
    ) {
        for (directory in plan.directories) {
            currentCoroutineContext().ensureActive()
            if (!destination.exists(directory)) destination.mkdir(directory)
        }

        var transferred = 0L
        var completed = 0

        for (file in plan.files) {
            currentCoroutineContext().ensureActive()

            val target = when {
                !destination.exists(file.to) -> file.to
                conflictPolicy == ConflictPolicy.SKIP -> {
                    completed++
                    transferred += file.size
                    onProgress(transferred, completed, FsPath.name(file.to))
                    continue
                }
                conflictPolicy == ConflictPolicy.KEEP_BOTH -> {
                    val parent = FsPath.parent(file.to)
                    FsPath.join(parent, FsPath.uniqueName(FsPath.name(file.to), destination.childNames(parent)))
                }
                else -> file.to
            }

            onProgress(transferred, completed, FsPath.name(target))
            transferred += copyOne(source, file.from, destination, target) { chunk ->
                onProgress(transferred + chunk, completed, FsPath.name(target))
            }
            completed++
            onProgress(transferred, completed, FsPath.name(target))
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
