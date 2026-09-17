package com.naki.skiff.code.ui

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject
import kotlin.concurrent.thread

private const val TAG = "SkiffCode"
private const val ORIGIN = "https://${WebViewAssetLoader.DEFAULT_DOMAIN}"

/**
 * M0 spike: one WebView served from assets, JSON-RPC over a web message listener.
 * `sampleText` hands the page a generated source file so CodeMirror can be measured on a real device.
 * Launch with `--ez selftest true` to have the page run its scripted scroll and zoom measurements,
 * and `--ei bytes N` to change the file size from 2 MB.
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        val webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                assetLoader.shouldInterceptRequest(request.url)
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                Log.i(TAG, "console: ${message.message()}")
                return true
            }
        }

        check(WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            "WebView does not support WEB_MESSAGE_LISTENER"
        }
        WebViewCompat.addWebMessageListener(webView, "skiffBridge", setOf(ORIGIN)) { _, message, sourceOrigin, _, reply ->
            handle(message, sourceOrigin, reply)
        }

        setContentView(webView)
        val query = buildList {
            if (intent.getBooleanExtra("selftest", false)) add("selftest=1")
            intent.getIntExtra("bytes", 0).takeIf { it > 0 }?.let { add("bytes=$it") }
        }.joinToString("&")
        webView.loadUrl("$ORIGIN/assets/web/index.html" + if (query.isEmpty()) "" else "?$query")
    }

    private fun handle(message: WebMessageCompat, sourceOrigin: Uri, reply: JavaScriptReplyProxy) {
        val request = JSONObject(message.data ?: return)
        val id = request.get("id")
        val method = request.getString("method")
        Log.i(TAG, "request $method from $sourceOrigin")
        when (method) {
            "sampleText" -> {
                val bytes = request.getJSONObject("params").getInt("bytes")
                thread {
                    val started = System.nanoTime()
                    val text = sampleText(bytes)
                    val built = System.nanoTime()
                    val response = JSONObject().put("jsonrpc", "2.0").put("id", id)
                        .put("result", JSONObject().put("text", text)).toString()
                    Log.i(
                        TAG,
                        "sampleText: ${text.length} chars, built in ${(built - started) / 1_000_000} ms, " +
                            "JSON encoded in ${(System.nanoTime() - built) / 1_000_000} ms",
                    )
                    runOnUiThread { reply.postMessage(response) }
                }
            }
            else -> reply.postMessage(
                JSONObject().put("jsonrpc", "2.0").put("id", id)
                    .put("error", JSONObject().put("code", -32601).put("message", "Method not found")).toString(),
            )
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
