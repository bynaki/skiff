package com.naki.skiff.code.doc

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.os.FileObserver
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.naki.skiff.fs.FileSystem
import com.naki.skiff.fs.FsError
import com.naki.skiff.fs.FsPath
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okio.source
import java.io.File

/**
 * The three kinds of document [FileWatcher] can be pointed at. Only [WatchedPath] is reachable
 * from a plain JVM test; the other two are the device's, and each is a few lines of Android API
 * over the same two questions.
 */

/** A file with a path, on a server or on the device: `stat` answers for both. */
open class WatchedPath(
    private val fs: FileSystem,
    private val path: String,
    private val loader: TextLoader,
) : WatchedFile {

    override suspend fun stamp(): Stamped {
        val node = fs.stat(path) ?: return Stamped.Gone
        return Stamped.At(Stamp(node.size, node.modifiedEpochSeconds))
    }

    override suspend fun read(): LoadResult = loader.load(fs, path)
}

/**
 * A file on this device. Its stamps are the same `stat` as any other path; what is different is
 * the waiting — the kernel says when something happened, so a change made by another app shows up
 * at once instead of on the next tick. The poll interval stays as a ceiling, because an event that
 * never arrives should make the notice late rather than absent.
 *
 * The parent directory is watched, not the file: a file replaced by a rename — which is how most
 * editors write — gets a new inode, and an observer on the old one goes quiet with nothing to say.
 * A directory reports the child's name, so the replacement is seen.
 */
class WatchedLocalPath(
    fs: FileSystem,
    private val path: String,
    loader: TextLoader,
) : WatchedPath(fs, path, loader) {

    private val name = FsPath.name(path)
    private val events = Channel<Unit>(Channel.CONFLATED)
    private var observer: FileObserver? = null

    override suspend fun awaitHint(timeoutMillis: Long) {
        // Started here rather than in the constructor, so a file that is never watched — a document
        // replaced before the app came back to the front — leaves nothing running behind it.
        if (observer == null) observer = newObserver().apply { startWatching() }
        withTimeoutOrNull(timeoutMillis) { events.receive() }
    }

    override fun close() {
        observer?.stopWatching()
        observer = null
    }

    private fun newObserver() = object : FileObserver(File(FsPath.parent(path)), MASK) {
        override fun onEvent(event: Int, affected: String?) {
            // A null name comes with the events about the directory itself, which are not ours.
            if (affected == name) events.trySend(Unit)
        }
    }

    private companion object {
        /** Written to, replaced by a rename, deleted, or its times or permissions changed. */
        const val MASK = FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO or FileObserver.MOVED_FROM or
            FileObserver.DELETE or FileObserver.CREATE or FileObserver.ATTRIB
    }
}

/**
 * A `content://` document, which has no `stat` behind it. The provider is asked for the size and
 * modification time columns instead, and one that offers neither leaves nothing to poll — such a
 * document is only looked at again when the app comes back to the front, where [FileWatcher]
 * compares the text rather than a stamp (user decision, 2026-09-21). Re-reading the whole stream
 * every couple of seconds to find out nothing changed is what that avoids.
 *
 * The columns are read by name off a full cursor rather than asked for in a projection: a provider
 * that does not know a column is free to throw on one that names it.
 */
class WatchedContent(
    private val resolver: ContentResolver,
    private val uri: Uri,
    private val loader: TextLoader,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : WatchedFile {

    override suspend fun stamp(): Stamped = withContext(dispatcher) {
        val cursor = try {
            resolver.query(uri, null, null, null, null)
        } catch (e: Exception) {
            throw FsError.Unknown("the provider refused to describe $uri", e)
        } ?: return@withContext Stamped.Unknown
        cursor.use {
            if (!it.moveToFirst()) return@withContext Stamped.Gone
            val size = it.longOrNull(OpenableColumns.SIZE)
            val modified = it.longOrNull(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            // Milliseconds where a stat's are seconds; nothing compares the two with each other.
            if (size == null && modified == null) Stamped.Unknown else Stamped.At(Stamp(size ?: -1, modified ?: -1))
        }
    }

    override suspend fun read(): LoadResult = withContext(dispatcher) {
        val stream = resolver.openInputStream(uri) ?: throw FsError.NotFound(uri.toString())
        stream.use { loader.load(it.source()) }
    }

    private fun Cursor.longOrNull(column: String): Long? {
        val index = getColumnIndex(column)
        return if (index < 0 || isNull(index)) null else getLong(index)
    }
}
