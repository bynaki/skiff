package com.naki.skiff.code.doc

import com.naki.skiff.SftpTestServer
import com.naki.skiff.fs.SourceId
import com.naki.skiff.fs.sftp.SftpFileSystem
import com.naki.skiff.fs.sftp.SshConnection
import kotlinx.coroutines.test.runTest
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.security.PublicKey
import java.time.Instant

/** Reads and writes through the production SFTP code against a real server, as saving will. */
class DocumentSaverTest {

    private val server = SftpTestServer()
    private val loader = TextLoader()
    private val saver = DocumentSaver()
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

    private suspend fun save(path: String, text: String, from: LoadResult.Text) =
        saver.save(fs, path, text, from.format, from.size, from.modifiedEpochSeconds)

    @Test
    fun `a utf-8 file is written back as utf-8`() = runTest {
        server.writeFile("메모.md", "# 제목\n본문\n".toByteArray())
        val opened = load("/메모.md")

        val result = save("/메모.md", "# 제목\n고친 본문\n", opened)

        assertTrue("expected a save, got $result", result is SaveResult.Saved)
        assertEquals("# 제목\n고친 본문\n", String(server.readFile("메모.md")))
    }

    @Test
    fun `an euc-kr file stays euc-kr`() = runTest {
        val eucKr = Charset.forName("EUC-KR")
        server.writeFile("한글.txt", "처음\n".toByteArray(eucKr))
        val opened = load("/한글.txt")
        assertEquals(TextEncoding.EUC_KR, opened.format.encoding)

        save("/한글.txt", "처음\n다음\n", opened)

        assertArrayEquals("처음\n다음\n".toByteArray(eucKr), server.readFile("한글.txt"))
    }

    @Test
    fun `crlf goes back as crlf, and the buffer holds lf`() = runTest {
        server.writeFile("win.txt", "one\r\ntwo\r\n".toByteArray())
        val opened = load("/win.txt")
        assertEquals("one\ntwo\n", opened.text)

        save("/win.txt", "one\ntwo\nthree\n", opened)

        assertEquals("one\r\ntwo\r\nthree\r\n", String(server.readFile("win.txt")))
    }

    @Test
    fun `a byte order mark is written back`() = runTest {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        server.writeFile("bom.txt", bom + "hello\n".toByteArray())
        val opened = load("/bom.txt")

        save("/bom.txt", "hello there\n", opened)

        assertArrayEquals(bom + "hello there\n".toByteArray(), server.readFile("bom.txt"))
    }

    @Test
    fun `the buffer decides the final newline, not the file it came from`() = runTest {
        server.writeFile("tail.txt", "no newline".toByteArray())
        val opened = load("/tail.txt")
        assertEquals(false, opened.format.finalNewline)

        save("/tail.txt", "no newline\n", opened)

        assertEquals("no newline\n", String(server.readFile("tail.txt")))
    }

    @Test
    fun `a file that changed under us is a conflict and is left alone`() = runTest {
        server.writeFile("busy.txt", "mine\n".toByteArray())
        val opened = load("/busy.txt")
        server.writeFile("busy.txt", "someone else was here\n".toByteArray())

        val result = save("/busy.txt", "my edit\n", opened)

        val conflict = result as SaveResult.Conflict
        assertEquals("someone else was here\n".length.toLong(), conflict.current?.size)
        assertEquals("someone else was here\n", String(server.readFile("busy.txt")))
    }

    @Test
    fun `a change that keeps the size is still a conflict, by its time`() = runTest {
        server.writeFile("same.txt", "mine\n".toByteArray())
        val opened = load("/same.txt")
        server.writeFile("same.txt", "your\n".toByteArray())
        // The replacement is the same length, and on a fast machine lands in the same second.
        Files.setLastModifiedTime(
            server.root.resolve("same.txt"),
            FileTime.from(Instant.ofEpochSecond(opened.modifiedEpochSeconds + 10)),
        )

        val result = save("/same.txt", "my edit\n", opened)

        assertEquals(opened.size, (result as SaveResult.Conflict).current?.size)
        assertEquals("your\n", String(server.readFile("same.txt")))
    }

    @Test
    fun `a file that is gone is a conflict with nothing to report`() = runTest {
        server.writeFile("gone.txt", "here for now\n".toByteArray())
        val opened = load("/gone.txt")
        fs.delete("/gone.txt", recursive = false)

        val result = save("/gone.txt", "my edit\n", opened)

        assertNull((result as SaveResult.Conflict).current)
        assertTrue(!server.exists("gone.txt"))
    }

    @Test
    fun `what a save reports can be saved against again`() = runTest {
        server.writeFile("again.txt", "one\n".toByteArray())
        val opened = load("/again.txt")
        val first = save("/again.txt", "two\n", opened) as SaveResult.Saved

        val second = saver.save(fs, "/again.txt", "three\n", opened.format, first.size, first.modifiedEpochSeconds)

        assertTrue("expected a save, got $second", second is SaveResult.Saved)
        assertEquals("three\n", String(server.readFile("again.txt")))
    }

    @Test
    fun `a character the file's encoding cannot hold is refused rather than written as a question mark`() = runTest {
        val eucKr = Charset.forName("EUC-KR")
        server.writeFile("한글.txt", "처음\n".toByteArray(eucKr))
        val opened = load("/한글.txt")

        val result = save("/한글.txt", "처음\n웃는 얼굴 🙂\n", opened)

        val refused = result as SaveResult.Unencodable
        assertEquals("🙂", refused.text)
        assertEquals("처음\n웃는 얼굴 ".length, refused.index)
        assertArrayEquals("처음\n".toByteArray(eucKr), server.readFile("한글.txt"))
    }

    @Test
    fun `the refused character is named at its place in the buffer, not in the crlf text`() = runTest {
        val eucKr = Charset.forName("EUC-KR")
        server.writeFile("win.txt", "처음\r\n둘째\r\n".toByteArray(eucKr))
        val opened = load("/win.txt")

        val result = save("/win.txt", "처음\n둘째\n🙂\n", opened)

        assertEquals("처음\n둘째\n".length, (result as SaveResult.Unencodable).index)
    }
}
