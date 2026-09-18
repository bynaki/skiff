package com.naki.skiff.code.session

import com.naki.skiff.data.crypto.SecretStore
import com.naki.skiff.data.store.AuthMethod
import com.naki.skiff.data.store.ServerProfile
import com.naki.skiff.fs.SourceId
import com.naki.skiff.fs.sftp.SftpFileSystem
import com.naki.skiff.fs.sftp.SshConnection
import net.schmizz.sshj.transport.verification.HostKeyVerifier

/**
 * One [SftpFileSystem] per profile, SFTP only — no exec here; that is `RemoteExec`'s alone.
 *
 * Profiles are passed in rather than looked up, because a server opened from a link without being
 * saved has a profile that exists only in memory. A profile that comes back changed (a new
 * password, say) replaces the connection it had.
 */
class RemoteSessions(private val newHostKeyGate: () -> HostKeyVerifier) {

    private val lock = Any()
    private val open = HashMap<String, Pair<ServerProfile, SftpFileSystem>>()

    fun get(profile: ServerProfile): SftpFileSystem {
        var stale: SftpFileSystem? = null
        val fs = synchronized(lock) {
            val current = open[profile.id]
            if (current != null && current.first == profile) return@synchronized current.second
            stale = current?.second
            val gate = newHostKeyGate()
            SftpFileSystem(
                id = SourceId.Remote(profile.id),
                displayName = profile.name,
                browseConnection = profile.connection(gate, "browse"),
                transferConnection = profile.connection(gate, "transfer"),
            ).also { open[profile.id] = profile to it }
        }
        // Closing disconnects on this thread, so never under the lock.
        stale?.close()
        return fs
    }

    fun closeAll() {
        val all = synchronized(lock) { open.values.map { it.second }.also { open.clear() } }
        all.forEach { it.close() }
    }
}

private fun ServerProfile.connection(gate: HostKeyVerifier, label: String) = SshConnection(
    host = host,
    port = port,
    username = username,
    // Decrypted per connect rather than held in memory for the session.
    password = { (auth as? AuthMethod.Password)?.encryptedPassword?.let(SecretStore::decrypt) },
    startPathRequest = startPath,
    hostKeyVerifier = gate,
    label = label,
)
