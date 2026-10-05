package com.naki.skiff.code.lsp

import com.naki.skiff.SftpTestServer
import com.naki.skiff.code.session.RemoteExec
import kotlinx.coroutines.runBlocking
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import org.apache.sshd.server.Environment
import org.apache.sshd.server.ExitCallback
import org.apache.sshd.server.channel.ChannelSession
import org.apache.sshd.server.command.Command
import org.apache.sshd.server.command.CommandFactory
import org.apache.sshd.server.shell.ProcessShellFactory
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.security.PublicKey
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * [LspProcess] over a real SSH exec channel. The server on the other end is a stub that answers the
 * few LSP messages these tests send ([StubServer]); its framing is written out here on its own, so a
 * mistake in [LspFraming] is not mirrored on both sides. M0 ran the real pyright this way.
 */
class LspProcessTest {

    private var server: SftpTestServer? = null
    private var exec: RemoteExec? = null
    private var process: LspProcess? = null

    private val messages = LinkedBlockingQueue<String>()
    private val ends = LinkedBlockingQueue<Result<Throwable?>>()

    private val acceptAnyKey = object : HostKeyVerifier {
        override fun verify(hostname: String, port: Int, key: PublicKey) = true
        override fun findExistingAlgorithms(hostname: String, port: Int) = emptyList<String>()
    }

    private fun start(commands: CommandFactory, argv: List<String> = listOf("stub-langserver", "--stdio")): LspProcess {
        val server = SftpTestServer(commands = commands).also { it.start() }
        this.server = server
        val exec = RemoteExec("127.0.0.1", server.port, server.username, { server.password }, acceptAnyKey)
        this.exec = exec
        val channel = runBlocking { exec.start(argv) }
        return LspProcess(channel, onMessage = { messages.put(it) }, onEnd = { ends.put(Result.success(it)) })
            .also { process = it }
    }

    private fun stub(stderrBytes: Int = 0, banner: String = "") =
        CommandFactory { _, _ -> StubServer(stderrBytes, banner) }

    private fun next(): JSONObject = JSONObject(messages.poll(10, TimeUnit.SECONDS) ?: fail("no message"))

    private fun end(): Throwable? = (ends.poll(10, TimeUnit.SECONDS) ?: fail("never ended")).getOrThrow()

    private fun fail(why: String): Nothing = throw AssertionError(why)

    @After
    fun tearDown() {
        process?.close()
        exec?.close()
        server?.stop()
    }

    @Test
    fun `initialize, a document and shutdown make the round trip`() {
        val lsp = start(stub())

        lsp.send("""{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"rootUri":"file:///project"}}""")
        val reply = next()
        assertEquals(1, reply.getInt("id"))
        assertEquals(2, reply.getJSONObject("result").getJSONObject("capabilities").getInt("textDocumentSync"))

        val text = "값 = \"한글\" + 1\n"
        lsp.send(JSONObject().put("jsonrpc", "2.0").put("method", "textDocument/didOpen").put(
            "params", JSONObject().put("textDocument", JSONObject().put("uri", "file:///project/a.py").put("text", text)),
        ).toString())
        val published = next()
        assertEquals("textDocument/publishDiagnostics", published.getString("method"))
        assertEquals(text, published.getJSONObject("params").getString("echo"))

        lsp.send("""{"jsonrpc":"2.0","id":2,"method":"shutdown"}""")
        assertEquals(2, next().getInt("id"))
        lsp.send("""{"jsonrpc":"2.0","method":"exit"}""")
        assertNull(end())
    }

    @Test
    fun `a server that logs heavily to stderr still answers`() {
        // Past sshj's 2 MB channel window, written before the first reply.
        val lsp = start(stub(stderrBytes = 8 shl 20))

        lsp.send("""{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}""")

        assertEquals(1, next().getInt("id"))
        val tail = lsp.stderrTail()
        assertTrue(tail.length in 1..8192)
        assertTrue(tail.endsWith("log line\n"))
    }

    @Test
    fun `messages go through a real command and come back framed as they went`() {
        val shell = CommandFactory { channel, line ->
            ProcessShellFactory(line, listOf("/bin/sh", "-c", line)).createShell(channel)
        }
        val lsp = start(shell, argv = listOf("cat"))
        val sent = listOf("""{"id":1}""", """{"text":"한글 🙂"}""", "{}")

        sent.forEach(lsp::send)

        assertEquals(sent, List(3) { messages.poll(10, TimeUnit.SECONDS) })
    }

