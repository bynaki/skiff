package com.naki.skiff.code.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
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
import com.naki.skiff.code.doc.LoadResult
import com.naki.skiff.code.intent.OpenRequest
import com.naki.skiff.code.skiffCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.text.DecimalFormat

private const val TAG = "SkiffCode"
private const val ORIGIN = WebBridge.ORIGIN

/**
 * One WebView served from assets, talking JSON-RPC over [WebBridge]. The page shows whatever
 * document this activity holds: it asks with `document`, and is told `documentChanged` when a link
 * (`skiffcode://…`, or `content://…` from another app) has run through [OpenFlow] to a new one.
 */
class MainActivity : Activity() {

    private val container by lazy { application.skiffCode }
    private val scope = MainScope()
    private val bridge = WebBridge(scope)
    private var hostKeyDialog: AlertDialog? = null
    private var permissionAnswer: CompletableDeferred<Boolean>? = null
    private var returned: CompletableDeferred<Unit>? = null
    private var opening: Job? = null

    /** What the page's `document` call answers, in the shape `main.ts`'s `DocumentState` expects. */
    private lateinit var document: JSONObject

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

        document = JSONObject().put("state", "empty").put("message", getString(R.string.viewer_empty))
        // Answered without suspending, so replies leave in the order the calls came in and the
        // page's last answer is always the current document.
        bridge.method("document") { document }
        bridge.method("labels") {
            JSONObject()
                .put("sidebar", getString(R.string.menu_sidebar))
                .put("resetZoom", getString(R.string.menu_reset_zoom))
                .put("layerViewer", getString(R.string.menu_layer_viewer))
                .put("layerEditor", getString(R.string.menu_layer_editor))
                .put("layerDiff", getString(R.string.menu_layer_diff))
                .put("more", getString(R.string.menu_more))
        }
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
        handleLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLink(intent)
    }

    private fun handleLink(intent: Intent) {
        val link = intent.dataString ?: return
        // A newer link replaces one still being opened: its dialogs close and its result is dropped,
        // rather than a second set of dialogs stacking on top of the first.
        opening?.cancel()
        opening = scope.launch {
            var request = OpenRequest.of(intent.action, link, container.store.profiles.first())
            if (request is OpenRequest.Remote || request is OpenRequest.UnknownServer) {
                // Skiff may know this server, or know it better than we do: take its profiles first.
                val shared = withContext(Dispatchers.IO) { readSkiffProfiles(this@MainActivity) }
                if (shared != null) {
                    container.store.importFromSkiff(shared.first, shared.second)
                    request = OpenRequest.of(intent.action, link, container.store.profiles.first())
                }
            }
            Log.i(TAG, "open ${request.javaClass.simpleName}")
            val opened = OpenFlow(this@MainActivity, container).open(link, request) ?: return@launch
            Log.i(TAG, "opened ${opened.name}: ${opened.result.javaClass.simpleName}")
            document = documentState(opened)
            bridge.notify("documentChanged", JSONObject())
        }
    }

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
