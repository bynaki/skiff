package com.naki.skiff.code.project

import com.naki.skiff.SftpTestServer
import com.naki.skiff.code.session.RemoteExec
import kotlinx.coroutines.runBlocking
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import org.apache.sshd.server.command.CommandFactory
import org.apache.sshd.server.shell.ProcessShellFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.PublicKey

/**
 * [ProjectSession.changes], what the sidebar's colors come from, on the accounts [CheckGitTest] tells
 * apart: wherever git cannot be asked the answer is none, never an exception for the tree to show.
 * The `git` here is a script standing in for one, so what it answers is the test's.
 */
class ProjectChangesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private var server: SftpTestServer? = null
    private var exec: RemoteExec? = null

    private val acceptAnyKey = object : HostKeyVerifier {
        override fun verify(hostname: String, port: Int, key: PublicKey) = true
        override fun findExistingAlgorithms(hostname: String, port: Int) = emptyList<String>()
    }

    // Set inside the line, because MINA runs `/bin/sh -c` on its first argument and ignores the rest.
    private fun shellWithPath(path: File) = CommandFactory { channel, line ->
        val withPath = "PATH='${path.path}'; $line"
        ProcessShellFactory(withPath, listOf("/bin/sh", "-c", withPath)).createShell(channel)
    }

    private fun session(commands: CommandFactory?): ProjectSession {
        val server = SftpTestServer(commands = commands).also { it.start() }
        this.server = server
        val exec = RemoteExec("127.0.0.1", server.port, server.username, { server.password }, acceptAnyKey).also { exec = it }
        return ProjectSession(Project(profileId = "p", root = "/srv/repo"), exec)
    }

    /** A folder with a `git` that runs [script]. */
    private fun gitThat(script: String): File {
        val bin = tmp.newFolder("bin")
        File(bin, "git").apply { writeText("#!/bin/sh\n$script\n") }.setExecutable(true)
        return bin
    }

    @After
    fun tearDown() {
        exec?.close()
        server?.stop()
    }

    @Test
    fun `what git says comes back`() = runBlocking {
        val session = session(shellWithPath(gitThat("printf ' M a.txt\\0?? notes/\\0'")))

        assertEquals(
            listOf(GitChange("a.txt", GitChange.Kind.Modified), GitChange("notes", GitChange.Kind.Untracked, directory = true)),
            session.changes { throw AssertionError(it) },
        )
    }

    @Test
    fun `git that fails is none, and said`() = runBlocking {
        val failures = mutableListOf<Exception>()
        val session = session(shellWithPath(gitThat("echo 'fatal: not a git repository' >&2; exit 128")))

        assertNull(session.changes { failures += it })
        assertTrue("$failures", failures.single() is GitFailed)
    }

    @Test
    fun `an account with no git is none`() = runBlocking {
        assertNull(session(shellWithPath(tmp.newFolder("empty"))).changes { throw AssertionError(it) })
    }

    @Test
    fun `an account that runs nothing is none`() = runBlocking {
        assertNull(session(commands = null).changes { throw AssertionError(it) })
    }
}
