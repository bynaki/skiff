package com.naki.skiff

import android.content.Context
import com.naki.skiff.data.SourceRegistry
import com.naki.skiff.data.store.SkiffStore
import com.naki.skiff.data.store.openSkiffDataStore
import com.naki.skiff.fs.sftp.HostKeyGate
import com.naki.skiff.fs.sftp.HostKeyPrompter
import com.naki.skiff.transfer.ConflictPrompter
import com.naki.skiff.transfer.TransferQueue
import com.naki.skiff.ui.describe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Process-scoped singletons. These outlive any screen on purpose: a transfer has to keep
 * running while the user is in another app, which means the connections and the queue
 * cannot be owned by a ViewModel.
 */
class SkiffContainer(private val context: Context) {

    val scope = CoroutineScope(SupervisorJob())

    val store = SkiffStore(openSkiffDataStore(context))

    val hostKeyPrompter = HostKeyPrompter()

    val registry = SourceRegistry(
        localSourceName = context.getString(R.string.source_local),
        newHostKeyGate = { HostKeyGate(store, hostKeyPrompter::ask) },
    )

    val conflictPrompter = ConflictPrompter()

    val transferQueue = TransferQueue(scope, registry, conflictPrompter) { context.describe(it) }

    init {
        scope.launch {
            store.profiles.collect { registry.updateProfiles(it) }
        }
    }
}
