package com.naki.skiff.code.lsp

import com.naki.skiff.SftpTestServer
import com.naki.skiff.code.session.RemoteExec
import com.naki.skiff.code.session.ShellQuote
import kotlinx.coroutines.runBlocking
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import org.apache.sshd.server.command.CommandFactory
import org.apache.sshd.server.shell.ProcessShellFactory
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.PublicKey
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * [LspManager] against a real SSH server. `command -v` and the login shell go to a real `/bin/sh`
 * with a HOME of the test's own, whose profile alone puts the stub's folder on the PATH — so a command
 * is found only if the login shell ran, and no profile of this machine's does. Starting the server
 * itself goes to [StubLanguageServer].
 */
class LspManagerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private var server: SftpTestServer? = null
    private var exec: RemoteExec? = null
    private var manager: LspManager? = null

    /** What the page would hear: message strings and [LspEnd]s, in order. */
    private val heard = LinkedBlockingQueue<Any>()

    /** Methods the stub servers were sent, and how many of them were started. */
    private val received = LinkedBlockingQueue<String>()
    private val starts = AtomicInteger()
    private val detections = AtomicInteger()

    private val acceptAnyKey = object : HostKeyVerifier {
        override fun verify(hostname: String, port: Int, key: PublicKey) = true
        override fun findExistingAlgorithms(hostname: String, port: Int) = emptyList<String>()
    }

    private val installed = listOf("skiff-stub-ls", "--stdio")
    private val missing = listOf("skiff-missing-ls", "--stdio")

    private fun start(
        commands: List<List<String>> = listOf(installed),
        positionEncoding: String? = null,
        idleMs: Long = LspManager.IDLE_MS,
    ): LspManager {
        val bin = File(tmp.root, "bin").apply { mkdirs() }
        File(bin, installed.first()).apply { writeText("#!/bin/sh\n") }.setExecutable(true)
        // The language server's folder is on the PATH only once the profile has run, as a version manager's is.
        val home = File(tmp.root, "home").apply { mkdirs() }
        File(home, ".profile").writeText("PATH='${bin.path}':\$PATH; export PATH\n")
        val factory = CommandFactory { channel, line ->
            // The start's line quotes the script a second time, so only the name is matched.
            if (installed.first() in line && "command -v" !in line) {
                starts.incrementAndGet()
                StubLanguageServer(positionEncoding = positionEncoding, received = received::put)
            } else {
                if ("command -v" in line) detections.incrementAndGet()
                // Set inside the line: MINA runs /bin/sh -c on its first argument and ignores the rest.
                val withEnv = "HOME='${home.path}'; PATH=/usr/bin:/bin; SHELL=/bin/sh; export HOME PATH SHELL; $line"
                ProcessShellFactory(withEnv, listOf("/bin/sh", "-c", withEnv)).createShell(channel)
            }
        }
        val server = SftpTestServer(commands = factory).also { it.start() }
        this.server = server
        val exec = RemoteExec("127.0.0.1", server.port, server.username, { server.password }, acceptAnyKey)
        this.exec = exec
        return LspManager(exec, tmp.root.path, idleMs, commands = { commands }).also {
            it.listener = object : LspManager.Listener {
                override fun message(server: LanguageServer, message: String) = heard.put(message)
                override fun ended(server: LanguageServer, why: LspEnd) = heard.put(why)
            }
            manager = it
        }
    }

    @After
    fun tearDown() {
        manager?.close()
        exec?.close()
        server?.stop()
    }

    private fun initialize(id: Int) = """{"jsonrpc":"2.0","id":$id,"method":"initialize","params":{}}"""

    private fun next(): Any = heard.poll(10, TimeUnit.SECONDS) ?: throw AssertionError("nothing heard")

    private fun nextMessage(): JSONObject = JSONObject(next() as String)

    private fun method(): String = received.poll(10, TimeUnit.SECONDS) ?: throw AssertionError("nothing received")

    @Test
    fun `a script runs in the login shell, from a root whose name needs quoting`() = runBlocking {
        start()
        val root = File(tmp.root, "it's a \$(project)").apply { mkdirs() }

        val result = exec!!.run(LspManager.loginShell("cd ${ShellQuote.quote(root.path)} && exec pwd"))

        assertEquals(0, result.exitStatus)
        assertEquals(root.path, result.stdout.decodeToString().trim())
    }

    @Test
    fun `the first installed command is the one, and is asked once`() = runBlocking {
        val lsp = start(commands = listOf(missing, installed))

        assertEquals(installed, lsp.command(LanguageServer.Python))
        assertEquals(installed, lsp.command(LanguageServer.Python))
        assertEquals(2, detections.get())
    }

    @Test
    fun `no installed command is none, and initialize says so`() = runBlocking {
        val lsp = start(commands = listOf(missing))

        assertNull(lsp.command(LanguageServer.Python))
        lsp.send(LanguageServer.Python, initialize(0))

        assertEquals(LspEnd.NotInstalled, next())
        assertEquals(0, starts.get())
    }

    @Test
    fun `initialize starts the server, and messages pass both ways`() {
        val lsp = start()

        lsp.send(LanguageServer.Python, """{"jsonrpc":"2.0","method":"textDocument/didOpen","params":{}}""")
        lsp.send(LanguageServer.Python, initialize(0))
        assertEquals(0, nextMessage().getInt("id"))
        assertEquals("initialize", method())

        lsp.send(LanguageServer.Python, JSONObject().put("jsonrpc", "2.0").put("method", "textDocument/didOpen").put(
            "params", JSONObject().put("textDocument", JSONObject().put("text", "한글")),
        ).toString())
        assertEquals("한글", nextMessage().getJSONObject("params").getString("echo"))
        // The didOpen sent before initialize never reached a server.
        assertEquals("textDocument/didOpen", method())
        assertNull(received.poll(200, TimeUnit.MILLISECONDS))
        assertEquals(1, starts.get())
    }

    @Test
    fun `a server unused for a while is shut down, and the next initialize starts another`() {
        val lsp = start(idleMs = 300)

        lsp.send(LanguageServer.Python, initialize(0))
        nextMessage()

        assertEquals(LspEnd.Idle, next())
        assertEquals(listOf("initialize", "shutdown", "exit"), List(3) { method() })

        lsp.send(LanguageServer.Python, initialize(1))
        assertEquals(1, nextMessage().getInt("id"))
        assertEquals(2, starts.get())
    }

    @Test
    fun `a server counting positions in UTF-8 is not let through`() {
        val lsp = start(positionEncoding = "utf-8")

        lsp.send(LanguageServer.Python, initialize(0))

        assertEquals(LspEnd.Unsupported("utf-8"), next())
        assertEquals(listOf("initialize", "shutdown", "exit"), List(3) { method() })
    }

    @Test
    fun `a server that says UTF-16 is let through`() {
        val lsp = start(positionEncoding = "utf-16")

        lsp.send(LanguageServer.Python, initialize(0))

        assertEquals(0, nextMessage().getInt("id"))
    }

    @Test
    fun `a server that dies is reported, and messages after it are dropped`() {
        val lsp = start()
        lsp.send(LanguageServer.Python, initialize(0))
        nextMessage()

        lsp.send(LanguageServer.Python, """{"jsonrpc":"2.0","id":1,"method":"die"}""")
        val why = next()
        lsp.send(LanguageServer.Python, """{"jsonrpc":"2.0","method":"textDocument/didOpen","params":{}}""")

        assertTrue(why is LspEnd.Failed)
        assertEquals(listOf("initialize", "die"), List(2) { method() })
        assertNull(received.poll(300, TimeUnit.MILLISECONDS))
        assertEquals(1, starts.get())
    }

    @Test
    fun `a second initialize replaces the server without reporting the first`() {
        val lsp = start()
        lsp.send(LanguageServer.Python, initialize(0))
        nextMessage()

        lsp.send(LanguageServer.Python, initialize(0))

        assertEquals(0, nextMessage().getInt("id"))
        assertEquals(listOf("initialize", "shutdown", "exit", "initialize"), List(4) { method() })
        assertEquals(2, starts.get())
        assertNull(heard.poll(300, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `stopping all shuts each one down and says so`() {
        val lsp = start()
        lsp.send(LanguageServer.Python, initialize(0))
        lsp.send(LanguageServer.TypeScript, initialize(0))
        nextMessage()
        nextMessage()

        lsp.stopAll()

        assertEquals(listOf(LspEnd.Stopped, LspEnd.Stopped), List(2) { next() })
        assertEquals(2, starts.get())
    }
}
