package com.naki.skiff.data

import com.naki.skiff.data.crypto.SecretStore
import com.naki.skiff.data.store.AuthMethod
import com.naki.skiff.data.store.ServerProfile
import com.naki.skiff.fs.FileSystem
import com.naki.skiff.fs.SourceId
import com.naki.skiff.fs.local.LocalFileSystem
import com.naki.skiff.fs.sftp.SftpFileSystem
import com.naki.skiff.fs.sftp.SshConnection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.schmizz.sshj.transport.verification.HostKeyVerifier

/**
 * Hands out a [FileSystem] per source and caches it, so both panes pointed at the same
 * server share its connections instead of opening a second login.
 */
class SourceRegistry(
    private val localSourceName: String,
    /**
     * Builds the verifier for a new connection. Taking a factory rather than the store keeps
     * this class off Android — the same reason [SshConnection] takes a password supplier
     * instead of reaching into the keystore itself.
     *
     * The type is wide enough to accept sshj's PromiscuousVerifier. Never hand one in: it
     * accepts every key, which is the whole of the protection against a man in the middle.
     */
    private val newHostKeyGate: () -> HostKeyVerifier,
) {

    /**
     * Guards [instances] and [profiles] together. Panes read them on the main thread while the
     * collector following the store writes them on another, and getOrPut is not atomic: two
     * panes reaching for one server at the same moment would otherwise build two filesystems,
     * which is two logins where this class promises one, and a leak of whichever loses.
     *
     * Never held across [FileSystem.close] — that disconnects on the calling thread.
     */
    private val lock = Any()

    private val instances = HashMap<SourceId, FileSystem>()
    private var profiles: List<ServerProfile> = emptyList()

    private val _sources = MutableStateFlow(descriptors())

    /**
     * What the source picker offers. It is published from here, and not derived from the store
     * a second time, so it cannot run ahead of [profiles]: a descriptor this registry does not
     * know yet would fail [get] the moment the user picked it.
     */
    val sources: StateFlow<List<SourceDescriptor>> = _sources.asStateFlow()

    fun updateProfiles(list: List<ServerProfile>) {
        val dropped = synchronized(lock) {
            profiles = list
            // Drop cached filesystems for servers that were deleted or edited away.
            val liveIds = list.map { it.id }.toSet()
            val stale = instances.keys.filterIsInstance<SourceId.Remote>()
                .filter { it.profileId !in liveIds }
            val removed = stale.mapNotNull { instances.remove(it) }
            // Publish last: whoever sees these descriptors sees the profiles behind them.
            _sources.value = descriptors()
            removed
        }
        // Outside the lock: closing disconnects on this thread.
        dropped.forEach { it.close() }
    }

    /** Call under [lock], or before this object is shared. */
    private fun descriptors(): List<SourceDescriptor> =
        listOf(SourceDescriptor(SourceId.Local, localSourceName)) +
            profiles.map { SourceDescriptor(SourceId.Remote(it.id), it.name) }

    fun get(id: SourceId): FileSystem = synchronized(lock) {
        instances.getOrPut(id) {
            when (id) {
                is SourceId.Local -> LocalFileSystem(localSourceName)
                is SourceId.Remote -> {
                    val profile = profiles.firstOrNull { it.id == id.profileId }
                        ?: error("Unknown server profile ${id.profileId}")
                    val gate = newHostKeyGate()
                    SftpFileSystem(
                        id = id,
                        displayName = profile.name,
                        browseConnection = profile.connection(gate, "browse"),
                        transferConnection = profile.connection(gate, "transfer"),
                    )
                }
            }
        }
    }

    /** Forces the next access to reconnect — used after editing a server's credentials. */
    fun invalidate(id: SourceId) {
        synchronized(lock) { instances.remove(id) }?.close()
    }

    fun closeAll() {
        val open = synchronized(lock) {
            instances.values.toList().also { instances.clear() }
        }
        open.forEach { it.close() }
    }
}

private fun ServerProfile.connection(
    gate: HostKeyVerifier,
    label: String,
) = SshConnection(
    host = host,
    port = port,
    username = username,
    // Decrypted per connect rather than held in memory for the session.
    password = { (auth as? AuthMethod.Password)?.encryptedPassword?.let(SecretStore::decrypt) },
    startPathRequest = startPath,
    hostKeyVerifier = gate,
    label = label,
)

data class SourceDescriptor(val id: SourceId, val name: String)
