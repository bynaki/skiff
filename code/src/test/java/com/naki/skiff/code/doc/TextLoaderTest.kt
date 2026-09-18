package com.naki.skiff.code.doc

import com.naki.skiff.SftpTestServer
import com.naki.skiff.fs.FsError
import com.naki.skiff.fs.SourceId
import com.naki.skiff.fs.sftp.SftpFileSystem
import com.naki.skiff.fs.sftp.SshConnection
import kotlinx.coroutines.test.runTest
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.PublicKey

class TextLoaderTest {

    private val loader = TextLoader(sizeLimit = 1024)

    private fun decode(bytes: ByteArray) = loader.decode(bytes, bytes.size.toLong(), 0)

    private fun text(bytes: ByteArray) = decode(bytes) as LoadResult.Text

    @Test
    fun `utf-8 with hangul is read as utf-8`() {
        val result = text("값 = \"한글\"\n".toByteArray())

        assertEquals("값 = \"한글\"\n", result.text)
        assertEquals(TextFormat(TextEncoding.UTF_8, bom = false, LineEnding.LF, finalNewline = true), result.format)
    }

    @Test
    fun `bytes that are not utf-8 fall back to euc-kr`() {
        val result = text("한글 문서\r\n".toByteArray(charset("EUC-KR")))

        assertEquals("한글 문서\n", result.text)
        assertEquals(TextEncoding.EUC_KR, result.format.encoding)
    }

    @Test
    fun `bytes neither encoding decodes are refused rather than replaced`() {
        // 0xFF is never valid in UTF-8 and is not a lead byte in EUC-KR.
        assertEquals(LoadResult.UnknownEncoding, decode(byteArrayOf('a'.code.toByte(), 0xFF.toByte(), 'b'.code.toByte())))
    }

    @Test
    fun `a literal replacement character is ordinary utf-8, not a reason to fall back`() {
        assertEquals(TextEncoding.UTF_8, text("a�b".toByteArray()).format.encoding)
    }

    @Test
    fun `a utf-8 bom is stripped and remembered`() {
        val result = text(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "한".toByteArray())

        assertEquals("한", result.text)
        assertTrue(result.format.bom)
    }

    @Test
    fun `crlf becomes lf in the text and is remembered`() {
        val result = text("a\r\nb\r\n".toByteArray())

        assertEquals("a\nb\n", result.text)
        assertEquals(LineEnding.CRLF, result.format.lineEnding)
        assertTrue(result.format.finalNewline)
    }

    @Test
    fun `a missing final newline is remembered`() {
        val result = text("a\nb".toByteArray())

        assertEquals(LineEnding.LF, result.format.lineEnding)
        assertEquals(false, result.format.finalNewline)
    }

    @Test
    fun `the first line ending decides for a mixed file`() {
        assertEquals(LineEnding.LF, text("a\nb\r\n".toByteArray()).format.lineEnding)
        assertEquals(LineEnding.CRLF, text("a\r\nb\n".toByteArray()).format.lineEnding)
    }

    @Test
    fun `an empty file is text`() {
        val result = text(ByteArray(0))

        assertEquals("", result.text)
        assertEquals(false, result.format.finalNewline)
    }

    @Test
    fun `a nul byte inside the probe window means binary`() {
        val bytes = ByteArray(TextLoader.BINARY_PROBE) { 'a'.code.toByte() }
        bytes[TextLoader.BINARY_PROBE - 1] = 0

        assertEquals(LoadResult.Binary, TextLoader(sizeLimit = Long.MAX_VALUE).decode(bytes, bytes.size.toLong(), 0))
    }

    @Test
    fun `a nul byte past the probe window is not looked for`() {
        val bytes = ByteArray(TextLoader.BINARY_PROBE + 1) { 'a'.code.toByte() }
        bytes[TextLoader.BINARY_PROBE] = 0

        assertTrue(TextLoader(sizeLimit = Long.MAX_VALUE).decode(bytes, bytes.size.toLong(), 0) is LoadResult.Text)
    }

    /** The same rules, read through the production SFTP code from a real server. */
    class OverSftp {

        private val server = SftpTestServer()
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

        @Test
        fun `reads a hangul file under a hangul name`() = runTest {
            server.writeFile("문서/메모.md", "# 제목\r\n본문\r\n".toByteArray())

            val result = TextLoader().load(fs, "/문서/메모.md") as LoadResult.Text

            assertEquals("# 제목\n본문\n", result.text)
            assertEquals(LineEnding.CRLF, result.format.lineEnding)
            assertEquals(fs.stat("/문서/메모.md")!!.size, result.size)
        }

        @Test
        fun `reads an euc-kr file`() = runTest {
            server.writeFile("old.txt", "옛날 파일".toByteArray(charset("EUC-KR")))

            val result = TextLoader().load(fs, "/old.txt") as LoadResult.Text

            assertEquals("옛날 파일", result.text)
            assertEquals(TextEncoding.EUC_KR, result.format.encoding)
        }

        @Test
        fun `a file over the limit is refused before it is read`() = runTest {
            server.writeFile("big.txt", ByteArray(2048) { 'a'.code.toByte() })

            assertEquals(LoadResult.TooLarge(2048, 1024), TextLoader(sizeLimit = 1024).load(fs, "/big.txt"))
        }

        @Test
        fun `a file at the limit is read`() = runTest {
            server.writeFile("edge.txt", ByteArray(1024) { 'a'.code.toByte() })

            assertEquals(1024, (TextLoader(sizeLimit = 1024).load(fs, "/edge.txt") as LoadResult.Text).text.length)
        }

        @Test
        fun `binary content is recognised whatever the extension`() = runTest {
            server.writeFile("looks.txt", byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0, 0))

            assertEquals(LoadResult.Binary, TextLoader().load(fs, "/looks.txt"))
        }

        @Test
        fun `a missing file is NotFound`() = runTest {
            val failure = runCatching { TextLoader().load(fs, "/absent.txt") }.exceptionOrNull()
            assertTrue("expected NotFound, got $failure", failure is FsError.NotFound)
        }
    }
}
