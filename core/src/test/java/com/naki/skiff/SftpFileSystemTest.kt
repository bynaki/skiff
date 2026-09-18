package com.naki.skiff

import com.naki.skiff.fs.FsError
import com.naki.skiff.fs.SourceId
import com.naki.skiff.fs.sftp.SftpFileSystem
import com.naki.skiff.fs.sftp.SshConnection
import kotlinx.coroutines.test.runTest
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import okio.buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.PublicKey

/** Drives the production SFTP code against a live SFTP server over a real socket. */
class SftpFileSystemTest {

    private val server = SftpTestServer()
    private lateinit var fs: SftpFileSystem

    /** The test server generates a throwaway host key each run, so pin nothing. */
    private val acceptAnyKey = object : HostKeyVerifier {
        override fun verify(hostname: String, port: Int, key: PublicKey) = true
        override fun findExistingAlgorithms(hostname: String, port: Int) = emptyList<String>()
    }

    private fun connection(label: String) = SshConnection(
        host = "127.0.0.1",
        port = server.port,
        username = server.username,
        password = { server.password },
        startPathRequest = ".",
        hostKeyVerifier = acceptAnyKey,
        label = label,
    )

    @Before
    fun setUp() {
        server.start()
        fs = SftpFileSystem(
            id = SourceId.Remote("test"),
            displayName = "test",
            browseConnection = connection("browse"),
            transferConnection = connection("transfer"),
        )
    }

    @After
    fun tearDown() {
        fs.close()
        server.stop()
    }

    @Test
    fun `lists a directory with sizes and types`() = runTest {
        server.writeFile("data/hello.txt", "hi".toByteArray())
        server.makeDirectory("data/sub")

        val entries = fs.list("/data").associateBy { it.name }

        assertEquals(setOf("hello.txt", "sub"), entries.keys)
        assertEquals(2L, entries.getValue("hello.txt").size)
        assertFalse(entries.getValue("hello.txt").isDirectory)
        assertTrue(entries.getValue("sub").isDirectory)
    }

    @Test
    fun `stat returns null for a missing path instead of throwing`() = runTest {
        assertNull(fs.stat("/nope.txt"))
    }

    @Test
    fun `reports permission bits`() = runTest {
        server.writeFile("f.txt", "x".toByteArray())
        java.nio.file.Files.setPosixFilePermissions(
            server.root.resolve("f.txt"),
            java.nio.file.attribute.PosixFilePermissions.fromString("rw-r-----"),
        )

        assertEquals(0b110_100_000, fs.stat("/f.txt")?.mode)
    }

    @Test
    fun `creates and removes directories`() = runTest {
        fs.mkdir("/made")
        assertTrue(server.exists("made"))

        fs.delete("/made", recursive = false)
        assertFalse(server.exists("made"))
    }

    @Test
    fun `createFile refuses to clobber an existing file`() = runTest {
        server.writeFile("taken.txt", "original".toByteArray())

        val failure = runCatching { fs.createFile("/taken.txt") }.exceptionOrNull()

        assertTrue("expected AlreadyExists, got $failure", failure is FsError.AlreadyExists)
        assertArrayEquals("original".toByteArray(), server.readFile("taken.txt"))
    }

    @Test
    fun `recursive delete removes a whole tree client-side`() = runTest {
        // No `rm -rf` on the server: this walks the tree over SFTP alone.
        server.writeFile("tree/a/b/deep.txt", "x".toByteArray())
        server.writeFile("tree/top.txt", "y".toByteArray())

        fs.delete("/tree", recursive = true)

        assertFalse(server.exists("tree"))
    }

    @Test
    fun `non-recursive delete of a populated directory fails`() = runTest {
        server.writeFile("full/inside.txt", "x".toByteArray())

        val failure = runCatching { fs.delete("/full", recursive = false) }.exceptionOrNull()

        assertTrue("expected a failure, got null", failure != null)
        assertTrue(server.exists("full/inside.txt"))
    }

    @Test
    fun `rename moves within the server`() = runTest {
        server.writeFile("before.txt", "same".toByteArray())

        fs.rename("/before.txt", "/after.txt")

        assertFalse(server.exists("before.txt"))
        assertArrayEquals("same".toByteArray(), server.readFile("after.txt"))
    }

    @Test
    fun `writes then reads back a file larger than one packet`() = runTest {
        val payload = ByteArray(300_000) { (it % 251).toByte() }

        fs.openWrite("/big.bin", append = false).buffer().use { it.write(payload) }

        assertArrayEquals(payload, server.readFile("big.bin"))
        val readBack = fs.openRead("/big.bin").buffer().use { it.readByteArray() }
        assertArrayEquals(payload, readBack)
    }

