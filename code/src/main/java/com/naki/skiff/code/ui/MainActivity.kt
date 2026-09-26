package com.naki.skiff.code.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.input.InputManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.InputDevice
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.webkit.WebViewAssetLoader
import com.naki.skiff.code.R
import com.naki.skiff.code.bridge.WebBridge
import com.naki.skiff.code.data.readSkiffProfiles
import com.naki.skiff.code.doc.DocumentSaver
import com.naki.skiff.code.doc.FileChange
import com.naki.skiff.code.doc.FileWatcher
import com.naki.skiff.code.doc.LoadResult
import com.naki.skiff.code.doc.SaveResult
import com.naki.skiff.code.doc.Stamp
import com.naki.skiff.code.doc.Stamped
import com.naki.skiff.code.intent.OpenRequest
import com.naki.skiff.code.intent.sentBySkiff
import com.naki.skiff.code.skiffCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.text.DecimalFormat

private const val TAG = "SkiffCode"
private const val ORIGIN = WebBridge.ORIGIN

/**
 * One WebView served from assets, talking JSON-RPC over [WebBridge]. The page shows whichever of
 * the open files this activity has made active: it asks with `documents` and `document`, and is
 * told `documentsChanged` when a link (`skiffcode://…`, or `content://…` from another app) has run
 * through [OpenFlow] to another one, or when the open files menu has moved between them.
 */
class MainActivity : Activity() {

    private val container by lazy { application.skiffCode }
    private val scope = MainScope()
    private val bridge = WebBridge(scope)
    private val saver = DocumentSaver()
    private var hostKeyDialog: AlertDialog? = null
    private var permissionAnswer: CompletableDeferred<Boolean>? = null
    private var returned: CompletableDeferred<Unit>? = null
    private var opening: Job? = null
    private var watching: Job? = null
    private var inFront = false

