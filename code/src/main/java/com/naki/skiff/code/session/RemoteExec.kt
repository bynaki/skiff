package com.naki.skiff.code.session

import com.naki.skiff.fs.sftp.SshClientFactory
import com.naki.skiff.fs.sftp.isFatalAuth
import com.naki.skiff.fs.sftp.toConnectionError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.ConnectionException
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

/**
 * The only place Skiff Code runs a command on a server (`AGENTS.md`: a call site anywhere else is a
 * design change). One per project, on an [SSHClient] of its own, so a long command never waits behind
 * the SFTP session's single thread, and the SFTP session never waits behind it.
 *
 * Commands are argv lists, put through [ShellQuote] here and nowhere else. An account that cannot run
 * them — `internal-sftp`, a `nologin` shell — is a normal state, not an error: [supported] answers it
 * once so the project opens without git and LSP.
 *
 * A dropped link is reconnected once and the command run again, as SFTP does, so a command run here
 * must be safe to run twice. Everything planned for it reads.
 */
class RemoteExec(
    private val host: String,
    port: Int,
    username: String,
    private val password: suspend () -> String?,
    hostKeyVerifier: HostKeyVerifier,
) {

    private val clients = SshClientFactory(host, port, username, hostKeyVerifier)
    private val connecting = Mutex()

    @Volatile
    private var client: SSHClient? = null

    /**
     * Whether this account runs commands at all. A refused request says no outright, but a forced
     * `internal-sftp` takes the request and runs itself instead, and `nologin` prints a refusal and
     * fails — so the only yes is a known answer coming back.
     */
    suspend fun supported(): Boolean = try {
        val result = run(listOf("printf", PROBE))
        result.exitStatus == 0 && result.stdout.decodeToString() == PROBE
    } catch (_: ExecRefused) {
        false
    }

    /** Runs [argv] to the end. Throws [ExecRefused], [ExecTimedOut], or an `FsError` for the link. */
    suspend fun run(argv: List<String>, timeoutMs: Long = TIMEOUT_MS): ExecResult =
        retrying(argv) { line, secret -> attempt(line, secret, timeoutMs) }

    /**
     * Starts [argv] and leaves it running, for a command that is talked to over its stdin rather than
     * run to the end — `git cat-file --batch`. The caller owns what comes back and closes it. Starting
     * is retried on a dropped link as [run] is; a link that drops afterwards is the caller's to start
     * again. Throws [ExecRefused], or an `FsError` for the link.
     */
    suspend fun start(argv: List<String>): ExecChannel = retrying(argv) { line, secret ->
        val client = connected(secret)
        runInterruptible(Dispatchers.IO) {
            val session = client.startSession()
            try {
                ExecChannel(session, session.exec(line))
            } catch (e: ConnectionException) {
                session.close()
                throw ExecRefused(e)
            } catch (e: Throwable) {
                session.close()
                throw e
            }
        }
    }

    /** [block] with the command line and the password, once more if the link dropped under it. */
    private suspend fun <T> retrying(argv: List<String>, block: suspend (String, String?) -> T): T {
        val line = ShellQuote.command(argv)
        // Resolved before the connection is touched: fetching it may reach the keystore.
        val secret = password()
        return try {
            block(line, secret)
        } catch (e: IOException) {
            val failure = e.toConnectionError(host)
            if (failure.isFatalAuth()) throw failure
            // Not disconnected here: another command may already be on a fresh client, and a dead
            // one is replaced by connected() anyway.
            try {
                block(line, secret)
            } catch (retry: IOException) {
                throw retry.toConnectionError(host)
            }
        }
    }

    private suspend fun attempt(line: String, secret: String?, timeoutMs: Long): ExecResult {
        val client = connected(secret)
        // Interruptible, because sshj's channel streams wait without a timeout of their own.
        return withTimeoutOrNull(timeoutMs) {
            try {
                runInterruptible(Dispatchers.IO) { execute(client, line, timeoutMs) }
            } catch (e: IOException) {
                // The interrupt that ends a timed-out read surfaces from sshj as an IOException; it is
                // the timeout, not a dropped link, and must not be retried as one.
                ensureActive()
                throw e
            }
        } ?: throw ExecTimedOut(timeoutMs)
    }

    private fun execute(client: SSHClient, line: String, timeoutMs: Long): ExecResult =
        client.startSession().use { session ->
            val command = try {
                session.exec(line)
            } catch (e: ConnectionException) {
                // The session is open, so a failed reply is the server declining this request.
                throw ExecRefused(e)
            }
            // Nothing is sent on stdin. A forced internal-sftp reads it for SFTP packets, and without
            // the EOF it would hold the channel open until the timeout.
            command.outputStream.close()
            val stdout = command.inputStream.readBytes()
            val stderr = command.errorStream.readBytes()
            command.join(timeoutMs, TimeUnit.MILLISECONDS)
            ExecResult(command.exitStatus, stdout, stderr.decodeToString())
        }

    private suspend fun connected(secret: String?): SSHClient = connecting.withLock {
        client?.takeIf { it.isConnected }?.let { return@withLock it }
        disconnect()
        runInterruptible(Dispatchers.IO) { clients.connect(secret) }.also { client = it }
    }

    private fun disconnect() {
        runCatching { client?.disconnect() }
        client = null
    }

    fun close() = disconnect()

    private companion object {
        const val PROBE = "skiff-exec-ok"
        const val TIMEOUT_MS = 30_000L
    }
}

/**
 * A command [RemoteExec.start] left running: what is written to [stdin] reaches it, and [stdout] is
 * what it answers. Its stderr is not read, so a command that writes much there would stall; the ones
 * started here write to it only as they fail. [close] disconnects nothing but this channel, and writes
 * to the socket, so not on the main thread.
 */
class ExecChannel internal constructor(private val session: Session, private val command: Session.Command) : Closeable {

    val stdin: OutputStream get() = command.outputStream
    val stdout: InputStream get() = command.inputStream

    /** What it said on the way out, once [stdout] has ended. */
    fun failure(): String = runCatching { command.errorStream.readBytes().decodeToString() }.getOrDefault("")

    override fun close() {
        runCatching { command.close() }
        runCatching { session.close() }
    }
}

/** [exitStatus] is null when the command ended on a signal rather than an exit. */
class ExecResult(val exitStatus: Int?, val stdout: ByteArray, val stderr: String)

/** The server declined to run a command at all. */
class ExecRefused(cause: Throwable) : Exception("The server does not run commands for this account", cause)

class ExecTimedOut(val timeoutMs: Long) : Exception("A remote command took longer than $timeoutMs ms")