    @Test
    fun `openWrite truncates rather than overwriting in place`() = runTest {
        server.writeFile("f.txt", "a-much-longer-original".toByteArray())

        fs.openWrite("/f.txt", append = false).buffer().use { it.write("short".toByteArray()) }

        assertArrayEquals("short".toByteArray(), server.readFile("f.txt"))
    }

    @Test
    fun `append adds to the end`() = runTest {
        server.writeFile("f.txt", "one".toByteArray())

        fs.openWrite("/f.txt", append = true).buffer().use { it.write("two".toByteArray()) }

        assertArrayEquals("onetwo".toByteArray(), server.readFile("f.txt"))
    }

    @Test
    fun `missing file reads as NotFound`() = runTest {
        val failure = runCatching { fs.openRead("/absent.bin") }.exceptionOrNull()
        assertTrue("expected NotFound, got $failure", failure is FsError.NotFound)
    }

    @Test
    fun `handles names with spaces and hangul`() = runTest {
        val name = "내 문서 (사본).txt"
        server.writeFile("dir/$name", "한글".toByteArray())

        val entry = fs.list("/dir").single()
        assertEquals(name, entry.name)

        val content = fs.openRead(entry.path).buffer().use { it.readByteArray() }
        assertArrayEquals("한글".toByteArray(), content)
    }

    @Test
    fun `wrong password surfaces as AuthFailed`() = runTest {
        val bad = SftpFileSystem(
            id = SourceId.Remote("bad"),
            displayName = "bad",
            browseConnection = SshConnection(
                host = "127.0.0.1",
                port = server.port,
                username = server.username,
                password = { "wrong" },
                startPathRequest = ".",
                hostKeyVerifier = acceptAnyKey,
                label = "browse",
            ),
            transferConnection = connection("transfer"),
        )
        try {
            val failure = runCatching { bad.list("/") }.exceptionOrNull()
            assertTrue("expected AuthFailed, got $failure", failure is FsError.AuthFailed)
        } finally {
            bad.close()
        }
    }

    @Test
    fun `a refused password is not tried again over keyboard-interactive`() = runTest {
        // macOS's sshd stops answering when keyboard-interactive follows a failed password
        // attempt on the same connection, which left a wrong password waiting out the 30 second
        // read timeout. sshj's authPassword makes exactly that second attempt.
        val checksBefore = server.passwordChecks.get()
        val bad = SftpFileSystem(
            id = SourceId.Remote("bad"),
            displayName = "bad",
            browseConnection = SshConnection(
                host = "127.0.0.1",
                port = server.port,
                username = server.username,
                password = { "wrong" },
                startPathRequest = ".",
                hostKeyVerifier = acceptAnyKey,
                label = "browse",
            ),
            transferConnection = connection("transfer"),
        )
        try {
            val failure = runCatching { bad.list("/") }.exceptionOrNull()
            assertTrue("expected AuthFailed, got $failure", failure is FsError.AuthFailed)
            assertEquals(1, server.passwordChecks.get() - checksBefore)
        } finally {
            bad.close()
        }
    }

    @Test
    fun `a server that only offers keyboard-interactive still takes the password`() = runTest {
        val kbd = SftpTestServer(keyboardInteractiveOnly = true).apply { start() }
        val fs = SftpFileSystem(
            id = SourceId.Remote("kbd"),
            displayName = "kbd",
            browseConnection = SshConnection(
                host = "127.0.0.1",
                port = kbd.port,
                username = kbd.username,
                password = { kbd.password },
                startPathRequest = ".",
                hostKeyVerifier = acceptAnyKey,
                label = "browse",
            ),
            transferConnection = connection("transfer"),
        )
        try {
            kbd.writeFile("a.txt", "hi".toByteArray())
            assertEquals(listOf("a.txt"), fs.list("/").map { it.name })
        } finally {
            fs.close()
            kbd.stop()
        }
    }

    @Test
    fun `a rejected host key stops the connection`() = runTest {
        val rejecting = object : HostKeyVerifier {
            override fun verify(hostname: String, port: Int, key: PublicKey) = false
            override fun findExistingAlgorithms(hostname: String, port: Int) = emptyList<String>()
        }
        val guarded = SftpFileSystem(
            id = SourceId.Remote("guarded"),
            displayName = "guarded",
            browseConnection = SshConnection(
                host = "127.0.0.1",
                port = server.port,
                username = server.username,
                password = { server.password },
                startPathRequest = ".",
                hostKeyVerifier = rejecting,
                label = "browse",
            ),
            transferConnection = connection("transfer"),
        )
        try {
            assertTrue(runCatching { guarded.list("/") }.isFailure)
        } finally {
            guarded.close()
        }
    }

    @Test
    fun `startPath canonicalizes the login directory`() = runTest {
        assertEquals("/", fs.startPath())
    }
}
