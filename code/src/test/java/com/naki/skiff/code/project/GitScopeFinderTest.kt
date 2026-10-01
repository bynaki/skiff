package com.naki.skiff.code.project

import com.naki.skiff.SftpTestServer
import com.naki.skiff.fs.SourceId
import com.naki.skiff.fs.sftp.SftpFileSystem
import com.naki.skiff.fs.sftp.SshConnection
import kotlinx.coroutines.test.runTest
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.security.PublicKey

/**
 * Over a real SFTP server. MINA's home is always the root of what it serves, so the tests that need
 * a home below it name one, as the app's caller would after asking the server.
 *
 * A symlink out of home is not tested here: MINA's REALPATH only normalizes the path and does not
 * follow links, where OpenSSH's resolves them, so the case can only be seen against a real sshd.
 */
class GitScopeFinderTest {

    private val server = SftpTestServer()
    private lateinit var fs: SftpFileSystem

    private val home = "/home/tester"

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

    private fun file(path: String) = server.writeFile(path.removePrefix("/"), "x\n".toByteArray())

    private fun gitDirectory(dir: String) = file("$dir/.git/HEAD")

    @Test
    fun `a file in a checkout belongs to the directory holding git`() = runTest {
        gitDirectory("$home/repo")
        file("$home/repo/src/main/a.kt")

        assertEquals("$home/repo", GitScopeFinder.find(fs, "$home/repo/src/main/a.kt", home))
    }

    @Test
    fun `a worktree's git file counts as much as a directory`() = runTest {
        server.writeFile("home/tester/wt/.git", "gitdir: /home/tester/repo/.git/worktrees/wt\n".toByteArray())
        file("$home/wt/a.kt")

        assertEquals("$home/wt", GitScopeFinder.find(fs, "$home/wt/a.kt", home))
    }

    @Test
    fun `the nearest git wins, so a submodule is its own scope`() = runTest {
        gitDirectory("$home/repo")
        server.writeFile("home/tester/repo/lib/.git", "gitdir: ../.git/modules/lib\n".toByteArray())
        file("$home/repo/lib/b.kt")

        assertEquals("$home/repo/lib", GitScopeFinder.find(fs, "$home/repo/lib/b.kt", home))
    }

    @Test
    fun `the walk stops at home and does not see a git above it`() = runTest {
        gitDirectory("/home")
        file("$home/notes/a.md")

        assertNull(GitScopeFinder.find(fs, "$home/notes/a.md", home))
    }

    @Test
    fun `home itself is still looked at`() = runTest {
        gitDirectory(home)
        file("$home/a.md")

        assertEquals(home, GitScopeFinder.find(fs, "$home/a.md", home))
    }

    @Test
    fun `a file outside home is not looked into`() = runTest {
        gitDirectory("/srv/app")
        file("/srv/app/a.kt")

        assertNull(GitScopeFinder.find(fs, "/srv/app/a.kt", home))
    }

    @Test
    fun `without a home given, the login directory is the boundary`() = runTest {
        // MINA logs in at the root it serves, so here that is "/".
        gitDirectory("/repo")
        file("/repo/a.kt")

        assertEquals("/repo", GitScopeFinder.find(fs, "/repo/a.kt"))
    }
}
