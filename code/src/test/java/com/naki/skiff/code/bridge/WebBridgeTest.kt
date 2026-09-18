package com.naki.skiff.code.bridge

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebBridgeTest {

    private val warnings = ArrayList<String>()
    private val sent = ArrayList<JSONObject>()
    private val toPage: (String) -> Unit = { sent += JSONObject(it) }

    private fun TestScope.bridge() = WebBridge(this) { message, _ -> warnings += message }

    private fun call(id: Any, method: String, params: String = "{}") =
        """{"jsonrpc":"2.0","id":${if (id is String) JSONObject.quote(id) else id},"method":"$method","params":$params}"""

    @Test
    fun `a call is answered with its id and the handler's result`() = runTest {
        val bridge = bridge()
        bridge.method("add") { JSONObject().put("sum", it.getInt("a") + it.getInt("b")) }

        bridge.receive(call(7, "add", """{"a":2,"b":3}"""), toPage)
        advanceUntilIdle()

        assertEquals(7, sent.single().getInt("id"))
        assertEquals(5, sent.single().getJSONObject("result").getInt("sum"))
    }

    @Test
    fun `a string id comes back as the same string`() = runTest {
        val bridge = bridge()
        bridge.method("ping") { "pong" }

        bridge.receive(call("a1", "ping"), toPage)
        advanceUntilIdle()

        assertEquals("a1", sent.single().getString("id"))
        assertEquals("pong", sent.single().getString("result"))
    }

    @Test
    fun `a handler that returns nothing answers null rather than leaving the call hanging`() = runTest {
        val bridge = bridge()
        bridge.method("nothing") { null }

        bridge.receive(call(1, "nothing"), toPage)
        advanceUntilIdle()

        assertTrue(sent.single().has("result"))
        assertTrue(sent.single().isNull("result"))
    }

    @Test
    fun `an unknown method is a method-not-found error`() = runTest {
        bridge().receive(call(1, "nope"), toPage)

        assertEquals(WebBridge.METHOD_NOT_FOUND, sent.single().getJSONObject("error").getInt("code"))
    }

    @Test
    fun `a missing param is an invalid-params error`() = runTest {
        val bridge = bridge()
        bridge.method("needs") { it.getString("path") }

        bridge.receive(call(1, "needs"), toPage)
        advanceUntilIdle()

        assertEquals(WebBridge.INVALID_PARAMS, sent.single().getJSONObject("error").getInt("code"))
    }

    @Test
    fun `a failing handler answers with an error carrying its message`() = runTest {
        val bridge = bridge()
        bridge.method("fails") { error("disk on fire") }

        bridge.receive(call(1, "fails"), toPage)
        advanceUntilIdle()

        val error = sent.single().getJSONObject("error")
        assertEquals(WebBridge.SERVER_ERROR, error.getInt("code"))
        assertEquals("disk on fire", error.getString("message"))
    }

    @Test
    fun `calls answer in the order their handlers finish, not the order they came`() = runTest {
        val bridge = bridge()
        val slow = CompletableDeferred<Unit>()
        bridge.method("slow") { slow.await(); "slow" }
        bridge.method("fast") { "fast" }

        bridge.receive(call(1, "slow"), toPage)
        bridge.receive(call(2, "fast"), toPage)
        advanceUntilIdle()
        slow.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(2, 1), sent.map { it.getInt("id") })
    }

    @Test
    fun `a notification from the page reaches its handler and gets no answer`() = runTest {
        val bridge = bridge()
        var got: String? = null
        bridge.onNotify("lspSend") { got = it.getString("message") }

        bridge.receive("""{"jsonrpc":"2.0","method":"lspSend","params":{"message":"{}"}}""", toPage)
        advanceUntilIdle()

        assertEquals("{}", got)
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `notifications sent before the page is ready are held and delivered in order`() = runTest {
        val bridge = bridge()
        bridge.notify("first", JSONObject().put("n", 1))
        bridge.notify("second", JSONObject().put("n", 2))
        advanceUntilIdle()
        assertTrue(sent.isEmpty())

        bridge.receive("""{"jsonrpc":"2.0","method":"ready"}""", toPage)
        bridge.notify("third", JSONObject().put("n", 3))
        advanceUntilIdle()

        assertEquals(listOf("first", "second", "third"), sent.map { it.getString("method") })
        assertFalse(sent.any { it.has("id") })
    }

    @Test
    fun `a reloaded page takes over notifications`() = runTest {
        val bridge = bridge()
        val old = ArrayList<String>()
        bridge.receive("""{"method":"ready"}""") { old += it }
        bridge.receive("""{"method":"ready"}""", toPage)

        bridge.notify("hello", JSONObject())
        advanceUntilIdle()

        assertTrue(old.isEmpty())
        assertEquals("hello", sent.single().getString("method"))
    }

    @Test
    fun `a message that is not JSON is dropped and logged`() = runTest {
        bridge().receive("not json", toPage)

        assertTrue(sent.isEmpty())
        assertEquals(1, warnings.size)
    }

    @Test
    fun `lsp text with slashes survives the round trip byte for byte`() = runTest {
        val bridge = bridge()
        val lsp = """{"method":"textDocument/didOpen","params":{"text":"a/b \"한글\""}}"""
        bridge.receive("""{"method":"ready"}""", toPage)

        bridge.notify("lspMessage", JSONObject().put("message", lsp))
        advanceUntilIdle()

        assertEquals(lsp, sent.single().getJSONObject("params").getString("message"))
    }
}
