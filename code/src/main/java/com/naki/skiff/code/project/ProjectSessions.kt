package com.naki.skiff.code.project

import com.naki.skiff.code.lsp.LanguageServer
import com.naki.skiff.code.lsp.LspManager
import com.naki.skiff.code.session.RemoteExec
import com.naki.skiff.code.session.decryptedPassword
import com.naki.skiff.data.store.ServerProfile
import net.schmizz.sshj.transport.verification.HostKeyVerifier

/** What a project's server lets it run, as far as git goes. */
enum class GitState {
    Available,

    /** Commands run, but there is no `git` on the PATH the account gets. */
    Missing,

    /** The account runs no commands at all: `internal-sftp`, a `nologin` shell, a refused request. */
    NoExec,
}

/**
 * Whether git can be run here. [RemoteExec.supported] is asked first because `command -v git` alone
 * fails the same way on an account with no git and on one that runs nothing (`docs/skiffcode.spec.md`).
 */
suspend fun checkGit(exec: RemoteExec): GitState = when {
    !exec.supported() -> GitState.NoExec
    exec.run(listOf("command", "-v", "git")).exitStatus == 0 -> GitState.Available
    else -> GitState.Missing
}

/** An activated project: its own command connection, and what the server was found to run. */
class ProjectSession(
    val project: Project,
    val exec: RemoteExec,
    private val languageCommands: (LanguageServer) -> List<List<String>> = LanguageServer::commands,
) {

    /** Null until [checkGit] has answered once; a failed check is asked again next time. */
    @Volatile
    var git: GitState? = null
        private set

    suspend fun checkGit(): GitState = git ?: checkGit(exec).also { git = it }

    private val repository = lazy { GitService(exec, project.root) }

    /** The repository's answers, or null where [checkGit] finds no git to ask. */
    suspend fun gitService(): GitService? = if (checkGit() == GitState.Available) repository.value else null

    private val languageServers = lazy { LspManager(exec, project.root, commands = languageCommands) }

    /**
     * The project's language servers, or null on an account that runs no commands. Asked of
     * [checkGit] because `command -v` alone cannot tell that account from one with the server
     * installed: a forced `internal-sftp` may answer any command with nothing and a 0.
     */
    suspend fun languageServers(): LspManager? = if (checkGit() == GitState.NoExec) null else languageServers.value

    /** Shuts down the language servers that are running, if any were ever asked for. */
    fun stopLanguageServers() {
        if (languageServers.isInitialized()) languageServers.value.stopAll()
    }

    /** Ends the command connection and what runs on it. Writes to the socket, so not on the main thread. */
    fun close() {
        if (repository.isInitialized()) repository.value.close()
        if (languageServers.isInitialized()) languageServers.value.close()
        exec.close()
    }
}

/**
 * One [ProjectSession] per project, for the life of the process. A profile that comes back changed
 * (a new password) replaces the session it had, as [com.naki.skiff.code.session.RemoteSessions] does.
 * [languageCommands] is what `settings.toml` says starts each language server, asked each time one starts.
 */
class ProjectSessions(
    private val newHostKeyGate: () -> HostKeyVerifier,
    private val languageCommands: (LanguageServer) -> List<List<String>> = LanguageServer::commands,
) {

    private val lock = Any()
    private val open = HashMap<String, Pair<ServerProfile, ProjectSession>>()

    fun get(project: Project, profile: ServerProfile): ProjectSession {
        var stale: ProjectSession? = null
        val session = synchronized(lock) {
            val current = open[project.id]
            if (current != null && current.first == profile) return@synchronized current.second
            stale = current?.second
            val exec = RemoteExec(profile.host, profile.port, profile.username, { profile.decryptedPassword() }, newHostKeyGate())
            ProjectSession(project, exec, languageCommands).also { open[project.id] = profile to it }
        }
        // Closing disconnects on this thread, so never under the lock.
        stale?.close()
        return session
    }

    /** Shuts down every project's language servers, for an app that has been away long enough. */
    fun stopLanguageServers() {
        val sessions = synchronized(lock) { open.values.map { it.second } }
        sessions.forEach { it.stopLanguageServers() }
    }

    /** Ends a removed project's session, and the command connection with it. */
    fun close(projectId: String) {
        val session = synchronized(lock) { open.remove(projectId)?.second }
        session?.close()
    }
}
