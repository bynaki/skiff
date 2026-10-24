package com.naki.skiff.code.settings

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URL
import java.nio.file.Files
import java.security.MessageDigest

/** Download Theme...: the repository's list and a theme of it, over a fake [Fetch]. */
class ThemeCatalogTest {

    private val dir: File = Files.createTempDirectory("themes").toFile().resolve("themes")

    private val themes = Themes({ path -> File("src/main/assets", path).inputStream() }, dir)

    private val light = File("src/main/assets/themes/light.toml").readBytes()
    private val dark = File("src/main/assets/themes/dark.toml").readBytes()

    /** What the fake repository serves, by path under the base, and what was asked for, with its limit. */
    private val served = mutableMapOf<String, ByteArray>()
    private val asked = mutableListOf<Pair<URL, Int>>()

    private val fetch = Fetch { url, limit ->
        asked += url to limit
        val path = url.toString().removePrefix(ThemeCatalog.BASE.toString())
        served[path] ?: throw CatalogError.Status(url, 404)
    }

    private val catalog = ThemeCatalog(themes, fetch)

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private val swatch = """["#1e1e2e","#cdd6f4","#cba6f7","#a6e3a1","#89b4fa","#cba6f7"]"""

    /** An `index.json` line for [id], describing [file] unless told otherwise. */
    private fun row(
        id: String,
        title: String = id.replaceFirstChar { it.uppercase() },
        base: String = "light",
        file: ByteArray = light,
        size: String = file.size.toString(),
        sha256: String = "\"${sha256(file)}\"",
        swatch: String = this.swatch,
    ) = """{"id":"$id","title":"$title","family":"Test","base":"$base","swatch":$swatch,"size":$size,"sha256":$sha256}"""

    private fun index(vararg rows: String, format: String = "1") {
        served["index.json"] = """{ "format": $format, "themes": [${rows.joinToString(",\n")}] }""".encodeToByteArray()
    }

    /** Serves [file] as the theme [id] and lists it, the way the repository does. */
    private fun publish(id: String, file: ByteArray = light, base: String = "light") {
        served["themes/$id.toml"] = file
        index(row(id, base = base, file = file))
    }

    private fun list() = runBlocking { catalog.list() }

    private fun install(id: String, replace: (String) -> Boolean = { fail("asked to replace $it"); false }) =
        runBlocking { catalog.install(id) { replace(it) } }

    private inline fun <reified E : CatalogError> refused(block: () -> Unit): E {
        try {
            block()
        } catch (e: CatalogError) {
            if (e is E) return e
            fail("expected ${E::class.simpleName}, got $e")
        }
        fail("expected ${E::class.simpleName}, got nothing")
        throw AssertionError()
    }

    @Test
    fun `the list is read from the one https address, by the name each theme shows`() {
        index(row("zenith", title = "Zenith"), row("aurora", title = "aurora dark", base = "dark", file = dark))
        val listed = list()
        assertEquals(listOf("aurora", "zenith"), listed.map { it.id })
        assertEquals(CatalogTheme("aurora", "aurora dark", "Test", true, Json.swatch(swatch), dark.size.toLong(), sha256(dark)), listed[0])
        assertEquals(listOf(URL("https://raw.githubusercontent.com/bynaki/skiffcode-themes/main/index.json") to 256 * 1024), asked)
    }

    @Test
    fun `an address that is not https is refused`() {
        assertThrows(IllegalArgumentException::class.java) { ThemeCatalog(themes, fetch, URL("http://example.com/themes/")) }
    }

