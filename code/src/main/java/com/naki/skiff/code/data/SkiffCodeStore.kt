package com.naki.skiff.code.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStoreFile
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
