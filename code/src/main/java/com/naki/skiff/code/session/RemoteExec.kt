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
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.io.IOException
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
    suspend fun run(argv: List<String>, timeoutMs: Long = TIMEOUT_MS): ExecResult {
        val line = ShellQuote.command(argv)
        // Resolved before the connection is touched: fetching it may reach the keystore.
        val secret = password()
        return try {
            attempt(line, secret, timeoutMs)
        } catch (e: IOException) {
            val failure = e.toConnectionError(host)
            if (failure.isFatalAuth()) throw failure
            // Not disconnected here: another command may already be on a fresh client, and a dead
            // one is replaced by connected() anyway.
            try {
                attempt(line, secret, timeoutMs)
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

/** [exitStatus] is null when the command ended on a signal rather than an exit. */
class ExecResult(val exitStatus: Int?, val stdout: ByteArray, val stderr: String)

/** The server declined to run a command at all. */
class ExecRefused(cause: Throwable) : Exception("The server does not run commands for this account", cause)

class ExecTimedOut(val timeoutMs: Long) : Exception("A remote command took longer than $timeoutMs ms")