    @Test
    fun `a line that breaks any rule is left out, and the rest are listed`() {
        index(
            row("good"),
            row("Upper"),
            row("9lives"),
            row("has_underscore"),
            row("a".repeat(65)),
            row("light"),
            row("system"),
            row("sepia", base = "sepia"),
            row("five", swatch = """["#000000","#111111","#222222","#333333","#444444"]"""),
            row("named", swatch = """["red","#111111","#222222","#333333","#444444","#555555"]"""),
            row("numbers", swatch = """[1,2,3,4,5,6]"""),
            row("textsize", size = "\"2843\""),
            row("nosize", size = "0"),
            row("huge", size = "${64 * 1024 + 1}"),
            row("fraction", size = "28.5"),
            row("shorthash", sha256 = "\"abc\""),
            row("upperhash", sha256 = "\"${sha256(light).uppercase()}\""),
            row("notitle", title = " "),
            row("good", title = "Second Good"),
            "\"just a string\"",
            """{"id":"bare"}""",
        )
        val listed = list()
        assertEquals(listOf("good"), listed.map { it.id })
        assertEquals("Good", listed.single().title)
    }

    @Test
    fun `a list for a newer app is refused, saying which format it is`() {
        index(row("good"), format = "2")
        assertEquals(2, refused<CatalogError.TooNew> { list() }.format)
    }

    @Test
    fun `a list that does not read is refused whole`() {
        for (format in listOf("\"1\"", "1.5", "0", "null")) {
            index(row("good"), format = format)
            refused<CatalogError.Malformed> { list() }
        }
        for (text in listOf("not json", "[]", """{ "format": 1 }""", """{ "themes": [] }""", """{ "format": 1, "themes": {} }""")) {
            served["index.json"] = text.encodeToByteArray()
            refused<CatalogError.Malformed> { list() }
        }
        served["index.json"] = byteArrayOf(0x7b, 0xff.toByte(), 0x7d)
        refused<CatalogError.Malformed> { list() }
    }

    @Test
    fun `a downloaded theme is kept as one of the person's own, as it was written`() {
        publish("aurora")
        list()
        assertTrue(install("aurora"))
        assertTrue(themes.isImported("aurora"))
        assertTrue("aurora" in themes.names)
        assertArrayEquals(light, File(dir, "aurora.toml").readBytes())
        assertEquals(URL(ThemeCatalog.BASE, "themes/aurora.toml") to 64 * 1024, asked.last())
    }

    @Test
    fun `only a theme of the last list can be downloaded`() {
        publish("aurora")
        assertThrows(IllegalArgumentException::class.java) { install("aurora") }
        list()
        assertThrows(IllegalArgumentException::class.java) { install("other") }
    }

    @Test
    fun `a file of another length or hash than the list says is not kept`() {
        index(
            row("longer", size = "${light.size - 1}"),
            row("other", sha256 = "\"${sha256(dark)}\""),
            row("darker", base = "dark", file = light),
        )
        for (id in listOf("longer", "other", "darker")) served["themes/$id.toml"] = light
        list()
        for (id in listOf("longer", "other", "darker")) assertEquals(id, refused<CatalogError.Mismatch> { install(id) }.id)
        assertFalse(dir.exists() && dir.list()!!.isNotEmpty())
    }

    @Test
    fun `a theme the parser finds anything wrong with is not kept`() {
        val files = mapOf(
            "unknown" to light.decodeToString().replaceFirst("[ui]\n", "[ui]\nglow = \"#ffffff\"\n").encodeToByteArray(),
            "named" to light.decodeToString().replaceFirst(Regex("background = \"#[0-9a-fA-F]+\""), "background = \"white\"").encodeToByteArray(),
            "broken" to "this is = = not toml".encodeToByteArray(),
            "latin" to byteArrayOf(0x62, 0x61, 0x73, 0x65, 0x20, 0x3d, 0x20, 0x22, 0xe9.toByte(), 0x22),
        )
        index(*files.map { (id, file) -> row(id, file = file) }.toTypedArray())
        files.forEach { (id, file) -> served["themes/$id.toml"] = file }
        list()
        for (id in files.keys) {
            val e = refused<CatalogError.Invalid> { install(id) }
            assertEquals(id, e.id)
            assertTrue(id, e.problems.isNotEmpty())
        }
        assertFalse(dir.exists() && dir.list()!!.isNotEmpty())
    }

