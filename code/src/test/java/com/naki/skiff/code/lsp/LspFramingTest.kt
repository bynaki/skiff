package com.naki.skiff.code.lsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.InputStream

class LspFramingTest {

    private fun reader(bytes: ByteArray) = LspReader(ByteArrayInputStream(bytes))

    private fun reader(text: String) = reader(text.encodeToByteArray())

    @Test
    fun `a message comes back as it was sent`() {
        val message = """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}"""

        assertEquals(message, reader(LspFraming.encode(message)).read())
    }

    @Test
    fun `the length counts bytes, not characters`() {
        val message = """{"text":"값 = \"한글\" + 1 🙂"}"""
        val framed = LspFraming.encode(message)

        val body = message.encodeToByteArray()
        assertEquals("Content-Length: ${body.size}\r\n\r\n", framed.copyOfRange(0, framed.size - body.size).decodeToString())
        assertTrue(body.size > message.length)
        assertEquals(message, reader(framed).read())
    }

    @Test
    fun `messages joined in one read come apart in order`() {
        val messages = listOf("""{"id":1}""", """{"id":2,"s":"둘"}""", "{}")
        val r = reader(messages.map(LspFraming::encode).reduce(ByteArray::plus))

        assertEquals(messages, List(3) { r.read() })
        assertNull(r.read())
    }

    @Test
    fun `a message that arrives a byte at a time is put back together`() {
        val message = """{"method":"textDocument/publishDiagnostics","params":{"message":"한글"}}"""
        val bytes = LspFraming.encode(message) + LspFraming.encode("{}")
        val trickle = object : InputStream() {
            private var at = 0
            override fun read() = if (at < bytes.size) bytes[at++].toInt() and 0xff else -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (len == 0) return 0
                val c = read()
                if (c < 0) return -1
                b[off] = c.toByte()
                return 1
            }
        }
        val r = LspReader(trickle)

        assertEquals(message, r.read())
        assertEquals("{}", r.read())
        assertNull(r.read())
    }

    @Test
    fun `other headers are passed over, and the name's case does not matter`() {
        val r = reader("content-length: 2\r\nContent-Type: application/vscode-jsonrpc; charset=utf-8\r\n\r\n{}")

        assertEquals("{}", r.read())
    }

    @Test
    fun `an empty stream is an end between messages`() {
        assertNull(reader("").read())
    }

    @Test
    fun `a stream that ends inside a message is not a clean end`() {
        assertThrows(EOFException::class.java) { reader("Content-Length: 10\r\n\r\n{}").read() }
        assertThrows(EOFException::class.java) { reader("Content-Length: 10\r\n").read() }
        assertThrows(EOFException::class.java) { reader("Content-Len").read() }
    }

    @Test
    fun `something printed ahead of the server is not taken for a header`() {
        val e = assertThrows(LspProtocolError::class.java) {
            reader("Welcome to the server\n" + LspFraming.encode("{}").decodeToString()).read()
        }
        assertTrue(e.message!!.contains("Welcome to the server"))
    }

    @Test
    fun `a header block with no usable length is refused`() {
        assertThrows(LspProtocolError::class.java) { reader("Content-Type: x\r\n\r\n{}").read() }
        assertThrows(LspProtocolError::class.java) { reader("Content-Length: -1\r\n\r\n").read() }
        assertThrows(LspProtocolError::class.java) { reader("Content-Length: two\r\n\r\n{}").read() }
    }
}
