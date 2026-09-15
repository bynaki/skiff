package com.naki.skiff.data

import android.content.Context
import com.naki.skiff.R
import com.naki.skiff.data.crypto.SecretStore
import com.naki.skiff.data.store.AuthMethod
import com.naki.skiff.data.store.ServerProfile
import com.naki.skiff.data.store.SkiffStore
import com.naki.skiff.fs.FileSystem
import com.naki.skiff.fs.SourceId
import com.naki.skiff.fs.local.LocalFileSystem
import com.naki.skiff.fs.sftp.HostKeyDecision
import com.naki.skiff.fs.sftp.HostKeyGate
import com.naki.skiff.fs.sftp.HostKeyPrompt
import com.naki.skiff.fs.sftp.SftpFileSystem
import com.naki.skiff.fs.sftp.SshConnection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Hands out a [FileSystem] per source and caches it, so both panes pointed at the same
 * server share its connections instead of opening a second login.
 */
class SourceRegistry(
    private val context: Context,
    private val store: SkiffStore,
    private val askHostKey: suspend (HostKeyPrompt) -> HostKeyDecision,
) {

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
        profiles = list
        // Drop cached filesystems for servers that were deleted or edited away.
        val liveIds = list.map { it.id }.toSet()
        val stale = instances.keys.filterIsInstance<SourceId.Remote>()
            .filter { it.profileId !in liveIds }
        stale.forEach { instances.remove(it)?.close() }
        // Publish last: whoever sees these descriptors sees the profiles behind them.
        _sources.value = descriptors()
    }

    private fun descriptors(): List<SourceDescriptor> =
        listOf(SourceDescriptor(SourceId.Local, context.getString(R.string.source_local))) +
            profiles.map { SourceDescriptor(SourceId.Remote(it.id), it.name) }

    fun get(id: SourceId): FileSystem = instances.getOrPut(id) {
        when (id) {
            is SourceId.Local -> LocalFileSystem(context.getString(R.string.source_local))
            is SourceId.Remote -> {
                val profile = profiles.firstOrNull { it.id == id.profileId }
                    ?: error("Unknown server profile ${id.profileId}")
                val gate = HostKeyGate(store, askHostKey)
                SftpFileSystem(
                    id = id,
                    displayName = profile.name,
                    browseConnection = profile.connection(gate, "browse"),
                    transferConnection = profile.connection(gate, "transfer"),
                )
            }
        }
    }

    /** Forces the next access to reconnect — used after editing a server's credentials. */
    fun invalidate(id: SourceId) {
        instances.remove(id)?.close()
    }

    fun closeAll() {
        instances.values.forEach { it.close() }
        instances.clear()
    }
}

private fun ServerProfile.connection(
    gate: HostKeyGate,
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
