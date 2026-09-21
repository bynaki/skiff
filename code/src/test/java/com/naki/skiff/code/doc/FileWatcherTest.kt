package com.naki.skiff.code.doc

import com.naki.skiff.SftpTestServer
import com.naki.skiff.fs.SourceId
import com.naki.skiff.fs.sftp.SftpFileSystem
import com.naki.skiff.fs.sftp.SshConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.nio.file.Files
import java.security.PublicKey

/**
 * Watching through the production SFTP code against a real server, as the app does.
 *
 * These run on real time rather than `runTest`'s virtual clock: the loop's tick and the server's
 * answer are two different clocks, and skipping one of them would only mean testing a loop that
 * nothing is racing. The interval is short so the tests are not.
 */
class FileWatcherTest {

    private val server = SftpTestServer()
    private val loader = TextLoader()
    private lateinit var fs: SftpFileSystem

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
        fs = SftpFileSystem(SourceId.Remote("test"), "test", connection("browse"), connection("transfer"))
    }

    @After
    fun tearDown() {
        fs.close()
        server.stop()
    }

    private suspend fun load(path: String) = loader.load(fs, path) as LoadResult.Text

    private fun watcherFor(path: String, from: LoadResult.Text) = FileWatcher(
        WatchedPath(fs, path, loader),
        Stamped.At(Stamp(from.size, from.modifiedEpochSeconds)),
        pollMillis = POLL,
    )

    /** Runs the loop for the length of [block], reporting everything it sees down the channel. */
    private fun watching(watcher: FileWatcher, block: suspend CoroutineScope.(Channel<FileChange>) -> Unit) = runBlocking {
        val changes = Channel<FileChange>(Channel.UNLIMITED)
        val job = launch(Dispatchers.Default) { watcher.watch { changes.send(it) } }
        try {
            block(changes)
        } finally {
            job.cancelAndJoin()
        }
    }

    private suspend fun Channel<FileChange>.next(): FileChange? = withTimeoutOrNull(WAIT) { receive() }

    private suspend fun Channel<FileChange>.nextWithin(millis: Long): FileChange? =
        withTimeoutOrNull(millis) { receive() }

    private fun textOf(change: FileChange?): String {
        assertTrue("expected a change with text, got $change", change is FileChange.Changed)
        val result = (change as FileChange.Changed).result
        assertTrue("expected text, got $result", result is LoadResult.Text)
        return (result as LoadResult.Text).text
    }

    @Test
    fun `a change made on the server is reported with the new text`() {
        runBlocking { server.writeFile("note.md", "한 줄\n".toByteArray()) }
        val opened = runBlocking { load("/note.md") }

        watching(watcherFor("/note.md", opened)) { changes ->
            server.writeFile("note.md", "한 줄\n두 줄\n".toByteArray())
            assertEquals("한 줄\n두 줄\n", textOf(changes.next()))
        }
    }

    @Test
    fun `a file nobody touches is never reported`() {
        server.writeFile("still.txt", "unchanged\n".toByteArray())
        val opened = runBlocking { load("/still.txt") }

        watching(watcherFor("/still.txt", opened)) { changes ->
            assertNull("a file that did not move was reported as changed", changes.nextWithin(QUIET))
        }
    }

    @Test
    fun `a deleted file is reported once, and is reported again when it comes back`() {
        server.writeFile("gone.txt", "here\n".toByteArray())
        val opened = runBlocking { load("/gone.txt") }

        watching(watcherFor("/gone.txt", opened)) { changes ->
            Files.delete(server.root.resolve("gone.txt"))
            assertEquals(FileChange.Gone, changes.next())
            assertNull("the same deletion was reported twice", changes.nextWithin(QUIET))

            server.writeFile("gone.txt", "back again\n".toByteArray())
            assertEquals("back again\n", textOf(changes.next()))
        }
    }

    @Test
    fun `a change made while nothing was polling is found on the way back`() = runBlocking {
        server.writeFile("away.txt", "before\n".toByteArray())
        val opened = load("/away.txt")
        val watcher = watcherFor("/away.txt", opened)

        // No watch() at all: this is the app having been in the background.
        server.writeFile("away.txt", "after the app came back\n".toByteArray())
        var seen: FileChange? = null
        watcher.recheck(opened.text) { seen = it }

        assertEquals("after the app came back\n", textOf(seen))
    }

    @Test
    fun `a document with nothing to compare is only looked at when the app comes back`() = runBlocking {
        val onDisk = "changed by the other app\n"
        val file = object : WatchedFile {
            override suspend fun stamp() = Stamped.Unknown
            override suspend fun read() = loader.decode(onDisk.toByteArray(), onDisk.length.toLong(), 0)
        }
        val watcher = FileWatcher(file, Stamped.Unknown, pollMillis = POLL)
        val seen = mutableListOf<FileChange>()

        // The loop gives up rather than re-reading the whole stream every couple of seconds.
        withTimeout(WAIT) { watcher.watch { seen.add(it) } }
        assertTrue("polling a document with no stamp reported $seen", seen.isEmpty())

        watcher.recheck(onDisk) { seen.add(it) }
        assertTrue("the same text was reported as a change: $seen", seen.isEmpty())

        watcher.recheck("what the page still shows\n") { seen.add(it) }
        assertEquals(onDisk, textOf(seen.singleOrNull()))
    }

    @Test
    fun `our own save is not reported as somebody else's change`() {
        server.writeFile("mine.txt", "before\n".toByteArray())
        val opened = runBlocking { load("/mine.txt") }
        val watcher = watcherFor("/mine.txt", opened)

        // What saving does: write the file, then tell the watch what it left there.
        server.writeFile("mine.txt", "what I typed\n".toByteArray())
        val now = runBlocking { fs.stat("/mine.txt") }!!
        watcher.saved(Stamp(now.size, now.modifiedEpochSeconds))

        watching(watcher) { changes ->
            assertNull("our own write came back as somebody else's change", changes.nextWithin(QUIET))
        }
    }

    @Test
    fun `a reload reads the file again although its stamp has not moved`() = runBlocking {
        // The same size in the same second, which is the change no `stat` can see — the blind spot
        // the reload command is there for.
        var onDisk = "before\n"
        val file = object : WatchedFile {
            override suspend fun stamp() = Stamped.At(Stamp(7, 100))
            override suspend fun read() = loader.decode(onDisk.toByteArray(), 7, 100)
        }
        val watcher = FileWatcher(file, Stamped.At(Stamp(7, 100)), pollMillis = POLL)
        val seen = mutableListOf<FileChange>()

        onDisk = "after!\n"
        val job = launch(Dispatchers.Default) { watcher.watch { seen.add(it) } }
        delay(QUIET)
        job.cancelAndJoin()
        assertTrue("polling saw a change it cannot see: $seen", seen.isEmpty())

        watcher.reread { seen.add(it) }
        assertEquals("after!\n", textOf(seen.singleOrNull()))
    }

    @Test
    fun `a stat that fails is not a change`() = runBlocking {
        var looks = 0
        val file = object : WatchedFile {
            override suspend fun stamp(): Stamped {
                looks++
                if (looks == 1) throw IOException("the server did not answer")
                return Stamped.At(Stamp(9, 9))
            }

            override suspend fun read() = loader.decode("x".toByteArray(), 1, 9)
        }
        val watcher = FileWatcher(file, Stamped.At(Stamp(9, 9)), pollMillis = POLL)
        val seen = mutableListOf<FileChange>()

        val job = launch(Dispatchers.Default) { watcher.watch { seen.add(it) } }
        delay(QUIET)
        job.cancelAndJoin()

        assertTrue("the loop stopped after one failed look", looks > 1)
        assertTrue("a server that did not answer was read as a change: $seen", seen.isEmpty())
    }

    private companion object {
        const val POLL = 50L
        /** Long enough for a round trip to the server, short enough to fail rather than hang. */
        const val WAIT = 10_000L
        /** Several ticks of nothing happening. */
        const val QUIET = 500L
    }
}
