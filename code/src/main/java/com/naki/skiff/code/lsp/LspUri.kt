package com.naki.skiff.code.lsp

import com.naki.skiff.code.intent.percentDecode

/**
 * `file://` URIs for paths on the server, the way language servers name documents
 * (docs/skiffcode.spec/lsp.md). The page's client finds a document a server talks about by
 * comparing URI strings, so a path is encoded the way servers built on `vscode-uri` write it back:
 * every byte but the unreserved characters and `/` percent-encoded, as UTF-8.
 */
object LspUri {

    fun of(path: String): String = buildString {
        append("file://")
        for (byte in path.encodeToByteArray()) {
            val c = byte.toInt() and 0xff
            if (c.toChar() in UNRESERVED || c == '/'.code) append(c.toChar()) else append('%').append(HEX[c shr 4]).append(HEX[c and 0xf])
        }
    }

    /**
     * The absolute path [uri] names on the server, or null for anything but a `file:` URI with an
     * absolute path and no host: a definition in some other kind of document, or a malformed one.
     */
    fun path(uri: String): String? {
        if (!uri.startsWith("file:///")) return null
        return percentDecode(uri.removePrefix("file://").substringBefore('#').substringBefore('?'))
    }

    private val UNRESERVED = ('A'..'Z').toSet() + ('a'..'z') + ('0'..'9') + setOf('-', '.', '_', '~')
    private const val HEX = "0123456789ABCDEF"
}
