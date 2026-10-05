package com.naki.skiff.code.lsp

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream

/**
 * LSP's base protocol below the JSON: a `Content-Length` header, a blank line, and that many bytes of
 * UTF-8 (docs/skiffcode.spec.md "git과 LSP"). The JSON itself is never parsed here — the page's client
 * writes it and reads it.
 */
internal object LspFraming {

    fun encode(message: String): ByteArray {
        val body = message.encodeToByteArray()
        return "Content-Length: ${body.size}\r\n\r\n".encodeToByteArray() + body
    }
}

/** Reads framed messages off [input] one at a time. Not thread safe. */
internal class LspReader(input: InputStream) {

    private val input = input as? BufferedInputStream ?: BufferedInputStream(input)

    /**
     * The next message, or null when the stream ended between two of them. Throws [EOFException]
     * when it ends inside one, and [LspProtocolError] when what came is not a header — a login
     * shell's profile that prints something lands here, ahead of the server's first message.
     */
    fun read(): String? {
        var length = -1
        var line = line(atStart = true) ?: return null
        while (line.isNotEmpty()) {
            val colon = line.indexOf(':')
            if (colon < 0) throw LspProtocolError("not an LSP header: $line")
            if (line.substring(0, colon).trim().equals("Content-Length", ignoreCase = true)) {
                length = line.substring(colon + 1).trim().toIntOrNull()?.takeIf { it >= 0 }
                    ?: throw LspProtocolError("not a length: $line")
            }
            line = line(atStart = false) ?: throw EOFException("ended inside an LSP header")
        }
        if (length < 0) throw LspProtocolError("an LSP message with no Content-Length")
        val body = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(body, read, length - read)
            if (n < 0) throw EOFException("ended inside an LSP message")
            read += n
        }
        return body.decodeToString()
    }

    /** One header line without its `\r\n`, or null when the stream ends before its first byte at [atStart]. */
    private fun line(atStart: Boolean): String? {
        val out = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) {
                if (atStart && out.size() == 0) return null
                throw EOFException("ended inside an LSP header")
            }
            if (b == '\n'.code) break
            out.write(b)
        }
        return out.toByteArray().decodeToString().removeSuffix("\r")
    }
}

/** The server wrote something that is not LSP. Not an `IOException`: the link is fine, so reconnecting would not help. */
class LspProtocolError(message: String) : Exception(message)
