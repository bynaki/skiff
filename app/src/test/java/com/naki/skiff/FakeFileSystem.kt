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

    fun putFile(path: String, content: ByteArray) {
        makeParents(FsPath.parent(path))
        files[FsPath.normalize(path)] = content
    }

    fun putDirectory(path: String) = makeParents(path)

    fun fileContent(path: String): ByteArray? = files[FsPath.normalize(path)]

    fun paths(): Set<String> = files.keys + directories

    private fun makeParents(path: String) {
        var current = FsPath.normalize(path)
        while (current != FsPath.ROOT) {
            directories.add(current)
            current = FsPath.parent(current)
        }
    }

    override suspend fun startPath(): String = FsPath.ROOT

    override suspend fun list(path: String): List<FileNode> {
        val normalized = FsPath.normalize(path)
        if (normalized !in directories) throw FsError.NotFound(normalized)
        val prefix = if (normalized == FsPath.ROOT) FsPath.ROOT else "$normalized/"
        val children = (files.keys + directories)
            .filter { it != normalized && it.startsWith(prefix) }
            .filter { !it.removePrefix(prefix).contains('/') }
        return children.map { node(it) }
    }

    override suspend fun stat(path: String): FileNode? {
        val normalized = FsPath.normalize(path)
        return if (normalized in files || normalized in directories) node(normalized) else null
    }

    private fun node(path: String) = FileNode(
        path = path,
        name = FsPath.name(path),
        isDirectory = path in directories,
        size = files[path]?.size?.toLong() ?: 0L,
        modifiedEpochSeconds = 0,
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
        val content = files[FsPath.normalize(path)] ?: throw FsError.NotFound(path)
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
}
