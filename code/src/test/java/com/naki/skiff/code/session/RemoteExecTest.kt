package com.naki.skiff.code.session

import com.naki.skiff.SftpTestServer
import com.naki.skiff.fs.FsError
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import org.apache.sshd.server.Environment
import org.apache.sshd.server.ExitCallback
import org.apache.sshd.server.channel.ChannelSession
import org.apache.sshd.server.command.Command
import org.apache.sshd.server.command.CommandFactory
import org.apache.sshd.server.shell.ProcessShellFactory
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStream
import java.io.OutputStream
import java.security.PublicKey
import kotlin.concurrent.thread

/**
 * Against a real SSH server. Where it runs commands, it hands the line to `/bin/sh -c` as sshd hands
 * it to the login shell; the accounts that cannot run them are stood in for by commands that behave
 * the way `nologin` and a forced `internal-sftp` do.
 *
 * A command killed by a signal is not tested: MINA reports the process's 128+n as an exit status where
 * OpenSSH sends exit-signal, which is what leaves [ExecResult.exitStatus] null.
 */
class RemoteExecTest {

    private var server: SftpTestServer? = null
    private var exec: RemoteExec? = null

    private val acceptAnyKey = object : HostKeyVerifier {
        override fun verify(hostname: String, port: Int, key: PublicKey) = true
        override fun findExistingAlgorithms(hostname: String, port: Int) = emptyList<String>()
    }

    private val shell = CommandFactory { channel, line ->
        ProcessShellFactory(line, listOf("/bin/sh", "-c", line)).createShell(channel)
    }

    private fun start(commands: CommandFactory?, password: String? = null): Pair<SftpTestServer, RemoteExec> {
        val server = SftpTestServer(commands = commands).also { it.start() }
        val exec = RemoteExec(
            host = "127.0.0.1",
            port = server.port,
            username = server.username,
            password = { password ?: server.password },
            hostKeyVerifier = acceptAnyKey,
        )
        this.server = server
        this.exec = exec
        return server to exec
    }

    @After
    fun tearDown() {
        exec?.close()
        server?.stop()
    }

    @Test
    fun `a command's output, errors and exit status come back`() = runBlocking {
        val (_, exec) = start(shell)

        val result = exec.run(listOf("/bin/sh", "-c", "printf out; printf err >&2; exit 3"))

        assertArrayEquals("out".toByteArray(), result.stdout)
        assertEquals("err", result.stderr)
        assertEquals(3, result.exitStatus)
    }

    @Test
    fun `arguments reach the program as they were given`() = runBlocking {
        val (_, exec) = start(shell)
        val arg = "it's \$(echo no) `echo no` 한글\nline"

        assertEquals(arg, exec.run(listOf("printf", "%s", arg)).stdout.decodeToString())
    }

    @Test
    fun `an account that runs commands is supported`() = runBlocking {
        val (_, exec) = start(shell)

        assertTrue(exec.supported())
    }

    @Test
    fun `a refused request is told apart, and is not supported`() = runBlocking {
        val (_, exec) = start(commands = null)

        assertThrows(ExecRefused::class.java) { runBlocking { exec.run(listOf("true")) } }
        assertThrows(ExecRefused::class.java) { runBlocking { exec.start(listOf("cat")) } }
        assertFalse(exec.supported())
    }

    @Test
    fun `a started command answers each line as it is written, until it is closed`() = runBlocking {
        val (_, exec) = start(shell)

        exec.start(listOf("cat")).use { channel ->
            val lines = channel.stdout.bufferedReader()
            for (line in listOf("one", "two words", "셋")) {
                channel.stdin.write("$line\n".toByteArray())
                channel.stdin.flush()
                assertEquals(line, lines.readLine())
            }
        }
        // The channel is gone, the login is not.
        assertEquals(0, exec.run(listOf("true")).exitStatus)
    }

    @Test
    fun `a nologin shell is not supported`() = runBlocking {
        val (_, exec) = start(CommandFactory { _, _ -> Scripted(exitStatus = 1, stdout = "This account is currently not available.\n") })

        assertFalse(exec.supported())
    }

    @Test
    fun `a forced internal-sftp is not supported, and does not hang`() = runBlocking {
        // Takes the request, ignores the command, and waits for SFTP packets until stdin ends.
        val (_, exec) = start(CommandFactory { _, _ -> Scripted(exitStatus = 0, stdout = "", waitForInput = true) })

        assertFalse(exec.supported())
    }

    @Test
    fun `a command that outlives its timeout is given up on`() = runBlocking {
        val (_, exec) = start(shell)
        val started = System.nanoTime()

        assertThrows(ExecTimedOut::class.java) { runBlocking { exec.run(listOf("sleep", "10"), timeoutMs = 300) } }
        assertTrue((System.nanoTime() - started) / 1_000_000 < 5_000)
        // The client is still usable afterwards.
        assertEquals(0, exec.run(listOf("true")).exitStatus)
    }

    @Test
    fun `one login serves many commands, side by side`() = runBlocking {
        val (server, exec) = start(shell)

        val results = (1..5).map { n -> async { exec.run(listOf("printf", "$n")) } }.awaitAll()

        assertEquals((1..5).map { "$it" }, results.map { it.stdout.decodeToString() })
        assertEquals(1, server.passwordChecks.get())
    }

    @Test
    fun `a wrong password is an auth failure, asked once`() = runBlocking {
        val (server, exec) = start(shell, password = "wrong")

        assertThrows(FsError.AuthFailed::class.java) { runBlocking { exec.run(listOf("true")) } }
        assertEquals(1, server.passwordChecks.get())
    }

    /** A server-side command that answers the same way whatever it was asked to run. */
    private class Scripted(
        private val exitStatus: Int,
        private val stdout: String,
        private val waitForInput: Boolean = false,
    ) : Command {
        private lateinit var input: InputStream
        private lateinit var output: OutputStream
        private lateinit var exit: ExitCallback

        override fun setInputStream(`in`: InputStream) { input = `in` }
        override fun setOutputStream(out: OutputStream) { output = out }
        override fun setErrorStream(err: OutputStream) = Unit
        override fun setExitCallback(callback: ExitCallback) { exit = callback }

        override fun start(channel: ChannelSession, env: Environment) {
            thread(isDaemon = true) {
                if (waitForInput) input.readBytes()
                output.write(stdout.toByteArray())
                output.flush()
                exit.onExit(exitStatus)
            }
        }

        override fun destroy(channel: ChannelSession) = Unit
    }
}
