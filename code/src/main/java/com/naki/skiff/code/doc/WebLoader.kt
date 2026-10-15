package com.naki.skiff.code.doc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.source
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/**
 * Reads a markdown file from the web, for a link tapped in the viewer
 * (docs/skiffcode.spec/markdown-links.md). The page itself never reaches the network — its CSP and
 * the asset loader keep it off — so the reading happens here, with the same limit and the same
 * strict decoding as a file on a server.
 *
 * Nothing goes with the request but the address: no cookies (nothing sets a `CookieHandler` in this
 * process) and no credentials. Redirects are followed only within the scheme, which is what
 * `HttpURLConnection` does. Whether the address is https is the caller's to check, so a test can
 * serve the file over plain http.
 */
class WebLoader(
    private val loader: TextLoader,
    private val connectTimeoutMillis: Int = 15_000,
    private val readTimeoutMillis: Int = 30_000,
) {

    suspend fun load(url: URL): LoadResult = withContext(Dispatchers.IO) {
        try {
            read(url)
        } catch (e: UnknownHostException) {
            throw WebError.Unreachable(url, e)
        } catch (e: ConnectException) {
            throw WebError.Unreachable(url, e)
        } catch (e: SocketTimeoutException) {
            throw WebError.Unreachable(url, e)
        }
    }

    private suspend fun read(url: URL): LoadResult {
        val connection = url.openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.useCaches = false
            connection.setRequestProperty("Accept", "text/markdown, text/plain;q=0.9, */*;q=0.5")
            val status = connection.responseCode
            if (status !in 200..299) throw WebError.Status(url, status)
            // A page around the markdown, not the markdown: GitHub's own file page, a login wall.
            // Shown, it would be a screen of tags; rendered, nothing a viewer should run.
            val type = connection.contentType?.substringBefore(';')?.trim()
            if (type.equals("text/html", ignoreCase = true)) throw WebError.NotMarkdown(url)
            val length = connection.contentLengthLong
            if (length > loader.sizeLimit) LoadResult.TooLarge(length, loader.sizeLimit)
            else connection.inputStream.source().use { loader.load(it) }
        } finally {
            connection.disconnect()
        }
    }
}

/** Why a markdown file on the web was not read, beyond what an [IOException] already says. */
sealed class WebError(val url: URL, message: String, cause: Throwable? = null) : IOException(message, cause) {
    class Status(url: URL, val status: Int) : WebError(url, "HTTP $status from $url")

    class NotMarkdown(url: URL) : WebError(url, "$url is a web page, not a markdown file")

    /** No answer: the name did not resolve, nothing listened, or it took too long. */
    class Unreachable(url: URL, cause: Throwable) : WebError(url, "${url.host} did not answer", cause)
}

/**
 * A web document for [com.naki.skiff.code.ui.OpenDocuments]: never watched — there is no stamp to
 * poll, and polling a stranger's server on a clock is not ours to do — but readable again.
 */
class WatchedWeb(private val url: URL, private val web: WebLoader) : WatchedFile {
    override suspend fun stamp(): Stamped = Stamped.Unknown

    override suspend fun read(): LoadResult = web.load(url)
}
