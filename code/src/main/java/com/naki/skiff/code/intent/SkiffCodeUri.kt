package com.naki.skiff.code.intent

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * A parsed `skiffcode://` link.
 *
 * ```
 * remote  skiffcode://alice@192.0.2.10:22/home/alice/demo/a.md?alias=home-server&line=42&layer=viewer
 * local   skiffcode:///storage/emulated/0/Documents/a.md
 * ```
 *
 * Links arrive from other apps, so everything here is outside input. Parsing is written by hand
 * rather than through `android.net.Uri` (a stub on the unit test JVM) or `java.net.URI` (which
 * rejects a Korean path typed without percent encoding, and gives up on the host of a name it
 * does not like instead of saying why).
 *
 * The parser only reads the link. Which profile an `alias` or a `(user, host, port)` names is
 * decided later, when the link is resolved against the stored profiles.
 */
data class SkiffCodeUri(
    val target: Target,
    /** 1-based. */
    val line: Int? = null,
    /** 1-based. */
    val col: Int? = null,
    val layer: Layer? = null,
) {

    sealed interface Target {
        /** Absolute POSIX path, percent-decoded. */
        val path: String
    }

    data class Local(override val path: String) : Target

    data class Remote(
        /** Null when the link names the server only by [alias]. */
        val user: String?,
        /** Lowercased. An IPv6 address is kept without its brackets. */
        val host: String,
        val port: Int,
        override val path: String,
        val alias: String?,
    ) : Target

    enum class Layer { VIEWER, EDITOR, DIFF }

    companion object {
        const val SCHEME = "skiffcode"
        const val DEFAULT_PORT = 22
        private const val HEX_DIGITS = "0123456789abcdefABCDEF"

        /** @throws IllegalArgumentException with a message naming what is wrong with [uri]. */
        fun parse(uri: String): SkiffCodeUri {
            val prefix = "$SCHEME://"
            require(uri.startsWith(prefix, ignoreCase = true)) { "not a $SCHEME:// link" }

            val rest = uri.substring(prefix.length).substringBefore('#')
            val rawQuery = rest.substringAfter('?', missingDelimiterValue = "")
            val hierarchy = rest.substringBefore('?')
            val slash = hierarchy.indexOf('/')
            require(slash >= 0) { "the link has no path" }
            val rawAuthority = hierarchy.substring(0, slash)
            val path = decode(hierarchy.substring(slash))
            require(path.length > 1) { "the link has no path" }

            val query = parseQuery(rawQuery)
            val target = if (rawAuthority.isEmpty()) {
                Local(path)
            } else {
                parseRemote(rawAuthority, path, query["alias"])
            }
            return SkiffCodeUri(
                target = target,
                line = query["line"]?.let { positiveInt("line", it) },
                col = query["col"]?.let { positiveInt("col", it) },
                layer = query["layer"]?.let { parseLayer(it) },
            )
        }

        private fun parseRemote(rawAuthority: String, path: String, alias: String?): Remote {
            val at = rawAuthority.lastIndexOf('@')
            val rawUser = if (at >= 0) rawAuthority.substring(0, at) else null
            val hostPort = rawAuthority.substring(at + 1)

            // A password in a link ends up in shell history, clipboard managers and chat logs.
            require(rawUser == null || ':' !in rawUser) { "a password in the link is not accepted" }
            val user = rawUser?.let { decode(it) }
            require(user == null || user.isNotEmpty()) { "the user name is empty" }

            val host: String
            val rawPort: String?
            if (hostPort.startsWith('[')) {
                val close = hostPort.indexOf(']')
                require(close > 1) { "the IPv6 address is not closed with ]" }
                host = hostPort.substring(1, close)
                require(host.all { it.isLetterOrDigit() || it == ':' || it == '.' } && ':' in host) {
                    "not an IPv6 address: $host"
                }
                val after = hostPort.substring(close + 1)
                require(after.isEmpty() || after.startsWith(':')) { "unexpected text after the IPv6 address" }
                rawPort = if (after.isEmpty()) null else after.substring(1)
            } else {
                val colon = hostPort.indexOf(':')
                host = decode(if (colon >= 0) hostPort.substring(0, colon) else hostPort)
                rawPort = if (colon >= 0) hostPort.substring(colon + 1) else null
                require(host.isNotEmpty()) { "the host is empty" }
                require(host.none { it.isWhitespace() || it in "/?#@[]" }) { "not a host name: $host" }
            }

            val port = rawPort?.let {
                val value = it.toIntOrNull()
                require(it.all(Char::isDigit) && value != null && value in 1..65535) { "not a port: $it" }
                value
            } ?: DEFAULT_PORT

            return Remote(
                user = user,
                host = host.lowercase(),
                port = port,
                path = path,
                alias = alias?.takeIf { it.isNotEmpty() },
            )
        }

        /** The first value wins when a key repeats. Unknown keys are ignored. */
        private fun parseQuery(rawQuery: String): Map<String, String> {
            val out = LinkedHashMap<String, String>()
            if (rawQuery.isEmpty()) return out
            for (pair in rawQuery.split('&')) {
                if (pair.isEmpty()) continue
                val key = decode(pair.substringBefore('='))
                val value = decode(pair.substringAfter('=', missingDelimiterValue = ""))
                out.putIfAbsent(key, value)
            }
            return out
        }

        private fun positiveInt(name: String, value: String): Int {
            val n = value.toIntOrNull()
            require(value.all(Char::isDigit) && n != null && n >= 1) { "$name must be a positive number: $value" }
            return n
        }

        private fun parseLayer(value: String): Layer =
            Layer.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException("layer must be viewer, editor or diff: $value")

        /**
         * RFC 3986 percent decoding. `+` stays a plus sign, since this is not a form body.
         * Characters that arrive unencoded (a Korean file name pasted into a note) are taken as
         * they are. The bytes must be valid UTF-8, and NUL is refused because no path can hold it.
         */
        private fun decode(raw: String): String {
            val bytes = ByteArrayOutputStream(raw.length)
            var i = 0
            while (i < raw.length) {
                val c = raw[i]
                if (c == '%') {
                    val hex = raw.substring(i + 1, minOf(i + 3, raw.length))
                    require(hex.length == 2 && hex.all { it in HEX_DIGITS }) {
                        "broken percent encoding at \"${raw.substring(i)}\""
                    }
                    bytes.write(hex.toInt(16))
                    i += 3
                } else {
                    val end = if (Character.isHighSurrogate(c) && i + 1 < raw.length) i + 2 else i + 1
                    bytes.write(raw.substring(i, end).toByteArray(Charsets.UTF_8))
                    i = end
                }
            }
            val decoded = try {
                Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes.toByteArray()))
                    .toString()
            } catch (e: CharacterCodingException) {
                throw IllegalArgumentException("the link is not valid UTF-8", e)
            }
            require(' ' !in decoded) { "the link contains a NUL character" }
            return decoded
        }
    }
}
