package com.naki.skiff.code.doc

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URL

class WebLoaderTest {

    private val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply { start() }

    @After
    fun stop() = server.stop(0)

    /** Serves [body] at [path] with [type], or with no length when [chunked]: what a server streaming it sends. */
    private fun serve(path: String, body: ByteArray, type: String? = "text/markdown; charset=utf-8", status: Int = 200, chunked: Boolean = false) {
        server.createContext(path) { exchange ->
            type?.let { exchange.responseHeaders.add("Content-Type", it) }
            exchange.sendResponseHeaders(status, if (chunked) 0 else body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
    }

    private fun url(path: String) = URL("http://127.0.0.1:${server.address.port}$path")

    private fun load(path: String, limit: Long = 1024, readTimeoutMillis: Int = 30_000) =
        runBlocking { WebLoader(TextLoader(sizeLimit = limit), readTimeoutMillis = readTimeoutMillis).load(url(path)) }

    private inline fun <reified E : WebError> refused(block: () -> Unit): E {
        try {
            block()
        } catch (e: WebError) {
            if (e is E) return e
            fail("expected ${E::class.simpleName}, got $e")
        }
        fail("expected ${E::class.simpleName}, got nothing")
        throw AssertionError()
    }

    @Test
    fun `a markdown file is read as text, hangul and all`() {
        serve("/README.md", "# 설치 방법\n\n본문\n".encodeToByteArray())
        val result = load("/README.md") as LoadResult.Text
        assertEquals("# 설치 방법\n\n본문\n", result.text)
    }

    @Test
    fun `a file served as plain text or with no type is read too`() {
        serve("/a.md", "a\n".encodeToByteArray(), type = "text/plain")
        serve("/b.md", "b\n".encodeToByteArray(), type = null)
        assertEquals("a\n", (load("/a.md") as LoadResult.Text).text)
        assertEquals("b\n", (load("/b.md") as LoadResult.Text).text)
    }

    @Test
    fun `a web page is refused, not shown as its tags`() {
        serve("/blob/README.md", "<html><body>page</body></html>".encodeToByteArray(), type = "Text/HTML; charset=utf-8")
        val e = refused<WebError.NotMarkdown> { load("/blob/README.md") }
        assertEquals(url("/blob/README.md"), e.url)
    }

    @Test
    fun `a file over the limit is too large, whether the length came first or not`() {
        val big = ByteArray(2048) { 'a'.code.toByte() }
        serve("/told.md", big)
        serve("/streamed.md", big, chunked = true)
        assertTrue(load("/told.md") is LoadResult.TooLarge)
        assertTrue(load("/streamed.md") is LoadResult.TooLarge)
    }

    @Test
    fun `binary is binary`() {
        serve("/x.md", byteArrayOf(0x50, 0x4b, 0, 0))
        assertEquals(LoadResult.Binary, load("/x.md"))
    }

    @Test
    fun `an answer other than success says which`() {
        serve("/gone.md", "missing".encodeToByteArray(), type = "text/plain", status = 404)
        serve("/locked.md", "no".encodeToByteArray(), type = "text/plain", status = 403)
        assertEquals(404, refused<WebError.Status> { load("/gone.md") }.status)
        assertEquals(403, refused<WebError.Status> { load("/locked.md") }.status)
    }

    @Test
    fun `nothing listening, or no answer in time, is unreachable`() {
        val port = ServerSocket(0, 0, InetAddress.getLoopbackAddress()).use { it.localPort }
        val closed = URL("http://127.0.0.1:$port/a.md")
        try {
            runBlocking { WebLoader(TextLoader()).load(closed) }
            fail("expected unreachable")
        } catch (e: WebError.Unreachable) {
            assertEquals(closed, e.url)
        }

        server.createContext("/slow.md") { exchange ->
            Thread.sleep(1_000)
            exchange.close()
        }
        refused<WebError.Unreachable> { load("/slow.md", readTimeoutMillis = 200) }
    }
}
