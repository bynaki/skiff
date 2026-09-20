package com.naki.skiff.fs.sftp

import com.naki.skiff.fs.FsError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.sftp.SFTPException
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.userauth.UserAuthException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.concurrent.Executors

/**
 * One SSH connection and its SFTP session.
 *
 * sshj's SFTPClient is not thread safe, so every call is pinned to a single-threaded
 * dispatcher owned by this connection. A profile gets two of these — one for browsing and
 * one for transfers — so a multi-gigabyte upload never blocks a directory listing.
 */
class SshConnection(
    private val host: String,
    private val port: Int,
    private val username: String,
    /**
     * Supplies the password at connect time. Passing a supplier rather than the profile
     * keeps this class free of the Android Keystore, which is also what lets it be tested
     * against a real SSH server on a plain JVM.
     */
    private val password: suspend () -> String?,
    private val startPathRequest: String,
    private val hostKeyVerifier: HostKeyVerifier,
    private val label: String,
) {

    private val clients = SshClientFactory(host, port, username, hostKeyVerifier)

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "skiff-ssh-$host-$label").apply { isDaemon = true }
    }
    private val dispatcher: CoroutineDispatcher = executor.asCoroutineDispatcher()
    private val mutex = Mutex()

    private var client: SSHClient? = null
    private var sftp: SFTPClient? = null

    /**
     * Runs [block] against a live SFTP session, connecting on first use. A dropped
     * connection is reconnected once and the block retried — a phone changing networks
     * should not turn into an error toast.
     */
    suspend fun <T> withSftp(block: (SFTPClient) -> T): T {
        // Resolved off the connection thread: fetching it may touch the keystore or disk.
        val secret = password()
        return withContext(dispatcher) {
            mutex.withLock {
            try {
                block(ensureConnected(secret))
            } catch (e: SFTPException) {
                // A status reply ("no such file", "already exists", "permission denied") is
                // the server answering, not the link dying. Reconnecting here would both
                // churn the session on every ordinary error and mask it as "connection lost".
                throw e
            } catch (e: IOException) {
                // Translate before deciding. A rejected host key reaches us as a plain
                // TransportException, so a check against the raw exception never matches it and
                // the retry below asks the user the same question a second time.
                val failure = e.toFsError()
                if (isFatalAuth(failure)) throw failure
                disconnectQuietly()
                try {
                    block(ensureConnected(secret))
                } catch (retry: IOException) {
                    throw retry.toFsError()
                }
            }
            }
        }
    }

    private fun ensureConnected(secret: String?): SFTPClient {
        sftp?.let { existing ->
            if (client?.isConnected == true) return existing
        }
        disconnectQuietly()

        val fresh = clients.connect(secret)
        try {
            val session = fresh.newSFTPClient()
            client = fresh
            sftp = session
            return session
        } catch (e: Throwable) {
            runCatching { fresh.close() }
            throw e
        }
    }

    /** Resolves the profile's start directory, expanding "." to the login directory. */
    suspend fun resolveStartPath(): String = withSftp { sftp ->
        val requested = startPathRequest.ifBlank { "." }
        runCatching { sftp.canonicalize(requested) }
            .recoverCatching { sftp.canonicalize(".") }
            .getOrDefault("/")
    }

    fun close() {
        runCatching { disconnectQuietly() }
        executor.shutdownNow()
    }

    private fun disconnectQuietly() {
        runCatching { sftp?.close() }
        runCatching { client?.disconnect() }
        sftp = null
        client = null
    }

    /** Asking again cannot change either answer, so neither is worth a reconnect. */
    private fun isFatalAuth(e: Throwable): Boolean =
        e is FsError.AuthFailed || e is FsError.HostKeyRejected

    private fun IOException.toFsError(): Throwable = when {
        this is UserAuthException -> FsError.AuthFailed(this)
        // A connect timeout is not a lost connection: the server was never reached.
        this is SocketTimeoutException || this is ConnectException -> FsError.Unreachable(host, this)
        message?.contains("host key", ignoreCase = true) == true ->
            FsError.HostKeyRejected(message ?: "")
        else -> FsError.NetworkLost(this)
    }
}
