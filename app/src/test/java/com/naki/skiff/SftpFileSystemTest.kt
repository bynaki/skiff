package com.naki.skiff

import com.naki.skiff.fs.FsError
import com.naki.skiff.fs.SourceId
import com.naki.skiff.fs.sftp.SftpFileSystem
import com.naki.skiff.fs.sftp.SshConnection
import com.naki.skiff.transfer.ConflictPolicy
import com.naki.skiff.transfer.CopyEngine
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
    fun `copies a tree from the device to the server`() = runTest {
        val local = FakeFileSystem(SourceId.Local, "device")
        local.putFile("/src/notes.md", "notes".toByteArray())
        local.putFile("/src/deep/inner/leaf.bin", ByteArray(100_000) { it.toByte() })
        server.makeDirectory("upload")

        val engine = CopyEngine()
        val plan = engine.plan(local, listOf("/src"), fs, "/upload", ConflictPolicy.KEEP_BOTH)
        engine.execute(plan, local, fs, ConflictPolicy.KEEP_BOTH) { _, _, _ -> }

        assertArrayEquals("notes".toByteArray(), server.readFile("upload/src/notes.md"))
        assertEquals(100_000, server.readFile("upload/src/deep/inner/leaf.bin").size)
        assertEquals(100_005L, plan.totalBytes)
    }

    @Test
    fun `downloads a tree from the server to the device`() = runTest {
        val local = FakeFileSystem(SourceId.Local, "device")
        local.putDirectory("/downloads")
        server.writeFile("remote/readme.txt", "from server".toByteArray())
        server.writeFile("remote/nested/leaf.txt", "leaf".toByteArray())

        val engine = CopyEngine()
        val plan = engine.plan(fs, listOf("/remote"), local, "/downloads", ConflictPolicy.KEEP_BOTH)
        engine.execute(plan, fs, local, ConflictPolicy.KEEP_BOTH) { _, _, _ -> }

        assertArrayEquals(
            "from server".toByteArray(),
            local.fileContent("/downloads/remote/readme.txt"),
        )
        assertArrayEquals(
            "leaf".toByteArray(),
            local.fileContent("/downloads/remote/nested/leaf.txt"),
        )
    }

    @Test
    fun `progress reaches the planned total on a real upload`() = runTest {
        val local = FakeFileSystem(SourceId.Local, "device")
        local.putFile("/a/one.bin", ByteArray(50_000))
        local.putFile("/a/two.bin", ByteArray(70_000))

        val engine = CopyEngine()
        val plan = engine.plan(local, listOf("/a"), fs, "/", ConflictPolicy.KEEP_BOTH)
        var seen = 0L
        engine.execute(plan, local, fs, ConflictPolicy.KEEP_BOTH) { bytes, _, _ -> seen = bytes }

        assertEquals(120_000L, plan.totalBytes)
        assertEquals(plan.totalBytes, seen)
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
