package com.naki.skiff.fs.sftp

import com.naki.skiff.fs.FsError
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.HostKeyVerifier

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
) {

    /** Blocking: call it from the thread that owns the connection. */
    fun connect(password: String?): SSHClient {
        val client = SSHClient(DefaultConfig())
        client.addHostKeyVerifier(hostKeyVerifier)
        client.connectTimeout = CONNECT_TIMEOUT_MS
        client.timeout = READ_TIMEOUT_MS
        try {
            client.connect(host, port)
            client.authPassword(username, password ?: throw FsError.AuthFailed())
            // Keeps NAT tables and idle-timeout servers from silently dropping us.
            client.connection.keepAlive.keepAliveInterval = KEEPALIVE_SECONDS
            return client
        } catch (e: Throwable) {
            runCatching { client.close() }
            throw e
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
        const val KEEPALIVE_SECONDS = 30
    }
}
