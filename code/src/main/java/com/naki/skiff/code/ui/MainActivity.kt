package com.naki.skiff.code.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.webkit.WebViewAssetLoader
import com.naki.skiff.code.R
import com.naki.skiff.code.bridge.WebBridge
import com.naki.skiff.code.intent.OpenRequest
import com.naki.skiff.code.lsp.StubLsp
import com.naki.skiff.code.skiffCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.concurrent.Executors

private const val TAG = "SkiffCode"
private const val ORIGIN = WebBridge.ORIGIN

/**
 * M0 spike: one WebView served from assets, JSON-RPC over a web message listener.
 * `sampleText` hands the page a generated source file so CodeMirror can be measured on a real device.
 * Launch with `--ez selftest true` to have the page run its scripted scroll and zoom measurements,
 * `--ei bytes N` to change the file size from 2 MB, `--es layer diff` for the unified diff and
 * `--es alpha 0.15` for the diff background opacity, `--es diff char|line` for the diff algorithm and
 * `--ez nopatch true` to leave out the CodeMirror viewport workaround. `--es layer lsp` connects the page's
 * `@codemirror/lsp-client` to the stub server in `lsp/StubLsp` over this same bridge, where
 * `--ez fullsync true` makes that server ask for whole-document syncing instead of incremental.
 *
 * Started with a link (`skiffcode://…`, or `content://…` from another app), it runs [OpenFlow] on
 * it. The spike page stays until the viewer replaces it; for now a file that opens is announced
 * in a toast and logged.
 */
class MainActivity : Activity() {

    private val container by lazy { application.skiffCode }
    private val scope = MainScope()
    private val bridge = WebBridge(scope)
    private var hostKeyDialog: AlertDialog? = null
    private var permissionAnswer: CompletableDeferred<Boolean>? = null
    private var returned: CompletableDeferred<Unit>? = null

    /** The stub server answers off the UI thread, in order, the way a real process's stdout would. */
    private val lspThread = Executors.newSingleThreadExecutor()

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

        registerSpikeMethods()
        bridge.attach(webView)

        setContentView(webView)
        val query = buildList {
            if (intent.getBooleanExtra("selftest", false)) add("selftest=1")
            intent.getIntExtra("bytes", 0).takeIf { it > 0 }?.let { add("bytes=$it") }
            intent.getStringExtra("layer")?.let { add("layer=${Uri.encode(it)}") }
            intent.getStringExtra("alpha")?.let { add("alpha=${Uri.encode(it)}") }
            intent.getStringExtra("diff")?.let { add("diff=${Uri.encode(it)}") }
            if (intent.getBooleanExtra("nopatch", false)) add("nopatch=1")
        }.joinToString("&")
        webView.loadUrl("$ORIGIN/assets/web/index.html" + if (query.isEmpty()) "" else "?$query")

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
        scope.launch {
            val request = OpenRequest.of(intent.action, link, container.store.profiles.first())
            Log.i(TAG, "open ${request.javaClass.simpleName}")
            val opened = OpenFlow(this@MainActivity, container).open(link, request) ?: return@launch
            Log.i(TAG, "opened ${opened.name}")
            Toast.makeText(this@MainActivity, getString(R.string.opened, opened.name), Toast.LENGTH_LONG).show()
        }
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
        lspThread.shutdownNow()
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

    /**
     * The spike page's two calls, until the viewer replaces it: `sampleText`, and the stub LSP
     * server, whose messages cross as notifications both ways — the server pushes diagnostics on
     * its own, the way a real one does.
     */
    private fun registerSpikeMethods() {
        bridge.method("sampleText") { params ->
            val bytes = params.getInt("bytes")
            val text = withContext(Dispatchers.Default) { sampleText(bytes) }
            JSONObject().put("text", text)
        }
        val lsp = StubLsp(intent.getBooleanExtra("fullsync", false)) { outgoing ->
            bridge.notify("lspMessage", JSONObject().put("message", outgoing))
        }
        bridge.onNotify("lspSend") { params ->
            val payload = params.getString("message")
            lspThread.execute { lsp.receive(payload) }
        }
    }

    /** A TypeScript-looking file of at least [bytes] UTF-8 bytes, with Korean comments for wide glyphs. */
    private fun sampleText(bytes: Int): String {
        val out = StringBuilder()
        var size = 0
        var n = 0
        while (size < bytes) {
            val block = TEMPLATE.replace("#N", n.toString()).replace("#M", (n % 7 + 2).toString())
            out.append(block)
            size += block.toByteArray().size
            n++
        }
        return out.toString()
    }
}

private const val REQUEST_PERMISSION = 1

private fun refused() = WebResourceResponse("text/plain", "utf-8", 403, "Forbidden", emptyMap(), ByteArrayInputStream(ByteArray(0)))

/** Serves `/assets/web/…` from the `web/` asset directory and nothing above it. */
private fun WebViewAssetLoader.AssetsPathHandler.underWeb() = WebViewAssetLoader.PathHandler { path -> handle("web/$path") }

private val TEMPLATE = """
    |// 블록 #N: 원격 파일을 읽어 줄 단위로 나눈다
    |export async function readChunk#N(path: string, offset = #N): Promise<string[]> {
    |  const response = await fetch(`/files/${'$'}{encodeURIComponent(path)}?offset=${'$'}{offset}`)
    |  if (!response.ok) throw new Error(`HTTP ${'$'}{response.status} for ${'$'}{path}`)
    |  const lines = (await response.text()).split('\n').map((line) => line.trimEnd())
    |  return lines.filter((line, index) => index % #M !== 0 || line.length > #N)
    |}
    |
""".trimMargin() + "\n"
