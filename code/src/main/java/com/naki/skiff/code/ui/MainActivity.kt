package com.naki.skiff.code.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.input.InputManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
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
import com.naki.skiff.code.doc.TextLoader
import com.naki.skiff.code.intent.OpenAt
import com.naki.skiff.code.intent.OpenRequest
import com.naki.skiff.code.intent.SkiffCodeUri
import com.naki.skiff.code.intent.sentBySkiff
import com.naki.skiff.code.settings.SettingsProblem
import com.naki.skiff.code.settings.SettingsRead
import com.naki.skiff.code.settings.SettingsToml
import com.naki.skiff.code.settings.Theme
import com.naki.skiff.code.settings.Themes
import com.naki.skiff.code.skiffCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import okio.buffer
import okio.source
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
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
    private lateinit var frame: FrameLayout
    /** The device's dark mode as the page was last told it, for a `system` theme to follow. */
    private var night = false
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
                    val answer = saved("saved", getString(R.string.save_done))
                    // Saving the settings is what applies them. What in them could not be used rides
                    // on the answer rather than a banner of its own, which the save's would replace.
                    if (entry.key == keyOf(OpenRequest.LocalPath(container.settings.file.path, OpenAt()))) {
                        val read = container.settings.reload()
                        bridge.notify("settingsChanged", pageSettings(read))
                        answer.putOpt("warning", describe(read.problems))
                    }
                    // A theme of the person's own is applied by saving it too, by the same rules. Only
                    // the file in this app's own files: a server's path could read the same.
                    val local = entry.key == keyOf(OpenRequest.LocalPath(target.path, OpenAt()))
                    container.themes.ownAt(target.path)?.takeIf { local }?.let { theme ->
                        val problems = withContext(Dispatchers.IO) { container.themes.reread(theme) }
                        bridge.notify("settingsChanged", pageSettings(container.settings.current()))
                        answer.putOpt("warning", describe(problems, theme = true))
                    }
                    answer
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
        // The files beside the one named, for the palette's file mode: every file in its directory
        // that is not open already. A `content://` document has no directory, and answers none.
        bridge.method("folder") { params ->
            val folder = docs.byId(params.getInt("id"))?.folder
            val names = folder?.names { request -> docs.byKey(keyOf(request)) != null } ?: emptyList()
            JSONObject().put("names", JSONArray(names))
        }
        // Opens a file the palette's file mode offered from beside an open one. The page sends only
        // a name, and [Folder.request] keeps it to that file's directory; the path is one the user
        // picked in this app, so it is not confirmed the way a link's is.
        bridge.method("openFromFolder") { params ->
            val entry = docs.byId(params.getInt("id")) ?: error("no open file to open beside")
            val name = params.getString("name")
            val request = entry.folder?.request(name) ?: error("not a file beside ${entry.name}: $name")
            startOpening { openFlow().open(linkOf(request), request, pathChosen = true) }
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
        bridge.method("settings") { pageSettings(container.settings.current()) }
        // The palette's Theme commands. Writing the file is what applies it, the same as saving it.
        bridge.method("setTheme") { params ->
            val name = params.getString("name")
            require(name in container.themes.names) { "no theme called $name" }
            bridge.notify("settingsChanged", pageSettings(container.settings.setTheme(name)))
            JSONObject()
        }
        // In and out through the system's file picker (SAF), so the file is one the person chose and
        // no storage permission is asked for. What follows the pick is [onActivityResult]'s, keyed by
        // the request alone: a picker that outlives this instance answers the next one.
        bridge.method("importSettings") {
            startActivityForResult(picker(Intent.ACTION_OPEN_DOCUMENT), REQUEST_IMPORT_SETTINGS)
            JSONObject()
        }
        bridge.method("importTheme") {
            startActivityForResult(picker(Intent.ACTION_OPEN_DOCUMENT), REQUEST_IMPORT_THEME)
            JSONObject()
        }
        bridge.method("exportSettings") {
            startActivityForResult(picker(Intent.ACTION_CREATE_DOCUMENT, "settings.toml"), REQUEST_EXPORT_SETTINGS)
            JSONObject()
        }
        bridge.method("exportTheme") {
            startActivityForResult(picker(Intent.ACTION_CREATE_DOCUMENT, shownTheme() + Themes.SUFFIX), REQUEST_EXPORT_THEME)
            JSONObject()
        }
        // Created on first use, with every key at its default and what it does, so there is a file
        // to open and something in it to read.
        bridge.method("openSettings") {
            container.settings.ensureExists()
            startOpening { openFlow().openOwn(container.settings.file) }
            JSONObject()
        }
        // The theme on the screen, as Export Theme takes it. A bundled theme is part of the app, so it
        // is copied rather than edited or deleted, and the page leaves Delete Theme out for one.
        bridge.method("copyTheme") {
            copyTheme(shownTheme())
            JSONObject()
        }
        bridge.method("editTheme") {
            editTheme(shownTheme())
            JSONObject()
        }
        bridge.method("deleteTheme") {
            deleteTheme(shownTheme())
            JSONObject()
        }
        getSystemService(InputManager::class.java).registerInputDeviceListener(keyboards, null)
        bridge.attach(webView)

        // Android 15 draws the app under the system bars. The page is kept inside them by the frame
        // rather than by padding the WebView, whose content ignores its own padding.
        frame = FrameLayout(this)
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
        // Otherwise the system lays a dark scrim over the navigation bar strip, and a light theme
        // ends in a band of grey the frame's color never reaches.
        window.isNavigationBarContrastEnforced = false
        night = isNight(resources.configuration)
        // Before the page has asked for the settings, so the frame is not white under a dark theme.
        scope.launch { showAround(container.settings.current()) }
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

    /** Reads with the size limit `settings.toml` says now, which is why each open builds its own. */
    private suspend fun openFlow() =
        OpenFlow(this, container, TextLoader(container.settings.current().settings.files.maxSizeBytes))

    /**
     * The settings the page applies itself; the file limits and the poll stay on this side. The
     * theme goes as its colors, already chosen for the dark mode the device is in — and around the
     * page too, since whatever is sent here is what the page is about to become.
     */
    private fun pageSettings(read: SettingsRead): JSONObject = with(read.settings) {
        val theme = showAround(read)
        JSONObject()
            .put("font", editor.font)
            .put("fontSize", editor.fontSize)
            .put("tabSize", editor.tabSize)
            .put("wrap", editor.wrap)
            .put("keptBuffers", files.keptBuffers)
            .put("theme", JSONObject().put("dark", theme.dark).put("colors", JSONObject(theme.colors)))
            .put("themes", JSONArray(container.themes.names))
            .put("ownTheme", container.themes.isImported(container.themes.nameIn(editor.theme, night)))
    }

    /** The theme on the screen by name: the one chosen, or for `system` the bundled one it follows now. */
    private suspend fun shownTheme() = container.themes.nameIn(container.settings.current().settings.editor.theme, night)

    /** The picker for a `.toml`: any type to open, since providers disagree on what TOML's is. */
    private fun picker(action: String, title: String? = null) = Intent(action)
        .addCategory(Intent.CATEGORY_OPENABLE)
        .setType(if (action == Intent.ACTION_OPEN_DOCUMENT) "*/*" else "application/toml")
        .apply { if (title != null) putExtra(Intent.EXTRA_TITLE, title) }

    /** Backing out of the picker does nothing and says nothing. */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        scope.launch {
            try {
                when (requestCode) {
                    REQUEST_IMPORT_SETTINGS -> importSettings(uri)
                    REQUEST_IMPORT_THEME -> importTheme(uri)
                    REQUEST_EXPORT_SETTINGS -> export(uri, container.settings.text())
                    REQUEST_EXPORT_THEME -> export(uri, withContext(Dispatchers.IO) { container.themes.text(shownTheme()) })
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A provider that went away, a document revoked or deleted under the picker, a full disk.
                Log.w(TAG, "import or export failed: $requestCode", e)
                notice(getString(R.string.transfer_failed), lasting = true)
            }
        }
    }

    /** Saving an imported file is what applies it, by the same rules as saving it in the app. */
    private suspend fun importSettings(uri: Uri) {
        val text = readPicked(uri) ?: return
        val read = container.settings.import(text)
        bridge.notify("settingsChanged", pageSettings(read))
        notice(getString(R.string.import_settings_done), describe(read.problems))
    }

    /**
     * Keeps the file as a theme named after it and puts it on the screen. A name already brought in is
     * asked about first; one of the bundled themes' is refused by [Themes.nameOf] before that.
     */
    private suspend fun importTheme(uri: Uri) {
        val fileName = withContext(Dispatchers.IO) {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        } ?: uri.lastPathSegment.orEmpty()
        val name = Themes.nameOf(fileName)
            ?: return notice(getString(R.string.theme_name_refused, fileName), lasting = true)
        val text = readPicked(uri) ?: return
        if (container.themes.isImported(name) &&
            !confirm(getString(R.string.theme_replace_title), getString(R.string.theme_replace, name), getString(R.string.action_replace))
        ) return
        val problems = withContext(Dispatchers.IO) { container.themes.import(name, text) }
        bridge.notify("settingsChanged", pageSettings(container.settings.setTheme(name)))
        notice(getString(R.string.import_theme_done, name), describe(problems, theme = true))
    }

    /**
     * Copies the theme [from] under a name the person gives, puts the copy on the screen and opens it
     * to be edited — which is what a copy is almost always made for. The copy is theirs: it does not
     * follow the bundled colors when the app is updated.
     */
    private suspend fun copyTheme(from: String) {
        val name = askCopyName(from) ?: return
        if (container.themes.isImported(name) &&
            !confirm(getString(R.string.theme_replace_title), getString(R.string.theme_copy_replace, name, from), getString(R.string.action_replace))
        ) return
        withContext(Dispatchers.IO) { container.themes.import(name, container.themes.text(from)) }
        bridge.notify("settingsChanged", pageSettings(container.settings.setTheme(name)))
        startOpening { openFlow().openOwn(container.themes.file(name)) }
    }

    /** A name for a copy of [from], asked again with the reason for as long as it is not one a theme can have. */
    private suspend fun askCopyName(from: String): String? {
        var typed = getString(R.string.theme_copy_name, from)
        var refused: String? = null
        while (true) {
            val message = listOfNotNull(refused, getString(R.string.theme_copy_body, from)).joinToString("\n\n")
            typed = askText(getString(R.string.theme_copy_title), message, typed, getString(R.string.action_copy))?.trim() ?: return null
            Themes.nameOf(typed)?.let { return it }
            refused = getString(R.string.theme_copy_refused, typed)
        }
    }

    /** Opens the theme [name] to be edited, or offers a copy of it when it is one of the bundled ones. */
    private suspend fun editTheme(name: String) {
        if (container.themes.isImported(name)) return startOpening { openFlow().openOwn(container.themes.file(name)) }
        if (confirm(getString(R.string.theme_bundled_title), getString(R.string.theme_bundled, name), getString(R.string.action_copy))) {
            copyTheme(name)
        }
    }

    /**
     * Deletes the theme [name] and goes back to `system`. Its file is closed if it is open, typing and
     * all — that is what deleting it means — rather than left on the screen saying it is gone.
     */
    private suspend fun deleteTheme(name: String) {
        require(container.themes.isImported(name)) { "a bundled theme cannot be deleted: $name" }
        if (!confirm(getString(R.string.theme_delete_title), getString(R.string.theme_delete, name), getString(R.string.action_delete))) return
        docs.byKey(keyOf(OpenRequest.LocalPath(container.themes.file(name).path, OpenAt())))?.let {
            docs.close(it.id)
            documentsChanged()
        }
        withContext(Dispatchers.IO) { container.themes.delete(name) }
        bridge.notify("settingsChanged", pageSettings(container.settings.setTheme(SettingsToml.SYSTEM_THEME)))
    }

    /** The picked document as text, or null — said on the banner — when it is too large to be either file. */
    private suspend fun readPicked(uri: Uri): String? {
        val text = withContext(Dispatchers.IO) {
            val input = contentResolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString())
            input.source().buffer().use { if (it.request(IMPORT_LIMIT + 1L)) null else it.readUtf8() }
        }
        if (text == null) notice(getString(R.string.import_too_large, IMPORT_LIMIT / 1024), lasting = true)
        return text
    }

    private suspend fun export(uri: Uri, text: String) {
        withContext(Dispatchers.IO) {
            val output = contentResolver.openOutputStream(uri, "wt") ?: throw FileNotFoundException(uri.toString())
            output.use { it.write(text.encodeToByteArray()) }
        }
        notice(getString(R.string.export_done), lasting = false)
    }

    /** What was done, and what in the file was passed over, on the banner: kept there when there was any. */
    private fun notice(done: String, problems: String?) = notice(listOfNotNull(done, problems).joinToString(" "), lasting = problems != null)

    private fun notice(message: String, lasting: Boolean) =
        bridge.notify("notice", JSONObject().put("message", message).put("lasting", lasting))

    /**
     * Paints what is outside the page — the strips the frame keeps clear under the system bars, and
     * the WebView before its page draws — the color of the menu, and turns the bar icons to show on it.
     */
    private fun showAround(read: SettingsRead): Theme {
        val theme = container.themes.resolve(read.settings.editor.theme, night)
        frame.setBackgroundColor(theme.argb("ui.background"))
        frame.getChildAt(0).setBackgroundColor(theme.argb("editor.background"))
        val light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        window.insetsController?.setSystemBarsAppearance(if (theme.dark) 0 else light, light)
        return theme
    }

    /**
     * `uiMode` is in the manifest's `configChanges`, so turning dark mode on or off lands here rather
     * than rebuilding the activity — which would put the reader back in the viewer at the top. A
     * `system` theme follows it; any other choice sends the same colors again, which changes nothing.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val now = isNight(newConfig)
        if (now == night) return
        night = now
        scope.launch { bridge.notify("settingsChanged", pageSettings(container.settings.current())) }
    }

    private fun isNight(config: Configuration) =
        config.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    /**
     * What in `settings.toml` — or in a theme, when [theme] — was passed over, as one line for the
     * banner, or null when nothing was.
     */
    private fun describe(problems: List<SettingsProblem>, theme: Boolean = false): String? = problems.takeIf { it.isNotEmpty() }?.joinToString(" ") {
        when (it) {
            is SettingsProblem.Unreadable -> getString(if (theme) R.string.theme_unreadable else R.string.settings_unreadable, it.reason)
            is SettingsProblem.UnknownKey -> getString(if (theme) R.string.theme_unknown_key else R.string.settings_unknown_key, it.key)
            is SettingsProblem.Refused -> if (it.allowed == null) {
                getString(R.string.settings_refused, it.key, it.value, it.default)
            } else {
                getString(R.string.settings_out_of_range, it.key, it.value, it.allowed.first, it.allowed.last, it.default)
            }
        }
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
        // Converted here rather than in OpenRequest.of, so the recent files keep the skiffcode:// form.
        val link = SkiffCodeUri.fromWebLink(intent.dataString ?: return)
        val fromSkiff = sentBySkiff(sender)
        startOpening {
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
            openFlow().open(link, request, pathChosen = fromSkiff)
        }
    }

    /**
     * Opens a file and puts it on the screen — from a link, or from the palette's file mode. A newer
     * one replaces one still being opened: its dialogs close and its result is dropped, rather than
     * a second set of dialogs stacking on top of the first.
     */
    private fun startOpening(open: suspend () -> OpenFlow.Opened?) {
        opening?.cancel()
        tellOpening(true)
        val job = scope.launch {
            val opened = open() ?: return@launch
            Log.i(TAG, "opened ${opened.name}: ${opened.result.javaClass.simpleName}")
            val key = keyOf(opened.request)
            val already = docs.byKey(key)
            if (already != null) {
                // The file is already open. What is in its buffer stays — it may have been typed in
                // — and the link only brings it back to the screen, at the line it asked for.
                opened.watched.close()
                docs.activate(already.id)
                documentsChanged(goToLine = opened.line)
                opened.notice?.let { notice(it, lasting = true) }
                return@launch
            }
            val watcher = if (opened.result is LoadResult.Text) {
                FileWatcher(
                    opened.watched, opened.stamp,
                    pollMillis = { container.settings.latest.files.pollMillis },
                    warn = { message, e -> Log.w(TAG, message, e) },
                )
            } else {
                // Nothing on screen for a change to be merged into: too large, binary, or in an
                // encoding we cannot name.
                opened.watched.close()
                null
            }
            docs.add(
                key, opened.name, whereOf(opened.request), documentState(opened), opened.watched, watcher,
                opened.save, (opened.stamp as? Stamped.At)?.stamp, opened.folder,
            )
            documentsChanged()
            opened.notice?.let { notice(it, lasting = true) }
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
private const val REQUEST_IMPORT_SETTINGS = 2
private const val REQUEST_IMPORT_THEME = 3
private const val REQUEST_EXPORT_SETTINGS = 4
private const val REQUEST_EXPORT_THEME = 5

/** Larger than either file has any reason to be; what is picked is read whole, into memory. */
private const val IMPORT_LIMIT = 256 * 1024

private fun refused() = WebResourceResponse("text/plain", "utf-8", 403, "Forbidden", emptyMap(), ByteArrayInputStream(ByteArray(0)))

/** Serves `/assets/web/…` from the `web/` asset directory and nothing above it. */
private fun WebViewAssetLoader.AssetsPathHandler.underWeb() = WebViewAssetLoader.PathHandler { path -> handle("web/$path") }
