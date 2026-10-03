package com.naki.skiff.code.project

import com.naki.skiff.code.doc.TextFormat
import com.naki.skiff.code.doc.TextLoader
import com.naki.skiff.code.session.ExecChannel
import com.naki.skiff.code.session.ExecTimedOut
import com.naki.skiff.code.session.RemoteExec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException

/**
 * What a project's git says, asked on its server through [RemoteExec] (docs/skiffcode.spec.md "git과
 * LSP"). Everything here reads; nothing changes the repository. Paths are relative to [root], the
 * repository's top, as [ProjectFile.path] has them.
 *
 * File contents come over one `git cat-file --batch` left running: a command per file would be a
 * channel opened per file, which is what makes exec slow. It is closed after [idleMs] unused and
 * started again when next asked. What it reads is kept by commit hash, which names one content for
 * good, so it is never stale — only [head] is asked every time.
 */
class GitService(
    private val exec: RemoteExec,
    private val root: String,
    private val idleMs: Long = IDLE_MS,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    /** The running `cat-file --batch`, and when it is to be put away. Both under [lock]. */
    private var batch: Batch? = null
    private var idle: Job? = null

    /** Contents and histories already read, by commit hash and path, under [lock]. */
    private val shown = Lru<Pair<String, String>, ByteArray?>()
    private val histories = Lru<Pair<String, String>, List<String>>()

    /** The commit HEAD is on, or null in a repository with no commits yet. */
    suspend fun head(): String? = batched { it.ask("HEAD", COMMIT_LIMIT)?.oid }

    /**
     * [path] as it was at [commit], or null when it was not a file there — new since, or a directory
     * then. Throws [GitObjectTooLarge] past [limit] bytes rather than reading it in.
     */
    suspend fun show(commit: String, path: String, limit: Long): ByteArray? {
        require(HASH.matches(commit)) { "not a commit hash: $commit" }
        checkPath(path)
        val key = commit to path
        lock.withLock { if (shown.contains(key)) return shown[key] }
        val found = batched { it.ask("$commit:$path", limit) }
        val bytes = found?.takeIf { it.type == "blob" }?.bytes
        lock.withLock { shown[key] = bytes }
        return bytes
    }

    /**
     * The last two commits that touched [path], newest first: the diff layer's second comparison is
     * the one before HEAD that touched it. Empty in a repository with no commits.
     */
    suspend fun history(path: String): List<String> {
        checkPath(path)
        val head = head() ?: return emptyList()
        val key = head to path
        lock.withLock { histories[key]?.let { return it } }
        // Literal, so a path that looks like pathspec magic (`:(glob)…`, `*.md`) is only itself.
        val out = git("--literal-pathspecs", "log", "-n", "2", "--format=%H", "--", path)
        val commits = out.decodeToString().lines().filter { it.isNotEmpty() }
        lock.withLock { histories[key] = commits }
        return commits
    }

    /**
     * Every file in the working tree that is not ignored, tracked or not, for the palette's file
     * search. Not kept: an untracked file comes and goes without HEAD moving.
     */
    suspend fun files(): List<String> =
        git("ls-files", "-co", "--exclude-standard", "-z").decodeToString().split('\u0000').filter { it.isNotEmpty() }

    /** Stops the running `cat-file`. Writes to the socket, so not on the main thread. */
    fun close() {
        scope.cancel()
        batch?.channel?.close()
        batch = null
    }

    private suspend fun git(vararg args: String): ByteArray {
        val result = exec.run(listOf("git", "-C", root) + args)
        if (result.exitStatus != 0) throw GitFailed(result.exitStatus, result.stderr)
        return result.stdout
    }

    /**
     * [ask] on the running `cat-file`, started if it is not. An answer read halfway leaves the stream
     * somewhere in the middle of an object, so any failure puts that one away; a link that dropped
     * while it waited is started again once.
     */
    private suspend fun <T> batched(ask: (Batch) -> T): T = lock.withLock {
        idle?.cancel()
        try {
            try {
                attempt(ask)
            } catch (_: IOException) {
                attempt(ask)
            }
        } finally {
            idle = scope.launch {
                delay(idleMs)
                lock.withLock { putAway() }
            }
        }
    }

    private suspend fun <T> attempt(ask: (Batch) -> T): T {
        val current = batch ?: Batch(exec.start(listOf("git", "-C", root, "cat-file", "--batch"))).also { batch = it }
        return try {
            // Boxed, because a null answer (no such object) is not the timeout's null.
            val answer = withTimeoutOrNull(TIMEOUT_MS) {
                try {
                    Answer(runInterruptible(Dispatchers.IO) { ask(current) })
                } catch (e: IOException) {
                    // An interrupted read is the timeout, not a dropped link (as in RemoteExec).
                    ensureActive()
                    throw e
                }
            } ?: throw ExecTimedOut(TIMEOUT_MS)
            answer.value
        } catch (e: Throwable) {
            putAway()
            throw e
        }
    }

    private suspend fun putAway() {
        val doomed = batch ?: return
        batch = null
        runInterruptible(Dispatchers.IO) { doomed.channel.close() }
    }

    private class Answer<T>(val value: T)

    private class Found(val oid: String, val type: String, val bytes: ByteArray)

    private class Batch(val channel: ExecChannel) {

        private val input = BufferedInputStream(channel.stdout)

        /** One object by name, or null when there is none by it. */
        fun ask(name: String, limit: Long): Found? {
            channel.stdin.write("$name\n".encodeToByteArray())
            channel.stdin.flush()
            val header = line()
            val match = HEADER.matchEntire(header)
            if (match == null) {
                if (header.endsWith(" missing")) return null
                throw GitFailed(null, header)
            }
            val (oid, type, sizeText) = match.destructured
            val size = sizeText.toLong()
            if (size > limit) throw GitObjectTooLarge(size)
            val bytes = ByteArray(size.toInt())
            var read = 0
            while (read < bytes.size) {
                val n = input.read(bytes, read, bytes.size - read)
                if (n < 0) ended()
                read += n
            }
            // Each object ends in a newline of its own.
            if (input.read() < 0) ended()
            return Found(oid, type, bytes)
        }

        private fun line(): String {
            val out = ByteArrayOutputStream()
            while (true) {
                val b = input.read()
                if (b < 0) ended()
                if (b == '\n'.code) return out.toByteArray().decodeToString()
                out.write(b)
            }
        }

        /** The command is gone: its own words if it left any — not a repository, no git — or a dropped link. */
        private fun ended(): Nothing {
            val said = channel.failure()
            if (said.isNotBlank()) throw GitFailed(null, said)
            throw EOFException("git cat-file ended")
        }
    }

    /** At most [CACHE_SIZE] entries, the least recently used going first. */
    private class Lru<K, V> : LinkedHashMap<K, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>) = size > CACHE_SIZE
    }

    private fun checkPath(path: String) {
        // A batch request is one line, and the empty path is the root, which is no file.
        require(path.isNotEmpty() && '\n' !in path) { "not a path in the repository: $path" }
    }

    private companion object {
        const val IDLE_MS = 60_000L
        const val TIMEOUT_MS = 30_000L

        /** As many as the open files that keep their buffers (`web/src/memories.ts`'s `RETAINED_BUFFERS`). */
        const val CACHE_SIZE = 30

        /** A commit object is its headers and message; HEAD is read whole only to step past it. */
        const val COMMIT_LIMIT = 1L shl 20
        val HASH = Regex("[0-9a-f]{40}|[0-9a-f]{64}")
        val HEADER = Regex("([0-9a-f]{40}|[0-9a-f]{64}) (\\w+) (\\d+)")
    }
}

