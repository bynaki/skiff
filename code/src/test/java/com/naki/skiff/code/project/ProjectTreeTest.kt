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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.PublicKey

/** The sidebar's tree over a real SFTP server, and what keeps the page's paths inside the root. */
class ProjectTreeTest {

    private val server = SftpTestServer()
    private lateinit var fs: SftpFileSystem

    private val root = "/home/tester/repo"

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

    @Test
    fun `a directory lists its folders first, then its files, by name, without git`() = runTest {
        file("$root/.git/HEAD")
        file("$root/b.md")
        file("$root/A.md")
        file("$root/.gitignore")
        file("$root/src/main.kt")
        file("$root/docs/a.md")

        assertEquals(
            listOf(
                ProjectTree.Entry("docs", true),
                ProjectTree.Entry("src", true),
                ProjectTree.Entry(".gitignore", false),
                ProjectTree.Entry("A.md", false),
                ProjectTree.Entry("b.md", false),
            ),
            ProjectTree.list(fs, root, ""),
        )
        assertEquals(listOf(ProjectTree.Entry("main.kt", false)), ProjectTree.list(fs, root, "src"))
    }

    @Test
    fun `a path that could leave the root is refused, not resolved`() = runTest {
        file("/home/tester/secret.txt")

        for (path in listOf("..", "src/../..", "/home/tester", "./src", "src//main.kt", "src/", "a\u0000b")) {
            assertNull(path, ProjectTree.resolve(root, path))
        }
        val refused = runCatching { ProjectTree.list(fs, root, "..") }.exceptionOrNull()
        assertTrue("$refused", refused is IllegalArgumentException)
    }

    @Test
    fun `a plain relative path resolves under the root`() {
        assertEquals(root, ProjectTree.resolve(root, ""))
        assertEquals("$root/src/main.kt", ProjectTree.resolve(root, "src/main.kt"))
        assertEquals("$root/..hidden", ProjectTree.resolve(root, "..hidden"))
    }

    @Test
    fun `a path is relative to the root only when it is under it`() {
        assertEquals("src/main.kt", ProjectTree.relative(root, "$root/src/main.kt"))
        assertEquals("", ProjectTree.relative(root, root))
        assertNull(ProjectTree.relative(root, "/home/tester/repo2/a.kt"))
        assertNull(ProjectTree.relative(root, "/home/tester/a.kt"))
    }
}
