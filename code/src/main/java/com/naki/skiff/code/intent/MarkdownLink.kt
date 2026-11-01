package com.naki.skiff.code.intent

import com.naki.skiff.fs.FsPath
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URISyntaxException

/**
 * What a link tapped in rendered markdown goes to (docs/skiffcode.spec/markdown-links.md). The page
 * sends the `href` as the document wrote it, and this resolves it against the document it was in —
 * never against anything the page says — so the server and the account stay that document's.
 */
sealed interface LinkTarget {
    /** A file beside the document, on the same server or device; [fragment] names a heading. */
    data class File(val path: String, val fragment: String?) : LinkTarget

    /** A markdown file on the web, opened read-only in the app. */
    data class Web(val url: String, val fragment: String?) : LinkTarget

    /** Handed to the default app, as the WebView did with every link before. */
    data class External(val uri: String) : LinkTarget

    data object Ignore : LinkTarget
}

/** Where an image in rendered markdown is read from (docs/skiffcode.spec/markdown-images.md). */
sealed interface ImageSource {
    /** A file on the document's server or device, read the way the document was. */
    data class File(val path: String) : ImageSource

    /** An https address, which Kotlin reads for the page. */
    data class Web(val url: String) : ImageSource

    /** Not read at all: the page shows the alt box. */
    data object None : ImageSource
}

object MarkdownLink {

    /** Where the document holding the link is. */
    sealed interface Base {
        /**
         * A file at [path]. [root] is the root of the project it is in, which a link starting with
         * `/` is taken from, as GitHub does; without one, such a link is an absolute path.
         */
        data class Path(val path: String, val root: String?) : Base

        /** A `content://` document: a grant for one file, with no folder for a relative link to be in. */
        data object NoFolder : Base

        /** A markdown file read from [url]. */
        data class Web(val url: String) : Base
    }

    /**
     * Resolves [href]. A bare `#fragment` is [LinkTarget.Ignore]: the page finds the heading in the
     * document it already has, and never sends one.
     */
    fun resolve(base: Base, href: String): LinkTarget {
        val link = href.trim()
        if (link.isEmpty() || link.startsWith('#')) return LinkTarget.Ignore
        if (SCHEME.containsMatchIn(link)) return absolute(link)
        return when (base) {
            is Base.Path -> if (link.startsWith("//")) LinkTarget.Ignore else file(base, link)
            Base.NoFolder -> LinkTarget.Ignore
            is Base.Web -> try {
                absolute(URI(base.url).resolve(link).toString())
            } catch (e: URISyntaxException) {
                LinkTarget.Ignore
            } catch (e: IllegalArgumentException) {
                LinkTarget.Ignore
            }
        }
    }

    /**
     * Resolves an image's [src] the way [resolve] does a link, against the same document and with
     * the same refusals. The difference is that an image is not told apart by its extension: any
     * https address is read, and nothing but https is.
     */
    fun resolveImage(base: Base, src: String): ImageSource {
        val link = src.trim()
        if (link.isEmpty() || link.startsWith('#')) return ImageSource.None
        if (SCHEME.containsMatchIn(link)) return webImage(link)
        return when (base) {
            is Base.Path -> {
                val path = if (link.startsWith("//")) null else filePath(base, link)
                path?.let(ImageSource::File) ?: ImageSource.None
            }
            Base.NoFolder -> ImageSource.None
            is Base.Web -> try {
                webImage(URI(base.url).resolve(link).toString())
            } catch (e: URISyntaxException) {
                ImageSource.None
            } catch (e: IllegalArgumentException) {
                ImageSource.None
            }
        }
    }

    private fun webImage(link: String): ImageSource {
        if (!link.substringBefore(':').equals("https", ignoreCase = true)) return ImageSource.None
        val uri = try {
            URI(link)
        } catch (e: URISyntaxException) {
            return ImageSource.None
        }
        if (uri.host.isNullOrEmpty()) return ImageSource.None
        return ImageSource.Web(github(uri) ?: link.substringBefore('#'))
    }

    private fun file(base: Base.Path, link: String): LinkTarget {
        val fragment = link.substringAfter('#', "").let { if (it.isEmpty()) null else percentDecode(it) }
        val path = filePath(base, link) ?: return LinkTarget.Ignore
        return LinkTarget.File(path, fragment)
    }

    /** The path [link] names from [base], without its query and fragment, or null when it names none. */
    private fun filePath(base: Base.Path, link: String): String? {
        val encoded = link.substringBefore('#').substringBefore('?')
        if (encoded.isEmpty()) return null
        val decoded = percentDecode(encoded) ?: return null
        val joined = if (decoded.startsWith(FsPath.SEPARATOR)) {
            (base.root ?: FsPath.ROOT).trimEnd(FsPath.SEPARATOR) + decoded
        } else {
            FsPath.parent(base.path) + FsPath.SEPARATOR + decoded
        }
        return withinRoot(joined)
    }

    /**
     * [path] with `.` and `..` folded, or null when a `..` would climb above `/` — which
     * [FsPath.normalize] would quietly clamp, opening some other file than the one written.
     */
    private fun withinRoot(path: String): String? {
        val stack = ArrayList<String>()
        for (segment in path.split(FsPath.SEPARATOR)) {
            when (segment) {
                "", "." -> Unit
                ".." -> if (stack.isEmpty()) return null else stack.removeAt(stack.lastIndex)
                else -> stack.add(segment)
            }
        }
        return FsPath.ROOT + stack.joinToString(FsPath.SEPARATOR.toString())
    }

    private fun absolute(link: String): LinkTarget {
        val scheme = link.substringBefore(':').lowercase()
        if (scheme == "mailto" || scheme == "http") return LinkTarget.External(link)
        if (scheme != "https") return LinkTarget.Ignore
        val uri = try {
            URI(link)
        } catch (e: URISyntaxException) {
            return LinkTarget.Ignore
        }
        val path = uri.rawPath ?: return LinkTarget.External(link)
        if (!path.endsWith(".md", ignoreCase = true)) return LinkTarget.External(link)
        val fragment = uri.rawFragment?.takeIf { it.isNotEmpty() }?.let(::percentDecode)
        return LinkTarget.Web(github(uri) ?: link.substringBefore('#'), fragment)
    }

    /**
     * The raw file behind a GitHub `blob` page, whose own address answers with the page around it
     * rather than the file. Null for any other address.
     */
    private fun github(uri: URI): String? {
        if (uri.host?.lowercase() !in GITHUB_HOSTS) return null
        val segments = uri.rawPath.trim('/').split('/')
        if (segments.size < 5 || segments[2] != "blob") return null
        val (owner, repo) = segments
        return "https://raw.githubusercontent.com/$owner/$repo/" + segments.drop(3).joinToString("/")
    }

    private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")
    private val GITHUB_HOSTS = setOf("github.com", "www.github.com")
}

/**
 * [encoded] with its `%XX` escapes decoded as UTF-8, or null when an escape is broken or the result
 * holds a NUL, which no path may.
 */
internal fun percentDecode(encoded: String): String? {
    val bytes = ByteArrayOutputStream()
    var i = 0
    while (i < encoded.length) {
        val c = encoded[i]
        if (c == '%') {
            val hex = encoded.substring(i + 1, minOf(i + 3, encoded.length))
            if (hex.length < 2 || !hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
            bytes.write(hex.toInt(16))
            i += 3
        } else {
            bytes.write(c.toString().encodeToByteArray())
            i++
        }
    }
    return bytes.toByteArray().decodeToString().takeIf { '\u0000' !in it }
}
