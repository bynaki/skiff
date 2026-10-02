package com.naki.skiff.code.project

import com.naki.skiff.SftpTestServer
import com.naki.skiff.code.doc.LineEnding
import com.naki.skiff.code.doc.TextEncoding
import com.naki.skiff.code.doc.TextFormat
import com.naki.skiff.code.doc.TextLoader
import com.naki.skiff.code.session.RemoteExec
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import org.apache.sshd.server.command.CommandFactory
import org.apache.sshd.server.shell.ProcessShellFactory
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.PublicKey
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Against a real SSH server that hands each command to `/bin/sh -c`, running this machine's real
 * `git` on a repository made for the test. The user's own git configuration is kept out, so what is
 * found is the repository's alone.
 */
class GitServiceTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var repo: File
    private lateinit var server: SftpTestServer
    private lateinit var exec: RemoteExec
    private var service: GitService? = null

    /** How many `cat-file` commands the server was asked to start. */
    private val batches = AtomicInteger()

    private val acceptAnyKey = object : HostKeyVerifier {
        override fun verify(hostname: String, port: Int, key: PublicKey) = true
        override fun findExistingAlgorithms(hostname: String, port: Int) = emptyList<String>()
    }

    // Set inside the line, because MINA runs `/bin/sh -c` on its first argument and ignores the rest.
    private val shell = CommandFactory { channel, line ->
        if ("cat-file" in line) batches.incrementAndGet()
        val withConfig = "export $GIT_ENV; $line"
        ProcessShellFactory(withConfig, listOf("/bin/sh", "-c", withConfig)).createShell(channel)
    }

    @Before
    fun setUp() {
        repo = tmp.newFolder("repo")
        git("init", "-q", "-b", "main")
        server = SftpTestServer(commands = shell).also { it.start() }
        exec = RemoteExec("127.0.0.1", server.port, server.username, { server.password }, acceptAnyKey)
    }

    @After
    fun tearDown() {
        service?.close()
        exec.close()
        server.stop()
    }

    private fun service(idleMs: Long = 60_000) = GitService(exec, repo.path, idleMs).also { service = it }

    /** Runs git on this machine, in the test's repository, as the server's shell would. */
    private fun git(vararg args: String): String {
        val process = ProcessBuilder(listOf("/bin/sh", "-c", "export $GIT_ENV; exec \"\$@\"", "git", "git") + args)
            .directory(repo)
            .redirectErrorStream(true)
            .start()
        val out = process.inputStream.readBytes().decodeToString()
        check(process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0) { "git ${args.joinToString(" ")}: $out" }
        return out.trim()
    }

    private fun write(path: String, text: String) = File(repo, path).apply { parentFile!!.mkdirs() }.writeText(text)

    private fun commit(message: String): String {
        git("add", "-A")
        git("commit", "-q", "-m", message)
        return git("rev-parse", "HEAD")
    }

    @Test
    fun `HEAD is the last commit, and nothing before the first`() = runBlocking {
        val service = service()
        assertNull(service.head())

        write("a.md", "one\n")
        val first = commit("first")
        assertEquals(first, service.head())
    }

    @Test
    fun `a file is read as it was committed, not as it is now`() = runBlocking {
        write("src/main.kt", "fun main() {}\n")
        write("한글 이름.txt", "가나다\n")
        val head = commit("first")
        write("src/main.kt", "fun main() { edited() }\n")
        write("new.md", "not committed\n")
        val service = service()

        assertEquals("fun main() {}\n", service.show(head, "src/main.kt", LIMIT)?.decodeToString())
        assertEquals("가나다\n", service.show(head, "한글 이름.txt", LIMIT)?.decodeToString())
        assertNull("new since", service.show(head, "new.md", LIMIT))
        assertNull("a directory then", service.show(head, "src", LIMIT))
    }

    @Test
    fun `bytes come back as they are`() = runBlocking {
        val bytes = ByteArray(256) { it.toByte() } + "\nHEAD missing\n".toByteArray()
        File(repo, "blob.bin").writeBytes(bytes)
        val head = commit("binary")

        assertArrayEquals(bytes, service().show(head, "blob.bin", LIMIT))
    }

    @Test
    fun `many files are read over one command`() = runBlocking {
        repeat(20) { write("f$it.txt", "file $it\n") }
        val head = commit("many")
        val service = service()

        repeat(20) { assertEquals("file $it\n", service.show(head, "f$it.txt", LIMIT)?.decodeToString()) }
        assertEquals(1, batches.get())
    }

    @Test
    fun `a commit made while it runs is seen`() = runBlocking {
        write("a.md", "one\n")
        val first = commit("first")
        val service = service()
        assertEquals(first, service.head())

        write("a.md", "two\n")
        val second = commit("second")

        assertEquals(second, service.head())
        assertEquals("two\n", service.show(second, "a.md", LIMIT)?.decodeToString())
        assertEquals("one\n", service.show(first, "a.md", LIMIT)?.decodeToString())
        assertEquals(1, batches.get())
    }

    @Test
    fun `an object past the limit is not read, and the next one still is`() = runBlocking {
        write("big.txt", "x".repeat(1000))
        write("small.txt", "small\n")
        val head = commit("sizes")
        val service = service()

        val refused = runCatching { service.show(head, "big.txt", 999) }.exceptionOrNull()
        assertTrue("$refused", refused is GitObjectTooLarge)
        assertEquals("small\n", service.show(head, "small.txt", LIMIT)?.decodeToString())
    }

    @Test
    fun `left idle it stops, and starts again when asked`() = runBlocking {
        write("a.md", "one\n")
        val head = commit("first")
        val service = service(idleMs = 200)
        assertEquals(head, service.head())

        delay(600)
        assertEquals("one\n", service.show(head, "a.md", LIMIT)?.decodeToString())
        assertEquals(2, batches.get())
    }

    @Test
    fun `history is the last two commits that touched the file, newest first`() = runBlocking {
        write("a.md", "1\n")
        val first = commit("a1")
        write("b.md", "1\n")
        commit("b1")
        write("a.md", "2\n")
        val third = commit("a2")
        write("a.md", "3\n")
        val fourth = commit("a3")
        val service = service()

        assertEquals(listOf(fourth, third), service.history("a.md"))
        assertNotEquals(first, service.history("a.md").last())
    }

    @Test
    fun `a path that looks like a pattern is only itself`() = runBlocking {
        write("*.md", "star\n")
        val star = commit("star")
        write("a.md", "a\n")
        commit("a")

        assertEquals(listOf(star), service().history("*.md"))
    }

    @Test
    fun `history is empty before the first commit`() = runBlocking {
        assertEquals(emptyList<String>(), service().history("a.md"))
    }

    @Test
    fun `files are the tracked and untracked ones, not the ignored`() = runBlocking {
        write(".gitignore", "build/\n")
        write("src/main.kt", "x\n")
        commit("first")
        write("notes/new file.md", "x\n")
        write("build/out.txt", "x\n")
        write("line\nbreak.txt", "x\n")

        assertEquals(
            setOf(".gitignore", "src/main.kt", "notes/new file.md", "line\nbreak.txt"),
            service().files().toSet(),
        )
    }

    @Test
    fun `HEAD's text is read the way the working file was`() = runBlocking {
        File(repo, "euc.txt").writeBytes("가\r\n나\r\n".toByteArray(charset("EUC-KR")))
        write("big.txt", "x".repeat(100) + "\n")
        commit("first")
        write("new.txt", "x\n")
        val service = service()
        val eucKr = TextFormat(TextEncoding.EUC_KR, bom = false, LineEnding.CRLF, finalNewline = true)
        val utf8 = TextFormat(TextEncoding.UTF_8, bom = false, LineEnding.LF, finalNewline = true)

        assertEquals("가\n나\n", service.headText("euc.txt", eucKr, TextLoader()))
        assertNull("not in HEAD", service.headText("new.txt", utf8, TextLoader()))
        assertNull("past the limit", service.headText("big.txt", utf8, TextLoader(sizeLimit = 10)))
    }

    @Test
    fun `there is no HEAD text before the first commit`() = runBlocking {
        write("a.txt", "x\n")
        val utf8 = TextFormat(TextEncoding.UTF_8, bom = false, LineEnding.LF, finalNewline = true)

        assertNull(service().headText("a.txt", utf8, TextLoader()))
    }

    @Test
    fun `a folder that is not a repository says so`() = runBlocking {
        val service = GitService(exec, tmp.newFolder("plain").path).also { service = it }

        val headFailed = runCatching { service.head() }.exceptionOrNull()
        assertTrue("$headFailed", headFailed is GitFailed)
        assertNotNull((headFailed as GitFailed).stderr.takeIf { "not a git repository" in it })
        val filesFailed = runCatching { service.files() }.exceptionOrNull()
        assertTrue("$filesFailed", filesFailed is GitFailed)
    }

    private companion object {
        const val LIMIT = 1L shl 20
        const val GIT_ENV = "GIT_CONFIG_GLOBAL=/dev/null GIT_CONFIG_NOSYSTEM=1 " +
            "GIT_AUTHOR_NAME=t GIT_AUTHOR_EMAIL=t@example.com GIT_COMMITTER_NAME=t GIT_COMMITTER_EMAIL=t@example.com"
    }
}
