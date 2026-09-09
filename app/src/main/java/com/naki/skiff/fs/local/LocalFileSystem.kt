package com.naki.skiff.fs.local

import android.os.Environment
import com.naki.skiff.fs.FileNode
import com.naki.skiff.fs.FileSystem
import com.naki.skiff.fs.FsError
import com.naki.skiff.fs.FsPath
import com.naki.skiff.fs.SourceId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Sink
import okio.Source
import okio.appendingSink
import okio.sink
import okio.source
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.PosixFileAttributes
import java.nio.file.attribute.PosixFilePermission

/**
 * The phone's own storage, reached through java.io.File. This requires the
 * "all files access" grant; without it Android hands back empty listings for
 * anything outside the app's own directories, so [LocalStorageAccess] gates the UI.
 */
class LocalFileSystem(
    override val displayName: String,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : FileSystem {

    override val id: SourceId = SourceId.Local

    override suspend fun startPath(): String =
        FsPath.normalize(Environment.getExternalStorageDirectory()?.absolutePath ?: FsPath.ROOT)

    override suspend fun list(path: String): List<FileNode> = withContext(dispatcher) {
        val dir = File(path)
        if (!dir.exists()) throw FsError.NotFound(path)
        if (!dir.isDirectory) throw FsError.NotADirectory(path)
        val children = dir.listFiles() ?: throw FsError.PermissionDenied(path)
        children.map { it.toNode() }
    }

    override suspend fun stat(path: String): FileNode? = withContext(dispatcher) {
        val file = File(path)
        if (file.exists() || isBrokenSymlink(file)) file.toNode() else null
    }

    override suspend fun mkdir(path: String) = withContext(dispatcher) {
        val dir = File(path)
        if (dir.exists()) throw FsError.AlreadyExists(path)
        if (!dir.mkdirs()) throw FsError.PermissionDenied(path)
    }

    override suspend fun createFile(path: String) = withContext(dispatcher) {
        val file = File(path)
        if (file.exists()) throw FsError.AlreadyExists(path)
        try {
            if (!file.createNewFile()) throw FsError.PermissionDenied(path)
        } catch (e: IOException) {
            throw e.toFsError(path)
        }
    }

    override suspend fun rename(from: String, to: String) = withContext(dispatcher) {
        val source = File(from)
        val target = File(to)
        if (!source.exists()) throw FsError.NotFound(from)
        if (target.exists()) throw FsError.AlreadyExists(to)
        // File.renameTo silently fails across mount points (e.g. internal -> SD card);
        // the caller falls back to copy+delete when this throws.
        if (!source.renameTo(target)) throw FsError.Unknown("Rename failed: $from -> $to")
    }

    override suspend fun delete(path: String, recursive: Boolean) {
        withContext(dispatcher) { deleteBlocking(File(path), recursive) }
    }

    private fun deleteBlocking(file: File, recursive: Boolean) {
        val path = file.absolutePath
        if (!file.exists() && !isBrokenSymlink(file)) throw FsError.NotFound(path)
        // Never follow a symlink into its target when deleting: unlink the link itself.
        if (file.isDirectory && !isSymlink(file)) {
            val children = file.listFiles()
            if (!children.isNullOrEmpty()) {
                if (!recursive) throw FsError.DirectoryNotEmpty(path)
                for (child in children) deleteBlocking(child, recursive = true)
            }
        }
        if (!file.delete()) throw FsError.PermissionDenied(path)
    }

    override suspend fun openRead(path: String): Source = withContext(dispatcher) {
        try {
            File(path).source()
        } catch (e: FileNotFoundException) {
            throw e.toFsError(path)
        }
    }

    override suspend fun openWrite(path: String, append: Boolean): Sink = withContext(dispatcher) {
        val file = File(path)
        try {
            if (append) file.appendingSink() else file.sink()
        } catch (e: FileNotFoundException) {
            throw e.toFsError(path)
        }
    }

    override suspend fun canonicalize(path: String): String = withContext(dispatcher) {
        runCatching { FsPath.normalize(File(path).canonicalPath) }.getOrDefault(FsPath.normalize(path))
    }

    override suspend fun freeSpace(path: String): Long? = withContext(dispatcher) {
        runCatching { File(path).usableSpace }.getOrNull()
    }

    override fun close() = Unit

    private fun File.toNode(): FileNode {
        val symlink = isSymlink(this)
        return FileNode(
            path = FsPath.normalize(absolutePath),
            name = name,
            isDirectory = isDirectory && !symlink,
            size = if (isDirectory) 0L else length(),
            modifiedEpochSeconds = lastModified() / 1000,
            mode = posixMode(this),
            isSymlink = symlink,
            // isDirectory on a File follows the link, so this is the target's type.
            linkTargetIsDirectory = symlink && isDirectory,
        )
    }

    private fun isSymlink(file: File): Boolean =
        runCatching { Files.isSymbolicLink(file.toPath()) }.getOrDefault(false)

    private fun isBrokenSymlink(file: File): Boolean =
        runCatching { Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS) }.getOrDefault(false)

    private fun posixMode(file: File): Int? = runCatching {
        val attributes = Files.readAttributes(file.toPath(), PosixFileAttributes::class.java)
        var mode = 0
        for (permission in attributes.permissions()) {
            mode = mode or when (permission) {
                PosixFilePermission.OWNER_READ -> 0b100_000_000
                PosixFilePermission.OWNER_WRITE -> 0b010_000_000
                PosixFilePermission.OWNER_EXECUTE -> 0b001_000_000
                PosixFilePermission.GROUP_READ -> 0b000_100_000
                PosixFilePermission.GROUP_WRITE -> 0b000_010_000
                PosixFilePermission.GROUP_EXECUTE -> 0b000_001_000
                PosixFilePermission.OTHERS_READ -> 0b000_000_100
                PosixFilePermission.OTHERS_WRITE -> 0b000_000_010
                PosixFilePermission.OTHERS_EXECUTE -> 0b000_000_001
            }
        }
        mode
    }.getOrNull()

    private fun IOException.toFsError(path: String): FsError = when {
        message?.contains("EACCES", ignoreCase = true) == true -> FsError.PermissionDenied(path)
        message?.contains("ENOSPC", ignoreCase = true) == true -> FsError.NoSpace(path)
        this is FileNotFoundException -> FsError.NotFound(path)
        else -> FsError.Unknown(message ?: "I/O error", this)
    }
}