    /** Every file that is open at once, and which of them the page is showing. */
    private lateinit var docs: OpenDocuments

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/web/", WebViewAssetLoader.AssetsPathHandler(this).underWeb())
            .build()

        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) WebView.setWebContentsDebuggingEnabled(true)
        val webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
        // Off by default, which leaves `localStorage` null in the page. The palette keeps the last
        // things it ran there: a trace that gathers as the app is used rather than a setting anyone
        // edits, so it belongs to the page and not to `settings.toml`. It is this app's own data,
        // under this origin, and the page runs nothing but its own bundle.
        webView.settings.domStorageEnabled = true
        webView.webViewClient = object : WebViewClient() {
            // The bundle is the only thing the page may load. Anything else — an image in a
            // rendered document, a stray fetch — gets an empty 403 instead of reaching the network.
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse =
                assetLoader.shouldInterceptRequest(request.url) ?: refused()

            // The page is never navigated away from. A link the user taps goes to another app.
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                openExternally(request.url)
                return true
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                Log.i(TAG, "console: ${message.message()}")
                return true
            }
        }

        // A configuration change this activity does not handle destroys it and builds another with
        // the same intent. Re-running the link there opens it a second time — reconnecting, asking
        // about the path again, and adding a second copy of what was already on screen — so the open
        // files are carried across instead, with the watches on them, each holding the stamp it last
        // saw, so that a rotation is not a change. Nothing is carried when nothing was open: an open
        // still waiting on a dialog was cancelled with the old scope, and re-running the link is how
        // it comes back.
        docs = lastNonConfigurationInstance as? OpenDocuments ?: OpenDocuments()
        // Answered without suspending, so replies leave in the order the calls came in and the
        // page's last answer is always the current document.
        bridge.method("document") { params ->
            val entry = if (params.has("id")) docs.byId(params.getInt("id")) else docs.active
            entry?.state ?: emptyDocument()
        }
        bridge.method("documents") { docs.listed() }
        bridge.method("activate") { params ->
            if (docs.activate(params.getInt("id"))) documentsChanged()
            JSONObject()
        }
        // Whether the buffer had been typed in is the page's to know, so it is the page that asks
        // before this is called on one.
        bridge.method("close") { params ->
            docs.close(params.getInt("id"))
            documentsChanged()
            JSONObject()
        }
        // Writes the buffer back to the file it came from. The text comes from the page, which is
        // where the buffer lives; where it goes does not — that is the file this entry was opened
        // with, through the dialog that confirmed its path, and the page never names one. What is
        // checked here is that the file is still the one the buffer knows (DocumentSaver, against
        // Entry.base). Whether the buffer counts as clean again is the page's to decide, and this
        // answer is what it decides it from.
        bridge.method("save") { params ->
            val entry = docs.byId(params.getInt("id")) ?: error("no open file to save")
            val target = entry.save ?: return@method saved("readonly", getString(R.string.save_readonly))
            // A file with somewhere to save has text on screen, and text on screen is watched.
            val watcher = entry.watcher ?: error("${entry.name} can be saved but is not watched")
            val text = params.getString("text")
            val base = entry.base
            val result = saver.save(
                target.fs, target.path, text, target.format,
                expectedSize = base?.size ?: -1,
                expectedModifiedEpochSeconds = base?.modifiedEpochSeconds ?: -1,
            )
            when (result) {
                is SaveResult.Saved -> {
                    val now = Stamp(result.size, result.modifiedEpochSeconds)
                    entry.base = now
                    watcher.saved(now)
                    // What the file holds now, so a page that reloads shows what was written.
                    entry.state.put("text", text)
                    saved("saved", getString(R.string.save_done))
                }
                // Told, not offered: the watch is what brings the other change in, and it asks.
                is SaveResult.Conflict ->
                    saved("conflict", getString(if (result.current == null) R.string.save_gone else R.string.save_conflict))
                is SaveResult.Unencodable -> saved(
                    "unencodable",
                    getString(
                        R.string.save_unencodable,
                        target.format.encoding.charset.name(),
                        text.take(result.index).count { it == '\n' } + 1,
                        result.text,
                    ),
                )
            }
        }
        // The buffer has taken what the file says, so the file as it was pushed is what the next
        // save is measured against. Only the page can say this: it is the only side that knows
        // whether the user kept what they had typed instead.
        bridge.onNotify("adopted") { params ->
            docs.byId(params.getInt("id"))?.let { it.base = it.offered }
        }
        // Reads the file again now rather than at the next tick. What it finds goes to the page as
        // the change it is, so a clean buffer takes it and one that has been typed in is asked.
        bridge.method("reload") { params ->
            val entry = docs.byId(params.getInt("id")) ?: error("no open file to reload")
            entry.watcher?.reread { change -> fileChanged(entry, change) }
            JSONObject()
        }
        bridge.method("labels") {
            JSONObject()
                .put("sidebar", getString(R.string.menu_sidebar))
                .put("resetZoom", getString(R.string.menu_reset_zoom))
                .put("layerViewer", getString(R.string.menu_layer_viewer))
                .put("layerEditor", getString(R.string.menu_layer_editor))
                .put("layerDiff", getString(R.string.menu_layer_diff))
                .put("more", getString(R.string.menu_more))
                .put("unsaved", getString(R.string.menu_unsaved))
                .put("reload", getString(R.string.watch_reload))
                .put("keepMine", getString(R.string.watch_keep))
                .put("dismiss", getString(R.string.watch_dismiss))
                .put("noFiles", getString(R.string.viewer_empty))
                .put("noProjects", getString(R.string.sidebar_no_projects))
                .put("close", getString(R.string.action_close))
                .put("closeDirty", getString(R.string.close_dirty))
                .put("cancel", getString(R.string.action_cancel))
                .put("saveFailed", getString(R.string.save_failed))
                .put("reloadFailed", getString(R.string.reload_failed))
        }
        bridge.method("hardwareKeyboard") { hardwareKeyboard() }
        getSystemService(InputManager::class.java).registerInputDeviceListener(keyboards, null)
        bridge.attach(webView)

        // Android 15 draws the app under the system bars. The page is kept inside them by the frame
        // rather than by padding the WebView, whose content ignores its own padding.
        val frame = FrameLayout(this)
        frame.setBackgroundColor(Color.WHITE)
        frame.addView(webView)
        frame.setOnApplyWindowInsetsListener { view, insets ->
            // ime() as well, so the editor layer shrinks instead of letting the soft keyboard cover
            // the caret. The WebView getting smaller is what makes CodeMirror scroll the caret back
            // into view; padding the WebView's own content would not.
            val bars = insets.getInsets(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime(),
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsets.CONSUMED
        }
        setContentView(frame)
        // The theme is dark, so its bar icons are white; on the page's white they vanish.
        val light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        window.insetsController?.setSystemBarsAppearance(light, light)
        webView.loadUrl("$ORIGIN/assets/web/index.html")

        // The prompter outlives this activity, so a question asked while none was in front is
        // shown by whichever instance comes up next.
        scope.launch {
            container.hostKeyPrompter.pending.collect { prompt ->
                hostKeyDialog?.dismiss()
                hostKeyDialog = prompt?.let { hostKeyDialog(it, container.hostKeyPrompter::respond).apply { show() } }
            }
        }
        if (docs.all.isEmpty()) handleLink(intent, senderOf(initial = true))
    }

    /** What a save answers with: what happened, and how to say it on the page's banner. */
    private fun saved(result: String, message: String) = JSONObject().put("result", result).put("message", message)

    private fun emptyDocument() = JSONObject().put("state", "empty").put("message", getString(R.string.viewer_empty))

    /** What a configuration change carries across: the open files and the watches on them. */
    override fun onRetainNonConfigurationInstance(): Any? = docs.takeIf { it.all.isNotEmpty() }

    override fun onStart() {
        super.onStart()
        inFront = true
        // Every open file is looked at once here: the ones that are not showing are not polled, so
        // this is where a change made while the app was away reaches them (docs/skiffcode.spec.md "감시").
        startWatching(recheckAll = true)
    }

    /**
     * Nothing is polled while the app is away — a file changed meanwhile is caught by the
     * [FileWatcher.recheck] that [startWatching] opens with, rather than by asking a server every
     * two seconds for a screen nobody is looking at.
     */
    override fun onStop() {
        super.onStop()
        inFront = false
        watching?.cancel()
        watching = null
    }

    /**
     * Tells the page that the list of open files, or which one is on screen, has changed — and
     * moves the watch to whichever file that is. [goToLine] is a line the link that brought a file
     * back to the screen asked for; a file being opened for the first time carries its own.
     */
    private fun documentsChanged(goToLine: Int? = null) {
        bridge.notify("documentsChanged", JSONObject().putOpt("goToLine", goToLine))
        startWatching(recheckAll = false)
    }

    /**
     * Polls the file on screen, after one look at it off the clock so that switching to a file
     * shows what it says now rather than what it said two seconds ago. [recheckAll] adds that look
     * for every other open file, which is what coming back to the front asks for.
     */
    private fun startWatching(recheckAll: Boolean) {
        val previous = watching
        watching = null
        if (!inFront) {
            previous?.cancel()
            return
        }
        val active = docs.active
        watching = scope.launch {
            // Waited out, not just cancelled: [FileWatcher.watch] releases the file it was watching
            // on its way out, and switching away and straight back would otherwise have the old job
            // close the watch the new one has just opened.
            previous?.cancelAndJoin()
            // A copy: closing a file while this is suspended would otherwise change the list underneath.
            if (recheckAll) for (entry in docs.all.toList()) if (entry !== active) recheck(entry)
            if (active == null) return@launch
            recheck(active)
            active.watcher?.watch { change -> fileChanged(active, change) }
        }
    }

    private suspend fun recheck(entry: OpenDocuments.Entry) {
        entry.watcher?.recheck(entry.text) { change -> fileChanged(entry, change) }
    }

    /**
     * A change made somewhere else, on its way to the page. Only the new text is pushed: whether
     * to take it or to ask first is the page's to decide, because the page is the only side that
     * knows whether the user has typed since the file was read.
     */
    private suspend fun fileChanged(entry: OpenDocuments.Entry, change: FileChange) {
        // What the page is being offered, which becomes the save baseline if it takes it.
        entry.offered = (change as? FileChange.Changed)?.stamp
        val result = (change as? FileChange.Changed)?.result
        if (result is LoadResult.Text) {
            // What the file says now, so that a page which reloads — or an activity rebuilt by a
            // configuration change — shows the file rather than the text from before the change.
            entry.state.put("text", result.text)
            bridge.notify(
                "fileChanged",
                JSONObject().put("id", entry.id).put("change", "text").put("text", result.text)
                    .put("message", getString(R.string.watch_changed)),
            )
            return
        }
        // Nothing to merge: the file is gone, or is no longer text this page can show. What is on
        // screen stays there, with a banner saying it is no longer what the file holds.
        val message = if (change is FileChange.Gone) R.string.watch_gone else R.string.watch_unreadable
        bridge.notify(
            "fileChanged",
            JSONObject().put("id", entry.id).put("change", "notice").put("message", getString(message)),
        )
    }

    /**
     * Whether keys can arrive without the soft keyboard. The page cannot tell on its own and needs
     * it to decide whether entering the editor layer takes focus, so it is answered on request and
     * pushed again whenever a device comes or goes.
     *
     * The devices are asked, not `Configuration.keyboard`: on the tablet that stays `nokeys` with a
     * Bluetooth keyboard connected and typing (`am get-config` said `keysexposed-nokeys` while
     * `dumpsys input` listed the keyboard as enabled), so reading the configuration answered no to
     * a keyboard that was right there. The virtual device every Android has is excluded by
     * [InputDevice.isVirtual], and a keyboard with no letters — a remote's d-pad, a volume rocker —
     * by [InputDevice.KEYBOARD_TYPE_ALPHABETIC].
     */
    private fun hardwareKeyboard(): JSONObject = JSONObject().put(
        "present",
        InputDevice.getDeviceIds().any { id ->
            val device = InputDevice.getDevice(id) ?: return@any false
            !device.isVirtual && device.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC &&
                device.supportsSource(InputDevice.SOURCE_KEYBOARD)
        },
    )

    private val keyboards = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = bridge.notify("hardwareKeyboardChanged", hardwareKeyboard())
        override fun onInputDeviceRemoved(deviceId: Int) = bridge.notify("hardwareKeyboardChanged", hardwareKeyboard())
        override fun onInputDeviceChanged(deviceId: Int) = bridge.notify("hardwareKeyboardChanged", hardwareKeyboard())
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // The sender of *this* intent, not of the one that started the activity. A link arriving
        // in a running instance is exactly the case where the two differ.
        handleLink(intent, senderOf(initial = false))
    }

    /**
     * The package that sent an intent, when the system will say so: only for a sender that shared
     * its identity, and only from Android 15, where `ComponentCaller` arrived. Null otherwise,
     * which [sentBySkiff] reads as "not Skiff" and so asks about the path.
     */
    private fun senderOf(initial: Boolean): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return null
        return try {
            (if (initial) initialCaller else currentCaller).getPackage()
        } catch (e: IllegalStateException) {
            // getCurrentCaller only answers inside onNewIntent, and not on every path into it.
            Log.i(TAG, "no caller for this intent", e)
            null
        }
    }

    private fun handleLink(intent: Intent, sender: String?) {
        val link = intent.dataString ?: return
        val fromSkiff = sentBySkiff(sender)
        // A newer link replaces one still being opened: its dialogs close and its result is dropped,
        // rather than a second set of dialogs stacking on top of the first.
        opening?.cancel()
        tellOpening(true)
        val job = scope.launch {
            var request = OpenRequest.of(intent.action, link, container.store.profiles.first())
            if (request is OpenRequest.Remote || request is OpenRequest.UnknownServer) {
                // Skiff may know this server, or know it better than we do: take its profiles first.
                val shared = withContext(Dispatchers.IO) { readSkiffProfiles(this@MainActivity) }
                if (shared != null) {
                    container.store.importFromSkiff(shared.first, shared.second)
                    request = OpenRequest.of(intent.action, link, container.store.profiles.first())
                }
            }
            Log.i(TAG, "open ${request.javaClass.simpleName} from ${sender ?: "an app that did not say"}")
            val opened = OpenFlow(this@MainActivity, container).open(link, request, fromSkiff) ?: return@launch
            Log.i(TAG, "opened ${opened.name}: ${opened.result.javaClass.simpleName}")
            val key = keyOf(opened.request)
            val already = docs.byKey(key)
            if (already != null) {
                // The file is already open. What is in its buffer stays — it may have been typed in
                // — and the link only brings it back to the screen, at the line it asked for.
                opened.watched.close()
                docs.activate(already.id)
                documentsChanged(goToLine = opened.line)
                return@launch
            }
            val watcher = if (opened.result is LoadResult.Text) {
                FileWatcher(opened.watched, opened.stamp, warn = { message, e -> Log.w(TAG, message, e) })
            } else {
                // Nothing on screen for a change to be merged into: too large, binary, or in an
                // encoding we cannot name.
                opened.watched.close()
                null
            }
            docs.add(
                key, opened.name, whereOf(opened.request), documentState(opened), opened.watched, watcher,
                opened.save, (opened.stamp as? Stamped.At)?.stamp,
            )
            documentsChanged()
        }
        opening = job
        // However it ended — opened, refused, or cancelled by a newer link. A link that was
        // replaced leaves the wait to the one that replaced it, whose own start has already been
        // sent: taking the line away here would take that one's too.
        job.invokeOnCompletion { if (opening === job) tellOpening(false) }
    }

    /**
     * Whether a link is on its way to the screen, which is the whole of what the page's loading
     * line follows. Everything between the intent and the document happens over here — the
     * dialogs, connecting, `stat`, the read — and until this the page had no way to know.
     */
    private fun tellOpening(opening: Boolean) =
        bridge.notify("openingChanged", JSONObject().put("opening", opening))

    private fun documentState(opened: OpenFlow.Opened): JSONObject {
        val state = JSONObject().put("name", opened.name)
        val refusal = when (val result = opened.result) {
            is LoadResult.Text -> return state.put("state", "text").put("text", result.text).putOpt("line", opened.line)
            // The limit is in binary megabytes; Formatter counts in thousands and calls 2 MiB "2.1 MB".
            is LoadResult.TooLarge -> getString(R.string.refused_too_large, DecimalFormat("0.#").format(result.limit / 1048576.0) + " MB")
            LoadResult.Binary -> getString(R.string.refused_binary)
            LoadResult.UnknownEncoding -> getString(R.string.refused_encoding)
        }
        return state.put("state", "refused").put("title", getString(R.string.error_open)).put("message", refusal)
    }

    /** For settings screens that grant something: resumes when this activity is back in front. */
    suspend fun startActivityAndWaitForReturn(intent: Intent) {
        val back = CompletableDeferred<Unit>().also { returned = it }
        startActivity(intent)
        back.await()
    }

    override fun onResume() {
        super.onResume()
        returned?.complete(Unit)
        returned = null
    }

    suspend fun requestPermissionAndWait(permission: String): Boolean {
        val answer = CompletableDeferred<Boolean>().also { permissionAnswer = it }
        requestPermissions(arrayOf(permission), REQUEST_PERMISSION)
        return answer.await()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_PERMISSION) return
        permissionAnswer?.complete(grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
        permissionAnswer = null
    }

    override fun onDestroy() {
        super.onDestroy()
        // The open files are handed to the next instance across a configuration change; anywhere
        // else this is the end of them.
        if (!isChangingConfigurations) docs.closeAll()
        getSystemService(InputManager::class.java).unregisterInputDeviceListener(keyboards)
        hostKeyDialog?.dismiss()
        scope.cancel()
    }

    /** Links out of the page. Only schemes another app should handle; `intent:` and the like are dropped. */
    private fun openExternally(uri: Uri) {
        if (uri.scheme !in setOf("http", "https", "mailto")) {
            Log.w(TAG, "link not opened: ${uri.scheme}")
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE))
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "no app for $uri", e)
        }
    }
}

private const val REQUEST_PERMISSION = 1

private fun refused() = WebResourceResponse("text/plain", "utf-8", 403, "Forbidden", emptyMap(), ByteArrayInputStream(ByteArray(0)))

/** Serves `/assets/web/…` from the `web/` asset directory and nothing above it. */
private fun WebViewAssetLoader.AssetsPathHandler.underWeb() = WebViewAssetLoader.PathHandler { path -> handle("web/$path") }
