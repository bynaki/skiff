package com.naki.skiff.code.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStoreFile
import com.naki.skiff.data.store.AuthMethod
import com.naki.skiff.data.store.KnownHost
import com.naki.skiff.data.store.KnownHostStore
import com.naki.skiff.data.store.ServerProfile
import com.naki.skiff.data.store.jsonDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

/** A file opened before, newest first in [SkiffCodeData.recentFiles]. */
@Serializable
data class RecentFile(
    /** The link it was opened by: `skiffcode://…` or `content://…`. */
    val uri: String,
    val openedAtEpochSeconds: Long,
)

@Serializable
data class SkiffCodeData(
    val profiles: List<ServerProfile> = emptyList(),
    val knownHosts: List<KnownHost> = emptyList(),
    val recentFiles: List<RecentFile> = emptyList(),
)

/** Opens Skiff Code's store. Call it once per process, from whatever holds the process's singletons. */
fun openSkiffCodeDataStore(context: Context): DataStore<SkiffCodeData> =
    jsonDataStore(context.dataStoreFile("skiffcode.json"), SkiffCodeData.serializer(), SkiffCodeData())

/**
 * Skiff Code's own server profiles, accepted host keys and recent files, in one JSON blob the way
 * Skiff keeps its own. The two apps do not share this file: Keystore keys are per app, so a
 * password encrypted by one could not be decrypted by the other. Passwords inside profiles are
 * already ciphertext from [com.naki.skiff.data.crypto.SecretStore]; this layer never sees plaintext.
 *
 * It takes the [DataStore] rather than a `Context` so it can be tested on a plain JVM.
 */
class SkiffCodeStore(private val dataStore: DataStore<SkiffCodeData>) : KnownHostStore {

    val profiles: Flow<List<ServerProfile>> = dataStore.data.map { it.profiles }

    val recentFiles: Flow<List<RecentFile>> = dataStore.data.map { it.recentFiles }

    suspend fun upsertProfile(profile: ServerProfile) {
        dataStore.updateData { data ->
            val existing = data.profiles.indexOfFirst { it.id == profile.id }
            val profiles = data.profiles.toMutableList()
            if (existing >= 0) profiles[existing] = profile else profiles.add(profile)
            data.copy(profiles = profiles)
        }
    }

    suspend fun deleteProfile(id: String) {
        dataStore.updateData { data ->
            data.copy(profiles = data.profiles.filterNot { it.id == id })
        }
    }

    /**
     * Takes in the servers Skiff shared. A profile is matched by Skiff's id, then by name — a
     * server saved from the unknown-server dialog is named after Skiff's alias — and takes Skiff's
     * address and start path, keeping its own id and password. The password is dropped when the
     * host, port or user changed, so it is never sent to another machine. Unmatched ones are added
     * without a password. Nothing is deleted.
     *
     * A host key is added only where there is none for that host and port. One we already have is
     * kept even if Skiff's differs: which is right is for [com.naki.skiff.fs.sftp.HostKeyGate] to
     * ask when the server shows its key, not for this to settle silently.
     */
    suspend fun importFromSkiff(profiles: List<ServerProfile>, knownHosts: List<KnownHost>) {
        dataStore.updateData { data ->
            val merged = data.profiles.toMutableList()
            for (shared in profiles) {
                val i = merged.indexOfFirst { it.id == shared.id }.takeIf { it >= 0 }
                    ?: merged.indexOfFirst { it.name == shared.name }
                if (i < 0) {
                    merged.add(shared.copy(auth = AuthMethod.Password(null), lastUsedEpochSeconds = 0))
                    continue
                }
                val own = merged[i]
                val sameMachine = own.host.equals(shared.host, ignoreCase = true) &&
                    own.port == shared.port && own.username == shared.username
                merged[i] = own.copy(
                    name = shared.name,
                    host = shared.host,
                    port = shared.port,
                    username = shared.username,
                    startPath = shared.startPath,
                    auth = if (sameMachine) own.auth else AuthMethod.Password(null),
                )
            }
            val newHosts = knownHosts.filter { shared ->
                data.knownHosts.none { it.host == shared.host && it.port == shared.port }
            }
            data.copy(profiles = merged, knownHosts = data.knownHosts + newHosts)
        }
    }

    /** `data.first()` and not `updateData`, for the reason in [KnownHostStore]. */
    override suspend fun knownHost(host: String, port: Int): KnownHost? =
        dataStore.data.first()
            .knownHosts.firstOrNull { it.host == host && it.port == port }

    override suspend fun rememberHost(knownHost: KnownHost) {
        dataStore.updateData { data ->
            val others = data.knownHosts.filterNot {
                it.host == knownHost.host && it.port == knownHost.port
            }
            data.copy(knownHosts = others + knownHost)
        }
    }

    suspend fun forgetHost(host: String, port: Int) {
        dataStore.updateData { data ->
            data.copy(knownHosts = data.knownHosts.filterNot { it.host == host && it.port == port })
        }
    }

    /** Moves [uri] to the front, and drops the oldest past [MAX_RECENT_FILES]. */
    suspend fun addRecentFile(uri: String, epochSeconds: Long) {
        dataStore.updateData { data ->
            val others = data.recentFiles.filterNot { it.uri == uri }
            data.copy(recentFiles = (listOf(RecentFile(uri, epochSeconds)) + others).take(MAX_RECENT_FILES))
        }
    }

    suspend fun removeRecentFile(uri: String) {
        dataStore.updateData { data ->
            data.copy(recentFiles = data.recentFiles.filterNot { it.uri == uri })
        }
    }

    companion object {
        const val MAX_RECENT_FILES = 50
    }
}
