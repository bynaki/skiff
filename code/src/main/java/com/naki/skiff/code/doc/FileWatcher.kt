package com.naki.skiff.code.doc

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Size and modification time together: what says a file is still the one that was read. The same
 * pair [DocumentSaver] guards a write with, and on both filesystems the time is whole seconds.
 */
data class Stamp(val size: Long, val modifiedEpochSeconds: Long)

/** What a look at the file found. */
sealed interface Stamped {
    data class At(val stamp: Stamp) : Stamped

    data object Gone : Stamped

    /** Nothing about it can be compared — a `content://` provider that reports neither column. */
    data object Unknown : Stamped
}

/** A file being watched: what it looks like now, and its text again once that has changed. */
interface WatchedFile {

    suspend fun stamp(): Stamped

    suspend fun read(): LoadResult

    /**
     * Waits until the file may have changed, and at most [timeoutMillis]. Waiting it out is
     * polling, which is all a remote file can do; a local one comes back the moment the kernel
     * has something to say and keeps the timeout only as a fallback.
     */
    suspend fun awaitHint(timeoutMillis: Long) {
        delay(timeoutMillis)
    }

    /** Releases whatever the watching holds open. */
    fun close() {}
}

/** What the watcher found, for the page to merge into a clean buffer or ask about a dirty one. */
sealed interface FileChange {
    /** [stamp] is null when there was none to take, which only a `content://` document can be. */
    data class Changed(val result: LoadResult, val stamp: Stamp?) : FileChange

    data object Gone : FileChange
}

/**
 * Watches the open document for a change made somewhere else — an editor on the server, another
 * app on the device — and reports it with the file's new text.
 *
 * A file with a path is compared by `stat`, which is one round trip and costs nothing to repeat;
 * reading it again happens only once the stamp has moved. That is also why a change made in the
 * same second that leaves the size alone is invisible here, the same blind spot [DocumentSaver]
 * documents: polling every couple of seconds is what narrows it, not what closes it.
 *
 * Nothing here decides what to do with a change. A clean buffer takes it as a transaction and a
 * dirty one gets a banner, and both of those are the page's, which is the only place that knows
 * whether the user has typed.
 */
class FileWatcher(
    private val file: WatchedFile,
    /** How the file looked when the document was read, which is what a change is measured from. */
    from: Stamped,
    private val pollMillis: Long = DEFAULT_POLL_MILLIS,
    /** logcat on the device; a plain JVM test, where `Log` is a stub that throws, says nothing. */
    private val warn: (String, Throwable) -> Unit = { _, _ -> },
) {

    private var known: Stamp? = (from as? Stamped.At)?.stamp
    private var gone = from is Stamped.Gone

    /**
     * Watches until the coroutine is cancelled — or returns early when there is nothing to
     * compare, since a document whose provider offers neither a size nor a modification time is
     * only ever looked at by [recheck].
     */
    suspend fun watch(onChange: suspend (FileChange) -> Unit) {
        try {
            while (true) {
                file.awaitHint(pollMillis)
                val now = stampOrNull() ?: continue
                if (now is Stamped.Unknown) return
                apply(now, onChange)
            }
        } finally {
            file.close()
        }
    }

    /**
     * One look off the clock, for when the app comes back to the front — and the only look a
     * document with nothing to compare ever gets, which is why it is handed [currentText] and
     * compares that instead of a stamp.
     */
    suspend fun recheck(currentText: String, onChange: suspend (FileChange) -> Unit) {
        val now = stampOrNull() ?: return
        if (now !is Stamped.Unknown) return apply(now, onChange)
        val result = readOrNull() ?: return
        if (result is LoadResult.Text && result.text == currentText) return
        onChange(FileChange.Changed(result, stamp = null))
    }

    private suspend fun apply(now: Stamped, onChange: suspend (FileChange) -> Unit) {
        when (now) {
            is Stamped.At -> {
                if (now.stamp == known) return
                // Read before the stamp is taken as known, so a read that fails is tried again on
                // the next tick rather than passed over. A file that changes again in between is
                // reported twice, and the second report is a merge that finds nothing to change.
                val result = readOrNull() ?: return
                known = now.stamp
                gone = false
                onChange(FileChange.Changed(result, now.stamp))
            }
            Stamped.Gone -> {
                if (gone) return
                gone = true
                known = null
                onChange(FileChange.Gone)
            }
            // Only reachable from watch(), which stops rather than spinning on it.
            Stamped.Unknown -> return
        }
    }

    /** Null when the look itself failed: a server that is not answering is not a change. */
    private suspend fun stampOrNull(): Stamped? = try {
        file.stamp()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        warn("could not stat the watched file", e)
        null
    }

    private suspend fun readOrNull(): LoadResult? = try {
        file.read()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        warn("could not read the watched file", e)
        null
    }

    companion object {
        /** plan.md's "약 2초". A setting once `settings.toml` exists (M4). */
        const val DEFAULT_POLL_MILLIS = 2000L
    }
}
