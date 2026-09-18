package com.naki.skiff.code.bridge

import android.util.Log
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject

/**
 * JSON-RPC 2.0 between Kotlin and the page, over the one web message listener `skiffBridge`.
 *
 * The page calls [method]s and gets an answer; either side can send a notification (no `id`, no
 * answer), which is how Kotlin speaks first — LSP messages, file changes. Kotlin cannot notify
 * before the page has said `ready`: the reply proxy that reaches the page arrives with the page's
 * first message, so anything sent earlier is held until then and delivered in order.
 *
 * Only messages from the main frame at [ORIGIN] are taken. The listener's allowed-origin rule
 * already stops other origins from seeing `skiffBridge` at all; the frame check keeps an iframe
 * that a rendered document might someday carry away from it.
 *
 * Everything runs on [scope], which must be the main thread's: that is where the listener is
 * called and the only thread a reply proxy may be used from. Handlers move their own work off it.
 */
class WebBridge(
    private val scope: CoroutineScope,
    /** logcat on the device; a plain JVM test, where [Log] is a stub that throws, passes its own. */
    private val warn: (String, Throwable?) -> Unit = { message, e -> Log.w(TAG, message, e) },
) {

    private val methods = HashMap<String, suspend (JSONObject) -> Any?>()
    private val notificationHandlers = HashMap<String, (JSONObject) -> Unit>()
    private var toPage: ((String) -> Unit)? = null
    private val held = ArrayList<String>()

    /** Answers the page's calls to [name]. The result must be something [JSONObject.put] accepts. */
    fun method(name: String, handler: suspend (params: JSONObject) -> Any?) {
        methods[name] = handler
    }

    /** Handles the page's notifications named [name]. */
    fun onNotify(name: String, handler: (params: JSONObject) -> Unit) {
        notificationHandlers[name] = handler
    }

    /** Sends a notification to the page. Safe from any thread. */
    fun notify(method: String, params: JSONObject) {
        val message = JSONObject().put("jsonrpc", "2.0").put("method", method).put("params", params).toString()
        scope.launch { toPage?.invoke(message) ?: held.add(message) }
    }

    fun attach(webView: WebView) {
        check(WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            "WebView does not support WEB_MESSAGE_LISTENER"
        }
        WebViewCompat.addWebMessageListener(webView, NAME, setOf(ORIGIN)) { _, message, sourceOrigin, isMainFrame, reply ->
            if (!isMainFrame || sourceOrigin.toString() != ORIGIN) {
                warn("bridge message refused from $sourceOrigin (main frame: $isMainFrame)", null)
                return@addWebMessageListener
            }
            receive(message.data ?: return@addWebMessageListener) { reply.postMessage(it) }
        }
    }

    /** One message from the page. [reply] reaches the page that sent it. */
    fun receive(data: String, reply: (String) -> Unit) {
        val message = try {
            JSONObject(data)
        } catch (e: JSONException) {
            warn("bridge message is not JSON", e)
            return
        }
        val method = message.optString("method")
        val params = message.optJSONObject("params") ?: JSONObject()

        // No id means the page is notifying, not calling: there is nothing to answer.
        if (!message.has("id")) {
            if (method == READY) {
                toPage = reply
                held.forEach(reply)
                held.clear()
                return
            }
            val handler = notificationHandlers[method] ?: return warn("unhandled notification $method", null)
            try {
                handler(params)
            } catch (e: Exception) {
                warn("notification $method failed", e)
            }
            return
        }

        val id = message.get("id")
        val handler = methods[method] ?: return reply(error(id, METHOD_NOT_FOUND, "Method not found: $method").toString())
        scope.launch {
            val answer = try {
                JSONObject().put("jsonrpc", "2.0").put("id", id).put("result", handler(params) ?: JSONObject.NULL)
            } catch (e: CancellationException) {
                throw e
            } catch (e: JSONException) {
                warn("call $method had bad params", e)
                error(id, INVALID_PARAMS, e.message ?: "Invalid params")
            } catch (e: Exception) {
                warn("call $method failed", e)
                error(id, SERVER_ERROR, e.message ?: e.javaClass.simpleName)
            }
            reply(answer.toString())
        }
    }

    private fun error(id: Any, code: Int, message: String) = JSONObject().put("jsonrpc", "2.0").put("id", id)
        .put("error", JSONObject().put("code", code).put("message", message))

    companion object {
        const val NAME = "skiffBridge"
        const val ORIGIN = "https://appassets.androidplatform.net"

        /** The page's first message, sent by `bridge.ts` as it loads. */
        const val READY = "ready"

        const val METHOD_NOT_FOUND = -32601
        const val INVALID_PARAMS = -32602
        const val SERVER_ERROR = -32000

        private const val TAG = "SkiffCode"
    }
}
