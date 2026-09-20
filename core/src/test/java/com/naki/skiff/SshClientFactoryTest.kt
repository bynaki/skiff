package com.naki.skiff

import com.naki.skiff.fs.sftp.SshClientFactory
import net.schmizz.sshj.transport.TransportException
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.PublicKey

/** Drives the connect path against a live SSH server on a random loopback port. */
class SshClientFactoryTest {

    private val server = SftpTestServer()

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.stop()

    /** Stands in for the host key dialog: it holds the transport thread, then accepts. */
    private fun slowVerifier(pauseMs: Long) = object : HostKeyVerifier {
        override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
            Thread.sleep(pauseMs)
            return true
        }

        override fun findExistingAlgorithms(hostname: String, port: Int) = emptyList<String>()
    }

    private fun factory(verifier: HostKeyVerifier, readTimeoutMs: Int, kexTimeoutMs: Int) =
        SshClientFactory(
            host = "127.0.0.1",
            port = server.port,
            username = server.username,
            hostKeyVerifier = verifier,
            readTimeoutMs = readTimeoutMs,
            kexTimeoutMs = kexTimeoutMs,
        )

    @Test
    fun `a host key answer may take longer than the read timeout`() {
        val client = factory(slowVerifier(1_500), readTimeoutMs = 500, kexTimeoutMs = 20_000)
            .connect(server.password)

        try {
            assertTrue(client.isAuthenticated)
        } finally {
            client.close()
        }
    }

    @Test
    fun `the key exchange still gives up on its own timeout`() {
        val failure = runCatching {
            factory(slowVerifier(1_500), readTimeoutMs = 500, kexTimeoutMs = 500)
                .connect(server.password)
        }.exceptionOrNull()

        assertTrue("expected a transport timeout, got $failure", failure is TransportException)
    }
}
