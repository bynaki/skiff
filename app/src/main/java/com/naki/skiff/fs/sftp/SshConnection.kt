package com.naki.skiff.fs.sftp

import com.naki.skiff.data.store.AuthMethod
import com.naki.skiff.data.store.ServerProfile
import com.naki.skiff.data.crypto.SecretStore
import com.naki.skiff.fs.FsError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.userauth.UserAuthException
import java.io.IOException
import java.util.concurrent.Executors

/**
 * One SSH connection and its SFTP session.
 *
 * sshj's SFTPClient is not thread safe, so every call is pinned to a single-threaded
 * dispatcher owned by this connection. A profile gets two of these — one for browsing and
 * one for transfers — so a multi-gigabyte upload never blocks a directory listing.
 */
class SshConnection(
    private val profile: ServerProfile,
    private val hostKeyVerifier: HostKeyVerifier,
    private val label: String,
) {

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "skiff-ssh-${profile.name}-$label").apply { isDaemon = true }
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
    suspend fun <T> withSftp(block: (SFTPClient) -> T): T = withContext(dispatcher) {
        mutex.withLock {
            try {
                block(ensureConnected())
            } catch (e: IOException) {
                if (isFatalAuth(e)) throw e.toFsError()
                disconnectQuietly()
                try {
                    block(ensureConnected())
                } catch (retry: IOException) {
                    throw retry.toFsError()
                }
            }
        }
    }

    private fun ensureConnected(): SFTPClient {
        sftp?.let { existing ->
            if (client?.isConnected == true) return existing
        }
        disconnectQuietly()

        val fresh = SSHClient(DefaultConfig())
        fresh.addHostKeyVerifier(hostKeyVerifier)
        fresh.connectTimeout = CONNECT_TIMEOUT_MS
        fresh.timeout = READ_TIMEOUT_MS
        try {
            fresh.connect(profile.host, profile.port)
            authenticate(fresh)
            // Keeps NAT tables and idle-timeout servers from silently dropping us.
            fresh.connection.keepAlive.keepAliveInterval = KEEPALIVE_SECONDS
            val session = fresh.newSFTPClient()
            client = fresh
            sftp = session
            return session
        } catch (e: Throwable) {
            runCatching { fresh.close() }
            throw e
        }
    }

    private fun authenticate(client: SSHClient) {
        when (val auth = profile.auth) {
            is AuthMethod.Password -> {
                val password = auth.encryptedPassword?.let(SecretStore::decrypt)
                    ?: throw FsError.AuthFailed()
                client.authPassword(profile.username, password)
            }
            // Key and keyboard-interactive auth are the next extension points.
            else -> throw FsError.AuthFailed()
        }
    }

    /** Resolves the profile's start directory, expanding "." to the login directory. */
    suspend fun resolveStartPath(): String = withSftp { sftp ->
        val requested = profile.startPath.ifBlank { "." }
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

    private fun isFatalAuth(e: Throwable): Boolean =
        e is UserAuthException || e is FsError.AuthFailed || e is FsError.HostKeyRejected

    private fun IOException.toFsError(): Throwable = when {
        this is UserAuthException -> FsError.AuthFailed(this)
        message?.contains("host key", ignoreCase = true) == true ->
            FsError.HostKeyRejected(message ?: "")
        else -> FsError.NetworkLost(this)
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
        const val KEEPALIVE_SECONDS = 30
    }
}
