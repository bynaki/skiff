package com.naki.skiff.code.doc

import com.naki.skiff.fs.FileSystem
import com.naki.skiff.fs.FsError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.Source
import okio.buffer
import okio.source
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** An image ready to hand to the page, as the type its first bytes say it is. */
class Image(val mime: String, val bytes: ByteArray)

/**
 * Reads an image in rendered markdown for the page (docs/skiffcode.spec/markdown-images.md), from
 * the document's own filesystem or from the web. The page can name any path beside the document, so
 * only what really is an image comes back: anything else — a key, a config file — is null before
 * more of it is read than the probe, and so is anything over [sizeLimit], missing, or unreachable.
 *
 * The web is read as [WebLoader] reads a markdown file: the address and nothing else, no cookies,
 * no credentials, no `Referer`, redirects only within the scheme. Whether the address is https is
 * the caller's to check ([com.naki.skiff.code.intent.MarkdownLink.resolveImage]), so a test can
 * serve one over plain http.
 */
class ImageLoader(
    val sizeLimit: Long = DEFAULT_SIZE_LIMIT,
    private val connectTimeoutMillis: Int = 15_000,
    private val readTimeoutMillis: Int = 30_000,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    suspend fun load(fs: FileSystem, path: String): Image? = try {
        val node = fs.stat(path)
        if (node == null || node.navigable || node.size > sizeLimit) null
        else withContext(dispatcher) { fs.openRead(path).use { read(it, path, null) } }
    } catch (e: FsError) {
        null
    } catch (e: IOException) {
        null
    }

    suspend fun load(url: URL): Image? = withContext(dispatcher) {
        try {
            fetch(url)
        } catch (e: IOException) {
            null
        }
    }

    private fun fetch(url: URL): Image? {
        val connection = url.openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.useCaches = false
            connection.setRequestProperty("Accept", "image/*")
            if (connection.responseCode !in 200..299) return null
            val type = connection.contentType?.substringBefore(';')?.trim()?.lowercase()
            // A page around the image — GitHub's own file page, a login wall — is not one.
            if (type == "text/html") return null
            if (connection.contentLengthLong > sizeLimit) return null
            connection.inputStream.source().use { read(it, url.path, type) }
        } finally {
            connection.disconnect()
        }
    }

    /** The image [source] holds, or null when it is not one or goes past the limit. */
    private fun read(source: Source, name: String, contentType: String?): Image? {
        val buffered = source.buffer()
        // Decided on the probe alone, so that a file that is not an image is never read through.
        buffered.request(PROBE)
        val head = buffered.peek().readByteArray(minOf(buffered.buffer.size, PROBE))
        val mime = sniff(head, name, contentType) ?: return null
        val sink = Buffer()
        while (sink.size <= sizeLimit && buffered.read(sink, sizeLimit + 1 - sink.size) != -1L) Unit
        return if (sink.size > sizeLimit) null else Image(mime, sink.readByteArray())
    }

    companion object {
        const val DEFAULT_SIZE_LIMIT = 10L * 1024 * 1024
        private const val PROBE = 1024L

        /**
         * The type [head], the first bytes of a file called [name], is an image of, or null for
         * none. Raster formats by their magic numbers; SVG, which has none, when the name or the
         * server's [contentType] says SVG and the text starts as XML does.
         */
        fun sniff(head: ByteArray, name: String, contentType: String?): String? {
            fun starts(vararg bytes: Int, at: Int = 0) =
                head.size >= at + bytes.size && bytes.indices.all { head[at + it] == bytes[it].toByte() }
            return when {
                starts(0x89, 'P'.code, 'N'.code, 'G'.code, 0x0D, 0x0A, 0x1A, 0x0A) -> "image/png"
                starts(0xFF, 0xD8, 0xFF) -> "image/jpeg"
                starts('G'.code, 'I'.code, 'F'.code, '8'.code) -> "image/gif"
                starts('R'.code, 'I'.code, 'F'.code, 'F'.code) && starts('W'.code, 'E'.code, 'B'.code, 'P'.code, at = 8) -> "image/webp"
                starts('B'.code, 'M'.code) -> "image/bmp"
                starts(0x00, 0x00, 0x01, 0x00) -> "image/x-icon"
                isSvg(head, name, contentType) -> "image/svg+xml"
                else -> null
            }
        }

        private fun isSvg(head: ByteArray, name: String, contentType: String?): Boolean {
            if (contentType != "image/svg+xml" && !name.endsWith(".svg", ignoreCase = true)) return false
            val text = head.decodeToString().removePrefix("\uFEFF").trimStart()
            return text.startsWith("<?xml") || text.startsWith("<svg") || text.startsWith("<!--") ||
                text.startsWith("<!DOCTYPE svg", ignoreCase = true)
        }
    }
}
