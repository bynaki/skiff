package com.naki.skiff.data

import android.content.Context
import com.naki.skiff.R
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

    fun updateProfiles(list: List<ServerProfile>) {
        profiles = list
        // Drop cached filesystems for servers that were deleted or edited away.
        val liveIds = list.map { it.id }.toSet()
        val stale = instances.keys.filterIsInstance<SourceId.Remote>()
            .filter { it.profileId !in liveIds }
        stale.forEach { instances.remove(it)?.close() }
    }

    fun descriptors(): List<SourceDescriptor> =
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
                    profile = profile,
                    browseConnection = SshConnection(profile, gate, "browse"),
                    transferConnection = SshConnection(profile, gate, "transfer"),
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

data class SourceDescriptor(val id: SourceId, val name: String)
