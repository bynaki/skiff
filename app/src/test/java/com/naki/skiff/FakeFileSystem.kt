package com.naki.skiff

import com.naki.skiff.fs.FileNode
import com.naki.skiff.fs.FileSystem
import com.naki.skiff.fs.FsError
import com.naki.skiff.fs.FsPath
import com.naki.skiff.fs.SourceId
import okio.Buffer
import okio.Sink
import okio.Source

/**
 * In-memory filesystem for exercising CopyEngine without a device or a server. Because the
 * engine only knows the FileSystem interface, testing it against this is testing the same
 * code path that runs against SFTP.
 */
class FakeFileSystem(
    override val id: SourceId,
    override val displayName: String = "fake",
) : FileSystem {

    private val directories = linkedSetOf(FsPath.ROOT)
    private val files = LinkedHashMap<String, ByteArray>()
    /** link path -> target path, so tests can build the symlink cases the engine guards against. */
    private val links = LinkedHashMap<String, String>()

    fun putSymlink(path: String, target: String) {
        makeParents(FsPath.parent(path))
        links[FsPath.normalize(path)] = FsPath.normalize(target)
    }

    fun putFile(path: String, content: ByteArray) {
        makeParents(FsPath.parent(path))
        files[FsPath.normalize(path)] = content
    }

    fun putDirectory(path: String) = makeParents(path)

    fun fileContent(path: String): ByteArray? = files[FsPath.normalize(path)]

    fun paths(): Set<String> = files.keys + directories + links.keys

    /** Follows links to their eventual target, giving up on a cycle. */
    private fun resolve(path: String): String {
        var current = FsPath.normalize(path)
        repeat(MAX_LINK_HOPS) {
            current = links[current] ?: return current
        }
        return current
    }

    private fun makeParents(path: String) {
        var current = FsPath.normalize(path)
        while (current != FsPath.ROOT) {
            directories.add(current)
            current = FsPath.parent(current)
        }
    }

    override suspend fun startPath(): String = FsPath.ROOT

    override suspend fun list(path: String): List<FileNode> {
        // opendir follows symlinks, so listing through a link lands in its target.
        val normalized = resolve(path)
        if (normalized !in directories) throw FsError.NotFound(normalized)
        val prefix = if (normalized == FsPath.ROOT) FsPath.ROOT else "$normalized/"
        val children = (files.keys + directories + links.keys)
            .filter { it != normalized && it.startsWith(prefix) }
            .filter { !it.removePrefix(prefix).contains('/') }
        return children.map { node(it) }
    }

    /** stat follows links, matching POSIX stat and sshj's statExistence. */
    override suspend fun stat(path: String): FileNode? {
        val resolved = resolve(path)
        if (resolved !in files && resolved !in directories) return null
        return FileNode(
            path = FsPath.normalize(path),
            name = FsPath.name(path),
            isDirectory = resolved in directories,
            size = files[resolved]?.size?.toLong() ?: 0L,
            modifiedEpochSeconds = 0,
        )
    }

    override suspend fun canonicalize(path: String): String = resolve(path)

    /** A listing reports the link itself, like readdir does. */
    private fun node(path: String) = FileNode(
        path = path,
        name = FsPath.name(path),
        isDirectory = path in directories,
        size = files[path]?.size?.toLong() ?: 0L,
        modifiedEpochSeconds = 0,
        isSymlink = path in links,
    )

    override suspend fun mkdir(path: String) {
        val normalized = FsPath.normalize(path)
        if (normalized in directories || normalized in files) throw FsError.AlreadyExists(normalized)
        directories.add(normalized)
    }

    override suspend fun createFile(path: String) {
        val normalized = FsPath.normalize(path)
        if (normalized in files || normalized in directories) throw FsError.AlreadyExists(normalized)
        files[normalized] = ByteArray(0)
    }

    override suspend fun rename(from: String, to: String) {
        val source = FsPath.normalize(from)
        val target = FsPath.normalize(to)
        if (target in files || target in directories) throw FsError.AlreadyExists(target)
        when (source) {
            in files -> files[target] = files.remove(source)!!
            in directories -> {
                for (path in (files.keys + directories).filter { FsPath.isAncestorOrSame(source, it) }) {
                    val moved = target + path.removePrefix(source)
                    if (path in files) files[moved] = files.remove(path)!! else {
                        directories.remove(path)
                        directories.add(moved)
                    }
                }
            }
            else -> throw FsError.NotFound(source)
        }
    }

    override suspend fun delete(path: String, recursive: Boolean) {
        val normalized = FsPath.normalize(path)
        when {
            normalized in files -> files.remove(normalized)
            normalized in directories -> {
                val descendants = (files.keys + directories)
                    .filter { it != normalized && FsPath.isAncestorOrSame(normalized, it) }
                if (descendants.isNotEmpty() && !recursive) throw FsError.DirectoryNotEmpty(normalized)
                descendants.forEach { files.remove(it); directories.remove(it) }
                directories.remove(normalized)
            }
            else -> throw FsError.NotFound(normalized)
        }
    }

    override suspend fun openRead(path: String): Source {
        val content = files[resolve(path)] ?: throw FsError.NotFound(path)
        return Buffer().write(content)
    }

    override suspend fun openWrite(path: String, append: Boolean): Sink {
        val normalized = FsPath.normalize(path)
        val existing = if (append) files[normalized] ?: ByteArray(0) else ByteArray(0)
        return object : Sink {
            private val buffer = Buffer().write(existing)
            override fun write(source: Buffer, byteCount: Long) = buffer.write(source, byteCount)
            override fun flush() { files[normalized] = buffer.copy().readByteArray() }
            override fun timeout() = okio.Timeout.NONE
            override fun close() { files[normalized] = buffer.readByteArray() }
        }
    }

    override suspend fun freeSpace(path: String): Long = Long.MAX_VALUE

    override fun close() = Unit

    private companion object {
        const val MAX_LINK_HOPS = 40
    }
}
