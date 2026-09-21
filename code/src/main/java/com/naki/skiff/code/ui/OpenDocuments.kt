package com.naki.skiff.code.ui

import com.naki.skiff.code.doc.FileWatcher
import com.naki.skiff.code.doc.WatchedFile
import com.naki.skiff.code.intent.OpenRequest
import org.json.JSONArray
import org.json.JSONObject

/**
 * The files that are open at once, and which of them the page is showing.
 *
 * A link used to replace the document; now it adds one, and the sidebar is how the user moves
 * between them. What each file *is* — its buffer, its layer, where it was scrolled to — stays in
 * the page, which is where CodeMirror's `EditorState` lives. Kotlin keeps only what it alone can:
 * the text as the file last said it, and the watch on the file behind it.
 *
 * Only the active file is polled (plan.md "감시"). The others are looked at once when they are
 * activated and once when the app comes back to the front, which is [MainActivity]'s to arrange;
 * this class just says which one is active.
 */
class OpenDocuments {

    /**
     * One open file. [state] is what the page's `document` call answers, in the shape `main.ts`'s
     * `DocumentState` expects, and is kept current as the file changes underneath so that a page
     * which reloads shows the file rather than the text it was opened with.
     */
    class Entry(
        val id: Int,
        /** What says two links mean the same file; see [keyOf]. */
        val key: String,
        val name: String,
        /** The second line in the sidebar: where this file is, for telling two `main.ts` apart. */
        val where: String,
        val state: JSONObject,
        private val watched: WatchedFile,
        /** Null for a file with no text on screen — too large, binary, an encoding we cannot name. */
        val watcher: FileWatcher?,
    ) {

        /** What the page was last told this file holds, which is what a recheck compares against. */
        val text: String get() = state.optString("text")

        /**
         * Releases what watching it holds open. Safe to call on a file that is being watched right
         * now: [FileWatcher.watch] closes the same file when its job ends, and both are idempotent.
         */
        fun close() = watched.close()

        fun listed(): JSONObject = JSONObject().put("id", id).put("name", name).put("where", where)
    }

    private val entries = ArrayList<Entry>()
    private var nextId = 1

    var active: Entry? = null
        private set

    val all: List<Entry> get() = entries

    fun byId(id: Int): Entry? = entries.firstOrNull { it.id == id }

    fun byKey(key: String): Entry? = entries.firstOrNull { it.key == key }

    /** Adds a newly opened file at the end of the list and makes it the one on screen. */
    fun add(key: String, name: String, where: String, state: JSONObject, watched: WatchedFile, watcher: FileWatcher?): Entry {
        val entry = Entry(nextId++, key, name, where, state, watched, watcher)
        entries.add(entry)
        active = entry
        return entry
    }

    /** True when this moved the screen to another file. */
    fun activate(id: Int): Boolean {
        val entry = byId(id) ?: return false
        if (entry === active) return false
        active = entry
        return true
    }

    /**
     * Closes one file. The screen follows to the one after it — the one that took its place in the
     * list — and to the one before it when it was last. Closing the only file leaves nothing open.
     */
    fun close(id: Int) {
        val index = entries.indexOfFirst { it.id == id }
        if (index < 0) return
        val entry = entries.removeAt(index)
        entry.close()
        if (entry !== active) return
        active = entries.getOrNull(index) ?: entries.getOrNull(index - 1)
    }

    fun closeAll() {
        entries.forEach { it.close() }
        entries.clear()
        active = null
    }

    /** The whole list and which one is showing, for the sidebar. */
    fun listed(): JSONObject = JSONObject()
        .put("active", active?.id ?: JSONObject.NULL)
        .put("files", JSONArray(entries.map { it.listed() }))
}

/**
 * What makes two links the same open file. The request is the resolved one — an unknown server has
 * become a profile by the time a file is opened through it — so a remote file is its server and its
 * path, and the alias or address the link happened to use does not come into it.
 */
fun keyOf(request: OpenRequest): String = when (request) {
    is OpenRequest.LocalPath -> "local:${request.path}"
    is OpenRequest.Content -> "content:${request.uri}"
    is OpenRequest.Remote -> "remote:${request.profile.id}:${request.path}"
    // Neither reaches an opened file: UnknownServer becomes Remote once the user has agreed, and
    // Invalid never gets as far as reading one.
    is OpenRequest.UnknownServer -> "server:${request.user}@${request.host}:${request.port}:${request.path}"
    is OpenRequest.Invalid -> "invalid:${request.reason}"
}

/** The line under the file's name in the sidebar. */
fun whereOf(request: OpenRequest): String = when (request) {
    is OpenRequest.LocalPath -> request.path
    is OpenRequest.Content -> request.uri
    is OpenRequest.Remote ->
        "${request.profile.username}@${request.profile.host}:${request.profile.port}${request.path}"
    is OpenRequest.UnknownServer -> "${request.user}@${request.host}:${request.port}${request.path}"
    is OpenRequest.Invalid -> request.reason
}
