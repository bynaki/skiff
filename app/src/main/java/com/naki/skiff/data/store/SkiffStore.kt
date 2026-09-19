package com.naki.skiff.data.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStoreFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Opens Skiff's store. Call it once per process — [SkiffContainer][com.naki.skiff.SkiffContainer]
 * does. The file is where the `dataStore` delegate used to keep it, so existing data is read.
 */
fun openSkiffDataStore(context: Context): DataStore<SkiffData> =
    jsonDataStore(context.dataStoreFile("skiff.json"), SkiffData.serializer(), SkiffData())

/**
 * Server profiles and accepted host keys. Passwords inside profiles are already
 * ciphertext from [com.naki.skiff.data.crypto.SecretStore]; this layer never sees plaintext.
 */
class SkiffStore(private val dataStore: DataStore<SkiffData>) : KnownHostStore {

    val profiles: Flow<List<ServerProfile>> =
        dataStore.data.map { it.profiles }

    val knownHosts: Flow<List<KnownHost>> =
        dataStore.data.map { it.knownHosts }

    val settings: Flow<Settings> =
        dataStore.data.map { it.settings }

    suspend fun updateSettings(transform: (Settings) -> Settings) {
        dataStore.updateData { it.copy(settings = transform(it.settings)) }
    }

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

    suspend fun touchProfile(id: String, epochSeconds: Long) {
        dataStore.updateData { data ->
            data.copy(
                profiles = data.profiles.map {
                    if (it.id == id) it.copy(lastUsedEpochSeconds = epochSeconds) else it
                },
            )
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
}
