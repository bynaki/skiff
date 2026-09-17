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

private const val TAG = "SkiffCode"
private const val ORIGIN = "https://${WebViewAssetLoader.DEFAULT_DOMAIN}"

/** M0 spike: one WebView served from assets, one JSON-RPC round trip over a web message listener. */
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
        WebViewCompat.addWebMessageListener(webView, "skiffBridge", setOf(ORIGIN)) { _, message, sourceOrigin, isMainFrame, reply ->
            handle(message, sourceOrigin, isMainFrame, reply)
        }

        setContentView(webView)
        webView.loadUrl("$ORIGIN/assets/web/index.html")
    }

    private fun handle(message: WebMessageCompat, sourceOrigin: Uri, isMainFrame: Boolean, reply: JavaScriptReplyProxy) {
        val raw = message.data ?: return
        Log.i(TAG, "request from $sourceOrigin (mainFrame=$isMainFrame): $raw")
        val request = JSONObject(raw)
        val response = JSONObject().put("jsonrpc", "2.0").put("id", request.get("id"))
        when (request.getString("method")) {
            "ping" -> response.put(
                "result",
                JSONObject()
                    .put("echo", request.getJSONObject("params").getString("text"))
                    .put("from", "kotlin"),
            )
            else -> response.put("error", JSONObject().put("code", -32601).put("message", "Method not found"))
        }
        Log.i(TAG, "response: $response")
        reply.postMessage(response.toString())
    }
}
