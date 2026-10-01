package com.naki.skiff.code.project

import com.naki.skiff.SftpTestServer
import com.naki.skiff.code.session.RemoteExec
import kotlinx.coroutines.runBlocking
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import org.apache.sshd.server.command.CommandFactory
import org.apache.sshd.server.shell.ProcessShellFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.PublicKey

/**
 * Against a real SSH server that hands each command to `/bin/sh -c`, with the PATH pointed at a folder
 * that has a `git` or one that does not — so what is found is ours, not whatever this machine has.
 */
class CheckGitTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private var server: SftpTestServer? = null
    private var exec: RemoteExec? = null

    private val acceptAnyKey = object : HostKeyVerifier {
        override fun verify(hostname: String, port: Int, key: PublicKey) = true
        override fun findExistingAlgorithms(hostname: String, port: Int) = emptyList<String>()
    }

    /**
     * A shell whose PATH is [path] alone. Set inside the line, because MINA runs `/bin/sh -c` on its
     * first argument and ignores the list given after it.
     */
    private fun shellWithPath(path: File) = CommandFactory { channel, line ->
        val withPath = "PATH='${path.path}'; $line"
        ProcessShellFactory(withPath, listOf("/bin/sh", "-c", withPath)).createShell(channel)
    }

    private fun start(commands: CommandFactory?): RemoteExec {
        val server = SftpTestServer(commands = commands).also { it.start() }
        this.server = server
        return RemoteExec("127.0.0.1", server.port, server.username, { server.password }, acceptAnyKey).also { exec = it }
    }

    @After
    fun tearDown() {
        exec?.close()
        server?.stop()
    }

    @Test
    fun `git on the PATH is available`() = runBlocking {
        val bin = tmp.newFolder("bin")
        File(bin, "git").apply { writeText("#!/bin/sh\n") }.setExecutable(true)

        assertEquals(GitState.Available, checkGit(start(shellWithPath(bin))))
    }

    @Test
    fun `an account that runs commands but has no git is missing it`() = runBlocking {
        assertEquals(GitState.Missing, checkGit(start(shellWithPath(tmp.newFolder("empty")))))
    }

    @Test
    fun `an account that runs nothing is not mistaken for one without git`() = runBlocking {
        assertEquals(GitState.NoExec, checkGit(start(commands = null)))
    }
}