    @Test
    fun `something printed ahead of the server ends it as not LSP`() {
        val lsp = start(stub(banner = "Welcome to the server\n"))

        lsp.send("""{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}""")

        assertTrue(end() is LspProtocolError)
    }

    @Test
    fun `a server that dies inside a message ends it with the reason`() {
        val lsp = start(stub())

        lsp.send("""{"jsonrpc":"2.0","id":1,"method":"die"}""")

        assertTrue(end() is EOFException)
    }

    @Test
    fun `closing it ends the command on the server, and is not reported`() {
        val gone = CountDownLatch(1)
        val lsp = start(CommandFactory { _, _ -> StubServer(onInputEnd = gone::countDown) })

        lsp.close()

        assertTrue(gone.await(10, TimeUnit.SECONDS))
        assertNull(ends.poll(500, TimeUnit.MILLISECONDS))
        lsp.send("{}")
    }

    /**
     * Answers `initialize` and `shutdown`, echoes a `didOpen`'s text back as a diagnostics
     * notification, leaves on `exit`, and on `die` writes half a message and leaves.
     */
    private class StubServer(
        private val stderrBytes: Int = 0,
        private val banner: String = "",
        private val onInputEnd: () -> Unit = {},
    ) : Command {
        private lateinit var input: InputStream
        private lateinit var output: OutputStream
        private lateinit var error: OutputStream
        private lateinit var exit: ExitCallback

        override fun setInputStream(`in`: InputStream) { input = `in` }
        override fun setOutputStream(out: OutputStream) { output = out }
        override fun setErrorStream(err: OutputStream) { error = err }
        override fun setExitCallback(callback: ExitCallback) { exit = callback }

        override fun start(channel: ChannelSession, env: Environment) {
            thread(isDaemon = true) {
                try {
                    serve()
                } catch (_: Exception) {
                } finally {
                    onInputEnd()
                }
            }
        }

        private fun serve() {
            write(banner.toByteArray())
            while (true) {
                val message = receive() ?: break
                val id = message.opt("id")
                when (message.optString("method")) {
                    "initialize" -> {
                        if (stderrBytes > 0) {
                            val line = "log line\n".toByteArray()
                            repeat(stderrBytes / line.size) { error.write(line) }
                            error.flush()
                        }
                        reply(id, JSONObject().put("capabilities", JSONObject().put("textDocumentSync", 2)))
                    }
                    "textDocument/didOpen" -> {
                        val text = message.getJSONObject("params").getJSONObject("textDocument").getString("text")
                        send(JSONObject().put("jsonrpc", "2.0").put("method", "textDocument/publishDiagnostics")
                            .put("params", JSONObject().put("echo", text)))
                    }
                    "shutdown" -> reply(id, JSONObject.NULL)
                    "exit" -> return exit.onExit(0)
                    "die" -> {
                        write("Content-Length: 100\r\n\r\n{\"id\":".toByteArray())
                        return exit.onExit(1)
                    }
                }
            }
            exit.onExit(0)
        }

        private fun receive(): JSONObject? {
            var length = 0
            while (true) {
                val line = buildString {
                    while (true) {
                        val c = input.read()
                        if (c < 0) return null
                        if (c == '\n'.code) break
                        append(c.toChar())
                    }
                }.trimEnd('\r')
                if (line.isEmpty()) break
                if (line.startsWith("Content-Length:")) length = line.substringAfter(':').trim().toInt()
            }
            return JSONObject(input.readNBytes(length).decodeToString())
        }

        private fun reply(id: Any?, result: Any) =
            send(JSONObject().put("jsonrpc", "2.0").put("id", id).put("result", result))

        private fun send(message: JSONObject) {
            val body = message.toString().toByteArray()
            write("Content-Length: ${body.size}\r\n\r\n".toByteArray() + body)
        }

        private fun write(bytes: ByteArray) {
            output.write(bytes)
            output.flush()
        }

        override fun destroy(channel: ChannelSession) = Unit
    }
}
