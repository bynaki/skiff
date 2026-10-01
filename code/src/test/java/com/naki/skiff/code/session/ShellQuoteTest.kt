package com.naki.skiff.code.session

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Through a real `/bin/sh`: what matters is what the shell makes of the line, not what the string
 * looks like. An injection here would run on the machine running the tests, so the hostile inputs
 * create a marker file instead of doing anything worse, and the test checks that it never appears.
 */
class ShellQuoteTest {

    private val dir = Files.createTempDirectory("shellquote").toFile()
    private val marker = File(dir, "ran")

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun sh(line: String): String {
        val process = ProcessBuilder("/bin/sh", "-c", line).directory(dir).redirectErrorStream(true).start()
        val out = process.inputStream.readBytes().toString(Charsets.UTF_8)
        process.waitFor()
        return out
    }

    /** The shell prints back exactly the one argument it was given. */
    private fun roundTrip(arg: String) {
        assertEquals(arg, sh(ShellQuote.command(listOf("printf", "%s", arg))))
        assertFalse("the argument ran as a command: $arg", marker.exists())
    }

    private val hostile = listOf(
        "'; touch ran; '",
        "a\ntouch ran",
        "$(touch ran)",
        "`touch ran`",
        "it's/a 'path'",
        "",
        "  spaces  and\ttabs ",
        "*",
        "~",
        "\$HOME \${HOME}",
        "back\\slash \\' \"double\"",
        "한글 경로/파일.md",
        "-n",
    )

    @Test
    fun `every hostile argument comes back unchanged and runs nothing`() {
        for (arg in hostile) roundTrip(arg)
    }

    @Test
    fun `a quoted command survives being quoted again for a second shell`() {
        // What LspProcess does: a whole command line handed to `sh -lc` as one argument.
        for (arg in hostile) {
            val inner = ShellQuote.command(listOf("printf", "%s", arg))
            assertEquals(arg, sh(ShellQuote.command(listOf("/bin/sh", "-c", inner))))
            assertFalse("the argument ran as a command: $arg", marker.exists())
        }
    }

    @Test
    fun `arguments stay separate`() {
        assertEquals("[a b][][c'd]", sh(ShellQuote.command(listOf("printf", "[%s]", "a b", "", "c'd"))))
    }

    @Test
    fun `a nul is refused rather than cut short`() {
        assertThrows(IllegalArgumentException::class.java) { ShellQuote.quote("a\u0000b") }
    }
}
