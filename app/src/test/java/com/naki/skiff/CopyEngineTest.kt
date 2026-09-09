package com.naki.skiff

import com.naki.skiff.fs.SourceId
import com.naki.skiff.transfer.ConflictPolicy
import com.naki.skiff.transfer.CopyEngine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CopyEngineTest {

    private val engine = CopyEngine()

    private fun source() = FakeFileSystem(SourceId.Local, "src")
    private fun destination() = FakeFileSystem(SourceId.Remote("server"), "dst")

    private suspend fun run(
        from: FakeFileSystem,
        to: FakeFileSystem,
        paths: List<String>,
        destinationDir: String,
        policy: ConflictPolicy = ConflictPolicy.KEEP_BOTH,
    ) {
        val plan = engine.plan(from, paths, to, destinationDir, policy)
        engine.execute(plan, from, to, policy) { _, _, _ -> }
    }

    @Test
    fun `copies a single file across filesystems`() = runTest {
        val from = source().apply { putFile("/a/hello.txt", "hi".toByteArray()) }
        val to = destination().apply { putDirectory("/target") }

        run(from, to, listOf("/a/hello.txt"), "/target")

        assertArrayEquals("hi".toByteArray(), to.fileContent("/target/hello.txt"))
        // A copy leaves the source alone.
        assertArrayEquals("hi".toByteArray(), from.fileContent("/a/hello.txt"))
    }

    @Test
    fun `recreates a nested directory tree`() = runTest {
        val from = source().apply {
            putFile("/a/top.txt", "1".toByteArray())
            putFile("/a/deep/inner.txt", "2".toByteArray())
            putFile("/a/deep/deeper/leaf.txt", "3".toByteArray())
            putDirectory("/a/empty")
        }
        val to = destination().apply { putDirectory("/target") }

        run(from, to, listOf("/a"), "/target")

        assertArrayEquals("1".toByteArray(), to.fileContent("/target/a/top.txt"))
        assertArrayEquals("2".toByteArray(), to.fileContent("/target/a/deep/inner.txt"))
        assertArrayEquals("3".toByteArray(), to.fileContent("/target/a/deep/deeper/leaf.txt"))
        // Empty directories are structure too, and must survive the copy.
        assertTrue("/target/a/empty" in to.paths())
    }

    @Test
    fun `plan reports the total byte count up front`() = runTest {
        val from = source().apply {
            putFile("/a/one", ByteArray(100))
            putFile("/a/two", ByteArray(150))
        }
        val to = destination()

        val plan = engine.plan(from, listOf("/a"), to, "/", ConflictPolicy.KEEP_BOTH)

        assertEquals(250L, plan.totalBytes)
        assertEquals(2, plan.fileCount)
    }

    @Test
    fun `keep both renames instead of clobbering`() = runTest {
        val from = source().apply { putFile("/notes.md", "new".toByteArray()) }
        val to = destination().apply { putFile("/notes.md", "old".toByteArray()) }

        run(from, to, listOf("/notes.md"), "/", ConflictPolicy.KEEP_BOTH)

        assertArrayEquals("old".toByteArray(), to.fileContent("/notes.md"))
        assertArrayEquals("new".toByteArray(), to.fileContent("/notes (1).md"))
    }

    @Test
    fun `overwrite replaces the existing file`() = runTest {
        val from = source().apply { putFile("/notes.md", "new".toByteArray()) }
        val to = destination().apply { putFile("/notes.md", "old".toByteArray()) }

        run(from, to, listOf("/notes.md"), "/", ConflictPolicy.OVERWRITE)

        assertArrayEquals("new".toByteArray(), to.fileContent("/notes.md"))
    }

    @Test
    fun `skip leaves the existing file alone`() = runTest {
        val from = source().apply { putFile("/notes.md", "new".toByteArray()) }
        val to = destination().apply { putFile("/notes.md", "old".toByteArray()) }

        run(from, to, listOf("/notes.md"), "/", ConflictPolicy.SKIP)

        assertArrayEquals("old".toByteArray(), to.fileContent("/notes.md"))
    }

    @Test
    fun `refuses to copy a directory into itself`() = runTest {
        val from = source().apply { putFile("/a/b/file.txt", "x".toByteArray()) }

        val failure = runCatching {
            engine.plan(from, listOf("/a"), from, "/a/b", ConflictPolicy.OVERWRITE)
        }.exceptionOrNull()

        assertTrue("expected a refusal, got $failure", failure != null)
    }

    @Test
    fun `progress reaches the planned total`() = runTest {
        val from = source().apply {
            putFile("/a/one", ByteArray(1000))
            putFile("/a/two", ByteArray(2000))
        }
        val to = destination()

        val plan = engine.plan(from, listOf("/a"), to, "/", ConflictPolicy.KEEP_BOTH)
        var lastBytes = 0L
        var lastFiles = 0
        engine.execute(plan, from, to, ConflictPolicy.KEEP_BOTH) { bytes, files, _ ->
            lastBytes = bytes
            lastFiles = files
        }

        assertEquals(plan.totalBytes, lastBytes)
        assertEquals(plan.fileCount, lastFiles)
    }

    @Test
    fun `copies larger than the buffer stream through intact`() = runTest {
        val payload = ByteArray(200_000) { (it % 251).toByte() }
        val from = source().apply { putFile("/big.bin", payload) }
        val to = destination()

        run(from, to, listOf("/big.bin"), "/")

        assertArrayEquals(payload, to.fileContent("/big.bin"))
    }

    @Test
    fun `missing source is reported rather than silently skipped`() = runTest {
        val from = source()
        val to = destination()

        val failure = runCatching {
            engine.plan(from, listOf("/nope.txt"), to, "/", ConflictPolicy.OVERWRITE)
        }.exceptionOrNull()

        assertTrue(failure is com.naki.skiff.fs.FsError.NotFound)
        assertNull(to.fileContent("/nope.txt"))
    }
}
