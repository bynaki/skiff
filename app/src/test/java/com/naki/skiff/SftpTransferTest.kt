package com.naki.skiff

import com.naki.skiff.fs.SourceId
import com.naki.skiff.fs.sftp.SftpFileSystem
import com.naki.skiff.fs.sftp.SshConnection
import com.naki.skiff.transfer.ConflictPolicy
import com.naki.skiff.transfer.CopyEngine
import kotlinx.coroutines.test.runTest
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.security.PublicKey

/**
 * CopyEngine across a real SFTP server, rather than between two in-memory filesystems.
 * The device side stays a FakeFileSystem so the test does not need storage permissions;
 * what it proves is that the engine's plan-and-execute survives a real transport.
 */
class SftpTransferTest {

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
}