    @Test
    fun `a name already theirs is asked about, and a no keeps their file`() {
        val mine = "# mine\n".encodeToByteArray() + light
        themes.import("aurora", mine.decodeToString())
        publish("aurora")
        list()
        var asked: String? = null
        assertFalse(install("aurora") { asked = it; false })
        assertEquals("aurora", asked)
        assertArrayEquals(mine, File(dir, "aurora.toml").readBytes())

        assertTrue(install("aurora") { true })
        assertArrayEquals(light, File(dir, "aurora.toml").readBytes())
    }

    @Test
    fun `a name theirs in another case is the one asked about and replaced`() {
        themes.import("Aurora", "# mine\n" + light.decodeToString())
        publish("aurora")
        list()
        var asked: String? = null
        assertTrue(install("aurora") { asked = it; true })
        assertEquals("Aurora", asked)
        assertEquals(listOf("aurora"), themes.names.filter { it.equals("aurora", ignoreCase = true) })
        assertEquals("aurora", themes.ownNamed("AURORA"))
        assertArrayEquals(light, File(dir, "aurora.toml").readBytes())
    }

    @Test
    fun `the person's own themes are not touched by a theme that is not theirs`() {
        publish("aurora")
        list()
        assertNull(themes.ownNamed("aurora"))
        assertTrue(install("aurora"))
        assertEquals("aurora", themes.ownNamed("aurora"))
    }

    private object Json {
        fun swatch(text: String) = Regex("#[0-9a-f]{6}").findAll(text).map { it.value }.toList()
    }
}

/** [HttpFetch] against a server on this machine. */
class HttpFetchTest {

    private val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply { start() }

    @After
    fun stop() = server.stop(0)

    private fun serve(path: String, body: ByteArray, status: Int = 200, chunked: Boolean = false) {
        server.createContext(path) { exchange ->
            exchange.sendResponseHeaders(status, if (chunked) 0 else body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
    }

    private fun url(path: String) = URL("http://127.0.0.1:${server.address.port}$path")

    private fun get(path: String, limit: Int = 1024, readTimeoutMillis: Int = 30_000) =
        runBlocking { HttpFetch(readTimeoutMillis = readTimeoutMillis).get(url(path), limit) }

    private inline fun <reified E : CatalogError> refused(block: () -> Unit): E {
        try {
            block()
        } catch (e: CatalogError) {
            if (e is E) return e
            fail("expected ${E::class.simpleName}, got $e")
        }
        fail("expected ${E::class.simpleName}, got nothing")
        throw AssertionError()
    }

    @Test
    fun `a file is read whole, up to the limit`() {
        val body = ByteArray(1024) { it.toByte() }
        serve("/a", body)
        assertArrayEquals(body, get("/a"))
    }

    @Test
    fun `a file over the limit is refused, whether the length came first or not`() {
        val big = ByteArray(1025)
        serve("/told", big)
        serve("/streamed", big, chunked = true)
        assertEquals(1024, refused<CatalogError.TooLarge> { get("/told") }.limit)
        refused<CatalogError.TooLarge> { get("/streamed") }
    }

    @Test
    fun `an answer other than success says which`() {
        serve("/gone", "missing".encodeToByteArray(), status = 404)
        assertEquals(404, refused<CatalogError.Status> { get("/gone") }.status)
    }

    @Test
    fun `nothing listening, or no answer in time, is unreachable`() {
        val port = ServerSocket(0, 0, InetAddress.getLoopbackAddress()).use { it.localPort }
        refused<CatalogError.Unreachable> { runBlocking { HttpFetch().get(URL("http://127.0.0.1:$port/a"), 1024) } }

        server.createContext("/slow") { exchange ->
            Thread.sleep(1_000)
            exchange.close()
        }
        refused<CatalogError.Unreachable> { get("/slow", readTimeoutMillis = 200) }
    }
}