/**
 * [path] as HEAD has it, read as the working file in [format] was ([TextLoader.decodeLike]) so the
 * two compare line for line: the git gutter's baseline. Null when there is nothing to compare with —
 * no commit yet, a file HEAD does not have, one past [loader]'s limit, or bytes that do not decode
 * as the working file's.
 */
suspend fun GitService.headText(path: String, format: TextFormat, loader: TextLoader): String? {
    val head = head() ?: return null
    return textAt(head, path, format, loader)
}

/**
 * [path] before the last commit that changed it, read the same way: what ④ offers the diff layer
 * besides HEAD. That is the commit before it in [GitService.history]. Null when there is none — the
 * file arrived in the one commit that ever changed it, or nothing is committed — or for the reasons
 * [headText] gives.
 */
suspend fun GitService.previousText(path: String, format: TextFormat, loader: TextLoader): String? {
    val previous = history(path).getOrNull(1) ?: return null
    return textAt(previous, path, format, loader)
}

private suspend fun GitService.textAt(commit: String, path: String, format: TextFormat, loader: TextLoader): String? {
    val bytes = try {
        show(commit, path, loader.sizeLimit)
    } catch (_: GitObjectTooLarge) {
        null
    } ?: return null
    return loader.decodeLike(bytes, format)
}

/** git ran and said no: [exitStatus] (null from `cat-file`, which answers in its output) and its words. */
class GitFailed(val exitStatus: Int?, val stderr: String) : Exception("git failed (${exitStatus ?: "-"}): ${stderr.trim()}")

/** An object bigger than the caller would hold, left unread. */
class GitObjectTooLarge(val size: Long) : Exception("A git object of $size bytes is past the limit")
