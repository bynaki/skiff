package com.naki.skiff.fs.sftp

import com.naki.skiff.data.store.ServerProfile
import com.naki.skiff.fs.FileNode
import com.naki.skiff.fs.FileSystem
import com.naki.skiff.fs.FsError
import com.naki.skiff.fs.FsPath
import com.naki.skiff.fs.SourceId
import net.schmizz.sshj.sftp.FileAttributes
import net.schmizz.sshj.sftp.FileMode
import net.schmizz.sshj.sftp.OpenMode
import net.schmizz.sshj.sftp.RemoteFile
import net.schmizz.sshj.sftp.Response
import net.schmizz.sshj.sftp.SFTPException
import okio.Sink
import okio.Source
import okio.sink
import okio.source
import java.util.EnumSet

/**
 * A remote filesystem over the SFTP subsystem that every OpenSSH install already ships.
 *
 * Deliberately no `exec` channel: no `rm -rf`, no `cp -r`, no `du`. Recursion happens on the
 * client (see the transfer engine), which is what lets this work against servers where the
 * user has no shell at all — internal-sftp with a ChrootDirectory.
 */
class SftpFileSystem(
    private val profile: ServerProfile,
    private val browseConnection: SshConnection,
    private val transferConnection: SshConnection,
) : FileSystem {

    override val id: SourceId = SourceId.Remote(profile.id)

    override val displayName: String = profile.name

    override suspend fun startPath(): String = browseConnection.resolveStartPath()

    override suspend fun list(path: String): List<FileNode> = translating(path) {
        browseConnection.withSftp { sftp ->
            sftp.ls(path).mapNotNull { entry ->
                // sshj includes "." and ".." on some servers.
                if (entry.name == "." || entry.name == "..") null else entry.toNode(path)
            }
        }
    }

    override suspend fun stat(path: String): FileNode? = try {
        browseConnection.withSftp { sftp ->
            val attributes = sftp.statExistence(path) ?: return@withSftp null
            attributes.toNode(path, FsPath.name(path), symlink = isSymlink(sftp, path))
        }
    } catch (e: SFTPException) {
        if (e.statusCode == Response.StatusCode.NO_SUCH_FILE) null else throw e.toFsError(path)
    }

    override suspend fun mkdir(path: String) = translating(path) {
        browseConnection.withSftp { it.mkdir(path) }
    }

    override suspend fun createFile(path: String) = translating(path) {
        browseConnection.withSftp { sftp ->
            // CREAT|EXCL is the atomic "fail if it already exists" that a stat+create race loses.
            sftp.open(path, EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.EXCL)).close()
        }
    }

    override suspend fun rename(from: String, to: String) = translating(from) {
        browseConnection.withSftp { it.rename(from, to) }
    }

    override suspend fun delete(path: String, recursive: Boolean) = translating(path) {
        browseConnection.withSftp { sftp ->
            val attributes = sftp.stat(path)
            if (attributes.type == FileMode.Type.DIRECTORY) {
                if (recursive) deleteTreeBlocking(sftp, path) else sftp.rmdir(path)
            } else {
                sftp.rm(path)
            }
        }
    }

    private fun deleteTreeBlocking(sftp: net.schmizz.sshj.sftp.SFTPClient, path: String) {
        for (entry in sftp.ls(path)) {
            if (entry.name == "." || entry.name == "..") continue
            val childPath = FsPath.join(path, entry.name)
            // A symlink to a directory is unlinked, never descended into.
            if (entry.attributes.type == FileMode.Type.DIRECTORY) {
                deleteTreeBlocking(sftp, childPath)
            } else {
                sftp.rm(childPath)
            }
        }
        sftp.rmdir(path)
    }

    override suspend fun openRead(path: String): Source = translating(path) {
        transferConnection.withSftp { sftp ->
            val handle = sftp.open(path, EnumSet.of(OpenMode.READ))
            // Read-ahead pipelines requests instead of waiting a full round trip per block;
            // on a real link this is the difference between ~1 MB/s and saturating the wifi.
            RemoteFileSource(handle, handle.ReadAheadRemoteFileInputStream(READ_AHEAD_MAX).source())
        }
    }

    override suspend fun openWrite(path: String, append: Boolean): Sink = translating(path) {
        transferConnection.withSftp { sftp ->
            val modes = if (append) {
                EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.APPEND)
            } else {
                EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.TRUNC)
            }
            val handle = sftp.open(path, modes)
            RemoteFileSink(handle, handle.RemoteFileOutputStream().sink())
        }
    }

    override suspend fun canonicalize(path: String): String = translating(path) {
        browseConnection.withSftp { sftp ->
            runCatching { FsPath.normalize(sftp.canonicalize(path)) }
                .getOrDefault(FsPath.normalize(path))
        }
    }

    // Free space over SFTP needs the statvfs@openssh.com extension, which the base
    // protocol does not define and sshj does not expose. Reporting "unknown" is honest;
    // the transfer engine just skips the pre-flight space check for remote targets.
    override suspend fun freeSpace(path: String): Long? = null

    override fun close() {
        browseConnection.close()
        transferConnection.close()
    }

    // ---- mapping ---------------------------------------------------------

    private fun net.schmizz.sshj.sftp.RemoteResourceInfo.toNode(parent: String): FileNode {
        val symlink = attributes.type == FileMode.Type.SYMLINK
        return attributes.toNode(FsPath.join(parent, name), name, symlink)
    }

    private fun FileAttributes.toNode(path: String, name: String, symlink: Boolean): FileNode =
        FileNode(
            path = FsPath.normalize(path),
            name = name,
            isDirectory = type == FileMode.Type.DIRECTORY,
            size = size,
            modifiedEpochSeconds = mtime,
            mode = permissions.fold(0) { acc, permission -> acc or permission.toMask() },
            isSymlink = symlink,
            // Resolving every symlink would cost one round trip per row, so a listing
            // reports the link itself; tapping it resolves lazily via stat.
            linkTargetIsDirectory = false,
        )

    private fun isSymlink(sftp: net.schmizz.sshj.sftp.SFTPClient, path: String): Boolean =
        runCatching { sftp.lstat(path).type == FileMode.Type.SYMLINK }.getOrDefault(false)

    private inline fun <T> translating(path: String, block: () -> T): T = try {
        block()
    } catch (e: SFTPException) {
        throw e.toFsError(path)
    }

    private fun SFTPException.toFsError(path: String): Throwable = when (statusCode) {
        Response.StatusCode.NO_SUCH_FILE, Response.StatusCode.NO_SUCH_PATH -> FsError.NotFound(path)
        Response.StatusCode.PERMISSION_DENIED -> FsError.PermissionDenied(path)
        Response.StatusCode.FILE_ALREADY_EXISTS -> FsError.AlreadyExists(path)
        Response.StatusCode.NO_SPACE_ON_FILESYSTEM, Response.StatusCode.QUOTA_EXCEEDED ->
            FsError.NoSpace(path)
        Response.StatusCode.DIR_NOT_EMPTY -> FsError.DirectoryNotEmpty(path)
        Response.StatusCode.NOT_A_DIRECTORY -> FsError.NotADirectory(path)
        else -> FsError.Unknown(message ?: "SFTP error", this)
    }

    private fun net.schmizz.sshj.xfer.FilePermission.toMask(): Int = when (this) {
        net.schmizz.sshj.xfer.FilePermission.USR_R -> 0b100_000_000
        net.schmizz.sshj.xfer.FilePermission.USR_W -> 0b010_000_000
        net.schmizz.sshj.xfer.FilePermission.USR_X -> 0b001_000_000
        net.schmizz.sshj.xfer.FilePermission.GRP_R -> 0b000_100_000
        net.schmizz.sshj.xfer.FilePermission.GRP_W -> 0b000_010_000
        net.schmizz.sshj.xfer.FilePermission.GRP_X -> 0b000_001_000
        net.schmizz.sshj.xfer.FilePermission.OTH_R -> 0b000_000_100
        net.schmizz.sshj.xfer.FilePermission.OTH_W -> 0b000_000_010
        net.schmizz.sshj.xfer.FilePermission.OTH_X -> 0b000_000_001
        else -> 0
    }

    private companion object {
        const val READ_AHEAD_MAX = 16
    }
}

/** Closes the remote handle when the stream is closed, so a cancelled transfer leaks nothing. */
private class RemoteFileSource(
    private val handle: RemoteFile,
    private val delegate: Source,
) : Source by delegate {
    override fun close() {
        runCatching { delegate.close() }
        runCatching { handle.close() }
    }
}

private class RemoteFileSink(
    private val handle: RemoteFile,
    private val delegate: Sink,
) : Sink by delegate {
    override fun close() {
        runCatching { delegate.close() }
        runCatching { handle.close() }
    }
}
