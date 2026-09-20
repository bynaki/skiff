package com.naki.skiff.fs.sftp

import com.naki.skiff.fs.FsError
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.userauth.UserAuthException
import net.schmizz.sshj.userauth.method.AuthKeyboardInteractive
import net.schmizz.sshj.userauth.method.AuthPassword
import net.schmizz.sshj.userauth.method.PasswordResponseProvider
import net.schmizz.sshj.userauth.password.PasswordUtils

/**
 * Connects and authenticates one [SSHClient], and knows nothing about what is then run over
 * it. [SshConnection] opens an SFTP session on the result; Skiff Code's remote commands will
 * open an exec channel on another, and both get the same host key gate, the same timeouts and
 * the same keepalive without a second copy of this.
 *
 * Like [SshConnection], it takes a password rather than reaching for the Android Keystore, so
 * it can be driven from a plain JVM test.
 */
class SshClientFactory(
    private val host: String,
    private val port: Int,
    private val username: String,
    private val hostKeyVerifier: HostKeyVerifier,
    /** Bounds a reply the server owes us once the session is up. */
    private val readTimeoutMs: Int = READ_TIMEOUT_MS,
    /**
     * Bounds the key exchange, which [hostKeyVerifier] blocks while the user answers. It has
     * to outlast someone comparing a fingerprint, so it is far longer than [readTimeoutMs];
     * what it costs is that a server which completes the socket and then says nothing takes
     * this long to give up. Both are parameters so a test can drive the difference in seconds.
     */
    private val kexTimeoutMs: Int = KEX_TIMEOUT_MS,
) {

    /** Blocking: call it from the thread that owns the connection. */
    fun connect(password: String?): SSHClient {
        val client = SSHClient(DefaultConfig())
        client.addHostKeyVerifier(hostKeyVerifier)
        client.connectTimeout = CONNECT_TIMEOUT_MS
        client.timeout = readTimeoutMs
        // The key exchange is bounded by the transport's own timeout, not the socket's, and
        // the host key gate holds the transport thread for as long as the user takes to
        // answer. Left at sshj's 30 second default, the dialog outlives the connection it is
        // asking about: it vanishes and the user is told the connection was lost.
        client.transport.timeoutMs = kexTimeoutMs
        try {
            client.connect(host, port)
            client.transport.timeoutMs = readTimeoutMs
            authenticate(client, password ?: throw FsError.AuthFailed())
            // Keeps NAT tables and idle-timeout servers from silently dropping us.
            client.connection.keepAlive.keepAliveInterval = KEEPALIVE_SECONDS
            return client
        } catch (e: Throwable) {
            runCatching { client.close() }
            throw e
        }
    }

    /**
     * The password method, and keyboard-interactive only for a server that does not offer it.
     *
     * Not sshj's `authPassword`, which follows a refused password with keyboard-interactive on
     * the same connection. macOS's sshd never answers that second attempt, so a mistyped password
     * sat out the whole read timeout before failing; OpenSSH's own client hangs the same way.
     */
    private fun authenticate(client: SSHClient, password: String) {
        try {
            client.auth(username, AuthPassword(PasswordUtils.createOneOff(password.toCharArray())))
        } catch (e: UserAuthException) {
            val allowed = client.userAuth.allowedMethods
            if ("password" in allowed || "keyboard-interactive" !in allowed) throw e
            val answers = PasswordResponseProvider(PasswordUtils.createOneOff(password.toCharArray()))
            client.auth(username, AuthKeyboardInteractive(answers))
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
        const val KEX_TIMEOUT_MS = 300_000
        const val KEEPALIVE_SECONDS = 30
    }
}
