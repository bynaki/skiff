package com.naki.skiff.code.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import okio.buffer
import okio.source
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.nio.charset.CharacterCodingException
import java.security.MessageDigest

/**
 * One line of the theme repository's `index.json`: what the palette's Download Theme... draws before
 * anything is downloaded, and what the file has to be when it is (docs/skiffcode.spec/community-themes.md).
 * [swatch] is [Themes.SWATCH]'s colors, in that order.
 */
data class CatalogTheme(
    val id: String,
    val title: String,
    val family: String,
    val dark: Boolean,
    val swatch: List<String>,
    val size: Long,
    val sha256: String,
)

/** Reads [url] whole, refusing anything longer than [limit] bytes. The test's is a fake. */
fun interface Fetch {
    suspend fun get(url: URL, limit: Int): ByteArray
}

/**
 * The community themes, from the one public repository the app knows (docs/skiffcode.spec/community-themes.md):
 * its list, and a theme of it kept as one of the person's own, as Import Theme keeps a file. The
 * page only asks; reading and checking what comes back is here. A line of the list that breaks a rule
 * is left out of it, and a theme that is not exactly the file the list describes — or that
 * [ThemeToml] finds anything wrong with — is not kept.
 */
class ThemeCatalog(
    private val themes: Themes,
    private val fetch: Fetch = HttpFetch(),
    private val base: URL = BASE,
) {

    init {
        require(base.protocol == "https") { "the theme repository is read over https only: $base" }
    }

    /** What [list] gave last, by id: what a download is checked against, so the page names only an id. */
    @Volatile
    private var listed: Map<String, CatalogTheme> = emptyMap()

    /** The repository's themes, by the name they show. Read again each time: the list is the repository's to change. */
    suspend fun list(): List<CatalogTheme> {
        val text = decode(fetch.get(URL(base, "index.json"), INDEX_LIMIT)) ?: throw CatalogError.Malformed("not UTF-8")
        val root = try {
            Json.parseToJsonElement(text)
        } catch (e: SerializationException) {
            throw CatalogError.Malformed(e.message ?: "not JSON")
        }
        val obj = root as? JsonObject ?: throw CatalogError.Malformed("not a JSON object")
        val format = (obj["format"] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull?.takeIf { it >= 1 }
            ?: throw CatalogError.Malformed("no format")
        if (format > FORMAT) throw CatalogError.TooNew(format)
        val rows = obj["themes"] as? JsonArray ?: throw CatalogError.Malformed("no themes")
        val entries = rows.mapNotNull(::entryOf).distinctBy { it.id }.sortedBy { it.title.lowercase() }
        listed = entries.associateBy { it.id }
        return entries
    }

    /** [row] as a theme of the list, or null when any of it breaks a rule. */
    private fun entryOf(row: JsonElement): CatalogTheme? {
        val obj = row as? JsonObject ?: return null
        val id = obj.text("id")?.takeIf { ID.matches(it) && themes.nameOf(it) == it } ?: return null
        val title = obj.text("title")?.takeIf { it.isNotBlank() } ?: return null
        val family = obj.text("family") ?: return null
        val dark = when (obj.text("base")) {
            "light" -> false
            "dark" -> true
            else -> return null
        }
        val swatch = (obj["swatch"] as? JsonArray)?.map { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
            ?.takeIf { colors -> colors.size == Themes.SWATCH.size && colors.all { it != null && ThemeToml.COLOR.matches(it) } }
            ?.filterNotNull() ?: return null
        val size = (obj["size"] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull?.takeIf { it in 1..THEME_LIMIT } ?: return null
        val sha256 = obj.text("sha256")?.takeIf { SHA256.matches(it) } ?: return null
        return CatalogTheme(id, title, family, dark, swatch, size, sha256)
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    /**
     * Downloads the theme [id] of the last [list] and keeps it as one of the person's own under that
     * name. When they already have one by that name, in any case, [replace] is asked first with its
     * name, and a no keeps everything as it was: then this says false.
     */
    suspend fun install(id: String, replace: suspend (existing: String) -> Boolean): Boolean {
        val entry = listed[id] ?: throw IllegalArgumentException("not in the theme list: $id")
        val text = download(entry)
        val existing = withContext(Dispatchers.IO) { themes.ownNamed(id) }
        if (existing != null && !replace(existing)) return false
        withContext(Dispatchers.IO) {
            if (existing != null && existing != id) themes.delete(existing)
            themes.import(id, text)
        }
        return true
    }

    /** [entry]'s file, once it is the one the list describes and reads as a theme with nothing wrong in it. */
    private suspend fun download(entry: CatalogTheme): String {
        val bytes = fetch.get(URL(base, "themes/${entry.id}${Themes.SUFFIX}"), THEME_LIMIT)
        if (bytes.size.toLong() != entry.size || hex(MessageDigest.getInstance("SHA-256").digest(bytes)) != entry.sha256) {
            throw CatalogError.Mismatch(entry.id)
        }
        val text = decode(bytes) ?: throw CatalogError.Invalid(entry.id, listOf(SettingsProblem.Unreadable("not UTF-8")))
        val read = ThemeToml.read(text) { emptyMap() }
        if (read.problems.isNotEmpty()) throw CatalogError.Invalid(entry.id, read.problems)
        if (read.theme.dark != entry.dark) throw CatalogError.Mismatch(entry.id)
        return text
    }

    private fun decode(bytes: ByteArray): String? = try {
        bytes.decodeToString(throwOnInvalidSequence = true)
    } catch (e: CharacterCodingException) {
        null
    }

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    companion object {
        val BASE = URL("https://raw.githubusercontent.com/bynaki/skiffcode-themes/main/")

        /**
         * The theme file format this app reads: the repository raises it when the theme keys change.
         * 2 added `ui.git_modified` and `ui.git_untracked` (M15).
         */
        const val FORMAT = 2

        const val INDEX_LIMIT = 256 * 1024
        const val THEME_LIMIT = 64 * 1024

        private val ID = Regex("[a-z][a-z0-9-]{0,63}")
        private val SHA256 = Regex("[0-9a-f]{64}")
    }
}

/** Why the theme list or a theme of it was not read or not kept. */
sealed class CatalogError(message: String, cause: Throwable? = null) : IOException(message, cause) {
    /** No answer: the name did not resolve, nothing listened, or it took too long. */
    class Unreachable(url: URL, cause: Throwable) : CatalogError("${url.host} did not answer", cause)

    class Status(url: URL, val status: Int) : CatalogError("HTTP $status from $url")

    class TooLarge(url: URL, val limit: Int) : CatalogError("$url is larger than $limit bytes")

    /** A list written for a newer app, whose theme files would name keys this one does not have. */
    class TooNew(val format: Int) : CatalogError("the theme list is format $format, newer than ${ThemeCatalog.FORMAT}")

    class Malformed(reason: String) : CatalogError("the theme list does not read: $reason")

    /** The file is not the one the list describes: another length, another hash, another base. */
    class Mismatch(val id: String) : CatalogError("$id is not the file the theme list describes")

    class Invalid(val id: String, val problems: List<SettingsProblem>) : CatalogError("$id is not a theme without problems: $problems")
}

/**
 * [Fetch] over `HttpURLConnection`, the way [com.naki.skiff.code.doc.WebLoader] reads a markdown file:
 * the address and nothing else, no caches, redirects only within the scheme, and the same time limits.
 */
class HttpFetch(
    private val connectTimeoutMillis: Int = 15_000,
    private val readTimeoutMillis: Int = 30_000,
) : Fetch {

    override suspend fun get(url: URL, limit: Int): ByteArray = withContext(Dispatchers.IO) {
        try {
            read(url, limit)
        } catch (e: UnknownHostException) {
            throw CatalogError.Unreachable(url, e)
        } catch (e: ConnectException) {
            throw CatalogError.Unreachable(url, e)
        } catch (e: SocketTimeoutException) {
            throw CatalogError.Unreachable(url, e)
        }
    }

    private fun read(url: URL, limit: Int): ByteArray {
        val connection = url.openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.useCaches = false
            val status = connection.responseCode
            if (status !in 200..299) throw CatalogError.Status(url, status)
            if (connection.contentLengthLong > limit) throw CatalogError.TooLarge(url, limit)
            connection.inputStream.source().buffer().use {
                if (it.request(limit + 1L)) throw CatalogError.TooLarge(url, limit)
                it.readByteArray()
            }
        } finally {
            connection.disconnect()
        }
    }
}
