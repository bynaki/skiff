package com.naki.skiff.code.doc

import com.naki.skiff.fs.local.LocalFileSystem
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URL

class ImageLoaderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val fs = LocalFileSystem("local")
    private val loader = ImageLoader(sizeLimit = 1024)

    private val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)
    private val svg = "<?xml version=\"1.0\"?>\n<svg xmlns=\"http://www.w3.org/2000/svg\"/>".encodeToByteArray()

    private fun file(name: String, bytes: ByteArray): String = tmp.newFile(name).apply { writeBytes(bytes) }.absolutePath

    private fun sniff(head: ByteArray, name: String = "x", type: String? = null) = ImageLoader.sniff(head, name, type)

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `each raster format is told by its first bytes, whatever the name says`() {
        assertEquals("image/png", sniff(png, "a.txt"))
        assertEquals("image/jpeg", sniff(bytes(0xFF, 0xD8, 0xFF, 0xE0)))
        assertEquals("image/gif", sniff("GIF89a..".encodeToByteArray()))
        assertEquals("image/webp", sniff("RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".encodeToByteArray()))
        assertEquals("image/bmp", sniff("BM......".encodeToByteArray()))
        assertEquals("image/x-icon", sniff(bytes(0, 0, 1, 0, 1, 0)))
        assertNull(sniff("RIFF\u0000\u0000\u0000\u0000WAVEfmt ".encodeToByteArray()))
    }

    @Test
    fun `svg needs both the name or the type and text that starts as xml`() {
        assertEquals("image/svg+xml", sniff(svg, "logo.SVG"))
        assertEquals("image/svg+xml", sniff("\uFEFF  <svg/>".encodeToByteArray(), "badge", "image/svg+xml"))
        assertEquals("image/svg+xml", sniff("<!-- made by hand -->\n<svg/>".encodeToByteArray(), "a.svg"))
        assertNull(sniff(svg, "logo.xml"))
        assertNull(sniff("not xml".encodeToByteArray(), "a.svg"))
    }

    @Test
    fun `what is not an image is not one, whatever it is called`() {
        assertNull(sniff("[server]\nport = 22\n".encodeToByteArray(), "config.png"))
        assertNull(sniff(ByteArray(0), "a.png"))
        assertNull(sniff("# title\n".encodeToByteArray(), "a.png"))
    }

    @Test
    fun `an image file is read with its type`() = runBlocking {
        val image = loader.load(fs, file("a.png", png))!!
        assertEquals("image/png", image.mime)
        assertArrayEquals(png, image.bytes)
        assertEquals("image/svg+xml", loader.load(fs, file("b.svg", svg))!!.mime)
    }

    @Test
    fun `a missing file, a folder, a file over the limit or one that is not an image is null`() = runBlocking {
        assertNull(loader.load(fs, tmp.root.absolutePath + "/missing.png"))
        assertNull(loader.load(fs, tmp.newFolder("dir.png").absolutePath))
        assertNull(loader.load(fs, file("big.png", png + ByteArray(1024))))
        assertNull(loader.load(fs, file("config.png", "[server]\nport = 22\n".encodeToByteArray())))
        // Right at the limit is still in.
        assertEquals("image/png", loader.load(fs, file("edge.png", png + ByteArray(1024 - png.size)))?.mime)
    }

    private val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply { start() }

    @After
    fun stop() = server.stop(0)

    private var cookie: String? = "unset"
    private var referer: String? = "unset"

    private fun serve(path: String, body: ByteArray, type: String? = "image/png", status: Int = 200, chunked: Boolean = false) {
        server.createContext(path) { exchange ->
            cookie = exchange.requestHeaders.getFirst("Cookie")
            referer = exchange.requestHeaders.getFirst("Referer")
            type?.let { exchange.responseHeaders.add("Content-Type", it) }
            exchange.sendResponseHeaders(status, if (chunked) 0 else body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
    }

    private fun web(path: String) = runBlocking { loader.load(URL("http://127.0.0.1:${server.address.port}$path")) }

    @Test
    fun `a web image is read with nothing but its address`() {
        serve("/a.png", png)
        assertArrayEquals(png, web("/a.png")!!.bytes)
        assertNull(cookie)
        assertNull(referer)
    }

    @Test
    fun `a badge served as svg with no extension is an svg`() {
        serve("/badge/build-passing-green", svg, type = "image/svg+xml;charset=utf-8")
        assertEquals("image/svg+xml", web("/badge/build-passing-green")!!.mime)
    }

    @Test
    fun `a web page, an error, a body over the limit or no answer is null`() {
        serve("/blob/a.png", "<html><svg/></html>".encodeToByteArray(), type = "text/html; charset=utf-8")
        serve("/gone.png", png, status = 404)
        serve("/told.png", png + ByteArray(2048))
        serve("/streamed.png", png + ByteArray(2048), chunked = true)
        serve("/text.png", "hello".encodeToByteArray(), type = "image/png")
        assertNull(web("/blob/a.png"))
        assertNull(web("/gone.png"))
        assertNull(web("/told.png"))
        assertNull(web("/streamed.png"))
        assertNull(web("/text.png"))
        assertNull(runBlocking { loader.load(URL("http://127.0.0.1:1/a.png")) })
    }
}
