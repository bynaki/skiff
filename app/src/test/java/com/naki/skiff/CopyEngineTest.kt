package com.naki.skiff

import com.naki.skiff.fs.SourceId
import com.naki.skiff.transfer.ConflictAnswer
import com.naki.skiff.transfer.ConflictPolicy
import com.naki.skiff.transfer.CopyEngine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `a symlink to a file is copied by content, not dropped`() = runTest {
        // Regression: symlinks used to be skipped by the walk. Combined with move, which
        // deletes the source tree afterwards, that silently destroyed them.
        val from = source().apply {
            putFile("/a/real.txt", "payload".toByteArray())
            putSymlink("/a/link.txt", "/a/real.txt")
        }
        val to = destination()

        run(from, to, listOf("/a"), "/target")

        assertArrayEquals("payload".toByteArray(), to.fileContent("/target/a/real.txt"))
        assertArrayEquals("payload".toByteArray(), to.fileContent("/target/a/link.txt"))
    }

    @Test
    fun `a symlink to a directory is followed`() = runTest {
        val from = source().apply {
            putFile("/other/inside.txt", "x".toByteArray())
            putDirectory("/a")
            putSymlink("/a/shortcut", "/other")
        }
        val to = destination()

        run(from, to, listOf("/a"), "/target")

        assertArrayEquals("x".toByteArray(), to.fileContent("/target/a/shortcut/inside.txt"))
    }

    @Test
    fun `a symlink cycle terminates instead of recursing forever`() = runTest {
        val from = source().apply {
            putFile("/a/file.txt", "x".toByteArray())
            putSymlink("/a/loop", "/a")
        }
        val to = destination()

        run(from, to, listOf("/a"), "/target")

        // The visited set stops the descent; the real contents still make it across.
        assertArrayEquals("x".toByteArray(), to.fileContent("/target/a/file.txt"))
    }

    @Test
    fun `plan counts symlinked files in the total`() = runTest {
        val from = source().apply {
            putFile("/a/real.bin", ByteArray(500))
            putSymlink("/a/alias.bin", "/a/real.bin")
        }
        val to = destination()

        val plan = engine.plan(from, listOf("/a"), to, "/", ConflictPolicy.KEEP_BOTH)

        assertEquals(2, plan.fileCount)
        assertEquals(1000L, plan.totalBytes)
    }

    @Test
    fun `ask puts each taken name to the user and follows each answer`() = runTest {
        val from = source().apply {
            putFile("/a.txt", "new a".toByteArray())
            putFile("/b.txt", "new b".toByteArray())
            putFile("/free.txt", "free".toByteArray())
        }
        val to = destination().apply {
            putFile("/a.txt", "old a".toByteArray())
            putFile("/b.txt", "old b".toByteArray())
        }
        val answers = mapOf(
            "/a.txt" to ConflictAnswer(ConflictPolicy.OVERWRITE, applyToRest = false),
            "/b.txt" to ConflictAnswer(ConflictPolicy.SKIP, applyToRest = false),
        )
        val asked = mutableListOf<String>()

        val plan = engine.plan(from, listOf("/a.txt", "/b.txt", "/free.txt"), to, "/", ConflictPolicy.ASK)
        val skipped = engine.execute(
            plan, from, to, ConflictPolicy.ASK,
            onConflict = { asked += it.to; answers.getValue(it.to) },
        ) { _, _, _ -> }

        // A free name is never asked about.
        assertEquals(listOf("/a.txt", "/b.txt"), asked)
        assertArrayEquals("new a".toByteArray(), to.fileContent("/a.txt"))
        assertArrayEquals("old b".toByteArray(), to.fileContent("/b.txt"))
        assertArrayEquals("free".toByteArray(), to.fileContent("/free.txt"))
        assertEquals(setOf("/b.txt"), skipped)
    }

    @Test
    fun `an answer applied to the rest is not asked again`() = runTest {
        val from = source().apply {
            putFile("/one.txt", "new".toByteArray())
            putFile("/two.txt", "new".toByteArray())
            putFile("/three.txt", "new".toByteArray())
        }
        val to = destination().apply {
            putFile("/one.txt", "old".toByteArray())
            putFile("/two.txt", "old".toByteArray())
            putFile("/three.txt", "old".toByteArray())
        }
        var asked = 0

        val plan = engine.plan(from, listOf("/one.txt", "/two.txt", "/three.txt"), to, "/", ConflictPolicy.ASK)
        engine.execute(
            plan, from, to, ConflictPolicy.ASK,
            onConflict = { asked++; ConflictAnswer(ConflictPolicy.KEEP_BOTH, applyToRest = true) },
        ) { _, _, _ -> }

        assertEquals(1, asked)
        for (name in listOf("one", "two", "three")) {
            assertArrayEquals("old".toByteArray(), to.fileContent("/$name.txt"))
            assertArrayEquals("new".toByteArray(), to.fileContent("/$name (1).txt"))
        }
    }

    @Test
    fun `a folder whose name is taken is merged, and only its taken files are asked about`() = runTest {
        val from = source().apply {
            putFile("/a/taken.txt", "new".toByteArray())
            putFile("/a/free.txt", "free".toByteArray())
        }
        val to = destination().apply {
            putFile("/target/a/taken.txt", "old".toByteArray())
            putFile("/target/a/theirs.txt", "theirs".toByteArray())
        }
        val asked = mutableListOf<String>()

        val plan = engine.plan(from, listOf("/a"), to, "/target", ConflictPolicy.ASK)
        engine.execute(
            plan, from, to, ConflictPolicy.ASK,
            onConflict = { asked += it.to; ConflictAnswer(ConflictPolicy.OVERWRITE, applyToRest = false) },
        ) { _, _, _ -> }

        assertEquals(listOf("/target/a/taken.txt"), asked)
        assertArrayEquals("new".toByteArray(), to.fileContent("/target/a/taken.txt"))
        assertArrayEquals("free".toByteArray(), to.fileContent("/target/a/free.txt"))
        assertArrayEquals("theirs".toByteArray(), to.fileContent("/target/a/theirs.txt"))
    }

    @Test
    fun `a move keeps a skipped file and the folders above it, and removes the rest`() = runTest {
        val from = source().apply {
            putFile("/a/kept.txt", "k".toByteArray())
            putFile("/a/moved.txt", "m".toByteArray())
            putFile("/a/sub/moved.txt", "m".toByteArray())
            putFile("/lone.txt", "l".toByteArray())
        }

        engine.removeSources(from, listOf("/a", "/lone.txt"), skipped = setOf("/a/kept.txt"))

        assertEquals(setOf("/", "/a", "/a/kept.txt"), from.paths())
    }

    @Test
    fun `a move does not empty the target of a link that holds a skipped file`() = runTest {
        val from = source().apply {
            putFile("/other/inside.txt", "x".toByteArray())
            putFile("/a/moved.txt", "m".toByteArray())
            putSymlink("/a/shortcut", "/other")
        }

        engine.removeSources(from, listOf("/a"), skipped = setOf("/a/shortcut/inside.txt"))

        assertArrayEquals("x".toByteArray(), from.fileContent("/other/inside.txt"))
        assertTrue("/a/shortcut" in from.paths())
        assertFalse("/a/moved.txt" in from.paths())
    }

    @Test
    fun `plan refuses a transfer larger than the free space before anything moves`() = runTest {
        val from = source().apply { putFile("/a/big.bin", ByteArray(1000)) }
        val to = destination().apply { freeBytes = 999 }

        val failure = runCatching {
            engine.plan(from, listOf("/a"), to, "/", ConflictPolicy.KEEP_BOTH)
        }.exceptionOrNull()

        assertTrue("expected NoSpace, got $failure", failure is com.naki.skiff.fs.FsError.NoSpace)
        assertEquals(setOf("/"), to.paths())
    }

    @Test
    fun `a destination that cannot report free space is not checked`() = runTest {
        val from = source().apply { putFile("/big.bin", ByteArray(1000)) }
        val to = destination().apply { freeBytes = null }

        run(from, to, listOf("/big.bin"), "/")

        assertEquals(1000, to.fileContent("/big.bin")?.size)
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
