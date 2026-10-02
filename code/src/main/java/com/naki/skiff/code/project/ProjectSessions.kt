package com.naki.skiff.code.project

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
class ProjectSession(val project: Project, val exec: RemoteExec) {

    /** Null until [checkGit] has answered once; a failed check is asked again next time. */
    @Volatile
    var git: GitState? = null
        private set

    suspend fun checkGit(): GitState = git ?: checkGit(exec).also { git = it }
}

/**
 * One [ProjectSession] per project, for the life of the process. A profile that comes back changed
 * (a new password) replaces the session it had, as [com.naki.skiff.code.session.RemoteSessions] does.
 */
class ProjectSessions(private val newHostKeyGate: () -> HostKeyVerifier) {

    private val lock = Any()
    private val open = HashMap<String, Pair<ServerProfile, ProjectSession>>()

    fun get(project: Project, profile: ServerProfile): ProjectSession {
        var stale: ProjectSession? = null
        val session = synchronized(lock) {
            val current = open[project.id]
            if (current != null && current.first == profile) return@synchronized current.second
            stale = current?.second
            val exec = RemoteExec(profile.host, profile.port, profile.username, { profile.decryptedPassword() }, newHostKeyGate())
            ProjectSession(project, exec).also { open[project.id] = profile to it }
        }
        // Closing disconnects on this thread, so never under the lock.
        stale?.exec?.close()
        return session
    }

    /** Ends a removed project's session, and the command connection with it. */
    fun close(projectId: String) {
        val session = synchronized(lock) { open.remove(projectId)?.second }
        session?.exec?.close()
    }
}
