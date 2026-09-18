package com.naki.skiff

import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.keyboard.UserAuthKeyboardInteractiveFactory
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.sftp.server.SftpSubsystemFactory
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

/**
 * A real SSH server with the SFTP subsystem, on a random port, rooted at a temp directory.
 *
 * The point is that SftpFileSystem then talks actual SFTP over an actual socket: protocol
 * mistakes (wrong open flags, mis-read attributes, handles left dangling) show up here,
 * which a hand-written fake of sshj would happily agree with instead.
 */
class SftpTestServer(
    val username: String = "tester",
    // Not a credential: this server is created in-process on a random loopback port and
    // torn down at the end of the test. It authenticates nothing that outlives the JVM.
    val password: String = "s3cret",
    /** Offers keyboard-interactive and not the password method, as some PAM setups do. */
    private val keyboardInteractiveOnly: Boolean = false,
) {
    /** Every password the server has checked, over either method. */
    val passwordChecks = AtomicInteger()

    lateinit var root: Path
        private set

    private lateinit var server: SshServer

    val port: Int get() = server.port

    fun start() {
        root = Files.createTempDirectory("skiff-sftp")
        server = SshServer.setUpDefaultServer().apply {
            host = "127.0.0.1"
            this.port = 0
            keyPairProvider = SimpleGeneratorHostKeyProvider(
                Files.createTempFile("skiff-hostkey", ".ser"),
            )
            // MINA's keyboard-interactive asks this same authenticator, so it counts both methods.
            setPasswordAuthenticator { user, pass, _ ->
                passwordChecks.incrementAndGet()
                user == this@SftpTestServer.username && pass == this@SftpTestServer.password
            }
            if (keyboardInteractiveOnly) userAuthFactories = listOf(UserAuthKeyboardInteractiveFactory.INSTANCE)
            subsystemFactories = listOf(SftpSubsystemFactory())
            fileSystemFactory = VirtualFileSystemFactory(root)
        }
        server.start()
    }

    fun stop() {
        runCatching { server.stop(true) }
        runCatching { root.toFile().deleteRecursively() }
    }

    fun writeFile(relative: String, content: ByteArray) {
        val target = root.resolve(relative)
        Files.createDirectories(target.parent)
        Files.write(target, content)
    }

    fun makeDirectory(relative: String) {
        Files.createDirectories(root.resolve(relative))
    }

    fun readFile(relative: String): ByteArray = Files.readAllBytes(root.resolve(relative))

    fun exists(relative: String): Boolean = Files.exists(root.resolve(relative))
}
