package com.naki.skiff.data

import android.content.Context
import com.naki.skiff.R
import com.naki.skiff.fs.FileSystem
import com.naki.skiff.fs.SourceId
import com.naki.skiff.fs.local.LocalFileSystem

/**
 * Hands out a [FileSystem] for a [SourceId] and keeps one instance per source, so both
 * panes showing the same server share a connection. Remote sources arrive in a later step;
 * for now the registry only knows about the device itself.
 */
class SourceRegistry(private val context: Context) {

    private val instances = HashMap<SourceId, FileSystem>()

    fun descriptors(): List<SourceDescriptor> =
        listOf(SourceDescriptor(SourceId.Local, context.getString(R.string.source_local)))

    fun get(id: SourceId): FileSystem = instances.getOrPut(id) {
        when (id) {
            is SourceId.Local -> LocalFileSystem(context.getString(R.string.source_local))
            is SourceId.Remote -> error("Remote sources are not wired up yet")
        }
    }

    fun closeAll() {
        instances.values.forEach { it.close() }
        instances.clear()
    }
}

data class SourceDescriptor(val id: SourceId, val name: String)
