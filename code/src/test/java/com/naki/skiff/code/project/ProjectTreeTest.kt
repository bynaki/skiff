package com.naki.skiff.code.project

import com.naki.skiff.SftpTestServer
import com.naki.skiff.fs.SourceId
import com.naki.skiff.fs.sftp.SftpFileSystem
import com.naki.skiff.fs.sftp.SshConnection
import kotlinx.coroutines.test.runTest
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermissions
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
    fun `a walk lists every file under the root by its path, leaving out skipped folders and git`() = runTest {
        file("$root/.git/HEAD")
        file("$root/README.md")
        file("$root/.github/ci.yml")
        file("$root/src/main.kt")
        file("$root/src/deep/er/x.kt")
        file("$root/node_modules/pkg/index.js")
        file("$root/src/node_modules/y.js")
        server.makeDirectory("$root/empty".removePrefix("/"))

        val walk = ProjectTree.walk(fs, root, listOf("node_modules"), 100)

        assertEquals(listOf(".github/ci.yml", "README.md", "src/deep/er/x.kt", "src/main.kt"), walk.paths.sorted())
        assertFalse(walk.truncated)
    }

    @Test
    fun `a walk lists a link to a file but neither follows a link to a folder nor lists a broken one`() = runTest {
        file("$root/a.txt")
        file("$root/sub/b.txt")
        val dir = server.root.resolve(root.removePrefix("/"))
        // Relative, as the server's root is not the host's.
        Files.createSymbolicLink(dir.resolve("link.txt"), Paths.get("a.txt"))
        // Back up the tree: followed, this would go round for ever.
        Files.createSymbolicLink(dir.resolve("sub/loop"), Paths.get(".."))
        Files.createSymbolicLink(dir.resolve("broken.txt"), Paths.get("nowhere.txt"))

        assertEquals(listOf("a.txt", "link.txt", "sub/b.txt"), ProjectTree.walk(fs, root, emptyList(), 100).paths.sorted())
    }

    @Test
    fun `a walk stops at its limit and says so, but not when the files just fit`() = runTest {
        for (i in 1..5) file("$root/d$i/f.txt")

        val stopped = ProjectTree.walk(fs, root, emptyList(), 3)
        assertEquals(3, stopped.paths.size)
        assertTrue(stopped.truncated)

        val fits = ProjectTree.walk(fs, root, emptyList(), 5)
        assertEquals(5, fits.paths.size)
        assertFalse(fits.truncated)
    }

    @Test
    fun `a folder the walk cannot read is passed over`() = runTest {
        file("$root/open/a.txt")
        file("$root/shut/b.txt")
        val shut = server.root.resolve("$root/shut".removePrefix("/"))
        Files.setPosixFilePermissions(shut, PosixFilePermissions.fromString("---------"))
        try {
            assertEquals(listOf("open/a.txt"), ProjectTree.walk(fs, root, emptyList(), 100).paths)
        } finally {
            Files.setPosixFilePermissions(shut, PosixFilePermissions.fromString("rwx------"))
        }
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
